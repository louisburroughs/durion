# ORDER_DOMAIN_NOTES.md

## Summary

This document provides non-normative, verbose rationale and decision logs for the Order domain within the Durion POS system, focusing primarily on order cancellation orchestration. It supports auditors, architects, and engineers by documenting design choices, alternatives considered, architectural implications, and migration notes for key decisions referenced in the normative AGENT_GUIDE.md.

## Completed items

- [x] Linked each Decision ID to a detailed rationale
- [x] Documented alternatives considered for order cancellation workflow
- [x] Provided architectural implications for state machine and orchestration
- [x] Included auditor-facing explanations with example queries
- [x] Documented migration and backward-compatibility considerations

## Decision details

<a id="decision-order-001---order-domain-as-cancellation-orchestrator"></a>
### DECISION-ORDER-001 — Order Domain as Cancellation Orchestrator

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-001)
- **Decision:** The Order domain is the authoritative orchestrator for order cancellation logic. It owns the cancellation policy (what can/cannot be cancelled), coordinates downstream systems (Payment, Work Execution), manages the order state machine transitions, and creates immutable audit records for every cancellation attempt.
- **Alternatives considered:**
  - **Option A (Chosen):** Order domain orchestrates cancellation
    - Pros: Single source of truth for order lifecycle, clear ownership of cancellation policy, simplified audit trail, consistent state management
    - Cons: Requires coordination interfaces with Payment and Work Execution domains
  - **Option B:** Distributed cancellation (each domain handles independently)
    - Pros: Loose coupling, domain autonomy
    - Cons: Inconsistent cancellation semantics, split audit trails, race conditions, no single point for policy enforcement
  - **Option C:** Saga/process manager pattern with separate orchestrator service
    - Pros: Pure separation of concerns, reusable orchestration patterns
    - Cons: Additional service complexity, over-engineering for current scope, delayed delivery
- **Reasoning and evidence:**
  - Order domain is already the system of record for order lifecycle and status
  - Cancellation is fundamentally an order lifecycle transition
  - Business rules (work status blocking, payment settlement handling) span multiple domains and require orchestration
  - Single orchestrator simplifies troubleshooting and maintains consistent audit trail
  - Aligns with current POS architecture where domains own their lifecycle logic
- **Architectural implications:**
  - **Components affected:**
    - Order service: Implements cancellation orchestration logic
    - Payment domain: Exposes query and void/refund requirement APIs
    - Work Execution domain: Exposes status query and rollback/cancel APIs
    - Order API: Exposes cancellation endpoint to frontend
  - **Integration pattern:**
    ```
    UI/Frontend
        ↓ (cancel request)
    Order Domain (Orchestrator)
        ├→ Work Execution (check status, attempt rollback)
        └→ Payment (check settlement, attempt void)
        ↓ (persist state + audit)
    Order Domain (response)
        ↓ (status + metadata)
    UI/Frontend
    ```
  - **State machine:**
    - Order domain owns state transitions: `OPEN` → `CANCELLING` → `CANCELLED` (or variants)
    - Downstream systems report outcomes; Order domain decides final state
  - **Database:**
    - `order` table includes cancellation-related status values
    - `cancellation_record` table stores audit trail with downstream outcomes
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all cancellation attempts have corresponding orchestration logs and audit records
  - **Query example:**
    ```sql
    -- Find orders cancelled without audit records
    SELECT o.order_id, o.status, o.updated_at
    FROM "order" o
    WHERE o.status LIKE '%CANCEL%'
      AND NOT EXISTS (
        SELECT 1 FROM cancellation_record cr
        WHERE cr.order_id = o.order_id
      );
    ```
  - **Expected outcome:** Zero orders with cancellation status but no audit record
  - **Audit trail completeness:**
    - Every cancellation attempt (success or failure) must have a `cancellation_record`
    - Audit record includes downstream outcomes, correlation IDs, and timestamps
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Define Order domain cancellation API contract
    2. Implement orchestration logic with downstream integrations
    3. Create audit record schema and persistence
    4. Deploy Order domain changes with feature flag
    5. Update frontend to call Order domain (not individual domains)
    6. Enable feature flag and monitor
  - **Rollback strategy:**
    - Feature flag can disable new orchestration logic
    - Existing cancellation paths remain available during migration
  - **Data migration:**
    - Backfill `cancellation_record` for historical cancelled orders (best-effort)
    - Include migration metadata flag to distinguish backfilled vs. new records
- **Governance & owner recommendations:**
  - **Owner:** Order domain team with coordination from Payment and Work Execution teams
  - **Review cadence:** Quarterly review of cancellation policies and success rates
  - **Escalation:** Product team approval required for policy changes (e.g., adding new blocking conditions)

<a id="decision-order-002---work-status-blocking-rules-for-cancellation"></a>
### DECISION-ORDER-002 — Work Status Blocking Rules for Cancellation

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-002)
- **Decision:** Order cancellation is blocked when associated work is in states: `IN_PROGRESS`, `LABOR_STARTED`, `PARTS_ISSUED`, `MATERIALS_CONSUMED`, `COMPLETED`, `CLOSED`. These states indicate irreversible or completed work that cannot be rolled back without operational disruption or inventory complications.
- **Alternatives considered:**
  - **Option A (Chosen):** Block cancellation for irreversible work states
    - Pros: Prevents operational chaos, protects inventory integrity, clear policy
    - Cons: May frustrate users who want to cancel after work has started
  - **Option B:** Allow cancellation but require manual cleanup
    - Pros: Flexibility for users
    - Cons: Inconsistent state, inventory discrepancies, technician confusion, increased support burden
  - **Option C:** Allow cancellation with automatic work reversal
    - Pros: User convenience
    - Cons: Complex reversal logic, risk of incorrect inventory adjustments, safety concerns with partially completed work
- **Reasoning and evidence:**
  - Once labor starts, technician time has been committed and cannot be "undone"
  - Parts issued from inventory require physical return and restocking workflows
  - Materials consumed (fluids, supplies) cannot be reversed
  - Completed/closed work represents delivered value and invoicing commitments
  - Manual intervention by operations/service advisor is safer than automated reversal
  - Blocking at orchestration level prevents downstream data corruption
