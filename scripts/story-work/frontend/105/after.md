STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:audit
- status:draft

### Recommended
- agent:audit-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** security-strict

---

## 1. Story Header

### Title
[FRONTEND] Immutable Audit Trail UI for Price/Cost Changes + Pricing Rule Evaluation Trace (Snapshot Drilldown + Search)

### Primary Persona
Auditor

### Business Value
Provide verifiable, immutable evidence for price/cost changes and “how price was calculated” rule traces so margin disputes and compliance investigations can be resolved with defensible, searchable records.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Auditor  
- **I want** to search immutable audit history for pricing/cost changes and drill from an estimate line’s `snapshotId` into the stored pricing snapshot and rule evaluation trace  
- **So that** I can explain pricing outcomes, validate margin calculations, and resolve disputes with unalterable evidence.

### In-scope
- New/updated **Moqui screens** for:
  - Searching audit records by product, location, and date range
  - Viewing an **audit record detail** (normalized fields + raw payload)
  - Viewing a **Pricing Snapshot** by `snapshotId` and its associated **Pricing Rule Trace**
  - Drill path: **estimate line → snapshot → rule trace** (read-only)
- Frontend enforcement of immutability expectations (no edit/delete UI; handle forbidden responses deterministically).
- Standard pagination, sorting, empty states, and error handling for audit query and detail views.

### Out-of-scope
- Implementing audit ingestion, retention enforcement, or immutability storage controls (backend responsibility).
- Defining or changing pricing rule logic.
- Creating/modifying audit records from the UI (this story is read/query/drilldown focused).
- Managing reason-code registry UI (not requested in provided inputs).

---

## 3. Actors & Stakeholders
- **Auditor (primary)**: reads/searches audit and pricing trace evidence.
- **Shop Manager / Support (secondary)**: may use the same UI to investigate disputes (authorization-dependent).
- **Pricing domain (stakeholder)**: producer of price/cost change events and pricing snapshot/trace records.
- **Workexec domain (stakeholder)**: stores `snapshotId` on estimate line(s); UI must drill from that reference.

---

## 4. Preconditions & Dependencies
- User is authenticated and authorized to access audit query/detail endpoints.
- Backend provides query/detail APIs for:
  - Audit log search (filter by product/location/date; plus any additional filters used by UI)
  - Pricing snapshot retrieval by `snapshotId`
  - Pricing rule trace retrieval (either embedded in snapshot response or via `ruleTraceId`)
- Workexec/estimate-line UI has access to the estimate line’s `snapshotId` (already stored) and provides an entry point (link/button) to open the snapshot/trace view.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
1. **Audit Search**: POS navigation entry “Audit” (or “Compliance/Audit”) leading to an audit search/list screen.
2. **Estimate Line Drilldown**: From an estimate line details context (Workexec screen), a “View Pricing Trace” action when `snapshotId` is present.

### Screens to create/modify
- **Create:** `apps/pos/audit/AuditSearch.xml` (screen)
  - Search form + results list
- **Create:** `apps/pos/audit/AuditDetail.xml` (screen)
  - View a single audit event by `eventId`
- **Create:** `apps/pos/audit/PricingSnapshotDetail.xml` (screen)
  - View PricingSnapshot by `snapshotId` + linked PricingRuleTrace
- **Modify (dependency):** Workexec estimate line screen to add navigation to `PricingSnapshotDetail` using `snapshotId` (screen name/path TBD)

### Navigation context
- Audit screens live under an audit module/menu, scoped by tenant/location context (if POS has a current location selector).
- Detail screens are deep-linkable with parameters:
  - `eventId` for AuditDetail
  - `snapshotId` for PricingSnapshotDetail

### User workflows
**Happy path A — Search audit**
1. Auditor opens Audit Search.
2. Sets filters: `productId` (or SKU), `locationId`, `fromDateTime`, `toDateTime`.
3. Submits → sees paginated reverse-chronological results.
4. Clicks a row → Audit Detail.

