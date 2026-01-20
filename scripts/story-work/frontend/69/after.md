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
[FRONTEND] [STORY] Accounting: Display & Reconcile Authoritative Accounting Status on Invoice Detail (Pending/Posted + Drilldown + Audit)

## Primary Persona
Cashier (secondary: Service Advisor, Manager/Supervisor, Accounting)

## Business Value
Reduce customer disputes and internal confusion by showing the **authoritative accounting posting status** (pending vs posted, etc.) alongside POS operational status, with traceable references and auditable reconciliation history.

---

# 2. Story Intent

## As a / I want / So that
- **As a Cashier**, I want the invoice screen to display accounting’s authoritative status (pending posting vs posted, etc.) so that I can confidently answer customer questions and resolve disputes without leaving POS.

## In-scope
- Display accounting status badge/section on an **Invoice Detail** screen.
- Show **status timestamp**, **staleness indicator**, and **manual refresh** action (permission-gated).
- Show **drilldown reference(s)** and “View in Accounting” deep link (permission-gated).
- Show **discrepancy banner** when POS local status conflicts with accounting status.
- Show **status sync/audit history** related to accounting reconciliation (read-only UI).
- Moqui screen actions to load/update (refresh) accounting status via service calls, and to log “viewed” audit events.

## Out-of-scope
- Implementing the event consumer / synchronizer (backend service) that ingests `InvoiceStatusChanged` / `PostingConfirmed` events.
- Changing invoice lifecycle or posting logic; this story is **display + refresh + audit UI**.
- Defining GL accounts, journal entry lines, or accounting posting rules.
- SSO mechanics beyond opening a provided deep link URL.

---

# 3. Actors & Stakeholders

- **Cashier**: Needs quick authoritative status, minimal details, can refresh if stale (if permitted).
- **Service Advisor**: Similar needs for follow-up/collections.
- **Manager/Supervisor**: Can view drilldown details; may handle exceptions.
- **Accounting Role**: Full drilldown details; investigates discrepancies.
- **Support/Audit/Compliance**: Requires retained audit trail and traceability.

---

# 4. Preconditions & Dependencies

## Preconditions
- An invoice exists and can be opened in POS.
- The invoice has (or can derive) a POS operational/local status (e.g., PAYMENT_RECEIVED).
- Accounting status data is available either:
  - already synchronized into a local read model, and/or
  - obtainable via a refresh/poll service call.

## Dependencies (blocking for implementation detail)
- Backend endpoints/services in Moqui for:
  - loading invoice + accounting status (read model)
  - triggering refresh/poll (manual refresh)
  - retrieving audit log entries for invoice accounting status sync
  - permission checks/authorization signals available to frontend

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Invoice search/list → select invoice → **Invoice Detail**.
- (Optional) From payment receipt screen → “View invoice” → Invoice Detail.

## Screens to create/modify
- **Modify**: `InvoiceDetail` screen (existing) to add an “Accounting Status” section.
- **Create (if missing)**: `InvoiceAccountingStatusHistory` sub-screen (embedded or dialog) to list audit events for this invoice.

> Moqui implementation expectation: update a screen under an application like `apps/pos/screen/.../InvoiceDetail.xml` (exact path TBD by repo conventions).

## Navigation context
- Within POS invoice context; must preserve invoiceId in URL parameters.
- Accounting drilldown opens a new tab/window.

## User workflows

### Happy path: view posted
1. User opens invoice detail.
2. UI loads invoice and accounting status.
3. UI shows badge: “Posted to Accounting”, updated timestamp, and (if permitted) drilldown link.

### Alternate: pending posting
1. Badge shows “Pending Posting”, updated timestamp.
2. If stale threshold exceeded, show “May be outdated” indicator and Refresh button (if permitted).

### Alternate: discrepancy / rejected
1. If accounting status indicates rejection/hold/reversal/etc., show warning banner with reason (if provided).
2. Show both POS status and Accounting status side-by-side (textual comparison).

### Alternate: permission-limited user
1. If no permission to view accounting status: hide entire section; optionally show restricted placeholder per policy (needs clarification).

---

# 6. Functional Behavior

