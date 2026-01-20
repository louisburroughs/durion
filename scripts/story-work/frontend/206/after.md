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

STOP: Clarification required before finalization

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Accounting Events: Idempotency, Replay, Conflict Visibility & Retry Controls

## Primary Persona
Accounting Ops / System Auditor (operational user monitoring and resolving ingestion integrity issues)

## Business Value
Prevent duplicate GL impact from event replays, surface integrity conflicts for triage, and provide safe retry controls for failed events—ensuring auditability, operational recoverability, and financial data integrity.

---

# 2. Story Intent

## As a / I want / So that
- **As an** Accounting Ops user (or System Auditor)  
- **I want** a UI to search and inspect accounting event ingestion idempotency records, identify replays vs conflicts, and trigger safe retries for failed events  
- **So that** duplicate submissions do not create duplicate financial impact, conflicts are escalated and traceable, and ingestion failures can be retried without duplicating postings.

## In-scope
- New Moqui screens to:
  - Search/list idempotency records by `eventId`, status, date range, and eventType
  - View an idempotency record including stored response and GL posting reference
  - Show replay vs new processing outcomes (as recorded by backend)
  - List and view conflict records (exceptions queue) and their state (`OPEN` → `TRIAGED` → resolution terminals)
  - Trigger a **retry** for `FAILED` idempotency records (hash match) via backend endpoint (if provided)
- Frontend error mapping for:
  - `409 Conflict` for conflicting duplicates
  - Unauthorized access errors
  - Validation errors (invalid UUID, missing required params)
- Audit/observability in UI (show who/when, correlation where available)

## Out-of-scope
- Implementing backend idempotency, hashing, DLQ publishing, or retention/tiering jobs (backend-owned)
- Editing/overwriting original events or conflict payloads (must remain immutable)
- Automated conflict resolution (workflow is Accounting Ops; UI may support manual state transitions only if backend supports it—currently unclear)
- Defining GL account mappings, posting rules, or journal entry logic

---

# 3. Actors & Stakeholders
- **Accounting Ops**: triages ingestion failures and conflicts, initiates retries, documents resolution notes (if supported).
- **System Auditor**: reviews idempotency behavior and traceability for compliance.
- **SRE / Support**: uses UI to diagnose spikes in conflicts/failures; correlates to DLQ references.
- **Event Producers (systems)**: indirectly impacted; conflict detection may indicate producer bugs.

---

# 4. Preconditions & Dependencies
- Backend provides read APIs for:
  - Idempotency store records (by `eventId`, list/search)
  - Conflict store records (list/search, view by `conflictId`)
- Backend provides a command API to **retry** failed events (or documents retry is automatic only).
- Authentication/authorization is implemented and exposes permissions/scopes for accounting operations.
- Moqui frontend project has standard layout, routing, auth guard conventions (per repository README).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Main navigation (Accounting section) entry:
  - `Accounting → Events → Ingestion Monitor` (name is implementable; exact nav placement depends on existing menu model)

## Screens to create/modify (Moqui)
Create new screens under an accounting/events namespace (final paths may vary by repo conventions):
1. **Event Ingestion Monitor (Search/List)**  
   - Screen: `apps/<app>/screens/accounting/events/IngestionMonitor.xml`
2. **Idempotency Record Detail**  
   - Screen: `.../IdempotencyDetail.xml`
3. **Conflict Queue (List)**  
   - Screen: `.../ConflictQueue.xml`
4. **Conflict Detail**  
   - Screen: `.../ConflictDetail.xml`

## Navigation context
- From Ingestion Monitor list → Idempotency detail
- From Conflict Queue → Conflict detail
- Cross-links:
  - Idempotency detail: link to conflict detail if related conflict exists (requires backend to expose link)
  - Conflict detail: link to idempotency record by `eventId`

## User workflows
### Happy path: check event status / replay result
1. User searches by `eventId`
2. User opens record
3. UI shows status `COMPLETED` and displays cached `glPostingReference` and `responsePayload`
4. User confirms replay behavior via “Seen count/lastSeenAt” (if available)

### Alternate path: conflict triage
1. User opens Conflict Queue filtered to `OPEN`
2. Opens a conflict
3. Reviews hashes, DLQ reference, flaggedAt/flaggedBy
4. Marks as TRIAGED / resolves (only if backend supports transitions; otherwise read-only)

### Alternate path: retry failed
1. User filters idempotency status = `FAILED`
2. Opens record
3. Clicks “Retry processing”
4. UI confirms outcome and refreshes status (no duplicate posting)

---

# 6. Functional Behavior

