package com.allset.api.professional.dto;

import com.allset.api.professional.domain.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record VerifyProfessionalRequest(

        @Schema(description = "Novo status de verificação (approved ou rejected)")
        @NotNull(message = "Status é obrigatório")
        VerificationStatus status,

        @Schema(description = "Motivo da rejeição — obrigatório quando status = rejected")
        String rejectionReason
) {}