**Happy path B — Drill estimate line → snapshot → rule trace**
1. Auditor opens an estimate line in Workexec.
2. Clicks “View Pricing Trace” (visible only if `snapshotId` exists).
3. Pricing Snapshot Detail loads snapshot + trace.
4. Auditor views rule evaluation steps (read-only).

**Alternate path — Missing snapshot**
- If `snapshotId` is absent, the action is hidden/disabled with an explanatory tooltip or inline message.

---

## 6. Functional Behavior

### Triggers
- User navigates to audit search screen.
- User submits search filters.
- User opens audit detail by `eventId`.
- User opens pricing snapshot detail by `snapshotId` (from Workexec or direct URL).

### UI actions
- Search form submit triggers a Moqui transition that calls a service to query audit logs.
- Selecting a row navigates to detail screen with `eventId`.
- “View raw payload” toggles visibility of raw JSON (read-only, copyable).

### State changes
- No domain state changes are performed by the UI (read-only).
- UI maintains view state: selected filters, current page, sorting.

### Service interactions (frontend ↔ Moqui)
- Screen actions use Moqui `transition` + `service-call` to backend services (names TBD; see Open Questions).
- Responses mapped into screen context for rendering lists/details.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Date range required** for audit searches:
  - UI must require `fromDateTime` and `toDateTime` (or apply backend default if backend enforces last-90-days; see Open Questions).
  - Validate `fromDateTime <= toDateTime`.
- **At least one primary filter** must be provided to avoid unbounded scans:
  - Must include at least one of: `productId` (or SKU/productRef), `locationId` (if not implicitly scoped), `eventType`, `actorId`, `entityId`, or `snapshotId` (if supported).
  - If only date range is provided, UI should block submission with a clear message (unless backend explicitly allows).

### Enable/disable rules
- “View Pricing Trace” is enabled only when estimate line has a non-empty `snapshotId`.
- No edit/delete controls exist on audit/snapshot/trace screens.
- Any UI affordance to “modify” should be absent; if user attempts via URL/method, handle 403/405 gracefully.

### Visibility rules
- If user lacks permission (403) for audit screens: show an “Access denied” page state (standard POS unauthorized state).
- If `snapshotId` not found: show “Snapshot not found” with guidance (cannot proceed).

### Error messaging expectations
- Validation errors: inline form errors and a summary message.
- Backend 401 → redirect to login/session restore flow (project standard).
- Backend 403 → show forbidden/insufficient permissions.
- Backend 404 (eventId/snapshotId not found) → show not-found empty state.
- Backend 5xx/timeouts → show retry action; do not expose stack traces.

---

## 8. Data Requirements

### Entities involved (logical; UI consumes)
- `AuditLog` (immutable log entry)
- `PricingSnapshot`
- `PricingRuleTrace`

### Fields (type, required, defaults)
**AuditLog (list + detail)**
- `eventId` (string/UUID, required, read-only)
- `timestamp`/`occurredAt` (datetime UTC, required, read-only)
- `eventType` (string, required)
- `actorId` (string, required)
- `entityType` (string, required)
- `entityId` (string, required)
- `locationId` (string, required for filtering/display if available)
- `changeSummary` (text/JSON, optional; display if present)
- `oldValue` (JSON, optional; detail only)
- `newValue` (JSON, optional; detail only)
- `rawPayload` (JSON/text, optional; detail only)

**PricingSnapshot (detail)**
- `snapshotId` (string/UUID, required)
- `timestamp` (datetime UTC, required)
- `quoteContext` (JSON, required/optional? TBD by backend)
- `finalPrice` (money, optional)
- `ruleTraceId` (string/UUID, required if trace not embedded)

**PricingRuleTrace (embedded or separate)**
- `ruleTraceId` (string/UUID, required)
- `evaluationSteps` (array, required; can be empty)
  - Each step: `ruleId` (string), `status` (enum APPLIED/REJECTED/SKIPPED), `inputs` (JSON), `outputs` (JSON)

### Read-only vs editable by state/role
- All fields read-only for all roles in this story.
- Any attempt to mutate is out of scope; UI should not present mutation capabilities.

### Derived/calculated fields
- Display-friendly timestamp formatting (viewer timezone) while preserving UTC in raw view.
- Optional computed “Changed Field Count” if backend provides structured diff (TBD; do not invent).

