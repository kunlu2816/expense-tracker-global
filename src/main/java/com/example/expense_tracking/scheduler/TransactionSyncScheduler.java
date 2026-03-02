package com.example.expense_tracking.scheduler;

import com.example.expense_tracking.service.TransactionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "sync.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionSyncScheduler {
    private final TransactionSyncService transactionSyncService;

    // Runs every N minutes based on sync.interval-minutes config
    // Initial delay prevents sync from firing immediately on startup
    @Scheduled(
            fixedRateString = "#{${sync.interval-minutes:15} * 60 * 1000}",
            initialDelayString = "#{${sync.interval-minutes:15} * 60 * 1000}"
    )
    public void scheduledSync() {
        log.info("Scheduled transaction sync started");
        long startTime = System.currentTimeMillis();
        
        try {
            transactionSyncService.syncAllActiveAccounts();
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Scheduled transaction sync completed in {} ms", elapsed);
        } catch (Exception e) {
            log.error("Unhandled exception during scheduled transaction sync: {}", e.getMessage(), e);
        }
    }
}
