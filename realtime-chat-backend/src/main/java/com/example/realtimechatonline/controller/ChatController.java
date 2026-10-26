package com.example.realtimechatonline.controller;

import lombok.RequiredArgsConstructor;
import com.example.realtimechatonline.base.RestApiV1;
import com.example.realtimechatonline.common.ApiResponse;
import com.example.realtimechatonline.domain.dto.response.MessageResponseDto;
import com.example.realtimechatonline.service.ChatService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestApiV1
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<MessageResponseDto>>> getHistory() {
        return ResponseEntity.ok(ApiResponse.<List<MessageResponseDto>>builder()
                .status(HttpStatus.OK)
                .message("Message history retrieved")
                .data(chatService.getHistory())
                .build());
    }

    @GetMapping("/history/page")
    public ResponseEntity<ApiResponse<Page<MessageResponseDto>>> getHistoryPaged(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(ApiResponse.<Page<MessageResponseDto>>builder()
                .status(HttpStatus.OK)
                .message("Message history retrieved")
                .data(chatService.getHistory(pageable))
                .build());
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Page<MessageResponseDto>>> getConversationHistoryPaged(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            org.springframework.security.core.Authentication authentication) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(ApiResponse.<Page<MessageResponseDto>>builder()
                .status(HttpStatus.OK)
                .message("Message history retrieved")
                .data(chatService.getHistoryByConversation(conversationId, pageable, authentication.getName()))
                .build());
    }
}