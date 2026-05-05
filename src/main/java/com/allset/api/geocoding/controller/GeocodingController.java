package com.allset.api.geocoding.controller;

import com.allset.api.geocoding.dto.GeocodeRequest;
import com.allset.api.geocoding.dto.GeocodeResponse;
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
                      "Não persiste nada — usar antes de criar um SavedAddress para confirmar o pin no mapa."
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
}
