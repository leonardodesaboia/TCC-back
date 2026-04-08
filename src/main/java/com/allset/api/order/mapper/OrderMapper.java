package com.allset.api.order.mapper;

import com.allset.api.order.domain.ExpressQueueEntry;
import com.allset.api.order.domain.Order;
import com.allset.api.order.dto.ExpressQueueEntryResponse;
import com.allset.api.order.dto.OrderResponse;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getClientId(),
                order.getProfessionalId(),
                order.getServiceId(),
                order.getAreaId(),
                order.getCategoryId(),
                order.getMode(),
                order.getStatus(),
                order.getDescription(),
                order.getAddressId(),
                order.getAddressSnapshot() == null ? null : order.getAddressSnapshot().toString(),
                order.getScheduledAt(),
                order.getExpiresAt(),
                order.getUrgencyFee(),
                order.getBaseAmount(),
                order.getPlatformFee(),
                order.getTotalAmount(),
                order.getSearchRadiusKm(),
                order.getSearchAttempts(),
                order.getProCompletedAt(),
                order.getDisputeDeadline(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getCancelReason(),
                order.getVersion(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public ExpressQueueEntryResponse toQueueResponse(ExpressQueueEntry entry) {
        return new ExpressQueueEntryResponse(
                entry.getId(),
                entry.getOrderId(),
                entry.getProfessionalId(),
                entry.getQueuePosition(),
                entry.getProposedAmount(),
                entry.getNotifiedAt(),
                entry.getRespondedAt(),
                entry.getProResponse(),
                entry.getClientResponse(),
                entry.getClientRespondedAt()
        );
    }
}
