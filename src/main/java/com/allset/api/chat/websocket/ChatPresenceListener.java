package com.allset.api.chat.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Monitora conexões WebSocket para logging e extensões futuras (ex: backfill de delivered_at).
 * A lógica de backfill de delivered_at fica reservada para quando o módulo de notificação estiver
 * implementado — por ora registra apenas os eventos de conectar/desconectar.
 */
@Slf4j
@Component
public class ChatPresenceListener {

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        log.debug("event=ws_connect sessionId={}", event.getMessage().getHeaders().get("simpSessionId"));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        log.debug("event=ws_disconnect sessionId={}", event.getSessionId());
    }
}
