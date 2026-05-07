package com.allset.api.review.scheduler;

import com.allset.api.review.service.ReviewPublicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewPublicationScheduler {

    private final ReviewPublicationService reviewPublicationService;

    @Scheduled(cron = "${review-publication-cron}")
    public void publishExpiredReviews() {
        long publishedCount = reviewPublicationService.publishExpiredReviews();
        if (publishedCount == 0) {
            return;
        }

        log.info("event=review_publication_scheduler count={}", publishedCount);
    }
}
