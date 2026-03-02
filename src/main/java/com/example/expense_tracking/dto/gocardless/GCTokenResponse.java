package com.example.expense_tracking.dto.gocardless;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

// Maps the JSON response when we call POST /api/v2/token/new/
// When authenticate with GoCardless, we get back a token response. This DTO captures that response.
@Data
public class GCTokenResponse {
    // JWT access token for API authentication
    // Use in the header: "Authorization: Bearer {access}"
    private String access;

    @JsonProperty("access_expires")
    private Integer accessExpires; // seconds until access token expires (24 hours)

    // refresh token to get new access token when it expires
    private String refresh;

    @JsonProperty("refresh_expires")
    private Integer refreshExpires; // seconds until refresh token expires (30 days)
}
