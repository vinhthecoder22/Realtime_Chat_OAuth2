package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.request.*;
import com.example.realtimechatonline.domain.dto.response.ConversationDto;
import com.example.realtimechatonline.domain.dto.response.GroupMemberResponseDto;
import com.example.realtimechatonline.domain.entity.*;
import com.example.realtimechatonline.exception.extended.ResourceNotFoundException;
import com.example.realtimechatonline.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
@DisplayName("GroupChatServiceIntegrationTest (STEP 10.3 Business Logic & Integration)")
class GroupChatServiceIntegrationTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private EntityManager entityManager;

    private User alice;
    private User bob;
    private User charlie;
    private User dave;
    private Conversation publicConv;

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();
        alice = userRepository.save(User.builder()
                .username("test_alice_" + nano)
                .email("alice_" + nano + "@test.com")
                .fullname("Alice Test")
                .password("hashed_password")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        bob = userRepository.save(User.builder()
                .username("test_bob_" + nano)
                .email("bob_" + nano + "@test.com")
                .fullname("Bob Test")
                .password("hashed_password")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        charlie = userRepository.save(User.builder()
                .username("test_charlie_" + nano)
                .email("charlie_" + nano + "@test.com")
                .fullname("Charlie Test")
                .password("hashed_password")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        dave = userRepository.save(User.builder()
                .username("test_dave_" + nano)
                .email("dave_" + nano + "@test.com")
                .fullname("Dave Test")
                .password("hashed_password")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        // Set up friendships: Alice - Bob, Alice - Charlie
        createFriendship(alice, bob, FriendRequestStatus.ACCEPTED);
        createFriendship(alice, charlie, FriendRequestStatus.ACCEPTED);

        // Ensure a PUBLIC conversation exists for regression testing
        publicConv = conversationRepository.findByTypeAndName(ConversationType.PUBLIC, "Global Chat")
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .type(ConversationType.PUBLIC)
                        .name("Global Chat")
                        .createdAt(Instant.now())
                        .build()));

        entityManager.flush();
        entityManager.clear();
    }

    private void createFriendship(User u1, User u2, FriendRequestStatus status) {
        User first = u1.getId() < u2.getId() ? u1 : u2;
        User second = u1.getId() > u2.getId() ? u1 : u2;
        friendRequestRepository.findByUser1AndUser2(first, second)
                .ifPresentOrElse(
                        fr -> {
                            fr.setStatus(status);
                            friendRequestRepository.save(fr);
                        },
                        () -> friendRequestRepository.save(FriendRequest.builder()
                                .user1(first)
                                .user2(second)
                                .sender(first)
                                .status(status)
                                .createdAt(Instant.now())
                                .build())
                );
    }

    // 1. Tạo nhóm thành công và xác nhận creator là ADMIN
    @Test
    @DisplayName("Scenario 1: createGroup - success, creator is ADMIN, members are MEMBER ACTIVE")
    void createGroup_success_creatorIsAdmin() {
        CreateGroupRequestDto request = CreateGroupRequestDto.builder()
                .name("Alpha Project Team")
                .memberUserIds(List.of(bob.getId(), charlie.getId()))
                .build();

        ConversationDto result = conversationService.createGroupConversation(request, alice.getUsername());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getType()).isEqualTo(ConversationType.GROUP);
        assertThat(result.getName()).isEqualTo("Alpha Project Team");
        assertThat(result.getMemberCount()).isEqualTo(3L);
        assertThat(result.getCurrentUserRole()).isEqualTo(ConversationMemberRole.ADMIN);
        assertThat(result.getUnreadCount()).isEqualTo(0L);

        // Verify DB state
        Conversation savedConv = conversationRepository.findById(result.getId()).orElseThrow();
        assertThat(savedConv.getCreator().getId()).isEqualTo(alice.getId());

        List<ConversationMember> members = conversationMemberRepository.findByConversationIdAndStatus(result.getId(), ConversationMemberStatus.ACTIVE);
        assertThat(members).hasSize(3);

        ConversationMember aliceMember = conversationMemberRepository.findByConversationIdAndUserId(result.getId(), alice.getId()).orElseThrow();
        assertThat(aliceMember.getRole()).isEqualTo(ConversationMemberRole.ADMIN);
        assertThat(aliceMember.getStatus()).isEqualTo(ConversationMemberStatus.ACTIVE);

        ConversationMember bobMember = conversationMemberRepository.findByConversationIdAndUserId(result.getId(), bob.getId()).orElseThrow();
        assertThat(bobMember.getRole()).isEqualTo(ConversationMemberRole.MEMBER);
        assertThat(bobMember.getStatus()).isEqualTo(ConversationMemberStatus.ACTIVE);
    }

    // 2. Từ chối tên nhóm rỗng hoặc quá dài
    @Test
    @DisplayName("Scenario 2: createGroup - rejects blank or excessively long group name")
    void createGroup_rejectsBlankOrTooLongName() {
        CreateGroupRequestDto blankRequest = CreateGroupRequestDto.builder()
                .name("   ")
                .memberUserIds(List.of(bob.getId()))
                .build();

        assertThatThrownBy(() -> conversationService.createGroupConversation(blankRequest, alice.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100 characters");

        CreateGroupRequestDto tooLongRequest = CreateGroupRequestDto.builder()
                .name("a".repeat(101))
                .memberUserIds(List.of(bob.getId()))
                .build();

        assertThatThrownBy(() -> conversationService.createGroupConversation(tooLongRequest, alice.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100 characters");
    }

    // 3. Từ chối ID user không tồn tại hoặc danh sách ID không hợp lệ
    @Test
    @DisplayName("Scenario 3: createGroup - rejects non-existent, empty, or self in memberUserIds")
    void createGroup_rejectsInvalidOrNonExistentUserIds() {
        // Non-existent user ID
        CreateGroupRequestDto nonExistentRequest = CreateGroupRequestDto.builder()
                .name("Invalid Members Group")
                .memberUserIds(List.of(999999L))
                .build();

        assertThatThrownBy(() -> conversationService.createGroupConversation(nonExistentRequest, alice.getUsername()))
                .isInstanceOf(ResourceNotFoundException.class);

        // Empty member list
        CreateGroupRequestDto emptyMembersRequest = CreateGroupRequestDto.builder()
                .name("No Members Group")
                .memberUserIds(List.of())
                .build();

        assertThatThrownBy(() -> conversationService.createGroupConversation(emptyMembersRequest, alice.getUsername()))
                .isInstanceOf(IllegalArgumentException.class);

        // Adding self
        CreateGroupRequestDto selfRequest = CreateGroupRequestDto.builder()
                .name("Self Group")
                .memberUserIds(List.of(alice.getId()))
                .build();

        assertThatThrownBy(() -> conversationService.createGroupConversation(selfRequest, alice.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add yourself");
    }

    // 4. Từ chối mời user không phải bạn bè theo mô hình hiện tại
    @Test
    @DisplayName("Scenario 4: createGroup - rejects non-friend user")
    void createGroup_rejectsNonFriendUser() {
        // Dave is NOT a friend of Alice
        CreateGroupRequestDto request = CreateGroupRequestDto.builder()
                .name("Non-friend Group")
                .memberUserIds(List.of(dave.getId()))
                .build();

        assertThatThrownBy(() -> conversationService.createGroupConversation(request, alice.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not your friend");
    }

    // 5. ADMIN thêm thành viên thành công
    @Test
    @DisplayName("Scenario 5: addGroupMembers - ADMIN successfully adds new friend member")
    void addGroupMembers_success_byAdmin() {
        ConversationDto conv = conversationService.createGroupConversation(
                CreateGroupRequestDto.builder().name("Alpha Team").memberUserIds(List.of(bob.getId())).build(),
                alice.getUsername());

        // Make Alice and Dave friends
        createFriendship(alice, dave, FriendRequestStatus.ACCEPTED);

        List<GroupMemberResponseDto> members = conversationService.addGroupMembers(
                conv.getId(),
                AddGroupMembersRequestDto.builder().memberUserIds(List.of(dave.getId())).build(),
                alice.getUsername());

        assertThat(members).hasSize(3);
        assertThat(members).extracting(GroupMemberResponseDto::getUsername)
                .containsExactlyInAnyOrder(alice.getUsername(), bob.getUsername(), dave.getUsername());

        ConversationMember daveMember = conversationMemberRepository.findByConversationIdAndUserId(conv.getId(), dave.getId()).orElseThrow();
        assertThat(daveMember.getStatus()).isEqualTo(ConversationMemberStatus.ACTIVE);
        assertThat(daveMember.getRole()).isEqualTo(ConversationMemberRole.MEMBER);
    }

    // 6. MEMBER không thể thêm hoặc kick người khác
    @Test
    @DisplayName("Scenario 6: add/remove member - rejected when attempted by regular MEMBER")
    void addOrRemoveMember_rejectedForNonAdminMember() {
        ConversationDto conv = conversationService.createGroupConversation(
                CreateGroupRequestDto.builder().name("Alpha Team").memberUserIds(List.of(bob.getId(), charlie.getId())).build(),
                alice.getUsername());

        createFriendship(bob, dave, FriendRequestStatus.ACCEPTED);

        // Bob (MEMBER) attempts to add Dave -> 403 Forbidden
        assertThatThrownBy(() -> conversationService.addGroupMembers(
                conv.getId(),
                AddGroupMembersRequestDto.builder().memberUserIds(List.of(dave.getId())).build(),
                bob.getUsername()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only group ADMIN");

        // Bob (MEMBER) attempts to kick Charlie -> 403 Forbidden
        assertThatThrownBy(() -> conversationService.removeGroupMember(
                conv.getId(),
                charlie.getId(),
                bob.getUsername()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only group ADMIN");
    }

    // 7. Người ngoài nhóm không thể xem danh sách thành viên
    @Test
    @DisplayName("Scenario 7: getGroupMembers - rejected for outsider user")
    void getGroupMembers_rejectedForOutsider() {
        ConversationDto conv = conversationService.createGroupConversation(
                CreateGroupRequestDto.builder().name("Alpha Team").memberUserIds(List.of(bob.getId())).build(),
                alice.getUsername());

        // Dave is outside the group
        assertThatThrownBy(() -> conversationService.getGroupMembers(conv.getId(), dave.getUsername()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not an active member");
    }

    // 8. Kick cập nhật status thành REMOVED mà không xóa message
    @Test
    @DisplayName("Scenario 8: removeGroupMember - sets status REMOVED, preserves chat messages, prevents self-kick")
    void removeGroupMember_updatesStatusToRemoved_preservesMessages() {
        ConversationDto conv = conversationService.createGroupConversation(
                CreateGroupRequestDto.builder().name("Alpha Team").memberUserIds(List.of(bob.getId())).build(),
                alice.getUsername());

        // Bob sends a message
        Message msg = messageRepository.save(Message.builder()
                .conversation(conversationRepository.findById(conv.getId()).orElseThrow())
                .sender(bob)
                .content("Hello team!")
                .timestamp(Instant.now())
                .build());

        // Alice (Admin) removes Bob
        conversationService.removeGroupMember(conv.getId(), bob.getId(), alice.getUsername());

        ConversationMember bobMember = conversationMemberRepository.findByConversationIdAndUserId(conv.getId(), bob.getId()).orElseThrow();
        assertThat(bobMember.getStatus()).isEqualTo(ConversationMemberStatus.REMOVED);
        assertThat(bobMember.getLeftAt()).isNotNull();

        // Message is still intact in repository
        assertThat(messageRepository.findById(msg.getId())).isPresent();

        // Admin cannot kick themselves
        assertThatThrownBy(() -> conversationService.removeGroupMember(conv.getId(), alice.getId(), alice.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADMIN cannot remove themselves");
    }

    // 9. Thành viên tự rời nhóm
    @Test
    @DisplayName("Scenario 9: leaveGroup - regular member leaves group with status LEFT")
    void leaveGroup_memberLeavesSuccessfully() {
        ConversationDto conv = conversationService.createGroupConversation(
                CreateGroupRequestDto.builder().name("Alpha Team").memberUserIds(List.of(bob.getId())).build(),
                alice.getUsername());

        conversationService.leaveGroup(conv.getId(), null, bob.getUsername());

        ConversationMember bobMember = conversationMemberRepository.findByConversationIdAndUserId(conv.getId(), bob.getId()).orElseThrow();
        assertThat(bobMember.getStatus()).isEqualTo(ConversationMemberStatus.LEFT);
        assertThat(bobMember.getLeftAt()).isNotNull();
    }

    // 10. ADMIN rời nhóm với việc chuyển quyền hợp lệ (chỉ định hoặc tự động)
    @Test
    @DisplayName("Scenario 10: leaveGroup - ADMIN leaves with explicit transfer or auto-promotion")
    void leaveGroup_adminLeavesWithValidTransferOrAutoPromotion() {
        // Case A: Explicit newAdminUserId
        ConversationDto convA = conversationService.createGroupConversation(
                CreateGroupRequestDto.builder().name("Team A").memberUserIds(List.of(bob.getId(), charlie.getId())).build(),
                alice.getUsername());

        conversationService.leaveGroup(convA.getId(), LeaveGroupRequestDto.builder().newAdminUserId(charlie.getId()).build(), alice.getUsername());

        ConversationMember charlieMember = conversationMemberRepository.findByConversationIdAndUserId(convA.getId(), charlie.getId()).orElseThrow();
        assertThat(charlieMember.getRole()).isEqualTo(ConversationMemberRole.ADMIN);
        assertThat(charlieMember.getStatus()).isEqualTo(ConversationMemberStatus.ACTIVE);

        ConversationMember aliceMemberA = conversationMemberRepository.findByConversationIdAndUserId(convA.getId(), alice.getId()).orElseThrow();
        assertThat(aliceMemberA.getStatus()).isEqualTo(ConversationMemberStatus.LEFT);
        assertThat(aliceMemberA.getRole()).isEqualTo(ConversationMemberRole.MEMBER);

        // Case B: Auto-promotion when newAdminUserId is null
        ConversationDto convB = conversationService.createGroupConversation(
                CreateGroupRequestDto.builder().name("Team B").memberUserIds(List.of(bob.getId())).build(),
                alice.getUsername());

        conversationService.leaveGroup(convB.getId(), null, alice.getUsername());

        ConversationMember bobMember = conversationMemberRepository.findByConversationIdAndUserId(convB.getId(), bob.getId()).orElseThrow();
        assertThat(bobMember.getRole()).isEqualTo(ConversationMemberRole.ADMIN);
        assertThat(bobMember.getStatus()).isEqualTo(ConversationMemberStatus.ACTIVE);

        ConversationMember aliceMemberB = conversationMemberRepository.findByConversationIdAndUserId(convB.getId(), alice.getId()).orElseThrow();
        assertThat(aliceMemberB.getStatus()).isEqualTo(ConversationMemberStatus.LEFT);

        // Case C: Sole member leaves -> status LEFT, no errors
        conversationService.leaveGroup(convB.getId(), null, bob.getUsername());
        ConversationMember bobSoleMember = conversationMemberRepository.findByConversationIdAndUserId(convB.getId(), bob.getId()).orElseThrow();
        assertThat(bobSoleMember.getStatus()).isEqualTo(ConversationMemberStatus.LEFT);
    }

    // 11. Từ chối chuyển quyền cho user ngoài nhóm hoặc không ACTIVE
    @Test
    @DisplayName("Scenario 11: transferAdmin - rejects transfer to outsider, left member, or non-admin caller")
    void transferAdmin_rejectsOutsiderOrNonActiveMember() {
        ConversationDto conv = conversationService.createGroupConversation(
                CreateGroupRequestDto.builder().name("Alpha Team").memberUserIds(List.of(bob.getId())).build(),
                alice.getUsername());

        // Transfer to outsider Dave -> IllegalArgumentException
        assertThatThrownBy(() -> conversationService.transferAdmin(
                conv.getId(),
                TransferAdminRequestDto.builder().newAdminUserId(dave.getId()).build(),
                alice.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an active member");

        // Bob leaves -> transfer to LEFT Bob -> IllegalArgumentException
        conversationService.leaveGroup(conv.getId(), null, bob.getUsername());
        assertThatThrownBy(() -> conversationService.transferAdmin(
                conv.getId(),
                TransferAdminRequestDto.builder().newAdminUserId(bob.getId()).build(),
                alice.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an active member");

        // Non-admin tries to transfer -> AccessDeniedException
        assertThatThrownBy(() -> conversationService.transferAdmin(
                conv.getId(),
                TransferAdminRequestDto.builder().newAdminUserId(alice.getId()).build(),
                bob.getUsername()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // 12. Đổi tên nhóm chỉ được phép với người có quyền
    @Test
    @DisplayName("Scenario 12: updateGroup - only ADMIN can rename or update avatar, validates constraints")
    void updateGroup_onlyAdminAllowed_validatesFields() {
        ConversationDto conv = conversationService.createGroupConversation(
                CreateGroupRequestDto.builder().name("Alpha Team").memberUserIds(List.of(bob.getId())).build(),
                alice.getUsername());

        // Member Bob cannot rename -> 403 Forbidden
        assertThatThrownBy(() -> conversationService.updateGroup(
                conv.getId(),
                UpdateGroupRequestDto.builder().name("Bob's Renamed Team").build(),
                bob.getUsername()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only group ADMIN");

        // Admin Alice renames successfully
        ConversationDto updated = conversationService.updateGroup(
                conv.getId(),
                UpdateGroupRequestDto.builder().name("Alpha Team 2026").avatar("https://example.com/avatar.png").build(),
                alice.getUsername());

        assertThat(updated.getName()).isEqualTo("Alpha Team 2026");
        assertThat(updated.getAvatar()).isEqualTo("https://example.com/avatar.png");

        // Blank name rejected
        assertThatThrownBy(() -> conversationService.updateGroup(
                conv.getId(),
                UpdateGroupRequestDto.builder().name("   ").build(),
                alice.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100 characters");
    }

    // 13. Membership trùng không tạo bản ghi trùng, tái kích hoạt thành viên cũ
    @Test
    @DisplayName("Scenario 13: addGroupMembers - reactivates LEFT/REMOVED member without duplicate row, advances read cursor")
    void addGroupMembers_rejoinDoesNotCreateDuplicateMembership_resetsReadCursorCleanly() {
        ConversationDto conv = conversationService.createGroupConversation(
                CreateGroupRequestDto.builder().name("Alpha Team").memberUserIds(List.of(bob.getId())).build(),
                alice.getUsername());

        // Bob leaves
        conversationService.leaveGroup(conv.getId(), null, bob.getUsername());

        // Post a message while Bob is absent
        Message msg = messageRepository.save(Message.builder()
                .conversation(conversationRepository.findById(conv.getId()).orElseThrow())
                .sender(alice)
                .content("Important notice while Bob was away")
                .timestamp(Instant.now())
                .build());

        // Alice re-adds Bob
        conversationService.addGroupMembers(
                conv.getId(),
                AddGroupMembersRequestDto.builder().memberUserIds(List.of(bob.getId())).build(),
                alice.getUsername());

        // Verify only 1 membership record exists for Bob in this conversation
        List<ConversationMember> allBobMemberships = conversationMemberRepository.findByConversationId(conv.getId())
                .stream().filter(m -> m.getUser().getId().equals(bob.getId())).toList();
        assertThat(allBobMemberships).hasSize(1);

        ConversationMember bobMember = allBobMemberships.get(0);
        assertThat(bobMember.getStatus()).isEqualTo(ConversationMemberStatus.ACTIVE);
        assertThat(bobMember.getLeftAt()).isNull();
        // Read cursor is advanced to msg.getId() so unread count starts at 0
        assertThat(bobMember.getLastReadMessageId()).isEqualTo(msg.getId());

        long bobUnread = messageRepository.countUnreadMessages(conv.getId(), bob.getId());
        assertThat(bobUnread).isEqualTo(0L);
    }

    // 14. Transaction rollback khi một bước nghiệp vụ thất bại
    @Test
    @DisplayName("Scenario 14: transactionRollback - aborts entire operation on business failure")
    void transactionRollback_whenStepFails() {
        long initialConvCount = conversationRepository.count();

        // One valid friend (Bob), one non-friend (Dave) -> should fail and roll back
        CreateGroupRequestDto request = CreateGroupRequestDto.builder()
                .name("Rollback Team")
                .memberUserIds(List.of(bob.getId(), dave.getId()))
                .build();

        assertThatThrownBy(() -> conversationService.createGroupConversation(request, alice.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not your friend");

        entityManager.flush();
        long postFailConvCount = conversationRepository.count();
        assertThat(postFailConvCount).isEqualTo(initialConvCount);
    }

    // 15. Hồi quy các test PUBLIC/DIRECT hiện có
    @Test
    @DisplayName("Scenario 15: regression - PUBLIC and DIRECT chats remain fully functional")
    void regression_publicAndDirectConversationsRemainFunctional() {
        // Direct chat get or create
        ConversationDto directDto = conversationService.getOrCreateDirectConversation(bob.getId(), alice.getUsername());
        assertThat(directDto).isNotNull();
        assertThat(directDto.getType()).isEqualTo(ConversationType.DIRECT);

        // Send a direct message
        Message directMsg = messageRepository.save(Message.builder()
                .conversation(conversationRepository.findById(directDto.getId()).orElseThrow())
                .sender(bob)
                .content("Hello Alice direct!")
                .timestamp(Instant.now())
                .build());

        // Mark as read in Direct chat
        var receipt = conversationService.markConversationAsRead(directDto.getId(), directMsg.getId(), alice.getUsername());
        assertThat(receipt.getUnreadCount()).isEqualTo(0L);

        // Get conversations list should contain DIRECT and PUBLIC
        Page<ConversationDto> paged = conversationService.getConversations(alice.getUsername(), PageRequest.of(0, 10));
        assertThat(paged.getContent()).isNotEmpty();
        assertThat(paged.getContent()).extracting(ConversationDto::getType)
                .contains(ConversationType.DIRECT, ConversationType.PUBLIC);
    }
}
