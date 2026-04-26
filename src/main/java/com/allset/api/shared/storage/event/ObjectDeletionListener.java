package com.allset.api.shared.storage.event;

import com.allset.api.shared.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ObjectDeletionListener {

    private final StorageService storageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeletionRequested(ObjectDeletionRequestedEvent event) {
        try {
            storageService.delete(event.bucket(), event.key());
        } catch (Exception e) {
            log.warn("event=storage_delete_failed bucket={} key={} error={}",
                    event.bucket(), event.key(), e.getMessage());
        }
    }
}
