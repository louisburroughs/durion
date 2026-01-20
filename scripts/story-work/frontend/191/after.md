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
[FRONTEND] [STORY] Accounting Periods: Create / Close / Reopen with Posting Locks (per Business Unit)

### Primary Persona
Finance / Accounting Manager (with period management permissions)

### Business Value
Enforce deterministic posting controls by accounting period per business unit, protect financial reporting integrity, and provide auditable exception handling for reopening closed periods.

---

## 2. Story Intent

### As a / I want / So that
**As a** Finance / Accounting Manager,  
**I want** to create, close, and (with elevated permission) reopen accounting periods per business unit,  
**so that** postings are blocked in closed periods unless explicitly reopened with an audit reason.

### In-scope
- Frontend screens to:
  - list/filter accounting periods by business unit and date range
  - create accounting periods with non-overlap validation feedback
  - close an open period
  - reopen a closed period with mandatory reason and elevated permission gate
  - view audit/history for period actions (create/close/reopen)
- Frontend handling of “posting blocked because period closed” errors (surface deterministic error message/code to user).

### Out-of-scope
- Implementing backend posting enforcement logic itself (frontend only consumes and displays errors).
- Defining GL accounts, posting rules, or journal entry behavior.
- Designing business unit master data (frontend only selects an existing business unit reference).
- Reporting UI beyond the period/audit views.

---

## 3. Actors & Stakeholders

### Actors
- **Accounting Manager**: can create and close periods.
- **Privileged Accounting Manager / Controller**: can reopen periods (requires special permission).
- **Auditor / Finance Reviewer**: reads period history/audit events.
- **System (Moqui app)**: enforces permissions and displays backend validation errors.

### Stakeholders
- Finance/Accounting leadership (controls and auditability)
- Operations (impacts ability to post transactions)
- Engineering (screen/service wiring, permission gating)
- Compliance/audit (exception logging for reopen)

---

## 4. Preconditions & Dependencies

### Preconditions
- User authentication is available in Moqui.
- Permission concepts exist and are checkable in UI (names TBD; see Open Questions).
- A Business Unit concept exists and is selectable (entity + id + display name).

### Dependencies (backend/API)
The frontend needs backend endpoints/services to:
- list periods (by businessUnitId, date filters)
- create period (validate overlap)
- close period
- reopen period (requires reason + permission)
- fetch audit/history for a period (or embedded history in period detail)

If these contracts do not exist yet, this frontend story is blocked until confirmed.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Accounting → Periods** (exact menu location TBD by app conventions)

### Screens to create/modify (Moqui)
1. **`AccountingPeriods`** (list)
   - search/filter form + results grid
2. **`AccountingPeriodDetail`** (view + actions)
   - period header fields
   - action buttons: Close / Reopen (conditional)
   - history/audit section
3. **`AccountingPeriodCreate`** (create form)
   - business unit + date range fields
4. (Optional) modal dialogs implemented as separate subscreens or screen dialogs:
   - Close confirmation
   - Reopen dialog (requires reason)

### Navigation context
- From list → click period row → detail screen
- From list → “Create Period” → create screen → success redirects to detail
- From detail → Close/Reopen actions remain on detail and refresh state/history

### User workflows

#### Happy path: create period
1. User opens Accounting Periods list
2. Clicks Create Period
3. Selects Business Unit, Start Date, End Date
4. Submits
5. Sees created period in detail as **OPEN**

#### Happy path: close period
1. User opens an OPEN period detail
2. Clicks Close Period
3. Confirms
4. Period becomes **CLOSED**; close metadata visible (who/when)

#### Happy path: reopen period
1. Privileged user opens a CLOSED period detail
2. Clicks Reopen Period
3. Enters mandatory reason and confirms
4. Period becomes **OPEN**; reopen audit entry visible (who/when/why)

#### Alternate: posting blocked by closed period
- When user attempts a posting flow elsewhere in the app and backend responds with period-closed error, UI shows a deterministic error that references:
  - business unit
  - transaction date (if available)
  - period status = CLOSED
  - next action: contact finance or request reopen (no reopen action from that screen unless already implemented)

---

## 6. Functional Behavior

### Triggers
- Screen load triggers fetch/list calls.
- Form submit triggers create/close/reopen service calls.
- Backend errors trigger UI error mapping.

### UI actions
- **List screen**
  - Select business unit filter (required to avoid cross-BU ambiguity; if not possible, see Open Questions)
  - Optional date filters (from/to) and status filter (OPEN/CLOSED/ALL)
  - Open detail from row click
- **Create screen**
  - Input: businessUnitId, startDate, endDate
  - Submit/cancel
