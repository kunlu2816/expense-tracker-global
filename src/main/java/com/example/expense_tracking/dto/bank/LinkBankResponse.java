package com.example.expense_tracking.dto.bank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Response for POST /api/banks/link
// Contains the requisition ID and the link where user should be redirected to authorize bank access
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkBankResponse {
    // Unique ID for this bank linking session
    private String requisitionId;
    // URL to redirect user to for bank authorization
    private String link;
    // Name of the institution being linked
    private String institutionName;
}
