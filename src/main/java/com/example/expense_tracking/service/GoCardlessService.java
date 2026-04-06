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
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoCardlessService {
    private final GoCardlessConfig config;
    private final RestTemplate restTemplate;
    private final AtomicReference<String> accessToken = new AtomicReference<>();
    private final AtomicReference<Instant> tokenExpiresAt = new AtomicReference<>();
    /**
     * Get a valid access token, refreshing if needed
     */
    public synchronized String getAccessToken() {
        // Before doing anything, check if
        // Do I have any token, do I know when the token expires, does the token expires in more than 60 seconds?
        String token = accessToken.get();
        Instant expiresAt = tokenExpiresAt.get();
        if (token != null && expiresAt != null
                && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return token;
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
        try {
            ResponseEntity<GCTokenResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, GCTokenResponse.class
            );
            // Extract from GCTokenResponse to get access token and expiration
            GCTokenResponse tokenResponse = response.getBody();
            if (tokenResponse != null && tokenResponse.getAccessExpires() != null) {
                this.accessToken.set(tokenResponse.getAccess());
                this.tokenExpiresAt.set(Instant.now().plusSeconds(tokenResponse.getAccessExpires()));
                log.info("GoCardless token refreshed, expires at: {}", this.tokenExpiresAt.get());
            }
        } catch (Exception e) {
            log.error("Failed to refresh GoCardless access token: {}", e.getMessage(), e);
            throw e;
        }
        return accessToken.get();
    }
    /**
     * List available banks/institutions for a country
     */
    public List<GCInstitution> getInstitutions(String countryCode) {
        String url = config.getBaseUrl() + "/api/v2/institutions/?country=" + countryCode;
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<GCInstitution[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, GCInstitution[].class
            );
            return response.getBody() != null ? List.of(response.getBody()) : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch institutions for country {}: {}", countryCode, e.getMessage(), e);
            return List.of();
        }
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
        try {
            ResponseEntity<GCRequisitionResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, GCRequisitionResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to create requisition for institution {}: {}", institutionId, e.getMessage(), e);
            return null;
        }
    }
    /**
     * Get requisition status and account IDs
     */
    public GCRequisitionResponse getRequisition(String requisitionId) {
        String url = config.getBaseUrl() + "/api/v2/requisitions/" + requisitionId + "/";
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<GCRequisitionResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, GCRequisitionResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch requisition {}: {}", requisitionId, e.getMessage(), e);
            return null;
        }
    }
    /**
     * Get account details (IBAN, owner name, etc.)
     */
    public GCAccountDetails getAccountDetails(String accountId) {
        String url = config.getBaseUrl() + "/api/v2/accounts/" + accountId + "/details/";
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<GCAccountDetailsWrapper> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, GCAccountDetailsWrapper.class
            );
            return response.getBody() != null ? response.getBody().getAccount() : null;
        } catch (Exception e) {
            log.error("Failed to fetch account details for {}: {}", accountId, e.getMessage(), e);
            return null;
        }
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
        try {
            ResponseEntity<GCTransactionResponse> response = restTemplate.exchange(
                    urlBuilder.toString(), HttpMethod.GET, request, GCTransactionResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch transactions for account {}: {}", accountId, e.getMessage(), e);
            return null;
        }
    }
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String token = getAccessToken();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}
