package com.allset.api.chat.service;

import com.allset.api.chat.domain.Message;
import com.allset.api.chat.domain.Conversation;
import com.allset.api.chat.event.MessageSentEvent;
import com.allset.api.chat.mapper.MessageMapper;
import com.allset.api.chat.repository.ConversationRepository;
import com.allset.api.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Listener que faz o broadcast WebSocket APÓS o commit da transação original.
 * Garante que nenhuma mensagem fantasma é publicada caso a transação faça rollback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageBroadcastListener {

    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final ConversationRepository conversationRepository;
    private final ChatBroadcaster broadcaster;
    private final SimpUserRegistry userRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(MessageSentEvent event) {
        Message msg = messageRepository.findById(event.messageId()).orElse(null);
        if (msg == null) {
            log.warn("event=broadcast_skipped reason=message_not_found messageId={}", event.messageId());
            return;
        }

        Conversation conv = conversationRepository.findById(msg.getConversationId()).orElse(null);
        if (conv == null) {
            log.warn("event=broadcast_skipped reason=conversation_not_found conversationId={}",
                    msg.getConversationId());
            return;
        }

        // 1. Broadcast da mensagem para todos os subscribers do tópico
        broadcaster.publishMessage(conv.getId(), messageMapper.toResponse(msg));

        // 2. Verificar presença do destinatário para delivery receipt
        // Mensagens de sistema não têm sender — não há delivery receipt aplicável
        if (msg.getSenderId() == null) {
            return;
        }

        UUID otherUserId = msg.getSenderId().equals(conv.getClientId())
                ? conv.getProfessionalUserId()
                : conv.getClientId();

        if (userRegistry.getUser(otherUserId.toString()) != null) {
            Instant deliveredAt = Instant.now();
            int count = messageRepository.markAllAsDelivered(
                    conv.getId(), msg.getSenderId(), deliveredAt);
            if (count > 0) {
                broadcaster.publishDeliveryReceipt(conv.getId(), otherUserId, deliveredAt, count);
                log.info("event=delivery_receipt conversationId={} receiverUserId={} count={}",
                        conv.getId(), otherUserId, count);
            }
        }
    }
}
