package com.allset.api.offering.mapper;

import com.allset.api.offering.domain.ProfessionalOffering;
import com.allset.api.offering.dto.ProfessionalOfferingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProfessionalOfferingMapper {

    public ProfessionalOfferingResponse toResponse(ProfessionalOffering offering) {
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
                offering.getCreatedAt()
        );
    }

    public List<ProfessionalOfferingResponse> toResponseList(List<ProfessionalOffering> offerings) {
        return offerings.stream().map(this::toResponse).toList();
    }
}
