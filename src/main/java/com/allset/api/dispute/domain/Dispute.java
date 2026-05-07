package com.allset.api.dispute.domain;

import com.allset.api.boilerplate.domain.PostgresEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute extends PostgresEntity {

    /**
     * Optimistic locking — previne dois admins resolvendo a mesma disputa
     * simultaneamente. Mesmo padrao do Order.
     */
    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private UUID openedBy;

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "dispute_status", nullable = false)
    private DisputeStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "dispute_resolution")
    private DisputeResolution resolution;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "client_refund_amount", precision = 10, scale = 2)
    private BigDecimal clientRefundAmount;

    @Column(name = "professional_amount", precision = 10, scale = 2)
    private BigDecimal professionalAmount;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;
}
