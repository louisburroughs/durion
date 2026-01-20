## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Accounting Controls: Suspense Queue for Unmapped/Failed Events + Reprocess (Idempotent)

### Primary Persona
Accounting Admin (accountant with controls responsibility) / System Administrator

### Business Value
Ensures financial completeness and integrity by making posting/mapping failures visible, actionable, and safely reprocessable without duplicate postings, with full audit traceability.

---

## 2. Story Intent

### As a / I want / So that
**As an** Accounting Admin or System Administrator,  
**I want** a Suspense Queue UI to view unmapped/failed accounting events, see actionable failure details, and trigger reprocessing,  
**so that** posting failures can be resolved and re-run safely and idempotently with complete attempt history and posting traceability.

### In-scope
- A Moqui screen flow to:
  - List suspense entries with filtering/sorting
  - View suspense entry details (including immutable original event payload/reference and failure metadata)
  - Trigger reprocess for a single entry
  - View reprocess attempt history and final posting reference (if processed)
- UI behavior for idempotent reprocess conflicts (e.g., 409) and authorization failures (403)
- Display of status lifecycle (`SUSPENDED`, `PROCESSED`, optionally `FAILED`) as provided by backend

### Out-of-scope
- Editing/correcting mapping/rules in the UI (the backend story states corrections are “externally correctable”)
- Building/operating the ingestion pipeline, event broker consumer, or posting engine
- Purge/retention job UI or configuration
- Defining GL accounts, posting rule sets, or tax policies

---

## 3. Actors & Stakeholders
- **Accounting Admin (Accountant):** primary user; investigates failures, confirms resolution, triggers reprocess.
- **System Administrator:** triggers reprocess and investigates technical symptoms; may have broader access.
- **Auditor/Compliance reviewer:** reads history for traceability (view-only).
- **Accounting Posting Pipeline (backend):** source of suspense entries, idempotency and posting reference.

---

## 4. Preconditions & Dependencies
- User is authenticated in the Moqui/Quasar frontend.
- Backend provides APIs to:
  - List suspense entries
  - Retrieve suspense entry details including immutable original payload/ref
  - List attempt history for an entry
  - Trigger reprocess for an entry with idempotency guarantees
