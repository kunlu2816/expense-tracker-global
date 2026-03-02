package com.example.expense_tracking.dto.gocardless;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

// Request body when asking to create a new Requisition (bank linking session)
// Sent to POST /api/v2/requisitions/ to start the bank linking process.
// GoCardless returns a URL where user is redirected to authorize access to their bank account.
@Data
@Builder
public class GCRequisitionRequest {
    private String redirect; // where to redirect after auth

    @JsonProperty("institution_id")
    private String institutionId; // Bank ID

    private String reference; // Out internal reference (optional)

    private String agreement; // End user agreement ID (optional)

    @JsonProperty("user_language")
    private String userLanguage;
}
