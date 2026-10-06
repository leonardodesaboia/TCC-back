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

    @Column(name = "service_id")
    private UUID serviceId;

    /**
     * Área de serviço (ex: Elétrica). Implica a categoria. Escolhida pelo cliente na
     * criação; no On Demand também pode mudar via troca de serviço aceita pelo cliente
     * (ver pendingServiceId).
     */
    @Column(name = "area_id")
    private UUID areaId;

    @Column(name = "category_id", nullable = false)
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

    /** No On Demand, pode mudar via troca de serviço aceita pelo cliente (ver pendingServiceId). */
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

    /**
     * true quando o cancelamento decorre do profissional sinalizar que o escopo
     * descoberto no local diverge do descrito na criação do pedido — usado pelo
     * futuro módulo payment para isentar a multa de cancelamento.
     */
    @Column(name = "scope_mismatch", nullable = false)
    private boolean scopeMismatch;

    /**
     * Proposta de novo preço pendente (só Express) — enquanto não-nula, o pedido
     * continua accepted mas aguarda resposta do cliente. No máximo uma por vez.
     */
    @Column(name = "pending_price_amount", precision = 10, scale = 2)
    private BigDecimal pendingPriceAmount;

    @Column(name = "pending_price_reason", columnDefinition = "TEXT")
    private String pendingPriceReason;

    @Column(name = "pending_price_proposed_at")
    private Instant pendingPriceProposedAt;

    /**
     * Proposta de troca de serviço pendente (só On Demand) — enquanto não-nula, o pedido
     * continua accepted mas aguarda resposta do cliente. No máximo uma por vez. Referencia
     * outro ProfessionalOffering do mesmo profissional já atribuído ao pedido.
     */
    @Column(name = "pending_service_id")
    private UUID pendingServiceId;

    @Column(name = "pending_service_reason", columnDefinition = "TEXT")
    private String pendingServiceReason;

    /** Só usado quando o novo serviço é hourly sem duração própria definida. */
    @Column(name = "pending_service_estimated_duration_minutes")
    private Integer pendingServiceEstimatedDurationMinutes;

    @Column(name = "pending_service_proposed_at")
    private Instant pendingServiceProposedAt;
}
