package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.response.MessageResponseDto;
import com.example.realtimechatonline.domain.dto.websocket.ChatMessageDto;
import com.example.realtimechatonline.domain.entity.Conversation;
import com.example.realtimechatonline.domain.entity.ConversationMemberStatus;
import com.example.realtimechatonline.domain.entity.ConversationType;
import com.example.realtimechatonline.domain.entity.Message;
import com.example.realtimechatonline.domain.entity.MessageType;
import com.example.realtimechatonline.domain.entity.Role;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.domain.mapper.MessageMapper;
import com.example.realtimechatonline.exception.extended.ResourceNotFoundException;
import com.example.realtimechatonline.repository.ConversationMemberRepository;
import com.example.realtimechatonline.repository.ConversationRepository;
import com.example.realtimechatonline.repository.MessageRepository;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatServiceImpl unit tests")
class ChatServiceTest {

    @Mock MessageRepository messageRepository;
    @Mock UserRepository userRepository;
    @Mock ConversationRepository conversationRepository;
    @Mock ConversationMemberRepository conversationMemberRepository;
    @Mock MessageMapper messageMapper;
    @Mock org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @InjectMocks ChatServiceImpl chatService;

    private User alice;
    private User bob;
    private Conversation publicChat;
    private Conversation directChat;

    @BeforeEach
    void setUp() {
        alice = User.builder()
                .id(1L)
                .username("alice")
                .role(Role.USER)
                .build();

        bob = User.builder()
                .id(2L)
                .username("bob")
                .role(Role.USER)
                .build();

        publicChat = Conversation.builder()
                .id(1L)
                .type(ConversationType.PUBLIC)
                .name("Global Chat")
                .build();

        directChat = Conversation.builder()
                .id(20L)
                .type(ConversationType.DIRECT)
                .name("direct_1_2")
                .build();

        lenient().when(conversationRepository.findByTypeAndName(ConversationType.PUBLIC, "Global Chat"))
                .thenReturn(Optional.of(publicChat));
        lenient().when(conversationRepository.findById(1L))
                .thenReturn(Optional.of(publicChat));
    }

    @Test
    @DisplayName("processAndSave — persists message and returns enriched ChatMessage")
    void processAndSave_success() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageDto result = chatService.processAndSave("Hello world", "alice");

