package com.example.realtimechatonline.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.realtimechatonline.config.AppProperties;
import com.example.realtimechatonline.exception.extended.OAuth2EmailConflictException;
import com.example.realtimechatonline.service.OAuthService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Handles a successful OAuth2 login by:
 *   1. Delegating user lookup / creation to OAuthService
 *   2. Generating a JWT
 *   3. Redirecting to the configured frontend URI with the token as a fragment
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthService oAuthService;
    private final AppProperties appProperties;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest  request,
            HttpServletResponse response,
            Authentication      authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        Map<String, Object> attributes = authToken.getPrincipal().getAttributes();

        String redirectUri = appProperties.getOauth2().getRedirectUri();

        try {
            String token = oAuthService.login(attributes, authentication);
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);

            log.debug("OAuth2 login successful, redirecting to: {}", redirectUri);
            String username = authentication.getName();
            response.sendRedirect(
                    redirectUri + "#token=" + encodedToken + "&username=" + username
            );

        } catch (OAuth2EmailConflictException ex) {
            log.warn("OAuth2 email conflict: {}", ex.getMessage());
            String encodedError = URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8);
            response.sendRedirect(redirectUri + "#error=" + encodedError);

        } catch (Exception ex) {
            log.error("Unexpected error during OAuth2 success handling", ex);
            response.sendRedirect(redirectUri + "#error=unexpected_error");
        }
    }
}