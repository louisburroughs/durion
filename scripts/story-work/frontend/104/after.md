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

**Title:** [FRONTEND] [STORY] Topology: Sync Locations from durion-hr (Admin UI for LocationRef + SyncLog)  
**Primary Persona:** Inventory Admin / Ops Admin (with inventory topology permissions)  
**Business Value:** Ensures inventory operations are scoped to the authoritative roster of valid shops/mobile sites by providing visibility into synced LocationRefs and auditability of sync runs, and by preventing selection/use of deactivated locations in inventory flows.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Inventory Admin (and the System via scheduled/event-driven sync),
- **I want** a frontend interface to view synced locations (LocationRef) and sync audit logs (SyncLog),
- **So that** I can confirm topology is up-to-date, detect drift/failures, and ensure users cannot initiate new stock movements to inactive locations.

### In-scope
- Moqui/Quasar screens to:
  - List/search/view **LocationRef** records synced from `durion-hr`
  - View **SyncLog** entries for runs/events and outcomes
  - Provide “Sync now” (manual trigger) **if** backend supports it
- UI enforcement in inventory-related location pickers (where locations are selected) to block **inactive** locations from being selectable for **new** stock movements, consistent with backend rules.
- Display of location status/timezone/tags in location selection contexts.

### Out-of-scope
- Implementing the actual sync ingestion/reconciliation logic (backend responsibility)
- Automatic transfers, reconciliation workflows for PendingTransfer stock, or inventory ledger operations
- Editing HR-owned fields (name/status/timezone/tags) from the frontend (must be read-only)
- Defining/implementing permissions/roles beyond using existing authz checks from backend

---

## 3. Actors & Stakeholders
- **Inventory Admin / Ops Admin:** monitors location roster, audits sync runs, investigates failures
- **Warehouse Staff / Service Advisors:** choose locations for stock movements; must be prevented from using inactive locations
- **SRE/Ops:** uses sync logs/metrics to monitor health and troubleshoot
- **durion-hr system:** authoritative topology source (upstream)

---

## 4. Preconditions & Dependencies
- Backend provides read APIs for:
  - `LocationRef` list/detail (including `hrLocationId`, `name`, `status/isActive`, `timezone`, `tags`, timestamps, version)
  - `SyncLog` list/detail (including `hrEventId`, payload summary, appliedAt, outcome, errorMessage)
- Backend enforces: “Deactivated locations cannot receive new stock movements” (rejects with 422); frontend should proactively prevent selection.
- Frontend has at least one inventory flow that selects a location for a stock movement (receipt/transfer/consume/adjust). If none exist yet, this story can only implement admin visibility screens and defer picker enforcement.

**Dependency:** Backend story `durion-positivity-backend#40` (Topology: Sync Locations from durion-hr) should be available or stubbed in Moqui services.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Inventory → Topology → Locations (Synced)**
- Main navigation: **Inventory → Topology → Sync Logs**

### Screens to create/modify
1. **New Screen:** `apps/pos/inventory/topology/LocationRefList.xml`
   - List + filters + row navigation to detail
2. **New Screen:** `apps/pos/inventory/topology/LocationRefDetail.xml`
   - Read-only detail of a location ref + recent sync log entries for that `hrLocationId`
3. **New Screen:** `apps/pos/inventory/topology/SyncLogList.xml`
   - List + filters (date range, outcome, hrLocationId, hrEventId)
4. **Optional New Screen/Section:** embedded “Sync run details” (if backend exposes run IDs)
5. **Modify existing inventory movement screens/components** (where a location is selected):
   - Enforce that only Active locations are selectable for new movements
   - Show clear messaging if an inactive location is pre-populated (historical record) but cannot be used for new actions

### Navigation context
- From LocationRef list → LocationRef detail
- From SyncLog list → (optional) SyncLog detail or modal viewer for payload/error
- Cross-links:
  - From LocationRef detail → filtered SyncLog list for that location
  - From SyncLog row (if contains `hrLocationId`) → LocationRef detail

