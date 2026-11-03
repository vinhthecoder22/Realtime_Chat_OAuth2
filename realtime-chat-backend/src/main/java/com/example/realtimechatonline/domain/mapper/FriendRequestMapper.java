package com.example.realtimechatonline.domain.mapper;

import com.example.realtimechatonline.domain.dto.response.FriendRequestDto;
import com.example.realtimechatonline.domain.entity.FriendRequest;
import com.example.realtimechatonline.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface FriendRequestMapper {

    @Mapping(target = "receiver", source = "friendRequest", qualifiedByName = "mapReceiver")
    FriendRequestDto toFriendRequestDto(FriendRequest friendRequest);

    @Named("mapReceiver")
    default User mapReceiver(FriendRequest friendRequest) {
        if (friendRequest == null || friendRequest.getSender() == null) {
            return null;
        }
        if (friendRequest.getUser1() != null && !friendRequest.getUser1().getId().equals(friendRequest.getSender().getId())) {
            return friendRequest.getUser1();
        }
        return friendRequest.getUser2();
    }
}
