package com.example.scada.history;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HistoryQueryService {
    private final JdbcTemplate jdbcTemplate;

    public HistoryQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<HistoryValueResponse> listValues(Long pointId, Instant start, Instant end) {
        if (pointId == null) {
            throw new IllegalArgumentException("请选择点位");
        }
        Instant actualEnd = end == null ? Instant.now() : end;
        Instant actualStart = start == null ? actualEnd.minus(Duration.ofHours(1)) : start;
        return jdbcTemplate.query("""
                select id, device_id, point_id, point_code, point_name, value, quality, collected_at
                from scada_history_value
                where point_id = ? and collected_at between ? and ?
                order by collected_at
                limit 1000
                """, (rs, rowNum) -> new HistoryValueResponse(
                rs.getLong("id"),
                rs.getLong("device_id"),
                rs.getLong("point_id"),
                rs.getString("point_code"),
                rs.getString("point_name"),
                rs.getString("value"),
                rs.getString("quality"),
                rs.getTimestamp("collected_at").toInstant()
        ), pointId, Timestamp.from(actualStart), Timestamp.from(actualEnd));
    }

    public List<HistoryLatestResponse> latest(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("请选择设备");
        }
        return jdbcTemplate.query("""
                select h.point_id, h.point_code, h.point_name, h.value, h.quality, h.collected_at
                from scada_history_value h
                join (
                    select point_id, max(id) as id
                    from scada_history_value
                    where device_id = ?
                    group by point_id
                ) latest on latest.id = h.id
                where h.device_id = ?
                order by h.point_code
                limit 200
                """, (rs, rowNum) -> new HistoryLatestResponse(
                rs.getLong("point_id"),
                rs.getString("point_code"),
                rs.getString("point_name"),
                rs.getString("value"),
                rs.getString("quality"),
                rs.getTimestamp("collected_at").toInstant().toString()
        ), deviceId, deviceId);
    }
}
