package com.allset.api.chat.service;

import com.allset.api.chat.dto.DeliveryReceiptEvent;
import com.allset.api.chat.dto.MessageResponse;
import com.allset.api.chat.dto.ReadReceiptEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Encapsula o {@link SimpMessagingTemplate} para facilitar testes e desacoplar o código de domínio
 * do protocolo STOMP.
 */
@Component
@RequiredArgsConstructor
public class ChatBroadcaster {

    private final SimpMessagingTemplate template;

    public void publishMessage(UUID conversationId, MessageResponse message) {
        template.convertAndSend("/topic/conversations/" + conversationId, message);
    }

    public void publishReadReceipt(UUID conversationId, ReadReceiptEvent event) {
        template.convertAndSend("/topic/conversations/" + conversationId, event);
    }

    public void publishDeliveryReceipt(UUID conversationId, UUID receiverUserId,
                                        Instant at, int count) {
        template.convertAndSend(
                "/topic/conversations/" + conversationId,
                new DeliveryReceiptEvent("DELIVERY_RECEIPT", conversationId, receiverUserId, at, count)
        );
    }
}
