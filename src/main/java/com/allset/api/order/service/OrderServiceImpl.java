package com.allset.api.order.service;

import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.config.AppProperties;
import com.allset.api.order.domain.*;
import com.allset.api.order.dto.*;
import com.allset.api.order.exception.*;
import com.allset.api.order.mapper.OrderMapper;
import com.allset.api.order.repository.*;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.20");

    private final OrderRepository            orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderPhotoRepository       photoRepository;
    private final ExpressQueueRepository     queueRepository;
    private final ServiceCategoryRepository  categoryRepository;
    private final SavedAddressRepository     addressRepository;
    private final ProfessionalRepository     professionalRepository;
    private final OrderMapper                orderMapper;
    private final ObjectMapper               objectMapper;
    private final AppProperties              appProperties;

    // ─────────────────────────────────────────
    // Criação do pedido Express
    // ─────────────────────────────────────────

    @Override
    public OrderResponse createExpressOrder(UUID clientId, CreateExpressOrderRequest request) {
        // Valida categoria (ativa)
        categoryRepository.findByIdAndDeletedAtIsNull(request.categoryId())
                .filter(c -> c.isActive())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Categoria não encontrada ou inativa: " + request.categoryId()));

        // Valida endereço: ownership + coordenadas obrigatórias para Express
        SavedAddress address = addressRepository.findByIdAndUserId(request.addressId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Endereço não encontrado: " + request.addressId()));

        if (address.getLat() == null || address.getLng() == null) {
            throw new IllegalArgumentException(
                    "Endereço sem coordenadas geográficas — necessário para o modo Express");
        }

        double lat    = address.getLat().doubleValue();
        double lng    = address.getLng().doubleValue();
        double radius = appProperties.expressSearchRadiusKm();

        // Busca profissionais próximos (todos notificados ao mesmo tempo)
        List<UUID> nearbyIds = queueRepository.findNearbyProfessionalIds(
                request.categoryId(), lat, lng, radius, appProperties.expressMaxQueueSize());

        if (nearbyIds.isEmpty()) {
            throw new NoProfessionalsAvailableException();
        }

        Instant now      = Instant.now();
        String  snapshot = serializeAddress(address);

        Order order = Order.builder()
                .clientId(clientId)
                .areaId(request.areaId())
                .categoryId(request.categoryId())
                .mode(OrderMode.express)
                .status(OrderStatus.pending)
                .description(request.description())
                .addressId(request.addressId())
                .addressSnapshot(snapshot)
                .urgencyFee(request.urgencyFee())
                .expiresAt(now.plus(appProperties.expressProTimeoutMinutes(), ChronoUnit.MINUTES))
                .searchRadiusKm(BigDecimal.valueOf(radius))
                .searchAttempts((short) 1)
                .build();

        Order saved = orderRepository.save(order);
        recordTransition(saved.getId(), null, OrderStatus.pending, "Pedido Express criado", null);

        // Foto do problema (opcional)
        if (request.photoUrl() != null && !request.photoUrl().isBlank()) {
            photoRepository.save(OrderPhoto.builder()
                    .orderId(saved.getId())
                    .uploaderId(clientId)
                    .photoType(PhotoType.request)
                    .url(request.photoUrl())
                    .build());
        }

        // Cria entries para todos — notificados simultaneamente
        List<ExpressQueueEntry> entries = new ArrayList<>();
        for (int i = 0; i < nearbyIds.size(); i++) {
            entries.add(ExpressQueueEntry.builder()
                    .orderId(saved.getId())
                    .professionalId(nearbyIds.get(i))
                    .queuePosition((short) (i + 1))
                    .notifiedAt(now)
                    .build());
        }
        queueRepository.saveAll(entries);

        // TODO: push notification para todos os profissionais da lista

        log.info("event=express_order_created orderId={} clientId={} professionals={}",
                saved.getId(), clientId, nearbyIds.size());

        return orderMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────
    // Leitura
    // ─────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, UUID requesterId, String requesterRole) {
        Order order = findActive(orderId);

        boolean isAdmin  = "admin".equals(requesterRole);
        boolean isClient = requesterId.equals(order.getClientId());

        // requesterId é o userId (JWT sub). Precisamos do professionalId para comparar
        // com order.professionalId e com a fila express.
        boolean isPro    = false;
        boolean isInQueue = false;
        if ("professional".equals(requesterRole)) {
            UUID proId = professionalRepository.findByUserIdAndDeletedAtIsNull(requesterId)
                    .map(p -> p.getId())
                    .orElse(null);
            if (proId != null) {
                isPro    = proId.equals(order.getProfessionalId());
                isInQueue = !isPro && queueRepository
                        .findByOrderIdAndProfessionalId(orderId, proId).isPresent();
            }
        }

        if (!isAdmin && !isClient && !isPro && !isInQueue) {
            throw new OrderNotFoundException(orderId);
        }

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(UUID userId, String role, OrderStatus status, Pageable pageable) {
        if ("professional".equals(role)) {
            return professionalRepository.findByUserIdAndDeletedAtIsNull(userId)
                    .map(pro -> status != null
                            ? orderRepository.findAllByProfessionalIdAndStatusAndDeletedAtIsNull(pro.getId(), status, pageable)
                            : orderRepository.findAllByProfessionalIdAndDeletedAtIsNull(pro.getId(), pageable))
                    .orElse(Page.empty())
                    .map(orderMapper::toResponse);
        }
        return (status != null
                ? orderRepository.findAllByClientIdAndStatusAndDeletedAtIsNull(userId, status, pageable)
                : orderRepository.findAllByClientIdAndDeletedAtIsNull(userId, pageable))
                .map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpressProposalResponse> getProposals(UUID orderId, UUID clientId, String requesterRole) {
        Order order = findActive(orderId);

        boolean isAdmin  = "admin".equals(requesterRole);
        boolean isClient = clientId.equals(order.getClientId());

        if (!isAdmin && !isClient) {
            throw new OrderNotFoundException(orderId);
        }
        if (order.getMode() != OrderMode.express) {
            throw new ExpressQueueViolationException("Propostas disponíveis apenas para pedidos Express");
        }

        return queueRepository.findAllByOrderIdAndProResponse(orderId, ProResponse.accepted)
                .stream()
                .filter(e -> e.getClientResponse() == null)
                .map(e -> new ExpressProposalResponse(
                        e.getProfessionalId(),
                        e.getProposedAmount(),
                        e.getRespondedAt(),
                        e.getQueuePosition()))
                .toList();
    }

    // ─────────────────────────────────────────
    // Resposta do profissional (Express)
    // ─────────────────────────────────────────

    @Override
    public OrderResponse proRespond(UUID orderId, UUID professionalId, ProRespondRequest request) {
        Order order = findActive(orderId);

        if (order.getMode() != OrderMode.express) {
            throw new ExpressQueueViolationException("Operação exclusiva para pedidos Express");
        }
        if (order.getStatus() != OrderStatus.pending) {
            throw new OrderStatusTransitionException(order.getStatus(), "resposta do profissional");
        }

        // Valida que este profissional está na fila e ainda não respondeu
        ExpressQueueEntry entry = queueRepository
                .findByOrderIdAndProfessionalId(orderId, professionalId)
                .orElseThrow(() -> new ExpressQueueViolationException(
                        "Você não foi notificado para este pedido"));

        if (entry.getProResponse() != null) {
            throw new ExpressQueueViolationException("Você já respondeu a este pedido");
        }

        Instant now = Instant.now();
        entry.setRespondedAt(now);

        if (request.response() == ProResponse.rejected) {
            entry.setProResponse(ProResponse.rejected);
            queueRepository.save(entry);
            log.info("event=express_pro_rejected orderId={} professionalId={}", orderId, professionalId);
            return orderMapper.toResponse(order);
        }

        // accepted — proposedAmount obrigatório
        if (request.proposedAmount() == null) {
            throw new IllegalArgumentException("Valor proposto é obrigatório ao aceitar");
        }

        entry.setProResponse(ProResponse.accepted);
        entry.setProposedAmount(request.proposedAmount());
        queueRepository.save(entry);

        // Se for a primeira proposta: inicia a janela de escolha do cliente
        long totalAccepted = queueRepository.countByOrderIdAndProResponse(orderId, ProResponse.accepted);
        if (totalAccepted == 1) {
            order.setExpiresAt(now.plus(appProperties.expressClientWindowMinutes(), ChronoUnit.MINUTES));
            orderRepository.save(order);
            log.info("event=express_first_proposal orderId={} clientWindowMinutes={}",
                    orderId, appProperties.expressClientWindowMinutes());
        }

        // TODO: push notification para o cliente avisando que há uma nova proposta

        log.info("event=express_pro_accepted orderId={} professionalId={} amount={}",
                orderId, professionalId, request.proposedAmount());
        return orderMapper.toResponse(orderRepository.findByIdAndDeletedAtIsNull(orderId).orElseThrow());
    }

    // ─────────────────────────────────────────
    // Resposta do cliente (Express) — escolhe proposta
    // ─────────────────────────────────────────

    @Override
    public OrderResponse clientRespond(UUID orderId, UUID clientId, ClientRespondRequest request) {
        Order order = findActive(orderId);

        if (order.getMode() != OrderMode.express) {
            throw new ExpressQueueViolationException("Operação exclusiva para pedidos Express");
        }
        if (!order.getClientId().equals(clientId)) {
            throw new OrderNotFoundException(orderId);
        }
        if (order.getStatus() != OrderStatus.pending) {
            throw new OrderStatusTransitionException(order.getStatus(), "escolha de proposta");
        }

        // Busca a proposta do profissional selecionado
        ExpressQueueEntry chosen = queueRepository
                .findByOrderIdAndProfessionalId(orderId, request.selectedProfessionalId())
                .orElseThrow(() -> new ExpressQueueViolationException(
                        "Proposta não encontrada para o profissional informado"));

        if (chosen.getProResponse() != ProResponse.accepted) {
            throw new ExpressQueueViolationException(
                    "O profissional informado não enviou uma proposta para este pedido");
        }
        if (chosen.getClientResponse() != null) {
            throw new ExpressQueueViolationException("Esta proposta já foi respondida");
        }
        if (chosen.getProposedAmount() == null) {
            throw new ExpressQueueViolationException(
                    "Inconsistência de dados: proposta sem valor definido");
        }

        Instant now = Instant.now();

        // Aceita a proposta escolhida
        chosen.setClientResponse(ClientResponse.accepted);
        chosen.setClientRespondedAt(now);
        queueRepository.save(chosen);

        // Rejeita todas as outras propostas em lote
        queueRepository.rejectOtherProposals(orderId, chosen.getId(), ClientResponse.rejected, now);

        // Calcula valores.
        // totalAmount = o que o cliente paga (base + urgência).
        // platformFee é descontado do repasse ao profissional na liberação do escrow — não do cliente.
        BigDecimal base    = chosen.getProposedAmount();
        BigDecimal fee     = base.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal urgency = order.getUrgencyFee() != null ? order.getUrgencyFee() : BigDecimal.ZERO;
        BigDecimal total   = base.add(urgency);

        order.setProfessionalId(chosen.getProfessionalId());
        order.setBaseAmount(base);
        order.setPlatformFee(fee);
        order.setTotalAmount(total);
        order.setStatus(OrderStatus.accepted);
        order.setExpiresAt(now); // janela encerrada

        Order saved = orderRepository.save(order);
        recordTransition(orderId, OrderStatus.pending, OrderStatus.accepted,
                "Cliente escolheu proposta", clientId);

        // TODO: iniciar cobrança via módulo payment
        // TODO: criar conversa via módulo chat

        log.info("event=express_client_chose orderId={} professionalId={} total={}",
                orderId, chosen.getProfessionalId(), total);
        return orderMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────
    // Conclusão pelo profissional
    // ─────────────────────────────────────────

    @Override
    public OrderResponse completeByPro(UUID orderId, UUID professionalId, CompleteByProRequest request) {
        Order order = findActive(orderId);

        if (!professionalId.equals(order.getProfessionalId())) {
            throw new OrderNotFoundException(orderId);
        }
        if (order.getStatus() != OrderStatus.accepted) {
            throw new OrderStatusTransitionException(order.getStatus(), "conclusão pelo profissional");
        }

        UUID proUserId = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .map(p -> p.getUserId())
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        photoRepository.save(OrderPhoto.builder()
                .orderId(orderId)
                .uploaderId(proUserId)
                .photoType(PhotoType.completion_proof)
                .url(request.photoUrl())
                .build());

        Instant now = Instant.now();
        order.setProCompletedAt(now);
        order.setDisputeDeadline(now.plus(24, ChronoUnit.HOURS));
        order.setStatus(OrderStatus.completed_by_pro);

        Order saved = orderRepository.save(order);
        recordTransition(orderId, OrderStatus.accepted, OrderStatus.completed_by_pro,
                "Profissional marcou como concluído", proUserId);

        log.info("event=order_completed_by_pro orderId={} professionalId={}", orderId, professionalId);
        return orderMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────
    // Confirmação pelo cliente
    // ─────────────────────────────────────────

    @Override
    public OrderResponse confirmCompletion(UUID orderId, UUID clientId) {
        Order order = findActive(orderId);

        if (!order.getClientId().equals(clientId)) {
            throw new OrderNotFoundException(orderId);
        }
        if (order.getStatus() != OrderStatus.completed_by_pro) {
            throw new OrderStatusTransitionException(order.getStatus(), "confirmação de conclusão");
        }

        Instant now = Instant.now();
        order.setCompletedAt(now);
        order.setStatus(OrderStatus.completed);

        Order saved = orderRepository.save(order);
        recordTransition(orderId, OrderStatus.completed_by_pro, OrderStatus.completed,
                "Cliente confirmou conclusão", clientId);

        // TODO: liberar escrow via módulo payment

        log.info("event=order_completed orderId={}", orderId);
        return orderMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────
    // Cancelamento
    // ─────────────────────────────────────────

    @Override
    public OrderResponse cancelOrder(UUID orderId, UUID requesterId, CancelOrderRequest request) {
        Order order = findActive(orderId);

        boolean isClient = requesterId.equals(order.getClientId());
        boolean isPro    = requesterId.equals(order.getProfessionalId());

        if (!isClient && !isPro) {
            throw new OrderNotFoundException(orderId);
        }

        OrderStatus current = order.getStatus();
        if (current == OrderStatus.completed || current == OrderStatus.cancelled
                || current == OrderStatus.disputed) {
            throw new OrderStatusTransitionException(current, "cancelamento");
        }

        Instant now = Instant.now();
        order.setCancelledAt(now);
        order.setCancelReason(request.reason());
        order.setStatus(OrderStatus.cancelled);

        Order saved = orderRepository.save(order);
        recordTransition(orderId, current, OrderStatus.cancelled, request.reason(), requesterId);

        log.info("event=order_cancelled orderId={} by={}", orderId, requesterId);
        return orderMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────
    // Scheduler — processamento de janelas expiradas
    // ─────────────────────────────────────────

    @Override
    public void processExpiredWindows() {
        Instant now = Instant.now();

        // 1. Marca timeout em profissionais que não responderam no prazo
        Instant proCutoff = now.minus(appProperties.expressProTimeoutMinutes(), ChronoUnit.MINUTES);
        List<ExpressQueueEntry> timedOutEntries = queueRepository.findTimedOutProEntries(proCutoff);

        Set<UUID> affectedOrderIds = timedOutEntries.stream()
                .map(ExpressQueueEntry::getOrderId)
                .collect(Collectors.toSet());

        for (ExpressQueueEntry entry : timedOutEntries) {
            try {
                entry.setProResponse(ProResponse.timeout);
                entry.setRespondedAt(now);
                queueRepository.save(entry);
                log.info("event=express_pro_timeout orderId={} professionalId={}",
                        entry.getOrderId(), entry.getProfessionalId());
            } catch (Exception e) {
                log.error("event=express_pro_timeout_error orderId={} error={}",
                        entry.getOrderId(), e.getMessage(), e);
            }
        }

        // 2. Para cada pedido afetado, decide se expande raio ou aguarda
        for (UUID orderId : affectedOrderIds) {
            try {
                Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId).orElse(null);
                if (order == null || order.getStatus() != OrderStatus.pending) continue;

                boolean stillWaiting = queueRepository.existsByOrderIdAndProResponseIsNull(orderId);
                if (stillWaiting) continue; // ainda há pros respondendo

                boolean hasProposals = queueRepository.existsByOrderIdAndProResponse(orderId, ProResponse.accepted);
                if (!hasProposals) {
                    expandSearchOrCancel(order, now);
                }
                // Se tem propostas: aguarda o cliente escolher (expires_at gerencia esse prazo)
            } catch (Exception e) {
                log.error("event=express_expand_error orderId={} error={}", orderId, e.getMessage(), e);
            }
        }

        // 3. Pedidos Express pending com expires_at expirado
        List<Order> expired = orderRepository.findAllByStatusAndModeAndExpiresAtBeforeAndDeletedAtIsNull(
                OrderStatus.pending, OrderMode.express, now);

        for (Order order : expired) {
            try {
                boolean hasProposals = queueRepository.existsByOrderIdAndProResponse(
                        order.getId(), ProResponse.accepted);

                if (hasProposals) {
                    // Janela do cliente expirou sem escolha
                    cancelOrder(order, "Prazo para escolha de proposta expirado", now);
                    log.info("event=express_client_window_expired orderId={}", order.getId());
                } else {
                    // Janela de propostas expirou sem propostas — tenta expandir
                    boolean stillWaiting = queueRepository.existsByOrderIdAndProResponseIsNull(order.getId());
                    if (!stillWaiting) {
                        expandSearchOrCancel(order, now);
                    }
                }
            } catch (Exception e) {
                log.error("event=express_expired_order_error orderId={} error={}",
                        order.getId(), e.getMessage(), e);
            }
        }
    }

    // ─────────────────────────────────────────
    // Privados
    // ─────────────────────────────────────────

    /**
     * Expande o raio de busca e notifica novos profissionais.
     * Se atingir o máximo de tentativas, cancela o pedido.
     */
    private void expandSearchOrCancel(Order order, Instant now) {
        if (order.getSearchAttempts() >= appProperties.expressMaxSearchAttempts()) {
            cancelOrder(order, "Nenhum profissional disponível na região após " +
                    order.getSearchAttempts() + " tentativas", now);
            log.info("event=express_max_attempts_reached orderId={}", order.getId());
            return;
        }

        // Calcula novo raio: interpola entre raio inicial e raio máximo
        double baseRadius = appProperties.expressSearchRadiusKm();
        double maxRadius  = appProperties.expressMaxRadiusKm();
        int    nextAttempt = order.getSearchAttempts() + 1;
        int    maxAttempts = appProperties.expressMaxSearchAttempts();
        double newRadius   = baseRadius + (maxRadius - baseRadius)
                * (double)(nextAttempt - 1) / (maxAttempts - 1);
        newRadius = Math.min(newRadius, maxRadius);

        // Busca profissionais no novo raio excluindo os já notificados
        List<UUID> existingProIds = queueRepository.findProfessionalIdsByOrderId(order.getId());

        // Precisamos das coordenadas do endereço do pedido
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
            // Sem novos profissionais no raio expandido — tenta na próxima rodada ou cancela
            if (nextAttempt >= maxAttempts) {
                cancelOrder(order, "Nenhum profissional disponível na região após " +
                        nextAttempt + " tentativas", now);
            } else {
                // Avança o contador mas aguarda próxima execução do scheduler
                order.setSearchAttempts((short) nextAttempt);
                order.setSearchRadiusKm(BigDecimal.valueOf(newRadius).setScale(2, RoundingMode.HALF_UP));
                order.setExpiresAt(now.plus(appProperties.expressProTimeoutMinutes(), ChronoUnit.MINUTES));
                orderRepository.save(order);
                log.info("event=express_expand_no_pros_yet orderId={} radius={}", order.getId(), newRadius);
            }
            return;
        }

        // Cria novos entries para os profissionais encontrados
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

        // TODO: push notification para os novos profissionais

        log.info("event=express_radius_expanded orderId={} newRadius={} newPros={} attempt={}",
                order.getId(), newRadius, newProIds.size(), nextAttempt);
    }

    private void cancelOrder(Order order, String reason, Instant now) {
        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.cancelled);
        order.setCancelledAt(now);
        order.setCancelReason(reason);
        orderRepository.save(order);
        recordTransition(order.getId(), previous, OrderStatus.cancelled, reason, null);
    }

    private Order findActive(UUID id) {
        return orderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
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

    private String serializeAddress(SavedAddress address) {
        try {
            return objectMapper.writeValueAsString(new AddressSnapshot(
                    address.getLabel(), address.getStreet(), address.getNumber(),
                    address.getComplement(), address.getDistrict(), address.getCity(),
                    address.getState(), address.getZipCode(), address.getLat(), address.getLng()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Erro ao serializar endereço", e);
        }
    }

    private record AddressSnapshot(
            String label, String street, String number, String complement,
            String district, String city, String state, String zipCode,
            java.math.BigDecimal lat, java.math.BigDecimal lng
    ) {}
}
