package com.example.realtimechatonline.controller;

import com.example.realtimechatonline.common.ApiResponse;
import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import com.example.realtimechatonline.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<UserProfileDto>>> searchUsers(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails currentUser) {

        // Clamp page size between 1 and 50
        size = Math.max(1, Math.min(size, 50));
        page = Math.max(0, page);

        PageRequest pageable = PageRequest.of(page, size, Sort.by("username").ascending());

        return ResponseEntity.ok(ApiResponse.<Page<UserProfileDto>>builder()
                .status(HttpStatus.OK)
                .message("Search results")
                .data(userService.searchUsers(query, currentUser.getUsername(), pageable))
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileDto>> getUserProfile(
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.<UserProfileDto>builder()
                .status(HttpStatus.OK)
                .message("User profile")
                .data(userService.getUserProfile(id))
                .build());
    }
}
