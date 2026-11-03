package com.example.realtimechatonline.security;

import com.example.realtimechatonline.domain.entity.Conversation;
import com.example.realtimechatonline.domain.entity.ConversationMemberStatus;
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
    @Mock org.springframework.beans.factory.ObjectProvider<com.example.realtimechatonline.service.PresenceService> presenceServiceProvider;
    @Mock com.example.realtimechatonline.service.PresenceService presenceService;

    @InjectMocks WebSocketAuthChannelInterceptor interceptor;

    private User alice;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        lenient().when(presenceServiceProvider.getIfAvailable()).thenReturn(presenceService);
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

    @Test
    @DisplayName("SUBSCRIBE — /topic/presence throws UNAUTHORIZED when unauthenticated")
    void subscribe_presenceTopic_unauthenticated_throwsUnauthorized() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/presence");
        accessor.setUser(null);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("UNAUTHORIZED");
    }

    @Test
    @DisplayName("SUBSCRIBE — /topic/presence allowed when authenticated")
    void subscribe_presenceTopic_authenticated_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/presence");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("SEND — client direct SEND to /topic/presence is rejected with FORBIDDEN")
    void send_presenceTopic_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/presence");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("FORBIDDEN");
    }

    @Test
    @DisplayName("preSend — touches session in PresenceService when sessionId is present")
    void preSend_touchesSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/chat.sendMessage");
        accessor.setSessionId("sess-xyz");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);

        verify(presenceService).touchSession("sess-xyz");
    }

    @Test
    @DisplayName("SUBSCRIBE — group chat allows when user is active member")
    void subscribe_groupChat_activeMember_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation.50");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Conversation groupConv = Conversation.builder().id(50L).type(ConversationType.GROUP).build();
        when(conversationRepository.findById(50L)).thenReturn(Optional.of(groupConv));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(50L, 1L, ConversationMemberStatus.ACTIVE)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("SUBSCRIBE — group chat throws UNAUTHORIZED when user is not member")
    void subscribe_groupChat_notMember_throwsUnauthorized() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation.50");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Conversation groupConv = Conversation.builder().id(50L).type(ConversationType.GROUP).build();
        when(conversationRepository.findById(50L)).thenReturn(Optional.of(groupConv));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(50L, 1L, ConversationMemberStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("UNAUTHORIZED");
    }

    @Test
    @DisplayName("SUBSCRIBE — throws INVALID_DESTINATION for non-existent conversation")
    void subscribe_conversation_notFound_throwsInvalidDestination() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation.9999");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(conversationRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("INVALID_DESTINATION");
    }

    @Test
    @DisplayName("SUBSCRIBE — throws INVALID_DESTINATION for malformed conversation topic")
    void subscribe_conversation_malformedTopic_throwsInvalidDestination() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation.abc");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("INVALID_DESTINATION");
    }

    @Test
    @DisplayName("SUBSCRIBE — direct subscribe to /queue/ is rejected with FORBIDDEN")
    void subscribe_directQueue_throwsForbidden() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/queue/events");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("FORBIDDEN");
    }

    @Test
    @DisplayName("SUBSCRIBE — unknown destination is rejected with INVALID_DESTINATION")
    void subscribe_unknownDestination_throwsInvalidDestination() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/random.destination");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("INVALID_DESTINATION");
    }

    @Test
    @DisplayName("SEND — unauthenticated SEND is rejected with UNAUTHORIZED")
    void send_unauthenticated_throwsUnauthorized() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/chat.sendMessage");
        accessor.setUser(null);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("UNAUTHORIZED");
    }

    @Test
    @DisplayName("SEND — direct client SEND to /topic/public is rejected with FORBIDDEN")
    void send_directToTopicPublic_throwsForbidden() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/public");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("FORBIDDEN");
    }

    @Test
    @DisplayName("SEND — direct client SEND to /queue/events is rejected with FORBIDDEN")
    void send_directToQueue_throwsForbidden() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/queue/events");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("FORBIDDEN");
    }

    @Test
    @DisplayName("SEND — valid SEND to /app/ destination is allowed")
    void send_appDestination_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/chat.sendMessage");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }
}
