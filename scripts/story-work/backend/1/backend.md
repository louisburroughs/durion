Title: [BACKEND] [STORY] Audit Trail: Track Price Overrides, Refunds, and Cancellations
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/1
Labels: backend, story-implementation, payment, type:story, domain:accounting, status:ready-for-dev

## 🏷️ Labels (Final)

### Required
- type:story
- domain:accounting
- status:ready-for-dev

### Recommended
- agent:accounting
- agent:story-authoring

---

**Rewrite Variant:** accounting-strict

---

## Story Intent

As an **Auditor**, I need to track and explain financial exceptions (price overrides, refunds, and order cancellations) through an immutable audit trail so that I can reconcile discrepancies, comply with financial controls, and provide evidence for exception reviews.

---

## Actors & Stakeholders

- **Auditor** (primary actor) — reviews and exports audit trails for exception analysis and compliance verification
- **Finance / Accounting** — interprets audit data and reconciles GL impacts
- **Payment System** — records transaction data that feeds audit entries
- **Compliance Officer** — ensures audit trails meet regulatory requirements
- **System** — captures and stores immutable audit events

---

## Preconditions

- The order/invoice/payment record exists in the system
- The user performing the override/refund/cancellation has been authenticated and authorized per policy
- Authorization thresholds and policies are defined and accessible at runtime
- The system clock is synchronized and trusted for timestamp accuracy

---

## Functional Behavior

### Override Scenario
**When** an authorized user initiates a price override on an order line,
**Then** the system SHALL:
1. **Validate authorization:** Enforce role-based thresholds and forbidden categories (pricing below cost, policy violations)
2. Reject immediately if policy cannot be evaluated or thresholds are exceeded
3. Upon approval, record the original price, new price, actor identity, timestamp, stated reason, and policy version
4. Append the record to the audit log (immutable)
5. Link the audit entry to the order, affected line items, and any resulting invoice adjustments
6. Emit an `OverridePriceCreated` event with `accountingIntent = REVENUE_ADJUSTMENT` and `accountingStatus = PENDING_POSTING`

### Refund Scenario
**When** a refund is authorized (reversals, credit memos, or adjustments),
**Then** the system SHALL:
1. **Validate separate authorization:** Refund authority is independent of the original sale authorization
2. Determine refund method based on payment settlement status:
   - Unsettled: issue REVERSAL (void/chargeback)
   - Settled: issue CREDIT_MEMO or REFUND_PAYMENT (cash out)
3. Record the refund amount, reason, authorized actor, timestamp, refund type, and authorization level
4. Link the refund audit entry to the original payment transaction, invoice, and settlement status
5. Emit a `RefundCreated` event with:
   - `refundType` (REVERSAL | CREDIT_MEMO | ADJUSTMENT)
   - `accountingIntent` (PAYMENT_REVERSAL | CUSTOMER_CREDIT | WRITE_OFF)
   - `linkedSourceIds` (invoiceId, paymentId)
   - `accountingStatus = PENDING_POSTING`

### Cancellation Scenario
**When** an order or invoice is cancelled,
**Then** the system SHALL:
1. Capture **before snapshot** (pre-cancellation state: totals, balances, payment status)
2. Record the cancellation actor, timestamp, stated reason, and cancellation type
3. Capture **after snapshot** (post-cancellation state)
4. Link the cancellation entry to:
   - Original order/invoice
   - All related financial transactions (partial payments, adjustments)
   - Expected GL reversal references (but not actual GL posting)
5. Emit a `CancellationCreated` event with:
   - `accountingIntent` (REVENUE_REVERSAL | PAYMENT_RECOVERY)
   - `accountingStatus = PENDING_POSTING`
   - `beforeSnapshot` and `afterSnapshot` for audit traceability
   - Note: Partial payments remain recorded; Accounting determines credit/reversal handling

---

## Alternate / Error Flows

### Error: User Not Authorized
**When** a user without override/refund/cancellation authority attempts the action,
**Then** the system SHALL reject the request immediately and log the denied attempt (with actor, timestamp, reason for denial, and policy version).

### Error: Override Exceeds Threshold
**When** a price override request exceeds role-based thresholds,
**Then** the system SHALL reject or escalate to higher authorization (per policy configuration).

### Error: Forbidden Override Category
**When** a price override falls into a forbidden category (below cost, stacking violations, regulated fees),
**Then** the system SHALL reject the request with specific violation reason.

### Error: Missing Authorization for Refund
**When** a refund is requested without proper separate authorization,
**Then** the system SHALL reject the request and require explicit approval with new authorization actor.

### Error: Conflicting Financial State
**When** a refund or cancellation cannot be processed due to policy constraints (GL period locked, invoice immutable),
**Then** the system SHALL raise an exception, log the failure, notify Finance, and defer processing.

---

## Business Rules

1. **Immutability:** Audit log entries MUST NOT be deleted or modified once recorded. Only append new correction entries.

