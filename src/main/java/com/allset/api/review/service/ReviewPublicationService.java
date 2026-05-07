package com.allset.api.review.service;

import com.allset.api.review.domain.Review;
import com.allset.api.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewPublicationService {

    private final ReviewRepository reviewRepository;

    public Instant publishOrderIfReady(UUID orderId) {
        List<Review> reviews = reviewRepository.findAllByOrderIdOrderBySubmittedAtAsc(orderId);
        if (reviews.size() < 2) {
            return null;
        }

        Instant publishedAt = Instant.now();
        reviewRepository.publishOrderReviews(orderId, publishedAt);
        return publishedAt;
    }

    public long publishExpiredReviews() {
        List<Review> dueReviews = reviewRepository.findAllReadyToPublish(Instant.now().minus(7, ChronoUnit.DAYS));
        if (dueReviews.isEmpty()) {
            return 0;
        }

        LinkedHashSet<UUID> orderIds = new LinkedHashSet<>();
        for (Review dueReview : dueReviews) {
            orderIds.add(dueReview.getOrderId());
        }

        long updated = 0;

        for (UUID orderId : orderIds) {
            updated += reviewRepository.publishOrderReviews(orderId, Instant.now());
        }

        return updated;
    }
}