### User workflows
- **Happy path (admin visibility):**
  1. Admin opens LocationRef list
  2. Filters to Active/Inactive, searches by name or HR location ID
  3. Opens a record to confirm timezone/tags/status and sees recent SyncLog outcomes
- **Alternate path (failure investigation):**
  1. Admin opens SyncLog list
  2. Filters to `FAILED` or `INVALID_PAYLOAD`
  3. Opens payload/error to diagnose and share with SRE/back-end team
- **Operational path (picker enforcement):**
  1. User opens a stock movement screen
  2. Location picker shows only Active locations
  3. If user tries to proceed with an inactive location (e.g., via stale state), UI blocks and shows message; backend would also reject with 422.

---

## 6. Functional Behavior

### Triggers
- User navigates to Topology screens
- User searches/filters lists
- User opens details
- (Optional) User clicks “Sync now” to trigger reconciliation

### UI actions
- List paging, sorting, filtering
- View detail
- Copy-to-clipboard for IDs (hrLocationId, internal id, hrEventId)
- Open payload viewer (read-only) for SyncLog entries (truncate large payloads with expand)

### State changes
- None directly in `LocationRef` (read-only in UI)
- If “Sync now” exists: creates a backend sync run and new SyncLog entries (backend-owned)

### Service interactions
- Load list/detail via Moqui services (see section 9)
- For location pickers: load Active-only locations; if backend returns inactive in a detail context, mark as disabled and show reason.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- No frontend editing of HR-owned fields (`hrLocationId`, `name`, `status`, `timezone`, `tags`)
- Filters:
  - Status filter supports `ACTIVE` and `INACTIVE` (and `PENDING` only if backend uses it)

### Enable/disable rules
- Location selection controls for **new stock movements** must:
  - Only allow selection of locations where `isActive=true` (or `status=ACTIVE`)
  - Disable/omit inactive locations from dropdown/search results
- If a screen displays a historical movement referencing an inactive location:
  - The location may be displayed read-only, but action buttons that create a *new* movement to that location must be disabled with explanatory text.

### Visibility rules
- SyncLog error message shown only when `outcome in (FAILED, INVALID, RETRY)` and `errorMessage` present
- Payload viewer only for authorized users if backend restricts (UI must handle 403)

### Error messaging expectations
- When backend returns **422** for movement attempts to inactive locations, show:
  - “Location is inactive and cannot receive new stock movements. Select an active location.”
- For 403: “You do not have permission to view this resource.”
- For 5xx/timeouts: generic retry guidance; do not expose stack traces.

---

## 8. Data Requirements

### Entities involved (frontend-viewed)
- **LocationRef** (inventory-owned reference table)
- **SyncLog** (immutable audit log)

### Fields
**LocationRef (read-only UI)**
- `id` (UUID, required, read-only)
- `tenantId` (string, required, read-only; may be implicit)
- `hrLocationId` (string, required, read-only)
- `name` (string, required, read-only)
- `status` (enum: `ACTIVE|INACTIVE|PENDING`, required)
- `isActive` (boolean, derived or stored; required if present)
- `timezone` (string IANA TZ, required if provided)
- `tags` (JSON, optional; display as key/value chips or JSON viewer)
- `effectiveFrom` / `effectiveTo` (datetime, optional)
- `createdAt/By`, `updatedAt/By` (datetime/string, optional)
- `deactivatedAt/By` (datetime/string, optional)
- `version` (number, optional, for optimistic locking; display only)

**SyncLog (read-only UI)**
- `syncId` (UUID, required)
- `hrEventId` (string, optional/required depending on backend)
- `hrPayload` (JSON, optional; may be truncated)
- `appliedAt` (datetime, required)
- `appliedBy` (string, optional)
- `outcome` (enum: `OK|INVALID|RETRY|FAILED`, required)
- `errorMessage` (string, optional)
- (Optional) `hrLocationId` (string, if backend includes to filter; otherwise link via payload)

### Read-only vs editable by state/role
- All fields read-only in this story.
- “Sync now” action (if present) is a button, not entity editing.

### Derived/calculated fields
- UI-derived:
  - `displayStatus` = `status` plus `isActive` indicator if both exist
  - `tagCount` = number of keys in tags
  - `payloadPreview` = first N chars/keys of hrPayload for list display

