package com.allset.api.professional.mapper;

import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.dto.ProfessionalResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProfessionalMapper {

    public ProfessionalResponse toResponse(Professional professional) {
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
                professional.getCreatedAt(),
                professional.getUpdatedAt()
        );
    }

    public List<ProfessionalResponse> toResponseList(List<Professional> professionals) {
        return professionals.stream().map(this::toResponse).toList();
    }
}
