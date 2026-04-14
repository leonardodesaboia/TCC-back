package com.allset.api.payment.repository;

import com.allset.api.payment.domain.Payment;
import com.allset.api.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderIdAndDeletedAtIsNull(UUID orderId);

    Optional<Payment> findByAsaasPaymentIdAndDeletedAtIsNull(String asaasPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.asaasPaymentId = :asaasPaymentId AND p.deletedAt IS NULL")
    Optional<Payment> findByAsaasPaymentIdForUpdate(String asaasPaymentId);

    Optional<Payment> findByIdAndDeletedAtIsNull(UUID id);

    Page<Payment> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Payment> findAllByStatusAndDeletedAtIsNull(PaymentStatus status, Pageable pageable);

    Page<Payment> findAllByPayerUserIdAndDeletedAtIsNull(UUID payerUserId, Pageable pageable);
}
