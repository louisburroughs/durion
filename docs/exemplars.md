# POS Accounting — Code Exemplars

Purpose: identify high-quality, representative code examples in `pos-accounting` that demonstrate patterns and standards for controllers, services, repositories, entities, and tests.

## Table of Contents

- Presentation Layer (Controllers)
- Business Logic Layer (Services)
- Data Access Layer (Repositories)
- Domain Models (Entities)
- Tests (Integration/Contract)

---

## Presentation Layer (Controllers)

### 1. `JournalEntryController` (path: `internal/controller/JournalEntryController.java`)

- Why exemplary: Clear REST resource design, consistent authorization annotations, uses `PagedResponse` DTO and `JournalEntryMapper` for separation of concerns, emits events for observability.
- Pattern: Thin controller, delegate to service, map entities → DTOs.
- Snippet:

## Test Mock Guidance

Practical guidance for creating reliable, deterministic tests and mocks in `pos-accounting`:

- **Use `@MockBean` for service-layer mocks**: In controller-level slice tests prefer `@WebMvcTest` with `@MockBean` for services to keep tests focused and fast.
- **Contract / integration tests**: Use `@SpringBootTest` with `@ActiveProfiles("test")` and lightweight embedded DB (H2) or Testcontainers for realistic persistence behavior.
- **Deterministic UUIDs**: Seed fixed UUIDs in tests or provide a test-only `UUIDv7Generator` bean that returns predictable values to make assertions stable.
- **WireMock for external HTTP**: When services call external HTTP APIs, use WireMock or `MockRestServiceServer` to mock HTTP responses and assert request content and headers.
- **Mockito best practices**: Prefer `when(...).thenReturn(...)` for simple stubbing; use `ArgumentCaptor` for asserting payloads sent to collaborators; avoid overly broad stubbing that hides behavior.
- **Idempotency tests**: Use `IdempotencyService` test helpers to seed idempotency keys and assert idempotent replay behavior (existing vs. new payload scenarios).
- **Outbox / event assertions**: Verify `EventOutbox` rows are created as part of transactional tests and then run `OutboxProcessor` logic in isolation unit tests to assert delivery semantics and retry behavior.
- **Test security**: Use `@WithMockUser` or a `TestSecurityConfig` that injects a test authentication principal for secure endpoints; prefer header injection for gateway-style tests.
- **Fixture seeding**: Create small builder helpers (e.g., `JournalEntryTestBuilder`) in `src/test/java/.../internal/test` to create domain objects consistently across tests.
- **Keep tests small and focused**: Unit tests should test behavior of a single class; integration/contract tests validate end-to-end flows and invariants.


```java
@RestController
@RequestMapping("/v1/accounting/journal-entries")
public class JournalEntryController {
    @GetMapping
    @PreAuthorize("hasAuthority('accounting:je:view')")
    @EmitEvent(id = "ACCOUNTING_JOURNAL_ENTRY_LIST", apiVersion = "1")
    public ResponseEntity<PagedResponse<JournalEntryResponse>> listJournalEntries(...) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort));
        Page<JournalEntry> entryPage = journalEntryService.listJournalEntries(pageable);
        return ResponseEntity.ok(new PagedResponse<>(...));
    }
}
```

### 2. `FinancialReportingController` (path: `internal/controller/FinancialReportingController.java`)

- Why exemplary: Exposes focused reporting endpoints, consistent security (`reporting:view:financial-statements`), OpenAPI annotations, and `@EmitEvent` usage for audit/observability.
- Pattern: Domain-specific controller separated from CRUD controllers.
- Snippet:

```java
@RestController
@RequestMapping("/api/v1/reports/financial")
public class FinancialReportingController {
    @GetMapping("/income-statement")
    @PreAuthorize("hasAuthority('reporting:view:financial-statements')")
    @EmitEvent(id = "REPORT_INCOME_STATEMENT_GENERATE", apiVersion = "1")
    public ResponseEntity<IncomeStatementReport> generateIncomeStatement(...) {
        if (endDate.isBefore(startDate)) throw new IllegalArgumentException("End date cannot be before start date");
        return ResponseEntity.ok(financialReportingService.generateIncomeStatement(startDate, endDate));
    }
}
```

### 3. `GLAccountController` (path: `internal/controller/GLAccountController.java`)

- Why exemplary: Illustrates consistent endpoint design for Chart-of-Accounts management, uses `@EmitEvent` and role-based guards, and separates legacy stubs from implemented APIs.
- Pattern: Resource-oriented controller with lifecycle actions (activate/deactivate/archive) and clear authorization scopes.
- Snippet:
  
