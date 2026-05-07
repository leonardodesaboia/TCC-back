package com.allset.api.chat.websocket;

import com.allset.api.chat.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Valida que, no comando STOMP SUBSCRIBE, o destino {@code /topic/conversations/{id}}
 * pertence ao usuário conectado. Evita que alguém escute conversas alheias apenas sabendo o UUID.
 */
@Component
@RequiredArgsConstructor
public class StompSubscriptionInterceptor implements ChannelInterceptor {

    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";

    private final ConversationRepository conversationRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String dest = accessor.getDestination();
            if (dest != null && dest.startsWith(CONVERSATION_TOPIC_PREFIX)) {
                String idStr = dest.substring(CONVERSATION_TOPIC_PREFIX.length());
                UUID convId;
                try {
                    convId = UUID.fromString(idStr);
                } catch (IllegalArgumentException e) {
                    throw new MessageDeliveryException("ID de conversa inválido: " + idStr);
                }

                if (accessor.getUser() == null) {
                    throw new MessageDeliveryException("Usuário não autenticado");
                }
                UUID userId = (UUID) ((Authentication) accessor.getUser()).getPrincipal();
                conversationRepository.findByIdAndParticipant(convId, userId)
                        .orElseThrow(() -> new MessageDeliveryException("Acesso negado à conversa"));
            }
        }
        return message;
    }
}
