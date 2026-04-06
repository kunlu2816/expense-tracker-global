# Expense Tracking - Fix Execution Plan

## Phase A: Security Fixes (8 tasks) — ALL COMPLETED ✅

### A1: Create UserProfileResponse DTO + @JsonProperty(WRITE_ONLY) on User.passwordHash ✅
- Added `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` on `passwordHash`
- Created `dto/UserProfileResponse.java`

### A2: Fix services to return DTOs instead of entities ✅
- DTO mapping in Service layer (not Controller)
- Updated UserService, TransactionService, CategoryService, BankLinkingService
- Created `dto/SyncLogResponse.java`

### A3: Externalize secrets to env vars ✅
- application.yaml uses `${DB_PASSWORD}`, `${JWT_SECRET}`, etc. with dev fallbacks
- docker-compose.yaml uses `${POSTGRES_PASSWORD:-...}`

### A4: Add UUID to bank callback reference ✅
- BankLinkingService `startLinking()` uses `UUID.randomUUID().toString()`
- Stored as `link_reference` in BankConfig (V4 migration)

### A5: Fix user enumeration ✅
- Login throws generic "Invalid email or password" for both bad email and bad password

### A6: Add validation to LoginRequest + @Valid on login endpoint ✅

### A7: Fix User.isEnabled() to use isActive field ✅

### A8: Remove dead webhook permit-all from SecurityConfig ✅

---

## Phase B: Code Quality Fixes (13 tasks) — ALL COMPLETED ✅

### B1: Create custom exception hierarchy ✅
- Created 4 exception classes: `ResourceNotFoundException`, `ForbiddenException`, `BadRequestException`, `ConflictException`
- Updated `GlobalExceptionHandler.java` with 4 new `@ExceptionHandler` methods (404, 403, 400, 409)
- Existing `RuntimeException` catch-all stays as 500 fallback

### B2: Replace all RuntimeException throws with custom exceptions ✅
- **Key constraint**: Custom exceptions keep the **exact same human-readable error messages** — only the exception type changes for correct HTTP status codes
- All 17 throw sites across 5 service files replaced:
  - TransactionService: 8 sites (4 not-found → ResourceNotFoundException, 4 ownership → ForbiddenException)
  - CategoryService: 3 sites (2 not-found → ResourceNotFoundException, 1 duplicate → ConflictException)
  - UserService: 1 site (bad password → BadRequestException)
  - AuthService: 3 sites (1 duplicate email → ConflictException, 2 invalid login → BadRequestException)
  - BankLinkingService: 2 sites (1 linking failure → BadRequestException, 1 not-found → ResourceNotFoundException)

### B3: Remove try-catch in AuthController ✅
- Removed try-catch blocks from `register()` and `login()`
- Changed return types to `ResponseEntity<String>` and `ResponseEntity<LoginResponse>`

### B4: Add @Transactional to service methods ✅
- TransactionService: `createTransaction()`, `updateTransaction()`, `deleteTransaction()`
- CategoryService: `createCategory()`, `updateCategory()`, `deleteCategory()`
- UserService: `updateProfile()`, `changePassword()`

### B5: Create V5 migration — UNIQUE constraint on categories ✅
- **Note**: V5 (not V4 — V4 was used for link_reference in Phase A)
- `V5__Add_category_unique_constraint.sql`: deletes duplicates first (keeps lowest id), then adds `UNIQUE (user_id, name)`

### B6: Fix N+1 queries with LEFT JOIN ✅
- `TransactionRepository.findFilteredTransactions`: main query uses `LEFT JOIN FETCH t.category`
- Count query uses `LEFT JOIN t.category c` (not implicit INNER JOIN, to include transactions without categories)
- Date filtering changed from `COALESCE` to `CAST(:param AS timestamp) IS NULL` pattern
- Added `fetch = FetchType.LAZY` to: `Transaction.user`, `Transaction.bankConfig`, `Transaction.category`, `Category.user`, `SyncLog.bankConfig`, `BankConfig.user`

### B7: Fix CSV export ✅
- Fixed null category NPE: `t.getCategory() != null ? ... : "Uncategorized"`
- Added `sanitizeCsvValue()` for CSV injection prevention (prefixes `=`, `+`, `-`, `@`, `\t`, `\r` with `'`)

### B8: Fix GoCardless thread safety ✅
- Changed `accessToken` and `tokenExpiresAt` to `AtomicReference`
- Added `synchronized` to `getAccessToken()`
- Added try-catch with logging to all 5 API methods
- Added null-safe check in `createAuthHeaders()`

