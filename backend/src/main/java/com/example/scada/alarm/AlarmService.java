package com.example.scada.alarm;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.scada.auth.AuthUser;
import com.example.scada.auth.CurrentUserHolder;
import com.example.scada.realtime.RealtimeService;
import com.example.scada.realtime.RealtimeValueResponse;

@Service
public class AlarmService {
    private static final String ACTIVE = "ACTIVE";
    private static final String ACKED = "ACKED";
    private static final String RECOVERED = "RECOVERED";

    private final JdbcTemplate jdbcTemplate;
    private final RealtimeService realtimeService;

    public AlarmService(JdbcTemplate jdbcTemplate, RealtimeService realtimeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.realtimeService = realtimeService;
    }

    @Transactional
    public List<AlarmEventResponse> syncAndListActiveAlarms() {
        List<ComputedAlarm> computed = computeActiveAlarms();
        Set<String> activeKeys = computed.stream().map(ComputedAlarm::alarmKey).collect(Collectors.toSet());
        for (ComputedAlarm alarm : computed) {
            upsertActiveAlarm(alarm);
        }
        recoverMissingAlarms(activeKeys);
        return listEvents(ACTIVE);
    }

    public List<AlarmEventResponse> listEvents(String status) {
        String normalized = normalizeStatus(status);
        if (normalized == null) {
            return jdbcTemplate.query("""
                    select *
                    from scada_alarm_event
                    order by field(status, 'ACTIVE', 'ACKED', 'RECOVERED'), occurred_at desc, id desc
                    limit 300
                    """, (rs, rowNum) -> mapEvent(rs));
        }
        return jdbcTemplate.query("""
                select *
                from scada_alarm_event
                where status = ?
                order by occurred_at desc, id desc
                limit 300
                """, (rs, rowNum) -> mapEvent(rs), normalized);
    }

    @Transactional
    public AlarmEventResponse acknowledge(Long id, AlarmAckRequest request) {
        AuthUser user = CurrentUserHolder.user();
        String actor = user == null ? "system" : user.username();
        String note = request == null || request.note() == null ? "" : request.note().trim();
        int updated = jdbcTemplate.update("""
                update scada_alarm_event
                set status = 'ACKED', acknowledged_at = ?, acknowledged_by = ?, ack_note = ?
                where id = ? and status in ('ACTIVE', 'ACKED')
                """, Timestamp.from(Instant.now()), actor, note, id);
        if (updated == 0) {
            throw new IllegalArgumentException("报警不存在或已恢复");
        }
        return getEvent(id);
    }

    private List<ComputedAlarm> computeActiveAlarms() {
        List<DeviceRow> devices = jdbcTemplate.query("""
                select id, name
                from scada_device
                order by id
                """, (rs, rowNum) -> new DeviceRow(rs.getLong("id"), rs.getString("name")));
        List<ComputedAlarm> alarms = new ArrayList<>();
        for (DeviceRow device : devices) {
            Map<Long, RealtimeValueResponse> values = realtimeService.listValues(device.id()).stream()
                    .collect(Collectors.toMap(RealtimeValueResponse::pointId, Function.identity()));
            List<PointRow> points = jdbcTemplate.query("""
                    select id, code, name, data_type, unit, io_type, modbus_type
                    from scada_point
                    where device_id = ?
                    order by sort_order, id
                    """, (rs, rowNum) -> new PointRow(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("data_type"),
                    rs.getString("unit"),
                    rs.getString("io_type"),
                    rs.getString("modbus_type")
            ), device.id());
            for (PointRow point : points) {
                RealtimeValueResponse value = values.get(point.id());
                if (value == null) {
                    continue;
                }
                ComputedAlarm alarm = evaluate(device, point, value);
                if (alarm != null) {
                    alarms.add(alarm);
                }
            }
        }
        return alarms;
    }

