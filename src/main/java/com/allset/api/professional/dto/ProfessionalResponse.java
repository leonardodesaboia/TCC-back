package com.allset.api.professional.dto;

import com.allset.api.professional.domain.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProfessionalResponse(

        @Schema(description = "ID do perfil profissional") UUID id,
        @Schema(description = "ID do usuario vinculado") UUID userId,
        @Schema(description = "Apresentacao do profissional") String bio,
        @Schema(description = "Anos de experiencia") Short yearsOfExperience,
        @Schema(description = "Taxa horaria base sugerida") BigDecimal baseHourlyRate,
        @Schema(description = "Especialidades com experiência por categoria") List<ProfessionalSpecialtyResponse> specialties,
        @Schema(description = "Status de verificacao KYC") VerificationStatus verificationStatus,
        @Schema(description = "Motivo de rejeicao do KYC") String rejectionReason,
        @Schema(description = "Disponivel para pedidos Express") boolean geoActive,
        @Schema(description = "Timestamp da última captura de localização", nullable = true) Instant geoCapturedAt,
        @Schema(description = "Acurácia da última captura em metros", nullable = true) BigDecimal geoAccuracyMeters,
        @Schema(description = "ID do plano de assinatura") UUID subscriptionPlanId,
        @Schema(description = "Expiracao do plano de assinatura") Instant subscriptionExpiresAt,
        @Schema(description = "Media das avaliacoes publicadas recebidas", nullable = true) BigDecimal averageRating,
        @Schema(description = "Quantidade de avaliacoes publicadas recebidas") long reviewCount,
        @Schema(description = "Data de criacao") Instant createdAt,
        @Schema(description = "Data de atualizacao") Instant updatedAt
) {}
