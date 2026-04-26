package com.allset.api.order.mapper;

import com.allset.api.order.domain.ExpressQueueEntry;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderPhoto;
import com.allset.api.order.dto.ExpressQueueEntryResponse;
import com.allset.api.order.dto.OrderPhotoResponse;
import com.allset.api.order.dto.OrderResponse;
import com.allset.api.shared.storage.domain.StorageBucket;
import com.allset.api.shared.storage.service.StorageRefFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final StorageRefFactory storageRefFactory;

    public OrderResponse toResponse(Order order) {
        return toResponse(order, Collections.emptyList());
    }

    public OrderResponse toResponse(Order order, List<OrderPhoto> photos) {
        List<OrderPhotoResponse> photoResponses = photos == null ? List.of() : photos.stream()
                .map(this::toPhotoResponse)
                .toList();

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
                order.getUpdatedAt(),
                photoResponses
        );
    }

    public OrderPhotoResponse toPhotoResponse(OrderPhoto photo) {
        return new OrderPhotoResponse(
                photo.getId(),
                photo.getPhotoType(),
                photo.getUploaderId(),
                storageRefFactory.from(StorageBucket.ORDER_PHOTOS, photo.getStorageKey()),
                photo.getUploadedAt()
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
