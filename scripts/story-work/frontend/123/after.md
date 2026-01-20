## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Work Order: Display Operational Context (Location/Bay/Team) with Pre-Start Update Rules and Audit

### Primary Persona
Mechanic (primary), Shop Manager / Service Advisor (secondary for privileged updates)

### Business Value
Mechanics can immediately see where to work (location/bay) and who they’re working with (team/assigned mechanics), reducing misrouting and improving shop-floor coordination; managers retain controlled ability to correct dispatch context with traceable audit.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Mechanic  
- **I want** the work order view to display operational context (location/bay/team/schedule/resources/constraints)  
- **So that** I know where to work and who/what is assigned before starting work.

### In-Scope
- Add an **Operational Context** panel/section to the Work Order view screen(s).
- Fetch and render operational context from **Shopmgr (system of record)** via Workexec/Shopmgr REST endpoints described in backend reference.
- Enforce **update/edit rules**:
  - Mechanic: read-only.
  - Manager override: allowed (privileged action) and **restricted after work start**.
- Display audit/trace info for context and overrides (who/when/reason/version).
- Handle partial/missing context and upstream unavailability gracefully.

### Out-of-Scope
- Creating/maintaining operational context as a writable independent record in frontend.
- Configuring bays/locations/teams or permissions model details (security domain).
- Event-stream consumption in the frontend (polling/refresh only unless specified).
- Work order start/transition UI beyond what is required to support “after start lock” behavior (we will only *react* to current work order status).

---

## 3. Actors & Stakeholders
- **Mechanic**: Views work order; needs operational context for execution.
- **Shop Manager**: May request/submit overrides to operational context when permitted.
- **Service Advisor**: May also act as manager-equivalent depending on RBAC (needs clarification).
- **Shopmgr (Shop Management System)**: System of record for operational context.
- **Workexec backend**: Provides work order status and may proxy override actions / lock context at work start.
- **Audit/Compliance stakeholders**: Require immutable traceability for overrides and lock-at-start behavior.

---

