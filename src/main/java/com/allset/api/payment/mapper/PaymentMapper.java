package com.allset.api.payment.mapper;

import com.allset.api.payment.domain.Payment;
import com.allset.api.payment.domain.PaymentTransaction;
import com.allset.api.payment.dto.PaymentResponse;
import com.allset.api.payment.dto.PaymentTransactionResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment, List<PaymentTransaction> transactions) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getPayerUserId(),
                payment.getReceiverProfessionalId(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getGrossAmount(),
                payment.getPlatformFee(),
                payment.getNetAmount(),
                payment.getRefundAmount(),
                payment.getPixCopyPaste(),
                payment.getPixQrCodeUrl(),
                payment.getInvoiceUrl(),
                payment.getPaidAt(),
                payment.getReleasedAt(),
                payment.getRefundedAt(),
                payment.getFailureReason(),
                transactions.stream().map(this::toTransactionResponse).toList(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    public PaymentTransactionResponse toTransactionResponse(PaymentTransaction transaction) {
        return new PaymentTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getAsaasId(),
                transaction.getFailureReason(),
                transaction.getProcessedAt(),
                transaction.getCreatedAt()
        );
    }
}