- **Architectural implications:**
  - **Components affected:**
    - Order cancellation orchestrator: Queries Work Execution for work status before proceeding
    - Work Execution domain: Exposes work status query API
    - Frontend: Displays reason for cancellation block
  - **Validation flow:**
    ```
    1. Order domain receives cancel request
    2. Query Work Execution: GET /work-orders/{workOrderId}/status
    3. If status IN blocking_list → return 400 with WORK_NOT_CANCELLABLE
    4. Else proceed with cancellation orchestration
    ```
  - **Blocking status list (configuration):**
    ```yaml
    blocking_work_statuses:
      - IN_PROGRESS
      - LABOR_STARTED
      - PARTS_ISSUED
      - MATERIALS_CONSUMED
      - COMPLETED
      - CLOSED
    ```
  - **Error response:**
    ```json
    {
      "errorCode": "WORK_NOT_CANCELLABLE",
      "message": "Order cannot be cancelled because work has started or been completed",
      "details": {
        "workOrderId": "wo-12345",
        "workStatus": "LABOR_STARTED"
      }
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no orders are cancelled when work is in blocking states
  - **Query example:**
    ```sql
    -- Find cancelled orders with work in blocking states at cancellation time
    SELECT o.order_id, o.status, wo.work_order_id, wo.status AS work_status, cr.created_at
    FROM "order" o
    JOIN work_order wo ON wo.order_id = o.order_id
    JOIN cancellation_record cr ON cr.order_id = o.order_id
    WHERE o.status LIKE '%CANCEL%'
      AND wo.status IN ('IN_PROGRESS', 'LABOR_STARTED', 'PARTS_ISSUED', 'MATERIALS_CONSUMED', 'COMPLETED', 'CLOSED')
      AND wo.updated_at <= cr.created_at;
    ```
  - **Expected outcome:** Zero records (all cancellations respected work blocking rules)
  - **Audit considerations:**
    - Check cancellation_record for work_status snapshot at cancellation time
    - Verify blocked attempts have audit records with blocking reason
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Identify any existing cancelled orders with work in blocking states
    2. Review and remediate data integrity issues (manual corrections)
    3. Deploy work status query integration
    4. Deploy blocking rule enforcement
    5. Update frontend error handling for WORK_NOT_CANCELLABLE
  - **Historical data cleanup:**
    - Query for orders cancelled with irreversible work
    - Coordinate with operations team to verify actual state
    - Document exceptions or data corrections
  - **Grace period:** Consider soft launch with warning-only mode before strict enforcement
- **Governance & owner recommendations:**
  - **Owner:** Order domain team with input from Work Execution and Operations
  - **Review cadence:** Semi-annual review of blocking status list
  - **Policy changes:** Require cross-functional approval (Product, Operations, Tech Lead)
  - **Monitoring:** Alert on high rate of cancellation blocks (may indicate UX issue)

<a id="decision-order-003---payment-settlement-handling-in-cancellation"></a>
### DECISION-ORDER-003 — Payment Settlement Handling in Cancellation

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-003)
- **Decision:** Order cancellation proceeds regardless of payment settlement state. For authorized/captured but unsettled payments, the system attempts automatic void. For settled payments, the order transitions to `CANCELLED_REQUIRES_REFUND` state and requires manual refund processing. No automatic refund execution is performed.
- **Alternatives considered:**
  - **Option A (Chosen):** Void unsettled, require manual refund for settled
    - Pros: Safe (no risk of double refund), clear manual approval gate, simple implementation
    - Cons: Manual refund delays customer resolution
  - **Option B:** Automatic refund for all cancellations
    - Pros: Faster customer resolution, better UX
    - Cons: Refund fraud risk, complex refund logic, accounting complications, chargebacks
  - **Option C:** Block cancellation if payment is settled
    - Pros: Forces proper workflow (refund first, then cancel)
    - Cons: Confusing UX, doesn't reflect business reality (order is logically cancelled even if payment isn't refunded yet)
- **Reasoning and evidence:**
  - Void (pre-settlement) is immediate and prevents funds transfer
  - Refund (post-settlement) requires accounting approval and fraud prevention checks
  - Business policy: manual review prevents accidental or fraudulent refunds
  - Settled payments may be days old; automatic refund without review is risky
  - Logical cancellation (order status) can occur independently of financial reversal timing
  - Operations team needs visibility into "cancelled but refund pending" orders
- **Architectural implications:**
  - **Components affected:**
    - Order orchestrator: Queries Payment domain for settlement status
    - Payment domain: Exposes payment status and void API
    - Order state machine: Includes `CANCELLED_REQUIRES_REFUND` status
    - Reporting/dashboards: Must surface orders awaiting refund
  - **Orchestration flow:**
    ```
    1. Order domain queries Payment: GET /payments/{paymentId}/status
    2. If payment status = AUTHORIZED or CAPTURED:
         a. Attempt void: POST /payments/{paymentId}/void
         b. If void succeeds → transition to CANCELLED
         c. If void fails → transition to CANCELLATION_FAILED
    3. If payment status = SETTLED:
         a. Transition to CANCELLED_REQUIRES_REFUND (no void attempt)
    4. If no payment or payment status = PENDING:
         a. Transition to CANCELLED (no action needed)
    ```
  - **State machine additions:**
    - `CANCELLED` — successful cancellation with void or no payment action needed
    - `CANCELLED_REQUIRES_REFUND` — cancellation completed, manual refund required
    - `CANCELLATION_FAILED` — cancellation attempted but void/rollback failed
  - **Database schema:**
    ```sql
    -- cancellation_record includes payment outcome
    CREATE TABLE cancellation_record (
      id UUID PRIMARY KEY,
      order_id UUID NOT NULL REFERENCES "order"(id),
      payment_void_status VARCHAR(50), -- 'VOIDED', 'VOID_FAILED', 'REFUND_REQUIRED', 'NOT_APPLICABLE'
      -- ... other fields
    );
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify payment handling follows policy (void for unsettled, manual refund for settled)
  - **Query example:**
    ```sql
    -- Find orders in CANCELLED_REQUIRES_REFUND with no refund record
    SELECT o.order_id, o.status, p.payment_id, p.status AS payment_status
    FROM "order" o
    JOIN payment p ON p.order_id = o.order_id
    WHERE o.status = 'CANCELLED_REQUIRES_REFUND'
      AND NOT EXISTS (
        SELECT 1 FROM refund_record r WHERE r.payment_id = p.payment_id
      )
      AND o.updated_at < NOW() - INTERVAL '7 days'; -- aged 7+ days
    ```
  - **Expected outcome:** Report for operations to process refunds
  - **Audit considerations:**
    - Track time from cancellation to refund completion
    - Monitor void success/failure rates
    - Alert on increasing backlog of orders awaiting refund
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add `CANCELLED_REQUIRES_REFUND` status to order schema
    2. Deploy Payment domain integration (status query, void API)
    3. Update cancellation orchestrator with payment handling logic
    4. Create operational dashboard for refund-pending orders
    5. Train operations team on manual refund workflow
  - **Historical data:**
    - Review existing cancelled orders with settled payments
    - Backfill status to `CANCELLED_REQUIRES_REFUND` where appropriate
  - **Operational process:**
    - Finance/operations team receives daily report of refund-required orders
    - Manual refund processed through payment processor admin interface
    - Refund record created in system (links to order and payment)
