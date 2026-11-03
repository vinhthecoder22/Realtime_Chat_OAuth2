package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.request.*;
import com.example.realtimechatonline.domain.dto.response.ConversationDto;
import com.example.realtimechatonline.domain.dto.response.GroupMemberResponseDto;
import com.example.realtimechatonline.domain.dto.response.ReadReceiptResponseDto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ConversationService {

    ConversationDto getOrCreateDirectConversation(Long targetUserId, String currentUsername);

    List<ConversationDto> getConversations(String currentUsername);

    Page<ConversationDto> getConversations(String currentUsername, Pageable pageable);

    ConversationDto getConversationById(Long conversationId, String currentUsername);

    ReadReceiptResponseDto markConversationAsRead(Long conversationId, Long messageId, String currentUsername);

    ConversationDto createGroupConversation(CreateGroupRequestDto request, String currentUsername);

    List<GroupMemberResponseDto> getGroupMembers(Long conversationId, String currentUsername);

    List<GroupMemberResponseDto> addGroupMembers(Long conversationId, AddGroupMembersRequestDto request, String currentUsername);

    void removeGroupMember(Long conversationId, Long targetUserId, String currentUsername);

    void leaveGroup(Long conversationId, LeaveGroupRequestDto request, String currentUsername);

    void transferAdmin(Long conversationId, TransferAdminRequestDto request, String currentUsername);

    ConversationDto updateGroup(Long conversationId, UpdateGroupRequestDto request, String currentUsername);

}