- **Detail screen**
  - Display: businessUnit, start/end (inclusive), status, created metadata, closed metadata (if any)
  - Close button visible only when status=OPEN and user has manage permission
  - Reopen button visible only when status=CLOSED and user has reopen permission
  - Audit/history visible read-only

### State changes (frontend-visible)
- Status transitions reflected immediately after successful service response:
  - OPEN → CLOSED
  - CLOSED → OPEN
- History section refreshes after transitions.

### Service interactions (Moqui)
- Use screen `actions` to call services for:
  - list/load
  - create
  - close
  - reopen (requires reason)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client-side, then server-side authoritative)
- **Create Period**
  - businessUnitId: required
  - startDate: required
  - endDate: required
  - startDate <= endDate (client-side validation)
  - Non-overlap with existing periods for the business unit (server-side validation; UI must render conflict clearly)
- **Close Period**
  - Allowed only if current status is OPEN (UI hides/disabled if not)
- **Reopen Period**
  - Allowed only if current status is CLOSED
  - Requires elevated permission (UI hides action if lacking permission)
  - **Reason is mandatory** (client-side required, server enforces)
  - Reason min/max length is undefined → must be confirmed; UI should enforce whatever backend returns.

### Enable/disable rules
- Disable submit while service call in flight.
- Disable Close/Reopen buttons while action in flight to prevent double submits.
- In detail view, actions must re-check current status from latest loaded data (avoid stale UI).

### Visibility rules
- Reopen action only visible if user has `ACCOUNTING_PERIOD_REOPEN` (name TBD) and period is CLOSED.
- Close action only visible if user has `ACCOUNTING_PERIOD_MANAGE` (name TBD) and period is OPEN.

### Error messaging expectations
- Overlap conflict: show message like “Date range overlaps an existing period for this business unit.” Include conflicting period identifiers/date range if returned.
- Period closed posting error: show deterministic message and preserve backend error code (e.g., `ERR_ACCOUNTING_PERIOD_CLOSED`).
- Unauthorized: show “You do not have permission to perform this action.” Preserve HTTP 403.
- Concurrency conflict (optimistic locking or stale update): show “This period was changed by another user. Refresh and try again.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `AccountingPeriod`
- `BusinessUnit` (or equivalent entity representing business unit)
- `User` (for display of closedBy/reopenedBy if returned)
- `PeriodReopenAuditEvent` or generic audit log entity (naming TBD)

### Fields (type, required, defaults)

#### AccountingPeriod (minimum required for UI)
- `accountingPeriodId` (string/UUID) — required, read-only
- `businessUnitId` (string/UUID) — required, editable on create only
- `businessUnitName` (string) — read-only (derived via join)
- `startDate` (date) — required, editable on create only (unless backend allows edits; not specified)
- `endDate` (date) — required, editable on create only
- `status` (enum: `OPEN`, `CLOSED`) — read-only except via transitions
- `createdAt` (datetime) — read-only
- `createdByUserId` (string) — read-only
- `closedAt` (datetime, nullable) — read-only
- `closedByUserId` (string, nullable) — read-only
- `updatedAt` (datetime) — read-only
- `updatedByUserId` (string) — read-only
- `version` or `lastUpdatedStamp` (if used for optimistic locking) — read-only but submitted back on transitions if required by backend

#### Reopen audit event (for display)
- `eventId` (string/UUID) — read-only
- `accountingPeriodId` — read-only
- `action` (enum/string: CREATED/CLOSED/REOPENED) — read-only
- `actorUserId` — read-only
- `occurredAt` — read-only
- `reason` (text, nullable except required for REOPENED) — read-only
- `clientIpAddress` (string, optional) — read-only (display optional)

### Read-only vs editable by state/role
- Only create form fields are editable (businessUnitId, startDate, endDate).
- Close/Reopen are transitions only (no direct edit of status).
- Reason is editable only within the reopen action dialog and not stored on period itself unless backend does so (unknown).

### Derived/calculated fields
- Display label “Period: YYYY-MM-DD to YYYY-MM-DD”
- Status badge from `status`
- “Locked for posting” derived as `status == CLOSED`

---

## 9. Service Contracts (Frontend Perspective)

> Backend endpoints/service names are not provided in inputs. The frontend must integrate with Moqui services; exact service names/paths require confirmation. Below are required contracts, not invented definitive names.

### Load/view calls
1. **List periods**
   - Input: `businessUnitId` (recommended required), optional `status`, optional `fromDate`, `toDate`, pagination
   - Output: list of `AccountingPeriod` summary fields
2. **Get period detail**
   - Input: `accountingPeriodId`
   - Output: full `AccountingPeriod` + optionally history entries
