package com.allset.api.address.controller;

import com.allset.api.address.dto.CreateSavedAddressRequest;
import com.allset.api.address.dto.SavedAddressResponse;
import com.allset.api.address.dto.UpdateSavedAddressRequest;
import com.allset.api.address.service.SavedAddressService;
import com.allset.api.shared.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

@Tag(name = "Endereços", description = "Gerenciamento de endereços salvos do usuário (RF-48)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users/{userId}/addresses")
@RequiredArgsConstructor
public class SavedAddressController {

    private final SavedAddressService savedAddressService;

    // -------------------------------------------------------------------------
    // POST /api/users/{userId}/addresses
    // -------------------------------------------------------------------------

    @Operation(
        summary = "Criar endereço",
        description = "Cria um novo endereço para o usuário. Se `isDefault=true`, todos os demais endereços do usuário são desmarcados como padrão."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Endereço criado com sucesso",
            headers = @Header(name = "Location", description = "URI do novo recurso", schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SavedAddressResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('admin') or #userId.toString() == authentication.name")
    public ResponseEntity<SavedAddressResponse> create(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID userId,
        @Valid @RequestBody CreateSavedAddressRequest request
    ) {
        SavedAddressResponse response = savedAddressService.create(userId, request);
        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();
        return ResponseEntity.created(location).body(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/users/{userId}/addresses
    // -------------------------------------------------------------------------

    @Operation(
        summary = "Listar endereços",
        description = "Retorna todos os endereços salvos do usuário."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Lista retornada com sucesso",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = SavedAddressResponse.class)))
        ),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('admin') or #userId.toString() == authentication.name")
    public ResponseEntity<List<SavedAddressResponse>> findAll(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(savedAddressService.findAllByUser(userId));
    }

    // -------------------------------------------------------------------------
    // GET /api/users/{userId}/addresses/{id}
    // -------------------------------------------------------------------------

    @Operation(
        summary = "Buscar endereço por ID",
        description = "Retorna um endereço específico do usuário pelo ID."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Endereço encontrado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SavedAddressResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
        @ApiResponse(responseCode = "404", description = "Endereço não encontrado ou não pertence ao usuário",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('admin') or #userId.toString() == authentication.name")
    public ResponseEntity<SavedAddressResponse> findById(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID userId,
        @Parameter(description = "ID do endereço", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(savedAddressService.findByIdAndUser(userId, id));
    }

    // -------------------------------------------------------------------------
    // PUT /api/users/{userId}/addresses/{id}
    // -------------------------------------------------------------------------

    @Operation(
        summary = "Atualizar endereço",
        description = "Atualiza os campos informados no endereço. Apenas campos não nulos são alterados. " +
                      "Se `isDefault=true`, todos os demais endereços do usuário são desmarcados."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Endereço atualizado com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SavedAddressResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
        @ApiResponse(responseCode = "404", description = "Endereço não encontrado ou não pertence ao usuário",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('admin') or #userId.toString() == authentication.name")
    public ResponseEntity<SavedAddressResponse> update(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID userId,
        @Parameter(description = "ID do endereço", required = true) @PathVariable UUID id,
        @Valid @RequestBody UpdateSavedAddressRequest request
    ) {
        return ResponseEntity.ok(savedAddressService.update(userId, id, request));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/users/{userId}/addresses/{id}
    // -------------------------------------------------------------------------

    @Operation(
        summary = "Deletar endereço",
        description = "Remove permanentemente o endereço (exclusão física, sem período de graça)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Endereço removido com sucesso", content = @Content),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
        @ApiResponse(responseCode = "404", description = "Endereço não encontrado ou não pertence ao usuário",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('admin') or #userId.toString() == authentication.name")
    public ResponseEntity<Void> delete(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID userId,
        @Parameter(description = "ID do endereço", required = true) @PathVariable UUID id
    ) {
        savedAddressService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // PATCH /api/users/{userId}/addresses/{id}/set-default
    // -------------------------------------------------------------------------

    @Operation(
        summary = "Definir endereço padrão",
        description = "Marca o endereço como padrão do usuário e desmarca todos os outros."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Endereço definido como padrão",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SavedAddressResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
        @ApiResponse(responseCode = "404", description = "Endereço não encontrado ou não pertence ao usuário",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{id}/set-default")
    @PreAuthorize("hasAuthority('admin') or #userId.toString() == authentication.name")
    public ResponseEntity<SavedAddressResponse> setDefault(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID userId,
        @Parameter(description = "ID do endereço", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(savedAddressService.setDefault(userId, id));
    }
}
