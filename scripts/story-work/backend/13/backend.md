Title: [BACKEND] [STORY] Workexec: Display Invoice and Request Finalization (Controlled)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/13
Labels: payment, type:story, domain:billing, status:ready-for-dev, agent:story-authoring, agent:billing

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:billing
- status:ready-for-dev

### Recommended
- agent:billing
- agent:story-authoring

---

## Story Intent

Allow Service Advisors to review complete invoice details for a completed work order and request controlled finalization — enforcing permissions, amount thresholds, approvals, and immutable state transitions — then post finalized invoices to accounting with auditability and reliable error handling.

---

## Actors & Stakeholders

- Service Advisor
- Billing System (system of record)
- Accounting System
- Shop Manager
- Work Execution Service
- Audit Service
- Customer

---

## Preconditions

1. Invoice exists and is linked to a completed work order
2. Invoice is in DRAFT/EDITABLE state and data-complete
3. Service Advisor has `FINALIZE_INVOICE` permission (subject to amount limits)
4. Taxes/fees calculated and customer account valid
5. Accounting integration available (async posting supported)

---

## Functional Behavior (Summary)

1. Display invoice (items, taxes, fees, totals) for review.
2. Validate eligibility: invoice status, data completeness, work order completion, customer account, and duplicate posting prevention.
3. Enforce permissions and amount limits; require manager approval for amounts above role limits.
4. Submit FinalizeRequest; billing transitions invoice DRAFT → FINALIZED and emits `InvoiceFinalized` event.
5. Accounting posts GL entries asynchronously; Billing updates invoice to POSTED or ERROR accordingly.
6. Finalized invoices are read-only; reversion is a separate, controlled operation.

---

## Resolved Decisions (from comments)

- Controlled finalization enforced via role-based permission `FINALIZE_INVOICE`, amount thresholds (Service Advisor ≤ $500, Shop Manager unlimited), and manager approval workflow for overrides. All requests and approvals are audited.
- Finalization is the mandatory gate before payment and is one-way except for a controlled revert: reversion allowed within 24 hours and before POSTED with manager approval; POSTED invoices require accounting reversal.
- Accounting integration is asynchronous and event-driven (`InvoiceFinalized`), idempotent by `invoiceId`, with retries (5 min, up to 24 hours) and alerting after repeated failures; successes emit `InvoicePosted` with GL entry ID.
- Data integrity failures (missing prices/quantities, mismatched totals/taxes) are hard rejects — no override allowed; invoice must be corrected in DRAFT/EDITABLE state.
- Error handling: automatic retries, alerts to accounting after failures, manual retry available, SLA target: 95% posted within 1 hour.

---

## Business Rules

- Finalization eligibility: invoice in DRAFT/EDITABLE, linked work order COMPLETED, data-complete, customer account valid, not already posted.
- Permission matrix: Service Advisor (FINALIZE_INVOICE ≤ $500), Shop Manager (FINALIZE_INVOICE unlimited), manager override audited.
- Data validation is mandatory and non-overridable for integrity.
- Finalization locks invoice and prevents edits; reversion is controlled and audited.
- Accounting posting is async and idempotent; GL linkage recorded.

---

## Data Models (excerpt)

- InvoiceView: invoiceId, workOrderId, customer, lineItems, taxes, fees, subtotal, grandTotal, invoiceStatus, finalizableReason, glEntryId, finalizedBy, finalizedAt.
- FinalizeRequest: invoiceId, requestedBy, requestedAt, permissionLevel, amountLimit, managerApprovalRequired, managerApprovalCode, overrideReason.
- AuditLog: auditId, eventType, invoiceId, actorId, invoiceAmount, permissionLevel, managerApprovalUsed, glEntryId, timestamp, notes.

---

## Acceptance Criteria (key)

- AC1: Service Advisor can view full invoice details (items, taxes, fees, totals) for a completed work order.
- AC2: Finalization eligibility checks run and block finalization when data incomplete or work order not complete.
- AC3: Permission and amount limits enforced; manager approval required for overrides and logged.
- AC4: FinalizeRequest transitions invoice DRAFT → FINALIZED and emits `InvoiceFinalized` event.
- AC5: Accounting posts GL entries asynchronously; Billing marks POSTED with GL entry ID or ERROR with retry/alerts.
- AC6: Finalized invoice is read-only; revert-to-draft requires manager approval within 24 hours (pre-POSTED).

---

## Audit & Observability

Emit InvoiceFinalizationRequested, InvoiceFinalizationPermissionDenied, InvoiceFinalizationManagerApprovalRequested, InvoiceFinalized, InvoicePostedToGL, InvoicePostingFailed, InvoiceFinalizationOverridden. Track metrics: finalization success rate, posting latency, posting failure rate, amount override rate, permission denial rate.

---

## Original Story (for traceability)

Original issue content and comments retained in issue history: https://github.com/louisburroughs/durion-positivity-backend/issues/13
