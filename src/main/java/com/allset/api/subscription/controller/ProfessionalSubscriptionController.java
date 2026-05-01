package com.allset.api.subscription.controller;

import com.allset.api.shared.exception.ApiError;
import com.allset.api.subscription.dto.AssignSubscriptionPlanRequest;
import com.allset.api.subscription.dto.CancelSubscriptionResponse;
import com.allset.api.subscription.dto.ProfessionalSubscriptionResponse;
import com.allset.api.subscription.service.ProfessionalSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Assinaturas - Profissional", description = "Contratacao, troca e cancelamento de planos de assinatura")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/professionals/{professionalId}/subscription")
@RequiredArgsConstructor
public class ProfessionalSubscriptionController {

    private final ProfessionalSubscriptionService professionalSubscriptionService;

    @Operation(summary = "Visualizar assinatura atual", description = "Retorna o plano atualmente ativo do profissional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assinatura encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalSubscriptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional sem assinatura ativa ou perfil nao encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    // TODO: mapear restricao de role - descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<ProfessionalSubscriptionResponse> findCurrent(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId
    ) {
        return ResponseEntity.ok(professionalSubscriptionService.findCurrent(professionalId));
    }

    @Operation(summary = "Contratar ou trocar plano", description = "Se o profissional nao tiver plano ativo, contrata um novo. Se ja tiver, troca o plano mantendo o fim do ciclo atual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plano aplicado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalSubscriptionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados invalidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional ou plano nao encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Plano ja esta ativo para o profissional",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping
    // TODO: mapear restricao de role - descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<ProfessionalSubscriptionResponse> assignPlan(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId,
            @Valid @RequestBody AssignSubscriptionPlanRequest request
    ) {
        return ResponseEntity.ok(professionalSubscriptionService.assignPlan(professionalId, request));
    }

    @Operation(summary = "Cancelar assinatura", description = "Cancela a renovacao futura e mantem os beneficios ate o fim do periodo atual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelamento registrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CancelSubscriptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional sem assinatura ativa ou perfil nao encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/cancel")
    // TODO: mapear restricao de role - descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<CancelSubscriptionResponse> cancel(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId
    ) {
        return ResponseEntity.ok(professionalSubscriptionService.cancel(professionalId));
    }
}
