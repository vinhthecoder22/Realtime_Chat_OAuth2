package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import com.example.realtimechatonline.domain.entity.Role;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.domain.mapper.UserMapper;
import com.example.realtimechatonline.exception.extended.ResourceNotFoundException;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl unit tests")
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserMapper userMapper;

    @InjectMocks UserServiceImpl userService;

    private User alice;
    private User bob;
    private UserProfileDto bobProfile;

    @BeforeEach
    void setUp() {
        alice = User.builder().id(1L).username("alice").role(Role.USER).build();
        bob = User.builder().id(2L).username("bob").fullname("Bob Smith").role(Role.USER).build();
        bobProfile = UserProfileDto.builder().id(2L).username("bob").fullname("Bob Smith").build();
    }

    @Test
    @DisplayName("searchUsers — returns matching users excluding current user")
    void searchUsers_success() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        Page<User> page = new PageImpl<>(List.of(bob));
        when(userRepository.searchByKeyword(eq("bob"), eq(1L), any(Pageable.class))).thenReturn(page);
        when(userMapper.toUserProfile(bob)).thenReturn(bobProfile);

        Pageable pageable = PageRequest.of(0, 20, Sort.by("username").ascending());
        Page<UserProfileDto> result = userService.searchUsers("bob", "alice", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("bob");
        verify(userRepository).searchByKeyword("bob", 1L, pageable);
    }

    @Test
    @DisplayName("searchUsers — returns empty for blank query")
    void searchUsers_blankQuery_returnsEmpty() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserProfileDto> result = userService.searchUsers("", "alice", pageable);

        assertThat(result.getContent()).isEmpty();
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("searchUsers — returns empty for single-char query")
    void searchUsers_shortQuery_returnsEmpty() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserProfileDto> result = userService.searchUsers("a", "alice", pageable);

        assertThat(result.getContent()).isEmpty();
        verify(userRepository, never()).searchByKeyword(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("searchUsers — enforces max page size of 50")
    void searchUsers_clipsPageSize() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.searchByKeyword(anyString(), anyLong(), any(Pageable.class))).thenReturn(emptyPage);

        Pageable oversized = PageRequest.of(0, 200);
        userService.searchUsers("test", "alice", oversized);

        verify(userRepository).searchByKeyword(eq("test"), eq(1L), argThat(p -> p.getPageSize() <= 50));
    }

    @Test
    @DisplayName("getUserProfile — returns safe DTO")
    void getUserProfile_success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(userMapper.toUserProfile(bob)).thenReturn(bobProfile);

        UserProfileDto result = userService.getUserProfile(2L);

        assertThat(result.getUsername()).isEqualTo("bob");
        assertThat(result.getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getUserProfile — throws ResourceNotFoundException for invalid ID")
    void getUserProfile_notFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("UserProfileDto — does not contain password or email fields")
    void userProfileDto_noSensitiveFields() {
        // Verify at compile time and runtime that the DTO has no password/email accessors
        UserProfileDto dto = UserProfileDto.builder()
                .id(1L).username("alice").fullname("Alice").picture(null).build();

        // Only these fields exist
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getUsername()).isEqualTo("alice");
        assertThat(dto.getFullname()).isEqualTo("Alice");
        assertThat(dto.getPicture()).isNull();

        // Verify via reflection that no password/email getters exist
        assertThatThrownBy(() -> dto.getClass().getMethod("getPassword"))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> dto.getClass().getMethod("getEmail"))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> dto.getClass().getMethod("getRole"))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> dto.getClass().getMethod("getAuthProvider"))
                .isInstanceOf(NoSuchMethodException.class);
    }
}
