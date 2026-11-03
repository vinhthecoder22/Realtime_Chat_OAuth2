package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.response.FriendRequestDto;
import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import com.example.realtimechatonline.domain.entity.FriendRequest;
import com.example.realtimechatonline.domain.entity.FriendRequestStatus;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.domain.mapper.FriendRequestMapper;
import com.example.realtimechatonline.domain.mapper.UserMapper;
import com.example.realtimechatonline.repository.FriendRequestRepository;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.impl.FriendRequestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendRequestServiceTest {

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendRequestMapper friendRequestMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private FriendRequestServiceImpl friendRequestService;

    private User userA;
    private User userB;
    private User userC;

    @BeforeEach
    void setUp() {
        userA = User.builder().id(1L).username("userA").build();
        userB = User.builder().id(2L).username("userB").build();
        userC = User.builder().id(3L).username("userC").build();
    }

    @Test
    void sendRequest_cannotSendToSelf() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> friendRequestService.sendRequest(1L, "userA"));

        assertEquals("Cannot send a friend request to yourself", exception.getMessage());
    }

    @Test
    void sendRequest_receiverNotFound() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> friendRequestService.sendRequest(2L, "userA"));

        assertEquals("Receiver not found", exception.getMessage());
    }

    @Test
    void sendRequest_success() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userB));
        when(friendRequestRepository.findByUser1AndUser2(userA, userB)).thenReturn(Optional.empty());

        FriendRequest savedReq = FriendRequest.builder().id(1L).status(FriendRequestStatus.PENDING).build();
        when(friendRequestRepository.save(any())).thenReturn(savedReq);
        when(friendRequestMapper.toFriendRequestDto(savedReq)).thenReturn(new FriendRequestDto());

        assertNotNull(friendRequestService.sendRequest(2L, "userA"));
    }

    @Test
    void sendRequest_alreadyFriends() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userB));

        FriendRequest existing = FriendRequest.builder()
                .user1(userA).user2(userB).status(FriendRequestStatus.ACCEPTED).build();
        when(friendRequestRepository.findByUser1AndUser2(userA, userB)).thenReturn(Optional.of(existing));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> friendRequestService.sendRequest(2L, "userA"));
        assertEquals("Users are already friends", exception.getMessage());
    }

    @Test
    void sendRequest_alreadySent() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userB));

        FriendRequest existing = FriendRequest.builder()
                .user1(userA).user2(userB).sender(userA).status(FriendRequestStatus.PENDING).build();
        when(friendRequestRepository.findByUser1AndUser2(userA, userB)).thenReturn(Optional.of(existing));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> friendRequestService.sendRequest(2L, "userA"));
        assertEquals("Friend request already sent", exception.getMessage());
    }

    @Test
    void sendRequest_reverseRequestPending_throwsException() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userB));

        FriendRequest existing = FriendRequest.builder()
                .user1(userA).user2(userB).sender(userB).status(FriendRequestStatus.PENDING).build();
        when(friendRequestRepository.findByUser1AndUser2(userA, userB)).thenReturn(Optional.of(existing));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> friendRequestService.sendRequest(2L, "userA"));
        assertEquals("Receiver has already sent a friend request to you. Please accept their request.", exception.getMessage());
    }

    @Test
    void sendRequest_recycleRejectedRequest() {
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userB));

        FriendRequest existing = FriendRequest.builder()
                .user1(userA).user2(userB).sender(userB).status(FriendRequestStatus.REJECTED).build();
        when(friendRequestRepository.findByUser1AndUser2(userA, userB)).thenReturn(Optional.of(existing));
        when(friendRequestRepository.save(any())).thenReturn(existing);
        when(friendRequestMapper.toFriendRequestDto(existing)).thenReturn(new FriendRequestDto());

        friendRequestService.sendRequest(2L, "userA");

        assertEquals(FriendRequestStatus.PENDING, existing.getStatus());
        assertEquals(userA, existing.getSender());
    }

    @Test
    void acceptRequest_success() {
        FriendRequest request = FriendRequest.builder()
                .id(10L).user1(userA).user2(userB).sender(userA).status(FriendRequestStatus.PENDING).build();

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(friendRequestRepository.save(any())).thenReturn(request);
        when(friendRequestMapper.toFriendRequestDto(request)).thenReturn(new FriendRequestDto());

        friendRequestService.acceptRequest(10L, "userB");
        assertEquals(FriendRequestStatus.ACCEPTED, request.getStatus());
    }

    @Test
    void acceptRequest_cannotAcceptOwnRequest() {
        FriendRequest request = FriendRequest.builder()
                .id(10L).user1(userA).user2(userB).sender(userA).status(FriendRequestStatus.PENDING).build();

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> friendRequestService.acceptRequest(10L, "userA"));
        assertEquals("You cannot accept your own request", exception.getMessage());
    }

    @Test
    void acceptRequest_notPartOfRequest() {
        FriendRequest request = FriendRequest.builder()
                .id(10L).user1(userA).user2(userB).sender(userA).status(FriendRequestStatus.PENDING).build();

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("userC")).thenReturn(Optional.of(userC));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> friendRequestService.acceptRequest(10L, "userC"));
        assertEquals("You are not part of this request", exception.getMessage());
    }

    @Test
    void acceptRequest_notPending() {
        FriendRequest request = FriendRequest.builder()
                .id(10L).user1(userA).user2(userB).sender(userA).status(FriendRequestStatus.REJECTED).build();

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> friendRequestService.acceptRequest(10L, "userB"));
        assertEquals("Friend request is not in PENDING status", exception.getMessage());
    }

    @Test
    void rejectRequest_success() {
        FriendRequest request = FriendRequest.builder()
                .id(10L).user1(userA).user2(userB).sender(userA).status(FriendRequestStatus.PENDING).build();

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(friendRequestRepository.save(any())).thenReturn(request);
        when(friendRequestMapper.toFriendRequestDto(request)).thenReturn(new FriendRequestDto());

        friendRequestService.rejectRequest(10L, "userB");
        assertEquals(FriendRequestStatus.REJECTED, request.getStatus());
    }

    @Test
    void cancelRequest_onlySenderCanCancel() {
        FriendRequest request = FriendRequest.builder()
                .id(10L).user1(userA).user2(userB).sender(userA).status(FriendRequestStatus.PENDING).build();

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> friendRequestService.cancelRequest(10L, "userB"));
        assertEquals("You can only cancel requests you sent", exception.getMessage());
    }

    @Test
    void cancelRequest_success() {
        FriendRequest request = FriendRequest.builder()
                .id(10L).user1(userA).user2(userB).sender(userA).status(FriendRequestStatus.PENDING).build();

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("userA")).thenReturn(Optional.of(userA));

        friendRequestService.cancelRequest(10L, "userA");
        verify(friendRequestRepository, times(1)).delete(request);
    }

    @Test
    void getFriends_success() {
        FriendRequest request = FriendRequest.builder()
                .id(10L).user1(userA).user2(userB).sender(userA).status(FriendRequestStatus.ACCEPTED).build();

        when(userRepository.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(friendRequestRepository.findFriends(userB, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(Collections.singletonList(request)));
        when(userMapper.toUserProfile(userA)).thenReturn(new UserProfileDto());

        Page<UserProfileDto> friends = friendRequestService.getFriends("userB", PageRequest.of(0, 10));
        assertEquals(1, friends.getTotalElements());
        verify(userMapper, times(1)).toUserProfile(userA);
    }
}
