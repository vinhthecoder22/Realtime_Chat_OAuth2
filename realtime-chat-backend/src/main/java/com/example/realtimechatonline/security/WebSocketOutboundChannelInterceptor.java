package com.example.realtimechatonline.security;

import com.example.realtimechatonline.domain.entity.ConversationType;
import com.example.realtimechatonline.service.GroupMembershipService;
import com.example.realtimechatonline.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketOutboundChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CONVERSATION_TOPIC_PATTERN = Pattern.compile("^/topic/conversation\\.(\\d+)$");

    private final GroupMembershipService groupMembershipService;
    private final ObjectProvider<PresenceService> presenceServiceProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        if (!SimpMessageType.MESSAGE.equals(accessor.getMessageType())) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/conversation.")) {
            return message;
        }

        Matcher matcher = CONVERSATION_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            log.warn("Dropping outbound frame: Invalid destination format [{}]", destination);
            return null;
        }

        long conversationId;
        try {
            conversationId = Long.parseLong(matcher.group(1));
            if (conversationId <= 0) {
                log.warn("Dropping outbound frame: Invalid conversation ID [{}]", conversationId);
                return null;
            }
        } catch (NumberFormatException e) {
            log.warn("Dropping outbound frame: Non-numeric conversation ID in destination [{}]", destination);
            return null;
        }

        ConversationType convType = groupMembershipService.getConversationType(conversationId);
        if (convType == null) {
            log.warn("Dropping outbound frame: Conversation [{}] does not exist", conversationId);
            return null;
        }

        if (convType == ConversationType.PUBLIC) {
            return message;
        }

        String recipientUsername = null;
        Principal principal = accessor.getUser();
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            recipientUsername = principal.getName();
        }

        if (recipientUsername == null && accessor.getSessionId() != null) {
            PresenceService presenceService = presenceServiceProvider.getIfAvailable();
            if (presenceService != null) {
                recipientUsername = presenceService.getUsernameBySession(accessor.getSessionId());
            }
        }

        if (recipientUsername == null || recipientUsername.isBlank()) {
            log.warn("Dropping outbound frame to [{}]: Unable to identify authenticated recipient for session [{}]",
                    destination, accessor.getSessionId());
            return null;
        }

        boolean isActive = groupMembershipService.isMemberActive(conversationId, recipientUsername);
        if (!isActive) {
            log.warn("Server-side subscription revocation: Dropped message for conversation [{}] to revoked/inactive member [{}] (session={})",
                    conversationId, recipientUsername, accessor.getSessionId());
            return null;
        }

        return message;
    }
}
