package com.allset.api.review.service;

import com.allset.api.review.domain.Review;
import com.allset.api.review.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewPublicationServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private ReviewPublicationService reviewPublicationService;

    @Test
    void publishOrderIfReadyShouldSkipWhenOnlyOneReviewExists() {
        UUID orderId = UUID.randomUUID();

        when(reviewRepository.findAllByOrderIdOrderBySubmittedAtAsc(orderId))
                .thenReturn(List.of(Review.builder().orderId(orderId).build()));

        reviewPublicationService.publishOrderIfReady(orderId);

        verify(reviewRepository, never()).publishOrderReviews(org.mockito.ArgumentMatchers.eq(orderId), org.mockito.ArgumentMatchers.any(Instant.class));
    }

    @Test
    void publishOrderIfReadyShouldPublishWhenTwoReviewsExist() {
        UUID orderId = UUID.randomUUID();

        when(reviewRepository.findAllByOrderIdOrderBySubmittedAtAsc(orderId))
                .thenReturn(List.of(
                        Review.builder().orderId(orderId).build(),
                        Review.builder().orderId(orderId).build()
                ));

        Instant publishedAt = reviewPublicationService.publishOrderIfReady(orderId);

        assertThat(publishedAt).isNotNull();
        verify(reviewRepository).publishOrderReviews(org.mockito.ArgumentMatchers.eq(orderId), org.mockito.ArgumentMatchers.any(Instant.class));
    }

    @Test
    void publishExpiredReviewsShouldPublishEveryDueOrderOnce() {
        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();

        when(reviewRepository.findAllReadyToPublish(org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(
                        Review.builder().orderId(firstOrderId).build(),
                        Review.builder().orderId(firstOrderId).build(),
                        Review.builder().orderId(secondOrderId).build()
                ));
        when(reviewRepository.publishOrderReviews(org.mockito.ArgumentMatchers.eq(firstOrderId), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(2);
        when(reviewRepository.publishOrderReviews(org.mockito.ArgumentMatchers.eq(secondOrderId), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(1);

        long publishedCount = reviewPublicationService.publishExpiredReviews();

        assertThat(publishedCount).isEqualTo(3);
        verify(reviewRepository).publishOrderReviews(org.mockito.ArgumentMatchers.eq(firstOrderId), org.mockito.ArgumentMatchers.any(Instant.class));
        verify(reviewRepository).publishOrderReviews(org.mockito.ArgumentMatchers.eq(secondOrderId), org.mockito.ArgumentMatchers.any(Instant.class));
    }
}
