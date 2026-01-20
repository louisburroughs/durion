## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:people
- status:draft

### Recommended
- agent:people-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** crm-pragmatic

---

## 1. Story Header

### Title
[FRONTEND] [STORY] People/HR: View ingested Timekeeping Entries from ShopMgr work sessions (payroll readiness + approval visibility)

### Primary Persona
Payroll Clerk

### Business Value
Payroll Clerks can reliably see finalized work sessions ingested from Shop Management as HR-owned Timekeeping Entries with approval status, enabling payroll preparation and compliance reporting without duplicate entries or unclear ingestion status.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Payroll Clerk  
- **I want** a People/HR UI to view timekeeping entries that were ingested from ShopMgr work sessions, including their approval status and source identifiers  
- **So that** I can prepare payroll and audit compliance with confidence that sessions are complete, deduplicated, and tracked.

### In-scope
- New People-domain UI screens to:
  - List `TimekeepingEntry` records (including filter/search)
  - View a `TimekeepingEntry` detail page with source metadata (tenant/source session identifiers) and approval status
- UI handling of idempotency/duplicate semantics **as visible results** (i.e., duplicates do not appear twice; UI can display unique keys/source IDs)
- UI error handling for unauthorized and load failures
- Read-only visibility for payroll review (approval actions may be linked but not implemented unless already supported)

### Out-of-scope
- Implementing ingestion itself (event subscriber, DLQ handling, mapping) — backend concern
- HR → ShopMgr “approval feedback” updates
- Editing or correcting TimekeepingEntries (unless an existing backend UI flow already exists; if it exists, only link to it)

---

## 3. Actors & Stakeholders
- **Primary actor:** Payroll Clerk
- **Secondary actors:** Compliance Officer (read-only auditor), HR Administrator (may troubleshoot)
- **Upstream producer (not an interactive actor in UI):** Shop Management / WorkExec event producer (`WorkSessionCompleted`)
- **Systems involved:** Moqui frontend + People backend services/entities

---

## 4. Preconditions & Dependencies
- Backend has People-domain `TimekeepingEntry` persisted from ShopMgr `WorkSessionCompleted` events with:
  - Idempotency enforced by `(tenantId, sourceSystem, sourceSessionId)` (or equivalent)
  - Default `approvalStatus = PENDING_APPROVAL` for newly ingested entries
- Backend provides a **read/list** API or Moqui service accessible to the frontend to:
  - Query timekeeping entries with paging/filtering
  - Retrieve a single entry by ID (or by unique key)
- Authentication is in place; authorization distinguishes payroll clerks from unauthorized users.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **People → Timekeeping → Ingested Sessions** (label can vary; must be discoverable under People/HR)
- Deep link route to detail: from list row click

### Screens to create/modify
1. **New Screen:** `apps/people/screen/timekeeping/TimekeepingEntryList.xml`
2. **New Screen:** `apps/people/screen/timekeeping/TimekeepingEntryDetail.xml`
3. **Modify Navigation/Menu:** People app menu to include the list screen entry

### Navigation context
- From People module home, user can open “Ingested Sessions” list.
- From list, user can open a detail view for a specific entry.
- Provide back navigation to list preserving filters (Moqui screen parameters).

### User workflows
**Happy path:**
1. Payroll Clerk opens Ingested Sessions list.
2. Sees paginated list of Timekeeping Entries.
3. Filters by employee, date range, approval status, and (optionally) location/work order.
4. Opens an entry to view details: session start/end, source system + session id, and approval status.

**Alternate paths:**
- No records: show empty state with guidance (e.g., “No ingested sessions found for selected filters.”)
- Unauthorized: show access denied screen/message.
- Backend error: show error banner with retry.

---

## 6. Functional Behavior

### Triggers
- User navigates to list screen.
- User changes filters or paging.
- User opens a detail screen.

### UI actions
- List screen:
  - Apply filters (without page reload if supported; otherwise submit form)
  - Click row to navigate to detail
  - Refresh action to reload results
- Detail screen:
  - Display key fields and audit/source identifiers
  - Provide link back to list
  - (Optional) If an approval workflow exists elsewhere, show a contextual link “Go to Approval” (not implemented here)

### State changes
- None in this story (read-only UI). No modifications to `TimekeepingEntry` are performed.

### Service interactions
- List: call People service to retrieve paged `TimekeepingEntry` results based on filters.
- Detail: call People service to retrieve a single `TimekeepingEntry` by ID (preferred) or by unique source key.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Filter validation (client-side or screen transition validation):
  - Date range: start ≤ end; if invalid, block submit and show inline message.
  - `approvalStatus` must be one of allowed enum values returned/defined by backend.
