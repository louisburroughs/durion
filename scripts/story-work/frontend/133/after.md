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
[FRONTEND] [STORY] Dispatch: Override Scheduling Conflict with Manager Permission (Reason + Audit Flag)

### Primary Persona
Shop Manager (or other authorized dispatcher role with `shopmgr.appointment.override`)

### Business Value
Enable urgent/exception appointments to be scheduled despite system-detected conflicts while preserving auditability (who/when/why + what conflict was overridden) and ensuring downstream work execution receives an “override/expedite” context flag.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Shop Manager  
- **I want** to override a scheduling conflict by providing a mandatory reason  
- **So that** urgent jobs can proceed while keeping a permanent audit trail and signaling downstream systems that the schedule was forced.

### In-scope
- Frontend affordance to override a detected scheduling conflict **only** when the user has permission `shopmgr.appointment.override`.
- Mandatory capture of override reason (non-empty).
- Persisting the schedule action with an override flag/context via backend call(s).
- Displaying “conflict overridden” indicator on the affected appointment/work order after success.
- Basic error handling for permission/validation/conflict/concurrency.

### Out-of-scope
- Implementing conflict detection itself (assumed backend provides conflict detection result).
- Defining scheduling resource models (tech/bay calendars) beyond displaying backend-provided conflict details.
- Creating/defining permission model beyond checking/handling `shopmgr.appointment.override`.
- Notification delivery to other parties.
- Any new workflow states beyond what backend exposes.

---

## 3. Actors & Stakeholders
- **Shop Manager (primary)**: may override conflicts with reason.
- **Dispatcher/Service Advisor (secondary)**: may see conflicts but cannot override without permission.
- **Auditor/Compliance**: needs immutable override record accessible via UI (at least indicator and reason visibility per policy).
- **Workexec downstream consumer**: needs `isConflictOverride` and override context to flow.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in POS UI.
- User is creating or editing an appointment (or scheduling a work order) and initiates “Save/Confirm Schedule”.
- Backend returns a scheduling conflict response when applicable.

### Dependencies
- Backend endpoints to:
  - Detect conflicts and return conflict details in a structured payload **or** return a typed error indicating a conflict.
  - Perform the “override schedule” action and create an immutable OverrideRecord.
  - Provide permission/authorization enforcement for `shopmgr.appointment.override`.

