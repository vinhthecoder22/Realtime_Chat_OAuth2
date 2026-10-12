package com.example.realtimechatonline.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateFriendRequestDto {

    @NotNull(message = "Receiver ID is required")
    Long receiverId;
}
