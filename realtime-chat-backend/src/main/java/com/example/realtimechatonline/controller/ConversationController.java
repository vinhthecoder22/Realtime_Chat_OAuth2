package com.example.realtimechatonline.controller;

import com.example.realtimechatonline.base.RestApiV1;
import com.example.realtimechatonline.common.ApiResponse;
import com.example.realtimechatonline.domain.dto.request.*;
import com.example.realtimechatonline.domain.dto.response.ConversationDto;
import com.example.realtimechatonline.domain.dto.response.GroupMemberResponseDto;
import com.example.realtimechatonline.domain.dto.response.ReadReceiptResponseDto;
import com.example.realtimechatonline.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @PostMapping("/group")
    public ResponseEntity<ApiResponse<ConversationDto>> createGroupConversation(
            @Valid @RequestBody CreateGroupRequestDto request,
            Authentication authentication) {
        ConversationDto conversation = conversationService.createGroupConversation(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ConversationDto>builder()
                .status(HttpStatus.CREATED)
                .message("Group conversation created")
                .data(conversation)
                .build());
    }

    @GetMapping("/{conversationId}/members")
    public ResponseEntity<ApiResponse<List<GroupMemberResponseDto>>> getGroupMembers(
            @PathVariable Long conversationId,
            Authentication authentication) {
        List<GroupMemberResponseDto> members = conversationService.getGroupMembers(conversationId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.<List<GroupMemberResponseDto>>builder()
                .status(HttpStatus.OK)
                .message("Group members retrieved")
                .data(members)
                .build());
    }

    @PostMapping("/{conversationId}/members")
    public ResponseEntity<ApiResponse<List<GroupMemberResponseDto>>> addGroupMembers(
            @PathVariable Long conversationId,
            @Valid @RequestBody AddGroupMembersRequestDto request,
            Authentication authentication) {
        List<GroupMemberResponseDto> members = conversationService.addGroupMembers(conversationId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.<List<GroupMemberResponseDto>>builder()
                .status(HttpStatus.OK)
                .message("Members added to group")
                .data(members)
                .build());
    }

    @DeleteMapping("/{conversationId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeGroupMember(
            @PathVariable Long conversationId,
            @PathVariable Long userId,
            Authentication authentication) {
        conversationService.removeGroupMember(conversationId, userId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK)
                .message("Member removed from group")
                .build());
    }

    @PostMapping("/{conversationId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveGroup(
            @PathVariable Long conversationId,
            @RequestBody(required = false) LeaveGroupRequestDto request,
            Authentication authentication) {
        conversationService.leaveGroup(conversationId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK)
                .message("Left group conversation")
                .build());
    }

    @PatchMapping("/{conversationId}/admin")
    public ResponseEntity<ApiResponse<Void>> transferAdmin(
            @PathVariable Long conversationId,
            @Valid @RequestBody TransferAdminRequestDto request,
            Authentication authentication) {
        conversationService.transferAdmin(conversationId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK)
                .message("Group admin transferred")
                .build());
    }

    @PatchMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationDto>> updateGroup(
            @PathVariable Long conversationId,
            @Valid @RequestBody UpdateGroupRequestDto request,
            Authentication authentication) {
        ConversationDto conversation = conversationService.updateGroup(conversationId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.<ConversationDto>builder()
                .status(HttpStatus.OK)
                .message("Group updated")
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
