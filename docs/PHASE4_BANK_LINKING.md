# Phase 4: Bank Linking & Management

This document provides a complete reference for the bank linking workflow, including all endpoints, DTOs, services, repositories, and entity relationships.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [File Structure](#file-structure)
3. [Workflow 1: List Available Banks](#workflow-1-list-available-banks)
4. [Workflow 2: Start Bank Linking](#workflow-2-start-bank-linking)
5. [Workflow 3: Handle Callback](#workflow-3-handle-callback-after-user-authorizes)
6. [Workflow 4: List User's Bank Accounts](#workflow-4-list-users-bank-accounts)
7. [Workflow 5: Get Single Bank Account](#workflow-5-get-single-bank-account)
8. [Workflow 6: Manual Sync](#workflow-6-manual-sync)
9. [Workflow 7: Unlink Bank Account](#workflow-7-unlink-bank-account)
10. [Entity Relationships](#entity-relationships)
11. [Repository Methods Summary](#repository-methods-summary)
12. [Configuration Reference](#configuration-reference)
13. [BankConfig Status State Machine](#bankconfig-status-state-machine)

---

## Architecture Overview

```
+-----------------------------------------------------------------------------------+
|                                   FRONTEND (Client)                               |
+-----------------------------------------------------------------------------------+
                                            |
                                            v
+-----------------------------------------------------------------------------------+
|                                  BankController.java                              |
|                                 @RequestMapping("/api/banks")                     |
|-----------------------------------------------------------------------------------|
|  GET  /institutions?country=   | POST /link        | GET /callback?ref=          |
|  GET  /                        | GET  /{id}        | POST /{id}/sync             |
|  DELETE /{id}                  |                   |                              |
+-----------------------------------------------------------------------------------+
                                            |
                                            v
+-----------------------------------------------------------------------------------+
|                               BankLinkingService.java                             |
|-----------------------------------------------------------------------------------|
|  getInstitutions()    | startLinking()      | processCallback()                  |
|  getUserBankAccounts()| getUserBankAccount()| manualSync()                       |
|  unlinkBank()         |                     |                                     |
+-----------------------------------------------------------------------------------+
                |                                              |
                v                                              v
+----------------------------+                +------------------------------------------+
|   GoCardlessService.java   |                |    TransactionSyncService.java           |
|----------------------------|                |------------------------------------------|
| getAccessToken()           |                | initialSync()                            |
| getInstitutions()          |                | syncTransactions()                       |
| createRequisition()        |                |                                          |
| getRequisition()           |                |                                          |
| getAccountDetails()        |                |                                          |
| getTransactions()          |                |                                          |
+----------------------------+                +------------------------------------------+
                |                                              |
                v                                              v
+----------------------------+                +------------------------------------------+
|    GoCardless Bank API     |                |       BankConfigRepository.java          |
|    (External Service)      |                |       TransactionRepository.java         |
|                            |                |       SyncLogRepository.java             |
+----------------------------+                +------------------------------------------+
```

---

## File Structure

```
src/main/java/com/example/expense_tracking/
|-- config/
|   |-- GoCardlessConfig.java      <-- API credentials & redirect URL
|   +-- SyncConfig.java            <-- Sync settings (intervals, days)
|
|-- controller/
|   +-- BankController.java        <-- REST endpoints for bank operations
|
|-- dto/
|   |-- bank/
|   |   |-- LinkBankRequest.java       <-- POST /link body
|   |   |-- LinkBankResponse.java      <-- POST /link response
|   |   |-- BankAccountResponse.java   <-- GET /banks response item
|   |   |-- SyncRequest.java           <-- POST /{id}/sync body
|   |   |-- SyncResponse.java          <-- POST /{id}/sync response
|   |   +-- CallbackResponse.java      <-- GET /callback response
|   |
|   +-- gocardless/
|       |-- GCInstitution.java             <-- Bank info from API
|       |-- GCRequisitionRequest.java      <-- Create requisition body
|       |-- GCRequisitionResponse.java     <-- Requisition with link
|       |-- GCAccountDetails.java          <-- Account IBAN, owner
|       |-- GCAccountDetailsWrapper.java   <-- Wrapper for nested JSON
|       |-- GCTransaction.java             <-- Single transaction
|       |-- GCTransactionResponse.java     <-- Transactions list
|       |-- GCTransactionAmount.java       <-- Amount + currency
|       |-- GCAccountReference.java        <-- Account reference
|       +-- GCTokenResponse.java           <-- Access token
|
|-- entity/
|   |-- BankConfig.java            <-- Bank account entity
|   |-- Transaction.java           <-- Transaction entity
|   |-- SyncLog.java               <-- Sync history entity
|   +-- User.java                  <-- User entity
|
|-- repository/
|   |-- BankConfigRepository.java      <-- Bank config queries
|   |-- TransactionRepository.java     <-- Transaction queries
|   +-- SyncLogRepository.java         <-- Sync log queries
|
+-- service/
    |-- GoCardlessService.java         <-- GoCardless API client
    |-- TransactionSyncService.java    <-- Transaction sync logic
    +-- BankLinkingService.java        <-- Bank linking orchestration
```

---

## Workflow 1: List Available Banks

### Endpoint: `GET /api/banks/institutions?country=GB`

### Sequence Diagram

```
+----------+     +----------------+     +-------------------+     +----------------+     +-----------------+
|  Client  |---->| BankController |---->| BankLinkingService|---->|GoCardlessService|---->| GoCardless API  |
+----------+     +----------------+     +-------------------+     +----------------+     +-----------------+
                                                                          |                       |
                                                                          |   GET /institutions/  |
                                                                          |    ?country=GB        |
                                                                          |---------------------->|
                                                                          |                       |
                                                                          |<----------------------|
                                                                          |   [GCInstitution[]]   |
                 <-------------------------------------------------------------
                           List<GCInstitution>
```

### Data Flow

| Step | Component | Method | Input | Output |
|------|-----------|--------|-------|--------|
| 1 | `BankController` | `getInstitutions()` | `country="GB"` | `ResponseEntity<List<GCInstitution>>` |
| 2 | `BankLinkingService` | `getInstitutions()` | `countryCode` | `List<GCInstitution>` |
| 3 | `GoCardlessService` | `getInstitutions()` | `countryCode` | `List<GCInstitution>` |
| 4 | `GoCardlessService` | `getAccessToken()` | - | `String` (cached token) |
| 5 | GoCardless API | `GET /api/v2/institutions/` | `?country=GB` | JSON array of institutions |

### DTO: GCInstitution.java

```java
@Data
public class GCInstitution {
    private String id;                      // "REVOLUT_REVOGB21"
    private String name;                    // "Revolut"
    private String bic;                     // SWIFT/BIC code
    private String transactionTotalDays;    // How many days of history available
    private List<String> countries;         // ["GB", "IE"]
    private String logo;                    // URL to bank logo
    private String maxAccessValidForDays;   // How long access lasts
}
```

### Example Response

```json
[
  {
    "id": "REVOLUT_REVOGB21",
    "name": "Revolut",
    "bic": "REVOGB21",
    "transactionTotalDays": "730",
    "countries": ["GB"],
    "logo": "https://cdn.gocardless.com/icons/revolut.png",
    "maxAccessValidForDays": "90"
  }
]
```

---

## Workflow 2: Start Bank Linking

### Endpoint: `POST /api/banks/link`

### Sequence Diagram

```
+----------+           +----------------+           +-------------------+
|  Client  |---------->| BankController |---------->| BankLinkingService|
+----------+           +----------------+           +-------------------+
     |                                                       |
     | POST /link                                            |
     | { institutionId: "REVOLUT_REVOGB21" }                 |
     |                                                       |
     |                                                       v
     |                                              +----------------+
     |                                              |GoCardlessService|
     |                                              +----------------+
     |                                                       |
     |                                                       | createRequisition()
     |                                                       v
     |                                              +-----------------+
     |                                              | GoCardless API  |
     |                                              | POST /requisitions/
     |                                              +-----------------+
     |                                                       |
     |                                                       | Returns: { id, link, status }
     |                                                       v
     |                                              +--------------------+
     |                                              | BankConfigRepository
     |                                              |   save(bankConfig) |
     |                                              |   status=PENDING   |
     |                                              +--------------------+
     |                                                       |
     <-------------------------------------------------------
               { requisitionId, link, institutionName }
```

### Data Flow

| Step | Component | Method | Action |
|------|-----------|--------|--------|
| 1 | `BankController` | `linkBank()` | Receive `LinkBankRequest` with `institutionId` |
| 2 | `BankLinkingService` | `startLinking()` | Generate unique reference: `user_{userId}_{timestamp}` |
| 3 | `GoCardlessService` | `createRequisition()` | Call GoCardless API to create requisition |
| 4 | `GoCardlessService` | `getInstitutions()` | Fetch institution name & logo |
| 5 | `BankLinkingService` | - | Create `BankConfig` with status=`PENDING` |
| 6 | `BankConfigRepository` | `save()` | Persist BankConfig |
| 7 | `BankController` | - | Return `LinkBankResponse` with redirect URL |

### DTO: LinkBankRequest.java

```java
@Data
public class LinkBankRequest {
    @NotBlank(message = "Institution ID is required")
    private String institutionId;  // "REVOLUT_REVOGB21"
}
```

### DTO: GCRequisitionRequest.java

```java
@Data @Builder
public class GCRequisitionRequest {
    private String redirect;       // "https://yourapp.com/api/banks/callback"
    private String institutionId;  // "REVOLUT_REVOGB21"
    private String reference;      // "user_123_1699999999999"
    private String userLanguage;   // "EN"
}
```

### DTO: GCRequisitionResponse.java

```java
@Data
public class GCRequisitionResponse {
    private String id;             // "req_abc123"
    private String status;         // "CR" (created)
    private String link;           // "https://ob.gocardless.com/psd2/start/..."
    private String institutionId;
    private String reference;
    private List<String> accounts; // Empty until user authorizes
}
```

### DTO: LinkBankResponse.java

```java
@Data @Builder
public class LinkBankResponse {
    private String requisitionId;     // "req_abc123"
    private String link;              // Redirect URL for bank auth
    private String institutionName;   // "Revolut"
}
```

### Entity Created: BankConfig (status = PENDING)

```java
BankConfig {
    id: 1,
    user: User,
    institutionId: "REVOLUT_REVOGB21",
    institutionName: "Revolut",
    institutionLogo: "https://...",
    requisitionId: "req_abc123",
    gocardlessAccountId: null,      // Not yet available
    iban: null,                      // Not yet available
    status: "PENDING",
    accessExpiresAt: null
}
```

---

## Workflow 3: Handle Callback (After User Authorizes)

### Endpoint: `GET /api/banks/callback?ref=user_123_1699999999999`

> **Note**: After user authorizes on bank's website, GoCardless redirects to this callback endpoint.

### Sequence Diagram

```
+--------------+    +----------------+    +-------------------+    +----------------+
| GoCardless   |--->| BankController |--->| BankLinkingService|--->|GoCardlessService|
| (redirect)   |    | /callback?ref= |    | processCallback() |    |                |
+--------------+    +----------------+    +-------------------+    +----------------+
                                                   |                       |
                                                   |                       |
        +------------------------------------------+-----------------------+-------+
        |                                                                          |
        |  1. Parse reference -> extract userId                                    |
        |  2. Find PENDING BankConfig for user                                     |
        |  3. Verify reference matches via getRequisition()                        |
        |  4. Check status = "LN" (linked)                                         |
        |  5. Get account IDs from requisition.accounts[]                          |
        |  6. For each account:                                                    |
        |     a. getAccountDetails() -> IBAN, ownerName                            |
        |     b. Update/Create BankConfig -> status=LINKED                         |
        |     c. initialSync() -> fetch 90 days of transactions                    |
        |  7. Return CallbackResponse with linked accounts + sync count            |
        |                                                                          |
        +--------------------------------------------------------------------------+
```

### Detailed Step-by-Step

#### Step 1: Parse Reference

```
reference = "user_123_1699999999999"
            +--+---+ +-----+-------+
              userId    timestamp
              = 123
```

#### Step 2-3: Find & Verify BankConfig

```
+-------------------------+
| BankConfigRepository    |
| findByStatus("PENDING") |--> Loop through all PENDING configs
+-------------------------+    where config.user.id == 123
                                    |
                                    v
                          +---------------------+
                          | GoCardlessService   |
                          | getRequisition(id)  |
                          +---------------------+
                                    |
                                    v
                          Check: requisition.reference == "user_123_..."?
                                    |
                                    v Match found!
```

#### Step 4: Verify Status

| Status | Meaning | Action |
|--------|---------|--------|
| `LN` | Linked | Continue processing |
| `RJ` | Rejected | Set status=ERROR, return error |
| `EX` | Expired | Set status=ERROR, return error |
| Other | Still pending | Return pending message |

#### Step 5-6: Process Each Account

```
accounts = ["acc_001", "acc_002"]  // User may have multiple accounts

For each accountId:
+----------------------------------------------------------------+
|                                                                |
|  a. getAccountDetails(accountId)                               |
|     +--> { iban: "GB29NWBK60...", ownerName: "John Doe" }      |
|                                                                |
|  b. Update BankConfig:                                         |
|     - gocardlessAccountId = "acc_001"                          |
|     - iban = "GB29NWBK60161331926819"                          |
|     - accountName = "John Doe"                                 |
|     - status = "LINKED"                                        |
|     - accessExpiresAt = now + 90 days                          |
|                                                                |
|  c. initialSync(bankConfig)                                    |
|     +--> Fetches 90 days of transactions                       |
|     +--> Returns count of new transactions saved               |
|                                                                |
+----------------------------------------------------------------+
```

### DTO: GCAccountDetails.java

```java
@Data
public class GCAccountDetails {
    private String resourceId;
    private String iban;          // "GB29NWBK60161331926819"
    private String currency;      // "GBP"
    private String ownerName;     // "John Doe"
    private String name;          // Account name
    private String product;       // Product type
}
```

### DTO: CallbackResponse.java

```java
@Data @Builder
public class CallbackResponse {
    private String status;                        // "SUCCESS" or "FAILED"
    private String message;                       // "Bank account linked successfully"
    private List<BankAccountResponse> bankAccounts;  // Linked accounts
    private int transactionsSynced;               // Initial sync count
    private String error;                         // Error details if failed
}
```

### DTO: BankAccountResponse.java

```java
@Data @Builder
public class BankAccountResponse {
    private Long id;                  // 1
    private String institutionId;     // "REVOLUT_REVOGB21"
    private String institutionName;   // "Revolut"
    private String institutionLogo;   // URL
    private String maskedIban;        // "GB29****6819"
    private String accountName;       // "John Doe"
    private String status;            // "LINKED"
    private LocalDateTime lastSyncedAt;
    private LocalDateTime accessExpiresAt;
    private LocalDateTime createdAt;
}
```

### Entity Updated: BankConfig (status = LINKED)

```java
BankConfig {
    id: 1,
    user: User,
    institutionId: "REVOLUT_REVOGB21",
    institutionName: "Revolut",
    institutionLogo: "https://...",
    requisitionId: "req_abc123",
    gocardlessAccountId: "acc_001",           // Now set
    iban: "GB29NWBK60161331926819",           // Now set
    accountName: "John Doe",                   // Now set
    status: "LINKED",                          // Changed from PENDING
    accessExpiresAt: 2024-03-15T10:30:00      // Set to now + 90 days
}
```

---

## Workflow 4: List User's Bank Accounts

### Endpoint: `GET /api/banks`

### Sequence Diagram

```
+----------+     +----------------+     +-------------------+     +----------------------+
|  Client  |---->| BankController |---->| BankLinkingService|---->| BankConfigRepository |
+----------+     +----------------+     +-------------------+     +----------------------+
     |                 |                         |                         |
     | GET /banks      | getUserBanks()          | getUserBankAccounts()   | findByUser(user)
     |                 |                         |                         |
     |                 |                         |                         |
     |                 |<------------------------|<------------------------|
     |                 | List<BankAccountResponse>   List<BankConfig>      |
     |<----------------|                         |                         |
           JSON Response
```

### Data Flow

| Step | Component | Method | Action |
|------|-----------|--------|--------|
| 1 | `BankController` | `getUserBanks()` | Extract `User` from JWT token |
| 2 | `BankLinkingService` | `getUserBankAccounts()` | Query all user's bank configs |
| 3 | `BankConfigRepository` | `findByUser()` | Return `List<BankConfig>` |
| 4 | `BankLinkingService` | `mapToBankAccountResponse()` | Convert each to DTO (mask IBAN) |

### Example Response

```json
[
  {
    "id": 1,
    "institutionId": "REVOLUT_REVOGB21",
    "institutionName": "Revolut",
    "institutionLogo": "https://cdn.gocardless.com/...",
    "maskedIban": "GB29****6819",
    "accountName": "John Doe",
    "status": "LINKED",
    "lastSyncedAt": "2024-01-15T10:30:00",
    "accessExpiresAt": "2024-04-15T10:30:00",
    "createdAt": "2024-01-15T10:00:00"
  }
]
```

---

## Workflow 5: Get Single Bank Account

### Endpoint: `GET /api/banks/{id}`

### Sequence Diagram

```
+----------+     +----------------+     +-------------------+     +----------------------+
|  Client  |---->| BankController |---->| BankLinkingService|---->| BankConfigRepository |
+----------+     +----------------+     +-------------------+     +----------------------+
     |                 |                         |                         |
     | GET /banks/1    | getBankAccount()        | getUserBankAccount()    | findByIdAndUser(1, user)
     |                 |                         |                         |
     |                 |                         |                         |
     |                 |<------------------------|<------------------------|
     |                 | BankAccountResponse     |  Optional<BankConfig>   |
     |<----------------|   or 404                |                         |
```

### Repository Query

```java
Optional<BankConfig> findByIdAndUser(Long id, User user);
```

---

## Workflow 6: Manual Sync

### Endpoint: `POST /api/banks/{id}/sync`

### Sequence Diagram

```
+----------+    +----------------+    +-------------------+    +------------------------+
|  Client  |--->| BankController |--->| BankLinkingService|--->| TransactionSyncService |
+----------+    +----------------+    +-------------------+    +------------------------+
     |                                        |                          |
     | POST /banks/1/sync                     |                          |
     | { dateFrom, dateTo } (optional)        | manualSync()             |
     |                                        |                          |
     |                                        | 1. Verify ownership      |
     |                                        | 2. Check status=LINKED   |
     |                                        | 3. Check not expired     |
     |                                        | 4. Calculate dates       |
     |                                        |------------------------->|
     |                                        |                          | syncTransactions()
     |                                        |                          |
     |                                        |                          v
     |                                        |                 +----------------+
     |                                        |                 |GoCardlessService|
     |                                        |                 | getTransactions()|
     |                                        |                 +----------------+
     |                                        |                          |
     |                                        |<-------------------------|
     |                                        | int newTransactions      |
     |<---------------------------------------|                          |
            SyncResponse
```

### Validation Checks in `manualSync()`

| Check | Condition | Failure Response |
|-------|-----------|------------------|
| 1. Ownership | `findByIdAndUser(id, user)` returns null | `{ status: "FAILED", error: "Bank not found" }` |
| 2. Status | `status != "LINKED"` | `{ status: "FAILED", error: "Not linked" }` |
| 3. Access Expiry | `accessExpiresAt != null && accessExpiresAt.isBefore(now)` | `{ status: "FAILED", error: "Access expired" }` |

### Date Calculation

```
dateTo = request.dateTo ?? LocalDate.now()
dateFrom = request.dateFrom ?? dateTo - lookBackDays (default: 3 days)
```

### DTO: SyncRequest.java

```java
@Data
public class SyncRequest {
    private LocalDate dateFrom;  // Optional: start date
    private LocalDate dateTo;    // Optional: end date
}
```

### DTO: SyncResponse.java

```java
@Data @Builder
public class SyncResponse {
    private String status;              // "SUCCESS" or "FAILED"
    private LocalDate dateFrom;         // Actual start date used
    private LocalDate dateTo;           // Actual end date used
    private int transactionsFetched;    // Total from bank
    private int transactionsNew;        // New ones saved
    private String errorMessage;        // Error if failed
}
```

---

## Workflow 7: Unlink Bank Account

### Endpoint: `DELETE /api/banks/{id}`

### Sequence Diagram

```
+----------+    +----------------+    +-------------------+    +------------------------+
|  Client  |--->| BankController |--->| BankLinkingService|--->| TransactionRepository  |
+----------+    +----------------+    +-------------------+    +------------------------+
     |                                        |                          |
     | DELETE /banks/1                        | unlinkBank()             |
     |                                        |                          |
     |                                        | 1. Find BankConfig       |
     |                                        | 2. Verify ownership      |
     |                                        |------------------------->|
     |                                        |                          | unlinkTransactionsFromBankConfig()
     |                                        |                          | SET bankConfig = null
     |                                        |                          | WHERE bankConfig = :config
     |                                        |                          |
     |                                        |                 +------------------------+
     |                                        |---------------->| BankConfigRepository   |
     |                                        |                 | deleteById(id)         |
     |                                        |                 +------------------------+
     |                                        |                          |
     |<---------------------------------------|                          |
           { message: "Bank unlinked successfully" }
```

### Key Behavior: Preserves Transactions

```
+----------------------------------------------------------------+
| UNLINK PRESERVES TRANSACTIONS                                  |
|----------------------------------------------------------------|
|                                                                |
| Before: Transaction.bankConfig = BankConfig(id=1)              |
| After:  Transaction.bankConfig = null                          |
|                                                                |
| The transaction STAYS in the database!                         |
| User keeps their transaction history.                          |
| Only the link to the bank connection is removed.               |
|                                                                |
+----------------------------------------------------------------+
```

### Repository Method

```java
@Modifying
@Query("UPDATE Transaction t SET t.bankConfig = null WHERE t.bankConfig = :bankConfig")
void unlinkTransactionsFromBankConfig(@Param("bankConfig") BankConfig bankConfig);
```

---

## Entity Relationships

### Database Schema

```
+-----------------------------------------------------------------------------------+
|                                  DATABASE SCHEMA                                  |
+-----------------------------------------------------------------------------------+

+-----------+         +-----------------+         +-----------------+
|   users   |---------+  bank_configs   |---------+  transactions   |
+-----------+   1:N   +-----------------+   1:N   +-----------------+
      |                       |                          |
      |                       |                          |
      +-----------------------+------------------------------------+
                              |
                       +------+------+
                       |  sync_logs  |
                       +-------------+
```

### Table: users

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Primary key |
| email | VARCHAR | User email |
| password | VARCHAR | Hashed password |
| last_active_at | TIMESTAMP | For filtering inactive users |

### Table: bank_configs

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Primary key |
| user_id | BIGINT FK | Reference to users |
| institution_id | VARCHAR | e.g., "REVOLUT_REVOGB21" |
| institution_name | VARCHAR | e.g., "Revolut" |
| institution_logo | VARCHAR | URL to logo |
| requisition_id | VARCHAR | GoCardless session ID |
| gocardless_account_id | VARCHAR (unique) | Account ID for fetching transactions |
| iban | VARCHAR | e.g., "GB29NWBK60161331926819" |
| account_name | VARCHAR | e.g., "John Doe" |
| status | VARCHAR | PENDING, LINKED, EXPIRED, ERROR |
| access_expires_at | TIMESTAMP | When consent expires |
| last_synced_at | TIMESTAMP | Last successful sync |
| created_at | TIMESTAMP | Creation timestamp |

### Table: transactions

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Primary key |
| user_id | BIGINT FK | Reference to users |
| bank_config_id | BIGINT FK (nullable) | Reference to bank_configs |
| bank_transaction_id | VARCHAR | GoCardless transaction ID |
| amount | DECIMAL | Signed amount (-100.50) |
| currency | VARCHAR | "GBP", "EUR" |
| type | ENUM | IN, OUT |
| description | VARCHAR | e.g., "Starbucks - Coffee" |
| transaction_date | TIMESTAMP | Transaction date |
| category_id | BIGINT FK (nullable) | Reference to categories |

### Table: sync_logs

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Primary key |
| bank_config_id | BIGINT FK | Reference to bank_configs |
| synced_at | TIMESTAMP | When sync occurred |
| date_from | DATE | Start of sync range |
| date_to | DATE | End of sync range |
| transactions_fetched | INT | Total fetched from bank |
| transactions_new | INT | New ones saved |
| status | VARCHAR | SUCCESS, FAILED, SKIPPED |
| error_message | VARCHAR | Error details if failed |

---

## Repository Methods Summary

### BankConfigRepository.java

```java
@Repository
public interface BankConfigRepository extends JpaRepository<BankConfig, Long> {
    // Find by GoCardless account ID
    Optional<BankConfig> findByGocardlessAccountId(String gocardlessAccountId);

    // Find all bank configs for a user with a specific status
    List<BankConfig> findByUserAndStatus(User user, String status);

    // Find all bank config with specific status (for sync scheduler)
    List<BankConfig> findByStatus(String status);

    // Find all bank configs for a user
    List<BankConfig> findByUser(User user);

    // Find specific bank config owned by user (for ownership verification)
    Optional<BankConfig> findByIdAndUser(Long id, User user);

    // Find by requisition ID (for callback processing)
    Optional<BankConfig> findByRequisitionId(String requisitionId);

    // Find by user and requisition ID
    Optional<BankConfig> findByUserAndRequisitionId(User user, String requisitionId);
}
```

### TransactionRepository.java (Bank-related methods)

```java
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // Check if transaction already exists (deduplication)
    boolean existsByBankTransactionIdAndBankConfig(String bankTransactionId, BankConfig bankConfig);

    // Unlink all transactions from a bank config (set bankConfig to null)
    @Modifying
    @Query("UPDATE Transaction t SET t.bankConfig = null WHERE t.bankConfig = :bankConfig")
    void unlinkTransactionsFromBankConfig(@Param("bankConfig") BankConfig bankConfig);
}
```

---

## Configuration Reference

### application.yaml

```yaml
gocardless:
  secret-id: ${GOCARDLESS_SECRET_ID}
  secret-key: ${GOCARDLESS_SECRET_KEY}
  base-url: https://bankaccountdata.gocardless.com  # or sandbox URL
  redirect-url: https://yourapp.com/api/banks/callback

sync:
  enabled: true
  interval-minutes: 15      # How often scheduler runs
  look-back-days: 3         # Days to fetch on incremental sync
  active-user-days: 30      # Only sync for users active within this period
  initial-sync-days: 90     # Days to fetch on first sync
```

### GoCardlessConfig.java

```java
@Configuration
@ConfigurationProperties(prefix = "gocardless")
@Data
public class GoCardlessConfig {
    private String secretId;
    private String secretKey;
    private String baseUrl;
    private String redirectUrl;
}
```

### SyncConfig.java

```java
@Configuration
@ConfigurationProperties(prefix = "sync")
@Data
public class SyncConfig {
    private boolean enabled = true;
    private int intervalMinutes = 15;
    private int lookBackDays = 3;
    private int activeUserDays = 30;
    private int initialSyncDays = 90;
}
```

---

## BankConfig Status State Machine

```
                    +---------+
         startLinking()       |
         ------------------>  PENDING
                              |
                              +---------+
                                   |
                                   | processCallback()
                                   | status = "LN"
                                   v
                              +---------+
                              |  LINKED |<------------------+
                              |         |                   |
                              +---------+                   |
                                   |                        |
           +-----------------------+-----------------------+|
           |                       |                       ||
           | accessExpiresAt       | status = "RJ"         || re-link
           | passed                | or "EX"               ||
           v                       v                       ||
      +---------+             +---------+                  ||
      | EXPIRED |             |  ERROR  |------------------+|
      |         |             |         |                   |
      +---------+             +---------+-------------------+
```

### Status Descriptions

| Status | Description | Transitions From | Next Actions |
|--------|-------------|------------------|--------------|
| `PENDING` | Requisition created, waiting for user authorization | - | User authorizes -> LINKED, User rejects -> ERROR |
| `LINKED` | Bank account linked and active | PENDING | Access expires -> EXPIRED, API error -> ERROR |
| `EXPIRED` | Bank access consent has expired | LINKED | User must re-link -> PENDING |
| `ERROR` | Error during linking or authorization rejected | PENDING, LINKED | User must retry -> PENDING |

---

## API Endpoints Summary

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/banks/institutions?country=` | List available banks | Yes |
| POST | `/api/banks/link` | Start bank linking | Yes |
| GET | `/api/banks/callback?ref=` | Handle GoCardless callback | No* |
| GET | `/api/banks` | List user's linked banks | Yes |
| GET | `/api/banks/{id}` | Get specific bank account | Yes |
| POST | `/api/banks/{id}/sync` | Manual sync transactions | Yes |
| DELETE | `/api/banks/{id}` | Unlink bank account | Yes |

> *The callback endpoint should be permitted in SecurityConfig as GoCardless redirects here.

---

## Related Documentation

- [Phase 1: Database Migration](./PHASE1_DATABASE_MIGRATION.md) (if created)
- [Phase 2: GoCardless Service Layer](./PHASE2_GOCARDLESS_SERVICE.md) (if created)
- [Phase 3: Transaction Sync Service](./PHASE3_TRANSACTION_SYNC.md) (if created)
- [Phase 5: Scheduler](./PHASE5_SCHEDULER.md) (upcoming)
