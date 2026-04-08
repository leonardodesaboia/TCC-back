package com.allset.api.order.exception;

public class ExpressQueueViolationException extends RuntimeException {
    public ExpressQueueViolationException(String message) {
        super(message);
    }
}
