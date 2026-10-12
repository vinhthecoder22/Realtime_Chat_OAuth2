package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.response.FriendRequestDto;
import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FriendRequestService {

    FriendRequestDto sendRequest(Long receiverId, String currentUsername);

    Page<FriendRequestDto> getReceivedRequests(String currentUsername, Pageable pageable);

    Page<FriendRequestDto> getSentRequests(String currentUsername, Pageable pageable);

    FriendRequestDto acceptRequest(Long requestId, String currentUsername);

    FriendRequestDto rejectRequest(Long requestId, String currentUsername);

    void cancelRequest(Long requestId, String currentUsername);

    Page<UserProfileDto> getFriends(String currentUsername, Pageable pageable);
}
