package com.example.expense_tracking.dto.bank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Response for GET /api/banks/callback
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackResponse {
    // Status: SUCCESS, FAILED or PENDING
    private String status;

    // Human-readable message about the callback result
    private String message;

    // List of bank accounts that were linked (may be multiple)
    private List<BankAccountResponse> bankAccounts;

    // Total transactions synced during initial sync
    private int transactionsSynced;

    // Error details if status = FAILED
    private String error;
}
