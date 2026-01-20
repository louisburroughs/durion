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
[FRONTEND] [STORY] AR: View AR Invoice Created from `InvoiceIssued` Event (Traceability + Idempotency Indicators)

### Primary Persona
Accounting Clerk / Finance User (AR) and Accounting Support Analyst

### Business Value
Enable finance users to verify that `InvoiceIssued` events resulted in an AR invoice and corresponding journal entry, with clear traceability and idempotency visibility to support reconciliation, auditing, and operational support.

---

## 2. Story Intent

### As a / I want / So that
- **As an** AR/Accounting user  
- **I want** to view an AR invoice record that was created from an `InvoiceIssued` event, including links to the source event and resulting journal entry  
- **So that** I can confirm financial recording correctness, diagnose failures/duplicates, and provide audit-ready traceability.

### In-scope
- Moqui UI screens to **search and view** AR invoices created from `InvoiceIssued` events.
- Display of **traceability identifiers**: `sourceInvoiceId`, `sourceEventId`, and `journalEntryId` (or equivalent).
- Display of key header amounts/dates: invoice date, due date, currency, totals, status.
- Read-only view of journal entry summary lines (account code + debit/credit + amount) if available via backend service.
- Indication of **idempotency behavior** (e.g., “Duplicate event processed as no-op”) if backend exposes it.

### Out-of-scope
- Creating AR invoices from events (backend responsibility).
- Editing AR invoices or journal entries (posted/immutable).
- Defining GL account mappings, payment terms configuration, or posting rules.
- DLQ management UI (unless explicitly exposed by backend).

---

## 3. Actors & Stakeholders
- **Primary user:** Accounting Clerk (AR)
- **Secondary users:** Controller/Auditor (read-only verification), Support Analyst (troubleshooting)
- **Upstream system:** Billing domain emits `InvoiceIssued`
- **Downstream/related:** GL posting subsystem / journal entry records

---

## 4. Preconditions & Dependencies
- Backend provides an API/service to:
  - List/search AR invoices (filter by `sourceInvoiceId`, `customerId`, date range, status).
  - Retrieve AR invoice detail including `sourceEventId` and link to journal entry.
  - Retrieve journal entry detail/summary by `journalEntryId` (or via invoice detail payload).
- Backend enforces immutability; frontend treats invoice and journal entry as read-only.
- User has appropriate permission(s) to view AR invoices and journal entries (permission names TBD).
- Routing conventions for Moqui screens in this repo (from README) must be followed (not available in provided inputs).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Accounting → Accounts Receivable → Invoices**
- Deep link: from a Billing invoice view (optional) using `sourceInvoiceId` query param.

### Screens to create/modify
1. **`Accounting/AR/InvoiceList`** (new or extend existing)
   - Search/filter AR invoices
2. **`Accounting/AR/InvoiceDetail`** (new or extend existing)
   - Read-only detail view with traceability section
3. **`Accounting/GL/JournalEntryDetail`** (optional if exists; otherwise create read-only summary screen)
   - Read-only journal entry view linked from invoice

### Navigation context
- From InvoiceList → InvoiceDetail via `arInvoiceId`
- From InvoiceDetail → JournalEntryDetail via `journalEntryId`
- Optional: InvoiceDetail shows a link to “Source Event” detail if backend exposes an event view endpoint.

### User workflows
- **Happy path**
  1. User opens AR Invoice List
  2. Filters by `sourceInvoiceId` (from Billing invoice) or date/customer
  3. Opens an invoice
  4. Verifies due date, totals, and sees `sourceEventId` + journal entry link
  5. Opens journal entry and verifies AR/Revenue/Tax payable lines
- **Alternate paths**
  - No results: user sees empty state with guidance to broaden filters.
  - Invoice exists but journal entry missing: user sees warning banner and fields show “Not available”.
  - User lacks permission for journal entry: link is hidden/disabled with an authorization message.

---

## 6. Functional Behavior

### Triggers
- Entering list screen loads initial results (with default date range if allowed by safe defaults).
- Submitting search form triggers reload.
- Opening invoice detail triggers load by primary key.
- Clicking journal entry link triggers navigation to JE screen.

### UI actions
- Search actions:
  - Filter by `sourceInvoiceId`
  - Filter by `customerId`
  - Filter by invoice date range
  - Filter by status (Open/Posted/etc. — backend-driven)
- Detail actions:
  - Copy-to-clipboard for `sourceEventId` and `sourceInvoiceId`
  - Navigate to journal entry detail

### State changes
- None to domain records (read-only).
- UI state: loading, loaded, empty, error.

### Service interactions
- `searchArInvoices` (list)
- `getArInvoice` (detail)
- `getJournalEntry` (optional detail)
- Optional: `getAccountingEvent` for `sourceEventId` if supported

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Search form validation:
  - `sourceInvoiceId` max length (TBD; enforce only if backend provides constraint)
  - Date range: `from <= to` (UI-level validation)
