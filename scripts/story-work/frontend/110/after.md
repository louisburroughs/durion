## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] Availability: Manufacturer Feed Normalization — Monitor Ingestion + Review Unmapped Parts (via Positivity)

### Primary Persona
Integration Operator (Ops user responsible for monitoring integration health and resolving unmapped items)

### Business Value
Provide operational visibility into manufacturer availability feed ingestion so the business can (a) confirm freshness/health of availability data and (b) quickly resolve unmapped manufacturer part numbers that prevent downstream availability/lead-time estimates for special orders.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Integration Operator  
- **I want** a UI in the POS frontend to monitor manufacturer availability feed ingestion results and review unmapped manufacturer part numbers  
- **So that** availability/lead-time data can be trusted and unmapped parts can be routed for mapping maintenance without digging through logs.

### In-scope
- Frontend screens to:
  - View recent feed ingestion runs (per manufacturer, as-of time, outcome)
  - View normalized availability records (read-only) for troubleshooting/verification
  - View and triage **unmapped manufacturer parts backlog** (read-only + status update if supported)
  - View integration exceptions/failed ingestions routed to an exception queue (read-only)
- Moqui screen + service-call wiring to backend APIs (via Moqui services) with robust error handling and empty states.
- Permission-gated access to these operational views.

### Out-of-scope
- Implementing the ingestion pipeline itself (backend responsibility).
- Editing/creating Manufacturer Part Map entries (explicitly out-of-scope; handled by separate product-domain tool/story).
- Enforcing min-order rules in cart/ordering.
- Any inventory valuation/costing logic.

---

## 3. Actors & Stakeholders

### Actors
- **Integration Operator**: monitors ingestion, investigates errors, escalates unmapped parts.
- **Inventory Manager** (secondary): consumes availability freshness insights.
- **Support/Admin** (secondary): uses exception visibility for incident response.

### Systems
- **Moqui Frontend**: screens/forms/transitions to display and manage ops workflows.
- **Positivity backend** (integration service): source/transport for feeds (indirectly referenced).
- **pos-inventory backend service**: system of record for normalized availability + unmapped backlog.
- **pos-product backend service**: SoR for part mapping (not modified here; referenced only via backend behavior).

---

## 4. Preconditions & Dependencies

1. Backend story `[BACKEND] Availability: Normalize Manufacturer Inventory Feeds (Stub via Positivity)` (durion-positivity-backend #46) is implemented and exposes read APIs (and optional triage/update APIs) for:
   - ingestion run summaries (or equivalent)
   - normalized availability records
   - unmapped manufacturer parts backlog
   - exception queue entries
2. Authentication is functional in Moqui frontend; user identity available to enforce permissions.
3. Authorization model exists for “integration/ops” views (exact permission strings TBD—see Open Questions).
4. Manufacturer entities/identifiers exist (manufacturerId is UUIDv7 per backend spec).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Inventory → Availability Feeds (Ops)** (name can vary per app conventions)
- Optional deep links from alerts/notifications to:
  - a specific ingestion run (manufacturerId + asOf)
  - the unmapped parts list filtered by manufacturerId
  - exception queue filtered by correlationId/runId

### Screens to create/modify
Create new screen tree under a single route namespace, e.g.:
- `apps/pos/screen/inventory/availabilityFeeds.xml` (container/menu)
- `.../availabilityFeeds/ingestionRuns.xml`
- `.../availabilityFeeds/unmappedParts.xml`
- `.../availabilityFeeds/normalizedAvailability.xml`
- `.../availabilityFeeds/exceptions.xml`
- `.../availabilityFeeds/runDetail.xml` (optional but recommended)

(Exact file paths may differ; implement using repo conventions from `durion-moqui-frontend` README.)

### Navigation context
- Tabbed or side-nav within **Availability Feeds (Ops)**:
  1) Ingestion Runs  
  2) Unmapped Parts  
  3) Normalized Availability  
  4) Exceptions

