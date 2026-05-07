package com.allset.api.notification.service;

import com.allset.api.notification.domain.PushToken;
import com.allset.api.notification.dto.PushTokenResponse;
import com.allset.api.notification.dto.RegisterPushTokenRequest;
import com.allset.api.notification.exception.PushTokenNotFoundException;
import com.allset.api.notification.mapper.PushTokenMapper;
import com.allset.api.notification.repository.PushTokenRepository;
import com.allset.api.user.exception.UserNotFoundException;
import com.allset.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PushTokenServiceImpl implements PushTokenService {

    private static final long PUSH_TOKEN_RETENTION_DAYS = 90;

    private final PushTokenRepository pushTokenRepository;
    private final UserRepository userRepository;
    private final PushTokenMapper pushTokenMapper;

    @Override
    public PushTokenResponse register(UUID userId, RegisterPushTokenRequest request) {
        requireActiveUser(userId);

        Instant now = Instant.now();
        PushToken pushToken = pushTokenRepository.findByExpoToken(request.expoToken())
                .orElseGet(PushToken::new);

        if (pushToken.getCreatedAt() == null) {
            pushToken.setCreatedAt(now);
        }

        pushToken.setUserId(userId);
        pushToken.setExpoToken(request.expoToken());
        pushToken.setPlatform(request.platform());
        pushToken.setLastSeen(now);

        PushToken saved = pushTokenRepository.save(pushToken);

        log.info("event=push_token_registered userId={} pushTokenId={} platform={}",
                userId,
                saved.getId(),
                saved.getPlatform());

        return pushTokenMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PushTokenResponse> listForUser(UUID userId) {
        requireActiveUser(userId);
        return pushTokenRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(pushTokenMapper::toResponse)
                .toList();
    }

    @Override
    public void delete(UUID userId, UUID pushTokenId) {
        requireActiveUser(userId);

        PushToken pushToken = pushTokenRepository.findByIdAndUserId(pushTokenId, userId)
                .orElseThrow(() -> new PushTokenNotFoundException(pushTokenId));

        pushTokenRepository.delete(pushToken);

        log.info("event=push_token_deleted userId={} pushTokenId={}", userId, pushTokenId);
    }

    @Override
    public long pruneInactiveTokens() {
        Instant cutoff = Instant.now().minus(PUSH_TOKEN_RETENTION_DAYS, ChronoUnit.DAYS);
        long deletedCount = pushTokenRepository.deleteByLastSeenBefore(cutoff);

        if (deletedCount > 0) {
            log.info("event=push_token_pruned count={} cutoff={}", deletedCount, cutoff);
        }

        return deletedCount;
    }

    private void requireActiveUser(UUID userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