- If backend returns “missing terms/missing revenue mapping” errors, UI must show them as non-editable informational errors on related diagnostic screens only (no mutation).

### Enable/disable rules
- Journal Entry link enabled only when `journalEntryId` is present **and** user has permission to view journal entries.
- Source Event link enabled only when `sourceEventId` present and endpoint exists.

### Visibility rules
- Traceability section always visible; fields show placeholder if values null.
- Audit fields visible if backend returns them (createdAt/createdBy).

### Error messaging expectations
- Map backend errors to:
  - Not found → “AR Invoice not found or you do not have access.”
  - Unauthorized → “You do not have permission to view this resource.”
  - Server error → “Unable to load. Try again. If the issue persists, contact support with Trace ID.”

---

## 8. Data Requirements

### Entities involved (frontend-known)
> Exact Moqui entity names are not provided in inputs; treat as backend/domain concepts until confirmed.

- **AR Invoice** (Accounting-owned)
- **AR Invoice Line** (optional display)
- **Journal Entry** (Accounting-owned)
- **Journal Entry Line**
- **Source Event reference** (`InvoiceIssued`), by `sourceEventId` (if queryable)

### Fields
**AR Invoice (header)**
- `arInvoiceId` (string/UUID; required; identifier) — read-only
- `sourceInvoiceId` (string; required) — read-only
- `sourceEventId` (UUID; required) — read-only
- `customerId` (string; required) — read-only
- `invoiceDate` (date; required) — read-only
- `dueDate` (date; required) — read-only
- `currencyUomId` or `currency` (string; required) — read-only
- `subtotalAmount` (decimal; required) — read-only
- `taxAmount` (decimal; required) — read-only
- `totalAmount` (decimal; required) — read-only
- `amountDue` (decimal; required) — read-only
- `status` (string; required) — read-only
- `journalEntryId` (string/UUID; nullable) — read-only
- Audit: `createdAt`, `createdBy` (optional; read-only)

**Journal Entry (summary/detail)**
- `journalEntryId` — read-only
- `transactionDate` — read-only
- `sourceEventId` — read-only
- `lines[]`: `glAccountCode`, `debitCreditFlag`, `amount` — read-only

### Read-only vs editable by state/role
- All fields in this story are **read-only** regardless of state/role; only visibility differs by permission.

### Derived/calculated fields
- UI may display formatted currency amounts; no recalculation of totals in frontend.

---

## 9. Service Contracts (Frontend Perspective)
> Moqui service names and request/response shapes must be confirmed. Below defines required capabilities.

### Load/view calls
1. **Search AR invoices**
   - Input: `sourceInvoiceId?`, `customerId?`, `invoiceDateFrom?`, `invoiceDateTo?`, `status?`, `pageIndex`, `pageSize`
   - Output: list of invoice headers with minimal fields for table display; include `arInvoiceId`, `sourceInvoiceId`, `customerId`, `invoiceDate`, `dueDate`, `totalAmount`, `currency`, `status`, `journalEntryId?`
2. **Get AR invoice detail**
   - Input: `arInvoiceId`
   - Output: full header + optional lines + `sourceEventId` + `journalEntryId`
3. **Get journal entry detail (optional)**
   - Input: `journalEntryId`
   - Output: header + lines

### Submit/transition calls
- None (read-only story).

### Error handling expectations
- Standard HTTP/JSON error mapping:
  - 400 validation → show inline error for filter inputs when applicable
  - 401/403 → show permission message and hide restricted links/data
  - 404 → not found empty state on detail
  - 409 conflict → show “data changed” and offer reload (if backend uses optimistic locking semantics even for reads)
  - 5xx → error panel with retry

---

## 10. State Model & Transitions

### Allowed states (display-only)
- AR invoice status values are backend-defined; UI must treat as enumerated strings and display as-is.
- Expected examples from backend story: `Posted/Open` (but not guaranteed).

### Role-based transitions
- None (no transitions in UI).

### UI behavior per state
- If status indicates posted/open: normal display.
- If status indicates void/closed (if exists): still viewable read-only.

---

## 11. Alternate / Error Flows

### Validation failures (search)
- Date range invalid → block search, show inline message.
- Unsupported status filter → backend 400; UI shows error and suggests clearing filter.

### Concurrency conflicts
- If invoice deleted/merged between list and detail → detail load returns 404; show not found with link back to list.

### Unauthorized access
- User can access list but not detail: detail returns 403; show permission error.
- User can view invoice but not journal entry: JE link hidden/disabled; attempting navigation shows 403 message.

### Empty states
- No matching invoices → empty state with suggestion to search by `sourceInvoiceId` from Billing invoice.

