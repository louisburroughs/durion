STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:security
- status:draft

### Recommended
- agent:story-authoring
- agent:security-domain-agent

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** security-strict

---

## 1. Story Header

### Title
[FRONTEND] Approval: Handle Approval Expiration

### Primary Persona
Approver (manager/supervisor) who reviews and approves requests in the POS.

### Business Value
Prevents invalid/late approvals by making “expired” approvals un-actionable, clearly communicating why, and guiding users to the correct next step—reducing operational errors and support overhead.

---

## 2. Story Intent

### As a / I want / So that
**As an** Approver,  
**I want** approvals to automatically become “Expired” after their expiration time and be blocked from being approved/denied,  
**so that** I cannot take action on stale approvals and the system remains secure and auditable.

### In-scope
- Frontend detection and handling of an approval that is expired (on load and on submit).
- UI behavior: disabling actions, showing status/alerts, and rendering expiration metadata.
- Error handling for backend responses indicating expiration.
- Moqui screen/form/transitions implementing the above.

### Out-of-scope
- Defining the backend expiration policy (duration, calculation, grace periods).
- Creating/renewing/reissuing approvals (unless explicitly provided by backend).
- Notification mechanisms (email/SMS/push) for impending expiration.

---

## 3. Actors & Stakeholders

- **Approver (Primary Actor):** Attempts to approve/deny a pending approval.
- **Requester (Stakeholder):** Needs timely decisions; impacted if approval expires.
- **System (Actor):** Enforces expiration, returns errors, provides current status.
- **Audit/Compliance (Stakeholder):** Requires traceability of expiration vs actions taken.
- **Security (Stakeholder):** Ensures expired approvals cannot be used to authorize actions.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the frontend.
- User has access to view the approval item (authorization enforced by backend / security).

### Dependencies
- A backend API/service exists to:
  - Load approval details including current status and expiration timestamp (or equivalent).
  - Attempt approve/deny actions and return deterministic errors for expired approvals.
- Time handling convention must be defined (server time vs client time; timezone).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From an “Approvals Inbox/List” screen: selecting an approval navigates to an approval detail screen.
- Deep link: direct navigation to an approval detail screen by `approvalId` in the URL.

### Screens to create/modify
1. **Modify**: `approvals/ApprovalList` (or equivalent)
   - Display status badge and optionally “Expired” state indicator.
2. **Modify/Create**: `approvals/ApprovalDetail`
   - Shows approval metadata, status, expiration info.
   - Provides actions: Approve / Deny (or equivalent).
3. **(Optional) Create**: reusable dialog/snackbar component wiring for error display consistent with project patterns.

> Exact screen names/paths are project-convention dependent; implementer must align with existing Moqui screen tree in `durion-moqui-frontend`.

### Navigation context
- Breadcrumb: Approvals → Approval Detail
- Back navigation returns to list with preserved filters (safe default).

### User workflows
#### Happy path (not expired)
1. User opens approval detail.
2. UI shows status = `PENDING` (or equivalent) and expiration timestamp.
3. User clicks Approve or Deny.
4. UI submits action; shows success; status updates to `APPROVED`/`DENIED`; actions become disabled.

#### Expired on view
1. User opens approval detail.
2. UI shows status = `EXPIRED`.
3. Approve/Deny actions are disabled/hidden; message explains expiration.
4. User can return to list.

#### Expires between view and submit
1. User opens approval detail while still actionable.
2. User waits; clicks Approve.
3. Backend rejects with “expired” error.
4. UI updates status to `EXPIRED`, displays error, disables actions.

---

## 6. Functional Behavior

### Triggers
- Screen load of Approval Detail (by `approvalId`).
- User clicks “Approve” or “Deny”.

### UI actions
- On load: fetch approval; compute whether expired **only if** backend provides an authoritative field; otherwise treat backend as source of truth and only reflect what backend says.
- Render:
  - Status chip (Pending/Approved/Denied/Expired)
  - Expiration timestamp (if provided)
  - If expired: show inline alert “This approval expired at <time> and can no longer be acted on.”

### State changes (frontend view state)
- `viewStatus`: `loading | loaded | error`
- `approval.status`: reflects backend status
- `actionsEnabled`: derived: enabled only when status is actionable and user is authorized.

### Service interactions (Moqui)
- `transition` on screen entry calls a service to load approval details.
- `transition` for approve/deny calls submit service; handles response; then reloads approval detail for consistency.

---

## 7. Business Rules (Translated to UI Behavior)

> Because the actual approval domain rules are not provided in inputs, only UI-safe translations are specified and key items are open questions.

### Validation
- Approve/Deny submit must include required identifiers (e.g., `approvalId`) and any required concurrency token/version if backend requires it.
- Do not allow submission if approval is expired (based on backend status/flag).

