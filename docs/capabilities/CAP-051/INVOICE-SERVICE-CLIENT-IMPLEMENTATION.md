# InvoiceServiceClient Implementation Summary

**Created:** February 8, 2026  
**Branch:** `cap/CAP051`  
**Related Issue:** [#114 - Apply Payment to Invoice](https://github.com/louisburroughs/durion-positivity-backend/issues/114)

---

## Overview

Implemented **InvoiceServiceClient** with circuit breaker resilience for safe cross-service communication with the Invoice module. This enables the accounting service to validate invoices and update their payment status when payments are applied or reversed.

---

## Files Created

### 1. DTOs (Request/Response Objects)

#### `InvoiceDetails.java` (57 lines)

- Invoice state retrieved from Invoice service
- Contains: `invoiceId`, `customerId`, `status`, `totalAmount`, `balanceDue`, `currency`, etc.
- Used for payment application validation

#### `ApplyPaymentToInvoiceRequest.java` (52 lines)

- Request to apply payment to invoice
- Sent to Invoice service after accounting records payment application
- Contains: `paymentApplicationId`, `amountApplied`, `appliedAt`, `currency`, etc.
- Full validation with Jakarta annotations (`@NotNull`, `@Positive`)

#### `ApplyPaymentToInvoiceResponse.java` (54 lines)

- Response from Invoice service after payment applied
- Contains updated invoice state: `status`, `balanceBefore`, `balanceAfter`, `totalPaid`, etc.
- Used to populate payment application response details

#### `ReversePaymentApplicationRequest.java` (52 lines)

- Request to reverse payment application on invoice
- Contains: `paymentApplicationId`, `reversalId`, `amountToRestore`, `reason`, `reversedBy`
- Full validation with Jakarta annotations

#### `ReversePaymentApplicationResponse.java` (47 lines)

- Response from Invoice service after reversal
- Contains restored invoice state: `status`, `balanceBefore`, `balanceDue`, etc.

### 2. REST Client with Circuit Breaker

#### `InvoiceServiceClient.java` (267 lines)

**Responsibilities:**

- Fetch invoice details via `GET /v1/invoices/{invoiceId}`
- Apply payment via `POST /v1/invoices/{invoiceId}/apply-payment`
- Reverse payment via `POST /v1/invoices/{invoiceId}/reverse-payment`

**Features:**

- ✅ Resilience4j circuit breaker with `@CircuitBreaker` annotation
- ✅ Fallback methods for graceful degradation
- ✅ Detailed logging for debugging (DEBUG: success/error, INFO: state changes, WARN: transitions)
- ✅ Error handling with `InvoiceServiceException`
- ✅ Health check method for pre-flight validation
- ✅ Configurable base URL via `pos.invoice.service.url` property

**Circuit Breaker Behavior:**

- **CLOSED:** Normal operation, all requests pass through
- **OPEN:** Threshold exceeded (50% failure or 100% slow calls), requests fail immediately with 503
- **HALF_OPEN:** Testing recovery, 3 requests allowed before determining state
- **Configuration:** 30s wait before half-open, 5s slow call duration threshold

### 3. Circuit Breaker Configuration

#### `InvoiceCircuitBreakerConfig.java` (95 lines)

- Defines circuit breaker bean `invoiceServiceCircuitBreaker`
- Global configuration: 50% failure threshold, 100% slow call threshold, 5s timeout
- Event listeners for state transitions and errors
- Logs all events for observability

**Thresholds:**

- Failure rate: 50%
- Slow call rate: 100%
- Slow call duration: 5 seconds
- Wait duration in open state: 30 seconds
- Minimum calls to measure: 10
- Half-open calls: 3

### 4. REST Client Configuration

#### `RestClientConfig.java` (27 lines)

- Provides `RestClient` bean for HTTP operations
- Spring Boot 4.0 native HTTP client (no RestTemplate)
- Extensible for future customization (timeouts, interceptors, etc.)

### 5. Custom Exception

#### `InvoiceServiceException.java` (103 lines)

- RuntimeException for invoice service failures
- Captures HTTP status codes
- Helper methods: `isServiceUnavailable()`, `isNotFound()`, `isClientError()`, `isServerError()`
- Full stack trace for debugging

---

## Configuration

### `application.yml` (Added)

```yaml
pos:
  invoice:
    service:
      url: http://pos-invoice:8085
      timeout: 5000  # milliseconds
```

**Environment Override:** Set `POS_INVOICE_SERVICE_URL` to override default URL.

### Dependencies Added (pom.xml)

```xml
<!-- Resilience4j Circuit Breaker -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-core</artifactId>
    <version>${resilience4j.version}</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
    <version>${resilience4j.version}</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>${resilience4j.version}</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-aspects</artifactId>
    <version>${resilience4j.version}</version>
</dependency>
```

---

## Usage in PaymentApplicationService

### To validate invoice before applying payment

```java
@Autowired
private InvoiceServiceClient invoiceServiceClient;

public void applyPaymentToInvoices(UUID paymentId, PaymentApplicationRequest request) {
    // Validate each invoice
    for (InvoiceApplication invoiceApp : request.getApplications()) {
        InvoiceDetails invoice = invoiceServiceClient.getInvoiceDetails(invoiceApp.getInvoiceId());
        
        // Validate invoice state
        if (!isInvoiceApplicable(invoice)) {
            throw new IllegalArgumentException(
                "Invoice " + invoiceApp.getInvoiceId() + " is not in applicable state: " + invoice.getStatus()
            );
        }
        
        // Validate currency match
        if (!invoice.getCurrency().equals(payment.getCurrency())) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        
        // Validate sufficient balance
        if (invoiceApp.getAmountToApply().compareTo(invoice.getBalanceDue()) > 0) {
            throw new IllegalArgumentException("Amount exceeds invoice balance");
        }
    }
}
```

### To update invoice after applying payment

```java
public void applyPaymentToInvoices(UUID paymentId, PaymentApplicationRequest request) {
    // Create payment application record
    PaymentApplication application = paymentApplicationRepository.save(...);
    
    // Apply to invoice service
    ApplyPaymentToInvoiceResponse response = invoiceServiceClient.applyPaymentToInvoice(
        invoiceApp.getInvoiceId(),
        ApplyPaymentToInvoiceRequest.builder()
            .paymentApplicationId(application.getPaymentApplicationId())
            .amountApplied(invoiceApp.getAmountToApply())
            .appliedAt(Instant.now())
            .currency(payment.getCurrency())
            .paymentId(paymentId)
            .appliedBy(getCurrentUser())
            .build()
    );
    
    // Use response for audit details
    log.info("Applied ${}  to invoice {}: balance {} → {}",
        invoiceApp.getAmountToApply(),
        invoiceApp.getInvoiceId(),
        response.getBalanceBefore(),
        response.getBalanceAfter()
    );
}
```

### To reverse payment

```java
public void reversePaymentApplication(UUID paymentApplicationId, String reason, String reversedBy) {
    // Get original application
    PaymentApplication original = paymentApplicationRepository.findById(paymentApplicationId).orElseThrow();
    
    // Create reversal record
    PaymentApplicationReversal reversal = reversalRepository.save(...);
    
    // Reverse in invoice service
    ReversePaymentApplicationResponse response = invoiceServiceClient.reversePaymentApplication(
        original.getInvoiceId(),
        ReversePaymentApplicationRequest.builder()
            .paymentApplicationId(paymentApplicationId)
            .reversalId(reversal.getReversalId())
            .amountToRestore(original.getAppliedAmount())
            .reason(reason)
            .reversedBy(reversedBy)
            .build()
    );
    
    log.info("Reversed payment on invoice {}: balance {} → {}",
        original.getInvoiceId(),
        response.getBalanceBefore(),
        response.getBalanceDue()
    );
}
```

---

## Error Handling

### Circuit Breaker States

| State | Behavior | Recovery |
|-------|----------|----------|
| **CLOSED** | Normal, requests pass through | N/A |
| **OPEN** | Fail fast with 503 | Wait 30s, then test |
| **HALF_OPEN** | Allow 3 test requests | If successful → CLOSED, else → OPEN |

### Exception Handling

```java
try {
    InvoiceDetails invoice = invoiceServiceClient.getInvoiceDetails(invoiceId);
} catch (InvoiceServiceException e) {
    if (e.isServiceUnavailable()) {
        // Retry later or queue for async processing
        log.warn("Invoice service unavailable, requeueing...");
    } else if (e.isNotFound()) {
        // Invoice doesn't exist, fail immediately
        throw new InvalidInvoiceException("Invoice not found");
    } else if (e.isClientError()) {
        // Validation error, don't retry
        throw new IllegalArgumentException(e.getMessage());
    } else {
        // Server error, could retry
        throw e;
    }
}
```

---

## Logging

### Log Levels

**DEBUG** (verbose, disabled by default):

```
Fetching invoice details for invoice 550e8400-e29b-41d4-a716-446655440000
Retrieved invoice ...: status=OPEN, balanceDue=500.00
Successfully applied payment to invoice ...: new status=PARTIALLY_PAID, balanceAfter=50.00
```

**INFO** (important events):

```
Applying payment 550e8400-e29b-41d4-a716-446655440001 to invoice 550e8400-e29b-41d4-a716-446655440000: amount=450.00, currency=USD
Successfully applied payment to invoice ...: new status=PARTIALLY_PAID, balanceAfter=50.00
Reversing payment application ...: amount=450.00, reason=Duplicate payment - authorized by customer service
```

**WARN** (anomalies):

```
Invoice service error: 404 Not Found
Invoice service error applying payment: 400 Bad Request
Circuit breaker transitioned: CLOSED -> OPEN
Circuit breaker transitioned: HALF_OPEN -> CLOSED
```

**ERROR** (failures):

```
Failed to fetch invoice details for ...: Connection refused
Circuit breaker open for getInvoiceDetails(...): org.springframework.web.client.ResourceAccessException
Failed to apply payment to invoice: ...
```

---

## Testing Recommendations

### Unit Tests

```java
@SpringBootTest
class InvoiceServiceClientTest {
    
    @MockBean
    private RestClient restClient;
    
    @Autowired
    private InvoiceServiceClient invoiceServiceClient;
    
    @Test
    void testGetInvoiceDetailsSuccess() {
        // Mock RestClient to return invoice details
    }
    
    @Test
    void testGetInvoiceDetailsNotFound() {
        // Mock RestClient to return 404
    }
    
    @Test
    void testCircuitBreakerOpensAfterThreshold() {
        // Simulate multiple failures, verify circuit breaker opens
    }
    
    @Test
    void testFallbackMethodCalledWhenOpen() {
        // Verify fallback method throws InvoiceServiceException
    }
}
```

### Integration Tests

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvoiceServiceClientIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Test
    void testApplyPaymentToInvoiceEndToEnd() {
        // Start Mock Invoice service
        // Call applyPaymentToInvoice()
        // Verify request/response
    }
}
```

---

## Performance Characteristics

| Operation | Expected Time | Slow Threshold |
|-----------|---|---|
| Get invoice details | 50-100ms | > 5s |
| Apply payment | 100-200ms | > 5s |
| Reverse payment | 100-200ms | > 5s |

**Timeouts:**

- Connection timeout: Spring default (10s)
- Read timeout: 5 seconds (configurable)
- Circuit breaker opens after 10 calls with 50% failure rate

---

## Next Steps

1. **Integrate into PaymentApplicationService:**
   - Add TODO #1-5 implementations using InvoiceServiceClient
   - See `$WORKSPACE/durion-positivity-backend/CAP-051-TODOS-AND-INTEGRATION-POINTS.md`

2. **Build and Test:**

   ```bash
   cd durion-positivity-backend
   ./mvnw clean compile -pl pos-accounting
   ./mvnw test -pl pos-accounting
   ```

3. **Mock Invoice Service for Testing:**
   - Use WireMock or TestRestTemplate
   - Create test fixtures for various invoice states

4. **Monitor in Production:**
   - Track circuit breaker state changes
   - Alert on high failure rates (> 50%)
   - Log all service interactions

---

## Architecture Diagram

```
PaymentApplicationService
    ↓
    ├─→ invoiceServiceClient.getInvoiceDetails(invoiceId)
    │   ├─→ [Circuit Breaker: CLOSED]
    │   ├─→ RestClient.get(/v1/invoices/{id})
    │   └─→ InvoiceDetails | InvoiceServiceException
    │
    ├─→ invoiceServiceClient.applyPaymentToInvoice(...)
    │   ├─→ [Circuit Breaker: CLOSED]
    │   ├─→ RestClient.post(/v1/invoices/{id}/apply-payment)
    │   └─→ ApplyPaymentToInvoiceResponse | InvoiceServiceException
    │
    └─→ invoiceServiceClient.reversePaymentApplication(...)
        ├─→ [Circuit Breaker: CLOSED]
        ├─→ RestClient.post(/v1/invoices/{id}/reverse-payment)
        └─→ ReversePaymentApplicationResponse | InvoiceServiceException
```

---

## References

- **Issue #114:** [Apply Payment to Invoice](https://github.com/louisburroughs/durion-positivity-backend/issues/114)
- **Resilience4j:** [Circuit Breaker Documentation](https://resilience4j.readme.io/docs/circuitbreaker)
- **Spring RestClient:** [Official Documentation](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- **Analysis Document:** [CAP-051-TODOS-AND-INTEGRATION-POINTS.md](CAP-051-TODOS-AND-INTEGRATION-POINTS.md)

---

**Status:** ✅ Complete — Ready for integration into PaymentApplicationService