## Triggers
- Screen load for Invoice Detail with `invoiceId`.
- User clicks “Refresh Status” (permission-gated).
- User clicks “View in Accounting” (permission-gated).
- User opens “Status History” (embedded panel or dialog).

## UI actions
- Render accounting status badge and details:
  - `accountingStatus` enum mapped to user-friendly labels
  - last updated timestamp
  - optional reason text for exceptional statuses
- Render discrepancy banner when `discrepancyDetected=true` OR when computed mismatch conditions apply (see Open Questions).
- Render staleness indicator when `now - accountingStatusUpdatedAt > stalenessThreshold`.
- Manual refresh:
  - invoke refresh service; update UI with new status; record audit “MANUAL_REFRESH” source (if backend supports).
- Drilldown:
  - open `drilldownUrl` (preferred) else construct from refs (blocked:clarification—do not guess URL patterns).
- Audit/history:
  - list audit entries with sync source, old/new status, timestamps, eventId (if permitted).

## State changes (frontend-visible)
- No direct domain state machine transitions owned by frontend.
- UI reflects updated fields after refresh:
  - `accountingStatus`, `accountingStatusUpdatedAt`, `postingReference`, `lastSyncEvent`, `discrepancyDetected`, `discrepancyReason`.

## Service interactions
- `loadInvoiceAccountingStatus` (read)
- `refreshInvoiceAccountingStatus` (command/poll)
- `listInvoiceAccountingStatusAudit` (read)
- `logInvoiceAccountingStatusViewed` (audit write) — optional; needs clarification whether backend wants explicit “view” logging.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- On refresh action:
  - require `invoiceId` present.
  - require user permission `REFRESH_ACCOUNTING_STATUS`.
- For drilldown:
  - require permission `VIEW_ACCOUNTING_DETAIL`.
  - require `drilldownUrl` or required postingReference fields if URL is constructed (construction is blocked).

## Enable/disable rules
- “Refresh Status” button:
  - visible+enabled only if user has `REFRESH_ACCOUNTING_STATUS`.
  - disabled with spinner while refresh is in-flight.
- “View in Accounting” link/button:
  - visible only if user has `VIEW_ACCOUNTING_DETAIL` **and** drilldown reference exists.
- “Accounting Status” section:
  - visible only if user has `VIEW_ACCOUNTING_STATUS` (per backend story).
  - If user lacks permission: hide entire section (per backend story); whether to show placeholder text is an open question.

## Visibility rules (status-driven)
- If `accountingStatus` in `{REJECTED, REVERSED, VOIDED, ON_HOLD, DISPUTED}`:
  - show a prominent banner (severity depends on status) including `reason`/`discrepancyReason` if provided.
- If staleness detected:
  - show staleness indicator text “Last synced X ago” (relative time) and refresh CTA if permitted.
- If out-of-order/duplicate handling occurs, UI does not need to expose internals; audit history may reflect latest status only.

## Error messaging expectations
- Refresh failures must show actionable, non-technical messages:
  - “Accounting status unavailable. Try again later.”
- Authorization failures:
  - “You don’t have permission to refresh accounting status.”
- Missing status:
  - “Accounting status not available yet” (only if backend indicates no status exists; do not infer).

---

# 8. Data Requirements

## Entities involved (frontend-facing read models)
- Invoice (existing POS invoice entity/read model; includes `posLocalStatus`)
- InvoiceAccountingStatus (read model described in backend story)
- AccountingStatusSyncAuditLog (audit list per invoice)

## Fields (type, required, defaults)

### InvoiceAccountingStatus (read)
- `invoiceId` (UUID, required)
- `posLocalStatus` (enum/string, required for comparison display)
- `accountingStatus` (enum, optional if not yet received)
- `accountingStatusUpdatedAt` (timestamp, optional)
- `postingReference.glEntryId` (UUID, optional; sensitive)
- `postingReference.batchId` (string, optional; sensitive)
- `postingReference.postingDate` (date, optional)
- `postingReference.drilldownUrl` (string/url, optional; sensitive)
- `lastSyncEvent` (UUID, optional)
- `discrepancyDetected` (boolean, default false if absent)
- `discrepancyReason` (string, optional)

