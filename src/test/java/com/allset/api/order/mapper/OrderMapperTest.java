package com.allset.api.order.mapper;

import com.allset.api.config.AppProperties;
import com.allset.api.integration.storage.service.StorageRefFactory;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderMode;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.dto.OrderResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Cobre o contrato de {@link OrderMapper#toResponse} para os campos introduzidos pela change
 * report-scope-mismatch: um consumidor do endpoint (app do cliente/profissional, ou o futuro
 * módulo payment lendo a resposta) precisa distinguir, sem ambiguidade, se um cancelamento
 * decorreu de escopo divergente e se há uma proposta de novo preço em aberto — não basta o
 * service calcular isso certo, a resposta HTTP precisa carregar a informação.
 */
class OrderMapperTest {

    private final OrderMapper mapper = new OrderMapper(mock(StorageRefFactory.class), mock(AppProperties.class));

    @Test
    void toResponseShouldFlagScopeMismatchWhenCancellationWasCausedByDivergentScope() {
        Order order = orderCancelledWithReason("O profissional identificou que o serviço é diferente do descrito");
        order.setScopeMismatch(true);

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.status()).isEqualTo(OrderStatus.cancelled);
        assertThat(response.scopeMismatch())
                .as("um consumidor da API precisa distinguir esse cancelamento de um cancelamento comum")
                .isTrue();
    }

    @Test
    void toResponseShouldNotFlagScopeMismatchForAnOrdinaryCancellation() {
        Order order = orderCancelledWithReason("Cliente desistiu do serviço");
        order.setScopeMismatch(false);

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.scopeMismatch())
                .as("cancelamento comum não pode ser confundido com escopo divergente na resposta da API")
                .isFalse();
    }

    @Test
    void toResponseShouldExposePendingPriceProposalWhenOneIsAwaitingClientResponse() {
        Instant proposedAt = Instant.now();
        Order order = acceptedExpressOrder();
        order.setPendingPriceAmount(new BigDecimal("150.00"));
        order.setPendingPriceReason("Era troca de disjuntor, não de tomada");
        order.setPendingPriceProposedAt(proposedAt);

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.pendingPriceAmount()).isEqualByComparingTo("150.00");
        assertThat(response.pendingPriceReason()).isEqualTo("Era troca de disjuntor, não de tomada");
        assertThat(response.pendingPriceProposedAt()).isEqualTo(proposedAt);
    }

    @Test
    void toResponseShouldExposeNoPendingProposalWhenNoneWasMade() {
        Order order = acceptedExpressOrder();

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.pendingPriceAmount()).isNull();
        assertThat(response.pendingPriceReason()).isNull();
        assertThat(response.pendingPriceProposedAt()).isNull();
    }

    @Test
    void toResponseShouldExposePendingServiceChangeProposalWhenOneIsAwaitingClientResponse() {
        Instant proposedAt = Instant.now();
        UUID newServiceId = UUID.randomUUID();
        Order order = acceptedExpressOrder();
        order.setMode(OrderMode.on_demand);
        order.setPendingServiceId(newServiceId);
        order.setPendingServiceReason("Era instalação, não manutenção");
        order.setPendingServiceEstimatedDurationMinutes(90);
        order.setPendingServiceProposedAt(proposedAt);

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.pendingServiceId()).isEqualTo(newServiceId);
        assertThat(response.pendingServiceReason()).isEqualTo("Era instalação, não manutenção");
        assertThat(response.pendingServiceEstimatedDurationMinutes()).isEqualTo(90);
        assertThat(response.pendingServiceProposedAt()).isEqualTo(proposedAt);
    }

    @Test
    void toResponseShouldExposeNoPendingServiceChangeProposalWhenNoneWasMade() {
        Order order = acceptedExpressOrder();

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.pendingServiceId()).isNull();
        assertThat(response.pendingServiceReason()).isNull();
        assertThat(response.pendingServiceEstimatedDurationMinutes()).isNull();
        assertThat(response.pendingServiceProposedAt()).isNull();
    }

    private Order acceptedExpressOrder() {
        Order order = Order.builder()
                .clientId(UUID.randomUUID())
                .professionalId(UUID.randomUUID())
                .mode(OrderMode.express)
                .status(OrderStatus.accepted)
                .description("Trocar tomada")
                .addressId(UUID.randomUUID())
                .build();
        order.setId(UUID.randomUUID());
        return order;
    }

    private Order orderCancelledWithReason(String reason) {
        Order order = acceptedExpressOrder();
        order.setStatus(OrderStatus.cancelled);
        order.setCancelledAt(Instant.now());
        order.setCancelReason(reason);
        return order;
    }
}
