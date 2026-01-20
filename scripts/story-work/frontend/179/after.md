STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Accounting: Ingest PaymentReceived Event (Ops UI + Work Queue)

### Primary Persona
Accounting Ops User (Accountant / Accounting Clerk)

### Business Value
Provide an operational UI to monitor and troubleshoot `PaymentReceived` event ingestion outcomes (success/duplicate/rejected), and to work “unapplied/unassigned” payments, enabling timely reconciliation and reducing support burden while preserving auditability and idempotency guarantees.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Accounting Ops User  
- **I want** a UI to view ingested `PaymentReceived` events and resulting `Payment` records (including duplicates and failures) and to triage unapplied/unassigned payments  
- **So that** I can reconcile cash receipts, identify issues (schema/currency/customer mismatches), and maintain traceable accounting records without needing direct database access.

### In-scope
- New/updated Moqui screens to:
  - List and view `Payment` records created from `PaymentReceived` events.
  - Provide an “Unapplied & Unassigned Payments” work queue (filters, drilldown).
  - Display source metadata: `externalTransactionId`, `sourceSystem`, `receivedTimestamp`, `currency`, `paymentMethod`, and stored `sourceEventPayload` (read-only).
  - Display ingestion outcome signals surfaced by backend (success/duplicate/rejected/quarantined) **if available**.
- Frontend actions limited to:
  - Search/filter/sort
  - View details
  - (Optional only if backend exists) manually associate a `customerId` for unassigned payments.

### Out-of-scope
- Building the ingestion pipeline itself (message broker consumption).
- Creating or changing accounting posting rules, GL mappings, journal entry logic (backend-owned).
- Applying payments to invoices / AR reduction (explicitly out-of-scope per business rules).
- Editing immutable payment financials (amount/currency/method) after ingestion.

---

## 3. Actors & Stakeholders

- **Primary Actor:** Accounting Ops User
- **Secondary Actors:**
  - Support/On-call Engineer (uses UI to diagnose ingestion failures)
  - Controller/Auditor (views traceability / audit fields)
- **System Stakeholders/Integrations (context):**
  - External payment source(s) emitting `PaymentReceived`
  - Accounting backend that persists Payment + JournalEntry and enforces idempotency

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the frontend and authorized to access accounting payments screens.
- Backend persists `Payment` records with fields described in the provided backend reference (or equivalent).

### Dependencies (blocking unless confirmed)
- Backend endpoints exist for:
  - Listing payments with filters (status, customer assigned/unassigned, date range, currency, sourceSystem, externalTransactionId)
  - Fetching payment details including `sourceEventPayload`
  - (Optional) Updating `customerId` for payments in `UNAPPLIED` state (if permitted)
