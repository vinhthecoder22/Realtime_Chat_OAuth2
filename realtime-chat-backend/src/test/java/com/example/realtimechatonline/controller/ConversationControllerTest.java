package com.example.realtimechatonline.controller;

import com.example.realtimechatonline.common.ApiResponse;
import com.example.realtimechatonline.domain.dto.response.ConversationDto;
import com.example.realtimechatonline.domain.entity.ConversationType;
import com.example.realtimechatonline.service.ConversationService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationController unit tests")
class ConversationControllerTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ConversationController conversationController;

    @BeforeEach
    void setUp() {
        lenient().when(authentication.getName()).thenReturn("alice");
    }

    @Test
    @DisplayName("getConversations — delegates with pagination and authenticated user")
    void getConversations_success() {
        ConversationDto dto = ConversationDto.builder().id(1L).name("Global Chat").type(ConversationType.PUBLIC).build();
        Page<ConversationDto> pagedResult = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);

        when(conversationService.getConversations(eq("alice"), any())).thenReturn(pagedResult);

        ResponseEntity<ApiResponse<Page<ConversationDto>>> response = conversationController.getConversations(0, 20, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getContent()).hasSize(1);
        assertThat(response.getBody().getData().getContent().get(0).getName()).isEqualTo("Global Chat");
    }

    @Test
    @DisplayName("getConversation — returns conversation details for authorized member")
    void getConversation_success() {
        ConversationDto dto = ConversationDto.builder().id(2L).name("Bob").type(ConversationType.DIRECT).build();
        when(conversationService.getConversationById(2L, "alice")).thenReturn(dto);

        ResponseEntity<ApiResponse<ConversationDto>> response = conversationController.getConversation(2L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getName()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("getOrCreateDirectConversation — delegates with targetUserId")
    void getOrCreateDirectConversation_success() {
        ConversationDto dto = ConversationDto.builder().id(2L).name("Bob").type(ConversationType.DIRECT).build();
        when(conversationService.getOrCreateDirectConversation(2L, "alice")).thenReturn(dto);

        ResponseEntity<ApiResponse<ConversationDto>> response =
                conversationController.getOrCreateDirectConversation(Map.of("targetUserId", 2L), authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("markConversationAsRead — delegates to service and returns 200 OK")
    void markConversationAsRead_success() {
        com.example.realtimechatonline.domain.dto.response.ReadReceiptResponseDto receipt =
                com.example.realtimechatonline.domain.dto.response.ReadReceiptResponseDto.builder()
                        .conversationId(2L)
                        .userId(1L)
                        .username("alice")
                        .lastReadMessageId(55L)
                        .unreadCount(0L)
                        .build();

        when(conversationService.markConversationAsRead(2L, 55L, "alice")).thenReturn(receipt);

        com.example.realtimechatonline.domain.dto.request.MarkReadRequestDto requestDto =
                com.example.realtimechatonline.domain.dto.request.MarkReadRequestDto.builder()
                        .messageId(55L)
                        .build();

        ResponseEntity<ApiResponse<com.example.realtimechatonline.domain.dto.response.ReadReceiptResponseDto>> response =
                conversationController.markConversationAsRead(2L, requestDto, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getLastReadMessageId()).isEqualTo(55L);
        assertThat(response.getBody().getData().getUnreadCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("createGroupConversation — delegates to service and returns 201 CREATED")
    void createGroupConversation_success() {
        com.example.realtimechatonline.domain.dto.request.CreateGroupRequestDto request =
                com.example.realtimechatonline.domain.dto.request.CreateGroupRequestDto.builder()
                        .name("Alpha Project")
                        .memberUserIds(List.of(2L, 3L))
                        .build();

        ConversationDto dto = ConversationDto.builder()
                .id(10L)
                .name("Alpha Project")
                .type(ConversationType.GROUP)
                .memberCount(3L)
                .currentUserRole(com.example.realtimechatonline.domain.entity.ConversationMemberRole.ADMIN)
                .build();

        when(conversationService.createGroupConversation(request, "alice")).thenReturn(dto);

        ResponseEntity<ApiResponse<ConversationDto>> response =
                conversationController.createGroupConversation(request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getName()).isEqualTo("Alpha Project");
        assertThat(response.getBody().getData().getMemberCount()).isEqualTo(3L);
        assertThat(response.getBody().getData().getCurrentUserRole()).isEqualTo(com.example.realtimechatonline.domain.entity.ConversationMemberRole.ADMIN);
    }

    @Test
    @DisplayName("getGroupMembers — delegates to service and returns 200 OK")
    void getGroupMembers_success() {
        com.example.realtimechatonline.domain.dto.response.GroupMemberResponseDto m1 =
                com.example.realtimechatonline.domain.dto.response.GroupMemberResponseDto.builder()
                        .userId(1L)
                        .username("alice")
                        .role(com.example.realtimechatonline.domain.entity.ConversationMemberRole.ADMIN)
                        .isOnline(true)
                        .build();

        when(conversationService.getGroupMembers(10L, "alice")).thenReturn(List.of(m1));

        ResponseEntity<ApiResponse<List<com.example.realtimechatonline.domain.dto.response.GroupMemberResponseDto>>> response =
                conversationController.getGroupMembers(10L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("addGroupMembers — delegates to service and returns 200 OK")
    void addGroupMembers_success() {
        com.example.realtimechatonline.domain.dto.request.AddGroupMembersRequestDto request =
                com.example.realtimechatonline.domain.dto.request.AddGroupMembersRequestDto.builder()
                        .memberUserIds(List.of(4L))
                        .build();
        com.example.realtimechatonline.domain.dto.response.GroupMemberResponseDto m1 =
                com.example.realtimechatonline.domain.dto.response.GroupMemberResponseDto.builder().userId(4L).username("dave").build();

        when(conversationService.addGroupMembers(10L, request, "alice")).thenReturn(List.of(m1));

        ResponseEntity<ApiResponse<List<com.example.realtimechatonline.domain.dto.response.GroupMemberResponseDto>>> response =
                conversationController.addGroupMembers(10L, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("removeGroupMember — delegates to service and returns 200 OK")
    void removeGroupMember_success() {
        doNothing().when(conversationService).removeGroupMember(10L, 2L, "alice");

        ResponseEntity<ApiResponse<Void>> response =
                conversationController.removeGroupMember(10L, 2L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Member removed from group");
        verify(conversationService).removeGroupMember(10L, 2L, "alice");
    }

    @Test
    @DisplayName("leaveGroup — delegates to service and returns 200 OK")
    void leaveGroup_success() {
        com.example.realtimechatonline.domain.dto.request.LeaveGroupRequestDto request =
                com.example.realtimechatonline.domain.dto.request.LeaveGroupRequestDto.builder().newAdminUserId(2L).build();
        doNothing().when(conversationService).leaveGroup(10L, request, "alice");

        ResponseEntity<ApiResponse<Void>> response =
                conversationController.leaveGroup(10L, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Left group conversation");
        verify(conversationService).leaveGroup(10L, request, "alice");
    }

    @Test
    @DisplayName("transferAdmin — delegates to service and returns 200 OK")
    void transferAdmin_success() {
        com.example.realtimechatonline.domain.dto.request.TransferAdminRequestDto request =
                com.example.realtimechatonline.domain.dto.request.TransferAdminRequestDto.builder().newAdminUserId(2L).build();
        doNothing().when(conversationService).transferAdmin(10L, request, "alice");

        ResponseEntity<ApiResponse<Void>> response =
                conversationController.transferAdmin(10L, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Group admin transferred");
        verify(conversationService).transferAdmin(10L, request, "alice");
    }

    @Test
    @DisplayName("updateGroup — delegates to service and returns 200 OK")
    void updateGroup_success() {
        com.example.realtimechatonline.domain.dto.request.UpdateGroupRequestDto request =
                com.example.realtimechatonline.domain.dto.request.UpdateGroupRequestDto.builder().name("Renamed Group").build();
        ConversationDto dto = ConversationDto.builder().id(10L).name("Renamed Group").type(ConversationType.GROUP).build();

        when(conversationService.updateGroup(10L, request, "alice")).thenReturn(dto);

        ResponseEntity<ApiResponse<ConversationDto>> response =
                conversationController.updateGroup(10L, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getName()).isEqualTo("Renamed Group");
    }
}

