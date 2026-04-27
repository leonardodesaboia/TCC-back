package com.allset.api.dispute.repository;

import com.allset.api.dispute.domain.DisputeEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, UUID> {

    List<DisputeEvidence> findAllByDisputeIdAndDeletedAtIsNullOrderBySentAtAsc(UUID disputeId);
}