- Detail view requires a valid identifier; missing/invalid should show not-found.

### Enable/disable rules
- If user lacks permission to view timekeeping entries, hide menu entry and block route access.
- Disable “Apply” while loading to avoid duplicate queries (UI ergonomics).

### Visibility rules
- Display `approvalStatus` prominently.
- Display source metadata fields (source system, source session id) for audit traceability.
- If `associatedWorkOrderId` is absent, show “—” (not an error).

### Error messaging expectations
- 401/403: “You do not have access to Timekeeping Entries.”
- 404: “Timekeeping Entry not found.”
- 409 (if ever returned on query): show “Conflict detected while loading; please refresh.” (do not invent business resolution)
- 5xx/network: “Unable to load timekeeping entries. Retry.”

---

## 8. Data Requirements

### Entities involved
- **People domain entity:** `TimekeepingEntry` (system of record for payroll after ingestion)

### Fields (type, required, defaults)
Minimum fields the UI must support (based on backend story reference #58):

- `timekeepingEntryId` (string/UUID) — **required** (primary key for routing)
- `tenantId` (string) — required (may be implicit; do not display if considered sensitive; see Open Questions)
- `sourceSystem` (string; expected `'shopmgr'`) — required
- `sourceSessionId` (string) — required (immutable; used for dedupe/audit)
- `employeeId` (string) — required
- `sessionStartTime` (datetime, timezone-aware) — required
- `sessionEndTime` (datetime, timezone-aware) — required
- `approvalStatus` (enum string) — required, default `PENDING_APPROVAL` at creation (ingestion)
- `associatedWorkOrderId` (string) — optional
- `locationId` (string) — optional (if available from event mapping)

### Read-only vs editable by state/role
- All fields are **read-only** in this story.
- If approval editing exists, it is handled by a separate workflow/story.

### Derived/calculated fields
- `sessionDuration` (derived) = end - start, displayed in list/detail if backend provides or frontend can compute safely from timestamps.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui can invoke services directly or via REST; the exact mechanism depends on repo conventions. This story specifies required capabilities; exact service names are an Open Question unless already standardized in the project.

### Load/view calls
1. **List Timekeeping Entries**
   - Capability: query with paging + filters
   - Inputs:
     - `pageIndex` (int, default 0)
     - `pageSize` (int, default 25)
     - `employeeId` (optional)
     - `approvalStatus` (optional)
     - `fromDateTime` (optional)
     - `thruDateTime` (optional)
     - `locationId` (optional)
     - `associatedWorkOrderId` (optional)
   - Outputs:
     - `items[]` each containing fields listed in Data Requirements (at least id, employeeId, start/end, approvalStatus, sourceSessionId)
     - `totalCount`

2. **Get Timekeeping Entry Detail**
   - Inputs: `timekeepingEntryId` (required)
   - Outputs: full record including source metadata

### Create/update calls
- none (read-only)

### Submit/transition calls
- none

### Error handling expectations
- 401/403 -> route to access denied / show banner
- 404 -> not found page for detail
- Validation errors (400) on filters -> show inline error and keep prior results unchanged
- Timeouts -> show non-blocking error with Retry

---

## 10. State Model & Transitions

### Allowed states
`TimekeepingEntry.approvalStatus` (minimum):
- `PENDING_APPROVAL`
- `APPROVED`
- `REJECTED`

### Role-based transitions
- Not implemented in this story (visibility only).
- UI must display current status and (if present) status history/audit timestamps.

### UI behavior per state
- `PENDING_APPROVAL`: show “Pending approval”
- `APPROVED`: show “Approved” and treat entry as immutable (read-only)
- `REJECTED`: show “Rejected”; if rejection reason fields exist, display them (see Open Questions)

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid date range filter: block query and show inline message.
- Unknown approval status filter: prevent submission (dropdown sourced from enum list if available).

### Concurrency conflicts
- If record disappears between list and detail (deleted is unlikely; but could be archived): detail load returns 404 -> show Not Found with link back.

### Unauthorized access
- User without permission:
  - Menu entry hidden
  - Direct URL access returns access denied screen

### Empty states
- No results for filters: show empty state + “Clear filters” action.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View list of ingested timekeeping entries
**Given** I am authenticated as a Payroll Clerk with permission to view timekeeping entries  
**When** I navigate to People → Timekeeping → Ingested Sessions  
**Then** I see a paginated list of Timekeeping Entries  
**And** each row shows employee identifier, session start time, session end time, approval status, and source session id

### Scenario 2: Filtering by approval status and date range
**Given** I am on the Ingested Sessions list screen  
**When** I set Approval Status to `PENDING_APPROVAL` and a valid date range and apply filters  
**Then** the list refreshes to show only matching entries  
**And** paging reflects the filtered result set

### Scenario 3: Invalid date range is blocked
**Given** I am on the Ingested Sessions list screen  
**When** I enter a from-date that is after the thru-date and apply filters  
**Then** the UI prevents the query  
**And** I see an inline validation message indicating the date range is invalid

### Scenario 4: View entry detail
**Given** I am on the Ingested Sessions list screen  
**When** I select a Timekeeping Entry  
**Then** I navigate to a detail screen for that entry  
**And** I can see approval status and source metadata including `sourceSystem` and `sourceSessionId`

### Scenario 5: Duplicate ingestion does not appear as duplicates in UI
**Given** the backend has received duplicate `WorkSessionCompleted` deliveries for the same `(tenantId, sessionId)`  
**When** I view the Ingested Sessions list  
**Then** I see only one Timekeeping Entry for that source session id  
**And** there are no duplicate rows representing the same source session

### Scenario 6: Unauthorized user cannot access
**Given** I am authenticated as a user without permission to view timekeeping entries  
**When** I attempt to navigate to the Ingested Sessions list URL directly  
**Then** I see an access denied message  
**And** no timekeeping entry data is displayed

### Scenario 7: Detail not found
**Given** I have a bookmarked Timekeeping Entry detail URL for an entry that does not exist  
**When** I open the URL  
**Then** I see a “not found” state  
**And** I can navigate back to the list screen

---

## 13. Audit & Observability

### User-visible audit data
- On detail screen, show (if available from backend):
  - `sourceSystem`
  - `sourceSessionId`
  - created timestamp / ingested timestamp
  - (optional) last updated timestamp
- If an “ingestion event id” exists, display it as read-only.

### Status history
- If backend exposes status change history (e.g., approval events), show a simple read-only timeline/table:
  - status, actor (if allowed), timestamp, reason (if rejected)

### Traceability expectations
- UI should display the stable source identifiers to allow cross-referencing with ShopMgr session logs.

---

## 14. Non-Functional UI Requirements
- **Performance:** list screen should load first page within 2 seconds on typical broadband for <= 25 items (excluding backend latency outside SLA).
- **Accessibility:** all controls keyboard accessible; labels for inputs; table has accessible headings.
- **Responsiveness:** usable on tablet widths; filters collapse appropriately (Quasar patterns).
- **i18n/timezone:** display session times in the user’s configured timezone; do not alter stored values.
- **Currency:** not applicable.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide empty-state messaging and “Clear filters” action; safe because it does not change domain behavior, only improves usability. (Impacted sections: UX Summary, Alternate / Error Flows)
- SD-UX-PAGINATION-DEFAULTS: Default paging to `pageSize=25`, `pageIndex=0`; safe UI ergonomics that do not alter business rules. (Impacted sections: Service Contracts, UX Summary)
- SD-ERR-STD-MAPPING: Map 401/403/404/5xx to standard UI states (access denied/not found/retry); safe because it follows conventional HTTP semantics without inventing policies. (Impacted sections: Business Rules, Alternate / Error Flows, Acceptance Criteria)

---

## 16. Open Questions
1. What are the **exact Moqui service names** (or REST endpoints) for:
   - listing `TimekeepingEntry` with filters + paging
   - fetching `TimekeepingEntry` by `timekeepingEntryId`?
2. What is the **permission/authorization hook** used in this frontend repo for People screens (e.g., a named permission like `timekeeping:read`), and which roles include Payroll Clerk?
3. Is `tenantId` considered sensitive in UI? Should it be hidden, shown only to admins, or always shown?
4. Does `TimekeepingEntry` include **rejection metadata** (reason code, notes) and/or **approval audit history** fields that should be displayed on the detail screen?
5. Should the list display `employeeId` only, or should it resolve/display employee name (would require People lookup and adds cross-entity UI behavior)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] CrossDomain: HR Ingests Work Sessions from Shopmgr — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/122

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] CrossDomain: HR Ingests Work Sessions from Shopmgr  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/122  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] CrossDomain: HR Ingests Work Sessions from Shopmgr

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As a **Payroll Clerk**, I want HR to receive work sessions from shopmgr so that payroll and compliance reporting can be produced.

## Details
- HR stores sessions with approval status.
- Reject/adjust supported.

## Acceptance Criteria
- Ingest idempotent.
- Visible for payroll.
- Approval tracked.

## Integrations
- Shopmgr→HR WorkSession events/API; optional HR→Shopmgr approval updates.

## Data / Entities
- TimekeepingEntry (hr domain)

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