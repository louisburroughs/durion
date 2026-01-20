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
[FRONTEND] [STORY] Completion: Reopen Completed Workorder (Controlled)

### Primary Persona
Back Office Manager

### Business Value
Enable controlled correction of completed work orders prior to invoicing, with strict permission gating, mandatory justification, and auditable invalidation of invoice-ready scope to protect downstream accounting and billing integrity.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Back Office Manager  
- **I want** to reopen a completed work order by providing a mandatory reason  
- **So that** I can correct mistakes while preserving auditability and preventing invoicing until the work order is properly re-completed.

### In-scope
- Frontend UI to initiate “Reopen” for eligible work orders.
- Mandatory reason capture and submission.
- Permission-aware visibility/enablement of the action.
- Post-action UI state updates (reopened flag, invoice-ready revoked, snapshot superseded).
- Display of relevant audit/transition feedback to the user (confirmation + status indicators).
- Idempotency-safe UX for repeated submission attempts (prevent double-submit; handle 409/duplicate gracefully).

### Out-of-scope
- Credit/rebill workflows when an invoice is already issued/finalized.
- Defining or editing the editability policy itself (config/table management).
- Implementing backend event emission, accounting integration plumbing (frontend will rely on backend behavior).
- Any “silent edit after completion” outside of the reopen flow.

---

## 3. Actors & Stakeholders

### Actors
- **Back Office Manager** (primary): initiates reopen.
- **Service Advisor** (secondary): may later edit fields unlocked by policy (not fully implemented here; only ensure UI respects read-only status post-reopen as data indicates).
- **Unauthorized User**: must be blocked.

### Stakeholders
- **Accounting / Event consumers**: depend on a single `WorkorderReopened` event and snapshot invalidation semantics.
- **Billing/Invoicing**: must not allow invoicing until re-completion and re-snapshot.

---

## 4. Preconditions & Dependencies

### Preconditions
- Work order exists and is viewable in POS frontend.
- Work order is in `COMPLETED` status (per backend rule).
- No associated invoice is `ISSUED` or `FINALIZED` (backend blocks otherwise).
- User has permission `WORKORDER_REOPEN_COMPLETED` (backend enforces; frontend should also hide/disable action where possible).

### Dependencies
- Backend endpoints must exist and be accessible from Moqui frontend:
  - Reopen endpoint (exact path needs confirmation—see Open Questions).
  - Work order detail endpoint providing `status`, `isReopened`, and invoice readiness indicator (name needs confirmation).
  - Billable scope snapshot info (at minimum: active snapshot id + status) OR a derived “invoice-ready” flag.
- Authentication/authorization context available in frontend (current userId, permissions/roles).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Work Order Detail screen (primary entry).
- Optional: Work Order list row actions (secondary entry) **only if** list provides sufficient eligibility info; otherwise keep only on detail.

### Screens to create/modify
- **Modify**: `WorkOrderDetail` screen (Moqui Screen)
  - Add an actions section with a `Reopen Work Order` action.
  - Add a modal/dialog to collect `reopenReason`.
- **Optional/Recommended**: `WorkOrderAudit/History` subsection or related screen region to show latest reopen audit event and snapshot status (read-only).

### Navigation context
- User is already viewing a specific work order (`workOrderId` in URL/parameters).
- After successful reopen, user remains on Work Order Detail and sees updated flags/status indicators.

### User workflows
#### Happy path
1. Back Office Manager opens a completed work order.
2. Clicks **Reopen Work Order**.
3. Enters mandatory reason.
4. Confirms.
5. UI shows success message; work order indicates reopened (`isReopened=true`), invoice-ready revoked, and (if available) snapshot now `SUPERSEDED`.

#### Alternate paths
- User without permission: action hidden or disabled; if forced via URL/action, backend returns 403 and UI shows access denied.
- Work order not `COMPLETED`: action hidden/disabled; backend returns 409/400 if invoked.
- Invoice already issued: backend blocks; UI shows explicit message (“Cannot reopen a work order that has been invoiced.”).
- Double-click / retry: UI prevents duplicate submissions; if backend responds conflict/duplicate, UI treats as non-fatal and refreshes work order.

---

## 6. Functional Behavior

### Triggers
- User clicks `Reopen Work Order` from Work Order Detail.

### UI actions
- Open dialog/form with:
  - `reopenReason` textarea (required).
  - Confirm and Cancel.
- Confirm triggers submit action calling backend service.
- On success:
  - Refresh work order detail data (re-fetch).
  - Display confirmation.
  - Update any invoice eligibility indicators and snapshot section.

