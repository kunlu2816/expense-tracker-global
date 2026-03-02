package com.example.expense_tracking.dto.gocardless;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

// Maps the JSON when we call GET /api/v2/institutions/?country=GB
// Used to show users a list of banks they can connect to via GoCardless
@Data
public class GCInstitution {
    private String id;

    private String name;

    private String bic; // Bank Identifier Code (SWIFT/BIC)

    @JsonProperty("transaction_total_days")
    private String transactionTotalDays;

    private List<String> countries;

    private String logo;

    // How long access is valid for (in days), after this user must re-authorize
    @JsonProperty("max_access_valid_for_days")
    private String maxAccessValidForDays;

    @JsonProperty("max_access_valid_for_days_reconfirmation")
    private String maxAccessValidForDaysReconfirmation;
}
