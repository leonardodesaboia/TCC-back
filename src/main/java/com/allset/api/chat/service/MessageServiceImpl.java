package com.allset.api.chat.service;

import com.allset.api.chat.domain.Message;
import com.allset.api.chat.domain.MessageType;
import com.allset.api.chat.dto.MessageResponse;
import com.allset.api.chat.dto.ReadReceiptEvent;
import com.allset.api.chat.dto.SendMessageRequest;
import com.allset.api.chat.event.MessageSentEvent;
import com.allset.api.chat.mapper.MessageMapper;
import com.allset.api.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatBroadcaster chatBroadcaster;

    @Override
    public MessageResponse sendText(UUID conversationId, UUID senderId, SendMessageRequest request) {
        conversationService.requireParticipant(conversationId, senderId);

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .msgType(MessageType.text)
                .content(request.content())
                .sentAt(Instant.now())
                .build();

        Message saved = messageRepository.save(message);
        eventPublisher.publishEvent(new MessageSentEvent(saved.getId()));

        log.info("event=message_sent conversationId={} messageId={} senderId={}",
                conversationId, saved.getId(), senderId);
        return messageMapper.toResponse(saved);
    }

    @Override
    public MessageResponse sendSystemMessage(UUID conversationId, String content) {
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(null)
                .msgType(MessageType.system)
                .content(content)
                .sentAt(Instant.now())
                .build();

        Message saved = messageRepository.save(message);
        eventPublisher.publishEvent(new MessageSentEvent(saved.getId()));

        log.info("event=system_message_sent conversationId={} messageId={}", conversationId, saved.getId());
        return messageMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> list(UUID conversationId, UUID currentUserId, Pageable pageable) {
        conversationService.requireParticipant(conversationId, currentUserId);
        return messageRepository.findByConversationIdAndDeletedAtIsNull(conversationId, pageable)
                .map(messageMapper::toResponse);
    }

    @Override
    public ReadReceiptEvent markAsRead(UUID conversationId, UUID currentUserId) {
        conversationService.requireParticipant(conversationId, currentUserId);

        Instant now = Instant.now();
        int count = messageRepository.markAllAsRead(conversationId, currentUserId, now);

        ReadReceiptEvent event = new ReadReceiptEvent(
                "READ_RECEIPT", conversationId, currentUserId, now, count);

        if (count > 0) {
            chatBroadcaster.publishReadReceipt(conversationId, event);
            log.info("event=messages_read conversationId={} userId={} count={}",
                    conversationId, currentUserId, count);
        }

        return event;
    }
}
