package com.allset.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Dados para criação de um pedido On Demand a partir de um serviço publicado")
public record CreateOnDemandOrderRequest(

        @Schema(description = "ID do serviço publicado pelo profissional")
        @NotNull(message = "Serviço é obrigatório")
        UUID serviceId,

        @Schema(description = "Descrição adicional do cliente sobre o que precisa")
        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 2000, message = "Descrição deve ter no máximo 2000 caracteres")
        String description,

        @Schema(description = "ID do endereço salvo do cliente")
        @NotNull(message = "Endereço é obrigatório")
        UUID addressId,

        @Schema(description = "Data/hora agendada para o serviço")
        @NotNull(message = "Data agendada é obrigatória")
        Instant scheduledAt
) {}
