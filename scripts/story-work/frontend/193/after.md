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
[FRONTEND] [STORY] AP: Approve and Schedule Payments with Controls

## Primary Persona
- AP Clerk
- AP Manager

## Business Value
Enable controlled, auditable progression of Bills from Draft to Approved to Scheduled, enforcing approval thresholds and permissions so the organization pays vendors accurately and reduces fraud/unauthorized disbursements.

---

# 2. Story Intent

## As a / I want / So that
**As an** AP Clerk or AP Manager,  
**I want** to approve Draft bills and schedule Approved bills for payment from the POS frontend,  
**so that** AP obligations can be managed with appropriate controls, audit trail, and readiness for payment execution.

## In-scope
- View bill details including current status and relevant audit fields.
- Perform **Approve** action on `DRAFT` bills (if authorized and within threshold).
- Perform **Schedule Payment** action on `APPROVED` bills (if authorized), capturing `scheduledFor` and `paymentMethod`.
- Display server validation/authorization/threshold errors clearly.
- Display bill status history/audit entries related to approval and scheduling (read-only).

## Out-of-scope
- Executing payments (owned by `domain:payment`).
- Editing bill header/lines, vendor data, or bill creation flow (unless already exists; not defined here).
- Configuration UI for approval policy/thresholds (owned by accounting configuration; not requested).
- Downstream payment outcome handling (`Payment.Executed.v1`, `Payment.Failed.v1`) UI (not requested here).

---

# 3. Actors & Stakeholders

## Actors
- **AP Clerk**: prepares bills; may request approval; may schedule if permitted.
- **AP Manager**: approves bills (higher thresholds); may schedule payments.
- **System (Moqui app)**: enforces status prerequisites, permission checks, threshold checks; records audit entries.

## Stakeholders
- **Finance/AP leadership**: needs controlled approval and cashflow scheduling.
- **Auditors/Compliance**: require immutable, queryable audit trail.
- **Payments/Treasury team**: relies on scheduled payments being valid and approved.

---

# 4. Preconditions & Dependencies

## Preconditions
- A Bill exists and is retrievable by `billId`.
- Bill has a status in `{DRAFT, APPROVED, SCHEDULED, ...}`.
- User is authenticated in the frontend and has server-evaluated permissions.

## Dependencies
- Backend capability for:
  - Loading bill details (including status, amount, vendor, and audit-related fields).
  - Approving a bill: transition `DRAFT → APPROVED` with threshold validation and audit entry.
  - Scheduling a bill: transition `APPROVED → SCHEDULED` capturing schedule inputs, audit entry, and emitting `Accounting.PaymentScheduled.v1`.

## Blocking dependency (needs clarification)
- Concrete Moqui service names/endpoints and entity names used by this frontend project are not provided in the inputs (see **Open Questions**).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From an AP Bills list screen (if exists): select a bill → navigate to Bill Detail.
- Direct route deep link: `/ap/bills/:billId` (proposed; must align with repo conventions—clarification needed).

## Screens to create/modify
1. **Bill List Screen (optional / if exists)**  
   - Add visible status badges and row actions or a “Details” navigation.
2. **Bill Detail Screen (required)**  
   - Summary section (read-only): billNumber, vendor, amount, currency, status.
   - Actions panel (contextual):
     - `Approve` button visible/enabled only when bill is `DRAFT` and user has permission.
     - `Schedule Payment` button visible/enabled only when bill is `APPROVED` and user has permission.
   - Schedule dialog/form (on Schedule action): fields for `scheduledFor`, `paymentMethod` (+ instrumentId if required).
   - Audit/history panel (read-only): approval and scheduling audit entries.

## Navigation context
- Breadcrumb: AP → Bills → Bill {billNumber}
- After successful transitions, remain on Bill Detail and refresh state from backend (show updated status and audit entries).

## User workflows

### Happy path A: Approve bill
1. User opens Bill Detail (status `DRAFT`).
2. Clicks **Approve**.
3. Frontend confirms intent (optional) and submits approve request.
4. On success: status updates to `APPROVED`, approval audit is visible.

### Happy path B: Schedule payment
1. User opens Bill Detail (status `APPROVED`).
2. Clicks **Schedule Payment**.
3. User enters `scheduledFor` and `paymentMethod` (and instrument if applicable).
4. Submit schedule request.
5. On success: status updates to `SCHEDULED`, scheduling audit visible; display paymentId (read-only) if returned.

