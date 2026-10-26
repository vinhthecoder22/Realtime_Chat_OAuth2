package com.example.realtimechatonline.domain.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateGroupRequestDto {

    @Size(min = 1, max = 100, message = "Group name must be between 1 and 100 characters")
    String name;

    @Size(max = 255, message = "Avatar URL must not exceed 255 characters")
    String avatar;

}