    private ComputedAlarm evaluate(DeviceRow device, PointRow point, RealtimeValueResponse value) {
        if ("BAD".equals(value.quality())) {
            return alarm(device, point, value, "高", "点位质量异常");
        }
        if ("STALE".equals(value.quality())) {
            return alarm(device, point, value, "中", "点位数据超时");
        }
        String text = (point.code() + " " + point.name()).toUpperCase();
        double numeric = parseNumber(value.value());
        if ((text.contains("故障") || text.contains("_GZ")) && "1".equals(value.value())) {
            return alarm(device, point, value, "高", "故障信号触发");
        }
        if (("%".equals(point.unit()) || text.contains("开度")) && numeric > 90) {
            return alarm(device, point, value, "中", "开度超过 90%");
        }
        if (("A".equals(point.unit()) || text.contains("电流")) && numeric > 95) {
            return alarm(device, point, value, "中", "电流偏高");
        }
        if ((text.contains("电压") || text.contains("UAB") || text.contains("UBC") || text.contains("UCA")) && (numeric < 360 || numeric > 410)) {
            return alarm(device, point, value, "低", "电压越限");
        }
        return null;
    }

    private ComputedAlarm alarm(DeviceRow device, PointRow point, RealtimeValueResponse value, String level, String message) {
        return new ComputedAlarm(
                device.id() + "-" + point.id() + "-" + message,
                device.id(),
                device.name(),
                point.id(),
                point.code(),
                point.name(),
                level,
                message,
                value.value(),
                value.quality(),
                value.collectedAt()
        );
    }

    private void upsertActiveAlarm(ComputedAlarm alarm) {
        jdbcTemplate.update("""
                insert into scada_alarm_event(alarm_key, device_id, device_name, point_id, point_code, point_name, level, message,
                    value, quality, status, occurred_at, last_seen_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                on duplicate key update device_name = values(device_name), point_name = values(point_name), level = values(level),
                    value = values(value), quality = values(quality), last_seen_at = values(last_seen_at),
                    status = if(status = 'RECOVERED', 'ACTIVE', status), recovered_at = if(status = 'RECOVERED', null, recovered_at)
                """, alarm.alarmKey(), alarm.deviceId(), alarm.deviceName(), alarm.pointId(), alarm.pointCode(), alarm.pointName(),
                alarm.level(), alarm.message(), alarm.value(), alarm.quality(), Timestamp.from(alarm.occurredAt()), Timestamp.from(Instant.now()));
    }

    private void recoverMissingAlarms(Set<String> activeKeys) {
        List<String> openKeys = jdbcTemplate.query("""
                select alarm_key
                from scada_alarm_event
                where status in ('ACTIVE', 'ACKED')
                """, (rs, rowNum) -> rs.getString("alarm_key"));
        Set<String> current = new HashSet<>(activeKeys);
        Instant now = Instant.now();
        for (String key : openKeys) {
            if (!current.contains(key)) {
                jdbcTemplate.update("""
                        update scada_alarm_event
                        set status = 'RECOVERED', recovered_at = ?
                        where alarm_key = ? and status in ('ACTIVE', 'ACKED')
                        """, Timestamp.from(now), key);
            }
        }
    }

    private AlarmEventResponse getEvent(Long id) {
        List<AlarmEventResponse> events = jdbcTemplate.query("select * from scada_alarm_event where id = ?", (rs, rowNum) -> mapEvent(rs), id);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("报警不存在");
        }
        return events.getFirst();
    }

    private AlarmEventResponse mapEvent(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AlarmEventResponse(
                rs.getLong("id"),
                rs.getString("alarm_key"),
                rs.getLong("device_id"),
                rs.getString("device_name"),
                rs.getLong("point_id"),
                rs.getString("point_code"),
                rs.getString("point_name"),
                rs.getString("level"),
                rs.getString("message"),
                rs.getString("value"),
                rs.getString("quality"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("occurred_at")),
                toInstant(rs.getTimestamp("last_seen_at")),
                toInstant(rs.getTimestamp("recovered_at")),
                toInstant(rs.getTimestamp("acknowledged_at")),
                rs.getString("acknowledged_by"),
                rs.getString("ack_note")
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of(ACTIVE, ACKED, RECOVERED).contains(normalized)) {
            throw new IllegalArgumentException("报警状态不支持");
        }
        return normalized;
    }

    private double parseNumber(String value) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private record DeviceRow(Long id, String name) {
    }

    private record PointRow(Long id, String code, String name, String dataType, String unit, String ioType, String modbusType) {
    }

    private record ComputedAlarm(String alarmKey, Long deviceId, String deviceName, Long pointId, String pointCode, String pointName,
                                 String level, String message, String value, String quality, Instant occurredAt) {
    }
}
