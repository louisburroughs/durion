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

**Title:** [FRONTEND] [STORY] Accounting: Ingest InventoryAdjusted Event (View, Validate, Process Status + Audit Trail)

**Primary Persona:** Accounting Ops User (Finance/Accounting back-office user)

**Business Value:**  
Provide a front-end workflow to view and manage ingestion of `InventoryAdjusted` accounting events (including validation outcome, idempotency status, and traceability links) so inventory correction postings are auditable, diagnosable, and supportable without backend log access.

---

## 2. Story Intent

**As a** Accounting Ops User  
**I want** to see received `InventoryAdjusted` events and their processing outcomes (success/duplicate/rejected/quarantined), with links to created journal entries and the referenced source transaction  
**So that** I can verify auditability, investigate failures (schema issues, missing source transaction, negative inventory policy violations), and support reconciliation.

### In-scope
- A Moqui screen flow to:
  - List `InventoryAdjusted` ingestion records with filtering
  - View event details (envelope + payload fields relevant to accounting)
  - View processing status and failure reason (including DLQ/quarantine indicator)
  - Navigate to linked accounting artifacts (journal entry, source transaction reference) where available
- UI-driven reprocessing action **only if** the backend supports it (otherwise show as unavailable with clear messaging)

### Out-of-scope
- Implementing the ingestion consumer, DLQ, idempotency, or journal entry creation logic (backend-owned)
- Defining GL debit/credit mappings, valuation rules, or posting rule configuration (requires accounting policy + backend)
- Editing event payloads (must remain immutable)

---

## 3. Actors & Stakeholders

- **Primary Actor:** Accounting Ops User
- **Secondary Actors:**
  - Controller/Auditor (read-only audit verification)
  - Support Engineer (triage failures)
- **Upstream System:** Work Execution domain publishes `InventoryAdjusted`
- **Downstream / Related:** Accounting journal entry subsystem; GL export/integration (if present)

---

## 4. Preconditions & Dependencies

- An authoritative event contract exists: “Durion Accounting Event Contract v1” (referenced PDF) and includes `InventoryAdjusted` (`eventType`) payload details.
- Backend provides query endpoints/services for:
  - Listing ingested events and their processing status
  - Retrieving a single event by `eventId`
  - Retrieving linked journal entry id(s) created from the event (if applicable)
  - (Optional) triggering reprocess/retry for a failed/quarantined event
- Moqui authentication exists; permissions for viewing ingestion records are defined.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Accounting → Event Ingestion → Inventory Adjustments**
- Deep link: `/accounting/events/inventory-adjusted/view?eventId=...`

### Screens to create/modify
1. **Screen:** `apps/accounting/screen/events/InventoryAdjustedList.xml`
   - List view with filters and quick status chips
2. **Screen:** `apps/accounting/screen/events/InventoryAdjustedDetail.xml`
   - Read-only event detail + processing panel + linked artifacts panel
3. (Optional if repo convention supports) **Component:** `apps/accounting/screen/events/components/EventEnvelopeCard.xml` for reuse across event types

### Navigation context
- Breadcrumb: Accounting / Event Ingestion / Inventory Adjustments / (EventId)
- Back action returns to list preserving last-used filters (safe default UI ergonomics)

### User workflows
**Happy path**
1. User opens Inventory Adjustments list
2. Filters by date/status/location/product
3. Opens an event detail
4. Confirms status = Processed (Success) and sees linked Journal Entry reference

**Alternate paths**
- Status = Duplicate → user sees “Already processed” and the original processing timestamp and linked journal entry (if stored)
- Status = Rejected/Quarantined → user sees error code, message, and recommended next action; if backend supports retry, user can trigger retry

---

## 6. Functional Behavior

### Triggers
- User navigates to list or detail screens
- User applies filters/sorts
- User requests “Retry processing” (if enabled)

### UI actions
**List Screen**
- Display table/grid of ingestion records
- Filter controls:
  - `occurredAt` range (from/to)
  - `processingStatus` multi-select
  - `locationId`
  - `productId`
  - free-text search on `eventId` / `sourceTransactionId` / `adjustmentId`
- Row action: “View”

**Detail Screen**
- Read-only sections:
  - **Envelope:** `eventId`, `eventType`, `schemaVersion`, `sourceModule`, `occurredAt`, `businessUnitId`, `currencyUomId`
  - **Payload:** `sourceTransactionId`, `adjustmentId`, `productId`, `locationId`, `originalQuantity`, `adjustedQuantity`, `adjustmentReasonCode`, `adjustingUserId`
  - **Processing:** `processingStatus`, `processedAt`, `idempotencyOutcome`, `failureCode`, `failureMessage`, `quarantineRef`/`dlqRef` (if applicable)
  - **Links:** Journal Entry Id(s), Source Transaction reference
