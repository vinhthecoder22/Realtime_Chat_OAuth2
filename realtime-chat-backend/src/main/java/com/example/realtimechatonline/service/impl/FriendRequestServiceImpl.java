package com.example.realtimechatonline.service.impl;

import com.example.realtimechatonline.domain.dto.response.FriendRequestDto;
import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import com.example.realtimechatonline.domain.entity.FriendRequest;
import com.example.realtimechatonline.domain.entity.FriendRequestStatus;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.domain.mapper.FriendRequestMapper;
import com.example.realtimechatonline.domain.mapper.UserMapper;
import com.example.realtimechatonline.repository.FriendRequestRepository;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.FriendRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendRequestServiceImpl implements FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final FriendRequestMapper friendRequestMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public FriendRequestDto sendRequest(Long receiverId, String currentUsername) {
        User sender = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (sender.getId().equals(receiverId)) {
            throw new IllegalArgumentException("Cannot send a friend request to yourself");
        }

        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new IllegalArgumentException("Receiver not found"));

        User user1 = sender.getId() < receiver.getId() ? sender : receiver;
        User user2 = sender.getId() > receiver.getId() ? sender : receiver;

        Optional<FriendRequest> existingOpt = friendRequestRepository.findByUser1AndUser2(user1, user2);

        if (existingOpt.isPresent()) {
            FriendRequest existing = existingOpt.get();
            if (existing.getStatus() == FriendRequestStatus.ACCEPTED) {
                throw new IllegalArgumentException("Users are already friends");
            }
            if (existing.getStatus() == FriendRequestStatus.PENDING) {
                if (existing.getSender().getId().equals(sender.getId())) {
                    throw new IllegalArgumentException("Friend request already sent");
                } else {
                    throw new IllegalArgumentException("Receiver has already sent a friend request to you. Please accept their request.");
                }
            }

            existing.setSender(sender);
            existing.setStatus(FriendRequestStatus.PENDING);
            return friendRequestMapper.toFriendRequestDto(friendRequestRepository.save(existing));
        }

        FriendRequest friendRequest = FriendRequest.builder()
                .user1(user1)
                .user2(user2)
                .sender(sender)
                .status(FriendRequestStatus.PENDING)
                .build();

        return friendRequestMapper.toFriendRequestDto(friendRequestRepository.save(friendRequest));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FriendRequestDto> getReceivedRequests(String currentUsername, Pageable pageable) {
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return friendRequestRepository.findReceivedRequests(user, FriendRequestStatus.PENDING, pageable)
                .map(friendRequestMapper::toFriendRequestDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FriendRequestDto> getSentRequests(String currentUsername, Pageable pageable) {
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return friendRequestRepository.findBySenderAndStatus(user, FriendRequestStatus.PENDING, pageable)
                .map(friendRequestMapper::toFriendRequestDto);
    }

    @Override
    @Transactional
    public FriendRequestDto acceptRequest(Long requestId, String currentUsername) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!request.getUser1().getId().equals(user.getId()) && !request.getUser2().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You are not part of this request");
        }

        if (request.getSender().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You cannot accept your own request");
        }

        if (request.getStatus() != FriendRequestStatus.PENDING) {
            throw new IllegalArgumentException("Friend request is not in PENDING status");
        }

        request.setStatus(FriendRequestStatus.ACCEPTED);
        return friendRequestMapper.toFriendRequestDto(friendRequestRepository.save(request));
    }

    @Override
    @Transactional
    public FriendRequestDto rejectRequest(Long requestId, String currentUsername) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!request.getUser1().getId().equals(user.getId()) && !request.getUser2().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You are not part of this request");
        }

        if (request.getSender().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You cannot reject your own request");
        }

        if (request.getStatus() != FriendRequestStatus.PENDING) {
            throw new IllegalArgumentException("Friend request is not in PENDING status");
        }

        request.setStatus(FriendRequestStatus.REJECTED);
        return friendRequestMapper.toFriendRequestDto(friendRequestRepository.save(request));
    }

    @Override
    @Transactional
    public void cancelRequest(Long requestId, String currentUsername) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!request.getSender().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You can only cancel requests you sent");
        }

        if (request.getStatus() != FriendRequestStatus.PENDING) {
            throw new IllegalArgumentException("You can only cancel PENDING requests");
        }

        friendRequestRepository.delete(request);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserProfileDto> getFriends(String currentUsername, Pageable pageable) {
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return friendRequestRepository.findFriends(user, pageable)
                .map(request -> {
                    User friend = request.getUser1().getId().equals(user.getId()) ? request.getUser2() : request.getUser1();
                    return userMapper.toUserProfile(friend);
                });
    }
}
