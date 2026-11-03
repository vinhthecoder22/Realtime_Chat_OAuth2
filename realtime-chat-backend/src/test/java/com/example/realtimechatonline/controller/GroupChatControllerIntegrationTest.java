package com.example.realtimechatonline.controller;

import com.example.realtimechatonline.domain.dto.request.AddGroupMembersRequestDto;
import com.example.realtimechatonline.domain.dto.request.CreateGroupRequestDto;
import com.example.realtimechatonline.domain.dto.request.LeaveGroupRequestDto;
import com.example.realtimechatonline.domain.dto.request.TransferAdminRequestDto;
import com.example.realtimechatonline.domain.dto.request.UpdateGroupRequestDto;
import com.example.realtimechatonline.domain.entity.*;
import com.example.realtimechatonline.repository.ConversationMemberRepository;
import com.example.realtimechatonline.repository.ConversationRepository;
import com.example.realtimechatonline.repository.FriendRequestRepository;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
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
@DisplayName("Group Chat REST MVC End-to-End Integration Tests (STEP 10.6)")
class GroupChatControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User alice;
    private User bob;
    private User charlie;
    private User dave;

    private String aliceToken;
    private String bobToken;
    private String charlieToken;
    private String daveToken;

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();

        alice = userRepository.save(User.builder()
                .username("mvc_alice_" + nano)
                .email("mvc_alice_" + nano + "@test.com")
                .fullname("Alice MVC")
                .password("hashed_pwd")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        bob = userRepository.save(User.builder()
                .username("mvc_bob_" + nano)
                .email("mvc_bob_" + nano + "@test.com")
                .fullname("Bob MVC")
                .password("hashed_pwd")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        charlie = userRepository.save(User.builder()
                .username("mvc_charlie_" + nano)
                .email("mvc_charlie_" + nano + "@test.com")
                .fullname("Charlie MVC")
                .password("hashed_pwd")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        dave = userRepository.save(User.builder()
                .username("mvc_dave_" + nano)
                .email("mvc_dave_" + nano + "@test.com")
                .fullname("Dave MVC")
                .password("hashed_pwd")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());

        // Alice is friends with Bob and Charlie
        createFriendship(alice, bob);
        createFriendship(alice, charlie);
        createFriendship(bob, charlie);
        // Dave is NOT a friend

        aliceToken = jwtTokenProvider.generateToken(alice);
        bobToken = jwtTokenProvider.generateToken(bob);
        charlieToken = jwtTokenProvider.generateToken(charlie);
        daveToken = jwtTokenProvider.generateToken(dave);
    }

    private void createFriendship(User a, User b) {
        User first = a.getId() < b.getId() ? a : b;
        User second = a.getId() > b.getId() ? a : b;
        friendRequestRepository.save(FriendRequest.builder()
                .user1(first)
                .user2(second)
                .sender(first)
                .status(FriendRequestStatus.ACCEPTED)
                .build());
    }

    private Long createGroupViaDb(String name, User creator, List<User> members) {
        Instant now = java.time.Instant.now();
        Conversation conv = conversationRepository.save(Conversation.builder()
                .name(name)
                .type(ConversationType.GROUP)
                .creator(creator)
                .createdAt(now)
                .build());

        conversationMemberRepository.save(ConversationMember.builder()
                .conversation(conv)
                .user(creator)
                .role(ConversationMemberRole.ADMIN)
                .status(ConversationMemberStatus.ACTIVE)
                .joinedAt(now)
                .build());

        for (User m : members) {
            conversationMemberRepository.save(ConversationMember.builder()
                    .conversation(conv)
                    .user(m)
                    .role(ConversationMemberRole.MEMBER)
                    .status(ConversationMemberStatus.ACTIVE)
                    .joinedAt(now)
                    .build());
        }
        return conv.getId();
    }

    // 1. Create Group - Success 201
    @Test
    @DisplayName("POST /api/v1/conversations/group — returns 201 CREATED with correct DTO")
    void createGroup_success_returns201() throws Exception {
        CreateGroupRequestDto request = CreateGroupRequestDto.builder()
                .name("Core Engineering")
                .memberUserIds(List.of(bob.getId(), charlie.getId()))
                .build();

        mockMvc.perform(post("/api/v1/conversations/group")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.data.name").value("Core Engineering"))
                .andExpect(jsonPath("$.data.type").value("GROUP"))
                .andExpect(jsonPath("$.data.memberCount").value(3))
                .andExpect(jsonPath("$.data.currentUserRole").value("ADMIN"));
    }

    // 2. Create Group - Bean Validation Failures return 400
    @Test
    @DisplayName("POST /api/v1/conversations/group — validation failures return 400 Bad Request")
    void createGroup_validationFailures_return400() throws Exception {
        // Blank name
        CreateGroupRequestDto blankName = CreateGroupRequestDto.builder()
                .name("   ")
                .memberUserIds(List.of(bob.getId()))
                .build();

        mockMvc.perform(post("/api/v1/conversations/group")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));

        // Name > 100 chars
        CreateGroupRequestDto tooLong = CreateGroupRequestDto.builder()
                .name("A".repeat(101))
                .memberUserIds(List.of(bob.getId()))
                .build();

        mockMvc.perform(post("/api/v1/conversations/group")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooLong)))
                .andExpect(status().isBadRequest());

        // Empty members
        CreateGroupRequestDto emptyMembers = CreateGroupRequestDto.builder()
                .name("Valid Name")
                .memberUserIds(List.of())
                .build();

        mockMvc.perform(post("/api/v1/conversations/group")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyMembers)))
                .andExpect(status().isBadRequest());
    }

    // 3. Create Group - Unauthenticated returns 401
    @Test
    @DisplayName("POST /api/v1/conversations/group — unauthenticated request returns 401")
    void createGroup_unauthenticated_returns401() throws Exception {
        CreateGroupRequestDto request = CreateGroupRequestDto.builder()
                .name("No Auth Group")
                .memberUserIds(List.of(bob.getId()))
                .build();

        mockMvc.perform(post("/api/v1/conversations/group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // 4. Create Group - Non-friend member returns 400
    @Test
    @DisplayName("POST /api/v1/conversations/group — including non-friend returns 400 Bad Request")
    void createGroup_withNonFriend_returns400() throws Exception {
        CreateGroupRequestDto request = CreateGroupRequestDto.builder()
                .name("Non-Friend Group")
                .memberUserIds(List.of(dave.getId())) // Dave is not Alice's friend
                .build();

        mockMvc.perform(post("/api/v1/conversations/group")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not your friend")));
    }

    // 5. Get Group Members - Active member returns 200
    @Test
    @DisplayName("GET /api/v1/conversations/{id}/members — active member gets 200 OK")
    void getGroupMembers_activeMember_returns200() throws Exception {
        Long convId = createGroupViaDb("Members Test", alice, List.of(bob));

        mockMvc.perform(get("/api/v1/conversations/" + convId + "/members")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].username", containsInAnyOrder(alice.getUsername(), bob.getUsername())));
    }

    // 6. Get Group Members - Outsider returns 403 Forbidden
    @Test
    @DisplayName("GET /api/v1/conversations/{id}/members — non-member returns 403 Forbidden")
    void getGroupMembers_outsider_returns403() throws Exception {
        Long convId = createGroupViaDb("Secret Group", alice, List.of(bob));

        mockMvc.perform(get("/api/v1/conversations/" + convId + "/members")
                        .header("Authorization", "Bearer " + daveToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // 7. Add Group Members - Admin adds friend returns 200
    @Test
    @DisplayName("POST /api/v1/conversations/{id}/members — admin adds friend returns 200 OK")
    void addGroupMembers_byAdmin_returns200() throws Exception {
        Long convId = createGroupViaDb("Add Members Group", alice, List.of(bob));

        AddGroupMembersRequestDto request = AddGroupMembersRequestDto.builder()
                .memberUserIds(List.of(charlie.getId()))
                .build();

        mockMvc.perform(post("/api/v1/conversations/" + convId + "/members")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].username", hasItem(charlie.getUsername())));
    }

    // 8. Add Group Members - Non-admin returns 403
    @Test
    @DisplayName("POST /api/v1/conversations/{id}/members — non-admin member returns 403 Forbidden")
    void addGroupMembers_byNonAdmin_returns403() throws Exception {
        Long convId = createGroupViaDb("Add Members Group 2", alice, List.of(bob));

        AddGroupMembersRequestDto request = AddGroupMembersRequestDto.builder()
                .memberUserIds(List.of(charlie.getId()))
                .build();

        mockMvc.perform(post("/api/v1/conversations/" + convId + "/members")
                        .header("Authorization", "Bearer " + bobToken) // Bob is MEMBER, not ADMIN
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // 9. Remove Member - Admin removes member returns 200
    @Test
    @DisplayName("DELETE /api/v1/conversations/{id}/members/{userId} — admin kicks member returns 200 OK")
    void removeGroupMember_byAdmin_returns200() throws Exception {
        Long convId = createGroupViaDb("Kick Group", alice, List.of(bob));

        mockMvc.perform(delete("/api/v1/conversations/" + convId + "/members/" + bob.getId())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Member removed from group"));

        // Verify Bob's status is REMOVED
        ConversationMember bobMember = conversationMemberRepository
                .findByConversationIdAndUserId(convId, bob.getId()).orElseThrow();
        assertThat(bobMember.getStatus()).isEqualTo(ConversationMemberStatus.REMOVED);
    }

    // 10. Remove Member - Non-admin returns 403
    @Test
    @DisplayName("DELETE /api/v1/conversations/{id}/members/{userId} — regular member cannot kick returns 403")
    void removeGroupMember_byNonAdmin_returns403() throws Exception {
        Long convId = createGroupViaDb("Kick Group 2", alice, List.of(bob, charlie));

        mockMvc.perform(delete("/api/v1/conversations/" + convId + "/members/" + charlie.getId())
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
    }

    // 11. Remove Member - Admin kicking self returns 400
    @Test
    @DisplayName("DELETE /api/v1/conversations/{id}/members/{userId} — admin kicking self returns 400")
    void removeGroupMember_adminRemovesSelf_returns400() throws Exception {
        Long convId = createGroupViaDb("Kick Group 3", alice, List.of(bob));

        mockMvc.perform(delete("/api/v1/conversations/" + convId + "/members/" + alice.getId())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot remove themselves")));
    }

    // 12. Transfer Admin - Admin transfers returns 200
    @Test
    @DisplayName("PATCH /api/v1/conversations/{id}/admin — admin transfers returns 200 OK")
    void transferAdmin_byAdmin_returns200() throws Exception {
        Long convId = createGroupViaDb("Transfer Group", alice, List.of(bob));

        TransferAdminRequestDto request = TransferAdminRequestDto.builder()
                .newAdminUserId(bob.getId())
                .build();

        mockMvc.perform(patch("/api/v1/conversations/" + convId + "/admin")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Group admin transferred"));

        // Verify roles updated
        ConversationMember aliceMember = conversationMemberRepository.findByConversationIdAndUserId(convId, alice.getId()).orElseThrow();
        ConversationMember bobMember = conversationMemberRepository.findByConversationIdAndUserId(convId, bob.getId()).orElseThrow();
        assertThat(aliceMember.getRole()).isEqualTo(ConversationMemberRole.MEMBER);
        assertThat(bobMember.getRole()).isEqualTo(ConversationMemberRole.ADMIN);
    }

    // 13. Transfer Admin - Non-admin returns 403
    @Test
    @DisplayName("PATCH /api/v1/conversations/{id}/admin — non-admin returns 403 Forbidden")
    void transferAdmin_byNonAdmin_returns403() throws Exception {
        Long convId = createGroupViaDb("Transfer Group 2", alice, List.of(bob, charlie));

        TransferAdminRequestDto request = TransferAdminRequestDto.builder()
                .newAdminUserId(charlie.getId())
                .build();

        mockMvc.perform(patch("/api/v1/conversations/" + convId + "/admin")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // 14. Transfer Admin - Missing newAdminUserId validation returns 400
    @Test
    @DisplayName("PATCH /api/v1/conversations/{id}/admin — missing newAdminUserId returns 400 Bad Request")
    void transferAdmin_nullNewAdmin_returns400() throws Exception {
        Long convId = createGroupViaDb("Transfer Group 3", alice, List.of(bob));

        mockMvc.perform(patch("/api/v1/conversations/" + convId + "/admin")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // 15. Leave Group - Member leaves returns 200
    @Test
    @DisplayName("POST /api/v1/conversations/{id}/leave — member leaves returns 200 OK")
    void leaveGroup_memberLeaves_returns200() throws Exception {
        Long convId = createGroupViaDb("Leave Group", alice, List.of(bob));

        mockMvc.perform(post("/api/v1/conversations/" + convId + "/leave")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Left group conversation"));

        ConversationMember bobMember = conversationMemberRepository.findByConversationIdAndUserId(convId, bob.getId()).orElseThrow();
        assertThat(bobMember.getStatus()).isEqualTo(ConversationMemberStatus.LEFT);
        assertThat(bobMember.getLeftAt()).isNotNull();
    }

    // 16. Leave Group - Admin leaves with designated successor returns 200
    @Test
    @DisplayName("POST /api/v1/conversations/{id}/leave — admin leaves with designated successor returns 200 OK")
    void leaveGroup_adminLeavesWithSuccessor_returns200() throws Exception {
        Long convId = createGroupViaDb("Leave Group 2", alice, List.of(bob, charlie));

        LeaveGroupRequestDto request = LeaveGroupRequestDto.builder()
                .newAdminUserId(bob.getId())
                .build();

        mockMvc.perform(post("/api/v1/conversations/" + convId + "/leave")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        ConversationMember aliceMember = conversationMemberRepository.findByConversationIdAndUserId(convId, alice.getId()).orElseThrow();
        ConversationMember bobMember = conversationMemberRepository.findByConversationIdAndUserId(convId, bob.getId()).orElseThrow();

        assertThat(aliceMember.getStatus()).isEqualTo(ConversationMemberStatus.LEFT);
        assertThat(bobMember.getRole()).isEqualTo(ConversationMemberRole.ADMIN);
    }

    // 17. Update Group - Admin renames and updates avatar returns 200
    @Test
    @DisplayName("PATCH /api/v1/conversations/{id} — admin updates group info returns 200 OK")
    void updateGroup_byAdmin_returns200() throws Exception {
        Long convId = createGroupViaDb("Old Name", alice, List.of(bob));

        UpdateGroupRequestDto request = UpdateGroupRequestDto.builder()
                .name("New Fantastic Name")
                .avatar("https://cdn.example.com/group-avatar.png")
                .build();

        mockMvc.perform(patch("/api/v1/conversations/" + convId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Fantastic Name"))
                .andExpect(jsonPath("$.data.avatar").value("https://cdn.example.com/group-avatar.png"));
    }

    // 18. Update Group - Non-admin returns 403
    @Test
    @DisplayName("PATCH /api/v1/conversations/{id} — non-admin member returns 403 Forbidden")
    void updateGroup_byNonAdmin_returns403() throws Exception {
        Long convId = createGroupViaDb("Old Name 2", alice, List.of(bob));

        UpdateGroupRequestDto request = UpdateGroupRequestDto.builder()
                .name("Hacker Renamed")
                .build();

        mockMvc.perform(patch("/api/v1/conversations/" + convId)
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // 19. Update Group - Name too long returns 400
    @Test
    @DisplayName("PATCH /api/v1/conversations/{id} — name > 100 characters returns 400 Bad Request")
    void updateGroup_nameTooLong_returns400() throws Exception {
        Long convId = createGroupViaDb("Old Name 3", alice, List.of(bob));

        UpdateGroupRequestDto request = UpdateGroupRequestDto.builder()
                .name("A".repeat(101))
                .build();

        mockMvc.perform(patch("/api/v1/conversations/" + convId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // 20. Non-existent Conversation returns 404
    @Test
    @DisplayName("GET /api/v1/conversations/999999999/members — non-existent conversation returns 404 Not Found")
    void groupEndpoint_onNonExistentConversation_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/999999999/members")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