- **Governance & owner recommendations:**
  - **Owner:** Order domain team with coordination from Finance and Payment teams
  - **Review cadence:** Monthly review of refund processing times and backlog
  - **SLA:** Establish target time for manual refund processing (e.g., 3 business days)
  - **Escalation:** Alert if refund backlog exceeds threshold or SLA violations occur
  - **Future enhancement:** Consider semi-automatic refund with approval workflow (phase 2)

<a id="decision-order-004---cancellation-audit-record-immutability"></a>
### DECISION-ORDER-004 — Cancellation Audit Record Immutability

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-004)
- **Decision:** Every cancellation attempt creates an immutable `CancellationRecord` that captures user identity, timestamp, reason, comments, downstream action outcomes (work rollback status, payment void status), and correlation IDs. Records are never updated or deleted; retries or corrections create new records.
- **Alternatives considered:**
  - **Option A (Chosen):** Immutable audit records, one per attempt
    - Pros: Complete history, tamper-proof audit trail, simple compliance
    - Cons: Multiple records per order if retries occur
  - **Option B:** Mutable record, update on retry
    - Pros: Single record per order, simpler queries
    - Cons: Loses intermediate state history, not audit-compliant, risk of data loss
  - **Option C:** Event sourcing pattern
    - Pros: Full event history, replayable
    - Cons: Over-engineering for current needs, increased complexity
- **Reasoning and evidence:**
  - Audit trails must be immutable to satisfy compliance and legal requirements
  - Cancellation attempts may fail and be retried; each attempt is significant
  - Troubleshooting requires understanding what was tried and when
  - Downstream outcomes (work rollback, payment void) may differ between attempts
  - Immutable records prevent accidental or malicious modification
  - Industry standard for financial and operational audit logs
- **Architectural implications:**
  - **Components affected:**
    - Cancellation orchestrator: Creates new record for each attempt
    - Database: `cancellation_record` table with append-only pattern
    - UI: Displays latest cancellation record; optionally shows history
  - **Database schema:**
    ```sql
    CREATE TABLE cancellation_record (
      id UUID PRIMARY KEY,
      order_id UUID NOT NULL REFERENCES "order"(id),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      cancelled_by VARCHAR(255) NOT NULL, -- user identifier
      reason VARCHAR(50) NOT NULL,
      comments TEXT, -- max 2000 chars enforced at app layer
      work_rollback_status VARCHAR(50), -- 'ROLLED_BACK', 'ROLLBACK_FAILED', 'NOT_APPLICABLE'
      payment_void_status VARCHAR(50), -- 'VOIDED', 'VOID_FAILED', 'REFUND_REQUIRED', 'NOT_APPLICABLE'
      correlation_id UUID,
      work_correlation_id UUID,
      payment_correlation_id UUID,
      -- No UPDATE or DELETE allowed at DB policy level
    );
    
    -- Index for latest record queries
    CREATE INDEX idx_cancellation_record_order_created 
    ON cancellation_record(order_id, created_at DESC);
    ```
  - **Application logic:**
    - On cancellation attempt: INSERT new record (never UPDATE)
    - Retrieve latest record: `ORDER BY created_at DESC LIMIT 1`
    - Retrieve history: `ORDER BY created_at DESC` (all records)
  - **Database policies:**
    ```sql
    -- Prevent updates and deletes (PostgreSQL example)
    CREATE POLICY no_update ON cancellation_record FOR UPDATE USING (false);
    CREATE POLICY no_delete ON cancellation_record FOR DELETE USING (false);
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all cancellation records are immutable and chronologically consistent
  - **Query example:**
    ```sql
    -- Check for any updated records (should be impossible)
    SELECT id, order_id, created_at, updated_at
    FROM cancellation_record
    WHERE updated_at != created_at OR updated_at IS NULL;
    
    -- Verify chronological consistency
    SELECT order_id, COUNT(*) as attempt_count
    FROM cancellation_record
    GROUP BY order_id
    HAVING COUNT(*) > 1; -- orders with multiple attempts
    ```
  - **Expected outcome:**
    - Zero updated records (all records have created_at = updated_at or no updated_at)
    - List of orders with multiple attempts (normal for retries/failures)
  - **Audit compliance:**
    - Demonstrate records cannot be altered after creation
    - Show complete history for orders with multiple attempts
    - Provide evidence of user actions and outcomes
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create `cancellation_record` table with immutability constraints
    2. Backfill historical cancellation data (if available)
    3. Update cancellation orchestrator to create records on every attempt
    4. Deploy database policies to prevent updates/deletes
    5. Update UI to display latest record (with optional history view)
  - **Backfill strategy:**
    - Extract cancellation metadata from order history/events
    - Mark backfilled records with metadata flag
    - Accept incomplete data for historical records (best-effort)
  - **Testing:**
    - Verify attempt to UPDATE or DELETE record fails at database level
    - Verify multiple cancellation attempts create multiple records
    - Verify UI displays correct latest record
- **Governance & owner recommendations:**
  - **Owner:** Order domain team with oversight from Compliance/Legal
  - **Review cadence:** Annual audit trail review for compliance
  - **Retention policy:** Define retention period for cancellation records (e.g., 7 years for financial data)
  - **Access control:** Restrict cancellation_record table access to read-only for most users

<a id="decision-order-005---idempotent-cancellation-semantics"></a>
### DECISION-ORDER-005 — Idempotent Cancellation Semantics

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-005)
- **Decision:** Cancellation requests are idempotent. If an order is already cancelled, repeated cancellation requests return HTTP 200 with the current state and latest cancellation metadata, without creating duplicate side effects or downstream calls. If cancellation is in-progress, the system returns HTTP 409 Conflict.
- **Alternatives considered:**
  - **Option A (Chosen):** Idempotent success (200 for already-cancelled), conflict for in-progress
    - Pros: Safe retries, no duplicate side effects, clear in-progress indication
    - Cons: Requires state tracking and concurrency control
  - **Option B:** Return 400 Bad Request for already-cancelled
    - Pros: Explicit rejection of duplicate request
    - Cons: Breaks idempotency, complicates client retry logic, confusing UX
  - **Option C:** Always re-execute cancellation logic
    - Pros: Simple implementation
    - Cons: Duplicate downstream calls, risk of double-void/double-refund, audit noise
- **Reasoning and evidence:**
  - Network failures and user double-clicks are common; idempotency prevents issues
  - Cancellation involves financial actions (void/refund) that must not be duplicated
  - HTTP 409 Conflict is standard for "operation already in progress" scenarios
  - 200 OK for already-completed operation is RESTful idempotency pattern
  - Client can safely retry on network error without checking state first
  - Simplifies UI logic (no need for complex request deduplication)
- **Architectural implications:**
  - **Components affected:**
    - Cancellation orchestrator: Checks order status before proceeding
    - Order state machine: Includes in-progress state (e.g., `CANCELLING`)
    - Database: Uses optimistic locking or row-level locking for concurrency control
  - **Request handling flow:**
    ```
    1. Receive cancel request for order_id
    2. Acquire lock or check current status (with version)
    3. If status IN ('CANCELLED', 'CANCELLED_REQUIRES_REFUND'):
         → Return 200 with current state + latest cancellation record
         → Do NOT create new cancellation record
         → Do NOT call downstream systems
    4. If status = 'CANCELLING' (in-progress):
         → Return 409 Conflict with message "Cancellation already in progress"
    5. If status is cancellable:
         → Proceed with orchestration (transition to CANCELLING → final state)
    6. Release lock
    ```
  - **Concurrency control:**
    ```sql
    -- Optimistic locking approach
    UPDATE "order"
    SET status = 'CANCELLING', version = version + 1, updated_at = NOW()
    WHERE order_id = ?
      AND version = ?
      AND status IN ('OPEN', 'CONFIRMED', ...); -- cancellable states
    
    -- If zero rows updated → concurrent modification or already cancelled
    ```
  - **Response examples:**
    ```json
    // 200 OK - already cancelled
    {
      "orderId": "ord-123",
      "status": "CANCELLED",
      "message": "Order was previously cancelled",
      "cancellationId": "cancel-456",
      "cancelledBy": "user-789",
      "cancelledAt": "2026-01-15T10:30:00Z",
      "reason": "CUSTOMER_REQUEST",
      "paymentVoidStatus": "VOIDED",
      "workRollbackStatus": "ROLLED_BACK"
    }
    
    // 409 Conflict - in progress
    {
      "errorCode": "CANCELLATION_IN_PROGRESS",
      "message": "Cancellation is currently being processed",
      "orderId": "ord-123",
      "retryAfterSeconds": 5
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no duplicate cancellation records or downstream calls for idempotent requests
  - **Query example:**
    ```sql
    -- Find orders with multiple cancellation records within short time window (suspicious)
    SELECT order_id, COUNT(*) as record_count, 
           MAX(created_at) - MIN(created_at) as time_span
    FROM cancellation_record
    GROUP BY order_id
    HAVING COUNT(*) > 1
      AND MAX(created_at) - MIN(created_at) < INTERVAL '10 seconds';
    ```
  - **Expected outcome:** Zero or few records (legitimate retries after failures are OK, but not rapid duplicates)
  - **Audit considerations:**
    - Monitor for unusually high rate of 409 responses (may indicate locking issues)
    - Verify downstream systems (Payment, Work Execution) show single action per order
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add `CANCELLING` in-progress status to order schema
    2. Implement optimistic locking or distributed lock for concurrency control
    3. Update cancellation orchestrator with idempotency checks
    4. Deploy and test with concurrent requests
  - **Testing:**
    - Concurrent cancellation requests (simulate double-click)
    - Network retry scenarios
    - Verify exactly one cancellation record created
    - Verify downstream systems called exactly once
  - **Rollback strategy:** Feature flag to disable idempotency checks if issues arise
- **Governance & owner recommendations:**
  - **Owner:** Order domain team
  - **Review cadence:** Monitor idempotency behavior weekly after launch, then quarterly
  - **Monitoring:** Alert on high rate of 409 responses or locking timeouts
  - **Documentation:** Update API docs with clear idempotency guarantees

<a id="decision-order-006---cancellation-reason-taxonomy"></a>
### DECISION-ORDER-006 — Cancellation Reason Taxonomy

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-006)
- **Decision:** Cancellation reason is required and selected from a fixed enum: `CUSTOMER_REQUEST`, `INVENTORY_UNAVAILABLE`, `PRICING_ERROR`, `DUPLICATE_ORDER`, `OTHER`. The frontend uses this enum for the reason picker. Comments field (optional, max 2000 chars) allows free-text elaboration.
- **Alternatives considered:**
  - **Option A (Chosen):** Fixed enum with optional free-text comments
    - Pros: Structured data for reporting, consistent categorization, searchable
    - Cons: May not cover all scenarios (addressed by OTHER + comments)
  - **Option B:** Free-text reason only
    - Pros: Maximum flexibility
    - Cons: Inconsistent categorization, difficult to report/analyze, data quality issues
  - **Option C:** Backend-provided dynamic reason list
    - Pros: Flexible, can add reasons without frontend changes
    - Cons: Adds complexity, versioning issues, unnecessary for stable reason set