---

## 9. Service Contracts (Frontend Perspective)

> Blocking: backend contract details (service names/paths, request/response shapes) are not provided in inputs; below is Moqui-oriented expectation and must be mapped to actual services once confirmed.

### Load/view calls
- `inventory.locationRef.list`
  - Inputs: `status?`, `isActive?`, `hrLocationId?`, `name?`, `tag?`, `pageIndex`, `pageSize`, `orderBy`
  - Output: list of LocationRef summaries + `totalCount`
- `inventory.locationRef.get`
  - Inputs: `id` **or** `hrLocationId`
  - Output: full LocationRef record
- `inventory.syncLog.list`
  - Inputs: `outcome?`, `hrEventId?`, `hrLocationId?`, `fromDate?`, `toDate?`, pagination
  - Output: list + `totalCount`
- `inventory.syncLog.get` (optional)
  - Inputs: `syncId`
  - Output: full SyncLog incl. payload

### Create/update calls
- None for LocationRef/SyncLog (read-only)
- Optional manual sync trigger:
  - `inventory.topology.syncLocations.trigger`
    - Inputs: `mode` (`FULL|INCREMENTAL`) if supported
    - Output: `syncRunId` or confirmation

### Submit/transition calls
- None

### Error handling expectations
- 401 → route to login/session refresh (existing app behavior)
- 403 → show permission error component
- 404 → show “Not found” with link back to list
- 422 on movement create/submit due to inactive location → show rule-specific message (see section 7)
- Network/5xx → standard retry UI with “Try again” and support reference ID if available

---

## 10. State Model & Transitions

### Allowed states (LocationRef)
- `ACTIVE`: selectable for new stock movements
- `INACTIVE`: not selectable for new stock movements
- `PENDING`: display-only; selection behavior **needs clarification** (see Open Questions)

### Role-based transitions
- No frontend transitions. State changes come from `durion-hr` sync.

### UI behavior per state
- ACTIVE: normal display; selectable in pickers
- INACTIVE: badge “Inactive”; disabled in pickers; detail view shows deactivatedAt/By if available
- PENDING: badge “Pending”; behavior TBD (block selection until active unless clarified)

---

## 11. Alternate / Error Flows

### Validation failures
- Filter form:
  - If date range invalid (fromDate > toDate), prevent search and show inline error
- Manual sync trigger (if exists):
  - If backend returns 400, show returned validation message
  - Disable trigger button while request in-flight to prevent duplicates

### Concurrency conflicts
- Not applicable (read-only screens)

### Unauthorized access
- If user lacks permission to view topology:
  - Screen shows 403 state; navigation item hidden if permissions are discoverable client-side (otherwise rely on server response)

### Empty states
- LocationRef list empty:
  - Show “No locations synced yet.” and (if manual sync exists) a “Sync now” CTA; otherwise instructions to check HR integration.
- SyncLog list empty:
  - Show “No sync activity recorded.”

---

## 12. Acceptance Criteria

### Scenario 1: View synced locations list
**Given** the user has permission to view inventory topology  
**When** they open “Inventory → Topology → Locations (Synced)”  
**Then** the system displays a paginated list of LocationRefs including hrLocationId, name, status (and timezone if available)  
**And** the user can filter by status (ACTIVE/INACTIVE) and search by hrLocationId or name.

### Scenario 2: View a location ref detail and related sync logs
**Given** at least one LocationRef exists  
**When** the user opens a LocationRef detail page  
**Then** the system shows the LocationRef fields as read-only (including status, timezone, tags)  
**And** shows recent SyncLog entries linked to that location (if available) including appliedAt, outcome, and error message for failures.

### Scenario 3: View sync logs and inspect failures
**Given** SyncLog entries exist with outcomes FAILED or INVALID  
**When** the user opens “Inventory → Topology → Sync Logs” and filters to FAILED  
**Then** the list shows matching entries with hrEventId (if present), appliedAt, outcome, and error message summary  
**And** the user can open a log entry to view payload details (or a safe preview) when permitted.

