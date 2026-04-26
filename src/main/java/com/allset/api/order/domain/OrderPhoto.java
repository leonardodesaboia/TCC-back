package com.allset.api.order.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_photos")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "uploader_id", nullable = false, updatable = false)
    private UUID uploaderId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "photo_type", columnDefinition = "photo_type", nullable = false, updatable = false)
    private PhotoType photoType;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    void prePersist() {
        if (this.uploadedAt == null) {
            this.uploadedAt = Instant.now();
        }
    }
}
