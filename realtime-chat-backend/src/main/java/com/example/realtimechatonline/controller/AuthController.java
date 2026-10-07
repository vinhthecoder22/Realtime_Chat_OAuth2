package com.example.realtimechatonline.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.realtimechatonline.base.RestApiV1;
import com.example.realtimechatonline.common.ApiResponse;
import com.example.realtimechatonline.constant.AuthMessage;
import com.example.realtimechatonline.domain.dto.request.LoginRequestDto;
import com.example.realtimechatonline.domain.dto.request.RegisterRequestDto;
import com.example.realtimechatonline.domain.dto.response.LoginResponseDto;
import com.example.realtimechatonline.domain.dto.response.RegisterResponseDto;
import com.example.realtimechatonline.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestApiV1(path = "/auth")
@RequiredArgsConstructor
@RestController
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponseDto>> register(
            @Valid @RequestBody RegisterRequestDto request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<RegisterResponseDto>builder()
                        .status(HttpStatus.CREATED)
                        .message(AuthMessage.Auth.REGISTER_SUCCESS)
                        .data(authService.register(request))
                        .build());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(
            @Valid @RequestBody LoginRequestDto request) {

        return ResponseEntity.ok(ApiResponse.<LoginResponseDto>builder()
                .status(HttpStatus.OK)
                .message(AuthMessage.Auth.LOGIN_SUCCESS)
                .data(authService.login(request))
                .build());
    }
}