### Scenario 4: Inactive location is not selectable for new stock movements
**Given** a LocationRef exists with status INACTIVE (or isActive=false)  
**When** a user attempts to select a destination/location on a “new stock movement” screen  
**Then** the inactive location is not selectable (hidden or disabled)  
**And** the UI communicates that inactive locations cannot receive new stock movements.

### Scenario 5: Backend rejects an attempt due to inactive location (defense in depth)
**Given** the user submits a stock movement referencing an inactive location (e.g., stale UI state)  
**When** the backend responds with HTTP 422 indicating the location is inactive  
**Then** the frontend displays “Location is inactive and cannot receive new stock movements. Select an active location.”  
**And** the user remains on the form with inputs preserved.

### Scenario 6: Permissions enforced
**Given** the user lacks permission to view topology data  
**When** they navigate directly to the LocationRef list URL  
**Then** the frontend shows an unauthorized/forbidden state  
**And** no LocationRef data is displayed.

---

## 13. Audit & Observability

### User-visible audit data
- SyncLog list/detail provides:
  - appliedAt, outcome, hrEventId, errorMessage, and payload preview/detail (as allowed)
- LocationRef detail shows:
  - created/updated timestamps and deactivatedAt/By when present

### Status history
- No new status history UI beyond what SyncLog provides unless backend exposes historical records.

### Traceability expectations
- UI should display/copy identifiers:
  - `LocationRef.id`, `LocationRef.hrLocationId`, `SyncLog.syncId`, `SyncLog.hrEventId`
- If backend returns correlation/request IDs in headers, surface them in error UI (optional; safe default only if existing app pattern).

---

## 14. Non-Functional UI Requirements
- **Performance:** LocationRef and SyncLog lists should load within 2s p95 on typical broadband for page size 25 (excluding backend latency issues); use pagination, avoid rendering full JSON payloads in list views.
- **Accessibility:** All interactive controls keyboard accessible; status indicated with text (not color-only); payload viewer supports copy/select.
- **Responsiveness:** Works on tablet widths used in shops; tables become stacked/condensed on small screens.
- **i18n/timezone:** Display `appliedAt` and timestamps in user locale, but preserve timezone context (show stored location timezone as text). Do not convert the stored `timezone` value; only timestamps formatting changes.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging for empty LocationRef/SyncLog lists; safe because it doesn’t alter domain logic. Impacted sections: UX Summary, Alternate/Empty states.
- SD-UX-PAGINATION-25: Default page size 25 with server-side pagination; safe UI ergonomics default. Impacted sections: UX Summary, Service Contracts.
- SD-ERR-STD-MAPPING: Standard handling for 401/403/404/422/5xx with non-leaky messaging; safe because it follows conventional HTTP semantics without inventing policy. Impacted sections: Service Contracts, Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. **Backend service contracts:** What are the exact Moqui service names (or REST endpoints) and request/response schemas for `LocationRef` and `SyncLog` list/detail? (Needed to implement screens.)  
2. **Permissions:** What permission(s) gate access to Topology screens and SyncLog payload visibility? (Needed for nav visibility and 403 expectations.)  
3. **Manual sync trigger:** Does the backend support an on-demand “Sync now” / reconcile action? If yes, what are the inputs/outputs, and should it be restricted to admins only?  
4. **Definition of “stock movements” in the current frontend:** Which specific screens/components in `durion-moqui-frontend` represent “new inbound stock movements” (receipts/transfers/adjustments/consumption) that must block inactive locations? Provide routes/screen names to update.  
5. **PENDING status behavior:** If `LocationRef.status=PENDING` exists in v1, should PENDING locations be selectable for new movements, or treated as inactive until ACTIVE?  
6. **Tags display expectations:** Should tags be treated as opaque JSON, or are there known keys that need friendly rendering/filtering?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Topology: Sync Locations from durion-hr — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/104

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Topology: Sync Locations from durion-hr  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/104  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Topology: Sync Locations from durion-hr

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As a **System**, I want to sync location identifiers and metadata from durion-hr so that inventory is scoped to valid shops and mobile sites.

## Details  
- Import locationId, name, status, timezone, and tags.  
- Keep a local reference table for FK integrity.

