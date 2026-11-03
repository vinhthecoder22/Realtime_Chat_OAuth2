package com.example.realtimechatonline.domain.dto.websocket;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PresenceEventDto {
    String username;
    Long userId;
    boolean online;
    Instant lastSeenAt;
    Instant timestamp;
}
