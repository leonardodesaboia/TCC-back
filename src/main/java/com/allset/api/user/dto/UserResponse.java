package com.allset.api.user.dto;

import com.allset.api.shared.storage.dto.StorageRefResponse;
import com.allset.api.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Dados publicos de um usuario")
public record UserResponse(

    @Schema(description = "ID unico do usuario", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(description = "Nome completo", example = "Joao da Silva")
    String name,

    @Schema(description = "Endereco de e-mail", example = "joao@email.com")
    String email,

    @Schema(description = "Telefone", example = "+5511999999999")
    String phone,

    @Schema(description = "Data de nascimento", example = "1995-09-15")
    LocalDate birthDate,

    @Schema(description = "Papel do usuario no sistema", example = "client")
    UserRole role,

    @Schema(description = "Avatar do usuario (chave + URL pre-assinada)", nullable = true)
    StorageRefResponse avatar,

    @Schema(description = "Indica se o usuario esta ativo", example = "true")
    boolean active,

    @Schema(description = "Motivo do banimento", nullable = true)
    String banReason,

    @Schema(description = "Media das avaliacoes publicadas recebidas", nullable = true, example = "4.50")
    BigDecimal averageRating,

    @Schema(description = "Quantidade de avaliacoes publicadas recebidas", example = "8")
    long reviewCount,

    @Schema(description = "Data e hora de criacao (UTC)", example = "2025-01-15T10:30:00Z")
    Instant createdAt,

    @Schema(description = "Data e hora da ultima atualizacao (UTC)", example = "2025-03-20T14:00:00Z")
    Instant updatedAt,

    @Schema(description = "Data agendada para exclusao permanente", nullable = true, example = "2025-04-20T14:00:00Z")
    Instant scheduledDeletionAt

) {}