### Alternate path: Not authorized or exceeds threshold
- Server rejects; UI shows error banner and keeps bill state unchanged.

---

# 6. Functional Behavior

## Triggers
- Screen load: Bill Detail is opened.
- User actions: Approve button click; Schedule Payment submit.

## UI actions
- **Load bill** on screen init and after any successful transition.
- **Approve**
  - Prompt (optional): “Approve this bill?” with billNumber and amount.
  - Submit approve request.
- **Schedule**
  - Open modal form with fields.
  - Validate required fields client-side (presence and basic format only).
  - Submit schedule request.

## State changes (frontend perspective)
- Optimistic UI is **not** permitted for financial state; update only after server success.
- After server success:
  - Refresh bill state from backend
  - Update action button availability based on returned status

## Service interactions
- `bill/load`: fetch details, including current status and audit info.
- `bill/approve`: transition to APPROVED (server enforces permissions + threshold).
- `bill/schedulePayment`: transition to SCHEDULED with schedule fields (server enforces permissions + state prerequisite).

(Exact service names/paths must be confirmed; see Open Questions.)

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Approve action:
  - UI must only offer Approve when status is `DRAFT`.
  - Server is authoritative for:
    - permission (`approve`)
    - threshold policy (approval limit)
- Schedule action:
  - UI must only offer Schedule when status is `APPROVED`.
  - Client-side required-field validation:
    - `scheduledFor` is required and must be a valid date.
    - `paymentMethod.type` is required.
    - `paymentMethod.instrumentId` required **only if** backend contract requires it for selected type (needs clarification).

## Enable/disable rules
- Disable all action buttons while a transition request is in-flight.
- Disable Approve button if bill status ≠ `DRAFT`.
- Disable Schedule button if bill status ≠ `APPROVED`.

## Visibility rules
- Hide actions entirely if user lacks permission (preferred) OR show disabled with tooltip “Not authorized” (needs project convention; clarify).
- Audit panel always visible if data exists; otherwise show “No audit events yet.”

## Error messaging expectations
- Authorization failures: show “You are not authorized to approve/schedule this bill.” Include backend error code if provided.
- Threshold failure: show “Approval amount exceeds your approval limit.” Include limit/required role only if backend returns safe details.
- Invalid state transition: show “Bill must be Approved before it can be scheduled.”
- Generic: show “Unable to complete action. Try again or contact support.”

---

# 8. Data Requirements

## Entities involved (frontend view models)
> Note: Backend entity names are not provided; below is the required data contract for UI.

### Bill
- `billId` (UUID, required, read-only)
- `billNumber` (string, required, read-only)
- `vendorId` (UUID, required, read-only)
- `vendorName` (string, required for UI display, read-only) **(if available)**
- `status` (enum, required, read-only): `DRAFT`, `APPROVED`, `SCHEDULED`, (others allowed but treated read-only)
- `amount.value` (decimal, required, read-only)
- `amount.currencyUomId` (string, required, read-only)
- `approvedByUserId` (UUID, nullable, read-only)
- `approvedAt` (timestamp, nullable, read-only)

### Scheduling (when status ≥ SCHEDULED)
- `paymentId` (UUID, nullable until scheduled; read-only)
- `scheduledFor` (date, nullable until scheduled; read-only after scheduled)
- `paymentMethod.type` (string/enum, nullable until scheduled; read-only after scheduled)
- `paymentMethod.instrumentId` (string/UUID, nullable; read-only after scheduled)
- `scheduledByUserId` (UUID, nullable; read-only)
- `scheduledAt` (timestamp, nullable; read-only)

### Audit entries (read-only list)
- `auditId` (UUID/string, required)
- `billId` (UUID, required)
- `eventType` (string, required) e.g., `BILL_APPROVED`, `PAYMENT_SCHEDULED`
- `oldStatus` (string, required)
- `newStatus` (string, required)
- `principalUserId` (UUID, required)
- `occurredAt` (timestamp, required)
- `notes` (string, optional) (if exists)

## Derived/calculated fields (frontend-only)
- `canApprove` = (status == `DRAFT`) AND (permission flag from backend if provided; else UI only uses status and relies on server for permission)
- `canSchedule` = (status == `APPROVED`) AND (permission flag if provided)

---

# 9. Service Contracts (Frontend Perspective)

