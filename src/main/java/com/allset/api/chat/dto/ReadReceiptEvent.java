package com.allset.api.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Evento de confirmação de leitura publicado no tópico STOMP da conversa")
public record ReadReceiptEvent(

        @Schema(description = "Discriminante do tipo de evento", example = "READ_RECEIPT")
        String eventType,

        @Schema(description = "ID da conversa")
        UUID conversationId,

        @Schema(description = "ID do usuário que leu as mensagens")
        UUID readerUserId,

        @Schema(description = "Momento da leitura")
        Instant readAt,

        @Schema(description = "Quantidade de mensagens marcadas como lidas")
        int affectedCount

) {}
