# POSITIVITY_DOMAIN_NOTES.md

## Summary

This document provides non-normative, verbose rationale and decision logs for the Positivity domain within the Durion POS system. Positivity is the POS-facing orchestration domain that owns the Order aggregate and provides API composition for browse/quote experiences. It documents design choices around order cancellation saga orchestration, product detail view aggregation with graceful degradation, and cross-domain integration patterns.

## Completed items

- [x] Linked each Decision ID to detailed rationale
- [x] Documented alternatives for saga orchestration and API composition
- [x] Provided architectural implications for order lifecycle and degradation
- [x] Included auditor-facing explanations with query examples
- [x] Documented cross-domain integration contracts

## Decision details

### DECISION-POSITIVITY-001 — Order Cancellation with Persisted Saga Pattern

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-POSITIVITY-001)
- **Decision:** Order cancellation uses a persisted saga pattern orchestrated by Positivity domain. No distributed transactions. The saga persists state at each step, cancels work order first (via workexec), then reverses payment (via billing), with explicit failure states and retry logic. Idempotency is guaranteed via idempotency keys.
- **Alternatives considered:**
  - **Option A (Chosen):** Persisted saga with sequential steps
    - Pros: Resilient to failures, no distributed transaction coordinator, clear failure recovery
    - Cons: More complex than synchronous orchestration
  - **Option B:** Two-phase commit across domains
    - Pros: Immediate consistency
    - Cons: Brittle, requires coordinator, blocks on any system failure
  - **Option C:** Eventually consistent events only (no orchestration)
    - Pros: Fully decoupled
    - Cons: Cannot guarantee order (work then payment), no deterministic outcome
- **Reasoning and evidence:**
  - Order cancellation spans multiple domains with different failure modes
  - Work order cancellation must precede payment reversal (minimize revenue loss)
  - Network failures and system outages require recovery mechanism
  - Saga pattern is proven for long-running business processes
  - Persisted state enables restart from last successful step
  - Industry standard: distributed saga (as opposed to 2PC)
