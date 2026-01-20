## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

## 1. Story Header

### Title
[FRONTEND] Availability: Normalize Distributor Inventory Feeds (Stub via Positivity) — Admin UI for Ingest, Exceptions, and Query

### Primary Persona
Integration Operator

### Business Value
Provide an operator-facing UI to (1) trigger and monitor distributor availability feed ingestion (stub v1), (2) review and resolve mapping/normalization exceptions, and (3) query normalized availability for downstream consumption/testing—enabling a unified, auditable availability view.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Integration Operator  
- **I want** a Moqui/Quasar UI to ingest (stub) distributor availability feeds, review exceptions, and query normalized availability  
- **So that** distributor feeds can be operationalized and validated without direct database access or ad-hoc scripts.

### In-scope
- Moqui screens (Vue/Quasar) to:
  1) Manually trigger ingestion of a stub feed payload/path via Positivity-backed service endpoint(s)  
  2) View last ingest runs and high-level outcomes (counts, timestamps, distributor)  
  3) View/search exception queue entries, inspect payload/reason, and mark as acknowledged/resolved (UI-driven status change only)  
  4) Query normalized availability records by product/distributor/region and view normalized vs raw fields
- Frontend validation and error handling aligned to backend contracts
- Routing, navigation, and basic RBAC gating (permission-based UI enable/disable)

### Out-of-scope
- Designing/implementing the actual ingestion pipeline logic (backend responsibility)
- Creating or editing SKU mapping logic beyond invoking existing mapping-management endpoints (if any)
- Defining distributor onboarding, pricing policies, valuation, or allocation/reservation logic
- Building live Positivity connector UI (v1 stub only)

---

## 3. Actors & Stakeholders

### Actors
- **Integration Operator**: triggers ingestion, monitors runs, triages exceptions
- **Inventory Data Steward** (secondary): may resolve mapping gaps (often outside this UI unless mapping screens exist)

### Stakeholders
- **Work Execution users** (indirect): depend on normalized availability accuracy
- **Inventory Manager** (indirect): uses availability/lead times for planning
- **Engineering / On-call**: uses UI for troubleshooting

---

## 4. Preconditions & Dependencies

- Backend provides endpoints/services (or Moqui service facades) to:
  - Trigger stub ingestion (e.g., “process feed from file/static endpoint”)
  - List/query normalized availability records
  - List/query exception queue entries and update exception status/acknowledgement
  - (Optional) list ingest job runs / last run status (if not available, UI must only show “trigger accepted”)
- User is authenticated and authorized; permissions are enforced server-side.
- Entities exist conceptually (names from reference): `ExternalAvailability`, `DistributorSkuMap`, `ExceptionQueue`. UI must not assume direct entity CRUD unless backend explicitly exposes it.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Inventory → Availability Feeds** (new menu entry)
- Deep links:
  - `/inventory/availability/feeds`
  - `/inventory/availability/exceptions`
  - `/inventory/availability/search`

### Screens to create/modify
Create new screen tree (example; final paths should follow repo conventions):
- `component://durion/screen/inventory/availability/AvailabilityFeeds.xml` (landing + trigger)
- `.../AvailabilityExceptions.xml` (exception queue list + detail)
- `.../AvailabilitySearch.xml` (query normalized availability)

Add menu/nav link in existing inventory menu screen.

### Navigation context
- Tabs or subnav within Availability section:
  - **Ingest**
  - **Exceptions**
  - **Search**

### User workflows

#### Happy path: Trigger ingest and verify data
1. Operator opens **Availability Feeds → Ingest**
2. Selects `distributorId` and provides stub source (path or selects a configured stub)
3. Clicks **Run Ingestion**
4. UI shows submission success, displays run correlation info (if returned), and links to:
   - exceptions filtered by distributor and time
   - normalized availability search filtered by distributor

#### Alternate path: Review exceptions
1. Operator opens **Exceptions**
2. Filters by distributor and reasonCode (e.g., `SKU_UNMAPPED`)
3. Opens exception detail to view raw payload + error message
4. Marks as **Acknowledged** (or **Resolved** if backend supports) with optional note
5. Returns to list; item reflects new status

#### Alternate path: Query normalized availability
1. Operator opens **Search**
2. Filters by productId and/or distributorId and/or shipFromRegionCode
3. Views normalized fields and raw fields for traceability

---

## 6. Functional Behavior

### Triggers
- User action: click **Run Ingestion**
- User action: change filters/search
- User action: open exception detail
- User action: acknowledge/resolve exception

### UI actions
- **Run Ingestion**
  - Validate required inputs
  - Call backend “trigger ingest” service
  - Render result summary (accepted/started + counts if returned)
