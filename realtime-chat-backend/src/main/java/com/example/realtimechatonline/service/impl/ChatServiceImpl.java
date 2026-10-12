package com.example.realtimechatonline.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.realtimechatonline.domain.dto.response.MessageResponseDto;
import com.example.realtimechatonline.domain.dto.websocket.ChatMessageDto;
import com.example.realtimechatonline.domain.entity.Message;
import com.example.realtimechatonline.domain.entity.MessageType;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.domain.mapper.MessageMapper;
import com.example.realtimechatonline.domain.entity.Conversation;
import com.example.realtimechatonline.domain.entity.ConversationType;
import com.example.realtimechatonline.exception.extended.ResourceNotFoundException;
import com.example.realtimechatonline.repository.ConversationMemberRepository;
import com.example.realtimechatonline.repository.ConversationRepository;
import com.example.realtimechatonline.repository.MessageRepository;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.ChatService;
import com.example.realtimechatonline.domain.dto.websocket.UserEventDto;
import com.example.realtimechatonline.domain.entity.ConversationMember;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Bangkok"));

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageMapper messageMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public ChatMessageDto processAndSave(String content, String senderUsername) {
        Conversation publicChat = conversationRepository.findByTypeAndName(ConversationType.PUBLIC, "Global Chat")
                .orElseThrow(() -> new IllegalStateException("Global Chat conversation not found"));
        return processAndSave(publicChat.getId(), content, senderUsername);
    }

    @Override
    @Transactional
    public ChatMessageDto processAndSave(Long conversationId, String content, String senderUsername) {
        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + senderUsername));

        Conversation conversation = null;
        if (conversationId != null) {
            conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

            if (conversation.getType() == ConversationType.DIRECT) {
                if (!conversationMemberRepository.existsByConversationIdAndUserId(conversation.getId(), sender.getId())) {
                    throw new org.springframework.security.access.AccessDeniedException("User is not a member of this direct conversation");
                }
            }
        } else {
            conversation = conversationRepository.findByTypeAndName(ConversationType.PUBLIC, "Global Chat")
                    .orElseThrow(() -> new IllegalStateException("Global Chat conversation not found"));
        }

        Message message = Message.builder()
                .content(content)
                .timestamp(Instant.now())
                .sender(sender)
                .conversation(conversation)
                .build();

        Message savedMessage = messageRepository.save(message);
        log.debug("Message saved: sender={}, length={}", senderUsername, content.length());

        if (conversation != null && conversation.getType() == ConversationType.DIRECT && messagingTemplate != null) {
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(conversation.getId());
            for (ConversationMember memberItem : members) {
                long unread = messageRepository.countUnreadMessages(conversation.getId(), memberItem.getUser().getId());
                UserEventDto updateEvent = UserEventDto.builder()
                        .type("CONVERSATION_UPDATE")
                        .conversationId(conversation.getId())
                        .lastMessage(savedMessage.getContent())
                        .lastMessageSender(senderUsername)
                        .lastMessageTimestamp(savedMessage.getTimestamp())
                        .lastActivityAt(savedMessage.getTimestamp())
                        .unreadCount(unread)
                        .messageId(savedMessage.getId())
                        .build();

                messagingTemplate.convertAndSendToUser(memberItem.getUser().getUsername(), "/queue/events", updateEvent);
            }
        }

        return ChatMessageDto.builder()
                .id(savedMessage.getId())
                .type(MessageType.CHAT)
                .conversationId(conversation != null ? conversation.getId() : null)
                .sender(senderUsername)
                .content(content)
                .time(TIME_FORMATTER.format(savedMessage.getTimestamp()))
                .build();
    }

    @Override
    public ChatMessageDto buildJoinMessage(String username) {
        return ChatMessageDto.builder()
                .type(MessageType.JOIN)
                .sender(username)
                .time(TIME_FORMATTER.format(Instant.now()))
                .build();
    }

    @Override
    public ChatMessageDto buildLeaveMessage(String username) {
        return ChatMessageDto.builder()
                .type(MessageType.LEAVE)
                .sender(username)
                .time(TIME_FORMATTER.format(Instant.now()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponseDto> getHistory() {
        Conversation publicChat = conversationRepository.findByTypeAndName(ConversationType.PUBLIC, "Global Chat")
                .orElseThrow(() -> new IllegalStateException("Global Chat conversation not found"));
        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(0, 50, org.springframework.data.domain.Sort.by("timestamp").descending());
        List<MessageResponseDto> recentMessages = new java.util.ArrayList<>(messageRepository.findByConversationIdOrderByTimestampDesc(publicChat.getId(), pageable)
                .map(messageMapper::toMessageResponse).getContent());
        java.util.Collections.reverse(recentMessages);
        return recentMessages;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponseDto> getHistory(Pageable pageable) {
        Conversation publicChat = conversationRepository.findByTypeAndName(ConversationType.PUBLIC, "Global Chat")
                .orElseThrow(() -> new IllegalStateException("Global Chat conversation not found"));
        return messageRepository.findByConversationIdOrderByTimestampDesc(publicChat.getId(), pageable)
                .map(messageMapper::toMessageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponseDto> getHistoryByConversation(Long conversationId, Pageable pageable, String currentUsername) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (conversation.getType() == ConversationType.DIRECT) {
            if (!conversationMemberRepository.existsByConversationIdAndUserId(conversation.getId(), currentUser.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("User is not a member of this direct conversation");
            }
        }

        return messageRepository.findByConversationIdOrderByTimestampDesc(conversation.getId(), pageable)
                .map(messageMapper::toMessageResponse);
    }
}