```java
@RestController
@RequestMapping("/v1/accounting/gl-accounts")
public class GLAccountController {
    @GetMapping
    @PreAuthorize("hasAuthority('accounting:coa:view')")
    @EmitEvent(id = "ACCOUNTING_GL_ACCOUNT_LIST", apiVersion = "1")
    public ResponseEntity<Void> listGLAccounts(...) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
```

---

## Business Logic Layer (Services)

### 1. `FinancialReportingService` (path: `service/FinancialReportingService.java`)

- Why exemplary: Well-documented service interface describing reproducible financial report contracts. Methods are parameter-focused and return DTOs suitable for API responses.
- Pattern: Service interface defines behaviors; implementation encapsulates aggregation logic and uses repositories for data access.
- Snippet (interface):

```java
@NonNull
IncomeStatementReport generateIncomeStatement(@NonNull LocalDate startDate, @NonNull LocalDate endDate);

@NonNull
BalanceSheetReport generateBalanceSheet(@NonNull LocalDate asOfDate);

@NonNull
List<AccountDrilldownResponse> drilldownToAccounts(@NonNull String statementLineCode, @NonNull LocalDate startDate, @NonNull LocalDate endDate);
```

### 2. `JournalEntryService` (domain service)

- Why exemplary: Encapsulates lifecycle operations (create, post, reverse) and is used by controllers for domain actions rather than exposing repositories directly.
-Snippet:

