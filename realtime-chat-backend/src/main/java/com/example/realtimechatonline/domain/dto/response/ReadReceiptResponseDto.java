package com.example.realtimechatonline.domain.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReadReceiptResponseDto {
    Long conversationId;
    Long userId;
    String username;
    Long lastReadMessageId;
    Instant readAt;
    Long unreadCount;
}
