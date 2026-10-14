package com.example.realtimechatonline.config;

import com.example.realtimechatonline.service.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketEventListener Unit Tests")
class WebSocketEventListenerTest {

    @Mock
    private PresenceService presenceService;

    @InjectMocks
    private WebSocketEventListener eventListener;

    private Principal principal;

    @BeforeEach
    void setUp() {
        principal = mock(Principal.class);
    }

    @Test
    @DisplayName("handleSessionConnected — registers session when principal is present")
    void handleSessionConnected_validPrincipal_registersSession() {
        when(principal.getName()).thenReturn("alice");
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        accessor.setSessionId("sess-100");
        accessor.setUser(principal);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionConnectedEvent event = new SessionConnectedEvent(this, message);
        eventListener.handleSessionConnected(event);

        verify(presenceService).addSession("alice", "sess-100");
    }

    @Test
    @DisplayName("handleSessionConnected — ignores when principal is null")
    void handleSessionConnected_nullPrincipal_ignored() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        accessor.setSessionId("sess-100");
        accessor.setUser(null);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionConnectedEvent event = new SessionConnectedEvent(this, message);
        eventListener.handleSessionConnected(event);

        verifyNoInteractions(presenceService);
    }

    @Test
    @DisplayName("handleSessionDisconnect — removes session from PresenceService")
    void handleSessionDisconnect_removesSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("sess-100");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "sess-100", org.springframework.web.socket.CloseStatus.NORMAL);
        eventListener.handleSessionDisconnect(event);

        verify(presenceService).removeSession("sess-100");
    }
}