```java
public interface JournalEntryService {
    # POS Accounting — Code Exemplars (expanded)

    Purpose: a concise catalog of high-quality, real examples in `pos-accounting` to guide contributors. Each exemplar includes the file path, why it's exemplary, the pattern it demonstrates, and a small representative snippet.

    ## Table of Contents
    - Presentation Layer (Controllers)
    - Business Logic Layer (Services)
    - Data Access Layer (Repositories)
    - Domain Models (Entities)
    - Tests (Integration / Contract)
    - Configuration & Observability
    - Patterns & Recommendations

    ---

    ## Presentation Layer (Controllers)

    1. `APPaymentController` — `src/main/java/com/positivity/accounting/internal/controller/APPaymentController.java`
    - Why: idempotent write endpoint with clear validation and event emission (`@EmitEvent`). Good example for request validation and idempotency handling.
    - Pattern: Thin controller delegating to services, explicit response codes.
    - Snippet:

    ```java
    Optional<APPaymentResponse> existing = apPaymentService.getPaymentByRef(request.getPaymentRef());
    if (existing.isPresent()) {
        APPaymentResponse response = apPaymentService.executePayment(request, currentUser);
        return ResponseEntity.ok(response);
    }
    APPaymentResponse response = apPaymentService.executePayment(request, currentUser);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
    ```

    2. `JournalEntryController` — `src/main/java/com/positivity/accounting/internal/controller/JournalEntryController.java`
    - Why: CRUD + domain actions (post/reverse) with clear authorization guards and mapping between DTOs and entities.

    3. `FinancialReportingController` — `src/main/java/com/positivity/accounting/internal/controller/FinancialReportingController.java`
    - Why: read-optimized controllers, streaming-friendly endpoints, and robust parameter validation for reporting queries.

    ---

    ## Business Logic Layer (Services)

    1. `APPaymentServiceImpl` — `src/main/java/com/positivity/accounting/service/APPaymentServiceImpl.java`
    - Why: Orchestrates idempotent payment execution, allocation logic, and GL posting. Demonstrates error handling and audit integration.
    - Pattern: Validate → orchestrate → persist → emit event.

    2. `PostingRuleEvaluatorImpl` — `src/main/java/com/positivity/accounting/service/PostingRuleEvaluatorImpl.java`
    - Why: Encapsulates complex mapping and rule evaluation; testable pure functions separated from I/O.

    3. `OutboxProcessor` — `src/main/java/com/positivity/accounting/service/OutboxProcessor.java`
    - Why: Background processing with retry/backoff semantics; good template for reliable delivery components.

    ---

    ## Data Access Layer (Repositories)

    1. `JournalEntryRepository` — `src/main/java/com/positivity/accounting/internal/repository/JournalEntryRepository.java`
    - Why: Uses Spring Data with custom JPQL for aggregated queries, projections for read-optimized flows.
    - Snippet (aggregation query):

    ```java
    @Query("""
    SELECT COALESCE(
      SUM(CASE WHEN jel.debitAmount IS NOT NULL THEN jel.debitAmount ELSE 0 END) -
      SUM(CASE WHEN jel.creditAmount IS NOT NULL THEN jel.creditAmount ELSE 0 END),
      0
    )
    FROM JournalEntry je
    JOIN je.lines jel
    WHERE je.status = 'POSTED'
      AND jel.glAccountId = :glAccountId
      AND je.transactionDate >= :startDate
      AND je.transactionDate <= :endDate
    """)
    BigDecimal sumPostedBalanceForAccount(UUID glAccountId, LocalDateTime startDate, LocalDateTime endDate);
    ```

    2. `IdempotencyKeyRepository` — `src/main/java/com/positivity/accounting/internal/repository/IdempotencyKeyRepository.java`
    - Why: Implements idempotency marker storage used across write endpoints.

    3. `EventOutboxRepository` — `src/main/java/com/positivity/accounting/internal/repository/EventOutboxRepository.java`
    - Why: Supports the transactional outbox pattern; has queries to fetch pending events and mark retries.

    ---

    ## Domain Models (Entities)

    1. `JournalEntry` — `src/main/java/com/positivity/accounting/internal/entity/JournalEntry.java`
    - Why: Aggregate root with child collection (`JournalEntryLine`), audit fields, `@PrePersist` UUIDv7 generation, and domain rules (`calculateTotals`). Good separation of concerns between persistence and domain invariants.
    - Snippet:

    ```java
    @Id
    @Column(name = "journal_entry_id", nullable = false, columnDefinition = "UUID")
    private UUID journalEntryId;

    @PrePersist
    public void onPrePersist() {
        if (journalEntryId == null) journalEntryId = UUIDv7Generator.generate();
        Instant now = Instant.now();
        this.createdAt = now; this.modifiedAt = now;
        initializeLines();
    }
    ```

    2. `VendorBill` — `src/main/java/com/positivity/accounting/internal/entity/VendorBill.java`
    - Why: Demonstrates careful monetary types, status enum usage, and posting semantics.

    3. `EventOutbox` — `src/main/java/com/positivity/accounting/internal/entity/EventOutbox.java`
    - Why: Minimal shape for persisted event payloads, retry metadata, and status field used by processors.

    ---

    ## Tests (Integration / Contract)

    1. `BaseIntegrationTest` — `src/test/java/com/positivity/accounting/BaseIntegrationTest.java`
    - Why: Standardized test scaffold used by contract tests (profiles, Testcontainers or embedded DB hooks, shared fixtures).

    2. `JournalEntryContractBehaviorIT` — `src/test/java/com/positivity/accounting/JournalEntryContractBehaviorIT.java`
    - Why: End-to-end contract tests validating critical domain invariants (JE balancing), mappings, and persistence concerns.

    3. `AuditTrailContractBehaviorIT` / `EventIngestionContractBehaviorIT` — contract tests demonstrating event capture and replay semantics.

    ---

    ## Configuration & Observability

    - `application-observability.yml` (repo root) and module-specific Actuator/OpenTelemetry integration show how services expose health and metrics. Use `@EmitEvent` annotations in controllers to standardize event logging.
    - Example: `pos-events` usage appears across controllers to centralize event type registration and performance thresholds.

    ---

    ## Patterns & Recommendations

    - Thin controllers: keep request validation, mapping, and orchestration as separate responsibilities — controllers call services; services call repositories.
    - Idempotency: reuse `IdempotencyService` + `IdempotencyKeyRepository` for external write endpoints.
    - Outbox pattern: persist events in `EventOutbox` and process with `OutboxProcessor` to ensure reliable downstream delivery.
    - UUIDv7: entities use `UUIDv7Generator.generate()` in `@PrePersist` hooks; avoid `UUID.randomUUID()` to maintain ordering and ADR compliance.
    - Testing: prefer contract-style integration tests with seeded deterministic fixtures and `@ActiveProfiles("test")` for reproducible results.

    ---

    If you want more exemplars (additional controllers, service helpers, or detailed test snippets), tell me which category to expand and I will add up to three more focused examples with short code excerpts.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ContractBehaviorIT {
    @Autowired private MockMvc mockMvc;
    @Test void example() throws Exception {
        mockMvc.perform(get("/v1/accounting/journal-entries").header("X-User","testuser"))
               .andExpect(status().isOk());
    }
}
```

---

## Recommendations

- Follow the thin-controller / service-layer pattern seen in `JournalEntryController` and `FinancialReportingService`.
- Keep complex aggregation SQL/JPQL in repositories (as in `JournalEntryRepository`) and perform business rules in services.
- Ensure deterministic tests by seeding required reference data (see `FinancialReportingContractBehaviorIT`).
- Use `@EmitEvent` consistently for important operations to improve observability and audit trails.

---

## Conclusion

This document highlights representative exemplars across layers in `pos-accounting`. Use these examples as templates when adding new features: prefer thin controllers, well-documented service interfaces, repository-level queries for aggregation, and contract-style integration tests for API guarantees.
