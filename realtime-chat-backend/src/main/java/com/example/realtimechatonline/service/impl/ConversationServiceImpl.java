package com.example.realtimechatonline.service.impl;

import com.example.realtimechatonline.domain.dto.request.*;
import com.example.realtimechatonline.domain.dto.response.ConversationDto;
import com.example.realtimechatonline.domain.dto.response.GroupMemberResponseDto;
import com.example.realtimechatonline.domain.dto.response.ReadReceiptResponseDto;
import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import com.example.realtimechatonline.domain.dto.websocket.UserEventDto;
import com.example.realtimechatonline.domain.entity.*;
import com.example.realtimechatonline.domain.mapper.UserMapper;
import com.example.realtimechatonline.exception.extended.ResourceNotFoundException;
import com.example.realtimechatonline.repository.ConversationMemberRepository;
import com.example.realtimechatonline.repository.ConversationRepository;
import com.example.realtimechatonline.repository.FriendRequestRepository;
import com.example.realtimechatonline.repository.MessageRepository;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.ConversationService;
import com.example.realtimechatonline.service.GroupMembershipService;
import com.example.realtimechatonline.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final GroupMembershipService groupMembershipService;

    @Override
    @Transactional
    public ConversationDto getOrCreateDirectConversation(Long targetUserId, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (currentUser.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot create a conversation with yourself");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        User user1 = currentUser.getId() < targetUser.getId() ? currentUser : targetUser;
        User user2 = currentUser.getId() > targetUser.getId() ? currentUser : targetUser;

        // Verify friendship
        Optional<FriendRequest> friendship = friendRequestRepository.findByUser1AndUser2(user1, user2);
        if (friendship.isEmpty() || friendship.get().getStatus() != FriendRequestStatus.ACCEPTED) {
            throw new IllegalArgumentException("You must be friends to start a direct chat");
        }

        String conversationName = "direct_" + user1.getId() + "_" + user2.getId();
        Optional<Conversation> existingOpt = conversationRepository.findByTypeAndName(ConversationType.DIRECT, conversationName);

        Conversation conversation;
        if (existingOpt.isPresent()) {
            conversation = existingOpt.get();
        } else {
            conversation = Conversation.builder()
                    .type(ConversationType.DIRECT)
                    .name(conversationName)
                    .createdAt(Instant.now())
                    .build();
            conversation = conversationRepository.save(conversation);

            ConversationMember member1 = ConversationMember.builder()
                    .conversation(conversation)
                    .user(user1)
                    .role(ConversationMemberRole.MEMBER)
                    .joinedAt(Instant.now())
                    .build();
            ConversationMember member2 = ConversationMember.builder()
                    .conversation(conversation)
                    .user(user2)
                    .role(ConversationMemberRole.MEMBER)
                    .joinedAt(Instant.now())
                    .build();
            conversationMemberRepository.saveAll(List.of(member1, member2));
        }

        Optional<Message> latestMsgOpt = messageRepository.findLatestMessageByConversationId(conversation.getId());
        Message latestMsg = latestMsgOpt.orElse(null);

        ConversationMember currentCm = conversationMemberRepository.findByConversationIdAndUserId(conversation.getId(), currentUser.getId()).orElse(null);
        ConversationMember otherCm = conversationMemberRepository.findByConversationIdAndUserId(conversation.getId(), targetUser.getId()).orElse(null);
        long unreadCount = messageRepository.countUnreadMessages(conversation.getId(), currentUser.getId());

        String displayName = (targetUser.getFullname() != null && !targetUser.getFullname().isBlank())
                ? targetUser.getFullname()
                : targetUser.getUsername();

        UserProfileDto otherUserProfile = userMapper.toUserProfile(targetUser);
        if (otherUserProfile != null && presenceService != null) {
            otherUserProfile.setIsOnline(presenceService.isUserOnline(otherUserProfile.getUsername()));
            otherUserProfile.setLastSeenAt(presenceService.getLastSeenAt(otherUserProfile.getUsername()));
        }

        return ConversationDto.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .name(displayName)
                .avatar(targetUser.getPicture())
                .otherUser(otherUserProfile)
                .lastMessage(latestMsg != null ? latestMsg.getContent() : null)
                .lastMessageSender(latestMsg != null && latestMsg.getSender() != null ? latestMsg.getSender().getUsername() : null)
                .lastMessageTimestamp(latestMsg != null ? latestMsg.getTimestamp() : null)
                .lastActivityAt(latestMsg != null ? latestMsg.getTimestamp() : conversation.getCreatedAt())
                .createdAt(conversation.getCreatedAt())
                .unreadCount(unreadCount)
                .lastReadMessageId(currentCm != null ? currentCm.getLastReadMessageId() : null)
                .otherLastReadMessageId(otherCm != null ? otherCm.getLastReadMessageId() : null)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationDto> getConversations(String currentUsername) {
        return getConversations(currentUsername, PageRequest.of(0, 50)).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConversationDto> getConversations(String currentUsername, Pageable pageable) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();
        if (size <= 0) size = 20;
        if (size > 50) size = 50;
        Pageable clampedPageable = PageRequest.of(page, size);

        Page<Conversation> convPage = conversationRepository.findUserConversationsOrderByLastActivity(
                currentUser.getId(), clampedPageable);

        List<Conversation> conversations = convPage.getContent();
        if (conversations.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), clampedPageable, convPage.getTotalElements());
        }

        List<Long> convIds = conversations.stream().map(Conversation::getId).toList();

        // 1. Batch fetch latest messages for all conversations
        List<Message> latestMessages = messageRepository.findLatestMessagesByConversationIds(convIds);
        Map<Long, Message> latestMessageMap = latestMessages.stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), m -> m, (m1, m2) -> m1));

        // 2. Batch fetch members for DIRECT conversations
        List<Long> directConvIds = conversations.stream()
                .filter(c -> c.getType() == ConversationType.DIRECT)
                .map(Conversation::getId)
                .toList();

        Map<Long, List<ConversationMember>> membersMap = directConvIds.isEmpty() ? Collections.emptyMap() :
                conversationMemberRepository.findByConversationIdIn(directConvIds).stream()
                        .collect(Collectors.groupingBy(cm -> cm.getConversation().getId()));

        // Batch fetch members for currentUser across all conversations
        Map<Long, ConversationMember> currentUserMembersMap = conversationMemberRepository.findByUserIdAndConversationIdIn(currentUser.getId(), convIds)
                .stream().collect(Collectors.toMap(cm -> cm.getConversation().getId(), cm -> cm, (cm1, cm2) -> cm1));

        // Batch fetch active member counts for all conversations
        List<Object[]> memberCountsRaw = conversationMemberRepository.countMembersByConversationIdInAndStatus(convIds, ConversationMemberStatus.ACTIVE);
        Map<Long, Long> memberCountMap = memberCountsRaw.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        // 3. Batch fetch unread counts for all conversations
        List<Object[]> unreadCountsRaw = messageRepository.countUnreadMessagesByConversationIds(convIds, currentUser.getId());
        Map<Long, Long> unreadCountMap = unreadCountsRaw.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        List<ConversationDto> dtos = conversations.stream().map(conversation -> {
            ConversationMember currentMember = currentUserMembersMap.get(conversation.getId());
            User otherUser = null;
            ConversationMember otherMember = null;

            if (conversation.getType() == ConversationType.DIRECT) {
                List<ConversationMember> members = membersMap.getOrDefault(conversation.getId(), Collections.emptyList());
                for (ConversationMember member : members) {
                    if (member.getUser().getId().equals(currentUser.getId())) {
                        currentMember = member;
                    } else {
                        otherMember = member;
                        otherUser = member.getUser();
                    }
                }
            }

            UserProfileDto otherUserProfile = otherUser != null ? userMapper.toUserProfile(otherUser) : null;
            if (otherUserProfile != null && presenceService != null) {
                otherUserProfile.setIsOnline(presenceService.isUserOnline(otherUserProfile.getUsername()));
                otherUserProfile.setLastSeenAt(presenceService.getLastSeenAt(otherUserProfile.getUsername()));
            }
            Message latestMsg = latestMessageMap.get(conversation.getId());

            String displayName;
            String avatar = null;
            Long memberCount = null;
            ConversationMemberRole currentUserRole = null;

            if (conversation.getType() == ConversationType.DIRECT) {
                if (otherUserProfile != null) {
                    displayName = (otherUserProfile.getFullname() != null && !otherUserProfile.getFullname().isBlank())
                            ? otherUserProfile.getFullname()
                            : otherUserProfile.getUsername();
                    avatar = otherUserProfile.getPicture();
                } else {
                    displayName = "Unknown User";
                }
            } else if (conversation.getType() == ConversationType.GROUP) {
                displayName = (conversation.getName() != null && !conversation.getName().isBlank())
                        ? conversation.getName()
                        : "Group Chat";
                avatar = conversation.getAvatar();
                memberCount = memberCountMap.getOrDefault(conversation.getId(), 0L);
                currentUserRole = currentMember != null ? currentMember.getRole() : null;
            } else {
                displayName = "Global Chat";
            }

            Instant lastActivity = latestMsg != null ? latestMsg.getTimestamp() : conversation.getCreatedAt();
            long unread = unreadCountMap.getOrDefault(conversation.getId(), 0L);

            return ConversationDto.builder()
                    .id(conversation.getId())
                    .type(conversation.getType())
                    .name(displayName)
                    .avatar(avatar)
                    .otherUser(otherUserProfile)
                    .lastMessage(latestMsg != null ? latestMsg.getContent() : null)
                    .lastMessageSender(latestMsg != null && latestMsg.getSender() != null ? latestMsg.getSender().getUsername() : null)
                    .lastMessageTimestamp(latestMsg != null ? latestMsg.getTimestamp() : null)
                    .lastActivityAt(lastActivity)
                    .createdAt(conversation.getCreatedAt())
                    .unreadCount(unread)
                    .memberCount(memberCount)
                    .currentUserRole(currentUserRole)
                    .lastReadMessageId(currentMember != null ? currentMember.getLastReadMessageId() : null)
                    .otherLastReadMessageId(otherMember != null ? otherMember.getLastReadMessageId() : null)
                    .build();
        }).toList();

        return new PageImpl<>(dtos, clampedPageable, convPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationDto getConversationById(Long conversationId, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        User otherUser = null;
        ConversationMember currentMember = null;
        ConversationMember otherMember = null;

        if (conversation.getType() == ConversationType.DIRECT) {
            if (!conversationMemberRepository.existsByConversationIdAndUserId(conversation.getId(), currentUser.getId())) {
                throw new AccessDeniedException("User is not a member of this direct conversation");
            }
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversation.getId());
            for (ConversationMember member : members) {
                if (member.getUser().getId().equals(currentUser.getId())) {
                    currentMember = member;
                } else {
                    otherMember = member;
                    otherUser = member.getUser();
                }
            }
        } else if (conversation.getType() == ConversationType.GROUP) {
            currentMember = conversationMemberRepository.findByConversationIdAndUserIdAndStatus(
                            conversation.getId(), currentUser.getId(), ConversationMemberStatus.ACTIVE)
                    .orElseThrow(() -> new AccessDeniedException("User is not an active member of this group"));
        } else {
            // PUBLIC
            currentMember = conversationMemberRepository.findByConversationIdAndUserId(conversation.getId(), currentUser.getId()).orElse(null);
        }

        UserProfileDto otherUserProfile = otherUser != null ? userMapper.toUserProfile(otherUser) : null;
        if (otherUserProfile != null && presenceService != null) {
            otherUserProfile.setIsOnline(presenceService.isUserOnline(otherUserProfile.getUsername()));
            otherUserProfile.setLastSeenAt(presenceService.getLastSeenAt(otherUserProfile.getUsername()));
        }
        Optional<Message> latestMsgOpt = messageRepository.findLatestMessageByConversationId(conversation.getId());
        Message latestMsg = latestMsgOpt.orElse(null);

        String displayName;
        String avatar = null;
        Long memberCount = null;
        ConversationMemberRole currentUserRole = null;

        if (conversation.getType() == ConversationType.DIRECT) {
            if (otherUserProfile != null) {
                displayName = (otherUserProfile.getFullname() != null && !otherUserProfile.getFullname().isBlank())
                        ? otherUserProfile.getFullname()
                        : otherUserProfile.getUsername();
                avatar = otherUserProfile.getPicture();
            } else {
                displayName = "Unknown User";
            }
        } else if (conversation.getType() == ConversationType.GROUP) {
            displayName = (conversation.getName() != null && !conversation.getName().isBlank())
                    ? conversation.getName()
                    : "Group Chat";
            avatar = conversation.getAvatar();
            memberCount = conversationMemberRepository.countByConversationIdAndStatus(conversation.getId(), ConversationMemberStatus.ACTIVE);
            currentUserRole = currentMember != null ? currentMember.getRole() : null;
        } else {
            displayName = "Global Chat";
        }

        Instant lastActivity = latestMsg != null ? latestMsg.getTimestamp() : conversation.getCreatedAt();
        long unread = messageRepository.countUnreadMessages(conversation.getId(), currentUser.getId());

        return ConversationDto.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .name(displayName)
                .avatar(avatar)
                .otherUser(otherUserProfile)
                .lastMessage(latestMsg != null ? latestMsg.getContent() : null)
                .lastMessageSender(latestMsg != null && latestMsg.getSender() != null ? latestMsg.getSender().getUsername() : null)
                .lastMessageTimestamp(latestMsg != null ? latestMsg.getTimestamp() : null)
                .lastActivityAt(lastActivity)
                .createdAt(conversation.getCreatedAt())
                .unreadCount(unread)
                .memberCount(memberCount)
                .currentUserRole(currentUserRole)
                .lastReadMessageId(currentMember != null ? currentMember.getLastReadMessageId() : null)
                .otherLastReadMessageId(otherMember != null ? otherMember.getLastReadMessageId() : null)
                .build();
    }

    @Override
    @Transactional
    public ReadReceiptResponseDto markConversationAsRead(Long conversationId, Long messageId, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (conversation.getType() == ConversationType.DIRECT) {
            if (!conversationMemberRepository.existsByConversationIdAndUserId(conversation.getId(), currentUser.getId())) {
                throw new AccessDeniedException("User is not a member of this direct conversation");
            }
        } else if (conversation.getType() == ConversationType.GROUP) {
            if (!conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(conversation.getId(), currentUser.getId(), ConversationMemberStatus.ACTIVE)) {
                throw new AccessDeniedException("User is not an active member of this group");
            }
        }

        // Get or create membership
        ConversationMember member = conversationMemberRepository.findByConversationIdAndUserId(conversation.getId(), currentUser.getId())
                .orElseGet(() -> {
                    if (conversation.getType() == ConversationType.PUBLIC) {
                        ConversationMember newMember = ConversationMember.builder()
                                .conversation(conversation)
                                .user(currentUser)
                                .role(ConversationMemberRole.MEMBER)
                                .joinedAt(Instant.now())
                                .build();
                        return conversationMemberRepository.save(newMember);
                    }
                    throw new AccessDeniedException("User is not a member of this conversation");
                });

        Optional<Message> latestMsgOpt = messageRepository.findLatestMessageByConversationId(conversation.getId());
        Long targetReadId = null;

        if (latestMsgOpt.isPresent()) {
            Message latestMsg = latestMsgOpt.get();
            if (messageId != null) {
                Message requestedMsg = messageRepository.findById(messageId)
                        .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
                if (!requestedMsg.getConversation().getId().equals(conversation.getId())) {
                    throw new IllegalArgumentException("Message " + messageId + " does not belong to conversation " + conversation.getId());
                }
                targetReadId = Math.min(requestedMsg.getId(), latestMsg.getId());
            } else {
                targetReadId = latestMsg.getId();
            }
        } else {
            if (messageId != null) {
                throw new ResourceNotFoundException("Message not found: " + messageId);
            }
        }

        // Monotonic cursor update with database-level concurrency protection
        if (targetReadId != null) {
            Long currentReadId = member.getLastReadMessageId();
            if (currentReadId == null || targetReadId > currentReadId) {
                Instant now = Instant.now();
                int updatedRows = conversationMemberRepository.updateLastReadIfGreater(
                        member.getId(), targetReadId, now);
                if (updatedRows > 0) {
                    member.setLastReadMessageId(targetReadId);
                    member.setLastReadAt(now);
                } else {
                    // Another concurrent request already advanced lastReadMessageId past targetReadId
                    member = conversationMemberRepository.findById(member.getId()).orElse(member);
                }
            }
        }

        long unreadCount = messageRepository.countUnreadMessages(conversation.getId(), currentUser.getId());

        ReadReceiptResponseDto response = ReadReceiptResponseDto.builder()
                .conversationId(conversation.getId())
                .userId(currentUser.getId())
                .username(currentUser.getUsername())
                .lastReadMessageId(member.getLastReadMessageId())
                .readAt(member.getLastReadAt() != null ? member.getLastReadAt() : Instant.now())
                .unreadCount(unreadCount)
                .build();

        // Broadcast realtime read receipt
        broadcastReadReceipt(conversation, currentUser, member.getLastReadMessageId());

        return response;
    }

    private void broadcastReadReceipt(Conversation conversation, User reader, Long lastReadMessageId) {
        if (messagingTemplate == null) return;

        UserEventDto event = UserEventDto.builder()
                .type("READ_RECEIPT")
                .conversationId(conversation.getId())
                .readerUsername(reader.getUsername())
                .lastReadMessageId(lastReadMessageId)
                .unreadCount(0L)
                .build();

        // 1. Sync multiple tabs of reader
        messagingTemplate.convertAndSendToUser(reader.getUsername(), "/queue/events", event);

        // 2. In DIRECT conversation, notify partner and active room
        if (conversation.getType() == ConversationType.DIRECT) {
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversation.getId());
            for (ConversationMember cm : members) {
                if (!cm.getUser().getId().equals(reader.getId())) {
                    messagingTemplate.convertAndSendToUser(cm.getUser().getUsername(), "/queue/events", event);
                }
            }
            messagingTemplate.convertAndSend("/topic/conversation." + conversation.getId(), event);
        }
    }

    @Override
    @Transactional
    public ConversationDto createGroupConversation(CreateGroupRequestDto request, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request == null) {
            throw new IllegalArgumentException("Request body cannot be null");
        }

        String groupName = request.getName() != null ? request.getName().trim() : "";
        if (groupName.isEmpty() || groupName.length() > 100) {
            throw new IllegalArgumentException("Group name must be between 1 and 100 characters");
        }

        List<Long> memberUserIds = request.getMemberUserIds();
        if (memberUserIds == null || memberUserIds.isEmpty()) {
            throw new IllegalArgumentException("memberUserIds must not be empty");
        }

        Set<Long> uniqueMemberIds = new LinkedHashSet<>(memberUserIds);
        if (uniqueMemberIds.contains(currentUser.getId())) {
            throw new IllegalArgumentException("Cannot add yourself to member list");
        }

        List<User> invitedUsers = new ArrayList<>();
        for (Long memberId : uniqueMemberIds) {
            if (memberId == null || memberId <= 0) {
                throw new IllegalArgumentException("Invalid user ID: " + memberId);
            }
            User memberUser = userRepository.findById(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + memberId));

            if (!areFriends(currentUser, memberUser)) {
                throw new IllegalArgumentException("User " + memberUser.getUsername() + " is not your friend");
            }
            invitedUsers.add(memberUser);
        }

        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
                .name(groupName)
                .creator(currentUser)
                .createdAt(Instant.now())
                .build();
        conversation = conversationRepository.save(conversation);

        Instant now = Instant.now();
        List<ConversationMember> membersToSave = new ArrayList<>();

        ConversationMember adminMember = ConversationMember.builder()
                .conversation(conversation)
                .user(currentUser)
                .role(ConversationMemberRole.ADMIN)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(now)
                .build();
        membersToSave.add(adminMember);

        for (User invitedUser : invitedUsers) {
            ConversationMember member = ConversationMember.builder()
                    .conversation(conversation)
                    .user(invitedUser)
                    .role(ConversationMemberRole.MEMBER)
                    .status(ConversationMemberStatus.ACTIVE)
                    .joinedAt(now)
                    .build();
            membersToSave.add(member);
        }

        conversationMemberRepository.saveAll(membersToSave);

        if (groupMembershipService != null) {
            groupMembershipService.invalidateGroup(conversation.getId());
        }

        return ConversationDto.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .name(conversation.getName())
                .avatar(conversation.getAvatar())
                .createdAt(conversation.getCreatedAt())
                .lastActivityAt(conversation.getCreatedAt())
                .unreadCount(0L)
                .memberCount((long) membersToSave.size())
                .currentUserRole(ConversationMemberRole.ADMIN)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupMemberResponseDto> getGroupMembers(Long conversationId, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new IllegalArgumentException("Conversation is not a group");
        }

        boolean isActive = conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(
                conversationId, currentUser.getId(), ConversationMemberStatus.ACTIVE);
        if (!isActive) {
            throw new AccessDeniedException("User is not an active member of this group");
        }

        List<ConversationMember> members = conversationMemberRepository.findActiveMembersWithUser(
                conversationId, ConversationMemberStatus.ACTIVE);

        return members.stream().map(cm -> {
            User u = cm.getUser();
            boolean isOnline = presenceService != null && presenceService.isUserOnline(u.getUsername());
            Instant lastSeenAt = presenceService != null ? presenceService.getLastSeenAt(u.getUsername()) : null;
            return GroupMemberResponseDto.builder()
                    .userId(u.getId())
                    .username(u.getUsername())
                    .fullname(u.getFullname())
                    .picture(u.getPicture())
                    .role(cm.getRole())
                    .isOnline(isOnline)
                    .lastSeenAt(lastSeenAt)
                    .joinedAt(cm.getJoinedAt())
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public List<GroupMemberResponseDto> addGroupMembers(Long conversationId, AddGroupMembersRequestDto request, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new IllegalArgumentException("Conversation is not a group");
        }

        ConversationMember callerMember = conversationMemberRepository.findByConversationIdAndUserIdAndStatus(
                        conversationId, currentUser.getId(), ConversationMemberStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("User is not an active member of this group"));

        if (callerMember.getRole() != ConversationMemberRole.ADMIN) {
            throw new AccessDeniedException("Only group ADMIN can add members");
        }

        if (request == null || request.getMemberUserIds() == null || request.getMemberUserIds().isEmpty()) {
            throw new IllegalArgumentException("memberUserIds must not be empty");
        }

        Set<Long> uniqueIds = new LinkedHashSet<>(request.getMemberUserIds());
        if (uniqueIds.contains(currentUser.getId())) {
            throw new IllegalArgumentException("Cannot add yourself");
        }

        Optional<Message> latestMsgOpt = messageRepository.findLatestMessageByConversationId(conversationId);
        Long latestMsgId = latestMsgOpt.map(Message::getId).orElse(null);
        Instant now = Instant.now();

        for (Long targetUserId : uniqueIds) {
            if (targetUserId == null || targetUserId <= 0) {
                throw new IllegalArgumentException("Invalid user ID: " + targetUserId);
            }
            User targetUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

            if (!areFriends(currentUser, targetUser)) {
                throw new IllegalArgumentException("User " + targetUser.getUsername() + " is not your friend");
            }

            Optional<ConversationMember> existingOpt = conversationMemberRepository.findByConversationIdAndUserId(conversationId, targetUserId);
            if (existingOpt.isPresent()) {
                ConversationMember existing = existingOpt.get();
                if (existing.getStatus() == ConversationMemberStatus.ACTIVE) {
                    throw new IllegalArgumentException("User " + targetUser.getUsername() + " is already an active member of this group");
                }
                // Reactivate member
                existing.setStatus(ConversationMemberStatus.ACTIVE);
                existing.setRole(ConversationMemberRole.MEMBER);
                existing.setJoinedAt(now);
                existing.setLeftAt(null);
                existing.setLastReadMessageId(latestMsgId);
                existing.setLastReadAt(now);
                conversationMemberRepository.save(existing);
            } else {
                // New member
                ConversationMember newMember = ConversationMember.builder()
                        .conversation(conversation)
                        .user(targetUser)
                        .role(ConversationMemberRole.MEMBER)
                        .status(ConversationMemberStatus.ACTIVE)
                        .joinedAt(now)
                        .lastReadMessageId(latestMsgId)
                        .lastReadAt(now)
                        .build();
                conversationMemberRepository.save(newMember);
            }
        }

        if (groupMembershipService != null) {
            groupMembershipService.invalidateGroup(conversationId);
        }

        return getGroupMembers(conversationId, currentUsername);
    }

    @Override
    @Transactional
    public void removeGroupMember(Long conversationId, Long targetUserId, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new IllegalArgumentException("Conversation is not a group");
        }

        ConversationMember callerMember = conversationMemberRepository.findByConversationIdAndUserIdAndStatus(
                        conversationId, currentUser.getId(), ConversationMemberStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("User is not an active member of this group"));

        if (callerMember.getRole() != ConversationMemberRole.ADMIN) {
            throw new AccessDeniedException("Only group ADMIN can remove members");
        }

        if (targetUserId.equals(currentUser.getId())) {
            throw new IllegalArgumentException("ADMIN cannot remove themselves. Use leave endpoint instead");
        }

        ConversationMember targetMember = conversationMemberRepository.findByConversationIdAndUserIdAndStatus(
                        conversationId, targetUserId, ConversationMemberStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Active member not found: " + targetUserId));

        targetMember.setStatus(ConversationMemberStatus.REMOVED);
        targetMember.setLeftAt(Instant.now());
        conversationMemberRepository.save(targetMember);

        if (groupMembershipService != null) {
            groupMembershipService.removeMember(conversationId, targetMember.getUser().getUsername());
        }

        if (messagingTemplate != null) {
            UserEventDto kickedEvent = UserEventDto.builder()
                    .type("MEMBER_KICKED")
                    .conversationId(conversationId)
                    .build();
            messagingTemplate.convertAndSendToUser(targetMember.getUser().getUsername(), "/queue/events", kickedEvent);
        }
    }

    @Override
    @Transactional
    public void leaveGroup(Long conversationId, LeaveGroupRequestDto request, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new IllegalArgumentException("Conversation is not a group");
        }

        ConversationMember callerMember = conversationMemberRepository.findByConversationIdAndUserIdAndStatus(
                        conversationId, currentUser.getId(), ConversationMemberStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("User is not an active member of this group"));

        Instant now = Instant.now();

        if (callerMember.getRole() == ConversationMemberRole.ADMIN) {
            List<ConversationMember> otherActiveMembers = conversationMemberRepository.findOtherActiveMembersOrderedByJoinedAt(
                    conversationId, currentUser.getId(), ConversationMemberStatus.ACTIVE);

            if (!otherActiveMembers.isEmpty()) {
                Long newAdminUserId = request != null ? request.getNewAdminUserId() : null;
                ConversationMember newAdminMember;
                if (newAdminUserId != null) {
                    if (newAdminUserId.equals(currentUser.getId())) {
                        throw new IllegalArgumentException("Cannot transfer admin to yourself");
                    }
                    newAdminMember = conversationMemberRepository.findByConversationIdAndUserIdAndStatus(
                                    conversationId, newAdminUserId, ConversationMemberStatus.ACTIVE)
                            .orElseThrow(() -> new IllegalArgumentException("Target user is not an active member of this group"));
                } else {
                    newAdminMember = otherActiveMembers.get(0);
                }
                newAdminMember.setRole(ConversationMemberRole.ADMIN);
                conversationMemberRepository.save(newAdminMember);
            }
        }

        callerMember.setStatus(ConversationMemberStatus.LEFT);
        callerMember.setRole(ConversationMemberRole.MEMBER);
        callerMember.setLeftAt(now);
        conversationMemberRepository.save(callerMember);

        if (groupMembershipService != null) {
            groupMembershipService.removeMember(conversationId, callerMember.getUser().getUsername());
        }
    }

    @Override
    @Transactional
    public void transferAdmin(Long conversationId, TransferAdminRequestDto request, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new IllegalArgumentException("Conversation is not a group");
        }

        ConversationMember callerMember = conversationMemberRepository.findByConversationIdAndUserIdAndStatus(
                        conversationId, currentUser.getId(), ConversationMemberStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("User is not an active member of this group"));

        if (callerMember.getRole() != ConversationMemberRole.ADMIN) {
            throw new AccessDeniedException("Only group ADMIN can transfer admin role");
        }

        if (request == null || request.getNewAdminUserId() == null) {
            throw new IllegalArgumentException("newAdminUserId is required");
        }

        if (request.getNewAdminUserId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Cannot transfer admin to yourself");
        }

        ConversationMember targetMember = conversationMemberRepository.findByConversationIdAndUserIdAndStatus(
                        conversationId, request.getNewAdminUserId(), ConversationMemberStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Target user is not an active member of this group"));

        targetMember.setRole(ConversationMemberRole.ADMIN);
        callerMember.setRole(ConversationMemberRole.MEMBER);
        conversationMemberRepository.saveAll(List.of(targetMember, callerMember));

        if (groupMembershipService != null) {
            groupMembershipService.invalidateGroup(conversationId);
        }
    }

    @Override
    @Transactional
    public ConversationDto updateGroup(Long conversationId, UpdateGroupRequestDto request, String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new IllegalArgumentException("Conversation is not a group");
        }

        ConversationMember callerMember = conversationMemberRepository.findByConversationIdAndUserIdAndStatus(
                        conversationId, currentUser.getId(), ConversationMemberStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("User is not an active member of this group"));

        if (callerMember.getRole() != ConversationMemberRole.ADMIN) {
            throw new AccessDeniedException("Only group ADMIN can update group info");
        }

        if (request != null) {
            if (request.getName() != null) {
                String trimmed = request.getName().trim();
                if (trimmed.isEmpty() || trimmed.length() > 100) {
                    throw new IllegalArgumentException("Group name must be between 1 and 100 characters");
                }
                conversation.setName(trimmed);
            }
            if (request.getAvatar() != null) {
                String avatar = request.getAvatar().trim();
                if (avatar.length() > 255) {
                    throw new IllegalArgumentException("Avatar URL must not exceed 255 characters");
                }
                conversation.setAvatar(avatar.isEmpty() ? null : avatar);
            }
        }
        conversation = conversationRepository.save(conversation);

        long memberCount = conversationMemberRepository.countByConversationIdAndStatus(conversationId, ConversationMemberStatus.ACTIVE);
        Optional<Message> latestMsgOpt = messageRepository.findLatestMessageByConversationId(conversationId);
        Message latestMsg = latestMsgOpt.orElse(null);
        long unreadCount = messageRepository.countUnreadMessages(conversationId, currentUser.getId());

        return ConversationDto.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .name(conversation.getName())
                .avatar(conversation.getAvatar())
                .lastMessage(latestMsg != null ? latestMsg.getContent() : null)
                .lastMessageSender(latestMsg != null && latestMsg.getSender() != null ? latestMsg.getSender().getUsername() : null)
                .lastMessageTimestamp(latestMsg != null ? latestMsg.getTimestamp() : null)
                .lastActivityAt(latestMsg != null ? latestMsg.getTimestamp() : conversation.getCreatedAt())
                .createdAt(conversation.getCreatedAt())
                .unreadCount(unreadCount)
                .memberCount(memberCount)
                .currentUserRole(callerMember.getRole())
                .lastReadMessageId(callerMember.getLastReadMessageId())
                .build();
    }

    private boolean areFriends(User u1, User u2) {
        if (u1.getId().equals(u2.getId())) {
            return false;
        }
        User first = u1.getId() < u2.getId() ? u1 : u2;
        User second = u1.getId() > u2.getId() ? u1 : u2;
        return friendRequestRepository.findByUser1AndUser2(first, second)
                .map(fr -> fr.getStatus() == FriendRequestStatus.ACCEPTED)
                .orElse(false);
    }
}
