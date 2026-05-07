package com.allset.api.chat.service;

import com.allset.api.chat.domain.Conversation;
import com.allset.api.chat.domain.Message;
import com.allset.api.chat.domain.MessageType;
import com.allset.api.chat.dto.ConversationSummaryResponse;
import com.allset.api.chat.repository.ConversationRepository;
import com.allset.api.chat.repository.MessageRepository;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderMode;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.dto.ProfessionalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private com.allset.api.chat.mapper.ConversationMapper conversationMapper;

    @Mock
    private com.allset.api.chat.mapper.MessageMapper messageMapper;

    @Mock
    private com.allset.api.professional.service.ProfessionalService professionalService;

    @InjectMocks
    private ConversationServiceImpl conversationService;

    @Test
    void createForOrderShouldPersistConversationForAcceptedOrder() {
        UUID orderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        UUID professionalUserId = UUID.randomUUID();

        Order order = Order.builder()
                .clientId(clientId)
                .professionalId(professionalId)
                .mode(OrderMode.express)
                .status(OrderStatus.accepted)
                .description("Servico")
                .addressId(UUID.randomUUID())
                .addressSnapshot(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode())
                .expiresAt(Instant.now())
                .build();
        order.setId(orderId);

        when(conversationRepository.existsByOrderId(orderId)).thenReturn(false);
        when(professionalService.findById(professionalId)).thenReturn(new ProfessionalResponse(
                professionalId,
                professionalUserId,
                "Profissional",
                null,
                "Bio",
                (short) 5,
                new BigDecimal("80.00"),
                List.of(),
                VerificationStatus.approved,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                0L,
                Instant.now(),
                Instant.now()
        ));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        Conversation saved = conversationService.createForOrder(order);

        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getClientId()).isEqualTo(clientId);
        assertThat(saved.getProfessionalUserId()).isEqualTo(professionalUserId);
    }

    @Test
    void createForOrderShouldReturnExistingConversationOnUniqueConstraintRace() {
        UUID orderId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        UUID existingConversationId = UUID.randomUUID();

        Order order = Order.builder()
                .clientId(UUID.randomUUID())
                .professionalId(professionalId)
                .mode(OrderMode.express)
                .status(OrderStatus.accepted)
                .description("Servico")
                .addressId(UUID.randomUUID())
                .addressSnapshot(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode())
                .expiresAt(Instant.now())
                .build();
        order.setId(orderId);

        Conversation existing = Conversation.builder()
                .orderId(orderId)
                .clientId(order.getClientId())
                .professionalUserId(UUID.randomUUID())
                .build();
        existing.setId(existingConversationId);

        when(conversationRepository.existsByOrderId(orderId)).thenReturn(false);
        when(professionalService.findById(professionalId)).thenReturn(new ProfessionalResponse(
                professionalId,
                existing.getProfessionalUserId(),
                "Profissional",
                null,
                "Bio",
                (short) 5,
                new BigDecimal("80.00"),
                List.of(),
                VerificationStatus.approved,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                0L,
                Instant.now(),
                Instant.now()
        ));
        when(conversationRepository.save(any(Conversation.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(conversationRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        Conversation result = conversationService.createForOrder(order);

        assertThat(result.getId()).isEqualTo(existingConversationId);
    }

    @Test
    void listForUserShouldIncludeLastMessageAndUnreadCount() {
        UUID currentUserId = UUID.randomUUID();
        UUID otherParticipantId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        Conversation conversation = Conversation.builder()
                .orderId(UUID.randomUUID())
                .clientId(currentUserId)
                .professionalUserId(otherParticipantId)
                .build();
        conversation.setId(conversationId);

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(otherParticipantId)
                .msgType(MessageType.text)
                .content("Ola")
                .sentAt(Instant.now())
                .build();
        message.setId(UUID.randomUUID());

        when(conversationRepository.findAllForParticipant(currentUserId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(conversation)));
        when(messageRepository.findTopByConversationIdAndDeletedAtIsNullOrderBySentAtDesc(conversationId))
                .thenReturn(Optional.of(message));
        when(messageMapper.toResponse(message)).thenReturn(new com.allset.api.chat.dto.MessageResponse(
                message.getId(), conversationId, otherParticipantId,
                MessageType.text, "Ola", null, null, null,
                message.getSentAt(), null, null
        ));
        when(messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(conversationId, currentUserId))
                .thenReturn(2L);

        var page = conversationService.listForUser(currentUserId, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        ConversationSummaryResponse summary = page.getContent().getFirst();
        assertThat(summary.otherParticipantId()).isEqualTo(otherParticipantId);
        assertThat(summary.unreadCount()).isEqualTo(2L);
        assertThat(summary.lastMessage().content()).isEqualTo("Ola");
    }
}