- **Architectural implications:**
  - **Components affected:**
    - Order service: Orchestrates saga, persists saga state
    - Workexec domain: Exposes cancel work order API
    - Billing domain: Exposes reverse payment API
    - Database: Saga state table with step tracking
  - **Saga workflow:**
    ```
    1. Persist cancellation request with idempotency key
    2. Transition order to CANCEL_REQUESTED
    3. Step 1: Cancel work order (if exists)
       - Call POST /api/v1/work-orders/{workOrderId}/cancel
       - If success: mark step complete, transition to WORKORDER_CANCELLED
       - If failure: mark step failed, transition to CANCEL_FAILED_WORKEXEC
    4. Step 2: Reverse payment (if exists)
       - Call POST /api/v1/payments/{paymentId}/reverse
       - If success: mark step complete, transition to PAYMENT_REVERSED
       - If failure: mark step failed, transition to CANCEL_FAILED_BILLING
    5. Finalize: transition order to CANCELLED
    6. Emit cancellation complete event
    ```
  - **Database schema:**
    ```sql
    CREATE TABLE order_cancellation_saga (
      id UUID PRIMARY KEY,
      order_id UUID NOT NULL UNIQUE REFERENCES "order"(id),
      idempotency_key VARCHAR(255) NOT NULL UNIQUE,
      cancellation_reason VARCHAR(100) NOT NULL,
      correlation_id UUID NOT NULL,
      requested_by UUID NOT NULL,
      requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      current_step VARCHAR(50) NOT NULL, -- CANCEL_WORK, REVERSE_PAYMENT, FINALIZE
      workorder_cancel_status VARCHAR(50), -- SUCCESS, FAILED, NOT_APPLICABLE
      payment_reverse_status VARCHAR(50), -- SUCCESS, FAILED, NOT_APPLICABLE
      completed_at TIMESTAMPTZ,
      failure_reason TEXT
    );
    
    CREATE INDEX idx_saga_order ON order_cancellation_saga(order_id);
    CREATE INDEX idx_saga_idempotency ON order_cancellation_saga(idempotency_key);
    ```
  - **Idempotency handling:**
    ```java
    @Transactional
    public CancellationResponse cancelOrder(CancelOrderRequest request) {
        // Check idempotency
        Optional<OrderCancellationSaga> existing = 
            sagaRepo.findByIdempotencyKey(request.getIdempotencyKey());
        
        if (existing.isPresent()) {
            // Return current state (idempotent)
            return buildResponse(existing.get());
        }
        
        // Create new saga
        OrderCancellationSaga saga = new OrderCancellationSaga();
        saga.setOrderId(request.getOrderId());
        saga.setIdempotencyKey(request.getIdempotencyKey());
        saga.setCancellationReason(request.getReason());
        saga.setCorrelationId(UUID.randomUUID());
        saga.setRequestedBy(request.getUserId());
        saga.setCurrentStep("CANCEL_WORK");
        
        sagaRepo.save(saga);
        
        // Start saga execution (async or sync)
        sagaOrchestrator.execute(saga);
        
        return buildResponse(saga);
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all cancellations have saga records, steps execute in order, failures are properly recorded
  - **Query example:**
    ```sql
    -- Find cancellations with incomplete sagas
    SELECT s.id, s.order_id, s.current_step, s.requested_at, 
           NOW() - s.requested_at as age
    FROM order_cancellation_saga s
    WHERE s.completed_at IS NULL
      AND s.requested_at < NOW() - INTERVAL '1 hour'; -- aged incomplete
    
    -- Verify step ordering (work before payment)
    SELECT s.id, s.order_id, 
           s.workorder_cancel_status, 
           s.payment_reverse_status
    FROM order_cancellation_saga s
    WHERE s.payment_reverse_status = 'SUCCESS'
      AND s.workorder_cancel_status IN ('FAILED', 'PENDING'); -- invalid order
    ```
  - **Expected outcome:** Zero incomplete sagas aged > 1 hour, zero invalid step orderings
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create order_cancellation_saga table
    2. Implement saga orchestrator with step handlers
    3. Deploy cancellation API with saga creation
    4. Backfill historical cancelled orders (best-effort saga records)
  - **Backfill strategy:**
    ```sql
    -- Create synthetic saga records for historical cancellations
    INSERT INTO order_cancellation_saga 
      (id, order_id, idempotency_key, cancellation_reason, correlation_id, 
       requested_by, requested_at, current_step, completed_at, 
       workorder_cancel_status, payment_reverse_status)
    SELECT 
      gen_random_uuid(),
      o.id,
      CONCAT('backfill-', o.id),
      'HISTORICAL',
      gen_random_uuid(),
      o.cancelled_by,
      o.cancelled_at,
      'FINALIZE',
      o.cancelled_at,
      'NOT_APPLICABLE',
      'NOT_APPLICABLE'
    FROM "order" o
    WHERE o.status LIKE '%CANCEL%'
      AND NOT EXISTS (
        SELECT 1 FROM order_cancellation_saga s WHERE s.order_id = o.id
      );
    ```
- **Governance & owner recommendations:**
  - **Owner:** Positivity domain team with coordination from Workexec and Billing
  - **Monitoring:** Alert on incomplete sagas aged > 1 hour, high failure rates
  - **Review cadence:** Monthly review of saga completion rates and failure patterns
  - **SLA:** 95% of sagas complete within 5 minutes

### DECISION-POSITIVITY-002 — Work Order Cancellation Before Payment Reversal

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-POSITIVITY-002)
- **Decision:** The cancellation saga must cancel work order first, then reverse payment. This ordering minimizes revenue loss: if work cancellation fails but payment reverses, customer keeps money but work continues (bad). If work cancels but payment reversal fails, work stops and manual refund follows (better).
- **Alternatives considered:**
  - **Option A (Chosen):** Work first, then payment
    - Pros: Minimizes revenue loss, work stoppage is immediate
    - Cons: Customer may wait for refund if payment reversal fails
  - **Option B:** Payment first, then work
    - Pros: Customer satisfaction (immediate refund)
    - Cons: Risk of payment reversal success but work cancellation failure (loses revenue)
  - **Option C:** Parallel execution (both at same time)
    - Pros: Fastest completion
    - Cons: Cannot handle partial failures gracefully, ambiguous outcome
- **Reasoning and evidence:**
  - Business priority: prevent revenue loss over customer refund speed
  - Work order cancellation has stricter gates (irreversible work blocks cancellation)
  - Payment reversal is usually successful (void/refund are standard operations)
  - Manual refund process exists for payment reversal failures
  - Industry standard: complete service cancellation before financial reversal
- **Architectural implications:**
  - **Saga step order is enforced by state machine**
  - **Components affected:**
    - Saga orchestrator: Enforces sequential execution
    - Failure handling: Different terminal states based on which step fails
  - **State transitions:**
    ```
    CANCEL_REQUESTED
    ↓
    WORKORDER_CANCEL_ATTEMPT
    ↓
    ├→ WORKORDER_CANCELLED (success) → PAYMENT_REVERSE_ATTEMPT
    │  ↓
    │  ├→ PAYMENT_REVERSED (success) → CANCELLED (complete)
    │  └→ CANCEL_FAILED_BILLING (fail) → CANCEL_REQUIRES_MANUAL_REVIEW
    │
    └→ CANCEL_FAILED_WORKEXEC (fail, stop saga)
    ```
  - **Failure scenarios:**
    - Work fails, payment not attempted: `CANCEL_FAILED_WORKEXEC` (order stays active, user notified)
    - Work succeeds, payment fails: `CANCEL_FAILED_BILLING` (manual refund required, work stopped)
- **Auditor-facing explanation:**
  - **What to inspect:** Verify payment reversal never happens before work cancellation
  - **Query example:**
    ```sql
    -- Find payment reversals without work cancellation (invalid ordering)
    SELECT s.id, s.order_id, 
           s.workorder_cancel_status,
           s.payment_reverse_status
    FROM order_cancellation_saga s
    WHERE s.payment_reverse_status = 'SUCCESS'
      AND (s.workorder_cancel_status IS NULL 
           OR s.workorder_cancel_status NOT IN ('SUCCESS', 'NOT_APPLICABLE'));
    ```
  - **Expected outcome:** Zero records (ordering invariant holds)
- **Migration & backward-compatibility notes:**
  - **No data migration required** (design decision for new implementation)
  - **Documentation:** Update operational runbooks with ordering rationale
- **Governance & owner recommendations:**
  - **Owner:** Positivity domain team
  - **Policy:** Step ordering is immutable without architecture review
  - **Exception handling:** Manual refund process for payment reversal failures

### DECISION-POSITIVITY-003 — Idempotent Cancellation with Idempotency Keys

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-POSITIVITY-003)
- **Decision:** Cancellation requests include a client-generated idempotency key. The backend uses this key to deduplicate requests: if a saga already exists for the key, return current state without re-executing. Idempotency keys are unique per cancellation attempt (not per order).
- **Alternatives considered:**
  - **Option A (Chosen):** Client-provided idempotency key with server deduplication
    - Pros: Safe retries, no duplicate side effects, clear client control
    - Cons: Requires client key generation logic
  - **Option B:** Server-generated idempotency based on order ID
    - Pros: Simpler client
    - Cons: Cannot distinguish retries from new attempts, multiple cancellation attempts not supported
  - **Option C:** No idempotency (check order status before call)
    - Pros: Simplest implementation
    - Cons: Race conditions, duplicate cancellations, complex client logic
- **Reasoning and evidence:**
  - Network failures cause request retries without guaranteed response delivery
  - Duplicate cancellation requests cause duplicate work order cancellations and payment reversals
  - Client-controlled idempotency keys support retry-after-failure scenarios
  - Server deduplication is standard pattern for non-idempotent operations
  - Industry standard: Stripe, AWS use idempotency keys for financial operations
- **Architectural implications:**
  - **Components affected:**
    - Cancellation API: Validates and deduplicates by idempotency key
    - Saga table: Unique constraint on idempotency_key
    - Client: Generates UUID for each cancellation attempt
  - **API contract:**
    ```json
    // POST /orders/{orderId}/cancel
    {
      "idempotencyKey": "uuid-generated-by-client",
      "reason": "CUSTOMER_REQUEST",
      "requestedBy": "user-id"
    }
    
    // Response (200 OK - new or existing saga)
    {
      "orderId": "ord-123",
      "sagaId": "saga-456",
      "status": "CANCEL_REQUESTED",
      "message": "Cancellation in progress"
    }
    
    // Response (200 OK - idempotent retry)
    {
      "orderId": "ord-123",
      "sagaId": "saga-456",
      "status": "CANCELLED",
      "message": "Order was previously cancelled",
      "completedAt": "2026-01-19T17:00:00Z"
    }
    ```
  - **Deduplication logic:**
    ```java
    Optional<OrderCancellationSaga> saga = 
        sagaRepo.findByIdempotencyKey(request.getIdempotencyKey());
    
    if (saga.isPresent()) {
        // Idempotent response
        log.info("Duplicate cancellation request for idempotency key {}", 
                 request.getIdempotencyKey());
        return buildResponse(saga.get());
    }
    
    // New cancellation...
    ```
  - **Client key generation:**
    ```javascript
    // Generate idempotency key once, reuse on retry
    const idempotencyKey = crypto.randomUUID();
    
    async function cancelOrder(orderId) {
      try {
        const response = await api.post(`/orders/${orderId}/cancel`, {
          idempotencyKey,
          reason: 'CUSTOMER_REQUEST'
        });
        return response;
      } catch (error) {
        if (error.status === 500 || error.status === 503) {
          // Safe to retry with same idempotency key
          return retryWithBackoff(() => cancelOrder(orderId));
        }
        throw error;
      }
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no duplicate sagas for same idempotency key, idempotent requests return existing state
  - **Query example:**
    ```sql
    -- Find duplicate idempotency keys (should be zero)
    SELECT idempotency_key, COUNT(*) as count
    FROM order_cancellation_saga
    GROUP BY idempotency_key
    HAVING COUNT(*) > 1;
    ```
  - **Expected outcome:** Zero duplicate idempotency keys
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add idempotency_key column with unique constraint
    2. Update API to require idempotency key
    3. Update clients to generate and pass keys
    4. Backfill historical sagas with synthetic keys
  - **Backfill:** Historical sagas get synthetic keys (CONCAT('backfill-', order_id))
- **Governance & owner recommendations:**
  - **Owner:** Positivity domain team
  - **Documentation:** Document idempotency key generation requirements for clients
  - **Monitoring:** Alert on high rate of idempotent retries (may indicate network issues)

### DECISION-POSITIVITY-004 — Product Detail View with Graceful Degradation

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-POSITIVITY-004)
- **Decision:** Product detail endpoint aggregates data from Catalog, Pricing, and Inventory services. If downstream services fail, return HTTP 200 with explicit status metadata (OK/UNAVAILABLE/STALE) and null values for unavailable components. Only return HTTP 404 if product not found in Catalog. Never emit "0" for price/quantity when service is unavailable.
- **Alternatives considered:**
  - **Option A (Chosen):** Graceful degradation with explicit status
    - Pros: Resilient to downstream failures, clear UI contract, no misrepresentation
    - Cons: More complex response schema
  - **Option B:** Fail-fast on any downstream failure (return 503)
    - Pros: Simple implementation
    - Cons: Brittle, entire product detail unavailable if pricing service down
  - **Option C:** Return 200 with zeros for unavailable data
    - Pros: Simple client handling
    - Cons: Misleading (zero != unavailable), UI cannot distinguish errors