### State changes (frontend-visible)
- Work order remains `status=COMPLETED` (per backend clarification), but:
  - `isReopened` becomes true.
  - Invoice-ready flag becomes false (field name TBD).
  - Active `BillableScopeSnapshot` becomes `SUPERSEDED` (if exposed).
- UI must treat reopened-completed work order as **not invoiceable** and as **locked unless policy allows** (policy data exposure TBD).

### Service interactions
- Submit reopen request with:
  - workOrderId
  - reopenReason
  - userId (if backend requires; otherwise backend derives from auth context—TBD)
- Then reload:
  - work order detail
  - (optional) snapshot data and/or audit event list

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `reopenReason` is mandatory:
  - Trim whitespace; must not be empty.
  - If empty: show inline error and disable confirm.
- Only allow initiating reopen when:
  - `workOrder.status == COMPLETED`
  - user has permission `WORKORDER_REOPEN_COMPLETED`
  - invoice not issued/finalized (if frontend has that info; otherwise rely on backend error)

### Enable/disable rules
- `Reopen Work Order` button:
  - **Visible + enabled** only when eligibility conditions true.
  - **Disabled** with tooltip/reason if status not eligible or invoice already issued (if known).
- Confirm button disabled until reason passes validation and submission not in progress.

### Visibility rules
- After reopen (`isReopened=true`), show a prominent read-only indicator:
  - “Reopened: invoice eligibility revoked until re-completion.”
- Show last reopen reason and metadata if backend returns (recommended; may require audit API).

### Error messaging expectations
- 403: “Access denied: you are not authorized to reopen completed work orders.”
- 409/400 invalid state: “This work order cannot be reopened in its current state.”
- 409 invoice already issued: “Cannot reopen a work order that has been invoiced.”
- 5xx/network: “Reopen failed due to a system error. No changes were applied.” plus retry option.

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `Workorder`
- `BillableScopeSnapshot`
- `AuditEvent` (read-only display; creation done server-side)

### Fields
#### Workorder (read)
- `id` (string/number; required)
- `status` (enum string; required; must include `COMPLETED`)
- `isReopened` (boolean; required after reopen support exists)
- `invoiceReady` (boolean; **field name TBD**; required for UI gating)
- `lastReopenedAt` (datetime; optional if provided)
- `lastReopenedBy` (userId; optional if provided)
- `lastReopenReason` (string; optional if provided)

#### Reopen request (write)
- `workOrderId` (path or body; required)
- `reopenReason` (string; required)
- `userId` (number/string; **only if backend requires**; TBD)

#### BillableScopeSnapshot (read)
- `id` (required if returned)
- `status` (enum: must include `ACTIVE`, `SUPERSEDED`)
- `supersededAt` (datetime; optional)
- `supersededBy` (userId; optional)
- `supersededReason` (string; optional)

### Read-only vs editable by state/role
- This story does **not** implement editing unlocked fields; it ensures:
  - In `COMPLETED` and not reopened: fields remain read-only (no silent edits).
  - In `COMPLETED` and reopened: fields *may* become editable per policy, but **policy data contract is undefined** → keep fields read-only until a separate story provides editability policy and UI enforcement details.

### Derived/calculated fields
- `canReopen` (derived client-side): based on permission + status + (optional) invoice status indicator.
- `reopenActionAvailableReason` (string): used for disabled tooltip.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui frontend may call REST endpoints via configured service facade; exact method names/paths must match backend.

### Load/view calls
- `GET /api/workorders/{id}` (or `/api/work-orders/{id}`): load work order detail including `status`, `isReopened`, invoice status/eligibility.
- Optional:
  - `GET /api/workorders/{id}/snapshots` or dedicated endpoint to fetch active billable scope snapshot
  - `GET /api/audit-events?entityType=WORKORDER&entityId={id}` or `/api/workorders/{id}/audit`

### Submit/transition calls
- **Reopen**: `POST /api/workorders/{id}/reopen` (TBD) with body `{ reopenReason, userId? }`
  - Success: 200/201 with updated Workorder or minimal acknowledgement; frontend must refresh via GET.

### Error handling expectations
- 400: validation failures (missing reason)
- 403: permission failure
- 404: work order not found
- 409: invalid state (not completed), invoice issued/finalized, idempotency conflict/duplicate
- 5xx: server failure; frontend shows retry and does not assume state changed

---

## 10. State Model & Transitions

### Allowed states (WorkOrderStatus, per workexec reference)
- `DRAFT`
- `APPROVED`
- `ASSIGNED`
- `WORK_IN_PROGRESS`
- `AWAITING_PARTS`
- `AWAITING_APPROVAL`
- `READY_FOR_PICKUP`
- `COMPLETED`
- `CANCELLED`