        assertThat(result.getType()).isEqualTo(MessageType.CHAT);
        assertThat(result.getSender()).isEqualTo("alice");
        assertThat(result.getContent()).isEqualTo("Hello world");
        assertThat(result.getTime()).isNotBlank();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Hello world");
        assertThat(captor.getValue().getSender()).isEqualTo(alice);
    }

    @Test
    @DisplayName("processAndSave — throws ResourceNotFoundException for unknown sender")
    void processAndSave_unknownSender_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.processAndSave("Hi", "ghost"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("processAndSave — direct conversation throws AccessDeniedException when sender is not member")
    void processAndSave_directConversation_notMember_throwsAccessDenied() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(20L)).thenReturn(Optional.of(directChat));
        when(conversationMemberRepository.existsByConversationIdAndUserId(20L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> chatService.processAndSave(20L, "Hello Bob", "alice"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not a member of this direct conversation");

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("getHistoryByConversation — direct conversation throws AccessDeniedException when user is not member")
    void getHistoryByConversation_directConversation_notMember_throwsAccessDenied() {
        when(conversationRepository.findById(20L)).thenReturn(Optional.of(directChat));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationMemberRepository.existsByConversationIdAndUserId(20L, 1L)).thenReturn(false);

        PageRequest pageable = PageRequest.of(0, 50);

        assertThatThrownBy(() -> chatService.getHistoryByConversation(20L, pageable, "alice"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not a member of this direct conversation");
    }

    @Test
    @DisplayName("getHistoryByConversation — direct conversation returns paged messages when user is member")
    void getHistoryByConversation_directConversation_member_success() {
        when(conversationRepository.findById(20L)).thenReturn(Optional.of(directChat));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationMemberRepository.existsByConversationIdAndUserId(20L, 1L)).thenReturn(true);

        Message msg = Message.builder().id(100L).conversation(directChat).sender(bob).content("Hi Alice").build();
        MessageResponseDto dto = MessageResponseDto.builder().id(100L).senderUsername("bob").content("Hi Alice").build();

        PageRequest pageable = PageRequest.of(0, 50);
        when(messageRepository.findByConversationIdOrderByTimestampDesc(20L, pageable))
                .thenReturn(new PageImpl<>(List.of(msg), pageable, 1));
        when(messageMapper.toMessageResponse(msg)).thenReturn(dto);

        Page<MessageResponseDto> result = chatService.getHistoryByConversation(20L, pageable, "alice");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("Hi Alice");
    }

    @Test
    @DisplayName("buildJoinMessage — returns JOIN type without persisting")
    void buildJoinMessage_returnsJoin() {
        ChatMessageDto msg = chatService.buildJoinMessage("alice");

        assertThat(msg.getType()).isEqualTo(MessageType.JOIN);
        assertThat(msg.getSender()).isEqualTo("alice");
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("buildLeaveMessage — returns LEAVE type without persisting")
    void buildLeaveMessage_returnsLeave() {
        ChatMessageDto msg = chatService.buildLeaveMessage("alice");

        assertThat(msg.getType()).isEqualTo(MessageType.LEAVE);
        assertThat(msg.getSender()).isEqualTo("alice");
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("processAndSave — direct conversation notifies members with CONVERSATION_UPDATE")
    void processAndSave_directConversation_success_notifiesMembers() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(20L)).thenReturn(Optional.of(directChat));
        when(conversationMemberRepository.existsByConversationIdAndUserId(20L, 1L)).thenReturn(true);

        com.example.realtimechatonline.domain.entity.ConversationMember cmAlice = com.example.realtimechatonline.domain.entity.ConversationMember.builder()
                .id(101L).conversation(directChat).user(alice).build();
        com.example.realtimechatonline.domain.entity.ConversationMember cmBob = com.example.realtimechatonline.domain.entity.ConversationMember.builder()
                .id(102L).conversation(directChat).user(bob).build();

        when(conversationMemberRepository.findByConversationId(20L)).thenReturn(List.of(cmAlice, cmBob));
        when(messageRepository.countUnreadMessages(20L, 1L)).thenReturn(0L);
        when(messageRepository.countUnreadMessages(20L, 2L)).thenReturn(1L);

        Message savedMsg = Message.builder().id(999L).content("Hey Bob").sender(alice).conversation(directChat).timestamp(java.time.Instant.now()).build();
        when(messageRepository.save(any(Message.class))).thenReturn(savedMsg);

        ChatMessageDto result = chatService.processAndSave(20L, "Hey Bob", "alice");

        assertThat(result.getId()).isEqualTo(999L);
        assertThat(result.getContent()).isEqualTo("Hey Bob");
        assertThat(result.getConversationId()).isEqualTo(20L);

        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/events"), any(com.example.realtimechatonline.domain.dto.websocket.UserEventDto.class));
        verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/events"), any(com.example.realtimechatonline.domain.dto.websocket.UserEventDto.class));
    }

    @Test
    @DisplayName("processAndSave — group conversation saves message and notifies active members")
    void processAndSave_groupConversation_success_notifiesActiveMembers() {
        Conversation groupChat = Conversation.builder().id(50L).type(ConversationType.GROUP).name("Test Group").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(50L)).thenReturn(Optional.of(groupChat));
        when(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(50L, 1L, ConversationMemberStatus.ACTIVE)).thenReturn(true);

        com.example.realtimechatonline.domain.entity.ConversationMember cmAlice = com.example.realtimechatonline.domain.entity.ConversationMember.builder()
                .id(101L).conversation(groupChat).user(alice).status(ConversationMemberStatus.ACTIVE).build();
        com.example.realtimechatonline.domain.entity.ConversationMember cmBob = com.example.realtimechatonline.domain.entity.ConversationMember.builder()
                .id(102L).conversation(groupChat).user(bob).status(ConversationMemberStatus.ACTIVE).build();

        when(conversationMemberRepository.findByConversationIdAndStatus(50L, ConversationMemberStatus.ACTIVE)).thenReturn(List.of(cmAlice, cmBob));
        when(messageRepository.countUnreadMessages(50L, 1L)).thenReturn(0L);
        when(messageRepository.countUnreadMessages(50L, 2L)).thenReturn(1L);

        Message savedMsg = Message.builder().id(1001L).content("Hello Group").sender(alice).conversation(groupChat).timestamp(java.time.Instant.now()).build();
        when(messageRepository.save(any(Message.class))).thenReturn(savedMsg);

        ChatMessageDto result = chatService.processAndSave(50L, "Hello Group", "alice");

        assertThat(result.getId()).isEqualTo(1001L);
        assertThat(result.getContent()).isEqualTo("Hello Group");
        assertThat(result.getConversationId()).isEqualTo(50L);

        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/events"), any(com.example.realtimechatonline.domain.dto.websocket.UserEventDto.class));
        verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/events"), any(com.example.realtimechatonline.domain.dto.websocket.UserEventDto.class));
    }

    @Test
    @DisplayName("processAndSave — group conversation throws AccessDeniedException when sender is not active member")
    void processAndSave_groupConversation_notActive_throwsAccessDenied() {
        Conversation groupChat = Conversation.builder().id(50L).type(ConversationType.GROUP).name("Test Group").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationRepository.findById(50L)).thenReturn(Optional.of(groupChat));
        when(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(50L, 1L, ConversationMemberStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> chatService.processAndSave(50L, "Hello Group", "alice"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member of this group conversation");

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("processAndSave — throws IllegalArgumentException when content is blank")
    void processAndSave_blankContent_throwsException() {
        assertThatThrownBy(() -> chatService.processAndSave(50L, "   ", "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message content cannot be blank");

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("processAndSave — throws IllegalArgumentException when content exceeds 2000 chars")
    void processAndSave_tooLongContent_throwsException() {
        String longContent = "a".repeat(2001);
        assertThatThrownBy(() -> chatService.processAndSave(50L, longContent, "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message content exceeds maximum allowed length");

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("getHistoryByConversation — group conversation returns messages when user is active")
    void getHistoryByConversation_group_activeMember_success() {
        Conversation groupChat = Conversation.builder().id(50L).type(ConversationType.GROUP).name("Test Group").build();
        when(conversationRepository.findById(50L)).thenReturn(Optional.of(groupChat));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(50L, 1L, ConversationMemberStatus.ACTIVE)).thenReturn(true);

        Message msg = Message.builder().id(100L).conversation(groupChat).sender(bob).content("Hi Alice").build();
        MessageResponseDto dto = MessageResponseDto.builder().id(100L).senderUsername("bob").content("Hi Alice").build();

        PageRequest pageable = PageRequest.of(0, 50);
        when(messageRepository.findByConversationIdOrderByTimestampDesc(50L, pageable))
                .thenReturn(new PageImpl<>(List.of(msg), pageable, 1));
        when(messageMapper.toMessageResponse(msg)).thenReturn(dto);

        Page<MessageResponseDto> result = chatService.getHistoryByConversation(50L, pageable, "alice");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("Hi Alice");
    }

    @Test
    @DisplayName("getHistoryByConversation — group conversation throws AccessDeniedException when user is not active")
    void getHistoryByConversation_group_notActive_throwsAccessDenied() {
        Conversation groupChat = Conversation.builder().id(50L).type(ConversationType.GROUP).name("Test Group").build();
        when(conversationRepository.findById(50L)).thenReturn(Optional.of(groupChat));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(50L, 1L, ConversationMemberStatus.ACTIVE)).thenReturn(false);

        PageRequest pageable = PageRequest.of(0, 50);

        assertThatThrownBy(() -> chatService.getHistoryByConversation(50L, pageable, "alice"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member of this group conversation");
    }

    @Test
    @DisplayName("validateConversationAccess — allows for active group member")
    void validateConversationAccess_group_active_success() {
        Conversation groupChat = Conversation.builder().id(50L).type(ConversationType.GROUP).name("Test Group").build();
        when(conversationRepository.findById(50L)).thenReturn(Optional.of(groupChat));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(50L, 1L, ConversationMemberStatus.ACTIVE)).thenReturn(true);

        chatService.validateConversationAccess(50L, "alice");
    }

    @Test
    @DisplayName("validateConversationAccess — throws AccessDeniedException for non-active group member")
    void validateConversationAccess_group_notActive_throwsAccessDenied() {
        Conversation groupChat = Conversation.builder().id(50L).type(ConversationType.GROUP).name("Test Group").build();
        when(conversationRepository.findById(50L)).thenReturn(Optional.of(groupChat));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(50L, 1L, ConversationMemberStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> chatService.validateConversationAccess(50L, "alice"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member of this group conversation");
    }

    @Test
    @DisplayName("validateConversationAccess — allows for public conversation")
    void validateConversationAccess_public_success() {
        chatService.validateConversationAccess(1L, "alice");
    }
}

