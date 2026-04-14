package com.allset.api.payment.service;

import com.allset.api.integration.asaas.AsaasClient;
import com.allset.api.integration.asaas.dto.AsaasChargeResponse;
import com.allset.api.integration.asaas.dto.AsaasCustomerResponse;
import com.allset.api.integration.asaas.dto.AsaasTransferResponse;
import com.allset.api.integration.asaas.dto.AsaasWebhookEvent;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderMode;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.payment.domain.*;
import com.allset.api.payment.dto.PaymentResponse;
import com.allset.api.payment.exception.PaymentAlreadyExistsException;
import com.allset.api.payment.exception.PaymentNotFoundException;
import com.allset.api.payment.exception.PaymentStatusTransitionException;
import com.allset.api.payment.mapper.PaymentMapper;
import com.allset.api.payment.repository.PaymentRepository;
import com.allset.api.payment.repository.PaymentStatusHistoryRepository;
import com.allset.api.payment.repository.PaymentTransactionRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private PaymentStatusHistoryRepository historyRepository;
    @Mock private PaymentMapper paymentMapper;
    @Mock private AsaasClient asaasClient;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    // ─────────────────────────────────────────
    // createPaymentForOrder
    // ─────────────────────────────────────────

    @Test
    void createPaymentForOrderShouldCreatePaymentAndChargeOnAsaas() {
        Order order = buildOrder(new BigDecimal("200.00"));
        User payer = buildUser(order.getClientId());

        when(paymentRepository.findByOrderIdAndDeletedAtIsNull(order.getId())).thenReturn(Optional.empty());
        when(userRepository.findById(order.getClientId())).thenReturn(Optional.of(payer));
        when(asaasClient.createCustomer(anyString(), anyString(), anyString()))
                .thenReturn(new AsaasCustomerResponse("cus_123", payer.getName(), payer.getCpf()));
        when(asaasClient.createCharge(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new AsaasChargeResponse("pay_123", "PENDING", "PIX",
                        new BigDecimal("200.00"), "https://qr.url", "pix-copy-paste", "https://invoice.url"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        when(transactionRepository.findAllByPaymentId(any())).thenReturn(List.of());
        when(paymentMapper.toResponse(any(Payment.class), anyList()))
                .thenReturn(mock(PaymentResponse.class));

        paymentService.createPaymentForOrder(order, PaymentMethod.pix);

        // Verifica que o Payment foi criado com valores corretos
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeast(2)).save(paymentCaptor.capture());

        Payment saved = paymentCaptor.getAllValues().get(0);
        assertThat(saved.getGrossAmount()).isEqualByComparingTo("200.00");
        assertThat(saved.getPlatformFee()).isEqualByComparingTo("40.00");
        assertThat(saved.getNetAmount()).isEqualByComparingTo("160.00");
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.pending);
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.pix);

        // Verifica que a transaction de charge foi criada
        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        PaymentTransaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(TransactionType.charge);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.pending);
        assertThat(tx.getAmount()).isEqualByComparingTo("200.00");
        assertThat(tx.getAsaasId()).isEqualTo("pay_123");

        // Verifica audit log
        verify(historyRepository).save(any(PaymentStatusHistory.class));
    }

    @Test
    void createPaymentForOrderShouldThrowWhenPaymentAlreadyExists() {
        Order order = buildOrder(new BigDecimal("200.00"));
        Payment existing = Payment.builder().orderId(order.getId()).build();

        when(paymentRepository.findByOrderIdAndDeletedAtIsNull(order.getId()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.createPaymentForOrder(order, PaymentMethod.pix))
                .isInstanceOf(PaymentAlreadyExistsException.class);

        verify(asaasClient, never()).createCharge(any(), any(), any(), any(), any());
    }

    // ─────────────────────────────────────────
    // handleWebhookEvent — confirmed
    // ─────────────────────────────────────────

    @Test
    void handleWebhookShouldConfirmPaymentOnPaymentConfirmedEvent() {
        Payment payment = buildPayment(PaymentStatus.pending, "pay_123");

        when(paymentRepository.findByAsaasPaymentIdForUpdate("pay_123")).thenReturn(Optional.of(payment));
        when(transactionRepository.findByAsaasId("pay_123"))
                .thenReturn(Optional.of(buildChargeTransaction(payment.getId(), "pay_123")));

        AsaasWebhookEvent event = new AsaasWebhookEvent("PAYMENT_CONFIRMED",
                new AsaasWebhookEvent.PaymentPayload("pay_123", "CONFIRMED", new BigDecimal("200.00"), null));

        paymentService.handleWebhookEvent(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.confirmed);
        assertThat(payment.getPaidAt()).isNotNull();
        verify(paymentRepository).save(payment);
        verify(historyRepository).save(any(PaymentStatusHistory.class));
    }

    @Test
    void handleWebhookShouldBeIdempotentWhenPaymentAlreadyConfirmed() {
        Payment payment = buildPayment(PaymentStatus.confirmed, "pay_123");

        when(paymentRepository.findByAsaasPaymentIdForUpdate("pay_123")).thenReturn(Optional.of(payment));

        AsaasWebhookEvent event = new AsaasWebhookEvent("PAYMENT_CONFIRMED",
                new AsaasWebhookEvent.PaymentPayload("pay_123", "CONFIRMED", new BigDecimal("200.00"), null));

        paymentService.handleWebhookEvent(event);

        // Nao deve transicionar novamente
        verify(paymentRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void handleWebhookShouldIgnoreUnknownAsaasPaymentId() {
        when(paymentRepository.findByAsaasPaymentIdForUpdate("unknown")).thenReturn(Optional.empty());

        AsaasWebhookEvent event = new AsaasWebhookEvent("PAYMENT_CONFIRMED",
                new AsaasWebhookEvent.PaymentPayload("unknown", "CONFIRMED", new BigDecimal("200.00"), null));

        paymentService.handleWebhookEvent(event);

        verify(paymentRepository, never()).save(any());
    }

    // ─────────────────────────────────────────
    // handleWebhookEvent — failed
    // ─────────────────────────────────────────

    @Test
    void handleWebhookShouldFailPaymentOnOverdueEvent() {
        Payment payment = buildPayment(PaymentStatus.pending, "pay_456");

        when(paymentRepository.findByAsaasPaymentIdForUpdate("pay_456")).thenReturn(Optional.of(payment));
        when(transactionRepository.findByAsaasId("pay_456"))
                .thenReturn(Optional.of(buildChargeTransaction(payment.getId(), "pay_456")));

        AsaasWebhookEvent event = new AsaasWebhookEvent("PAYMENT_OVERDUE",
                new AsaasWebhookEvent.PaymentPayload("pay_456", "OVERDUE", new BigDecimal("200.00"), null));

        paymentService.handleWebhookEvent(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.failed);
        assertThat(payment.getFailureReason()).contains("PAYMENT_OVERDUE");
    }

    // ─────────────────────────────────────────
    // releaseEscrow
    // ─────────────────────────────────────────

    @Test
    void releaseEscrowShouldTransferNetAmountToProfessional() {
        UUID orderId = UUID.randomUUID();
        Payment payment = buildPayment(PaymentStatus.confirmed, "pay_789");
        payment.setOrderId(orderId);
        payment.setNetAmount(new BigDecimal("160.00"));

        when(paymentRepository.findByOrderIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(payment));
        when(asaasClient.createTransfer(anyString(), any()))
                .thenReturn(new AsaasTransferResponse("trf_001", "DONE", new BigDecimal("160.00")));

        paymentService.releaseEscrow(orderId);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.released);
        assertThat(payment.getReleasedAt()).isNotNull();
        assertThat(payment.getAsaasTransferId()).isEqualTo("trf_001");

        // Verifica transaction de transfer
        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getType()).isEqualTo(TransactionType.transfer);
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("160.00");
    }

    @Test
    void releaseEscrowShouldThrowWhenPaymentNotFound() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByOrderIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.releaseEscrow(orderId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void releaseEscrowShouldThrowWhenStatusIsPending() {
        UUID orderId = UUID.randomUUID();
        Payment payment = buildPayment(PaymentStatus.pending, "pay_000");
        payment.setOrderId(orderId);

        when(paymentRepository.findByOrderIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.releaseEscrow(orderId))
                .isInstanceOf(PaymentStatusTransitionException.class);
    }

    // ─────────────────────────────────────────
    // refundPayment
    // ─────────────────────────────────────────

    @Test
    void refundPaymentShouldRefundTotalAmount() {
        Payment payment = buildPayment(PaymentStatus.confirmed, "pay_refund");
        payment.setId(UUID.randomUUID());

        when(paymentRepository.findByIdAndDeletedAtIsNull(payment.getId())).thenReturn(Optional.of(payment));

        paymentService.refundPayment(payment.getId(), new BigDecimal("200.00"),
                "Reembolso total", UUID.randomUUID());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.refunded);
        assertThat(payment.getRefundAmount()).isEqualByComparingTo("200.00");

        verify(asaasClient).refundCharge(eq("pay_refund"), eq(new BigDecimal("200.00")), anyString());
    }

    @Test
    void refundPaymentShouldRefundPartialAmount() {
        Payment payment = buildPayment(PaymentStatus.confirmed, "pay_partial");
        payment.setId(UUID.randomUUID());

        when(paymentRepository.findByIdAndDeletedAtIsNull(payment.getId())).thenReturn(Optional.of(payment));

        paymentService.refundPayment(payment.getId(), new BigDecimal("100.00"),
                "Reembolso parcial", UUID.randomUUID());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.refunded_partial);
        assertThat(payment.getRefundAmount()).isEqualByComparingTo("100.00");
    }

    // ─────────────────────────────────────────
    // cancelPayment
    // ─────────────────────────────────────────

    @Test
    void cancelPaymentShouldCancelPendingChargeOnAsaas() {
        UUID orderId = UUID.randomUUID();
        Payment payment = buildPayment(PaymentStatus.pending, "pay_cancel");
        payment.setOrderId(orderId);

        when(paymentRepository.findByOrderIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(payment));
        when(transactionRepository.findByAsaasId("pay_cancel"))
                .thenReturn(Optional.of(buildChargeTransaction(payment.getId(), "pay_cancel")));

        paymentService.cancelPayment(orderId);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.cancelled);
        verify(asaasClient).cancelCharge("pay_cancel");
    }

    @Test
    void cancelPaymentShouldSkipWhenNoPaymentExists() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByOrderIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.empty());

        paymentService.cancelPayment(orderId);

        verify(asaasClient, never()).cancelCharge(any());
    }

    // ─────────────────────────────────────────
    // processOrderCancellation
    // ─────────────────────────────────────────

    @Test
    void processOrderCancellationShouldRefund50PercentWhenClientCancelsWithin24h() {
        UUID clientId = UUID.randomUUID();
        Order order = buildOrder(new BigDecimal("200.00"));
        order.setClientId(clientId);
        // Pedido criado ha 1 hora — dentro das 24h
        order.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));

        Payment payment = buildPayment(PaymentStatus.confirmed, "pay_cancel_24");
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order.getId());

        when(paymentRepository.findByOrderIdAndDeletedAtIsNull(order.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdAndDeletedAtIsNull(payment.getId())).thenReturn(Optional.of(payment));

        paymentService.processOrderCancellation(order, clientId);

        // 50% de 200 = 100
        assertThat(payment.getRefundAmount()).isEqualByComparingTo("100.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.refunded_partial);
    }

    @Test
    void processOrderCancellationShouldRefund100PercentWhenClientCancelsAfter24h() {
        UUID clientId = UUID.randomUUID();
        Order order = buildOrder(new BigDecimal("200.00"));
        order.setClientId(clientId);
        // Pedido criado ha 48 horas — fora das 24h
        order.setCreatedAt(Instant.now().minus(48, ChronoUnit.HOURS));

        Payment payment = buildPayment(PaymentStatus.confirmed, "pay_cancel_48");
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order.getId());

        when(paymentRepository.findByOrderIdAndDeletedAtIsNull(order.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdAndDeletedAtIsNull(payment.getId())).thenReturn(Optional.of(payment));

        paymentService.processOrderCancellation(order, clientId);

        assertThat(payment.getRefundAmount()).isEqualByComparingTo("200.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.refunded);
    }

    @Test
    void processOrderCancellationShouldRefund100PercentWhenProfessionalCancels() {
        UUID professionalUserId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        Order order = buildOrder(new BigDecimal("200.00"));
        order.setProfessionalId(professionalId);
        // Mesmo dentro de 24h, profissional cancela = 100%
        order.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));

        Payment payment = buildPayment(PaymentStatus.confirmed, "pay_cancel_pro");
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order.getId());

        when(paymentRepository.findByOrderIdAndDeletedAtIsNull(order.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdAndDeletedAtIsNull(payment.getId())).thenReturn(Optional.of(payment));

        // professionalUserId != clientId, entao nao eh cancelamento por cliente
        paymentService.processOrderCancellation(order, professionalUserId);

        assertThat(payment.getRefundAmount()).isEqualByComparingTo("200.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.refunded);
    }

    // ─────────────────────────────────────────
    // Consultas
    // ─────────────────────────────────────────

    @Test
    void getPaymentByOrderIdShouldThrowWhenNotFound() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(paymentRepository.findByOrderIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByOrderId(orderId, userId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void getPaymentByIdShouldThrowWhenNotFound() {
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(paymentRepository.findByIdAndDeletedAtIsNull(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(paymentId, userId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void getPaymentByIdShouldThrowWhenUserIsNotOwner() {
        UUID userId = UUID.randomUUID();
        Payment payment = buildPayment(PaymentStatus.confirmed, "pay_owner");
        payment.setId(UUID.randomUUID());

        when(paymentRepository.findByIdAndDeletedAtIsNull(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getPaymentById(payment.getId(), userId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void refundPaymentShouldThrowWhenAmountExceedsGrossAmount() {
        Payment payment = buildPayment(PaymentStatus.confirmed, "pay_over");
        payment.setId(UUID.randomUUID());

        when(paymentRepository.findByIdAndDeletedAtIsNull(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refundPayment(payment.getId(), new BigDecimal("999.99"),
                "Reembolso excessivo", UUID.randomUUID()))
                .isInstanceOf(com.allset.api.payment.exception.PaymentProcessingException.class);

        verify(asaasClient, never()).refundCharge(any(), any(), any());
    }

    // ─────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────

    private Order buildOrder(BigDecimal totalAmount) {
        BigDecimal platformFee = totalAmount.multiply(new BigDecimal("0.20")).setScale(2, java.math.RoundingMode.HALF_UP);
        Order order = Order.builder()
                .clientId(UUID.randomUUID())
                .professionalId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .mode(OrderMode.express)
                .status(OrderStatus.accepted)
                .description("Servico de teste")
                .addressId(UUID.randomUUID())
                .totalAmount(totalAmount)
                .platformFee(platformFee)
                .urgencyFee(BigDecimal.ZERO)
                .baseAmount(totalAmount)
                .expiresAt(Instant.now())
                .build();
        order.setId(UUID.randomUUID());
        order.setCreatedAt(Instant.now());
        return order;
    }

    private Payment buildPayment(PaymentStatus status, String asaasPaymentId) {
        Payment payment = Payment.builder()
                .orderId(UUID.randomUUID())
                .payerUserId(UUID.randomUUID())
                .receiverProfessionalId(UUID.randomUUID())
                .status(status)
                .method(PaymentMethod.pix)
                .grossAmount(new BigDecimal("200.00"))
                .platformFee(new BigDecimal("40.00"))
                .netAmount(new BigDecimal("160.00"))
                .asaasPaymentId(asaasPaymentId)
                .build();
        payment.setId(UUID.randomUUID());
        return payment;
    }

    private PaymentTransaction buildChargeTransaction(UUID paymentId, String asaasId) {
        return PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .type(TransactionType.charge)
                .status(TransactionStatus.pending)
                .amount(new BigDecimal("200.00"))
                .asaasId(asaasId)
                .createdAt(Instant.now())
                .build();
    }

    private User buildUser(UUID userId) {
        User user = User.builder()
                .name("Cliente Teste")
                .cpf("12345678901")
                .cpfHash("a".repeat(64))
                .email("teste@example.com")
                .phone("85999999999")
                .password("senha-hash")
                .role(UserRole.client)
                .build();
        user.setId(userId);
        return user;
    }
}
