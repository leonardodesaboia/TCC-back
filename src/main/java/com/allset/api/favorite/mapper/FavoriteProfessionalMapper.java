package com.allset.api.favorite.mapper;

import com.allset.api.favorite.domain.FavoriteProfessional;
import com.allset.api.favorite.dto.FavoriteProfessionalResponse;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.dto.ProfessionalResponse;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.mapper.ProfessionalMapper;
import com.allset.api.professional.repository.ProfessionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FavoriteProfessionalMapper {

    private final ProfessionalRepository professionalRepository;
    private final ProfessionalMapper professionalMapper;

    public FavoriteProfessionalResponse toResponse(FavoriteProfessional favorite) {
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(favorite.getProfessionalId())
                .orElseThrow(() -> new ProfessionalNotFoundException(favorite.getProfessionalId()));
        ProfessionalResponse professionalResponse = professionalMapper.toResponse(professional);

        return new FavoriteProfessionalResponse(
                favorite.getId(),
                favorite.getClientId(),
                professionalResponse,
                favorite.getCreatedAt()
        );
    }
}
