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

STOP: Clarification required before finalization

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] Accounting: Ingest `InvoiceIssued` Event (UI for Ingestion Status, Idempotency, and Posting References)

**Primary Persona:** Accounting Ops / Finance Admin (with operational responsibility for accounting integrations)

**Business Value:** Provide visibility and operational control for `InvoiceIssued` event ingestion so Accounting can confirm idempotent processing, diagnose failures (schema/CoA mapping), and trace resulting financial postings (AR/revenue/tax liabilities) without backend access.

---

## 2. Story Intent

**As an** Accounting Ops user  
**I want** a Moqui/Quasar UI to view and troubleshoot `InvoiceIssued` event ingestion results (processed/duplicate/rejected), including idempotency keys and posting references  
**So that** I can ensure invoices post exactly once, quickly resolve ingestion errors, and provide traceability for audit and reconciliation.

### In-scope
- Moqui screens to:
  - List ingested `InvoiceIssued` events and their processing status
  - View event details (envelope + key invoice financial fields relevant to posting)
  - View idempotency determination (`invoiceId + invoiceVersion`, and/or `eventId`)
  - View posting references created by ingestion (e.g., `ledgerTransactionId`, journal entry references) **as read-only**
  - Basic operational actions **only if backend supports them** (e.g., retry/reprocess) — otherwise, display-only
- Standardized error rendering for validation failures and duplicates (user-facing, non-sensitive)
- Navigation entry under an Accounting/Integrations area

### Out-of-scope
- Implementing the ingestion/posting logic itself (backend responsibility)
- Creating or editing Chart of Accounts, Posting Rules, or GL mappings
- Changing invoice state or issuing invoices
- Defining GL debit/credit mappings in the UI (must come from backend results)
- DLQ management beyond surfacing a “sent to DLQ” indicator and error details provided by backend

---

## 3. Actors & Stakeholders

- **Primary actor:** Accounting Ops user
- **Secondary stakeholders:** Finance Controller, Auditors, Support Engineers
- **System actors (informational):**
  - Event source: Work Execution / Billing emitting `InvoiceIssued`
  - Accounting backend ingestion service

---

## 4. Preconditions & Dependencies

1. User is authenticated in the Moqui frontend and has permission to access Accounting integration screens (exact permission name TBD).
2. Backend provides at least one API/service endpoint to:
   - List ingestion records for `InvoiceIssued`
   - Retrieve a single ingestion record detail by identifier
3. Backend persists ingestion outcome records including:
   - Status (processed/duplicate/rejected/quarantined/etc.)
   - Idempotency key values and/or processed log reference
   - Posting references (ledger/journal identifiers) when processed
   - Error code/message when rejected
4. Event schema referenced: “Durion Accounting Event Contract v1” (PDF linked in story) — UI should display fields as provided by backend; UI must not attempt to validate schema beyond required filter inputs.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Left nav (or Accounting module menu): **Accounting → Integrations → InvoiceIssued Ingestion**
- Direct route: `/accounting/integrations/invoice-issued` (proposed; final route must match repo conventions)

### Screens to create/modify
1. **Screen: `Accounting/Integrations/InvoiceIssued/List`**
   - List/search ingestion records
   - Filter by status, date range, invoiceId, eventId
   - Open detail view
2. **Screen: `Accounting/Integrations/InvoiceIssued/Detail`**
   - Read-only details:
     - Event envelope identifiers
     - Invoice identifiers and totals
     - Revenue items and tax items summary (as provided)
     - Processing status + timestamps
     - Posting references (ledgerTransactionId, etc.) if available
     - Error details if rejected
   - Optional “Retry” action if backend supports it (see Open Questions)

### Navigation context
- Breadcrumb: Accounting → Integrations → InvoiceIssued Ingestion → (Record Detail)

### User workflows
**Happy path (monitoring processed events):**
1. User opens list screen
2. Filters for a date range and status=Processed
3. Opens a record to confirm posting references exist and credits/debits balanced flag (if provided)

**Alternate path (duplicate event):**
1. User filters status=Duplicate
2. Opens record to verify it was ignored and no new posting references were created

**Failure path (rejected/validation failure):**
1. User filters status=Rejected
2. Opens record, reviews error code/message and missing/unknown account details
3. (If supported) triggers retry after upstream fix; otherwise exports identifiers for backend investigation

---

## 6. Functional Behavior

