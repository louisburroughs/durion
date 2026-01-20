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
[FRONTEND] [STORY] Accounting Events: Validate Event Completeness & Integrity (UI for Ingestion Validation + Status Tracking)

### Primary Persona
Accounting Operations Analyst (and System Auditor as secondary)

### Business Value
Provide a deterministic UI to submit/test accounting events and to review validation outcomes and processing status history so upstream teams can quickly correct invalid events and Accounting can prevent corrupted/unsafe data from entering downstream mapping/posting.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Accounting Operations Analyst  
- **I want** to submit or view incoming accounting events and see clear validation results with actionable error codes and processing status transitions  
- **So that** invalid or inconsistent events are rejected/suspended early, and valid events proceed with traceability and auditability.

### In-scope
- Frontend screens in Moqui (Vue/Quasar) to:
  - List/search accounting event ingestion records
  - View event details including validation errors and status history
  - Manually submit an event payload for validation (test/ops tool) **if supported by backend**
- UI behaviors for handling:
  - Schema validation failures
  - Missing reference failures
  - Financial consistency failures
  - Unknown event type handling (reject vs suspense) **as provided by backend**
- Display processing status progression (Received→Validated→Mapped→Posted/Rejected/Suspense)

### Out-of-scope
- Defining or implementing validation logic (backend-owned)
- Defining accounting formulas/tolerances, tax policy, GL account mappings
- Manual remediation actions like “move from suspense to mapped/posted” unless explicitly supported by backend contract
- Creating/maintaining canonical schemas

---

## 3. Actors & Stakeholders
- **Primary user:** Accounting Operations Analyst
- **Secondary user:** System Auditor / Compliance reviewer
- **System actor:** Accounting Event Ingestion Service (backend)
- **Upstream stakeholders:** Engineering teams producing events (need actionable error feedback)

---

## 4. Preconditions & Dependencies
- Backend provides one or more endpoints/services to:
  - List ingestion records (filterable)
  - Retrieve a single event record with payload + validation errors + status history
  - Submit an event for ingestion/validation (optional but implied by story capability)
