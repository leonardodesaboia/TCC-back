package com.allset.api.order.dto;

import com.allset.api.order.domain.ClientResponse;
import com.allset.api.order.domain.ProResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExpressQueueEntryResponse(
        UUID id,
        UUID orderId,
        UUID professionalId,
        short queuePosition,
        BigDecimal proposedAmount,
        Instant notifiedAt,
        Instant respondedAt,
        ProResponse proResponse,
        ClientResponse clientResponse,
        Instant clientRespondedAt
) {}