- **Exceptions list**
  - Query backend with pagination/sort
  - Render reasonCode, distributorId, firstSeenAt, lastSeenAt, occurrenceCount, status
- **Exception detail**
  - Load exception by id
  - Render payload (read-only), reasonCode, errorMessage, timestamps
  - Allow status update action (acknowledge/resolve)
- **Availability search**
  - Query backend with filters
  - Render normalized and raw fields:
    - quantityAvailable, leadTimeDaysMin/Max, shipFromRegionCode
    - normalizationPolicyVersion, rawLeadTimeRaw, rawShipFromRegionRaw, lastUpdatedAt

### State changes
- Exception status transitions (only if backend supports):
  - `OPEN` → `ACKNOWLEDGED`
  - `ACKNOWLEDGED` → `RESOLVED`
  - (Optional) `RESOLVED` → `OPEN` (reopen) if supported  
If statuses are not defined by backend, UI must treat exceptions as read-only and only support “Add operator note” if available.

### Service interactions
- Trigger ingestion: `availability.feed.ingestStub` (placeholder name; must align with actual backend/Moqui service)
- Query normalized availability: `availability.externalAvailability.search`
- Exception queue:
  - list/search: `availability.exception.search`
  - view: `availability.exception.get`
  - update status: `availability.exception.updateStatus`

(If the Moqui frontend uses REST calls via a gateway rather than Moqui services, map these to REST endpoints instead; see Open Questions.)

---

## 7. Business Rules (Translated to UI Behavior)

- **BR1 Idempotency is mandatory (backend rule):**
  - UI must allow re-running ingestion without warning that implies duplication risk.
  - UI copy should state: “Re-running the same feed will refresh lastUpdatedAt without creating duplicates.”
- **BR2 Mapping authority is DistributorSkuMap (backend rule):**
  - UI must not attempt to “guess” mappings.
  - Exceptions with `SKU_UNMAPPED` must be clearly labeled as requiring mapping creation elsewhere.
- **BR3 Record-level atomicity:**
  - After an ingest run, UI must show counts including succeeded/failed/exception if provided; otherwise show “Run submitted; see Exceptions.”
- **BR4 Explainable normalization:**
  - Availability Search results must display `normalizationPolicyVersion` and raw fields alongside normalized fields.

### Validation
- Ingest form:
  - `distributorId` required
  - Stub source required (either `stubPath` or `stubPayload`, depending on backend contract)
- Exception status update:
  - status must be one of allowed values from backend (UI must not hardcode unknown enums; if backend returns allowed transitions, use them)

### Enable/disable rules
- Disable **Run Ingestion** while request is in-flight
- Disable exception status actions if user lacks permission or backend returns 403
- Hide/disable entire Availability section if user lacks base permission (see Open Questions)

### Error messaging expectations
- 400: show validation message inline (e.g., “distributorId is required”)
- 403: show “You do not have permission to perform this action.”
- 409: show concurrency/updated-by-other message on exception status updates
- 5xx/network: show retryable toast + preserve form inputs

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- **ExternalAvailability** (normalized availability record; read-only in UI)
- **ExceptionQueue** (exception records; status may be updatable)
- **DistributorSkuMap** (referenced; not edited in this story unless an endpoint exists—out-of-scope)

### Fields

#### Ingest request (UI model)
- `distributorId` (string/UUID; required)
- `asOf` (datetime UTC; optional; if provided, sent to backend)
- One of:
  - `stubPath` (string; required if backend uses file path)
  - OR `payload` (JSON object; required if backend accepts posted payload)

#### ExternalAvailability (read-only list/detail fields)
- `productId` (UUID; required)
- `distributorId` (string; required)
- `quantityAvailable` (int >= 0; required)
- `leadTimeDaysMin` (int|null)
- `leadTimeDaysMax` (int|null)
- `shipFromRegionCode` (string; required)
- `normalizationPolicyVersion` (string; required)
- `rawLeadTimeRaw` (string; nullable)
- `rawShipFromRegionRaw` (string; nullable)
- `lastUpdatedAt` (datetime UTC; required)

#### ExceptionQueue (list/detail fields)
- `exceptionId` (string/UUID; required)
- `distributorId` (string; required)
- `reasonCode` (enum; required)
- `errorMessage` (string; required)
- `payload` (JSON/text; required)
- `firstSeenAt` (datetime UTC; required)
- `lastSeenAt` (datetime UTC; required)
- `occurrenceCount` (int; required)
- `status` (enum; required if backend supports)
- `operatorNote` (string; optional if supported)

