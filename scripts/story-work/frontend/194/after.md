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
[FRONTEND] [STORY] AP: Create & Review Vendor Bill Created from Purchasing/Receiving Event

### Primary Persona
AP Clerk (Accounts Payable)

### Business Value
Ensure vendor liabilities created from upstream receiving/invoice events are reviewable, traceable, and ready for downstream approval/payment, with clear idempotency/duplicate visibility and audit links (event ↔ bill ↔ journal entry preview).

---

## 2. Story Intent

### As a / I want / So that
**As an** AP Clerk,  
**I want** a UI to find and open a Vendor Bill that was automatically created from a Goods Received or Vendor Invoice Received event,  
**So that** I can verify the bill details, confirm traceability to the source event and staged GL impact, and route issues (duplicates/invalid references) to investigation.

### In-scope
- Read-only review UI for system-created Vendor Bills originating from:
  - `GoodsReceivedEvent` and/or `VendorInvoiceReceivedEvent` (final trigger policy TBD)
- Ability to search/list Vendor Bills by vendor, PO, source event id, vendor invoice reference, status, date range
- Bill detail view including:
  - header fields, line items, totals
  - traceability links (source event, PO/receipt refs)
  - staged GL impact preview (Dr inventory/expense, Cr AP) as displayed data (no posting configuration editing)
  - idempotency/duplicate indicators (where available)
- Standard error handling and empty states

### Out-of-scope
- Creating/editing Vendor Bills manually
- Approving, paying, voiding bills
- Defining GL accounts, posting categories, or posting rule sets
- Implementing event ingestion (backend responsibility)
- Three-way match resolution workflow beyond displaying discrepancies (policy TBD)

---

## 3. Actors & Stakeholders
- **AP Clerk (Primary):** reviews bills created by events.
- **Accounting System (Secondary):** produces bills, audit, and GL preview data.
- **Auditor:** needs traceability and immutable audit trail visibility.
- **Purchasing/Receiving stakeholders:** need linkage to PO/receipts for investigation.

---

## 4. Preconditions & Dependencies
- Backend provides an authoritative Vendor Bill record created from an event (ingestion already occurred).
- Backend exposes read endpoints to:
  - list Vendor Bills with filters
  - view a single Vendor Bill with lines, refs, traceability
  - view staged GL impact (journal entry preview/staged entry id)
  - view source event envelope (or at least event metadata)
- Authentication is configured and user has permission to view AP bills.

**Dependency on backend story #130 outcomes:** trigger policy, initial bill state, discrepancy handling, and concrete API contracts are not finalized in the provided inputs.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Accounting → Accounts Payable → Vendor Bills**
- Deep link: `/ap/vendor-bill/<billId>` (screen path to be aligned with repo conventions)

### Screens to create/modify
1. **APVendorBillList** (new)
   - Filter/search Vendor Bills
   - List results
   - Navigate to detail screen
2. **APVendorBillDetail** (new)
   - View bill header + status
   - View line items
   - View traceability panel (source event, PO/receipt refs)
   - View GL impact preview (staged journal entry lines summary)
   - View audit/status history (createdAt/by, sourceModule if available)
3. Optional (if backend supports): **APSourceEventView** (new modal or sub-screen)
   - Read-only JSON or field rendering of the source event envelope

### Navigation context
- Breadcrumbs:
  - Accounting → Accounts Payable → Vendor Bills → Vendor Bill Detail
- From detail, links out to:
  - Purchase Order detail (if a screen exists; otherwise display id only)
  - Receipt/Goods Received reference (if a screen exists; otherwise display id only)
  - Journal Entry detail (if it exists and user permitted) OR keep as preview-only

### User workflows
**Happy path**
1. User opens Vendor Bills list
2. Filters by vendor or date range or status “Pending Approval/Draft”
3. Opens bill detail
4. Confirms:
   - source event id and type
   - PO/receipt refs
   - amounts/lines
   - GL impact preview is present
5. Copies identifiers for downstream approval/payment workflow (out of scope)

