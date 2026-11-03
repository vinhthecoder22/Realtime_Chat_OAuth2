package com.example.realtimechatonline.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.realtimechatonline.domain.dto.websocket.ChatMessageDto;
import com.example.realtimechatonline.domain.entity.MessageType;
import com.example.realtimechatonline.service.ChatService;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(
            @Payload ChatMessageDto inbound,
            StompHeaderAccessor headerAccessor) {

        String senderUsername = getSessionUsername(headerAccessor);
        if (senderUsername == null) {
            log.warn("Unauthenticated message attempt — no username in session");
            return;
        }

        // Delegate all business logic to service
        ChatMessageDto outbound = chatService.processAndSave(inbound.getConversationId(), inbound.getContent(), senderUsername);

        if (inbound.getConversationId() != null) {
            messagingTemplate.convertAndSend("/topic/conversation." + inbound.getConversationId(), outbound);
        } else {
            messagingTemplate.convertAndSend("/topic/public", outbound);
        }
    }

    @MessageMapping("/chat.typing")
    public void processTyping(
            @Payload ChatMessageDto inbound,
            StompHeaderAccessor headerAccessor) {

        String senderUsername = getSessionUsername(headerAccessor);
        if (senderUsername == null) {
            log.warn("Unauthenticated typing attempt");
            return;
        }

        Long conversationId = inbound.getConversationId();
        if (conversationId == null) {
            ChatMessageDto typingDto = ChatMessageDto.builder()
                    .type(MessageType.TYPING)
                    .sender(senderUsername)
                    .content(inbound.getContent())
                    .build();
            messagingTemplate.convertAndSend("/topic/public", typingDto);
            return;
        }

        try {
            chatService.validateConversationAccess(conversationId, senderUsername);
        } catch (Exception ex) {
            log.warn("Typing event rejected for user {} on conversation {}: {}", senderUsername, conversationId, ex.getMessage());
            return;
        }

        ChatMessageDto typingDto = ChatMessageDto.builder()
                .type(MessageType.TYPING)
                .conversationId(conversationId)
                .sender(senderUsername)
                .content(inbound.getContent())
                .build();

        messagingTemplate.convertAndSend("/topic/conversation." + conversationId, typingDto);
    }

    @MessageMapping("/chat.addUser")
    public void joinMessage(
            @Payload ChatMessageDto inbound,
            StompHeaderAccessor headerAccessor) {

        String senderUsername = getSessionUsername(headerAccessor);
        if (senderUsername == null) {
            log.warn("Unauthenticated join attempt");
            return;
        }

        ChatMessageDto joinMessage = chatService.buildJoinMessage(senderUsername);
        messagingTemplate.convertAndSend("/topic/public", joinMessage);
        log.debug("User joined chat: {}", senderUsername);
    }

    @MessageMapping("/chat.removeUser")
    public void leaveMessage(StompHeaderAccessor headerAccessor) {
        String senderUsername = getSessionUsername(headerAccessor);
        if (senderUsername == null) return;

        ChatMessageDto leaveMessage = chatService.buildLeaveMessage(senderUsername);
        messagingTemplate.convertAndSend("/topic/public", leaveMessage);
        log.debug("User left chat: {}", senderUsername);
    }

    @MessageExceptionHandler
    public void handleException(Exception ex, StompHeaderAccessor headerAccessor) {
        log.warn("WebSocket message processing error: {}", ex.getMessage());
        String username = getSessionUsername(headerAccessor);
        if (username != null && messagingTemplate != null) {
            messagingTemplate.convertAndSendToUser(username, "/queue/errors", Map.of(
                    "error", ex.getMessage() != null ? ex.getMessage() : "Error processing message"
            ));
        }
    }

    // helpers
    private String getSessionUsername(StompHeaderAccessor accessor) {
        if (accessor != null && accessor.getUser() != null) {
            return accessor.getUser().getName();
        }
        return null;
    }
}