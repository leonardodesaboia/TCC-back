package com.allset.api.chat.dto;

import com.allset.api.shared.validation.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload para envio de mensagem de texto")
public record SendMessageRequest(

        @Schema(description = "Conteúdo da mensagem", example = "Chego em 10 minutos")
        @NotBlank(message = "Conteúdo é obrigatório")
        @Size(max = 4000, message = "Mensagem não pode exceder 4000 caracteres")
        @NoHtml
        String content

) {}
