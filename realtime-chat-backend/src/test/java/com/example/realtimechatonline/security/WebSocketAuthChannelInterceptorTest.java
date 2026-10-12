package com.example.realtimechatonline.security;

import com.example.realtimechatonline.domain.entity.Conversation;
import com.example.realtimechatonline.domain.entity.ConversationType;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.repository.ConversationMemberRepository;
import com.example.realtimechatonline.repository.ConversationRepository;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketAuthChannelInterceptor Unit Tests")
class WebSocketAuthChannelInterceptorTest {

    @Mock JwtTokenProvider tokenProvider;
    @Mock UserRepository userRepository;
    @Mock ConversationMemberRepository conversationMemberRepository;
    @Mock ConversationRepository conversationRepository;
    @Mock MessageChannel channel;

    @InjectMocks WebSocketAuthChannelInterceptor interceptor;

    private User alice;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        alice = User.builder().id(1L).username("alice").build();
        auth = new UsernamePasswordAuthenticationToken("alice", "cred", Collections.emptyList());
    }

    @Test
    @DisplayName("CONNECT — throws UNAUTHORIZED when Authorization header is missing")
    void connect_missingHeader_throwsUnauthorized() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("UNAUTHORIZED");
    }

    @Test
    @DisplayName("CONNECT — throws INVALID_TOKEN when token validation fails")
    void connect_invalidToken_throwsInvalidToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer invalid-jwt");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        doThrow(new RuntimeException("Expired token")).when(tokenProvider).validateToken("invalid-jwt");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("INVALID_TOKEN");
    }

    @Test
    @DisplayName("CONNECT — successfully sets user on valid token")
    void connect_validToken_success() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer valid-jwt");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        doNothing().when(tokenProvider).validateToken("valid-jwt");
        when(tokenProvider.getAuthentication("valid-jwt")).thenReturn(auth);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isEqualTo(auth);
    }

    @Test
    @DisplayName("SUBSCRIBE — /topic/public allows without error")
    void subscribe_publicTopic_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/public");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("SUBSCRIBE — direct conversation throws UNAUTHORIZED when user is not member")
    void subscribe_directChat_notMember_throwsUnauthorized() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation.99");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Conversation directConv = Conversation.builder().id(99L).type(ConversationType.DIRECT).build();
        when(conversationRepository.findById(99L)).thenReturn(Optional.of(directConv));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationMemberRepository.existsByConversationIdAndUserId(99L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("UNAUTHORIZED");
    }

    @Test
    @DisplayName("SUBSCRIBE — direct conversation allows when user is member")
    void subscribe_directChat_member_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation.99");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Conversation directConv = Conversation.builder().id(99L).type(ConversationType.DIRECT).build();
        when(conversationRepository.findById(99L)).thenReturn(Optional.of(directConv));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationMemberRepository.existsByConversationIdAndUserId(99L, 1L)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }
}
