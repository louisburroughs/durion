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
[FRONTEND] [STORY] Accounting: Ingest InventoryIssued Event (Ops UI for Ingestion Status, Idempotency, and Posting Traceability)

### Primary Persona
Accounting Operations User (Accountant / Finance Ops) monitoring and troubleshooting automated accounting event ingestion.

### Business Value
Provide a front-end operational interface to observe, validate, and troubleshoot `InventoryIssued` event ingestion so accounting postings are traceable, idempotency is verifiable, and failures can be investigated without backend access.

---

## 2. Story Intent

### As a / I want / So that
**As an** Accounting Operations user,  
**I want** to view the ingestion status and resulting accounting postings (journal entry references) for `InventoryIssued` events, including duplicate detection and failures,  
**so that** I can confirm inventory valuation postings occurred exactly once, investigate rejects/DLQ cases, and support audit traceability.

### In-scope
- A Moqui screen set to:
  - Search/list ingested accounting events filtered to `eventType=InventoryIssued`
  - View event details (payload fields + metadata + payload hash if available)
  - View processing outcome: status, errors, idempotency result (new vs duplicate), linked journal entry reference(s)
  - View traceability links: workorderId, inventoryIssueId, inventoryItemId
- Basic operational actions **if supported by backend**:
  - “Retry processing” for failed events (no guarantee; gated by Open Questions)
  - “Mark as reviewed/acknowledged” note for ops workflow (only if entity exists)

### Out-of-scope
- Implementing actual ingestion, valuation, GL posting logic (backend responsibility)
- Defining GL account mappings, valuation method configuration, or COGS/WIP rules
- Editing inventory quantities or journal entry lines from the UI

---

## 3. Actors & Stakeholders
- **Primary actor:** Accounting Operations User
- **Secondary stakeholders:** Auditor (read-only access), Inventory Manager (read-only traceability), Engineering On-call (troubleshooting)
- **System actors (referenced):** Workorder Execution (event source), Accounting backend services (ingestion + posting)

---

## 4. Preconditions & Dependencies
- Backend has an ingestion pipeline that persists `InventoryIssued` events and processing results (status/errors/idempotency outcome).
- Backend exposes services/endpoints to:
  - List ingested events with filters and pagination
  - Fetch event detail by eventId
  - Fetch linked posting artifact(s) (e.g., JournalEntry) by source eventId
- Permissions exist for viewing event ingestion data (exact permission names are unclear → Open Questions).
- Event schema is “Durion Accounting Event Contract v1” (PDF referenced) but field-level canonical names beyond those listed are not fully specified here.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Accounting → Event Ingestion → Inventory Issued**
- Deep link:
  - `/apps/accounting/events/inventory-issued` (list)
  - `/apps/accounting/events/inventory-issued/<eventId>` (detail)

### Screens to create/modify
Create new screen flow under an accounting app:
- `screen://accounting/events/InventoryIssuedList.xml`
- `screen://accounting/events/InventoryIssuedDetail.xml`

(Exact package path may vary per repo conventions; implement consistent with existing `apps/` screen structure in the frontend repo.)

### Navigation context
- Breadcrumb: Accounting → Event Ingestion → InventoryIssued → (Event Detail)
- From detail, allow navigation to linked Journal Entry detail screen if it exists in frontend; otherwise show journalEntryId as a copyable reference.

### User workflows

#### Happy path: verify successful processing
1. User opens InventoryIssued events list
2. Filters by date range and/or workorderId
3. Opens an event row
4. Sees “Processed Successfully” with linked journalEntryId and traceability fields
5. Copies references for audit/support

#### Alternate path: duplicate event confirmation
1. User searches by eventId
2. Opens event detail
3. Sees “Duplicate (Idempotent)” outcome and the original processing reference (original journalEntryId)

#### Error path: rejected/DLQ investigation
1. User filters status = Failed/Rejected
2. Opens event detail
3. Sees validation error code/message and where it was routed (e.g., DLQ)
4. (If supported) user clicks “Retry processing” and sees updated status

---

## 6. Functional Behavior

### Triggers
- Screen load triggers read-only retrieval of event ingestion records.
- Optional user action triggers “retry” command for a failed event (only if backend supports).

### UI actions
**InventoryIssuedList**
- Search filters:
  - eventId (exact match)
  - workorderId (exact match)
  - inventoryIssueId (exact match)
  - inventoryItemId (exact match)
  - occurredAt date/time range
  - processingStatus (multi-select)
  - idempotencyResult (New / Duplicate / Conflict if available)
- Pagination + sort (default sort: occurredAt desc)
- Row click navigates to detail

