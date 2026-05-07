package com.allset.api.chat.exception;

import java.util.UUID;

public class ConversationClosedException extends RuntimeException {
    public ConversationClosedException(UUID orderId) {
        super("Conversa encerrada: o pedido " + orderId + " foi concluído, cancelado ou está em disputa");
    }
}
