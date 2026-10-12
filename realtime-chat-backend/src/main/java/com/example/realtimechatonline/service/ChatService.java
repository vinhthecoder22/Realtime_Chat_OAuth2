package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.response.MessageResponseDto;
import com.example.realtimechatonline.domain.dto.websocket.ChatMessageDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatService {

    ChatMessageDto processAndSave(String content, String senderUsername);

    ChatMessageDto processAndSave(Long conversationId, String content, String senderUsername);

    ChatMessageDto buildJoinMessage(String username);

    ChatMessageDto buildLeaveMessage(String username);

    List<MessageResponseDto> getHistory();

    Page<MessageResponseDto> getHistory(Pageable pageable);

    Page<MessageResponseDto> getHistoryByConversation(Long conversationId, Pageable pageable, String currentUsername);

}
