Title: [BACKEND] [STORY] Invoicing: Finalize and Issue Invoice
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/145
Labels: type:story, domain:accounting, domain:billing, status:ready-for-dev, agent:story-authoring, agent:accounting, agent:billing, risk:financial-integrity

## Story Intent
**As an** Accounts Receivable Clerk,
**I want to** finalize and issue a draft invoice,
**so that** it becomes a locked, official billing record and downstream accounting can create AR and post appropriately.

**Rewrite Variant:** integration-conservative

## Actors & Stakeholders
- **Accounts Receivable Clerk:** Initiates issuance.
- **Billing Service (`domain:billing`):** System of record (SoR) for the `Invoice` entity and its lifecycle/state machine.
- **Accounting Service (`domain:accounting`):** Creates Accounts Receivable asynchronously upon `InvoiceIssued`.
- **Customer:** Receives the final invoice.
- **Auditor / Compliance:** Requires immutable history and non-repudiation.

## Preconditions
1. An `Invoice` exists in state `Draft`.
2. The invoice is associated with a valid `Customer` with required billing/delivery attributes.
3. The invoice contains one or more `InvoiceLine` items.
4. Upstream source domains (e.g., `workexec`) have completed billable work and have produced inputs needed for a draft invoice.
5. The initiating principal is authorized to issue invoices (existing permission concept: `invoice:issue`).

## Functional Behavior
### Happy Path: Successful Invoice Issuance
1. **Trigger:** Accounts Receivable Clerk initiates “Issue Invoice” for a `Draft` invoice.
2. **Validation (Billing-owned):** Validate all issuance rules (see Business Rules), including customer data completeness and invoice total consistency.
3. **Finalization + State Transition (Billing-owned, single transaction):**
   - Assign an `invoiceNumber` if not present (unique; sequential behavior is policy-driven).
   - Set `issuedAt` to server time and capture `issuedBy`.
   - Transition invoice state to `Issued`.
   - Enforce immutability: once `Issued`, invoice header/lines/totals cannot be edited.
4. **Event Emission (Billing-owned):** Publish `InvoiceIssued` reliably after commit (e.g., outbox pattern).
   - `InvoiceIssued.sourceDomain` is `"billing"`.
5. **Delivery:** Queue the finalized invoice document for delivery according to the customer’s `DeliveryPreference` (email/print).

## Alternate / Error Flows
- **Validation failure:** Reject issuance, keep invoice in `Draft`, and return actionable errors detailing what must be corrected.
- **Invalid state (duplicate issuance attempt):** If invoice is not `Draft` (e.g., `Issued`, `Paid`, `Void`), reject with `409 Conflict`.
- **Downstream unavailability:** Billing issuance succeeds even if the event bus is temporarily unavailable; `InvoiceIssued` is queued for later delivery.

## Business Rules
1. **Domain Ownership / SoR:** `domain:billing` owns the `Invoice` entity and its lifecycle/state transitions.
2. **Workexec contract:** `workexec` signals “ready for invoicing” / eligibility and does not directly issue invoices.
   - If `workexec` needs to initiate issuance, it calls a Billing command/API rather than mutating invoice state.
3. **Allowed transition:** `Draft` → `Issued` only.
4. **Immutability:** Once `Issued`, the invoice is an immutable financial/billing record; corrections occur via out-of-scope credit/rebill processes.
5. **Issuance validations (must pass before issuing):**
   - Customer has `billingAddress`.
   - If delivery is Email, `emailAddress` is present and valid.
   - Totals are internally consistent: header total equals the sum of lines/taxes/discounts per system calculation rules.
   - Each line has quantity > 0 and non-negative unit price.
   - Tax calculations are finalized per applicable tax rules.

## Data Requirements
| Entity / Event | Field | Type | Notes |
|---|---|---|---|
| `Invoice` | `status` | Enum/String | `Draft` → `Issued` |
| `Invoice` | `invoiceNumber` | String | Unique business key |
| `Invoice` | `issuedAt` | Timestamp | Set on issue |
| `Invoice` | `issuedBy` | String/UUID | Principal ID |
| `InvoiceIssued` | `eventId`, `eventTimestamp` | UUID/ISO8601 | Unique + timestamp |
| `InvoiceIssued` | `sourceDomain` | String | Must be `"billing"` |
| `InvoiceIssued` | `idempotencyKey` | String | `invoiceId:invoiceVersion` (or equivalent) |
| `InvoiceIssued` | `payload` | JSON | Full invoice snapshot (header, customer, totals, taxes, lines) |
| `AccountsReceivable` | created by accounting | — | Created asynchronously by `domain:accounting` on `InvoiceIssued` |

