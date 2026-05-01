package com.allset.api.subscription.controller;

import com.allset.api.shared.exception.ApiError;
import com.allset.api.subscription.dto.CreateSubscriptionPlanRequest;
import com.allset.api.subscription.dto.SubscriptionPlanResponse;
import com.allset.api.subscription.dto.UpdateSubscriptionPlanRequest;
import com.allset.api.subscription.service.SubscriptionPlanService;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Assinaturas", description = "Gerenciamento de planos de assinatura para profissionais")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/subscription-plans")
@RequiredArgsConstructor
public class SubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    @Operation(summary = "Criar plano de assinatura", description = "Cria um novo plano. Exclusivo para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Plano criado com sucesso",
                    headers = @Header(name = "Location", description = "URI do novo recurso", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SubscriptionPlanResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados invalidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado - requer role admin", content = @Content),
            @ApiResponse(responseCode = "409", description = "Nome ja cadastrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    // TODO: mapear restricao de role - descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<SubscriptionPlanResponse> create(@Valid @RequestBody CreateSubscriptionPlanRequest request) {
        SubscriptionPlanResponse response = subscriptionPlanService.create(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Listar planos de assinatura", description = "Retorna planos paginados. Por padrao retorna apenas ativos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SubscriptionPlanResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content)
    })
    @GetMapping
    public ResponseEntity<Page<SubscriptionPlanResponse>> findAll(
            @Parameter(description = "Incluir planos inativos") @RequestParam(defaultValue = "false") boolean includeInactive,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return ResponseEntity.ok(subscriptionPlanService.findAll(includeInactive, pageable));
    }

    @Operation(summary = "Buscar plano por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plano encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SubscriptionPlanResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Plano nao encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionPlanResponse> findById(
            @Parameter(description = "ID do plano", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(subscriptionPlanService.findById(id));
    }

    @Operation(summary = "Atualizar plano de assinatura", description = "Atualiza dados do plano. Exclusivo para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plano atualizado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SubscriptionPlanResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados invalidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado - requer role admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "Plano nao encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Nome ja cadastrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    // TODO: mapear restricao de role - descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<SubscriptionPlanResponse> update(
            @Parameter(description = "ID do plano", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionPlanRequest request
    ) {
        return ResponseEntity.ok(subscriptionPlanService.update(id, request));
    }

    @Operation(summary = "Remover plano de assinatura", description = "Remove o plano via soft delete. Exclusivo para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Plano removido com sucesso", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado - requer role admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "Plano nao encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    // TODO: mapear restricao de role - descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do plano", required = true) @PathVariable UUID id
    ) {
        subscriptionPlanService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
