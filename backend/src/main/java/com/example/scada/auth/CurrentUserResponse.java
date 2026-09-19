package com.example.scada.auth;

import java.util.List;
import com.example.scada.menu.MenuItemResponse;

public record CurrentUserResponse(AuthUser user, List<MenuItemResponse> menus) {
}
