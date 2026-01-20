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
[FRONTEND] [STORY] Workexec: WIP Dashboard for Active Work Orders (Status, Assignment, Drilldown, Realtime Updates)

### Primary Persona
Counter Associate

### Business Value
Enable counter staff to provide accurate, timely work-in-progress (WIP) updates to customers by showing current work order execution status, assignment context (mechanic/bay/location), and recent status changes in near real time.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Counter Associate  
- **I want** a WIP dashboard that shows current execution status for active work orders and allows drilling into a work order’s details/history  
- **So that** I can quickly answer customer “what’s the status?” questions and manage expectations.

### In-scope
- A WIP list view for active work orders with:
  - Canonical work order execution status (workexec state machine)
  - Assignment context (mechanic + bay/location) when available
  - “Last updated” / staleness indicator
  - Drilldown to a work order detail view/panel
- Data refresh via real-time updates **or** polling fallback (as available)
- Basic filtering (status/mechanic/location) and empty/loading/error states
- Moqui screen implementation (screens/forms/transitions) and Vue/Quasar frontend components integrated via Moqui

### Out-of-scope
- Changing work order state from this UI (no “force start/complete”)
- Implementing backend SSE/event infrastructure if not already present
- Building/owning Shop Management assignment logic (SoR outside workexec)
- Detailed inventory parts breakdown beyond what backend provides for WIP (unless explicitly available)
- Automatic customer notifications (manual “send update” not included unless clarified)

---

## 3. Actors & Stakeholders
- **Counter Associate (primary)**: consumes WIP list + detail to answer customers
- **Mechanic / Technician**: updates work order status in workexec (not via this UI)
- **Shop Manager / Dispatcher**: owns assignment decisions (mechanic/bay/location)
- **Workexec backend**: authoritative work order status + transition history
- **Shop management backend** (if separate): authoritative assignment context
- **Audit / Observability consumers**: rely on traceability of state changes and view refresh failures

---

## 4. Preconditions & Dependencies
- User is authenticated in the POS frontend.
- Work orders exist with statuses from the workexec state machine (see “State Model & Transitions”).
- Backend endpoints exist (or will exist) to:
  - list WIP work orders for a location/user scope
  - fetch work order WIP detail including status history
  - (optional) stream status change events or provide event polling support
- Permission model exists to scope which locations the user can view.

**Hard dependency clarifications needed**
- Exact backend API paths, response schemas, and auth headers for:
  - WIP list
  - WIP detail
  - event stream (SSE) vs polling endpoint
- How “assignment context” is retrieved (same endpoint vs separate service call).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- POS navigation: “Work In Progress” (WIP) item available to Counter Associate users.
- Deep link: `/wip` and `/wip/{workOrderId}` (or equivalent Moqui screen path) for drilldown.

### Screens to create/modify (Moqui)
1. **`WorkexecWip.xml` screen** (new)
   - Root screen for WIP dashboard list
2. **`WorkexecWipDetail.xml` screen** (new or embedded as subscreen/dialog)
   - Work order detail panel/screen: status history + key identifiers
3. **Reusable components**
   - `WipWorkOrderTable` Vue component (Quasar table)
   - `WipWorkOrderDetailPanel` Vue component
   - `WipStalenessBanner` component

### Navigation context
- From dashboard list, selecting a row opens:
  - either a right-side detail panel (preferred for fast workflow) **or**
  - a separate detail route/screen.
- Provide breadcrumb/back to WIP list.

### User workflows
**Happy path**
1. Counter Associate opens WIP dashboard
2. System loads active work orders within allowed scope
3. User optionally filters by status/mechanic/location
4. User clicks a work order row → detail view opens
5. If status changes arrive (event/poll), UI updates list and detail (if open)

**Alternate paths**
- No active work orders → show empty state
- Backend unavailable → show stale/cached view indication + retry
- User lacks permission for selected location → show authorization error and offer switching to allowed location (if available)

---

## 6. Functional Behavior

### Triggers
- Screen load of WIP dashboard
- User changes filter/sort
- Timer tick for polling refresh (fallback)
- Receipt of WIP status change event (if SSE enabled)
- User selects a work order row to open detail

### UI actions
- Load WIP list (initial + refresh)
- Subscribe/unsubscribe to event stream scoped by location (if configured)
- Open detail view and load detail payload
- Display staleness: last refresh timestamp + “updates paused” banner when disconnected
- Manual “Retry connection” action to reattempt SSE/polling

### State changes (frontend)
- Local store state:
  - `wipList[]`
  - `selectedWorkOrderId`
  - `detail` payload
  - `connectionMode` = `SSE` | `POLLING` | `DISCONNECTED`
  - `lastUpdatedAt` timestamp
  - `isStale` boolean computed

### Service interactions (frontend→backend)
- `GET WIP list` on load and refresh
- `GET WIP detail` on drilldown
- `SSE subscribe` (if supported) with fallback to polling

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Filters:
  - Status filter values must match supported workexec statuses (see State Model); invalid status filter should be rejected client-side and not sent.
