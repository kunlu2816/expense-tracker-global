package com.example.expense_tracking.dto.bank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Response for GET /api/banks and GET /api/banks/{id}
// Represents a user's linked bank account with masked IBAN for security
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountResponse {
    // Internal ID of the bank config
    private Long id;

    // GoCardless institution ID
    private String institutionId;

    // Human-readable bank name
    private String institutionName;

    // URL to bank's logo image
    private String institutionLogo;

    // Masked IBAN (e.g. "DE** **** **** 1234")
    private String maskedIban;

    // Account holder name
    private String accountName;

    // Current status: PENDING, LINKED, EXPIRED, ERROR
    private String status;

    // When this account was last synced
    private LocalDateTime lastSyncedAt;

    // When the bank access authorization expires
    private LocalDateTime accessExpiresAt;

    // When this bank account was linked
    private LocalDateTime createdAt;
}
