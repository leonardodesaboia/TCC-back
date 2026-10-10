package com.allset.api.geocoding.controller;

import com.allset.api.geocoding.dto.GeocodeRequest;
import com.allset.api.geocoding.dto.GeocodeResponse;
import com.allset.api.geocoding.dto.ReverseGeocodeRequest;
import com.allset.api.geocoding.service.GeocodingService;
import com.allset.api.shared.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Geocoding", description = "Conversão de endereço escrito em coordenadas (lat/lng)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/geocoding")
@RequiredArgsConstructor
public class GeocodingController {

    private final GeocodingService geocodingService;

    @Operation(
        summary = "Converter endereço em coordenadas",
        description = "Recebe um endereço escrito (CEP, rua, número, bairro, cidade, estado) e devolve " +
                      "latitude/longitude + endereço normalizado + nível de confiança. " +
                      "Não persiste nada e o resultado é uma **sugestão**: o ponto definitivo do " +
                      "endereço é sempre o pin confirmado pelo usuário. Verifique o campo " +
                      "`confidence` — `CITY` significa aproximação de bairro/município, que o " +
                      "modo Express não aceita."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Endereço localizado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GeocodeResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "422", description = "Endereço não localizável",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "429", description = "Limite de consultas atingido",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "503", description = "Serviço de geocoding indisponível",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/lookup")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GeocodeResponse> lookup(@Valid @RequestBody GeocodeRequest request) {
        return ResponseEntity.ok(geocodingService.geocode(request));
    }

    @Operation(
        summary = "Converter coordenadas em endereço",
        description = "Caminho inverso do lookup: recebe um ponto e devolve o endereço escrito " +
                      "correspondente. Serve para preencher rua e bairro depois que a pessoa move " +
                      "o pin no mapa. **A coordenada devolvida é exatamente a que foi enviada** — " +
                      "o endereço encontrado nunca reposiciona o pin do usuário."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Endereço encontrado para o ponto",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GeocodeResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "422", description = "Nenhum endereço reconhecível no ponto",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "429", description = "Limite de consultas atingido",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "503", description = "Serviço de geocoding indisponível",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/reverse")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GeocodeResponse> reverse(@Valid @RequestBody ReverseGeocodeRequest request) {
        return ResponseEntity.ok(geocodingService.reverse(request));
    }
}