- Drilldown:
  - WorkOrderId must be a valid identifier; if detail load returns 404, show “Work order not found or no longer visible.”

### Enable/disable rules
- If user cannot view all locations:
  - Location selector disabled or limited to allowed locations.
- If connection lost:
  - Show retry action enabled
  - Auto-refresh pauses after repeated failures (see Error Flows)

### Visibility rules
- Show assignment context fields only if present; otherwise show “Unassigned”.
- Show “Last updated” always.
- Show staleness warning if `now - lastUpdatedAt > stalenessThresholdSeconds`.

### Error messaging expectations
- Backend timeout/unavailable: “Real-time updates unavailable; last updated {timestamp}. Retry.”
- Unauthorized/forbidden: “You do not have access to this location’s work orders.”
- Generic: “Unable to load work-in-progress list. Try again.”

---

## 8. Data Requirements

### Entities involved (frontend view models)
> Note: Entities below are “view models” for UI; actual Moqui entities/services must map to backend payloads.

- `WorkOrderStatusView` (read model)
- `AssignmentView` (assignment context)
- `WorkOrderStateTransition` (history rows) — as a DTO/view model
- `EventSubscription` (SSE connection state) — frontend-only

### Fields (type, required, defaults)
**WIP List row (minimum required for buildability)**
- `workOrderId` (string/number, required)
- `status` (enum string, required)
- `statusLabel` (string, optional if backend provides display mapping)
- `updatedAt` (datetime UTC, required)
- `customerName` (string, optional — depends on privacy/policy)
- `vehicleSummary` (string, optional)
- `assignedMechanicName` (string, optional)
- `assignedLocationName` (string, optional)
- `assignedBay` (string, optional)

**WIP Detail (minimum)**
- `workOrderId` (required)
- `status` (required)
- `statusHistory[]` where each item:
  - `fromStatus` (enum, required)
  - `toStatus` (enum, required)
  - `transitionedAt` (datetime UTC, required)
  - `transitionedBy` (user id/name, optional depending on policy)
  - `reason` (string, optional)

### Read-only vs editable
- All WIP fields are read-only in this story.

### Derived/calculated fields
- `isStale` = `now - lastUpdatedAt > threshold`
- “Last updated X seconds ago” derived display string

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is partially defined in backend story reference, but **not guaranteed** for this frontend repo; treat as needing confirmation.

### Load/view calls
1. **WIP list**
   - Proposed: `GET /wip?locationId={id}&status=...&includeAssignments=true`
   - Returns: paginated `WorkOrderStatusView[]`
2. **WIP detail**
   - Proposed: `GET /wip/{workOrderId}`
   - Returns: `WorkOrderStatusView` + `statusHistory[]` (+ assignmentContext)

### Submit/transition calls
- None (view-only story)

### Event stream (optional)
- Proposed SSE: `GET /events/wip?locationId={id}`
- Event: `WorkorderStatusChanged` containing `workOrderId`, `newStatus`, `updatedAt`, optional `reason`, optional `assignmentContext`

### Error handling expectations
- `401/403`: route to login or show “not authorized” inline depending on app convention
- `404` detail: show not found message and return to list
- `409` (if returned for concurrency): refresh list and show “Updated while you were viewing”
- Network/timeout: show banner, pause SSE, fall back to polling if configured

---

## 10. State Model & Transitions

### Allowed states (authoritative from workexec state machine doc)
- `DRAFT`
- `APPROVED`
- `ASSIGNED`
- `WORK_IN_PROGRESS`
- `AWAITING_PARTS`
- `AWAITING_APPROVAL`
- `READY_FOR_PICKUP`
- `COMPLETED`
- `CANCELLED`

### What counts as “Active” for WIP list (needs confirmation)
Backend reference mentions statuses like “SCHEDULED, WAITING, IN_PROGRESS…” which **do not match** the documented workexec enum above. This is a conflict that must be clarified.

**UI behavior per state (once clarified)**
- Map each canonical status to a display bucket:
  - “Waiting” (likely `APPROVED`/`ASSIGNED`)
  - “In Progress” (`WORK_IN_PROGRESS`)
  - “Parts Pending” (`AWAITING_PARTS`)
  - “Approval Pending” (`AWAITING_APPROVAL`)
  - “Ready” (`READY_FOR_PICKUP`)
  - “Completed” (`COMPLETED`)
  - “Cancelled” (`CANCELLED`)
- Show terminal states only if user includes “Show completed/cancelled” filter toggle.

### Role-based transitions
- Not applicable (view-only), but visibility may be role-scoped.

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid filter status values → show inline error and reset to “All statuses”.

### Concurrency conflicts
- If SSE/poll refresh updates a work order while detail is open:
  - highlight changed status field
  - append new history row without losing scroll position (best-effort)
  - show message “Status updated while viewing”

### Unauthorized access
- If list call returns 403:
  - show full-page “Not authorized to view WIP for this location”
  - if multiple locations supported, prompt to select another allowed location

### Empty states
- No active work orders:
  - show “No work in progress” plus last refresh timestamp
  - keep refresh mechanism active

