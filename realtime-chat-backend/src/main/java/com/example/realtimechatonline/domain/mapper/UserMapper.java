package com.example.realtimechatonline.domain.mapper;

import com.example.realtimechatonline.domain.dto.response.UserProfileDto;
import com.example.realtimechatonline.domain.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserProfileDto toUserProfile(User user);

}