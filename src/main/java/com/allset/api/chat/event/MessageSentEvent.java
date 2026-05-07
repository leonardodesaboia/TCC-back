package com.allset.api.chat.event;

import java.util.UUID;

/**
 * Evento de domínio publicado após persistência de mensagem.
 * Consumido por {@code MessageBroadcastListener} com {@code @TransactionalEventListener(AFTER_COMMIT)}
 * para garantir que o broadcast WebSocket só ocorre após commit bem-sucedido.
 */
public record MessageSentEvent(UUID messageId) {}
