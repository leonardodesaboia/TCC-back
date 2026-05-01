package com.allset.api.order.mapper;

import com.allset.api.config.AppProperties;
import com.allset.api.order.domain.ExpressQueueEntry;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderPhoto;
import com.allset.api.order.dto.DistanceBand;
import com.allset.api.order.dto.ExpressProposalResponse;
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
    private final AppProperties appProperties;

    public OrderResponse toResponse(Order order) {
        return toResponse(order, Collections.emptyList(), null);
    }

    public OrderResponse toResponse(Order order, ExpressQueueEntry queueEntry) {
        return toResponse(order, Collections.emptyList(), queueEntry);
    }

    public OrderResponse toResponse(Order order, List<OrderPhoto> photos) {
        return toResponse(order, photos, null);
    }

    public OrderResponse toResponse(Order order, List<OrderPhoto> photos, ExpressQueueEntry queueEntry) {
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
                queueEntry == null ? null : queueEntry.getProResponse(),
                queueEntry == null ? null : queueEntry.getClientResponse(),
                queueEntry == null ? null : queueEntry.getProposedAmount(),
                order.getMode(),
                order.getStatus(),
                order.getDescription(),
                order.getAddressId(),
                order.getAddressSnapshot() == null ? null : order.getAddressSnapshot().toString(),
                order.getScheduledAt(),
                order.getExpiresAt(),
                order.getProposalDeadline(),
                order.getUrgencyFee(),
                order.getBaseAmount(),
                order.getPlatformFee(),
                order.getTotalAmount(),
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

    public ExpressProposalResponse toProposalResponse(ExpressQueueEntry entry) {
        return new ExpressProposalResponse(
                entry.getProfessionalId(),
                entry.getProposedAmount(),
                entry.getRespondedAt(),
                entry.getQueuePosition(),
                computeDistanceBand(entry.getDistanceMeters(), appProperties.expressSearchRadiusMeters())
        );
    }

    /**
     * Divide o raio configurado em 3 faixas iguais (arredondadas para cima ao inteiro).
     * Garantia: qualquer distância no intervalo [0, radiusMeters] cai em exatamente uma faixa.
     */
    static DistanceBand computeDistanceBand(int distanceMeters, int radiusMeters) {
        int bandSize = (int) Math.ceil(radiusMeters / 3.0);
        int b1 = bandSize;
        int b2 = bandSize * 2;
        int top = radiusMeters;

        if (distanceMeters < b1) {
            return new DistanceBand("0-" + b1 + "m", 0, b1);
        }
        if (distanceMeters < b2) {
            return new DistanceBand(b1 + "-" + b2 + "m", b1, b2);
        }
        return new DistanceBand(b2 + "-" + top + "m", b2, top);
    }
}
