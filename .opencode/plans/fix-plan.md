# Expense Tracking - Fix Execution Plan

## Phase A: Security Fixes (8 tasks)

### A1: Create UserProfileResponse DTO + @JsonIgnore on User.passwordHash

**File: `User.java`**
- Add `import com.fasterxml.jackson.annotation.JsonIgnore;`
- Add `@JsonIgnore` annotation on `passwordHash` field (defense-in-depth)

**New file: `dto/UserProfileResponse.java`**
```java
package com.example.expense_tracking.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserProfileResponse {
    private Long id;
    private String email;
    private String fullName;
    private Boolean isActive;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
}
```

### A2: Fix all controllers to return DTOs instead of entities

**UserController.java** - Change return types from `User` to `UserProfileResponse`:
- `getProfile()` -> return `UserProfileResponse`
- `updateProfile()` -> return `UserProfileResponse`
- Add a private `mapToProfileResponse(User user)` helper or put in UserService

**TransactionController.java**:
- `createTransaction()` currently returns `ResponseEntity<Transaction>` -> change to `ResponseEntity<TransactionResponse>`
- Update `TransactionService.createTransaction()` to return `TransactionResponse` instead of `Transaction`

**CategoryController.java** - Change return types from `Category` to `CategoryDTO`:
- `getUserCategories()` -> `List<CategoryDTO>`
- `createCategory()` -> `CategoryDTO`
- `updateCategory()` -> `CategoryDTO`
- Add mapping in CategoryService or controller

**BankController.java**:
- `getSyncHistory()` returns `Page<SyncLog>` -> create `SyncLogResponse` DTO
- SyncLog entity contains `BankConfig` which contains `User` - leaks user data

**New file: `dto/SyncLogResponse.java`**
```java
package com.example.expense_tracking.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SyncLogResponse {
    private Long id;
    private LocalDateTime syncedAt;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer transactionsFetched;
    private Integer transactionsNew;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}
```

### A3: Externalize secrets to env vars

**application.yaml**:
- `spring.datasource.password` -> `${DB_PASSWORD:MinhHieu2816@}` (env var with fallback for dev)
- `spring.datasource.url` -> `${DB_URL:jdbc:postgresql://localhost:5433/postgres}`
- `spring.datasource.username` -> `${DB_USERNAME:postgres}`
- JWT secret -> `${JWT_SECRET:MySuperStrongSuperLongSecretKeyForSecurity2806}`
- GoCardless secrets -> `${GOCARDLESS_SECRET_ID}`, `${GOCARDLESS_SECRET_KEY}`

**docker-compose.yaml**:
- Use `${POSTGRES_PASSWORD:-MinhHieu2816@}` syntax

**pom.xml**:
- Remove hardcoded password from flyway plugin config, use `${env.DB_PASSWORD}` or remove flyway-maven-plugin if unused

### A4: Add UUID to bank callback reference

**BankLinkingService.java** `startLinking()`:
- Change reference format from `"user_" + user.getId() + "_" + System.currentTimeMillis()`
- To `UUID.randomUUID().toString()`
- Store UUID in BankConfig so callback can look it up

### A5: Fix user enumeration (generic login error message)

**AuthService.java**:
- `register()` currently throws "Email already registered" -> keep as-is (registration needs specific feedback)
- `login()` currently throws "User not found" and "Incorrect password" separately -> change both to generic "Invalid email or password"

### A6: Add validation to LoginRequest + @Valid on login endpoint

**LoginRequest.java** - Add validation:
```java
@NotBlank(message = "Email is required")
@Email(message = "Invalid email format")
private String email;

@NotBlank(message = "Password is required")
private String password;
```

**AuthController.java** line 33:
- Change `@RequestBody LoginRequest` to `@Valid @RequestBody LoginRequest`

### A7: Fix User.isEnabled() to use isActive field

**User.java** line 78-80:
```java
@Override
public boolean isEnabled() {
    return isActive != null && isActive;
}
```

### A8: Remove dead webhook permit-all from SecurityConfig

**SecurityConfig.java**:
- Remove `.requestMatchers("/api/webhook/**").permitAll()` line (no webhook controller exists)

---

## Phase B: Code Quality Fixes (13 tasks)

### B1: Create custom exception hierarchy

**New files in `exception/` package:**

```java
// ResourceNotFoundException.java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
}

// ForbiddenException.java (for ownership violations)
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}

// BadRequestException.java
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}

// ConflictException.java (for duplicates)
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
```

**Update GlobalExceptionHandler.java** - Add specific handlers:
```java
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", e.getMessage()));
}

@ExceptionHandler(ForbiddenException.class)
public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Map.of("error", e.getMessage()));
}

@ExceptionHandler(BadRequestException.class)
public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", e.getMessage()));
}

@ExceptionHandler(ConflictException.class)
public ResponseEntity<Map<String, String>> handleConflict(ConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", e.getMessage()));
}
```