### Triggers
- User navigates to list or detail screens
- User applies filters / pagination
- (Optional) User requests retry/reprocess for a failed ingestion record

### UI actions
- **List screen**
  - Filter form submit refreshes results
  - Clicking a row navigates to Detail screen with `ingestionId` (or equivalent key)
- **Detail screen**
  - Show status badge and processing timeline fields
  - Render sections conditionally based on status:
    - Processed/Duplicate: show idempotency evaluation and posting references
    - Rejected/Quarantined: show error details and DLQ indicator
  - (Optional) Retry button visible only for retry-eligible statuses and authorized users

### State changes (frontend)
- No local domain state; UI is read-only except optional retry action.
- UI must reflect backend state after actions (refresh detail after retry).

### Service interactions
- List call on initial load and on filter change
- Detail call on route enter
- Optional retry call that returns updated status or an async job reference

---

## 7. Business Rules (Translated to UI Behavior)

> Note: Accounting posting rules are backend-owned. UI enforces only presentation/guardrails.

1. **Idempotency visibility**
   - UI must display idempotency keys used by backend (at minimum `invoiceId` and `invoiceVersion`; also `eventId` if available).
   - For status=Duplicate, UI must clearly state “Duplicate ignored; no new postings created” (wording may vary but must be explicit).

2. **Revenue vs tax segregation visibility**
   - UI must render separate summaries for:
     - Revenue items (amount + revenueAccountCode per item if provided)
     - Tax items (amount + jurisdiction + taxLiabilityAccountCode per item if provided)

3. **Error messaging expectations**
   - If backend provides an error code (e.g., `SCHEMA_VALIDATION_FAILED`, `INGESTION_DUPLICATE_CONFLICT`, `UNKNOWN_GL_ACCOUNT`), UI must show:
     - Error code (verbatim)
     - User-safe message (verbatim from backend, but must not expose secrets)
     - Field-level details if provided (e.g., missing field list, unknown account code)

4. **Authorization/visibility**
   - If user lacks permission, screen must deny access (Moqui authz) and show standard unauthorized page.

---

## 8. Data Requirements

### Entities involved (frontend-read)
**Unclear—depends on backend/Moqui entities.** UI needs a persistent entity representing ingestion results, e.g.:
- `AccountingEventIngestion`
- `ProcessedEventLog`
- `LedgerTransaction` / `JournalEntry` references

If existing entity names differ, map accordingly.

### Fields (type, required, defaults)

**Ingestion Record (List + Detail)**
- `ingestionId` (string/UUID, required, primary key for detail routing)
- `eventType` (string, required; should equal `InvoiceIssued`)
- `schemaVersion` (string, optional)
- `eventId` (string/UUID, required)
- `occurredAt` (datetime, optional if not provided)
- `receivedAt` (datetime, required)
- `sourceModule` (string, optional)
- `invoiceId` (string, required)
- `invoiceVersion` (string/int, required)
- `issueDate` (date/datetime, optional)
- `businessUnitId` (string, optional)
- `currencyUomId` (string, optional)

**Financial summary (Detail)**
- `totalAmount` (decimal, optional but expected)
- `revenueItems[]` (array, optional)
  - `amount` (decimal)
  - `description` (string)
  - `revenueAccountCode` (string)
- `taxItems[]` (array, optional)
  - `amount` (decimal)
  - `jurisdiction` (string)
  - `taxLiabilityAccountCode` (string)

**Processing outcome**
- `processingStatus` (enum string, required; see State Model)
- `processedAt` (datetime, optional)
- `duplicateOfIngestionId` (string/UUID, optional)
- `ledgerTransactionId` (string/UUID, optional)
- `journalEntryId` (string/UUID, optional) — if applicable
- `errorCode` (string, optional)
- `errorMessage` (string, optional)
- `dlqRouted` (boolean, optional)
- `dlqRef` (string, optional)

### Read-only vs editable
- All fields read-only in UI (this story).
- Filter inputs editable: status/date/invoiceId/eventId.

### Derived/calculated fields (frontend)
- `creditsSum` / `debitsSum` **ONLY if provided by backend**. UI must not compute accounting balances as authoritative.
- Display-only label mapping for `processingStatus`.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui implementation may use `service-call` to REST endpoints or local services; exact names TBD.

