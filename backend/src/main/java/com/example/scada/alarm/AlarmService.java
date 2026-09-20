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

    public List<AlarmRuleResponse> listRules(Long deviceId, Long pointId, Boolean enabled) {
        StringBuilder sql = new StringBuilder("""
                select r.id, r.point_id, r.point_code, r.point_name, r.rule_name, r.rule_type, r.operator, r.threshold_value, r.level, r.message, r.enabled
                from scada_alarm_rule r
                join scada_point p on p.id = r.point_id
                where 1 = 1
                """);
        ArrayList<Object> params = new ArrayList<>();
        if (deviceId != null) {
            sql.append(" and p.device_id = ?");
            params.add(deviceId);
        }
        if (pointId != null) {
            sql.append(" and r.point_id = ?");
            params.add(pointId);
        }
        if (enabled != null) {
            sql.append(" and r.enabled = ?");
            params.add(enabled ? 1 : 0);
        }
        sql.append(" order by p.device_id, p.sort_order, r.id limit 1000");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapRule(rs), params.toArray());
    }

    @Transactional
    public AlarmRuleResponse updateRule(Long id, AlarmRuleRequest request) {
        if (request == null || isBlank(request.ruleName()) || isBlank(request.level()) || isBlank(request.message())) {
            throw new IllegalArgumentException("规则名称、等级和内容不能为空");
        }
        int updated = jdbcTemplate.update("""
                update scada_alarm_rule
                set rule_name = ?, operator = ?, threshold_value = ?, level = ?, message = ?, enabled = ?
                where id = ?
                """, clean(request.ruleName()), clean(request.operator()), request.thresholdValue(), clean(request.level()), clean(request.message()),
                Boolean.FALSE.equals(request.enabled()) ? 0 : 1, id);
        if (updated == 0) {
            throw new IllegalArgumentException("报警规则不存在");
        }
        return getRule(id);
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
                for (AlarmRuleRow rule : listEnabledRules(point.id())) {
                    ComputedAlarm alarm = evaluate(device, point, value, rule);
                    if (alarm != null) {
                        alarms.add(alarm);
                    }
                }
            }
        }
        return alarms;
    }

    private List<AlarmRuleRow> listEnabledRules(Long pointId) {
        return jdbcTemplate.query("""
                select id, rule_name, rule_type, operator, threshold_value, level, message
                from scada_alarm_rule
                where point_id = ? and enabled = 1
                order by id
                """, (rs, rowNum) -> new AlarmRuleRow(
                rs.getLong("id"),
                rs.getString("rule_name"),
                rs.getString("rule_type"),
                rs.getString("operator"),
                numberValue(rs.getObject("threshold_value")),
                rs.getString("level"),
                rs.getString("message")
        ), pointId);
    }

    private ComputedAlarm evaluate(DeviceRow device, PointRow point, RealtimeValueResponse value, AlarmRuleRow rule) {
        double numeric = parseNumber(value.value());
        boolean triggered = switch (rule.ruleType()) {
            case "QUALITY_BAD" -> "BAD".equals(value.quality());
            case "QUALITY_STALE" -> "STALE".equals(value.quality());
            case "EQUAL" -> rule.thresholdValue() != null && numeric == rule.thresholdValue();
            case "HIGH" -> rule.thresholdValue() != null && numeric > rule.thresholdValue();
            case "LOW" -> rule.thresholdValue() != null && numeric < rule.thresholdValue();
            default -> false;
        };
        return triggered ? alarm(device, point, value, rule.level(), rule.message()) : null;
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

    private AlarmRuleResponse getRule(Long id) {
        List<AlarmRuleResponse> rules = jdbcTemplate.query("""
                select id, point_id, point_code, point_name, rule_name, rule_type, operator, threshold_value, level, message, enabled
                from scada_alarm_rule
                where id = ?
                """, (rs, rowNum) -> mapRule(rs), id);
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("报警规则不存在");
        }
        return rules.getFirst();
    }

    private AlarmRuleResponse mapRule(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AlarmRuleResponse(
                rs.getLong("id"),
                rs.getLong("point_id"),
                rs.getString("point_code"),
                rs.getString("point_name"),
                rs.getString("rule_name"),
                rs.getString("rule_type"),
                rs.getString("operator"),
                numberValue(rs.getObject("threshold_value")),
                rs.getString("level"),
                rs.getString("message"),
                rs.getBoolean("enabled")
        );
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private Double numberValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
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

    private record AlarmRuleRow(Long id, String ruleName, String ruleType, String operator, Double thresholdValue, String level, String message) {
    }

    private record ComputedAlarm(String alarmKey, Long deviceId, String deviceName, Long pointId, String pointCode, String pointName,
                                 String level, String message, String value, String quality, Instant occurredAt) {
    }
}