- **Reasoning and evidence:**
  - Business needs reporting on cancellation reasons for operational insights
  - Fixed enum ensures consistent categorization across users and time
  - Most cancellation scenarios fit into a small set of standard reasons
  - "OTHER" + comments provides escape hatch for edge cases
  - Comments field allows capturing context without breaking reporting structure
  - Industry standard pattern (e.g., support ticketing, refund requests)
- **Architectural implications:**
  - **Components affected:**
    - Frontend: Reason dropdown picker with fixed enum
    - Cancellation orchestrator: Validates reason against allowed values
    - Database: Store reason as VARCHAR or ENUM type
    - Reporting: Aggregates cancellations by reason for dashboards
  - **Reason enum definition:**
    ```typescript
    // Frontend TypeScript
    enum CancellationReason {
      CUSTOMER_REQUEST = 'CUSTOMER_REQUEST',
      INVENTORY_UNAVAILABLE = 'INVENTORY_UNAVAILABLE',
      PRICING_ERROR = 'PRICING_ERROR',
      DUPLICATE_ORDER = 'DUPLICATE_ORDER',
      OTHER = 'OTHER'
    }
    
    // Display labels
    const reasonLabels = {
      CUSTOMER_REQUEST: 'Customer requested cancellation',
      INVENTORY_UNAVAILABLE: 'Inventory not available',
      PRICING_ERROR: 'Pricing error',
      DUPLICATE_ORDER: 'Duplicate order',
      OTHER: 'Other (specify in comments)'
    };
    ```
  - **Database schema:**
    ```sql
    -- cancellation_record table
    reason VARCHAR(50) NOT NULL CHECK (reason IN (
      'CUSTOMER_REQUEST',
      'INVENTORY_UNAVAILABLE',
      'PRICING_ERROR',
      'DUPLICATE_ORDER',
      'OTHER'
    )),
    comments TEXT CHECK (LENGTH(comments) <= 2000)
    ```
  - **Validation:**
    - Backend enforces reason is one of allowed enum values (400 if invalid)
    - Backend enforces comments max length 2000 chars (400 if exceeded)
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all cancellation records have valid reasons; check if "OTHER" is overused
  - **Query example:**
    ```sql
    -- Cancellation reason distribution
    SELECT reason, COUNT(*) as count, 
           ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER(), 2) as percentage
    FROM cancellation_record
    GROUP BY reason
    ORDER BY count DESC;
    
    -- Orders with OTHER reason but no comments (potential data quality issue)
    SELECT id, order_id, reason, comments
    FROM cancellation_record
    WHERE reason = 'OTHER'
      AND (comments IS NULL OR TRIM(comments) = '');
    ```
  - **Expected outcome:**
    - Distribution shows most cancellations fit into standard reasons
    - OTHER category is < 10% (if higher, may indicate missing reason category)
    - OTHER records generally have non-empty comments
  - **Reporting:**
    - Weekly dashboard showing cancellation reason trends
    - Alert if OTHER category exceeds threshold
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Define cancellation reason enum in shared constants/config
    2. Update frontend reason picker with enum values and labels
    3. Deploy backend validation for reason enum
    4. Backfill historical cancellation records with best-effort reason mapping
  - **Backfill strategy:**
    ```sql
    -- Map historical free-text reasons to enum values (example)
    UPDATE cancellation_record
    SET reason = CASE
      WHEN LOWER(reason_text) LIKE '%customer%request%' THEN 'CUSTOMER_REQUEST'
      WHEN LOWER(reason_text) LIKE '%inventory%' OR LOWER(reason_text) LIKE '%stock%' THEN 'INVENTORY_UNAVAILABLE'
      WHEN LOWER(reason_text) LIKE '%pric%' OR LOWER(reason_text) LIKE '%cost%' THEN 'PRICING_ERROR'
      WHEN LOWER(reason_text) LIKE '%duplicate%' THEN 'DUPLICATE_ORDER'
      ELSE 'OTHER'
    END,
    comments = reason_text -- preserve original text in comments
    WHERE reason IS NULL;
    ```
  - **Phased rollout:**
    - Phase 1: Add enum to schema with nullable reason (allow legacy nulls)
    - Phase 2: Backfill historical data
    - Phase 3: Make reason NOT NULL and enforce enum
