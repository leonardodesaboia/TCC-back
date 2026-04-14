package com.allset.api.payment.dto;

import com.allset.api.payment.domain.PaymentMethod;
import com.allset.api.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID payerUserId,
        UUID receiverProfessionalId,
        PaymentStatus status,
        PaymentMethod method,
        BigDecimal grossAmount,
        BigDecimal platformFee,
        BigDecimal netAmount,
        BigDecimal refundAmount,
        String pixCopyPaste,
        String pixQrCodeUrl,
        String invoiceUrl,
        Instant paidAt,
        Instant releasedAt,
        Instant refundedAt,
        String failureReason,
        List<PaymentTransactionResponse> transactions,
        Instant createdAt,
        Instant updatedAt
) {}
