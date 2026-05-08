package com.allset.api.order.repository;

import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderMode;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.domain.ProResponse;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.deletedAt IS NULL")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    Page<Order> findAllByClientIdAndDeletedAtIsNull(UUID clientId, Pageable pageable);

    Page<Order> findAllByClientIdAndStatusAndDeletedAtIsNull(UUID clientId, OrderStatus status, Pageable pageable);

    Page<Order> findAllByProfessionalIdAndDeletedAtIsNull(UUID professionalId, Pageable pageable);

    Page<Order> findAllByProfessionalIdAndStatusAndDeletedAtIsNull(UUID professionalId, OrderStatus status, Pageable pageable);

    @Query("""
        SELECT o FROM Order o
        WHERE o.deletedAt IS NULL
          AND o.mode = :mode
          AND o.status = :status
          AND o.professionalId IS NULL
          AND EXISTS (
              SELECT 1 FROM ExpressQueueEntry e
              WHERE e.orderId = o.id
                AND e.professionalId = :professionalId
                AND (
                    e.proResponse IS NULL
                    OR (e.proResponse = :acceptedResponse AND e.clientResponse IS NULL)
                )
          )
        """)
    Page<Order> findExpressInboxByProfessionalId(
            @Param("professionalId") UUID professionalId,
            @Param("mode") OrderMode mode,
            @Param("status") OrderStatus status,
            @Param("acceptedResponse") ProResponse acceptedResponse,
            Pageable pageable
    );

    @Query(value = """
        SELECT o.id FROM orders o
        WHERE o.mode = 'express'
          AND o.status = 'pending'
          AND o.deleted_at IS NULL
          AND o.proposal_deadline <= :now
          AND EXISTS (
              SELECT 1 FROM express_queue eq
              WHERE eq.order_id = o.id AND eq.pro_response IS NULL
          )
        """, nativeQuery = true)
    List<UUID> findExpressIdsWithExpiredProposalWindow(@Param("now") Instant now);

    @Query(value = """
        SELECT o.id FROM orders o
        WHERE o.mode = 'express'
          AND o.status = 'pending'
          AND o.deleted_at IS NULL
          AND o.expires_at <= :now
        """, nativeQuery = true)
    List<UUID> findExpressIdsToExpire(@Param("now") Instant now);

    @Query(value = """
        SELECT o.id FROM orders o
        WHERE o.status = 'completed_by_pro'
          AND o.deleted_at IS NULL
          AND o.dispute_deadline <= :now
        """, nativeQuery = true)
    List<UUID> findIdsToAutoConfirm(@Param("now") Instant now);
}
