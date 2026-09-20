package com.example.scada.collector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.scada.realtime.RealtimeValueCache;
import com.example.scada.realtime.RealtimeValueResponse;

@Component
public class SimulatedCollectScheduler {
    private final JdbcTemplate jdbcTemplate;
    private final RealtimeValueCache realtimeValueCache;

    public SimulatedCollectScheduler(JdbcTemplate jdbcTemplate, RealtimeValueCache realtimeValueCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.realtimeValueCache = realtimeValueCache;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public void collect() {
        List<BindingPoint> points = jdbcTemplate.query("""
                select c.id as channel_id, c.poll_interval_ms, p.id as point_id, p.device_id, p.code, p.name,
                       p.data_type, p.unit, p.io_type, p.modbus_type, p.address
                from scada_collect_channel c
                join scada_collect_binding b on b.channel_id = c.id and b.enabled = 1
                join scada_point p on p.id = b.point_id
                where c.enabled = 1
                order by c.id, p.sort_order, p.id
                """, (rs, rowNum) -> new BindingPoint(
                rs.getLong("channel_id"),
                rs.getInt("poll_interval_ms"),
                rs.getLong("point_id"),
                rs.getLong("device_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("data_type"),
                rs.getString("unit"),
                rs.getString("io_type"),
                rs.getString("modbus_type"),
                rs.getString("address")
        ));
        if (points.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<RealtimeValueResponse> values = new ArrayList<>(points.size());
        for (BindingPoint point : points) {
            values.add(new RealtimeValueResponse(
                    point.pointId(),
                    point.deviceId(),
                    point.code(),
                    simulateValue(point, now),
                    quality(point, now),
                    now
            ));
        }
        realtimeValueCache.putAll(values);
        jdbcTemplate.update("""
                update scada_collect_channel
                set last_polled_at = ?, status = 'READY'
                where enabled = 1
                """, java.sql.Timestamp.from(now));
    }

    private String simulateValue(BindingPoint point, Instant now) {
        String text = ((point.code() == null ? "" : point.code()) + " " + (point.name() == null ? "" : point.name()) + " " + (point.ioType() == null ? "" : point.ioType())).toUpperCase(java.util.Locale.ROOT);
        int seed = Math.floorMod((point.code() == null ? String.valueOf(point.pointId()) : point.code()).hashCode(), 1000);
        long tick = now.getEpochSecond();
        if ("BOOLEAN".equalsIgnoreCase(point.dataType()) || text.startsWith("DI") || text.startsWith("DO") || text.contains("_GZ")) {
            if (text.contains("故障") || text.contains("_GZ")) {
                return Math.floorMod(seed + tick / 20, 23) == 0 ? "1" : "0";
            }
            return Math.floorMod(seed + tick / 10, 4) == 0 ? "0" : "1";
        }
        double wave = Math.sin((tick + seed) / 12.0);
        double value;
        if ("%".equals(point.unit()) || text.contains("开度")) {
            value = 50 + wave * 35 + Math.floorMod(seed, 20) / 10.0;
        } else if ("m".equals(point.unit()) || text.contains("水位")) {
            value = 2.0 + wave * 0.4 + Math.floorMod(seed, 30) / 100.0;
        } else if ("A".equals(point.unit()) || text.contains("电流") || text.contains("IA") || text.contains("IB") || text.contains("IC")) {
            value = 45 + wave * 25 + Math.floorMod(seed, 80) / 10.0;
        } else if ("C".equals(point.unit()) || text.contains("温度") || text.contains("WD")) {
            value = 26 + wave * 4 + Math.floorMod(seed, 30) / 10.0;
        } else if (text.contains("电压") || text.contains("UAB") || text.contains("UBC") || text.contains("UCA")) {
            value = 385 + wave * 18 + Math.floorMod(seed, 30) / 10.0;
        } else if (text.contains("频率") || text.endsWith("_F")) {
            value = 50 + wave * 0.15;
        } else {
            value = 50 + wave * 20 + Math.floorMod(seed, 100) / 10.0;
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private String quality(BindingPoint point, Instant now) {
        int marker = Math.floorMod((point.code() == null ? String.valueOf(point.pointId()) : point.code()).hashCode() + (int) (now.getEpochSecond() / 15), 37);
        if (marker == 0) {
            return "BAD";
        }
        if (marker == 1) {
            return "STALE";
        }
        return "GOOD";
    }

    private record BindingPoint(Long channelId, Integer pollIntervalMs, Long pointId, Long deviceId, String code, String name,
                                String dataType, String unit, String ioType, String modbusType, String address) {
    }
}
