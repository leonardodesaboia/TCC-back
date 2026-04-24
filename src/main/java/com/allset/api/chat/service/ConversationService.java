package com.allset.api.chat.service;

import com.allset.api.chat.domain.Conversation;
import com.allset.api.chat.dto.ConversationResponse;
import com.allset.api.chat.dto.ConversationSummaryResponse;
import com.allset.api.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ConversationService {

    /**
     * Chamado pelo OrderServiceImpl quando a order transita para accepted.
     * Resolve professional.user_id a partir de order.professionalId.
     */
    Conversation createForOrder(Order order);

    ConversationResponse getById(UUID id, UUID currentUserId);

    Page<ConversationSummaryResponse> listForUser(UUID currentUserId, Pageable pageable);

    /**
     * Usado internamente pelo MessageService para autorização.
     * Lança ConversationNotFoundException (404) para "não existe" E "não participa".
     */
    Conversation requireParticipant(UUID conversationId, UUID userId);
}
