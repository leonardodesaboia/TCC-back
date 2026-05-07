package com.allset.api.dispute.controller;

import com.allset.api.dispute.domain.DisputeStatus;
import com.allset.api.dispute.dto.AddTextEvidenceRequest;
import com.allset.api.dispute.dto.DisputeEvidenceResponse;
import com.allset.api.dispute.dto.DisputeResponse;
import com.allset.api.dispute.dto.OpenDisputeRequest;
import com.allset.api.dispute.dto.ResolveDisputeRequest;
import com.allset.api.dispute.service.DisputeService;
import com.allset.api.shared.annotation.CurrentUser;
import com.allset.api.shared.exception.ApiError;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Disputas", description = "Abertura, resolucao e evidencias de disputas")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    // ─────────────────────────────────────────
    // Abertura (aninhado no order)
    // ─────────────────────────────────────────

    @Operation(
            summary = "Abrir disputa",
            description = "Cliente abre uma disputa para um pedido em status completed_by_pro "
                        + "dentro da janela de 24h apos pro_completed_at."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Disputa criada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DisputeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Status invalido, janela expirada ou dados invalidos",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas clientes podem abrir disputa", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido nao encontrado",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Ja existe uma disputa para este pedido",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/orders/{orderId}/disputes")
    @PreAuthorize("hasAuthority('client')")
    public ResponseEntity<DisputeResponse> openDispute(
            @Parameter(description = "ID do pedido", required = true) @PathVariable UUID orderId,
            @Valid @RequestBody OpenDisputeRequest request,
            @CurrentUser UUID currentUserId
    ) {
        DisputeResponse response = disputeService.openDispute(orderId, currentUserId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/disputes/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(
            summary = "Buscar disputa pelo pedido",
            description = "Busca a disputa associada a um pedido. Acessivel por cliente, profissional do pedido ou admin."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Disputa encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DisputeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido ou disputa nao encontrados",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/orders/{orderId}/disputes")
    public ResponseEntity<DisputeResponse> getByOrderId(
            @Parameter(description = "ID do pedido", required = true) @PathVariable UUID orderId,
            @CurrentUser UUID currentUserId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                disputeService.getByOrderId(orderId, currentUserId, resolveRole(authentication)));
    }

    // ─────────────────────────────────────────
    // CRUD de disputa
    // ─────────────────────────────────────────

    @Operation(
            summary = "Buscar disputa por ID",
            description = "Detalhes da disputa. Acessivel por cliente, profissional do pedido ou admin. "
                        + "Notas internas (adminNotes) sao expostas apenas para admins."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Disputa encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DisputeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Disputa nao encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/disputes/{id}")
    public ResponseEntity<DisputeResponse> getById(
            @Parameter(description = "ID da disputa", required = true) @PathVariable UUID id,
            @CurrentUser UUID currentUserId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                disputeService.getById(id, currentUserId, resolveRole(authentication)));
    }

    @Operation(
            summary = "Listar disputas (admin)",
            description = "Lista paginada de disputas com filtro opcional por status. Apenas administradores."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de disputas",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DisputeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas administradores", content = @Content)
    })
    @GetMapping("/disputes")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Page<DisputeResponse>> listAll(
            @Parameter(description = "Filtrar por status") @RequestParam(required = false) DisputeStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "openedAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(disputeService.listAll(status, pageable));
    }

    // ─────────────────────────────────────────
    // Acoes admin
    // ─────────────────────────────────────────

    @Operation(
            summary = "Marcar disputa como em analise",
            description = "Admin sinaliza que comecou a analisar a disputa. Permitido apenas a partir do status open."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Disputa em analise",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DisputeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Transicao invalida",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas administradores", content = @Content),
            @ApiResponse(responseCode = "404", description = "Disputa nao encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/disputes/{id}/under-review")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<DisputeResponse> markUnderReview(
            @Parameter(description = "ID da disputa", required = true) @PathVariable UUID id,
            @CurrentUser UUID currentUserId
    ) {
        return ResponseEntity.ok(disputeService.markUnderReview(id, currentUserId));
    }

    @Operation(
            summary = "Resolver disputa",
            description = "Admin define a resolucao final. Em refund_partial os valores devem somar exatamente "
                        + "o total do pedido. Em refund_full e release_to_pro os valores sao calculados automaticamente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Disputa resolvida",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DisputeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Valores invalidos ou disputa ja resolvida",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Apenas administradores", content = @Content),
            @ApiResponse(responseCode = "404", description = "Disputa nao encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/disputes/{id}/resolve")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<DisputeResponse> resolve(
            @Parameter(description = "ID da disputa", required = true) @PathVariable UUID id,
            @Valid @RequestBody ResolveDisputeRequest request,
            @CurrentUser UUID currentUserId
    ) {
        return ResponseEntity.ok(disputeService.resolve(id, currentUserId, request));
    }

    // ─────────────────────────────────────────
    // Evidencias
    // ─────────────────────────────────────────

    @Operation(
            summary = "Adicionar evidencia textual",
            description = "Cliente, profissional do pedido ou admin envia uma descricao textual como evidencia. "
                        + "Nao permitido apos a resolucao da disputa."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Evidencia registrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DisputeEvidenceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Disputa ja resolvida ou conteudo invalido",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Disputa nao encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/disputes/{id}/evidences")
    public ResponseEntity<DisputeEvidenceResponse> addTextEvidence(
            @Parameter(description = "ID da disputa", required = true) @PathVariable UUID id,
            @Valid @RequestBody AddTextEvidenceRequest request,
            @CurrentUser UUID currentUserId,
            Authentication authentication
    ) {
        DisputeEvidenceResponse response = disputeService.addTextEvidence(
                id, currentUserId, resolveRole(authentication), request);
        return ResponseEntity.status(201).body(response);
    }

    @Operation(
            summary = "Adicionar evidencia em foto",
            description = "Faz upload de uma imagem (JPEG/PNG) como evidencia. Caption opcional. "
                        + "Nao permitido apos a resolucao da disputa."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Foto enviada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DisputeEvidenceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Tipo de arquivo invalido ou disputa ja resolvida",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Disputa nao encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "413", description = "Arquivo excede o tamanho maximo permitido",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(value = "/disputes/{id}/evidences/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DisputeEvidenceResponse> addPhotoEvidence(
            @Parameter(description = "ID da disputa", required = true) @PathVariable UUID id,
            @Parameter(description = "Arquivo de imagem (JPEG/PNG)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Legenda opcional para a foto")
            @RequestPart(value = "caption", required = false) String caption,
            @CurrentUser UUID currentUserId,
            Authentication authentication
    ) {
        DisputeEvidenceResponse response = disputeService.addPhotoEvidence(
                id, currentUserId, resolveRole(authentication), file, caption);
        return ResponseEntity.status(201).body(response);
    }

    @Operation(
            summary = "Listar evidencias",
            description = "Lista todas as evidencias da disputa em ordem cronologica. "
                        + "Acessivel por cliente, profissional do pedido ou admin."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de evidencias",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DisputeEvidenceResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Disputa nao encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/disputes/{id}/evidences")
    public ResponseEntity<List<DisputeEvidenceResponse>> listEvidences(
            @Parameter(description = "ID da disputa", required = true) @PathVariable UUID id,
            @CurrentUser UUID currentUserId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                disputeService.listEvidences(id, currentUserId, resolveRole(authentication)));
    }

    // ─────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────

    private String resolveRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("client");
    }
}