## 4. Preconditions & Dependencies
### Preconditions
- User is authenticated in the Moqui UI.
- Work Order exists and is viewable by the current user.
- Work Order has a current status available (e.g., `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, etc.).

### Dependencies (Backend/Contracts)
This frontend story depends on backend endpoints/contracts (from backend reference):
- **Shopmgr Operational Context**
  - `GET /shopmgr/v1/workorders/{workOrderId}/operational-context`
- **Manager Override (privileged)**
  - `POST /workexec/v1/workorders/{workOrderId}/operational-context:override`
- **Work order status (for “started” determination)**
  - Workexec state machine statuses include: `DRAFT`, `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`.

### Moqui/Frontend Dependency
- Existing Work Order view route/screen must exist in `durion-moqui-frontend` (exact path/URL unknown from inputs).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From any work order list/search screen: user opens a Work Order detail view.
- Direct navigation via deep link to a Work Order view by ID.

### Screens to create/modify
- **Modify** existing Work Order detail screen (Moqui Screen) to add an “Operational Context” section.
  - If no Work Order view exists, create one (blocking clarification: confirm route conventions).

### Navigation context
- Operational context appears on the Work Order view page and refreshes when:
  - Page loads
  - User triggers explicit refresh
  - (Optional safe default) on standard pull-to-refresh / page refresh behavior

### User workflows
#### Happy path: Mechanic views operational context
1. Mechanic opens Work Order.
2. UI loads work order details (existing).
3. UI loads operational context from Shopmgr and renders:
   - Location, Bay, Dispatch Schedule, Assigned Mechanics, Resources, Constraints, last updated metadata, version.
4. UI indicates data source: “Provided by Shop Management (Shopmgr)”.

#### Manager flow: request override (pre-start)
1. Manager opens Work Order in a pre-start status (e.g., `APPROVED` or `ASSIGNED`).
2. UI shows “Request Override” action.
3. Manager enters override fields + reason and submits.
4. UI calls override endpoint, then re-fetches operational context and displays updated version and audit note.

#### Alternate path: after work start
1. Work Order status indicates started (`WORK_IN_PROGRESS` or other in-progress sub-status).
2. UI disables inline override and indicates “Changes require manager action” (but backend reference implies overrides are possible with permission; the story input says after start require manager action—needs clarification whether *override action* remains available for managers after start).

---

## 6. Functional Behavior

### Triggers
- Screen load of Work Order view.
- Manual refresh action.
- Successful override submission.

### UI actions
- **View**: render operational context fields with placeholders for missing optional fields.
- **Override (privileged)**: open a modal/form to submit changes:
  - override fields (at minimum bay and/or schedule) + required reason.
- **Refresh**: re-fetch operational context.

### State changes (frontend)
- Loading states:
  - `loadingOperationalContext=true/false`
- Error states:
  - `operationalContextLoadError` populated on failure.
- Post-override:
  - show success toast/banner and refreshed context version.

### Service interactions (high-level)
- `GET` operational context from Shopmgr endpoint.
- `POST` override to Workexec endpoint with optimistic concurrency field if required (version).
- (If available/required) Load work order status from Workexec to determine “started” and gate actions.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Override submission requires:
  - `reason` (non-empty)
  - At least one field in `overrides` set (e.g., bayId or scheduledStartAt, etc.) **(needs confirmation)**
  - If backend requires `actorId`, frontend must provide current user ID (from session context).
  - If backend requires `version` for optimistic concurrency, include current `version` (from latest GET).

### Enable/disable rules
- **Operational Context panel**: always visible if work order viewable.
- **Override action visibility**:
  - Visible only to users with `workexec:operational_context:override` permission (frontend may not be able to determine; can show and rely on 403 handling, but better if permission is available in session—needs clarification).
- **Override action enabled/disabled based on work order status**:
  - If status is start-eligible (`APPROVED`, `ASSIGNED`): override allowed for permitted users.
  - If status is in-progress (`WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`): behavior unclear per inputs (see Open Questions).
  - If terminal (`COMPLETED`, `CANCELLED`): override disabled.

### Visibility rules
- Show “Locked at work start” indicator if context includes `lockedAtWorkStart=true` (backend reference mentions this as a field; Shopmgr response schema does not include it—needs clarification).

### Error messaging expectations
- Upstream load failure: “Operational context is temporarily unavailable. You can still view the work order.”
- 403 on override: “You do not have permission to override operational context.”
- 409 on override (version conflict): “Operational context changed since you loaded this page. Refresh and try again.”
- 400 validation: show field-level messages (reason required, invalid timestamps, etc.).
- 404: “Work order or operational context not found.”

---

## 8. Data Requirements

### Entities involved (frontend view models)
- **WorkOrder** (from Workexec backend, existing in UI)
  - `id`
  - `status` (must map to known FSM statuses)
- **Operational Context** (from Shopmgr)
  - `workOrderId` (string/uuid)
  - `version` (number)
  - `location.locationId` (string/uuid)
  - `location.locationName` (string)
  - `bay.bayId` (string/uuid, nullable)
  - `bay.bayName` (string, nullable)
  - `bay.bayType` (string, nullable)
  - `dispatchSchedule.scheduledStartAt` (ISO timestamp, nullable)
  - `dispatchSchedule.scheduledEndAt` (ISO timestamp, nullable)
  - `assignedMechanics[]`:
    - `mechanicId` (string/uuid)
    - `name` (string)
    - `role` (string enum unknown: e.g., `LEAD`)
    - `assignedAt` (ISO timestamp)
  - `assignedResources[]`:
    - `resourceId`, `resourceType`, `resourceName`
  - `constraints[]`:
    - `constraintType`, `value`
  - `metadata.lastUpdatedAt` (ISO timestamp)
  - `metadata.lastUpdatedBy` (string)

### Fields: required vs optional
- Required to display minimally (per backend BR3):
  - Location name (required if present; if missing treat as unavailable)
  - Assigned mechanics (may be empty list but should render as “None assigned”)
  - Work order status (from work order)
- Optional:
  - Bay, schedule, resources, constraints

### Read-only vs editable
- All operational context fields are **read-only** in Work Order view.
- Override form fields are editable inputs but do not directly mutate local “source of truth”; they submit a request to backend.

### Derived/calculated fields
- “Started” boolean derived from Work Order status ∈ {`WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`, `COMPLETED`} (this mapping must be confirmed; see Open Questions).
- Display-friendly schedule times derived from timestamps with shop timezone (timezone source unknown; see Open Questions).

---

## 9. Service Contracts (Frontend Perspective)

> Note: Moqui frontend may call Moqui services that proxy to backend APIs. Exact service names are not provided; below are required contracts at HTTP level plus expected Moqui service wrappers.

### Load/view calls
1. **Load Work Order (existing)**
   - Contract: existing Workexec endpoint used by current Work Order view.
   - Must provide: `status` at minimum.

2. **Load Operational Context**
   - `GET /shopmgr/v1/workorders/{workOrderId}/operational-context`
   - Success: `200` with schema per backend reference (RQ2).
   - Failures:
     - `404` not found
     - `503/504` upstream unavailable/timeout

### Create/update calls
- None (direct CRUD) in this story.

### Submit/transition calls
1. **Override Operational Context (privileged)**
   - `POST /workexec/v1/workorders/{workOrderId}/operational-context:override`
   - Request body (from backend reference RQ1):
     ```json
     {
       "overrides": {
         "bayId": "uuid",
         "scheduledStartAt": "2025-01-24T10:00:00Z"
       },
       "reason": "Emergency vehicle arrival; bay 3 required",
       "actorId": "uuid"
     }
     ```
   - Expected responses:
     - `200` or `201` (unclear) returning updated context or an acknowledgment (needs clarification).
     - `400` validation error
     - `403` forbidden
     - `409` conflict (version mismatch) — if optimistic concurrency is enforced

### Error handling expectations
- Map HTTP errors to user-facing banners/toasts + inline field errors for 400.
- Ensure errors do not leak internal details (display generic message, log detailed error).

---

## 10. State Model & Transitions

### Relevant states (Work Order status)
From workexec state machine reference:
- `DRAFT`
- `APPROVED`
- `ASSIGNED`
- `WORK_IN_PROGRESS`
- `AWAITING_PARTS`
- `AWAITING_APPROVAL`
- `READY_FOR_PICKUP`
- `COMPLETED`
- `CANCELLED`

### Role-based transitions (impact on UI)
- This story does **not** implement transitions, but uses status to gate override behavior:
  - Override allowed pre-start (at least `APPROVED`, `ASSIGNED`) for permitted users.
  - After start, behavior must follow policy (see Open Questions).

### UI behavior per state
- `DRAFT`: show context if available; overrides likely allowed for manager (unclear).
- `APPROVED`/`ASSIGNED`: show override action (permitted users).
- `WORK_IN_PROGRESS`/`AWAITING_*`: show “context locked for this work session” indicator if available; disable/enable override based on clarified rules.
- `READY_FOR_PICKUP`/`COMPLETED`/`CANCELLED`: overrides disabled; show context read-only and audit.

---

## 11. Alternate / Error Flows

### Validation failures (override)
- Missing reason → field error, prevent submit.
- No override fields set → show form error “Select at least one change to submit.”
- Invalid timestamps (end before start) → show field error (only if backend validates; frontend may also pre-validate if rules provided—currently not).

### Concurrency conflicts
- Operational context version changes between load and override:
  - Backend returns `409`; UI shows conflict message and offers “Refresh context” action; preserve entered reason/inputs if possible.

### Unauthorized access
- Viewing context: if operational context endpoint returns `403`, show “Not authorized to view operational context” and hide details (unclear if possible; likely same as work order view auth).
- Override: handle `403` as described.

### Empty states
- No assigned mechanics: render “No mechanics assigned”.
- No bay/schedule/resources/constraints: render “Not set”.

### Upstream unavailable
- If Shopmgr context load fails:
  - Render panel with “Unavailable” state and retry button.
  - Do not block rest of Work Order screen.

---

## 12. Acceptance Criteria

### Scenario 1: Display full operational context
**Given** a work order exists and Shopmgr returns operational context containing location, bay, schedule, assigned mechanics, assigned resources, and constraints  
**When** a Mechanic opens the Work Order view  
**Then** the UI displays all returned operational context fields in the Operational Context section  
**And** the UI indicates the data source is Shopmgr  
**And** the UI displays the context `version` and `lastUpdatedAt` metadata.

### Scenario 2: Display partial context (graceful degradation)
**Given** Shopmgr returns operational context with location and assignedMechanics only, and bay/schedule/resources/constraints are null/empty  
**When** a Mechanic opens the Work Order view  
**Then** the UI displays location and assigned mechanics  
**And** the UI displays “Not set” (or equivalent) for missing optional fields  
**And** the rest of the Work Order view remains usable.

### Scenario 3: Context unavailable
**Given** Shopmgr operational context request fails with a network error or 5xx  
**When** a user opens the Work Order view  
**Then** the UI shows “Operational context is temporarily unavailable” within the Operational Context section  
**And** the UI provides a retry action  
**And** the Work Order details outside this section remain visible.

### Scenario 4: Manager override allowed before work start
**Given** the current user has permission to override operational context  
**And** the work order status is `APPROVED` (or `ASSIGNED`)  
**When** the user submits an override with a non-empty reason and at least one override field  
**Then** the UI calls `POST /workexec/v1/workorders/{workOrderId}/operational-context:override` with the override payload  
**And** on success, the UI re-fetches operational context  
**And** the updated context version/values are displayed.

### Scenario 5: Override rejected due to permission
**Given** the current user does not have permission to override operational context  
**When** they attempt to submit an override  
**Then** the UI displays an authorization error message  
**And** no local context is modified.

### Scenario 6: Override rejected due to version conflict
**Given** a manager loads operational context version N  
**And** Shopmgr updates the operational context to version N+1 before the manager submits  
**When** the manager submits an override based on version N  
**Then** the UI receives a `409 Conflict` response  
**And** the UI prompts the user to refresh  
**And** after refresh, the UI shows version N+1.

### Scenario 7: Edit rules after work start
**Given** the work order status is `WORK_IN_PROGRESS` (or any in-progress state)  
**When** a user views the Operational Context section  
**Then** the UI indicates the context is locked/recorded for the work start session (if lock metadata is available)  
**And** the UI enforces the configured rule for overrides after start (disabled or manager-only per clarification).

### Scenario 8: Audit visible
**Given** operational context contains metadata including lastUpdatedAt/lastUpdatedBy and version  
**When** the Work Order view is displayed  
**Then** the UI renders these audit fields in a user-visible manner  
**And** after a successful override, the UI reflects updated metadata/version.

---

## 13. Audit & Observability

### User-visible audit data
- Display:
  - `version`
  - `metadata.lastUpdatedAt`
  - `metadata.lastUpdatedBy`
- For overrides, display confirmation message including timestamp (from response if provided) and the submitted reason (if returned/available).

### Status history
- Out of scope to display full work order status transition history; only current status used for gating.

### Traceability expectations
- Frontend must include correlation/request ID headers if the project standard supports it (unknown; safe default only if already implemented globally).
- Log client-side error events (non-PII) with workOrderId and endpoint name for debugging.

---

## 14. Non-Functional UI Requirements
- **Performance**: Operational context fetch should not block initial Work Order render; load asynchronously; target < 2s fetch under normal conditions (from backend AC5 “reasonable timeframe”).
- **Accessibility**: All actions (refresh, override modal) must be keyboard navigable and have accessible labels.
- **Responsiveness**: Operational context section must adapt to mobile/tablet layouts (stack fields).
- **i18n/timezone**:
  - Timestamps must display in shop/user locale/timezone; source of timezone must be clarified.
  - No currency in this story.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Render “Not set” / “None assigned” for missing optional context fields; qualifies as safe UI ergonomics. (Impacts: UX Summary, Error Flows, Acceptance Criteria)
- SD-UX-NONBLOCKING-ASYNC-LOAD: Load operational context asynchronously and do not block Work Order details render; qualifies as safe UI ergonomics/perf. (Impacts: UX Summary, Non-Functional UI Requirements)
- SD-ERR-STD-HTTP-MAP: Standard mapping of 400/403/404/409/5xx to banner + field errors; qualifies as safe error-handling boilerplate. (Impacts: Business Rules, Error Flows, Acceptance Criteria)

---

## 16. Open Questions

1. **Domain contract mismatch (blocking):** The frontend issue text labels domain as “Shop Management/user”, but the backend reference is `domain:workexec` with Shopmgr as SoR. Confirm this frontend story should be owned/labeled `domain:workexec` (current proposal) and implemented in workexec UI screens.
2. **Override-after-start rule (blocking):** The issue says “Updates allowed until work starts; after start require manager action.” Does that mean:
   - (A) no overrides allowed after start for anyone, or
   - (B) overrides allowed after start but **only** for managers (likely), or
   - (C) overrides allowed but must create a new future context version without altering locked-at-start version?
3. **Which Work Order statuses count as “work started” in the UI gate?** Should “started” be strictly `WORK_IN_PROGRESS` and in-progress sub-statuses only, or also include `READY_FOR_PICKUP`?
4. **Where does the frontend get current user permissions and userId (actorId)?** Is `actorId` required in override payload, and if so should it be session user UUID/ID? How is it accessed in this Moqui frontend project?
5. **Override response shape and status code:** Does the override endpoint return updated operational context (preferred) or an acknowledgment requiring a subsequent GET? What HTTP status should be treated as success (200 vs 201)?
6. **Optimistic concurrency for overrides:** Must the frontend include a `version` in the override request? If yes, what is the exact field name/path?
7. **“Team” definition:** The mechanic story says “team”. Is “team” represented by `assignedMechanics[]` only, or is there a separate team entity/identifier to display?
8. **Routing/screen location:** What is the canonical Moqui screen path/route for viewing a work order in `durion-moqui-frontend` where this section should be added?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] CrossDomain: Workexec Displays Operational Context in Workorder View  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/123  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] CrossDomain: Workexec Displays Operational Context in Workorder View

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Mechanic**, I want workexec to display my assigned location/bay and team on the workorder so that I know where to work and who I’m working with.

## Details
- Workexec shows operational context fields.
- Updates allowed until work starts; after start require manager action.

## Acceptance Criteria
- Workorder shows location/resource/team.
- Update rules enforced.
- Audit visible.

## Integrations
- Shopmgr provides context; workexec renders it and emits statuses back.

## Data / Entities
- WorkorderOperationalContext (workexec domain)

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


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