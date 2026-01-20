STOP: Clarification required before finalization

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

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Workexec: Submit Job Time (TimeEntry) as Labor Performed (Idempotent)

### Primary Persona
Mechanic / Technician (primary), with system-integration behavior initiated from the POS UI context.

### Business Value
Ensure technician labor is recorded in `workexec` accurately and without duplication, with clear, actionable failures when the Work Order state prevents labor posting—supporting correct execution tracking and downstream billing readiness.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Mechanic/Technician  
- **I want** my finalized job time (TimeEntry) submitted to Work Execution (`workexec`) as `LaborPerformed` in an idempotent way  
- **So that** labor performed lines are created/updated accurately without duplication, and I receive stable error feedback when submission is disallowed.

### In Scope
- Frontend UI flow to **initiate** (or re-initiate) submission of a TimeEntry to workexec.
- Frontend display of **submission status**, and **stable error codes**/messages returned by the integration.
- Idempotency behavior at the UI level: repeated “Submit” actions do not create duplicates; UI handles “replayed” success.
- Audit/traceability display elements (workOrder reference; correlation/idempotency key surfaced where appropriate).
- Moqui screens/forms/transitions wiring to a Moqui service that performs the POST to workexec.

### Out of Scope
- Authoring or editing TimeEntry time capture itself (People/Job-Time domain).
- Implementing the backend `workexec` endpoint (already referenced).
- Defining new Work Order state machine states (must follow workexec backend).
- Payroll/billing calculation rules based on labor time (explicitly not assumed).

---

## 3. Actors & Stakeholders
- **Mechanic/Technician (UI user):** initiates submit/retry and needs clear status.
- **Service Advisor (stakeholder):** needs confidence labor is recorded; may help resolve conflicts.
- **Workexec system (`pos-workexec`):** system of record for Work Orders and Labor Performed.
- **People/Job-Time system (upstream):** source of TimeEntry, provides `timeEntryId`, `workOrderId`, `technicianId`, time quantities.

---

## 4. Preconditions & Dependencies
- A `TimeEntry` exists and is eligible for submission (backend reference says “FINALIZED” is the trigger).
- Frontend can access:
  - `timeEntryId`
  - associated `workOrderId`
  - `technicianId`
  - `performedAt` timestamp
  - labor quantity in hours (decimal)
- Workexec endpoint is reachable:
  - `POST /api/workexec/labor-performed`
- Workexec supports idempotency:
  - Request header `Idempotency-Key: <timeEntryId>`
  - Returns `201 Created` on first success
  - Returns `200 OK` with header `Idempotency-Replayed: true` on replay success
  - Returns `409 Conflict` for terminal business conflict (e.g., work order state blocks)

