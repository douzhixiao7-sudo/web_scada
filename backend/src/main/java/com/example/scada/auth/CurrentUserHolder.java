package com.example.scada.auth;

public final class CurrentUserHolder {
    private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(AuthUser user, String token) {
        CURRENT.set(user);
        TOKEN.set(token);
    }

    public static AuthUser user() {
        return CURRENT.get();
    }

    public static String token() {
        return TOKEN.get();
    }

    public static void clear() {
        CURRENT.remove();
        TOKEN.remove();
    }
}
