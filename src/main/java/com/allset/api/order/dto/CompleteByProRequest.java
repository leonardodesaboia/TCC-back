package com.allset.api.order.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteByProRequest(

        @NotBlank(message = "URL da foto comprobatória é obrigatória")
        String photoUrl
) {}
