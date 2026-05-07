package com.allset.api.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Evento de confirmação de entrega publicado no tópico STOMP da conversa")
public record DeliveryReceiptEvent(

        @Schema(description = "Discriminante do tipo de evento", example = "DELIVERY_RECEIPT")
        String eventType,

        @Schema(description = "ID da conversa")
        UUID conversationId,

        @Schema(description = "ID do usuário que recebeu as mensagens")
        UUID receiverUserId,

        @Schema(description = "Momento da entrega")
        Instant deliveredAt,

        @Schema(description = "Quantidade de mensagens marcadas como entregues")
        int affectedCount

) {}