### Enable/disable rules
- If `approval.status == EXPIRED`: disable/hide approve/deny buttons.
- If `approval.status in (APPROVED, DENIED)`: disable/hide approve/deny buttons.
- If user lacks permission (backend returns 403): show “Not authorized” message and do not show actions.

### Visibility rules
- Expiration info is visible when provided by backend; if not provided, show status only.

### Error messaging expectations
- If backend indicates expiration on submit: show a non-dismissive error message:
  - Title: “Approval expired”
  - Body: “This approval expired and can’t be approved/denied. Refreshing status…”
  - After reload, status displays as `EXPIRED`.

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **Approval** (read model / DTO)
  - Source of record: backend approval service.

### Fields
Because the backend contract is not included, these are **required-to-implement UI** but must be confirmed:

| Field | Type | Required | Default | Notes |
|---|---|---:|---|---|
| `approvalId` | string/UUID | yes | n/a | Route param and submit identifier |
| `status` | enum|string | yes | n/a | Must include an `EXPIRED` representation |
| `expiresAt` | timestamp (ISO-8601) | no* | null | Needed to display expiry time; optional if backend doesn’t provide |
| `requestedAt` | timestamp | no | null | Informational |
| `requestedBy` | string | no | null | Informational |
| `subjectType` / `subjectId` | string | no | null | What this approval is for |
| `version` / `etag` | string/int | no* | null | Needed if backend requires optimistic concurrency |

\* “Required” depends on backend; if missing, UI still must function by relying on backend status and submit errors.

### Read-only vs editable
- All fields read-only in UI; only actions are approve/deny.

### Derived/calculated fields
- `isActionable = (status == PENDING)` (exact actionable status set is an open question).
- `isExpired = (status == EXPIRED)`; do **not** derive from client time unless backend explicitly authorizes that behavior.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui names are placeholders; implementer must map to actual service names or REST endpoints used in this frontend repo.

### Load/view calls
- **Service:** `approval.ApprovalServices.get#ApprovalDetail` (placeholder)
- **Input:** `{ approvalId }`
- **Output:** `{ approval: { ...fields above... } }`
- **Errors:**
  - `404` not found → show “Approval not found”
  - `403` forbidden → show “Not authorized”
  - `5xx` → generic retryable error

### Submit/transition calls
1. **Approve**
   - **Service:** `approval.ApprovalServices.post#Approve` (placeholder)
   - **Input:** `{ approvalId, version? }`
2. **Deny**
   - **Service:** `approval.ApprovalServices.post#Deny` (placeholder)
   - **Input:** `{ approvalId, version?, reason? }` (reason is unknown; open question)

### Error handling expectations
- Expired:
  - Backend returns `409 Conflict` or `422 Unprocessable Entity` (TBD) with machine code like `APPROVAL_EXPIRED` (TBD).
  - UI maps to “Approval expired” and reloads detail.
- Concurrency:
  - If backend returns stale version error: UI shows “This approval was updated by someone else. Reloading…” and reloads.

---

## 10. State Model & Transitions

### Allowed states (must be confirmed)
- `PENDING` (actionable)
- `APPROVED` (terminal)
- `DENIED` (terminal)
- `EXPIRED` (terminal)

### Role-based transitions
- Only authorized approvers may transition `PENDING → APPROVED` or `PENDING → DENIED`.
- System may transition `PENDING → EXPIRED` (time-based).

### UI behavior per state
- `PENDING`: show Approve/Deny enabled.
- `APPROVED`/`DENIED`: show status, disable actions.
- `EXPIRED`: show expired banner, disable actions, show `expiresAt` if available.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing `approvalId` route param → show error state and link back to list.
- Missing required fields on submit (backend 400) → show backend message; keep user on page.

### Concurrency conflicts
- Backend indicates already approved/denied/expired while user is viewing:
  - UI shows conflict message and reloads to reflect latest status.

### Unauthorized access
- 403 on load: show unauthorized page/section; do not render actions.
- 403 on submit: show “You are not authorized to take this action.”

### Empty states
- Approval detail returns minimal info: still render status and identifiers; hide unknown sections.

---

## 12. Acceptance Criteria

### Scenario 1: View an expired approval
**Given** an approval exists with status `EXPIRED`  
**When** the user opens the Approval Detail screen for that approval  
**Then** the screen shows status `EXPIRED`  
**And** the Approve and Deny actions are not actionable (disabled or hidden)  
**And** the user sees a message indicating the approval is expired  
**And** no approve/deny request is sent from the UI.

### Scenario 2: Expiration handled on submit
**Given** an approval is displayed as actionable (`PENDING`)  
**And** the approval expires before the user submits  
**When** the user clicks Approve (or Deny)  
**And** the backend responds with an “expired approval” error  
**Then** the UI shows an “Approval expired” error message  
**And** the UI reloads the approval detail  
**And** the status is shown as `EXPIRED`  
**And** actions are no longer actionable.