### User workflows
#### Happy path: confirm feed health and spot issues
1. Operator opens **Ingestion Runs**
2. Filters by manufacturer and date range
3. Opens a run detail
4. Sees counts: items received, mapped, unmapped, stored; outcome success/partial/failure
5. If unmapped > 0, clicks through to **Unmapped Parts** pre-filtered.

#### Alternate path: investigate a specific part number
1. Operator opens **Unmapped Parts**
2. Searches by manufacturer part number
3. Views firstSeen/lastSeen/occurrenceCount
4. Copies identifiers and escalates to mapping tool (out of scope).

#### Error path: investigate failures
1. Operator opens **Exceptions**
2. Filters to “manufacturer availability feed”
3. Opens exception detail to see error message and correlation identifiers.

---

## 6. Functional Behavior

### Triggers
- User navigates to any Availability Feeds (Ops) screen.
- User changes filters (manufacturer, asOf range, status).
- User opens a detail view row.
- Optional: user attempts to update unmapped part status (if backend supports).

### UI actions
- **List views** (Runs, Unmapped, Availability, Exceptions):
  - Filter controls (manufacturerId, date range, status)
  - Search (by part number, productId, correlationId as applicable)
  - Pagination and sorting
  - Row click → detail screen or drawer
- **Detail views**:
  - Show structured JSON-like fields in readable format (no raw payload unless backend provides it)
  - Copy-to-clipboard for IDs (manufacturerId, asOf, mpn, productId)

### State changes (frontend)
- No domain state changes for availability records (read-only).
- Optional: Unmapped part triage state change (PENDING_REVIEW → IGNORED/RESOLVED) **only if backend contract exists**.

### Service interactions
- All data fetched via Moqui services that call backend endpoints (REST) or via Moqui artifact/service wrappers as per project conventions.
- Must handle:
  - 200 with empty results
  - 400 validation errors for bad filters
  - 401/403 unauthorized
  - 5xx backend errors and timeouts

---

## 7. Business Rules (Translated to UI Behavior)

1. **Idempotency is backend-owned**: UI must not attempt to deduplicate; it only displays latest records and run summaries.
2. **Mapping authority is product service**: UI must not allow editing mappings here; at most provide navigation/links to mapping tool (if exists) or copy data for escalation.
3. **Unmapped parts policy**:
   - UI must surface unmapped items distinctly (status PENDING_REVIEW default).
   - If repeated occurrences exist, UI displays `occurrenceCount`, `firstSeen`, `lastSeen`.
4. **Data freshness**:
   - UI displays `asOf` from feed as the authoritative timestamp and `receivedAt` as system receipt time.
5. **Schema version mismatch**:
   - UI must clearly show “unsupported schemaVersion” failures in Exceptions and/or run outcome.

Error messaging expectations:
- Validation errors: show inline (filters) or banner with backend-provided message.
- Unauthorized: show “You do not have access to Availability Feed Ops” with no data leakage.
- Backend unavailable: show retry option and preserve user filters.

---

## 8. Data Requirements

### Entities involved (frontend-consumed)
(Backend entities; Moqui frontend uses them as DTOs)
- `NormalizedAvailability` (or `normalized_availability`)
- `UnmappedManufacturerParts` (or `unmapped_manufacturer_parts`)
- `ExceptionQueue` (integration exception records)
- Optional: `FeedIngestionRun` / `FeedReceiptAudit` (name TBD; required for “Ingestion Runs” view)

### Fields (type, required, defaults)

#### Normalized Availability (read-only)
- `id` (UUIDv7, required)
- `productId` (UUIDv7, required)
- `manufacturerId` (UUIDv7, required)
- `manufacturerPartNumber` (string, required)
- `availableQty` (decimal, required)
- `uom` (string, required)
- `unitPrice.amount` (decimal, required)
- `unitPrice.currency` (string ISO4217, required)
- `leadTimeDaysMin` (int, required)
- `leadTimeDaysMax` (int, required)
- `minOrderQty` (int, nullable)
- `packSize` (int, required)
- `sourceLocationCode` (string, required)
- `asOf` (timestamp UTC, required)
- `receivedAt` (timestamp UTC, required)
- `schemaVersion` (string, required)