## Acceptance Criteria  
- Location refs created/updated idempotently.  
- Deactivated locations cannot receive new stock movements.  
- Audit sync runs.

## Integrations  
- HR → Inventory location roster API/events.

## Data / Entities  
- LocationRef, SyncLog

## Classification (confirm labels)  
- Type: Story  
- Layer: Domain  
- Domain: Inventory Management

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

BACKEND STORY REFERENCES (FOR REFERENCE ONLY)

----------------------------------------------------------------------------------------------------

Backend matches (extracted from story-work):


[1] backend/40/backend.md

    Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory, layer:domain

----------------------------------------------------------------------------------------------------

Backend Story Full Content:

### BACKEND STORY #1: backend/40/backend.md

------------------------------------------------------------

Title: [BACKEND] [STORY] Topology: Sync Locations from durion-hr  
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/40  
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory, layer:domain

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:ready-for-dev

### Recommended
- agent:inventory
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** inventory-flexible

## Story Intent
Keep the system-of-record for physical locations (shops, mobile sites) synchronized from the authoritative HR topology source (`durion-hr`) so inventory is always scoped and validated against a current roster of active locations.

## Actors & Stakeholders
- `durion-hr` service (authoritative source of locations)
- Inventory service (primary consumer; owns local `LocationRef` and enforces location-level constraints)
- Service Advisors / Warehouse Staff (use locations in stock movements)
- Finance/Operations (rely on accurate location scoping for reporting)
- SRE/Operations (monitor sync health)

## Preconditions
- `durion-hr` exposes either a roster API and/or emits location lifecycle events (create/update/deactivate) that can be consumed.
- Inventory service has database and permissions to write `LocationRef` records.
- An established correlation/identity mapping exists between HR location identifiers and Inventory location references.

## Functional Behavior
1. Source-of-truth: `durion-hr` is authoritative for location identity, name, status, timezone, and tags.
2. Sync Modes (both supported):
   - Event-driven (preferred): `durion-hr` publishes location events (LocationCreated/LocationUpdated/LocationDeactivated) which Inventory consumes. Inventory processes events idempotently and updates local `LocationRef`.
   - Bulk/REST sync: Inventory calls HR roster API to reconcile full or incremental roster on schedule or on-demand.
3. Idempotent Upsert: For each incoming record/event, Inventory upserts a `LocationRef` by `hrLocationId` within tenant scope:
   - If not present -> INSERT with provided fields.
   - If present -> UPDATE changed fields and bump `version`.
   - Always write an immutable `SyncLog` entry recording payload, source event id, and outcome.
4. Deactivation semantics (resolved):
   - On HR deactivation, Inventory marks `LocationRef.is_active=false`, records `deactivated_at`, `deactivated_by`, and links `hr_event_id`.
   - Inventory prevents **new inbound** stock movements to that location.
   - Remaining on-hand quantities are marked as **`PendingTransfer`** (derived state) for manual reconciliation; no automatic transfers in v1.
   - Reconciliation actions (manual, authorized roles only): transfer-out, adjust/write-off, dispose/RTV.
   - Default reconciliation SLA: **5 business days** before escalation.
5. Identity mapping (resolved):
   - `hrLocationId` is unique within a tenant. Use composite key: `tenantId + ":" + hrLocationId` (or unique constraint on `(tenant_id, hr_location_id)`).
6. Reconciliation job and FK integrity: A periodic reconciliation job compares HR roster to local `LocationRef` and emits alerts for drift. Orders and transactions reference `LocationRef.id` (not raw HR id) to maintain FK integrity.
7. Backpressure & retries: Event consumption uses at-least-once delivery with idempotency on `hr_event_id`, exponential backoff with jitter, max retries 10 over ~30 minutes, and DLQ/escalation on repeated failures.

## Alternate / Error Flows
- Missing HR field: If an incoming record lacks required fields, record `SyncLog` with `INVALID_PAYLOAD` and flag for manual review; do not overwrite existing fields with nulls.
- Transient failures: Retry with exponential backoff; after retries exhausted, write `SyncFailure`, send to DLQ/quarantine, and alert.
- Unknown HR location referenced by inventory operations: Default v1 behavior is to **reject with 422**; optional config can allow `PENDING` location creation when explicitly enabled.