**Note:** Backend reference exists (durion-positivity-backend issue #69) but the exact API path/schemas for the scheduling/dispatch module are not provided in the frontend issue text—this blocks final, buildable Moqui wiring without confirmation.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Appointment create/edit screen “Save/Schedule” action when a conflict is detected.
- Potentially from a “Dispatch Calendar” view when placing/rescheduling an appointment into a conflicting slot.

### Screens to create/modify (Moqui)
1. **Modify existing dispatch/scheduling screen** (exact path TBD by repo conventions):
   - Add conflict-handling section/modal.
   - Add “Override Conflict” action gated by permission.
2. **New dialog screen or modal form**:
   - `Override Conflict` modal with required `reason` field and read-only conflict details.

### Navigation context
- User remains on scheduling flow; override is a continuation of the original schedule action.
- After successful override, user returns to the appointment/work order view in “scheduled” state with override badge/flag visible.

### User workflows
#### Happy path
1. User attempts to schedule → backend indicates conflict + provides details.
2. UI shows conflict details; if user has permission, show “Override conflict”.
3. User clicks override → modal prompts for required reason.
4. Submit override → backend schedules and returns updated appointment/work order + override record id/flag.
5. UI displays success and an “Overridden” indicator.

#### Alternate paths
- User lacks permission → conflict shown, override option hidden/disabled; user must adjust schedule.
- User cancels override modal → no schedule saved; remains in conflict state.
- Backend rejects override (403/validation) → show error; keep user in modal with reason preserved.

---

## 6. Functional Behavior

### Triggers
- Triggered when schedule save/reschedule attempt results in a **conflict response**.

### UI actions
- Display conflict summary + list of conflicts (if multiple).
- If authorized, allow “Override conflict” action.
- Collect override reason (required).
- Submit override request.

### State changes (frontend)
- Local UI state: `conflictDetected=true`, `overrideModalOpen=true`, `overrideReason=<text>`.
- After success: `conflictDetected=false`, show `isConflictOverride=true` on the appointment/work order view model.

### Service interactions (frontend ↔ backend)
- Call scheduling “save” endpoint normally.
- On conflict response: call override endpoint (or retry schedule with override payload) with:
  - appointment/workOrder identifier
  - reason
  - conflict details reference (if backend requires)
  - user context (implicit via auth token; do not send userId unless required by backend contract)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Override reason required**: non-empty, non-whitespace string.
  - UI must block submit until valid.
  - On backend validation failure, show inline error and keep modal open.

### Enable/disable rules
- “Override conflict” button is only enabled when:
  - conflict is present, and
  - user has `shopmgr.appointment.override`, and
  - override modal reason field is valid (for submit).

### Visibility rules
- If user lacks permission, override action is not shown (or shown disabled with “requires permission” tooltip) — choose one consistent pattern per project conventions (needs confirmation).

### Error messaging expectations
- 403: “You don’t have permission to override scheduling conflicts.”
- 409 (conflict changed/concurrency): “The schedule changed; refresh and try again.”
- 400 validation: “Reason is required to override a conflict.”
- 5xx: “Could not override conflict due to a system error. Try again.”

---

## 8. Data Requirements

### Entities involved (frontend view models)
- **Appointment / WorkOrder (scheduled item)**:
  - `id` (string/number)
  - `isConflictOverride` (boolean, read-only from backend)
  - optional: `overrideRecordId` (string/number, read-only)
  - optional: `overrideContext` (object, read-only; contains reason/type)
- **OverrideRecord** (immutable, primarily for audit display):
  - `overrideId`
  - `appointmentId` (or scheduled entity id)
  - `overriddenByUserId` (read-only)
  - `overrideTimestamp` (read-only)
  - `overrideReason` (read-only after creation; input when creating)
  - `conflictDetails` (read-only; structured)
- **Conflict details** (from backend conflict response):
  - `type` (enum/string)
  - `resourceId` (string)
  - `conflictingAppointmentId` (string) optional
  - Additional fields as provided

### Fields (type, required, defaults)
- `overrideReason: string` (required; no default)
- `isConflictOverride: boolean` (default false; becomes true on successful override)

### Read-only vs editable
- Editable: `overrideReason` only during override action.
- Read-only: all audit fields and conflict details.

### Derived/calculated fields
- `conflictFlagLabel` derived from `isConflictOverride` (e.g., “Overridden”).

---

## 9. Service Contracts (Frontend Perspective)

> **Blocking:** exact endpoints/schemas for dispatch scheduling in this Moqui frontend are not specified in provided inputs. Below is the minimum contract the frontend needs; must be mapped to actual Moqui services/transitions.

### Load/view calls
- `GET appointment/workorder` details (existing):
  - Must include `isConflictOverride` and (if allowed) override metadata.

### Create/update calls
- **Schedule attempt** (existing):
  - `POST/PUT /api/.../appointments/{id}/schedule` (placeholder)
  - On conflict, returns either:
    - HTTP `409` with body containing `conflicts[]`, or
    - HTTP `200` with `conflictDetected=true` and details (less likely)

### Submit/transition calls
- **Override conflict** (new or existing variant):
  - Option A: `POST /api/appointments/{id}/override-conflict`
    - body: `{ reason: string, conflictDetails?: object }`
    - returns updated appointment + `overrideRecord`
  - Option B: retry schedule with `override=true` and `reason`
    - body: `{ ..., override: { reason } }`

### Error handling expectations
- 403 for missing permission (`shopmgr.appointment.override`).
- 400 for missing/invalid reason.
- 409 if conflict details changed or resource state moved (optimistic concurrency).

---

## 10. State Model & Transitions

### Allowed states (workexec-aligned, but scheduling-specific)
This story does **not** introduce new work order FSM states. It introduces a *flag/context* on a scheduled entity indicating it was scheduled via override.

### Role-based transitions
- Only users with `shopmgr.appointment.override` can execute the override action.

### UI behavior per state
- Conflict present: show conflict panel; possibly block scheduling unless override or schedule adjustment.
- After override: show “Overridden” indicator and allow normal navigation.

---

## 11. Alternate / Error Flows

1. **Validation failure (empty reason)**
   - UI blocks submit; backend 400 handled with inline error; keep modal open.

2. **Unauthorized (no permission)**
   - If backend returns 403, show permission error and do not schedule.
   - Ensure UI does not offer override action when permissions are absent (defense-in-depth).

3. **Conflict no longer applicable / changed**
   - Backend returns 409; UI prompts to refresh conflict details and reattempt.

4. **Network/server error**
   - Show non-technical message; allow retry.

5. **Empty state**
   - If backend returns conflicts array empty but still indicates conflict, show generic conflict message and log diagnostic details.

---

## 12. Acceptance Criteria

### Scenario 1: Authorized manager overrides conflict successfully
**Given** a user is logged in with permission `shopmgr.appointment.override`  
**And** the user attempts to schedule an appointment that results in a scheduling conflict  
**When** the UI presents the conflict details and the user selects “Override conflict”  
**And** the user enters a non-empty reason “Emergency customer vehicle”  
**And** the user submits the override  
**Then** the appointment/work order is saved/scheduled successfully  
**And** the UI shows the appointment flagged as overridden (`isConflictOverride=true`)  
**And** an override record is created (backend-confirmed via response fields such as `overrideRecordId` or `overrideRecord`) containing the reason and conflict details.

### Scenario 2: User without permission cannot override
**Given** a user is logged in without permission `shopmgr.appointment.override`  
**And** the user attempts to schedule an appointment that results in a scheduling conflict  
**When** the conflict is displayed  
**Then** the UI does not present an enabled “Override conflict” action  
**And** the user cannot complete scheduling in the conflicting slot.

### Scenario 3: Override blocked when reason missing
**Given** a user is logged in with permission `shopmgr.appointment.override`  
**And** a scheduling conflict is detected  
**When** the user selects “Override conflict”  
**And** leaves the reason empty or whitespace  
**Then** the UI prevents submission and shows “Reason is required”  
**And** no override request is sent (or if sent, backend 400 is surfaced and appointment remains unscheduled).

### Scenario 4: Backend denies override (403) even if UI offered it (defense-in-depth)
**Given** a user attempts an override  
**And** the backend responds with HTTP 403 Forbidden  
**When** the UI receives the response  
**Then** the UI shows a permission error message  
**And** does not mark the appointment as overridden  
**And** keeps the user in the conflict resolution flow.

### Scenario 5: Downstream flag visibility after reload
**Given** an appointment was scheduled via conflict override  
**When** the user reloads the appointment/work order details screen  
**Then** the UI displays `isConflictOverride=true`  
**And** displays the override indicator consistently (e.g., “Conflict overridden”).

---

## 13. Audit & Observability

### User-visible audit data
- Display (if provided by backend and allowed by policy):
  - override timestamp
  - overridden by (name/id)
  - reason
  - conflict type/resource summary

### Status history
- No new lifecycle states; treat override as an auditable event attached to the appointment/work order.

### Traceability expectations
- UI should surface an override identifier if returned (e.g., `overrideRecordId`) for support/audit lookup.
- Ensure correlation/request IDs (if present in headers) are logged client-side per project conventions.

---

## 14. Non-Functional UI Requirements
- **Performance:** conflict modal opens immediately; avoid extra calls until user chooses override.
- **Accessibility:** modal is keyboard-navigable; reason field has label and error text; focus management on open/close.
- **Responsiveness:** works on tablet resolutions used in shop.
- **i18n:** reason validation/error strings use existing translation mechanism (if present); otherwise keep strings centralized.
- **Security:** do not expose internal conflict resource identifiers unless backend already exposes them; handle 403 safely.

---

## 15. Applied Safe Defaults
- none

---

## 16. Open Questions

1. **What are the actual Moqui screen routes and entity names for “Dispatch/Scheduling” in `durion-moqui-frontend`?** (Need target screen XML paths and navigation context to implement.)
2. **What is the authoritative backend API contract for conflict detection and override?**  
   - Is conflict reported via HTTP 409 with a payload, or via a normal response with a conflict flag?  
   - Is override done via a dedicated endpoint or by retrying schedule with override payload?
3. **What identifier is being scheduled/overridden in this frontend: Appointment ID, WorkOrder ID, or a separate Dispatch entity?** (Story text uses “appointment/work order” interchangeably.)
4. **Should users without permission see the override option disabled (with explanation) or hidden entirely?** (Affects UX and test expectations.)
5. **Should the UI display the full conflict details and override reason after success, or only a badge/flag?** (Audit visibility policy.)
6. **Does override require capturing any additional “impacts” fields beyond a free-text reason (e.g., impacted resource, urgency code), or is reason + conflictDetails sufficient?** (Story mentions “records rationale and impacts” but doesn’t define “impacts”.)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Dispatch: Override Conflict with Manager Permission  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/133  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Dispatch: Override Conflict with Manager Permission

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Shop Manager**, I want to override a scheduling conflict with a reason so that urgent jobs can proceed with auditability.

## Details
- Requires shopmgr.appointment.override.
- Records rationale and impacts.

## Acceptance Criteria
- Permission required.
- Reason required.
- Conflict flagged.

## Integrations
- Workexec receives override/expedite flag via context.

## Data / Entities
- OverrideRecord, PermissionCheck

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