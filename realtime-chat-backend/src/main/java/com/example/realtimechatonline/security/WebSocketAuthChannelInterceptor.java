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
import com.example.realtimechatonline.domain.entity.ConversationMemberStatus;
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

                PresenceService presenceService = presenceServiceProvider.getIfAvailable();
                if (presenceService != null && accessor.getSessionId() != null) {
                    presenceService.addSession(auth.getName(), accessor.getSessionId());
                }

            } catch (Exception e) {
                log.warn("Kết nối WebSocket thất bại: Token không hợp lệ ({})", e.getMessage());
                // Ném lỗi để Spring gửi STOMP ERROR frame về client
                throw new MessageDeliveryException("INVALID_TOKEN");
            }
        } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination == null) {
                throw new MessageDeliveryException("INVALID_DESTINATION");
            }

            Authentication auth = (Authentication) accessor.getUser();
            if (auth == null) {
                log.warn("SUBSCRIBE to {} rejected: Unauthenticated", destination);
                throw new MessageDeliveryException("UNAUTHORIZED");
            }

            if (destination.startsWith("/topic/conversation.")) {
                String idStr = destination.substring("/topic/conversation.".length());
                Long conversationId;
                try {
                    conversationId = Long.parseLong(idStr);
                } catch (NumberFormatException e) {
                    log.warn("Invalid conversation topic format: {}", destination);
                    throw new MessageDeliveryException("INVALID_DESTINATION");
                }

                Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
                if (conversation == null) {
                    log.warn("Subscribe rejected: Conversation {} not found", conversationId);
                    throw new MessageDeliveryException("INVALID_DESTINATION");
                }

                if (conversation.getType() == ConversationType.DIRECT) {
                    User user = userRepository.findByUsername(auth.getName()).orElse(null);
                    if (user == null || !conversationMemberRepository.existsByConversationIdAndUserId(conversationId, user.getId())) {
                        log.warn("Subscribe rejected: User {} is not a member of direct conversation {}", auth.getName(), conversationId);
                        throw new MessageDeliveryException("UNAUTHORIZED");
                    }
                } else if (conversation.getType() == ConversationType.GROUP) {
                    User user = userRepository.findByUsername(auth.getName()).orElse(null);
                    if (user == null || !conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(conversationId, user.getId(), ConversationMemberStatus.ACTIVE)) {
                        log.warn("Subscribe rejected: User {} is not an active member of group conversation {}", auth.getName(), conversationId);
                        throw new MessageDeliveryException("UNAUTHORIZED");
                    }
                }
            } else if (destination.equals("/topic/public") || destination.equals("/topic/presence")) {
                // Allowed for authenticated users
            } else if (destination.startsWith("/user/")) {
                // Allowed user destinations (e.g. /user/queue/events)
            } else if (destination.startsWith("/queue/")) {
                log.warn("Direct subscribe to /queue/ rejected: {}", destination);
                throw new MessageDeliveryException("FORBIDDEN");
            } else {
                log.warn("Subscribe to unknown destination rejected: {}", destination);
                throw new MessageDeliveryException("INVALID_DESTINATION");
            }
        } else if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
            Authentication auth = (Authentication) accessor.getUser();
            if (auth == null) {
                log.warn("WebSocket SEND rejected: Unauthenticated");
                throw new MessageDeliveryException("UNAUTHORIZED");
            }

            String destination = accessor.getDestination();
            if (destination == null) {
                throw new MessageDeliveryException("INVALID_DESTINATION");
            }

            if (destination.startsWith("/topic/") || destination.equals("/topic")
                    || destination.startsWith("/queue/") || destination.equals("/queue")
                    || destination.startsWith("/user/") || destination.equals("/user")) {
                log.warn("Direct SEND to broker prefix {} rejected from client {}", destination, auth.getName());
                throw new MessageDeliveryException("FORBIDDEN");
            }

            if (!destination.startsWith("/app/")) {
                log.warn("SEND to unknown destination prefix {} rejected", destination);
                throw new MessageDeliveryException("INVALID_DESTINATION");
            }
        }

        return message;
    }
}