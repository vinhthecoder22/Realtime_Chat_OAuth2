package com.example.realtimechatonline.constant;

public final class TokenError {

    private TokenError() {}

    public static final class Token {
        public static final String MALFORMED   = "Malformed JWT token";
        public static final String BLANK       = "JWT token must not be blank";
        public static final String UNSUPPORTED = "Unsupported JWT token";
        public static final String EXPIRED     = "JWT token has expired";
        public static final String SIGNATURE   = "Invalid JWT signature";
    }
}
