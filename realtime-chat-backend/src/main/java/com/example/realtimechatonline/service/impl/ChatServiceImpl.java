package com.example.realtimechatonline.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.realtimechatonline.domain.dto.response.MessageResponseDto;
import com.example.realtimechatonline.domain.dto.websocket.ChatMessageDto;
import com.example.realtimechatonline.domain.entity.Message;
import com.example.realtimechatonline.domain.entity.MessageType;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.domain.mapper.MessageMapper;
import com.example.realtimechatonline.exception.extended.ResourceNotFoundException;
import com.example.realtimechatonline.repository.MessageRepository;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.ChatService;
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
    private final MessageMapper messageMapper;

    @Override
    @Transactional
    public ChatMessageDto processAndSave(String content, String senderUsername) {
        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + senderUsername));

        Message message = Message.builder()
                .content(content)
                .timestamp(Instant.now())
                .sender(sender)
                .build();

        messageRepository.save(message);
        log.debug("Message saved: sender={}, length={}", senderUsername, content.length());

        return ChatMessageDto.builder()
                .type(MessageType.CHAT)
                .sender(senderUsername)
                .content(content)
                .time(TIME_FORMATTER.format(Instant.now()))
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
        return messageMapper.toMessageResponseList(
                messageRepository.findAllByOrderByTimestampAsc()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponseDto> getHistory(Pageable pageable) {
        return messageRepository.findAllByOrderByTimestampDesc(pageable)
                .map(messageMapper::toMessageResponse);
    }
}