**Alternate paths**
- User searches by `sourceEventId` and finds:
  - exactly one bill (expected)
  - none (shows empty with guidance)
  - multiple (should not happen under idempotency; show warning and list all)
- User opens a bill flagged as “duplicate ignored / conflict” (if represented by backend) and sees investigation guidance.

---

## 6. Functional Behavior

### Triggers
- User navigates to Vendor Bill list screen
- User submits filters/search
- User opens a bill detail from the list or deep link

### UI actions
- Search/filter actions submit to Moqui transition calling a service (or REST facade)
- Row click navigates to detail transition with `billId`
- “View Source Event” opens modal/sub-screen (if supported)
- “Copy” actions for `billId`, `sourceEventId`, `vendorInvoiceReference` (UI-only convenience)

### State changes
- No domain state changes in this frontend story (read-only).
- If backend supplies “viewedAt” tracking, do **not** implement without explicit requirement (denylist: audit policy).

### Service interactions
- List screen calls a backend service to retrieve paged results
- Detail screen calls backend service to retrieve bill + lines + traceability + GL preview
- Optional call to retrieve event metadata/payload

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Filter validation:
  - date range: `fromDate <= toDate` else show inline error “From date must be on or before To date.”
  - if `sourceEventId` provided, treat as exact match (no partial guessing)
- Deep link validation:
  - if `billId` not found, show not-found state with link back to list

### Enable/disable rules
- Actions are read-only; no edit/save controls.
- “View Journal Entry” link only enabled if backend provides `journalEntryId` and user has permission (permission name TBD → Open Question).

### Visibility rules
- Show traceability panel only if at least one of:
  - `sourceEventId`
  - `purchaseOrderId`
  - receipt/GRN reference
- Show GL preview section if backend provides staged lines; otherwise show “GL impact not available” informational message.

### Error messaging expectations
- Display backend error message with a stable UI wrapper:
  - “Unable to load Vendor Bills. Please retry.” + technical `errorCode` if provided
- For authorization failures: show “You do not have access to view Vendor Bills.”

---

## 8. Data Requirements

### Entities involved (frontend-facing)
(Exact Moqui entity names TBD; this story treats these as backend resources.)
- `VendorBill`
- `VendorBillLineItem`
- `Vendor` (display name only)
- `PurchaseOrder` (reference only)
- `GoodsReceipt` / `Receipt` (reference only)
- `JournalEntry` (preview or reference)
- `AccountingEvent` / Source Event metadata (reference)

### Fields
**VendorBill (header)**
- `billId` (string/UUID, required, read-only)
- `vendorId` (string/UUID, required, read-only)
- `vendorName` (string, optional display)
- `purchaseOrderId` (string/UUID, optional, read-only)
- `sourceEventId` (string/UUID, optional but expected for system-created bills, read-only)
- `sourceEventType` (string, optional; e.g., `GoodsReceivedEvent` / `VendorInvoiceReceivedEvent`)
- `vendorInvoiceReference` (string, optional)
- `status` (string enum; values TBD by backend, read-only)
- `billDate` (date, required/optional TBD)
- `dueDate` (date, optional)
- `currencyUomId` (string, required)
- `totalAmount` (decimal, required)
- `createdAt`, `createdBy` (datetime/string, required for audit display if available)

**VendorBillLineItem**
- `lineId` (string/UUID, required)
- `productId` (string/UUID, optional)
- `description` (string, required/optional TBD)
- `quantity` (decimal, required)
- `unitPrice` (decimal, required)
- `lineTotal` (decimal, required)
- `debitAccountRef` (string/UUID or code, optional display-only; do not infer)
- `taxAmount` / `feeAmount` (optional; only display if provided)

**GL Impact Preview**
- `journalEntryId` (string/UUID, optional)
- lines:
  - `glAccountRef` (string/code, required)
  - `debitAmount` (decimal, optional)
  - `creditAmount` (decimal, optional)
  - `memo` (string, optional)

### Read-only vs editable
- All fields are read-only in this story.

### Derived/calculated fields (UI-only)
- Display totals formatted using `currencyUomId`
- Display “Origin” badge derived from `sourceEventType`:
  - “Goods Received” or “Vendor Invoice Received” (no business logic beyond mapping string to label)

