package com.allset.api.professional.controller;

import com.allset.api.professional.repository.ProfessionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Helper usado nas expressões @PreAuthorize do ProfessionalController.
 * Necessário porque authentication.name contém o UUID do usuário,
 * mas o path variable é o ID do perfil profissional — a resolução
 * requer uma query para verificar o vínculo.
 */
@Component("professionalAuthHelper")
@RequiredArgsConstructor
public class ProfessionalAuthHelper {

    private final ProfessionalRepository professionalRepository;

    public boolean isOwner(UUID professionalId, Authentication authentication) {
        return professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .map(p -> p.getUserId().toString().equals(authentication.getName()))
                .orElse(false);
    }
}
