package com.allset.api.offering.controller;

import com.allset.api.offering.dto.CreateProfessionalOfferingRequest;
import com.allset.api.offering.dto.ProfessionalOfferingResponse;
import com.allset.api.offering.dto.UpdateProfessionalOfferingRequest;
import com.allset.api.offering.service.ProfessionalOfferingService;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Profissionais — Serviços", description = "Catálogo de serviços ofertados pelo profissional (nível 3 do catálogo)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/professionals/{professionalId}/services")
@RequiredArgsConstructor
public class ProfessionalOfferingController {

    private final ProfessionalOfferingService professionalOfferingService;

    @Operation(summary = "Criar serviço", description = "Adiciona um serviço ao catálogo do profissional.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Serviço criado com sucesso",
                    headers = @Header(name = "Location", description = "URI do novo recurso", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalOfferingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional ou categoria não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<ProfessionalOfferingResponse> create(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId,
            @Valid @RequestBody CreateProfessionalOfferingRequest request
    ) {
        ProfessionalOfferingResponse response = professionalOfferingService.create(professionalId, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Listar serviços", description = "Retorna os serviços do profissional. Por padrão retorna apenas ativos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalOfferingResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ResponseEntity<Page<ProfessionalOfferingResponse>> findAll(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId,
            @Parameter(description = "Incluir inativos") @RequestParam(defaultValue = "false") boolean includeInactive,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(professionalOfferingService.findAllByProfessional(professionalId, includeInactive, pageable));
    }

    @Operation(summary = "Buscar serviço por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Serviço encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalOfferingResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Serviço não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProfessionalOfferingResponse> findById(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId,
            @Parameter(description = "ID do serviço", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(professionalOfferingService.findById(professionalId, id));
    }

    @Operation(summary = "Atualizar serviço", description = "Apenas os campos informados são alterados. Permitido ao próprio profissional ou admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Serviço atualizado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalOfferingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Serviço não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<ProfessionalOfferingResponse> update(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId,
            @Parameter(description = "ID do serviço", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateProfessionalOfferingRequest request
    ) {
        return ResponseEntity.ok(professionalOfferingService.update(professionalId, id, request));
    }

    @Operation(summary = "Remover serviço (soft delete)", description = "Preserva histórico de pedidos. Permitido ao próprio profissional ou admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Serviço removido com sucesso", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Serviço não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId,
            @Parameter(description = "ID do serviço", required = true) @PathVariable UUID id
    ) {
        professionalOfferingService.delete(professionalId, id);
        return ResponseEntity.noContent().build();
    }
}
