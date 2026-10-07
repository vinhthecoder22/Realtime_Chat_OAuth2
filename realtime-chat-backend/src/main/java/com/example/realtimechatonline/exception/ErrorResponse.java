package com.example.realtimechatonline.exception;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ErrorResponse {

    int status;
    String error;
    String message;

    @Builder.Default
    LocalDateTime timestamp = LocalDateTime.now();

    /** Convenience factory used by exception handlers. */
    public static ErrorResponse of(int status, String error, String message) {
        return ErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .build();
    }
}
