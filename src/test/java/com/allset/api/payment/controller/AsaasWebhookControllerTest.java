package com.allset.api.payment.controller;

import com.allset.api.config.AppProperties;
import com.allset.api.integration.asaas.dto.AsaasWebhookEvent;
import com.allset.api.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.allset.api.payment.dto.WebhookAckResponse;
import com.allset.api.payment.exception.InvalidWebhookSignatureException;

@ExtendWith(MockitoExtension.class)
class AsaasWebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private AsaasWebhookController webhookController;

    @Test
    void shouldProcessValidWebhookEvent() {
        when(appProperties.asaasWebhookToken()).thenReturn("valid-token");

        AsaasWebhookEvent event = new AsaasWebhookEvent("PAYMENT_CONFIRMED",
                new AsaasWebhookEvent.PaymentPayload("pay_123", "CONFIRMED", new BigDecimal("200.00"), null));

        ResponseEntity<WebhookAckResponse> response = webhookController.handleAsaasWebhook("valid-token", event);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().received()).isTrue();

        ArgumentCaptor<AsaasWebhookEvent> captor = ArgumentCaptor.forClass(AsaasWebhookEvent.class);
        verify(paymentService).handleWebhookEvent(captor.capture());
        assertThat(captor.getValue().event()).isEqualTo("PAYMENT_CONFIRMED");
    }

    @Test
    void shouldRejectWebhookWithInvalidToken() {
        when(appProperties.asaasWebhookToken()).thenReturn("valid-token");

        AsaasWebhookEvent event = new AsaasWebhookEvent("PAYMENT_CONFIRMED",
                new AsaasWebhookEvent.PaymentPayload("pay_123", "CONFIRMED", new BigDecimal("200.00"), null));

        assertThatThrownBy(() -> webhookController.handleAsaasWebhook("wrong-token", event))
                .isInstanceOf(InvalidWebhookSignatureException.class);

        verify(paymentService, never()).handleWebhookEvent(any());
    }

    @Test
    void shouldRejectWebhookWithMissingToken() {
        when(appProperties.asaasWebhookToken()).thenReturn("valid-token");

        AsaasWebhookEvent event = new AsaasWebhookEvent("PAYMENT_CONFIRMED",
                new AsaasWebhookEvent.PaymentPayload("pay_123", "CONFIRMED", new BigDecimal("200.00"), null));

        assertThatThrownBy(() -> webhookController.handleAsaasWebhook(null, event))
                .isInstanceOf(InvalidWebhookSignatureException.class);

        verify(paymentService, never()).handleWebhookEvent(any());
    }

    @Test
    void shouldReturn200EvenWhenServiceThrowsException() {
        when(appProperties.asaasWebhookToken()).thenReturn("valid-token");
        doThrow(new RuntimeException("Erro interno")).when(paymentService).handleWebhookEvent(any());

        AsaasWebhookEvent event = new AsaasWebhookEvent("PAYMENT_CONFIRMED",
                new AsaasWebhookEvent.PaymentPayload("pay_123", "CONFIRMED", new BigDecimal("200.00"), null));

        ResponseEntity<WebhookAckResponse> response = webhookController.handleAsaasWebhook("valid-token", event);

        // Retorna 200 mesmo com erro para evitar retries infinitos do Asaas
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().received()).isTrue();
    }
}