## Triggers
- User navigates to screens (load list/detail)
- User submits search filters
- User clicks retry
- User attempts conflict state change (conditional on backend support)

## UI actions
### Ingestion Monitor (list)
- Filter inputs:
  - `eventId` (exact match)
  - `status` (`PROCESSING|COMPLETED|FAILED`)
  - `eventType` (string match; exact/contains depends on backend)
  - `firstSeenAt` date range (from/to)
- Results table columns (minimum):
  - `eventId`
  - `eventType` (if stored)
  - `status`
  - `firstSeenAt`
  - `lastSeenAt`
  - `glPostingReference` (nullable)
  - action: View

### Idempotency Detail
Display read-only fields (if present):
- `eventId`
- `status`
- `domainPayloadHash`
- `eventType` (if available)
- `firstSeenAt`, `lastSeenAt`
- `glPostingReference`
- `responsePayload` (JSON viewer; collapsible)
- `archivedAt` (if applicable)
Actions:
- `Retry` button visible only when `status == FAILED` **and** user has permission (see Open Questions)
- `View related conflict` link if backend indicates conflict exists

### Conflict Queue (list)
Filters:
- `eventId`
- `conflictState` (`OPEN|TRIAGED|RESOLVED_ACCEPT_ORIGINAL|RESOLVED_ACCEPT_NEW|RESOLVED_INVALID_PRODUCER`)
- `flaggedAt` date range
Columns:
- `conflictId`
- `eventId`
- `conflictState`
- `flaggedAt`
- `dlqMessageRef` (nullable)
- action: View

### Conflict Detail
Read-only display:
- `conflictId`, `eventId`
- `originalDomainPayloadHash`, `conflictingDomainPayloadHash`
- `conflictState`
- `flaggedAt`, `flaggedBy`
- `dlqMessageRef`
- `resolutionNotes` (nullable)
Actions (only if backend supports):
- Transition to `TRIAGED`
- Resolve to one of terminal states with required notes (policy-driven; see Open Questions)

## State changes (frontend-observable)
- Idempotency record can change `FAILED → PROCESSING → COMPLETED` after retry.
- Conflict record can change `OPEN → TRIAGED → RESOLVED_*` (if command endpoints exist).

## Service interactions
- Read: list and detail fetches
- Write/command:
  - Retry failed event
  - Update conflict state / resolution notes (if supported)

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- `eventId` search input:
  - Must be a valid UUID format (UUIDv7 acceptable as UUID)
  - If invalid: block search submit and show inline error “Enter a valid UUID”
- Date range:
  - `from <= to` else inline error

## Enable/disable rules
- **Retry** enabled only when:
  - record `status == FAILED`
  - user is authorized
  - record is not currently `PROCESSING` (if backend exposes)
- Conflict state transition controls enabled only when:
  - backend exposes allowed transitions for current state (preferred)
  - user authorized

## Visibility rules
- Hide response payload viewer when `responsePayload` is null; show “No stored response”
- Show warning banner when:
  - status `FAILED` (actionable)
  - conflict detected (if the idempotency record links to a conflict)

## Error messaging expectations
- `409 Conflict` (conflicting duplicate): show a blocking banner:
  - “Conflict detected for eventId <id>. This indicates the same eventId was received with different business payload. See Conflict Queue.”
- `403/401`: show “You don’t have access to Accounting Event Ingestion Monitor.”
- `5xx`: show standard retryable error with “Try again” and preserve filters.

---

# 8. Data Requirements

## Entities involved (frontend view models)
> Exact Moqui entity names are **unknown** from provided inputs; frontend must bind to backend responses.

### Idempotency Record (hot tier)
Fields expected (from backend story):
- `eventId` (string UUID) **required**
- `domainPayloadHash` (string SHA-256 hex) **required**
- `status` (enum: `PROCESSING|COMPLETED|FAILED`) **required**
- `responsePayload` (JSON/object) optional
- `glPostingReference` (string) optional
- `firstSeenAt` (datetime) required
- `lastSeenAt` (datetime) required
- `archivedAt` (datetime) optional
Additional (not guaranteed):
- `eventType` (string) optional

Read-only vs editable
- All fields read-only in UI; user does not edit idempotency records.

### Conflict Record (exceptions queue)
Fields expected:
- `conflictId` (string/UUID) required
- `eventId` (string UUID) required
- `originalDomainPayloadHash` required
- `conflictingDomainPayloadHash` required
- `conflictState` enum required
- `flaggedAt` datetime required
- `flaggedBy` string optional/required (unclear)
- `resolutionNotes` string optional
- `dlqMessageRef` string optional

