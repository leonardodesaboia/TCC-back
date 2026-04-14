package com.allset.api.payment.repository;

import com.allset.api.payment.domain.PaymentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, UUID> {

    List<PaymentStatusHistory> findAllByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
}
