package com.allset.api.dispute.domain;

import com.allset.api.boilerplate.domain.PostgresEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispute_evidences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeEvidence extends PostgresEntity {

    @Column(name = "dispute_id", nullable = false, updatable = false)
    private UUID disputeId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "evidence_type", columnDefinition = "evidence_type", nullable = false, updatable = false)
    private EvidenceType evidenceType;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** Chave do objeto no bucket dispute-evidences (MinIO/S3). */
    @Column(name = "file_key", columnDefinition = "TEXT")
    private String fileKey;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "file_mime_type", length = 64)
    private String fileMimeType;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;
}