---

## 9. Service Contracts (Frontend Perspective)

> Concrete service names/paths are not provided in inputs. The frontend must integrate with Moqui services that wrap backend APIs. Final contract requires clarification.

### Load/view calls
- `audit.search` (TBD): returns paginated audit records.
  - Inputs: `fromDateTime`, `toDateTime`, `productId` and/or `locationId`, pagination (`pageIndex/pageSize`), sort (default `occurredAt desc`)
- `audit.get#eventId` (TBD): returns audit record detail including raw payload.
- `pricingSnapshot.get#snapshotId` (TBD): returns PricingSnapshot and either embeds rule trace or returns `ruleTraceId`.
- `pricingRuleTrace.get#ruleTraceId` (TBD): returns full evaluation steps (if not embedded).

### Create/update calls
- none (read-only story).

### Submit/transition calls
- Moqui `transition` actions:
  - `AuditSearch` screen `search` transition → calls `audit.search`
  - `AuditDetail` screen `load` transition → calls `audit.get`
  - `PricingSnapshotDetail` screen `load` transition → calls `pricingSnapshot.get` and optionally `pricingRuleTrace.get`

### Error handling expectations
- Map backend error shapes into:
  - Field-level errors for 400 validation responses (if backend returns field details).
  - Global toast/banner for unexpected errors.
- Handle 403/405 on any “mutating” attempt as forbidden; ensure UI never calls such endpoints from these screens.

---

## 10. State Model & Transitions

### Allowed states
- Not applicable (audit entries/snapshots are immutable records; UI is read-only).

### Role-based transitions
- `Auditor` role can access:
  - Audit search/list
  - Audit detail
  - Pricing snapshot/trace detail (if authorized)
- If other roles are allowed, must be confirmed (see Open Questions).

### UI behavior per state
- N/A beyond authorized/unauthorized and found/not-found.

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Missing required date range → block search submit; show inline errors.
- Invalid range (`from > to`) → block; show inline errors.
- Missing primary filter (only date range) → block; show message explaining need for at least one filter.

### Concurrency conflicts
- N/A (read-only). If backend indicates record deleted (should not happen for audit), treat as 404.

### Unauthorized access
- 401: user must re-authenticate.
- 403: show Access Denied; do not leak whether eventId/snapshotId exists.

### Empty states
- Search yields 0 results: show “No audit records found for given filters” and keep filters visible.
- Snapshot exists but trace missing: show snapshot, and a “Trace unavailable” section with guidance (and log correlationId if present).

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Search audit logs by product/location/date
**Given** an Auditor is authenticated and authorized to view audit logs  
**And** the Auditor is on the Audit Search screen  
**When** the Auditor enters a valid `fromDateTime` and `toDateTime`  
**And** selects a `locationId`  
**And** enters a `productId`  
**And** submits the search  
**Then** the system displays a paginated list of matching immutable audit records  
**And** the results are sorted by occurred timestamp descending by default

### Scenario 2: Prevent unbounded audit searches
**Given** an Auditor is on the Audit Search screen  
**When** the Auditor enters only a date range with no other filter  
**And** submits the search  
**Then** the UI blocks the request  
**And** displays a message requiring at least one additional filter (e.g., product or location)

### Scenario 3: View audit record detail
**Given** an Auditor has performed a search and sees an audit record with an `eventId`  
**When** the Auditor opens the audit record detail  
**Then** the system shows the audit record fields as read-only  
**And** includes the raw payload (if available) in a read-only view

### Scenario 4: Drill from estimate line to pricing snapshot and rule trace
**Given** an Auditor is viewing an estimate line that contains a `snapshotId`  
**When** the Auditor selects “View Pricing Trace”  
**Then** the system loads the Pricing Snapshot for that `snapshotId`  
**And** displays the associated Pricing Rule Trace evaluation steps as read-only

### Scenario 5: Snapshot not found
**Given** an Auditor navigates to the Pricing Snapshot Detail screen with a `snapshotId` that does not exist  
**When** the screen loads  
**Then** the system displays a “Snapshot not found” state  
**And** does not display any stale or partial trace data