### Read-only vs editable by state/role
- ExternalAvailability: read-only for all roles in this story
- ExceptionQueue:
  - payload and system fields read-only
  - status/operatorNote editable only for authorized users and only for allowed transitions

### Derived/calculated fields (UI-only)
- Display “Lead time” as:
  - `min-max days` if both present
  - `min days` if only min present
  - `—` if null
- Display “Age” for exception as `now - firstSeenAt` (presentation only)

---

## 9. Service Contracts (Frontend Perspective)

> Note: exact service names/routes must align with the backend implementation. The frontend must be written to the definitive contract once confirmed.

### Load/view calls
1. **Search External Availability**
   - Request params:
     - `productId?`, `distributorId?`, `shipFromRegionCode?`
     - `pageIndex`, `pageSize`, `sort`
   - Response:
     - `items[]: ExternalAvailability`
     - `pageIndex`, `pageSize`, `totalCount`

2. **Search Exceptions**
   - Request params:
     - `distributorId?`, `reasonCode?`, `status?`, `from?`, `to?`
     - pagination/sort
   - Response:
     - `items[]: ExceptionQueueSummary`
     - pagination envelope

3. **Get Exception Detail**
   - Request: `exceptionId`
   - Response: `ExceptionQueue` record including payload

### Create/update calls
4. **Update Exception Status / Note**
   - Request:
     - `exceptionId`
     - `newStatus`
     - `operatorNote?`
     - `expectedVersion?` (if backend uses optimistic locking)
   - Response:
     - updated exception record

### Submit/transition calls
5. **Trigger Stub Ingestion**
   - Request:
     - `distributorId`
     - `asOf?`
     - `stubPath` OR `payload`
   - Response (preferred):
     - `runId` / `correlationId`
     - `receivedCount`, `normalizedCount`, `exceptionCount` (optional if async)
     - `startedAt`, `finishedAt` (if sync)

### Error handling expectations
- Standard envelope mapping:
  - `fieldErrors[]` for 400 → map to form fields
  - `message` for non-field errors → toast/banner
- 403 → show permission error and keep user on screen
- 404 on exception detail → show “Exception not found” with back link
- 409 on update → prompt to reload record

---

## 10. State Model & Transitions

### Allowed states (ExceptionQueue)
If backend supports exception workflow states, UI must support at least:
- `OPEN`
- `ACKNOWLEDGED`
- `RESOLVED`

### Role-based transitions
- Integration Operator:
  - may transition `OPEN → ACKNOWLEDGED`
  - may transition `ACKNOWLEDGED → RESOLVED`
- Read-only roles:
  - no transitions; view only

(Exact permissions are unknown; see Open Questions.)

### UI behavior per state
- `OPEN`: show primary action “Acknowledge”
- `ACKNOWLEDGED`: show action “Resolve”
- `RESOLVED`: show read-only; optionally “Reopen” only if backend supports

---

## 11. Alternate / Error Flows

### Validation failures
- Missing `distributorId` on ingest: inline error; do not submit
- Missing stub source (if required): inline error

### Concurrency conflicts
- Exception updated elsewhere:
  - backend returns 409 or version mismatch
  - UI shows conflict message and reload button; preserves operator note in local state so it can be re-applied

### Unauthorized access
- User navigates to Availability section without permission:
  - Screen shows “Not authorized” (or redirects to home) based on app convention
- User attempts action:
  - Disable buttons if permission info is known; otherwise handle 403 gracefully

### Empty states
- No exceptions: show “No exceptions found for current filters”
- No availability results: show “No normalized availability found” + suggestion to run ingest

---

## 12. Acceptance Criteria

### Scenario 1: Trigger stub ingestion successfully
**Given** I am an authenticated Integration Operator with permission to run availability ingestion  
**And** I am on the Availability Feeds “Ingest” screen  
**When** I select a distributorId and provide the required stub source input  
**And** I click “Run Ingestion”  
**Then** the UI submits the trigger request to the backend  
**And** I see a confirmation that the run was accepted (including a runId/correlationId if provided)  
**And** the Run button is disabled while the request is in-flight and re-enabled afterward.

### Scenario 2: Ingest validation blocks submission
**Given** I am on the “Ingest” screen  
**When** I click “Run Ingestion” without selecting a distributorId  
**Then** I see an inline validation error for distributorId  
**And** no backend request is made.

### Scenario 3: View exceptions list with filters
**Given** exceptions exist for distributor D1  
**When** I open the “Exceptions” screen and filter by distributorId=D1  
**Then** I see a paginated list of exception records  
**And** each row includes reasonCode, occurrenceCount, lastSeenAt, and status (if supported).

