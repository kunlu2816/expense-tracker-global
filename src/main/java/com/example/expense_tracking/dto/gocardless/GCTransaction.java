package com.example.expense_tracking.dto.gocardless;

import lombok.Data;

// Single transaction from bank account
// Part of GCTransactionResponse.transactions.booked or .pending
@Data
public class GCTransaction {
    private String transactionId;
    private String debtorName; // Who sent money (for incoming/credit transactions)
    private GCAccountReference debtorAccount;
    private String creditorName; // Who received money (for outgoing/debit transactions)
    private GCAccountReference creditorAccount;
    private GCTransactionAmount transactionAmount; // amount can be negative (expense) or positive (income)
    private String bankTransactionCode;
    private String bookingDate;
    private String valueDate;
    private String remittanceInformationUnstructured;
}
