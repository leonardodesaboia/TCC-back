package com.allset.api.order.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ClientRespondRequest(

        /**
         * ID do profissional cuja proposta o cliente está aceitando.
         * As demais propostas recebidas são automaticamente recusadas.
         */
        @NotNull(message = "ID do profissional selecionado é obrigatório")
        UUID selectedProfessionalId
) {}