## Acceptance Criteria
**Scenario 1: Successful invoice issuance**
- Given an invoice in `Draft` with all required data
- When the user issues the invoice
- Then invoice state becomes `Issued`
- And `invoiceNumber`, `issuedAt`, and `issuedBy` are set
- And the invoice becomes immutable
- And `InvoiceIssued` is published exactly once (idempotent)

**Scenario 2: Missing billing address blocks issuance**
- Given a `Draft` invoice where the customer has no billing address
- When the user attempts to issue
- Then the operation fails with actionable validation errors
- And the invoice remains `Draft`
- And no `InvoiceIssued` event is emitted

**Scenario 3: Re-issuing an already issued invoice is rejected**
- Given an invoice in `Issued`
- When issuance is attempted again
- Then the system returns `409 Conflict`
- And no duplicate `InvoiceIssued` event is emitted

**Scenario 4: Accounting creates AR asynchronously from InvoiceIssued**
- Given billing emits `InvoiceIssued`
- When accounting consumes the event
- Then accounting creates an `AccountsReceivable` entry idempotently (no duplicates on replay)

## Audit & Observability
- **Audit log:** Immutable audit entry on successful issuance capturing `invoiceId`, `invoiceNumber`, `issuedBy`, `issuedAt`, and key totals.
- **Metrics:**
  - `invoices_issued_total`
  - `invoice_issuance_failures_total{reason=...}`
  - `invoice_issuance_duration_seconds`
- **Logging:** Structured logs with `invoiceId` and `correlationId` for start/end/failure.

## Resolved Decisions (from issue comments)
These decisions were applied from the resolution comment posted on 2026-01-14 ("Decision Record — Issue #145", generated by `clarification-resolver.sh`):
1. **SoR:** `domain:billing` owns `Invoice` and its lifecycle.
2. **Workexec contract:** `workexec` signals readiness only; `billing` performs `Draft → Issued`.
3. **Event authority:** `InvoiceIssued.sourceDomain` must be `"billing"`.
4. **AR creation:** handled asynchronously in `domain:accounting` by consuming `InvoiceIssued` (idempotent).

---
## Original Story (Unmodified – For Traceability)
# Issue #145 — [BACKEND] [STORY] Invoicing: Finalize and Issue Invoice

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Invoicing: Finalize and Issue Invoice

**Domain**: payment

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300023/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Back Office / Accounts Receivable Clerk

## Trigger
Draft invoice is reviewed and ready to be issued.

## Main Flow
1. User reviews invoice totals and traceability links.
2. User selects 'Issue Invoice'.
3. System validates invoice completeness (customer details, taxes, totals, traceability).
4. System transitions invoice to Issued/Posted state per workflow.
5. System locks invoice lines and records issuance audit event; prepares delivery (email/print) per preference.

## Alternate / Error Flows
- Validation fails (missing billing info) → block issuance and show actionable errors.
- Invoice already issued → prevent duplicate issuance.

## Business Rules
- Issuance is a state transition with validations and locking.
- Issued invoice should be immutable except via credit/rebill (out of scope).

 ## Data Requirements
  - Entities: Invoice, Customer, AuditEvent, DeliveryPreference
  - Fields: status, issuedAt, issuedBy, deliveryMethod, emailAddress, billingAddress

## Acceptance Criteria
- [ ] Invoice can be issued only when validations pass.
- [ ] Issued invoice is locked against edits.
- [ ] Issuance is auditable and invoice is prepared for delivery.
- [ ] InvoiceIssued event is emitted exactly once per invoice version
- [ ] Event includes full line-item, tax, and total breakdown
- [ ] Accounts Receivable is created correctly
- [ ] Revenue and tax liabilities post accurately
- [ ] Duplicate or replayed events do not double-post

## Integrations

### Accounting
- Emits Event: InvoiceIssued
- Event Type: Posting
- Source Domain: workexec
- Source Entity: Invoice
- Trigger: Invoice finalized and issued
- Idempotency Key: invoiceId + invoiceVersion


## Notes for Agents
Issuance ends quote-to-cash; protect the integrity and lock the record.


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