### Load/view calls
1. **List ingestion records**
   - **Service:** `accounting.invoiceIssuedIngestion.list` (placeholder)
   - **Inputs:**
     - `status` (optional)
     - `fromDate` / `thruDate` (optional)
     - `invoiceId` (optional)
     - `eventId` (optional)
     - `pageIndex`, `pageSize` (optional)
     - `orderBy` (default: `-receivedAt`)
   - **Outputs:**
     - `results[]` (records)
     - `totalCount`

2. **Get ingestion record detail**
   - **Service:** `accounting.invoiceIssuedIngestion.get` (placeholder)
   - **Inputs:** `ingestionId` (required)
   - **Outputs:** full record including payload summary + posting references + errors

### Create/update calls
- None (read-only story), unless retry is supported.

### Submit/transition calls (optional)
3. **Retry / Reprocess ingestion record**
   - **Service:** `accounting.invoiceIssuedIngestion.retry` (placeholder)
   - **Inputs:** `ingestionId` (required)
   - **Outputs:** updated status or `jobId`
   - **UI behavior:** disable button while in-flight; refresh detail on success.

### Error handling expectations
- 401/403: route to unauthorized
- 404: show “record not found”
- 409: show conflict (e.g., cannot retry because status changed)
- 422/400: show validation message for filter inputs (if backend validates)
- 500: show generic error with correlationId if provided

---

## 10. State Model & Transitions

### Allowed states (processingStatus)
Because backend contract is not fully defined, UI must support at least:
- `RECEIVED` (optional)
- `PROCESSED`
- `DUPLICATE_IGNORED`
- `REJECTED`
- `QUARANTINED` (optional; for conflicts)
- `PROCESSING` (optional; async)

### Role-based transitions
- Standard users: no transitions
- Accounting Ops with elevated permission: may invoke `RETRY` if backend supports it
  - Allowed from: `REJECTED` (and possibly `QUARANTINED`)
  - Not allowed from: `PROCESSED`, `DUPLICATE_IGNORED`

### UI behavior per state
- `PROCESSED`: show posting references section (ledger/journal IDs)
- `DUPLICATE_IGNORED`: show idempotency/duplicate section; posting references should point to original processed record if backend provides link
- `REJECTED`: show error code/message; show DLQ indicator if routed
- `QUARANTINED`: show conflict details (if provided) and guidance “requires investigation”
- `PROCESSING`: show in-progress indicator; auto-refresh allowed only if safe default is permitted (see Applied Safe Defaults)

---

## 11. Alternate / Error Flows

1. **Empty state**
   - No results for filters → show “No ingestion records found” and suggest clearing filters.

2. **Backend unavailable**
   - List/detail service call fails (timeout/5xx) → show retry affordance and preserve filter state.

3. **Concurrency**
   - Detail viewed while status changes (e.g., processing completes) → refresh shows latest; if retry attempted and backend returns 409, show “Record already processed / state changed”.

4. **Unauthorized access**
   - User lacks permission → deny screen access; no data leakage.

5. **Partial payload**
   - Backend does not provide `revenueItems`/`taxItems` → UI shows “Not available” and still displays envelope + identifiers.

---

## 12. Acceptance Criteria

### Scenario 1: List InvoiceIssued ingestion records
**Given** I am an authenticated user with permission to view Accounting integrations  
**When** I open the “InvoiceIssued Ingestion” list screen  
**Then** I see a paginated list of ingestion records including at minimum `receivedAt`, `invoiceId`, `invoiceVersion`, `eventId`, and `processingStatus`  
**And** I can filter by `processingStatus` and a date range  
**And** the list refreshes to show only matching records.

### Scenario 2: View processed ingestion record details
**Given** an ingestion record exists with `processingStatus = PROCESSED`  
**When** I open its detail screen  
**Then** I can see `invoiceId`, `invoiceVersion`, `eventId`, and `schemaVersion` (if available)  
**And** I can see revenue items and tax items in separate sections when provided by the backend  
**And** I can see at least one posting reference identifier (e.g., `ledgerTransactionId` or equivalent) when provided.

### Scenario 3: Duplicate event is clearly indicated and non-posting is visible
**Given** an ingestion record exists with `processingStatus = DUPLICATE_IGNORED`  
**When** I open its detail screen  
**Then** the UI explicitly indicates the event was identified as a duplicate and ignored  
**And** the UI displays the idempotency key values used (at minimum `invoiceId` and `invoiceVersion`)  
**And** the UI does not display new posting references for this duplicate record (unless the backend provides a link to the original processed record).