- **Governance & owner recommendations:**
  - **Owner:** Order domain team with input from Product and Operations
  - **Review cadence:** Quarterly review of reason distribution and OTHER usage
  - **Reason taxonomy updates:** Requires cross-functional approval; consider impact on reporting
  - **Future enhancement:** If OTHER usage is high, conduct user research to identify new reason categories

<a id="decision-order-007---cancellation-orchestration-timeout-and-failure-handling"></a>
### DECISION-ORDER-007 — Cancellation Orchestration Timeout and Failure Handling

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-007)
- **Decision:** If downstream systems (Payment void or Work rollback) fail or timeout during cancellation orchestration, the order transitions to `CANCELLATION_FAILED` state and requires manual intervention. No automatic retry is performed. The failure is fully audited with correlation IDs for troubleshooting.
- **Alternatives considered:**
  - **Option A (Chosen):** Transition to CANCELLATION_FAILED, require manual intervention
    - Pros: Safe, prevents cascading failures, clear signal for operations team
    - Cons: Requires manual work to resolve
  - **Option B:** Automatic retry with exponential backoff
    - Pros: May resolve transient failures automatically
    - Cons: Risks duplicate actions if partial success, delays user feedback, complex retry logic
  - **Option C:** Block cancellation and return error to user immediately
    - Pros: User retains control
    - Cons: User may be unaware of downstream failure, poor UX
- **Reasoning and evidence:**
  - Downstream failures (Payment, Work Execution) may indicate systemic issues
  - Automatic retries without understanding failure cause can worsen problems
  - Manual intervention allows operations team to assess and correct underlying issues
  - Financial operations (void/refund) should not be retried blindly due to risk of double-processing
  - Clear failure state enables monitoring and alerting for operations team
  - User receives immediate feedback (failure state) rather than indefinite wait