- If processing failed and retry is supported:
  - Button “Retry Processing”
  - Confirmation modal includes warning about idempotency and audit trail
  - After retry: refresh status panel

### State changes
- Frontend does not mutate event payload
- Frontend may invoke a backend retry action which changes processing state; UI must reflect eventual result by refresh

### Service interactions
- List: call a backend service to fetch paginated results
- Detail: call a backend service to fetch full record, including links
- Retry: call a backend service/action; handle async vs sync response (see Open Questions)

---

## 7. Business Rules (Translated to UI Behavior)

> Note: Accounting-domain financial posting rules are backend-owned. Frontend reflects outcomes and enforces immutability + clarity.

### Validation (UI-level)
- Filters validate date range (`from <= to`)
- Retry action is disabled unless:
  - status is one of: `REJECTED`, `QUARANTINED`, `FAILED` (exact enum TBD)
  - user has permission `accounting.events.retry` (permission name TBD)
- Display warning banner on events where payload indicates potential negative inventory scenario:
  - If backend exposes a boolean like `wouldCauseNegativeInventory` or failure code indicates negative inventory policy violation, show banner “Blocked by negative inventory policy”

### Enable/disable rules
- “Retry Processing” disabled if status = `PROCESSED_SUCCESS` or `DUPLICATE`
- Links to Journal Entry only enabled if `journalEntryId`(s) present

### Visibility rules
- Failure details visible only when `processingStatus` in a failed category
- Raw payload JSON viewer (collapsible) shown for users with elevated permission `accounting.events.viewRaw` (permission name TBD)

### Error messaging expectations
- Backend error codes mapped to user-readable messages, while preserving code for support:
  - Example: `SCHEMA_VALIDATION_FAILED` → “Event schema validation failed. See details and contact integrations team.”
  - `SOURCE_TRANSACTION_NOT_FOUND` → “Referenced source transaction was not found in accounting records.”
  - `NEGATIVE_INVENTORY_PROHIBITED` → “Adjustment would create prohibited negative inventory position.”
  - `INGESTION_DUPLICATE_CONFLICT` → “Duplicate eventId with conflicting payload. Quarantined for investigation.”

---

## 8. Data Requirements

### Entities involved (frontend perspective; exact Moqui entity names TBD)
- `AcctIngestedEvent` (or similar): stores event envelope + processing status
- `AcctIngestedEventPayload` or JSON field: stores payload
- `JournalEntry` (read-only link)
- `SourceTransactionRef` (read-only link to original posting / transaction record)

### Fields (type, required, defaults)
**Event Envelope**
- `eventId` (string/UUID; required; immutable)
- `eventType` (string; required; must equal `InventoryAdjusted` for these screens)
- `schemaVersion` (string; required)
- `sourceModule` (string; required)
- `occurredAt` (datetime; required)
- `businessUnitId` (string; required)
- `currencyUomId` (string; required)

**Event Payload (InventoryAdjusted)**
- `sourceTransactionId` (string/UUID; required)
- `adjustmentId` (string/UUID; required)
- `productId` (string; required)
- `locationId` (string; required)
- `originalQuantity` (decimal; required)
- `adjustedQuantity` (decimal; required)
- `adjustmentReasonCode` (string/enum; required)
- `adjustingUserId` (string; required)

**Processing / Audit**
- `processingStatus` (enum; required)
- `processedAt` (datetime; optional)
- `idempotencyOutcome` (enum/string; optional; e.g., `PROCESSED`, `DUPLICATE_SAME_PAYLOAD`, `DUPLICATE_CONFLICT`)
- `failureCode` (string; optional)
- `failureMessage` (string; optional, safe content)
- `quarantineRef` / `dlqRef` (string; optional)
- `createdAt`, `createdBy` (read-only)
- `lastUpdatedAt`, `lastUpdatedBy` (read-only)

### Read-only vs editable by state/role
- All event data is **read-only** for all roles
- Retry action available only for authorized role and only on failed/quarantined states

### Derived/calculated fields
- `quantityDelta = adjustedQuantity - originalQuantity` (display-only)
- `age = now - occurredAt` (display-only)
- `hasJournalEntryLinks` boolean (display-only)

---

## 9. Service Contracts (Frontend Perspective)

> Backend service names are unknown from provided inputs; define contract expectations and map to Moqui `service-call` placeholders.

### Load/view calls
1. **List ingestion records**
   - Service: `accounting.events.InventoryAdjusted.list`
   - Inputs:
     - `occurredFrom` (datetime, optional)
     - `occurredTo` (datetime, optional)
     - `statuses` (list<string>, optional)
     - `locationId` (string, optional)
     - `productId` (string, optional)
     - `q` (string, optional)
     - `pageIndex` (int, default 0)
     - `pageSize` (int, default 25)
     - `sortField` (string, default `occurredAt`)
     - `sortOrder` (`asc|desc`, default `desc`)
   - Output:
     - `items[]` with list-row fields
     - `totalCount`