- **Reasoning and evidence:**
  - Product browse/quote must work even if pricing or inventory systems are down
  - Displaying "$0.00" when price is unknown misleads customers
  - Showing "0 in stock" when inventory system is down creates false scarcity
  - Explicit status allows UI to show appropriate messages ("Price unavailable")
  - Industry pattern: Netflix, Amazon gracefully degrade features during outages
- **Architectural implications:**
  - **Components affected:**
    - Product detail API: Fan-out calls to Catalog, Pricing, Inventory
    - Response builder: Composes response with status metadata
    - Frontend: Handles status-aware rendering
  - **Response schema:**
    ```json
    {
      "productId": "prod-12345",
      "description": "Premium Oil Filter",
      "specifications": [
        {"name": "Thread Size", "value": "3/4-16"},
        {"name": "Micron Rating", "value": "25"}
      ],
      "pricing": {
        "status": "OK",
        "asOf": "2026-01-19T17:00:00Z",
        "msrp": 24.99,
        "storePrice": 19.99,
        "currency": "USD"
      },
      "availability": {
        "status": "OK",
        "asOf": "2026-01-19T17:00:00Z",
        "onHandQuantity": 45,
        "availableToPromiseQuantity": 42,
        "leadTime": {
          "source": "DYNAMIC",
          "minDays": 0,
          "maxDays": 1,
          "confidence": "HIGH"
        }
      },
      "generatedAt": "2026-01-19T17:00:05Z"
    }
    
    // Example with degraded pricing
    {
      "productId": "prod-12345",
      "description": "Premium Oil Filter",
      "pricing": {
        "status": "UNAVAILABLE", // pricing service down
        "msrp": null,
        "storePrice": null,
        "currency": null
      },
      "availability": {
        "status": "OK",
        "onHandQuantity": 45
      }
    }
    ```
  - **Orchestration logic:**
    ```java
    public ProductDetailView getProductDetail(String productId, String locationId) {
        ProductDetailView view = new ProductDetailView();
        
        // 1. Catalog (required)
        try {
            Product product = catalogService.getProduct(productId);
            if (product == null) {
                throw new NotFoundException("Product not found");
            }
            view.setProductId(product.getId());
            view.setDescription(product.getDescription());
            view.setSpecifications(product.getSpecifications());
        } catch (Exception e) {
            throw new NotFoundException("Product not found");
        }
        
        // 2. Pricing (optional, graceful degradation)
        try {
            Price price = pricingService.getPrice(productId, locationId);
            view.setPricing(new PricingView("OK", price.getMsrp(), 
                                             price.getStorePrice(), price.getCurrency()));
        } catch (Exception e) {
            log.warn("Pricing unavailable for product {}", productId, e);
            view.setPricing(new PricingView("UNAVAILABLE", null, null, null));
        }
        
        // 3. Inventory (optional, graceful degradation)
        try {
            Inventory inv = inventoryService.getInventory(productId, locationId);
            view.setAvailability(new AvailabilityView("OK", inv.getOnHand(), inv.getAtp()));
        } catch (Exception e) {
            log.warn("Inventory unavailable for product {}", productId, e);
            view.setAvailability(new AvailabilityView("UNAVAILABLE", null, null));
        }
        
        view.setGeneratedAt(Instant.now());
        return view;
    }
    ```
  - **Frontend rendering:**
    ```typescript
    function renderPrice(pricing) {
      if (pricing.status === 'OK') {
        return `$${pricing.storePrice.toFixed(2)}`;
      } else if (pricing.status === 'STALE') {
        return `Price may not be current`;
      } else {
        return `Price unavailable`;
      }
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify product detail responses never show zeros when status != OK
  - **Monitoring query:**
    ```sql
    -- Log product detail responses with degraded components
    SELECT COUNT(*) as degraded_count,
           SUM(CASE WHEN pricing_status != 'OK' THEN 1 ELSE 0 END) as pricing_degraded,
           SUM(CASE WHEN availability_status != 'OK' THEN 1 ELSE 0 END) as avail_degraded
    FROM product_detail_response_log
    WHERE created_at >= NOW() - INTERVAL '1 hour';
    ```
  - **Expected outcome:** Degraded responses allowed, but prices/quantities must be null when status != OK
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Update product detail API to handle downstream failures
    2. Add status fields to response schema
    3. Update frontend to handle status-aware rendering
    4. Deploy with backward compatibility (old clients ignore status)
  - **Phased rollout:**
    - Phase 1: Backend returns status fields (old clients ignore)
    - Phase 2: New frontend uses status fields
    - Phase 3: Deprecate old response format (remove support after transition period)
- **Governance & owner recommendations:**
  - **Owner:** Positivity domain team with coordination from Catalog, Pricing, Inventory
  - **SLA:** Product detail endpoint must have 99.9% availability even if downstream services degrade
  - **Monitoring:** Track degradation rate by service, alert on sustained outages

### DECISION-POSITIVITY-005 — Location-Scoped Pricing and Availability

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-POSITIVITY-005)
- **Decision:** Product detail endpoint requires `location_id` as a mandatory query parameter. Pricing (store price) and availability (on-hand, ATP) are location-scoped. Missing or invalid location_id returns HTTP 400 Bad Request. Catalog data (description, specs) is location-agnostic.
- **Alternatives considered:**
  - **Option A (Chosen):** Required location_id with validation
    - Pros: Explicit contract, clear business rules, prevents misuse
    - Cons: Requires frontend to always provide location
  - **Option B:** Optional location_id with global defaults
    - Pros: Simpler for browse-only scenarios
    - Cons: Ambiguous semantics, risk of showing wrong prices/availability
  - **Option C:** Separate endpoints (catalog-only vs full detail)
    - Pros: Clear separation of concerns
    - Cons: More endpoints to maintain, duplicate logic
- **Reasoning and evidence:**
  - Pricing varies by location (regional pricing, promotions)
  - Inventory is tracked per location (cannot show global stock)
  - Showing wrong location's price/stock causes customer service issues
  - POS frontend always has location context (selected store)
  - Explicit validation prevents API misuse and data quality issues
- **Architectural implications:**
  - **Components affected:**
    - Product detail API: Validates location_id parameter
    - Pricing service: Receives location_id for price lookup
    - Inventory service: Receives location_id for stock lookup
  - **API contract:**
    ```
    GET /api/v1/products/{productId}/detail?location_id={locationId}
    
    Required parameters:
    - productId (path): UUID
    - location_id (query): UUID
    
    400 Bad Request if location_id missing or invalid UUID format
    404 Not Found if productId not found in catalog
    200 OK with location-scoped pricing/availability
    ```
  - **Validation logic:**
    ```java
    @GetMapping("/products/{productId}/detail")
    public ProductDetailView getDetail(
        @PathVariable String productId,
        @RequestParam(required = true) String location_id) {
        
        // Validate location_id format
        if (!isValidUUID(location_id)) {
            throw new BadRequestException("Invalid location_id format");
        }
        
        // Validate location exists (optional; depends on auth)
        if (!locationService.exists(location_id)) {
            throw new BadRequestException("Location not found");
        }
        
        return productDetailService.getDetail(productId, location_id);
    }
    ```
  - **Frontend usage:**
    ```typescript
    // Store location context in app state
    const selectedLocationId = useLocationContext();
    
    async function loadProductDetail(productId: string) {
      if (!selectedLocationId) {
        throw new Error("No location selected");
      }
      
      const response = await api.get(
        `/products/${productId}/detail?location_id=${selectedLocationId}`
      );
      return response.data;
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all product detail requests include location_id, no requests succeed without it
  - **Audit query:**
    ```sql
    -- Find product detail requests without location_id (should be zero)
    SELECT COUNT(*) as missing_location_count
    FROM api_request_log
    WHERE endpoint LIKE '%/products/%/detail'
      AND query_params NOT LIKE '%location_id=%'
      AND response_status = 200; -- should be 400, not 200
    ```
  - **Expected outcome:** Zero successful requests without location_id
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add location_id validation (initially as warning)
    2. Update frontend to always pass location_id
    3. Enable strict validation (400 for missing location_id)
  - **Communication:**
    - Notify frontend developers of required parameter
    - Update API documentation with examples
- **Governance & owner recommendations:**
  - **Owner:** Positivity domain team
  - **Policy:** location_id requirement is immutable (part of core business logic)
  - **Documentation:** Maintain examples showing proper location_id usage

### DECISION-POSITIVITY-006 — Null Values for Unavailable Pricing/Availability

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-POSITIVITY-006)
- **Decision:** When pricing or availability status is not "OK", all numeric fields (prices, quantities) must be null. Never emit "0" or default values. This prevents UI from misrepresenting unavailable data as "free" or "out of stock."
- **Alternatives considered:**
  - **Option A (Chosen):** Null for unavailable, numbers only when OK
    - Pros: Unambiguous, prevents misrepresentation, forces correct UI handling
    - Cons: Frontend must handle null checks
  - **Option B:** Emit zero for unavailable
    - Pros: Simpler frontend (no null checks)
    - Cons: "$0.00" misleads customers, "0 in stock" creates false scarcity
  - **Option C:** Omit fields entirely when unavailable
    - Pros: Clean response
    - Cons: Breaks client contracts, requires optional field handling
