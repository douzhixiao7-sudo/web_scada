package com.example.scada.system;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        jdbcTemplate.execute("""
                create table if not exists scada_alarm_event (
                    id bigint primary key auto_increment,
                    alarm_key varchar(128) not null unique,
                    device_id bigint not null,
                    device_name varchar(128) not null,
                    point_id bigint not null,
                    point_code varchar(64) not null,
                    point_name varchar(128) not null,
                    level varchar(16) not null,
                    message varchar(128) not null,
                    value varchar(64) not null,
                    quality varchar(32) not null,
                    status varchar(32) not null,
                    occurred_at timestamp not null,
                    last_seen_at timestamp not null,
                    recovered_at timestamp null,
                    acknowledged_at timestamp null,
                    acknowledged_by varchar(64) not null default '',
                    ack_note varchar(255) not null default '',
                    created_at timestamp not null default current_timestamp,
                    updated_at timestamp not null default current_timestamp on update current_timestamp,
                    index idx_scada_alarm_status(status),
                    index idx_scada_alarm_device(device_id),
                    index idx_scada_alarm_point(point_id)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists scada_alarm_rule (
                    id bigint primary key auto_increment,
                    point_id bigint not null,
                    point_code varchar(64) not null,
                    point_name varchar(128) not null,
                    rule_name varchar(128) not null,
                    rule_type varchar(32) not null,
                    operator varchar(16) not null default '',
                    threshold_value decimal(14,4) null,
                    level varchar(16) not null,
                    message varchar(128) not null,
                    enabled tinyint not null default 1,
                    created_at timestamp not null default current_timestamp,
                    updated_at timestamp not null default current_timestamp on update current_timestamp,
                    unique key uk_scada_alarm_rule_point_type_msg(point_id, rule_type, message),
                    index idx_scada_alarm_rule_point(point_id),
                    index idx_scada_alarm_rule_enabled(enabled)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists scada_collect_channel (
                    id bigint primary key auto_increment,
                    device_id bigint not null,
                    name varchar(128) not null,
                    code varchar(64) not null unique,
                    protocol varchar(32) not null,
                    host varchar(64) not null default '',
                    port int null,
                    poll_interval_ms int not null default 1000,
                    enabled tinyint not null default 1,
                    status varchar(32) not null default 'READY',
                    last_polled_at timestamp null,
                    created_at timestamp not null default current_timestamp,
                    updated_at timestamp not null default current_timestamp on update current_timestamp,
                    unique key uk_scada_collect_channel_device(device_id),
                    index idx_scada_collect_channel_enabled(enabled),
                    index idx_scada_collect_channel_status(status)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists scada_collect_binding (
                    id bigint primary key auto_increment,
                    channel_id bigint not null,
                    point_id bigint not null,
                    enabled tinyint not null default 1,
                    created_at timestamp not null default current_timestamp,
                    updated_at timestamp not null default current_timestamp on update current_timestamp,
                    unique key uk_scada_collect_binding_channel_point(channel_id, point_id),
                    index idx_scada_collect_binding_channel(channel_id),
                    index idx_scada_collect_binding_point(point_id),
                    index idx_scada_collect_binding_enabled(enabled)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists scada_control_command (
                    id bigint primary key auto_increment,
                    command_no varchar(64) not null unique,
                    device_id bigint not null,
                    device_name varchar(128) not null,
                    point_id bigint not null,
                    point_code varchar(64) not null,
                    point_name varchar(128) not null,
                    target_value decimal(14,4) not null,
                    status varchar(32) not null,
                    message varchar(255) not null default '',
                    requested_by varchar(64) not null default '',
                    created_at timestamp not null default current_timestamp,
                    executed_at timestamp null,
                    updated_at timestamp not null default current_timestamp on update current_timestamp,
                    index idx_scada_control_command_device(device_id),
                    index idx_scada_control_command_point(point_id),
                    index idx_scada_control_command_status(status)
                )
                """);
        addColumnIfMissing("scada_point", "source_group", "varchar(64) not null default ''");
        addColumnIfMissing("scada_point", "source_sheet", "varchar(128) not null default ''");
        addColumnIfMissing("scada_point", "io_module", "varchar(64) not null default ''");
        addColumnIfMissing("scada_point", "io_type", "varchar(32) not null default ''");
        addColumnIfMissing("scada_point", "modbus_type", "varchar(32) not null default ''");
        addColumnIfMissing("scada_point", "sixnet_address", "varchar(64) not null default ''");
        addColumnIfMissing("scada_point", "iconics_path", "varchar(255) not null default ''");
        addColumnIfMissing("scada_point", "remark", "varchar(255) not null default ''");
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = database() and table_name = ? and column_name = ?
                """, Integer.class, tableName, columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute("alter table " + tableName + " add column " + columnName + " " + definition);
        }
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
        seedDictionaryType("modbus_area", "Modbus 数据区", "0/1/3/4 区语义", 70);

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
        seedDictionaryItems("modbus_area", List.of(
                new DictItemSeed("0", "0区 Coil", "线圈，可读写，常用于控制输出 DO", 10),
                new DictItemSeed("1", "1区 Discrete Input", "离散输入，只读，常用于状态输入 DI", 20),
                new DictItemSeed("3", "3区 Input Register", "输入寄存器，只读，常用于 AI/测量值", 30),
                new DictItemSeed("4", "4区 Holding Register", "保持寄存器，可读写，常用于 AO/设定值", 40)
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
        cleanupLegacyDemoPoints();
        cleanupLegacyDemoDevices();
        seedJindouhePointTable();
        seedAlarmRules();
        seedCollectChannels();
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

    private void cleanupLegacyDemoDevices() {
        List<String> oldCodes = List.of("DEV-PUMP-001", "DEV-HEAT-002", "DEV-AIR-A", "DEV-WASTE-LIFT");
        for (String code : oldCodes) {
            Long id = jdbcTemplate.query("select id from scada_device where code = ?", rs -> rs.next() ? rs.getLong("id") : null, code);
            if (id != null) {
                jdbcTemplate.update("delete from scada_point where device_id = ?", id);
                jdbcTemplate.update("delete from scada_device where id = ?", id);
            }
        }
    }

    private void seedJindouhePointTable() {
        InputStream input = getClass().getResourceAsStream("/jindouhe_points.csv");
        if (input == null) {
            return;
        }
        Map<String, JindouheAreaSeed> areas = new LinkedHashMap<>();
        Map<String, JindouheDeviceSeed> devices = new LinkedHashMap<>();
        List<JindouhePointSeed> points = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                if (values.length < 21) {
                    continue;
                }
                JindouhePointSeed point = new JindouhePointSeed(
                        values[0], values[1], values[2], values[3], values[4], values[5], values[6],
                        values[7], values[8], values[9], values[10], values[11], values[12], parseDouble(values[13]),
                        parseInt(values[14]), values[15], values[16], values[17], values[18], values[19], values[20]
                );
                areas.putIfAbsent(point.areaCode(), new JindouheAreaSeed(point.areaName(), point.areaCode(), "来自金斗河现场自动化点表"));
                devices.putIfAbsent(point.deviceCode(), new JindouheDeviceSeed(point.deviceName(), point.deviceCode(), point.deviceType(), point.areaCode(), point.sourceGroup()));
                points.add(point);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("金斗河现场点表初始化失败", ex);
        }
        for (JindouheAreaSeed area : areas.values()) {
            jdbcTemplate.update("""
                    insert into scada_area(name, code, description)
                    values (?, ?, ?)
                    on duplicate key update name = values(name), description = values(description)
                    """, area.name(), area.code(), area.description());
        }
        for (JindouheDeviceSeed device : devices.values()) {
            jdbcTemplate.update("""
                    insert into scada_device(area_id, name, code, type, status, protocol, ip_address, port, description)
                    values (?, ?, ?, ?, '运行', 'MODBUS_TCP', '127.0.0.1', 1502, ?)
                    on duplicate key update area_id = values(area_id), name = values(name), type = values(type), protocol = values(protocol),
                        ip_address = values(ip_address), port = values(port), description = values(description)
                    """, areaId(device.areaCode()), device.name(), device.code(), device.type(), "来源 LCU：" + device.sourceGroup());
        }
        for (JindouhePointSeed point : points) {
            seedJindouhePoint(point);
        }
    }

    private void seedJindouhePoint(JindouhePointSeed point) {
        jdbcTemplate.update("""
                insert into scada_point(device_id, name, code, data_type, unit, address, access_mode, scale_value, sort_order,
                    source_group, source_sheet, io_module, io_type, modbus_type, sixnet_address, iconics_path, remark)
                values ((select id from scada_device where code = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update name = values(name), data_type = values(data_type), unit = values(unit), address = values(address),
                    access_mode = values(access_mode), scale_value = values(scale_value), sort_order = values(sort_order),
                    source_group = values(source_group), source_sheet = values(source_sheet), io_module = values(io_module),
                    io_type = values(io_type), modbus_type = values(modbus_type), sixnet_address = values(sixnet_address),
                    iconics_path = values(iconics_path), remark = values(remark)
                """, point.deviceCode(), point.pointName(), point.pointCode(), point.dataType(), point.unit(), point.address(), point.accessMode(),
                point.scaleValue(), point.sortOrder(), point.sourceGroup(), point.sourceSheet(), point.ioModule(), point.ioType(), point.modbusType(),
                point.sixnetAddress(), point.iconicsPath(), point.remark());
    }

    private void seedAlarmRules() {
        List<AlarmRuleSeed> points = jdbcTemplate.query("""
                select id, code, name, unit
                from scada_point
                order by id
                """, (rs, rowNum) -> new AlarmRuleSeed(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("unit")));
        for (AlarmRuleSeed point : points) {
            seedAlarmRule(point, "质量异常", "QUALITY_BAD", "=", null, "高", "点位质量异常");
            seedAlarmRule(point, "数据超时", "QUALITY_STALE", "=", null, "中", "点位数据超时");
            String text = (point.code() + " " + point.name()).toUpperCase(java.util.Locale.ROOT);
            if (text.contains("故障") || text.contains("_GZ")) {
                seedAlarmRule(point, "故障触发", "EQUAL", "=", 1.0, "高", "故障信号触发");
            }
            if ("%".equals(point.unit()) || text.contains("开度")) {
                seedAlarmRule(point, "开度高限", "HIGH", ">", 90.0, "中", "开度超过 90%");
            }
            if ("A".equals(point.unit()) || text.contains("电流")) {
                seedAlarmRule(point, "电流高限", "HIGH", ">", 95.0, "中", "电流偏高");
            }
            if (text.contains("电压") || text.contains("UAB") || text.contains("UBC") || text.contains("UCA")) {
                seedAlarmRule(point, "电压低限", "LOW", "<", 360.0, "低", "电压低限");
                seedAlarmRule(point, "电压高限", "HIGH", ">", 410.0, "低", "电压高限");
            }
        }
    }

    private void seedAlarmRule(AlarmRuleSeed point, String ruleName, String ruleType, String operator, Double thresholdValue, String level, String message) {
        jdbcTemplate.update("""
                insert into scada_alarm_rule(point_id, point_code, point_name, rule_name, rule_type, operator, threshold_value, level, message, enabled)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                on duplicate key update point_code = values(point_code), point_name = values(point_name), rule_name = values(rule_name),
                    operator = values(operator), threshold_value = values(threshold_value), level = values(level), message = values(message)
                """, point.id(), point.code(), point.name(), ruleName, ruleType, operator, thresholdValue, level, message);
    }

    private void seedCollectChannels() {
        List<CollectChannelSeed> devices = jdbcTemplate.query("""
                select id, name, code, protocol, ip_address, port
                from scada_device
                order by id
                """, (rs, rowNum) -> new CollectChannelSeed(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("protocol"),
                rs.getString("ip_address"),
                (Integer) rs.getObject("port")
        ));
        for (CollectChannelSeed device : devices) {
            jdbcTemplate.update("""
                    insert into scada_collect_channel(device_id, name, code, protocol, host, port, poll_interval_ms, enabled, status)
                    values (?, ?, ?, ?, ?, ?, 1000, 1, 'READY')
                    on duplicate key update name = values(name), protocol = values(protocol), host = values(host), port = values(port)
                    """, device.id(), device.name() + "采集通道", device.code() + "_CH", device.protocol(), device.host(), device.port());
            Long channelId = jdbcTemplate.queryForObject("select id from scada_collect_channel where device_id = ?", Long.class, device.id());
            jdbcTemplate.update("""
                    insert ignore into scada_collect_binding(channel_id, point_id, enabled)
                    select ?, id, 1
                    from scada_point
                    where device_id = ?
                    """, channelId, device.id());
        }
    }
    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 1.0;
        }
        return Double.parseDouble(value);
    }

    private int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
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


    private record JindouheAreaSeed(String name, String code, String description) {
    }

    private record JindouheDeviceSeed(String name, String code, String type, String areaCode, String sourceGroup) {
    }

    private record JindouhePointSeed(
            String sourceSheet,
            String sourceGroup,
            String areaCode,
            String areaName,
            String deviceCode,
            String deviceName,
            String deviceType,
            String pointCode,
            String pointName,
            String dataType,
            String unit,
            String address,
            String accessMode,
            Double scaleValue,
            Integer sortOrder,
            String ioModule,
            String ioType,
            String modbusType,
            String sixnetAddress,
            String iconicsPath,
            String remark) {
    }

    private record AlarmRuleSeed(Long id, String code, String name, String unit) {
    }

    private record CollectChannelSeed(Long id, String name, String code, String protocol, String host, Integer port) {
    }
}