### Scenario 6: Unauthorized access handling
**Given** a user without audit permissions navigates to the Audit Search screen  
**When** the screen attempts to load or query audit data  
**Then** the system displays an access denied state (403 handling)  
**And** no audit data is shown

---

## 13. Audit & Observability

### User-visible audit data
- Display normalized audit metadata (eventId, occurredAt/timestamp, actor, entity, location, eventType).
- Provide raw payload view for evidentiary inspection (read-only).

### Status history
- Not applicable; audit records are immutable entries, but UI should show occurredAt and emittedAt if provided (TBD).

### Traceability expectations
- From estimate line: `snapshotId` must be shown (or accessible) and used as the drill key.
- Where available, display `correlationId` and `sourceSystem` for cross-system tracing (backend-provided).

---

## 14. Non-Functional UI Requirements

### Performance
- Audit search must support pagination; default page size (TBD) and a maximum page size enforced by UI control.
- Avoid rendering extremely large JSON blobs by default: raw payload collapsed by default with “expand/copy” actions.

### Accessibility
- All interactive controls keyboard accessible.
- JSON/raw payload view supports copy and is readable with screen readers (use proper labels).

### Responsiveness
- Screens usable on tablet sizes typical in POS environments; long JSON uses scroll containers.

### i18n/timezone/currency
- Timestamps displayed in user’s locale/timezone with UTC shown or available in raw detail.
- Money fields (finalPrice) display using tenant currency settings (TBD; do not assume).

---

## 15. Applied Safe Defaults
- **SD-UX-EMPTY-STATE**: Provide consistent empty/loading/error states for list/detail screens; safe because it doesn’t alter domain behavior. (Impacted: UX Summary, Alternate/Error Flows)
- **SD-UX-PAGINATION**: Use standard pagination controls and default sort descending by occurred timestamp; safe because it affects only presentation and query parameters. (Impacted: UX Summary, Service Contracts, Acceptance Criteria)
- **SD-ERR-MAP**: Map HTTP 401/403/404/5xx to standard POS UI states; safe because it’s generic error handling without changing business policy. (Impacted: Business Rules, Alternate/Error Flows)

---

## 16. Open Questions

1. **API/service contract**: What are the exact Moqui service names and request/response shapes for:
   - audit search (filters, pagination, sort)
   - audit detail by `eventId`
   - pricing snapshot by `snapshotId`
   - rule trace retrieval (embedded vs separate)?
2. **Date range rule**: Must the UI enforce a mandatory date range and “at least one indexed filter”, or does backend enforce defaults (e.g., last 90 days) and guardrails? Provide the canonical rule for audit queries in this product.
3. **Product identifier**: Should search filter use `productId`, SKU, part number, or multiple? What is the user-facing identifier in POS?
4. **Location scoping**: Is `locationId` implicitly derived from the current POS location context, or must it be selectable as a filter? Can auditors search across locations?
5. **Authorization**: Which roles besides “Auditor” are allowed to access audit search/detail and snapshot/trace screens (Shop Manager, Support)? Provide role names used in Moqui security groups.
6. **Workexec integration point**: Which exact Workexec screen/component should add “View Pricing Trace”, and what parameter name holds the snapshot reference (`snapshotId` exactly)?
7. **Trace format**: For `evaluationSteps`, what fields are guaranteed and what is the maximum expected size? Should the UI provide step filtering (applied-only) or is full list always required?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Security: Immutable Audit Trail for Price/Cost Changes and Rule Evaluations  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/105  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Security: Immutable Audit Trail for Price/Cost Changes and Rule Evaluations

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Auditor**, I want immutable history and pricing-rule traces so that we can explain margins and resolve disputes.

## Details
- Append-only audit for price books, overrides, costs.
- Keep evaluation traces for pricing quotes (rule trace).

## Acceptance Criteria
- Audit is append-only.
- Drill estimate line → snapshot → rule trace.
- Search by product/location/date.

## Integrations
- Workexec stores snapshotId for traceability.

## Data / Entities
- AuditLog, PricingRuleTrace, PricingSnapshot

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