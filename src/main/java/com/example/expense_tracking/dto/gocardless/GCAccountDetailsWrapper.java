package com.example.expense_tracking.dto.gocardless;

import lombok.Data;

// Wrapper for account details response from GET /api/v2/accounts/{id}/details/
// The actual account data is nested inside "account" key
@Data
public class GCAccountDetailsWrapper {
    private GCAccountDetails account;
}
