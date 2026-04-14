package com.allset.api.payment.repository;

import com.allset.api.payment.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    List<PaymentTransaction> findAllByPaymentId(UUID paymentId);

    Optional<PaymentTransaction> findByAsaasId(String asaasId);
}
