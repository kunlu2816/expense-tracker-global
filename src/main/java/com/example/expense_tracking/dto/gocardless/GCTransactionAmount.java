package com.example.expense_tracking.dto.gocardless;

import lombok.Data;

@Data
public class GCTransactionAmount {
    private String currency;
    private String amount;
}
