package com.example.expense_tracking.dto.gocardless;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

// The main response containing transaction data from the banks via GoCardless
// Maps the JSON from GET /api/v2/accounts/{id}/transactions/
// This is the main data sync to database for expenses and income
// TRANSACTION CATEGORIES: (booked: sync, pending: ignore)
@Data
public class GCTransactionResponse {
    private Transaction transactions;

    @JsonProperty("last_updated")
    private String lastUpdated;

    @Data
    public static class Transaction {
        private List<GCTransaction> booked;
        private List<GCTransaction> pending;
    }
}