**InventoryIssuedDetail**
- Read-only sections:
  - Event metadata: eventId, eventType, schemaVersion, sourceModule, occurredAt, businessUnitId, currencyUomId
  - Domain payload highlights: inventoryItemId, quantityIssued, unitOfMeasure, workorderId, inventoryIssueId, issueTimestamp/eventTimestamp (naming depends on backend)
  - Processing result: status, processedAt, idempotency outcome, errorCode, errorMessage, retryCount (if available)
  - Posting links: journalEntryId(s), transactionDate, postingStatus (if available)
- Actions:
  - Copy eventId / workorderId / journalEntryId
  - Retry (only visible when status in Failed/Rejected AND user has permission AND backend supports)

### State changes (frontend)
- Frontend does not mutate accounting data except optional retry command.
- Local UI state:
  - Loading / success / error
  - Retry in-progress, then refresh detail on success

### Service interactions
- List: call a Moqui service to retrieve records
- Detail: call a Moqui service to retrieve one record + linked journal entry refs
- Retry: call a Moqui service that requests reprocessing (if available), then re-fetch detail

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- List filter inputs:
  - eventId must be UUID format if provided (client-side validation; still allow submit if uncertain? **Blocking question**—see Open Questions; default is fail-fast with message)
  - quantityIssued displayed as decimal; if backend provides negative quantity in failed events, display as-is but highlight as invalid per schema expectations
- Retry action:
  - Only enabled for statuses indicating not successfully processed
  - Must require user confirmation modal: “Retry processing this event? This may create postings if not idempotent.” (wording safe, but must not imply non-idempotent behavior)

### Enable/disable rules
- “Retry processing” button:
  - Visible only if backend indicates `canRetry=true` OR status in (FAILED, REJECTED) **and** permission granted
  - Disabled while request in-flight
- Journal Entry link:
  - Enabled only if journalEntryId exists and user has permission to view journal entries (unclear → Open Questions)

### Visibility rules
- Show “Duplicate” badge if idempotency result indicates duplicate
- Show “Conflict” badge if backend flags duplicate eventId with differing payload (DLQ/quarantine scenario) (concept in backend story; UI should support if fields exist)

### Error messaging expectations
- Map backend error codes to user-facing messages:
  - If errorCode present, display: `errorCode` + `errorMessage`
  - If HTTP/service error, show generic “Unable to load event data” with correlation/request ID if provided by Moqui
- Do not display sensitive internals; show sanitized message.

---

## 8. Data Requirements

### Entities involved (frontend perspective; exact entity names TBD)
Because the provided inputs do not include actual Moqui entity definitions, the frontend must rely on services returning these fields.

Expected data objects:

1) **AccountingEventIngestionRecord** (name TBD)
- `eventId` (string/UUID, required, read-only)
- `eventType` (string, required, read-only; must equal `InventoryIssued` in this screen set)
- `schemaVersion` (string, required)
- `sourceModule` (string, required)
- `sourceEntityRef` (string, optional)
- `occurredAt` (datetime, required)
- `businessUnitId` (string, required)
- `currencyUomId` (string, required)
- `payload` (object or JSON string, required; read-only)
- `payloadHash` (string, optional but desired for audit)
- `processingStatus` (enum string, required)
- `idempotencyResult` (enum string, optional; e.g., NEW, DUPLICATE, CONFLICT)
- `processedAt` (datetime, optional)
- `errorCode` (string, optional)
- `errorMessage` (string, optional)
- `dlqRouted` (boolean, optional)
- `retryCount` (integer, optional)

2) **Derived payload highlights** (may be extracted server-side or client-side)
From payload, display:
- `inventoryItemId` (string, required)
- `quantityIssued` (decimal, required; expected positive)
- `unitOfMeasure` (string, required)
- `workorderId` (string, required)
- `inventoryIssueId` (string, required)
- `eventTimestamp` or `issueTimestamp` (datetime, required/optional depending on contract)

3) **JournalEntryReference** (optional)
- `journalEntryId` (string, optional)
- `postingStatus` (string, optional)
- `transactionDate` (date, optional)
- `totalDebit` / `totalCredit` (money, optional read-only)
- `linkable` (boolean optional)

### Read-only vs editable by state/role
- All fields are read-only.
- Only command is “Retry processing” (no direct edits).

### Derived/calculated fields
- `valuationAmount` is mentioned in original inputs; if backend returns it, display as read-only.
- Display `quantityIssued × unitCost = totalValue` only if backend returns both unitCost and computed total; do not compute financial amounts client-side unless explicitly provided (accounting strictness).

---

## 9. Service Contracts (Frontend Perspective)

> Names are placeholders; implement as Moqui services invoked by screen actions. Exact service names/endpoints are **Open Questions**.

