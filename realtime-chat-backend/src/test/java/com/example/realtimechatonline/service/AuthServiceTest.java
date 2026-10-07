package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.request.LoginRequestDto;
import com.example.realtimechatonline.domain.dto.request.RegisterRequestDto;
import com.example.realtimechatonline.domain.dto.response.LoginResponseDto;
import com.example.realtimechatonline.domain.dto.response.RegisterResponseDto;
import com.example.realtimechatonline.domain.entity.AuthProvider;
import com.example.realtimechatonline.domain.entity.Role;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.domain.mapper.AuthMapper;
import com.example.realtimechatonline.exception.extended.PasswordMismatchException;
import com.example.realtimechatonline.exception.extended.ResourceAlreadyExistsException;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.security.jwt.JwtTokenProvider;
import com.example.realtimechatonline.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl unit tests")
class AuthServiceTest {

    @Mock UserRepository       userRepository;
    @Mock AuthMapper           authMapper;
    @Mock AuthenticationManager authManager;
    @Mock JwtTokenProvider     jwtTokenProvider;
    @Mock PasswordEncoder      passwordEncoder;

    @InjectMocks AuthServiceImpl authService;

    private RegisterRequestDto validRequest;
    private User            savedUser;

    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequestDto("alice", "secret123", "secret123", "alice@example.com");

        savedUser = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build();
    }

    // register

    @Test
    @DisplayName("register — happy path creates and returns user")
    void register_success() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(authMapper.toUser(validRequest)).thenReturn(savedUser);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(authMapper.toRegisterResponse(savedUser))
                .thenReturn(new RegisterResponseDto("alice", "alice@example.com", Role.USER, AuthProvider.LOCAL));

        RegisterResponseDto resp = authService.register(validRequest);

        assertThat(resp.getUsername()).isEqualTo("alice");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register — throws PasswordMismatchException when passwords differ (was NPE before fix)")
    void register_passwordMismatch_throwsPasswordMismatchException() {
        RegisterRequestDto bad = new RegisterRequestDto("alice", "secret123", "WRONG", "alice@example.com");

        assertThatThrownBy(() -> authService.register(bad))
                .isInstanceOf(PasswordMismatchException.class)
                .hasMessageContaining("match");

        verifyNoInteractions(userRepository, authMapper);
    }

    @Test
    @DisplayName("register — throws ResourceAlreadyExistsException for duplicate username")
    void register_duplicateUsername_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRequest))
                .isInstanceOf(ResourceAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register — throws ResourceAlreadyExistsException for duplicate email")
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRequest))
                .isInstanceOf(ResourceAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    // login

    @Test
    @DisplayName("login — happy path returns token")
    void login_success() {
        LoginRequestDto req  = new LoginRequestDto("alice", "secret123");
        var          auth = new UsernamePasswordAuthenticationToken("alice", null);

        when(authManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(auth)).thenReturn("jwt-token");

        LoginResponseDto resp = authService.login(req);

        assertThat(resp.getToken()).isEqualTo("jwt-token");
        assertThat(resp.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("login — throws BadCredentialsException for wrong credentials")
    void login_wrongCredentials_throws() {
        LoginRequestDto req = new LoginRequestDto("alice", "wrong");
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }
}