### AccountingStatusSyncAuditLog (read list)
- `auditId` (UUID)
- `invoiceId` (UUID)
- `eventId` (UUID, optional)
- `oldStatus` (string/enum)
- `newStatus` (string/enum)
- `syncedAt` (timestamp)
- `discrepancyDetected` (boolean)
- `viewedByUserId` (UUID, optional)
- `metadata.syncSource` (enum EVENT|POLLING|MANUAL_REFRESH)
- `metadata.latencyMs` (integer, optional)

## Read-only vs editable
- All fields are **read-only** in UI.
- Only action is “Refresh” (command) which may update backend state; UI never edits accountingStatus fields directly.

## Derived/calculated fields (UI-only)
- `isStale = accountingStatusUpdatedAt && (now - accountingStatusUpdatedAt > threshold)`
- `relativeUpdatedAt` string for display
- `statusLabel` mapping per enum
- `statusSeverity` mapping per enum (info/warn/error)

---

# 9. Service Contracts (Frontend Perspective)

> Names are placeholders until confirmed by Moqui service naming conventions in this repo.

## Load/view calls
1. **Load invoice + accounting status**
   - Service: `pos.invoice.getInvoiceAccountingStatus`
   - Inputs: `invoiceId`
   - Outputs: Invoice + InvoiceAccountingStatus composite (or two calls)
   - Errors:
     - 404 if invoice not found
     - 403 if no VIEW permission (behavior needs clarification: hide vs error)

2. **List audit history**
   - Service: `accounting.invoice.listAccountingStatusSyncAudit`
   - Inputs: `invoiceId`, pagination params
   - Outputs: list of audit log entries
   - Errors: 403 if restricted

## Create/update calls (commands)
3. **Manual refresh**
   - Service: `accounting.invoice.refreshAccountingStatus`
   - Inputs: `invoiceId`
   - Outputs: updated InvoiceAccountingStatus
   - Errors:
     - 403 unauthorized
     - 409 conflict (if invoice in invalid state?) (only if backend defines)
     - 503/504 upstream accounting unavailable (map to user-friendly message)

4. **Log “viewed” audit event (optional)**
   - Service: `accounting.invoice.logAccountingStatusViewed`
   - Inputs: `invoiceId`, maybe `viewContext`
   - Outputs: success
   - Errors: non-blocking for UX; should not prevent screen render.

## Error handling expectations
- Standard Moqui error responses should be surfaced:
  - field errors inline if any (unlikely)
  - toast/banner for service failures
- Correlation ID (if provided in response headers/body) should be logged client-side per repo conventions (needs confirmation).

---

# 10. State Model & Transitions

## Allowed states (Accounting status enum, v1)
- `PENDING_POSTING`
- `POSTED`
- `RECONCILED`
- `REJECTED`
- `REVERSED`
- `VOIDED`
- `ON_HOLD`
- `DISPUTED`

## Role-based transitions (UI)
- UI does not initiate status transitions; it only:
  - refreshes (polls) current status if permitted
  - views detail if permitted

## UI behavior per state
- `PENDING_POSTING`: yellow/neutral badge; show staleness if applicable.
- `POSTED`: green badge; show posting refs if permitted.
- `RECONCILED`: green badge; show posting refs if permitted.
- `REJECTED`: error badge/banner; show reason if present; encourage escalation.
- `REVERSED`: warning/error banner; show that prior posting was reversed.
- `VOIDED`: terminal indicator; banner; drilldown if permitted.
- `ON_HOLD`: warning banner; show reason if present.
- `DISPUTED`: warning banner.

Backward progression detection:
- If backend exposes “out-of-order ignored” audit entries, UI may show in history only; otherwise no special UI.

---

# 11. Alternate / Error Flows

## Validation failures
- Refresh clicked without permission → show permission error; do not call service.
- Drilldown clicked without URL/ref → show “Details not available” and do nothing.

## Concurrency conflicts
- If refresh returns conflict due to concurrent update:
  - UI reloads status (re-fetch) and shows “Status updated elsewhere; showing latest.”

## Unauthorized access
- No `VIEW_ACCOUNTING_STATUS`:
  - Hide accounting section (per backend story).
  - Log attempt only if backend supports; UI should not leak existence of restricted fields.