### Reopen semantics (per backend clarification)
- Reopen **does not change** `Workorder.status` away from `COMPLETED`.
- Reopen sets `Workorder.isReopened = true`.
- Re-completion later sets `isReopened = false` (frontend expects; not implemented here).

### Role-based transitions
- Reopen action requires permission `WORKORDER_REOPEN_COMPLETED`.
- Frontend must not offer transition to users lacking permission.

### UI behavior per state
- If `status != COMPLETED`: no reopen action.
- If `status == COMPLETED && isReopened == false`: show reopen action if permitted.
- If `status == COMPLETED && isReopened == true`: hide reopen action (or disable with “Already reopened”) and show “invoice eligibility revoked” banner.

---

## 11. Alternate / Error Flows

### Validation failures
- Empty reason: inline error; no API call.
- Excessive length (if backend enforces): show server-provided validation message (needs max length clarification).

### Concurrency conflicts
- If another user reopens first:
  - Backend returns 409 or returns already-reopened state.
  - Frontend refreshes and displays “Work order was already reopened by <user> at <time>.”

### Unauthorized access
- If user lacks permission but UI allowed navigation:
  - Backend 403; show error; do not change UI state; keep dialog closed after acknowledging.

### Empty states
- If snapshot/audit history endpoint returns none:
  - Show “No billing snapshot history available” / “No audit history available” (read-only informational).

---

## 12. Acceptance Criteria

### Scenario 1: Authorized user successfully reopens a completed, not-invoiced work order
**Given** I am logged in as a Back Office Manager with permission `WORKORDER_REOPEN_COMPLETED`  
**And** a work order exists with `status = COMPLETED` and `isReopened = false`  
**And** the work order has no invoice in `ISSUED` or `FINALIZED` status  
**When** I click “Reopen Work Order”  
**And** I enter reopen reason “Corrected labor hours”  
**And** I confirm the reopen action  
**Then** the UI sends a reopen request including the workOrderId and reopenReason  
**And** the UI shows a success confirmation  
**And** the work order detail refresh shows `isReopened = true`  
**And** the UI indicates invoice eligibility is revoked until re-completion  
**And** the UI shows the billing snapshot (if displayed) is no longer active (status `SUPERSEDED` when available).

### Scenario 2: Reopen requires a reason (client-side validation)
**Given** I am an authorized Back Office Manager viewing a completed work order eligible for reopen  
**When** I open the “Reopen Work Order” dialog  
**And** I leave the reason blank (or only whitespace)  
**Then** the Confirm button is disabled  
**And** an inline validation message is shown indicating the reason is required  
**And** no reopen API request is sent.

### Scenario 3: Unauthorized user cannot reopen
**Given** I am logged in as a user without permission `WORKORDER_REOPEN_COMPLETED`  
**And** I am viewing a work order with `status = COMPLETED`  
**Then** the “Reopen Work Order” action is not shown (or is disabled with an access message)  
**When** I attempt to invoke the reopen endpoint (e.g., via direct request)  
**Then** the UI displays “Access denied” on a 403 response  
**And** the work order remains unchanged after refresh.

### Scenario 4: Cannot reopen if invoice is issued/finalized
**Given** I am an authorized Back Office Manager  
**And** I am viewing a work order with `status = COMPLETED`  
**And** the work order is associated to an invoice with status `ISSUED` (or `FINALIZED`)  
**When** I attempt to reopen with a valid reason  
**Then** the backend responds with an error (409 or domain error) indicating invoiced work orders cannot be reopened  
**And** the UI displays “Cannot reopen a work order that has been invoiced.”  
**And** the work order remains unchanged after refresh.

### Scenario 5: Idempotency/double-submit does not create duplicate effects
**Given** I am an authorized Back Office Manager viewing an eligible completed work order  
**When** I click Confirm twice quickly (or resubmit due to a retry) with the same reason  
**Then** the UI prevents duplicate submission while the first request is in-flight  
**And** if a duplicate request is still sent and the backend returns a conflict/duplicate indicator  
**Then** the UI treats it as non-fatal, refreshes the work order, and shows the work order as reopened  
**And** the UI does not show multiple success toasts for the same action.

---

## 13. Audit & Observability

### User-visible audit data
- After successful reopen, show (if provided by backend):
  - reopened timestamp
  - reopened by (user identifier)
  - reopen reason
- Provide a link/expand section to view audit history (if endpoint exists).

### Status history
- If transitions/snapshots endpoints exist, display in a read-only table:
  - last completion snapshot id and status (`SUPERSEDED`)
  - reopen audit event type `WORKORDER_REOPENED`

