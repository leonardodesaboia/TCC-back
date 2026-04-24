package com.allset.api.review.service;

import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.review.dto.ReviewRatingSummary;
import com.allset.api.review.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewSummaryServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @InjectMocks
    private ReviewSummaryServiceImpl reviewSummaryService;

    @Test
    void summarizeUserShouldReturnNullAverageWhenThereIsNoPublishedReview() {
        UUID userId = UUID.randomUUID();

        when(reviewRepository.summarizePublishedByRevieweeId(userId))
                .thenReturn(new RatingSummaryViewStub(null, 0));

        ReviewRatingSummary summary = reviewSummaryService.summarizeUser(userId);

        assertThat(summary.averageRating()).isNull();
        assertThat(summary.reviewCount()).isZero();
    }

    @Test
    void summarizeProfessionalShouldUseProfessionalUserIdAndRoundAverage() {
        UUID professionalId = UUID.randomUUID();
        UUID professionalUserId = UUID.randomUUID();

        Professional professional = Professional.builder()
                .userId(professionalUserId)
                .build();
        professional.setId(professionalId);

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(reviewRepository.summarizePublishedByRevieweeId(professionalUserId))
                .thenReturn(new RatingSummaryViewStub(4.666666D, 3));

        ReviewRatingSummary summary = reviewSummaryService.summarizeProfessional(professionalId);

        assertThat(summary.averageRating()).hasToString("4.67");
        assertThat(summary.reviewCount()).isEqualTo(3);
    }

    private static final class RatingSummaryViewStub implements ReviewRepository.RatingSummaryView {

        private final Double averageRating;
        private final long reviewCount;

        private RatingSummaryViewStub(Double averageRating, long reviewCount) {
            this.averageRating = averageRating;
            this.reviewCount = reviewCount;
        }

        @Override
        public Double getAverageRating() {
            return averageRating;
        }

        @Override
        public long getReviewCount() {
            return reviewCount;
        }
    }
}
