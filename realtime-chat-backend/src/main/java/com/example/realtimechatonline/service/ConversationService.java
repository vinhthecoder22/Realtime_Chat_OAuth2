package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.response.ConversationDto;
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

}
