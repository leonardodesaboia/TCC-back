package com.allset.api.order.repository;

import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderMode;
import com.allset.api.order.domain.OrderStatus;
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

    /**
     * Pedidos Express pendentes cujo prazo expirou.
     * O scheduler usa esta query para processar tanto o fim da janela de propostas
     * quanto o fim da janela de escolha do cliente.
     */
    List<Order> findAllByStatusAndModeAndExpiresAtBeforeAndDeletedAtIsNull(
            OrderStatus status, OrderMode mode, Instant expiresAt);
}