---

## 12. Acceptance Criteria

### Scenario 1: Search AR invoices by sourceInvoiceId
**Given** I have permission to view AR invoices  
**When** I open the AR Invoice List screen and search by a valid `sourceInvoiceId`  
**Then** I see a list including invoices matching that `sourceInvoiceId`  
**And** each row shows `invoiceDate`, `dueDate`, `totalAmount`, `currency`, `status`  
**And** selecting a row navigates to AR Invoice Detail for that invoice.

### Scenario 2: View AR invoice detail with traceability
**Given** an AR invoice exists that was created from an `InvoiceIssued` event  
**When** I open the AR Invoice Detail screen for that invoice  
**Then** I see `sourceInvoiceId` and `sourceEventId` displayed in a Traceability section  
**And** I see a `journalEntryId` displayed when present  
**And** I can navigate to the journal entry detail from the invoice when allowed.

### Scenario 3: Journal entry not available
**Given** an AR invoice exists but `journalEntryId` is null or journal entry retrieval fails with 404  
**When** I view the AR Invoice Detail screen  
**Then** the UI shows “Journal entry not available” (non-blocking warning)  
**And** no journal entry navigation action is enabled.

### Scenario 4: Unauthorized to view journal entry
**Given** I can view AR invoice details but I do not have permission to view journal entries  
**When** I open an AR invoice that has a `journalEntryId`  
**Then** the journal entry link/action is hidden or disabled  
**And** the UI indicates I lack permission to view journal entry details.

### Scenario 5: Invoice not found
**Given** I navigate to AR Invoice Detail with an invalid or inaccessible `arInvoiceId`  
**When** the screen loads  
**Then** I see a “Not found or no access” message  
**And** I can return to the AR Invoice List.

---

## 13. Audit & Observability

### User-visible audit data
- Display (if available): `createdAt`, `createdBy`, and invoice status.
- Display traceability IDs: `sourceEventId`, `sourceInvoiceId`, `journalEntryId`.

### Status history
- If backend provides status history, show read-only timeline; otherwise omit.

### Traceability expectations
- Users can copy `sourceEventId` to provide to support.
- Logs/telemetry should include `arInvoiceId`, `sourceInvoiceId`, `sourceEventId` on load failures (frontend console/logging abstraction per project convention).

---

## 14. Non-Functional UI Requirements
- **Performance:** List search should return and render within 2s for typical page size (assumes backend paging).
- **Accessibility:** All interactive controls keyboard accessible; labels for inputs; sufficient contrast.
- **Responsiveness:** Works on tablet viewport used in POS back-office contexts.
- **i18n/timezone/currency:** Dates shown in user locale/timezone; currency formatted by `currency`/`currencyUomId` from data without conversion.

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Provide a standard empty state message and “Clear filters” action; safe because it does not alter domain behavior. (UX Summary, Alternate / Error Flows)
- SD-UI-PAGINATION: Use standard paginated list behavior with `pageSize` and `pageIndex`; safe because it is presentation-only. (UX Summary, Service Contracts)
- SD-ERR-MAP-STD: Map HTTP 401/403/404/409/5xx to standard UI error patterns; safe because it does not invent business rules. (Service Contracts, Alternate / Error Flows)

---

## 16. Open Questions
1. What are the **actual Moqui service names** and request/response fields for:
   - searching AR invoices
   - fetching AR invoice detail
   - fetching journal entry detail?
2. What are the **Moqui screen route conventions** in this repo (base path/module name), and do AR/GL screens already exist that must be extended rather than created?
3. What are the **frontend permissions/authorization signals**?
   - Required permission(s) to view AR invoices?
   - Required permission(s) to view journal entries?
4. Does backend expose **idempotency/duplicate event indicators** (e.g., a processing log by `sourceEventId`), and should the UI show it? If yes, what endpoint/fields?
5. Should the AR invoice list support searching by **eventId (`sourceEventId`)** directly (useful for support workflows)?

---

## Original Story (Unmodified – For Traceability)
Title: [FRONTEND] [STORY] AR: Create Customer Invoice from Invoice-Issued Event — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/197  
Labels: frontend, story-implementation, customer

**Original Story**: [STORY] AR: Create Customer Invoice from Invoice-Issued Event

**Domain**: customer

### Story Description

/kiro  
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Accounts Receivable (Invoice → Cash Application)

## Story
AR: Create Customer Invoice from Invoice-Issued Event

## Acceptance Criteria
- [ ] InvoiceIssued event creates an AR invoice record with terms/due date
- [ ] GL postings: Dr AR, Cr Revenue, Cr Tax Payable (per rules)
- [ ] Traceability links invoice ↔ event ↔ journal entry
- [ ] Idempotent by invoiceId/eventId

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