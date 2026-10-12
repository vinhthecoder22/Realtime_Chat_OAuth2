package com.example.realtimechatonline.controller;

import com.example.realtimechatonline.base.RestApiV1;
import com.example.realtimechatonline.common.ApiResponse;
import com.example.realtimechatonline.domain.dto.request.MarkReadRequestDto;
import com.example.realtimechatonline.domain.dto.response.ConversationDto;
import com.example.realtimechatonline.domain.dto.response.ReadReceiptResponseDto;
import com.example.realtimechatonline.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestApiV1
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/direct")
    public ResponseEntity<ApiResponse<ConversationDto>> getOrCreateDirectConversation(
            @RequestBody Map<String, Long> payload,
            Authentication authentication) {

        Long targetUserId = payload.get("targetUserId");
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId is required");
        }

        ConversationDto conversation = conversationService.getOrCreateDirectConversation(targetUserId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.<ConversationDto>builder()
                .status(HttpStatus.OK)
                .message("Direct conversation retrieved or created")
                .data(conversation)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ConversationDto>>> getConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.<Page<ConversationDto>>builder()
                .status(HttpStatus.OK)
                .message("Conversations retrieved")
                .data(conversationService.getConversations(authentication.getName(), pageable))
                .build());
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationDto>> getConversation(
            @PathVariable Long conversationId,
            Authentication authentication) {

        return ResponseEntity.ok(ApiResponse.<ConversationDto>builder()
                .status(HttpStatus.OK)
                .message("Conversation retrieved")
                .data(conversationService.getConversationById(conversationId, authentication.getName()))
                .build());
    }

    @PostMapping("/{conversationId}/read")
    public ResponseEntity<ApiResponse<ReadReceiptResponseDto>> markConversationAsRead(
            @PathVariable Long conversationId,
            @RequestBody(required = false) MarkReadRequestDto request,
            Authentication authentication) {

        Long messageId = request != null ? request.getMessageId() : null;
        ReadReceiptResponseDto response = conversationService.markConversationAsRead(
                conversationId, messageId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.<ReadReceiptResponseDto>builder()
                .status(HttpStatus.OK)
                .message("Conversation marked as read")
                .data(response)
                .build());
    }
}
