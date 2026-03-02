# Expense Tracking API - Technical Documentation

This folder contains technical documentation for the Expense Tracking API migration from Casso webhook (Vietnam bank integration) to GoCardless Bank Account Data API (EU/UK Open Banking).

---

## Migration Phases

| Phase | Document | Status | Description |
|-------|----------|--------|-------------|
| 1 | Database & Cleanup | Completed | Migrations, removed Casso code, updated entities |
| 2 | GoCardless Service Layer | Completed | Config, DTOs, API client |
| 3 | Transaction Sync Service | Completed | Core sync logic |
| 4 | [Bank Linking](./PHASE4_BANK_LINKING.md) | **Documented** | REST endpoints, bank linking flow |
| 5 | Scheduler | Pending | Background transaction sync |
| 6 | Security Configuration | Pending | Permit callback endpoint |

---

## Quick Links

### Phase 4: Bank Linking & Management
- [Full Documentation](./PHASE4_BANK_LINKING.md)
- Architecture overview
- 7 complete workflow diagrams
- All DTOs and entities
- Database schema
- Configuration reference

---

## Project Structure

```
expense-tracking-global/
|-- src/main/java/com/example/expense_tracking/
|   |-- config/
|   |   |-- GoCardlessConfig.java      # GoCardless API settings
|   |   |-- SyncConfig.java            # Sync scheduler settings
|   |   +-- SecurityConfig.java        # Security rules
|   |
|   |-- controller/
|   |   +-- BankController.java        # Bank linking endpoints
|   |
|   |-- dto/
|   |   |-- bank/                      # Bank-related DTOs
|   |   +-- gocardless/                # GoCardless API DTOs
|   |
|   |-- entity/
|   |   |-- BankConfig.java            # Bank account entity
|   |   |-- Transaction.java           # Transaction entity
|   |   |-- SyncLog.java               # Sync history entity
|   |   +-- User.java                  # User entity
|   |
|   |-- repository/
|   |   |-- BankConfigRepository.java
|   |   |-- TransactionRepository.java
|   |   +-- SyncLogRepository.java
|   |
|   +-- service/
|       |-- GoCardlessService.java     # GoCardless API client
|       |-- TransactionSyncService.java # Sync logic
|       +-- BankLinkingService.java    # Bank linking orchestration
|
|-- src/main/resources/
|   +-- db/migration/
|       |-- V2__GoCardless_Migration.sql
|       +-- V3__Adjust_bank_configs_And_users.sql
|
+-- docs/
    |-- README.md                      # This file
    +-- PHASE4_BANK_LINKING.md         # Phase 4 documentation
```

---

## API Endpoints Overview

### Bank Operations (`/api/banks`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/institutions?country=` | List available banks for a country |
| POST | `/link` | Start bank linking process |
| GET | `/callback?ref=` | Handle GoCardless redirect callback |
| GET | `/` | List user's linked bank accounts |
| GET | `/{id}` | Get specific bank account details |
| POST | `/{id}/sync` | Manually trigger transaction sync |
| DELETE | `/{id}` | Unlink bank account (preserves transactions) |

---

## Configuration

### Environment Variables

```bash
# GoCardless API credentials
GOCARDLESS_SECRET_ID=your_secret_id
GOCARDLESS_SECRET_KEY=your_secret_key
```

### application.yaml

```yaml
gocardless:
  secret-id: ${GOCARDLESS_SECRET_ID}
  secret-key: ${GOCARDLESS_SECRET_KEY}
  base-url: https://bankaccountdata.gocardless.com
  redirect-url: https://yourapp.com/api/banks/callback

sync:
  enabled: true
  interval-minutes: 15
  look-back-days: 3
  active-user-days: 30
  initial-sync-days: 90
```

---

## Key Concepts

### Bank Linking Flow

1. **User requests to link bank** -> `POST /api/banks/link`
2. **User redirected to bank's website** for authorization
3. **Bank redirects back** -> `GET /api/banks/callback`
4. **Initial sync** fetches 90 days of transaction history
5. **Background scheduler** syncs new transactions every 15 minutes

### BankConfig Status Lifecycle

```
PENDING -> LINKED -> EXPIRED
              |
              +-> ERROR
```

| Status | Description |
|--------|-------------|
| PENDING | Waiting for user to authorize on bank website |
| LINKED | Active and syncing transactions |
| EXPIRED | Access consent expired, needs re-linking |
| ERROR | Authorization rejected or API error |

### Transaction Deduplication

Transactions are deduplicated using:
- `bankTransactionId` (GoCardless transaction ID)
- `bankConfig` (which bank account)

This prevents duplicate imports when syncing overlapping date ranges.

---

## Tech Stack

- **Framework**: Spring Boot 3.5.9
- **Language**: Java 21
- **Database**: PostgreSQL
- **Migrations**: Flyway
- **Bank API**: GoCardless Bank Account Data API
- **Authentication**: JWT

---

## External Resources

- [GoCardless Bank Account Data API Docs](https://developer.gocardless.com/bank-account-data/overview)
- [GoCardless Sandbox](https://bankaccountdata.gocardless.com/)