2. **Get event detail**
   - Service: `accounting.events.InventoryAdjusted.get`
   - Inputs: `eventId` (required)
   - Output: full envelope, payload, processing fields, linked refs

### Create/update calls
- None (immutable)

### Submit/transition calls
1. **Retry processing (optional)**
   - Service: `accounting.events.InventoryAdjusted.retry`
   - Inputs: `eventId` (required), `retryReason` (string optional; see Open Questions), `expectedSchemaVersion` (string optional)
   - Output:
     - Either: immediate `processingStatus` + message
     - Or: `jobId` for async processing (TBD)

### Error handling expectations
- HTTP/service errors map to:
  - Validation error → show inline error in filter panel or dialog
  - Unauthorized → route to unauthorized screen or show “Insufficient permissions”
  - Not found → show “Event not found or purged”
  - Conflict (duplicate conflict) → show banner “Quarantined due to duplicate conflict” and display code
- Never display raw stack traces; show `failureCode` + sanitized `failureMessage`

---

## 10. State Model & Transitions

### Allowed states (processingStatus) — **TBD exact enum**
Frontend must support at minimum:
- `RECEIVED` (ingested but not processed)
- `PROCESSED_SUCCESS`
- `DUPLICATE` (same eventId, same payload)
- `QUARANTINED` (duplicate conflict or policy hold)
- `REJECTED` / `FAILED` (schema/validation/lookup failures)

### Role-based transitions
- Accounting Ops with permission may trigger: `REJECTED|QUARANTINED|FAILED` → “Retry Requested”
- All other users: no transitions (read-only)

### UI behavior per state
- `PROCESSED_SUCCESS`: show linked journal entry; retry disabled
- `DUPLICATE`: show idempotency outcome; retry disabled
- `REJECTED/FAILED`: show failure details; retry enabled if supported
- `QUARANTINED`: show quarantine reason; retry may be disabled depending on backend policy (TBD)

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid filter inputs → show inline message and do not call list service
- Retry without permission → show “Insufficient permissions” and do not call retry service

### Concurrency conflicts
- If status changes between list and detail load:
  - Detail screen must show latest status
  - If user hits retry but status is now success/duplicate → show conflict message and refresh

### Unauthorized access
- If service returns authorization error:
  - Screen shows standard unauthorized panel
  - No sensitive payload fields rendered

### Empty states
- List returns 0 results → show “No InventoryAdjusted events found for current filters”
- Detail missing journal entry link → show “No journal entry linked (yet)” with status-dependent hint (e.g., still processing)

---

## 12. Acceptance Criteria

### Scenario 1: View list of InventoryAdjusted ingestion records
**Given** I am an authenticated Accounting Ops User with permission to view accounting ingestion events  
**When** I navigate to Accounting → Event Ingestion → Inventory Adjustments  
**Then** I see a paginated list of `InventoryAdjusted` events including `eventId`, `occurredAt`, `locationId`, `productId`, and `processingStatus`  
**And** I can filter by occurred date range and status  
**And** the list updates to only show matching events.

### Scenario 2: View event detail with audit and traceability
**Given** an `InventoryAdjusted` event exists with `eventId = X`  
**When** I open the event detail for `eventId = X`  
**Then** I see the event envelope fields (`eventId`, `eventType`, `schemaVersion`, `sourceModule`, `occurredAt`, `businessUnitId`, `currencyUomId`)  
**And** I see the payload fields (`sourceTransactionId`, `adjustmentId`, `productId`, `locationId`, `originalQuantity`, `adjustedQuantity`, `adjustmentReasonCode`, `adjustingUserId`)  
**And** I see processing fields including `processingStatus` and `processedAt` (if processed)  
**And** if journal entry links exist, I can navigate to the linked journal entry view.

### Scenario 3: Display failure details for rejected/quarantined event
**Given** an `InventoryAdjusted` event has `processingStatus = REJECTED` (or `QUARANTINED`)  
**When** I open the event detail  
**Then** I see `failureCode` and a user-readable `failureMessage`  
**And** the UI indicates whether the event is in DLQ/quarantine (if backend provides a reference)  
**And** no event payload fields are editable.

### Scenario 4: Retry processing (only if supported) from a failed event
**Given** an `InventoryAdjusted` event has `processingStatus = FAILED`  
**And** I have permission to retry ingestion processing  
**When** I click “Retry Processing” and confirm  
**Then** the frontend calls the retry service with `eventId`  
**And** the UI refreshes to show the latest processing status (success, still failed, or quarantined)  
**And** if the backend returns unauthorized/conflict, I see an actionable message and no duplicate retries are submitted.

