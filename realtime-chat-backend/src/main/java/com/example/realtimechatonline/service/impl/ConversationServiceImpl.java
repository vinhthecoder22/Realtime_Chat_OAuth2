package com.example.realtimechatonline.service.impl;

import com.example.realtimechatonline.domain.dto.response.ConversationDto;
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

        return ConversationDto.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .name(displayName)
                .avatar(targetUser.getPicture())
                .otherUser(userMapper.toUserProfile(targetUser))
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

        // 3. Batch fetch unread counts for all conversations
        List<Object[]> unreadCountsRaw = messageRepository.countUnreadMessagesByConversationIds(convIds, currentUser.getId());
        Map<Long, Long> unreadCountMap = unreadCountsRaw.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        List<ConversationDto> dtos = conversations.stream().map(conversation -> {
            User otherUser = null;
            ConversationMember currentMember = null;
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
            Message latestMsg = latestMessageMap.get(conversation.getId());

            String displayName;
            String avatar = null;
            if (conversation.getType() == ConversationType.DIRECT) {
                if (otherUserProfile != null) {
                    displayName = (otherUserProfile.getFullname() != null && !otherUserProfile.getFullname().isBlank())
                            ? otherUserProfile.getFullname()
                            : otherUserProfile.getUsername();
                    avatar = otherUserProfile.getPicture();
                } else {
                    displayName = "Unknown User";
                }
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
        }

        UserProfileDto otherUserProfile = otherUser != null ? userMapper.toUserProfile(otherUser) : null;
        Optional<Message> latestMsgOpt = messageRepository.findLatestMessageByConversationId(conversation.getId());
        Message latestMsg = latestMsgOpt.orElse(null);

        String displayName;
        String avatar = null;
        if (conversation.getType() == ConversationType.DIRECT) {
            if (otherUserProfile != null) {
                displayName = (otherUserProfile.getFullname() != null && !otherUserProfile.getFullname().isBlank())
                        ? otherUserProfile.getFullname()
                        : otherUserProfile.getUsername();
                avatar = otherUserProfile.getPicture();
            } else {
                displayName = "Unknown User";
            }
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
}
