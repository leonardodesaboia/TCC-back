package com.allset.api.order.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "express_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpressQueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "professional_id", nullable = false, updatable = false)
    private UUID professionalId;

    /** Preço proposto pelo profissional. Null se rejeitou ou timeout. */
    @Column(name = "proposed_amount", precision = 10, scale = 2)
    private BigDecimal proposedAmount;

    /**
     * Quando este entry foi ativado (profissional efetivamente notificado).
     * Atualizado para NOW() a cada avanço da fila.
     * O scheduler usa este campo para calcular o timeout de 10 minutos.
     */
    @Column(name = "notified_at", nullable = false)
    private Instant notifiedAt;

    /** Quando o profissional respondeu (accepted/rejected). */
    @Column(name = "responded_at")
    private Instant respondedAt;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "pro_response", columnDefinition = "pro_response")
    private ProResponse proResponse;

    /** Preenchido apenas quando pro_response = accepted. */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "client_response", columnDefinition = "client_response")
    private ClientResponse clientResponse;

    @Column(name = "client_responded_at")
    private Instant clientRespondedAt;

    /** Ordem de notificação — profissional com posição 1 é notificado primeiro. */
    @Column(name = "queue_position", nullable = false)
    private short queuePosition;

    // Sem @PrePersist — notifiedAt é definido explicitamente no service.
    // Todos os profissionais da rodada recebem notifiedAt = NOW() (broadcast simultâneo).
}
