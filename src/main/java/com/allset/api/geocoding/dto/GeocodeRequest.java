package com.allset.api.geocoding.dto;

import com.allset.api.shared.validation.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Endereço escrito a ser convertido em coordenadas. " +
                      "Todos os campos são opcionais individualmente, mas o request " +
                      "precisa ter pelo menos CEP **ou** (rua + cidade).")
public record GeocodeRequest(

    @Schema(description = "CEP no formato 99999-999 ou 99999999", example = "60160-230", nullable = true)
    @Pattern(regexp = "^$|^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 99999-999 ou 99999999")
    String zipCode,

    @Schema(description = "Logradouro", example = "Av. Dom Luís", nullable = true)
    @Size(max = 200, message = "Logradouro deve ter no máximo 200 caracteres")
    @NoHtml
    String street,

    @Schema(description = "Número", example = "1233", nullable = true)
    @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
    @NoHtml
    String number,

    @Schema(description = "Complemento (não influencia o geocoding, retornado apenas para conveniência)",
            example = "Sala 501", nullable = true)
    @Size(max = 80, message = "Complemento deve ter no máximo 80 caracteres")
    @NoHtml
    String complement,

    @Schema(description = "Bairro", example = "Aldeota", nullable = true)
    @Size(max = 80, message = "Bairro deve ter no máximo 80 caracteres")
    @NoHtml
    String district,

    @Schema(description = "Cidade", example = "Fortaleza", nullable = true)
    @Size(max = 80, message = "Cidade deve ter no máximo 80 caracteres")
    @NoHtml
    String city,

    @Schema(description = "Sigla do estado (2 letras maiúsculas)", example = "CE", nullable = true)
    @Pattern(regexp = "^$|^[A-Z]{2}$", message = "Estado deve ter exatamente 2 letras maiúsculas")
    String state

) {

    /**
     * O request precisa de informação suficiente para localizar o endereço:
     * CEP sozinho já basta, ou rua + cidade.
     */
    @AssertTrue(message = "Informe pelo menos CEP ou (logradouro + cidade)")
    @Schema(hidden = true)
    public boolean isUsable() {
        if (notBlank(zipCode)) return true;
        return notBlank(street) && notBlank(city);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
