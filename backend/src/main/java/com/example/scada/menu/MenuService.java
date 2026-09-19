package com.example.scada.menu;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.scada.auth.AuthUser;

@Service
public class MenuService {
    private final JdbcTemplate jdbcTemplate;

    public MenuService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MenuItemResponse> listMenus(AuthUser user) {
        if (user == null) {
            return List.of();
        }
        return jdbcTemplate.query("""
                select distinct m.menu_key, m.label, m.helper, m.icon, m.sort_order
                from sys_menu m
                join sys_role_menu rm on rm.menu_id = m.id
                join sys_user_role ur on ur.role_id = rm.role_id
                where ur.user_id = ? and m.enabled = 1
                order by m.sort_order
                """, (rs, rowNum) -> new MenuItemResponse(
                rs.getString("menu_key"),
                rs.getString("label"),
                rs.getString("helper"),
                rs.getString("icon"),
                rs.getInt("sort_order")
        ), user.id());
    }
}