### B9: Fix category deletion ✅
- Added `nullifyCategoryOnTransactions(@Param("category") Category category)` to TransactionRepository
- CategoryService.deleteCategory() calls it before delete
- Added TransactionRepository as dependency to CategoryService

### B10: Fix password validation ✅
- **Standardized to min = 8 everywhere**
- `ChangePasswordRequest`: fixed message from "at least 6 characters" to "at least 8 characters" (min was already 8)
- `RegisterRequest`: changed `@Size(min = 6)` to `@Size(min = 8, message = "Password must be at least 8 characters")`

### B11: Add pagination size limit + fix delete return type ✅
- Added `@Validated` and `@Max(100)` on `size` param to TransactionController and BankController
- Changed `TransactionController.deleteTransaction()` return type to `ResponseEntity<Void>`

### B12: Replace Math.random() with UUID ✅
- Changed `"MANUAL_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000)` → `"MANUAL_" + UUID.randomUUID()`

### B13: Standardize to @AuthenticationPrincipal ✅
- UserController (3 endpoints) and TransactionController (6 endpoints): replaced `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` with `@AuthenticationPrincipal User user` parameter
- Removed unused imports (`Authentication`, `SecurityContextHolder`)

### Build Verification ✅
- `BUILD SUCCESS` — all 13 Phase B tasks compile cleanly
- Only warnings: pre-existing Lombok `@Builder` warnings (not introduced by us)

---

## Phase C: Frontend Fixes (6 tasks) — ALL COMPLETED ✅

### C1: Fix date filter format ✅
- Changed `toISOString()` to `dayjs.format('YYYY-MM-DDTHH:mm:ss')` (no `Z` suffix, no milliseconds)
- `startDate` uses `startOf('day')`, `endDate` uses `endOf('day')` for proper end-of-day (`23:59:59`)
- Also fixed form submit `transactionDate` format

### C2: Fix dashboard pie chart ✅
- **Backend**: Created `CategorySummaryResponse.java` DTO with `categoryName`, `total`, `type`
- **Backend**: Added `getCategorySummary()` JPQL query in `TransactionRepository` — `GROUP BY c.name, t.type`
- **Backend**: Added `getCategorySummary()` method in `TransactionService`
- **Backend**: Added `GET /api/transactions/category-summary` endpoint in `TransactionController`
- **Frontend**: Dashboard.jsx now fetches from `/transactions/category-summary` instead of deriving from 5 recent transactions
- Removed unused `buildCategoryData()` function
- Cleaned up unused Ant Design / Recharts imports (`Statistic`, `Tag`, `Segmented`, `BarChart`, `Bar`, `XAxis`, `YAxis`, `CartesianGrid`, icon imports)

### C3: Remove non-functional search bar and notification bell ✅
- Removed `Input` search bar and `Badge`/`BellOutlined` bell from `AppLayout.jsx` header
- Removed unused imports: `Input`, `Badge`, `SearchOutlined`, `BellOutlined`

### C4: Delete leftover scaffold files ✅
- Deleted: `frontend/src/counter.ts`, `main.ts`, `style.css`, `typescript.svg`
- Deleted: `frontend/public/vite.svg`
- Deleted: `frontend/tsconfig.json`

### C5: Remove redundant localStorage 'user' key ✅
- Removed `localStorage.setItem('user', ...)` from `AuthContext.login()`
- Removed `localStorage.removeItem('user')` from `AuthContext.logout()`, `loadProfile()` catch, and `axios.js` response interceptor
- JWT token in localStorage is sufficient; user profile is always fetched from API

### C6: Fix frontend password validation to match backend (min 8) ✅
- `Profile.jsx`: Changed `min: 6` to `min: 8`, message updated to "at least 8 characters"
- `Register.jsx`: Changed `min: 6` to `min: 8`, message and placeholder updated

### Build Verification ✅
- Backend: `BUILD SUCCESS` — 65 source files compiled (new `CategorySummaryResponse.java`)
- Only warnings: pre-existing Lombok `@Builder` warnings

---

## Execution Order

```
Phase A: A1 → A2 → A3, A4, A5, A6, A7, A8  ✅ ALL DONE
Phase B: B1 → B2 → B3 → B4–B12 → B13       ✅ ALL DONE
Phase C: C1, C3, C4, C5, C6 → C2            ✅ ALL DONE
```

## ALL PHASES COMPLETE

All 27 fix tasks (8 + 13 + 6) across Phase A, B, and C have been completed and verified.

### Next Steps: Phase 5 — DevOps
- Dockerize the full application
- CI/CD pipeline
- Cloud deployment
- SSL/TLS setup
