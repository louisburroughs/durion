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
[FRONTEND] [STORY] Timekeeping: Approve/Reject Submitted Time (Day or Work Order) with Adjustments + Exception Resolution

### Primary Persona
Shop Manager

### Business Value
Locks approved labor time for payroll accuracy, prevents retroactive changes without audit, and ensures exceptions are explicitly handled before approval.

---

## 2. Story Intent

### As a / I want / So that
**As a** Shop Manager,  
**I want** to review submitted time entries by day and/or work order, resolve/waive any exceptions, optionally create audited adjustments, and approve or reject submissions,  
**so that** time becomes locked and finalized for payroll and downstream HR integration.

### In Scope
- A manager-facing Moqui screen flow to:
  - View time entries filtered by **work date** and/or **work order**
  - See entry status and exception badges
  - Approve one or more `PENDING_APPROVAL` time entries (lock after approval)
  - Reject one or more `PENDING_APPROVAL` time entries with a required reason
  - Create a **separate adjustment record** for an entry (delta/proposed times), without editing the original entry
  - View and act on exceptions (acknowledge/resolve/waive) based on severity
- Read-only display of approval/decision audit metadata (who/when/reason)

### Out of Scope
- Creating/submitting time entries by employees (separate workflow)
- Payroll/HR system UI or monitoring (integration is backend/event-driven)
- Defining pay rates, wage calculations, tax/benefits
- Configuring exception thresholds and approval methods (policy/config domain)

---

## 3. Actors & Stakeholders
- **Shop Manager (primary):** approves/rejects, creates adjustments, resolves/waives exceptions.
- **Technician / Employee (indirect):** submits time; may correct and resubmit after rejection.
- **HR/Payroll system (downstream):** consumes approval outputs/events (no synchronous dependency for UI).
- **Audit/Compliance (stakeholder):** requires immutable history of approvals, rejections, adjustments, and exception handling.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS frontend.
- User has permission to approve/reject time entries (exact permission codes TBD; see Open Questions).
- There exist time entries in `PENDING_APPROVAL` for the selected day and/or work order.

