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
[FRONTEND] [STORY] Promotion: View Promotion Audit Trail (Estimate → Work Order)

### Primary Persona
Shop Manager / System Auditor (secondary: Service Advisor)

### Business Value
Provide a non-repudiable, easy-to-review UI record for each estimate promotion so disputes can be resolved quickly and compliance/audit needs are met (who promoted, when, what snapshot/approval, what totals were promoted).

---

## 2. Story Intent

### As a / I want / So that
- **As a** Shop Manager or System Auditor  
- **I want** to view the immutable promotion audit record created when an Estimate is promoted to a Work Order  
- **So that** I can verify exactly what was approved and promoted (snapshot + approval), who performed it, and the summary of promoted items.

### In-Scope
- Frontend screens/components to **display** promotion audit records linked to a Work Order and its source Estimate.
- Role-gated visibility of audit data.
- Empty/error states and safe navigation to related records (work order, estimate, snapshot, approval reference).

### Out-of-Scope
- Creating/writing the audit record (triggered by backend during promotion).
- Changing promotion policy (fail vs retry) or implementing the promotion action itself (unless already existing and simply needs a link).
- Editing/deleting audit events (must be immutable).

---

## 3. Actors & Stakeholders
- **Primary user:** Shop Manager / System Auditor
- **Secondary user:** Service Advisor (may review after promoting)
- **System actor:** Backend process that records `PromotionAuditEvent` atomically with promotion
- **Stakeholders:** Compliance/audit reviewers, customer dispute resolution

---

## 4. Preconditions & Dependencies
- Backend has implemented and persists an immutable `PromotionAuditEvent` (or equivalent) per successful promotion.
- There is an existing UI entry point to view a Work Order and/or Estimate.
- Authorization model exists to restrict audit visibility (roles/permissions).
- Backend endpoints exist to fetch audit records (see Open Questions if not).

Dependencies (frontend):
- Moqui screens framework present in `durion-moqui-frontend` with established routing/layout conventions (README).
- API client patterns for calling backend (REST) from Vue/Quasar already established.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Work Order detail**: “Promotion Audit” panel/tab/section (read-only).
- Optional: From **Estimate detail**: “Promotion Audit” panel if estimate has been promoted.

### Screens to create/modify
1. **Modify** `WorkOrder` detail screen (Moqui screen) to include a read-only “Promotion Audit” section.
2. **Modify** `Estimate` detail screen (Moqui screen) to include a read-only “Promotion Audit” section (only when applicable).
3. **Create** a dedicated view screen for a single promotion audit event (optional but recommended for deep-linking):
   - `component://.../screen/workexec/PromotionAudit/ViewPromotionAudit.xml` (name illustrative; match repo conventions)

### Navigation context
- Work Order → Promotion Audit (inline summary + “View details”)
- Estimate → Promotion Audit (inline summary + link to Work Order)
- Deep link: Promotion Audit Event → links to Work Order / Estimate / Snapshot

### User workflows
**Happy path**
1. User opens Work Order detail.
2. User navigates to Promotion Audit section.
3. UI displays: promoter identity, timestamp, estimate ID, work order ID, snapshot reference, approval reference, summary totals.
4. User clicks to view full audit details (if separate screen) and/or to open referenced Estimate/Work Order.

**Alternate path: no audit**
- Work order exists but has no promotion audit (e.g., created directly, migrated data). UI shows “No promotion audit recorded” with explanatory text.

**Unauthorized path**
- User without permission sees section hidden OR sees “You do not have access to promotion audit details” (see security question).

---

## 6. Functional Behavior

### Triggers
- User loads Work Order detail screen or Estimate detail screen.

### UI actions
- Load audit record(s) for the Work Order (and/or Estimate).
- Render audit summary and references.
- Provide navigation actions:
  - “Open Work Order”
  - “Open Estimate”
  - “View Snapshot” (if a snapshot view exists)
  - “View Approval” (if approval view exists)
- If multiple audit events exist (rare, but possible in data): show most recent and allow expanding list.

### State changes
- No domain state transitions initiated by this story (read-only UI).
- UI state: loading → loaded / empty / error.

### Service interactions
- Call backend to retrieve promotion audit event(s) by `workOrderId` and/or `estimateId`.
- Optional: call user/identity endpoint to display promoter name if only ID is returned (see Open Questions).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- None for user input (read-only), except:
  - Validate route params (IDs are present, numeric/UUID per system standard) before calling APIs; otherwise show 404-style “Invalid identifier”.

### Enable/disable rules
- “View Snapshot” button enabled only if `estimateSnapshotId` present.
- “View Approval” button enabled only if `approvalId` present.
- If `promotionSummary` missing, show “Summary unavailable” and still display core references.

