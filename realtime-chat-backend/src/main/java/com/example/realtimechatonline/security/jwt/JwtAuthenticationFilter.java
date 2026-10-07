package com.example.realtimechatonline.security.jwt;

import com.example.realtimechatonline.exception.extended.JwtAuthenticationException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.realtimechatonline.constant.TokenError;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts every HTTP request, extracts the Bearer token from the
 * Authorization header, validates it, and populates the SecurityContext.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider       tokenProvider;
    private final RestAuthenticationEntryPoint entryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         filterChain) throws ServletException, IOException {

        String path = request.getServletPath();

        if (path.startsWith("/ws-chat")) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                tokenProvider.validateToken(token);
                Authentication auth = tokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (MalformedJwtException ex) {
                log.debug("Malformed JWT: {}", ex.getMessage());
                entryPoint.commence(request, response,
                        new JwtAuthenticationException(TokenError.Token.MALFORMED, ex));
                return;

            } catch (SignatureException ex) {
                log.debug("Invalid JWT signature: {}", ex.getMessage());
                entryPoint.commence(request, response,
                        new JwtAuthenticationException(TokenError.Token.SIGNATURE, ex));
                return;

            } catch (ExpiredJwtException ex) {
                log.debug("Expired JWT: {}", ex.getMessage());
                entryPoint.commence(request, response,
                        new JwtAuthenticationException(TokenError.Token.EXPIRED, ex));
                return;

            } catch (UnsupportedJwtException ex) {
                log.debug("Unsupported JWT: {}", ex.getMessage());
                entryPoint.commence(request, response,
                        new JwtAuthenticationException(TokenError.Token.UNSUPPORTED, ex));
                return;

            } catch (IllegalArgumentException ex) {
                log.debug("Blank JWT claims: {}", ex.getMessage());
                entryPoint.commence(request, response,
                        new JwtAuthenticationException(TokenError.Token.BLANK, ex));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT string from the Authorization header.
     * Returns null if the header is absent or malformed.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}