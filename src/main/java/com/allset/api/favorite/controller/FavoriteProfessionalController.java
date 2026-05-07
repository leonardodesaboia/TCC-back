package com.allset.api.favorite.controller;

import com.allset.api.favorite.dto.FavoriteProfessionalResponse;
import com.allset.api.favorite.dto.FavoriteStatusResponse;
import com.allset.api.favorite.service.FavoriteProfessionalService;
import com.allset.api.shared.annotation.CurrentUser;
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
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Profissionais favoritos", description = "Favoritos do cliente autenticado")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FavoriteProfessionalController {

    private final FavoriteProfessionalService favoriteProfessionalService;

    @Operation(summary = "Favoritar profissional")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Profissional favoritado",
                    headers = @Header(name = "Location", description = "URI do favorito criado",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FavoriteProfessionalResponse.class))),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional nao encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Profissional ja favoritado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/professionals/{professionalId}/favorite")
    @PreAuthorize("hasAuthority('client')")
    public ResponseEntity<FavoriteProfessionalResponse> favorite(
            @CurrentUser UUID currentUserId,
            @Parameter(description = "ID do profissional", required = true) @PathVariable UUID professionalId
    ) {
        FavoriteProfessionalResponse response = favoriteProfessionalService.favorite(currentUserId, professionalId);
        URI location = ServletUriComponentsBuilder
                .fromPath("/api/v1/favorite-professionals/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Listar profissionais favoritos")
    @ApiResponse(responseCode = "200", description = "Favoritos retornados com sucesso")
    @GetMapping("/favorite-professionals")
    @PreAuthorize("hasAuthority('client')")
    public ResponseEntity<Page<FavoriteProfessionalResponse>> list(
            @CurrentUser UUID currentUserId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(favoriteProfessionalService.list(currentUserId, pageable));
    }

    @Operation(summary = "Consultar se o profissional esta favoritado")
    @ApiResponse(responseCode = "200", description = "Status retornado com sucesso")
    @GetMapping("/professionals/{professionalId}/favorite")
    @PreAuthorize("hasAuthority('client')")
    public ResponseEntity<FavoriteStatusResponse> status(
            @CurrentUser UUID currentUserId,
            @Parameter(description = "ID do profissional", required = true) @PathVariable UUID professionalId
    ) {
        return ResponseEntity.ok(favoriteProfessionalService.status(currentUserId, professionalId));
    }

    @Operation(summary = "Remover profissional dos favoritos")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Favorito removido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Favorito ou profissional nao encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/professionals/{professionalId}/favorite")
    @PreAuthorize("hasAuthority('client')")
    public ResponseEntity<Void> unfavorite(
            @CurrentUser UUID currentUserId,
            @Parameter(description = "ID do profissional", required = true) @PathVariable UUID professionalId
    ) {
        favoriteProfessionalService.unfavorite(currentUserId, professionalId);
        return ResponseEntity.noContent().build();
    }
}
