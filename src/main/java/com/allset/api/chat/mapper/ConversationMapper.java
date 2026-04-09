package com.allset.api.chat.mapper;

import com.allset.api.chat.domain.Conversation;
import com.allset.api.chat.dto.ConversationResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationMapper {

    public ConversationResponse toResponse(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getOrderId(),
                conversation.getClientId(),
                conversation.getProfessionalUserId(),
                conversation.getCreatedAt()
        );
    }

    public List<ConversationResponse> toResponseList(List<Conversation> conversations) {
        return conversations.stream().map(this::toResponse).toList();
    }
}
