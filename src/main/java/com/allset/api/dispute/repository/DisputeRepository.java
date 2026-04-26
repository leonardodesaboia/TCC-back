package com.allset.api.dispute.repository;

import com.allset.api.dispute.domain.Dispute;
import com.allset.api.dispute.domain.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    Optional<Dispute> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Dispute> findByOrderIdAndDeletedAtIsNull(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    Page<Dispute> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Dispute> findAllByStatusAndDeletedAtIsNull(DisputeStatus status, Pageable pageable);
}
