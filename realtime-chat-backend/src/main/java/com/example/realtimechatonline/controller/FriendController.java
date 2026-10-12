package com.example.realtimechatonline.controller;

import com.example.realtimechatonline.base.RestApiV1;
import com.example.realtimechatonline.common.ApiResponse;
import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import com.example.realtimechatonline.service.FriendRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@RestApiV1(path = "/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendRequestService friendRequestService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserProfileDto>>> getFriends(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal) {
        Page<UserProfileDto> response = friendRequestService.getFriends(principal.getName(), pageable);
        return ResponseEntity.ok(ApiResponse.<Page<UserProfileDto>>builder()
                .status(org.springframework.http.HttpStatus.OK)
                .message("Friends fetched successfully")
                .data(response)
                .build());
    }
}
