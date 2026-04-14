package com.allset.api.integration.asaas;

import com.allset.api.config.AppProperties;
import com.allset.api.integration.asaas.dto.*;
import com.allset.api.payment.exception.PaymentProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Slf4j
@Component
public class AsaasClient {

    private final RestClient restClient;

    public AsaasClient(AppProperties appProperties) {
        this.restClient = RestClient.builder()
                .baseUrl(appProperties.asaasBaseUrl())
                .defaultHeader("access_token", appProperties.asaasApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Cria ou busca cliente no Asaas.
     */
    public AsaasCustomerResponse createCustomer(String name, String cpf, String email) {
        var request = new AsaasCreateCustomerRequest(name, cpf, email);
        try {
            return restClient.post()
                    .uri("/customers")
                    .body(request)
                    .retrieve()
                    .body(AsaasCustomerResponse.class);
        } catch (RestClientException e) {
            log.error("event=asaas_create_customer_error email={} error={}", email, e.getMessage(), e);
            throw new PaymentProcessingException("Falha ao criar cliente no Asaas", e);
        }
    }

    /**
     * Cria cobrança (PIX, cartão ou boleto).
     */
    public AsaasChargeResponse createCharge(String customerId, BigDecimal amount,
                                             String billingType, String description,
                                             String externalReference) {
        var request = new AsaasCreateChargeRequest(customerId, billingType, amount, description, externalReference);
        try {
            return restClient.post()
                    .uri("/payments")
                    .body(request)
                    .retrieve()
                    .body(AsaasChargeResponse.class);
        } catch (RestClientException e) {
            log.error("event=asaas_create_charge_error customerId={} amount={} error={}",
                    customerId, amount, e.getMessage(), e);
            throw new PaymentProcessingException("Falha ao criar cobrança no Asaas", e);
        }
    }

    /**
     * Consulta status da cobrança.
     */
    public AsaasChargeResponse getCharge(String asaasPaymentId) {
        try {
            return restClient.get()
                    .uri("/payments/{id}", asaasPaymentId)
                    .retrieve()
                    .body(AsaasChargeResponse.class);
        } catch (RestClientException e) {
            log.error("event=asaas_get_charge_error asaasPaymentId={} error={}",
                    asaasPaymentId, e.getMessage(), e);
            throw new PaymentProcessingException("Falha ao consultar cobrança no Asaas", e);
        }
    }

    /**
     * Transfere valor ao profissional (liberação do escrow).
     */
    public AsaasTransferResponse createTransfer(String walletId, BigDecimal amount) {
        var request = new AsaasTransferRequest(walletId, amount);
        try {
            return restClient.post()
                    .uri("/transfers")
                    .body(request)
                    .retrieve()
                    .body(AsaasTransferResponse.class);
        } catch (RestClientException e) {
            log.error("event=asaas_create_transfer_error walletId={} amount={} error={}",
                    walletId, amount, e.getMessage(), e);
            throw new PaymentProcessingException("Falha ao transferir valor no Asaas", e);
        }
    }

    /**
     * Estorna cobrança (total ou parcial).
     */
    public void refundCharge(String asaasPaymentId, BigDecimal amount, String description) {
        var request = new AsaasRefundRequest(amount, description);
        try {
            restClient.post()
                    .uri("/payments/{id}/refund", asaasPaymentId)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("event=asaas_refund_error asaasPaymentId={} amount={} error={}",
                    asaasPaymentId, amount, e.getMessage(), e);
            throw new PaymentProcessingException("Falha ao estornar cobrança no Asaas", e);
        }
    }

    /**
     * Cancela cobrança pendente.
     */
    public void cancelCharge(String asaasPaymentId) {
        try {
            restClient.delete()
                    .uri("/payments/{id}", asaasPaymentId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("event=asaas_cancel_error asaasPaymentId={} error={}",
                    asaasPaymentId, e.getMessage(), e);
            throw new PaymentProcessingException("Falha ao cancelar cobrança no Asaas", e);
        }
    }
}
