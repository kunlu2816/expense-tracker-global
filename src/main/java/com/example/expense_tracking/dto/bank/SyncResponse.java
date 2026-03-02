package com.example.expense_tracking.dto.bank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// Response for POST /api/banks/{id}/sync
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResponse {
    // Sync result: SUCCESS or FAILED
    private String status;

    // Start date that was synced
    private LocalDate dateFrom;

    // End date that was synced
    private LocalDate dateTo;

    // Total transactions returned by the bank
    private int transactionsFetched;

    // New transactions saved (excluding duplicates)
    private int transactionsNew;

    // Error message if sync failed (status = FAILED)
    private String errorMessage;
}
