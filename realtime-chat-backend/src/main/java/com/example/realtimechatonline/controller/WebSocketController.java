package com.example.realtimechatonline.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.realtimechatonline.domain.dto.websocket.ChatMessageDto;
import com.example.realtimechatonline.service.ChatService;
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
        ChatMessageDto outbound = chatService.processAndSave(inbound.getContent(), senderUsername);
        messagingTemplate.convertAndSend("/topic/public", outbound);
    }

    @MessageMapping("/chat.addUser")
    public void joinMessage(
            @Payload ChatMessageDto inbound,
            StompHeaderAccessor headerAccessor) {

        String senderUsername = getSessionUsername(headerAccessor);
        if (senderUsername == null) {
            senderUsername = inbound.getSender(); // fallback if interceptor set it differently
        }

        // Store in session for future messages
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs != null) {
            sessionAttrs.put("username", senderUsername);
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

    // helpers
    private String getSessionUsername(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return null;
        Object val = attrs.get("username");
        return val instanceof String s ? s : null;
    }
}