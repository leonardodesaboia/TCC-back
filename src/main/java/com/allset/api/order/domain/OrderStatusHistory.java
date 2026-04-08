package com.allset.api.order.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.UUID;

/**
 * Auditoria imutável de todas as transições de status de um pedido.
 * Registros desta tabela NUNCA devem ser atualizados ou deletados.
 */
@Entity
@Table(name = "order_status_history")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    /** null na primeira transição (criação do pedido). */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "from_status", columnDefinition = "order_status")
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "to_status", columnDefinition = "order_status", nullable = false)
    private OrderStatus toStatus;

    @Column(columnDefinition = "TEXT")
    private String reason;

    /** null = transição automática do sistema (timeout, scheduler, etc.). */
    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
