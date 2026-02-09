# CAP-052 Backend Implementation Plan

**Capability:** Accounts Receivable (Invoice → Cash Application)  
**Story:** AR: Issue Credit Memo / Refund with Traceability  
**Parent Issue:** [durion#52](https://github.com/louisburroughs/durion/issues/52)  
**Backend Child:** [durion-positivity-backend#131](https://github.com/louisburroughs/durion-positivity-backend/issues/131)  
**Branch:** `cap/CAP052`  
**Date:** 2026-02-08

---

## Executive Summary

This document outlines the backend implementation for Credit Memo functionality in the Accounting domain. The implementation is delivered in phases:

**Phase 1 (This PR):**
- Contract guide updates with complete endpoint documentation
- Foundation entities, DTOs, and repository interfaces
- Enables frontend development planning and contract review

**Phase 2 (Follow-up PR):**
- Full service layer with business logic and validation
- REST controller with OpenAPI annotations
- Event emission and registration
- Comprehensive test suite
- GL posting integration

---

## Story Requirements (from Issue #131)

### Business Rules
1. **Traceability:** Credit Memo immutably linked to finalized Invoice
2. **Financial Integrity:** Credit amount cannot exceed invoice outstanding balance
3. **Justification:** Mandatory reason code for audit trail
4. **Double-Entry Accounting:** Balanced GL entries (debit revenue + debit tax = credit AR)
5. **Period-Close Handling:** Prior period adjustments posted to current period with flag

### Scope (v1.0)
✅ Create Credit Memo entity  
✅ Post reversing GL entries (debit revenue, debit tax, credit AR)  
✅ Update original invoice's outstanding balance  
✅ Maintain traceability link (CM → Invoice)  
✅ Audit trail and period-close handling  
❌ **NOT included:** Cash refund execution (separate Payment story v2.0)

---

## Completed Deliverables

### 1. Contract Guide Updates

**File:** `durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md`

**Changes:**
- Updated endpoint count from 56 to 59
- Added 3 new Credit Memo endpoints:
  - `POST /v1/accounting/credit-memos` - Create Credit Memo
  - `GET /v1/accounting/credit-memos` - List Credit Memos
  - `GET /v1/accounting/credit-memos/{creditMemoId}` - Get Credit Memo
- Documented request/response schemas (`CreateCreditMemoRequest`, `CreditMemoResponse`)
- Added comprehensive examples:
  - Full credit memo creation (current period)
  - Partial credit memo creation with prior period adjustment
  - Error responses (amount exceeds balance, missing reason code)
- Added Credit Memo status enum values (DRAFT, POSTED, APPLIED, VOIDED)
- Added reason code enum values (RETURNED_GOODS, PRICING_ERROR, SERVICE_CREDIT, etc.)
- Added Implementation Links section linking to CAP-052 issues
- Updated change log to version 1.1

### 2. Entity Layer

**File:** `pos-accounting/src/main/java/com/positivity/accounting/internal/entity/CreditMemo.java`

```java
@Entity
@Table(name = "credit_memo", indexes = {
    @Index(name = "idx_credit_memo_original_invoice", columnList = "original_invoice_id"),
    @Index(name = "idx_credit_memo_customer", columnList = "customer_id"),
    @Index(name = "idx_credit_memo_status", columnList = "status"),
    @Index(name = "idx_credit_memo_posted_timestamp", columnList = "posted_timestamp")
})
public class CreditMemo {
    @Id private UUID creditMemoId;
    private UUID originalInvoiceId;
    private UUID customerId;
    private BigDecimal creditAmount;
    private BigDecimal taxAmountReversed;
    private BigDecimal totalAmount;
    private String reasonCode;
    private String justificationNote;
    @Enumerated(EnumType.STRING) private CreditMemoStatus status;
    private Instant creationTimestamp;
    private Instant postedTimestamp;
    private String createdByUserId;
    private Boolean priorPeriodAdjustment;
    private String originalPeriodId;
    private String currency;
    
    // ... getters/setters
}
```

**Key Features:**
- JPA entity with proper indexing for query performance
- Supports prior period adjustment tracking
- Immutable original invoice reference
- Audit fields (createdBy, timestamps)
- `@PrePersist` hook for auto-generation of ID and timestamps

**File:** `pos-accounting/src/main/java/com/positivity/accounting/internal/entity/CreditMemoStatus.java`

```java
public enum CreditMemoStatus {
    DRAFT,      // Created but not posted to GL
    POSTED,     // GL entries recorded; AR reduced
    APPLIED,    // Applied to customer account or future invoice
    VOIDED      // Reversed
}
```

### 3. Repository Layer

**File:** `pos-accounting/src/main/java/com/positivity/accounting/internal/repository/CreditMemoRepository.java`

```java
@Repository
public interface CreditMemoRepository extends JpaRepository<CreditMemo, UUID> {
    List<CreditMemo> findByOriginalInvoiceId(UUID originalInvoiceId);
    Page<CreditMemo> findByCustomerId(UUID customerId, Pageable pageable);
    Page<CreditMemo> findByStatus(CreditMemoStatus status, Pageable pageable);
    boolean existsByOriginalInvoiceId(UUID originalInvoiceId);
}
```

**Key Features:**
- Standard Spring Data JPA repository
- Query methods for customer, invoice, and status filters
- Pagination support for list endpoints
- Existence check for business rule validation

### 4. DTO Layer

**File:** `pos-accounting/src/main/java/com/positivity/accounting/internal/dto/CreateCreditMemoRequest.java`

```java
public class CreateCreditMemoRequest {
    @NotNull(message = "Original invoice ID is required")
    private UUID originalInvoiceId;
    
    @NotNull(message = "Credit amount is required")
    @DecimalMin(value = "0.01", message = "Credit amount must be positive")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal creditAmount;
    
    @NotBlank(message = "Reason code is required")
    @Size(min = 1, max = 50)
    private String reasonCode;
    
    @Size(max = 1000)
    private String justificationNote;
    
    // ... getters/setters
}
```

**Key Features:**
- Bean Validation annotations for request validation
- Clear, descriptive validation messages
- Follows camelCase naming convention from contract guide

**File:** `pos-accounting/src/main/java/com/positivity/accounting/internal/dto/CreditMemoResponse.java`

```java
public class CreditMemoResponse {
    private UUID creditMemoId;
    private UUID originalInvoiceId;
    private UUID customerId;
    private BigDecimal creditAmount;
    private BigDecimal taxAmountReversed;
    private BigDecimal totalAmount;
    private String reasonCode;
    private String justificationNote;
    private String status;
    private Instant creationTimestamp;
    private Instant postedTimestamp;
    private String createdByUserId;
    private Boolean priorPeriodAdjustment;
    private String originalPeriodId;
    private String currency;
    private BigDecimal invoiceBalanceAfter;
    
    // ... getters/setters
}
```

**Key Features:**
- Complete representation of Credit Memo state
- Includes computed fields (`invoiceBalanceAfter`)
- Jackson annotations for JSON serialization

---

## Deferred Implementation (Phase 2)

### 1. Service Layer

**File (to create):** `pos-accounting/src/main/java/com/positivity/accounting/service/CreditMemoService.java`

**Responsibilities:**
1. **Create Credit Memo:**
   - Validate original invoice exists and is finalized
   - Validate credit amount does not exceed outstanding balance
   - Validate reason code is provided
   - Calculate proportional tax reversal
   - Determine if prior period adjustment is needed
   - Create Credit Memo entity
   - Post GL entries (debit revenue, debit tax, credit AR)
   - Update invoice outstanding balance
   - Emit domain event for audit trail

2. **List Credit Memos:**
   - Support pagination
   - Filter by customer, invoice, status
   - Return paginated results

3. **Get Credit Memo:**
   - Retrieve by ID
   - Return 404 if not found

**Pseudocode:**

```java
@Service
@Transactional
public class CreditMemoService {
    
    private final CreditMemoRepository creditMemoRepository;
    private final InvoiceServiceClient invoiceServiceClient;
    private final GLPostingService glPostingService;
    private final AccountingPeriodService periodService;
    
    public CreditMemoResponse createCreditMemo(
            @NonNull CreateCreditMemoRequest request,
            @NonNull String currentUser) {
        
        // 1. Fetch and validate invoice
        InvoiceDetails invoice = invoiceServiceClient.getInvoiceDetails(
            request.getOriginalInvoiceId()
        );
        
        if (invoice.getStatus() != InvoiceStatus.FINALIZED) {
            throw new BusinessRuleViolationException(
                "Credit memos can only be issued against finalized invoices"
            );
        }
        
        if (request.getCreditAmount().compareTo(invoice.getOutstandingBalance()) > 0) {
            throw new BusinessRuleViolationException(
                "Credit amount cannot exceed invoice outstanding balance"
            );
        }
        
        // 2. Calculate proportional tax reversal
        BigDecimal creditRatio = request.getCreditAmount()
            .divide(invoice.getSubtotal(), 4, HALF_UP);
        BigDecimal taxReversed = invoice.getTaxAmount()
            .multiply(creditRatio)
            .setScale(2, HALF_UP);
        
        // 3. Determine if prior period adjustment
        AccountingPeriod invoicePeriod = periodService.findByDate(invoice.getInvoiceDate());
        AccountingPeriod currentPeriod = periodService.findCurrentOpenPeriod();
        boolean isPriorPeriod = !invoicePeriod.getId().equals(currentPeriod.getId());
        
        // 4. Create Credit Memo entity
        CreditMemo creditMemo = new CreditMemo();
        creditMemo.setOriginalInvoiceId(request.getOriginalInvoiceId());
        creditMemo.setCustomerId(invoice.getCustomerId());
        creditMemo.setCreditAmount(request.getCreditAmount());
        creditMemo.setTaxAmountReversed(taxReversed);
        creditMemo.setTotalAmount(request.getCreditAmount().add(taxReversed));
        creditMemo.setReasonCode(request.getReasonCode());
        creditMemo.setJustificationNote(request.getJustificationNote());
        creditMemo.setStatus(CreditMemoStatus.POSTED);
        creditMemo.setCreatedByUserId(currentUser);
        creditMemo.setPriorPeriodAdjustment(isPriorPeriod);
        creditMemo.setOriginalPeriodId(isPriorPeriod ? invoicePeriod.getId() : null);
        creditMemo.setCurrency(invoice.getCurrency());
        
        creditMemo = creditMemoRepository.save(creditMemo);
        
        // 5. Post GL entries
        List<GLEntry> glEntries = List.of(
            GLEntry.debit(config.getRevenueAccountId(), request.getCreditAmount(), 
                "Revenue Reversal - CM#" + creditMemo.getCreditMemoId()),
            GLEntry.debit(config.getSalesTaxPayableAccountId(), taxReversed,
                "Tax Reversal - CM#" + creditMemo.getCreditMemoId()),
            GLEntry.credit(config.getAccountsReceivableAccountId(), 
                creditMemo.getTotalAmount(),
                "AR Reduction - CM#" + creditMemo.getCreditMemoId())
        );
        
        glPostingService.postEntries(glEntries, currentPeriod.getId(), 
            isPriorPeriod, invoicePeriod.getId());
        
        // 6. Update invoice balance (via Invoice service)
        invoiceServiceClient.applyCreditMemo(
            invoice.getInvoiceId(),
            ApplyCreditMemoRequest.builder()
                .creditMemoId(creditMemo.getCreditMemoId())
                .totalAmount(creditMemo.getTotalAmount())
                .appliedBy(currentUser)
                .build()
        );
        
        // 7. Build and return response
        return buildResponse(creditMemo, invoice.getOutstandingBalance()
            .subtract(creditMemo.getTotalAmount()));
    }
}
```

### 2. Controller Layer

**File (to create):** `pos-accounting/src/main/java/com/positivity/accounting/internal/controller/CreditMemoController.java`

**Responsibilities:**
1. Expose REST endpoints for Credit Memo operations
2. Validate request bodies with Bean Validation
3. Enforce authorization with `@PreAuthorize`
4. Emit telemetry events with `@EmitEvent`
5. Provide OpenAPI documentation with annotations

**Pseudocode:**

```java
@RestController
@RequestMapping("/v1/accounting/credit-memos")
@Tag(name = "Credit Memos", description = "Manage credit memos for AR corrections")
public class CreditMemoController {
    
    private final CreditMemoService creditMemoService;
    
    @PostMapping
    @PreAuthorize("hasAuthority('accounting:credit-memo:create')")
    @Operation(summary = "Create credit memo", 
               description = "Create a new Credit Memo to reverse invoice charges...")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Credit memo created"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Invoice not found"),
        @ApiResponse(responseCode = "409", description = "Business rule violation")
    })
    @EmitEvent(id = "ACCOUNTING_CREDIT_MEMO_CREATE", apiVersion = "1")
    public ResponseEntity<CreditMemoResponse> createCreditMemo(
            @Valid @RequestBody CreateCreditMemoRequest request) {
        
        String currentUser = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        
        CreditMemoResponse response = creditMemoService.createCreditMemo(
            request, currentUser
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('accounting:credit-memo:read')")
    @Operation(summary = "List credit memos", 
               description = "Retrieve paginated credit memos with optional filters")
    @EmitEvent(id = "ACCOUNTING_CREDIT_MEMO_LIST", apiVersion = "1")
    public ResponseEntity<Page<CreditMemoResponse>> listCreditMemos(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID originalInvoiceId,
            @RequestParam(required = false) CreditMemoStatus status,
            Pageable pageable) {
        
        Page<CreditMemoResponse> results = creditMemoService.listCreditMemos(
            customerId, originalInvoiceId, status, pageable
        );
        
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/{creditMemoId}")
    @PreAuthorize("hasAuthority('accounting:credit-memo:read')")
    @Operation(summary = "Get credit memo", 
               description = "Retrieve details for a specific Credit Memo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Credit memo returned"),
        @ApiResponse(responseCode = "404", description = "Credit memo not found")
    })
    @EmitEvent(id = "ACCOUNTING_CREDIT_MEMO_GET", apiVersion = "1")
    public ResponseEntity<CreditMemoResponse> getCreditMemo(
            @PathVariable UUID creditMemoId) {
        
        CreditMemoResponse response = creditMemoService.getCreditMemo(creditMemoId);
        return ResponseEntity.ok(response);
    }
}
```

### 3. Event Registration

**File (to create):** `pos-accounting/src/main/java/com/positivity/accounting/internal/config/CreditMemoEventTypes.java`

```java
public final class CreditMemoEventTypes {
    private CreditMemoEventTypes() {}

    public static List<EventTypeRegistration> all() {
        return List.of(
            EventTypeRegistration.write("ACCOUNTING_CREDIT_MEMO_CREATE", 
                "Create a credit memo").build(),
            EventTypeRegistration.fastRead("ACCOUNTING_CREDIT_MEMO_LIST", 
                "List credit memos").build(),
            EventTypeRegistration.fastRead("ACCOUNTING_CREDIT_MEMO_GET", 
                "Get credit memo details").build()
        );
    }
}
```

**File (to create):** `pos-accounting/src/main/java/com/positivity/accounting/internal/config/CreditMemoEventTypeInitializer.java`

```java
@Component
public class CreditMemoEventTypeInitializer implements ApplicationRunner {
    // ... (follow pattern from PaymentApplicationEventTypeInitializer)
    
    @Override
    public void run(ApplicationArguments args) {
        initializerSupport.registerEventTypes(
            CreditMemoEventTypes.all(), 
            this::registerEventType
        );
    }
}
```

### 4. Testing

**File (to create):** `pos-accounting/src/test/java/com/positivity/accounting/service/CreditMemoServiceTest.java`

**Test Cases:**
1. `testCreateCreditMemo_FullCredit_Success`
2. `testCreateCreditMemo_PartialCredit_Success`
3. `testCreateCreditMemo_PriorPeriodInvoice_FlagSet`
4. `testCreateCreditMemo_AmountExceedsBalance_ThrowsException`
5. `testCreateCreditMemo_InvoiceNotFinalized_ThrowsException`
6. `testCreateCreditMemo_MissingReasonCode_ThrowsException`
7. `testCreateCreditMemo_GLEntriesBalanced`
8. `testListCreditMemos_FilterByCustomer`
9. `testListCreditMemos_FilterByInvoice`
10. `testGetCreditMemo_NotFound_ThrowsException`

**File (to create):** `pos-accounting/src/test/java/com/positivity/accounting/contract/CreditMemoContractBehaviorIT.java`

**Contract Test Cases (from Contract Guide Examples):**
1. Create full credit memo (example from contract guide)
2. Create partial credit memo with prior period adjustment
3. Validate error response: amount exceeds balance (409 Conflict)
4. Validate error response: missing reason code (400 Bad Request)
5. Validate error response: invoice not finalized (409 Conflict)
6. Validate pagination for list endpoint
7. Validate filters (customerId, originalInvoiceId, status)

---

## Integration Requirements

### 1. Invoice Service Integration

**New Endpoint Required in pos-invoice:**

```http
POST /v1/invoices/{invoiceId}/apply-credit-memo
```

**Request:**
```json
{
  "creditMemoId": "cm-uuid",
  "totalAmount": 110.00,
  "appliedBy": "user-id"
}
```

**Response:**
```json
{
  "invoiceId": "inv-uuid",
  "balanceBefore": 110.00,
  "balanceAfter": 0.00,
  "status": "PAID_IN_FULL"
}
```

**Coordination:** Requires separate PR in `pos-invoice` module to add this endpoint.

### 2. GL Posting Service

**Integration Point:**

The `GLPostingService` (or equivalent) must support:
- Posting multiple GL entries atomically
- Tagging entries with `priorPeriodAdjustment` flag
- Linking entries back to originating Credit Memo
- Validating entry balance (total debits = total credits)

**If GLPostingService doesn't exist:**
- Stub implementation for Phase 1
- Full implementation in separate GL capability

### 3. Accounting Period Service

**Integration Point:**

The `AccountingPeriodService` must support:
- Finding accounting period by date
- Finding current open period
- Determining if a period is closed

**If AccountingPeriodService doesn't exist:**
- Simplify: assume all periods are open (skip prior period adjustment)
- Or: stub with in-memory period tracking

---

## Database Schema

### Table: `credit_memo`

```sql
CREATE TABLE credit_memo (
    credit_memo_id UUID PRIMARY KEY,
    original_invoice_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    credit_amount NUMERIC(19,4) NOT NULL,
    tax_amount_reversed NUMERIC(19,4) NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    justification_note VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    creation_timestamp TIMESTAMP NOT NULL,
    posted_timestamp TIMESTAMP,
    created_by_user_id VARCHAR(50) NOT NULL,
    prior_period_adjustment BOOLEAN NOT NULL DEFAULT FALSE,
    original_period_id VARCHAR(50),
    currency CHAR(3) NOT NULL,
    
    CONSTRAINT fk_credit_memo_invoice FOREIGN KEY (original_invoice_id) 
        REFERENCES invoice(invoice_id),
    CONSTRAINT fk_credit_memo_customer FOREIGN KEY (customer_id) 
        REFERENCES customer(customer_id)
);

CREATE INDEX idx_credit_memo_original_invoice ON credit_memo(original_invoice_id);
CREATE INDEX idx_credit_memo_customer ON credit_memo(customer_id);
CREATE INDEX idx_credit_memo_status ON credit_memo(status);
CREATE INDEX idx_credit_memo_posted_timestamp ON credit_memo(posted_timestamp);
```

**Migration:** Create Flyway or Liquibase migration script.

---

## Security & Permissions

### Required Authorities

- `accounting:credit-memo:create` - Create Credit Memos
- `accounting:credit-memo:read` - List and view Credit Memos

**Configuration:** Add to `pos-security-service` role definitions.

---

## Observability

### Metrics

- `credit_memo.created.count` - Counter, tags: `reasonCode`, `isPriorPeriodAdjustment`
- `credit_memo.created.duration` - Histogram
- `credit_memo.gl_posting.duration` - Histogram
- `credit_memo.validation_errors.count` - Counter, tags: `errorType`

### Logging

- Log Credit Memo creation with: `creditMemoId`, `originalInvoiceId`, `totalAmount`, `reasonCode`
- Log GL posting success/failure
- Log invoice balance update success/failure
- Use correlation IDs for request tracing

### Tracing

- Span: `createCreditMemo`
- Span: `postGLEntries`
- Span: `updateInvoiceBalance`
- Propagate trace context to Invoice service client

---

## Testing Strategy

### Unit Tests
- Service layer: business logic, validation rules
- Repository layer: query methods (if custom SQL)
- DTO validation: Bean Validation constraints

### Integration Tests
- `CreditMemoContractBehaviorIT`: End-to-end tests using contract guide examples
- Test with embedded database (H2)
- Mock Invoice service client
- Stub GL posting service

### Manual Testing
- Postman collection with contract guide examples
- Test against dev environment
- Verify GL entries in accounting reports

---

## Deployment & Rollout

### Prerequisites
- Database migration applied
- Event types registered in `pos-events`
- Authorities added to security service
- Invoice service integration deployed (if ready)

### Rollout Plan
1. Deploy to dev environment
2. Run integration tests
3. Deploy to staging
4. Run manual tests with accounting team
5. Deploy to production (off-hours)
6. Monitor for errors and rollback if needed

### Rollback Plan
- Database migration is backward-compatible (table is new)
- Rollback application deployment
- No data cleanup required (no Credit Memos created yet)

---

## Open Questions & Risks

### Open Questions
1. **GL Account Configuration:** Where are revenue/tax/AR account IDs stored?
2. **Invoice Service Availability:** Is Invoice service ready for integration?
3. **Accounting Period Service:** Does it exist? If not, how should periods be tracked?
4. **Approval Workflow:** Is approval required for v1.0? (Resolved: No per issue #131 comment)

### Risks
1. **Invoice Service Dependency:** Credit Memo creation blocked without Invoice service integration
   - **Mitigation:** Implement with stubbed invoice client; coordinate on Invoice service PR
2. **GL Posting Complexity:** Full GL integration is non-trivial
   - **Mitigation:** Start with stub implementation; full GL in separate capability
3. **Prior Period Adjustment Logic:** Complex accounting rules
   - **Mitigation:** Document assumptions; coordinate with accounting domain experts

---

## Next Steps

1. **Contract Review:** Get approval on contract guide updates from stakeholders
2. **Invoice Service Coordination:** Open issue in `pos-invoice` for Credit Memo integration endpoint
3. **GL Service Coordination:** Identify or create GL posting service
4. **Service Implementation:** Complete Phase 2 implementation (service, controller, tests)
5. **Integration Testing:** End-to-end testing with Invoice and GL services
6. **Deployment:** Follow rollout plan

---

## References

- **Parent Capability:** [CAP-052](https://github.com/louisburroughs/durion/issues/52)
- **Backend Story:** [durion-positivity-backend#131](https://github.com/louisburroughs/durion-positivity-backend/issues/131)
- **Contract Guide:** `durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- **Issue #131 Resolution Comment:** [Comment #3734513758](https://github.com/louisburroughs/durion-positivity-backend/issues/131#issuecomment-3734513758)
- **Payment Application Pattern:** `pos-accounting/src/main/java/com/positivity/accounting/service/PaymentApplicationService.java`

---

**Document Version:** 1.0  
**Author:** GitHub Copilot (Claude Sonnet 4.5)  
**Generated:** 2026-02-08
