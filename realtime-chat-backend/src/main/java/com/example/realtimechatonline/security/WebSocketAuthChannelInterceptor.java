package com.example.realtimechatonline.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.example.realtimechatonline.security.jwt.JwtTokenProvider;
import org.springframework.messaging.MessageDeliveryException;

import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.repository.ConversationMemberRepository;
import com.example.realtimechatonline.repository.ConversationRepository;
import com.example.realtimechatonline.domain.entity.Conversation;
import com.example.realtimechatonline.domain.entity.ConversationType;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.service.PresenceService;
import org.springframework.beans.factory.ObjectProvider;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationRepository conversationRepository;
    private final ObjectProvider<PresenceService> presenceServiceProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && accessor.getSessionId() != null) {
            PresenceService presenceService = presenceServiceProvider.getIfAvailable();
            if (presenceService != null) {
                presenceService.touchSession(accessor.getSessionId());
            }
        }

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            // 1. Chặn nếu không có token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Kết nối WebSocket thất bại: Thiếu token");
                // Chặn kết nối nếu không có token hợp lệ
                throw new MessageDeliveryException("UNAUTHORIZED");
            }

            String token = authHeader.substring(7);

            // 2. Chặn nếu token lỗi/hết hạn
            try {
                // Nếu validateToken throw exception, nó sẽ nhảy xuống catch
                tokenProvider.validateToken(token);
                Authentication auth = tokenProvider.getAuthentication(token);
                accessor.setUser(auth);

            } catch (Exception e) {
                log.warn("Kết nối WebSocket thất bại: Token không hợp lệ ({})", e.getMessage());
                // Ném lỗi để Spring gửi STOMP ERROR frame về client
                throw new MessageDeliveryException("INVALID_TOKEN");
            }
        } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/conversation.")) {
                try {
                    Long conversationId = Long.parseLong(destination.substring("/topic/conversation.".length()));
                    Authentication auth = (Authentication) accessor.getUser();
                    if (auth == null) {
                        throw new MessageDeliveryException("UNAUTHORIZED");
                    }

                    Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
                    if (conversation == null) {
                        throw new MessageDeliveryException("INVALID_DESTINATION");
                    }

                    if (conversation.getType() == ConversationType.DIRECT) {
                        User user = userRepository.findByUsername(auth.getName()).orElse(null);
                        if (user == null || !conversationMemberRepository.existsByConversationIdAndUserId(conversationId, user.getId())) {
                            log.warn("Subscribe bị từ chối: User {} không nằm trong conversation {}", auth.getName(), conversationId);
                            throw new MessageDeliveryException("UNAUTHORIZED");
                        }
                    }

                } catch (NumberFormatException e) {
                    throw new MessageDeliveryException("INVALID_DESTINATION");
                }
            } else if (destination != null && (destination.equals("/topic/presence") || destination.startsWith("/topic/presence"))) {
                Authentication auth = (Authentication) accessor.getUser();
                if (auth == null) {
                    log.warn("Subscribe /topic/presence rejected: Unauthenticated");
                    throw new MessageDeliveryException("UNAUTHORIZED");
                }
            }
        } else if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && (destination.equals("/topic/presence") || destination.startsWith("/topic/presence"))) {
                log.warn("Direct SEND to /topic/presence rejected from client");
                throw new MessageDeliveryException("FORBIDDEN");
            }
        }

        return message;
    }
}