package com.example.realtimechatonline.domain.dto.response;

import com.example.realtimechatonline.domain.entity.FriendRequestStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendRequestDto {

    Long id;
    UserProfileDto sender;
    UserProfileDto receiver;
    FriendRequestStatus status;
    Instant createdAt;
    Instant updatedAt;

}
