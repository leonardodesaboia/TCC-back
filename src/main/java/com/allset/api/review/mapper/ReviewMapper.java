package com.allset.api.review.mapper;

import com.allset.api.review.domain.Review;
import com.allset.api.review.dto.ReviewResponse;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getOrderId(),
                review.getReviewerId(),
                review.getRevieweeId(),
                review.getRating(),
                review.getComment(),
                review.getSubmittedAt(),
                review.getPublishedAt()
        );
    }
}
