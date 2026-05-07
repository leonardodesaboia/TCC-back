package com.allset.api.notification.repository;

import com.allset.api.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByUserId(UUID userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("""
        UPDATE Notification n
           SET n.readAt = :readAt
         WHERE n.userId = :userId
           AND n.readAt IS NULL
        """)
    int markAllAsRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
