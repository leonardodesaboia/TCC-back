package com.allset.api.offering.domain;

import com.allset.api.boilerplate.domain.PostgresEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "professional_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfessionalOffering extends PostgresEntity {

    @Column(name = "professional_id", nullable = false, updatable = false)
    private UUID professionalId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "pricing_type", columnDefinition = "pricing_type", nullable = false)
    private PricingType pricingType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