- Backend returns structured error responses and HTTP status codes (at minimum: 403, 409, 422/400, 500).
- Authorization permissions exist for viewing and reprocessing (exact permission strings not provided for frontend—see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Admin navigation: **Accounting → Controls → Suspense Queue**
- Deep link: `/accounting/controls/suspense` (list) and `/accounting/controls/suspense/{suspenseEntryId}` (detail)

### Screens to create/modify
1. **Screen:** `Accounting/Controls/SuspenseQueue.xml` (list)
2. **Screen:** `Accounting/Controls/SuspenseEntryDetail.xml` (detail + actions)
3. **Reusable section/widget (optional):** `components/AttemptHistory.xml` (renders attempt list)

### Navigation context
- Breadcrumb: Accounting / Controls / Suspense Queue / {Entry}
- From list row → detail screen
- From detail → back to list retaining query parameters (filters/sort/page)

### User workflows
#### Happy path: investigate and reprocess successfully
1. User opens Suspense Queue list
2. Filters to `status=SUSPENDED`
3. Opens an entry
4. Reviews failure reason/details and original event payload/ref
5. Clicks **Reprocess**
6. UI shows attempt started and then success; status becomes `PROCESSED`
7. UI displays `final_posting_reference_id` and new attempt history record

#### Alternate path: reprocess fails again
- Reprocess returns failure; UI remains `SUSPENDED` (or `FAILED` if backend applies policy) and shows updated latest failure details + new attempt record.

#### Alternate path: reprocess an already processed entry
- Backend returns `409 Conflict`; UI shows “Already processed” and does not duplicate anything; attempt history must **not** be incremented client-side (only reflect server truth after refresh).

---

## 6. Functional Behavior

### Triggers
- Screen load (list/detail)
- User selects filters/sorting/pagination
- User clicks **Reprocess** on detail screen

### UI actions
**List Screen**
- Render table/grid of suspense entries
- Controls:
  - Filter by `status` (default SUSPENDED)
  - Filter by `failure_reason_code`
  - Search by `event_type` and/or `final_posting_reference_id` and/or `suspense_entry_id`
  - Date range filter: `created_at` (optional but safe for UI ergonomics)
- Row action: “View”

**Detail Screen**
- Display read-only fields for the suspense entry (see Data Requirements)
- Show immutable original event payload/ref in a read-only viewer
- Show attempt history list (most recent first)
- Primary action: **Reprocess**
  - Enabled only when status is `SUSPENDED` (and optionally `FAILED` if backend permits; see Open Questions)
  - Confirmation modal: explains idempotency, that current rules will be used, and that an attempt will be logged

### State changes (frontend-observed)
- After successful reprocess: status changes to `PROCESSED`, `processed_at` populated, `final_posting_reference_id` populated, attempt history appended (server-side)
- After failed reprocess: status remains `SUSPENDED` (or becomes `FAILED`), `failure_details` may change, attempt_count increments

### Service interactions
- List: load suspense entries with query params
- Detail: load entry + attempt history (either separate calls or one composite)
- Reprocess: POST action; then refresh detail + history from server to ensure canonical truth

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Reprocess requires a valid `suspense_entry_id` in route params; if missing/invalid → show “Not found” and do not call backend.
- Reprocess action is blocked client-side when status is terminal (`PROCESSED`) to reduce erroneous calls; still must handle server 409 (source of truth).

### Enable/disable rules
- **Reprocess button enabled** when:
  - user has reprocess permission (if permission info available in session context; otherwise rely on backend 403)
  - entry status == `SUSPENDED` (and optionally `FAILED` if permitted)
- **Reprocess button disabled** when:
  - entry status == `PROCESSED`
  - a reprocess request is currently in-flight (prevent double-submit)

### Visibility rules
- Show `failure_reason_code` and `failure_details` prominently in detail.
- Show `final_posting_reference_id` only when present; otherwise show “Not yet processed”.
- Attempt history section visible even if empty (show “No attempts yet”).

### Error messaging expectations
Map backend outcomes to user-visible messages:
- `403`: “You are not authorized to reprocess suspense entries.”
- `404`: “Suspense entry not found or no longer available.”
- `409`: “This entry is already processed; reprocess is blocked to prevent duplicate postings.”
- `422/400`: “Cannot reprocess due to validation error: <backend message>”
- `500/503`: “Reprocess failed due to system error; try again later.”

---

## 8. Data Requirements

### Entities involved (frontend read models)
(Backend-owned; frontend consumes via services)
- **SuspenseEntry**
- **ReprocessingAttemptHistory** (attempt records)

### Fields

#### SuspenseEntry (read-only in UI)
- `suspense_entry_id` (UUID, required, displayed)
- `status` (enum: `SUSPENDED`, `PROCESSED`, optionally `FAILED`; required)
- `event_type` (string, required)
- `failure_reason_code` (string/enum, required when SUSPENDED/FAILED)
- `failure_details` (string, optional but expected; display as multiline)
- `mapping_version_attempted` (string, nullable)
- `attempt_count` (int, required)
- `created_at` (datetime, required)
- `updated_at` (datetime, required)
- `processed_at` (datetime, nullable)
- `resolved_by_user_id` (string/UUID, nullable; display if provided)
- `final_posting_reference_id` (string, nullable)
- `original_event_payload` (JSON/text) **OR** `original_event_ref` (string) (one required)
  - UI must support either shape:
    - If payload is provided: display in JSON viewer
    - If ref is provided: display as immutable reference identifier

#### ReprocessingAttemptHistory (read-only list)
- `attempt_id` (string/UUID, required)
- `suspense_entry_id` (UUID, required)
- `attempted_at` (datetime, required)
- `triggered_by_user_id` (string/UUID, required)
- `outcome` (`SUCCESS` | `FAILURE`, required)
- `outcome_details` (string, nullable)

### Read-only vs editable by state/role
- All fields are read-only; the only mutation is the **Reprocess** command.

### Derived/calculated fields (UI-only)
- `statusBadge` derived from `status`
- `canReprocess` derived from `status` + permission + in-flight flag

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact endpoint names are not provided in inputs. The frontend must integrate with Moqui services; final naming depends on backend API. Below are required capabilities and suggested Moqui service names as placeholders.

### Load/view calls
1. **List suspense entries**
   - Capability: search/filter/paginate suspense entries
   - Suggested Moqui service: `accounting.suspense.SuspenseEntryFind`
   - Inputs (query params / service in-params):
     - `status` (optional; default `SUSPENDED`)
     - `failureReasonCode` (optional)
     - `eventType` (optional)
     - `createdFrom` / `createdThru` (optional)
     - `pageIndex`, `pageSize`, `orderByField` (optional)
   - Output:
     - `entries[]` (with key list fields)
     - `totalCount`

2. **Get suspense entry detail**
   - Suggested service: `accounting.suspense.SuspenseEntryGet`
   - Input: `suspenseEntryId`
   - Output: SuspenseEntry full record

3. **Get attempt history**
   - Suggested service: `accounting.suspense.SuspenseAttemptFindByEntry`
   - Input: `suspenseEntryId`
   - Output: `attempts[]`

### Submit/transition calls
4. **Trigger reprocess**
   - Suggested service: `accounting.suspense.SuspenseEntryReprocess`
   - Input:
     - `suspenseEntryId` (required)
     - (optional) `force` (NOT allowed as safe default; must be explicit if exists—do not implement unless backend defines)
   - Output:
     - Return updated SuspenseEntry OR just success indicator; frontend must refresh afterward.

### Error handling expectations
- Backend uses HTTP status semantics as described in backend story:
  - 409 conflict for terminal reprocess
  - 403 forbidden for unauthorized
- Frontend must:
  - Display error banner/toast
  - Preserve user context (stay on page)
  - Offer retry where appropriate (non-409)

---

## 10. State Model & Transitions

### Allowed states (from backend story)
- `SUSPENDED` (actionable)
- `PROCESSED` (terminal)
- `FAILED` (optional terminal or semi-terminal per policy; not fully specified)

### Role-based transitions (frontend enforcement)
- Users with reprocess permission may request:
  - `SUSPENDED` → (reprocess) → `PROCESSED` on success
  - `SUSPENDED` → (reprocess) → `SUSPENDED` (or `FAILED`) on failure
- `PROCESSED` → (reprocess) is **disallowed**; expect `409 Conflict`

### UI behavior per state
- `SUSPENDED`:
  - show failure info
  - Reprocess enabled
- `PROCESSED`:
  - show final posting reference and processed timestamp
  - Reprocess disabled; show info note about idempotency protection
- `FAILED` (if present):
  - show failure info
  - Reprocess behavior requires clarification (see Open Questions)

---

## 11. Alternate / Error Flows

### Validation failures
- Missing/invalid `suspenseEntryId` in route:
  - show 404-style not found state; do not show Reprocess
- Backend returns 400/422 on reprocess:
  - show message from backend; keep status as last loaded; offer retry

### Concurrency conflicts
- If two admins attempt reprocess simultaneously:
  - One succeeds → entry becomes `PROCESSED`
  - Other receives 409 or sees updated status on refresh
  - Frontend must refresh detail after any reprocess attempt completion and handle 409 gracefully

### Unauthorized access
- If user can view list but cannot reprocess:
  - Reprocess button hidden or disabled (if permission known)
  - If not known, backend 403 triggers error banner and button remains disabled until refresh

### Empty states
- List returns no entries:
  - show “No suspense entries found for current filters.”
- Attempt history empty:
  - show “No attempts yet.”

---

## 12. Acceptance Criteria

### Scenario 1: View Suspense Queue list filtered to suspended
**Given** I am an authenticated user with access to Accounting Controls  
**When** I open the Suspense Queue screen  
**Then** I see a list of suspense entries filtered to `status = SUSPENDED` by default  
**And** each row shows `suspense_entry_id`, `event_type`, `failure_reason_code`, `attempt_count`, and `created_at`.

### Scenario 2: View suspense entry details including immutable original payload/ref
**Given** a suspense entry exists with status `SUSPENDED`  
**When** I open the entry detail screen  
**Then** I can view `failure_reason_code` and `failure_details`  
**And** I can view the immutable `original_event_payload` as read-only JSON **or** `original_event_ref` as read-only reference  
**And** I can view the reprocess attempt history for that entry.

### Scenario 3: Successful reprocess updates status and retains posting reference
**Given** a suspense entry is `SUSPENDED` and backend rules/mappings have been corrected externally  
**When** I click Reprocess and confirm  
**Then** the system triggers reprocessing  
**And** after completion the entry status is `PROCESSED`  
**And** `final_posting_reference_id` is displayed  
**And** a new attempt history record exists with outcome `SUCCESS` and my user as actor.

### Scenario 4: Reprocess failure retains suspense status and logs attempt
**Given** a suspense entry is `SUSPENDED`  
**When** I click Reprocess and backend returns a failure outcome  
**Then** the entry remains `SUSPENDED` (or is `FAILED` if backend applies such policy)  
**And** I see an error message describing the failure  
**And** attempt_count increases compared to before  
**And** attempt history includes a new record with outcome `FAILURE`.

### Scenario 5: Idempotency protection prevents reprocessing processed entry
**Given** a suspense entry is `PROCESSED`  
**When** I attempt to reprocess it (via UI or direct call)  
**Then** the UI prevents the action or the backend responds with `409 Conflict`  
**And** no duplicate posting is created  
**And** the UI shows a message that the entry is already processed.

### Scenario 6: Unauthorized user cannot reprocess
**Given** I do not have permission to reprocess suspense entries  
**When** I click Reprocess  
**Then** the backend responds with `403 Forbidden`  
**And** the UI shows an authorization error  
**And** the entry remains unchanged.

---

## 13. Audit & Observability

### User-visible audit data
- Display attempt history (who/when/outcome/details)
- Display `processed_at` and `resolved_by_user_id` (if provided)
- Display `final_posting_reference_id` for traceability

### Status history
- Attempt history acts as operational history; if backend exposes status-change audit events, show them (not required by inputs)

### Traceability expectations
- UI must preserve and display identifiers:
  - `suspense_entry_id`
  - `final_posting_reference_id`
  - (if present) event identifiers within `original_event_payload` (e.g., `eventId`, `eventType`, `schemaVersion`) as read-only

---

## 14. Non-Functional UI Requirements
- **Performance:** List loads must support pagination to avoid rendering extremely large datasets.
- **Accessibility:** All actions accessible via keyboard; confirmation dialog must trap focus; status conveyed with text (not color alone).
- **Responsiveness:** Works on standard desktop widths; list supports horizontal scroll for wide IDs/payload refs.
- **i18n/timezone:** Display datetimes in user’s locale/timezone as configured by Moqui; do not assume currency formatting (this UI is mostly identifiers and timestamps).

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Provide explicit empty-state messaging for list and history; safe because it changes only presentation, not domain logic. Impacted sections: UX Summary, Alternate/waswo Error Flows.
- SD-UI-PAGINATION: Paginate list results with configurable page size; safe because it’s UI ergonomics and does not alter backend behavior. Impacted sections: UX Summary, Service Contracts, Non-Functional.
- SD-UI-DOUBLE-SUBMIT-GUARD: Disable Reprocess while request is in-flight; safe because it prevents accidental duplicate user actions without changing backend idempotency semantics. Impacted sections: Business Rules, Alternate/Error Flows.

---

## 16. Open Questions
1. What are the exact backend endpoints or Moqui service names/paths for:
   - suspense entry list, detail, attempt history, and reprocess?
2. What permission(s)/scope(s) should the frontend check to:
   - view suspense queue
   - reprocess entries
   (If not available client-side, should we hide/disable actions purely based on backend 403?)
3. Is `FAILED` a real status exposed to the UI? If yes:
   - Is reprocess allowed from `FAILED`, or is it terminal like `PROCESSED`?
4. For `original_event_payload`:
   - Will backend always return JSON (structured) or sometimes a string blob?
   - Are there size limits requiring truncation/download behavior?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Controls: Route Unmapped or Failed Events to Suspense Queue and Reprocess  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/186  
Labels: frontend, story-implementation, admin

## Frontend Implementation for Story

**Original Story**: [STORY] Controls: Route Unmapped or Failed Events to Suspense Queue and Reprocess

**Domain**: admin

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Reconciliation, Audit, and Controls

## Story
Controls: Route Unmapped or Failed Events to Suspense Queue and Reprocess

## Acceptance Criteria
- [ ] Unmapped/failed events go to Suspense with actionable missing-key details
- [ ] Admin can correct mapping/rules and reprocess
- [ ] Reprocess is idempotent (no duplicate postings)
- [ ] Attempt history and final posting references are retained


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