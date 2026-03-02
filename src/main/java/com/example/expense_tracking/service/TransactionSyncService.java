package com.example.expense_tracking.service;

import com.example.expense_tracking.config.SyncConfig;
import com.example.expense_tracking.dto.gocardless.GCTransaction;
import com.example.expense_tracking.dto.gocardless.GCTransactionResponse;
import com.example.expense_tracking.entity.*;
import com.example.expense_tracking.repository.BankConfigRepository;
import com.example.expense_tracking.repository.SyncLogRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Responsible for synchronizing bank transactions from GoCardless
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionSyncService {
    private final GoCardlessService goCardlessService;
    private final TransactionRepository transactionRepository;
    private final BankConfigRepository bankConfigRepository;
    private final SyncLogRepository syncLogRepository;
    private final SyncConfig syncConfig;

    // Status constants for BankConfig
    private static final String STATUS_LINKED = "LINKED";
    private static final String STATUS_EXPIRED = "EXPIRED";

    // Status constants for SyncLog
    public static final String SYNC_SUCCESS = "SUCCESS";
    public static final String SYNC_FAILED = "FAILED";
    public static final String SYNC_SKIPPED = "SKIPPED";

    // Default description when bank provides none
    private static final String DEFAULT_DESCRIPTION = "Bank transaction";

    // ==================== PUBLIC METHODS ====================

    // SYNC ALL ACTIVE ACCOUNTS - INTENDED TO BE CALLED BY SCHEDULER
    @Transactional
    public void syncAllActiveAccounts() {
        log.info("Starting batch sync for all active accounts");

        // 1. Get all Bankconfigs with status = LINKED
        List<BankConfig> linkedAccounts = bankConfigRepository.findByStatus(STATUS_LINKED);
        log.info("Found {} linked bank accounts", linkedAccounts.size());

        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        // 2. Process each BankConfig
        for (BankConfig bankConfig : linkedAccounts) {
            // 2a. Calculate dateFrom and dateTo based on lookback period
            LocalDate dateTo = LocalDate.now();
            LocalDate dateFrom = dateTo.minusDays(syncConfig.getLookBackDays());
            try {
                // 2b. Check if user is active
                if (!isUserActive(bankConfig.getUser())) {
                    log.debug("Skipping sync for bank config {} - user {} is inactive",
                            bankConfig.getId(), bankConfig.getUser().getEmail());
                    skipCount++;
                    continue;
                }
                // 2c. Check if bank access has expired
                if (checkAndUpdateExpiredAccess(bankConfig)) {
                    log.info("Skipping sync for bank config {} - access expired",
                            bankConfig.getId());
                    createSyncLog(bankConfig, SYNC_SKIPPED, dateFrom, dateTo, 0, 0, "Bank access expired");
                    skipCount++;
                    continue;
                }

                // 2d. Fetch and save new transactions from the bank
                int newTransactions = syncTransactions(bankConfig, dateFrom, dateTo);
                log.info("Synced bank config {}: {} new transactions",
                        bankConfig.getId(), newTransactions);
                successCount++;
            } catch (Exception e) {
                // Error handling. Log and continue with other accounts
                log.error("Failed to sync bank config {}: {}",
                        bankConfig.getId(), e.getMessage(), e);
                createSyncLog(bankConfig, SYNC_FAILED, dateFrom, dateTo, 0, 0, e.getMessage());
                failCount++;
            }
        }
        // 3. Log final batch sync summary
        log.info("Batch sync completed - Success: {}, Skipped: {}, Failed: {}",
                successCount, skipCount, failCount);
    }

    // INITIAL SYNC FOR A NEWLY LINKED BANK ACCOUNT (RETURN NUMBER OF NEW TRANSACTIONS)
    // FETCHES TRANSACTION HISTORY FOR THE CONFIGURED INITIAL SYNC DAYS (DEFAULT 90 DAYS)
    @Transactional
    public int initialSync(BankConfig bankConfig) {
        log.info("Starting sync for bank config {} (account: {}",
            bankConfig.getId(), bankConfig.getUser().getEmail());
        // 1. Calculate date range for initial sync (default 90 days)
        LocalDate dateTo = LocalDate.now();
        LocalDate dateFrom = dateTo.minusDays(syncConfig.getInitialSyncDays());

        // 2. Fetch, save, and return count of new transactions
        return syncTransactions(bankConfig, dateFrom, dateTo);
    }

    // SYNC TRANSACTIONS FOR A SPECIFIC BANK ACCOUNT AND DATE RANGE (RETURN NUMBER OF NEW TRANSACTIONS SAVED)
    @Transactional
    public int syncTransactions(BankConfig bankConfig, LocalDate dateFrom, LocalDate dateTo) {
        log.debug("Syncing transactions for bank config {} from {} to {}",
                bankConfig.getId(), dateFrom, dateTo);

        // 1. Fetch transactions from GoCardless API
        GCTransactionResponse response = goCardlessService.getTransactions(
                bankConfig.getGocardlessAccountId(), dateFrom, dateTo);

        // 2. Validate response - return early if no data
        if (response == null || response.getTransactions() == null) {
            log.warn("No transactions data returned for bank config {}", bankConfig.getId());
            createSyncLog(bankConfig, SYNC_SUCCESS, dateFrom, dateTo, 0, 0, null);
            updateLastSyncedAt(bankConfig);
            return 0;
        }

        // 3. Get only confirmed/booked transactions (ignore pending)
        List<GCTransaction> bookedTransactions = response.getTransactions().getBooked();
        // 4. Return early if no booked transactions
        if (bookedTransactions == null || bookedTransactions.isEmpty()) {
            log.debug("No booked transactions found for bank config {}", bankConfig.getId());
            createSyncLog(bankConfig, SYNC_SUCCESS, dateFrom, dateTo, 0, 0, null);
            updateLastSyncedAt(bankConfig);
            return 0;
        }
        log.debug("Processing {} booked transactions for bank config {}",
                bookedTransactions.size(), bankConfig.getId());

        // 5. Process each transaction (skip duplicates, save new ones)
        int newCount = 0;
        int fetchedCount = bookedTransactions.size();

        for (GCTransaction gcTransaction : bookedTransactions) {
            boolean isNew = processTransaction(gcTransaction, bankConfig);
            if (isNew) {
                newCount++;
            }
        }

        // 6. Update last synced timestamp
        updateLastSyncedAt(bankConfig);

        // 7. Record log the sync result
        createSyncLog(bankConfig, SYNC_SUCCESS, dateFrom, dateTo, fetchedCount, newCount, null);

        log.info("Sync completed for bank config {}: fetch {}, new {}",
                bankConfig.getId(), fetchedCount, newCount);

        return newCount;
    }

    // ==================== PRIVATE METHODS ====================

    // PROCESS A SINGLE TRANSACTION FROM GOCARDLESS
    private boolean processTransaction(GCTransaction gcTransaction, BankConfig bankConfig) {
        // 1. Extract the unique transaction ID
        String transactionId = gcTransaction.getTransactionId();

        // 2. Check for duplicate, skips if already exists
        if (transactionRepository.existsByBankTransactionIdAndBankConfig(transactionId, bankConfig)) {
            log.debug("Skipping duplicate transaction: {}", transactionId);
            return false;
        }

        // 3. Map to internal format and save new transaction
        Transaction transaction = mapToTransaction(gcTransaction, bankConfig);
        transactionRepository.save(transaction);

        log.trace("Saved new transaction: {} amount={} type={}",
                transactionId, transaction.getAmount(), transaction.getType());

        return true;
    }

    // MAP A GOCARDLESS TRANSACTION TO INTERNAL TRANSACTION ENTITY
    private Transaction mapToTransaction(GCTransaction gcTransaction, BankConfig bankConfig) {
        // 1. Parse amount (keep original sign)
        BigDecimal amount = new BigDecimal(gcTransaction.getTransactionAmount().getAmount());

        // 2. Determine type from amount sign
        TransactionType type = determineTransactionType(amount);

        // 3. Get currency
        String currency = gcTransaction.getTransactionAmount().getCurrency();

        // 4. Parse booking date to LocalDateTime (at start of day)
        LocalDateTime transactionDate = parseBookingDate(gcTransaction.getBookingDate());

        // 5. Build description from available fields
        String description = buildDescription(gcTransaction);

        return Transaction.builder()
                .user(bankConfig.getUser())
                .bankConfig(bankConfig)
                .bankTransactionId(gcTransaction.getTransactionId())
                .amount(amount)  // Keep original signed value
                .currency(currency)
                .type(type)
                .description(description)
                .transactionDate(transactionDate)
                .category(null)
                .build();
    }

    // DETERMINE TRANSACTION TYPE BASED ON AMOUNT SIGN
    private TransactionType determineTransactionType(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) >= 0 ? TransactionType.IN : TransactionType.OUT;
    }

    // PARSE BOOKING DATE STRING TO LOCALDATETIME
    private LocalDateTime parseBookingDate(String bookingDate) {
        // 1. If date is missing, use current time
        if (bookingDate == null) {
            return LocalDateTime.now();
        }

        try {
            // 2. Parse ISO date and convert to datetime at midnight
            LocalDate date = LocalDate.parse(bookingDate, DateTimeFormatter.ISO_DATE);
            return date.atStartOfDay();
        } catch (Exception e) {
            // 3. Parsing failed, use current time
            log.warn("Failed to parse booking date '{}', using current time", bookingDate);
            return LocalDateTime.now();
        }
    }

    // BUILD A HUMAN-READABLE DESCRIPTION FOR THE TRANSACTION
    private String buildDescription(GCTransaction gcTransaction) {
        StringBuilder description = new StringBuilder();

        // 1. Get counterparty name (creditor or debtor)
        String counterpartyName = null;
        if (gcTransaction.getCreditorName() != null && !gcTransaction.getCreditorName().isBlank()) {
            counterpartyName = gcTransaction.getCreditorName();
        } else if (gcTransaction.getDebtorName() != null && !gcTransaction.getDebtorName().isBlank()) {
            counterpartyName = gcTransaction.getDebtorName();
        }

        // 2. Add counterparty name if available
        if (counterpartyName != null) {
            description.append(counterpartyName);
        }

        // 3. Add remittance information if available
        String remittanceInfo = gcTransaction.getRemittanceInformationUnstructured();
        if (remittanceInfo != null && !remittanceInfo.isBlank()) {
            if (!description.isEmpty()) {
                description.append(" - ");
            }
            description.append(remittanceInfo);
        }

        // 4. Fallback to default if no description data
        if (description.isEmpty()) {
            return DEFAULT_DESCRIPTION;
        }

        // 5. Return the constructed description
        return description.toString();
    }

    // CHECK IF A USER IS CONSIDERED "ACTIVE" FOR SYNC PURPOSES
    private boolean isUserActive(User user) {
        if (user == null) {
            return false;
        }
        // 1. Null lastActiveAt = treat as active
        if (user.getLastActiveAt() == null) {
            return true;
        }
        // 2. Check if last active within configured days
        LocalDateTime cutoff = LocalDateTime.now().minusDays(syncConfig.getActiveUserDays());
        return user.getLastActiveAt().isAfter(cutoff);
    }

    // GOCARDLESS BANK AUTHORIZATIONS EXPIRE AFTER 90-180 DAYS (VARIES BY BANK)
    // WHEN EXPIRED, USER MUST RE-LINK THEIR BANK ACCOUNT
    private boolean checkAndUpdateExpiredAccess(BankConfig bankConfig) {
        // 1. Get expiration time
        LocalDateTime expiresAt = bankConfig.getAccessExpiresAt();

        // 2. If no expiry set, assume valid
        if (expiresAt == null) {
            return false;
        }
        // 3. Check if expired
        if (LocalDateTime.now().isAfter(expiresAt)) {
            log.info("Bank access expired for config {} (expires at {}",
                    bankConfig.getId(), expiresAt);
            // Update status to EXPIRED
            bankConfig.setStatus(STATUS_EXPIRED);
            bankConfigRepository.save(bankConfig);
            return true;
        }

        // Not expired
        return false;
    }

    private void updateLastSyncedAt(BankConfig bankConfig) {
        bankConfig.setLastSyncedAt(LocalDateTime.now());
        bankConfigRepository.save(bankConfig);
    }

    // CREATE A SYNCLOG ENTRY TO RECORD THIS SYNC ATTEMPT
    private void createSyncLog(BankConfig bankConfig, String status,
                               LocalDate dateFrom, LocalDate dateTo,
                               int transactionsFetched, int transactionsNew,
                               String errorMessage) {
        SyncLog syncLog = SyncLog.builder()
                .bankConfig(bankConfig)
                .syncedAt(LocalDateTime.now())
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .transactionsFetched(transactionsFetched)
                .transactionsNew(transactionsNew)
                .status(status)
                .errorMessage(errorMessage)
                .build();
        syncLogRepository.save(syncLog);
    }
}

