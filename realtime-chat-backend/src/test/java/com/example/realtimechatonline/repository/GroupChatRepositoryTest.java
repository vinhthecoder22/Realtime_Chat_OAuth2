package com.example.realtimechatonline.repository;

import com.example.realtimechatonline.domain.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
@DisplayName("Group Chat Database & Repository Refactoring Tests (STEP 10.2)")
class GroupChatRepositoryTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User alice;
    private User bob;
    private User charlie;
    private User dave;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(User.builder()
                .username("test_alice_" + System.nanoTime())
                .email("alice_" + System.nanoTime() + "@test.com")
                .fullname("Alice Wonderland")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        bob = userRepository.save(User.builder()
                .username("test_bob_" + System.nanoTime())
                .email("bob_" + System.nanoTime() + "@test.com")
                .fullname("Bob Builder")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        charlie = userRepository.save(User.builder()
                .username("test_charlie_" + System.nanoTime())
                .email("charlie_" + System.nanoTime() + "@test.com")
                .fullname("Charlie Chaplin")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        dave = userRepository.save(User.builder()
                .username("test_dave_" + System.nanoTime())
                .email("dave_" + System.nanoTime() + "@test.com")
                .fullname("Dave Miller")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());
    }

    @Test
    @DisplayName("1. Entity and Repository support ACTIVE, LEFT, and REMOVED statuses")
    void testEntityAndRepository_supportAllStatuses() {
        Conversation group = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Alpha Team")
                .creator(alice)
                .avatar("https://example.com/alpha.png")
                .createdAt(Instant.now())
                .build());

        ConversationMember cmActive = conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(alice)
                .role(ConversationMemberRole.ADMIN)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        ConversationMember cmLeft = conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(bob)
                .role(ConversationMemberRole.MEMBER)
                .status(ConversationMemberStatus.LEFT)
                .joinedAt(Instant.now().minusSeconds(3600))
                .leftAt(Instant.now())
                .build());

        ConversationMember cmRemoved = conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(charlie)
                .role(ConversationMemberRole.MEMBER)
                .status(ConversationMemberStatus.REMOVED)
                .joinedAt(Instant.now().minusSeconds(7200))
                .leftAt(Instant.now().minusSeconds(1800))
                .build());

        entityManager.flush();
        entityManager.clear();

        // Query active members
        List<ConversationMember> activeMembers = conversationMemberRepository.findByConversationIdAndStatus(
                group.getId(), ConversationMemberStatus.ACTIVE);
        assertThat(activeMembers).hasSize(1);
        assertThat(activeMembers.get(0).getUser().getId()).isEqualTo(alice.getId());

        // Check exists by status
        assertThat(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(
                group.getId(), alice.getId(), ConversationMemberStatus.ACTIVE)).isTrue();
        assertThat(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(
                group.getId(), bob.getId(), ConversationMemberStatus.ACTIVE)).isFalse();
        assertThat(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(
                group.getId(), bob.getId(), ConversationMemberStatus.LEFT)).isTrue();
        assertThat(conversationMemberRepository.existsByConversationIdAndUserIdAndStatus(
                group.getId(), charlie.getId(), ConversationMemberStatus.REMOVED)).isTrue();

        // Check count by status
        assertThat(conversationMemberRepository.countByConversationIdAndStatus(
                group.getId(), ConversationMemberStatus.ACTIVE)).isEqualTo(1);
        assertThat(conversationMemberRepository.countByConversationIdAndStatus(
                group.getId(), ConversationMemberStatus.LEFT)).isEqualTo(1);
        assertThat(conversationMemberRepository.countByConversationIdAndStatus(
                group.getId(), ConversationMemberStatus.REMOVED)).isEqualTo(1);
    }

    @Test
    @DisplayName("2. Legacy membership defaults to ACTIVE when status is omitted")
    void testLegacyMembership_defaultsToActive() {
        Conversation direct = conversationRepository.save(Conversation.builder()
                .type(ConversationType.DIRECT)
                .name("direct_" + alice.getId() + "_" + bob.getId())
                .createdAt(Instant.now())
                .build());

        // Do not explicitly set status, verify builder default
        ConversationMember member = ConversationMember.builder()
                .conversation(direct)
                .user(alice)
                .role(ConversationMemberRole.MEMBER)
                .joinedAt(Instant.now())
                .build();

        ConversationMember saved = conversationMemberRepository.save(member);
        entityManager.flush();
        entityManager.clear();

        ConversationMember reloaded = conversationMemberRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ConversationMemberStatus.ACTIVE);
        assertThat(reloaded.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("3. Message sender does not increase their own unread count in GROUP")
    void testUnreadCount_senderDoesNotIncreaseOwnCount() {
        Conversation group = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Dev Team")
                .creator(alice)
                .createdAt(Instant.now())
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(alice)
                .role(ConversationMemberRole.ADMIN)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(bob)
                .role(ConversationMemberRole.MEMBER)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        // Alice sends 3 messages
        messageRepository.save(Message.builder().conversation(group).sender(alice).content("Msg 1").timestamp(Instant.now()).build());
        messageRepository.save(Message.builder().conversation(group).sender(alice).content("Msg 2").timestamp(Instant.now()).build());
        messageRepository.save(Message.builder().conversation(group).sender(alice).content("Msg 3").timestamp(Instant.now()).build());

        entityManager.flush();
        entityManager.clear();

        // Alice (sender) should have 0 unread
        long aliceUnread = messageRepository.countUnreadMessages(group.getId(), alice.getId());
        assertThat(aliceUnread).isEqualTo(0L);

        // Bob (recipient) should have 3 unread
        long bobUnread = messageRepository.countUnreadMessages(group.getId(), bob.getId());
        assertThat(bobUnread).isEqualTo(3L);
    }

    @Test
    @DisplayName("4. Two members in same GROUP with different read cursors have different unread counts")
    void testUnreadCount_membersWithDifferentReadCursors_haveDifferentUnreadCounts() {
        Conversation group = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Release Planning")
                .creator(alice)
                .createdAt(Instant.now())
                .build());

        ConversationMember cmBob = conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(bob)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        ConversationMember cmCharlie = conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(charlie)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        ConversationMember cmDave = conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(dave)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        // Alice sends 5 messages
        Message m1 = messageRepository.save(Message.builder().conversation(group).sender(alice).content("Step 1").timestamp(Instant.now()).build());
        Message m2 = messageRepository.save(Message.builder().conversation(group).sender(alice).content("Step 2").timestamp(Instant.now()).build());
        Message m3 = messageRepository.save(Message.builder().conversation(group).sender(alice).content("Step 3").timestamp(Instant.now()).build());
        Message m4 = messageRepository.save(Message.builder().conversation(group).sender(alice).content("Step 4").timestamp(Instant.now()).build());
        Message m5 = messageRepository.save(Message.builder().conversation(group).sender(alice).content("Step 5").timestamp(Instant.now()).build());

        // Bob read up to m2 -> unread: m3, m4, m5 = 3
        cmBob.setLastReadMessageId(m2.getId());
        cmBob.setLastReadAt(Instant.now());
        conversationMemberRepository.save(cmBob);

        // Charlie read up to m4 -> unread: m5 = 1
        cmCharlie.setLastReadMessageId(m4.getId());
        cmCharlie.setLastReadAt(Instant.now());
        conversationMemberRepository.save(cmCharlie);

        // Dave read up to m5 -> unread: 0
        cmDave.setLastReadMessageId(m5.getId());
        cmDave.setLastReadAt(Instant.now());
        conversationMemberRepository.save(cmDave);

        entityManager.flush();
        entityManager.clear();

        assertThat(messageRepository.countUnreadMessages(group.getId(), bob.getId())).isEqualTo(3L);
        assertThat(messageRepository.countUnreadMessages(group.getId(), charlie.getId())).isEqualTo(1L);
        assertThat(messageRepository.countUnreadMessages(group.getId(), dave.getId())).isEqualTo(0L);
    }

    @Test
    @DisplayName("5. Active member with null read cursor has all messages by others counted as unread")
    void testUnreadCount_nullReadCursor_countsAllOthersMessages() {
        Conversation group = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("New Joiners")
                .createdAt(Instant.now())
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(bob)
                .lastReadMessageId(null)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        // Alice sends 4 messages
        for (int i = 1; i <= 4; i++) {
            messageRepository.save(Message.builder().conversation(group).sender(alice).content("Msg " + i).timestamp(Instant.now()).build());
        }

        entityManager.flush();
        entityManager.clear();

        long unread = messageRepository.countUnreadMessages(group.getId(), bob.getId());
        assertThat(unread).isEqualTo(4L);
    }

    @Test
    @DisplayName("6. Non-active (LEFT or REMOVED) member has unread count = 0")
    void testUnreadCount_nonActiveMember_returnsZero() {
        Conversation group = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Ex-members Group")
                .createdAt(Instant.now())
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(bob)
                .status(ConversationMemberStatus.REMOVED)
                .leftAt(Instant.now())
                .joinedAt(Instant.now().minusSeconds(1000))
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(charlie)
                .status(ConversationMemberStatus.LEFT)
                .leftAt(Instant.now())
                .joinedAt(Instant.now().minusSeconds(1000))
                .build());

        messageRepository.save(Message.builder().conversation(group).sender(alice).content("Secret 1").timestamp(Instant.now()).build());
        messageRepository.save(Message.builder().conversation(group).sender(alice).content("Secret 2").timestamp(Instant.now()).build());

        entityManager.flush();
        entityManager.clear();

        assertThat(messageRepository.countUnreadMessages(group.getId(), bob.getId())).isEqualTo(0L);
        assertThat(messageRepository.countUnreadMessages(group.getId(), charlie.getId())).isEqualTo(0L);
    }

    @Test
    @DisplayName("7. DIRECT and PUBLIC unread counts continue to work as before")
    void testUnreadCount_directAndPublic_remainUnchanged() {
        // Direct
        Conversation direct = conversationRepository.save(Conversation.builder()
                .type(ConversationType.DIRECT)
                .name("direct_alice_bob")
                .createdAt(Instant.now())
                .build());

        ConversationMember cmAliceDirect = conversationMemberRepository.save(ConversationMember.builder()
                .conversation(direct)
                .user(alice)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        ConversationMember cmBobDirect = conversationMemberRepository.save(ConversationMember.builder()
                .conversation(direct)
                .user(bob)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        Message dm1 = messageRepository.save(Message.builder().conversation(direct).sender(alice).content("Hi Bob").timestamp(Instant.now()).build());
        Message dm2 = messageRepository.save(Message.builder().conversation(direct).sender(alice).content("Are you there?").timestamp(Instant.now()).build());

        // Bob has read up to dm1
        cmBobDirect.setLastReadMessageId(dm1.getId());
        conversationMemberRepository.save(cmBobDirect);

        // Public
        Conversation global = conversationRepository.findByTypeAndName(ConversationType.PUBLIC, "Global Chat")
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .type(ConversationType.PUBLIC)
                        .name("Global Chat")
                        .createdAt(Instant.now())
                        .build()));

        Message pub1 = messageRepository.save(Message.builder().conversation(global).sender(alice).content("Public 1").timestamp(Instant.now()).build());
        Message pub2 = messageRepository.save(Message.builder().conversation(global).sender(alice).content("Public 2").timestamp(Instant.now()).build());

        // Charlie has not read public -> unread = 0 by convention
        // Dave has read public up to pub1 -> unread = 1 (pub2)
        ConversationMember cmDavePublic = conversationMemberRepository.save(ConversationMember.builder()
                .conversation(global)
                .user(dave)
                .lastReadMessageId(pub1.getId())
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        entityManager.flush();
        entityManager.clear();

        // Direct assertions
        assertThat(messageRepository.countUnreadMessages(direct.getId(), bob.getId())).isEqualTo(1L);
        assertThat(messageRepository.countUnreadMessages(direct.getId(), alice.getId())).isEqualTo(0L);

        // Public assertions
        assertThat(messageRepository.countUnreadMessages(global.getId(), charlie.getId())).isEqualTo(0L);
        assertThat(messageRepository.countUnreadMessages(global.getId(), dave.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("8. Conversation list query includes Global Chat and ACTIVE conversations, excludes LEFT/REMOVED")
    void testConversationListQuery_doesNotExcludePublicOrActiveDirect() {
        // Global Chat (PUBLIC)
        Conversation global = conversationRepository.findByTypeAndName(ConversationType.PUBLIC, "Global Chat")
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .type(ConversationType.PUBLIC)
                        .name("Global Chat")
                        .createdAt(Instant.now().minusSeconds(10000))
                        .build()));

        // Active DIRECT
        Conversation activeDirect = conversationRepository.save(Conversation.builder()
                .type(ConversationType.DIRECT)
                .name("direct_alice_bob")
                .createdAt(Instant.now().minusSeconds(5000))
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(activeDirect)
                .user(alice)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now().minusSeconds(5000))
                .build());

        // Active GROUP
        Conversation activeGroup = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Active Project")
                .createdAt(Instant.now().minusSeconds(3000))
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(activeGroup)
                .user(alice)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now().minusSeconds(3000))
                .build());

        // Removed GROUP
        Conversation removedGroup = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Old Project")
                .createdAt(Instant.now().minusSeconds(8000))
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(removedGroup)
                .user(alice)
                .status(ConversationMemberStatus.REMOVED)
                .leftAt(Instant.now().minusSeconds(1000))
                .joinedAt(Instant.now().minusSeconds(8000))
                .build());

        entityManager.flush();
        entityManager.clear();

        Page<Conversation> page = conversationRepository.findUserConversationsOrderByLastActivity(
                alice.getId(), PageRequest.of(0, 10));

        List<Long> conversationIds = page.getContent().stream().map(Conversation::getId).toList();

        // Must include Global, activeDirect, activeGroup
        assertThat(conversationIds).contains(global.getId(), activeDirect.getId(), activeGroup.getId());

        // Must NOT include removedGroup
        assertThat(conversationIds).doesNotContain(removedGroup.getId());
    }

    @Test
    @DisplayName("9. Unique constraint on (conversation_id, user_id) prevents duplicate membership")
    void testUniqueConstraint_preventsDuplicateMembership() {
        Conversation group = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Unique Members Test")
                .createdAt(Instant.now())
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(alice)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());
        entityManager.flush();

        // Attempting to save another record for same conversation and user should fail
        ConversationMember duplicate = ConversationMember.builder()
                .conversation(group)
                .user(alice)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        assertThatThrownBy(() -> {
            conversationMemberRepository.save(duplicate);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("10. Conversation allows non-unique names for GROUP type")
    void testConversation_allowsNonUniqueNamesForGroup() {
        Conversation group1 = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Duplicate Name Group")
                .creator(alice)
                .avatar("https://img.com/1.png")
                .createdAt(Instant.now())
                .build());

        Conversation group2 = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("Duplicate Name Group")
                .creator(bob)
                .avatar("https://img.com/2.png")
                .createdAt(Instant.now())
                .build());

        entityManager.flush();
        entityManager.clear();

        assertThat(group1.getId()).isNotNull();
        assertThat(group2.getId()).isNotNull();
        assertThat(group1.getId()).isNotEqualTo(group2.getId());
        assertThat(group1.getName()).isEqualTo(group2.getName());
    }

    @Test
    @DisplayName("11. Batch unread count query works across mixed conversation types")
    void testBatchUnreadCount_byConversationIds() {
        Conversation direct = conversationRepository.save(Conversation.builder()
                .type(ConversationType.DIRECT)
                .name("batch_direct")
                .createdAt(Instant.now())
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(direct)
                .user(alice)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        Conversation group = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name("batch_group")
                .createdAt(Instant.now())
                .build());
        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(group)
                .user(alice)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        // Bob sends 2 messages to direct, 3 messages to group
        messageRepository.save(Message.builder().conversation(direct).sender(bob).content("D1").timestamp(Instant.now()).build());
        messageRepository.save(Message.builder().conversation(direct).sender(bob).content("D2").timestamp(Instant.now()).build());

        messageRepository.save(Message.builder().conversation(group).sender(bob).content("G1").timestamp(Instant.now()).build());
        messageRepository.save(Message.builder().conversation(group).sender(bob).content("G2").timestamp(Instant.now()).build());
        messageRepository.save(Message.builder().conversation(group).sender(bob).content("G3").timestamp(Instant.now()).build());

        entityManager.flush();
        entityManager.clear();

        List<Object[]> results = messageRepository.countUnreadMessagesByConversationIds(
                List.of(direct.getId(), group.getId()), alice.getId());

        Map<Long, Long> unreadMap = results.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        assertThat(unreadMap.get(direct.getId())).isEqualTo(2L);
        assertThat(unreadMap.get(group.getId())).isEqualTo(3L);
    }
}