3. **Get period audit/history** (if not embedded)
   - Input: `accountingPeriodId`
   - Output: ordered events (desc by occurredAt)

### Create/update calls
4. **Create period**
   - Input: `businessUnitId`, `startDate`, `endDate`
   - Output: created `accountingPeriodId` and resulting record

### Submit/transition calls
5. **Close period**
   - Input: `accountingPeriodId` (+ optimistic lock token if required)
   - Output: updated record
6. **Reopen period**
   - Input: `accountingPeriodId`, `reason` (+ optimistic lock token if required)
   - Output: updated record + audit event reference (optional)

### Error handling expectations (mapping)
- `400 Bad Request`: validation errors (missing reason, start>end)
- `403 Forbidden`: permission denied
- `409 Conflict`: overlap on create OR period already in target state OR optimistic lock conflict
- Domain-specific error code expected for posting rejection:
  - `ERR_ACCOUNTING_PERIOD_CLOSED` (from backend reference); UI must surface code + friendly message

Moqui screen actions must capture these errors and present them via standard message rendering used in the project.

---

## 10. State Model & Transitions

### Allowed states (AccountingPeriod.status)
- `OPEN`
- `CLOSED`

### Transitions
- `OPEN` → `CLOSED` via **Close Period**
  - Permission: `ACCOUNTING_PERIOD_MANAGE` (name TBD)
- `CLOSED` → `OPEN` via **Reopen Period**
  - Permission: `ACCOUNTING_PERIOD_REOPEN` (name TBD)
  - Requires: reason (non-empty)

### UI behavior per state
- OPEN:
  - Show “Close Period” if permitted
  - Hide “Reopen Period”
  - Indicate posting allowed
- CLOSED:
  - Show “Reopen Period” if permitted
  - Hide “Close Period”
  - Indicate posting locked/blocked for dates in range

---

## 11. Alternate / Error Flows

### Validation failures
- Create with missing fields → inline field errors + no request (client-side)
- Create overlap detected server-side → show non-field error at top + keep form values
- Reopen without reason → inline reason error, no request (client-side)

### Concurrency conflicts
- If close/reopen returns conflict indicating stale version:
  - show message “Period was updated by another user”
  - provide “Reload” action on the detail screen

### Unauthorized access
- If user navigates directly to create/detail URL without permission:
  - screen should show 403-style message and not render action controls
  - do not leak data if backend denies load

### Empty states
- List with no periods → show empty-state guidance: “No periods found for selected business unit/date range.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: List periods for a business unit
**Given** I am authenticated  
**And** I have permission to view accounting periods  
**When** I open the Accounting Periods screen and select a business unit  
**Then** I see a list of accounting periods for that business unit  
**And** each row shows start date, end date, and status  

### Scenario 2: Create an accounting period successfully
**Given** I am authenticated  
**And** I have permission to manage accounting periods  
**When** I create a period for business unit “BU-1” with start date “2026-01-01” and end date “2026-01-31”  
**Then** the period is created with status “OPEN”  
**And** I am navigated to the period detail screen  
**And** the detail screen shows the correct business unit and date range  

### Scenario 3: Reject creating an overlapping accounting period
**Given** an accounting period exists for business unit “BU-1” from “2026-01-01” to “2026-01-31”  
**And** I have permission to manage accounting periods  
**When** I attempt to create another period for “BU-1” from “2026-01-15” to “2026-02-15”  
**Then** the system rejects the request with a conflict error  
**And** the UI displays a non-overlap validation message  
**And** the form remains editable with my entered values preserved  

### Scenario 4: Close an open period successfully
**Given** I am authenticated  
**And** I have permission to manage accounting periods  
**And** an accounting period exists with status “OPEN”  
**When** I click “Close Period” and confirm  
**Then** the period status becomes “CLOSED”  
**And** the UI displays closed metadata (closed by, closed at) if provided by the backend  
**And** the “Close Period” action is no longer available  

### Scenario 5: Reopen a closed period with reason (authorized)
**Given** I am authenticated  
**And** I have permission to reopen accounting periods  
**And** an accounting period exists with status “CLOSED”  
**When** I click “Reopen Period”  
**And** I enter the reason “Correcting mis-categorized invoice #123”  
**And** I confirm the reopen action  
**Then** the period status becomes “OPEN”  
**And** the period history/audit section includes a “REOPENED” entry with my user and reason  

### Scenario 6: Reopen a closed period without reason (client validation)
**Given** I am authenticated  
**And** I have permission to reopen accounting periods  
**And** an accounting period exists with status “CLOSED”  
**When** I click “Reopen Period” and submit without a reason  
**Then** the UI prevents submission  
**And** the reason field shows a required validation error  

