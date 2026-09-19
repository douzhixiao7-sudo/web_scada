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
        seedDictionaries();
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
        jdbcTemplate.execute("""
                create table if not exists sys_dict_type (
                    id bigint primary key auto_increment,
                    type_code varchar(64) not null unique,
                    name varchar(128) not null,
                    description varchar(255) not null default '',
                    sort_order int not null default 0,
                    enabled tinyint not null default 1,
                    created_at timestamp not null default current_timestamp
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists sys_dict_item (
                    id bigint primary key auto_increment,
                    type_id bigint not null,
                    item_code varchar(64) not null,
                    label varchar(128) not null,
                    description varchar(255) not null default '',
                    sort_order int not null default 0,
                    enabled tinyint not null default 1,
                    created_at timestamp not null default current_timestamp,
                    unique key uk_sys_dict_item_type_code(type_id, item_code),
                    index idx_sys_dict_item_type(type_id)
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

    private void seedDictionaries() {
        seedDictionaryType("device_status", "设备状态", "设备台账运行状态", 10);
        seedDictionaryType("device_protocol", "通讯协议", "设备通讯协议类型", 20);
        seedDictionaryType("device_type", "设备类型", "独立设备分类", 30);
        seedDictionaryType("point_data_type", "点位数据类型", "采集点位值类型", 40);
        seedDictionaryType("point_access_mode", "点位读写属性", "采集点位读写能力", 50);
        seedDictionaryType("point_unit", "点位单位", "常用工程单位", 60);

        seedDictionaryItems("device_status", List.of(
                new DictItemSeed("运行", "运行", "设备处于运行态", 10),
                new DictItemSeed("待机", "待机", "设备可用但未运行", 20),
                new DictItemSeed("告警", "告警", "设备存在报警或异常", 30),
                new DictItemSeed("离线", "离线", "设备通讯不可用", 40)
        ));
        seedDictionaryItems("device_protocol", List.of(
                new DictItemSeed("MODBUS_TCP", "Modbus TCP", "TCP 模式 Modbus 协议", 10),
                new DictItemSeed("MQTT", "MQTT", "消息订阅发布协议", 20),
                new DictItemSeed("OPC_UA", "OPC UA", "OPC UA 工业互联协议", 30),
                new DictItemSeed("HTTP", "HTTP", "HTTP 接口采集", 40)
        ));
        seedDictionaryItems("device_type", List.of(
                new DictItemSeed("闸门", "闸门", "闸门或阀门执行设备", 10),
                new DictItemSeed("水泵", "水泵", "泵类动力设备", 20),
                new DictItemSeed("仪表", "仪表", "压力、液位、流量等仪表", 30),
                new DictItemSeed("PLC", "PLC", "控制器或远程 IO", 40),
                new DictItemSeed("网关", "网关", "协议网关或边缘采集器", 50),
                new DictItemSeed("变频器", "变频器", "变频驱动设备", 60)
        ));
        seedDictionaryItems("point_data_type", List.of(
                new DictItemSeed("DECIMAL", "小数", "浮点或定点数值", 10),
                new DictItemSeed("INTEGER", "整数", "整数数值", 20),
                new DictItemSeed("BOOLEAN", "布尔", "开关量或状态量", 30),
                new DictItemSeed("STRING", "字符串", "文本值", 40)
        ));
        seedDictionaryItems("point_access_mode", List.of(
                new DictItemSeed("R", "只读", "采集读取", 10),
                new DictItemSeed("W", "只写", "控制写入", 20),
                new DictItemSeed("RW", "读写", "可读可写", 30)
        ));
        seedDictionaryItems("point_unit", List.of(
                new DictItemSeed("%", "%", "百分比", 10),
                new DictItemSeed("MPa", "MPa", "压力", 20),
                new DictItemSeed("Hz", "Hz", "频率", 30),
                new DictItemSeed("A", "A", "电流", 40),
                new DictItemSeed("m", "m", "长度或液位", 50),
                new DictItemSeed("C", "℃", "温度", 60),
                new DictItemSeed("", "无单位", "无工程单位", 70)
        ));
    }

    private void seedDictionaryType(String typeCode, String name, String description, int sortOrder) {
        jdbcTemplate.update("""
                insert into sys_dict_type(type_code, name, description, sort_order, enabled)
                values (?, ?, ?, ?, 1)
                on duplicate key update name = values(name), description = values(description), sort_order = values(sort_order), enabled = 1
                """, typeCode, name, description, sortOrder);
    }

    private void seedDictionaryItems(String typeCode, List<DictItemSeed> items) {
        Long typeId = jdbcTemplate.queryForObject("select id from sys_dict_type where type_code = ?", Long.class, typeCode);
        for (DictItemSeed item : items) {
            jdbcTemplate.update("""
                    insert into sys_dict_item(type_id, item_code, label, description, sort_order, enabled)
                    values (?, ?, ?, ?, ?, 1)
                    on duplicate key update label = values(label), description = values(description), sort_order = values(sort_order), enabled = 1
                    """, typeId, item.code(), item.label(), item.description(), item.sortOrder());
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
        cleanupLegacyDemoPoints();
        seedPoints();
    }

    private void seedAreas() {
        List<AreaSeed> areas = List.of(
                new AreaSeed("进水闸门区", "AREA-WATER-01", "进水闸门与液位监测区域"),
                new AreaSeed("泵房一区", "AREA-ENERGY", "加压泵与出口仪表区域"),
                new AreaSeed("仪表间", "AREA-POWER", "独立仪表与网关设备"),
                new AreaSeed("配电室", "AREA-ENV", "配电、变频与控制设备")
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
                new DeviceSeed(areaId("AREA-WATER-01"), "1#进水闸门", "DEV-PUMP-001", "闸门", "运行", "MODBUS_TCP", "127.0.0.1", 1502, "独立闸门执行设备，挂载开度与到位反馈点位"),
                new DeviceSeed(areaId("AREA-WATER-01"), "2#进水闸门", "DEV-HEAT-002", "闸门", "待机", "MODBUS_TCP", "127.0.0.1", 1503, "独立闸门执行设备，挂载开关到位与故障点位"),
                new DeviceSeed(areaId("AREA-ENERGY"), "1#加压泵", "DEV-AIR-A", "水泵", "运行", "MQTT", "127.0.0.1", 1883, "独立泵设备，挂载频率、电流和运行状态点位"),
                new DeviceSeed(areaId("AREA-POWER"), "出口压力变送器", "DEV-WASTE-LIFT", "仪表", "告警", "MODBUS_TCP", "127.0.0.1", 1504, "独立压力仪表，挂载压力值和报警状态点位")
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
                new PointSeed("开度反馈", "OPENING_FEEDBACK", "DECIMAL", "%", "40001", "R", 1.0, 10),
                new PointSeed("开到位", "OPEN_LIMIT", "BOOLEAN", "", "00001", "R", 1.0, 20),
                new PointSeed("关到位", "CLOSE_LIMIT", "BOOLEAN", "", "00002", "R", 1.0, 30)
        ));
        seedDevicePoints("DEV-HEAT-002", List.of(
                new PointSeed("开度反馈", "OPENING_FEEDBACK", "DECIMAL", "%", "40011", "R", 1.0, 10),
                new PointSeed("远程允许", "REMOTE_ENABLE", "BOOLEAN", "", "00011", "R", 1.0, 20),
                new PointSeed("故障状态", "FAULT_STATE", "BOOLEAN", "", "00012", "R", 1.0, 30)
        ));
        seedDevicePoints("DEV-AIR-A", List.of(
                new PointSeed("频率反馈", "FREQ_FEEDBACK", "DECIMAL", "Hz", "pump/1/frequency", "R", 1.0, 10),
                new PointSeed("电机电流", "MOTOR_CURRENT", "DECIMAL", "A", "pump/1/current", "R", 1.0, 20),
                new PointSeed("运行状态", "RUN_STATE", "BOOLEAN", "", "pump/1/run", "R", 1.0, 30)
        ));
        seedDevicePoints("DEV-WASTE-LIFT", List.of(
                new PointSeed("压力值", "PRESSURE_VALUE", "DECIMAL", "MPa", "40021", "R", 1.0, 10),
                new PointSeed("高压报警", "HIGH_PRESSURE_ALARM", "BOOLEAN", "", "00021", "R", 1.0, 20),
                new PointSeed("通讯状态", "COMM_STATE", "BOOLEAN", "", "00022", "R", 1.0, 30)
        ));
    }

    private void cleanupLegacyDemoPoints() {
        List<String> oldCodes = List.of(
                "OUT_PRESSURE", "PUMP_FREQ", "SUPPLY_TEMP", "RETURN_TEMP", "PUMP_STATE",
                "AIR_PRESSURE", "MOTOR_CURRENT", "LOAD_STATE", "RUN_STATE",
                "WELL_LEVEL", "HH_LEVEL_ALARM", "LIFT_PUMP_STATE"
        );
        for (String code : oldCodes) {
            jdbcTemplate.update("delete from scada_point where code = ?", code);
        }
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

    private record DictItemSeed(String code, String label, String description, int sortOrder) {
    }

    private record AreaSeed(String name, String code, String description) {
    }

    private record DeviceSeed(Long areaId, String name, String code, String type, String status, String protocol, String ipAddress, Integer port, String description) {
    }

    private record PointSeed(String name, String code, String dataType, String unit, String address, String accessMode, Double scaleValue, Integer sortOrder) {
    }
}