### Scenario 5: Unauthorized user cannot retry
**Given** I do not have permission to retry ingestion processing  
**When** I view a failed `InventoryAdjusted` event  
**Then** the “Retry Processing” action is not shown or is disabled  
**And** direct access to the retry endpoint response is handled by showing an unauthorized message.

---

## 13. Audit & Observability

### User-visible audit data
- Display `createdAt/createdBy` for ingestion record (if available)
- Display `processedAt` and `idempotencyOutcome`
- Display immutable identifiers: `eventId`, `adjustmentId`, `sourceTransactionId`

### Status history
- If backend provides status history, show chronological list:
  - `RECEIVED` → `FAILED` → `RETRY_REQUESTED` → `PROCESSED_SUCCESS` (example)
- If not available, show current status only and open question logged

### Traceability expectations
- Detail screen must show link references:
  - `eventId` → journal entry id(s)
  - `sourceTransactionId` reference (even if it does not resolve, show as raw id)

---

## 14. Non-Functional UI Requirements

- **Performance:** List loads first page within 2 seconds for typical datasets (25 rows) assuming backend is responsive; use pagination (no unbounded loads).
- **Accessibility:** All actions keyboard accessible; status conveyed not by color alone; ARIA labels for buttons like “Retry Processing”.
- **Responsiveness:** Works on tablet widths; table can switch to stacked row layout on small screens (Quasar default patterns).
- **i18n/timezone/currency:** Display `occurredAt` and `processedAt` in user’s timezone; quantities shown with appropriate decimal precision (do not assume currency formatting for quantities). Currency shown as `currencyUomId` label if available.

---

## 15. Applied Safe Defaults

- **SD-UI-01 PaginationDefault**
  - **Assumed:** Default `pageSize = 25` with server-side pagination.
  - **Why safe:** UI ergonomics only; does not affect business logic or accounting policy.
  - **Impacted sections:** UX Summary, Service Contracts, Non-Functional UI Requirements.
- **SD-UI-02 FilterEmptyState**
  - **Assumed:** Standard empty-state message and “Clear filters” action when no results.
  - **Why safe:** Presentation-only; no domain assumptions.
  - **Impacted sections:** UX Summary, Alternate / Error Flows.
- **SD-ERR-01 StandardErrorSurface**
  - **Assumed:** Show backend `failureCode` + sanitized `failureMessage`, never raw stack traces.
  - **Why safe:** Security/UX best practice; does not invent domain policy.
  - **Impacted sections:** Business Rules, Service Contracts, Alternate / Error Flows.

---

## 16. Open Questions

1. **Event contract fields (blocking):** What is the exact `InventoryAdjusted` event schema per “Durion Accounting Event Contract v1” (field names, types, required/optional, nesting)? The frontend needs exact payload paths to render without guessing.
2. **Processing status model (blocking):** What are the authoritative `processingStatus` and `idempotencyOutcome` enums and their meanings? (e.g., `DUPLICATE` vs `DUPLICATE_CONFLICT` vs `QUARANTINED`)
3. **Backend service availability (blocking):** What Moqui services/endpoints exist for listing/getting ingestion events and (optionally) retrying processing? Provide service names + input/output fields.
4. **Permissions (blocking):** What permission IDs/roles govern:
   - viewing ingestion events,
   - viewing raw payload JSON,
   - retrying processing?
5. **Retry semantics (blocking):** If retry is supported, is it synchronous (immediate result) or asynchronous (job queued)? If async, what is the polling mechanism (jobId + status endpoint)?
6. **Linked artifacts (blocking):** How are journal entry links represented (single `journalEntryId`, multiple IDs, or query by `eventId`)? Is there an existing Journal Entry view screen to link to?
7. **Negative inventory policy display (blocking for completeness):** When processing fails due to “negative inventory prohibited unless explicitly allowed”, what exact failure code is returned and should the UI show any override indicator from the payload (e.g., `allowNegativeOverride`)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Accounting: Ingest InventoryAdjustment Event  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/184  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest InventoryAdjustment Event

**Domain**: user

### Story Description

/kiro  
Handle inventory corrections with full auditability.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `InventoryAdjusted` event from Workorder Execution

## Main Flow
1. Validate adjustment reason and quantities
2. Reverse or adjust prior inventory/COGS entries
3. Apply corrected inventory quantities
4. Record adjustment journal with reason code

## Business Rules
- Adjustments must reference original issue
- Negative inventory positions are prohibited unless explicitly allowed

## Acceptance Criteria
- [ ] Adjustments reconcile inventory correctly
- [ ] Prior postings are traceable and reversible

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

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