package com.allset.api.user.mapper;

import com.allset.api.user.domain.User;
import com.allset.api.user.dto.UserResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getPhone(),
            user.getRole(),
            user.getAvatarUrl(),
            user.isActive(),
            user.getBanReason(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    public List<UserResponse> toResponseList(List<User> users) {
        return users.stream()
            .map(this::toResponse)
            .toList();
    }
}
