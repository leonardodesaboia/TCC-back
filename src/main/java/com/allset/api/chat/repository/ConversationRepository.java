package com.allset.api.chat.repository;

import com.allset.api.chat.domain.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<Conversation> findByOrderId(UUID orderId);

    @Query("""
        SELECT c FROM Conversation c
        WHERE c.id = :id
          AND c.deletedAt IS NULL
          AND (c.clientId = :userId OR c.professionalUserId = :userId)
    """)
    Optional<Conversation> findByIdAndParticipant(@Param("id") UUID id,
                                                   @Param("userId") UUID userId);

    @Query("""
        SELECT c FROM Conversation c
        WHERE c.deletedAt IS NULL
          AND (c.clientId = :userId OR c.professionalUserId = :userId)
    """)
    Page<Conversation> findAllForParticipant(@Param("userId") UUID userId, Pageable pageable);
}
