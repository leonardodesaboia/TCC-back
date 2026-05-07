package com.allset.api.order.domain;

import com.allset.api.boilerplate.domain.PostgresEntity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends PostgresEntity {

    /**
     * Optimistic locking — previne race conditions em transições de status
     * concorrentes (ex: scheduler de timeout vs resposta do profissional).
     */
    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "professional_id")
    private UUID professionalId;

    @Column(name = "service_id", updatable = false)
    private UUID serviceId;

    /** Área de serviço escolhida pelo cliente (ex: Elétrica). Implica a categoria. */
    @Column(name = "area_id", updatable = false)
    private UUID areaId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "order_mode", nullable = false, updatable = false)
    private OrderMode mode;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "order_status", nullable = false)
    private OrderStatus status;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "address_id", nullable = false, updatable = false)
    private UUID addressId;

    /**
     * Snapshot imutável do endereço no momento do pedido.
     * Preserva histórico mesmo se o endereço for editado/deletado posteriormente.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "address_snapshot", columnDefinition = "jsonb", nullable = false, updatable = false)
    private JsonNode addressSnapshot;

    @Column(name = "scheduled_at", updatable = false)
    private Instant scheduledAt;

    /**
     * Express: fixo na criação = created_at + 15 min (propostas) + 30 min (escolha) = 45 min totais.
     * On demand: scheduled_at - 4h.
     * Nunca reescrito após a criação no fluxo Express.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Express — instante até o qual profissionais podem enviar propostas.
     * Imutável após criação. Igual a created_at + EXPRESS_PROPOSAL_WINDOW_MINUTES.
     * Após esse marco, novas tentativas de proposta retornam ProposalWindowExpiredException;
     * o cliente continua podendo escolher entre as propostas já recebidas até order.expiresAt.
     */
    @Column(name = "proposal_deadline", nullable = false, updatable = false)
    private Instant proposalDeadline;

    @Column(name = "urgency_fee", precision = 10, scale = 2)
    private BigDecimal urgencyFee;

    @Column(name = "base_amount", precision = 10, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "platform_fee", precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "pro_completed_at")
    private Instant proCompletedAt;

    /** pro_completed_at + 24h — após esse prazo não é possível abrir disputa. */
    @Column(name = "dispute_deadline")
    private Instant disputeDeadline;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;
}
