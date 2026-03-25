package com.allset.api.shared.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Formato padrão de resposta de erro da API")
public record ApiError(

    @Schema(description = "Código HTTP do erro", example = "400")
    int status,

    @Schema(description = "Mensagem descritiva do erro", example = "Validation failed")
    String message,

    @Schema(description = "Mapa de erros por campo (apenas em erros de validação)", nullable = true,
            example = "{\"email\": \"must be a valid email\"}")
    Map<String, String> fields,

    @Schema(description = "Momento do erro em UTC", example = "2025-03-25T12:00:00Z")
    Instant timestamp

) {}
