package com.allset.api.chat.service;

import com.allset.api.chat.domain.Conversation;
import com.allset.api.chat.domain.Message;
import com.allset.api.chat.domain.MessageType;
import com.allset.api.chat.dto.MessageResponse;
import com.allset.api.chat.dto.SendMessageRequest;
import com.allset.api.chat.mapper.MessageMapper;
import com.allset.api.chat.repository.MessageRepository;
import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ConversationService conversationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ChatBroadcaster chatBroadcaster;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MessageServiceImpl messageService;

    @Test
    void sendTextShouldNotifyTheOtherParticipant() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        Conversation conversation = Conversation.builder()
                .orderId(UUID.randomUUID())
                .clientId(senderId)
                .professionalUserId(recipientId)
                .build();
        conversation.setId(conversationId);

        Message saved = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .msgType(MessageType.text)
                .content("Oi")
                .sentAt(Instant.now())
                .build();
        saved.setId(messageId);

        MessageResponse response = new MessageResponse(
                messageId,
                conversationId,
                senderId,
                MessageType.text,
                "Oi",
                null,
                saved.getSentAt(),
                null,
                null
        );

        when(conversationService.requireParticipant(conversationId, senderId)).thenReturn(conversation);
        when(messageRepository.save(any(Message.class))).thenReturn(saved);
        when(messageMapper.toResponse(saved)).thenReturn(response);
        when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());

        MessageResponse result = messageService.sendText(conversationId, senderId, new SendMessageRequest("Oi"));

        assertThat(result).isEqualTo(response);
        verify(notificationService).notifyUser(
                eq(recipientId),
                eq(NotificationType.new_message),
                eq("Nova mensagem"),
                eq("Voce recebeu uma nova mensagem no chat."),
                any()
        );
    }
}
