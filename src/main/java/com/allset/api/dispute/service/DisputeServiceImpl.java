package com.allset.api.dispute.service;

import com.allset.api.chat.repository.ConversationRepository;
import com.allset.api.chat.service.MessageService;
import com.allset.api.dispute.domain.Dispute;
import com.allset.api.dispute.domain.DisputeEvidence;
import com.allset.api.dispute.domain.DisputeResolution;
import com.allset.api.dispute.domain.DisputeStatus;
import com.allset.api.dispute.domain.EvidenceType;
import com.allset.api.dispute.dto.AddTextEvidenceRequest;
import com.allset.api.dispute.dto.DisputeEvidenceResponse;
import com.allset.api.dispute.dto.DisputeResponse;
import com.allset.api.dispute.dto.OpenDisputeRequest;
import com.allset.api.dispute.dto.ResolveDisputeRequest;
import com.allset.api.dispute.exception.DisputeAlreadyExistsException;
import com.allset.api.dispute.exception.DisputeNotFoundException;
import com.allset.api.dispute.exception.DisputeStatusTransitionException;
import com.allset.api.dispute.exception.DisputeWindowExpiredException;
import com.allset.api.dispute.mapper.DisputeEvidenceMapper;
import com.allset.api.dispute.mapper.DisputeMapper;
import com.allset.api.dispute.repository.DisputeEvidenceRepository;
import com.allset.api.dispute.repository.DisputeRepository;
import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.service.NotificationService;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.domain.OrderStatusHistory;
import com.allset.api.order.exception.OrderNotFoundException;
import com.allset.api.order.exception.OrderStatusTransitionException;
import com.allset.api.order.repository.OrderRepository;
import com.allset.api.order.repository.OrderStatusHistoryRepository;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.shared.storage.domain.StorageBucket;
import com.allset.api.shared.storage.domain.StoredObject;
import com.allset.api.shared.storage.service.StorageService;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DisputeServiceImpl implements DisputeService {

    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_PROFESSIONAL = "professional";

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository evidenceRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ProfessionalRepository professionalRepository;
    private final ConversationRepository conversationRepository;
    private final MessageService messageService;
    private final NotificationService notificationService;
    private final StorageService storageService;
    private final DisputeMapper disputeMapper;
    private final DisputeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────
    // Abertura
    // ─────────────────────────────────────────

    @Override
    public DisputeResponse openDispute(UUID orderId, UUID clientId, OpenDisputeRequest request) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Ownership: 404 (nao 403) para nao vazar existencia
        if (!order.getClientId().equals(clientId)) {
            throw new OrderNotFoundException(orderId);
        }
        if (order.getStatus() != OrderStatus.completed_by_pro) {
            throw new OrderStatusTransitionException(order.getStatus(), "abertura de disputa");
        }
        if (order.getDisputeDeadline() == null || Instant.now().isAfter(order.getDisputeDeadline())) {
            throw new DisputeWindowExpiredException();
        }
        if (disputeRepository.existsByOrderId(orderId)) {
            throw new DisputeAlreadyExistsException(orderId);
        }

        Instant now = Instant.now();
        Dispute dispute = Dispute.builder()
                .orderId(orderId)
                .openedBy(clientId)
                .reason(request.reason())
                .status(DisputeStatus.open)
                .openedAt(now)
                .build();
        Dispute savedDispute = disputeRepository.save(dispute);

        // Transita o pedido para disputed
        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.disputed);
        orderRepository.save(order);
        recordOrderTransition(orderId, previous, OrderStatus.disputed,
                "Disputa aberta: " + request.reason(), clientId);

        // Mensagem de sistema na conversa do pedido
        sendSystemMessageForOrder(orderId,
                "Disputa aberta pelo cliente. Motivo: " + request.reason());

        // Notifica o profissional
        UUID professionalUserId = resolveProfessionalUserId(order);
        if (professionalUserId != null) {
            notificationService.notifyUser(
                    professionalUserId,
                    NotificationType.dispute_opened,
                    "Disputa aberta",
                    "O cliente abriu uma disputa para o pedido. Acesse para enviar evidencias.",
                    disputeData(savedDispute.getId(), orderId)
            );
        }

        log.info("event=dispute_opened disputeId={} orderId={} clientId={}",
                savedDispute.getId(), orderId, clientId);
        return disputeMapper.toResponse(savedDispute, false);
    }

    // ─────────────────────────────────────────
    // Leitura
    // ─────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public DisputeResponse getById(UUID disputeId, UUID requesterId, String requesterRole) {
        Dispute dispute = findActiveDispute(disputeId);
        Order order = orderRepository.findByIdAndDeletedAtIsNull(dispute.getOrderId())
                .orElseThrow(() -> new DisputeNotFoundException(disputeId));

        boolean isAdmin = ROLE_ADMIN.equals(requesterRole);
        if (!isAdmin && !isParticipant(order, requesterId, requesterRole)) {
            throw new DisputeNotFoundException(disputeId);
        }
        return disputeMapper.toResponse(dispute, isAdmin);
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeResponse getByOrderId(UUID orderId, UUID requesterId, String requesterRole) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        boolean isAdmin = ROLE_ADMIN.equals(requesterRole);
        if (!isAdmin && !isParticipant(order, requesterId, requesterRole)) {
            throw new OrderNotFoundException(orderId);
        }

        Dispute dispute = disputeRepository.findByOrderIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new DisputeNotFoundException("Pedido nao possui disputa: " + orderId));
        return disputeMapper.toResponse(dispute, isAdmin);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DisputeResponse> listAll(DisputeStatus status, Pageable pageable) {
        Page<Dispute> page = (status != null)
                ? disputeRepository.findAllByStatusAndDeletedAtIsNull(status, pageable)
                : disputeRepository.findAllByDeletedAtIsNull(pageable);
        return page.map(d -> disputeMapper.toResponse(d, true));
    }

    // ─────────────────────────────────────────
    // Acoes admin — under_review e resolve
    // ─────────────────────────────────────────

    @Override
    public DisputeResponse markUnderReview(UUID disputeId, UUID adminId) {
        Dispute dispute = findActiveDispute(disputeId);
        if (dispute.getStatus() != DisputeStatus.open) {
            throw new DisputeStatusTransitionException(dispute.getStatus(), "marcar em analise");
        }

        dispute.setStatus(DisputeStatus.under_review);
        Dispute saved = disputeRepository.save(dispute);

        Order order = orderRepository.findByIdAndDeletedAtIsNull(dispute.getOrderId())
                .orElseThrow(() -> new DisputeNotFoundException(disputeId));

        sendSystemMessageForOrder(order.getId(),
                "Um administrador esta analisando a disputa.");

        notifyParties(order, saved.getId(),
                NotificationType.request_status_update,
                "Disputa em analise",
                "Um administrador comecou a analisar a disputa do seu pedido.");

        log.info("event=dispute_under_review disputeId={} adminId={}", disputeId, adminId);
        return disputeMapper.toResponse(saved, true);
    }

    @Override
    public DisputeResponse resolve(UUID disputeId, UUID adminId, ResolveDisputeRequest request) {
        Dispute dispute = findActiveDispute(disputeId);
        if (dispute.getStatus() == DisputeStatus.resolved) {
            throw new DisputeStatusTransitionException(dispute.getStatus(), "resolucao");
        }

        Order order = orderRepository.findByIdAndDeletedAtIsNull(dispute.getOrderId())
                .orElseThrow(() -> new DisputeNotFoundException(disputeId));

        if (order.getTotalAmount() == null) {
            throw new IllegalStateException(
                    "Pedido sem total_amount definido — nao e possivel calcular resolucao");
        }
        BigDecimal total = order.getTotalAmount();

        BigDecimal clientRefund;
        BigDecimal proAmount;
        DisputeResolution resolution = request.resolution();
        switch (resolution) {
            case refund_full -> {
                clientRefund = total;
                proAmount = BigDecimal.ZERO;
            }
            case release_to_pro -> {
                clientRefund = BigDecimal.ZERO;
                proAmount = total;
            }
            case refund_partial -> {
                if (request.clientRefundAmount() == null || request.professionalAmount() == null) {
                    throw new IllegalArgumentException(
                            "Resolucao parcial exige clientRefundAmount e professionalAmount");
                }
                BigDecimal sum = request.clientRefundAmount().add(request.professionalAmount());
                if (sum.compareTo(total) != 0) {
                    throw new IllegalArgumentException(
                            "A soma dos valores deve ser exatamente igual ao total do pedido ("
                                    + total + ")");
                }
                clientRefund = request.clientRefundAmount();
                proAmount = request.professionalAmount();
            }
            default -> throw new IllegalArgumentException("Resolucao invalida: " + resolution);
        }

        Instant now = Instant.now();
        dispute.setResolution(resolution);
        dispute.setClientRefundAmount(clientRefund);
        dispute.setProfessionalAmount(proAmount);
        dispute.setAdminNotes(request.adminNotes());
        dispute.setResolvedBy(adminId);
        dispute.setResolvedAt(now);
        dispute.setStatus(DisputeStatus.resolved);
        Dispute saved = disputeRepository.save(dispute);

        sendSystemMessageForOrder(order.getId(),
                "Disputa resolvida pelo administrador. " + describeResolution(resolution, clientRefund, proAmount));

        notifyParties(order, saved.getId(),
                NotificationType.dispute_resolved,
                "Disputa resolvida",
                "A disputa do seu pedido foi resolvida. Acesse para ver os detalhes.");

        // TODO: integrar com modulo payment para processar reembolso/liberacao
        log.info("event=dispute_resolved disputeId={} adminId={} resolution={} clientRefund={} proAmount={}",
                disputeId, adminId, resolution, clientRefund, proAmount);
        return disputeMapper.toResponse(saved, true);
    }

    // ─────────────────────────────────────────
    // Evidencias
    // ─────────────────────────────────────────

    @Override
    public DisputeEvidenceResponse addTextEvidence(UUID disputeId, UUID senderId, String senderRole,
                                                    AddTextEvidenceRequest request) {
        Dispute dispute = findActiveDispute(disputeId);
        ensureCanReceiveEvidence(dispute);
        ensureSenderAllowed(dispute, senderId, senderRole);

        DisputeEvidence evidence = DisputeEvidence.builder()
                .disputeId(disputeId)
                .senderId(senderId)
                .evidenceType(EvidenceType.text)
                .content(request.content())
                .sentAt(Instant.now())
                .build();
        DisputeEvidence saved = evidenceRepository.save(evidence);

        log.info("event=dispute_evidence_text_added disputeId={} senderId={} evidenceId={}",
                disputeId, senderId, saved.getId());
        return evidenceMapper.toResponse(saved);
    }

    @Override
    public DisputeEvidenceResponse addPhotoEvidence(UUID disputeId, UUID senderId, String senderRole,
                                                     MultipartFile file, String caption) {
        Dispute dispute = findActiveDispute(disputeId);
        ensureCanReceiveEvidence(dispute);
        ensureSenderAllowed(dispute, senderId, senderRole);

        StoredObject stored = storageService.upload(
                StorageBucket.DISPUTE_EVIDENCES, disputeId.toString(), file);

        DisputeEvidence evidence = DisputeEvidence.builder()
                .disputeId(disputeId)
                .senderId(senderId)
                .evidenceType(EvidenceType.photo)
                .content(caption)
                .fileKey(stored.key())
                .fileSizeBytes(stored.sizeBytes())
                .fileMimeType(stored.contentType())
                .sentAt(Instant.now())
                .build();
        DisputeEvidence saved = evidenceRepository.save(evidence);

        log.info("event=dispute_evidence_photo_added disputeId={} senderId={} evidenceId={} key={}",
                disputeId, senderId, saved.getId(), stored.key());
        return evidenceMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeEvidenceResponse> listEvidences(UUID disputeId, UUID requesterId, String requesterRole) {
        Dispute dispute = findActiveDispute(disputeId);
        Order order = orderRepository.findByIdAndDeletedAtIsNull(dispute.getOrderId())
                .orElseThrow(() -> new DisputeNotFoundException(disputeId));

        if (!ROLE_ADMIN.equals(requesterRole) && !isParticipant(order, requesterId, requesterRole)) {
            throw new DisputeNotFoundException(disputeId);
        }

        List<DisputeEvidence> evidences =
                evidenceRepository.findAllByDisputeIdAndDeletedAtIsNullOrderBySentAtAsc(disputeId);
        return evidenceMapper.toResponseList(evidences);
    }

    // ─────────────────────────────────────────
    // Helpers privados
    // ─────────────────────────────────────────

    private Dispute findActiveDispute(UUID disputeId) {
        return disputeRepository.findByIdAndDeletedAtIsNull(disputeId)
                .orElseThrow(() -> new DisputeNotFoundException(disputeId));
    }

    private void ensureCanReceiveEvidence(Dispute dispute) {
        if (dispute.getStatus() == DisputeStatus.resolved) {
            throw new DisputeStatusTransitionException(
                    "Nao e possivel adicionar evidencias a uma disputa ja resolvida");
        }
    }

    /**
     * Valida se o sender pode anexar evidencia: cliente do pedido,
     * profissional do pedido (via user_id) ou admin.
     */
    private void ensureSenderAllowed(Dispute dispute, UUID senderId, String senderRole) {
        if (ROLE_ADMIN.equals(senderRole)) {
            return;
        }
        Order order = orderRepository.findByIdAndDeletedAtIsNull(dispute.getOrderId())
                .orElseThrow(() -> new DisputeNotFoundException(dispute.getId()));
        if (!isParticipant(order, senderId, senderRole)) {
            // 404 para nao vazar existencia
            throw new DisputeNotFoundException(dispute.getId());
        }
    }

    private boolean isParticipant(Order order, UUID userId, String role) {
        if (order.getClientId().equals(userId)) {
            return true;
        }
        if (ROLE_PROFESSIONAL.equals(role) && order.getProfessionalId() != null) {
            UUID professionalUserId = professionalRepository
                    .findByIdAndDeletedAtIsNull(order.getProfessionalId())
                    .map(p -> p.getUserId())
                    .orElse(null);
            return userId.equals(professionalUserId);
        }
        return false;
    }

    private UUID resolveProfessionalUserId(Order order) {
        if (order.getProfessionalId() == null) {
            return null;
        }
        return professionalRepository.findByIdAndDeletedAtIsNull(order.getProfessionalId())
                .map(p -> p.getUserId())
                .orElse(null);
    }

    private void recordOrderTransition(UUID orderId, OrderStatus from, OrderStatus to,
                                       String reason, UUID changedBy) {
        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .changedBy(changedBy)
                .build());
    }

    /**
     * Envia mensagem de sistema na conversa do pedido. Se a conversa nao existir
     * (caso defensivo — toda transicao para accepted cria conversa), apenas loga.
     */
    private void sendSystemMessageForOrder(UUID orderId, String content) {
        Optional<UUID> conversationId = conversationRepository.findByOrderId(orderId)
                .map(c -> c.getId());
        if (conversationId.isPresent()) {
            messageService.sendSystemMessage(conversationId.get(), content);
        } else {
            log.warn("event=dispute_system_message_skipped orderId={} reason=no_conversation", orderId);
        }
    }

    private void notifyParties(Order order, UUID disputeId, NotificationType type,
                               String title, String body) {
        JsonNode data = disputeData(disputeId, order.getId());

        notificationService.notifyUser(order.getClientId(), type, title, body, data);

        UUID professionalUserId = resolveProfessionalUserId(order);
        if (professionalUserId != null) {
            notificationService.notifyUser(professionalUserId, type, title, body, data);
        }
    }

    private JsonNode disputeData(UUID disputeId, UUID orderId) {
        var data = objectMapper.createObjectNode();
        data.put("disputeId", disputeId.toString());
        data.put("orderId", orderId.toString());
        return data;
    }

    private String describeResolution(DisputeResolution resolution,
                                      BigDecimal clientRefund, BigDecimal proAmount) {
        return switch (resolution) {
            case refund_full -> "Reembolso integral ao cliente: R$ " + clientRefund;
            case release_to_pro -> "Valor liberado ao profissional: R$ " + proAmount;
            case refund_partial -> "Resolucao parcial — cliente: R$ " + clientRefund
                    + " | profissional: R$ " + proAmount;
        };
    }
}
