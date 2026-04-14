package com.allset.api.payment.controller;

import com.allset.api.config.AppProperties;
import com.allset.api.integration.asaas.dto.AsaasWebhookEvent;
import com.allset.api.payment.dto.WebhookAckResponse;
import com.allset.api.payment.exception.InvalidWebhookSignatureException;
import com.allset.api.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Tag(name = "Webhook Asaas", description = "Recebe eventos de pagamento do Asaas")
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
public class AsaasWebhookController {

    private final PaymentService paymentService;
    private final AppProperties appProperties;

    @Operation(
        summary = "Webhook Asaas",
        description = "Endpoint público autenticado via token no header. "
                    + "Recebe eventos de pagamento (PAYMENT_CONFIRMED, PAYMENT_REFUNDED, etc)."
    )
    @ApiResponse(responseCode = "200", description = "Evento recebido")
    @PostMapping("/asaas")
    public ResponseEntity<WebhookAckResponse> handleAsaasWebhook(
            @RequestHeader(value = "asaas-access-token", required = false) String webhookToken,
            @RequestBody AsaasWebhookEvent event
    ) {
        // Valida token do webhook (constant-time comparison para evitar timing attacks)
        if (webhookToken == null || !MessageDigest.isEqual(
                webhookToken.getBytes(StandardCharsets.UTF_8),
                appProperties.asaasWebhookToken().getBytes(StandardCharsets.UTF_8))) {
            log.warn("event=webhook_invalid_token receivedToken={}", webhookToken != null ? "***" : "null");
            throw new InvalidWebhookSignatureException();
        }

        try {
            paymentService.handleWebhookEvent(event);
        } catch (Exception e) {
            // Retorna 200 mesmo em caso de erro interno para evitar retries infinitos do Asaas
            log.error("event=webhook_processing_error eventType={} error={}",
                    event != null ? event.event() : "null", e.getMessage(), e);
        }

        return ResponseEntity.ok(new WebhookAckResponse(true));
    }
}
