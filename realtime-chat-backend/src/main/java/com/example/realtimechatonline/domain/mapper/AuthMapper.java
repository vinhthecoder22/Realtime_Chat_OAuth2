package com.example.realtimechatonline.domain.mapper;

import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.domain.dto.request.RegisterRequestDto;
import com.example.realtimechatonline.domain.dto.response.RegisterResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper — componentModel="spring" is set globally via
 * -Amapstruct.defaultComponentModel=spring compiler arg in pom.xml.
 */
@Mapper(componentModel = "spring")
public interface AuthMapper {

    /**
     * Maps RegisterRequest → User.
     * password will be encoded by the service before saving — NOT here.
     */
    @Mapping(target = "id",           ignore = true)
    @Mapping(target = "fullname",     ignore = true)
    @Mapping(target = "picture",      ignore = true)
    @Mapping(target = "role",         ignore = true)
    @Mapping(target = "authProvider", ignore = true)
    User toUser(RegisterRequestDto request);

    @Mapping(source = "username",     target = "username")
    @Mapping(source = "email",        target = "email")
    @Mapping(source = "role",         target = "role")
    @Mapping(source = "authProvider", target = "authProvider")
    RegisterResponseDto toRegisterResponse(User user);
}
