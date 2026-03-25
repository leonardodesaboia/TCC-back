package com.allset.api.address.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Dados de um endereço salvo")
public record SavedAddressResponse(

    @Schema(description = "ID único do endereço", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(description = "ID do usuário proprietário", example = "550e8400-e29b-41d4-a716-446655440001")
    UUID userId,

    @Schema(description = "Rótulo do endereço", example = "Casa", nullable = true)
    String label,

    @Schema(description = "Logradouro", example = "Rua das Flores")
    String street,

    @Schema(description = "Número", example = "42", nullable = true)
    String number,

    @Schema(description = "Complemento", example = "Apto 3B", nullable = true)
    String complement,

    @Schema(description = "Bairro", example = "Centro", nullable = true)
    String district,

    @Schema(description = "Cidade", example = "São Paulo")
    String city,

    @Schema(description = "Sigla do estado", example = "SP")
    String state,

    @Schema(description = "CEP", example = "01310-100")
    String zipCode,

    @Schema(description = "Latitude geográfica", example = "-23.561414", nullable = true)
    BigDecimal lat,

    @Schema(description = "Longitude geográfica", example = "-46.655881", nullable = true)
    BigDecimal lng,

    @Schema(description = "Indica se é o endereço padrão do usuário", example = "false")
    boolean isDefault,

    @Schema(description = "Data e hora de criação (UTC)", example = "2025-01-15T10:30:00Z")
    Instant createdAt,

    @Schema(description = "Data e hora da última atualização (UTC)", example = "2025-03-20T14:00:00Z")
    Instant updatedAt

) {}
