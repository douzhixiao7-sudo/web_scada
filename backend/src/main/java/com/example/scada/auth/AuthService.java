package com.example.scada.auth;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.scada.menu.MenuItemResponse;
import com.example.scada.menu.MenuService;

@Service
public class AuthService {
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final String SESSION_PREFIX = "scada:session:";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;
    private final StringRedisTemplate redisTemplate;
    private final MenuService menuService;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordService passwordService,
            StringRedisTemplate redisTemplate,
            MenuService menuService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
        this.redisTemplate = redisTemplate;
        this.menuService = menuService;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();
        if (username.isBlank() || password.isBlank()) {
            throw new AuthException("请输入账号和密码");
        }

        UserCredential credential = jdbcTemplate.query("""
                select id, username, display_name, password_hash
                from sys_user
                where username = ? and enabled = 1
                """, rs -> rs.next()
                ? new UserCredential(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getString("password_hash"))
                : null, username);

        if (credential == null || !passwordService.matches(password, credential.passwordHash())) {
            throw new AuthException("账号或密码不正确");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(SESSION_PREFIX + token, credential.username(), SESSION_TTL);
        jdbcTemplate.update("insert into sys_audit_log(actor, action, detail) values (?, ?, ?)", credential.username(), "LOGIN", "用户登录后台");

        AuthUser user = new AuthUser(credential.id(), credential.username(), credential.displayName());
        List<MenuItemResponse> menus = menuService.listMenus(user);
        return new LoginResponse(token, user, menus);
    }

    public AuthUser requireUser(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthException("请先登录");
        }
        String username = redisTemplate.opsForValue().get(SESSION_PREFIX + token);
        if (username == null || username.isBlank()) {
            throw new AuthException("登录已过期，请重新登录");
        }
        redisTemplate.expire(SESSION_PREFIX + token, SESSION_TTL);
        AuthUser user = jdbcTemplate.query("""
                select id, username, display_name
                from sys_user
                where username = ? and enabled = 1
                """, rs -> rs.next()
                ? new AuthUser(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"))
                : null, username);
        if (user == null) {
            throw new AuthException("登录用户不可用");
        }
        return user;
    }

    public void logout(String token, AuthUser user) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(SESSION_PREFIX + token);
        }
        if (user != null) {
            jdbcTemplate.update("insert into sys_audit_log(actor, action, detail) values (?, ?, ?)", user.username(), "LOGOUT", "用户退出后台");
        }
    }

    private record UserCredential(Long id, String username, String displayName, String passwordHash) {
    }
}
