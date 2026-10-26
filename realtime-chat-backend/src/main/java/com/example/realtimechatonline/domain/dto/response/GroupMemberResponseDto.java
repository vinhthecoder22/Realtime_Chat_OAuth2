package com.example.realtimechatonline.domain.dto.response;

import com.example.realtimechatonline.domain.entity.ConversationMemberRole;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroupMemberResponseDto {

    Long userId;
    String username;
    String fullname;
    String picture;
    ConversationMemberRole role;
    Boolean isOnline;
    Instant lastSeenAt;
    Instant joinedAt;

}