### Visibility rules
- Promotion Audit section/details visible only to authorized roles (per backend/RBAC).
- If user is unauthorized and backend returns 403, UI must not leak partial data (show generic access denied).

### Error messaging expectations
- 404: “Promotion audit not found.”
- 403: “You do not have permission to view promotion audit details.”
- 409/500: “Unable to load promotion audit at this time. Try again.”

---

## 8. Data Requirements

### Entities involved (frontend view model)
- `PromotionAuditEvent` (or `AuditEvent` subtype) — immutable
- `WorkOrder`
- `Estimate`
- `ApprovalRecord` (reference only)
- `EstimateSnapshot` / `EstimateVersion` (reference only)
- `User` (reference for `promotingUserId` display)

### Fields (type, required, defaults)
Promotion audit fields to display:
- `auditEventId` (string/number, **required**)
- `eventType` (string, **required**, expect constant like `ESTIMATE_PROMOTED_TO_WORK_ORDER`)
- `eventTimestamp` (ISO-8601 UTC string, **required**)
- `promotingUserId` (string/number, **required**)
- `workOrderId` (string/number, **required**)
- `estimateId` (string/number, **required**)
- `estimateSnapshotId` (string/number, **required** per business rule)
- `approvalId` (string/number, **required** per business rule)
- `promotionSummary` (object/JSON, **required** for “quick review”; if absent, handle gracefully)

`promotionSummary` expected keys (if provided):
- `total_cost` / `totalCost` (number, currency-aware)
- `labor_items` / `laborItemCount` (int)
- `part_items` / `partItemCount` (int)
(Exact shape is an Open Question; UI must tolerate unknown keys.)

### Read-only vs editable
- All audit fields: **read-only** always.

### Derived/calculated fields (UI only)
- Display name for promoter: derived from `promotingUserId` via user lookup (if needed).
- Formatted timestamp in user locale; retain UTC in raw view.

---

## 9. Service Contracts (Frontend Perspective)

> Backend contracts are not fully specified in provided inputs for the frontend repo. The following are required contracts to implement the UI; if they do not exist, this story is blocked.

### Load/view calls
One of the following patterns must exist:

**Option A (preferred): Work order scoped**
- `GET /api/workorders/{workOrderId}/promotion-audit`  
  Response: either a single audit event or list.

**Option B: Generic audit lookup**
- `GET /api/audit-events?entityType=WORK_ORDER&entityId={workOrderId}&eventType=ESTIMATE_PROMOTED_TO_WORK_ORDER`

**Option C: By estimate**
- `GET /api/estimates/{estimateId}/promotion-audit`

Minimum response fields required: those listed in Data Requirements.

### Create/update calls
- None (read-only).

### Submit/transition calls
- None.

### Error handling expectations
- `200`: render audit info
- `204` or `200` empty list: render empty state (“No promotion audit recorded”)
- `401`: redirect to login / show auth-required per app convention
- `403`: show access denied (no data)
- `404`: show not found
- `5xx`: show retry UI, log client error with correlation id if provided in headers/body

---

## 10. State Model & Transitions

### Allowed states
- N/A for domain (no transitions in UI).
- UI view-state:
  - `LOADING`
  - `LOADED`
  - `EMPTY`
  - `ERROR_FORBIDDEN`
  - `ERROR_NOT_FOUND`
  - `ERROR_TRANSIENT`

### Role-based transitions
- N/A (read-only), but access gating applies:
  - Authorized → can view
  - Unauthorized → cannot view

### UI behavior per state
- LOADING: skeleton/loader
- LOADED: show audit summary + references
- EMPTY: show “No promotion audit recorded”
- ERROR_*: show appropriate message + “Back” navigation

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid route param → show invalid identifier message; do not call API.

### Concurrency conflicts
- If audit is being written concurrently (rare): if API returns not found then later exists, allow user retry. No optimistic locking required.

### Unauthorized access
- Backend 403: hide content; optionally hide entire section for non-authorized users if permission info is available client-side.

### Empty states
- Work order created without promotion: show empty state.
- Estimate not promoted: show empty state on Estimate detail.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Authorized user views promotion audit from Work Order
**Given** I am logged in as a user with permission to view promotion audit records  
**And** a Work Order exists with an associated Promotion Audit Event  
**When** I open the Work Order detail screen  
**Then** I see a “Promotion Audit” section  
**And** it displays the promoting user, event timestamp, estimateId, workOrderId  
**And** it displays the estimateSnapshotId and approvalId references  
**And** it displays promotion summary totals and item counts when provided.

### Scenario 2: Work Order has no promotion audit
**Given** I am logged in as an authorized user  
**And** a Work Order exists with no promotion audit record  
**When** I open the Work Order detail screen  
**Then** I see “No promotion audit recorded” in the Promotion Audit section  
**And** the UI does not display stale or placeholder audit identifiers.

