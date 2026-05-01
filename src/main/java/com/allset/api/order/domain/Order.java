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
     * Express — fase de propostas: NOW() + N minutos para a rodada atual de busca.
     * Express — fase de escolha: NOW() + janela do cliente a partir da primeira proposta.
     * On demand: scheduled_at - 4h.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "urgency_fee", precision = 10, scale = 2)
    private BigDecimal urgencyFee;

    @Column(name = "base_amount", precision = 10, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "platform_fee", precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /** Raio de busca atual em km — aumentado a cada rodada sem propostas. */
    @Column(name = "search_radius_km", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal searchRadiusKm = BigDecimal.valueOf(0.1);

    /** Número de rodadas de busca realizadas. Máximo em AppProperties. */
    @Column(name = "search_attempts", nullable = false)
    @Builder.Default
    private short searchAttempts = 1;

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
