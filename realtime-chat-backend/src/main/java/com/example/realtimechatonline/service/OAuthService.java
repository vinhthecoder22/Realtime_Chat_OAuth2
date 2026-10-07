package com.example.realtimechatonline.service;

import org.springframework.security.core.Authentication;

import java.util.Map;

public interface OAuthService {

    String login(Map<String, Object> attributes, Authentication authentication);
}
