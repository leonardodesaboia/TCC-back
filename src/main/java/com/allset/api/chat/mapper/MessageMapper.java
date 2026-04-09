package com.allset.api.chat.mapper;

import com.allset.api.chat.domain.Message;
import com.allset.api.chat.dto.MessageResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessageMapper {

    public MessageResponse toResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getMsgType(),
                message.getContent(),
                message.getAttachmentUrl(),
                message.getSentAt(),
                message.getDeliveredAt(),
                message.getReadAt()
        );
    }

    public List<MessageResponse> toResponseList(List<Message> messages) {
        return messages.stream().map(this::toResponse).toList();
    }
}
