package com.example.scada.system;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.scada.auth.PasswordService;

@Component
public class DatabaseInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate, PasswordService passwordService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
    }

    @Override
    public void run(ApplicationArguments args) {
        createSystemTables();
        createScadaTables();
        seedRole();
        seedMenus();
        seedAdminUser();
        linkAdminPermissions();
        seedScadaCatalog();
    }

    private void createSystemTables() {
        jdbcTemplate.execute("""
                create table if not exists sys_user (
                    id bigint primary key auto_increment,
                    username varchar(64) not null unique,
                    display_name varchar(128) not null,
                    password_hash varchar(255) not null,
                    enabled tinyint not null default 1,
                    created_at timestamp not null default current_timestamp
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists sys_role (
                    id bigint primary key auto_increment,
                    role_key varchar(64) not null unique,
                    name varchar(128) not null,
                    created_at timestamp not null default current_timestamp
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists sys_menu (
                    id bigint primary key auto_increment,
                    menu_key varchar(64) not null unique,
                    label varchar(64) not null,
                    helper varchar(128) not null,
                    icon varchar(16) not null,
                    sort_order int not null,
                    enabled tinyint not null default 1
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists sys_user_role (
                    user_id bigint not null,
                    role_id bigint not null,
                    primary key (user_id, role_id)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists sys_role_menu (
                    role_id bigint not null,
                    menu_id bigint not null,
                    primary key (role_id, menu_id)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists sys_audit_log (
                    id bigint primary key auto_increment,
                    actor varchar(64) not null,
                    action varchar(64) not null,
                    detail varchar(255) not null,
                    created_at timestamp not null default current_timestamp
                )
                """);
    }

    private void createScadaTables() {
        jdbcTemplate.execute("""
                create table if not exists scada_area (
                    id bigint primary key auto_increment,
                    name varchar(128) not null,
                    code varchar(64) not null unique,
                    description varchar(255) not null default '',
                    created_at timestamp not null default current_timestamp
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists scada_device (
                    id bigint primary key auto_increment,
                    area_id bigint not null,
                    name varchar(128) not null,
                    code varchar(64) not null unique,
                    type varchar(64) not null,
                    status varchar(32) not null,
                    protocol varchar(32) not null,
                    ip_address varchar(64) not null default '',
                    port int null,
                    description varchar(255) not null default '',
                    created_at timestamp not null default current_timestamp,
                    updated_at timestamp not null default current_timestamp on update current_timestamp,
                    index idx_scada_device_area(area_id),
                    index idx_scada_device_status(status)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists scada_point (
                    id bigint primary key auto_increment,
                    device_id bigint not null,
                    name varchar(128) not null,
                    code varchar(64) not null,
                    data_type varchar(32) not null,
                    unit varchar(32) not null default '',
                    address varchar(128) not null,
                    access_mode varchar(16) not null,
                    scale_value decimal(12,4) not null default 1,
                    sort_order int not null default 0,
                    created_at timestamp not null default current_timestamp,
                    unique key uk_scada_point_device_code(device_id, code),
                    index idx_scada_point_device(device_id)
                )
                """);
    }

    private void seedRole() {
        jdbcTemplate.update("insert ignore into sys_role(role_key, name) values (?, ?)", "admin", "系统管理员");
    }

    private void seedMenus() {
        List<MenuSeed> menus = List.of(
                new MenuSeed("overview", "首页总览", "运行态势", "⌁", 10),
                new MenuSeed("devices", "设备管理", "站点与控制柜", "▦", 20),
                new MenuSeed("monitor", "实时监控", "采集点位", "◌", 30),
                new MenuSeed("alarms", "报警中心", "待确认事件", "!", 40),
                new MenuSeed("history", "历史数据", "趋势与报表", "∿", 50),
                new MenuSeed("hmi", "组态画面", "工艺流程", "⌗", 60),
                new MenuSeed("users", "用户与权限", "角色策略", "◎", 70),
                new MenuSeed("settings", "系统设置", "运行参数", "⚙", 80)
        );
        for (MenuSeed menu : menus) {
            jdbcTemplate.update("""
                    insert into sys_menu(menu_key, label, helper, icon, sort_order, enabled)
                    values (?, ?, ?, ?, ?, 1)
                    on duplicate key update label = values(label), helper = values(helper), icon = values(icon), sort_order = values(sort_order), enabled = 1
                    """, menu.key(), menu.label(), menu.helper(), menu.icon(), menu.sortOrder());
        }
    }

    private void seedAdminUser() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from sys_user where username = ?", Integer.class, "admin");
        if (count != null && count == 0) {
            jdbcTemplate.update("""
                    insert into sys_user(username, display_name, password_hash, enabled)
                    values (?, ?, ?, 1)
                    """, "admin", "系统管理员", passwordService.hash("admin"));
        }
    }

    private void linkAdminPermissions() {
        Long adminRoleId = jdbcTemplate.queryForObject("select id from sys_role where role_key = ?", Long.class, "admin");
        Long adminUserId = jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, "admin");
        jdbcTemplate.update("insert ignore into sys_user_role(user_id, role_id) values (?, ?)", adminUserId, adminRoleId);
        jdbcTemplate.update("""
                insert ignore into sys_role_menu(role_id, menu_id)
                select ?, id from sys_menu where enabled = 1
                """, adminRoleId);
    }

    private void seedScadaCatalog() {
        seedAreas();
        seedDevices();
        seedPoints();
    }

    private void seedAreas() {
        List<AreaSeed> areas = List.of(
                new AreaSeed("供水一区", "AREA-WATER-01", "市政供水与二次加压站点"),
                new AreaSeed("能源中心", "AREA-ENERGY", "换热、能耗与循环系统"),
                new AreaSeed("动力车间", "AREA-POWER", "空压、配电与动力设备"),
                new AreaSeed("环保站", "AREA-ENV", "污水与环保治理站点")
        );
        for (AreaSeed area : areas) {
            jdbcTemplate.update("""
                    insert into scada_area(name, code, description)
                    values (?, ?, ?)
                    on duplicate key update name = values(name), description = values(description)
                    """, area.name(), area.code(), area.description());
        }
    }

    private void seedDevices() {
        List<DeviceSeed> devices = List.of(
                new DeviceSeed(areaId("AREA-WATER-01"), "一号加压泵站", "DEV-PUMP-001", "泵站", "运行", "MODBUS_TCP", "127.0.0.1", 1502, "供水一区主加压泵站"),
                new DeviceSeed(areaId("AREA-ENERGY"), "二号换热机组", "DEV-HEAT-002", "换热机组", "待机", "MODBUS_TCP", "127.0.0.1", 1503, "能源中心二号换热单元"),
                new DeviceSeed(areaId("AREA-POWER"), "空压站 A 线", "DEV-AIR-A", "空压机", "运行", "MQTT", "127.0.0.1", 1883, "动力车间空压 A 线"),
                new DeviceSeed(areaId("AREA-ENV"), "污水提升井", "DEV-WASTE-LIFT", "提升井", "告警", "MODBUS_TCP", "127.0.0.1", 1504, "环保站污水提升与液位监测")
        );
        for (DeviceSeed device : devices) {
            jdbcTemplate.update("""
                    insert into scada_device(area_id, name, code, type, status, protocol, ip_address, port, description)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on duplicate key update area_id = values(area_id), name = values(name), type = values(type), status = values(status),
                        protocol = values(protocol), ip_address = values(ip_address), port = values(port), description = values(description)
                    """, device.areaId(), device.name(), device.code(), device.type(), device.status(), device.protocol(), device.ipAddress(), device.port(), device.description());
        }
    }

    private void seedPoints() {
        seedDevicePoints("DEV-PUMP-001", List.of(
                new PointSeed("出口压力", "OUT_PRESSURE", "DECIMAL", "MPa", "40001", "R", 1.0, 10),
                new PointSeed("泵组频率", "PUMP_FREQ", "DECIMAL", "Hz", "40002", "R", 1.0, 20),
                new PointSeed("运行状态", "RUN_STATE", "BOOLEAN", "", "00001", "R", 1.0, 30)
        ));
        seedDevicePoints("DEV-HEAT-002", List.of(
                new PointSeed("供水温度", "SUPPLY_TEMP", "DECIMAL", "C", "40011", "R", 1.0, 10),
                new PointSeed("回水温度", "RETURN_TEMP", "DECIMAL", "C", "40012", "R", 1.0, 20),
                new PointSeed("循环泵状态", "PUMP_STATE", "BOOLEAN", "", "00011", "R", 1.0, 30)
        ));
        seedDevicePoints("DEV-AIR-A", List.of(
                new PointSeed("出口压力", "AIR_PRESSURE", "DECIMAL", "MPa", "air/a/pressure", "R", 1.0, 10),
                new PointSeed("电机电流", "MOTOR_CURRENT", "DECIMAL", "A", "air/a/current", "R", 1.0, 20),
                new PointSeed("加载状态", "LOAD_STATE", "BOOLEAN", "", "air/a/load", "R", 1.0, 30)
        ));
        seedDevicePoints("DEV-WASTE-LIFT", List.of(
                new PointSeed("井内液位", "WELL_LEVEL", "DECIMAL", "m", "40021", "R", 1.0, 10),
                new PointSeed("高高液位报警", "HH_LEVEL_ALARM", "BOOLEAN", "", "00021", "R", 1.0, 20),
                new PointSeed("提升泵状态", "LIFT_PUMP_STATE", "BOOLEAN", "", "00022", "R", 1.0, 30)
        ));
    }

    private void seedDevicePoints(String deviceCode, List<PointSeed> points) {
        Long deviceId = deviceId(deviceCode);
        for (PointSeed point : points) {
            jdbcTemplate.update("""
                    insert into scada_point(device_id, name, code, data_type, unit, address, access_mode, scale_value, sort_order)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on duplicate key update name = values(name), data_type = values(data_type), unit = values(unit), address = values(address),
                        access_mode = values(access_mode), scale_value = values(scale_value), sort_order = values(sort_order)
                    """, deviceId, point.name(), point.code(), point.dataType(), point.unit(), point.address(), point.accessMode(), point.scaleValue(), point.sortOrder());
        }
    }

    private Long areaId(String code) {
        return jdbcTemplate.queryForObject("select id from scada_area where code = ?", Long.class, code);
    }

    private Long deviceId(String code) {
        return jdbcTemplate.queryForObject("select id from scada_device where code = ?", Long.class, code);
    }

    private record MenuSeed(String key, String label, String helper, String icon, int sortOrder) {
    }

    private record AreaSeed(String name, String code, String description) {
    }

    private record DeviceSeed(Long areaId, String name, String code, String type, String status, String protocol, String ipAddress, Integer port, String description) {
    }

    private record PointSeed(String name, String code, String dataType, String unit, String address, String accessMode, Double scaleValue, Integer sortOrder) {
    }
}
