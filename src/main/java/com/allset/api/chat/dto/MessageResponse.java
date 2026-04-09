package com.allset.api.chat.dto;

import com.allset.api.chat.domain.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Representação de uma mensagem")
public record MessageResponse(

        @Schema(description = "ID da mensagem")
        UUID id,

        @Schema(description = "ID da conversa")
        UUID conversationId,

        @Schema(description = "ID do remetente. Nulo em mensagens do sistema", nullable = true)
        UUID senderId,

        @Schema(description = "Tipo da mensagem", example = "text")
        MessageType msgType,

        @Schema(description = "Conteúdo textual da mensagem", example = "Chego em 10 minutos", nullable = true)
        String content,

        @Schema(description = "URL do anexo (apenas para msg_type=image)", nullable = true)
        String attachmentUrl,

        @Schema(description = "Momento em que a mensagem foi enviada")
        Instant sentAt,

        @Schema(description = "Momento em que a mensagem foi entregue ao destinatário", nullable = true)
        Instant deliveredAt,

        @Schema(description = "Momento em que a mensagem foi lida pelo destinatário", nullable = true)
        Instant readAt

) {}