- **Reasoning and evidence:**
  - Zero is a valid price/quantity value (legitimately free, actually out of stock)
  - Unavailable data must be distinguishable from zero values
  - Showing "$0.00" when pricing system is down misleads customers (potential false advertising)
  - Showing "0 in stock" when inventory system is down creates artificial urgency
  - Null is standard JSON convention for "no value available"
  - TypeScript/Java nullable types make null handling explicit
- **Architectural implications:**
  - **Components affected:**
    - Response builder: Sets null for numeric fields when status != OK
    - Frontend: Null checks before rendering numeric values
    - API documentation: Documents nullable fields
  - **Response builder logic:**
    ```java
    if (pricing.getStatus() == Status.OK) {
        view.setPricing(new PricingView(
            "OK",
            pricing.getMsrp(),
            pricing.getStorePrice(),
            pricing.getCurrency()
        ));
    } else {
        view.setPricing(new PricingView(
            pricing.getStatus().name(),
            null, // msrp
            null, // storePrice
            null  // currency
        ));
    }
    ```
  - **Frontend null handling:**
    ```typescript
    interface PricingView {
      status: 'OK' | 'UNAVAILABLE' | 'STALE';
      msrp: number | null;
      storePrice: number | null;
      currency: string | null;
    }
    
    function renderPrice(pricing: PricingView): string {
      if (pricing.status !== 'OK' || pricing.storePrice === null) {
        return 'Price unavailable';
      }
      return `$${pricing.storePrice.toFixed(2)}`;
    }
    ```
  - **Schema documentation:**
    ```yaml
    ProductDetailView:
      pricing:
        msrp:
          type: number
          nullable: true
          description: "MSRP in currency; null when status != OK"
        storePrice:
          type: number
          nullable: true
          description: "Store price in currency; null when status != OK"
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no product detail responses emit zero prices/quantities when status != OK
  - **Compliance check:**
    ```sql
    -- Find responses with zeros when status not OK (policy violation)
    SELECT id, product_id, pricing_status, pricing_store_price,
           availability_status, availability_on_hand
    FROM product_detail_response_log
    WHERE (pricing_status != 'OK' AND pricing_store_price = 0)
       OR (availability_status != 'OK' AND availability_on_hand = 0);
    ```
  - **Expected outcome:** Zero policy violations
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Update response builder to emit nulls (not zeros)
    2. Update frontend to handle nulls gracefully
    3. Add integration tests verifying null behavior
  - **Testing:**
    - Simulate pricing service failure, verify nulls
    - Simulate inventory service failure, verify nulls
    - Verify legitimate zero prices/quantities still work (status=OK)
- **Governance & owner recommendations:**
  - **Owner:** Positivity domain team with Product/UX review
  - **Policy:** Null convention is immutable (core contract requirement)
  - **Documentation:** Document null handling requirements in API guide

### DECISION-POSITIVITY-007 — Staleness Metadata with Component Timestamps

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-POSITIVITY-007)
- **Decision:** Product detail responses include `generatedAt` timestamp (when response was composed) and optional `asOf` timestamps for pricing and availability (when component data was last updated). Frontend can display staleness warnings based on these timestamps.
- **Alternatives considered:**
  - **Option A (Chosen):** Timestamps with optional staleness warnings
    - Pros: Transparent to users, enables cache diagnostics, supports staleness UX
    - Cons: Requires clock synchronization across services
  - **Option B:** No timestamps (rely on HTTP cache headers only)
    - Pros: Simpler response
    - Cons: Frontend cannot detect or communicate staleness
  - **Option C:** Binary fresh/stale flag only
    - Pros: Simple boolean logic
    - Cons: Loses granularity, cannot debug cache issues
- **Reasoning and evidence:**
  - Customers deserve to know if displayed information is current
  - Stale pricing can lead to checkout surprises and cart abandonment
  - Stale inventory can lead to backorders and customer frustration
  - Timestamps enable debugging cache misses and staleness issues
  - Regulatory consideration: displaying outdated prices may be misleading
  - Industry pattern: financial data displays timestamps ("as of" disclaimers)
- **Architectural implications:**
  - **Components affected:**
    - Pricing service: Returns asOf timestamp with price data
    - Inventory service: Returns asOf timestamp with stock data
    - Response builder: Includes generatedAt and component asOf
    - Frontend: Displays staleness warnings based on age
  - **Response schema:**
    ```json
    {
      "generatedAt": "2026-01-19T17:10:30Z",
      "pricing": {
        "status": "OK",
        "asOf": "2026-01-19T17:10:15Z", // price last updated 15 sec ago
        "storePrice": 19.99
      },
      "availability": {
        "status": "STALE",
        "asOf": "2026-01-19T16:50:00Z", // inventory last updated 20 min ago
        "onHandQuantity": null // null because stale
      }
    }
    ```
  - **Staleness detection:**
    ```java
    public PricingView getPricing(String productId, String locationId) {
        Price price = pricingService.getPrice(productId, locationId);
        
        Duration age = Duration.between(price.getAsOf(), Instant.now());
        String status;
        
        if (age.toMinutes() < 5) {
            status = "OK";
        } else if (age.toMinutes() < 60) {
            status = "STALE";
        } else {
            status = "UNAVAILABLE"; // too old to trust
        }
        
        return new PricingView(status, price.getAsOf(), 
                               status.equals("OK") ? price.getAmount() : null);
    }
    ```
  - **Frontend staleness warning:**
    ```typescript
    function showStalenessWarning(asOf: string): boolean {
      const age = Date.now() - new Date(asOf).getTime();
      return age > 5 * 60 * 1000; // > 5 minutes old
    }
    
    <div v-if="showStalenessWarning(pricing.asOf)">
      ⚠️ Price may not be current
    </div>
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify timestamps are accurate, staleness thresholds are applied consistently
  - **Monitoring:**
    ```sql
    -- Track staleness rates
    SELECT 
      COUNT(*) as total_requests,
      SUM(CASE WHEN pricing_status = 'STALE' THEN 1 ELSE 0 END) as stale_pricing,
      SUM(CASE WHEN availability_status = 'STALE' THEN 1 ELSE 0 END) as stale_avail,
      AVG(EXTRACT(EPOCH FROM (response_generated_at - pricing_as_of))) as avg_pricing_age_sec
    FROM product_detail_response_log
    WHERE created_at >= NOW() - INTERVAL '1 hour';
    ```
  - **Expected outcome:** Staleness rates < 5%, average age < 60 seconds under normal operation
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add generatedAt and asOf fields to response schema
    2. Update downstream services to return asOf timestamps
    3. Deploy staleness detection logic
    4. Update frontend to display staleness warnings
  - **Backward compatibility:** Timestamps are additive (old clients ignore)
