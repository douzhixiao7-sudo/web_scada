package com.example.scada.alarm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.scada.realtime.RealtimeService;
import com.example.scada.realtime.RealtimeValueResponse;

@Service
public class AlarmService {
    private final JdbcTemplate jdbcTemplate;
    private final RealtimeService realtimeService;

    public AlarmService(JdbcTemplate jdbcTemplate, RealtimeService realtimeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.realtimeService = realtimeService;
    }

    public List<AlarmEventResponse> listActiveAlarms() {
        List<DeviceRow> devices = jdbcTemplate.query("""
                select id, name
                from scada_device
                order by id
                """, (rs, rowNum) -> new DeviceRow(rs.getLong("id"), rs.getString("name")));
        List<AlarmEventResponse> alarms = new ArrayList<>();
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
                AlarmEventResponse alarm = evaluate(device, point, value);
                if (alarm != null) {
                    alarms.add(alarm);
                }
            }
        }
        return alarms;
    }

    private AlarmEventResponse evaluate(DeviceRow device, PointRow point, RealtimeValueResponse value) {
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

    private AlarmEventResponse alarm(DeviceRow device, PointRow point, RealtimeValueResponse value, String level, String message) {
        return new AlarmEventResponse(
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
}
