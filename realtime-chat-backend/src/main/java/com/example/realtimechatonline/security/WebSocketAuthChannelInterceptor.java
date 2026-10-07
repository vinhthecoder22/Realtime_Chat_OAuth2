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

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider tokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

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
        }

        return message;
    }
}