### Dependencies (Backend/API)
This frontend story depends on the backend providing endpoints matching the referenced backend story (#66), plus exception and adjustment operations (some are only described conceptually in backend story text and need confirmation):
- Time entries list/view with status and decision metadata
- Approve/reject operations with validation and proper error codes
- Time exceptions list and mutation operations (acknowledge/resolve/waive)
- Time entry adjustment create/list + approval workflow (or at minimum create “proposed” adjustment)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- POS main navigation: **Timekeeping → Approvals**
- Deep links:
  - `/timekeeping/approvals` (queue by date default)
  - `/timekeeping/approvals?workDate=YYYY-MM-DD`
  - `/timekeeping/approvals?workOrderId=<id>`

### Screens to create/modify (Moqui)
Create new screen tree (names can be adjusted to repo conventions):
- **Screen:** `apps/pos/screen/Timekeeping/Approvals.xml`
  - Subscreens:
    - `Queue.xml` (list + filters + batch actions)
    - `EntryDetail.xml` (single entry view + exceptions + adjustments)
    - `AdjustmentDialog.xml` or inline dialog within `EntryDetail`
    - `ExceptionDetail.xml` (optional, can be dialog/panel)

### Navigation context
- Breadcrumb: `Timekeeping > Approvals`
- Filter chips show active scope: `Work Date` and/or `Work Order`

### User workflows

#### Happy path A — Approve entries for a day
1. Manager opens **Timekeeping > Approvals**
2. Selects a work date
3. Reviews list; entries with exceptions show badges
4. Resolves/waives any **BLOCKING** exceptions
5. Selects entries and clicks **Approve**
6. UI shows success and updates status to `APPROVED` + read-only lock indicator

#### Happy path B — Reject entries with reason
1. Manager selects one or more `PENDING_APPROVAL` entries
2. Clicks **Reject**
3. Modal requires a reason
4. Submits; entries become `REJECTED`, unlock for submitter (informational note)

#### Happy path C — Create adjustment (delta/proposed times)
1. From entry detail (or row action) manager chooses **Create Adjustment**
2. Enters reason code + either proposed start/end OR minutes delta (contract TBD)
3. Submits; adjustment appears in adjustments list as `PROPOSED` (or equivalent)
4. Manager proceeds to resolve exceptions then approve/reject time entry as allowed by backend rules

#### Alternate path — Approve by work order
- Filter by `workOrderId`, review all pending entries, approve/reject as above.

---

## 6. Functional Behavior

### Triggers
- Screen load: fetch approval queue for default date (today) or selected filters.
- Filter change: re-fetch queue.
- Row click: open entry detail.
- Approve/Reject button: perform batch action.
- Create Adjustment: submit adjustment request.
- Exception actions: acknowledge/waive/resolve (depending on capability).

### UI actions
- Select rows (multi-select) limited to eligible statuses (must be `PENDING_APPROVAL`).
- Approve: confirmation dialog (shows count and warns about locking).
- Reject: modal with required `rejectionReason`.
- Entry detail:
  - Read-only times, work order, employee, status, submittedAt
  - Exceptions section with severity and required actions
  - Adjustments section listing existing adjustments + create new

### State changes (frontend-visible)
- `PENDING_APPROVAL` → `APPROVED` on success (entry becomes read-only; disable actions)
- `PENDING_APPROVAL` → `REJECTED` on success (display rejection reason; allow no further manager action except view)
- Exception status changes: `OPEN` → `ACKNOWLEDGED` / `RESOLVED` / `WAIVED`
- Adjustment status change is **not performed by this story** unless backend supports approving adjustments in same UI (TBD)

### Service interactions (Moqui)
- Use Moqui `service-call` from transitions/actions for:
  - Loading queue data
  - Performing approve/reject
  - Creating adjustments
  - Updating exception statuses

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Reject requires non-empty reason (client-side required + server-side enforced).
- Approve only allowed when:
  - Entry status is `PENDING_APPROVAL`
  - All `BLOCKING` exceptions are not `OPEN` (must be `RESOLVED` or `WAIVED`)
- Adjustments:
  - Must not edit original time entry directly.
  - Must require `reasonCode`. (Reason code set is TBD; do not invent values beyond examples.)
  - Must require either:
    - proposed start/end timestamps, **or**
    - minutes delta
  - If both provided, backend should reject; UI should prevent selecting both (pending contract confirmation).

### Enable/disable rules
- Approve button enabled only when selection contains at least one entry and all selected are `PENDING_APPROVAL`.
- Reject button enabled only when selection contains at least one entry and all selected are `PENDING_APPROVAL`.
- If any selected entry has `BLOCKING` exceptions still `OPEN`, Approve is disabled and UI explains why.
- For `APPROVED` entries: all edit/decision controls hidden/disabled; show “Locked” indicator.

### Visibility rules
- Exceptions badge visible when entry has 1+ open exceptions (or any exceptions; TBD).
- Exception action buttons depend on severity and backend-permitted transitions:
  - `WARNING`: allow `ACKNOWLEDGED`
  - `BLOCKING`: require `RESOLVED` or `WAIVED` prior to approval

### Error messaging expectations
- 403: “You do not have permission to approve/reject time entries.”
- 409: “This entry was updated by someone else. Refresh and try again.”
- 400: show field-level error (e.g., missing rejection reason).
- 422 (if used): show validation summary for exceptions not resolved.

---

## 8. Data Requirements

### Entities involved (frontend view models)
From backend reference + resolved questions section:
- `TimeEntry`
- `TimeEntryAdjustment`
- `TimeException` (exception flags)

### Fields (type, required, defaults)

#### TimeEntry (read)
- `timeEntryId` (string/uuid) required
- `personId`/`employeeId` (string/uuid) required
- `workOrderId` (string/uuid or string) optional? required when tied to WO
- `workDate` (date) required (needed for day view) **TBD**
- `startAt`/`startTimeUtc` (timestamp) required
- `endAt`/`endTimeUtc` (timestamp) optional (missing clock-out exception)
- `status` enum required: at least `DRAFT|SUBMITTED/PENDING_APPROVAL|APPROVED|REJECTED` (naming mismatch exists; see Open Questions)
- `submittedAtUtc` required when pending/approved/rejected
- `decisionByUserId` required when approved/rejected
- `decisionAtUtc` required when approved/rejected
- `rejectionReason` required when rejected
- Derived (display-only):
  - `durationMinutes` (computed client-side if start/end present; do not treat as authoritative)
  - `hasBlockingExceptions` boolean (from exceptions list)

#### TimeEntryAdjustment (create + read)
- `adjustmentId` (uuid) read-only
- `timeEntryId` required (FK)
- `reasonCode` enum required
- `notes` string optional
- One-of input required:
  - `proposedStartAt` timestamp optional
  - `proposedEndAt` timestamp optional
  - `minutesDelta` integer optional
- `status` enum read-only (e.g., `PROPOSED|APPROVED|REJECTED`)
- audit fields read-only: `requestedBy`, `createdAt`, `approvedBy`, `approvedAt`

#### TimeException (read + update status)
- `exceptionId` uuid required
- `timeEntryId` uuid optional (nullable for day-level exceptions)
- `personId` uuid required
- `workDate` date required
- `exceptionCode` enum required
- `severity` enum required: `WARNING|BLOCKING`
- `status` enum required: `OPEN|ACKNOWLEDGED|RESOLVED|WAIVED`
- `resolutionNotes` string required when waiving (per backend narrative) **TBD**
- `resolvedBy`, `resolvedAt` read-only

### Read-only vs editable
- Managers can only change:
  - time entry decision (approve/reject) while pending
  - exception status (ack/resolve/waive) as permitted
  - create adjustment records (not mutate time entry)

---

## 9. Service Contracts (Frontend Perspective)

> Moqui implementation will call services or REST endpoints; exact names depend on backend exposure in this frontend repo. Below are required contracts; if not present, add Open Questions / blocking.

### Load/view calls
- `GET /api/time-entries?status=PENDING_APPROVAL&workDate=YYYY-MM-DD`
- `GET /api/time-entries?status=PENDING_APPROVAL&workOrderId=<id>`
- `GET /api/time-entries/{timeEntryId}`
- `GET /api/time-entries/{timeEntryId}/exceptions`
- `GET /api/time-entries/{timeEntryId}/adjustments`

### Create/update calls
- `POST /api/time-entries/{timeEntryId}/adjustments`
  - body: `{ reasonCode, notes?, proposedStartAt?, proposedEndAt?, minutesDelta? }`

### Submit/transition calls
- Approve (batch capable preferred):
  - `POST /api/time-entries/approve`
    - body: `{ timeEntryIds: [], approvedByUserId?, reason? }` (approvedBy from auth preferred)
- Reject (batch capable preferred):
  - `POST /api/time-entries/reject`
    - body: `{ timeEntryIds: [], reason: "..." }`

- Exception actions:
  - `POST /api/time-exceptions/{exceptionId}/acknowledge`
  - `POST /api/time-exceptions/{exceptionId}/resolve`
  - `POST /api/time-exceptions/{exceptionId}/waive` with `{ resolutionNotes }`

### Error handling expectations
- 401 unauthenticated → redirect to login
- 403 unauthorized → show permission error; keep screen read-only
- 404 not found → show “Entry no longer exists”
- 409 conflict → prompt refresh; do not assume idempotent success
- Validation errors → map to form-level messages

---

## 10. State Model & Transitions

### TimeEntry states (as used by UI)
- `PENDING_APPROVAL` (or `SUBMITTED`) — eligible for manager approve/reject
- `APPROVED` — locked, immutable
- `REJECTED` — not locked; submitter can correct/resubmit (outside scope)
- Other states may exist (e.g., `DRAFT`), displayed read-only if encountered.

### Allowed transitions (UI-permitted)
- `PENDING_APPROVAL` → `APPROVED` via Approve action (manager)
- `PENDING_APPROVAL` → `REJECTED` via Reject action with reason (manager)

### Role-based transitions
- Only Shop Manager (or users with equivalent permissions) see enabled approve/reject controls.

### UI behavior per state
- `PENDING_APPROVAL`: selectable in queue; actions enabled subject to exception rules
- `APPROVED`: show locked, decision metadata; disable selection/actions
- `REJECTED`: show rejection metadata; disable selection/actions

---

## 11. Alternate / Error Flows

### Validation failures
- Reject without reason:
  - UI blocks submit; server 400 displayed if bypassed.
- Approve with unresolved blocking exceptions:
  - UI disables Approve and lists blocking exceptions; if bypassed, handle 422/400 with message.

### Concurrency conflicts
- Another manager approves while current manager viewing queue:
  - Approve attempt returns 409; UI refreshes queue and shows which entries failed.

### Unauthorized access
- User without permission loads screen:
  - Screen loads queue read-only (if backend allows) or blocks with 403 message.
  - Actions hidden/disabled.

### Empty states
- No pending entries for selected filters:
  - Show empty state with guidance: “No time awaiting approval for this scope.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View pending approvals for a day
Given I am authenticated as a Shop Manager  
When I navigate to Timekeeping Approvals for workDate "2026-01-15"  
Then I see a list of time entries with status "PENDING_APPROVAL" for that date  
And each row shows employee, work order (if any), start/end times, and exception indicator (if any)

### Scenario 2: Approve a pending time entry with no blocking exceptions
Given a time entry "T1" is in status "PENDING_APPROVAL"  
And "T1" has no exceptions with severity "BLOCKING" in status "OPEN"  
When I approve "T1"  
Then the system updates "T1" to status "APPROVED"  
And the UI shows decision metadata (approved by, approved at)  
And "T1" becomes read-only/locked in the UI

### Scenario 3: Reject a pending time entry requires reason
Given a time entry "T2" is in status "PENDING_APPROVAL"  
When I attempt to reject "T2" without entering a reason  
Then the UI prevents submission and displays "Rejection reason is required"  
When I reject "T2" with reason "Incorrect work order"  
Then the system updates "T2" to status "REJECTED"  
And the UI displays the rejection reason and decision metadata

### Scenario 4: Approval blocked by unresolved blocking exception
Given a time entry "T3" is in status "PENDING_APPROVAL"  
And "T3" has an exception "E1" with severity "BLOCKING" and status "OPEN"  
When I view "T3" in the approval queue  
Then the Approve action is disabled for "T3" (or blocked with explanation)  
When I waive exception "E1" with resolution notes "Reviewed; acceptable for payroll"  
And I approve "T3"  
Then "T3" transitions to "APPROVED"

### Scenario 5: Create an adjustment record for an entry
Given a time entry "T4" exists  
When I create an adjustment for "T4" with reasonCode "INCORRECT_WORKORDER" and minutesDelta "+15"  
Then an adjustment record is created and visible under "T4" adjustments  
And the original time entry timestamps are unchanged in the UI

### Scenario 6: Concurrency conflict on approve
Given a time entry "T5" is in status "PENDING_APPROVAL"  
And another manager approves "T5" before I do  
When I attempt to approve "T5"  
Then the system responds with a conflict error  
And the UI prompts me to refresh and shows "T5" is already approved

---

## 13. Audit & Observability

### User-visible audit data
- For each entry: submittedAt, decisionBy, decisionAt, rejectionReason (if rejected)
- For adjustments: createdBy/requestedBy, createdAt, status, approval metadata (if any)
- For exceptions: detectedAt, severity, status, resolvedBy/resolvedAt, resolution notes (if waived)

### Status history
- If backend supports history endpoints, provide a “History” panel:
  - approvals/rejections
  - exception state changes
  - adjustments lifecycle
(If not available, omit UI history and rely on backend audit; see Open Questions.)

### Traceability expectations
- UI includes identifiers in error details/logs: `timeEntryId`, `exceptionId`, correlation/request id (if exposed by backend headers).

---

## 14. Non-Functional UI Requirements
- **Performance:** Queue load for a single day should render within 2s for up to 200 entries (paging if more).
- **Accessibility:** All dialogs keyboard-navigable; form errors announced; table selection accessible.
- **Responsiveness:** Works on tablet (manager station) and desktop.
- **i18n/timezone:** Display timestamps in shop local timezone; store/submit in UTC as provided by API. (Timezone source TBD: user profile vs location.)

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty state messaging and “change filters” guidance; safe as UI-only ergonomics. (Sections: UX Summary, Error Flows)
- SD-UX-PAGINATION: Paginate queue results when large; safe as it doesn’t change domain logic. (Sections: UX Summary, Service Contracts)
- SD-ERR-HTTP-MAP: Standard mapping for 401/403/404/409 to user-facing messages; safe as it does not invent business rules. (Sections: Business Rules, Error Flows, Service Contracts)

---

## 16. Open Questions

1. **Domain label conflict:** The frontend issue states domain “user / shop management”, but the backend story is labeled `domain:workexec`. Should this frontend story be owned by `domain:workexec` (timekeeping within execution) or another domain (e.g., people/user)? This story is currently labeled `domain:workexec` to match the authoritative backend reference, but needs confirmation.  
2. **API contracts:** What are the actual backend endpoints exposed to the Moqui frontend for:
   - listing time entries by workDate/workOrder/status
   - approving/rejecting (single vs batch; request schema; whether approvedBy comes from auth)
   - adjustments create/list and whether adjustments require separate approval in this same UI  
3. **Status naming:** Backend reference uses both `PENDING_APPROVAL` and `SUBMITTED`. What are the canonical enum values returned by the API for time entry status?  
4. **Exception operations:** Are exception actions (ACKNOWLEDGED/RESOLVED/WAIVED) implemented as endpoints? If not, is exception handling read-only for v1 and enforced purely server-side on approval attempts?  
5. **Permission model:** What permission codes/roles does the frontend check to show approve/reject/exception actions (e.g., `TimeEntry:Approve`, `TimeEntry:Reject`)? Is there a distinct permission for waiving blocking exceptions?  
6. **Adjustment input rule:** Must the UI support both “proposed start/end” and “minutes delta”, or only one? If both are supported, are they mutually exclusive (one-of) as described?  
7. **Timezone source:** Should timestamps be displayed in the shop/location timezone or the manager’s user preference timezone?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Timekeeping: Approve Submitted Time for a Day/Workorder  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/130  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Timekeeping: Approve Submitted Time for a Day/Workorder

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Shop Manager**, I want to approve time submissions so that time becomes locked for payroll.

## Details
- Approve/reject with reason.
- Adjustments via delta entry.

## Acceptance Criteria
- Approved locked.
- Adjustments tracked.
- Exceptions list supported.

## Integrations
- HR receives approval state and totals.

## Data / Entities
- TimeApproval, AdjustmentEntry, ExceptionFlag

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