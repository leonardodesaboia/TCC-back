package com.allset.api.professional.controller;

import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.dto.*;
import com.allset.api.professional.service.ProfessionalService;
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

@Tag(name = "Profissionais", description = "Gerenciamento de perfis de profissionais")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/professionals")
@RequiredArgsConstructor
public class ProfessionalController {

    private final ProfessionalService professionalService;

    @Operation(summary = "Criar perfil profissional", description = "Cria o perfil profissional vinculado a um usuário existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Perfil criado com sucesso",
                    headers = @Header(name = "Location", description = "URI do novo recurso", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Usuário já possui perfil profissional",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('admin') or #request.userId().toString() == authentication.name")
    public ResponseEntity<ProfessionalResponse> create(@Valid @RequestBody CreateProfessionalRequest request) {
        ProfessionalResponse response = professionalService.create(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Listar profissionais", description = "Retorna profissionais paginados. Use `?status=` para filtrar por verificação ou `?geoActive=true` para disponíveis no Express.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content)
    })
    @GetMapping
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Page<ProfessionalResponse>> findAll(
            @Parameter(description = "Filtrar por status de verificação") @RequestParam(required = false) VerificationStatus status,
            @Parameter(description = "Filtrar disponíveis no Express") @RequestParam(defaultValue = "false") boolean geoActive,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(professionalService.findAll(status, geoActive, pageable));
    }

    @Operation(summary = "Buscar profissional por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profissional encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    public ResponseEntity<ProfessionalResponse> findById(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(professionalService.findById(id));
    }

    @Operation(summary = "Atualizar perfil profissional", description = "Atualiza bio, experiência e taxa horária. Permitido ao próprio profissional ou admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#id, authentication)")
    public ResponseEntity<ProfessionalResponse> update(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateProfessionalRequest request
    ) {
        return ResponseEntity.ok(professionalService.update(id, request));
    }

    @Operation(summary = "Atualizar geolocalização", description = "Atualiza posição GPS e disponibilidade Express. Permitido ao próprio profissional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Geolocalização atualizada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{id}/geo")
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#id, authentication)")
    public ResponseEntity<ProfessionalResponse> updateGeo(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateGeoRequest request
    ) {
        return ResponseEntity.ok(professionalService.updateGeo(id, request));
    }

    @Operation(summary = "Verificar profissional (KYC)", description = "Aprova ou rejeita a verificação de identidade. Exclusivo para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status de verificação atualizado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou motivo ausente para rejeição",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{id}/verify")
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<ProfessionalResponse> verify(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID id,
            @Valid @RequestBody VerifyProfessionalRequest request
    ) {
        return ResponseEntity.ok(professionalService.verify(id, request));
    }

    @Operation(summary = "Remover perfil profissional (soft delete)", description = "Permitido ao próprio profissional ou admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Perfil removido com sucesso", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#id, authentication)")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID id
    ) {
        professionalService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
