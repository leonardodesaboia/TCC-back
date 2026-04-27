package com.allset.api.chat.dto;

import com.allset.api.chat.domain.MessageType;
import com.allset.api.shared.storage.dto.StorageRefResponse;
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

        @Schema(description = "Anexo da mensagem (apenas para msg_type=image)", nullable = true)
        StorageRefResponse attachment,

        @Schema(description = "Tamanho do anexo em bytes", nullable = true)
        Integer attachmentSizeBytes,

        @Schema(description = "MIME type do anexo", nullable = true)
        String attachmentMimeType,

        @Schema(description = "Momento em que a mensagem foi enviada")
        Instant sentAt,

        @Schema(description = "Momento em que a mensagem foi entregue ao destinatário", nullable = true)
        Instant deliveredAt,

        @Schema(description = "Momento em que a mensagem foi lida pelo destinatário", nullable = true)
        Instant readAt

) {}
