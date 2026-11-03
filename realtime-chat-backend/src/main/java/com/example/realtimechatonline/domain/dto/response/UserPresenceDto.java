package com.example.realtimechatonline.domain.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserPresenceDto {
    Long userId;
    String username;
    boolean online;
    Instant lastSeenAt;
}