- Backend provides a way to surface ingestion failures/duplicates (could be via a separate “event ingestion log” entity/service). If not available, UI will only show persisted `Payment` records (successful ingestions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Left nav (Accounting) → **Payments**
  - **Payments List**
  - **Unapplied & Unassigned Queue** (pre-filtered list view)

### Screens to create/modify
- `apps/accounting/screen/PaymentList.xml` (new)
- `apps/accounting/screen/PaymentDetail.xml` (new)
- `apps/accounting/screen/UnappliedPaymentQueue.xml` (new; can reuse PaymentList with preset parameters)
- Optional: `apps/accounting/screen/PaymentCustomerAssignDialog.xml` (inline modal pattern) or embedded form in detail screen.

### Navigation context
- `PaymentList` → click row → `PaymentDetail?paymentId=...`
- From `UnappliedPaymentQueue` → `PaymentDetail`
- `PaymentDetail` includes related links (read-only) to:
  - Customer detail (if `customerId` exists)
  - Journal Entry detail (if backend exposes reference)

### User workflows
**Happy path (ops review):**
1. User opens Payments List
2. Filters by date range and/or sourceSystem
3. Opens a payment detail
4. Confirms status `UNAPPLIED`, reviews payload/metadata, and (if allowed) assigns customer

**Alternate path (duplicate investigation):**
1. User searches by `externalTransactionId`
2. UI shows existing payment record (only one)
3. If backend exposes ingestion log, user sees duplicate attempts listed as non-mutating events

**Alternate path (unknown customer):**
1. User opens Unapplied & Unassigned Queue (customerId null)
2. Opens payment detail
3. Assigns customer (if supported) and saves; payment remains `UNAPPLIED` (application to invoices is out-of-scope)

---

## 6. Functional Behavior

### Triggers
- User navigates to Payments screens.
- User submits filters/search.
- User opens a payment detail.
- (Optional) User submits “Assign Customer” action.

### UI actions
- **List view actions**
  - Filter by:
    - `status` (default: all)
    - `customerAssigned` (assigned/unassigned)
    - `receivedTimestamp` range (required default: last 30 days if supported as safe UX default)
    - `currency`
    - `paymentMethod`
    - `sourceSystem`
    - `externalTransactionId` exact match
  - Pagination and sortable columns.

- **Detail view actions**
  - View read-only fields (see Data Requirements)
  - Expand/collapse `sourceEventPayload` JSON viewer (read-only)
  - (Optional) Assign customer:
    - Customer lookup control (search by name/email/phone/id per backend capability)
    - Save association

### State changes (frontend-visible)
- None for ingestion itself.
- Optional customer assignment changes `Payment.customerId` only.
- No UI action changes `Payment.status` in this story.

### Service interactions
- Load list and detail via Moqui transitions calling backend services (see Service Contracts).
- On optional customer assignment: call update service and refresh detail.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Filters:
  - `receivedTimestampFrom` must be <= `receivedTimestampThru` (client-side validation).
- Customer assignment (if supported):
  - Only allowed when `Payment.status == UNAPPLIED`.
  - Only allowed if payment is not marked immutable due to posting lock (backend should enforce; UI should disable if backend indicates not allowed).

### Enable/disable rules
- Disable “Assign Customer” action when:
  - user lacks permission
  - `customerId` already set (unless “Change” is explicitly allowed; see Open Questions)
  - payment status is not `UNAPPLIED`

### Visibility rules
- Show `sourceEventPayload` only to authorized roles (may contain sensitive data); otherwise show “Restricted”.
- Show journal entry link only if backend provides a `journalEntryId`/reference.

### Error messaging expectations
- Display backend error codes/messages verbatim but user-friendly:
  - `UNAUTHORIZED` → “You do not have access to this action.”
  - `VALIDATION_ERROR` → show field-level messages
  - `NOT_FOUND` → “Payment not found or you no longer have access.”
  - `CONFLICT/OPTIMISTIC_LOCK` → “This payment was updated by someone else. Refresh and try again.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `Payment` (accounting-owned)
- `Customer` (CRM/people-owned; referenced read-only except lookup)
- Optional: `JournalEntry` reference (accounting-owned, read-only link)
- Optional: `PaymentIngestionLog` / `AccountingEventIngestion` (if backend exposes)

### `Payment` fields to display
| Field | Type | Required | Editable | Notes |
|---|---|---:|---:|---|
| `paymentId` | UUID | yes | no | Primary identifier |
| `status` | Enum | yes | no | Expect at least `UNAPPLIED`, `APPLIED` (others TBD) |
| `amount` | Decimal(19,4) | yes | no | Display with currency |
| `currency` | ISO 4217 string(3) | yes | no | |
| `paymentMethod` | Enum | yes | no | Values TBD; display label |
| `receivedTimestamp` | UTC timestamp | yes | no | Display in user timezone, store UTC |
| `externalTransactionId` | string | yes | no | Search key; unique |
| `sourceSystem` | string | yes | no | |
| `customerId` | UUID nullable | no | **optional** | Editable only if policy allows |
| `sourceEventPayload` | JSON | yes | no | Read-only; restricted visibility |
| Standard audit | `createdAt/By`, `updatedAt/By` | yes | no | Display on detail |

### Derived/calculated fields (UI-only)
- `customerAssigned` boolean = (`customerId` != null)
- “Age” = now - receivedTimestamp (display)

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact service names/routes must align with `durion-moqui-frontend` conventions; define placeholders and require confirmation if unknown.

### Load/list calls
- `GET /accounting/payments` (or Moqui screen transition calling service like `accounting.PaymentServices.listPayments`)
  - Request params: filters described above, `pageIndex`, `pageSize`, `sortField`, `sortOrder`
  - Response: list of Payment summaries + total count

### View/detail calls
- `GET /accounting/payments/{paymentId}` (or `accounting.PaymentServices.getPayment`)
  - Response: full Payment incl. `sourceEventPayload` (if authorized)

### Update calls (optional)
- `POST /accounting/payments/{paymentId}/assignCustomer` (or `accounting.PaymentServices.assignCustomerToPayment`)
  - Request: `paymentId`, `customerId`, optional `reason` (if required by audit policy; see Open Questions)
  - Response: updated Payment

### Error handling expectations
- Map HTTP 401/403 → route to login or show unauthorized banner; disable actions.
- 404 → show not found screen with back link.
- 409 → show conflict toast and refresh option.
- Validation errors: render field messages.

---

## 10. State Model & Transitions

### Allowed states (Payment)
- `UNAPPLIED` (created on ingestion)
- `APPLIED` (exists but out-of-scope to transition here)
- Others (e.g., `REVERSED`, `VOID`) are unknown → do not assume.

### Role-based transitions (UI)
- No status transitions in this story.
- Optional “Assign Customer” is a *mutation* but not a state transition:
  - Allowed roles/permissions: **TBD** (see Open Questions)

### UI behavior per state
- `UNAPPLIED`: show “Unapplied” badge; show queue inclusion.
- `APPLIED`: read-only; hide queue inclusion; assignment disabled.

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid date range filter → block submission; show inline error.

### Concurrency conflicts
- If assignment save returns 409/optimistic lock:
  - show conflict message
  - reload payment detail
  - user re-attempts if still applicable

### Unauthorized access
- If user lacks permission to view payload:
  - hide payload section and show “Restricted”
- If user lacks permission to assign customer:
  - do not render action, or render disabled with tooltip.

### Empty states
- Payments list returns 0 results:
  - show “No payments found” with “Clear filters”.
- Unapplied/unassigned queue empty:
  - show “No unassigned payments.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View payments list with filters
**Given** an Accounting Ops User with permission to view payments  
**When** the user opens the Payments List screen  
**Then** the system displays a paginated list of payments with columns including amount, currency, status, received timestamp, source system, and external transaction id  
**And** the user can filter by received date range and source system  
**And** the results update to match the filters.

### Scenario 2: Drill into payment details with traceability
**Given** a payment exists with an `externalTransactionId` and stored `sourceEventPayload`  
**When** the user opens the Payment Detail screen for that payment  
**Then** the system displays all read-only payment fields including `externalTransactionId`, `sourceSystem`, and `receivedTimestamp`  
**And** the system displays the `sourceEventPayload` as read-only JSON **when the user is authorized**.

### Scenario 3: Restricted payload access
**Given** a user who can view payments but is not authorized to view `sourceEventPayload`  
**When** the user opens the Payment Detail screen  
**Then** the system does not render the payload content  
**And** the UI indicates the payload is restricted.

### Scenario 4 (Optional): Assign customer to an unassigned unapplied payment
**Given** a payment exists with `status` = `UNAPPLIED` and `customerId` is null  
**And** the user has permission to assign a customer  
**When** the user selects a customer and submits the assignment  
**Then** the payment is updated to include the selected `customerId`  
**And** the payment remains in `UNAPPLIED` status  
**And** the change is reflected after refresh.

### Scenario 5: Prevent customer assignment when not allowed
**Given** a payment exists with `status` != `UNAPPLIED`  
**When** the user views the payment detail  
**Then** the “Assign Customer” action is not available (or disabled)  
**And** attempting the action (if forced) returns an authorization/state error and the UI shows a clear message.

---

## 13. Audit & Observability

### User-visible audit data
- Show `createdAt`, `createdBy`, `updatedAt`, `updatedBy` on Payment Detail.
- If backend provides ingestion timestamps/status (e.g., processedAt, ingestionStatus), display read-only.

### Status history
- If backend exposes status history for payment (not specified), show in a timeline; otherwise omit.

### Traceability expectations
- Payment Detail must prominently display:
  - `externalTransactionId` (copyable)
  - `sourceSystem`
  - correlation id / event id **if present in payload or explicit field** (not assumed)
- All frontend transitions should include `paymentId` in logs (Moqui server logs) and UI errors.

---

## 14. Non-Functional UI Requirements

- **Performance:** Payments list should load within 2s for typical page sizes (e.g., 25–50) under normal conditions; server-side pagination required.
- **Accessibility:** Keyboard navigable table; proper labels for filter inputs; JSON viewer accessible (expand/collapse).
- **Responsiveness:** List and detail usable on tablet widths; columns may collapse to stacked fields.
- **i18n/timezone/currency:**
  - Display timestamps in user locale/timezone; store/transport UTC.
  - Display money with currency code; do not assume symbol mapping beyond currency code.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide standardized empty-state messaging with “Clear filters” action; safe because it doesn’t alter business logic. (Impacted: UX Summary, Alternate / Error Flows)
- SD-UX-PAGINATION: Use server-side pagination with default page size 25; safe UI ergonomics only. (Impacted: UX Summary, Service Contracts, Acceptance Criteria)

---

## 16. Open Questions

1. **Backend API contract (blocking):** What are the exact Moqui service names and screen paths (or REST endpoints) for listing and retrieving `Payment` records in `durion-moqui-frontend` conventions?
2. **Permission model (blocking):** What permissions/roles control:
   - viewing payments list/detail,
   - viewing `sourceEventPayload`,
   - assigning/changing `customerId`?
3. **Customer assignment policy (blocking):** Is manual association of `customerId` allowed for ingested payments? If yes, is “change customer after set” allowed, and is a reason/justification required for audit?
4. **Ingestion outcome visibility (blocking):** Is there an entity/service to view duplicates, rejected events, DLQ/quarantine status, or ingestion logs? If yes, what fields and filters should be exposed in the UI?
5. **Currency mismatch handling visibility (blocking):** If currency mismatch causes rejection/DLQ, should the UI expose these rejected events (even though no Payment record exists), or is UI limited to successfully persisted payments only?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Accounting: Ingest PaymentReceived Event  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/179  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest PaymentReceived Event

**Domain**: payment

### Story Description

/kiro  
Focus on cash recognition, AR reduction, and idempotency.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `PaymentReceived` event from an external payment source  
(e.g., POS terminal, bank feed, payment processor, manual entry)

## Main Flow
1. Receive payment event with amount, currency, method, and reference(s)
2. Validate event schema and idempotency key
3. Identify target customer and candidate open invoices
4. Record cash receipt in appropriate cash/bank account
5. Create unapplied payment record or proceed to invoice application
6. Persist payment with full source metadata

## Alternate / Error Flows
- Duplicate event → ignore (idempotent)
- Unknown customer or reference → create unapplied payment
- Currency mismatch → reject or flag for review
- Posting failure → retry or dead-letter

## Business Rules
- Payment receipt reduces cash suspense or increases cash immediately
- Payment does not reduce AR until applied to invoice(s)
- Idempotency is enforced per external transaction reference

## Data Requirements
- Entities: Payment, CashAccount, Customer
- Fields: amount, currency, method, receivedTimestamp, externalTxnId

## Acceptance Criteria
- [ ] Cash/bank balance increases correctly
- [ ] Payment is recorded exactly once
- [ ] Unapplied payments are visible and traceable
- [ ] Payment references external source transaction

## References
- Durion Accounting Event Contract v1

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299815/Durion_Accounting_Event_Contract_v1.pdf)

### Frontend Requirements
- Implement Vue.js 3 components with TypeScript
- Use Quasar framework for UI components
- Integrate with Moqui Framework backend
- Ensure responsive design and accessibility

### Technical Stack
- Vue.js 3 with Composition API
- TypeScript 5.x
- Quasar v2.x
- Moqui Framework integration

---
*This issue was automatically created by the Durion Workspace Agent*