package com.example.scada.control;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.scada.auth.AuthUser;
import com.example.scada.auth.CurrentUserHolder;
import com.example.scada.collector.ModbusTcpClient;

@Service
public class ControlCommandService {
    private final JdbcTemplate jdbcTemplate;
    private final ModbusTcpClient modbusTcpClient;

    public ControlCommandService(JdbcTemplate jdbcTemplate, ModbusTcpClient modbusTcpClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.modbusTcpClient = modbusTcpClient;
    }

    @Transactional
    public ControlCommandResponse submit(ControlCommandRequest request) {
        if (request.pointId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择控制点位");
        }
        if (request.targetValue() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入目标值");
        }
        ControlPoint point = loadPoint(request.pointId());
        validateWritable(point);
        String commandNo = "CMD-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String requestedBy = currentUsername();
        jdbcTemplate.update("""
                insert into scada_control_command(command_no, device_id, device_name, point_id, point_code, point_name,
                    target_value, status, message, requested_by)
                values (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """, commandNo, point.deviceId(), point.deviceName(), point.pointId(), point.pointCode(), point.pointName(),
                request.targetValue(), "命令已创建，等待写入", requestedBy);
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        try {
            modbusTcpClient.writeValue(point.host(), point.port(), point.modbusType(), point.address(), point.dataType(), request.targetValue());
            jdbcTemplate.update("""
                    update scada_control_command
                    set status = 'SUCCESS', message = ?, executed_at = current_timestamp
                    where id = ?
                    """, "写入 Modbus 仿真器成功", id);
        } catch (Exception ex) {
            jdbcTemplate.update("""
                    update scada_control_command
                    set status = 'FAILED', message = ?, executed_at = current_timestamp
                    where id = ?
                    """, trimMessage(ex.getMessage()), id);
        }
        return getCommand(id);
    }

    public List<ControlCommandResponse> list(Long deviceId) {
        String sql = """
                select id, command_no, device_id, device_name, point_id, point_code, point_name, target_value,
                       status, message, requested_by, created_at, executed_at
                from scada_control_command
                """;
        if (deviceId == null) {
            return jdbcTemplate.query(sql + " order by id desc limit 50", (rs, rowNum) -> mapCommand(rs));
        }
        return jdbcTemplate.query(sql + " where device_id = ? order by id desc limit 50", (rs, rowNum) -> mapCommand(rs), deviceId);
    }

    private ControlCommandResponse getCommand(Long id) {
        return jdbcTemplate.queryForObject("""
                select id, command_no, device_id, device_name, point_id, point_code, point_name, target_value,
                       status, message, requested_by, created_at, executed_at
                from scada_control_command
                where id = ?
                """, (rs, rowNum) -> mapCommand(rs), id);
    }

    private ControlPoint loadPoint(Long pointId) {
        List<ControlPoint> rows = jdbcTemplate.query("""
                select p.id as point_id, p.code as point_code, p.name as point_name, p.data_type, p.address,
                       p.access_mode, p.modbus_type, d.id as device_id, d.name as device_name,
                       coalesce(nullif(c.host, ''), nullif(d.ip_address, ''), '127.0.0.1') as host,
                       coalesce(c.port, d.port, 1502) as port
                from scada_point p
                join scada_device d on d.id = p.device_id
                left join scada_collect_channel c on c.device_id = d.id and c.enabled = 1
                where p.id = ?
                limit 1
                """, (rs, rowNum) -> new ControlPoint(
                rs.getLong("point_id"),
                rs.getString("point_code"),
                rs.getString("point_name"),
                rs.getString("data_type"),
                rs.getString("address"),
                rs.getString("access_mode"),
                rs.getString("modbus_type"),
                rs.getLong("device_id"),
                rs.getString("device_name"),
                rs.getString("host"),
                rs.getInt("port")
        ), pointId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "点位不存在");
        }
        return rows.getFirst();
    }

    private void validateWritable(ControlPoint point) {
        if ("R".equalsIgnoreCase(point.accessMode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该点位是只读点，不能下发控制");
        }
        String modbusType = point.modbusType() == null ? "" : point.modbusType().trim();
        if (!"0".equals(modbusType) && !"4".equals(modbusType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 Modbus 0 区线圈和 4 区保持寄存器写入");
        }
    }

    private String currentUsername() {
        AuthUser user = CurrentUserHolder.user();
        return user == null ? "system" : user.username();
    }

    private String trimMessage(String message) {
        if (message == null || message.isBlank()) {
            return "写入失败";
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private ControlCommandResponse mapCommand(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp executed = rs.getTimestamp("executed_at");
        return new ControlCommandResponse(
                rs.getLong("id"),
                rs.getString("command_no"),
                rs.getLong("device_id"),
                rs.getString("device_name"),
                rs.getLong("point_id"),
                rs.getString("point_code"),
                rs.getString("point_name"),
                rs.getBigDecimal("target_value"),
                rs.getString("status"),
                rs.getString("message"),
                rs.getString("requested_by"),
                rs.getTimestamp("created_at").toInstant(),
                executed == null ? null : executed.toInstant()
        );
    }

    private record ControlPoint(Long pointId, String pointCode, String pointName, String dataType, String address,
                                String accessMode, String modbusType, Long deviceId, String deviceName,
                                String host, Integer port) {
    }
}
