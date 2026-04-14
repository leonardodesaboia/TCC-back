package com.allset.api.payment.service;

import com.allset.api.integration.asaas.AsaasClient;
import com.allset.api.integration.asaas.dto.AsaasChargeResponse;
import com.allset.api.integration.asaas.dto.AsaasTransferResponse;
import com.allset.api.integration.asaas.dto.AsaasWebhookEvent;
import com.allset.api.order.domain.Order;
import com.allset.api.payment.domain.*;
import com.allset.api.payment.dto.PaymentResponse;
import com.allset.api.payment.exception.*;
import com.allset.api.payment.mapper.PaymentMapper;
import com.allset.api.payment.repository.*;
import com.allset.api.user.domain.User;
import com.allset.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final BigDecimal PARTIAL_REFUND_RATE = new BigDecimal("0.50");
    private static final Duration CANCELLATION_GRACE_PERIOD = Duration.ofHours(24);

    private static final Set<PaymentStatus> VALID_TRANSITIONS_FROM_PENDING =
            Set.of(PaymentStatus.confirmed, PaymentStatus.failed, PaymentStatus.cancelled);
    private static final Set<PaymentStatus> VALID_TRANSITIONS_FROM_CONFIRMED =
            Set.of(PaymentStatus.released, PaymentStatus.refunded, PaymentStatus.refunded_partial, PaymentStatus.held);
    private static final Set<PaymentStatus> VALID_TRANSITIONS_FROM_HELD =
            Set.of(PaymentStatus.released, PaymentStatus.refunded);

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final PaymentMapper paymentMapper;
    private final AsaasClient asaasClient;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────
    // Criação de pagamento
    // ─────────────────────────────────────────

    @Override
    public PaymentResponse createPaymentForOrder(Order order, PaymentMethod method) {
        // Verifica se já existe pagamento ativo para o pedido
        paymentRepository.findByOrderIdAndDeletedAtIsNull(order.getId())
                .ifPresent(p -> { throw new PaymentAlreadyExistsException(order.getId()); });

        BigDecimal grossAmount = order.getTotalAmount();
        BigDecimal platformFee = order.getPlatformFee();
        BigDecimal netAmount = grossAmount.subtract(platformFee);

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .payerUserId(order.getClientId())
                .receiverProfessionalId(order.getProfessionalId())
                .status(PaymentStatus.pending)
                .method(method)
                .grossAmount(grossAmount)
                .platformFee(platformFee)
                .netAmount(netAmount)
                .build();

        Payment saved = paymentRepository.save(payment);
        recordTransition(saved.getId(), null, PaymentStatus.pending, "Pagamento criado", null);

        // Busca dados do cliente para criar cobrança no Asaas
        User payer = userRepository.findById(order.getClientId())
                .orElseThrow(() -> new PaymentProcessingException(
                        "Usuário pagador não encontrado: " + order.getClientId()));

        // Cria cliente no Asaas (ou recupera se já existe)
        var customer = asaasClient.createCustomer(payer.getName(), payer.getCpf(), payer.getEmail());

        // Converte método de pagamento para billingType do Asaas
        String billingType = switch (method) {
            case pix -> "PIX";
            case credit_card -> "CREDIT_CARD";
            case boleto -> "BOLETO";
        };

        // Cria cobrança no Asaas
        AsaasChargeResponse charge = asaasClient.createCharge(
                customer.id(),
                grossAmount,
                billingType,
                "AllSet - Pedido #" + order.getId().toString().substring(0, 8),
                order.getId().toString()
        );

        // Atualiza payment com dados do Asaas
        saved.setAsaasPaymentId(charge.id());
        saved.setPixCopyPaste(charge.pixCopyPaste());
        saved.setPixQrCodeUrl(charge.pixQrCodeUrl());
        saved.setInvoiceUrl(charge.invoiceUrl());
        paymentRepository.save(saved);

        // Registra transaction de cobrança
        PaymentTransaction chargeTransaction = PaymentTransaction.builder()
                .paymentId(saved.getId())
                .type(TransactionType.charge)
                .status(TransactionStatus.pending)
                .amount(grossAmount)
                .asaasId(charge.id())
                .build();
        transactionRepository.save(chargeTransaction);

        log.info("event=payment_created paymentId={} orderId={} grossAmount={} method={}",
                saved.getId(), order.getId(), grossAmount, method);

        return toResponse(saved);
    }

    // ─────────────────────────────────────────
    // Webhook Asaas
    // ─────────────────────────────────────────

    @Override
    public void handleWebhookEvent(AsaasWebhookEvent event) {
        if (event.payment() == null || event.payment().id() == null) {
            log.warn("event=webhook_ignored reason=missing_payment_data");
            return;
        }

        String asaasPaymentId = event.payment().id();
        Payment payment = paymentRepository.findByAsaasPaymentIdForUpdate(asaasPaymentId).orElse(null);

        if (payment == null) {
            log.warn("event=webhook_ignored reason=payment_not_found asaasPaymentId={}", asaasPaymentId);
            return;
        }

        String eventType = event.event();
        log.info("event=webhook_received eventType={} asaasPaymentId={} paymentId={}",
                eventType, asaasPaymentId, payment.getId());

        switch (eventType) {
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> handlePaymentConfirmed(payment);
            case "PAYMENT_OVERDUE", "PAYMENT_DELETED" -> handlePaymentFailed(payment, eventType);
            case "PAYMENT_REFUNDED" -> handlePaymentRefunded(payment);
            default -> log.warn("event=webhook_event_not_handled eventType={} asaasPaymentId={}",
                    eventType, asaasPaymentId);
        }
    }

    private void handlePaymentConfirmed(Payment payment) {
        // Idempotência: se já está confirmed ou em status posterior, ignora
        if (payment.getStatus() != PaymentStatus.pending) {
            log.info("event=webhook_idempotent paymentId={} currentStatus={} expectedStatus=pending",
                    payment.getId(), payment.getStatus());
            return;
        }

        PaymentStatus fromStatus = payment.getStatus();
        payment.setStatus(PaymentStatus.confirmed);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);

        // Atualiza transaction de charge
        transactionRepository.findByAsaasId(payment.getAsaasPaymentId())
                .ifPresent(tx -> {
                    tx.setStatus(TransactionStatus.confirmed);
                    tx.setProcessedAt(Instant.now());
                    transactionRepository.save(tx);
                });

        recordTransition(payment.getId(), fromStatus, PaymentStatus.confirmed,
                "Pagamento confirmado via webhook Asaas", null);

        log.info("event=payment_confirmed paymentId={} orderId={}", payment.getId(), payment.getOrderId());
    }

    private void handlePaymentFailed(Payment payment, String eventType) {
        if (payment.getStatus() != PaymentStatus.pending) {
            log.info("event=webhook_idempotent paymentId={} currentStatus={} expectedStatus=pending",
                    payment.getId(), payment.getStatus());
            return;
        }

        PaymentStatus fromStatus = payment.getStatus();
        payment.setStatus(PaymentStatus.failed);
        payment.setFailureReason("Evento Asaas: " + eventType);
        paymentRepository.save(payment);

        transactionRepository.findByAsaasId(payment.getAsaasPaymentId())
                .ifPresent(tx -> {
                    tx.setStatus(TransactionStatus.failed);
                    tx.setFailureReason("Evento Asaas: " + eventType);
                    tx.setProcessedAt(Instant.now());
                    transactionRepository.save(tx);
                });

        recordTransition(payment.getId(), fromStatus, PaymentStatus.failed,
                "Pagamento falhou: " + eventType, null);

        log.info("event=payment_failed paymentId={} orderId={} reason={}",
                payment.getId(), payment.getOrderId(), eventType);
    }

    private void handlePaymentRefunded(Payment payment) {
        if (payment.getStatus() == PaymentStatus.refunded
                || payment.getStatus() == PaymentStatus.refunded_partial) {
            log.info("event=webhook_idempotent paymentId={} currentStatus={}",
                    payment.getId(), payment.getStatus());
            return;
        }

        PaymentStatus fromStatus = payment.getStatus();
        payment.setStatus(PaymentStatus.refunded);
        payment.setRefundedAt(Instant.now());
        payment.setRefundAmount(payment.getGrossAmount());
        paymentRepository.save(payment);

        recordTransition(payment.getId(), fromStatus, PaymentStatus.refunded,
                "Reembolso confirmado via webhook Asaas", null);

        log.info("event=payment_refunded paymentId={} orderId={}", payment.getId(), payment.getOrderId());
    }

    // ─────────────────────────────────────────
    // Liberação do escrow
    // ─────────────────────────────────────────

    @Override
    public void releaseEscrow(UUID orderId) {
        Payment payment = paymentRepository.findByOrderIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        validateTransition(payment.getStatus(), PaymentStatus.released, "liberação do escrow");

        // Cria transferência no Asaas
        AsaasTransferResponse transfer = asaasClient.createTransfer(
                payment.getReceiverProfessionalId().toString(),
                payment.getNetAmount()
        );

        PaymentStatus fromStatus = payment.getStatus();
        payment.setStatus(PaymentStatus.released);
        payment.setReleasedAt(Instant.now());
        payment.setAsaasTransferId(transfer.id());
        paymentRepository.save(payment);

        // Registra transaction de transferência
        PaymentTransaction transferTx = PaymentTransaction.builder()
                .paymentId(payment.getId())
                .type(TransactionType.transfer)
                .status(TransactionStatus.confirmed)
                .amount(payment.getNetAmount())
                .asaasId(transfer.id())
                .processedAt(Instant.now())
                .build();
        transactionRepository.save(transferTx);

        recordTransition(payment.getId(), fromStatus, PaymentStatus.released,
                "Escrow liberado ao profissional", null);

        log.info("event=escrow_released paymentId={} orderId={} netAmount={}",
                payment.getId(), orderId, payment.getNetAmount());
    }

    // ─────────────────────────────────────────
    // Reembolso
    // ─────────────────────────────────────────

    @Override
    public void refundPayment(UUID paymentId, BigDecimal amount, String reason, UUID changedBy) {
        Payment payment = paymentRepository.findByIdAndDeletedAtIsNull(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (amount.compareTo(payment.getGrossAmount()) > 0) {
            throw new PaymentProcessingException(
                    "Valor do reembolso (R$ " + amount + ") excede o valor bruto do pagamento (R$ " + payment.getGrossAmount() + ")");
        }

        boolean isPartial = amount.compareTo(payment.getGrossAmount()) < 0;
        PaymentStatus targetStatus = isPartial ? PaymentStatus.refunded_partial : PaymentStatus.refunded;

        validateTransition(payment.getStatus(), targetStatus, "reembolso");

        // Estorna no Asaas
        asaasClient.refundCharge(payment.getAsaasPaymentId(), amount,
                "Reembolso AllSet: " + reason);

        PaymentStatus fromStatus = payment.getStatus();
        payment.setStatus(targetStatus);
        payment.setRefundedAt(Instant.now());
        payment.setRefundAmount(amount);
        paymentRepository.save(payment);

        // Registra transaction de reembolso
        PaymentTransaction refundTx = PaymentTransaction.builder()
                .paymentId(payment.getId())
                .type(TransactionType.refund)
                .status(TransactionStatus.confirmed)
                .amount(amount)
                .processedAt(Instant.now())
                .build();
        transactionRepository.save(refundTx);

        recordTransition(payment.getId(), fromStatus, targetStatus, reason, changedBy);

        log.info("event=payment_refunded paymentId={} amount={} partial={} reason={}",
                paymentId, amount, isPartial, reason);
    }

    // ─────────────────────────────────────────
    // Cancelamento de cobrança pendente
    // ─────────────────────────────────────────

    @Override
    public void cancelPayment(UUID orderId) {
        Payment payment = paymentRepository.findByOrderIdAndDeletedAtIsNull(orderId).orElse(null);

        if (payment == null) {
            log.info("event=cancel_payment_skipped reason=no_payment orderId={}", orderId);
            return;
        }

        if (payment.getStatus() != PaymentStatus.pending) {
            log.info("event=cancel_payment_skipped reason=not_pending paymentId={} status={}",
                    payment.getId(), payment.getStatus());
            return;
        }

        // Cancela cobrança no Asaas
        if (payment.getAsaasPaymentId() != null) {
            asaasClient.cancelCharge(payment.getAsaasPaymentId());
        }

        PaymentStatus fromStatus = payment.getStatus();
        payment.setStatus(PaymentStatus.cancelled);
        paymentRepository.save(payment);

        // Marca transaction de charge como failed
        transactionRepository.findByAsaasId(payment.getAsaasPaymentId())
                .ifPresent(tx -> {
                    tx.setStatus(TransactionStatus.failed);
                    tx.setFailureReason("Cobrança cancelada");
                    tx.setProcessedAt(Instant.now());
                    transactionRepository.save(tx);
                });

        recordTransition(payment.getId(), fromStatus, PaymentStatus.cancelled,
                "Cobrança cancelada por cancelamento do pedido", null);

        log.info("event=payment_cancelled paymentId={} orderId={}", payment.getId(), orderId);
    }

    // ─────────────────────────────────────────
    // Processamento de cancelamento de pedido
    // ─────────────────────────────────────────

    @Override
    public void processOrderCancellation(Order order, UUID requesterId) {
        Payment payment = paymentRepository.findByOrderIdAndDeletedAtIsNull(order.getId()).orElse(null);

        if (payment == null) {
            log.info("event=order_cancellation_no_payment orderId={}", order.getId());
            return;
        }

        if (payment.getStatus() == PaymentStatus.pending) {
            // Cliente não pagou ainda — apenas cancela a cobrança
            cancelPayment(order.getId());
            return;
        }

        if (payment.getStatus() == PaymentStatus.confirmed) {
            boolean isCancelledByClient = requesterId.equals(order.getClientId());

            if (isCancelledByClient) {
                Duration timeSinceCreation = Duration.between(order.getCreatedAt(), Instant.now());
                if (timeSinceCreation.compareTo(CANCELLATION_GRACE_PERIOD) < 0) {
                    // Cancelamento < 24h: reembolso de 50%
                    BigDecimal refundAmount = payment.getGrossAmount()
                            .multiply(PARTIAL_REFUND_RATE)
                            .setScale(2, RoundingMode.HALF_UP);
                    refundPayment(payment.getId(), refundAmount,
                            "Cancelamento pelo cliente dentro de 24h — reembolso parcial (50%)", requesterId);
                } else {
                    // Cancelamento >= 24h: reembolso de 100%
                    refundPayment(payment.getId(), payment.getGrossAmount(),
                            "Cancelamento pelo cliente após 24h — reembolso total", requesterId);
                }
            } else {
                // Cancelamento pelo profissional: sempre 100%
                refundPayment(payment.getId(), payment.getGrossAmount(),
                        "Cancelamento pelo profissional — reembolso total", requesterId);
            }
        }
    }

    // ─────────────────────────────────────────
    // Consultas
    // ─────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(UUID orderId, UUID userId) {
        Payment payment = paymentRepository.findByOrderIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
        verifyOwnership(payment, userId);
        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId, UUID userId) {
        Payment payment = paymentRepository.findByIdAndDeletedAtIsNull(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        verifyOwnership(payment, userId);
        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> listPayments(Pageable pageable) {
        return paymentRepository.findAllByDeletedAtIsNull(pageable)
                .map(this::toResponse);
    }

    // ─────────────────────────────────────────
    // Admin — liberação e reembolso manual
    // ─────────────────────────────────────────

    @Override
    public void adminRelease(UUID paymentId, String reason, UUID adminId) {
        Payment payment = paymentRepository.findByIdAndDeletedAtIsNull(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        validateTransition(payment.getStatus(), PaymentStatus.released, "liberação manual");

        AsaasTransferResponse transfer = asaasClient.createTransfer(
                payment.getReceiverProfessionalId().toString(),
                payment.getNetAmount()
        );

        PaymentStatus fromStatus = payment.getStatus();
        payment.setStatus(PaymentStatus.released);
        payment.setReleasedAt(Instant.now());
        payment.setAsaasTransferId(transfer.id());
        paymentRepository.save(payment);

        PaymentTransaction transferTx = PaymentTransaction.builder()
                .paymentId(payment.getId())
                .type(TransactionType.transfer)
                .status(TransactionStatus.confirmed)
                .amount(payment.getNetAmount())
                .asaasId(transfer.id())
                .processedAt(Instant.now())
                .build();
        transactionRepository.save(transferTx);

        recordTransition(payment.getId(), fromStatus, PaymentStatus.released,
                "Liberação manual por admin: " + (reason != null ? reason : ""), adminId);

        log.info("event=admin_release paymentId={} adminId={} netAmount={}",
                paymentId, adminId, payment.getNetAmount());
    }

    @Override
    public void adminRefund(UUID paymentId, BigDecimal amount, String reason, UUID adminId) {
        refundPayment(paymentId, amount, "Reembolso manual por admin: " + reason, adminId);
    }

    // ─────────────────────────────────────────
    // Privados
    // ─────────────────────────────────────────

    private PaymentResponse toResponse(Payment payment) {
        List<PaymentTransaction> transactions = transactionRepository.findAllByPaymentId(payment.getId());
        return paymentMapper.toResponse(payment, transactions);
    }

    private void recordTransition(UUID paymentId, PaymentStatus from, PaymentStatus to,
                                  String reason, UUID changedBy) {
        historyRepository.save(PaymentStatusHistory.builder()
                .paymentId(paymentId)
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .changedBy(changedBy)
                .build());
    }

    private void verifyOwnership(Payment payment, UUID userId) {
        if (!payment.getPayerUserId().equals(userId)
                && !payment.getReceiverProfessionalId().equals(userId)) {
            throw new PaymentNotFoundException(payment.getId());
        }
    }

    private void validateTransition(PaymentStatus current, PaymentStatus target, String action) {
        Set<PaymentStatus> validTargets = switch (current) {
            case pending -> VALID_TRANSITIONS_FROM_PENDING;
            case confirmed -> VALID_TRANSITIONS_FROM_CONFIRMED;
            case held -> VALID_TRANSITIONS_FROM_HELD;
            default -> Set.of();
        };

        if (!validTargets.contains(target)) {
            throw new PaymentStatusTransitionException(current, action);
        }
    }
}
