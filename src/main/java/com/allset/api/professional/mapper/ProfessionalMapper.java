package com.allset.api.professional.mapper;

import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.dto.ProfessionalResponse;
import com.allset.api.review.dto.ReviewRatingSummary;
import com.allset.api.review.service.ReviewSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProfessionalMapper {

    private final ReviewSummaryService reviewSummaryService;

    public ProfessionalResponse toResponse(Professional professional) {
        ReviewRatingSummary ratingSummary = reviewSummaryService.summarizeProfessional(professional.getId());

        return new ProfessionalResponse(
                professional.getId(),
                professional.getUserId(),
                professional.getBio(),
                professional.getYearsOfExperience(),
                professional.getBaseHourlyRate(),
                professional.getVerificationStatus(),
                professional.getRejectionReason(),
                professional.isGeoActive(),
                professional.getSubscriptionPlanId(),
                professional.getSubscriptionExpiresAt(),
                ratingSummary.averageRating(),
                ratingSummary.reviewCount(),
                professional.getCreatedAt(),
                professional.getUpdatedAt()
        );
    }

    public List<ProfessionalResponse> toResponseList(List<Professional> professionals) {
        return professionals.stream().map(this::toResponse).toList();
    }
}
