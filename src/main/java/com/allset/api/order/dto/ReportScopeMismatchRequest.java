package com.allset.api.order.dto;

import com.allset.api.shared.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportScopeMismatchRequest(

        @NotBlank(message = "Descrição da divergência é obrigatória")
        @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
        @NoHtml
        String description
) {}