### Scenario 7: Reopen denied without permission
**Given** I am authenticated  
**And** I do not have permission to reopen accounting periods  
**And** an accounting period exists with status “CLOSED”  
**When** I view the period detail screen  
**Then** I do not see the “Reopen Period” action  
**And** if I attempt to call reopen (e.g., via direct request), I receive a 403 error and the UI shows a permission error  

### Scenario 8: Posting attempt blocked by closed period is shown deterministically
**Given** an accounting period for business unit “BU-1” is “CLOSED” for dates including “2026-01-10”  
**When** I attempt a posting operation elsewhere in the app for business unit “BU-1” with transaction date “2026-01-10”  
**And** the backend responds with error code “ERR_ACCOUNTING_PERIOD_CLOSED”  
**Then** the UI shows an error message indicating posting is blocked due to a closed accounting period  
**And** the UI displays or logs the backend error code “ERR_ACCOUNTING_PERIOD_CLOSED”  

---

## 13. Audit & Observability

### User-visible audit data
- Period detail must display a history list including:
  - created (who/when)
  - closed (who/when)
  - reopened (who/when/why)
- For reopen, display the reason text.

### Status history
- Must be ordered by occurredAt descending.
- Must be refreshable after an action without full page reload (screen rerender is acceptable).

### Traceability expectations
- Frontend logs (where project supports it) should include:
  - `accountingPeriodId`
  - action name (CREATE/CLOSE/REOPEN)
  - correlation/request id if available from Moqui
- Do not log sensitive free-text beyond what is required; reason text should not be echoed to console logs in production if that violates policy (policy not provided → see Open Questions).

---

## 14. Non-Functional UI Requirements

### Performance
- List view should load within 2 seconds for typical business unit period counts (assume < 200); if pagination exists, fetch first page only.

### Accessibility
- All actions must be keyboard accessible.
- Dialogs must trap focus and provide accessible labels for confirm/cancel and reason field.
- Validation errors must be announced (aria-live) per Quasar defaults.

### Responsiveness
- List and detail usable on tablet widths used in POS back-office.
- Action buttons should collapse into a menu on narrow screens (implementation detail allowed by UI library).

### i18n/timezone/currency
- Dates displayed in the user’s locale/timezone as configured in the app.
- No currency behavior involved.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide a standard empty-state message and “Create Period” CTA on the list screen; qualifies as safe because it does not alter domain policy, only improves usability. (Impacted sections: UX Summary, Alternate/Empty states)
- SD-UX-INFLIGHT-DISABLE: Disable submit/action buttons during in-flight requests to prevent duplicate actions; qualifies as safe because it is UI-only idempotency protection. (Impacted sections: Functional Behavior, Error Flows)
- SD-ERR-STANDARD-MAPPING: Map 400/403/409 to inline vs banner errors consistently; qualifies as safe because it only renders backend outcomes without changing business rules. (Impacted sections: Service Contracts, Business Rules, Error Flows)

---

## 16. Open Questions

1. **Backend service contracts (blocking):** What are the exact Moqui service names and request/response shapes for:
   - list periods, get detail, create, close, reopen, and fetch audit/history?
2. **Permissions (blocking):** Confirm the permission identifiers and how the frontend should check them (e.g., `ACCOUNTING_PERIOD_MANAGE`, `ACCOUNTING_PERIOD_REOPEN`, or different names).
3. **Business Unit entity (blocking):** What entity represents a “Business Unit” in this system (e.g., `Organization`, `Party`, `Facility`, `Store/Location`), and what fields should be displayed/selected in the UI?
4. **Edit policy (blocking):** Are start/end dates editable after creation while OPEN, or immutable once created? Current story assumes create-only.
5. **Audit/history source (blocking):** Is period history returned as part of period detail, or via a separate endpoint/entity? What fields are available (reason, clientIp, actor display name)?
6. **Reopen reason constraints:** Are there min/max length, allowed characters, or required templates for the reopen reason?
7. **Posting-blocked UX integration:** Which existing posting flows in the frontend must surface `ERR_ACCOUNTING_PERIOD_CLOSED` (and where), or is this story limited to period management screens only?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Close: Open/Close Accounting Periods with Locks  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/191  
Labels: frontend, story-implementation, reporting

## Frontend Implementation for Story

**Original Story**: [STORY] Close: Open/Close Accounting Periods with Locks

**Domain**: reporting

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Period Close, Adjustments, and Reporting

## Story
Close: Open/Close Accounting Periods with Locks

## Acceptance Criteria
- [ ] Periods can be created and closed per business unit
- [ ] Closed periods block posting unless reopened with permission
- [ ] Reopen requires reason and is audit-logged
- [ ] Posting logic enforces period policy deterministically


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