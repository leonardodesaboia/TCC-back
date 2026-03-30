package com.allset.api.professional.dto;

import com.allset.api.professional.domain.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProfessionalResponse(

        @Schema(description = "ID do perfil profissional") UUID id,
        @Schema(description = "ID do usuário vinculado") UUID userId,
        @Schema(description = "Apresentação do profissional") String bio,
        @Schema(description = "Anos de experiência") Short yearsOfExperience,
        @Schema(description = "Taxa horária base sugerida") BigDecimal baseHourlyRate,
        @Schema(description = "Status de verificação KYC") VerificationStatus verificationStatus,
        @Schema(description = "Motivo de rejeição do KYC") String rejectionReason,
        @Schema(description = "Disponível para pedidos Express") boolean geoActive,
        @Schema(description = "ID do plano de assinatura") UUID subscriptionPlanId,
        @Schema(description = "Expiração do plano de assinatura") Instant subscriptionExpiresAt,
        @Schema(description = "Data de criação") Instant createdAt,
        @Schema(description = "Data de atualização") Instant updatedAt
) {}