Editable by state/role
- `resolutionNotes` editable only if backend supports updates and user authorized
- `conflictState` changeable only through allowed transitions and authorization

## Derived/calculated fields (UI)
- “Age” = now - `firstSeenAt` (display-only)
- “Replay count” not available unless backend provides; do not infer.

---

# 9. Service Contracts (Frontend Perspective)

## Load/view calls
> Endpoints are not provided in inputs. Define Moqui screen transitions/services with placeholders and require backend contract confirmation.

- `GET /accounting/events/idempotency` (list/search)
  - Query params: `eventId`, `status`, `eventType`, `firstSeenFrom`, `firstSeenTo`, `pageIndex`, `pageSize`, `sort`
- `GET /accounting/events/idempotency/{eventId}` (detail)

- `GET /accounting/events/conflicts` (list/search)
  - Query params: `eventId`, `conflictState`, `flaggedFrom`, `flaggedTo`, pagination/sort
- `GET /accounting/events/conflicts/{conflictId}` (detail)

## Create/update calls
- None for idempotency records.
- Conflict updates (only if supported):
  - `POST /accounting/events/conflicts/{conflictId}/transition` with `toState` (+ `resolutionNotes` when resolving)

## Submit/transition calls
- Retry failed event:
  - `POST /accounting/events/idempotency/{eventId}/retry`
  - Response: updated idempotency record and/or command receipt

## Error handling expectations
- `400` validation: show inline errors (bad UUID, invalid date range)
- `401/403`: route to login or show access denied
- `404`: show “Record not found” (eventId/conflictId)
- `409`: show conflict banner and link to Conflict Queue filtered by eventId
- `5xx`: show retryable error; keep screen state

---

# 10. State Model & Transitions

## Allowed states (from backend story)
### Idempotency record status
- `PROCESSING`
- `COMPLETED`
- `FAILED`

UI behavior per state:
- PROCESSING: show spinner/badge; disable retry
- COMPLETED: show glPostingReference/responsePayload; retry hidden
- FAILED: show error banner; allow retry if authorized

### Conflict record state
- `OPEN` → `TRIAGED` → terminal:
  - `RESOLVED_ACCEPT_ORIGINAL`
  - `RESOLVED_ACCEPT_NEW` (must be via compensating/reversal events; UI must not imply overwrite)
  - `RESOLVED_INVALID_PRODUCER`

Role-based transitions
- Unknown exact roles/permissions; must be clarified.
- UI must not present transitions the user cannot perform.

---

# 11. Alternate / Error Flows

## Validation failures
- Invalid `eventId` format:
  - Prevent submit; show inline validation
- Invalid date range:
  - Prevent submit; show inline validation

## Concurrency conflicts
- If user retries while backend transitions to PROCESSING:
  - Backend returns `409` or `409/423` equivalent; UI refreshes record and shows “Already processing”

## Unauthorized access
- Accessing any screen without permission:
  - Show access denied screen; do not leak record existence via differing messages (prefer consistent 403 handling)

## Empty states
- No idempotency records match filters:
  - Show “No events found” and keep filters
- No conflicts in OPEN state:
  - Show “No open conflicts”

---

# 12. Acceptance Criteria (Gherkin)

## Scenario 1: Search idempotency records by eventId
**Given** I am an authenticated user with permission to view accounting event ingestion records  
**And** an idempotency record exists for `eventId = ABC-123`  
**When** I open the Ingestion Monitor and search for `eventId = ABC-123`  
**Then** I see exactly one result with `eventId = ABC-123`  
**And** I can navigate to the Idempotency Detail screen for that event.

## Scenario 2: View completed idempotency record shows cached GL reference
**Given** an idempotency record exists with `status = COMPLETED` and `glPostingReference` populated  
**When** I view the Idempotency Detail  
**Then** the UI displays `status = COMPLETED`  
**And** the UI displays the `glPostingReference`  
**And** if `responsePayload` exists, the UI renders it read-only.

## Scenario 3: Retry is available only for FAILED records
**Given** an idempotency record exists with `status = FAILED`  
**When** I view the Idempotency Detail  
**Then** the UI shows a “Retry processing” action  
**And** when I click “Retry processing”  
**Then** the UI calls the retry endpoint for that `eventId`  
**And** the UI refreshes and shows the updated status (PROCESSING or COMPLETED)  
**And** the UI does not create duplicate retries while a request is in-flight.

## Scenario 4: Retry is not available for COMPLETED records
**Given** an idempotency record exists with `status = COMPLETED`  
**When** I view the Idempotency Detail  
**Then** the UI does not show the “Retry processing” action.

