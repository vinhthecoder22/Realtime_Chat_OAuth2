package com.example.realtimechatonline.domain.dto.response;

import com.example.realtimechatonline.domain.entity.MessageType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * HTTP response DTO for message history queries.
 * Separate from ChatMessage (WebSocket DTO) to keep concerns clean.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageResponseDto {
    Long id;
    String content;
    String senderUsername;
    Instant timestamp;
    MessageType type;
}