> Moqui implementations typically use screen transitions and services. Exact service names are not provided; define required interfaces below and map them to Moqui `service-call` / REST as implemented in this repo.

## Load/view calls
### Get Bill Detail
- **Request**: `{ billId }`
- **Response**: Bill object + (optional) audit list + (optional) permissions for current user (e.g., `allowedActions: ['APPROVE','SCHEDULE']`)

## Create/update calls
### Approve Bill
- **Request**: `{ billId }`
- **Response**: updated Bill (status `APPROVED`, approvedBy/approvedAt populated) and/or success flag

### Schedule Payment
- **Request**:
  - `{ billId, scheduledFor, paymentMethod: { type, instrumentId? } }`
- **Response**: updated Bill (status `SCHEDULED`, schedule fields populated incl `paymentId`) and/or success flag

## Submit/transition calls
- Treat approve and schedule as **commands** that may return `409 Conflict` for invalid state transitions and `403 Forbidden` for unauthorized.

## Error handling expectations
Frontend must map backend errors to user-visible messages:
- `403` or errorCode `UNAUTHORIZED` → show authorization message
- `409` or errorCode `INVALID_STATE_TRANSITION` → show state prerequisite message
- `422` or errorCode `APPROVAL_THRESHOLD_EXCEEDED` → show threshold message
- `400/422` validation → show field-level errors for schedule form when possible
- Network/5xx → show retryable error banner; do not change UI state

(Exact error codes need confirmation; see Open Questions.)

---

# 10. State Model & Transitions

## Allowed states (in scope)
- `DRAFT`
- `APPROVED`
- `SCHEDULED`

Other states may exist; they are read-only for this story.

## Role-based transitions
- `DRAFT → APPROVED`: requires permission for approval and passing threshold policy (server-side).
- `APPROVED → SCHEDULED`: requires permission for scheduling (server-side).

## UI behavior per state
- `DRAFT`:
  - Show Approve action if permitted
  - Hide/disable Schedule action
- `APPROVED`:
  - Show Schedule action if permitted
  - Hide/disable Approve action
- `SCHEDULED`:
  - No approval/schedule actions
  - Show schedule details (paymentId, scheduledFor, method) read-only

---

# 11. Alternate / Error Flows

## Validation failures (schedule form)
- Missing `scheduledFor` → inline error; block submit
- Missing `paymentMethod.type` → inline error; block submit
- Invalid date format → inline error; block submit

## Threshold exceeded on approval
- Server rejects; UI shows non-field error banner; keep status `DRAFT`.

## Concurrency conflicts
- If bill status changed since load (e.g., another user approved it):
  - Approve or Schedule call returns conflict
  - UI shows “Bill was updated by another user; reloading.”
  - Auto-refresh bill details.

## Unauthorized access
- If user can load bill but cannot act:
  - Actions hidden/disabled; attempt via direct call results in server 403 and UI shows authorization error.

## Empty states
- Audit panel empty: show “No audit events recorded yet.”
- Missing schedule details in `SCHEDULED` due to partial data: show “Scheduling details unavailable” and log client error (without PII).

---

# 12. Acceptance Criteria

## Scenario 1: View bill details with contextual actions
**Given** I am an authenticated AP user  
**And** a bill exists with status `DRAFT`  
**When** I open the Bill Detail screen for that bill  
**Then** I can see billNumber, vendor, amount, currency, and status  
**And** I see an Approve action available (if I am authorized)  
**And** I do not see Schedule Payment available

## Scenario 2: Approve a draft bill successfully
**Given** I am authorized to approve bills and within my approval threshold  
**And** a bill exists in status `DRAFT`  
**When** I click Approve and confirm the action  
**Then** the frontend submits an approve request for that bill  
**And** on success the bill status updates to `APPROVED`  
**And** the approval audit entry is visible with approver and timestamp

## Scenario 3: Approval blocked due to threshold
**Given** I am authenticated  
**And** a bill exists in status `DRAFT`  
**And** the bill amount exceeds my approval threshold  
**When** I attempt to approve the bill  
**Then** the backend rejects the request  
**And** the frontend shows an error indicating the approval limit was exceeded  
**And** the bill remains in status `DRAFT`

## Scenario 4: Schedule payment successfully for approved bill
**Given** I am authorized to schedule payments  
**And** a bill exists in status `APPROVED`  
**When** I open Schedule Payment, enter a scheduled date and payment method, and submit  
**Then** the frontend submits the scheduling request  
**And** on success the bill status updates to `SCHEDULED`  
**And** scheduling audit entry is visible  
**And** a paymentId is displayed if returned by the backend