### Scenario 3: Non-expired approval can be approved
**Given** an approval exists with status `PENDING`  
**When** the user clicks Approve  
**And** the backend returns success  
**Then** the UI shows a success confirmation  
**And** the approval status updates to `APPROVED` (after reload or response binding)  
**And** the Approve/Deny actions are no longer actionable.

### Scenario 4: Unauthorized user cannot act
**Given** a user without permission to act on approvals  
**When** they open an approval detail  
**Then** the UI indicates they are not authorized  
**And** no action controls are available  
**And** if they attempt a direct submit (e.g., via crafted request), the UI displays a forbidden error on response.

### Scenario 5: Not found approval
**Given** no approval exists for `approvalId = X`  
**When** the user navigates to Approval Detail for `X`  
**Then** the UI displays “Approval not found”  
**And** provides navigation back to Approval List.

---

## 13. Audit & Observability

### User-visible audit data
- Show status and timestamps if provided (requestedAt, expiresAt, decidedAt).
- Show “Last updated” timestamp if backend provides.

### Status history
- If backend exposes history, render a read-only timeline; otherwise out of scope.

### Traceability expectations
- Frontend should include correlation/request ID in error logs if available from existing frontend logging patterns.
- On submit failures due to expiration, log event with `approvalId` and error code (no PII).

---

## 14. Non-Functional UI Requirements

- **Performance:** Approval detail load under 2s on typical broadband; show skeleton/loading state immediately.
- **Accessibility:** WCAG 2.1 AA; expired banner announced to screen readers; buttons reflect disabled state correctly.
- **Responsiveness:** Works on tablet and desktop; approval actions remain reachable without horizontal scrolling.
- **i18n/timezone:** Display `expiresAt` in the location/user timezone per app standard; if undefined, display in UTC with explicit “UTC” label (needs confirmation).

---

## 15. Applied Safe Defaults

- SD-UI-EMPTY-STATE-01: Provide a standard “Not found / Unauthorized / Error” empty state component behavior; safe because it doesn’t change domain logic, only presentation. (Impacted: UX Summary, Error Flows)
- SD-UI-LOADING-STATE-01: Use consistent loading indicator/skeleton while fetching approval detail; safe as it only improves ergonomics. (Impacted: UX Summary)
- SD-ERR-MAP-01: Map HTTP 401/403/404/409/422/500 to standard UI notifications with retry where applicable; safe because it doesn’t assume business policy, only transport-level handling. (Impacted: Service Contracts, Error Flows)

---

## 16. Open Questions

1. **Domain ownership / label:** Is “Approval” governed by `domain:security` (authorization/approval control) or another domain (e.g., workexec/accounting)? Confirm the correct domain label and agent.
2. **Backend contract:** What are the actual API endpoints/services used by the frontend to:
   - list approvals,
   - load approval detail,
   - approve,
   - deny?
   Include request/response schemas and error format (including a machine-readable error code for expiration).
3. **State model:** What are the authoritative approval statuses and which are actionable? (Is it only `PENDING`? Are there intermediate states like `REQUESTED`, `IN_REVIEW`?)
4. **Expiration definition:** Does backend expose:
   - `expiresAt` timestamp,
   - `isExpired` boolean,
   - or only `status=EXPIRED`?
   Also confirm whether client should ever compute expiration from `expiresAt` or must rely solely on backend status.
5. **Timezones:** Which timezone governs display of `expiresAt` (user profile timezone, site/location timezone, or always UTC)?
6. **Deny requirements:** Does denying require a reason/comment? If yes, what validation rules (min/max length, required/optional)?
7. **Post-expiration user path:** Should UI offer an explicit “Request new approval” action/link when expired? If yes, what screen/route/service handles it?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] Approval: Handle Approval Expiration  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/268  
Labels: frontend, story-implementation, type:story, layer:functional, kiro

## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #23 - Approval: Handle Approval Expiration  
**URL**: https://github.com/louisburroughs/durion/issues/23  
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for frontend development
- Coordinate with corresponding backend implementation if needed

### Technical Requirements
**Frontend Implementation Requirements:**
- Use Vue.js 3 with Composition API
- Follow TypeScript best practices
- Implement using Quasar UI framework components
- Ensure responsive design and accessibility (WCAG 2.1)
- Handle loading states and error conditions gracefully
- Implement proper form validation where applicable
- Follow established routing and state management patterns

### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Frontend agents: Focus on Vue.js 3 components, TypeScript, Quasar UI framework. Coordinate with backend implementation for API contracts.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `frontend` - Implementation type

---
*Generated by Missing Issues Audit System - 2025-12-26T17:37:43.83278977*