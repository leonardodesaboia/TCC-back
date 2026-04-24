package com.allset.api.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para envio de uma avaliacao")
public record CreateReviewRequest(

        @Schema(description = "Nota da avaliacao", example = "5", minimum = "1", maximum = "5")
        @NotNull(message = "rating e obrigatorio")
        @Min(value = 1, message = "rating deve ser entre 1 e 5")
        @Max(value = 5, message = "rating deve ser entre 1 e 5")
        Short rating,

        @Schema(description = "Comentario da avaliacao. Obrigatorio para cliente -> profissional e proibido para profissional -> cliente",
                example = "Servico muito bom, chegou no horario combinado.")
        @Size(max = 1000, message = "comment nao pode ultrapassar 1000 caracteres")
        String comment
) {}