## Scenario 5: Scheduling blocked when bill not approved
**Given** I am authenticated  
**And** a bill exists in status `DRAFT`  
**When** I attempt to schedule payment for the bill  
**Then** the backend rejects the request  
**And** the frontend shows an error that the bill must be approved first  
**And** the bill remains in status `DRAFT`

## Scenario 6: Unauthorized approve/schedule
**Given** I am authenticated but not authorized to approve (or schedule)  
**And** a bill exists in the corresponding prerequisite status  
**When** I attempt the action  
**Then** the backend returns an authorization error  
**And** the frontend shows a “not authorized” message  
**And** no state change is shown in the UI

---

# 13. Audit & Observability

## User-visible audit data
- Bill detail shows:
  - `approvedByUserId` + `approvedAt` (if approved)
  - `scheduledByUserId` + `scheduledAt` + `scheduledFor` + `paymentMethod` (if scheduled)
- Audit list shows immutable events for status transitions with actor and timestamp.

## Status history
- Display chronological history of status changes (at least approval and scheduling).
- If backend provides only latest fields, UI must still show what is available; missing history triggers Open Question for audit API.

## Traceability expectations
- All UI actions log (client-side) structured info (no PII) for debugging:
  - `billId`, action (`approve` / `schedule`), result (`success` / `error`), backend errorCode if present.

---

# 14. Non-Functional UI Requirements

## Performance
- Bill Detail load time: target < 1s on typical network for average bill payload (excluding large attachments; not in scope).
- Action calls should show spinner/loading state immediately and prevent duplicate submissions.

## Accessibility
- Buttons and form controls must be keyboard accessible.
- Error messages must be announced to screen readers (use Quasar accessible components).

## Responsiveness
- Bill Detail usable on tablet width and above; mobile support acceptable but not required beyond responsive stacking.

## i18n/timezone/currency
- Display amount formatted using `currencyUomId`.
- Display timestamps in user locale/timezone (project convention; if not defined, use browser locale—needs confirmation).
- `scheduledFor` is a **date** (not datetime); display and submit as date without timezone conversion.

---

# 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Show explicit empty-state copy for missing audit/history; safe because it doesn’t alter domain behavior; impacts UX Summary, Alternate/Empty states.
- SD-UX-INFLIGHT-DISABLE: Disable actions while request in-flight to prevent double submit; safe UI ergonomics; impacts Functional Behavior, Error Flows.
- SD-ERR-MAP-GENERIC: Map unknown server/network errors to a generic retryable banner; safe because it avoids guessing business meaning; impacts Service Contracts, Error Flows.

---

# 16. Open Questions

1. **Moqui routing/screen conventions:** What is the correct screen path and URL pattern for AP Bill list/detail in `durion-moqui-frontend` (e.g., `/ap/bills`, `/ap/bills/:billId`), and should we modify an existing screen or create new?  
2. **Backend service/API contract:** What are the exact Moqui services (or REST endpoints) for:
   - load bill detail (including audit/history if available),
   - approve bill,
   - schedule payment?
3. **Error codes & response shapes:** What canonical error codes/messages does backend return for:
   - threshold exceeded,
   - invalid state transition,
   - unauthorized?
   (Needed for deterministic UI mapping and tests.)
4. **Payment method model:** What are allowed `paymentMethod.type` values and when is `instrumentId` required? Is there an API to list available instruments for the vendor/company?  
5. **Permission UX convention:** Should unauthorized actions be hidden entirely or shown disabled with tooltip? (Project-wide UX consistency.)
6. **Audit/history source:** Is there a dedicated audit endpoint/entity for Bill status transitions, or do we rely only on fields like `approvedAt/approvedBy` and `scheduledAt/scheduledBy`?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] AP: Approve and Schedule Payments with Controls  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/193  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] AP: Approve and Schedule Payments with Controls

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Accounts Payable (Bill → Payment)

## Story
AP: Approve and Schedule Payments with Controls

## Acceptance Criteria
- [ ] Bill workflow supports Draft → Approved → Scheduled
- [ ] Approval thresholds and role permissions enforced
- [ ] Payment scheduling records date/method and audit trail
- [ ] Payment execution blocked unless approved


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