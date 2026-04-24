package com.allset.api.notification.repository;

import com.allset.api.notification.domain.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {

    Optional<PushToken> findByExpoToken(String expoToken);

    List<PushToken> findAllByUserId(UUID userId);

    List<PushToken> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PushToken> findByIdAndUserId(UUID id, UUID userId);

    long deleteByLastSeenBefore(Instant cutoff);
}
