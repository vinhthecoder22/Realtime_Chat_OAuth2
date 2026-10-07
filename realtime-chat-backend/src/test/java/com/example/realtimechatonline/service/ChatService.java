package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.websocket.ChatMessageDto;
import com.example.realtimechatonline.domain.entity.Message;
import com.example.realtimechatonline.domain.entity.MessageType;
import com.example.realtimechatonline.domain.entity.Role;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.domain.mapper.MessageMapper;
import com.example.realtimechatonline.exception.extended.ResourceNotFoundException;
import com.example.realtimechatonline.repository.MessageRepository;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatServiceImpl unit tests")
class ChatServiceTest {

    @Mock MessageRepository messageRepository;
    @Mock UserRepository    userRepository;
    @Mock MessageMapper     messageMapper;

    @InjectMocks ChatServiceImpl chatService;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = User.builder()
                .id(1L)
                .username("alice")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("processAndSave — persists message and returns enriched ChatMessage")
    void processAndSave_success() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageDto result = chatService.processAndSave("Hello world", "alice");

        assertThat(result.getType()).isEqualTo(MessageType.CHAT);
        assertThat(result.getSender()).isEqualTo("alice");
        assertThat(result.getContent()).isEqualTo("Hello world");
        assertThat(result.getTime()).isNotBlank();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Hello world");
        assertThat(captor.getValue().getSender()).isEqualTo(alice);
    }

    @Test
    @DisplayName("processAndSave — throws ResourceNotFoundException for unknown sender")
    void processAndSave_unknownSender_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.processAndSave("Hi", "ghost"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("buildJoinMessage — returns JOIN type without persisting")
    void buildJoinMessage_returnsJoin() {
        ChatMessageDto msg = chatService.buildJoinMessage("alice");

        assertThat(msg.getType()).isEqualTo(MessageType.JOIN);
        assertThat(msg.getSender()).isEqualTo("alice");
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("buildLeaveMessage — returns LEAVE type without persisting")
    void buildLeaveMessage_returnsLeave() {
        ChatMessageDto msg = chatService.buildLeaveMessage("alice");

        assertThat(msg.getType()).isEqualTo(MessageType.LEAVE);
        assertThat(msg.getSender()).isEqualTo("alice");
        verifyNoInteractions(messageRepository);
    }
}