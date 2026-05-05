package com.allset.api.chat.mapper;

import com.allset.api.chat.domain.Message;
import com.allset.api.chat.dto.MessageResponse;
import com.allset.api.integration.storage.domain.StorageBucket;
import com.allset.api.integration.storage.service.StorageRefFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MessageMapper {

    private final StorageRefFactory storageRefFactory;

    public MessageResponse toResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getMsgType(),
                message.getContent(),
                storageRefFactory.from(StorageBucket.CHAT_ATTACHMENTS, message.getAttachmentKey()),
                message.getAttachmentSizeBytes(),
                message.getAttachmentMimeType(),
                message.getSentAt(),
                message.getDeliveredAt(),
                message.getReadAt()
        );
    }

    public List<MessageResponse> toResponseList(List<Message> messages) {
        return messages.stream().map(this::toResponse).toList();
    }
}
