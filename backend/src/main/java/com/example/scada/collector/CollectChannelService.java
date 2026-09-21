package com.example.scada.collector;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectChannelService {
    private final JdbcTemplate jdbcTemplate;
    private final ModbusTcpClient modbusTcpClient;

    public CollectChannelService(JdbcTemplate jdbcTemplate, ModbusTcpClient modbusTcpClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.modbusTcpClient = modbusTcpClient;
    }

    public List<CollectChannelResponse> listChannels(Long deviceId, Boolean enabled) {
        StringBuilder sql = new StringBuilder("""
                select c.id, c.device_id, d.name as device_name, d.code as device_code, c.name, c.code, c.protocol,
                       c.channel_mode, c.host, c.port, c.slave_id, c.timeout_ms, c.retry_count,
                       c.poll_interval_ms, c.enabled, c.status, c.last_polled_at, c.last_success_at,
                       c.last_error, c.last_latency_ms, c.consecutive_failures,
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
        sql.append("""
                group by c.id, c.device_id, d.name, d.code, c.name, c.code, c.protocol, c.channel_mode, c.host, c.port,
                         c.slave_id, c.timeout_ms, c.retry_count, c.poll_interval_ms, c.enabled, c.status, c.last_polled_at,
                         c.last_success_at, c.last_error, c.last_latency_ms, c.consecutive_failures
                order by d.id, c.id
                """);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapChannel(rs), params.toArray());
    }

    @Transactional
    public CollectChannelResponse updateChannel(Long id, CollectChannelRequest request) {
        if (request == null || isBlank(request.name()) || isBlank(request.protocol()) || isBlank(request.host()) || isBlank(request.status())) {
            throw new IllegalArgumentException("通道名称、协议、主机和状态不能为空");
        }
        int updated = jdbcTemplate.update("""
                update scada_collect_channel
                set name = ?, protocol = ?, channel_mode = ?, host = ?, port = ?, slave_id = ?, timeout_ms = ?, retry_count = ?,
                    poll_interval_ms = ?, enabled = ?, status = ?
                where id = ?
                """, clean(request.name()), clean(request.protocol()), channelMode(request), clean(request.host()), request.port(), slaveId(request),
                timeoutMs(request), retryCount(request), pollInterval(request), Boolean.FALSE.equals(request.enabled()) ? 0 : 1, clean(request.status()), id);
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
                rs.getLong("id"), rs.getLong("channel_id"), rs.getLong("point_id"), rs.getString("point_code"),
                rs.getString("point_name"), rs.getString("address"), rs.getString("modbus_type"), rs.getString("access_mode"), rs.getBoolean("enabled")
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
    public CollectDiagnosticResponse testConnection(Long id) {
        ChannelRow channel = loadChannel(id);
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(channel.host(), channel.port()), channel.timeoutMs());
            long latency = latencyMs(started);
            markDiagnosticSuccess(id, latency);
            return new CollectDiagnosticResponse(id, channel.name(), true, "连接测试成功", latency, null, "", "", "", "GOOD");
        } catch (Exception ex) {
            long latency = latencyMs(started);
            String message = trimMessage(ex.getMessage());
            markDiagnosticFailure(id, latency, message);
            return new CollectDiagnosticResponse(id, channel.name(), false, message, latency, null, "", "", "", "BAD");
        }
    }

    @Transactional
    public CollectDiagnosticResponse testRead(Long channelId, Long pointId) {
        ChannelRow channel = loadChannel(channelId);
        BindingPointRow point = pointId == null ? loadFirstEnabledPoint(channelId) : loadPoint(channelId, pointId);
        long started = System.nanoTime();
        try {
            String value = modbusTcpClient.readValue(channel.host(), channel.port(), point.modbusType(), point.address(), point.dataType());
            long latency = latencyMs(started);
            markDiagnosticSuccess(channelId, latency);
            return new CollectDiagnosticResponse(channelId, channel.name(), true, "测试读取成功", latency,
                    point.pointId(), point.pointCode(), point.pointName(), value, "GOOD");
        } catch (Exception ex) {
            long latency = latencyMs(started);
            String message = trimMessage(ex.getMessage());
            markDiagnosticFailure(channelId, latency, message);
            return new CollectDiagnosticResponse(channelId, channel.name(), false, message, latency,
                    point.pointId(), point.pointCode(), point.pointName(), "", "BAD");
        }
    }

    @Transactional
    public CollectChannelResponse markPolled(Long id) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                update scada_collect_channel
                set last_polled_at = ?, last_success_at = ?, status = 'READY', last_error = '', consecutive_failures = 0
                where id = ?
                """, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), id);
        if (updated == 0) {
            throw new IllegalArgumentException("采集通道不存在");
        }
        return getChannel(id);
    }

    private CollectChannelResponse getChannel(Long id) {
        List<CollectChannelResponse> channels = jdbcTemplate.query("""
                select c.id, c.device_id, d.name as device_name, d.code as device_code, c.name, c.code, c.protocol,
                       c.channel_mode, c.host, c.port, c.slave_id, c.timeout_ms, c.retry_count,
                       c.poll_interval_ms, c.enabled, c.status, c.last_polled_at, c.last_success_at,
                       c.last_error, c.last_latency_ms, c.consecutive_failures,
                       count(b.id) as point_count
                from scada_collect_channel c
                join scada_device d on d.id = c.device_id
                left join scada_collect_binding b on b.channel_id = c.id and b.enabled = 1
                where c.id = ?
                group by c.id, c.device_id, d.name, d.code, c.name, c.code, c.protocol, c.channel_mode, c.host, c.port,
                         c.slave_id, c.timeout_ms, c.retry_count, c.poll_interval_ms, c.enabled, c.status,
                         c.last_polled_at, c.last_success_at, c.last_error, c.last_latency_ms, c.consecutive_failures
                """, (rs, rowNum) -> mapChannel(rs), id);
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("采集通道不存在");
        }
        return channels.getFirst();
    }

    private CollectChannelResponse mapChannel(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CollectChannelResponse(
                rs.getLong("id"), rs.getLong("device_id"), rs.getString("device_name"), rs.getString("device_code"),
                rs.getString("name"), rs.getString("code"), rs.getString("protocol"), rs.getString("channel_mode"), rs.getString("host"),
                (Integer) rs.getObject("port"), rs.getInt("slave_id"), rs.getInt("timeout_ms"), rs.getInt("retry_count"),
                rs.getInt("poll_interval_ms"), rs.getBoolean("enabled"), rs.getString("status"), rs.getInt("point_count"),
                rs.getTimestamp("last_polled_at") == null ? null : rs.getTimestamp("last_polled_at").toInstant().toString(),
                rs.getTimestamp("last_success_at") == null ? null : rs.getTimestamp("last_success_at").toInstant().toString(),
                rs.getString("last_error"), (Integer) rs.getObject("last_latency_ms"), rs.getInt("consecutive_failures")
        );
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

    private ChannelRow loadChannel(Long id) {
        List<ChannelRow> channels = jdbcTemplate.query("""
                select id, name, host, coalesce(port, 1502) as port, timeout_ms
                from scada_collect_channel
                where id = ?
                """, (rs, rowNum) -> new ChannelRow(rs.getLong("id"), rs.getString("name"), rs.getString("host"), rs.getInt("port"), rs.getInt("timeout_ms")), id);
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("采集通道不存在");
        }
        return channels.getFirst();
    }

    private BindingPointRow loadFirstEnabledPoint(Long channelId) {
        List<BindingPointRow> points = jdbcTemplate.query("""
                select p.id, p.code, p.name, p.data_type, p.address, p.modbus_type
                from scada_collect_binding b
                join scada_point p on p.id = b.point_id
                where b.channel_id = ? and b.enabled = 1
                order by p.sort_order, p.id
                limit 1
                """, (rs, rowNum) -> mapBindingPoint(rs), channelId);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("通道没有启用的点位绑定");
        }
        return points.getFirst();
    }

    private BindingPointRow loadPoint(Long channelId, Long pointId) {
        List<BindingPointRow> points = jdbcTemplate.query("""
                select p.id, p.code, p.name, p.data_type, p.address, p.modbus_type
                from scada_collect_binding b
                join scada_point p on p.id = b.point_id
                where b.channel_id = ? and p.id = ?
                limit 1
                """, (rs, rowNum) -> mapBindingPoint(rs), channelId, pointId);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("测试点位不属于该通道");
        }
        return points.getFirst();
    }

    private BindingPointRow mapBindingPoint(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BindingPointRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("data_type"), rs.getString("address"), rs.getString("modbus_type"));
    }

    private void markDiagnosticSuccess(Long id, Long latencyMs) {
        jdbcTemplate.update("""
                update scada_collect_channel
                set status = 'DIAG_OK', last_success_at = ?, last_error = '', last_latency_ms = ?, consecutive_failures = 0
                where id = ?
                """, java.sql.Timestamp.from(Instant.now()), latencyMs.intValue(), id);
    }

    private void markDiagnosticFailure(Long id, Long latencyMs, String message) {
        jdbcTemplate.update("""
                update scada_collect_channel
                set status = 'DIAG_FAILED', last_error = ?, last_latency_ms = ?, consecutive_failures = consecutive_failures + 1
                where id = ?
                """, message, latencyMs.intValue(), id);
    }

    private long latencyMs(long startedNanos) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }

    private String channelMode(CollectChannelRequest request) {
        String value = clean(request.channelMode());
        return value.isBlank() ? "SIMULATOR" : value;
    }

    private Integer slaveId(CollectChannelRequest request) {
        return request.slaveId() == null || request.slaveId() < 1 ? 1 : request.slaveId();
    }

    private Integer timeoutMs(CollectChannelRequest request) {
        return request.timeoutMs() == null || request.timeoutMs() < 200 ? 1200 : request.timeoutMs();
    }

    private Integer retryCount(CollectChannelRequest request) {
        return request.retryCount() == null || request.retryCount() < 0 ? 1 : Math.min(request.retryCount(), 5);
    }

    private Integer pollInterval(CollectChannelRequest request) {
        return request.pollIntervalMs() == null || request.pollIntervalMs() < 200 ? 1000 : request.pollIntervalMs();
    }

    private String trimMessage(String message) {
        if (message == null || message.isBlank()) {
            return "诊断失败";
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record ChannelRow(Long id, String name, String host, Integer port, Integer timeoutMs) {
    }

    private record BindingPointRow(Long pointId, String pointCode, String pointName, String dataType, String address, String modbusType) {
    }
}
