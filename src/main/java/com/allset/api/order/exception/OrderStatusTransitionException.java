package com.allset.api.order.exception;

import com.allset.api.order.domain.OrderStatus;

public class OrderStatusTransitionException extends RuntimeException {
    public OrderStatusTransitionException(OrderStatus current, String action) {
        super("Operação '" + action + "' não permitida para pedido com status '" + current.name() + "'");
    }
}
