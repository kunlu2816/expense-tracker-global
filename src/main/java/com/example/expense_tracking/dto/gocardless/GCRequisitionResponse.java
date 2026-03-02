package com.example.expense_tracking.dto.gocardless;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

// Response after creating a requisition - contains the URL to redirect user to for bank authorization
// Maps the JSON response from POST /api/v2/requisitions/
@Data
public class GCRequisitionResponse {
    private String id;                    // Requisition ID
    private String status;                // CR, LN, EX, RJ, UA, GA, SA
    private String link;                  // URL to redirect user for bank auth

    @JsonProperty("institution_id")
    private String institutionId;

    private String agreement;             // End user agreement ID
    private String reference;             // Our reference
    private List<String> accounts;        // Account IDs (populated after user links)
    private String created;
    private String redirect;

    @JsonProperty("user_language")
    private String userLanguage;

    private String ssn;

    @JsonProperty("account_selection")
    private Boolean accountSelection;

    @JsonProperty("redirect_immediate")
    private Boolean redirectImmediate;
}