### Scenario 4: Rejected event shows actionable error details
**Given** an ingestion record exists with `processingStatus = REJECTED` and includes `errorCode` and `errorMessage`  
**When** I open its detail screen  
**Then** the UI displays `errorCode` and `errorMessage`  
**And** if the backend indicates DLQ routing, the UI displays that the event was routed to DLQ and shows any provided DLQ reference.

### Scenario 5: Unauthorized access is blocked
**Given** I am authenticated but do not have permission to view Accounting integrations  
**When** I navigate to the InvoiceIssued ingestion route  
**Then** access is denied and I do not see ingestion record data.

### Scenario 6 (Optional, only if backend supports retry): Retry a rejected ingestion record
**Given** an ingestion record exists with `processingStatus = REJECTED`  
**And** I have permission to retry ingestion  
**When** I click “Retry”  
**Then** the UI calls the retry service with the record identifier  
**And** on success the detail view refreshes and shows the updated processing status.

---

## 13. Audit & Observability

- UI must display user-visible traceability fields when available:
  - `eventId`, `invoiceId`, `invoiceVersion`, `ledgerTransactionId`/`journalEntryId`
  - timestamps: `receivedAt`, `processedAt`
- Moqui screen actions (list load, detail load, retry) should log:
  - screen path, userId, key identifiers (eventId/invoiceId/ingestionId), and correlationId if returned
- No sensitive payload fields beyond what backend already classifies as safe should be displayed.

---

## 14. Non-Functional UI Requirements

- **Performance:** List should support pagination; initial load should not fetch full payload bodies for all rows (detail fetch only).
- **Accessibility:** Quasar components must be keyboard navigable; status indicators must have text (not color-only).
- **Responsiveness:** List and detail usable on tablet; detail sections stack vertically.
- **i18n/timezone:** Display datetimes in user’s locale/timezone per app standard (do not invent timezone rules).
- **Currency:** Display amounts with `currencyUomId` when provided; do not assume USD formatting if currency missing.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging on list screens; safe because it does not affect domain policy. (Impacted: UX Summary, Alternate/Error Flows)
- SD-UX-PAGINATION: Use standard pagination with default sort by most recent `receivedAt`; safe as UI ergonomics only. (Impacted: UX Summary, Service Contracts)
- SD-ERR-STANDARD: Map HTTP 401/403/404/409/422/500 to standard user-facing error patterns; safe because it’s generic error handling without changing business rules. (Impacted: Service Contracts, Alternate/Error Flows)

---

## 16. Open Questions

1. **Backend contract (blocking):** What are the exact Moqui service names and input/output schemas for:
   - listing ingestion records
   - retrieving a record detail
   - (optional) retry/reprocess?
2. **Entity model (blocking):** What entity/entities in Moqui store ingestion outcomes and posting references (exact entity names and primary keys)?
3. **Permissions (blocking):** What permission(s) gate access to Accounting integration monitoring screens, and what permission gates “retry” (if supported)?
4. **Status taxonomy (blocking):** What are the authoritative processing status values and their meanings (especially for DLQ/quarantine/conflict)?
5. **Posting references (blocking):** Which identifiers should UI display for traceability (ledgerTransactionId vs journalEntryId vs both), and what routes (if any) should deep-link to those records?
6. **Payload visibility (blocking/security):** Which parts of the `InvoiceIssued` event payload are approved to display in UI (full payload JSON vs curated fields only)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Accounting: Ingest InvoiceIssued Event — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/181  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest InvoiceIssued Event

**Domain**: user

### Story Description

/kiro  
Post AR, revenue, and tax liabilities from issued invoices.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `InvoiceIssued` event from Workorder Execution

## Main Flow
1. Validate invoice payload and idempotency
2. Create Accounts Receivable entry
3. Post revenue by classification
4. Post tax liabilities by jurisdiction
5. Persist posting references

## Business Rules
- Invoice is the legal revenue trigger
- Taxes must be posted separately from revenue
- Posting must be idempotent per invoice version

## Data Requirements
- Entities: Invoice, AR, RevenueAccount, TaxLiability
- Fields: invoiceId, totals, taxBreakdown

## Acceptance Criteria
- [ ] AR balance increases correctly
- [ ] Revenue posted to correct accounts
- [ ] Tax liabilities recorded accurately
- [ ] Duplicate events do not double-post

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