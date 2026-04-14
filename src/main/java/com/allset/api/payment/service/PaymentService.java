package com.allset.api.payment.service;

import com.allset.api.integration.asaas.dto.AsaasWebhookEvent;
import com.allset.api.order.domain.Order;
import com.allset.api.payment.domain.PaymentMethod;
import com.allset.api.payment.dto.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentService {

    /** Cria Payment + Transaction(charge) + cobrança no Asaas. */
    PaymentResponse createPaymentForOrder(Order order, PaymentMethod method);

    /** Processa evento do webhook Asaas (idempotente). */
    void handleWebhookEvent(AsaasWebhookEvent event);

    /** Libera escrow: cria Transaction(transfer) + transfere no Asaas (gross - 20%). */
    void releaseEscrow(UUID orderId);

    /** Reembolsa: cria Transaction(refund) + estorna no Asaas. */
    void refundPayment(UUID paymentId, BigDecimal amount, String reason, UUID changedBy);

    /** Cancela cobrança pendente no Asaas. */
    void cancelPayment(UUID orderId);

    /** Decide reembolso parcial/total baseado nas regras de cancelamento. */
    void processOrderCancellation(Order order, UUID requesterId);

    /** Consulta pagamento por ID do pedido (com verificação de propriedade). */
    PaymentResponse getPaymentByOrderId(UUID orderId, UUID userId);

    /** Consulta pagamento por ID (com verificação de propriedade). */
    PaymentResponse getPaymentById(UUID paymentId, UUID userId);

    /** Lista pagamentos (admin). */
    Page<PaymentResponse> listPayments(Pageable pageable);

    /** Liberação manual do escrow (admin — pós-disputa). */
    void adminRelease(UUID paymentId, String reason, UUID adminId);

    /** Reembolso manual (admin — pós-disputa). */
    void adminRefund(UUID paymentId, BigDecimal amount, String reason, UUID adminId);
}
