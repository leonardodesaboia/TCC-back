package com.allset.api.review.service;

import com.allset.api.review.dto.ReviewRatingSummary;

import java.util.UUID;

public interface ReviewSummaryService {

    ReviewRatingSummary summarizeUser(UUID userId);

    ReviewRatingSummary summarizeProfessional(UUID professionalId);

    ReviewRatingSummary summarizeProfessionalService(UUID professionalId, UUID serviceId);
}
