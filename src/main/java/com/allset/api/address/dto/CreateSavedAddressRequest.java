package com.allset.api.address.dto;

import com.allset.api.shared.validation.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Dados para cadastro de um novo endereço")
public record CreateSavedAddressRequest(

    @Schema(description = "Rótulo do endereço", example = "Casa", nullable = true)
    @Size(max = 60, message = "Rótulo deve ter no máximo 60 caracteres")
    @NoHtml
    String label,

    @Schema(description = "Logradouro", example = "Rua das Flores")
    @NotBlank(message = "Logradouro é obrigatório")
    @Size(max = 200, message = "Logradouro deve ter no máximo 200 caracteres")
    @NoHtml
    String street,

    @Schema(description = "Número", example = "42", nullable = true)
    @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
    @NoHtml
    String number,

    @Schema(description = "Complemento", example = "Apto 3B", nullable = true)
    @Size(max = 80, message = "Complemento deve ter no máximo 80 caracteres")
    @NoHtml
    String complement,

    @Schema(description = "Bairro", example = "Centro", nullable = true)
    @Size(max = 80, message = "Bairro deve ter no máximo 80 caracteres")
    @NoHtml
    String district,

    @Schema(description = "Cidade", example = "São Paulo")
    @NotBlank(message = "Cidade é obrigatória")
    @Size(max = 80, message = "Cidade deve ter no máximo 80 caracteres")
    @NoHtml
    String city,

    @Schema(description = "Sigla do estado (2 letras maiúsculas)", example = "SP")
    @NotBlank(message = "Estado é obrigatório")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Estado deve ter exatamente 2 letras maiúsculas")
    String state,

    @Schema(description = "CEP no formato 99999-999 ou 99999999", example = "01310-100")
    @NotBlank(message = "CEP é obrigatório")
    @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 99999-999 ou 99999999")
    String zipCode,

    @Schema(description = "Latitude geográfica", example = "-23.561414", nullable = true)
    @DecimalMin(value = "-90.000000", message = "Latitude inválida")
    @DecimalMax(value = "90.000000", message = "Latitude inválida")
    BigDecimal lat,

    @Schema(description = "Longitude geográfica", example = "-46.655881", nullable = true)
    @DecimalMin(value = "-180.000000", message = "Longitude inválida")
    @DecimalMax(value = "180.000000", message = "Longitude inválida")
    BigDecimal lng,

    @Schema(description = "Define este como endereço padrão do usuário", example = "false")
    boolean isDefault

) {}