### Scenario 4: View exception detail
**Given** an exception with id E1 exists  
**When** I open exception E1 from the list  
**Then** I see the raw payload, reasonCode, and errorMessage  
**And** I can navigate back to the filtered list.

### Scenario 5: Acknowledge an open exception (if backend supports status updates)
**Given** exception E1 is in status OPEN  
**And** I have permission to update exception status  
**When** I click “Acknowledge” and confirm  
**Then** the UI sends an update request for E1  
**And** E1 status becomes ACKNOWLEDGED in the UI  
**And** if the backend returns 403, the UI shows a permission error and E1 remains unchanged.

### Scenario 6: Query normalized availability
**Given** normalized availability exists for product P1 and distributor D1  
**When** I open the “Search” screen and filter by productId=P1 and distributorId=D1  
**Then** I see availability results including quantityAvailable, shipFromRegionCode, leadTimeDaysMin/Max  
**And** I can see normalizationPolicyVersion and the raw lead time/region fields for traceability.

### Scenario 7: Backend error handling on ingestion trigger
**Given** the backend ingestion trigger returns a 5xx error  
**When** I click “Run Ingestion”  
**Then** the UI shows a retryable error message  
**And** my entered form values remain intact.

---

## 13. Audit & Observability

### User-visible audit data
- Exception detail shows timestamps: `firstSeenAt`, `lastSeenAt`
- If exception status updates are supported, show:
  - last updated timestamp
  - last updated by (if backend returns actor)

### Status history
- If backend provides status history, display in exception detail as a read-only list (timestamp, from/to, actor).  
- If not available, do not invent; show only current status.

### Traceability expectations
- After triggering ingestion, display returned `runId/correlationId` and include it in UI logs (console/network) for support correlation.

---

## 14. Non-Functional UI Requirements

- **Performance**: exception and availability lists must support server-side pagination; initial load under 2s for typical datasets (first page).
- **Accessibility**: all actions keyboard reachable; dialogs have focus trap; tables have accessible labels; color not sole indicator for status.
- **Responsiveness**: screens usable on tablet widths; tables may switch to stacked rows/cards on small screens if consistent with app patterns.
- **i18n/timezone**: display timestamps in user locale/timezone but keep underlying values UTC; show timezone indicator in UI.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE  
  - **Assumed**: Standard empty-state messages for no results/no exceptions.  
  - **Why safe**: Pure UI ergonomics; does not change domain logic.  
  - **Impacted sections**: UX Summary, Alternate / Error Flows, Acceptance Criteria.

- SD-UX-PAGINATION  
  - **Assumed**: Server-side pagination with page size default (e.g., 25) and sort by `lastSeenAt desc` for exceptions.  
  - **Why safe**: UI ergonomics; does not define business policy.  
  - **Impacted sections**: UX Summary, Service Contracts, Non-Functional UI Requirements.

- SD-ERR-HTTP-MAPPING  
  - **Assumed**: Standard handling for 400/403/404/409/5xx with inline field errors and toast/banner messages.  
  - **Why safe**: Generic error UX mapping; does not invent domain rules.  
  - **Impacted sections**: Business Rules, Service Contracts, Alternate / Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. What are the **actual Moqui endpoints/services** to call for:
   - triggering stub ingestion,
   - querying ExternalAvailability,
   - querying/updating ExceptionQueue?
   (Provide route/service names and request/response schemas.)

2. What **permissions/roles** gate:
   - viewing Availability section,
   - triggering ingestion,
   - updating exception status/adding operator notes?

3. Does ExceptionQueue support **status lifecycle** (`OPEN/ACKNOWLEDGED/RESOLVED`) and **optimistic locking/versioning**? If yes, what are the exact field names (`status`, `version`, etc.) and allowed transitions?

4. Is the stub ingest input **file-path-based** (e.g., `AVAIL_FEED_STUB_PATH`) or **payload-posted** from UI, or both? If both, which is preferred for v1?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Availability: Normalize Distributor Inventory Feeds (Stub via Positivity) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/111


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Availability: Normalize Distributor Inventory Feeds (Stub via Positivity)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/111
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Availability: Normalize Distributor Inventory Feeds (Stub via Positivity)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Integration Operator**, I want to ingest distributor availability feeds so that a unified availability view can be presented.

## Details
- Map distributor SKUs to internal productId.
- Normalize qty, lead time, ship-from region.
- Stub connector acceptable in v1.

## Acceptance Criteria
- Ingestion idempotent.
- Mapping errors routed to exception queue.
- Normalized availability queryable.

## Integrations
- Positivity connectors fetch feeds; product normalizes for inventory/workexec.

## Data / Entities
- ExternalAvailability, DistributorSkuMap, ExceptionQueue

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