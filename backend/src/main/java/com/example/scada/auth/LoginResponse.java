package com.example.scada.auth;

import java.util.List;
import com.example.scada.menu.MenuItemResponse;

public record LoginResponse(String token, AuthUser user, List<MenuItemResponse> menus) {
}
