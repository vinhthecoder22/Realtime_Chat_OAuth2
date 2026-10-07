package com.example.realtimechatonline.domain.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginResponseDto {
    String token;
    String tokenType;

    public static LoginResponseDto of(String token) {
        return LoginResponseDto.builder()
                .token(token)
                .tokenType("Bearer")
                .build();
    }
}
