package com.example.realtimechatonline.constant;

public final class ErrorMessage {

    private ErrorMessage() {}

    public static final class User {
        public static final String ERR_DUPLICATED_USERNAME   = "Username already exists";
        public static final String ERR_DUPLICATED_EMAIL      = "Email is already in use by another account";
        public static final String ERR_USER_NOT_FOUND        = "User not found";
        public static final String ERR_PASSWORD_MISMATCH     = "Passwords do not match";
        public static final String ERR_INVALID_CREDENTIALS   = "Wrong username or password";
        public static final String ERR_OAUTH2_EMAIL_CONFLICT = "This email is already linked to a local account";
    }

    public static final class Chat {
        public static final String ERR_EMPTY_MESSAGE = "Message content cannot be empty";
    }
}
