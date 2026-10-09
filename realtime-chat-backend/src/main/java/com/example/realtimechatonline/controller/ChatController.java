package com.example.realtimechatonline.controller;

import com.example.realtimechatonline.domain.dto.websocket.ChatMessageDto;
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
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.GetMapping;
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

    @MessageMapping("/chat.typing")
    @SendTo("/topic/public")
    public ChatMessageDto processTyping(ChatMessageDto message, StompHeaderAccessor headerAccessor) {
        if (headerAccessor.getUser() != null) {
            message.setSender(headerAccessor.getUser().getName());
        } else {
            message.setSender(null);
        }
        return message;
    }
}