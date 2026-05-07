package com.allset.api.chat.repository;

import com.allset.api.chat.domain.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdAndDeletedAtIsNull(UUID conversationId, Pageable pageable);

    Optional<Message> findTopByConversationIdAndDeletedAtIsNullOrderBySentAtDesc(UUID conversationId);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(UUID conversationId, UUID currentUserId);

    @Modifying
    @Query("""
        UPDATE Message m
           SET m.readAt = :now
         WHERE m.conversationId = :conversationId
           AND m.senderId <> :currentUserId
           AND m.readAt IS NULL
    """)
    int markAllAsRead(@Param("conversationId") UUID conversationId,
                      @Param("currentUserId") UUID currentUserId,
                      @Param("now") Instant now);

    @Modifying
    @Query("""
        UPDATE Message m
           SET m.deliveredAt = :now
         WHERE m.conversationId = :conversationId
           AND m.senderId <> :currentUserId
           AND m.deliveredAt IS NULL
    """)
    int markAllAsDelivered(@Param("conversationId") UUID conversationId,
                           @Param("currentUserId") UUID currentUserId,
                           @Param("now") Instant now);
}
