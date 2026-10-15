package com.example.realtimechatonline.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransferAdminRequestDto {

    @NotNull(message = "newAdminUserId is required")
    Long newAdminUserId;

}
