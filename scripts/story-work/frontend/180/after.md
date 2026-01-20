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
[FRONTEND] [STORY] Accounting: Review & Resolve `InvoiceAdjusted` / `CreditMemoIssued` Ingestion Results (Journal Entry Corrections UI)

### Primary Persona
Accounting Ops Specialist (and Accounting Manager as approver/oversight)

### Business Value
Provide a Moqui UI to view, triage, and trace invoice adjustment/credit memo ingestion outcomes so finance can verify AR/Revenue/Tax corrections, investigate failures (DLQ/quarantine), and prove audit linkage to the original invoice and resulting journal entries.

---

## 2. Story Intent

### As a / I want / So that
**As an** Accounting Ops Specialist,  
**I want** to view `InvoiceAdjusted` and `CreditMemoIssued` events and their resulting journal entries (or failure reasons),  
**so that** I can confirm postings reconcile prior postings, investigate ingestion exceptions, and maintain an audit trail linking adjustments/credit memos to the original invoice.

### In-scope
- Moqui screens to:
  - List ingestion records for `InvoiceAdjusted` and `CreditMemoIssued`
  - View event payload summary + processing status + errors
  - Navigate from event → original invoice → generated journal entry (and lines)
  - View reconciliation summary (before vs after) when available from backend
- UI support for idempotency/conflict visibility (duplicate/conflict flags)
- Standard filtering/searching for operational triage (date range, status, invoiceId, eventId)

### Out-of-scope
- Defining debit/credit mappings or financial posting rules (Accounting backend responsibility)
- Implementing the event consumer/ingestion backend itself
- Editing or re-posting journal entries from the UI (immutability constraint)
- Approving variances/adjustments unless an explicit backend contract exists (not provided)

---

## 3. Actors & Stakeholders
- **Primary user:** Accounting Ops Specialist
- **Secondary:** Accounting Manager / Auditor (read-only traceability)
- **System actors (context):** Event ingestion processor (backend), DLQ/quarantine mechanism (backend)

---

## 4. Preconditions & Dependencies
- Backend provides persistent records representing:
  - Received accounting events (`InvoiceAdjusted`, `CreditMemoIssued`)
  - Their processing status (processed/rejected/quarantined/etc.)
  - Linkage to `originalInvoiceId`
  - Linkage to created `JournalEntry`/transaction IDs when processed successfully
  - Error codes/messages when not successful
- Backend exposes read endpoints (Moqui services or REST) to retrieve:
  - Event ingestion list + filters
  - Event ingestion detail (including payload summary and error detail)
  - Journal entry detail/lines by transactionId or journalEntryId
  - Invoice header (at least invoiceId/invoiceNumber/status/currency) for navigation context