## Empty states
- No accounting status yet:
  - Display “Not yet received from accounting” (only if backend returns explicit null/empty), and show refresh button if permitted.
- No audit logs:
  - Display “No synchronization history available.”

## Upstream unavailable
- Refresh fails due to accounting API outage:
  - keep current status; show error banner and allow retry.

---

# 12. Acceptance Criteria

## Scenario 1: Display authoritative status badge (posted)
**Given** an invoice exists with POS local status `PAYMENT_RECEIVED`  
**And** the invoice has `accountingStatus = POSTED` and `accountingStatusUpdatedAt` populated  
**When** a user with `VIEW_ACCOUNTING_STATUS` opens the Invoice Detail screen  
**Then** the UI displays an Accounting Status badge labeled “Posted” (or “Posted to Accounting”)  
**And** the UI displays the last updated timestamp for the accounting status  
**And** the POS local status remains visible but is visually secondary to the accounting status.

## Scenario 2: Pending vs posted are clearly distinguishable
**Given** an invoice has `accountingStatus = PENDING_POSTING`  
**When** a Cashier opens Invoice Detail  
**Then** the badge indicates “Pending Posting” with a non-success severity  
**And** the “last updated” timestamp is displayed.  

**Given** the same invoice later has `accountingStatus = POSTED`  
**When** the Cashier refreshes the screen or triggers refresh (if permitted)  
**Then** the badge updates to “Posted” with success severity.

## Scenario 3: Permission-gated drilldown visibility
**Given** an invoice has `postingReference.drilldownUrl` present  
**And** the user has `VIEW_ACCOUNTING_DETAIL`  
**When** the user views the invoice  
**Then** the UI shows a “View in Accounting” action  
**And** activating it opens the drilldown URL in a new tab/window.

**Given** the user lacks `VIEW_ACCOUNTING_DETAIL`  
**When** the user views the invoice  
**Then** the UI does not display GL identifiers (`glEntryId`, `batchId`) nor the drilldown action  
**And** the accounting badge and timestamp remain visible (if `VIEW_ACCOUNTING_STATUS` is granted).

## Scenario 4: Discrepancy banner when accounting is exceptional
**Given** an invoice has `accountingStatus = REJECTED`  
**And** `discrepancyReason` is “Invoice total mismatch”  
**When** a user with `VIEW_ACCOUNTING_STATUS` opens Invoice Detail  
**Then** the UI shows a warning/error banner indicating accounting rejected the invoice  
**And** the banner includes the reason text “Invoice total mismatch”  
**And** both POS local status and accounting status are shown for comparison.

## Scenario 5: Staleness indicator and refresh action
**Given** an invoice has `accountingStatusUpdatedAt` older than the configured staleness threshold  
**When** a user with `VIEW_ACCOUNTING_STATUS` views the invoice  
**Then** the UI displays “Accounting status may be outdated” and the relative last sync time.  

**Given** the user has `REFRESH_ACCOUNTING_STATUS`  
**When** the user clicks “Refresh Status”  
**Then** the UI calls the refresh service with `invoiceId`  
**And** on success the UI updates the badge/status fields without requiring a full page reload  
**And** the refresh action is recorded in audit history as `MANUAL_REFRESH` (if backend provides).

## Scenario 6: Restricted users do not see accounting section
**Given** a user lacks `VIEW_ACCOUNTING_STATUS`  
**When** the user opens Invoice Detail  
**Then** the Accounting Status section is not displayed  
**And** the UI does not display posting references or audit history links.

## Scenario 7: Refresh failure handling
**Given** a user has `REFRESH_ACCOUNTING_STATUS`  
**When** the user clicks “Refresh Status” and the service returns an upstream unavailable error  
**Then** the UI keeps the current displayed status unchanged  
**And** shows an error message “Accounting status unavailable. Try again later.”  
**And** the user can attempt refresh again.

---

# 13. Audit & Observability

## User-visible audit data
- A “Status History” view shows rows with:
  - syncedAt, oldStatus, newStatus, syncSource, (optional) eventId
  - discrepancyDetected indicator
