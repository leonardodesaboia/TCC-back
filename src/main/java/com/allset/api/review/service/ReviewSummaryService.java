package com.allset.api.review.service;

import com.allset.api.review.dto.ReviewRatingSummary;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ReviewSummaryService {

    ReviewRatingSummary summarizeUser(UUID userId);

    ReviewRatingSummary summarizeProfessional(UUID professionalId);

    Map<UUID, ReviewRatingSummary> summarizeProfessionals(List<UUID> professionalIds);

    ReviewRatingSummary summarizeProfessionalService(UUID professionalId, UUID serviceId);
}
