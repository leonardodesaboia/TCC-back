package com.allset.api.order.service;

import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.chat.domain.Conversation;
import com.allset.api.chat.service.ConversationService;
import com.allset.api.chat.service.MessageService;
import com.allset.api.config.AppProperties;
import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.service.NotificationService;
import com.allset.api.order.domain.*;
import com.allset.api.order.dto.*;
import com.allset.api.order.exception.*;
import com.allset.api.order.repository.ExpressQueueRepository.NearbyProfessional;
import com.allset.api.order.mapper.OrderMapper;
import com.allset.api.order.repository.*;
import com.allset.api.offering.domain.PricingType;
import com.allset.api.offering.domain.ProfessionalOffering;
import com.allset.api.offering.repository.ProfessionalOfferingRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.exception.ProfessionalNotApprovedException;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.professional.repository.ProfessionalSpecialtyRepository;
import com.allset.api.user.repository.UserRepository;
import com.allset.api.integration.storage.domain.StorageBucket;
import com.allset.api.integration.storage.domain.StoredObject;
import com.allset.api.integration.storage.service.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private final ProfessionalSpecialtyRepository specialtyRepository;
    private final ProfessionalOfferingRepository offeringRepository;
    private final UserRepository             userRepository;
    private final OrderMapper                orderMapper;
    private final ObjectMapper               objectMapper;
    private final AppProperties              appProperties;
    private final ExpressWindowProcessor     expressWindowProcessor;
    private final ConversationService        conversationService;
    private final MessageService             messageService;
    private final NotificationService        notificationService;
    private final StorageService             storageService;

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
        int radius    = appProperties.expressSearchRadiusMeters();

        // Busca profissionais próximos com snapshot da distância (todos notificados ao mesmo tempo)
        List<NearbyProfessional> nearby = queueRepository.findNearbyProfessionals(
                request.categoryId(), lat, lng, radius, appProperties.expressMaxQueueSize());

        if (nearby.isEmpty()) {
            throw new NoProfessionalsAvailableException();
        }

        Instant now              = Instant.now();
        Instant proposalDeadline = now.plus(appProperties.expressProposalWindowMinutes(), ChronoUnit.MINUTES);
        Instant orderExpires     = proposalDeadline.plus(appProperties.expressClientWindowMinutes(), ChronoUnit.MINUTES);
        JsonNode snapshot        = serializeAddress(address);

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
                .proposalDeadline(proposalDeadline)
                .expiresAt(orderExpires)
                .build();

        Order saved = orderRepository.save(order);
        recordTransition(saved.getId(), null, OrderStatus.pending, "Pedido Express criado", null);

        // Cria entries para todos com distância snapshot — notificados simultaneamente
        List<ExpressQueueEntry> entries = new ArrayList<>();
        for (int i = 0; i < nearby.size(); i++) {
            NearbyProfessional n = nearby.get(i);
            entries.add(ExpressQueueEntry.builder()
                    .orderId(saved.getId())
                    .professionalId(n.getProfessionalId())
                    .distanceMeters(n.getDistanceMeters())
                    .queuePosition((short) (i + 1))
                    .notifiedAt(now)
                    .build());
        }
        queueRepository.saveAll(entries);

        notifyProfessionals(
                nearby.stream().map(NearbyProfessional::getProfessionalId).toList(),
                NotificationType.new_request,
                "Nova solicitação Express",
                "Há um novo pedido Express disponível para a sua categoria.",
                saved.getId()
        );

        log.info("event=express_order_created orderId={} clientId={} professionals={} radiusMeters={}",
                saved.getId(), clientId, nearby.size(), radius);

        return orderMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────
    // Criação do pedido On Demand
    // ─────────────────────────────────────────

    @Override
    public OrderResponse createOnDemandOrder(UUID clientId, CreateOnDemandOrderRequest request) {
        ProfessionalOffering offering = offeringRepository.findById(request.serviceId())
                .filter(o -> o.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Serviço não encontrado: " + request.serviceId()));

        if (!offering.isActive()) {
            throw new IllegalArgumentException("Serviço não está ativo: " + request.serviceId());
        }

        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(offering.getProfessionalId())
                .orElseThrow(() -> new IllegalArgumentException("Profissional do serviço não encontrado"));

        if (professional.getVerificationStatus() != VerificationStatus.approved) {
            throw new IllegalArgumentException("Profissional não está aprovado para receber pedidos");
        }

        SavedAddress address = addressRepository.findByIdAndUserId(request.addressId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Endereço não encontrado: " + request.addressId()));

        if (request.scheduledAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Data agendada deve ser no futuro");
        }

        UUID areaId = categoryRepository.findByIdAndDeletedAtIsNull(offering.getCategoryId())
                .map(category -> category.getAreaId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Categoria do serviço não encontrada: " + offering.getCategoryId()));

        JsonNode snapshot = serializeAddress(address);

        Integer resolvedDurationMinutes;
        BigDecimal price;

        if (offering.getPricingType() == PricingType.hourly) {
            BigDecimal hourlyRate = resolveOfferingPrice(offering);
            if (hourlyRate == null) {
                throw new IllegalArgumentException(
                        "Serviço sem taxa horária definida e sem valor/hora na especialidade vinculada");
            }
            if (offering.getEstimatedDurationMinutes() != null) {
                resolvedDurationMinutes = offering.getEstimatedDurationMinutes();
            } else {
                if (request.estimatedDurationMinutes() == null) {
                    throw new IllegalArgumentException(
                            "Duração é obrigatória para serviços por hora sem duração definida");
                }
                if (request.estimatedDurationMinutes() % 30 != 0) {
                    throw new IllegalArgumentException(
                            "Duração deve ser múltiplo de 30 minutos");
                }
                resolvedDurationMinutes = request.estimatedDurationMinutes();
            }
            price = hourlyRate
                    .multiply(BigDecimal.valueOf(resolvedDurationMinutes))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        } else {
            price = resolveOfferingPrice(offering);
            if (price == null) {
                throw new IllegalArgumentException(
                        "Serviço sem preço definido e sem valor/hora na especialidade vinculada");
            }
            resolvedDurationMinutes = offering.getEstimatedDurationMinutes();
        }

        BigDecimal fee   = price.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = price;

        Order order = Order.builder()
                .clientId(clientId)
                .professionalId(offering.getProfessionalId())
                .serviceId(offering.getId())
                .areaId(areaId)
                .categoryId(offering.getCategoryId())
                .mode(OrderMode.on_demand)
                .status(OrderStatus.pending)
                .description(request.description())
                .addressId(request.addressId())
                .addressSnapshot(snapshot)
                .scheduledAt(request.scheduledAt())
                .expiresAt(request.scheduledAt().minus(4, ChronoUnit.HOURS))
                .proposalDeadline(request.scheduledAt().minus(4, ChronoUnit.HOURS))
                .baseAmount(price)
                .platformFee(fee)
                .totalAmount(total)
                .estimatedDurationMinutes(resolvedDurationMinutes)
                .build();

        Order saved = orderRepository.save(order);
        recordTransition(saved.getId(), null, OrderStatus.pending, "Pedido On Demand criado", null);

        notifyProfessional(
                offering.getProfessionalId(),
                NotificationType.new_request,
                "Novo pedido On Demand",
                "Voce recebeu um novo pedido para o servico \"" + offering.getTitle() + "\".",
                saved.getId()
        );

        log.info("event=on_demand_order_created orderId={} clientId={} professionalId={} serviceId={}",
                saved.getId(), clientId, offering.getProfessionalId(), offering.getId());

        return orderMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────
    // Resposta do profissional (On Demand)
    // ─────────────────────────────────────────

    @Override
    public OrderResponse respondOnDemand(UUID orderId, UUID professionalId, boolean accepted) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getMode() != OrderMode.on_demand) {
            throw new OrderStatusTransitionException(order.getStatus(), "resposta on demand em pedido não on_demand");
        }
        if (order.getStatus() != OrderStatus.pending) {
            throw new OrderStatusTransitionException(order.getStatus(), "resposta do profissional");
        }
        if (!professionalId.equals(order.getProfessionalId())) {
            throw new OrderNotFoundException(orderId);
        }

        UUID professionalUserId = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .map(Professional::getUserId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!accepted) {
            Instant now = Instant.now();
            order.setCancelledAt(now);
            order.setCancelReason("Profissional recusou o pedido");
            order.setStatus(OrderStatus.cancelled);

            Order saved = orderRepository.save(order);
            recordTransition(orderId, OrderStatus.pending, OrderStatus.cancelled,
                    "Profissional recusou o pedido On Demand", professionalUserId);

            notifyClient(
                    order.getClientId(),
                    NotificationType.request_rejected,
                    "Pedido recusado",
                    "O profissional recusou o seu pedido. Voce pode tentar outro profissional.",
                    orderId
            );

            log.info("event=on_demand_rejected orderId={} professionalId={}", orderId, professionalId);
            return orderMapper.toResponse(saved);
        }

        order.setStatus(OrderStatus.accepted);
        Order saved = orderRepository.save(order);
        recordTransition(orderId, OrderStatus.pending, OrderStatus.accepted,
                "Profissional aceitou o pedido On Demand", professionalUserId);

        Conversation conv = conversationService.createForOrder(saved);
        messageService.sendSystemMessage(conv.getId(), "Pedido aceito. Vocês podem conversar por aqui.");

        notifyClient(
                order.getClientId(),
                NotificationType.request_accepted,
                "Pedido aceito",
                "O profissional aceitou o seu pedido.",
                orderId
        );

        log.info("event=on_demand_accepted orderId={} professionalId={}", orderId, professionalId);
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
        ExpressQueueEntry queueEntry = null;
        if ("professional".equals(requesterRole)) {
            UUID proId = professionalRepository.findByUserIdAndDeletedAtIsNull(requesterId)
                    .map(p -> p.getId())
                    .orElse(null);
            if (proId != null) {
                isPro    = proId.equals(order.getProfessionalId());
                queueEntry = queueRepository.findByOrderIdAndProfessionalId(orderId, proId).orElse(null);
                isInQueue = !isPro && queueEntry != null;
            }
        }

        if (!isAdmin && !isClient && !isPro && !isInQueue) {
            throw new OrderNotFoundException(orderId);
        }

        return orderMapper.toResponse(order, photoRepository.findAllByOrderId(orderId), queueEntry);
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
                .map(order -> {
                    String professionalName = null;
                    String serviceName = null;
                    if (order.getProfessionalId() != null) {
                        professionalName = professionalRepository
                                .findByIdAndDeletedAtIsNull(order.getProfessionalId())
                                .flatMap(pro -> userRepository.findByIdAndDeletedAtIsNull(pro.getUserId()))
                                .map(user -> user.getName())
                                .orElse(null);
                    }
                    if (order.getServiceId() != null) {
                        serviceName = offeringRepository
                                .findById(order.getServiceId())
                                .map(offering -> offering.getTitle())
                                .orElse(null);
                    }
                    return orderMapper.toResponse(order, null, null, professionalName, serviceName);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> listProfessionalExpressInbox(UUID userId, OrderStatus status, Pageable pageable) {
        if (status != null && status != OrderStatus.pending) {
            return Page.empty(pageable);
        }

        return professionalRepository.findByUserIdAndDeletedAtIsNull(userId)
                .map(pro -> orderRepository.findExpressInboxByProfessionalId(
                        pro.getId(),
                        OrderMode.express,
                        OrderStatus.pending,
                        ProResponse.accepted,
                        pageable
                ).map(order -> orderMapper.toResponse(
                        order,
                        queueRepository.findByOrderIdAndProfessionalId(order.getId(), pro.getId()).orElse(null)
                )))
                .orElse(Page.empty(pageable))
                ;
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
                .map(orderMapper::toProposalResponse)
                .toList();
    }

    // ─────────────────────────────────────────
    // Resposta do profissional (Express)
    // ─────────────────────────────────────────

    @Override
    public OrderResponse proRespond(UUID orderId, UUID professionalId, ProRespondRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getMode() != OrderMode.express) {
            throw new ExpressQueueViolationException("Operação exclusiva para pedidos Express");
        }
        if (order.getStatus() != OrderStatus.pending) {
            throw new OrderStatusTransitionException(order.getStatus(), "resposta do profissional");
        }

        Instant now = Instant.now();
        if (now.isAfter(order.getProposalDeadline())) {
            throw new ProposalWindowExpiredException(orderId);
        }

        // Valida que o profissional ainda está aprovado
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ExpressQueueViolationException("Profissional não encontrado"));
        if (professional.getVerificationStatus() != VerificationStatus.approved) {
            throw new ProfessionalNotApprovedException(professional.getVerificationStatus());
        }

        // Valida que este profissional está na fila e ainda não respondeu
        ExpressQueueEntry entry = queueRepository
                .findByOrderIdAndProfessionalIdForUpdate(orderId, professionalId)
                .orElseThrow(() -> new ExpressQueueViolationException(
                        "Você não foi notificado para este pedido"));

        if (entry.getProResponse() != null) {
            throw new ExpressQueueViolationException("Você já respondeu a este pedido");
        }

        entry.setRespondedAt(now);

        if (request.response() == ProResponse.rejected) {
            entry.setProResponse(ProResponse.rejected);
            queueRepository.save(entry);

            notifyClient(
                    order.getClientId(),
                    NotificationType.request_status_update,
                    "Atualizacao do pedido",
                    "Um profissional recusou o seu pedido Express. Continuaremos buscando outras opcoes.",
                    orderId,
                    professionalId
            );

            log.info("event=express_pro_rejected orderId={} professionalId={}", orderId, professionalId);
            return orderMapper.toResponse(order, entry);
        }

        // accepted — proposedAmount obrigatório
        if (request.proposedAmount() == null) {
            throw new IllegalArgumentException("Valor proposto é obrigatório ao aceitar");
        }

        entry.setProResponse(ProResponse.accepted);
        entry.setProposedAmount(request.proposedAmount());
        queueRepository.save(entry);

        notifyClient(
                order.getClientId(),
                NotificationType.request_status_update,
                "Nova proposta recebida",
                "Voce recebeu uma nova proposta para o seu pedido Express.",
                orderId,
                professionalId
        );

        log.info("event=express_pro_accepted orderId={} professionalId={} amount={}",
                orderId, professionalId, request.proposedAmount());
        return orderMapper.toResponse(
                orderRepository.findByIdAndDeletedAtIsNull(orderId).orElseThrow(),
                queueRepository.findByOrderIdAndProfessionalId(orderId, professionalId).orElse(entry)
        );
    }

    // ─────────────────────────────────────────
    // Resposta do cliente (Express) — escolhe proposta
    // ─────────────────────────────────────────

    @Override
    public OrderResponse clientRespond(UUID orderId, UUID clientId, ClientRespondRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getMode() != OrderMode.express) {
            throw new ExpressQueueViolationException("Operação exclusiva para pedidos Express");
        }
        if (!order.getClientId().equals(clientId)) {
            throw new OrderNotFoundException(orderId);
        }
        if (order.getStatus() != OrderStatus.pending) {
            throw new OrderStatusTransitionException(order.getStatus(), "escolha de proposta");
        }

        // Busca a proposta do profissional selecionado com lock pessimista para evitar race condition
        ExpressQueueEntry chosen = queueRepository
                .findByOrderIdAndProfessionalIdForUpdate(orderId, request.selectedProfessionalId())
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

        List<UUID> rejectedProfessionalIds = queueRepository.findAllByOrderIdAndProResponse(orderId, ProResponse.accepted)
                .stream()
                .map(ExpressQueueEntry::getProfessionalId)
                .filter(professionalId -> !professionalId.equals(chosen.getProfessionalId()))
                .toList();

        Instant now = Instant.now();

        // Aceita a proposta escolhida
        chosen.setClientResponse(ClientResponse.accepted);
        chosen.setClientRespondedAt(now);
        queueRepository.save(chosen);

        // Rejeita todas as outras propostas em lote
        queueRepository.rejectOtherProposals(
                orderId,
                chosen.getId(),
                ProResponse.accepted,
                ClientResponse.rejected,
                now
        );

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

        Order saved = orderRepository.save(order);
        recordTransition(orderId, OrderStatus.pending, OrderStatus.accepted,
                "Cliente escolheu proposta", clientId);

        // TODO: iniciar cobrança via módulo payment
        // Criar conversa de chat entre cliente e profissional escolhido
        Conversation conv = conversationService.createForOrder(saved);
        notifyProfessional(
                chosen.getProfessionalId(),
                NotificationType.request_accepted,
                "Pedido aceito",
                "Seu orçamento foi aceito pelo cliente.",
                orderId
        );

        if (!rejectedProfessionalIds.isEmpty()) {
            notifyProfessionals(
                    rejectedProfessionalIds,
                    NotificationType.request_rejected,
                    "Proposta não selecionada",
                    "O cliente escolheu outro profissional para este pedido.",
                    orderId
            );
        }
        messageService.sendSystemMessage(conv.getId(), "Pedido aceito. Vocês podem conversar por aqui.");

        log.info("event=express_client_chose orderId={} professionalId={} total={}",
                orderId, chosen.getProfessionalId(), total);
        return orderMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────
    // Conclusão pelo profissional
    // ─────────────────────────────────────────

    @Override
    public OrderResponse completeByPro(UUID orderId, UUID professionalId, MultipartFile file) {
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

        Instant now = Instant.now();
        order.setProCompletedAt(now);
        order.setDisputeDeadline(now.plus(24, ChronoUnit.HOURS));
        order.setStatus(OrderStatus.completed_by_pro);

        Order saved = orderRepository.save(order);

        // Upload feito após o save para que uma falha no storage não deixe o BD em estado inconsistente.
        // Em caso de falha no upload, o status já foi atualizado — um job de reconciliação pode
        // detectar pedidos completed_by_pro sem foto e alertar operações.
        StoredObject stored = storageService.upload(StorageBucket.ORDER_PHOTOS, orderId.toString(), file);

        photoRepository.save(OrderPhoto.builder()
                .orderId(orderId)
                .uploaderId(proUserId)
                .photoType(PhotoType.completion_proof)
                .storageKey(stored.key())
                .build());
        recordTransition(orderId, OrderStatus.accepted, OrderStatus.completed_by_pro,
                "Profissional marcou como concluído", proUserId);

        notifyClient(
                order.getClientId(),
                NotificationType.request_status_update,
                "Serviço marcado como concluído",
                "O profissional marcou o serviço como concluído e enviou a comprovação.",
                orderId
        );

        log.info("event=order_completed_by_pro orderId={} professionalId={}", orderId, professionalId);
        return orderMapper.toResponse(saved, photoRepository.findAllByOrderId(orderId));
    }

    @Override
    public OrderPhotoResponse uploadPhoto(UUID orderId, UUID requesterUserId, String requesterRole,
                                          PhotoType type, MultipartFile file) {
        Order order = findActive(orderId);

        boolean isAdmin = "admin".equals(requesterRole);
        boolean isClient = requesterUserId.equals(order.getClientId());
        boolean isPro = false;
        if ("professional".equals(requesterRole)) {
            UUID proId = professionalRepository.findByUserIdAndDeletedAtIsNull(requesterUserId)
                    .map(p -> p.getId())
                    .orElse(null);
            isPro = proId != null && proId.equals(order.getProfessionalId());
        }

        if (!isAdmin && !isClient && !isPro) {
            throw new OrderNotFoundException(orderId);
        }

        StoredObject stored = storageService.upload(StorageBucket.ORDER_PHOTOS, orderId.toString(), file);

        OrderPhoto saved = photoRepository.save(OrderPhoto.builder()
                .orderId(orderId)
                .uploaderId(requesterUserId)
                .photoType(type)
                .storageKey(stored.key())
                .build());

        log.info("event=order_photo_uploaded orderId={} type={} uploaderId={}", orderId, type, requesterUserId);
        return orderMapper.toPhotoResponse(saved);
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

        notifyProfessional(
                order.getProfessionalId(),
                NotificationType.request_status_update,
                "Servico confirmado",
                "O cliente confirmou a conclusao do servico.",
                orderId
        );

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
        boolean isPro    = professionalRepository.findByUserIdAndDeletedAtIsNull(requesterId)
                .map(Professional::getId)
                .map(professionalId -> professionalId.equals(order.getProfessionalId()))
                .orElse(false);

        if (!isClient && !isPro) {
            throw new OrderNotFoundException(orderId);
        }

        OrderStatus current = order.getStatus();
        if (current == OrderStatus.completed || current == OrderStatus.cancelled
                || current == OrderStatus.disputed || current == OrderStatus.completed_by_pro) {
            throw new OrderStatusTransitionException(current, "cancelamento");
        }

        Instant now = Instant.now();
        order.setCancelledAt(now);
        order.setCancelReason(request.reason());
        order.setStatus(OrderStatus.cancelled);

        Order saved = orderRepository.save(order);
        recordTransition(orderId, current, OrderStatus.cancelled, request.reason(), requesterId);

        if (isClient) {
            if (order.getProfessionalId() != null) {
                notifyProfessional(
                        order.getProfessionalId(),
                        NotificationType.request_status_update,
                        "Pedido cancelado",
                        "O cliente cancelou o pedido.",
                        orderId
                );
            } else if (order.getMode() == OrderMode.express) {
                List<UUID> proposedProfessionalIds = queueRepository.findAllByOrderIdAndProResponse(orderId, ProResponse.accepted)
                        .stream()
                        .map(ExpressQueueEntry::getProfessionalId)
                        .toList();

                notifyProfessionals(
                        proposedProfessionalIds,
                        NotificationType.request_status_update,
                        "Pedido cancelado",
                        "O cliente cancelou o pedido antes da seleção final.",
                        orderId
                );
            }
        }

        if (isPro) {
            notifyClient(
                    order.getClientId(),
                    NotificationType.request_status_update,
                    "Pedido cancelado",
                    "O profissional cancelou o pedido.",
                    orderId
            );
        }

        log.info("event=order_cancelled orderId={} by={}", orderId, requesterId);
        return orderMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────
    // Scheduler — processamento de janelas expiradas
    // ─────────────────────────────────────────

    @Override
    public void processExpiredWindows() {
        Instant now = Instant.now();

        // Fase 1 → 2: marca timeouts em pedidos cuja janela de propostas venceu
        List<UUID> ordersToCloseProposalWindow = orderRepository
                .findExpressIdsWithExpiredProposalWindow(now);

        for (UUID orderId : ordersToCloseProposalWindow) {
            try {
                expressWindowProcessor.closeProposalWindow(orderId, now);
            } catch (Exception e) {
                log.error("event=express_close_window_error orderId={} error={}", orderId, e.getMessage(), e);
            }
        }

        // Janela total expirada → cancela
        List<UUID> expiredIds = orderRepository.findExpressIdsToExpire(now);

        for (UUID orderId : expiredIds) {
            try {
                expressWindowProcessor.cancelExpiredOrder(orderId, now);
            } catch (Exception e) {
                log.error("event=express_cancel_expired_error orderId={} error={}", orderId, e.getMessage(), e);
            }
        }
    }

    // ─────────────────────────────────────────
    // Privados
    // ─────────────────────────────────────────

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

    private void notifyClient(UUID clientId, NotificationType type, String title, String body, UUID orderId) {
        notificationService.notifyUser(clientId, type, title, body, orderData(orderId));
    }

    private void notifyClient(UUID clientId, NotificationType type, String title, String body,
                              UUID orderId, UUID professionalId) {
        notificationService.notifyUser(clientId, type, title, body, orderAndProfessionalData(orderId, professionalId));
    }

    private void notifyProfessional(UUID professionalId, NotificationType type, String title, String body, UUID orderId) {
        UUID professionalUserId = findProfessionalUserId(professionalId);
        if (professionalUserId == null) {
            return;
        }

        notificationService.notifyUser(professionalUserId, type, title, body, orderData(orderId));
    }

    private void notifyProfessionals(List<UUID> professionalIds, NotificationType type, String title, String body, UUID orderId) {
        if (professionalIds == null || professionalIds.isEmpty()) {
            return;
        }

        List<UUID> professionalUserIds = professionalRepository.findAllById(professionalIds).stream()
                .map(professional -> professional.getUserId())
                .toList();

        notificationService.notifyUsers(professionalUserIds, type, title, body, orderData(orderId));
    }

    private UUID findProfessionalUserId(UUID professionalId) {
        return professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .map(professional -> professional.getUserId())
                .orElse(null);
    }

    private JsonNode orderData(UUID orderId) {
        var data = objectMapper.createObjectNode();
        data.put("orderId", orderId.toString());
        return data;
    }

    private JsonNode orderAndProfessionalData(UUID orderId, UUID professionalId) {
        var data = objectMapper.createObjectNode();
        data.put("orderId", orderId.toString());
        data.put("professionalId", professionalId.toString());
        return data;
    }

    private JsonNode serializeAddress(SavedAddress address) {
        return objectMapper.valueToTree(new AddressSnapshot(
                address.getLabel(), address.getStreet(), address.getNumber(),
                address.getComplement(), address.getDistrict(), address.getCity(),
                address.getState(), address.getZipCode(), address.getLat(), address.getLng()
        ));
    }

    private BigDecimal resolveOfferingPrice(ProfessionalOffering offering) {
        if (offering.getPrice() != null) {
            return offering.getPrice();
        }
        if (offering.getPricingType() == PricingType.hourly) {
            return specialtyRepository
                    .findByProfessionalIdAndCategoryIdAndDeletedAtIsNull(
                            offering.getProfessionalId(), offering.getCategoryId())
                    .map(s -> s.getHourlyRate())
                    .orElse(null);
        }
        return null;
    }

    private record AddressSnapshot(
            String label, String street, String number, String complement,
            String district, String city, String state, String zipCode,
            java.math.BigDecimal lat, java.math.BigDecimal lng
    ) {}
}