### Load/view calls
1) **List InventoryIssued events**
- Service: `accounting.event.IngestedEventList` (placeholder)
- Inputs:
  - `eventType = "InventoryIssued"` (fixed)
  - `eventId?`, `workorderId?`, `inventoryIssueId?`, `inventoryItemId?`
  - `occurredAt_from?`, `occurredAt_thru?`
  - `processingStatus?[]`, `idempotencyResult?[]`
  - `pageIndex`, `pageSize`, `orderByField`, `orderByDirection`
- Outputs:
  - `events[]` with summary fields (eventId, occurredAt, status, idempotencyResult, workorderId, inventoryItemId)
  - `totalCount`

2) **Get InventoryIssued event detail**
- Service: `accounting.event.IngestedEventDetail` (placeholder)
- Inputs: `eventId` (required)
- Outputs:
  - `event` object with metadata + payload + processing fields
  - `journalEntries[]` references (optional)

### Submit/transition calls
3) **Retry processing a failed event**
- Service: `accounting.event.RetryIngestedEvent` (placeholder)
- Inputs: `eventId` (required), `reasonNote?` (optional)
- Outputs: updated `processingStatus`, `processedAt`, and any new `journalEntryId`

### Error handling expectations
- Validation errors from services should return structured error codes where possible.
- UI must handle:
  - 401/403: show “Not authorized” and hide restricted actions
  - 404: show “Event not found”
  - 409: show conflict if duplicate conflict/quarantine
  - 5xx: show generic error; allow retry reload

---

## 10. State Model & Transitions

### Allowed states (processingStatus) — MUST be confirmed by backend
UI should support at minimum (display-only):
- `RECEIVED`
- `VALIDATED`
- `PROCESSED_SUCCESS`
- `PROCESSED_DUPLICATE` (or via idempotencyResult)
- `FAILED_SCHEMA_VALIDATION`
- `FAILED_UNKNOWN_ITEM`
- `FAILED_POSTING`
- `DLQ` / `QUARANTINED`

### Role-based transitions
- Only “Retry” transition is user-initiated:
  - Allowed from failure/DLQ states only
  - Requires explicit permission (Open Question)

### UI behavior per state
- Success: green status, show journalEntry link/ref
- Duplicate: show “Duplicate” badge, show reference to original journalEntry if provided
- Failed: show red status, show errorCode/errorMessage, show “Retry” if allowed
- Quarantined/Conflict: show warning, display conflict details if provided; no retry unless backend allows

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Invalid UUID in eventId filter:
  - Show inline error and disable “Search” **(needs confirmation)** OR allow search and rely on backend validation (Open Question)

### Concurrency conflicts
- If user retries while another process already retried:
  - Backend may return 409/conflict; UI shows conflict message and refreshes detail

### Unauthorized access
- User lacking permission:
  - List: either hide menu entry or show 403 screen
  - Detail: show 403 if direct-linked
  - Retry action hidden/disabled

### Empty states
- No events found: show empty state with guidance “Adjust filters or widen date range.”

---

## 12. Acceptance Criteria

### Scenario 1: View InventoryIssued events list
**Given** I have permission to view accounting event ingestion records  
**When** I open the InventoryIssued events list screen  
**Then** I see a paginated list of events filtered to `eventType = InventoryIssued`  
**And** each row shows at least `eventId`, `occurredAt`, `processingStatus`, and `workorderId` (if present)

### Scenario 2: Filter by workorderId
**Given** there are InventoryIssued events for `workorderId = "WO-12345"`  
**When** I filter the list by `workorderId = "WO-12345"`  
**Then** only events with that `workorderId` are returned  
**And** the totalCount reflects the filtered result set

### Scenario 3: View event detail with successful processing
**Given** an InventoryIssued event exists with `eventId = "uuid-abc-123"`  
**And** its processingStatus indicates success  
**When** I open the event detail screen for `eventId = "uuid-abc-123"`  
**Then** I see the event metadata (eventId, eventType, schemaVersion, sourceModule, occurredAt)  
**And** I see payload highlights including `inventoryItemId`, `quantityIssued`, and `workorderId`  
**And** I see at least one linked posting reference (e.g., `journalEntryId`) if provided by backend

### Scenario 4: View duplicate/idempotent outcome
**Given** an InventoryIssued event exists whose idempotencyResult indicates it is a duplicate  
**When** I open the event detail  
**Then** I see a “Duplicate” indicator  
**And** I see the original processing reference (original journalEntryId) if the backend provides it  
**And** no UI action implies a second posting occurred

### Scenario 5: View failed event and error details
**Given** an InventoryIssued event exists with processingStatus indicating failure  
**When** I open the event detail  
**Then** I see `errorCode` and `errorMessage` (if provided)  
**And** I see whether it was routed to DLQ/quarantine (if provided)