## Scenario 5: Conflict queue lists OPEN conflicts and supports filtering by eventId
**Given** conflict records exist with `conflictState = OPEN`  
**When** I open Conflict Queue and filter to `conflictState = OPEN`  
**Then** I see a list of conflicts including `conflictId`, `eventId`, and `flaggedAt`  
**And** when I filter by a specific `eventId`  
**Then** only conflicts for that `eventId` are shown.

## Scenario 6: Backend returns 409 Conflict and UI surfaces conflict guidance
**Given** I attempt an operation that triggers a backend `409 Conflict` for a conflicting duplicate event  
**When** the UI receives the `409 Conflict` response  
**Then** the UI shows a clear conflict banner referencing the `eventId`  
**And** the UI provides a link to Conflict Queue filtered by that `eventId`.

## Scenario 7: Unauthorized users cannot access ingestion monitoring screens
**Given** I am authenticated but lack the required permission  
**When** I navigate to the Ingestion Monitor URL  
**Then** I receive an access denied experience (403)  
**And** no idempotency/conflict details are displayed.

---

# 13. Audit & Observability

## User-visible audit data
- Display audit timestamps:
  - Idempotency: `firstSeenAt`, `lastSeenAt`, `archivedAt`
  - Conflict: `flaggedAt`, `flaggedBy`, `resolutionNotes` (if present)
- Display immutable identifiers:
  - `eventId`, `conflictId`, hashes, `dlqMessageRef`

## Status history
- If backend provides history (not specified), show a read-only “Status History” panel.
- Otherwise, show current state only; do not fabricate history.

## Traceability expectations
- Screens should include copyable identifiers for support:
  - `eventId`, `conflictId`, `glPostingReference`
- Include correlation ID from HTTP responses if available (header-based; project-dependent).

---

# 14. Non-Functional UI Requirements

## Performance
- List screens must support pagination (server-side) and avoid rendering large JSON blobs inline.
- JSON payload viewer should lazy-render/collapse by default.

## Accessibility
- All actions keyboard-accessible
- Status communicated via text (not color-only)
- JSON viewer supports screen readers (at minimum: accessible label and copy-to-clipboard)

## Responsiveness
- Tables responsive with column priority; detail screens scrollable on small viewports.

## i18n/timezone/currency
- Datetimes displayed in user’s locale/timezone (do not change stored values).
- Currency not applicable for these screens.

---

# 15. Applied Safe Defaults
- SD-UX-PAGINATION-001: Use server-side pagination defaults (pageSize=25) for list screens; qualifies as UI ergonomics and does not change domain behavior. Impacted sections: UX Summary, Functional Behavior, Non-Functional.
- SD-UX-EMPTYSTATE-001: Provide standard empty-state messaging (“No results found”) for lists; safe UI ergonomics. Impacted sections: UX Summary, Alternate / Error Flows.
- SD-ERR-MAP-001: Map common HTTP statuses (400/401/403/404/409/5xx) to consistent banners/inline errors; safe because it only reflects backend outcomes. Impacted sections: Business Rules, Service Contracts, Alternate / Error Flows.

---

# 16. Open Questions

1. **Backend API contracts:** What are the exact endpoints/paths, request/response schemas, and pagination/sort conventions for:
   - idempotency list/detail
   - conflict list/detail
   - retry failed event
   - (optional) conflict state transitions?
2. **Authorization model:** What permissions/roles control:
   - viewing idempotency records
   - viewing conflict records
   - retrying failed events
   - transitioning conflict state / editing resolution notes?
3. **UI scope for conflict workflow:** Is the frontend expected to support **changing** `conflictState` and adding `resolutionNotes`, or is it strictly read-only with ops handled elsewhere?
4. **Linking idempotency ↔ conflict:** Does backend expose a direct relationship (e.g., idempotency record contains `conflictId`), or must UI search conflicts by `eventId`?
5. **EventType availability:** Is `eventType` stored alongside idempotency records (backend data model shows it in incoming event, but idempotency store fields listed do not include it)? If not stored, should UI omit eventType filtering?
6. **Retry behavior visibility:** On retry, does backend return synchronous updated status/record, or an async job reference requiring polling? If async, what is the polling endpoint and recommended interval?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Events: Implement Idempotency and Deduplication  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/206  
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] Events: Implement Idempotency and Deduplication

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
Events: Implement Idempotency and Deduplication

## Acceptance Criteria
- [ ] Duplicate submissions of same eventId are detected and do not create duplicate GL impact
- [ ] Conflicting duplicates (same eventId, different payload) are rejected and flagged
- [ ] Replays return the prior posting reference when already posted
- [ ] Retry workflow exists for failed events without duplicating postings


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