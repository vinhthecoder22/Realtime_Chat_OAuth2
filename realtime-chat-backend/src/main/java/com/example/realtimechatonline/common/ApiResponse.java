package com.example.realtimechatonline.common;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ApiResponse<T> {

    HttpStatus status;
    String message;
    T data;

    @Builder.Default
    LocalDateTime timestamp = LocalDateTime.now();
}