### Scenario 6: Retry is gated and refreshes status (if supported)
**Given** an InventoryIssued event exists with processingStatus = FAILED (or DLQ)  
**And** I have permission to retry processing  
**When** I click “Retry processing” and confirm  
**Then** the UI calls the retry service with the `eventId`  
**And** on success the UI refreshes the event detail  
**And** the updated processingStatus is displayed

### Scenario 7: Unauthorized user cannot retry
**Given** I can view event detail but do not have permission to retry processing  
**When** I open a failed event detail  
**Then** I do not see the “Retry processing” action (or it is disabled with an authorization message)

### Scenario 8: Backend/service error handling
**Given** the backend list service is unavailable (5xx)  
**When** I load the InventoryIssued events list  
**Then** I see a non-technical error message indicating the list could not be loaded  
**And** I can retry loading without a full app refresh

---

## 13. Audit & Observability

### User-visible audit data
- Display (read-only) audit fields if provided:
  - processedAt, retryCount, lastErrorAt
  - payloadHash (if available) for forensic comparison

### Status history
- If backend provides status history entries (timestamp + status + message), show them in chronological order on detail screen.
- If not available, show current status only (Open Question).

### Traceability expectations
- Event detail must show the chain:
  - `eventId` → workorderId/inventoryIssueId/inventoryItemId → journalEntryId (if created)
- All identifiers should be copyable and included in screen logs (Moqui logs) for support correlation (without printing full payload in logs by default).

---

## 14. Non-Functional UI Requirements
- **Performance:** list page should load first page within 2 seconds for typical dataset sizes (assumes backend paging).
- **Accessibility:** all actions keyboard accessible; status indicators include text (not color-only).
- **Responsiveness:** usable on tablet widths; list table supports horizontal scroll if needed.
- **i18n/timezone:** display occurredAt/processedAt in user locale timezone; store/transport in ISO8601 UTC from backend.
- **Currency:** display currencyUomId as code; do not format monetary amounts unless backend provides currency + amount explicitly.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a standard empty-state message and “Clear filters” action; safe because it does not affect domain policy. (Impacted: UX Summary, Alternate/Error Flows)
- SD-UX-PAGINATION: Default page size = 25 with server-side pagination; safe as a UI ergonomics choice only. (Impacted: UX Summary, Service Contracts)
- SD-ERR-GENERIC: Use a generic load error banner with retry for 5xx/network errors; safe because it doesn’t change business behavior. (Impacted: Error Flows, Acceptance Criteria)

---

## 16. Open Questions
1. **Backend service/API contracts:** What are the exact Moqui services (names, input params, output fields) for listing ingested events, retrieving event details, and retrying processing (if allowed)?
2. **Entity/source of truth for ingestion records:** What Moqui entity (or view-entity) stores ingested accounting events and processing outcomes (status, errorCode, idempotencyResult, payload/payloadHash)?
3. **Processing status taxonomy:** What are the canonical values for `processingStatus` and `idempotencyResult` that the UI must support?
4. **Retry semantics & permissions:** Is a manual retry permitted from the UI? If yes, what permission name(s) gate it, and what states are eligible for retry?
5. **Journal entry linking:** How is the resulting posting represented and linked (single `journalEntryId`, multiple, or reference object)? Is there an existing frontend screen to view journal entries by ID?
6. **Payload field names:** In the canonical contract v1, is the timestamp field `eventTimestamp`, `issueTimestamp`, or `occurredAt` (and which should be displayed as the business timestamp)?
7. **Client-side UUID validation behavior:** Should the UI block searches with a non-UUID `eventId`, or allow submission and rely on backend validation?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Accounting: Ingest InventoryIssued Event  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/185  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest InventoryIssued Event

**Domain**: user

### Story Description

/kiro  
Focus on inventory valuation, COGS timing, and idempotent posting.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `InventoryIssued` event from Workorder Execution

## Main Flow
1. Receive inventory issue event with part, quantity, and workorder reference
2. Validate event schema and idempotency key
3. Determine valuation method (configured, e.g., FIFO/average)
4. Reduce on-hand inventory quantity
5. Record corresponding COGS or WIP entry based on configuration
6. Persist posting references and source links

## Alternate / Error Flows
- Duplicate event → ignore (idempotent)
- Invalid inventory reference → reject and flag
- Posting failure → retry or dead-letter

## Business Rules
- Inventory may only be reduced once per issued quantity
- Valuation method is configuration-driven
- Posting must be traceable to source workorder and part issue

## Data Requirements
- Entities: InventoryItem, InventoryTransaction, WorkorderRef
- Fields: quantity, valuationAmount, issueTimestamp

## Acceptance Criteria
- [ ] Inventory quantity is reduced correctly
- [ ] COGS/WIP is recorded per configuration
- [ ] Event is idempotent
- [ ] Posting references original workorder

## Classification (confirm labels)
- Type: Story
- Layer: Functional
- Domain: Accounting

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