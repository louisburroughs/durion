Title: [BACKEND] [STORY] Invoicing: Support Authorized Invoice Adjustments
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/146
Labels: type:story, domain:accounting, status:ready-for-dev, agent:story-authoring, agent:accounting

## Story Intent
**As a** Back Office Manager,
**I want to** apply *authorized* adjustments to a **Draft** invoice (edit line items and/or apply invoice-level discounts),
**so that** I can correct errors or apply goodwill while keeping accounting state correct and fully auditable.

**Rewrite Variant:** integration-conservative

## Actors & Stakeholders
- **Back Office Manager:** Initiates and authorizes adjustments.
- **Accounting Service (`domain:accounting`):** System of record (SoR) for invoice financial state and rules.
- **Workorder Execution (`domain:workexec`):** Produces invoice inputs and may initiate an adjustment request, but does not own financial recalculation rules.
- **Auditor / Compliance:** Needs immutable, explainable history of adjustments.
- **Downstream Accounting/Posting consumers:** Subscribe to `InvoiceAdjusted` / `CreditMemoIssued` events.

## Preconditions
1. An invoice exists and is in status **`Draft`**.
2. The user is authenticated and authorized to perform invoice adjustments (existing permission concept: `invoice.adjust`).
3. Valid `ReasonCode` values for invoice adjustments exist and can be validated as active.

## Functional Behavior
1. **Trigger:** Back Office Manager initiates an adjustment on a **Draft** invoice.
2. **Input:** User submits one or more changes:
   - Line item change(s) (quantity, price, discount)
   - Invoice-level discount
   - Optional `reasonCode` and free-text `justification` (required when configured)
3. **Validation (Accounting-owned):**
   - Validate invoice is still `Draft` at commit time.
   - Validate authorization for invoice adjustment.
   - If configured, require and validate `reasonCode` (active) and require `justification`.
4. **Recalculation (Accounting-owned):**
   - Recalculate subtotal, taxes, and grand total from the adjusted invoice content.
5. **Negative Total Guardrail:**
   - If the recalculated grand total would be **< $0.00**, reject the adjustment as invalid for invoices and require a Credit Memo path (details below).
   - Adjustments may reduce total down to **exactly $0.00**, but **not below**.
6. **Persistence:**
   - Persist invoice changes atomically.
   - Mark invoice as adjusted (e.g., `isAdjusted = true`).
7. **Audit:**
   - Create an immutable audit record capturing actor, timestamp, reason/justification, and before/after financial snapshots (and enough detail to reconstruct what changed).
8. **Integration Events:**
   - Emit `InvoiceAdjusted` when the adjustment is applied directly to a **Draft** invoice and the resulting total is **>= $0.00**.
   - Emit `CreditMemoIssued` when the business action results in creation/issuance of a Credit Memo.

## Alternate / Error Flows
- **Unauthorized adjustment attempt:** Reject with `403 Forbidden`. No invoice mutation, no audit record, no event.
- **Invoice not in Draft:** Reject direct adjustment and require Credit Memo or other formally defined reversal/credit process. Return `409 Conflict` (or `422 Unprocessable Entity`) with a machine-readable error.
- **Adjustment would cause negative invoice total:** Reject with `409 Conflict` or `422 Unprocessable Entity` and error code `INVOICE_TOTAL_NEGATIVE_REQUIRES_CREDIT_MEMO`.
  - Optional (explicit mode): Support a request mode that *splits* into:
    1) adjust invoice down to `$0.00`, and
    2) create a Credit Memo for the remaining absolute value.
- **Missing/invalid reason code when required:** Reject with `400 Bad Request`.
- **Concurrent modification:** Detect via optimistic locking and reject with `409 Conflict`, prompting reload.

## Business Rules
1. **Domain Ownership / SoR:** `domain:accounting` owns invoice financial state and all recalculation/adjustment rules.
2. **Draft-only direct adjustments:** Only `Draft` invoices may be directly adjusted.
3. **Never allow negative invoice totals:** Resulting invoice total must be $\ge 0.00$.
4. **Credit Memo required for over-credit:** If the requested change would push below $0.00$, create a **Credit Memo** instead of allowing a negative invoice.
   - Requires separate authorization (Credit Memo permission concept; exact permission name TBD by Security/Authorization conventions).
5. **Audit is mandatory:** Every successful adjustment produces an immutable audit record.
6. **Idempotency:** Adjustment commands and resulting events must be deduplicated (e.g., idempotency key based on `invoiceId + auditEventId` or `invoiceId + adjustmentId`).

## Data Requirements
| Entity / Event | Field | Type | Notes |
|---|---|---|---|
| `Invoice` | `isAdjusted` | boolean | Defaults `false`; set `true` after first adjustment |
| `InvoiceAuditEvent` | `invoiceId`, `actorId`, `timestamp` | UUID/Timestamp | Immutable record |
| `InvoiceAuditEvent` | `reasonCode`, `justification` | String/Text | Required when configured |
| `InvoiceAuditEvent` | `changeDetails` | JSON | Before/after totals + changed line items summary |
| `InvoiceAdjusted` | `invoiceId`, `adjustmentId` | UUID | `adjustmentId` is idempotency anchor |
| `InvoiceAdjusted` | `previousTotals`, `newTotals` | JSON/structured | Include tax/subtotal/total |
| `CreditMemo` | `creditMemoId`, `sourceInvoiceId` | UUID | Separate accounting document/aggregate |
| `CreditMemoIssued` | `creditMemoId`, `sourceInvoiceId`, `amount` | UUID/Decimal | Emitted when credit memo created/issued |

