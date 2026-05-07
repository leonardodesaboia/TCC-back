package com.allset.api.professional.domain;

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
@Table(name = "professionals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Professional extends PostgresEntity {

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "years_of_experience")
    private Short yearsOfExperience;

    @Column(name = "base_hourly_rate", precision = 10, scale = 2)
    private BigDecimal baseHourlyRate;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "verification_status", columnDefinition = "verification_status", nullable = false)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.pending;

    @Column(name = "idwall_token", length = 255)
    private String idwallToken;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "idwall_result", columnDefinition = "jsonb")
    private JsonNode idwallResult;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "geo_lat", precision = 9, scale = 6)
    private BigDecimal geoLat;

    @Column(name = "geo_lng", precision = 9, scale = 6)
    private BigDecimal geoLng;

    @Builder.Default
    @Column(name = "geo_active", nullable = false)
    private boolean geoActive = false;

    @Column(name = "geo_captured_at")
    private Instant geoCapturedAt;

    @Column(name = "geo_accuracy_meters", precision = 7, scale = 2)
    private BigDecimal geoAccuracyMeters;

    @Column(name = "geo_source", length = 20)
    private String geoSource;

    @Column(name = "subscription_plan_id")
    private UUID subscriptionPlanId;

    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;

    @Column(name = "subscription_cancelled_at")
    private Instant subscriptionCancelledAt;
}