### Traceability expectations
- Frontend includes correlation/request id headers if standard in project (TBD by repo conventions).
- UI logs (console) should not include PII beyond the reason text; prefer not logging reason at all.

---

## 14. Non-Functional UI Requirements

- **Performance:** Reopen submission should complete within 2s under normal conditions; show loading state and disable inputs during submission.
- **Accessibility:** Dialog must be keyboard navigable; focus trapped within dialog; error messages announced (ARIA).
- **Responsiveness:** Works on tablet and desktop widths; dialog fits viewport with scroll for long reason.
- **i18n/timezone:** Display reopened timestamps in user’s local timezone; strings i18n-ready (no hardcoded concatenation). Currency not applicable.

---

## 15. Applied Safe Defaults

- SD-UI-EMPTY-STATE: Provide explicit empty-state text for missing snapshot/audit history; qualifies as safe because it does not change domain behavior, only improves UX clarity. (Impacted sections: UX Summary, Alternate / Error Flows)
- SD-UI-DOUBLE-SUBMIT-GUARD: Disable confirm button and show spinner during submit to prevent accidental duplicate actions; qualifies as safe because it reduces duplicate requests without affecting business rules. (Impacted sections: Functional Behavior, Acceptance Criteria)
- SD-ERR-MAP-HTTP: Map standard HTTP codes (400/403/404/409/5xx) to user-friendly messages; qualifies as safe because it follows implied backend contract and does not invent domain policy. (Impacted sections: Business Rules, Service Contracts, Alternate / Error Flows)

---

## 16. Open Questions

1. **Endpoint contract:** What is the exact backend endpoint for reopen? (`POST /api/workorders/{id}/reopen` vs another path) and does it require `userId` in body/query or derive from auth?
2. **Invoice status exposure:** Which field(s) does the work order detail endpoint expose to determine “invoice issued/finalized” or “invoice-ready”? (e.g., `invoiceStatus`, `hasIssuedInvoice`, `invoiceReady`).
3. **Reason constraints:** Is there a maximum length and/or allowed character set for `reopenReason`? Should the UI enforce max length client-side?
4. **Audit/snapshot read APIs:** Are there existing endpoints in the Moqui-integrated backend to fetch:
   - last `AuditEvent` for `WORKORDER_REOPENED`
   - active/superseded `BillableScopeSnapshot` for the work order?
5. **Post-reopen editability:** Will the backend return an explicit editability policy (editableFields/readOnlyFields) per the backend story, or is that handled separately? Until defined, should the frontend keep all fields read-only even when reopened?
6. **Idempotency signaling:** How does the backend signal a duplicate reopen attempt (409 with specific error code vs 200 returning current state)? What error code/message should frontend key off?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Completion: Reopen Completed Workorder (Controlled)  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/214  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Completion: Reopen Completed Workorder (Controlled)

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300018/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Back Office Manager

## Trigger
A completed workorder needs correction after completion.

## Main Flow
1. Authorized user selects 'Reopen' and provides a mandatory reason.
2. System records reopen audit event and transitions workorder to Reopened (or InProgress per policy).
3. System unlocks specific editable fields per policy.
4. System marks prior billable scope snapshot as superseded.
5. System requires re-completion and re-snapshot before invoicing.

## Alternate / Error Flows
- User lacks permission → block.
- Invoice already issued → block reopen or require credit/rebill workflow (out of scope).

## Business Rules
- Reopen is an exception workflow with strict permissions and audit.
- Reopening invalidates invoice-ready snapshot.

## Data Requirements
- Entities: Workorder, BillableScopeSnapshot, AuditEvent
- Fields: status, reopenReason, reopenedBy, reopenedAt, supersededSnapshotVersion

## Acceptance Criteria
- [ ] Only authorized users can reopen completed workorders.
- [ ] Reopen is auditable and requires a reason.
- [ ] Invoice-ready snapshot is invalidated and must be regenerated.
- [ ] Reopen emits a single WorkorderReopened event
- [ ] Any completion-related accounting state is reversible
- [ ] Invoice eligibility is revoked if not yet invoiced
- [ ] Reopen requires authorization and records reason
- [ ] Repeated reopen attempts do not emit duplicate events

## Integrations

### Accounting
- Emits Event: WorkorderReopened
- Event Type: Non-posting (reversal / invalidation signal)
- Source Domain: workexec
- Source Entity: Workorder
- Trigger: Authorized reopen of a completed workorder
- Idempotency Key: workorderId + reopenVersion


## Notes for Agents
Don’t allow silent edits after completion; reopen is the controlled escape hatch.


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