---

## 9. Service Contracts (Frontend Perspective)

> Concrete endpoint names are not provided in inputs; below defines required capabilities and expected shapes. Implementations should map to Moqui services or REST calls per repo convention.

### Load/list calls
**`ap.vendorBill.list`**
- Inputs:
  - `status` (optional, multi)
  - `vendorId` (optional)
  - `purchaseOrderId` (optional)
  - `sourceEventId` (optional, exact)
  - `vendorInvoiceReference` (optional, exact or contains? **TBD**)
  - `billDateFrom` / `billDateTo` (optional)
  - paging: `pageIndex`, `pageSize`
  - sorting: `sortBy`, `sortOrder`
- Returns:
  - `items[]` minimal fields: `billId`, `vendorName/vendorId`, `status`, `billDate`, `dueDate`, `totalAmount`, `currencyUomId`, `sourceEventId`, `vendorInvoiceReference`, `purchaseOrderId`
  - `pageIndex`, `pageSize`, `totalCount`

### View/detail calls
**`ap.vendorBill.get`**
- Inputs: `billId`
- Returns: full header + `lineItems[]` + traceability refs + optional `glPreview`

### Optional source event call
**`accounting.event.get`**
- Inputs: `eventId` (== `sourceEventId`)
- Returns: event envelope fields and/or payload (read-only)

### Error handling expectations
- 401/403 → show unauthorized page/state
- 404 (bill not found) → not-found state on detail screen
- 409 (conflict) → show “Data changed; reload” CTA (if backend uses optimistic locking for reads, unlikely but safe)
- 5xx/timeouts → show retry affordance

---

## 10. State Model & Transitions

### Allowed states (displayed)
Backend-defined. UI must support at minimum the states mentioned in provided reference:
- `DRAFT` or `PENDING_APPROVAL` (initial; exact one TBD)
- `APPROVED`
- `PAID`
- `VOID`

### Role-based transitions
- None implemented (read-only). If approval/payment transitions exist, they belong to separate stories.

### UI behavior per state
- Show state badge and explanatory text:
  - If `PENDING_APPROVAL`/`DRAFT`: “Not payable until approved.”
  - If `PAID`: show payment status (only if backend provides payment refs; otherwise just state)
  - If `VOID`: show void reason (only if backend provides; do not invent)

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid filter date range → inline error, prevent search submit.

### Concurrency conflicts
- If detail fetch returns 409 or stale indicator (if provided), show message “This bill was updated. Reload to view latest.”

### Unauthorized access
- If user lacks permission:
  - list screen shows access denied
  - direct link to detail shows access denied (do not leak existence)

### Empty states
- No results: show “No Vendor Bills match your filters.” with “Clear filters” action.
- Missing GL preview: show informational empty state, not an error.

---

## 12. Acceptance Criteria

### Scenario 1: List Vendor Bills by status and date
**Given** I am an authenticated AP Clerk with permission to view Vendor Bills  
**When** I open the Vendor Bills list screen  
**And** I filter by status `PENDING_APPROVAL` and a bill date range  
**Then** I see a paged list of Vendor Bills matching the filters  
**And** each row shows `billId`, vendor, status, bill date, due date, and total amount with currency.

### Scenario 2: Search by sourceEventId
**Given** a Vendor Bill exists with `sourceEventId = E123`  
**When** I search the Vendor Bills list by `sourceEventId` = `E123`  
**Then** the results include the Vendor Bill linked to `E123`  
**And** opening the bill shows `sourceEventId` = `E123` in the traceability section.

### Scenario 3: View bill detail with line items and GL preview
**Given** a Vendor Bill exists with line items and an available GL impact preview  
**When** I open the Vendor Bill detail screen for that bill  
**Then** I see all line items with quantity, unit price, and line total  
**And** I see a GL impact preview showing debit-side account refs and a credit-side AP account ref (as provided by backend)  
**And** I see a traceability link to the associated purchase order id (if present).