- **Governance & owner recommendations:**
  - **Owner:** Positivity domain team with Product/UX
  - **Policy:** Define staleness thresholds (5 min OK, 60 min stale, > 60 min unavailable)
  - **Review cadence:** Quarterly review of staleness rates and thresholds

### DECISION-POSITIVITY-008 — Caching Strategy with Short TTLs

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-POSITIVITY-008)
- **Decision:** Product detail responses are cached with short TTLs (15 seconds aggregated, component-specific TTLs per service). Cache keys include productId and locationId. Stale-while-revalidate pattern refreshes cache in background.
- **Alternatives considered:**
  - **Option A (Chosen):** Short TTL with stale-while-revalidate
    - Pros: Balance between freshness and performance, reduces load
    - Cons: More complex caching logic
  - **Option B:** No caching (always fetch fresh)
    - Pros: Always current data
    - Cons: High latency, poor scalability, expensive
  - **Option C:** Long TTL (5+ minutes)
    - Pros: Best performance
    - Cons: Stale data, poor customer experience
- **Reasoning and evidence:**
  - Product browsing has high read:write ratio (99:1 or higher)
  - Pricing and inventory change frequency varies (pricing: hours, inventory: minutes)
  - 15 seconds is imperceptible to users but significantly reduces backend load
  - Stale-while-revalidate keeps responses fast while ensuring eventual freshness
  - Industry pattern: e-commerce sites cache product data with short TTLs
