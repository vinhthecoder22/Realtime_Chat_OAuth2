package com.example.realtimechatonline.service.impl;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.example.realtimechatonline.constant.ErrorMessage;
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
import com.example.realtimechatonline.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final AuthMapper authMapper;
    private final AuthenticationManager authManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public RegisterResponseDto register(RegisterRequestDto request) {
        if (!request.getPassword().equals(request.getRepeatPassword())) {
            throw new PasswordMismatchException(ErrorMessage.User.ERR_PASSWORD_MISMATCH);
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResourceAlreadyExistsException(ErrorMessage.User.ERR_DUPLICATED_USERNAME);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException(ErrorMessage.User.ERR_DUPLICATED_EMAIL);
        }

        User user = authMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullname(request.getUsername());
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.LOCAL);

        User saved = userRepository.save(user);

        log.debug("New user registered: id={}, username={}, role={}",
                saved.getId(), saved.getUsername(), saved.getRole());

        return authMapper.toRegisterResponse(saved);
    }

    @Override
    public LoginResponseDto login(LoginRequestDto request) {
        Authentication auth;
        try {
            auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException ex) {
            log.warn("Failed login attempt for username: {}", request.getUsername());
            throw new BadCredentialsException(ErrorMessage.User.ERR_INVALID_CREDENTIALS, ex);
        }

        String token = jwtTokenProvider.generateToken(auth);
        log.debug("User logged in: {}", request.getUsername());
        return LoginResponseDto.of(token);
    }
}