2. **Authorization Enforcement:** 
   - Price overrides MUST be authorized per role-based thresholds before record creation
   - Forbidden override categories MUST be rejected synchronously
   - Refund authorization is separate from and independent of sale authorization
   - Authorization must be re-evaluated at creation time, not inherited

3. **Completeness:** Every financial exception (override, refund, cancellation) MUST create at least one audit entry with:
   - Actor, timestamp, reason, and authorization level
   - Original and adjusted amounts (for overrides/refunds)
   - Affected reference IDs (order, invoice, payment)
   - For cancellations: before/after snapshots

4. **Traceability:** Every audit entry MUST be linkable back to:
   - Originating order/invoice/payment
   - Authorization actor and policy version used
   - For GL: `sourceEventId` and `sourceDocumentId` (GL posting is Accounting's responsibility)

5. **Accounting Intent Capture:** Every operational event MUST include:
   - `accountingIntent` (REVENUE_ADJUSTMENT, PAYMENT_REVERSAL, WRITE_OFF, etc.)
   - `accountingStatus = PENDING_POSTING` (initial state)
   - `expectedAccountingOutcome` (descriptive, not procedural)

6. **GL Posting Out of Scope:** This story records exceptions; GL posting timing, execution, and reversal logic are Accounting's responsibility. Operational systems do NOT post to GL or determine posting timing.

7. **Refund Method Distinction:** Settled payments MUST NOT be reversed; instead, issue Credit Memo or Refund Payment (cash out).

8. **Partial Payment Handling:** When an invoice with partial payment is cancelled, both payment and cancellation remain recorded as separate facts; Accounting reconciles via credit/refund, not netting.

---

## Data Requirements

**Core Audit Entry Entity: `AuditTrailEntry`**
- `audit_id` (UUID, unique identifier)
- `exception_type` (PRICE_OVERRIDE | REFUND | CANCELLATION)
- `actor_id` (UUID, user performing the action)
- `actor_role` (string, role of actor)
- `timestamp` (ISO-8601 UTC, server-generated, immutable)
- `reason` (string, free text or structured reason code)
- `authorization_level` (string, e.g., SERVICE_WRITER, MANAGER, GLOBAL_ADMIN)
- `policy_version` (string, version of policy enforced at creation time)

**For PRICE_OVERRIDE Exception Type:**
- `order_id` (UUID, required)
- `line_item_id` (UUID, affected line)
- `original_price` (decimal)
- `adjusted_price` (decimal)
- `override_percent_or_amount` (string, e.g., "-$5.00" or "-10%")
- `forbidden_category_code` (nullable, if rejected: BELOW_COST, STACKING_VIOLATION, REGULATED_FEE, etc.)
- `policy_validation_result` (APPROVED | REJECTED_FORBIDDEN | REJECTED_THRESHOLD_EXCEEDED)

**For REFUND Exception Type:**
- `invoice_id` (UUID)
- `payment_id` (UUID, original payment)
- `refund_type` (REVERSAL | CREDIT_MEMO | ADJUSTMENT)
- `refund_amount` (decimal)
- `original_payment_status` (PENDING | SETTLED)
- `refund_method` (VOID, CHARGEBACK, CASH_REFUND, CREDIT_MEMO) — determined by settled status
- `linked_source_ids` (JSON object: { invoiceId, paymentId, creditNoteId })

**For CANCELLATION Exception Type:**
- `order_id` or `invoice_id` (UUID, primary reference)
- `cancellation_type` (ORDER_CANCELLED | INVOICE_CANCELLED)
- `before_snapshot` (JSON object: totals, balances, payment status pre-cancellation)
- `after_snapshot` (JSON object: state post-cancellation)
- `partial_payment_info` (JSON, if applicable: paymentId, amountReceived, stillRecorded=true)
- `gl_reversal_status` (PENDING_REVERSAL — Accounting owns actual posting)

**Supporting Entities:**

- `OverridePolicyThreshold` (CRM/Pricing domain)
  - `role` (e.g., SERVICE_WRITER, MANAGER)
  - `max_absolute_amount` (currency)
  - `max_percent_off` (percentage)
  - `effective_date` (start)
  - `expiration_date` (end, for versioning)

- `RefundPolicyConfig` (Accounting domain)
  - `requires_separate_authorization` (true — hardcoded)
  - `settled_payment_handling` (CREDIT_MEMO | REFUND_PAYMENT)
  - `unsettled_payment_handling` (REVERSAL)

- `ExceptionReport` — aggregates audit entries by order, date range, actor, exception type for export

---

## Acceptance Criteria

### Authorization Enforcement at Creation
- **Given** a price override request exceeding role threshold,
- **When** the request is submitted,
- **Then** the system rejects it immediately with reason "Authorization threshold exceeded" and no audit entry is created.

### Forbidden Override Categories Blocked
- **Given** a price override below cost,
- **When** the request is submitted,
- **Then** the system rejects it with reason "Pricing below cost not permitted" and no audit entry is created.

### Audit Trail Integrity
- **Given** an override/refund/cancellation is recorded,
- **When** the audit log is queried,
- **Then** the record exists with all required fields populated, timestamp is immutable, and no editing history is shown (only appended corrections).

### Drilldown by Order
- **Given** an order ID,
- **When** the auditor queries the audit trail,
- **Then** all financial exceptions for that order are returned, linked to their authorization level, policy version, original amounts, and settlement status, sorted by timestamp.

### Exportable Report with Full Context
- **Given** an auditor requests a report filtered by date range and/or exception type,
- **When** the report is generated,
- **Then** it includes all matching audit entries in CSV or JSON with columns for:
  - Actor, actor_role, timestamp, authorization_level, policy_version, reason
  - For overrides: original/adjusted price, policy_validation_result, forbidden_category_code
  - For refunds: refund_type, refund_amount, original_payment_status, refund_method
  - For cancellations: before/after snapshots, partial_payment_info

### Refund Separate Authorization
- **Given** a refund is initiated with original sale authorization only,
- **When** the request is submitted,
- **Then** the system rejects it and requires explicit refund authorization with a different (refund-authorized) actor.

### Settled Payment Refund Handling
- **Given** a refund for a settled payment is approved,
- **When** the refund is recorded,
- **Then** the system captures `refund_method = CREDIT_MEMO or REFUND_PAYMENT` (not REVERSAL), and links to the settled payment without attempting reversal.

### Cancellation Snapshots
- **Given** an invoice with partial payment is cancelled,
- **When** the cancellation is recorded,
- **Then** the audit entry captures:
  - before_snapshot (pre-cancel totals and payment status)
  - after_snapshot (post-cancel state)
  - partial_payment_info (original payment remains recorded)

### Accounting Intent Events
- **Given** an override/refund/cancellation is recorded,
- **When** the event is emitted,
- **Then** it includes:
  - `accountingIntent` (specific intent: REVENUE_ADJUSTMENT, PAYMENT_REVERSAL, etc.)
  - `accountingStatus = PENDING_POSTING`
  - `expectedAccountingOutcome` (descriptive)
  - `sourceEventId` and `sourceDocumentId` (for Accounting to link GL posting)

### GL Posting is Out of Scope
- **Given** an override/refund/cancellation is recorded,
- **When** the audit entry is queried,
- **Then** the entry does NOT contain `glEntryId` or posting timestamp; instead it contains `accountingIntent` and `accountingStatus = PENDING_POSTING`, delegating GL posting to Accounting domain.

---

## Audit & Observability

**Audit Events to Log (Immutable):**
- `AuditEntryCreated` — fired when an override/refund/cancellation is recorded
  - Include: `exception_type`, `actor_id`, `actor_role`, `authorization_level`, `policy_version`
- `AuthorizationDenied` — fired when an actor lacks authority or threshold is exceeded
  - Include: `exception_type`, `actor_id`, `actor_role`, `reason_denied` (threshold exceeded, forbidden category, missing refund auth)
- `AuditTrailExported` — fired when an auditor exports a report
  - Include: export scope, actor, filters applied
- `OverridePriceCreated` — operational event with accounting intent
  - Include: `accountingIntent`, `accountingStatus = PENDING_POSTING`
- `RefundCreated` — operational event with accounting intent
  - Include: `accountingIntent`, `refundType`, `accountingStatus = PENDING_POSTING`
- `CancellationCreated` — operational event with before/after snapshots
  - Include: `accountingIntent`, `accountingStatus = PENDING_POSTING`, snapshots

**Observability Metrics:**
- Counter: `price_override_attempts` (tagged by role, result=approved|rejected_threshold|rejected_forbidden)
- Counter: `refund_attempts` (tagged by refund_type, result=approved|rejected_no_auth)
- Counter: `cancellations_by_type` (ORDER_CANCELLED | INVOICE_CANCELLED)
- Counter: `audit_exports` (frequency, scope, actors)
- Gauge: `pending_accounting_items` (count of audit entries awaiting GL posting)

---

## Original Story (Unmodified – For Traceability)

# Issue #1 — [BACKEND] [STORY] Audit Trail: Track Price Overrides, Refunds, and Cancellations

## Original Labels
- backend
- story-implementation
- payment

## Original Body Summary
Record who/when/why for overrides/refunds/cancellations. Audit entries append-only. Drilldown by order/invoice. Payment refs included. Integrations with Accounting and payment references. Data entities: AuditLog, ExceptionReport, ReferenceIndex. Backend: Spring Boot 3.2.6, Java 21, Spring Data JPA, PostgreSQL/MySQL.

---

## Clarification Resolution Summary

**Clarification Issue:** #215  
**Questions Resolved:** 4 of 4

1. ✅ **GL Posting Timing:** Out of scope; Accounting owns all GL decisions
2. ✅ **Price Override Authority:** Role-based thresholds + forbidden categories enforced synchronously
3. ✅ **Refund Policy:** All three types (reversals, credits, adjustments); separate authorization required; settled payments use credits/refunds
4. ✅ **Cancellation Scope:** GL reversals not automatic; partial payments remain intact; before/after snapshots mandatory

All decisions incorporated. Story ready for development.