- Backend returns structured error codes for validation failures (e.g., `SCHEMA_VALIDATION_FAILED`, `REFERENCE_NOT_FOUND`, `FINANCIAL_INCONSISTENCY`, `UNKNOWN_EVENT_TYPE`)
- Authentication/authorization exists; UI must respect permissions for viewing/submitting events.
- Moqui screens framework is available in this repo with Vue 3 + Quasar integration (per repo conventions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Accounting → Events → Ingestion**
- Deep links:
  - `/accounting/events` (list)
  - `/accounting/events/<eventId>` (detail)

### Screens to create/modify
1. **Screen: `AccountingEventIngestionList`**
   - Purpose: searchable list of ingestion records with status and timestamps
2. **Screen: `AccountingEventIngestionDetail`**
   - Purpose: full record view including payload, validation results, status history
3. **Screen/Dialog: `AccountingEventSubmit`** (optional, if backend supports submission)
   - Purpose: paste/upload JSON payload to validate/ingest

### Navigation context
- From list → select row → detail screen
- From list → “Submit Event” action → submit screen/dialog → on success navigate to detail

### User workflows
- **Happy path (review valid event):**
  1. User opens list, filters by status `Validated`
  2. Opens event detail
  3. Sees status history `Received → Validated`, no errors
- **Alternate path (rejected event triage):**
  1. Filter by status `Rejected`
  2. Open detail; view error codes + error details
  3. Copy eventId/error payload excerpt to share with upstream team
- **Alternate path (unknown event type):**
  1. Filter by `Suspense` or `Rejected` for `UNKNOWN_EVENT_TYPE`
  2. Review payload and policy result (as returned by backend)
- **Optional workflow (submit/test):**
  1. Open submit
  2. Paste JSON and submit
  3. UI shows created record and status `Received` (then refresh until `Validated/Rejected/Suspense`)

---

## 6. Functional Behavior

### Triggers
- Page load (list/detail) triggers backend fetch
- User submits filter changes triggers list refresh
- User opens detail triggers record fetch
- User submits event triggers ingestion call (if supported)

### UI actions
- List:
  - Filter by: status, eventType, sourceModule/sourceSystem, date range, businessUnitId (if available)
  - Pagination and sorting by `occurredAt` or `receivedAt`
- Detail:
  - Display: event envelope fields, payload (read-only JSON viewer), status history timeline/table, validation errors table
  - Actions: copy eventId, copy error code, copy payload snippet (client-side)
  - Refresh button (re-fetch)
- Submit:
  - JSON input validation on client only for “valid JSON” (not schema correctness)
  - Submit button calls backend; show server response and navigate to detail

### State changes (frontend-observable)
- No client-side state machine beyond view state
- Reflect backend status changes on refresh/poll:
  - `Received → Validated → Mapped → Posted` (or terminal `Rejected` / `Suspense`)
- If backend supports it, UI may show “Processing” indicator when status is non-terminal.

### Service interactions
- `load list` service call with filters
- `load detail` service call by eventId (or processingId)
- `submit` service call with raw event JSON

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- UI must surface backend validation outcomes as:
  - **Error code** (canonical)
  - **Human-readable message**
  - **Field/path context** where available (e.g., JSON pointer)
- UI must not attempt to re-implement schema/reference/financial checks.

### Enable/disable rules
- “Submit Event” action only visible/enabled if user has permission (see Open Questions) and backend contract exists.
- Detail “Refresh” always enabled.

### Visibility rules
- Validation errors section visible when backend returns `validationErrors` or status is `Rejected`/`Suspense`.
- Status history always visible; empty state if none.

### Error messaging expectations
- For 400 validation responses: show banner “Event rejected” plus returned error code(s).
- For 401/403: show “Not authorized” and hide restricted content.
- For 404: show “Event not found”.
- For 409 conflict/idempotency: show “Duplicate event” with returned disposition (processed/no-op) if provided.

---

## 8. Data Requirements

> Note: entity names/fields below reflect domain guidance; exact backend DTO/entity field names must be confirmed (Open Questions).

### Entities involved (frontend-facing)
- **AccountingEvent / CanonicalAccountingEvent** (event envelope + payload)
- **EventProcessingRecord** (processing status + timestamps + validationErrors)
- **EventStatusHistory / Audit log entries** (status transitions)

### Fields (type, required, defaults)

**Event envelope (read-only)**
- `eventId` (string UUIDv7) — required
- `eventType` (string) — required
- `schemaVersion` (string SemVer) — required
- `sourceModule` or `sourceSystem` (string) — required
- `sourceEntityRef` (string/object) — required (exact shape unknown)
- `occurredAt` (datetime ISO-8601) — required
- `businessUnitId` (string) — required
- `currencyUomId` (string) — required

**Processing record (read-only)**
- `processingId` (string UUID) — required
- `status` (enum) — required; one of `Received|Validated|Mapped|Posted|Rejected|Suspense`
- `receivedAt` (datetime) — required
- `lastUpdatedAt` (datetime) — required
- `validationErrors` (array/object, nullable) — optional

**Validation error item (read-only)**
- `errorCode` (string) — required
- `message` (string) — required
- `path` (string, optional) — JSON pointer / field name if provided
- `details` (object, optional) — structured metadata

**Payload (read-only JSON)**
- `payload` (object) — required (may be large)

### Editable vs read-only
- List and detail are fully read-only.
- Submit screen allows editing only the raw JSON submission body.

### Derived/calculated (UI-only)
- Terminal status flag: status in `{Posted, Rejected, Suspense}` (unless policy says otherwise)
- Status duration display: `lastUpdatedAt - receivedAt` (presentation only)

---

## 9. Service Contracts (Frontend Perspective)

> Moqui implementation can call REST via `service-call` or `screen` transitions; exact endpoint/service names must be confirmed.

### Load/view calls
1. **List events**
   - Inputs: `status?`, `eventType?`, `sourceModule?`, `fromDate?`, `toDate?`, `businessUnitId?`, `pageIndex`, `pageSize`, `sortBy`, `sortOrder`
   - Output: array of processing records with minimal envelope fields
2. **Get event detail**
   - Input: `eventId` (or `processingId`)
   - Output: full envelope + payload + processing record + status history + validation errors

### Create/update calls
- None (read-only) except optional submit.

### Submit/transition calls (optional)
- **Submit accounting event**
  - Input: raw JSON body representing canonical event
  - Output: created/acknowledged record including `eventId` and current `status`
  - Idempotency handling: if duplicate, backend may return success/no-op or conflict; UI must display outcome.

### Error handling expectations (mapping)
- `400` → validation error banner; render returned error codes
- `401/403` → route to login or show unauthorized
- `404` → not found state
- `409` → duplicate/conflict; show duplicate disposition
- `5xx`/network → retry affordance and preserve filters/input

---

## 10. State Model & Transitions

### Allowed states (displayed)
- `Received`
- `Validated`
- `Mapped`
- `Posted`
- `Rejected`
- `Suspense`

### Role-based transitions
- No UI-initiated transitions are in scope (view-only).
- If “Submit” exists: creating a new event results in initial state `Received` (backend-owned).

### UI behavior per state
- `Received`: show “Processing” indicator; refresh available
- `Validated`: show “Validated” badge; errors hidden unless provided
- `Mapped/Posted`: show success badges; status history visible
- `Rejected`: show error banner; validation errors section required
- `Suspense`: show warning banner; display any policy rationale and errors

---

## 11. Alternate / Error Flows

### Validation failures (submit or view)
- If submit returns `Rejected` with errors, remain on submit result view and provide link to detail.
- If viewing an event in `Rejected`, show errors with codes and details.

### Concurrency conflicts
- If event status changes between list and detail, detail fetch shows latest; UI shows “Updated since last view” if `lastUpdatedAt` changed after list load (UI-only indicator).

### Unauthorized access
- List/detail endpoints return 403: show “You do not have access to Accounting Events” and do not render payload.

### Empty states
- List: “No events found” with active filters summary and clear-filters action.
- Detail: If missing, show not found.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View list of event ingestion records
**Given** I am an authorized Accounting Operations Analyst  
**When** I navigate to Accounting → Events → Ingestion  
**Then** I see a paginated list of event ingestion records including eventId, eventType, status, receivedAt, and lastUpdatedAt

### Scenario 2: Filter list by status
**Given** I am on the event ingestion list  
**When** I filter by status "Rejected"  
**Then** the list refreshes and only shows records with status "Rejected"

### Scenario 3: View event detail with successful validation
**Given** an event exists with status history "Received" then "Validated" and no validation errors  
**When** I open the event detail page for that event  
**Then** I see the event envelope fields and status history including both transitions  
**And** I do not see a validation error banner

### Scenario 4: View event detail for schema validation failure
**Given** an event exists with status "Rejected" due to schema validation  
**When** I open the event detail page  
**Then** I see a validation error banner  
**And** I see an error entry with code "SCHEMA_VALIDATION_FAILED" (or backend-provided equivalent) and an actionable message

### Scenario 5: View event detail for missing reference failure
**Given** an event exists with status "Rejected" due to a missing required reference  
**When** I open the event detail page  
**Then** I see an error entry with code "REFERENCE_NOT_FOUND" (or backend-provided equivalent)  
**And** the UI displays any provided reference identifier context (e.g., `customerId`) without guessing

### Scenario 6: View event detail for financial inconsistency
**Given** an event exists with status "Rejected" due to inconsistent monetary amounts  
**When** I open the event detail page  
**Then** I see an error entry with code "FINANCIAL_INCONSISTENCY" (or backend-provided equivalent)  
**And** the UI displays any provided details about the failed check

### Scenario 7: Unknown event type handled per policy
**Given** an event exists with an unknown eventType  
**When** I open the event detail page  
**Then** I see the terminal disposition as either "Rejected" or "Suspense" as returned by the backend  
**And** I see an error/policy message indicating the unknown event type handling result

### Scenario 8: Unauthorized user cannot view payload
**Given** I am logged in without permission to view accounting event details  
**When** I navigate to an event detail URL  
**Then** I receive an unauthorized message state  
**And** the event payload is not rendered

### Scenario 9 (Optional): Submit an event payload for validation
**Given** the backend supports an event ingestion endpoint and I have permission to submit events  
**When** I paste valid JSON for a known eventType and submit  
**Then** the UI shows the created/acknowledged eventId and current status  
**And** I can navigate to the event detail page for that event

---

## 13. Audit & Observability

### User-visible audit data
- Detail page must display status history entries with:
  - fromStatus, toStatus
  - timestamp
  - actor/principal (service principal) if provided by backend

### Status history
- Must render in chronological order.
- Must handle missing intermediate states (display what backend provides; do not infer).

### Traceability expectations
- All screens must display identifiers: `eventId`, and if present `processingId`, `sourceModule/sourceSystem`, `schemaVersion`.
- UI copy actions facilitate debugging without editing the payload.

---

## 14. Non-Functional UI Requirements
- **Performance:** List loads within 2s for first page under normal conditions; detail renders large JSON payload with virtualized viewer or collapsed sections to avoid freezing.
- **Accessibility:** Keyboard navigable filters; proper labels; error banners announced to screen readers.
- **Responsiveness:** Works on tablet widths; list uses responsive columns.
- **i18n/timezone/currency:** Timestamps displayed in user locale/timezone; currency shown as code only unless backend provides formatted amounts (do not format money without contract).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide deterministic empty-state messaging and clear-filters action; qualifies as safe UI ergonomics; impacts UX Summary, Alternate/Empty states.
- SD-UX-PAGINATION: Use standard pagination (pageIndex/pageSize) and sorting; qualifies as safe UI ergonomics; impacts UX Summary, Service Contracts.
- SD-ERR-HTTP-MAP: Standard mapping of HTTP 400/401/403/404/409/5xx to UI banners and retry; qualifies as safe error-handling boilerplate; impacts Business Rules, Error Flows, Service Contracts.

---

## 16. Open Questions
1. **Backend contract (blocking):** What are the exact endpoints or Moqui services for:
   - listing event processing records
   - retrieving event detail (by `eventId` vs `processingId`)
   - submitting an event (if supported)
2. **Permissions (blocking):** What permissions control:
   - viewing event list
   - viewing event detail/payload
   - submitting events (ops/test tool)
3. **Unknown event policy (blocking):** Is unknown `eventType` always `Rejected`, always `Suspense`, or environment-configurable? How should UI message it?
4. **Canonical error codes (blocking):** What is the canonical list of error codes and the response schema for `validationErrors` (array shape, fields like `path`, `details`)?
5. **Status history shape (blocking):** Does backend provide explicit transition records? If yes, what fields (actor, reason, correlationId)?
6. **Idempotency UX (blocking):** On duplicate `eventId`, does backend return 200 with existing record, or 409, or both depending on payload match? What response fields should the UI rely on?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Events: Validate Event Completeness and Integrity  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/205  
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] Events: Validate Event Completeness and Integrity

**Domain**: general

### Story Description

/kiro  
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Ingest Accounting Events (Cross-Module)

## Story
Events: Validate Event Completeness and Integrity

## Acceptance Criteria
- [ ] Invalid schema or missing required references are rejected with actionable error codes
- [ ] Unknown eventType is rejected or routed to suspense per policy
- [ ] Amount and tax consistency checks are enforced per policy
- [ ] Processing status transitions are recorded (Received→Validated→Mapped→Posted/Rejected/Suspense)

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