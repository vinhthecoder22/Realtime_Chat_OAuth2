package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.response.ConversationDto;
import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import com.example.realtimechatonline.domain.entity.*;
import com.example.realtimechatonline.domain.mapper.UserMapper;
import com.example.realtimechatonline.exception.extended.ResourceNotFoundException;
import com.example.realtimechatonline.repository.*;
import com.example.realtimechatonline.service.impl.ConversationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationServiceImpl Unit Tests")
class ConversationServiceTest {

    @Mock ConversationRepository conversationRepository;
    @Mock ConversationMemberRepository conversationMemberRepository;
    @Mock MessageRepository messageRepository;
    @Mock UserRepository userRepository;
    @Mock FriendRequestRepository friendRequestRepository;
    @Mock UserMapper userMapper;
    @Mock org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @Mock com.example.realtimechatonline.service.PresenceService presenceService;
    @Mock com.example.realtimechatonline.service.GroupMembershipService groupMembershipService;

    @InjectMocks ConversationServiceImpl conversationService;

    private User currentUser;
    private User bob;
    private Conversation publicConv;
    private Conversation directConv;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(1L).username("alice").fullname("Alice Carroll").build();
        bob = User.builder().id(2L).username("bob").fullname("Bob Builder").build();

        publicConv = Conversation.builder()
                .id(100L)
                .type(ConversationType.PUBLIC)
                .name("Global Chat")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        directConv = Conversation.builder()
                .id(200L)
                .type(ConversationType.DIRECT)
                .name("direct_1_2")
                .createdAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("getConversations — paginated, sorts conversations and enriches latest messages and member names")
    void getConversations_paged_success() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));

        List<Conversation> convList = List.of(directConv, publicConv);
        Page<Conversation> page = new PageImpl<>(convList, PageRequest.of(0, 20), 2);
        when(conversationRepository.findUserConversationsOrderByLastActivity(eq(1L), any()))
                .thenReturn(page);

        // Mock latest messages
        Message directMsg = Message.builder()
                .id(10L)
                .conversation(directConv)
                .sender(bob)
                .content("Hey Alice!")
                .timestamp(Instant.parse("2026-01-03T12:00:00Z"))
                .build();
        Message publicMsg = Message.builder()
                .id(11L)
                .conversation(publicConv)
                .sender(currentUser)
                .content("Hello everyone!")
                .timestamp(Instant.parse("2026-01-02T15:00:00Z"))
                .build();

        when(messageRepository.findLatestMessagesByConversationIds(List.of(200L, 100L)))
                .thenReturn(List.of(directMsg, publicMsg));

        // Mock members for directConv
        ConversationMember cm1 = ConversationMember.builder().conversation(directConv).user(currentUser).build();
        ConversationMember cm2 = ConversationMember.builder().conversation(directConv).user(bob).build();
        when(conversationMemberRepository.findByConversationIdIn(List.of(200L)))
                .thenReturn(List.of(cm1, cm2));

        UserProfileDto bobProfile = UserProfileDto.builder().id(2L).username("bob").fullname("Bob Builder").build();
        when(userMapper.toUserProfile(bob)).thenReturn(bobProfile);

        Page<ConversationDto> result = conversationService.getConversations("alice", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);

        // 1st item: direct conversation with bob
        ConversationDto c1 = result.getContent().get(0);
        assertThat(c1.getId()).isEqualTo(200L);
        assertThat(c1.getType()).isEqualTo(ConversationType.DIRECT);
        assertThat(c1.getName()).isEqualTo("Bob Builder");
        assertThat(c1.getLastMessage()).isEqualTo("Hey Alice!");
        assertThat(c1.getLastMessageSender()).isEqualTo("bob");
        assertThat(c1.getLastMessageTimestamp()).isEqualTo(Instant.parse("2026-01-03T12:00:00Z"));
        assertThat(c1.getOtherUser()).isNotNull();
        assertThat(c1.getOtherUser().getUsername()).isEqualTo("bob");

        // 2nd item: public conversation
        ConversationDto c2 = result.getContent().get(1);
        assertThat(c2.getId()).isEqualTo(100L);
        assertThat(c2.getType()).isEqualTo(ConversationType.PUBLIC);
        assertThat(c2.getName()).isEqualTo("Global Chat");
        assertThat(c2.getLastMessage()).isEqualTo("Hello everyone!");
        assertThat(c2.getLastMessageSender()).isEqualTo("alice");
    }

    @Test
    @DisplayName("getConversationById — returns conversation when user is a member")
    void getConversationById_success() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(directConv));
        when(conversationMemberRepository.existsByConversationIdAndUserId(200L, 1L)).thenReturn(true);

        ConversationMember cm1 = ConversationMember.builder().conversation(directConv).user(currentUser).build();
        ConversationMember cm2 = ConversationMember.builder().conversation(directConv).user(bob).build();
        when(conversationMemberRepository.findByConversationId(200L)).thenReturn(List.of(cm1, cm2));

        UserProfileDto bobProfile = UserProfileDto.builder().id(2L).username("bob").fullname("Bob Builder").build();
        when(userMapper.toUserProfile(bob)).thenReturn(bobProfile);

        ConversationDto result = conversationService.getConversationById(200L, "alice");

        assertThat(result.getId()).isEqualTo(200L);
        assertThat(result.getName()).isEqualTo("Bob Builder");
        assertThat(result.getType()).isEqualTo(ConversationType.DIRECT);
    }

    @Test
    @DisplayName("getConversationById — throws AccessDeniedException when user is NOT a member of direct chat")
    void getConversationById_notMember_throwsAccessDenied() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(directConv));
        when(conversationMemberRepository.existsByConversationIdAndUserId(200L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> conversationService.getConversationById(200L, "alice"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not a member of this direct conversation");
    }

    @Test
    @DisplayName("getConversationById — throws ResourceNotFoundException when conversation does not exist")
    void getConversationById_notFound_throws() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getConversationById(999L, "alice"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    @DisplayName("getOrCreateDirectConversation — returns existing conversation between friends")
    void getOrCreateDirectConversation_existing_success() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

        FriendRequest friendRequest = FriendRequest.builder()
                .user1(currentUser)
                .user2(bob)
                .sender(currentUser)
                .status(FriendRequestStatus.ACCEPTED)
                .build();
        when(friendRequestRepository.findByUser1AndUser2(currentUser, bob)).thenReturn(Optional.of(friendRequest));

        when(conversationRepository.findByTypeAndName(ConversationType.DIRECT, "direct_1_2"))
                .thenReturn(Optional.of(directConv));

        UserProfileDto bobProfile = UserProfileDto.builder().id(2L).username("bob").fullname("Bob Builder").build();
        when(userMapper.toUserProfile(bob)).thenReturn(bobProfile);

        ConversationDto dto = conversationService.getOrCreateDirectConversation(2L, "alice");

        assertThat(dto.getId()).isEqualTo(200L);
        assertThat(dto.getName()).isEqualTo("Bob Builder");
        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("markConversationAsRead — updates cursor and broadcasts event")
    void markConversationAsRead_success() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(directConv));
        when(conversationMemberRepository.existsByConversationIdAndUserId(200L, 1L)).thenReturn(true);

        ConversationMember cmAlice = ConversationMember.builder()
                .id(101L).conversation(directConv).user(currentUser).lastReadMessageId(50L).build();
        ConversationMember cmBob = ConversationMember.builder()
                .id(102L).conversation(directConv).user(bob).build();

        when(conversationMemberRepository.findByConversationIdAndUserId(200L, 1L)).thenReturn(Optional.of(cmAlice));
        when(conversationMemberRepository.findByConversationId(200L)).thenReturn(List.of(cmAlice, cmBob));

        Message latestMsg = Message.builder().id(60L).conversation(directConv).build();
        when(messageRepository.findLatestMessageByConversationId(200L)).thenReturn(Optional.of(latestMsg));
        when(messageRepository.findById(60L)).thenReturn(Optional.of(latestMsg));
        when(conversationMemberRepository.updateLastReadIfGreater(eq(101L), eq(60L), any(Instant.class))).thenReturn(1);
        when(messageRepository.countUnreadMessages(200L, 1L)).thenReturn(0L);

        com.example.realtimechatonline.domain.dto.response.ReadReceiptResponseDto response =
                conversationService.markConversationAsRead(200L, 60L, "alice");

        assertThat(response.getConversationId()).isEqualTo(200L);
        assertThat(response.getLastReadMessageId()).isEqualTo(60L);
        assertThat(response.getUnreadCount()).isEqualTo(0L);
        assertThat(cmAlice.getLastReadMessageId()).isEqualTo(60L);

        verify(conversationMemberRepository).updateLastReadIfGreater(eq(101L), eq(60L), any(Instant.class));
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/events"), any(com.example.realtimechatonline.domain.dto.websocket.UserEventDto.class));
        verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/events"), any(com.example.realtimechatonline.domain.dto.websocket.UserEventDto.class));
    }

    @Test
    @DisplayName("markConversationAsRead — monotonic cursor does not rewind when given older messageId")
    void markConversationAsRead_monotonicCursor_doesNotRewind() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(directConv));
        when(conversationMemberRepository.existsByConversationIdAndUserId(200L, 1L)).thenReturn(true);

        ConversationMember cmAlice = ConversationMember.builder()
                .id(101L).conversation(directConv).user(currentUser).lastReadMessageId(80L).build();

        when(conversationMemberRepository.findByConversationIdAndUserId(200L, 1L)).thenReturn(Optional.of(cmAlice));

        Message latestMsg = Message.builder().id(90L).conversation(directConv).build();
        Message olderMsg = Message.builder().id(70L).conversation(directConv).build();
        when(messageRepository.findLatestMessageByConversationId(200L)).thenReturn(Optional.of(latestMsg));
        when(messageRepository.findById(70L)).thenReturn(Optional.of(olderMsg));

        conversationService.markConversationAsRead(200L, 70L, "alice");

        // Cursor should still remain 80L, never rewind to 70L
        assertThat(cmAlice.getLastReadMessageId()).isEqualTo(80L);
        verify(conversationMemberRepository, never()).updateLastReadIfGreater(any(), any(), any());
    }

    @Test
    @DisplayName("markConversationAsRead — concurrent update preserves higher cursor if racing thread was faster")
    void markConversationAsRead_concurrentUpdate_preservesHigherCursor() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(directConv));
        when(conversationMemberRepository.existsByConversationIdAndUserId(200L, 1L)).thenReturn(true);

        ConversationMember cmAlice = ConversationMember.builder()
                .id(101L).conversation(directConv).user(currentUser).lastReadMessageId(50L).build();

        when(conversationMemberRepository.findByConversationIdAndUserId(200L, 1L)).thenReturn(Optional.of(cmAlice));

        Message latestMsg = Message.builder().id(90L).conversation(directConv).build();
        when(messageRepository.findLatestMessageByConversationId(200L)).thenReturn(Optional.of(latestMsg));
        when(messageRepository.findById(90L)).thenReturn(Optional.of(latestMsg));

        // Atomic query returns 0 because another thread concurrently wrote lastReadMessageId = 95L
        when(conversationMemberRepository.updateLastReadIfGreater(eq(101L), eq(90L), any(Instant.class))).thenReturn(0);
        ConversationMember reloaded = ConversationMember.builder()
                .id(101L).conversation(directConv).user(currentUser).lastReadMessageId(95L).build();
        when(conversationMemberRepository.findById(101L)).thenReturn(Optional.of(reloaded));

        com.example.realtimechatonline.domain.dto.response.ReadReceiptResponseDto response =
                conversationService.markConversationAsRead(200L, 90L, "alice");

        assertThat(response.getLastReadMessageId()).isEqualTo(95L);
    }

    @Test
    @DisplayName("markConversationAsRead — throws IllegalArgumentException when message belongs to another conversation")
    void markConversationAsRead_messageBelongsToAnotherConversation_throws() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(directConv));
        when(conversationMemberRepository.existsByConversationIdAndUserId(200L, 1L)).thenReturn(true);
        ConversationMember cmAlice = ConversationMember.builder()
                .id(101L).conversation(directConv).user(currentUser).build();
        when(conversationMemberRepository.findByConversationIdAndUserId(200L, 1L)).thenReturn(Optional.of(cmAlice));

        Conversation otherConv = Conversation.builder().id(300L).type(ConversationType.DIRECT).build();
        Message msgOther = Message.builder().id(55L).conversation(otherConv).build();
        when(messageRepository.findLatestMessageByConversationId(200L)).thenReturn(Optional.of(Message.builder().id(100L).conversation(directConv).build()));
        when(messageRepository.findById(55L)).thenReturn(Optional.of(msgOther));

        assertThatThrownBy(() -> conversationService.markConversationAsRead(200L, 55L, "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to conversation");
    }

    @Test
    @DisplayName("markConversationAsRead — throws ResourceNotFoundException when messageId does not exist")
    void markConversationAsRead_messageNotFound_throws() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(directConv));
        when(conversationMemberRepository.existsByConversationIdAndUserId(200L, 1L)).thenReturn(true);
        ConversationMember cmAlice = ConversationMember.builder()
                .id(101L).conversation(directConv).user(currentUser).build();
        when(conversationMemberRepository.findByConversationIdAndUserId(200L, 1L)).thenReturn(Optional.of(cmAlice));

        when(messageRepository.findLatestMessageByConversationId(200L)).thenReturn(Optional.of(Message.builder().id(100L).conversation(directConv).build()));
        when(messageRepository.findById(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.markConversationAsRead(200L, 99999L, "alice"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Message not found");
    }

    @Test
    @DisplayName("markConversationAsRead — throws AccessDeniedException when non-member")
    void markConversationAsRead_nonMember_throwsAccessDenied() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));
        when(conversationRepository.findById(200L)).thenReturn(Optional.of(directConv));
        when(conversationMemberRepository.existsByConversationIdAndUserId(200L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> conversationService.markConversationAsRead(200L, null, "alice"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not a member of this direct conversation");
    }
}