#### Unmapped Manufacturer Parts (read-only unless triage supported)
- `id` (UUIDv7, required)
- `manufacturerId` (UUIDv7, required)
- `manufacturerPartNumber` (string, required)
- `firstSeen` (timestamp UTC, required)
- `lastSeen` (timestamp UTC, required)
- `occurrenceCount` (int, required)
- `status` (enum: PENDING_REVIEW, RESOLVED, IGNORED; required)

#### Ingestion Run Summary (required for runs screen; exact shape TBD)
Minimum fields UI needs:
- `runId` (string/UUID, required)
- `manufacturerId` (UUIDv7, required)
- `asOf` (timestamp UTC, required)
- `receivedAt` (timestamp UTC, required)
- `schemaVersion` (string, required)
- `itemCount` (int, required)
- `mappedCount` (int, required)
- `unmappedCount` (int, required)
- `outcome` (enum: SUCCESS, PARTIAL_SUCCESS, FAILED; required)
- `errorSummary` (string, nullable)
- `correlationId` (string, nullable)

#### Exception Queue (read-only)
Minimum fields UI needs:
- `exceptionId` (string/UUID)
- `source` (string; e.g., “PositivityManufacturerFeed”)
- `manufacturerId` (UUIDv7, nullable)
- `asOf` (timestamp, nullable)
- `createdAt` (timestamp, required)
- `severity` (enum/string, required)
- `message` (string, required)
- `details` (object/string, nullable)
- `correlationId` (string, nullable)
- `status` (OPEN/ACKED/RESOLVED or similar; TBD)

### Read-only vs editable by state/role
- All availability records are **read-only**.
- Unmapped parts **status update** is **optional** and must be permission-gated if supported.
- Exceptions are **read-only** in this story (ack/resolve out-of-scope unless backend already has it and product requests it).

### Derived/calculated fields (UI-only)
- “Freshness age” = now - `asOf` (display only; no business policy thresholds assumed)
- “Mapping rate” = mappedCount / itemCount (display only)

---

## 9. Service Contracts (Frontend Perspective)

> Backend endpoints are not defined for frontend in the provided frontend issue; therefore exact Moqui service names and REST paths are **Open Questions**. Below are required contract shapes the frontend needs.

### Load/view calls (required)
1. **List ingestion runs**
   - Inputs: `manufacturerId?`, `asOfFrom?`, `asOfTo?`, `outcome?`, `pageIndex`, `pageSize`, `sort`
   - Output: list of run summaries + total count

2. **Get run detail**
   - Inputs: `runId` OR (`manufacturerId` + `asOf`)
   - Output: run summary + optional per-item error counts + linkable identifiers

3. **List normalized availability**
   - Inputs: `manufacturerId?`, `productId?`, `manufacturerPartNumber?`, `asOfFrom?`, `asOfTo?`, pagination/sort
   - Output: list + total

4. **List unmapped parts**
   - Inputs: `manufacturerId?`, `status?`, `manufacturerPartNumber?` (search), pagination/sort
   - Output: list + total

5. **List exceptions**
   - Inputs: `source?`, `manufacturerId?`, `createdFrom?`, `createdTo?`, `status?`, pagination/sort
   - Output: list + total
   - Detail view call optional if list doesn’t include `details`

### Create/update calls (optional)
- **Update unmapped part status** (ONLY if backend supports)
  - Inputs: `id`, `status`, optional `note`
  - Output: updated record

### Submit/transition calls
- None required (unless unmapped status update is treated as transition).

### Error handling expectations
- `400`: show validation error message; do not clear filters.
- `401/403`: route to access denied screen or show forbidden banner; do not reveal data.
- `404` (detail views): show “Not found (may have been archived)” and link back to list.
- `409` (if any concurrency on status update): show conflict and refresh row.
- `5xx/timeout`: show retry CTA; log client-side error event (see Observability).

---

## 10. State Model & Transitions

### Allowed states

#### UnmappedManufacturerParts.status
- `PENDING_REVIEW`
- `RESOLVED`
- `IGNORED`

