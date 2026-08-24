package com.dev.MonicaKimEnglish.service.storage;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

/**
 * Keeps filesystem changes consistent with a surrounding DB transaction.
 * New files are removed on rollback, while replaced/deleted old files are
 * removed only after the DB commit succeeds.
 */
@Component
@RequiredArgsConstructor
public class TransactionFileCleanup {
    private final FileStorageService storage;

    public void deleteOnRollback(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    storage.deleteQuietly(relativePath);
                }
            }
        });
    }

    public void deleteAfterCommit(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.deleteQuietly(relativePath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.deleteQuietly(relativePath);
            }
        });
    }
}
