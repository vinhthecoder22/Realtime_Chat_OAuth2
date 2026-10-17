package com.example.realtimechatonline.security;

import com.example.realtimechatonline.controller.WebSocketController;
import com.example.realtimechatonline.domain.dto.websocket.ChatMessageDto;
import com.example.realtimechatonline.domain.dto.websocket.UserEventDto;
import com.example.realtimechatonline.domain.entity.*;
import com.example.realtimechatonline.repository.ConversationMemberRepository;
import com.example.realtimechatonline.repository.ConversationRepository;
import com.example.realtimechatonline.repository.MessageRepository;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "MAIL_HOST=localhost",
        "MAIL_PORT=587",
        "MAIL_USERNAME=test",
        "MAIL_PASSWORD=test",
        "JWT_SECRET=thisismocksecretthisismocksecret1234567890",
        "GOOGLE_CLIENT_ID=test",
        "GOOGLE_CLIENT_SECRET=test"
})
@DisplayName("Group Chat WebSocket Security & Authorization Integration Tests (STEP 10.4)")
class GroupChatWebSocketSecurityTest {

    @Autowired
    private WebSocketAuthChannelInterceptor interceptor;

    @Autowired
    private WebSocketController webSocketController;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @org.mockito.Mock
    private MessageChannel channel;

    private User adminUser;
    private User activeMember;
    private User leftMember;
    private User removedMember;
    private User outsider;
    private Conversation groupChat;
    private Conversation publicChat;

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();

