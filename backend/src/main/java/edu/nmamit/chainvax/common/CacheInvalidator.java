package edu.nmamit.chainvax.common;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts cached unit state / dashboard aggregates after the surrounding transaction commits,
 * so a concurrent reader cannot re-cache data that is about to be rolled back.
 */
@Component
public class CacheInvalidator {

    private final CacheManager cacheManager;

    public CacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void batchChanged(Long batchId) {
        afterCommit(() -> {
            evict(CacheNames.UNIT_STATE, batchId);
            clear(CacheNames.DASHBOARD);
        });
    }

    public void dashboardChanged() {
        afterCommit(() -> clear(CacheNames.DASHBOARD));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        }
        else {
            action.run();
        }
    }

    private void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null && key != null) {
            cache.evict(key);
        }
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
