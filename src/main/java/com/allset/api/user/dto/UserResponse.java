package com.allset.api.user.dto;

import com.allset.api.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Dados públicos de um usuário")
public record UserResponse(

    @Schema(description = "ID único do usuário", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(description = "Nome completo", example = "João da Silva")
    String name,

    @Schema(description = "Endereço de e-mail", example = "joao@email.com")
    String email,

    @Schema(description = "Telefone no formato E.164", example = "+5511999999999")
    String phone,

    @Schema(description = "Papel do usuário no sistema", example = "client")
    UserRole role,

    @Schema(description = "URL do avatar", example = "https://cdn.allset.com.br/avatars/uuid.jpg", nullable = true)
    String avatarUrl,

    @Schema(description = "Indica se o usuário está ativo", example = "true")
    boolean active,

    @Schema(description = "Motivo do banimento, caso o usuário esteja banido", nullable = true)
    String banReason,

    @Schema(description = "Data e hora de criação (UTC)", example = "2025-01-15T10:30:00Z")
    Instant createdAt,

    @Schema(description = "Data e hora da última atualização (UTC)", example = "2025-03-20T14:00:00Z")
    Instant updatedAt,

    @Schema(description = "Data agendada para exclusão permanente (presente apenas quando a conta está em período de graça)", nullable = true, example = "2025-04-20T14:00:00Z")
    Instant scheduledDeletionAt

) {}
