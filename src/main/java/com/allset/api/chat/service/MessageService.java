package com.allset.api.chat.service;

import com.allset.api.chat.dto.MessageResponse;
import com.allset.api.chat.dto.ReadReceiptEvent;
import com.allset.api.chat.dto.SendMessageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface MessageService {

    MessageResponse sendText(UUID conversationId, UUID senderId, SendMessageRequest request);

    MessageResponse sendImageMessage(UUID conversationId, UUID senderId, MultipartFile file);

    /**
     * Chamado pelo módulo order em transições de status.
     * sender_id = null, msg_type = system.
     */
    MessageResponse sendSystemMessage(UUID conversationId, String content);

    Page<MessageResponse> list(UUID conversationId, UUID currentUserId, Pageable pageable);

    ReadReceiptEvent markAsRead(UUID conversationId, UUID currentUserId);
}
