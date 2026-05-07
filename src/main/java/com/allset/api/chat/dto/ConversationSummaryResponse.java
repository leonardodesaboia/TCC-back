package com.allset.api.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Resumo de uma conversa para listagem")
public record ConversationSummaryResponse(

        @Schema(description = "ID da conversa")
        UUID id,

        @Schema(description = "ID do pedido associado")
        UUID orderId,

        @Schema(description = "ID do outro participante (não é o usuário autenticado)")
        UUID otherParticipantId,

        @Schema(description = "Última mensagem da conversa. Nulo se sem mensagens", nullable = true)
        MessageResponse lastMessage,

        @Schema(description = "Quantidade de mensagens não lidas pelo usuário autenticado")
        long unreadCount

) {}
