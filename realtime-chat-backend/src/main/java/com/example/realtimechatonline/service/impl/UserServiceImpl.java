package com.example.realtimechatonline.service.impl;

import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.domain.mapper.UserMapper;
import com.example.realtimechatonline.exception.extended.ResourceNotFoundException;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<UserProfileDto> searchUsers(String keyword, String currentUsername, Pageable pageable) {
        if (keyword == null || keyword.trim().length() < 2) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            pageable = org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found: " + currentUsername));

        Page<User> users = userRepository.searchByKeyword(keyword.trim(), currentUser.getId(), pageable);
        return users.map(userMapper::toUserProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return userMapper.toUserProfile(user);
    }
}
