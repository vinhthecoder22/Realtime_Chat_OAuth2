package com.example.realtimechatonline.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-ms}")
    private long validityMs;

    // Key

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // Token generation

    /**
     * Generates a JWT from a Spring Security Authentication object.
     * Used after username/password login.
     */
    public String generateToken(Authentication auth) {
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();

        List<String> roles = auth.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Date now    = new Date();
        Date expiry = new Date(now.getTime() + validityMs);

        return Jwts.builder()
                .setSubject(String.valueOf(userDetails.getId()))
                .claim("username", userDetails.getUsername())
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates a JWT directly from a User entity.
     * Used after OAuth2 login where no Authentication object is available yet.
     */
    public String generateToken(User user) {
        List<String> roles = List.of("ROLE_" + user.getRole().name());

        Date now    = new Date();
        Date expiry = new Date(now.getTime() + validityMs);

        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Token parsing

    /**
     * Validates token signature and expiry.
     * Throws a JwtException subclass if invalid — caught by JwtAuthenticationFilter.
     */
    public void validateToken(String token) {
        Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
    }

    /**
     * Extracts a Spring Security Authentication from a valid JWT.
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);

        String username  = claims.get("username", String.class);
        List<?> rawRoles = claims.get("roles", List.class);

        List<SimpleGrantedAuthority> authorities = rawRoles.stream()
                .map(r -> new SimpleGrantedAuthority(String.valueOf(r)))
                .toList();

        UserDetails principal = new org.springframework.security.core.userdetails.User(
                username, "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    /**
     * Extracts the username claim from a token (without full validation).
     * Useful for logging or refresh-token flows.
     */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).get("username", String.class);
    }

    // Internal helpers

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}