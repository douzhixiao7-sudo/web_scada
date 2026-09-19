package com.example.scada.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.scada.menu.MenuItemResponse;
import com.example.scada.menu.MenuService;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final MenuService menuService;

    public AuthController(AuthService authService, MenuService menuService) {
        this.authService = authService;
        this.menuService = menuService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        AuthUser user = CurrentUserHolder.user();
        List<MenuItemResponse> menus = menuService.listMenus(user);
        return new CurrentUserResponse(user, menus);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authService.logout(CurrentUserHolder.token(), CurrentUserHolder.user());
    }
}
