package com.example.realtimechatonline.domain.dto.websocket;

import com.example.realtimechatonline.domain.entity.MessageType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageDto {

    Long id;
    MessageType type;
    Long conversationId;
    String content;
    String sender;
    String time;
}