## Business Rules
- HR owns location lifecycle and authoritative attributes; Inventory enforces local constraints derived from that model.
- Inventory must not invent or permanently rename HR-provided identifiers; any local display names may include local suffixes but must store the canonical `hr_location_id` and tenant.
- Deactivated locations cannot receive NEW stock movements; existing stock is `PendingTransfer` and requires manual reconciliation per SLA.
- Sync must be auditable and idempotent; every applied change must create a `SyncLog` entry with source event id and `appliedAt`.
- Reconciliation job must run at configurable intervals and emit `location.sync.drift.count` when discrepancies are found.

## Data Requirements
- `LocationRef` table (inventory.schema.location_ref):
  - `id` (UUID PK)
  - `tenant_id` (string) -- tenant scope
  - `hr_location_id` (string, unique per tenant)
  - `name` (string)
  - `status` (ENUM: `ACTIVE`, `INACTIVE`, `PENDING`)
  - `timezone` (IANA tz)
  - `tags` (jsonb)
  - `is_active` (boolean)
  - `effective_from`, `effective_to` (timestamp)
  - `created_at`, `created_by`, `updated_at`, `updated_by`
  - `deactivated_at`, `deactivated_by`
  - `version` (optimistic lock)
  - Index: unique on `(tenant_id, hr_location_id)`, index on `is_active`

- `SyncLog` table:
  - `sync_id` (UUID PK)
  - `hr_event_id` (string)
  - `hr_payload` (jsonb)
  - `applied_at`, `applied_by`, `outcome` (OK/INVALID/RETRY/FAILED)
  - `error_message` (nullable)

- Reconciliation job state table for last-run timestamps and metrics.

## Acceptance Criteria
- Scenario: Event-driven creation
  - Given `durion-hr` emits `LocationCreated` with hrLocationId=L100 and required fields
  - When Inventory consumes the event
  - Then a `LocationRef` for L100 exists with `status=ACTIVE`, a `SyncLog` entry is recorded, and `location.sync.processed` metric increments

- Scenario: Idempotent update
  - Given the same `LocationUpdated` event is delivered twice
  - When processed
  - Then the second processing is a no-op (idempotent) and `SyncLog` shows duplicate delivery handled with same resulting `LocationRef`

- Scenario: Deactivation blocks receipts
  - Given HR marks L100 as deactivated
  - When an inbound stock movement to L100 is attempted
  - Then Inventory rejects the movement with a clear error (422) and records audit entry referencing `deactivated_at` and `hr_event_id`

- Scenario: Bulk reconcile finds drift
  - Given HR roster and local `LocationRef` differ
  - When reconciliation runs
  - Then discrepancies are logged, `location.sync.drift.count` metric increases, and a reconcile report is saved

## Audit & Observability
- `SyncLog` entries for every inbound HR payload and applied action.
- Metrics: `location.sync.processed`, `location.sync.failures`, `location.sync.drift.count`, `location.sync.lag_seconds`.
- Tracing: spans for event ingestion -> upsert -> reconciliation with `hrLocationId` tag.
- Alerts: high failure rates or large drift trigger Ops alerts per SLO.

## Resolved Decisions (from clarification #410)
- Deactivation policy (v1): **Option B** — mark existing stock as `PendingTransfer` and require manual reconciliation within **5 business days**.
- Identity mapping: use composite key `tenantId + ":" + hrLocationId` (unique per tenant); store `(tenant_id, hr_location_id)` with unique constraint.
- Sync SLA: **event-driven p95 ≤ 60s**; bulk reconcile freshness ≤ 15 minutes. Retry policy: at-least-once, exponential backoff, max 10 attempts over ~30 minutes, DLQ + alert on exhaustion.

## Open Questions
- Should Inventory create a configurable `HOLDING` virtual location for manual transfers, or rely solely on manual transfer-out to existing active locations? (Config keys provided in clarification; decide if `inventory.location.holding.enabled` should default to true.)

---