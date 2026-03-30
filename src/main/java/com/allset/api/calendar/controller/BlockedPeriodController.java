package com.allset.api.calendar.controller;

import com.allset.api.calendar.dto.BlockedPeriodResponse;
import com.allset.api.calendar.dto.CreateBlockedPeriodRequest;
import com.allset.api.calendar.service.BlockedPeriodService;
import com.allset.api.shared.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Profissionais — Calendário", description = "Gerenciamento de bloqueios de agenda do profissional")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/professionals/{professionalId}/calendar/blocks")
@RequiredArgsConstructor
public class BlockedPeriodController {

    private final BlockedPeriodService blockedPeriodService;

    @Operation(summary = "Criar bloqueio", description = "Registra um bloqueio na agenda do profissional. "
            + "Campos obrigatórios variam por tipo: recurring exige weekday, specific_date exige specificDate, order exige orderId/orderStartsAt/orderEndsAt.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bloqueio criado com sucesso",
                    headers = @Header(name = "Location", description = "URI do novo recurso", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BlockedPeriodResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou campos obrigatórios ausentes para o tipo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<BlockedPeriodResponse> create(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId,
            @Valid @RequestBody CreateBlockedPeriodRequest request
    ) {
        BlockedPeriodResponse response = blockedPeriodService.create(professionalId, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Listar bloqueios", description = "Retorna todos os bloqueios de agenda do profissional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BlockedPeriodResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<List<BlockedPeriodResponse>> findAll(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId
    ) {
        return ResponseEntity.ok(blockedPeriodService.findAllByProfessional(professionalId));
    }

    @Operation(summary = "Remover bloqueio", description = "Remove fisicamente um bloqueio da agenda.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bloqueio removido com sucesso", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Bloqueio não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId,
            @Parameter(description = "ID do bloqueio", required = true) @PathVariable UUID id
    ) {
        blockedPeriodService.delete(professionalId, id);
        return ResponseEntity.noContent().build();
    }
}
