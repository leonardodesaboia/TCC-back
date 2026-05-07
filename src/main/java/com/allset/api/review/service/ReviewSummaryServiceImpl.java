package com.allset.api.review.service;

import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.review.dto.ReviewRatingSummary;
import com.allset.api.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewSummaryServiceImpl implements ReviewSummaryService {

    private final ReviewRepository reviewRepository;
    private final ProfessionalRepository professionalRepository;

    @Override
    public ReviewRatingSummary summarizeUser(UUID userId) {
        return toSummary(reviewRepository.summarizePublishedByRevieweeId(userId));
    }

    @Override
    public ReviewRatingSummary summarizeProfessional(UUID professionalId) {
        UUID professionalUserId = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .map(professional -> professional.getUserId())
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        return summarizeUser(professionalUserId);
    }

    @Override
    public Map<UUID, ReviewRatingSummary> summarizeProfessionals(List<UUID> professionalIds) {
        if (professionalIds == null || professionalIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UUID> professionalToUserId = professionalRepository.findAllByIdInAndDeletedAtIsNull(professionalIds)
                .stream()
                .collect(Collectors.toMap(
                        professional -> professional.getId(),
                        professional -> professional.getUserId()
                ));

        if (professionalToUserId.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ReviewRatingSummary> summariesByUserId = reviewRepository
                .summarizePublishedByRevieweeIds(professionalToUserId.values().stream().distinct().toList())
                .stream()
                .collect(Collectors.toMap(
                        ReviewRepository.RatingSummaryByRevieweeView::getRevieweeId,
                        this::toSummary
                ));

        Map<UUID, ReviewRatingSummary> summariesByProfessionalId = new LinkedHashMap<>();
        for (UUID professionalId : professionalIds) {
            UUID userId = professionalToUserId.get(professionalId);
            summariesByProfessionalId.put(
                    professionalId,
                    userId == null
                            ? new ReviewRatingSummary(null, 0)
                            : summariesByUserId.getOrDefault(userId, new ReviewRatingSummary(null, 0))
            );
        }
        return summariesByProfessionalId;
    }

    @Override
    public ReviewRatingSummary summarizeProfessionalService(UUID professionalId, UUID serviceId) {
        UUID professionalUserId = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .map(professional -> professional.getUserId())
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        return toSummary(reviewRepository.summarizePublishedServiceReviews(professionalUserId, serviceId));
    }

    private ReviewRatingSummary toSummary(ReviewRepository.RatingSummaryView projection) {
        if (projection == null || projection.getReviewCount() == 0) {
            return new ReviewRatingSummary(null, 0);
        }

        BigDecimal averageRating = BigDecimal.valueOf(projection.getAverageRating())
                .setScale(2, RoundingMode.HALF_UP);

        return new ReviewRatingSummary(averageRating, projection.getReviewCount());
    }
}
