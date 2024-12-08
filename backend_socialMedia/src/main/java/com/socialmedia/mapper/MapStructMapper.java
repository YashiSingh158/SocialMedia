package com.socialmedia.mapper;

import com.socialmedia.dto.UpdateUserInfoDto;
import com.socialmedia.entity.Users;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface MapStructMapper {
    Users userUpdateDtoToUser(UpdateUserInfoDto updateUserInfoDto);
}
