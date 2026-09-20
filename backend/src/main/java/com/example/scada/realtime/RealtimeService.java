package com.example.scada.realtime;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeService {
    private final JdbcTemplate jdbcTemplate;
    private final RealtimeValueCache realtimeValueCache;

    public RealtimeService(JdbcTemplate jdbcTemplate, RealtimeValueCache realtimeValueCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.realtimeValueCache = realtimeValueCache;
    }

    public List<RealtimeValueResponse> listValues(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("请选择设备");
        }
        Integer deviceCount = jdbcTemplate.queryForObject("select count(*) from scada_device where id = ?", Integer.class, deviceId);
        if (deviceCount == null || deviceCount == 0) {
            throw new IllegalArgumentException("设备不存在");
        }
        List<PointRow> points = jdbcTemplate.query("""
                select id, device_id, code
                from scada_point
                where device_id = ?
                order by sort_order, id
                """, (rs, rowNum) -> new PointRow(
                rs.getLong("id"),
                rs.getLong("device_id"),
                rs.getString("code")
        ), deviceId);
        Map<Long, RealtimeValueResponse> cachedValues = realtimeValueCache.getAll(points.stream().map(PointRow::id).toList());
        return points.stream()
                .map(point -> cachedValues.getOrDefault(point.id(), realtimeValueCache.stale(point.id(), point.deviceId(), point.code())))
                .toList();
    }

    public long cachedValueCount() {
        return realtimeValueCache.size();
    }

    private record PointRow(Long id, Long deviceId, String code) {
    }
}