### Scenario 4: Bill detail not found
**Given** I navigate directly to a Vendor Bill detail URL with a non-existent `billId`  
**When** the screen loads  
**Then** I see a “Vendor Bill not found” message  
**And** I can navigate back to the Vendor Bills list.

### Scenario 5: Unauthorized access
**Given** I am authenticated but do not have permission to view Vendor Bills  
**When** I open the Vendor Bills list screen  
**Then** I see an access denied state  
**And** no bill data is displayed.

### Scenario 6: Missing GL preview data
**Given** a Vendor Bill exists but backend does not provide GL preview/journal entry reference  
**When** I open the Vendor Bill detail screen  
**Then** I see the bill header and lines  
**And** the GL preview section displays “GL impact not available” without failing the page load.

---

## 13. Audit & Observability

### User-visible audit data
- Display (if provided):
  - `createdAt`, `createdBy`
  - `sourceModule` / source system identifier from event metadata
  - `sourceEventId` and `sourceEventType`

### Status history
- If backend provides `statusHistory[]`, render it chronologically:
  - status, changedAt, changedBy
- If not provided, do not fabricate; just show current status.

### Traceability expectations
- Bill detail must display identifiers to support audit tracing:
  - `sourceEventId`
  - `purchaseOrderId`
  - `journalEntryId` (if available)

---

## 14. Non-Functional UI Requirements

- **Performance:** list screen initial load under 2s for first page on typical network; paginate rather than infinite load by default.
- **Accessibility:** all interactive controls keyboard accessible; ensure form inputs have labels; table supports screen reader summaries.
- **Responsiveness:** usable on tablet widths; columns may collapse to stacked layout per Quasar patterns.
- **i18n/timezone/currency:** format money using `currencyUomId`; dates displayed in user locale/timezone as provided by frontend settings (do not change backend semantics).

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Added standard empty-state messaging and “Clear filters” action; qualifies as UI ergonomics only; impacts UX Summary, Alternate/ Error Flows, Acceptance Criteria.
- SD-UI-PAGINATION: Use paged list with `pageIndex/pageSize` and server-side totalCount; qualifies as UI ergonomics/performance; impacts UX Summary, Service Contracts, Non-Functional UI Requirements.
- SD-ERR-STD-MAPPING: Standard mapping of 401/403/404/5xx to UI states with retry; qualifies as standard error-handling; impacts Service Contracts, Alternate / Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **Authoritative trigger & labeling:** Is the Vendor Bill created from `GoodsReceivedEvent`, `VendorInvoiceReceivedEvent`, or both? If both, how should UI represent the origin and matching status (three-way match) without guessing policy?
2. **Initial bill status enum:** What is the exact initial status for an auto-created bill (`DRAFT` vs `PENDING_APPROVAL`), and what are the canonical status values the UI must support?
3. **Backend read API contract:** What are the exact Moqui service names (or REST endpoints), request params, and response schemas for:
   - listing bills
   - bill detail (including lines)
   - GL preview / journal entry reference
   - source event metadata/payload
4. **Idempotency/duplicate visibility:** Will the backend expose any explicit flags/records for “duplicate event ignored” or “conflicting duplicate quarantined”? If yes, what fields should the UI display to support investigation?
5. **Permissions:** What permission(s) gate viewing Vendor Bills and viewing linked Journal Entries/Source Events (e.g., `ap.bill.view`, `journalEntry.view`, `accounting.event.view`)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] AP: Create Vendor Bill from Purchasing/Receiving Event  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/194  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] AP: Create Vendor Bill from Purchasing/Receiving Event

**Domain**: payment

### Story Description

/kiro  
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Accounts Payable (Bill → Payment)

## Story
AP: Create Vendor Bill from Purchasing/Receiving Event

## Acceptance Criteria
- [ ] VendorInvoiceReceived (or GoodsReceived) event creates an AP bill with PO/receipt refs
- [ ] GL postings: Dr Expense/Inventory, Cr AP (per rules)
- [ ] Traceability links bill ↔ event ↔ journal entry
- [ ] Idempotent by vendorInvoiceRef/eventId


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