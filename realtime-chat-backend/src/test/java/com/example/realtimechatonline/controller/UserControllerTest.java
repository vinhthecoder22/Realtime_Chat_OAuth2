package com.example.realtimechatonline.controller;

import com.example.realtimechatonline.common.ApiResponse;
import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import com.example.realtimechatonline.domain.dto.response.UserPresenceDto;
import com.example.realtimechatonline.service.PresenceService;
import com.example.realtimechatonline.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController Unit Tests")
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private PresenceService presenceService;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("getUsersPresence — returns presence list successfully")
    void getUsersPresence_success() {
        UserPresenceDto dto = UserPresenceDto.builder()
                .userId(1L)
                .username("alice")
                .online(true)
                .lastSeenAt(null)
                .build();

        when(presenceService.getPresenceList(List.of("alice"))).thenReturn(List.of(dto));

        ResponseEntity<ApiResponse<List<UserPresenceDto>>> response = userController.getUsersPresence(List.of("alice"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getUsername()).isEqualTo("alice");
        assertThat(response.getBody().getData().get(0).isOnline()).isTrue();
    }

    @Test
    @DisplayName("searchUsers — returns paged user profiles")
    void searchUsers_success() {
        UserDetails currentUser = User.withUsername("alice").password("pwd").authorities("ROLE_USER").build();
        UserProfileDto bobProfile = UserProfileDto.builder().id(2L).username("bob").fullname("Bob").build();
        Page<UserProfileDto> page = new PageImpl<>(List.of(bobProfile), PageRequest.of(0, 20), 1);

        when(userService.searchUsers(eq("bo"), eq("alice"), any())).thenReturn(page);

        ResponseEntity<ApiResponse<Page<UserProfileDto>>> response =
                userController.searchUsers("bo", 0, 20, currentUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getUserProfile — returns user profile by id")
    void getUserProfile_success() {
        UserProfileDto profile = UserProfileDto.builder().id(1L).username("alice").fullname("Alice").build();
        when(userService.getUserProfile(1L)).thenReturn(profile);

        ResponseEntity<ApiResponse<UserProfileDto>> response = userController.getUserProfile(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getUsername()).isEqualTo("alice");
    }
}