#### Ingestion run outcome
- `SUCCESS`
- `PARTIAL_SUCCESS`
- `FAILED`

#### Exception status (if present)
- TBD by backend (OPEN/ACKED/RESOLVED etc.)

### Role-based transitions
- Only users with ops/integration permission can access screens.
- Only users with a specific permission (TBD) can update unmapped part status (if implemented).

### UI behavior per state
- Ingestion Runs:
  - FAILED: highlight row; show errorSummary; link to Exceptions filtered by correlationId/runId.
  - PARTIAL_SUCCESS: show unmappedCount > 0 and link to Unmapped Parts filtered.
- Unmapped Parts:
  - PENDING_REVIEW: default filter; show occurrenceCount prominently.
  - RESOLVED/IGNORED: visible via filter; updates (if supported) require confirmation.

---

## 11. Alternate / Error Flows

1. **Empty state: no runs**
   - Show “No ingestion runs found for selected filters” with suggestion to expand date range.

2. **Empty state: no unmapped parts**
   - Show “No unmapped parts currently pending review.”

3. **Backend unavailable**
   - Show non-blocking error banner + retry; keep last successful data cached in screen state until refresh (UI-only behavior).

4. **Unauthorized access**
   - User navigates directly to URL: show Access Denied; no partial render of sensitive data.

5. **Invalid filter values**
   - If user inputs malformed UUID/date: prevent submit and show inline validation.
   - If backend rejects: show banner + mark invalid field.

6. **Detail not found**
   - Run detail URL stale due to retention/archival: show not found + link back.

7. **Concurrency (only if status update exists)**
   - Another operator updates status: on save, backend returns 409 or updated object differs; UI refreshes row and shows message.

---

## 12. Acceptance Criteria

### Scenario 1: View ingestion runs list
**Given** I am an authenticated Integration Operator with permission to view availability feed ops  
**When** I open the “Availability Feeds (Ops) → Ingestion Runs” screen  
**Then** I see a paginated list of ingestion runs including manufacturerId, asOf, receivedAt, itemCount, mappedCount, unmappedCount, and outcome  
**And** I can filter by manufacturerId and asOf date range

### Scenario 2: Navigate from partial success run to unmapped parts
**Given** an ingestion run exists with outcome `PARTIAL_SUCCESS` and `unmappedCount > 0`  
**When** I select that run and click “View unmapped parts”  
**Then** I am taken to the Unmapped Parts screen with filters pre-applied for that manufacturerId  
**And** I see unmapped items with manufacturerPartNumber, firstSeen, lastSeen, occurrenceCount, and status

### Scenario 3: Search for a specific unmapped part number
**Given** I am on the Unmapped Parts screen  
**When** I search for manufacturerPartNumber “ABC-123”  
**Then** the list updates to show matching unmapped records (or an explicit empty state if none)

### Scenario 4: View normalized availability records
**Given** normalized availability records exist for a manufacturerId within a date range  
**When** I open “Normalized Availability” and filter by manufacturerId and asOf range  
**Then** I see records including productId, manufacturerPartNumber, availableQty, uom, unitPrice, leadTime min/max, minOrderQty (blank if null), packSize, sourceLocationCode, and asOf

### Scenario 5: Access denied
**Given** I am authenticated but do not have permission to view availability feed ops  
**When** I navigate to any Availability Feeds (Ops) URL directly  
**Then** I see an access denied message  
**And** no operational data is displayed  
**And** the UI does not attempt to fetch list data after receiving a 403

### Scenario 6: Backend error handling
**Given** the backend service returns a 5xx or times out when loading ingestion runs  
**When** I open the Ingestion Runs screen  
**Then** I see an error banner with a retry action  
**And** my selected filters remain intact

### Scenario 7 (optional, only if supported): Update unmapped part status
**Given** I have permission to triage unmapped parts  
**And** an unmapped part is in `PENDING_REVIEW`  
**When** I set its status to `IGNORED` and confirm  
**Then** the UI calls the update endpoint  
**And** the row updates to show status `IGNORED`  
**And** the change is reflected on refresh

