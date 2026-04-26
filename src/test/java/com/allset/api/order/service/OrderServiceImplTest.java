package com.allset.api.order.service;

import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.catalog.domain.ServiceCategory;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.chat.domain.Conversation;
import com.allset.api.chat.service.ConversationService;
import com.allset.api.chat.service.MessageService;
import com.allset.api.config.AppProperties;
import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.service.NotificationService;
import com.allset.api.order.domain.*;
import com.allset.api.order.dto.ClientRespondRequest;
import com.allset.api.order.dto.CreateExpressOrderRequest;
import com.allset.api.order.dto.OrderResponse;
import com.allset.api.order.exception.NoProfessionalsAvailableException;
import com.allset.api.order.mapper.OrderMapper;
import com.allset.api.order.repository.*;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.shared.storage.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private OrderPhotoRepository photoRepository;

    @Mock
    private ExpressQueueRepository queueRepository;

    @Mock
    private ServiceCategoryRepository categoryRepository;

    @Mock
    private SavedAddressRepository addressRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private AppProperties appProperties;

    @Mock
    private ExpressWindowProcessor expressWindowProcessor;

    @Mock
    private ConversationService conversationService;

    @Mock
    private MessageService messageService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private StorageService storageService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void createExpressOrderShouldRejectWhenNoProfessionalsAreAvailable() {
        UUID clientId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        SavedAddress address = address(clientId);

        when(categoryRepository.findByIdAndDeletedAtIsNull(categoryId)).thenReturn(Optional.of(ServiceCategory.builder()
                .areaId(UUID.randomUUID())
                .name("Eletrica")
                .active(true)
                .build()));
        when(addressRepository.findByIdAndUserId(address.getId(), clientId)).thenReturn(Optional.of(address));
        when(appProperties.expressSearchRadiusKm()).thenReturn(15.0);
        when(appProperties.expressMaxQueueSize()).thenReturn(10);
        when(queueRepository.findNearbyProfessionalIds(categoryId, -3.731862, -38.526669, 15.0, 10))
                .thenReturn(List.of());

        CreateExpressOrderRequest request = new CreateExpressOrderRequest(
                UUID.randomUUID(),
                categoryId,
                "Trocar tomada",
                address.getId(),
                new BigDecimal("15.00")
        );

        assertThatThrownBy(() -> orderService.createExpressOrder(clientId, request))
                .isInstanceOf(NoProfessionalsAvailableException.class);
    }

    @Test
    void createExpressOrderShouldPersistQueueAndNotifyProfessionals() {
        UUID clientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID pro1 = UUID.randomUUID();
        UUID pro2 = UUID.randomUUID();
        UUID proUser1 = UUID.randomUUID();
        UUID proUser2 = UUID.randomUUID();
        SavedAddress address = address(clientId);

        when(categoryRepository.findByIdAndDeletedAtIsNull(categoryId)).thenReturn(Optional.of(ServiceCategory.builder()
                .areaId(areaId)
                .name("Eletrica")
                .active(true)
                .build()));
        when(addressRepository.findByIdAndUserId(address.getId(), clientId)).thenReturn(Optional.of(address));
        when(appProperties.expressSearchRadiusKm()).thenReturn(15.0);
        when(appProperties.expressMaxQueueSize()).thenReturn(10);
        when(appProperties.expressProTimeoutMinutes()).thenReturn(10);
        when(queueRepository.findNearbyProfessionalIds(categoryId, -3.731862, -38.526669, 15.0, 10))
                .thenReturn(List.of(pro1, pro2));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(orderId);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(professionalRepository.findAllById(List.of(pro1, pro2))).thenReturn(List.of(
                professional(pro1, proUser1),
                professional(pro2, proUser2)
        ));
        when(orderMapper.toResponse(any(Order.class))).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        OrderResponse response = orderService.createExpressOrder(clientId, new CreateExpressOrderRequest(
                areaId,
                categoryId,
                "Trocar tomada",
                address.getId(),
                new BigDecimal("15.00")
        ));

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(OrderStatus.pending);

        ArgumentCaptor<List<ExpressQueueEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(queueRepository).saveAll(entriesCaptor.capture());
        assertThat(entriesCaptor.getValue()).hasSize(2);
        assertThat(entriesCaptor.getValue().get(0).getQueuePosition()).isEqualTo((short) 1);
        assertThat(entriesCaptor.getValue().get(1).getQueuePosition()).isEqualTo((short) 2);

        verify(notificationService).notifyUsers(
                List.of(proUser1, proUser2),
                NotificationType.new_request,
                "Nova solicitacao Express",
                "Ha um novo pedido Express disponivel para a sua categoria.",
                objectMapper.createObjectNode().put("orderId", orderId.toString())
        );
    }

    @Test
    void clientRespondShouldChooseProposalCreateConversationAndNotifyParticipants() {
        UUID orderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID chosenProfessionalId = UUID.randomUUID();
        UUID chosenProfessionalUserId = UUID.randomUUID();
        UUID otherProfessionalId = UUID.randomUUID();
        UUID otherProfessionalUserId = UUID.randomUUID();

        Order order = Order.builder()
                .clientId(clientId)
                .mode(OrderMode.express)
                .status(OrderStatus.pending)
                .description("Trocar tomada")
                .addressId(UUID.randomUUID())
                .addressSnapshot(objectMapper.createObjectNode())
                .expiresAt(Instant.now())
                .urgencyFee(new BigDecimal("15.00"))
                .build();
        order.setId(orderId);

        ExpressQueueEntry chosen = ExpressQueueEntry.builder()
                .orderId(orderId)
                .professionalId(chosenProfessionalId)
                .proResponse(ProResponse.accepted)
                .proposedAmount(new BigDecimal("120.00"))
                .queuePosition((short) 1)
                .notifiedAt(Instant.now())
                .build();
        chosen.setId(UUID.randomUUID());

        ExpressQueueEntry other = ExpressQueueEntry.builder()
                .orderId(orderId)
                .professionalId(otherProfessionalId)
                .proResponse(ProResponse.accepted)
                .proposedAmount(new BigDecimal("125.00"))
                .queuePosition((short) 2)
                .notifiedAt(Instant.now())
                .build();
        other.setId(UUID.randomUUID());

        Conversation conversation = Conversation.builder()
                .orderId(orderId)
                .clientId(clientId)
                .professionalUserId(chosenProfessionalUserId)
                .build();
        conversation.setId(UUID.randomUUID());

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(queueRepository.findByOrderIdAndProfessionalId(orderId, chosenProfessionalId)).thenReturn(Optional.of(chosen));
        when(queueRepository.findAllByOrderIdAndProResponse(orderId, ProResponse.accepted)).thenReturn(List.of(chosen, other));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationService.createForOrder(any(Order.class))).thenReturn(conversation);
        when(professionalRepository.findByIdAndDeletedAtIsNull(chosenProfessionalId))
                .thenReturn(Optional.of(professional(chosenProfessionalId, chosenProfessionalUserId)));
        when(professionalRepository.findAllById(anyIterable())).thenAnswer(invocation -> {
            List<Professional> professionals = new ArrayList<>();
            for (UUID id : invocation.<Iterable<UUID>>getArgument(0)) {
                if (id.equals(otherProfessionalId)) {
                    professionals.add(professional(otherProfessionalId, otherProfessionalUserId));
                }
            }
            return professionals;
        });
        when(orderMapper.toResponse(any(Order.class))).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        OrderResponse response = orderService.clientRespond(orderId, clientId, new ClientRespondRequest(chosenProfessionalId));

        assertThat(response.status()).isEqualTo(OrderStatus.accepted);
        assertThat(response.professionalId()).isEqualTo(chosenProfessionalId);
        assertThat(response.baseAmount()).isEqualByComparingTo("120.00");
        assertThat(response.platformFee()).isEqualByComparingTo("24.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("135.00");

        verify(queueRepository).rejectOtherProposals(
                eq(orderId),
                eq(chosen.getId()),
                eq(ProResponse.accepted),
                eq(ClientResponse.rejected),
                any(Instant.class)
        );
        verify(messageService).sendSystemMessage(conversation.getId(), "Pedido aceito. Vocês podem conversar por aqui.");
        verify(notificationService).notifyUser(
                eq(chosenProfessionalUserId),
                eq(NotificationType.request_accepted),
                eq("Pedido aceito"),
                eq("Seu orcamento foi aceito pelo cliente."),
                any()
        );
        verify(notificationService).notifyUsers(
                eq(List.of(otherProfessionalUserId)),
                eq(NotificationType.request_rejected),
                eq("Proposta nao selecionada"),
                eq("O cliente escolheu outro profissional para este pedido."),
                any()
        );
    }

    @Test
    void confirmCompletionShouldSetCompletedAndNotifyProfessional() {
        UUID orderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        UUID professionalUserId = UUID.randomUUID();

        Order order = Order.builder()
                .clientId(clientId)
                .professionalId(professionalId)
                .mode(OrderMode.express)
                .status(OrderStatus.completed_by_pro)
                .description("Trocar tomada")
                .addressId(UUID.randomUUID())
                .addressSnapshot(objectMapper.createObjectNode())
                .expiresAt(Instant.now())
                .build();
        order.setId(orderId);

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(professional(professionalId, professionalUserId)));
        when(orderMapper.toResponse(any(Order.class))).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        OrderResponse response = orderService.confirmCompletion(orderId, clientId);

        assertThat(response.status()).isEqualTo(OrderStatus.completed);
        assertThat(order.getCompletedAt()).isNotNull();
        verify(notificationService).notifyUser(
                eq(professionalUserId),
                eq(NotificationType.request_status_update),
                eq("Servico confirmado"),
                eq("O cliente confirmou a conclusao do servico."),
                any()
        );
    }

    private SavedAddress address(UUID userId) {
        SavedAddress address = SavedAddress.builder()
                .userId(userId)
                .label("Casa")
                .street("Rua A")
                .number("100")
                .district("Centro")
                .city("Fortaleza")
                .state("CE")
                .zipCode("60000-000")
                .lat(new BigDecimal("-3.731862"))
                .lng(new BigDecimal("-38.526669"))
                .isDefault(true)
                .build();
        address.setId(UUID.randomUUID());
        return address;
    }

    private Professional professional(UUID professionalId, UUID userId) {
        Professional professional = Professional.builder()
                .userId(userId)
                .build();
        professional.setId(professionalId);
        return professional;
    }

    private OrderResponse toResponse(Order order) {
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
                order.getAddressSnapshot() != null ? order.getAddressSnapshot().toString() : null,
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
                List.of()
        );
    }
}
