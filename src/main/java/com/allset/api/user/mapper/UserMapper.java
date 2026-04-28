package com.allset.api.user.mapper;

import com.allset.api.review.dto.ReviewRatingSummary;
import com.allset.api.review.service.ReviewSummaryService;
import com.allset.api.shared.storage.domain.StorageBucket;
import com.allset.api.shared.storage.service.StorageRefFactory;
import com.allset.api.user.domain.User;
import com.allset.api.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final ReviewSummaryService reviewSummaryService;
    private final StorageRefFactory storageRefFactory;

    public UserResponse toResponse(User user) {
        ReviewRatingSummary ratingSummary = reviewSummaryService.summarizeUser(user.getId());

        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getPhone(),
            user.getBirthDate(),
            user.getRole(),
            storageRefFactory.from(StorageBucket.AVATARS, user.getAvatarUrl()),
            user.isActive(),
            user.getBanReason(),
            ratingSummary.averageRating(),
            ratingSummary.reviewCount(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getDeletedAt() != null ? user.getDeletedAt().plus(30, ChronoUnit.DAYS) : null
        );
    }

    public List<UserResponse> toResponseList(List<User> users) {
        return users.stream()
            .map(this::toResponse)
            .toList();
    }
}