- **Architectural implications:**
  - **Components affected:**
    - Cancellation orchestrator: Handles downstream errors and transitions to failure state
    - Order state machine: Includes `CANCELLATION_FAILED` status
    - Operations dashboard: Lists orders in failed state for manual resolution
    - Alerting system: Notifies operations team of failures
  - **Failure handling flow:**
    ```
    1. Orchestrator calls Work Execution rollback
    2. If work rollback fails or times out:
         a. Record failure in cancellation_record (work_rollback_status = 'ROLLBACK_FAILED')
         b. Transition order to CANCELLATION_FAILED
         c. Emit OrderCancellationFailed event with correlation IDs
         d. Return 500 to client with "manual intervention required" message
    3. Orchestrator calls Payment void
    4. If payment void fails or times out:
         a. Record failure in cancellation_record (payment_void_status = 'VOID_FAILED')
         b. Transition order to CANCELLATION_FAILED
         c. Emit OrderCancellationFailed event with correlation IDs
         d. Return 500 to client with "manual intervention required" message
    ```
  - **Database schema:**
    ```sql
    -- Order status includes failure state
    ALTER TYPE order_status ADD VALUE 'CANCELLATION_FAILED';
    
    -- cancellation_record captures failure details
    work_rollback_status VARCHAR(50) CHECK (work_rollback_status IN (
      'ROLLED_BACK', 'ROLLBACK_FAILED', 'NOT_APPLICABLE'
    )),
    payment_void_status VARCHAR(50) CHECK (payment_void_status IN (
      'VOIDED', 'VOID_FAILED', 'REFUND_REQUIRED', 'NOT_APPLICABLE'
    ))
    ```
  - **Monitoring and alerting:**
    ```
    Alert: cancellation_failed_orders
    Condition: COUNT(orders with status = CANCELLATION_FAILED) > threshold
    Severity: High
    Action: Notify operations team, create incident ticket
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify failed cancellations are properly audited and resolved
  - **Query example:**
    ```sql
    -- Orders in CANCELLATION_FAILED state
    SELECT o.order_id, o.status, o.updated_at,
           cr.work_rollback_status, cr.payment_void_status,
           cr.work_correlation_id, cr.payment_correlation_id
    FROM "order" o
    JOIN cancellation_record cr ON cr.order_id = o.order_id
    WHERE o.status = 'CANCELLATION_FAILED'
    ORDER BY o.updated_at DESC;
    
    -- Failed cancellations older than SLA (e.g., 24 hours)
    SELECT order_id, updated_at, 
           NOW() - updated_at as time_in_failed_state
    FROM "order"
    WHERE status = 'CANCELLATION_FAILED'
      AND updated_at < NOW() - INTERVAL '24 hours';
    ```
  - **Expected outcome:**
    - List of orders requiring manual intervention
    - Alert if any order has been in failed state beyond SLA
  - **Resolution tracking:**
    - Operations team logs resolution actions (e.g., manual void, manual work cancellation)
    - Update order to final state (CANCELLED or CANCELLED_REQUIRES_REFUND) after resolution
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add `CANCELLATION_FAILED` status to order schema
    2. Implement failure handling in orchestrator
    3. Create operations dashboard view for failed cancellations
    4. Set up monitoring and alerting
    5. Train operations team on manual resolution procedures
  - **Operational procedures:**
    - Check correlation IDs in downstream systems to understand failure cause
    - Manually complete void/refund in Payment system if needed
    - Manually cancel work in Work Execution system if needed
    - Update order status to reflect resolution
    - Document resolution in order notes/comments
  - **Testing:**
    - Simulate downstream timeout (mock Payment/Work Execution)
    - Verify order transitions to CANCELLATION_FAILED
    - Verify alert is triggered
    - Verify operations dashboard displays failed order
- **Governance & owner recommendations:**
  - **Owner:** Order domain team with coordination from Operations and SRE
  - **Review cadence:** Weekly review of failed cancellations; monthly trend analysis
  - **SLA:** Define target time for manual resolution (e.g., 4 business hours)
  - **Root cause analysis:** Investigate patterns in downstream failures; address systemic issues
  - **Future enhancement:** Consider semi-automatic retry with approval workflow for specific failure types

<a id="decision-order-008---cancellation-comments-maximum-length"></a>
### DECISION-ORDER-008 — Cancellation Comments Maximum Length

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-008)
- **Decision:** The optional comments field for cancellation requests has a maximum length of 2000 characters. This limit is enforced client-side and server-side. The field is optional (may be null or empty string).
- **Alternatives considered:**
  - **Option A (Chosen):** 2000 character limit
    - Pros: Allows detailed context, reasonable database storage, protects against abuse
    - Cons: Arbitrary limit may be too restrictive in rare cases
  - **Option B:** Unlimited length
    - Pros: Maximum flexibility
    - Cons: Database performance issues, UI display challenges, risk of abuse
  - **Option C:** 500 character limit
    - Pros: Concise, faster to read
    - Cons: May be too restrictive for complex scenarios
- **Reasoning and evidence:**
  - 2000 characters allows approximately 300-350 words (enough for detailed explanation)
  - Most cancellation comments are brief (< 100 characters); 2000 provides ample headroom
  - Protects database and UI from extremely long inputs
  - Standard practice in similar fields (e.g., support ticket comments, refund reasons)
  - TEXT column type in database supports this length efficiently
- **Architectural implications:**
  - **Components affected:**
    - Frontend: Comment textarea with character counter and validation
    - Backend API: Validation middleware checks length
    - Database: Store as TEXT (supports up to 64KB, but application enforces 2000)
  - **Frontend validation:**
    ```typescript
    // Vue 3 component
    const MAX_COMMENT_LENGTH = 2000;
    const comments = ref('');
    const isCommentTooLong = computed(() => comments.value.length > MAX_COMMENT_LENGTH);
    
    // Display character counter
    <div>{{ comments.value.length }} / {{ MAX_COMMENT_LENGTH }}</div>
    <button :disabled="isCommentTooLong">Submit</button>
    ```
  - **Backend validation:**
    ```java
    @Size(max = 2000, message = "Comments must not exceed 2000 characters")
    private String comments;
    ```
  - **Error response:**
    ```json
    {
      "errorCode": "COMMENTS_TOO_LONG",
      "message": "Comments exceed maximum length of 2000 characters",
      "field": "comments",
      "maxLength": 2000,
      "actualLength": 2543
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no comments exceed the 2000 character limit
  - **Query example:**
    ```sql
    -- Find comments exceeding limit (should be impossible with proper validation)
    SELECT id, order_id, LENGTH(comments) as comment_length
    FROM cancellation_record
    WHERE comments IS NOT NULL
      AND LENGTH(comments) > 2000;
    ```
  - **Expected outcome:** Zero records with oversized comments
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add length validation to backend API
    2. Update frontend with character counter and validation
    3. Audit existing comments for length violations
    4. Truncate or migrate any existing oversized comments
  - **Historical data cleanup:**
    ```sql
    -- Find oversized comments (if any)
    SELECT id, order_id, LENGTH(comments) as length
    FROM cancellation_record
    WHERE LENGTH(comments) > 2000;
    
    -- Option 1: Truncate with indicator
    UPDATE cancellation_record
    SET comments = SUBSTRING(comments, 1, 1997) || '...'
    WHERE LENGTH(comments) > 2000;
    ```
- **Governance & owner recommendations:**
  - **Owner:** Order domain team
  - **Review cadence:** No regular review needed; stable limit
  - **Future consideration:** If users consistently hit the limit, reconsider increasing to 5000

<a id="decision-order-009---concurrency-control-with-409-conflict-response"></a>
### DECISION-ORDER-009 — Concurrency Control with 409 Conflict Response

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-009)
- **Decision:** When a cancellation is already in progress for an order, concurrent cancellation requests receive HTTP 409 Conflict response with message "Cancellation already in progress." The frontend displays this as a retriable error and prompts the user to refresh the order status.
- **Alternatives considered:**
  - **Option A (Chosen):** 409 Conflict for concurrent requests
    - Pros: RESTful standard, clear semantics, prevents duplicate processing
    - Cons: Requires client retry logic
  - **Option B:** Accept all requests, queue internally
    - Pros: User never sees conflict
    - Cons: Complex queue management, unclear request completion time
  - **Option C:** Return 503 Service Unavailable
    - Pros: Standard for "try again later"
    - Cons: Implies system-wide issue rather than order-specific conflict
- **Reasoning and evidence:**
  - HTTP 409 Conflict is the standard status code for "resource conflict" scenarios
  - Concurrent cancellation requests typically indicate user double-click or UI bug
  - First request wins; subsequent requests should be rejected until first completes
  - Client can retry after brief delay or after confirming order status
  - Prevents race conditions and undefined behavior in orchestration
