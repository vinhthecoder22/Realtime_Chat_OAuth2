package com.example.realtimechatonline.domain.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileDto {
    Long id;
    String username;
    String fullname;
    String picture;
    Boolean isOnline;
    Instant lastSeenAt;

}