### B2: Replace all RuntimeException throws with custom exceptions

Services to update:
- **TransactionService**: "Transaction not found" -> ResourceNotFoundException, "You do not own" -> ForbiddenException, "Bank Account not found" -> ResourceNotFoundException
- **CategoryService**: "Category not found" -> ResourceNotFoundException, "You do not own" -> ForbiddenException, duplicate name -> ConflictException
- **UserService**: password mismatch -> BadRequestException
- **AuthService**: "Email already registered" -> ConflictException, "Invalid email or password" -> BadRequestException
- **BankLinkingService**: not found -> ResourceNotFoundException, ownership -> ForbiddenException

### B3: Remove try-catch in AuthController

**AuthController.java**:
- Remove try-catch blocks from `register()` and `login()`
- Let exceptions propagate to GlobalExceptionHandler
- Simplify to direct service calls

```java
@PostMapping("/register")
public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest registerRequest) {
    User registeredUser = authService.register(registerRequest);
    return ResponseEntity.ok("Account registered successfully: " + registeredUser.getEmail());
}

@PostMapping("/login")
public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
    LoginResponse response = authService.login(loginRequest);
    return ResponseEntity.ok(response);
}
```

### B4: Add @Transactional to service methods

- **TransactionService**: `createTransaction()`, `updateTransaction()`, `deleteTransaction()`
- **CategoryService**: `createCategory()`, `updateCategory()`, `deleteCategory()`
- **UserService**: `updateProfile()`, `changePassword()`
- **TransactionSyncService**: `syncTransactions()` (the main sync method)
- **BankLinkingService**: `processCallback()`, `unlinkBank()`

### B5: Create V4 migration with UNIQUE constraint

**New file: `db/migration/V4__Add_category_unique_constraint.sql`**
```sql
-- Add unique constraint on (user_id, name) for categories to prevent race condition duplicates
-- First, remove duplicates if any exist (keep the one with the lowest id)
DELETE FROM categories c1
USING categories c2
WHERE c1.user_id = c2.user_id
  AND c1.name = c2.name
  AND c1.id > c2.id;

-- Add unique constraint
ALTER TABLE categories ADD CONSTRAINT uq_category_user_name UNIQUE (user_id, name);
```

### B6: Fix N+1 queries

**TransactionRepository.java** - Update `findFilteredTransactions`:
```java
@Query("SELECT t FROM Transaction t " +
       "LEFT JOIN FETCH t.category " +
       "WHERE t.user = :user " +
       "AND (:category IS NULL OR t.category.name = :category) " +
       "AND (CAST(:startDate AS timestamp) IS NULL OR t.transactionDate >= :startDate) " +
       "AND (CAST(:endDate AS timestamp) IS NULL OR t.transactionDate <= :endDate)")
Page<Transaction> findFilteredTransactions(...);
```

Note: `JOIN FETCH` with `Page` requires a separate count query:
```java
@Query(value = "SELECT t FROM Transaction t LEFT JOIN FETCH t.category WHERE t.user = :user AND (:category IS NULL OR t.category.name = :category) AND (CAST(:startDate AS timestamp) IS NULL OR t.transactionDate >= :startDate) AND (CAST(:endDate AS timestamp) IS NULL OR t.transactionDate <= :endDate)",
       countQuery = "SELECT COUNT(t) FROM Transaction t WHERE t.user = :user AND (:category IS NULL OR t.category.name = :category) AND (CAST(:startDate AS timestamp) IS NULL OR t.transactionDate >= :startDate) AND (CAST(:endDate AS timestamp) IS NULL OR t.transactionDate <= :endDate)")
```

Also set `@ManyToOne(fetch = FetchType.LAZY)` on:
- `Transaction.user`
- `Transaction.bankConfig`
- `Transaction.category`
- `Category.user`
- `SyncLog.bankConfig`

### B7: Fix CSV export

**TransactionService.exportToCsv()**:
1. Fix null category NPE: `t.getCategory() != null ? t.getCategory().getName() : "Uncategorized"`
2. CSV injection prevention - sanitize values that start with `=`, `+`, `-`, `@`, `\t`, `\r`:
```java
private String sanitizeCsvValue(String value) {
    if (value == null) return "";
    if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@") || value.startsWith("\t") || value.startsWith("\r")) {
        return "'" + value;
    }
    return value;
}
```

### B8: Fix GoCardless thread safety + error handling

**GoCardlessService.java**:
- Make token storage thread-safe using `AtomicReference<String>` or `volatile` + synchronization
- Add proper error handling for API calls (try-catch with logging)
- Add token expiry tracking

