package com.allset.api.calendar.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "blocked_periods")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "professional_id", nullable = false, updatable = false)
    private UUID professionalId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "block_type", columnDefinition = "block_type", nullable = false, updatable = false)
    private BlockType blockType;

    /** 0=Dom, 1=Seg … 6=Sáb — obrigatório se blockType = recurring */
    @Column
    private Short weekday;

    /** Obrigatório se blockType = specific_date */
    @Column(name = "specific_date")
    private LocalDate specificDate;

    /** null = dia inteiro bloqueado */
    @Column(name = "starts_at")
    private LocalTime startsAt;

    /** null = dia inteiro bloqueado */
    @Column(name = "ends_at")
    private LocalTime endsAt;

    /** Obrigatório se blockType = order */
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_starts_at")
    private Instant orderStartsAt;

    @Column(name = "order_ends_at")
    private Instant orderEndsAt;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
