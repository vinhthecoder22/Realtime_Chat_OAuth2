package com.example.realtimechatonline.domain.dto.response;

import com.example.realtimechatonline.domain.entity.AuthProvider;
import com.example.realtimechatonline.domain.entity.Role;
import lombok.*;
import lombok.experimental.FieldDefaults;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterResponseDto {
    String username;
    String email;
    Role role;
    AuthProvider authProvider;
}
