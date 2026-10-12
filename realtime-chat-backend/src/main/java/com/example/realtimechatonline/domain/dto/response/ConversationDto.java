package com.example.realtimechatonline.domain.dto.response;

import com.example.realtimechatonline.domain.entity.ConversationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationDto {

    Long id;
    ConversationType type;
    String name;
    String avatar;
    UserProfileDto otherUser; // For DIRECT chat
    String lastMessage;
    String lastMessageSender;
    Instant lastMessageTimestamp;
    Instant lastActivityAt;
    Instant createdAt;

    @Builder.Default
    Long unreadCount = 0L;
    Long lastReadMessageId;
    Long otherLastReadMessageId;
}
