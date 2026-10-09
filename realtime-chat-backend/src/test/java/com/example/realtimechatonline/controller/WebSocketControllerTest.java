package com.example.realtimechatonline.controller;

import com.example.realtimechatonline.domain.dto.websocket.ChatMessageDto;
import com.example.realtimechatonline.domain.entity.MessageType;
import com.example.realtimechatonline.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.security.Principal;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketController unit tests")
class WebSocketControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketController webSocketController;

    private StompHeaderAccessor headerAccessor;
    private Principal principal;

    @BeforeEach
    void setUp() {
        headerAccessor = StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.MESSAGE);
        principal = mock(Principal.class);
    }

    @Test
    @DisplayName("joinMessage — derives sender from Principal, ignores payload")
    void joinMessage_derivesFromPrincipal() {
        when(principal.getName()).thenReturn("real_user");
        headerAccessor.setUser(principal);
        ChatMessageDto inbound = ChatMessageDto.builder().sender("fake_user").type(MessageType.JOIN).build();
        ChatMessageDto outbound = ChatMessageDto.builder().sender("real_user").type(MessageType.JOIN).build();
        when(chatService.buildJoinMessage("real_user")).thenReturn(outbound);
        webSocketController.joinMessage(inbound, headerAccessor);
        verify(chatService).buildJoinMessage("real_user");
        verify(messagingTemplate).convertAndSend("/topic/public", outbound);
    }

    @Test
    @DisplayName("joinMessage — unauthenticated is rejected safely")
    void joinMessage_unauthenticatedRejected() {
        headerAccessor.setUser(null);
        ChatMessageDto inbound = ChatMessageDto.builder().sender("fake_user").type(MessageType.JOIN).build();
        webSocketController.joinMessage(inbound, headerAccessor);
        verifyNoInteractions(chatService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("sendMessage — derives sender from Principal, ignores payload")
    void sendMessage_derivesFromPrincipal() {
        when(principal.getName()).thenReturn("real_user");
        headerAccessor.setUser(principal);
        ChatMessageDto inbound = ChatMessageDto.builder().sender("fake_user").content("Hello").type(MessageType.CHAT).build();
        ChatMessageDto outbound = ChatMessageDto.builder().sender("real_user").content("Hello").type(MessageType.CHAT).build();
        when(chatService.processAndSave("Hello", "real_user")).thenReturn(outbound);
        webSocketController.sendMessage(inbound, headerAccessor);
        verify(chatService).processAndSave("Hello", "real_user");
        verify(messagingTemplate).convertAndSend("/topic/public", outbound);
    }

    @Test
    @DisplayName("leaveMessage — derives sender from Principal")
    void leaveMessage_derivesFromPrincipal() {
        when(principal.getName()).thenReturn("real_user");
        headerAccessor.setUser(principal);
        ChatMessageDto outbound = ChatMessageDto.builder().sender("real_user").type(MessageType.LEAVE).build();
        when(chatService.buildLeaveMessage("real_user")).thenReturn(outbound);
        webSocketController.leaveMessage(headerAccessor);
        verify(chatService).buildLeaveMessage("real_user");
        verify(messagingTemplate).convertAndSend("/topic/public", outbound);
    }
}