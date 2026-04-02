package com.allset.api.catalog.controller;

import com.allset.api.catalog.dto.CreateServiceCategoryRequest;
import com.allset.api.catalog.dto.ServiceCategoryResponse;
import com.allset.api.catalog.dto.UpdateServiceCategoryRequest;
import com.allset.api.catalog.service.ServiceCategoryService;
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

@Tag(name = "Catálogo — Categorias", description = "Gerenciamento de categorias de serviço (nível 2 do catálogo)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/service-categories")
@RequiredArgsConstructor
public class ServiceCategoryController {

    private final ServiceCategoryService serviceCategoryService;

    @Operation(summary = "Criar categoria de serviço", description = "Cria uma nova categoria vinculada a uma área. Exclusivo para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria criada com sucesso",
                    headers = @Header(name = "Location", description = "URI do novo recurso", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ServiceCategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "Área não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    // @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<ServiceCategoryResponse> create(@Valid @RequestBody CreateServiceCategoryRequest request) {
        ServiceCategoryResponse response = serviceCategoryService.create(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Listar categorias", description = "Retorna categorias paginadas. Use `?areaId=` para filtrar por área.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ServiceCategoryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content)
    })
    @GetMapping
    public ResponseEntity<Page<ServiceCategoryResponse>> findAll(
            @Parameter(description = "Filtrar por área") @RequestParam(required = false) UUID areaId,
            @Parameter(description = "Incluir inativas") @RequestParam(defaultValue = "false") boolean includeInactive,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return ResponseEntity.ok(serviceCategoryService.findAll(areaId, includeInactive, pageable));
    }

    @Operation(summary = "Buscar categoria por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ServiceCategoryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ServiceCategoryResponse> findById(
            @Parameter(description = "ID da categoria", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(serviceCategoryService.findById(id));
    }

    @Operation(summary = "Atualizar categoria", description = "Exclusivo para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria atualizada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ServiceCategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    // @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<ServiceCategoryResponse> update(
            @Parameter(description = "ID da categoria", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceCategoryRequest request
    ) {
        return ResponseEntity.ok(serviceCategoryService.update(id, request));
    }

    @Operation(summary = "Remover categoria (soft delete)", description = "Exclusivo para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Categoria removida com sucesso", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    // TODO: mapear restrição de role — descomentar e ajustar quando o mapeamento de roles estiver definido
    // @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID da categoria", required = true) @PathVariable UUID id
    ) {
        serviceCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
