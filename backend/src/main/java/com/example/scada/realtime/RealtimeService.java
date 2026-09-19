package com.example.scada.realtime;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeService {
    private final JdbcTemplate jdbcTemplate;

    public RealtimeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RealtimeValueResponse> listValues(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("请选择设备");
        }
        Integer deviceCount = jdbcTemplate.queryForObject("select count(*) from scada_device where id = ?", Integer.class, deviceId);
        if (deviceCount == null || deviceCount == 0) {
            throw new IllegalArgumentException("设备不存在");
        }
        Instant now = Instant.now();
        return jdbcTemplate.query("""
                select id, device_id, code, name, data_type, unit, access_mode, io_type, address
                from scada_point
                where device_id = ?
                order by sort_order, id
                """, (rs, rowNum) -> new RealtimeValueResponse(
                rs.getLong("id"),
                rs.getLong("device_id"),
                rs.getString("code"),
                simulateValue(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("data_type"),
                        rs.getString("unit"),
                        rs.getString("io_type")
                ),
                quality(rs.getLong("id"), rs.getString("code")),
                now.minusSeconds(Math.floorMod(rs.getLong("id"), 12))
        ), deviceId);
    }

    private String simulateValue(Long pointId, String code, String name, String dataType, String unit, String ioType) {
        String merged = ((code == null ? "" : code) + " " + (name == null ? "" : name) + " " + (ioType == null ? "" : ioType)).toUpperCase();
        int base = Math.floorMod((code == null ? pointId.toString() : code).hashCode(), 1000);
        if ("BOOLEAN".equalsIgnoreCase(dataType) || merged.startsWith("DI") || merged.startsWith("DO") || merged.contains("_GZ")) {
            if (merged.contains("故障") || merged.contains("_GZ")) {
                return Math.floorMod(base, 17) == 0 ? "1" : "0";
            }
            return Math.floorMod(base, 3) == 0 ? "0" : "1";
        }
        double value;
        if ("%".equals(unit) || merged.contains("开度")) {
            value = 35 + Math.floorMod(base, 600) / 10.0;
        } else if ("m".equals(unit) || merged.contains("水位")) {
            value = 1.2 + Math.floorMod(base, 260) / 100.0;
        } else if ("A".equals(unit) || merged.contains("电流")) {
            value = 20 + Math.floorMod(base, 900) / 10.0;
        } else if ("C".equals(unit) || merged.contains("温度")) {
            value = 18 + Math.floorMod(base, 180) / 10.0;
        } else if (merged.contains("电压") || merged.contains("UAB") || merged.contains("UBC") || merged.contains("UCA")) {
            value = 360 + Math.floorMod(base, 420) / 10.0;
        } else if (merged.contains("频率") || merged.endsWith("_F")) {
            value = 49.5 + Math.floorMod(base, 12) / 10.0;
        } else {
            value = Math.floorMod(base, 1000) / 10.0;
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private String quality(Long pointId, String code) {
        int marker = Math.floorMod((code == null ? pointId.toString() : code).hashCode(), 31);
        if (marker == 0) {
            return "BAD";
        }
        if (marker <= 2) {
            return "STALE";
        }
        return "GOOD";
    }
}
