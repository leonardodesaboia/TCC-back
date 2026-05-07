package com.allset.api.chat.service;

import com.allset.api.chat.domain.Conversation;
import com.allset.api.chat.dto.ConversationResponse;
import com.allset.api.chat.dto.ConversationSummaryResponse;
import com.allset.api.chat.dto.MessageResponse;
import com.allset.api.chat.exception.ConversationNotFoundException;
import com.allset.api.chat.mapper.ConversationMapper;
import com.allset.api.chat.mapper.MessageMapper;
import com.allset.api.chat.repository.ConversationRepository;
import com.allset.api.chat.repository.MessageRepository;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.repository.OrderRepository;
import com.allset.api.professional.service.ProfessionalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ProfessionalService professionalService;
    private final OrderRepository orderRepository;

    @Override
    public Conversation createForOrder(Order order) {
        if (order.getStatus() != OrderStatus.accepted) {
            throw new IllegalArgumentException(
                    "Conversa só pode ser criada para pedidos com status 'accepted'");
        }
        if (order.getProfessionalId() == null) {
            throw new IllegalArgumentException(
                    "Pedido sem profissional atribuído — impossível criar conversa");
        }
        if (conversationRepository.existsByOrderId(order.getId())) {
            throw new IllegalArgumentException(
                    "Conversa já existe para o pedido: " + order.getId());
        }

        UUID professionalUserId = professionalService.findById(order.getProfessionalId()).userId();

        Conversation conversation = Conversation.builder()
                .orderId(order.getId())
                .clientId(order.getClientId())
                .professionalUserId(professionalUserId)
                .build();

        Conversation saved;
        try {
            saved = conversationRepository.save(conversation);
        } catch (DataIntegrityViolationException e) {
            // Race condition: outra thread criou a conversa entre o existsByOrderId e o save.
            // A constraint UNIQUE de order_id captura isso; retornamos a conversa já existente.
            log.warn("event=conversation_duplicate_ignored orderId={}", order.getId());
            return conversationRepository.findByOrderId(order.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Conversa não encontrada após violação de constraint: " + order.getId()));
        }
        log.info("event=conversation_created orderId={} conversationId={}",
                order.getId(), saved.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getById(UUID id, UUID currentUserId) {
        Conversation conversation = requireParticipant(id, currentUserId);
        String orderStatus = orderRepository.findByIdAndDeletedAtIsNull(conversation.getOrderId())
                .map(order -> order.getStatus().name())
                .orElse(null);
        return conversationMapper.toResponse(conversation, orderStatus);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConversationSummaryResponse> listForUser(UUID currentUserId, Pageable pageable) {
        return conversationRepository.findAllForParticipant(currentUserId, pageable)
                .map(conv -> {
                    MessageResponse lastMessage = messageRepository
                            .findTopByConversationIdAndDeletedAtIsNullOrderBySentAtDesc(conv.getId())
                            .map(messageMapper::toResponse)
                            .orElse(null);

                    long unreadCount = messageRepository
                            .countByConversationIdAndSenderIdNotAndReadAtIsNull(
                                    conv.getId(), currentUserId);

                    UUID otherParticipantId = conv.getClientId().equals(currentUserId)
                            ? conv.getProfessionalUserId()
                            : conv.getClientId();

                    return new ConversationSummaryResponse(
                            conv.getId(),
                            conv.getOrderId(),
                            otherParticipantId,
                            lastMessage,
                            unreadCount
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Conversation requireParticipant(UUID conversationId, UUID userId) {
        return conversationRepository.findByIdAndParticipant(conversationId, userId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }
}
