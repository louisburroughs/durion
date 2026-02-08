# CAP-051 TODOs and Integration Points — Detailed Analysis

**Issue:** [#114 - Apply Payment to Invoice](https://github.com/louisburroughs/durion-positivity-backend/issues/114)  
**Branch:** `cap/CAP051`  
**Status:** Implementation Complete — Integration Pending  
**Created:** February 8, 2026

---

## Executive Summary

PR #114 implements the core payment application workflow with **atomic transactions, idempotency, and audit trails**. However, **integration with the Invoice service is stubbed** with TODOs throughout the codebase. This document maps each TODO, explains its impact, and provides implementation guidance.

### Key Points

- ✅ **Payment application entity model** complete
- ✅ **Service business logic** (atomic apply, reversals, overpayments) implemented
- ✅ **REST API controllers** with security decorators
- ✅ **Event infrastructure** (Spring listener) in place
- 🟡 **Invoice integration** stubbed with TODOs (5 items)
- 🟡 **Kafka migration** planned but not started
- 🟡 **Comprehensive tests** deferred due to API signature drift

---

## Part 1: Invoice Service Integration (HIGH PRIORITY)

### Why This Matters

Invoice balance tracking is **the business heart** of payment application. Without it:
- Invoice statuses remain stale (`OPEN` → should transition to `PartiallyPaid` / `PaidInFull`)
- Balance-due calculations don't reflect paid amounts
- Downstream reporting (aged payables, DSO) becomes inaccurate
- Revenue recognition systems can't rely on invoice payment state

### Current State

**Existing Service:**
- `InvoicePaymentStatusService` exists in `pos-accounting` module (lines 1-173)
- Handles idempotency via `IdempotencyService`
- Has retry logic (`@Retryable` with exponential backoff)
- Tracks payment events and status views

**Problem:**
- `PaymentApplicationService` has 5 TODO comments indicating invoice integration is incomplete
- No cross-module REST client to `pos-invoice` service
- Status view updates are stubbed

### TODO #1: Validate Invoice Applicability

**Location:** `PaymentApplicationService.validateInvoiceApplication()`, lines 301-315

**Current Code:**
```java
private void validateInvoiceApplication(
        PaymentApplicationRequest.InvoiceApplication invoiceApp,
        String paymentCurrency) {

    // Validate amount > 0
    if (invoiceApp.getAmountToApply().compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException(
                "Amount to apply must be greater than 0 for invoice " + invoiceApp.getInvoiceId());
    }

    // TODO: Validate invoice exists, is applicable (not
    // PaidInFull/Voided/Cancelled)
    // TODO: Validate currency matches
    // TODO: Validate amountToApply <= invoice.balanceDue

    // For now, log warning that full validation is pending
    log.warn("Invoice validation not yet implemented for invoice {}", invoiceApp.getInvoiceId());
}
```

**What Needs to Happen:**

1. **Call Invoice Service** to fetch invoice details:
   - Invoice status (`OPEN`, `PartiallyPaid`, `PaidInFull`, `Voided`, `Cancelled`)
   - Current `balanceDue`
   - Invoice total amount
   - Currency

2. **Validate invoice is applicable:**
   ```java
   // Invalid states for payment application
   if (invoice.status in [PAID_IN_FULL, VOIDED, CANCELLED]) {
       throw new IllegalArgumentException(
           "Invoice " + invoiceId + " is in state " + invoice.status + 
           " and cannot accept payments");
   }
   ```

3. **Validate currency match:**
   ```java
   if (!invoice.currency.equals(paymentCurrency)) {
       throw new IllegalArgumentException(
           "Currency mismatch: payment is " + paymentCurrency + 
           " but invoice is " + invoice.currency);
   }
   ```

4. **Validate applied amount doesn't exceed balance:**
   ```java
   if (invoiceApp.getAmountToApply().compareTo(invoice.balanceDue) > 0) {
       throw new IllegalArgumentException(
           "Amount to apply (" + invoiceApp.getAmountToApply() + 
           ") exceeds invoice balance (" + invoice.balanceDue + ")");
   }
   ```

**Implementation Pattern:**

Create a REST client using Spring's `RestClient` (or `RestTemplate`):

```java
@Component
public class InvoiceServiceClient {
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public InvoiceServiceClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://pos-invoice:8085").build();
        // Circuit breaker config below
    }

    public InvoiceDetails getInvoiceDetails(UUID invoiceId) {
        // GET /v1/invoices/{invoiceId}
        return restClient.get()
            .uri("/v1/invoices/{id}", invoiceId)
            .retrieve()
            .body(InvoiceDetails.class);
    }
}
```

**Error Handling:**
- If invoice service is unavailable → fail the application with **clear user message**
- Use circuit breaker to prevent cascading failures
- Log invoice service failures with trace context for debugging

---

### TODO #2: Update Invoice Balance After Application

**Location:** `PaymentApplicationService.applyPaymentToInvoices()`, lines 152-162

**Current Code:**
```java
for (PaymentApplicationRequest.InvoiceApplication invoiceApp : request.getApplications()) {
    PaymentApplication application = new PaymentApplication();
    application.setPaymentId(paymentId);
    // ... set fields ...
    PaymentApplication saved = paymentApplicationRepository.save(application);

    // Update invoice status (via existing service)
    // Note: This assumes invoice balance tracking exists elsewhere
    // For now, we'll use the existing InvoicePaymentStatusService pattern
    applicationDetails.add(buildApplicationDetail(saved, invoiceApp.getInvoiceId()));
    // NO actual invoice update happens here!
}
```

**What Needs to Happen:**

After creating each `PaymentApplication` record, call the invoice service to:

1. **Reduce invoice `balanceDue`:**
   ```
   new_balanceDue = current_balanceDue - appliedAmount
   ```

2. **Update invoice status** based on balance:
   - `balanceDue > 0` → `PARTIALLY_PAID`
   - `balanceDue == 0` → `PAID_IN_FULL`
   - `balanceDue < 0` → `OVERPAID` (error case, or create credit)

3. **Record audit event** for payment application

**API Contract (suggested):**

```http
POST /v1/invoices/{invoiceId}/apply-payment
Content-Type: application/json

{
  "paymentApplicationId": "550e8400-e29b-41d4-a716-446655440000",
  "amountApplied": 450.00,
  "appliedAt": "2026-02-08T10:30:00Z",
  "currency": "USD"
}

Response: 200 OK
{
  "invoiceId": "550e8400-e29b-41d4-a716-446655440001",
  "status": "PARTIALLY_PAID",
  "balanceDue": 50.00,
  "totalAmount": 500.00,
  "amountApplied": 450.00,
  "lastUpdated": "2026-02-08T10:30:00Z"
}
```

**Implementation Pattern:**

```java
// In PaymentApplicationService.applyPaymentToInvoices()

for (PaymentApplicationRequest.InvoiceApplication invoiceApp : request.getApplications()) {
    PaymentApplication application = new PaymentApplication();
    // ... create application ...
    PaymentApplication saved = paymentApplicationRepository.save(application);

    // NEW: Call invoice service to update balance
    try {
        InvoiceUpdateResponse invoiceUpdate = invoiceServiceClient.applyPaymentToInvoice(
            invoiceApp.getInvoiceId(),
            new ApplyPaymentRequest(
                saved.getPaymentApplicationId(),
                invoiceApp.getAmountToApply(),
                applicationTimestamp,
                payment.getCurrency()
            )
        );
        
        applicationDetails.add(buildApplicationDetail(
            saved, 
            invoiceApp.getInvoiceId(),
            invoiceUpdate  // Include updated balance before/after
        ));
    } catch (ServiceUnavailableException e) {
        // TODO: Consider compensation strategy:
        // Option A: Rollback entire transaction (strict consistency)
        // Option B: Queue for retry (eventual consistency)
        log.error("Failed to update invoice balance", e);
        throw new PaymentApplicationException("Could not apply to invoice", e);
    }
}
```

**Consistency Strategy:**
- **Strict Consistency (Recommended for Payments):** If invoice service fails, rollback entire transaction. Payment application doesn't proceed if invoice can't be updated.
- **Eventual Consistency:** Create payment application, queue invoice update as async event, retry on failure.

---

### TODO #3: Fetch Invoice Balance Before/After (for Response)

**Location:** `PaymentApplicationService.buildApplicationDetail()`, lines 320-333

**Current Code:**
```java
private PaymentApplicationResponse.ApplicationDetail buildApplicationDetail(
        PaymentApplication application,
        UUID invoiceId) {

    // TODO: Fetch actual invoice balance before/after
    // For now, return minimal details
    return PaymentApplicationResponse.ApplicationDetail.builder()
            .paymentApplicationId(application.getPaymentApplicationId())
            .invoiceId(invoiceId)
            .appliedAmount(application.getAppliedAmount())
            .invoiceBalanceBefore(BigDecimal.ZERO) // TODO: Get from invoice service
            .invoiceBalanceAfter(BigDecimal.ZERO) // TODO: Get from invoice service
            .invoiceStatus("UNKNOWN") // TODO: Get from invoice service
            .build();
}
```

**What Needs to Happen:**

Populate response with actual invoice details (from TODO #2 above):

```java
private PaymentApplicationResponse.ApplicationDetail buildApplicationDetail(
        PaymentApplication application,
        UUID invoiceId,
        InvoiceUpdateResponse invoiceUpdate) { // NEW: pass invoice data

    return PaymentApplicationResponse.ApplicationDetail.builder()
            .paymentApplicationId(application.getPaymentApplicationId())
            .invoiceId(invoiceId)
            .appliedAmount(application.getAppliedAmount())
            .invoiceBalanceBefore(invoiceUpdate.getBalanceBefore())
            .invoiceBalanceAfter(invoiceUpdate.getBalanceDue())
            .invoiceStatus(invoiceUpdate.getStatus())
            .appliedAt(application.getApplicationTimestamp())
            .build();
}
```

---

### TODO #4: Restore Invoice Balance on Reversal

**Location:** `PaymentApplicationService.reversePaymentApplication()`, lines 263-264

**Current Code:**
```java
// 5. Restore invoice balance (via existing service)
// TODO: Implement invoice balance restoration

log.info("Created reversal {} for application {} by {} (reason: {})",
        saved.getReversalId(), paymentApplicationId, reversedBy, reason);
```

**What Needs to Happen:**

When a payment application is reversed, **undo the invoice balance update**:

1. **Get original invoice state** (before the reversed application)
2. **Restore balance:** `new_balanceDue = old_balanceDue + reversal_amount`
3. **Update invoice status:**
   - If `balanceDue > 0` → back to `OPEN` or `PARTIALLY_PAID`
   - If `balanceDue == original_amount` → back to `OPEN`

**API Contract (suggested):**

```http
POST /v1/invoices/{invoiceId}/reverse-payment
Content-Type: application/json

{
  "paymentApplicationId": "550e8400-e29b-41d4-a716-446655440000",
  "reversalId": "660e8400-e29b-41d4-a716-446655440001",
  "amountToRestore": 450.00,
  "reason": "Duplicate payment - authorized by customer service",
  "reversedBy": "john.doe@company.com"
}

Response: 200 OK
{
  "invoiceId": "550e8400-e29b-41d4-a716-446655440001",
  "status": "OPEN",
  "balanceDue": 500.00,
  "reason": "Duplicate payment - authorized by customer service"
}
```

**Implementation Pattern:**

```java
// In PaymentApplicationService.reversePaymentApplication()

// 5. Restore invoice balance
try {
    InvoiceReverseResponse invoiceReverse = invoiceServiceClient.reversePaymentApplication(
        original.getInvoiceId(),
        new ReversePaymentRequest(
            paymentApplicationId,
            saved.getReversalId(),
            original.getAppliedAmount(),
            reason,
            reversedBy
        )
    );
    
    log.info("Restored invoice {} balance from {} to {}",
            original.getInvoiceId(), 
            invoiceReverse.getBalanceBefore(),
            invoiceReverse.getBalanceDue());
} catch (Exception e) {
    log.error("Failed to reverse invoice balance for {}", paymentApplicationId, e);
    // Consider: should reversal fail if invoice service is down?
    // Option A: Rollback the entire reversal (strict consistency)
    // Option B: Mark reversal as "pending_invoice_restoration" (eventual consistency)
    throw new PaymentApplicationException("Could not complete reversal", e);
}
```

---

### TODO #5: Get from SecurityContext (User Attribution)

**Location:** Multiple locations (lines 163, 180, 366, 376)

**Current Code:**
```java
application.setCreatedBy("SYSTEM"); // TODO: Get from SecurityContext
payment.setModifiedBy("SYSTEM"); // TODO: Get from SecurityContext
credit.setCreatedBy("SYSTEM"); // TODO: Get from SecurityContext
```

**What Needs to Happen:**

Extract authenticated user from Spring Security context:

```java
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

private String getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated()) {
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return principal.toString();
    }
    return "SYSTEM"; // Fallback if no auth context
}

// Usage:
application.setCreatedBy(getCurrentUser());
```

**Security Note:** Ensure `@PreAuthorize` decorators are in place to control who can apply/reverse payments.

---

## Part 2: Message Broker Integration (MEDIUM PRIORITY)

### Current State

**What exists:**
- `PaymentEventListenerConfig.java` — Spring `@EventListener` for `PaymentCleared` events
- Disabled by default: `@ConditionalOnProperty(prefix="pos.accounting.event-listener", name="enabled", havingValue="true", matchIfMissing=false)`
- Calls `PaymentApplicationService.handlePaymentCleared()` when event is published

**Problem:**
- Spring's internal event model is local and in-memory
- No durability: events lost on service restart
- No replay capability
- Not suitable for distributed systems

### Migration Path: Kafka

**Phase 1: Add Kafka Dependency**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

**Phase 2: Replace @EventListener with @KafkaListener**

```java
// BEFORE (PaymentEventListenerConfig.java)
@EventListener
@ConditionalOnProperty(prefix="pos.accounting.event-listener", 
    name="enabled", havingValue="true", matchIfMissing=false)
public void handlePaymentCleared(PaymentClearedEvent event) {
    paymentApplicationService.handlePaymentCleared(
        event.getPaymentId(),
        event.getCustomerId(),
        event.getCurrency(),
        event.getTotalAmount(),
        event.getClearedAt(),
        event.getSourceEventId()
    );
}

// AFTER
@KafkaListener(
    topics = "pos-payment.cleared",
    groupId = "pos-accounting",
    containerFactory = "kafkaListenerContainerFactory"
)
public void handlePaymentCleared(PaymentClearedEvent event) {
    try {
        paymentApplicationService.handlePaymentCleared(...);
    } catch (Exception e) {
        log.error("Failed to process PaymentCleared event", e);
        // Kafka will retry based on configuration
    }
}
```

**Phase 3: Configure Broker Connection**

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: kafka:9092
    consumer:
      group-id: pos-accounting
      auto-offset-reset: earliest
      max-poll-records: 500
    producer:
      acks: all
      retries: 3
```

**Phase 4: Dead-Letter Queue**

```java
@Configuration
public class KafkaConfig {
    
    @Bean
    public DeadLetterPublishingRecoverer dlpr(KafkaTemplate<String, Object> template) {
        return new DeadLetterPublishingRecoverer(
            template,
            (record, exception) -> new TopicPartition("pos-payment.cleared.dlq", 0)
        );
    }

    @Bean
    public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer dlpr) {
        return new DefaultErrorHandler(dlpr, new FixedBackOff(1000, 3));
    }
}
```

### Timeline

- **Now:** Spring `@EventListener` (suitable for monolithic proof-of-concept)
- **After Kafka Stable:** Migrate to `@KafkaListener`, enable dead-letter queue
- **Optional:** Add event sourcing if audit trail becomes critical

---

## Part 3: Comprehensive Test Coverage (MEDIUM PRIORITY)

### Unit Tests (PaymentApplicationService)

**Test Class:** `src/test/java/.../PaymentApplicationServiceTest.java`

**Key Test Cases:**

```java
@Test
public void testHandlePaymentCleared_Idempotency() {
    // Same sourceEventId processed twice should return existing ReceivablePayment
}

@Test
public void testHandlePaymentCleared_CreatesPaymentWithUnappliedAmount() {
    // Payment created with unappliedAmount = totalAmount
}

@Test
public void testApplyPaymentToInvoices_Idempotency() {
    // Same applicationRequestId processed twice should return same result
}

@Test
public void testApplyPaymentToInvoices_FailsIfInsufficientFunds() {
    // Requesting $600 with $500 payment should throw
}

@Test
public void testApplyPaymentToInvoices_FailsIfPaymentNotAvailable() {
    // Payment with status != AVAILABLE should throw
}

@Test
public void testApplyPaymentToInvoices_UpdatesPaymentUnappliedAmount() {
    // After applying $300 to payment with $500, unappliedAmount should be $200
}

@Test
public void testApplyPaymentToInvoices_CreatesCustomerCreditOnOverpayment() {
    // When applied amount < total payment, create CustomerCredit
}

@Test
public void testReversePaymentApplication_RestoresUnappliedAmount() {
    // After reversal, payment.unappliedAmount should increase
}

@Test
public void testReversePaymentApplication_FailsIfAlreadyReversed() {
    // Same application reversed twice should fail on second attempt
}
```

### Integration Tests (PaymentApplicationController)

**Test Class:** `src/test/java/.../PaymentApplicationControllerIntegrationTest.java`

```java
@Test
public void testApplyPaymentEndpoint_Success() {
    // POST /v1/accounting/payments/{paymentId}/applications
    // Verify: PaymentApplication created, response includes application details
}

@Test
public void testApplyPaymentEndpoint_IdempotencyOnRetry() {
    // POST same request twice with same applicationRequestId
    // Verify: Both return 200 OK with identical response
}

@Test
public void testApplyPaymentEndpoint_UnauthorizedWithoutAuthority() {
    // POST without "accounting:payment:apply" authority
    // Verify: 403 Forbidden
}

@Test
public void testReversePaymentEndpoint_Success() {
    // POST /v1/accounting/payment-applications/{applicationId}/reverse
    // Verify: PaymentApplicationReversal created
}

@Test
public void testReversePaymentEndpoint_RequiresReason() {
    // POST with missing "reason"
    // Verify: 400 Bad Request
}
```

### Event Listener Tests

```java
@Test
public void testPaymentClearedEventListener_CreatesReceivablePayment() {
    // Publish PaymentCleared event
    // Verify: ReceivablePayment created in database
}

@Test
public void testPaymentClearedEventListener_Idempotency() {
    // Publish same event twice
    // Verify: Only one ReceivablePayment created
}
```

---

## Part 4: Security & Authorization Audit

### Current State ✅

- ✅ `@PreAuthorize("hasAuthority('accounting:payment:apply')")` on apply endpoint
- ✅ `@PreAuthorize("hasAuthority('accounting:payment:reverse')")` on reverse endpoint
- ✅ Jakarta validation on request DTOs (`@NotNull`, `@NotBlank`, `@Size`, `@Positive`)
- ✅ SQL injection prevented (Spring Data JPA with parameter binding)

### Recommended Enhancements

1. **Audit Logging**
   - Log all payment application operations with user attribution
   - Include: `userId`, `paymentId`, `invoiceId`, `amount`, `timestamp`, `reason` (for reversals)

2. **PII Masking**
   - Ensure customer IDs don't leak into logs (use obfuscation or hashing)
   - Example: log customer ID as `CUST_***_5678` instead of full UUID

3. **Sensitive Data Handling**
   - Never log full payment amounts in debug logs
   - Mask amount in response bodies if user lacks "view:payment:details" authority

4. **Rate Limiting**
   - Add rate limiting to payment application endpoints to prevent abuse
   - Example: 100 requests per minute per customer

---

## Part 5: Performance & Observability

### Metrics to Track

```
# Meter: payment.application.duration
- Histogram of application execution time
- Attributes: paymentId, customerId, invoiceCount, status

# Counter: payment.application.total
- Total applications created
- Attributes: customerId, status (success/failure), reason_if_failure

# Counter: payment.reversal.total
- Total reversals created
- Attributes: customerId, reason

# Gauge: payment.unapplied.balance
- Current unapplied balance per customer
- Attributes: customerId, currency
```

### Observability Implementation

```java
@Component
public class PaymentApplicationMetrics {
    private final MeterRegistry meterRegistry;

    public void recordApplicationDuration(long durationMs, String status) {
        Timer.builder("payment.application.duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .record(durationMs, TimeUnit.MILLISECONDS);
        
        Counter.builder("payment.application.total")
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }
}
```

---

## Implementation Roadmap

### Week 1-2: Invoice Integration (Blocking)

1. ✅ Create `InvoiceServiceClient` REST client
2. ✅ Implement `validateInvoiceApplication()` with invoice service call
3. ✅ Implement invoice balance update after application
4. ✅ Implement invoice balance restoration after reversal
5. ✅ Add circuit breaker + retry logic
6. ✅ Update `buildApplicationDetail()` to include balance before/after
7. ✅ Extract user from `SecurityContext` for audit trail

### Week 3: Testing (Non-Blocking)

1. ✅ Unit tests for `PaymentApplicationService`
2. ✅ Integration tests for `PaymentApplicationController`
3. ✅ Event listener tests (Spring or Kafka)

### Week 4: Observability & Kafka (Non-Blocking)

1. ✅ Add metrics for payment applications and reversals
2. ✅ Plan Kafka migration (document topic names, schemas, DLQ config)
3. ✅ Document observability contract

---

## Questions for Product / Architecture Review

1. **Invoice Integration Consistency:**
   - Should payment application fail if invoice service is unavailable? (Strict consistency)
   - Or should we queue invoice update as async event? (Eventual consistency)

2. **Overpayment Handling:**
   - When applied amount < payment total, auto-create `CustomerCredit`?
   - Should customer credit be applied to subsequent invoices automatically?

3. **Reversal Restrictions:**
   - Should there be a time limit for reversals (e.g., only within 30 days)?
   - Who should be authorized to reverse? Only accounting managers?

4. **Event Replay:**
   - With Kafka, should we support replaying `PaymentCleared` events?
   - How far back can we replay (all history, or last 30 days)?

5. **Multi-Currency:**
   - Should we support payments in one currency applied to invoices in another?
   - Or enforce strict currency matching?

---

## Related Documentation

- **Issue #114:** [Apply Payment to Invoice](https://github.com/louisburroughs/durion-positivity-backend/issues/114)
- **PR #114:** CAP-051 Backend Implementation
- **Contract Guide:** `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- **Implementation Doc:** `pos-accounting/docs/CAP-051-backend-implementation.md`

---

## Next Steps

1. **Review this analysis** with product and architecture teams
2. **Prioritize invoice integration** (blocking for production)
3. **Create JIRA tickets** for each TODO
4. **Assign owners** and set timeline
5. **Update this document** as implementation progresses

---

**Last Updated:** February 8, 2026  
**Author:** GitHub Copilot  
**Status:** Ready for Team Review
