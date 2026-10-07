package com.example.realtimechatonline.exception.extended;

public class OAuth2EmailConflictException extends RuntimeException {

    public OAuth2EmailConflictException(String message) {
        super(message);
    }
}