---

## 13. Audit & Observability

### User-visible audit data
- Display timestamps on records (`receivedAt`, `asOf`, `firstSeen`, `lastSeen`, `createdAt`).
- For any status update (if implemented), show “Last updated at/by” **only if backend provides**.

### Status history
- Not required unless backend exposes it; if exposed, render read-only history on detail view.

### Traceability expectations
- Provide copyable identifiers:
  - manufacturerId, asOf, runId/correlationId
  - exceptionId/correlationId
  - unmapped part id + manufacturerPartNumber
- Frontend logs (client-side) should include correlationId/runId when available in responses.

---

## 14. Non-Functional UI Requirements

- **Performance**: List screens must support pagination; do not fetch unbounded lists.
- **Accessibility**: All controls keyboard accessible; table rows have accessible labels; error banners announced.
- **Responsiveness**: Works on tablet widths (Ops may use tablet on floor).
- **i18n/timezone**: Display timestamps in user locale but preserve UTC in data; show timezone indicator (e.g., “asOf (UTC)” or convert with clear label).
- **Currency**: Display unitPrice currency code; do not assume USD-only in UI.

---

## 15. Applied Safe Defaults

- SD-UI-EMPTY-STATE: Provide explicit empty state messaging for lists; safe because it doesn’t affect domain logic and improves usability. (Impacted: UX Summary, Error Flows, Acceptance Criteria)
- SD-UI-PAGINATION: Default pagination (pageSize e.g., 25) with server-side paging; safe because it’s UI ergonomics and avoids performance issues. (Impacted: UX Summary, Service Contracts, Non-Functional)
- SD-ERR-STANDARD: Standard mapping of HTTP errors (400/401/403/404/409/5xx) to banner/inline messages; safe because it is generic error handling without domain policy. (Impacted: Service Contracts, Error Flows, Acceptance Criteria)
- SD-OBS-CLIENT-LOG: Log client-side fetch failures with screen name and correlationId if present; safe because it’s observability boilerplate without secrets. (Impacted: Audit & Observability, Error Flows)

---

## 16. Open Questions

1. **Backend API contracts (blocking):** What are the exact endpoints (paths), request params, and response schemas for:
   - ingestion run list + detail
   - normalized availability list
   - unmapped parts list (+ optional status update)
   - exception queue list + detail  
   (Need this to implement Moqui service calls and data bindings.)

2. **Authorization (blocking):** What permission(s)/role(s) gate access to these screens? Are they the same as existing “inventory ops” permissions or new integration-specific ones?

3. **ExceptionQueue scope (blocking):** Is there an existing exception queue API in this frontend/backends, or must this screen be limited to inventory-domain exceptions only? What fields are guaranteed (severity, status, details)?

4. **Unmapped status update (scope decision):** Should the frontend allow changing `UnmappedManufacturerParts.status` (PENDING_REVIEW/IGNORED/RESOLVED), or is it strictly read-only in v1?

5. **Retention/TTL UX:** Backend mentions TTL/archival policy is an implementation decision. Will runs/availability be retained, and should UI support selecting archived ranges?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Availability: Normalize Manufacturer Inventory Feeds (Stub via Positivity) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/110


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Availability: Normalize Manufacturer Inventory Feeds (Stub via Positivity)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/110
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Availability: Normalize Manufacturer Inventory Feeds (Stub via Positivity)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Integration Operator**, I want manufacturer availability/lead-time feeds so that we can estimate fulfillment for special orders.

## Details
- Map manufacturer part numbers to internal productId.
- Capture lead time, backorder status, min-order rules (optional).

## Acceptance Criteria
- Ingestion idempotent.
- Lead time/status exposed.
- Errors routed to exception queue.

## Integrations
- Positivity connectors fetch feeds; product normalizes; workexec can display lead-time messaging.

## Data / Entities
- ExternalAvailability, ManufacturerPartMap, ExceptionQueue

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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

====================================================================================================

END FRONTEND STORY (FULL CONTEXT)

====================================================================================================