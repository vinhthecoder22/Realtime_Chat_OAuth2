package com.example.realtimechatonline.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateGroupRequestDto {

    @NotBlank(message = "Group name must not be blank")
    @Size(min = 1, max = 100, message = "Group name must be between 1 and 100 characters")
    String name;

    @NotEmpty(message = "memberUserIds must not be empty")
    List<Long> memberUserIds;

}
