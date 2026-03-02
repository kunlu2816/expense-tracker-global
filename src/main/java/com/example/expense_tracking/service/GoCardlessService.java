package com.example.expense_tracking.service;

import com.example.expense_tracking.config.GoCardlessConfig;
import com.example.expense_tracking.dto.gocardless.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoCardlessService {
    private final GoCardlessConfig config;
    private final RestTemplate restTemplate;
    private String accessToken;
    private Instant tokenExpiresAt;
    /**
     * Get a valid access token, refreshing if needed
     */
    public String getAccessToken() {
        // Before doing anything, check if
        // Do I have any token, do I know when the token expires, does the token expires in more than 60 seconds?
        if (accessToken != null && tokenExpiresAt != null
                && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }
        // If the token is missing or expired, create a new one
        String url = config.getBaseUrl() + "/api/v2/token/new/";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "secret_id", config.getSecretId(),
                "secret_key", config.getSecretKey()
        );
        // Send request to URL via POST method with body and headers and expect a GCTokenResponse class back
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<GCTokenResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, request, GCTokenResponse.class
        );
        // Extract from GCTokenResponse to get access token and expiration
        GCTokenResponse tokenResponse = response.getBody();
        if (tokenResponse != null && tokenResponse.getAccessExpires() != null) {
            this.accessToken = tokenResponse.getAccess();
            this.tokenExpiresAt = Instant.now().plusSeconds(tokenResponse.getAccessExpires());
            log.info("GoCardless token refreshed, expires at: {}", tokenExpiresAt);
        }
        return accessToken;
    }
    /**
     * List available banks/institutions for a country
     */
    public List<GCInstitution> getInstitutions(String countryCode) {
        String url = config.getBaseUrl() + "/api/v2/institutions/?country=" + countryCode;
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<GCInstitution[]> response = restTemplate.exchange(
                url, HttpMethod.GET, request, GCInstitution[].class
        );
        return response.getBody() != null ? List.of(response.getBody()) : List.of();
    }
    /**
     * Create a requisition to link a bank account
     */
    public GCRequisitionResponse createRequisition(String institutionId, String reference) {
        String url = config.getBaseUrl() + "/api/v2/requisitions/";
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        GCRequisitionRequest body = GCRequisitionRequest.builder()
                .redirect(config.getRedirectUrl())
                .institutionId(institutionId)
                .reference(reference)
                .userLanguage("EN")
                .build();
        HttpEntity<GCRequisitionRequest> request = new HttpEntity<>(body, headers);
        ResponseEntity<GCRequisitionResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, request, GCRequisitionResponse.class
        );
        return response.getBody();
    }
    /**
     * Get requisition status and account IDs
     */
    public GCRequisitionResponse getRequisition(String requisitionId) {
        String url = config.getBaseUrl() + "/api/v2/requisitions/" + requisitionId + "/";
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<GCRequisitionResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, request, GCRequisitionResponse.class
        );
        return response.getBody();
    }
    /**
     * Get account details (IBAN, owner name, etc.)
     */
    public GCAccountDetails getAccountDetails(String accountId) {
        String url = config.getBaseUrl() + "/api/v2/accounts/" + accountId + "/details/";
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<GCAccountDetailsWrapper> response = restTemplate.exchange(
                url, HttpMethod.GET, request, GCAccountDetailsWrapper.class
        );
        return response.getBody() != null ? response.getBody().getAccount() : null;
    }
    /**
     * Fetch transactions for an account
     */
    public GCTransactionResponse getTransactions(String accountId, LocalDate dateFrom, LocalDate dateTo) {
        StringBuilder urlBuilder = new StringBuilder()
                .append(config.getBaseUrl())
                .append("/api/v2/accounts/")
                .append(accountId)
                .append("/transactions/");
        if (dateFrom != null || dateTo != null) {
            urlBuilder.append("?");
            if (dateFrom != null) {
                urlBuilder.append("date_from=").append(dateFrom);
            }
            if (dateTo != null) {
                if (dateFrom != null) urlBuilder.append("&");
                urlBuilder.append("date_to=").append(dateTo);
            }
        }
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<GCTransactionResponse> response = restTemplate.exchange(
                urlBuilder.toString(), HttpMethod.GET, request, GCTransactionResponse.class
        );
        return response.getBody();
    }
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}
