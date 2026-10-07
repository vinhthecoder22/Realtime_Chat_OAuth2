package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.request.LoginRequestDto;
import com.example.realtimechatonline.domain.dto.request.RegisterRequestDto;
import com.example.realtimechatonline.domain.dto.response.LoginResponseDto;
import com.example.realtimechatonline.domain.dto.response.RegisterResponseDto;

public interface AuthService {

    RegisterResponseDto register(RegisterRequestDto request);

    LoginResponseDto login(LoginRequestDto request);
}
