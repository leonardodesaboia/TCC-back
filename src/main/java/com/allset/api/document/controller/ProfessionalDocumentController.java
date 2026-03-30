package com.allset.api.document.controller;

import com.allset.api.document.dto.CreateProfessionalDocumentRequest;
import com.allset.api.document.dto.ProfessionalDocumentResponse;
import com.allset.api.document.service.ProfessionalDocumentService;
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

@Tag(name = "Profissionais — Documentos", description = "Gerenciamento de documentos de KYC do profissional")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/professionals/{professionalId}/documents")
@RequiredArgsConstructor
public class ProfessionalDocumentController {

    private final ProfessionalDocumentService professionalDocumentService;

    @Operation(summary = "Enviar documento", description = "Adiciona um documento de identificação ao perfil do profissional.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Documento enviado com sucesso",
                    headers = @Header(name = "Location", description = "URI do novo recurso", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalDocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<ProfessionalDocumentResponse> create(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId,
            @Valid @RequestBody CreateProfessionalDocumentRequest request
    ) {
        ProfessionalDocumentResponse response = professionalDocumentService.create(professionalId, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Listar documentos", description = "Retorna todos os documentos do profissional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfessionalDocumentResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<List<ProfessionalDocumentResponse>> findAll(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId
    ) {
        return ResponseEntity.ok(professionalDocumentService.findAllByProfessional(professionalId));
    }

    @Operation(summary = "Remover documento", description = "Remove fisicamente um documento do profissional.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Documento removido com sucesso", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Documento não encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('admin') or @professionalAuthHelper.isOwner(#professionalId, authentication)")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do perfil profissional", required = true) @PathVariable UUID professionalId,
            @Parameter(description = "ID do documento", required = true) @PathVariable UUID id
    ) {
        professionalDocumentService.delete(professionalId, id);
        return ResponseEntity.noContent().build();
    }
}