### Scenario 3: Unauthorized user cannot view promotion audit
**Given** I am logged in as a user without permission to view promotion audit records  
**When** I open a Work Order detail screen that has a promotion audit record  
**Then** the UI either hides the Promotion Audit section or shows an access denied message  
**And** the client must not render any audit fields  
**And** if the API returns 403, the UI shows “You do not have permission to view promotion audit details.”

### Scenario 4: Backend returns error while loading audit
**Given** I am logged in as an authorized user  
**When** the Promotion Audit API request fails with a 5xx error  
**Then** I see an error state indicating the audit could not be loaded  
**And** I can retry the load  
**And** the UI remains responsive and does not break the Work Order page.

### Scenario 5: Audit references are linkable when present
**Given** I am viewing a Promotion Audit record  
**When** the record includes an `estimateId` and `workOrderId`  
**Then** I can navigate to the Estimate detail and Work Order detail from the audit section  
**And** when `estimateSnapshotId` is present, a “View Snapshot” action is enabled (destination depends on available screen/API).

---

## 13. Audit & Observability

### User-visible audit data
- Display immutable audit fields:
  - promoter (user id/name)
  - timestamp (UTC + localized display)
  - event type (human label)
  - linked entities and references
  - summary totals

### Status history
- Not created by this story; but promotion audit should be viewable as part of historical record.

### Traceability expectations
- UI should display `auditEventId` (in a details view) for support.
- If backend returns a correlation/request id header, include it in error details UI (optional) and client logs.

---

## 14. Non-Functional UI Requirements
- **Performance:** Audit section load should not block initial Work Order render; load asynchronously; target < 300ms additional perceived latency on warm path (client-side).
- **Accessibility:** All actions keyboard navigable; headings/labels for audit fields; ensure sufficient contrast; screen-reader friendly key-value layout.
- **Responsiveness:** Works on tablet and desktop (POS typical); audit section collapsible on small screens.
- **i18n/timezone/currency:** Timestamps displayed in user locale with UTC available; currency formatting for totals uses store currency configuration if available (otherwise display raw number + currency code if provided).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging when no audit record exists; safe because it does not change domain behavior and only affects presentation. Impacted sections: UX Summary, Alternate/Empty states, Acceptance Criteria.
- SD-UX-ASYNC-SECTION-LOAD: Load Promotion Audit independently from primary Work Order details to avoid blocking; safe as it’s a UI ergonomics choice. Impacted sections: Functional Behavior, Non-Functional Requirements, Error Flows.
- SD-ERR-HTTP-MAP: Standard mapping of 401/403/404/5xx to user-friendly messages; safe because it doesn’t alter domain logic and follows typical frontend handling. Impacted sections: Business Rules (error messaging), Service Contracts, Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. **Backend API contract for retrieving promotion audit:** What is the exact endpoint/path and response schema for PromotionAuditEvent retrieval (by `workOrderId` and/or `estimateId`)?  
2. **RBAC/permission name and UI gating approach:** Which roles/permissions are authorized (`SHOP_MANAGER`, `SYSTEM_AUDITOR`, etc.), and should the UI proactively hide the section or rely purely on 403?  
3. **Snapshot/approval deep-linking:** Are there existing screens/endpoints to view `estimateSnapshotId` and `approvalId` details? If not, should the UI show them as plain text only?  
4. **promotionSummary schema:** What are the canonical field names and currency handling (e.g., `total_cost` vs `totalCost`, includes tax or not)?  
5. **Multiplicity:** Can there be more than one promotion audit event per work order (e.g., re-promote after rollback/migration)? If yes, should UI show a list ordered by timestamp?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Promotion: Record Promotion Audit Trail  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/226  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Record Promotion Audit Trail

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
System

## Trigger
A promotion event completes successfully.

## Main Flow
1. System records an audit event including who initiated promotion and when.
2. System records the estimate snapshot version and approval reference used.
3. System stores a summary of items promoted (counts, totals) for quick review.
4. System links audit record to the workorder and estimate.
5. System exposes audit record in UI for authorized roles.

## Alternate / Error Flows
- Audit write fails → fail promotion or retry per strictness policy (recommended: fail).

## Business Rules
- Promotion must be auditable and traceable.
- Audit must reference the exact snapshot promoted.

## Data Requirements
- Entities: AuditEvent, Workorder, Estimate, ApprovalRecord
- Fields: eventType, actorUserId, timestamp, estimateId, snapshotVersion, approvalId, workorderId, summaryTotals

## Acceptance Criteria
- [ ] Promotion event is stored and retrievable.
- [ ] Audit record references estimate snapshot and approval.
- [ ] Audit record shows summary totals and item counts.

## Notes for Agents
Audit isn’t optional—this protects you in customer disputes.

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