### B9: Fix category deletion

**CategoryService.deleteCategory()**:
- Before deleting a category, nullify the FK on all transactions:
```java
transactionRepository.findAllByCategory(category).forEach(t -> {
    t.setCategory(null);
    transactionRepository.save(t);
});
categoryRepository.delete(category);
```
- Or better: add a bulk update query: `@Query("UPDATE Transaction t SET t.category = null WHERE t.category = :category")`

### B10: Fix ChangePasswordRequest validation

**ChangePasswordRequest.java**:
- Change `@Size(min = 8)` to `@Size(min = 6, message = "New password must be at least 6 characters")`
- Consistent with RegisterRequest

### B11: Add pagination size limit + fix delete return type

**TransactionController.java** and **BankController.java**:
- Add `@Max(100)` to `size` parameter: `@RequestParam(defaultValue = "10") @Max(100) int size`
- Import `jakarta.validation.constraints.Max`
- Add `@Validated` to controller class

**TransactionController.deleteTransaction()**:
- Return type is `ResponseEntity<TransactionResponse>` but returns `noContent()` -> change to `ResponseEntity<Void>`

### B12: Replace Math.random() with UUID

**TransactionService.createTransaction()** line 68:
- Change: `"MANUAL_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000)`
- To: `"MANUAL_" + UUID.randomUUID().toString()`

### B13: Fix stale comments, standardize @AuthenticationPrincipal

**All controllers**:
- Replace `SecurityContextHolder.getContext().getAuthentication()` pattern with `@AuthenticationPrincipal User user` parameter
- Affected: `UserController` (3 endpoints), `TransactionController` (5 endpoints)
- `CategoryController` and `BankController` already use `@AuthenticationPrincipal`

---

## Phase C: Frontend Fixes (6 tasks)

### C1: Fix date filter format

**frontend/src/pages/Transactions.jsx**:
- When sending date params, strip `Z` suffix or use `.toISOString().slice(0, 19)` format
- For `endDate`, set time to `23:59:59` for end-of-day

### C2: Fix dashboard pie chart

**Backend**: Add new endpoint `GET /api/transactions/category-summary` that returns aggregated spending by category:
```java
@GetMapping("/category-summary")
public ResponseEntity<List<CategorySummaryResponse>> getCategorySummary() { ... }
```

**New DTO: `CategorySummaryResponse.java`**:
```java
@Data @Builder
public class CategorySummaryResponse {
    private String categoryName;
    private BigDecimal totalAmount;
    private TransactionType type;
}
```

**New repository query**:
```java
@Query("SELECT new com.example.expense_tracking.dto.CategorySummaryResponse(c.name, SUM(t.amount), t.type) FROM Transaction t JOIN t.category c WHERE t.user = :user GROUP BY c.name, t.type")
List<CategorySummaryResponse> getCategorySummary(@Param("user") User user);
```

**Frontend Dashboard.jsx**: Replace the pie chart data source to use the new endpoint.

### C3: Remove non-functional search bar and notification bell

**frontend/src/components/AppLayout.jsx**:
- Remove the search `<Input.Search>` and notification `<Badge>`/bell icon from the header
- Keep the layout clean

### C4: Delete leftover scaffold files

Delete these files:
- `frontend/src/counter.ts`
- `frontend/src/style.css`
- `frontend/src/main.ts`
- `frontend/src/typescript.svg`
- `frontend/public/vite.svg`
- `frontend/tsconfig.json`

### C5: Remove redundant localStorage 'user' key

**frontend/src/contexts/AuthContext.jsx**:
- Remove `localStorage.setItem('user', ...)` / `localStorage.getItem('user')` if token-based auth makes it redundant
- The JWT token in localStorage is sufficient; user profile should be fetched from API

### C6: Fix frontend password validation to match backend

**frontend/src/pages/Profile.jsx** (depends on B10):
- Align password min length validation with backend (6 characters after B10 fix)
- Update any frontend validation messages

---

## Execution Order

```
A1 -> A2 (DTOs first, then controllers use them)
A3, A4, A5, A6, A7, A8 (independent, can be done in any order)
B1 -> B2 -> B3 (exception classes -> usage -> controller cleanup)
B4 through B12 (mostly independent)
B13 (cleanup pass, do last in Phase B)
C1, C3, C4, C5 (independent frontend fixes)
C2 (needs new backend endpoint)
C6 (depends on B10)
```

## Post-Fix Verification

After all fixes:
1. `./mvnw clean package` - verify build succeeds
2. `docker-compose up -d` - ensure DB starts
3. `./mvnw spring-boot:run` - verify app starts
4. Test key endpoints manually or with curl
