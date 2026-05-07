package com.allset.api.order.service;

import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.service.NotificationService;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.domain.OrderStatusHistory;
import com.allset.api.order.domain.ProResponse;
import com.allset.api.order.repository.ExpressQueueRepository;
import com.allset.api.order.repository.OrderRepository;
import com.allset.api.order.repository.OrderStatusHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * Marca em lote todos os profissionais que não responderam até o proposal_deadline
     * de um pedido Express como timeout. Não muda o status do pedido.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeProposalWindow(UUID orderId, Instant now) {
        int marked = queueRepository.markPendingEntriesAsTimeout(orderId, now);
        if (marked > 0) {
            log.info("event=express_proposal_window_closed orderId={} timeouts={}", orderId, marked);
        }
    }

    /**
     * Cancela um pedido Express cujo expiresAt venceu sem o cliente ter escolhido proposta.
     * Motivo varia conforme tinham propostas válidas ou não.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelExpiredOrder(UUID orderId, Instant now) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.pending) return;

        boolean hasProposals = queueRepository.existsByOrderIdAndProResponse(orderId, ProResponse.accepted);

        String reason = hasProposals
                ? "Prazo para escolha de proposta expirado"
                : "Nenhum profissional aceitou o pedido no prazo";

        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.cancelled);
        order.setCancelledAt(now);
        order.setCancelReason(reason);
        orderRepository.save(order);

        historyRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(previous)
                .toStatus(OrderStatus.cancelled)
                .reason(reason)
                .build());

        notificationService.notifyUser(
                order.getClientId(),
                NotificationType.request_status_update,
                "Pedido cancelado",
                reason,
                orderData(orderId)
        );

        log.info("event=express_order_expired orderId={} hadProposals={}", orderId, hasProposals);
    }

    private JsonNode orderData(UUID orderId) {
        var data = objectMapper.createObjectNode();
        data.put("orderId", orderId.toString());
        return data;
    }
}