- Visibility of this history should follow `VIEW_ACCOUNTING_STATUS` at minimum; finer control is an open question.

## Status history
- Must be ordered descending by `syncedAt`.
- Must support pagination for long retention.

## Traceability expectations
- UI should display `lastSyncEvent` (eventId) **only** to users with `VIEW_ACCOUNTING_DETAIL` (or separate permission, open question).
- When a user triggers refresh or clicks drilldown, emit frontend logs (console/logger) with `invoiceId` and action outcome per repo conventions (no PII).

---

# 14. Non-Functional UI Requirements

- **Performance**: Invoice detail load should not be meaningfully degraded; accounting status load should be within typical POS SLA (target p95 < 1s for cached read; refresh is user-initiated).
- **Accessibility**: Status indicators must not rely on color alone; include text labels and ARIA-friendly semantics.
- **Responsiveness**: Works on tablet/desktop POS layouts; accounting section collapsible if space constrained.
- **i18n/timezone/currency**:
  - Timestamps displayed in store/user timezone (needs repo convention confirmation).
  - Status labels translatable via existing i18n mechanism.

---

# 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE-01: Show explicit empty states (“Not yet received”, “No history available”) for missing status/history; safe because it’s UX-only and does not alter domain policy. Impacted sections: UX Summary, Error Flows, Acceptance Criteria.
- SD-UX-PAGINATION-01: Paginate audit history list with a conservative default page size (e.g., 25) and next/prev controls; safe because it’s a presentation concern. Impacted sections: Data Requirements, Audit & Observability.
- SD-ERR-MAP-01: Map 401/403 to permission messaging, 404 to “not found”, 5xx to “temporarily unavailable”; safe because it’s standard error surfacing without changing business rules. Impacted sections: Service Contracts, Error Flows.

---

# 16. Open Questions

1. **Moqui screen locations & existing invoice detail screen ID**: What is the exact Moqui screen path/name to extend for Invoice Detail in `durion-moqui-frontend`?
2. **Service names and endpoints**: What are the actual Moqui services (or REST endpoints) for:
   - loading invoice accounting status,
   - triggering manual refresh/poll,
   - listing audit history,
   - logging “viewed” events (if required)?
3. **Deep link construction**: Will backend provide `postingReference.drilldownUrl` fully formed, or must frontend construct it? If constructed, what is the canonical base URL/pattern and required identifiers?
4. **Permission source**: How does frontend obtain permission flags in this project (session user groups, Moqui artifacts authz, explicit permission service, etc.)? Are the three permissions (`VIEW_ACCOUNTING_STATUS`, `REFRESH_ACCOUNTING_STATUS`, `VIEW_ACCOUNTING_DETAIL`) already modeled?
5. **Staleness threshold**: Is the 1-hour staleness threshold configurable and provided to frontend (config/entity/service), or should frontend use a constant? (Cannot assume for denylist policy/threshold.)
6. **Discrepancy logic**: Should discrepancy display rely only on backend-provided `discrepancyDetected/discrepancyReason`, or should frontend compute mismatch between POS local status and accounting status? If computed, what is the exact rule mapping?
7. **Audit history access control**: Should viewing the audit history require only `VIEW_ACCOUNTING_STATUS`, or a separate permission?
8. **Restricted users UX**: When user lacks `VIEW_ACCOUNTING_STATUS`, should UI (a) hide silently, or (b) show a restricted placeholder text? Backend story suggests hide; confirm desired behavior.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Accounting: Reconcile POS Status with Accounting Authoritative Status  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/69  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Reconcile POS Status with Accounting Authoritative Status

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative  
As a **Cashier**, I want POS to reflect accounting’s authoritative status so that disputes are minimized.

## Details  
- Show “pending posting” vs “posted.”  
- Provide drilldown refs.

## Acceptance Criteria  
- Accounting status overrides local state.  
- Pending/posted states clear.  
- Audit of reconciliation.

## Integrations  
- Accounting emits InvoiceStatusChanged/PostingConfirmed events.

## Data / Entities  
- PostingConfirmation, InvoiceStatusChangedEvent, AuditLog

## Classification (confirm labels)  
- Type: Story  
- Layer: Experience  
- domain :  Point of Sale

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