### Dependency failures
- SSE fails to connect:
  - switch to polling (if enabled)
  - show “Realtime unavailable; using polling”
- Polling fails repeatedly:
  - show “Updates paused” and require user to click Retry

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Load WIP list (happy path)
**Given** the Counter Associate is authenticated and has access to a location  
**When** they open the WIP dashboard  
**Then** the system loads and displays a list of active work orders for that location  
**And** each row shows workOrderId and current status  
**And** each row shows assignment context when available or “Unassigned” when not.

### Scenario 2: Drill into work order detail
**Given** the WIP list is displayed  
**When** the user selects a work order  
**Then** the UI loads and displays work order details including current status and status history (timestamps)  
**And** the user can return to the list without losing current filters.

### Scenario 3: Realtime update via event stream
**Given** the WIP dashboard is open and SSE connection is active  
**When** a status change event is received for a listed work order  
**Then** the status in the list updates within 5 seconds  
**And** the “last updated” timestamp updates  
**And** if that work order’s detail view is open, the detail view updates to reflect the new status and history entry.

### Scenario 4: Polling fallback
**Given** the WIP dashboard is open and SSE is unavailable  
**When** the polling interval elapses  
**Then** the UI refreshes the WIP list automatically  
**And** reflects any status changes returned by the backend  
**And** displays the last updated timestamp.

### Scenario 5: Staleness indicator
**Given** the WIP dashboard has not successfully refreshed within the configured staleness threshold  
**When** the user views the dashboard  
**Then** the UI displays a staleness warning including the last successful updated time  
**And** provides a “Retry” action.

### Scenario 6: Unauthorized location
**Given** the user attempts to load WIP for a location they are not permitted to view  
**When** the backend returns 403  
**Then** the UI shows an authorization error  
**And** does not display any work orders for that location.

---

## 13. Audit & Observability

### User-visible audit data
- In detail view, show status history entries (timestamp, from→to, optional reason)

### Status history
- Must be ordered newest-first (or explicitly specified) and stable across refreshes.

### Traceability expectations
- Frontend logs (console/app logger per project convention) should include:
  - correlation/request id if provided by backend headers
  - connection mode changes (SSE→polling)
  - refresh failures with status codes (no PII)

---

## 14. Non-Functional UI Requirements

- **Performance**: initial list load should not block interaction; show loading skeleton/table spinner.
- **Accessibility**: table and detail panel keyboard-navigable; status changes announced via aria-live region (for screen readers) when feasible.
- **Responsiveness**: usable on tablet widths; detail panel may become full-screen on small screens.
- **i18n/timezone**: timestamps displayed in store/user timezone (needs confirmation of app standard); backend timestamps assumed UTC.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty/loading/error states for list and detail views; safe as it does not change domain policy. Impacted sections: UX Summary, Error Flows.
- SD-UX-POLLING-FALLBACK: If SSE is unavailable, enable a configurable polling refresh interval (default 30s) and staleness threshold (default 60s) as UI ergonomics; safe because it only affects refresh cadence/display, not business rules. Impacted: Functional Behavior, Error Flows, Acceptance Criteria.
- SD-OBS-CLIENT-LOGGING: Log connection mode transitions and request failures without PII; safe observability boilerplate. Impacted: Audit & Observability, Error Flows.

---

## 16. Open Questions

1. **Backend contract**: What are the exact Moqui-accessible endpoints for WIP list, WIP detail, and (if present) SSE events (paths, query params, schemas, pagination)?  
2. **Status taxonomy conflict**: The backend story mentions statuses like `SCHEDULED/WAITING/IN_PROGRESS`, but the authoritative workexec FSM defines `APPROVED/ASSIGNED/WORK_IN_PROGRESS/...`. What is the canonical set the frontend must use and what exactly is considered “active” for the WIP dashboard?  
3. **Assignment source**: Is assignment context returned by the same WIP endpoints, or must the frontend call a separate shop management API? If separate, what is the contract and caching strategy?  
4. **Permissions**: What is the exact permission/role name(s) controlling multi-location visibility (e.g., `WIP_VIEW_ALL_LOCATIONS`) and how is it exposed to the frontend (claims, user context service, etc.)?  
5. **PII policy**: May the WIP list/detail display customer name/phone and vehicle VIN, or should it show anonymized identifiers only?  
6. **Realtime transport**: Is SSE supported in this frontend/Moqui setup, or should we implement polling only for now? If SSE, what authentication mechanism is used for the event stream?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Workexec: Display Work In Progress Status for Active Workorders — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/78

```
/kiro
# User Story

## Narrative
As a **Counter associate**, I want to see WIP status so that I can provide accurate updates.

## Details
- Show status by workorder: waiting, in progress, parts pending, ready, completed.
- Display assigned mechanic/location if available.

## Acceptance Criteria
- WIP view updates from events or polling.
- Status mapping consistent.
- Drilldown available.

## Integrations
- Workexec emits status events; shopmgr provides assignment context.

## Data / Entities
- WorkorderStatusView, AssignmentView, EventSubscription

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
```