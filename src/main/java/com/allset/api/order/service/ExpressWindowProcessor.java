package com.allset.api.order.service;

import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.config.AppProperties;
import com.allset.api.order.domain.*;
import com.allset.api.order.repository.ExpressQueueRepository;
import com.allset.api.order.repository.OrderRepository;
import com.allset.api.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Processa entradas individuais do Express em transações independentes (REQUIRES_NEW).
 * Garante que uma falha em uma entry/order não comprometa as demais no mesmo ciclo do scheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpressWindowProcessor {

    private final OrderRepository orderRepository;
    private final ExpressQueueRepository queueRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final SavedAddressRepository addressRepository;
    private final AppProperties appProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markTimeout(ExpressQueueEntry entry, Instant now) {
        entry.setProResponse(ProResponse.timeout);
        entry.setRespondedAt(now);
        queueRepository.save(entry);
        log.info("event=express_pro_timeout orderId={} professionalId={}",
                entry.getOrderId(), entry.getProfessionalId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processAffectedOrder(UUID orderId, Instant now) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.pending) return;

        boolean stillWaiting = queueRepository.existsByOrderIdAndProResponseIsNull(orderId);
        if (stillWaiting) return;

        boolean hasProposals = queueRepository.existsByOrderIdAndProResponse(orderId, ProResponse.accepted);
        if (!hasProposals) {
            expandSearchOrCancel(order, now);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processExpiredOrder(Order order, Instant now) {
        // Re-fetch dentro desta transação
        order = orderRepository.findByIdAndDeletedAtIsNull(order.getId()).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.pending) return;

        boolean hasProposals = queueRepository.existsByOrderIdAndProResponse(
                order.getId(), ProResponse.accepted);

        if (hasProposals) {
            cancelOrder(order, "Prazo para escolha de proposta expirado", now);
            log.info("event=express_client_window_expired orderId={}", order.getId());
        } else {
            boolean stillWaiting = queueRepository.existsByOrderIdAndProResponseIsNull(order.getId());
            if (!stillWaiting) {
                expandSearchOrCancel(order, now);
            }
        }
    }

    void expandSearchOrCancel(Order order, Instant now) {
        if (order.getSearchAttempts() >= appProperties.expressMaxSearchAttempts()) {
            cancelOrder(order, "Nenhum profissional disponível na região após " +
                    order.getSearchAttempts() + " tentativas", now);
            log.info("event=express_max_attempts_reached orderId={}", order.getId());
            return;
        }

        double baseRadius = appProperties.expressSearchRadiusKm();
        double maxRadius  = appProperties.expressMaxRadiusKm();
        int    nextAttempt = order.getSearchAttempts() + 1;
        int    maxAttempts = appProperties.expressMaxSearchAttempts();
        double newRadius   = baseRadius + (maxRadius - baseRadius)
                * (double)(nextAttempt - 1) / (maxAttempts - 1);
        newRadius = Math.min(newRadius, maxRadius);

        List<UUID> existingProIds = queueRepository.findProfessionalIdsByOrderId(order.getId());

        SavedAddress address = addressRepository.findById(order.getAddressId()).orElse(null);
        if (address == null || address.getLat() == null || address.getLng() == null) {
            cancelOrder(order, "Endereço sem coordenadas para expansão de busca", now);
            return;
        }

        double lat = address.getLat().doubleValue();
        double lng = address.getLng().doubleValue();

        List<UUID> newProIds = queueRepository.findNearbyProfessionalIdsExcluding(
                order.getCategoryId(), lat, lng, newRadius,
                appProperties.expressMaxQueueSize(), existingProIds);

        if (newProIds.isEmpty()) {
            if (nextAttempt >= maxAttempts) {
                cancelOrder(order, "Nenhum profissional disponível na região após " +
                        nextAttempt + " tentativas", now);
            } else {
                order.setSearchAttempts((short) nextAttempt);
                order.setSearchRadiusKm(BigDecimal.valueOf(newRadius).setScale(2, RoundingMode.HALF_UP));
                order.setExpiresAt(now.plus(appProperties.expressProTimeoutMinutes(), ChronoUnit.MINUTES));
                orderRepository.save(order);
                log.info("event=express_expand_no_pros_yet orderId={} radius={}", order.getId(), newRadius);
            }
            return;
        }

        int nextPosition = existingProIds.size() + 1;
        List<ExpressQueueEntry> entries = new ArrayList<>();
        for (int i = 0; i < newProIds.size(); i++) {
            entries.add(ExpressQueueEntry.builder()
                    .orderId(order.getId())
                    .professionalId(newProIds.get(i))
                    .queuePosition((short) (nextPosition + i))
                    .notifiedAt(now)
                    .build());
        }
        queueRepository.saveAll(entries);

        order.setSearchAttempts((short) nextAttempt);
        order.setSearchRadiusKm(BigDecimal.valueOf(newRadius).setScale(2, RoundingMode.HALF_UP));
        order.setExpiresAt(now.plus(appProperties.expressProTimeoutMinutes(), ChronoUnit.MINUTES));
        orderRepository.save(order);

        log.info("event=express_radius_expanded orderId={} newRadius={} newPros={} attempt={}",
                order.getId(), newRadius, newProIds.size(), nextAttempt);
    }

    void cancelOrder(Order order, String reason, Instant now) {
        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.cancelled);
        order.setCancelledAt(now);
        order.setCancelReason(reason);
        orderRepository.save(order);
        recordTransition(order.getId(), previous, OrderStatus.cancelled, reason, null);
    }

    private void recordTransition(UUID orderId, OrderStatus from, OrderStatus to,
                                  String reason, UUID changedBy) {
        historyRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .changedBy(changedBy)
                .build());
    }
}
