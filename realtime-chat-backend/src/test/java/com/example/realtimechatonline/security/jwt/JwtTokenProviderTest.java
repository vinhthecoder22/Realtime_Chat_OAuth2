package com.example.realtimechatonline.security.jwt;

import com.example.realtimechatonline.domain.entity.Role;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.security.CustomUserDetails;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider Security & Configuration Tests")
class JwtTokenProviderTest {

    private static final String VALID_SECRET = "PhCVkoT3CiFBQb1OcZd6mf7cWDZdA+WTJzz7LNhGpP9wVWPTJqD+tpm9Zi+NWjjBLkepWsM/ok3hdbbtnf5zDg==";
    private static final long VALIDITY_MS = 86400000L; // 24 hours

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", VALID_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "validityMs", VALIDITY_MS);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("init — succeeds with valid 256+ bit secret key")
    void init_validKey_succeeds() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secretKey", VALID_SECRET);
        ReflectionTestUtils.setField(provider, "validityMs", VALIDITY_MS);

        provider.init();
        assertThat(provider).isNotNull();
    }

    @Test
    @DisplayName("init — fails fast on startup when secret is null")
    void init_nullKey_throwsIllegalStateException() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secretKey", null);
        ReflectionTestUtils.setField(provider, "validityMs", VALIDITY_MS);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be null or blank");
    }

    @Test
    @DisplayName("init — fails fast on startup when secret is blank")
    void init_blankKey_throwsIllegalStateException() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secretKey", "   ");
        ReflectionTestUtils.setField(provider, "validityMs", VALIDITY_MS);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be null or blank");
    }

    @Test
    @DisplayName("init — fails fast on startup when secret is shorter than 256 bits (32 bytes)")
    void init_shortKey_throwsIllegalStateException() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secretKey", "short-secret-key-less-32-byte"); // 29 bytes
        ReflectionTestUtils.setField(provider, "validityMs", VALIDITY_MS);

        assertThatThrownBy(provider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short: must be at least 256 bits");
    }

    @Test
    @DisplayName("generateToken & validateToken — generates valid JWT from Authentication")
    void generateToken_fromAuthentication_succeeds() {
        CustomUserDetails userDetails = CustomUserDetails.builder()
                .id(101L)
                .username("testuser")
                .password("encoded_pass")
                .authority(new SimpleGrantedAuthority("ROLE_USER"))
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        String token = jwtTokenProvider.generateToken(auth);
        assertThat(token).isNotBlank();

        // Validating should not throw
        jwtTokenProvider.validateToken(token);

        // Verify claims
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("testuser");

        Authentication parsedAuth = jwtTokenProvider.getAuthentication(token);
        assertThat(parsedAuth.getName()).isEqualTo("testuser");
        assertThat(parsedAuth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("generateToken — generates valid JWT directly from User entity (OAuth2 flow)")
    void generateToken_fromUser_succeeds() {
        User user = User.builder()
                .id(202L)
                .username("oauth_user")
                .role(Role.USER)
                .build();

        String token = jwtTokenProvider.generateToken(user);
        assertThat(token).isNotBlank();

        jwtTokenProvider.validateToken(token);
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("oauth_user");

        Authentication parsedAuth = jwtTokenProvider.getAuthentication(token);
        assertThat(parsedAuth.getName()).isEqualTo("oauth_user");
        assertThat(parsedAuth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("validateToken — throws SignatureException when token is signed with a different key")
    void validateToken_differentKey_throwsSignatureException() {
        JwtTokenProvider otherProvider = new JwtTokenProvider();
        // 32-byte alternate key
        ReflectionTestUtils.setField(otherProvider, "secretKey", "another-very-long-secret-key-that-is-valid-32b!");
        ReflectionTestUtils.setField(otherProvider, "validityMs", VALIDITY_MS);
        otherProvider.init();

        User user = User.builder().id(303L).username("alice").role(Role.USER).build();
        String tokenFromOther = otherProvider.generateToken(user);

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tokenFromOther))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("validateToken — throws ExpiredJwtException when token has passed expiration time")
    void validateToken_expiredToken_throwsExpiredJwtException() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(expiredProvider, "secretKey", VALID_SECRET);
        ReflectionTestUtils.setField(expiredProvider, "validityMs", -1000L); // Expired immediately
        expiredProvider.init();

        User user = User.builder().id(404L).username("expired_user").role(Role.USER).build();
        String expiredToken = expiredProvider.generateToken(user);

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("validateToken — throws MalformedJwtException when token structure is corrupt")
    void validateToken_malformedToken_throwsMalformedJwtException() {
        String malformedToken = "not.a.valid.jwt.token";

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(malformedToken))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    @DisplayName("validateToken — throws IllegalArgumentException when token is blank or null")
    void validateToken_blankToken_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
