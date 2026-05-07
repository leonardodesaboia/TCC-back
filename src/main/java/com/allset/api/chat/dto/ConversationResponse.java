package com.allset.api.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Dados de uma conversa")
public record ConversationResponse(

        @Schema(description = "ID da conversa")
        UUID id,

        @Schema(description = "ID do pedido associado")
        UUID orderId,

        @Schema(description = "ID do usuário cliente")
        UUID clientId,

        @Schema(description = "ID do usuário profissional (users.id)")
        UUID professionalUserId,

        @Schema(description = "Momento de criação da conversa")
        Instant createdAt,

        @Schema(description = "Status atual do pedido associado", nullable = true)
        String orderStatus

) {}
