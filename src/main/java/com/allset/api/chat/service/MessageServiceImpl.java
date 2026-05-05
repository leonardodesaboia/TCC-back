package com.allset.api.chat.service;

import com.allset.api.chat.domain.Conversation;
import com.allset.api.chat.domain.Message;
import com.allset.api.chat.domain.MessageType;
import com.allset.api.chat.dto.MessageResponse;
import com.allset.api.chat.dto.ReadReceiptEvent;
import com.allset.api.chat.dto.SendMessageRequest;
import com.allset.api.chat.event.MessageSentEvent;
import com.allset.api.chat.mapper.MessageMapper;
import com.allset.api.chat.repository.MessageRepository;
import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.service.NotificationService;
import com.allset.api.integration.storage.domain.StorageBucket;
import com.allset.api.integration.storage.domain.StoredObject;
import com.allset.api.integration.storage.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    @Override
    public MessageResponse sendText(UUID conversationId, UUID senderId, SendMessageRequest request) {
        Conversation conversation = conversationService.requireParticipant(conversationId, senderId);

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .msgType(MessageType.text)
                .content(request.content())
                .sentAt(Instant.now())
                .build();

        Message saved = messageRepository.save(message);
        eventPublisher.publishEvent(new MessageSentEvent(saved.getId()));
        notifyMessageRecipient(conversation, senderId, saved.getId());

        log.info("event=message_sent conversationId={} messageId={} senderId={}",
                conversationId, saved.getId(), senderId);
        return messageMapper.toResponse(saved);
    }

    @Override
    public MessageResponse sendImageMessage(UUID conversationId, UUID senderId, MultipartFile file) {
        Conversation conversation = conversationService.requireParticipant(conversationId, senderId);

        StoredObject stored = storageService.upload(StorageBucket.CHAT_ATTACHMENTS, conversationId.toString(), file);

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .msgType(MessageType.image)
                .attachmentKey(stored.key())
                .attachmentSizeBytes((int) Math.min(stored.sizeBytes(), Integer.MAX_VALUE))
                .attachmentMimeType(stored.contentType())
                .sentAt(Instant.now())
                .build();

        Message saved = messageRepository.save(message);
        eventPublisher.publishEvent(new MessageSentEvent(saved.getId()));
        notifyMessageRecipient(conversation, senderId, saved.getId());

        log.info("event=image_message_sent conversationId={} messageId={} senderId={}",
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

    private void notifyMessageRecipient(Conversation conversation, UUID senderId, UUID messageId) {
        UUID recipientId = conversation.getClientId().equals(senderId)
                ? conversation.getProfessionalUserId()
                : conversation.getClientId();

        var data = objectMapper.createObjectNode();
        data.put("conversationId", conversation.getId().toString());
        data.put("messageId", messageId.toString());

        notificationService.notifyUser(
                recipientId,
                NotificationType.new_message,
                "Nova mensagem",
                "Voce recebeu uma nova mensagem no chat.",
                data
        );
    }
}
