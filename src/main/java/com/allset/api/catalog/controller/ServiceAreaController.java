package com.allset.api.catalog.controller;

import com.allset.api.catalog.dto.CreateServiceAreaRequest;
import com.allset.api.catalog.dto.ServiceAreaResponse;
import com.allset.api.catalog.dto.UpdateServiceAreaRequest;
import com.allset.api.catalog.service.ServiceAreaService;
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

@Tag(name = "Catálogo — Áreas", description = "Gerenciamento de áreas de serviço (nível 1 do catálogo)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/service-areas")
@RequiredArgsConstructor
public class ServiceAreaController {

    private final ServiceAreaService serviceAreaService;

    @Operation(summary = "Criar área de serviço", description = "Cria uma nova área de serviço. Exclusivo para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Área criada com sucesso",
                    headers = @Header(name = "Location", description = "URI do novo recurso", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ServiceAreaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content),
            @ApiResponse(responseCode = "409", description = "Nome já cadastrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<ServiceAreaResponse> create(@Valid @RequestBody CreateServiceAreaRequest request) {
        ServiceAreaResponse response = serviceAreaService.create(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Listar áreas de serviço", description = "Retorna áreas paginadas. Por padrão retorna apenas ativas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ServiceAreaResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content)
    })
    @GetMapping
    public ResponseEntity<Page<ServiceAreaResponse>> findAll(
            @Parameter(description = "Incluir inativas") @RequestParam(defaultValue = "false") boolean includeInactive,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return ResponseEntity.ok(serviceAreaService.findAll(includeInactive, pageable));
    }

    @Operation(summary = "Buscar área por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Área encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ServiceAreaResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Área não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ServiceAreaResponse> findById(
            @Parameter(description = "ID da área", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(serviceAreaService.findById(id));
    }

    @Operation(summary = "Atualizar área de serviço", description = "Atualiza dados da área. Exclusivo para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Área atualizada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ServiceAreaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "Área não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Nome já cadastrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<ServiceAreaResponse> update(
            @Parameter(description = "ID da área", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceAreaRequest request
    ) {
        return ResponseEntity.ok(serviceAreaService.update(id, request));
    }

    @Operation(summary = "Remover área de serviço (soft delete)", description = "Exclusivo para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Área removida com sucesso", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "Área não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID da área", required = true) @PathVariable UUID id
    ) {
        serviceAreaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
