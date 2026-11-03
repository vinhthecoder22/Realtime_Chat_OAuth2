package com.example.realtimechatonline.domain.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AddGroupMembersRequestDto {

    @NotEmpty(message = "memberUserIds must not be empty")
    List<Long> memberUserIds;

}