**Dependency note:** This frontend story depends on an existing/available UI context where a technician can view a TimeEntry and submit it, or an integration queue UI. That context is not defined in the provided inputs (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
At least one entry point must exist (to be confirmed):
1. From a **Time Entry detail** screen: “Submit to Work Order” action.
2. From a **My Time Entries** list: bulk or per-row “Submit” / “Retry”.
3. From an **Integration/Outbox** admin screen: view failures and retry (if such UI exists).

### Screens to create/modify
- **Create/Modify**: `TimeEntryDetail` (or equivalent) screen to add:
  - submission status panel
  - submit/retry button
  - last attempt result (success/replayed/error code)
- **Optional** (if project has integration queue UI): `IntegrationSubmissionList` for failed submissions.

> Exact screen names/paths must match repo conventions; not provided here (blocking clarification).

### Navigation context
- Standard Moqui screen with transitions:
  - `transition submitLaborPerformed`
  - on success, refresh same screen with updated status
  - on error, show message + persist/display error details

### User workflows
**Happy path**
1. Mechanic opens a finalized TimeEntry.
2. Clicks “Submit to Work Order”.
3. UI shows in-progress state.
4. On `201` or `200 replay`, UI shows “Submitted” with workexec reference (if returned).

**Alternate paths**
- Mechanic clicks submit twice → second call returns replay success; UI shows “Already submitted” (still success).
- Work order state blocks submission → UI shows non-retriable failure with stable error code.
- Network/5xx transient errors → UI indicates “Temporary failure, try again” (UI-level retry only; no assumptions about background retry).

---

## 6. Functional Behavior

### Triggers
- User action: click “Submit to Workexec” / “Retry submission”.

### UI actions
- Disable submit button while request is in-flight.
- On completion:
  - Success: show success banner + update status.
  - Replay success: show success banner noting already submitted (no duplication).
  - Failure: show error banner with stable error code + guidance.

### State changes (frontend-local)
- Store/display a local submission state for the TimeEntry view model:
  - `NOT_SUBMITTED` | `SUBMITTING` | `SUBMITTED` | `FAILED`
- Persisting these statuses requires a frontend-accessible store/entity or backend endpoint; not defined in inputs (blocking clarification). If not persisted, at minimum show last response in-session.

### Service interactions
- Moqui service call from screen transition invokes HTTP POST to:
  - `POST /api/workexec/labor-performed`
- Must include headers:
  - `Idempotency-Key = timeEntryId`
  - `Content-Type = application/json`
  - `X-Correlation-Id` if available (generate if missing)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
Before allowing submit, UI must validate presence of required fields:
- `timeEntryId` present
- `workOrderId` present
- `technicianId` present
- `performedAt` present and ISO-8601 serializable
- `labor.quantity` > 0
- `labor.unit` fixed to `HOURS`

If missing, block submission and show inline error: “Cannot submit: missing required time entry fields: …”

### Enable/disable rules
- Submit button enabled only when:
  - TimeEntry is in eligible state (expected `FINALIZED`) **(needs confirmation from frontend-accessible field)**.
- Submit button disabled when:
  - request in-flight
  - TimeEntry is clearly not eligible (not finalized)

### Visibility rules
- Show “Submission Result” panel if any submission attempt has been made (or if backend provides submission status).

### Error messaging expectations
- For `409` workexec conflict: show stable error code (e.g., `WORKEXEC_CONFLICT_WORKORDER_STATE`) and guidance: “Work order is not accepting labor in its current state.”
- For `401/403`: show “Not authorized to submit labor performed.”
- For `404`: show “Work order not found in workexec.”
- For `422/400`: show validation failure details (field-level if provided).
- For `5xx/timeout`: show “Temporary failure; please retry.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `TimeEntry` (source)
- `LaborPerformed` (workexec target; likely not stored locally in POS frontend)
- Optional: local “submission record” entity (if system tracks attempts)

### Fields
**TimeEntry (read)**
- `timeEntryId` (string/uuid, required)
- `workOrderId` (string/uuid, required)
- `technicianId` (string/uuid, required)
- `performedAt` (datetime, required)
- `quantityHours` (decimal, required, > 0)
- `status` (enum; must indicate finalized/eligible)

**Submission payload (derived)**
- `labor.unit` = `"HOURS"` (constant)
- `source.system` = `"people"` (constant per backend reference)
- `source.sourceReferenceId` = `timeEntryId` (derived)

### Read-only vs editable
- All fields used for submission are read-only on this screen (submission action only).
- If any are editable today, this story does not change edit behavior.

### Derived/calculated fields
- None beyond payload mapping.

---

## 9. Service Contracts (Frontend Perspective)

### Load/view calls
- Load TimeEntry details (existing service/screen load). **Contract not provided**.
- Optional: load current submission status (if tracked). **Contract not provided**.

### Create/update calls (submission)
**Service:** `POST /api/workexec/labor-performed`  
**Headers:**
- `Idempotency-Key: <timeEntryId>` (required)
- `Content-Type: application/json`
- `X-Correlation-Id: <uuid>` (optional but recommended)

**Request body:**
```json
{
  "workOrderId": "uuid",
  "technicianId": "uuid",
  "performedAt": "2026-01-15T17:30:00Z",
  "labor": { "quantity": 1.50, "unit": "HOURS" },
  "source": { "system": "people", "sourceReferenceId": "uuid" }
}
```

**Success responses:**
- `201 Created` (new)
- `200 OK` with `Idempotency-Replayed: true` (replay, treat as success)

**Error handling expectations:**
- `409 Conflict`: terminal, show stable workexec error code; do not auto-retry in UI.
- `400/422`: show validation message(s).
- `401/403`: show authorization error.
- `404`: show not found.
- `408/429/5xx`: show transient error + allow user retry.

> Stable error envelope fields are not specified in the provided frontend inputs; must be clarified (see Open Questions).

---

## 10. State Model & Transitions

### Allowed states (relevant subset)
Work Order states that may block submission are specified by backend reference:
- `CANCELLED`, `CLOSED`, `INVOICED`, `VOIDED`, `ARCHIVED`
- `ON_HOLD`, `LOCKED_FOR_REVIEW`
- `COMPLETED` (unless reopened)
Allowed again when reopened/active (e.g., `REOPENED` or other active states).

### Role-based transitions
- Only authenticated users can submit.
- Mechanic role expected to be allowed, but exact permission/role mapping is not provided (blocking clarification; do not assume).

### UI behavior per state
- If workexec returns conflict due to blocked state:
  - UI marks submission as `FAILED (terminal)` and disables further retries unless user explicitly retries (still would fail) and/or state changes.
- If replay success:
  - UI shows `SUBMITTED` and disables submit (or changes button to “Re-submit (idempotent)” based on product decision—needs clarification).

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Missing required ids or quantity <= 0:
  - block submit, show inline validation.

### Workexec rejects due to work order state (409)
- Show code + message
- Provide link/navigation to Work Order detail (if exists) for state review.

### Concurrency conflicts / duplicate clicks
- Double-submit results in replay; treat as success.
- Ensure UI prevents multiple simultaneous posts by disabling button during in-flight.

### Unauthorized access
- If `401/403`, display and do not retry automatically.

### Empty states
- If TimeEntry cannot be loaded, show standard “Not found” state.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Submit labor performed successfully (first time)
**Given** I am viewing a TimeEntry with status `FINALIZED` and it has `timeEntryId`, `workOrderId`, `technicianId`, `performedAt`, and a positive hours quantity  
**When** I click “Submit to Workexec”  
**Then** the system sends `POST /api/workexec/labor-performed` with header `Idempotency-Key = timeEntryId`  
**And** the request body maps the time entry to the labor-performed schema (unit `HOURS`, source.system `people`, sourceReferenceId = timeEntryId)  
**And** when workexec returns `201 Created`, the UI shows status `SUBMITTED`.

### Scenario 2: Idempotent replay is treated as success
**Given** a TimeEntry has already been submitted previously using `Idempotency-Key = timeEntryId`  
**When** I click “Submit to Workexec” again  
**Then** if workexec returns `200 OK` with header `Idempotency-Replayed: true`  
**Then** the UI displays success and does not indicate a duplicate was created  
**And** the UI state remains `SUBMITTED`.

### Scenario 3: Work order state blocks submission (terminal conflict)
**Given** I attempt to submit labor for a TimeEntry whose Work Order is in a blocking state in workexec  
**When** workexec responds `409 Conflict` with a stable error code indicating work order state conflict  
**Then** the UI shows an actionable error message including the stable error code  
**And** the UI marks the submission as failed (terminal) for that attempt  
**And** the UI does not automatically retry.

### Scenario 4: Transient failure allows manual retry
**Given** I attempt to submit labor for a valid TimeEntry  
**When** workexec responds with `503 Service Unavailable` (or the request times out)  
**Then** the UI shows a transient error message  
**And** provides an enabled “Retry” action once the request is no longer in-flight.

### Scenario 5: Client-side validation prevents invalid submission
**Given** I am viewing a TimeEntry missing `workOrderId` (or hours quantity is `0`)  
**When** I click “Submit to Workexec”  
**Then** the UI prevents the request from being sent  
**And** displays a validation error indicating which required field(s) are missing/invalid.

---

## 13. Audit & Observability
- UI should display (or make available in logs):
  - `timeEntryId` (idempotency key)
  - `workOrderId`
  - correlation id used (`X-Correlation-Id`) if generated
- Each submission attempt should be traceable in Moqui logs with:
  - correlation id
  - HTTP status code
  - workexec error code (if present)
- User-visible: show “Last attempted at” timestamp (requires persistence; otherwise omit).

---

## 14. Non-Functional UI Requirements
- **Performance:** Submission action should return control within a reasonable time; show spinner/loading state while waiting.
- **Accessibility:** Buttons and status messages must be screen-reader friendly; errors announced (ARIA live region).
- **Responsiveness:** Works on tablet-sized UI (shop floor).
- **i18n:** Error messages should be localizable; do not hardcode long prose in service layer.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standard empty/not-found state messaging for missing TimeEntry data; qualifies as safe because it does not affect business policy, only presentation. Impacted sections: UX Summary, Alternate/Error Flows.
- SD-UX-INFLIGHT-DISABLE: Disable submit button while request is in-flight to prevent accidental double posts; qualifies as safe because idempotency already exists and this only improves ergonomics. Impacted sections: Functional Behavior, Alternate/Error Flows.
- SD-ERR-TRANSIENT-RETRY-MANUAL: Treat 408/429/5xx/timeouts as transient and allow *manual* retry from UI; qualifies as safe because it does not introduce automated domain side effects and mirrors common HTTP semantics. Impacted sections: Service Contracts, Alternate/Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. **Where in the frontend does this action live?** What is the canonical screen route/name for TimeEntry list/detail in this Moqui frontend repo (so we can specify the exact screen(s) to modify)?
2. **What is the frontend-accessible TimeEntry contract?** Which endpoint/service loads TimeEntry details and what are the exact field names for: `id`, `status`, `workOrderId`, `technicianId`, `performedAt`, `hours quantity`?
3. **Should submission attempt status be persisted?** If yes, where (Moqui entity in this frontend app vs upstream People service), and what fields are required (lastStatusCode, lastErrorCode, lastAttemptAt, laborPerformedId)?
4. **What is the exact error envelope from workexec?** For non-2xx responses, what JSON fields contain the stable `workexecErrorCode` and message (e.g., `{code, message, details}`)? The ACs require “stable error codes,” but the response schema is unspecified in the provided frontend inputs.
5. **Authorization/permission expectation:** Which roles may invoke submission from UI (Mechanic only, or also Service Advisor/Admin)? Any feature flag?
6. **Post-success UX:** After `SUBMITTED`, should the button be hidden/disabled, or remain available as “Re-submit (idempotent)”?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Integration: Submit Job Time to workexec as Labor Performed (Idempotent)  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/145  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Integration: Submit Job Time to workexec as Labor Performed (Idempotent)

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative
As a **Mechanic**, I want **to submit my job time to workexec** so that **labor performed lines are created/updated accurately**.

## Details
- Idempotent posting using timeEntryId as reference.
- Reject with actionable errors when workorder state disallows.

## Acceptance Criteria
- Posting creates or updates labor-performed without duplication.
- Failures provide stable error codes.
- Audit includes workorder reference.

## Integration Points (workexec)
- Outbound: JobTimePosted event and/or API call.

## Data / Entities
- LaborPerformed (workexec)
- TimeEntry (job)

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: People Management


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