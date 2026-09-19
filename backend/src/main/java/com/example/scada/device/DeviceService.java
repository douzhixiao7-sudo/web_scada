package com.example.scada.device;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {
    private final JdbcTemplate jdbcTemplate;

    public DeviceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AreaResponse> listAreas() {
        return jdbcTemplate.query("""
                select id, name, code, description
                from scada_area
                order by id
                """, (rs, rowNum) -> new AreaResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("description")
        ));
    }

    public List<DeviceResponse> listDevices(Long areaId, String status) {
        StringBuilder sql = new StringBuilder("""
                select d.id, d.area_id, a.name as area_name, d.name, d.code, d.type, d.status,
                       d.protocol, d.ip_address, d.port, d.description,
                       count(p.id) as point_count
                from scada_device d
                join scada_area a on a.id = d.area_id
                left join scada_point p on p.device_id = d.id
                where 1 = 1
                """);
        ArrayList<Object> params = new ArrayList<>();
        if (areaId != null) {
            sql.append(" and d.area_id = ?");
            params.add(areaId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and d.status = ?");
            params.add(status.trim());
        }
        sql.append(" group by d.id, d.area_id, a.name, d.name, d.code, d.type, d.status, d.protocol, d.ip_address, d.port, d.description order by d.id");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapDevice(rs), params.toArray());
    }

    public DeviceResponse getDevice(Long id) {
        List<DeviceResponse> devices = jdbcTemplate.query("""
                select d.id, d.area_id, a.name as area_name, d.name, d.code, d.type, d.status,
                       d.protocol, d.ip_address, d.port, d.description,
                       count(p.id) as point_count
                from scada_device d
                join scada_area a on a.id = d.area_id
                left join scada_point p on p.device_id = d.id
                where d.id = ?
                group by d.id, d.area_id, a.name, d.name, d.code, d.type, d.status, d.protocol, d.ip_address, d.port, d.description
                """, (rs, rowNum) -> mapDevice(rs), id);
        if (devices.isEmpty()) {
            throw new IllegalArgumentException("设备不存在");
        }
        return devices.getFirst();
    }

    public List<PointResponse> listPoints(Long deviceId) {
        ensureDeviceExists(deviceId);
        return jdbcTemplate.query("""
                select id, device_id, name, code, data_type, unit, address, access_mode, scale_value, sort_order,
                       source_group, source_sheet, io_module, io_type, modbus_type, sixnet_address, iconics_path, remark
                from scada_point
                where device_id = ?
                order by sort_order, id
                """, (rs, rowNum) -> new PointResponse(
                rs.getLong("id"),
                rs.getLong("device_id"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("data_type"),
                rs.getString("unit"),
                rs.getString("address"),
                rs.getString("access_mode"),
                rs.getDouble("scale_value"),
                rs.getInt("sort_order"),
                rs.getString("source_group"),
                rs.getString("source_sheet"),
                rs.getString("io_module"),
                rs.getString("io_type"),
                rs.getString("modbus_type"),
                rs.getString("sixnet_address"),
                rs.getString("iconics_path"),
                rs.getString("remark")
        ), deviceId);
    }

    @Transactional
    public PointResponse createPoint(Long deviceId, PointRequest request) {
        ensureDeviceExists(deviceId);
        validatePoint(request);
        jdbcTemplate.update("""
                insert into scada_point(device_id, name, code, data_type, unit, address, access_mode, scale_value, sort_order)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, deviceId, clean(request.name()), clean(request.code()), clean(request.dataType()), clean(request.unit()),
                clean(request.address()), clean(request.accessMode()), scaleValue(request), sortOrder(request));
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return getPoint(deviceId, id);
    }

    @Transactional
    public PointResponse updatePoint(Long deviceId, Long pointId, PointRequest request) {
        ensureDeviceExists(deviceId);
        validatePoint(request);
        int updated = jdbcTemplate.update("""
                update scada_point
                set name = ?, code = ?, data_type = ?, unit = ?, address = ?, access_mode = ?, scale_value = ?, sort_order = ?
                where id = ? and device_id = ?
                """, clean(request.name()), clean(request.code()), clean(request.dataType()), clean(request.unit()),
                clean(request.address()), clean(request.accessMode()), scaleValue(request), sortOrder(request), pointId, deviceId);
        if (updated == 0) {
            throw new IllegalArgumentException("点位不存在");
        }
        return getPoint(deviceId, pointId);
    }

    @Transactional
    public void deletePoint(Long deviceId, Long pointId) {
        ensureDeviceExists(deviceId);
        int deleted = jdbcTemplate.update("delete from scada_point where id = ? and device_id = ?", pointId, deviceId);
        if (deleted == 0) {
            throw new IllegalArgumentException("点位不存在");
        }
    }

    @Transactional
    public DeviceResponse createDevice(DeviceRequest request) {
        validate(request);
        jdbcTemplate.update("""
                insert into scada_device(area_id, name, code, type, status, protocol, ip_address, port, description)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, request.areaId(), clean(request.name()), clean(request.code()), clean(request.type()), clean(request.status()),
                clean(request.protocol()), clean(request.ipAddress()), request.port(), clean(request.description()));
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return getDevice(id);
    }

    @Transactional
    public DeviceResponse updateDevice(Long id, DeviceRequest request) {
        validate(request);
        int updated = jdbcTemplate.update("""
                update scada_device
                set area_id = ?, name = ?, code = ?, type = ?, status = ?, protocol = ?, ip_address = ?, port = ?, description = ?
                where id = ?
                """, request.areaId(), clean(request.name()), clean(request.code()), clean(request.type()), clean(request.status()),
                clean(request.protocol()), clean(request.ipAddress()), request.port(), clean(request.description()), id);
        if (updated == 0) {
            throw new IllegalArgumentException("设备不存在");
        }
        return getDevice(id);
    }

    @Transactional
    public void deleteDevice(Long id) {
        jdbcTemplate.update("delete from scada_point where device_id = ?", id);
        int deleted = jdbcTemplate.update("delete from scada_device where id = ?", id);
        if (deleted == 0) {
            throw new IllegalArgumentException("设备不存在");
        }
    }

    private DeviceResponse mapDevice(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DeviceResponse(
                rs.getLong("id"),
                rs.getLong("area_id"),
                rs.getString("area_name"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("type"),
                rs.getString("status"),
                rs.getString("protocol"),
                rs.getString("ip_address"),
                (Integer) rs.getObject("port"),
                rs.getString("description"),
                rs.getInt("point_count")
        );
    }

    private PointResponse getPoint(Long deviceId, Long pointId) {
        List<PointResponse> points = jdbcTemplate.query("""
                select id, device_id, name, code, data_type, unit, address, access_mode, scale_value, sort_order,
                       source_group, source_sheet, io_module, io_type, modbus_type, sixnet_address, iconics_path, remark
                from scada_point
                where id = ? and device_id = ?
                """, (rs, rowNum) -> new PointResponse(
                rs.getLong("id"),
                rs.getLong("device_id"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("data_type"),
                rs.getString("unit"),
                rs.getString("address"),
                rs.getString("access_mode"),
                rs.getDouble("scale_value"),
                rs.getInt("sort_order"),
                rs.getString("source_group"),
                rs.getString("source_sheet"),
                rs.getString("io_module"),
                rs.getString("io_type"),
                rs.getString("modbus_type"),
                rs.getString("sixnet_address"),
                rs.getString("iconics_path"),
                rs.getString("remark")
        ), pointId, deviceId);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("点位不存在");
        }
        return points.getFirst();
    }

    private void validate(DeviceRequest request) {
        if (request.areaId() == null) {
            throw new IllegalArgumentException("请选择区域");
        }
        if (isBlank(request.name()) || isBlank(request.code()) || isBlank(request.type()) || isBlank(request.status()) || isBlank(request.protocol())) {
            throw new IllegalArgumentException("设备名称、编码、类型、状态和协议不能为空");
        }
        Integer areaCount = jdbcTemplate.queryForObject("select count(*) from scada_area where id = ?", Integer.class, request.areaId());
        if (areaCount == null || areaCount == 0) {
            throw new IllegalArgumentException("区域不存在");
        }
    }

    private void validatePoint(PointRequest request) {
        if (request == null || isBlank(request.name()) || isBlank(request.code()) || isBlank(request.dataType())
                || isBlank(request.address()) || isBlank(request.accessMode())) {
            throw new IllegalArgumentException("点位名称、编码、数据类型、采集地址和读写属性不能为空");
        }
    }

    private void ensureDeviceExists(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("请选择设备");
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from scada_device where id = ?", Integer.class, deviceId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("设备不存在");
        }
    }

    private double scaleValue(PointRequest request) {
        return request.scaleValue() == null ? 1.0 : request.scaleValue();
    }

    private int sortOrder(PointRequest request) {
        return request.sortOrder() == null ? 0 : request.sortOrder();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

