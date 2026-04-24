package com.allset.api.offering.mapper;

import com.allset.api.offering.domain.ProfessionalOffering;
import com.allset.api.offering.dto.ProfessionalOfferingResponse;
import com.allset.api.review.dto.ReviewRatingSummary;
import com.allset.api.review.service.ReviewSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProfessionalOfferingMapper {

    private final ReviewSummaryService reviewSummaryService;

    public ProfessionalOfferingResponse toResponse(ProfessionalOffering offering) {
        ReviewRatingSummary ratingSummary = reviewSummaryService
                .summarizeProfessionalService(offering.getProfessionalId(), offering.getId());

        return new ProfessionalOfferingResponse(
                offering.getId(),
                offering.getProfessionalId(),
                offering.getCategoryId(),
                offering.getTitle(),
                offering.getDescription(),
                offering.getPricingType(),
                offering.getPrice(),
                offering.getEstimatedDurationMinutes(),
                offering.isActive(),
                ratingSummary.averageRating(),
                ratingSummary.reviewCount(),
                offering.getCreatedAt()
        );
    }

    public List<ProfessionalOfferingResponse> toResponseList(List<ProfessionalOffering> offerings) {
        return offerings.stream().map(this::toResponse).toList();
    }
}
