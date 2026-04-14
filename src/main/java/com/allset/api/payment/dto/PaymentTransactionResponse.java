package com.allset.api.payment.dto;

import com.allset.api.payment.domain.TransactionStatus;
import com.allset.api.payment.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionResponse(
        UUID id,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String asaasId,
        String failureReason,
        Instant processedAt,
        Instant createdAt
) {}
