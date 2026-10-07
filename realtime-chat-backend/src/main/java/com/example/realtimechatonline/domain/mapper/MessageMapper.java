package com.example.realtimechatonline.domain.mapper;

import com.example.realtimechatonline.domain.dto.response.MessageResponseDto;
import com.example.realtimechatonline.domain.entity.Message;
import com.example.realtimechatonline.domain.entity.MessageType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MessageMapper {

    @Mapping(source = "sender.username", target = "senderUsername")
    @Mapping(target = "type", expression = "java(com.example.realtimechatonline.domain.entity.MessageType.CHAT)")
    MessageResponseDto toMessageResponse(Message message);

    List<MessageResponseDto> toMessageResponseList(List<Message> messages);
}
