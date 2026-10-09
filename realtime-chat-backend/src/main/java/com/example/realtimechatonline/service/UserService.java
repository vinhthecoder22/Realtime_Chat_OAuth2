package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    Page<UserProfileDto> searchUsers(String keyword, String currentUsername, Pageable pageable);

    UserProfileDto getUserProfile(Long userId);
}