## Acceptance Criteria
**Scenario 1: Successful Draft Invoice Adjustment (non-negative)**
- Given a `Draft` invoice
- And a Back Office Manager authorized for invoice adjustments
- And the system requires `reasonCode` + `justification`
- When the user applies an adjustment that results in $\text{newTotal} \ge 0.00$
- Then the invoice totals are recalculated correctly (subtotal/tax/total)
- And `isAdjusted = true`
- And an immutable audit record is written with before/after snapshots and reason/justification
- And an `InvoiceAdjusted` event is emitted

**Scenario 2: Unauthorized user attempts adjustment**
- Given a `Draft` invoice
- When a user without invoice adjustment authorization attempts any change
- Then the system returns `403`
- And no data is mutated
- And no audit/event is created

**Scenario 3: Adjustment would cause negative invoice total**
- Given a `Draft` invoice
- When the requested adjustment would make $\text{newTotal} < 0.00$
- Then the system rejects the request with `409` or `422`
- And the response includes error code `INVOICE_TOTAL_NEGATIVE_REQUIRES_CREDIT_MEMO`
- And the invoice remains unchanged

**Scenario 4: Non-Draft invoice cannot be directly adjusted**
- Given an invoice that is not `Draft`
- When a user attempts a direct adjustment
- Then the system rejects the request and instructs to use Credit Memo / reversal process
- And no `InvoiceAdjusted` event is emitted

**Scenario 5: Credit Memo issuance path emits event**
- Given a request that results in a Credit Memo creation/issuance
- When the system issues the Credit Memo
- Then a `CreditMemoIssued` event is emitted containing `creditMemoId`, `sourceInvoiceId` (if applicable), and amount

## Audit & Observability
- **Audit log:** `InvoiceAuditEvent` is immutable and contains sufficient detail to explain changes (before/after + actor + reason).
- **Application logs:**
  - `INFO` on success (`invoiceId`, `actorId`, `adjustmentId`)
  - `WARN` on business-rule rejects (negative total, missing reason code, not-draft)
  - `ERROR` on unauthorized attempts

## Resolved Decisions (from issue comments)
These decisions were applied from the resolution comment posted on 2026-01-14 ("Answers to Open Questions", generated by `clarification-resolver.sh`):
1. **Domain ownership:** `domain:accounting` is SoR for invoice financial state.
2. **Negative totals:** never allow negative invoice totals; use Credit Memo instead.
3. **Events:** `InvoiceAdjusted` for direct non-negative Draft adjustments; `CreditMemoIssued` whenever a Credit Memo is created/issued; implies a first-class `CreditMemo` entity/document.

---
## Original Story (Unmodified – For Traceability)
# Issue #146 — [BACKEND] [STORY] Invoicing: Support Authorized Invoice Adjustments

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Invoicing: Support Authorized Invoice Adjustments

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300028/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Back Office Manager

## Trigger
A Draft invoice requires an adjustment (goodwill discount, correction).

## Main Flow
1. Authorized user edits invoice line items or applies a discount.
2. System requires a reason code and free-text justification if configured.
3. System recalculates taxes and totals.
4. System records adjustment audit event including before/after values.
5. System flags invoice as adjusted for reporting.

## Alternate / Error Flows
- Unauthorized user attempts adjustment → block.
- Adjustment would cause negative totals → block or require special permission.

## Business Rules
- Adjustments require permissions and audit trail.
- Adjustments must not break traceability; they must be explainable.

## Data Requirements
- Entities: Invoice, InvoiceItem, AuditEvent, ReasonCode
- Fields: adjustmentType, reasonCode, justification, beforeTotal, afterTotal, adjustedBy, adjustedAt

## Acceptance Criteria
- [ ] Only authorized roles can adjust invoices.
- [ ] Adjustments require reason codes and are auditable.
- [ ] Totals are recalculated correctly after adjustments.
- [ ] Invoice adjustments emit a corresponding accounting event
- [ ] Revenue, tax, and AR are adjusted correctly
- [ ] Adjustments reference the original invoice
- [ ] Authorization and reason code are required
- [ ] Multiple adjustments do not corrupt invoice totals

## Integrations

### Accounting
- Emits Event: InvoiceAdjusted or CreditMemoIssued
- Event Type: Posting (reversal / amendment)
- Source Domain: workexec
- Source Entity: Invoice
- Trigger: Authorized adjustment or credit issuance
- Idempotency Key: invoiceId + adjustmentVersion


## Notes for Agents
Keep adjustments rare and transparent; otherwise you erode trust in the system.


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*