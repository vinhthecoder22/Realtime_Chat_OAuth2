package com.example.realtimechatonline.constant;

public final class SecurityPath {

    private SecurityPath() {}

    public static final String[] ANONYMOUS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
    };

    public static final String[] PUBLIC = {
            "/ws-chat/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/login/oauth2/**",
    };
}