- **Architectural implications:**
  - **Components affected:**
    - API layer: Implements caching logic
    - Cache store: Redis with TTL support
    - Cache key strategy: Include locationId for proper scoping
  - **Cache key structure:**
    ```
    product_detail:{productId}:{locationId}
    TTL: 15 seconds
    ```
  - **Stale-while-revalidate:**
    ```java
    public ProductDetailView getDetailWithCache(String productId, String locationId) {
        String cacheKey = String.format("product_detail:%s:%s", productId, locationId);
        
        // Try cache first
        ProductDetailView cached = redis.get(cacheKey, ProductDetailView.class);
        if (cached != null && isFresh(cached)) {
            return cached;
        }
        
        // Stale but usable? Return stale + trigger background refresh
        if (cached != null && isStale(cached)) {
            asyncRefresh(productId, locationId);
            return cached;
        }
        
        // Cache miss: fetch fresh and cache
        ProductDetailView fresh = fetchFresh(productId, locationId);
        redis.set(cacheKey, fresh, 15, TimeUnit.SECONDS);
        return fresh;
    }
    ```
  - **HTTP cache headers:**
    ```
    Cache-Control: max-age=15, stale-while-revalidate=30
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify cache hit rates, TTLs are respected
  - **Monitoring:**
    ```sql
    -- Cache performance metrics
    SELECT 
      COUNT(*) as total_requests,
      SUM(CASE WHEN cache_hit = true THEN 1 ELSE 0 END) as cache_hits,
      ROUND(100.0 * SUM(CASE WHEN cache_hit = true THEN 1 ELSE 0 END) / COUNT(*), 2) as hit_rate_pct,
      AVG(response_time_ms) as avg_response_ms
    FROM product_detail_request_log
    WHERE created_at >= NOW() - INTERVAL '1 hour';
    ```
  - **Expected outcome:** Cache hit rate > 80%, response time < 100ms for hits
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Deploy Redis cache infrastructure
    2. Implement caching layer (initially bypass with feature flag)
    3. Test cache behavior with load testing
    4. Enable caching with monitoring
  - **Rollback:** Feature flag to disable caching if issues arise
- **Governance & owner recommendations:**
  - **Owner:** Positivity domain team with Infrastructure/SRE
  - **Monitoring:** Alert on cache hit rate < 70%, cache unavailability
  - **Review cadence:** Monthly review of TTL effectiveness and adjustment

### DECISION-POSITIVITY-009 — Fail-Fast Only for Missing Product

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-POSITIVITY-009)
- **Decision:** Return HTTP 404 Not Found only if product does not exist in Catalog (authoritative source). All other failures (pricing unavailable, inventory unavailable) return HTTP 200 with degraded response. This allows browse/quote to work even when dependent services are down.
- **Alternatives considered:**
  - **Option A (Chosen):** 404 only for missing product, 200 with degradation otherwise
    - Pros: Resilient, clear error semantics, maximizes availability
    - Cons: Client must check status fields for completeness
  - **Option B:** 404 for missing product, 503 for any service unavailable
    - Pros: Clear error distinction
    - Cons: Entire product detail unavailable if pricing/inventory down
  - **Option C:** Always return 200 (even for missing product)
    - Pros: Simplest HTTP semantics
    - Cons: Loses semantic meaning of 404, harder to debug
- **Reasoning and evidence:**
  - Product existence is authoritative and binary (exists or not)
  - Missing product is a permanent error (404 semantics)
  - Pricing/inventory unavailability is transient (service outage, network issue)
  - Returning 503 for transient issues blocks entire product detail
  - Industry pattern: return lowest-common-denominator success with degradation metadata
- **Architectural implications:**
  - **Components affected:**
    - Product detail orchestrator: Only throws 404 for catalog miss
    - Error handling: Catches and converts other errors to degradation
  - **Logic:**
    ```java
    try {
        Product product = catalogService.getProduct(productId);
        if (product == null) {
            throw new NotFoundException("Product not found"); // 404
        }
        // ... compose response with optional pricing/availability
        return ResponseEntity.ok(view); // 200 with possible degradation
    } catch (NotFoundException e) {
        return ResponseEntity.notFound().build(); // 404
    }
    ```
  - **Error mapping:**
    - Catalog: Product not found → 404
    - Pricing: Service unavailable → 200 with pricing.status=UNAVAILABLE
    - Inventory: Service unavailable → 200 with availability.status=UNAVAILABLE
- **Auditor-facing explanation:**
  - **What to inspect:** Verify 404 only for missing products, all other failures return 200
  - **Audit query:**
    ```sql
    -- Verify 404 responses are only for missing products
    SELECT endpoint, query_params, response_status, error_message
    FROM api_request_log
    WHERE endpoint LIKE '%/products/%/detail'
      AND response_status = 404
      AND error_message NOT LIKE '%not found%'; -- should be empty
    ```
  - **Expected outcome:** All 404s have "not found" error message
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Update error handling to only throw 404 for catalog miss
    2. Convert other errors to degradation (status fields)
    3. Update tests to verify error mapping
  - **No data migration required**
- **Governance & owner recommendations:**
  - **Owner:** Positivity domain team
  - **Policy:** 404 semantics reserved for missing product only
  - **Documentation:** Document error codes and degradation strategy in API guide

### DECISION-POSITIVITY-010 — Dynamic Lead Time Overrides Static Lead Time

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-POSITIVITY-010)
- **Decision:** Product detail response includes lead time from two sources: Catalog (static hint) and Inventory (dynamic, best-effort). If Inventory provides dynamic lead time, it overrides Catalog static hint in the response. Frontend displays what backend provides; precedence is backend responsibility.
- **Alternatives considered:**
  - **Option A (Chosen):** Backend precedence (dynamic overrides static)
    - Pros: Single source of truth, clear semantics, backend control
    - Cons: Requires backend logic to merge sources
  - **Option B:** Return both, let frontend choose
    - Pros: Flexible client behavior
    - Cons: Inconsistent precedence across clients, complex client logic
  - **Option C:** Inventory-only (no static fallback)
    - Pros: Simplest
    - Cons: No lead time when Inventory unavailable
- **Reasoning and evidence:**
  - Dynamic lead time reflects current supply chain state (more accurate)
  - Static lead time provides fallback when dynamic unavailable
  - Backend is authoritative for business rules (precedence is business rule)
  - Frontend should not implement business logic (display layer)
  - Consistent precedence across all clients ensures predictable behavior
- **Architectural implications:**
  - **Components affected:**
    - Lead time resolver: Merges catalog and inventory lead times
    - Response builder: Includes resolved lead time in response
  - **Resolution logic:**
    ```java
    public LeadTimeView resolveLeadTime(Product product, Inventory inventory) {
        // Prefer dynamic from inventory
        if (inventory != null && inventory.getDynamicLeadTime() != null) {
            return new LeadTimeView(
                "DYNAMIC",
                inventory.getDynamicLeadTime().getMinDays(),
                inventory.getDynamicLeadTime().getMaxDays(),
                inventory.getDynamicLeadTime().getAsOf(),
                inventory.getDynamicLeadTime().getConfidence()
            );
        }
        
        // Fallback to static from catalog
        if (product.getStaticLeadTime() != null) {
            return new LeadTimeView(
                "STATIC",
                product.getStaticLeadTime().getMinDays(),
                product.getStaticLeadTime().getMaxDays(),
                null, // no asOf for static
                "LOW" // static has low confidence
            );
        }
        
        return null; // no lead time available
    }
    ```
  - **Response schema:**
    ```json
    {
      "availability": {
        "status": "OK",
        "leadTime": {
          "source": "DYNAMIC",
          "minDays": 0,
          "maxDays": 1,
          "asOf": "2026-01-19T17:00:00Z",
          "confidence": "HIGH"
        }
      }
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify dynamic lead time is preferred when available
  - **Audit query:**
    ```sql
    -- Verify dynamic lead times are used when available
    SELECT 
      COUNT(*) as total,
      SUM(CASE WHEN lead_time_source = 'DYNAMIC' THEN 1 ELSE 0 END) as dynamic_count,
      SUM(CASE WHEN lead_time_source = 'STATIC' THEN 1 ELSE 0 END) as static_count,
      SUM(CASE WHEN lead_time_source IS NULL THEN 1 ELSE 0 END) as none_count
    FROM product_detail_response_log
    WHERE created_at >= NOW() - INTERVAL '1 hour';
    ```
  - **Expected outcome:** Dynamic lead times used when inventory service available
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Implement lead time resolution logic
    2. Update response builder to include source field
    3. Test with and without inventory service
  - **Backward compatibility:** Lead time field is optional (clients handle null)
- **Governance & owner recommendations:**
  - **Owner:** Positivity domain team with input from Inventory and Product
  - **Policy:** Dynamic precedence is immutable (business rule)
  - **Review cadence:** Annual review of lead time accuracy and customer satisfaction

## End

End of document.
