package com.allset.api.payment.domain;

public enum PaymentStatus {
    pending,
    confirmed,
    released,
    refunded,
    refunded_partial,
    failed,
    held,
    cancelled
}
