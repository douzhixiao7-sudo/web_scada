package com.example.scada.collector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectChannelService {
    private final JdbcTemplate jdbcTemplate;

    public CollectChannelService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CollectChannelResponse> listChannels(Long deviceId, Boolean enabled) {
        StringBuilder sql = new StringBuilder("""
                select c.id, c.device_id, d.name as device_name, d.code as device_code, c.name, c.code, c.protocol,
                       c.host, c.port, c.poll_interval_ms, c.enabled, c.status, c.last_polled_at,
                       count(b.id) as point_count
                from scada_collect_channel c
                join scada_device d on d.id = c.device_id
                left join scada_collect_binding b on b.channel_id = c.id and b.enabled = 1
                where 1 = 1
                """);
        ArrayList<Object> params = new ArrayList<>();
        if (deviceId != null) {
            sql.append(" and c.device_id = ?");
            params.add(deviceId);
        }
        if (enabled != null) {
            sql.append(" and c.enabled = ?");
            params.add(enabled ? 1 : 0);
        }
        sql.append(" group by c.id, c.device_id, d.name, d.code, c.name, c.code, c.protocol, c.host, c.port, c.poll_interval_ms, c.enabled, c.status, c.last_polled_at order by d.id, c.id");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new CollectChannelResponse(
                rs.getLong("id"),
                rs.getLong("device_id"),
                rs.getString("device_name"),
                rs.getString("device_code"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("protocol"),
                rs.getString("host"),
                (Integer) rs.getObject("port"),
                rs.getInt("poll_interval_ms"),
                rs.getBoolean("enabled"),
                rs.getString("status"),
                rs.getInt("point_count"),
                rs.getTimestamp("last_polled_at") == null ? null : rs.getTimestamp("last_polled_at").toInstant().toString()
        ), params.toArray());
    }

    @Transactional
    public CollectChannelResponse updateChannel(Long id, CollectChannelRequest request) {
        if (request == null || isBlank(request.name()) || isBlank(request.protocol()) || isBlank(request.host()) || isBlank(request.status())) {
            throw new IllegalArgumentException("通道名称、协议、主机和状态不能为空");
        }
        int updated = jdbcTemplate.update("""
                update scada_collect_channel
                set name = ?, protocol = ?, host = ?, port = ?, poll_interval_ms = ?, enabled = ?, status = ?
                where id = ?
                """, clean(request.name()), clean(request.protocol()), clean(request.host()), request.port(), pollInterval(request),
                Boolean.FALSE.equals(request.enabled()) ? 0 : 1, clean(request.status()), id);
        if (updated == 0) {
            throw new IllegalArgumentException("采集通道不存在");
        }
        return getChannel(id);
    }

    public List<CollectBindingResponse> listBindings(Long channelId) {
        ensureChannelExists(channelId);
        return jdbcTemplate.query("""
                select b.id, b.channel_id, b.point_id, p.code as point_code, p.name as point_name, p.address, p.modbus_type, p.access_mode, b.enabled
                from scada_collect_binding b
                join scada_point p on p.id = b.point_id
                where b.channel_id = ?
                order by p.sort_order, p.id
                """, (rs, rowNum) -> new CollectBindingResponse(
                rs.getLong("id"),
                rs.getLong("channel_id"),
                rs.getLong("point_id"),
                rs.getString("point_code"),
                rs.getString("point_name"),
                rs.getString("address"),
                rs.getString("modbus_type"),
                rs.getString("access_mode"),
                rs.getBoolean("enabled")
        ), channelId);
    }

    @Transactional
    public CollectBindingResponse updateBinding(Long channelId, Long bindingId, CollectBindingRequest request) {
        ensureChannelExists(channelId);
        int updated = jdbcTemplate.update("""
                update scada_collect_binding
                set enabled = ?
                where id = ? and channel_id = ?
                """, Boolean.FALSE.equals(request == null ? null : request.enabled()) ? 0 : 1, bindingId, channelId);
        if (updated == 0) {
            throw new IllegalArgumentException("采集点位绑定不存在");
        }
        return getBinding(channelId, bindingId);
    }

    @Transactional
    public CollectChannelResponse markPolled(Long id) {
        int updated = jdbcTemplate.update("""
                update scada_collect_channel
                set last_polled_at = ?, status = 'READY'
                where id = ?
                """, java.sql.Timestamp.from(Instant.now()), id);
        if (updated == 0) {
            throw new IllegalArgumentException("采集通道不存在");
        }
        return getChannel(id);
    }

    private CollectChannelResponse getChannel(Long id) {
        List<CollectChannelResponse> channels = jdbcTemplate.query("""
                select c.id, c.device_id, d.name as device_name, d.code as device_code, c.name, c.code, c.protocol,
                       c.host, c.port, c.poll_interval_ms, c.enabled, c.status, c.last_polled_at,
                       count(b.id) as point_count
                from scada_collect_channel c
                join scada_device d on d.id = c.device_id
                left join scada_collect_binding b on b.channel_id = c.id and b.enabled = 1
                where c.id = ?
                group by c.id, c.device_id, d.name, d.code, c.name, c.code, c.protocol, c.host, c.port, c.poll_interval_ms, c.enabled, c.status, c.last_polled_at
                """, (rs, rowNum) -> new CollectChannelResponse(
                rs.getLong("id"), rs.getLong("device_id"), rs.getString("device_name"), rs.getString("device_code"),
                rs.getString("name"), rs.getString("code"), rs.getString("protocol"), rs.getString("host"),
                (Integer) rs.getObject("port"), rs.getInt("poll_interval_ms"), rs.getBoolean("enabled"), rs.getString("status"),
                rs.getInt("point_count"), rs.getTimestamp("last_polled_at") == null ? null : rs.getTimestamp("last_polled_at").toInstant().toString()
        ), id);
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("采集通道不存在");
        }
        return channels.getFirst();
    }

    private CollectBindingResponse getBinding(Long channelId, Long bindingId) {
        List<CollectBindingResponse> bindings = jdbcTemplate.query("""
                select b.id, b.channel_id, b.point_id, p.code as point_code, p.name as point_name, p.address, p.modbus_type, p.access_mode, b.enabled
                from scada_collect_binding b
                join scada_point p on p.id = b.point_id
                where b.channel_id = ? and b.id = ?
                """, (rs, rowNum) -> new CollectBindingResponse(
                rs.getLong("id"), rs.getLong("channel_id"), rs.getLong("point_id"), rs.getString("point_code"),
                rs.getString("point_name"), rs.getString("address"), rs.getString("modbus_type"), rs.getString("access_mode"), rs.getBoolean("enabled")
        ), channelId, bindingId);
        if (bindings.isEmpty()) {
            throw new IllegalArgumentException("采集点位绑定不存在");
        }
        return bindings.getFirst();
    }

    private void ensureChannelExists(Long channelId) {
        if (channelId == null) {
            throw new IllegalArgumentException("请选择采集通道");
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from scada_collect_channel where id = ?", Integer.class, channelId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("采集通道不存在");
        }
    }

    private Integer pollInterval(CollectChannelRequest request) {
        return request.pollIntervalMs() == null || request.pollIntervalMs() < 200 ? 1000 : request.pollIntervalMs();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