- Permissions exist for accounting operations viewing (exact permission strings are **not provided**; see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main menu: **Accounting → Event Ingestion → Adjustments & Credit Memos**
- Deep links supported:
  - `/accounting/events/adjustments` (list)
  - `/accounting/events/adjustments/<eventId>` (detail)

### Screens to create/modify
1. **Screen:** `Accounting/EventIngestion/AdjCreditMemoList`
   - Purpose: operational list/queue view of relevant event types
2. **Screen:** `Accounting/EventIngestion/AdjCreditMemoDetail`
   - Purpose: traceability view showing event → invoice → journal entry results
3. (Optional if already exists) Reuse or link to existing screens:
   - `Accounting/JournalEntry/View` (or create a minimal view screen if missing)
   - `Billing/Invoice/View` (read-only header link)

### Navigation context
- Breadcrumb: Accounting > Event Ingestion > Adjustments & Credit Memos > (Event Detail)
- From detail screen, provide transitions to:
  - Original Invoice view (by `originalInvoiceId`)
  - Journal Entry view (by `transactionId`/`journalEntryId`)

### User workflows
**Happy path**
1. User opens list screen and filters to “Processed” for a date range.
2. User opens an event row.
3. Detail shows:
   - Event identifiers (eventId, type, occurredAt, sourceModule)
   - Linked original invoice
   - Linked resulting journal entry/transaction
   - Reconciliation summary (if provided)
4. User navigates to journal entry lines to confirm balanced totals and linkage.

**Alternate paths**
- User filters to “Rejected/Quarantined/DLQ” and opens a failed record to view error code/message and invoice lookup context.
- User finds duplicate/conflict and reviews conflict reason.

---

## 6. Functional Behavior

### Triggers
- User navigates to list/detail screens (no automatic posting actions initiated from frontend).

### UI actions
**List screen**
- Filter by:
  - Event Type: `InvoiceAdjusted`, `CreditMemoIssued` (default both)
  - Processing Status (multi-select)
  - Date range (occurredAt or receivedAt — clarify)
  - `eventId` (exact match)
  - `originalInvoiceId` (exact match)
  - `sourceModule` (optional)
- Row click → open detail screen for that `eventId`

**Detail screen**
- Show summary fields + processing outcome
- If processed successfully:
  - Show journal linkage(s) and allow navigation
- If failed:
  - Show error code, message, and failure classification (schema validation, not found, unbalanced, auth failure, duplicate conflict, etc.) as provided by backend
  - If backend provides DLQ/quarantine reference, show it read-only

### State changes
- Frontend performs **no** state changes unless backend exposes explicit “acknowledge/retry” actions (not provided; out-of-scope for now).

### Service interactions
- Read-only service calls to load list and detail data; see Service Contracts.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Filter inputs:
  - Date range: start must be <= end
  - UUID fields (`eventId`, `originalInvoiceId`) must validate as UUID format before search submit; show inline validation error

### Enable/disable rules
- “View Journal Entry” link enabled only when backend returns a journal reference.
- “View Invoice” link enabled only when `originalInvoiceId` present.

### Visibility rules
- Error panel visible only when status is failure/quarantined/etc. and error details exist.
- Conflict/duplicate badge visible only when backend flags event as duplicate/conflict.

### Error messaging expectations
- Backend error codes must be displayed verbatim with a human-readable label where possible (do not re-interpret accounting meaning).
- Standard Moqui error banner for network/500 errors; preserve correlationId if returned.

---

## 8. Data Requirements

### Entities involved (frontend-read models; actual entity names TBD)
Because the authoritative backend contract is not provided, the UI will require these logical data objects:

1. **AccountingEventIngestionRecord**
   - `eventId` (UUID, required)
   - `eventType` (enum: `InvoiceAdjusted` | `CreditMemoIssued`, required)
   - `schemaVersion` (string, required)
   - `sourceModule` (string, required)
   - `occurredAt` (datetime, required)
   - `receivedAt` (datetime, required)
   - `businessUnitId` (string/UUID, required)
   - `currencyUomId` (string, required)
   - `originalInvoiceId` (UUID, required)
   - `processingStatus` (enum, required)
   - `processingAttemptCount` (int, optional)
   - `lastErrorCode` (string, optional)
   - `lastErrorMessage` (string, optional)
   - `correlationId` (string, optional)
   - `journalTransactionId` (UUID/string, optional) OR `journalEntryId` (UUID/string, optional)
   - `duplicateOfEventId` (UUID, optional)
   - `conflictDetected` (boolean, optional)
   - `payloadSummary` (json/string, optional; must be safe to display)

2. **InvoiceHeader (read-only)**
   - `invoiceId` (UUID)
   - `invoiceNumber` (string)
   - `status` (string)
   - `issuedAt` (datetime)
   - `customerId` (UUID/string)
   - `currencyUomId` (string)
   - Totals optional (only if backend provides without implying accounting calculations)

3. **JournalEntryView**
   - `journalEntryId` (UUID/string)
   - `transactionId` (UUID/string)
   - `effectiveDate` (date)
   - `status` (Draft/Posted/etc. as provided)
   - `sourceEventId` (UUID)
   - `lines[]`: (`glAccountId`, `debitAmount`, `creditAmount`, `description`)

### Fields: read-only vs editable
- All fields in this story are **read-only** in the UI.

### Derived/calculated fields (UI-only)
- Display-only:
  - Status badge mapping from `processingStatus`
  - “Has Journal Link” boolean = journal reference present
  - “Has Error” boolean = lastErrorCode/message present

---

## 9. Service Contracts (Frontend Perspective)

> Moqui naming below uses placeholders; must be aligned to actual services/endpoints once confirmed.

### Load/view calls
1. **List ingestion records**
   - Service: `accounting.event.IngestionList` (placeholder)
   - Inputs:
     - `eventTypes[]`
     - `status[]`
     - `occurredAtFrom`, `occurredAtTo` (or receivedAt; clarify)
     - `eventId`, `originalInvoiceId`
     - pagination: `pageIndex`, `pageSize`
     - sorting: `sortBy`, `sortOrder`
   - Output:
     - `records[]: AccountingEventIngestionRecord (summary subset)`
     - `totalCount`

2. **Get ingestion record detail**
   - Service: `accounting.event.IngestionDetail` (placeholder)
   - Inputs: `eventId`
   - Output: full `AccountingEventIngestionRecord` + optional linked resources keys

3. **Get invoice header**
   - Service: `billing.invoice.GetHeader` (or `accounting.invoice.ViewHeader` if replicated)
   - Inputs: `invoiceId`
   - Output: `InvoiceHeader`

4. **Get journal entry / transaction**
   - Service: `accounting.journalEntry.Get` (by `journalEntryId` or `transactionId`)
   - Output: `JournalEntryView`

### Create/update/submit calls
- None (read-only story)

### Error handling expectations
- For service validation errors:
  - Display inline on filter form (list screen)
- For 401/403:
  - Show “Not authorized” page/message and hide data
- For 404 on detail:
  - Show “Event not found” with link back to list
- For network/500:
  - Global error banner; provide correlationId if returned in headers/body

---

## 10. State Model & Transitions

### Allowed states (processingStatus)
Backend-driven; UI must treat as opaque enumerations but support common statuses if present:
- `RECEIVED`
- `VALIDATED`
- `PROCESSED`
- `REJECTED`
- `QUARANTINED` / `DLQ`
- `DUPLICATE_IGNORED`
- `DUPLICATE_CONFLICT`

### Role-based transitions
- None in UI (no actions that mutate state).

### UI behavior per state
- `PROCESSED`: show journal linkage panel + navigation links
- Failure states (`REJECTED`, `QUARANTINED`, `DLQ`, `DUPLICATE_CONFLICT`): show error panel with code/message and any provided remediation hint
- `DUPLICATE_IGNORED`: show badge and link to original event if `duplicateOfEventId` provided

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Invalid UUID in filters → block submit; show “Must be a valid UUID”
- Date range invalid → block submit; show “Start date must be before end date”

### Concurrency conflicts
- If record status changes between list and detail load:
  - Detail screen shows latest status; optionally show “Updated since list view” indicator (non-blocking)

### Unauthorized access
- If user lacks permission:
  - List screen shows no data and a permission error (do not leak existence of events)

### Empty states
- No records match filters:
  - Show empty state with “Clear filters” action

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: List adjustment/credit memo ingestion records
Given I am an authorized accounting user  
When I navigate to Accounting > Event Ingestion > Adjustments & Credit Memos  
Then I see a list of ingestion records containing only event types InvoiceAdjusted and CreditMemoIssued  
And each row shows at minimum eventId, eventType, occurredAt (or receivedAt), processingStatus, and originalInvoiceId

### Scenario 2: Filter by originalInvoiceId and status
Given I am on the ingestion list screen  
When I enter a valid originalInvoiceId and select status “PROCESSED” and submit  
Then the list shows only records matching that invoiceId and status  
And pagination controls allow navigating result pages when totalCount > pageSize

### Scenario 3: View processed event detail with journal linkage
Given an ingestion record exists with processingStatus “PROCESSED” and a journalTransactionId (or journalEntryId)  
When I open the event detail screen for its eventId  
Then I see the event identifiers (eventId, eventType, schemaVersion, sourceModule, occurredAt)  
And I see a link to the original invoice using originalInvoiceId  
And I see a link to the resulting journal entry/transaction using the journal reference

### Scenario 4: View rejected/quarantined event detail with error details
Given an ingestion record exists with processingStatus “REJECTED” (or “QUARANTINED/DLQ”) and lastErrorCode/lastErrorMessage populated  
When I open the event detail screen  
Then I see the error code and error message displayed  
And no journal navigation link is shown

### Scenario 5: Invalid filter input is blocked client-side
Given I am on the ingestion list screen  
When I enter an invalid UUID into the eventId filter and submit  
Then the UI prevents the search  
And I see a validation message indicating the UUID format is invalid

### Scenario 6: Unauthorized user cannot access ingestion data
Given I am not authorized to view accounting ingestion records  
When I navigate to the ingestion list screen  
Then the UI shows an unauthorized message  
And no ingestion record data is displayed

---

## 13. Audit & Observability

### User-visible audit data
- Display read-only audit/trace fields when provided:
  - `correlationId`
  - `processingAttemptCount`
  - `receivedAt` and `occurredAt`
  - `sourceModule`
- Ensure links provide traceability chain:
  - Event → Original Invoice → Journal Entry/Lines

### Status history
- If backend provides status history (timestamps + status), render as a read-only timeline.
- If not provided, omit (do not fabricate).

### Traceability expectations
- All screens must include key identifiers in the UI for support copy/paste:
  - eventId, originalInvoiceId, journalTransactionId/journalEntryId, correlationId

---

## 14. Non-Functional UI Requirements
- **Performance:** list screen should load first page within 2 seconds under normal conditions (excluding backend slowness)
- **Accessibility:** keyboard navigable table, proper labels for filters, sufficient contrast for status badges
- **Responsiveness:** usable on tablet widths; filters collapse to stacked layout
- **i18n/timezone:** display timestamps in user locale/timezone (do not change stored values); currency displayed using `currencyUomId` formatting only when amounts are provided by backend (no new calculations)

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standard empty-state messaging and “Clear filters” action; qualifies as safe UX ergonomics; impacts UX Summary, Error Flows.
- SD-UX-PAGINATION: Default pageSize=25 with server-side pagination parameters; qualifies as safe UX ergonomics; impacts Service Contracts, UX Summary.
- SD-ERR-STANDARD-MAPPING: Map HTTP 401/403/404/409/500 to standard Moqui/Quasar notification patterns without changing domain semantics; qualifies as safe error-handling; impacts Error Flows, Service Contracts.

---

## 16. Open Questions
1. **Backend read model & endpoints (blocking):** What are the exact Moqui services (or REST endpoints) and field names for listing and viewing `InvoiceAdjusted` / `CreditMemoIssued` ingestion records (including status, error details, and journal linkage)?
2. **Status enumeration (blocking):** What are the authoritative `processingStatus` values and their meanings (especially DLQ vs quarantined vs rejected vs duplicate/conflict)?
3. **Timestamp filter basis (blocking):** Should list filtering default to `occurredAt` (event time) or `receivedAt` (ingestion time)? Which fields are available?
4. **Permissions (blocking):** What permission(s) govern access to these screens (e.g., `accounting.events.view`, `accounting.ingestion.view`), and are auditors allowed read-only access?
5. **Journal navigation key (blocking):** Does the backend expose linkage by `transactionId`, `journalEntryId`, or both? Which should be primary for navigation?
6. **Payload display policy (blocking/security):** Is it acceptable to display raw event payload JSON in the UI, or must it be redacted/limited to a safe “payloadSummary” provided by backend?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Accounting: Ingest InvoiceAdjusted or CreditMemo Event  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/180  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest InvoiceAdjusted or CreditMemo Event

**Domain**: user

### Story Description

/kiro  
Handle revenue, tax, and AR changes from invoice adjustments.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `InvoiceAdjusted` or `CreditMemoIssued` event

## Main Flow
1. Validate adjustment authorization
2. Reverse or amend prior AR, revenue, and tax entries
3. Post adjusted values
4. Maintain linkage to original invoice

## Acceptance Criteria
- [ ] Adjustments reconcile prior postings
- [ ] Credit memos reduce AR and revenue correctly
- [ ] Full audit trail preserved

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