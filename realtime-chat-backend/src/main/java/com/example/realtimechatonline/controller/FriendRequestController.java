package com.example.realtimechatonline.controller;

import com.example.realtimechatonline.base.RestApiV1;
import com.example.realtimechatonline.common.ApiResponse;
import com.example.realtimechatonline.domain.dto.request.CreateFriendRequestDto;
import com.example.realtimechatonline.domain.dto.response.FriendRequestDto;
import com.example.realtimechatonline.service.FriendRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestApiV1(path = "/friend-requests")
@RequiredArgsConstructor
public class FriendRequestController {

    private final FriendRequestService friendRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<FriendRequestDto>> sendRequest(
            @Valid @RequestBody CreateFriendRequestDto requestDto,
            Principal principal) {
        FriendRequestDto response = friendRequestService.sendRequest(requestDto.getReceiverId(), principal.getName());
        return ResponseEntity.ok(ApiResponse.<FriendRequestDto>builder()
                .status(org.springframework.http.HttpStatus.OK)
                .message("Friend request sent successfully")
                .data(response)
                .build());
    }

    @GetMapping("/received")
    public ResponseEntity<ApiResponse<Page<FriendRequestDto>>> getReceivedRequests(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal) {
        Page<FriendRequestDto> response = friendRequestService.getReceivedRequests(principal.getName(), pageable);
        return ResponseEntity.ok(ApiResponse.<Page<FriendRequestDto>>builder()
                .status(org.springframework.http.HttpStatus.OK)
                .message("Received requests fetched successfully")
                .data(response)
                .build());
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<Page<FriendRequestDto>>> getSentRequests(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal) {
        Page<FriendRequestDto> response = friendRequestService.getSentRequests(principal.getName(), pageable);
        return ResponseEntity.ok(ApiResponse.<Page<FriendRequestDto>>builder()
                .status(org.springframework.http.HttpStatus.OK)
                .message("Sent requests fetched successfully")
                .data(response)
                .build());
    }

    @PatchMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<FriendRequestDto>> acceptRequest(
            @PathVariable Long id,
            Principal principal) {
        FriendRequestDto response = friendRequestService.acceptRequest(id, principal.getName());
        return ResponseEntity.ok(ApiResponse.<FriendRequestDto>builder()
                .status(org.springframework.http.HttpStatus.OK)
                .message("Friend request accepted")
                .data(response)
                .build());
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<FriendRequestDto>> rejectRequest(
            @PathVariable Long id,
            Principal principal) {
        FriendRequestDto response = friendRequestService.rejectRequest(id, principal.getName());
        return ResponseEntity.ok(ApiResponse.<FriendRequestDto>builder()
                .status(org.springframework.http.HttpStatus.OK)
                .message("Friend request rejected")
                .data(response)
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelRequest(
            @PathVariable Long id,
            Principal principal) {
        friendRequestService.cancelRequest(id, principal.getName());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(org.springframework.http.HttpStatus.OK)
                .message("Friend request cancelled successfully")
                .data(null)
                .build());
    }
}
