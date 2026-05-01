package com.allset.api.order.dto;

import com.allset.api.order.domain.OrderMode;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.domain.ProResponse;
import com.allset.api.order.domain.ClientResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID clientId,
        UUID professionalId,
        UUID serviceId,
        UUID areaId,
        UUID categoryId,
        ProResponse professionalProResponse,
        ClientResponse professionalClientResponse,
        BigDecimal professionalProposedAmount,
        OrderMode mode,
        OrderStatus status,
        String description,
        UUID addressId,
        String addressSnapshot,
        Instant scheduledAt,
        Instant expiresAt,
        BigDecimal urgencyFee,
        BigDecimal baseAmount,
        BigDecimal platformFee,
        BigDecimal totalAmount,
        BigDecimal searchRadiusKm,
        short searchAttempts,
        Instant proCompletedAt,
        Instant disputeDeadline,
        Instant completedAt,
        Instant cancelledAt,
        String cancelReason,
        int version,
        Instant createdAt,
        Instant updatedAt,
        List<OrderPhotoResponse> photos
) {}