- **Architectural implications:**
  - **Components affected:**
    - Cancellation orchestrator: Detects in-progress state and returns 409
    - Order state machine: Includes in-progress state (e.g., `CANCELLING`)
    - Frontend: Handles 409 with user-friendly message and refresh prompt
  - **Concurrency detection:**
    ```java
    // Pseudo-code
    if (order.getStatus() == OrderStatus.CANCELLING) {
        return Response.status(409)
            .entity(new ErrorResponse(
                "CANCELLATION_IN_PROGRESS",
                "Cancellation is currently being processed. Please refresh to see updated status.",
                Map.of("orderId", orderId, "retryAfterSeconds", 5)
            ))
            .build();
    }
    ```
  - **Frontend handling:**
    ```typescript
    // Vue 3 component
    async function handleCancelOrder() {
      try {
        const response = await cancelOrder(orderId, reason, comments);
        // Success: refresh order detail
        await refreshOrderDetail();
      } catch (error) {
        if (error.status === 409) {
          // Conflict: show message and refresh button
          showMessage({
            type: 'warning',
            message: 'Cancellation is already in progress. Refreshing order status...',
          });
          setTimeout(() => refreshOrderDetail(), 2000);
        } else {
          // Other errors
          handleError(error);
        }
      }
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify 409 responses are handled correctly and don't result in duplicate cancellations
  - **Monitoring query:**
    ```sql
    -- Track 409 response rate
    SELECT DATE_TRUNC('hour', timestamp) as hour,
           COUNT(*) as conflict_count
    FROM api_request_log
    WHERE endpoint LIKE '%cancel%'
      AND response_status = 409
    GROUP BY hour
    ORDER BY hour DESC;
    ```
  - **Expected outcome:**
    - Low rate of 409 responses (< 1% of cancel requests)
    - If rate is high, investigate UI issues (e.g., button not disabled during submit)
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add in-progress status (CANCELLING) to order schema
    2. Implement concurrency detection in orchestrator
    3. Update frontend error handling for 409
    4. Test concurrent cancellation scenarios
  - **Testing:**
    ```javascript
    // Test concurrent cancellation
    const [response1, response2] = await Promise.all([
      cancelOrder(orderId, reason, comments),
      cancelOrder(orderId, reason, comments)
    ]);
    
    expect([response1.status, response2.status].sort()).toEqual([200, 409]);
    ```
- **Governance & owner recommendations:**
  - **Owner:** Order domain team
  - **Monitoring:** Alert if 409 rate exceeds threshold (indicates UI issue)
  - **Review cadence:** Monthly review of concurrency patterns

<a id="decision-order-010---authorization-and-permission-model-for-cancellation"></a>
### DECISION-ORDER-010 — Authorization and Permission Model for Cancellation

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-010)
- **Decision:** Order cancellation requires the `ORDER_CANCEL` permission. Users without this permission cannot initiate cancellation. Authorization is enforced server-side; frontend gates the UI but backend enforcement is mandatory. Unauthorized requests return HTTP 403 Forbidden.
- **Alternatives considered:**
  - **Option A (Chosen):** Explicit ORDER_CANCEL permission
    - Pros: Fine-grained control, clear audit trail, role-based access
    - Cons: Requires permission management infrastructure
  - **Option B:** Role-based (e.g., only "Store Manager" role)
    - Pros: Simple to understand
    - Cons: Inflexible, difficult to grant exceptions
  - **Option C:** Allow all authenticated users
    - Pros: Simplest implementation
    - Cons: No access control, anyone can cancel any order
- **Reasoning and evidence:**
  - Cancellation has financial and operational impact; must be controlled
  - Permission-based model allows flexible role assignment
  - Audit trail requires knowing who had permission at time of action
  - Industry standard for critical operations (refunds, cancellations, deletions)
  - Backend enforcement prevents privilege escalation via UI bypass
- **Architectural implications:**
  - **Components affected:**
    - Cancellation API: Checks ORDER_CANCEL permission before processing
    - Frontend: Conditionally displays Cancel button based on user permissions
    - Permission service: Manages and exposes user permissions
  - **Authorization check:**
    ```java
    // Backend authorization
    @PreAuthorize("hasAuthority('ORDER_CANCEL')")
    public CancelOrderResponse cancelOrder(CancelOrderRequest request) {
        // ... cancellation logic
    }
    ```
  - **Frontend permission check:**
    ```typescript
    // Vue 3 component
    const userPermissions = inject('userPermissions');
    const canCancelOrder = computed(() => 
      userPermissions.value.includes('ORDER_CANCEL')
    );
    
    <button v-if="canCancelOrder" @click="showCancelDialog">
      Cancel Order
    </button>
    ```
  - **Error response:**
    ```json
    {
      "errorCode": "FORBIDDEN",
      "message": "You do not have permission to cancel orders",
      "requiredPermission": "ORDER_CANCEL"
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all cancellations were performed by users with ORDER_CANCEL permission
  - **Query example:**
    ```sql
    -- Find cancellations by users without permission at time of action
    SELECT cr.id, cr.order_id, cr.cancelled_by, cr.created_at
    FROM cancellation_record cr
    LEFT JOIN user_permission_history uph ON 
      uph.user_id = cr.cancelled_by
      AND uph.permission = 'ORDER_CANCEL'
      AND uph.effective_from <= cr.created_at
      AND (uph.effective_to IS NULL OR uph.effective_to >= cr.created_at)
    WHERE uph.id IS NULL; -- No matching permission found
    ```
  - **Expected outcome:** Zero unauthorized cancellations
  - **Compliance:** Demonstrate proper access controls for financial operations
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Define ORDER_CANCEL permission in permission system
    2. Assign permission to appropriate roles (Store Manager, Service Advisor, etc.)
    3. Deploy backend authorization checks
    4. Update frontend to check permission and gate UI
    5. Test with users having and lacking permission
  - **Rollout strategy:**
    - Phase 1: Deploy permission checks in warn-only mode (log violations but allow)
    - Phase 2: Review logs for unexpected permission gaps
    - Phase 3: Enable strict enforcement (403 for unauthorized)
  - **User communication:**
    - Notify users of permission requirement
    - Provide instructions for requesting access if needed
- **Governance & owner recommendations:**
  - **Owner:** Order domain team with coordination from Security team
  - **Review cadence:** Quarterly review of permission assignments
  - **Access requests:** Standard approval process for granting ORDER_CANCEL permission
  - **Monitoring:** Alert on unusual cancellation patterns (may indicate compromised account)

<a id="decision-order-011---canonical-domain-label-and-ownership-for-cancellation-ui"></a>
### DECISION-ORDER-011 — Canonical Domain Label and Ownership for Cancellation UI

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-011)
- **Decision:** Cancellation orchestration and cancellation UI stories are canonically owned by the Order domain and should be labeled `domain:order`. Payment and Work Execution are dependencies and remain authoritative for their own statuses, but do not own the cancellation entrypoint.
- **Alternatives considered:**
  - **Option A (Chosen):** `domain:order` ownership
    - Pros: Aligns with order lifecycle state machine and orchestration responsibility
    - Cons: Requires cross-domain coordination and clear contracts
  - **Option B:** Label as `domain:payment` / “Point of Sale”
    - Pros: Matches some frontend labeling conventions
    - Cons: Misroutes ownership; encourages UI to couple directly to payment logic
  - **Option C:** Split ownership across domains
    - Pros: Domain autonomy
    - Cons: Inconsistent policy, split audit trail, unclear accountability
