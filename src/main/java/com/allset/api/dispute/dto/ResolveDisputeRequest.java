package com.allset.api.dispute.dto;

import com.allset.api.dispute.domain.DisputeResolution;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Payload para resolucao de disputa pelo admin. "
        + "Para refund_partial os dois valores sao obrigatorios e devem somar exatamente o total do pedido. "
        + "Para refund_full e release_to_pro os valores sao calculados automaticamente.")
public record ResolveDisputeRequest(

        @Schema(description = "Tipo da resolucao", example = "refund_partial")
        @NotNull(message = "Tipo de resolucao e obrigatorio")
        DisputeResolution resolution,

        @Schema(description = "Valor devolvido ao cliente — obrigatorio em refund_partial",
                example = "75.00", nullable = true)
        @DecimalMin(value = "0.00", message = "Valor de reembolso deve ser maior ou igual a zero")
        @Digits(integer = 8, fraction = 2, message = "Formato monetario invalido")
        BigDecimal clientRefundAmount,

        @Schema(description = "Valor liberado ao profissional — obrigatorio em refund_partial",
                example = "25.00", nullable = true)
        @DecimalMin(value = "0.00", message = "Valor do profissional deve ser maior ou igual a zero")
        @Digits(integer = 8, fraction = 2, message = "Formato monetario invalido")
        BigDecimal professionalAmount,

        @Schema(description = "Notas internas do admin justificando a decisao",
                example = "Evidencias do cliente comprovam servico parcialmente concluido", nullable = true)
        @Size(max = 4000, message = "Notas nao podem exceder 4000 caracteres")
        String adminNotes
) {}
