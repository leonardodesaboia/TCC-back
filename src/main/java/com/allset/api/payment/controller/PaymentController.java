package com.allset.api.payment.controller;

import com.allset.api.order.domain.Order;
import com.allset.api.order.exception.OrderNotFoundException;
import com.allset.api.order.repository.OrderRepository;
import com.allset.api.payment.dto.*;
import com.allset.api.payment.service.PaymentService;
import com.allset.api.shared.annotation.CurrentUser;
import com.allset.api.shared.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "Pagamentos", description = "Criação, consulta e gerenciamento de pagamentos")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    // ─────────────────────────────────────────
    // Criação
    // ─────────────────────────────────────────

    @Operation(
        summary = "Criar pagamento para pedido",
        description = "Cria uma cobrança no Asaas (PIX, cartão ou boleto) para o pedido aceito. "
                    + "O valor é retido em escrow até a conclusão do serviço."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cobrança criada com sucesso",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Pedido já possui pagamento ativo",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/orders/{orderId}/payment")
    @PreAuthorize("hasAuthority('client')")
    public ResponseEntity<PaymentResponse> createPayment(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreatePaymentRequest request,
            @CurrentUser UUID clientId
    ) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getClientId().equals(clientId)) {
            throw new OrderNotFoundException(orderId);
        }

        PaymentResponse response = paymentService.createPaymentForOrder(order, request.method());

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/payments/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    // ─────────────────────────────────────────
    // Consultas
    // ─────────────────────────────────────────

    @Operation(summary = "Consultar pagamento do pedido")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagamento encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Pagamento não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/orders/{orderId}/payment")
    public ResponseEntity<PaymentResponse> getByOrderId(
            @PathVariable UUID orderId,
            @CurrentUser UUID userId
    ) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId, userId));
    }

    @Operation(summary = "Consultar pagamento por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagamento encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Pagamento não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/payments/{id}")
    public ResponseEntity<PaymentResponse> getById(
            @PathVariable UUID id,
            @CurrentUser UUID userId
    ) {
        return ResponseEntity.ok(paymentService.getPaymentById(id, userId));
    }

    @Operation(
        summary = "Listar pagamentos (admin)",
        description = "Lista todos os pagamentos com paginação. Acesso restrito a administradores."
    )
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Page<PaymentResponse>> list(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(paymentService.listPayments(pageable));
    }

    // ─────────────────────────────────────────
    // Admin — operações manuais (pós-disputa)
    // ─────────────────────────────────────────

    @Operation(
        summary = "Liberar escrow manualmente (admin)",
        description = "Libera o valor retido em escrow ao profissional. "
                    + "Usado após resolução de disputa a favor do profissional."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Escrow liberado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Status não permite liberação",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Pagamento não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/payments/{id}/release")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Void> adminRelease(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminReleaseRequest request,
            @CurrentUser UUID adminId
    ) {
        String reason = request != null ? request.reason() : null;
        paymentService.adminRelease(id, reason, adminId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Reembolsar manualmente (admin)",
        description = "Reembolsa o valor ao cliente (total ou parcial). "
                    + "Usado após resolução de disputa a favor do cliente."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Reembolso processado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Status não permite reembolso ou dados inválidos",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Pagamento não encontrado",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/payments/{id}/refund")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Void> adminRefund(
            @PathVariable UUID id,
            @Valid @RequestBody AdminRefundRequest request,
            @CurrentUser UUID adminId
    ) {
        paymentService.adminRefund(id, request.amount(), request.reason(), adminId);
        return ResponseEntity.noContent().build();
    }
}
