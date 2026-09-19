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
        createTables();
        seedRole();
        seedMenus();
        seedAdminUser();
        linkAdminPermissions();
    }

    private void createTables() {
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

    private record MenuSeed(String key, String label, String helper, String icon, int sortOrder) {
    }
}
