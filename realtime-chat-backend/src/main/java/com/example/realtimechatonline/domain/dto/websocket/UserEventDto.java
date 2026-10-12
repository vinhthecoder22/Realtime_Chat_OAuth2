package com.example.realtimechatonline.domain.dto.websocket;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserEventDto {

    String type; // "CONVERSATION_UPDATE" or "READ_RECEIPT"
    Long conversationId;
    String lastMessage;
    String lastMessageSender;
    Instant lastMessageTimestamp;
    Instant lastActivityAt;
    Long unreadCount;
    Long lastReadMessageId;
    String readerUsername;
    Long messageId;

}
