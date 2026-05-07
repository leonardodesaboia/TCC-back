package com.allset.api.review.scheduler;

import com.allset.api.review.service.ReviewPublicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewPublicationSchedulerTest {

    @Mock
    private ReviewPublicationService reviewPublicationService;

    @InjectMocks
    private ReviewPublicationScheduler reviewPublicationScheduler;

    @Test
    void publishExpiredReviewsShouldCallService() {
        when(reviewPublicationService.publishExpiredReviews()).thenReturn(2L);

        reviewPublicationScheduler.publishExpiredReviews();

        verify(reviewPublicationService).publishExpiredReviews();
    }
}