        adminUser = userRepository.save(User.builder()
                .username("ws_admin_" + nano)
                .email("admin_" + nano + "@test.com")
                .fullname("Admin User")
                .password("hashed_pwd")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        activeMember = userRepository.save(User.builder()
                .username("ws_active_" + nano)
                .email("active_" + nano + "@test.com")
                .fullname("Active Member")
                .password("hashed_pwd")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        leftMember = userRepository.save(User.builder()
                .username("ws_left_" + nano)
                .email("left_" + nano + "@test.com")
                .fullname("Left Member")
                .password("hashed_pwd")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        removedMember = userRepository.save(User.builder()
                .username("ws_removed_" + nano)
                .email("removed_" + nano + "@test.com")
                .fullname("Removed Member")
                .password("hashed_pwd")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        outsider = userRepository.save(User.builder()
                .username("ws_outsider_" + nano)
                .email("outsider_" + nano + "@test.com")
                .fullname("Outsider User")
                .password("hashed_pwd")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        publicChat = conversationRepository.findByTypeAndName(ConversationType.PUBLIC, "Global Chat")
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .type(ConversationType.PUBLIC)
                        .name("Global Chat")
                        .createdAt(Instant.now())
                        .build()));

        groupChat = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Secured Group")
                .creator(adminUser)
                .createdAt(Instant.now())
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(groupChat)
                .user(adminUser)
                .role(ConversationMemberRole.ADMIN)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(groupChat)
                .user(activeMember)
                .role(ConversationMemberRole.MEMBER)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(groupChat)
                .user(leftMember)
                .role(ConversationMemberRole.MEMBER)
                .status(ConversationMemberStatus.LEFT)
                .joinedAt(Instant.now().minusSeconds(3600))
                .leftAt(Instant.now().minusSeconds(1800))
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(groupChat)
                .user(removedMember)
                .role(ConversationMemberRole.MEMBER)
                .status(ConversationMemberStatus.REMOVED)
                .joinedAt(Instant.now().minusSeconds(7200))
                .leftAt(Instant.now().minusSeconds(3600))
                .build());
    }

    private Authentication createAuth(User user) {
        return new UsernamePasswordAuthenticationToken(user.getUsername(), "pwd", Collections.emptyList());
    }

    // ==========================================
    // 1. SUBSCRIBE AUTHORIZATION TESTS
    // ==========================================

    @Test
    @DisplayName("1. Active member can SUBSCRIBE to /topic/conversation.{id}")
    void subscribe_activeMember_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation." + groupChat.getId());
        accessor.setUser(createAuth(activeMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("2. Outsider cannot SUBSCRIBE to /topic/conversation.{id}")
    void subscribe_outsider_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation." + groupChat.getId());
        accessor.setUser(createAuth(outsider));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("UNAUTHORIZED");
    }

    @Test
    @DisplayName("3. Member with status LEFT cannot SUBSCRIBE to /topic/conversation.{id}")
    void subscribe_leftMember_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation." + groupChat.getId());
        accessor.setUser(createAuth(leftMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("UNAUTHORIZED");
    }

    @Test
    @DisplayName("4. Member with status REMOVED cannot SUBSCRIBE to /topic/conversation.{id}")
    void subscribe_removedMember_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation." + groupChat.getId());
        accessor.setUser(createAuth(removedMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("UNAUTHORIZED");
    }

    @Test
    @DisplayName("5. Non-existent conversation ID returns INVALID_DESTINATION")
    void subscribe_nonExistentConversation_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation.99999999");
        accessor.setUser(createAuth(activeMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("INVALID_DESTINATION");
    }

    @Test
    @DisplayName("6. Malformed conversation ID returns INVALID_DESTINATION")
    void subscribe_malformedId_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation.abc");
        accessor.setUser(createAuth(activeMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("INVALID_DESTINATION");
    }

    @Test
    @DisplayName("7. Topic alias (slash instead of dot) returns INVALID_DESTINATION")
    void subscribe_topicAlias_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversation/" + groupChat.getId());
        accessor.setUser(createAuth(activeMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("INVALID_DESTINATION");
    }

    @Test
    @DisplayName("8. Direct subscription to /queue/** returns FORBIDDEN")
    void subscribe_directQueue_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/queue/events");
        accessor.setUser(createAuth(activeMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("FORBIDDEN");
    }

    @Test
    @DisplayName("9. Subscription to /user/queue/events allowed for authenticated user")
    void subscribe_userQueueEvents_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/events");
        accessor.setUser(createAuth(activeMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("10. Subscription to /topic/public allowed for authenticated user")
    void subscribe_topicPublic_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/public");
        accessor.setUser(createAuth(activeMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("11. Subscription to /topic/presence allowed when authenticated, rejected when not")
    void subscribe_topicPresence_authCheck() {
        StompHeaderAccessor authAcc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        authAcc.setDestination("/topic/presence");
        authAcc.setUser(createAuth(activeMember));
        Message<?> authMsg = MessageBuilder.createMessage(new byte[0], authAcc.getMessageHeaders());
        assertThat(interceptor.preSend(authMsg, channel)).isNotNull();

        StompHeaderAccessor unauthAcc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        unauthAcc.setDestination("/topic/presence");
        unauthAcc.setUser(null);
        Message<?> unauthMsg = MessageBuilder.createMessage(new byte[0], unauthAcc.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(unauthMsg, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("UNAUTHORIZED");
    }

    // ==========================================
    // 2. SEND AUTHORIZATION & BROKER INJECTION TESTS
    // ==========================================

    @Test
    @DisplayName("12. Unauthenticated client cannot SEND")
    void send_unauthenticated_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/chat.sendMessage");
        accessor.setUser(null);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("UNAUTHORIZED");
    }

    @Test
    @DisplayName("13. Client direct SEND to /topic/conversation.{id} is rejected with FORBIDDEN")
    void send_directTopicConversation_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/conversation." + groupChat.getId());
        accessor.setUser(createAuth(activeMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("FORBIDDEN");
    }

    @Test
    @DisplayName("14. Client direct SEND to /topic/public is rejected with FORBIDDEN")
    void send_directTopicPublic_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/public");
        accessor.setUser(createAuth(activeMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("FORBIDDEN");
    }

    @Test
    @DisplayName("15. Client direct SEND to /queue/events is rejected with FORBIDDEN")
    void send_directQueueEvents_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/queue/events");
        accessor.setUser(createAuth(activeMember));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("FORBIDDEN");
    }

    // ==========================================
    // 3. END-TO-END MESSAGE SENDING & PERSISTENCE
    // ==========================================

    @Test
    @DisplayName("16. Active member sending via /app/chat.sendMessage persists and broadcasts to topic")
    void sendMessage_activeMember_persistsAndBroadcasts() {
        long initialCount = messageRepository.count();

        StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        headerAccessor.setUser(createAuth(activeMember));

        // Intentionally spoof sender as adminUser in payload - system must ignore and use authenticated user!
        ChatMessageDto inbound = ChatMessageDto.builder()
                .conversationId(groupChat.getId())
                .sender("spoofed_admin")
                .content("Valid group message")
                .type(MessageType.CHAT)
                .build();

        webSocketController.sendMessage(inbound, headerAccessor);

        // 1. Verify message was saved to database
        assertThat(messageRepository.count()).isEqualTo(initialCount + 1);
        com.example.realtimechatonline.domain.entity.Message saved = messageRepository.findLatestMessageByConversationId(groupChat.getId()).orElse(null);
        assertThat(saved).isNotNull();
        assertThat(saved.getContent()).isEqualTo("Valid group message");
        // Must be authored by authenticated user, NOT spoofed user!
        assertThat(saved.getSender().getId()).isEqualTo(activeMember.getId());
        assertThat(saved.getSender().getUsername()).isEqualTo(activeMember.getUsername());

        // 2. Verify broadcast to conversation topic
        ArgumentCaptor<ChatMessageDto> broadcastCaptor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation." + groupChat.getId()), broadcastCaptor.capture());
        ChatMessageDto broadcasted = broadcastCaptor.getValue();
        assertThat(broadcasted.getSender()).isEqualTo(activeMember.getUsername());
        assertThat(broadcasted.getContent()).isEqualTo("Valid group message");
    }

    @Test
    @DisplayName("17. Outsider or inactive member sending throws AccessDeniedException and is NOT persisted")
    void sendMessage_outsiderAndInactive_rejectedAndNotPersisted() {
        long initialCount = messageRepository.count();

        // 1. Outsider attempt
        StompHeaderAccessor outsiderAcc = StompHeaderAccessor.create(StompCommand.MESSAGE);
        outsiderAcc.setUser(createAuth(outsider));
        ChatMessageDto outsiderInbound = ChatMessageDto.builder()
                .conversationId(groupChat.getId())
                .content("Outsider intrusion")
                .type(MessageType.CHAT)
                .build();

        assertThatThrownBy(() -> webSocketController.sendMessage(outsiderInbound, outsiderAcc))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member of this group conversation");

        assertThat(messageRepository.count()).isEqualTo(initialCount);

        // 2. Left member attempt
        StompHeaderAccessor leftAcc = StompHeaderAccessor.create(StompCommand.MESSAGE);
        leftAcc.setUser(createAuth(leftMember));
        ChatMessageDto leftInbound = ChatMessageDto.builder()
                .conversationId(groupChat.getId())
                .content("Left member intrusion")
                .type(MessageType.CHAT)
                .build();

        assertThatThrownBy(() -> webSocketController.sendMessage(leftInbound, leftAcc))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member of this group conversation");

        assertThat(messageRepository.count()).isEqualTo(initialCount);

        // 3. Removed member attempt
        StompHeaderAccessor removedAcc = StompHeaderAccessor.create(StompCommand.MESSAGE);
        removedAcc.setUser(createAuth(removedMember));
        ChatMessageDto removedInbound = ChatMessageDto.builder()
                .conversationId(groupChat.getId())
                .content("Removed member intrusion")
                .type(MessageType.CHAT)
                .build();

        assertThatThrownBy(() -> webSocketController.sendMessage(removedInbound, removedAcc))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member of this group conversation");

        // Verify zero messages were persisted
        assertThat(messageRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("18. Blank or over-limit message is rejected and NOT persisted")
    void sendMessage_blankOrTooLong_rejectedAndNotPersisted() {
        long initialCount = messageRepository.count();

        StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        headerAccessor.setUser(createAuth(activeMember));

        // Blank content
        ChatMessageDto blankInbound = ChatMessageDto.builder()
                .conversationId(groupChat.getId())
                .content("   ")
                .type(MessageType.CHAT)
                .build();

        assertThatThrownBy(() -> webSocketController.sendMessage(blankInbound, headerAccessor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message content cannot be blank");

        assertThat(messageRepository.count()).isEqualTo(initialCount);

        // Over 2000 chars
        ChatMessageDto tooLongInbound = ChatMessageDto.builder()
                .conversationId(groupChat.getId())
                .content("x".repeat(2001))
                .type(MessageType.CHAT)
                .build();

        assertThatThrownBy(() -> webSocketController.sendMessage(tooLongInbound, headerAccessor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message content exceeds maximum allowed length");

        assertThat(messageRepository.count()).isEqualTo(initialCount);
    }

    // ==========================================
    // 4. TYPING EVENTS ISOLATION TESTS
    // ==========================================

    @Test
    @DisplayName("19. Active group member typing goes ONLY to /topic/conversation.{id}, NOT to /topic/public")
    void processTyping_activeMember_sentOnlyToConversationTopic() {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        headerAccessor.setUser(createAuth(activeMember));

        ChatMessageDto inbound = ChatMessageDto.builder()
                .conversationId(groupChat.getId())
                .sender("spoofed")
                .content("true")
                .type(MessageType.TYPING)
                .build();

        webSocketController.processTyping(inbound, headerAccessor);

        // Verify sent to private group topic
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation." + groupChat.getId()), argThat((ChatMessageDto dto) ->
                dto.getType() == MessageType.TYPING &&
                        dto.getSender().equals(activeMember.getUsername()) &&
                        "true".equals(dto.getContent()) &&
                        dto.getConversationId().equals(groupChat.getId())
        ));

        // Verify NEVER sent to /topic/public!
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/public"), any(Object.class));
    }

    @Test
    @DisplayName("20. Outsider/inactive member typing is rejected and not broadcast anywhere")
    void processTyping_outsiderAndInactive_rejected() {
        reset(messagingTemplate);

        // 1. Outsider
        StompHeaderAccessor outsiderAcc = StompHeaderAccessor.create(StompCommand.MESSAGE);
        outsiderAcc.setUser(createAuth(outsider));
        ChatMessageDto outsiderInbound = ChatMessageDto.builder()
                .conversationId(groupChat.getId())
                .content("true")
                .type(MessageType.TYPING)
                .build();

        webSocketController.processTyping(outsiderInbound, outsiderAcc);
        verifyNoInteractions(messagingTemplate);

        // 2. Left member
        StompHeaderAccessor leftAcc = StompHeaderAccessor.create(StompCommand.MESSAGE);
        leftAcc.setUser(createAuth(leftMember));
        ChatMessageDto leftInbound = ChatMessageDto.builder()
                .conversationId(groupChat.getId())
                .content("true")
                .type(MessageType.TYPING)
                .build();

        webSocketController.processTyping(leftInbound, leftAcc);
        verifyNoInteractions(messagingTemplate);

        // 3. Removed member
        StompHeaderAccessor removedAcc = StompHeaderAccessor.create(StompCommand.MESSAGE);
        removedAcc.setUser(createAuth(removedMember));
        ChatMessageDto removedInbound = ChatMessageDto.builder()
                .conversationId(groupChat.getId())
                .content("true")
                .type(MessageType.TYPING)
                .build();

        webSocketController.processTyping(removedInbound, removedAcc);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("21. Public typing (null conversationId) is sent to /topic/public")
    void processTyping_publicChat_sentToTopicPublic() {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        headerAccessor.setUser(createAuth(activeMember));

        ChatMessageDto inbound = ChatMessageDto.builder()
                .conversationId(null)
                .content("true")
                .type(MessageType.TYPING)
                .build();

        webSocketController.processTyping(inbound, headerAccessor);

        verify(messagingTemplate).convertAndSend(eq("/topic/public"), argThat((ChatMessageDto dto) ->
                dto.getType() == MessageType.TYPING &&
                        dto.getSender().equals(activeMember.getUsername()) &&
                        "true".equals(dto.getContent()) &&
                        dto.getConversationId() == null
        ));
    }

    // ==========================================
    // 5. CONVERSATION_UPDATE BROADCAST TESTS
    // ==========================================

    @Test
    @DisplayName("22. CONVERSATION_UPDATE event is sent ONLY to active members, NOT to left or removed members")
    void conversationUpdate_sentOnlyToActiveMembers() {
        reset(messagingTemplate);

        StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        headerAccessor.setUser(createAuth(activeMember));

        ChatMessageDto inbound = ChatMessageDto.builder()
                .conversationId(groupChat.getId())
                .content("Message for active members")
                .type(MessageType.CHAT)
                .build();

        webSocketController.sendMessage(inbound, headerAccessor);

        // Active members (adminUser and activeMember) must receive CONVERSATION_UPDATE on /queue/events
        verify(messagingTemplate).convertAndSendToUser(eq(adminUser.getUsername()), eq("/queue/events"), any(UserEventDto.class));
        verify(messagingTemplate).convertAndSendToUser(eq(activeMember.getUsername()), eq("/queue/events"), any(UserEventDto.class));

        // LEFT and REMOVED members must NOT receive CONVERSATION_UPDATE
        verify(messagingTemplate, never()).convertAndSendToUser(eq(leftMember.getUsername()), eq("/queue/events"), any(UserEventDto.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq(removedMember.getUsername()), eq("/queue/events"), any(UserEventDto.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq(outsider.getUsername()), eq("/queue/events"), any(UserEventDto.class));
    }
}
