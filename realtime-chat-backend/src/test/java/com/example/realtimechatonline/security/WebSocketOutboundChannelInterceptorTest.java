package com.example.realtimechatonline.security;

import com.example.realtimechatonline.domain.entity.ConversationType;
import com.example.realtimechatonline.service.GroupMembershipService;
import com.example.realtimechatonline.service.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketOutboundChannelInterceptor Unit Tests (STEP 10.8 Server-Side Revocation)")
class WebSocketOutboundChannelInterceptorTest {

    @Mock
    private GroupMembershipService groupMembershipService;

    @Mock
    private ObjectProvider<PresenceService> presenceServiceProvider;

    @Mock
    private PresenceService presenceService;

    @Mock
    private MessageChannel channel;

    @InjectMocks
    private WebSocketOutboundChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        lenient().when(presenceServiceProvider.getIfAvailable()).thenReturn(presenceService);
    }

    private Message<?> createMessage(SimpMessageType type, String destination, Principal principal, String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(type);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (principal != null) {
            accessor.setUser(principal);
        }
        if (sessionId != null) {
            accessor.setSessionId(sessionId);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("Non-MESSAGE frames (CONNECT_ACK, SUBSCRIBE, etc.) pass through untouched")
    void nonMessageFrame_passesThrough() {
        Message<?> message = createMessage(SimpMessageType.CONNECT_ACK, null, null, "sess-1");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verifyNoInteractions(groupMembershipService);
    }

    @Test
    @DisplayName("Messages with null destination pass through untouched")
    void nullDestination_passesThrough() {
        Message<?> message = createMessage(SimpMessageType.MESSAGE, null, () -> "alice", "sess-1");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verifyNoInteractions(groupMembershipService);
    }

    @Test
    @DisplayName("Non-conversation topics (/topic/public, /topic/presence, /user/queue/events) pass through untouched")
    void nonConversationTopics_passThrough() {
        Message<?> publicMsg = createMessage(SimpMessageType.MESSAGE, "/topic/public", () -> "alice", "sess-1");
        Message<?> presenceMsg = createMessage(SimpMessageType.MESSAGE, "/topic/presence", () -> "alice", "sess-1");
        Message<?> queueMsg = createMessage(SimpMessageType.MESSAGE, "/user/alice/queue/events", () -> "alice", "sess-1");

        assertThat(interceptor.preSend(publicMsg, channel)).isSameAs(publicMsg);
        assertThat(interceptor.preSend(presenceMsg, channel)).isSameAs(presenceMsg);
        assertThat(interceptor.preSend(queueMsg, channel)).isSameAs(queueMsg);
        verifyNoInteractions(groupMembershipService);
    }

    @Test
    @DisplayName("Invalid conversation topic format is dropped (returns null)")
    void invalidConversationTopic_dropsFrame() {
        Message<?> badTopic1 = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.abc", () -> "alice", "sess-1");
        Message<?> badTopic2 = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.0", () -> "alice", "sess-1");
        Message<?> badTopic3 = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.12.extra", () -> "alice", "sess-1");

        assertThat(interceptor.preSend(badTopic1, channel)).isNull();
        assertThat(interceptor.preSend(badTopic2, channel)).isNull();
        assertThat(interceptor.preSend(badTopic3, channel)).isNull();
    }

    @Test
    @DisplayName("Non-existent conversation ID drops frame (returns null)")
    void nonExistentConversation_dropsFrame() {
        when(groupMembershipService.getConversationType(999L)).thenReturn(null);

        Message<?> message = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.999", () -> "alice", "sess-1");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("PUBLIC conversation type passes through for all authenticated users")
    void publicConversation_passesThrough() {
        when(groupMembershipService.getConversationType(100L)).thenReturn(ConversationType.PUBLIC);

        Message<?> message = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.100", () -> "alice", "sess-1");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verify(groupMembershipService, never()).isMemberActive(anyLong(), anyString());
    }

    @Test
    @DisplayName("GROUP conversation — ACTIVE member receives message frame")
    void groupConversation_activeMember_receivesMessage() {
        when(groupMembershipService.getConversationType(10L)).thenReturn(ConversationType.GROUP);
        when(groupMembershipService.isMemberActive(10L, "alice")).thenReturn(true);

        Message<?> message = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.10", () -> "alice", "sess-1");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verify(groupMembershipService).isMemberActive(10L, "alice");
    }

    @Test
    @DisplayName("GROUP conversation — Kicked/REMOVED member frame is DROPPED (returns null)")
    void groupConversation_kickedMember_frameDropped() {
        when(groupMembershipService.getConversationType(10L)).thenReturn(ConversationType.GROUP);
        when(groupMembershipService.isMemberActive(10L, "bob")).thenReturn(false);

        Message<?> message = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.10", () -> "bob", "sess-bob");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNull(); // Frame dropped!
        verify(groupMembershipService).isMemberActive(10L, "bob");
    }

    @Test
    @DisplayName("GROUP conversation — Left member frame is DROPPED (returns null)")
    void groupConversation_leftMember_frameDropped() {
        when(groupMembershipService.getConversationType(10L)).thenReturn(ConversationType.GROUP);
        when(groupMembershipService.isMemberActive(10L, "charlie")).thenReturn(false);

        Message<?> message = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.10", () -> "charlie", "sess-charlie");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNull(); // Frame dropped!
    }

    @Test
    @DisplayName("GROUP conversation — Outsider frame is DROPPED (returns null)")
    void groupConversation_outsider_frameDropped() {
        when(groupMembershipService.getConversationType(10L)).thenReturn(ConversationType.GROUP);
        when(groupMembershipService.isMemberActive(10L, "eve")).thenReturn(false);

        Message<?> message = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.10", () -> "eve", "sess-eve");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNull(); // Frame dropped!
    }

    @Test
    @DisplayName("GROUP conversation — Recipient resolved via session fallback when Principal is null")
    void groupConversation_sessionFallbackResolution() {
        when(groupMembershipService.getConversationType(10L)).thenReturn(ConversationType.GROUP);
        when(presenceService.getUsernameBySession("sess-fallback-active")).thenReturn("alice");
        when(groupMembershipService.isMemberActive(10L, "alice")).thenReturn(true);

        Message<?> message = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.10", null, "sess-fallback-active");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verify(groupMembershipService).isMemberActive(10L, "alice");
    }

    @Test
    @DisplayName("GROUP conversation — Session fallback with inactive member drops frame")
    void groupConversation_sessionFallbackInactive_frameDropped() {
        when(groupMembershipService.getConversationType(10L)).thenReturn(ConversationType.GROUP);
        when(presenceService.getUsernameBySession("sess-fallback-kicked")).thenReturn("bob");
        when(groupMembershipService.isMemberActive(10L, "bob")).thenReturn(false);

        Message<?> message = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.10", null, "sess-fallback-kicked");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("GROUP conversation — Unidentifiable recipient drops frame (fail-closed)")
    void groupConversation_unidentifiableRecipient_dropsFrame() {
        when(groupMembershipService.getConversationType(10L)).thenReturn(ConversationType.GROUP);
        when(presenceService.getUsernameBySession("unknown-sess")).thenReturn(null);

        Message<?> message = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.10", null, "unknown-sess");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNull();
        verify(groupMembershipService, never()).isMemberActive(anyLong(), anyString());
    }

    @Test
    @DisplayName("DIRECT conversation — Active member receives frame, outsider drops frame")
    void directConversation_activeAllowed_outsiderDropped() {
        when(groupMembershipService.getConversationType(20L)).thenReturn(ConversationType.DIRECT);
        when(groupMembershipService.isMemberActive(20L, "alice")).thenReturn(true);
        when(groupMembershipService.isMemberActive(20L, "eve")).thenReturn(false);

        Message<?> allowedMsg = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.20", () -> "alice", "sess-1");
        Message<?> droppedMsg = createMessage(SimpMessageType.MESSAGE, "/topic/conversation.20", () -> "eve", "sess-2");

        assertThat(interceptor.preSend(allowedMsg, channel)).isSameAs(allowedMsg);
        assertThat(interceptor.preSend(droppedMsg, channel)).isNull();
    }
}
