package com.allset.api.subscription.domain;

import com.allset.api.boilerplate.domain.PostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan extends PostgresEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "price_monthly", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceMonthly;

    @Builder.Default
    @Column(name = "highlight_in_search", nullable = false)
    private boolean highlightInSearch = false;

    @Builder.Default
    @Column(name = "express_priority", nullable = false)
    private boolean expressPriority = false;

    @Column(name = "badge_label", length = 30)
    private String badgeLabel;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
