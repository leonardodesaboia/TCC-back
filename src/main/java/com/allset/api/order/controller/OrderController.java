package com.allset.api.order.controller;

import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.domain.PhotoType;
import com.allset.api.order.dto.*;
import com.allset.api.order.service.OrderService;
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
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.repository.ProfessionalRepository;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

@Tag(name = "Pedidos", description = "Criação e gerenciamento de pedidos Express")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ProfessionalRepository professionalRepository;

    // ─────────────────────────────────────────
    // Criação
    // ─────────────────────────────────────────

    @Operation(
        summary = "Criar pedido Express",
        description = "Cria um pedido Express e monta a fila de profissionais disponíveis "
                    + "no raio de 15km, ordenados por proximidade (com prioridade para assinantes Pro)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pedido criado com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos ou endereço sem coordenadas",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "422", description = "Nenhum profissional disponível na região",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/express")
    @PreAuthorize("hasAuthority('client')")
    public ResponseEntity<OrderResponse> createExpress(
            @Valid @RequestBody CreateExpressOrderRequest request,
            @CurrentUser UUID clientId
    ) {
        OrderResponse response = orderService.createExpressOrder(clientId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/orders/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(
        summary = "Criar pedido On Demand",
        description = "Cria um pedido On Demand a partir de um serviço publicado por um profissional. "
                    + "O pedido vai direto para o profissional dono do serviço."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pedido criado com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos, serviço inativo ou profissional não aprovado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/on-demand")
    @PreAuthorize("hasAuthority('client')")
    public ResponseEntity<OrderResponse> createOnDemand(
            @Valid @RequestBody CreateOnDemandOrderRequest request,
            @CurrentUser UUID clientId
    ) {
        OrderResponse response = orderService.createOnDemandOrder(clientId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/orders/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    // ─────────────────────────────────────────
    // Leitura
    // ─────────────────────────────────────────

    @Operation(summary = "Buscar pedido por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pedido encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado ou sem permissão",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(
            @PathVariable UUID id,
            @CurrentUser UUID requesterId,
            Authentication authentication
    ) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("client");
        return ResponseEntity.ok(orderService.getOrder(id, requesterId, role));
    }

    @Operation(
        summary = "Listar meus pedidos",
        description = "Clientes veem os pedidos que criaram. Profissionais veem os pedidos atribuídos a eles."
    )
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> list(
            @Parameter(description = "Filtrar por status") @RequestParam(required = false) OrderStatus status,
            @CurrentUser UUID userId,
            Authentication authentication,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("client");
        return ResponseEntity.ok(orderService.listOrders(userId, role, status, pageable));
    }

    // ─────────────────────────────────────────
    // Express — propostas recebidas
    // ─────────────────────────────────────────

    @Operation(
        summary = "Listar propostas recebidas (Express)",
        description = "Retorna todas as propostas (ACCEPTED) enviadas por profissionais para o pedido Express. "
                    + "Acessível apenas pelo cliente dono do pedido ou admin."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de propostas",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExpressProposalResponse.class))),
        @ApiResponse(responseCode = "403", description = "Sem permissão para visualizar as propostas",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}/express/proposals")
    @PreAuthorize("hasAnyAuthority('client', 'admin')")
    public ResponseEntity<List<ExpressProposalResponse>> getProposals(
            @PathVariable UUID id,
            @CurrentUser UUID requesterId,
            Authentication authentication
    ) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("client");
        return ResponseEntity.ok(orderService.getProposals(id, requesterId, role));
    }

    // ─────────────────────────────────────────
    // Express — resposta do profissional
    // ─────────────────────────────────────────

    @Operation(
        summary = "Resposta do profissional (Express)",
        description = "Profissional aceita (informando preço) ou recusa o pedido Express. "
                    + "Qualquer profissional da fila pode responder enquanto o pedido está PENDING."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resposta registrada",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos ou valor ausente ao aceitar",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/express/pro-respond")
    @PreAuthorize("hasAuthority('professional')")
    public ResponseEntity<OrderResponse> proRespond(
            @PathVariable UUID id,
            @Valid @RequestBody ProRespondRequest request,
            @CurrentUser UUID userId
    ) {
        // Precisamos do professionalId a partir do userId
        UUID professionalId = resolveProfessionalId(userId);
        return ResponseEntity.ok(orderService.proRespond(id, professionalId, request));
    }

    // ─────────────────────────────────────────
    // On Demand — resposta do profissional
    // ─────────────────────────────────────────

    @Operation(
        summary = "Profissional aceita ou recusa pedido On Demand",
        description = "Profissional aceita ou recusa um pedido On Demand pendente."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resposta registrada",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Status não permite resposta",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/on-demand/respond")
    @PreAuthorize("hasAuthority('professional')")
    public ResponseEntity<OrderResponse> respondOnDemand(
            @PathVariable UUID id,
            @RequestParam boolean accepted,
            @CurrentUser UUID userId
    ) {
        UUID professionalId = resolveProfessionalId(userId);
        return ResponseEntity.ok(orderService.respondOnDemand(id, professionalId, accepted));
    }

    // ─────────────────────────────────────────
    // Express — resposta do cliente
    // ─────────────────────────────────────────

    @Operation(
        summary = "Cliente escolhe proposta (Express)",
        description = "Cliente seleciona qual proposta aceitar informando o ID do profissional escolhido. "
                    + "As demais propostas são recusadas automaticamente. "
                    + "O pedido avança para IN_PROGRESS e o escrow é iniciado."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proposta aceita e pedido iniciado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Profissional não tem proposta aceita para este pedido",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/express/client-respond")
    @PreAuthorize("hasAuthority('client')")
    public ResponseEntity<OrderResponse> clientRespond(
            @PathVariable UUID id,
            @Valid @RequestBody ClientRespondRequest request,
            @CurrentUser UUID clientId
    ) {
        return ResponseEntity.ok(orderService.clientRespond(id, clientId, request));
    }

    // ─────────────────────────────────────────
    // Conclusão
    // ─────────────────────────────────────────

    @Operation(
        summary = "Profissional marca como concluído",
        description = "Profissional sinaliza que o serviço foi executado e envia foto comprobatória. "
                    + "Aguarda confirmação do cliente."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status atualizado para completed_by_pro",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Status inválido para esta operação",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(value = "/{id}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('professional')")
    public ResponseEntity<OrderResponse> completeByPro(
            @PathVariable UUID id,
            @Parameter(description = "Foto comprobatória (JPEG/PNG)", required = true)
            @RequestPart("file") MultipartFile file,
            @CurrentUser UUID userId
    ) {
        UUID professionalId = resolveProfessionalId(userId);
        return ResponseEntity.ok(orderService.completeByPro(id, professionalId, file));
    }

    @Operation(
        summary = "Enviar foto do pedido",
        description = "Adiciona uma foto vinculada ao pedido (request, completion_proof, etc.). "
                    + "Permitido ao cliente dono, profissional do pedido ou admin."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Foto enviada com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderPhotoResponse.class))),
        @ApiResponse(responseCode = "400", description = "Tipo de arquivo inválido",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado ou sem permissão",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "413", description = "Arquivo excede o tamanho máximo permitido",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('client', 'professional', 'admin')")
    public ResponseEntity<OrderPhotoResponse> uploadPhoto(
            @PathVariable UUID id,
            @Parameter(description = "Tipo da foto", required = true) @RequestParam("type") PhotoType type,
            @Parameter(description = "Arquivo de imagem (JPEG/PNG)", required = true)
            @RequestPart("file") MultipartFile file,
            @CurrentUser UUID userId,
            Authentication authentication
    ) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("client");
        OrderPhotoResponse response = orderService.uploadPhoto(id, userId, role, type, file);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/orders/{orderId}/photos/{photoId}")
                .buildAndExpand(id, response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(
        summary = "Cliente confirma conclusão",
        description = "Cliente confirma que o serviço foi concluído corretamente. "
                    + "O escrow é liberado ao profissional (- 20% de taxa da plataforma)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pedido concluído e escrow liberado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Status inválido para esta operação",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('client')")
    public ResponseEntity<OrderResponse> confirmCompletion(
            @PathVariable UUID id,
            @CurrentUser UUID clientId
    ) {
        return ResponseEntity.ok(orderService.confirmCompletion(id, clientId));
    }

    // ─────────────────────────────────────────
    // Cancelamento
    // ─────────────────────────────────────────

    @Operation(
        summary = "Cancelar pedido",
        description = "Cliente ou profissional cancela o pedido. "
                    + "Não permitido após completed, cancelled ou disputed."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pedido cancelado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Status não permite cancelamento",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('client', 'professional')")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelOrderRequest request,
            @CurrentUser UUID requesterId
    ) {
        return ResponseEntity.ok(orderService.cancelOrder(id, requesterId, request));
    }

    // ─────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────

    /**
     * Resolve o ID do perfil profissional a partir do userId.
     * O professional_id é necessário para operar sobre pedidos.
     */
    private UUID resolveProfessionalId(UUID userId) {
        // Importação lazy para evitar dependência circular entre módulos
        // O repositório é injetado via construtor no campo abaixo
        return professionalRepository.findByUserIdAndDeletedAtIsNull(userId)
                .map(p -> p.getId())
                .orElseThrow(() -> new ProfessionalNotFoundException(userId));
    }

}