- **Reasoning and evidence:**
  - Cancellation is an order lifecycle change with cross-domain side effects
  - Centralized ownership reduces divergence and improves auditability
- **Architectural implications:**
  - Code review routing and story routing should include Order domain reviewers
  - Contracts for payment/work integration must be documented and versioned
- **Auditor-facing explanation:**
  - Validate that cancellations are initiated via Order-domain services and audited consistently
- **Migration & backward-compatibility notes:**
  - Update story labels and docs; no data migration required
- **Governance & owner recommendations:**
  - **Owner:** Order domain
  - **Review cadence:** Annual or when cancellation expands beyond current scope

<a id="decision-order-012---ui-to-moqui-service-contract-conventions-safe-defaults"></a>
### DECISION-ORDER-012 — UI to Moqui Service Contract Conventions (Safe Defaults)

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-012)
- **Decision:** Use a single command-style cancel service owned by Order (inputs: `orderId`, `reason`, `comments`) and a single order-detail read service that returns cancellation summary (or a dedicated Order-owned “latest cancellation” read service if embedding is not feasible). Error responses should include stable `errorCode` and user-safe `message` with optional `details`/`fieldErrors`.
- **Alternatives considered:**
  - **Option A (Chosen):** Single cancel command + order detail includes summary
    - Pros: Minimal UI complexity; avoids UI calling downstream domains
    - Cons: Requires order detail contract evolution
  - **Option B:** UI calls downstream domains for work/payment pre-checks
    - Pros: Potentially richer pre-submit warnings
    - Cons: Double calls, stale data risk, tighter coupling, security complexity
  - **Option C:** Separate pre-check endpoint
    - Pros: Early feedback without side effects
    - Cons: More surface area; still risks divergence from submit policy
- **Reasoning and evidence:**
  - Submit response is authoritative and already needs structured errors
  - Pre-checks can be advisory using already-loaded summaries
- **Architectural implications:**
  - Requires a documented contract in Moqui services/screens
  - Standardize error schema for deterministic UI mapping
- **Auditor-facing explanation:**
  - Ensure error codes are logged and auditable without leaking sensitive data
- **Migration & backward-compatibility notes:**
  - safe_to_defer: true (service naming/paths must be confirmed per repo conventions)
  - Start with contract tests; finalize service names in implementation
- **Governance & owner recommendations:**
  - **Owner:** Order domain + Moqui frontend maintainers
  - **Review cadence:** Per release when contracts change

<a id="decision-order-013---canonical-order-cancellation-status-enum-contract"></a>
### DECISION-ORDER-013 — Canonical Order Cancellation Status Enum Contract

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-013)
- **Decision:** The UI-facing canonical cancellation-related statuses are `CANCELLING`, `CANCELLED`, `CANCELLED_REQUIRES_REFUND`, and `CANCELLATION_FAILED`. Any legacy backend variants must be mapped server-side to these canonical values for UI.
- **Alternatives considered:**
  - **Option A (Chosen):** Canonicalize to a small stable set
    - Pros: Prevents enum drift; simplifies UI logic
    - Cons: Requires mapping layer if legacy values exist
  - **Option B:** Expose all internal variants to UI
    - Pros: No mapping work
    - Cons: UI complexity, higher chance of mismatch, harder to test
- **Reasoning and evidence:**
  - Frontend stories explicitly warn about status drift risk
  - Canonical enum supports deterministic rendering and reduced QA matrix
- **Architectural implications:**
  - Backend should document and test mapping behavior
  - UI should still treat unknown values as non-fatal
- **Auditor-facing explanation:**
  - Inspect state transition logs; confirm mapping does not hide failed cancellations
- **Migration & backward-compatibility notes:**
  - safe_to_defer: true (mapping details depend on existing persisted status values)
  - Add compatibility tests for any legacy states encountered
- **Governance & owner recommendations:**
  - **Owner:** Order domain
  - **Review cadence:** Only when adding new cancellation-related states

<a id="decision-order-014---frontend-permission-exposure-pattern-safe-default"></a>
### DECISION-ORDER-014 — Frontend Permission Exposure Pattern (Safe Default)

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-014)
- **Decision:** UI should gate cancel action based on a backend-provided capability signal (e.g., `canCancel`) returned by order detail (or a capability set), not by duplicating permission rules client-side. Server-side still enforces `ORDER_CANCEL`.
- **Alternatives considered:**
  - **Option A (Chosen):** Capability boolean in order detail
    - Pros: Single source of truth; avoids duplicating authorization logic in UI
    - Cons: Requires order detail contract update
  - **Option B:** UI evaluates permissions from session context
    - Pros: Less backend coupling
    - Cons: Risk of drift; hard to keep consistent across screens
- **Reasoning and evidence:**
  - Conservative pattern: UI should not infer security policy
- **Architectural implications:**
  - Order detail must compute capability using the same server-side checks
- **Auditor-facing explanation:**
  - Validate unauthorized users cannot cancel (403) even if UI gating fails
- **Migration & backward-compatibility notes:**
  - safe_to_defer: true (repo may already have a standard permission exposure mechanism)
  - If a standard exists, replace `canCancel` with that mechanism
- **Governance & owner recommendations:**
  - **Owner:** Security domain + Order domain
  - **Review cadence:** When permission model changes

<a id="decision-order-015---correlation-ids-and-admin-only-details-visibility"></a>
### DECISION-ORDER-015 — Correlation IDs and Admin-only Details Visibility

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-ORDER-015)
- **Decision:** `cancellationId` is visible to all users authorized to view the order. Correlation IDs and downstream subsystem detail fields are returned and displayed only when the caller has an explicit admin/support permission; otherwise they are omitted/redacted.
- **Alternatives considered:**
  - **Option A (Chosen):** Admin-only operational detail
    - Pros: Reduces internal leakage; still supports troubleshooting
    - Cons: Support users need explicit permission
  - **Option B:** Show correlation IDs to all
    - Pros: Self-service support
    - Cons: Higher leakage risk; encourages exposing internals
- **Reasoning and evidence:**
  - Correlation identifiers can be operationally sensitive even if not secret
  - Supportability is maintained via role-scoped visibility
- **Architectural implications:**
  - Backend must apply field-level redaction based on permissions
  - UI must handle absence of correlation fields gracefully
- **Auditor-facing explanation:**
  - Validate only support/admin roles can access operational detail
- **Migration & backward-compatibility notes:**
  - Add permissions and implement redaction; no historical data migration required
- **Governance & owner recommendations:**
  - **Owner:** Security domain
  - **Review cadence:** Quarterly review of who has access to support identifiers

## End

End of document.
