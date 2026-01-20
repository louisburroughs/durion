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

**Title:** [FRONTEND] [STORY] Timekeeping: Capture Mobile Travel Time Separately (Travel Segments)

**Primary Persona:** Mobile Lead (and Mobile Technician as day-to-day actor)

**Business Value:** Ensure mobile technician availability is blocked during travel and travel time is captured accurately for downstream timekeeping/payroll workflows, reducing scheduling conflicts and payroll corrections.

---

## 2. Story Intent

**As a** Mobile Lead (or authorized Mobile Technician),  
**I want** to create and complete travel time segments for a mobile work assignment,  
**So that** scheduling accurately blocks availability during travel and timekeeping has the raw travel records needed for later approval/export to HR/payroll.

### In-scope
- View travel segments for a given mobile assignment / work order context
- Start a new travel segment (choose segment type)
- End the active in-progress travel segment
- Basic validation and user feedback for invalid actions (duplicate in-progress segment, non-mobile assignment, etc.)
- Show “availability blocked / in transit” indicator based on in-progress segment status (UI-derived from segment state)

### Out-of-scope
- Travel-time buffer/rounding policy application (explicitly owned by People/Timekeeping per backend reference)
- Time approval workflow and “send to HR” (frontend may display status but not implement approval/export unless endpoints exist)
- GPS tracking, geofencing, map routing
- Managing approval configurations or HR integration settings
- Post-approval corrections via adjustment records (backend concept exists; frontend editing of approved is out-of-scope unless API confirmed)

---

## 3. Actors & Stakeholders

- **Mobile Technician:** Starts/ends travel segments on their own assignment.
- **Mobile Lead / Service Advisor:** May create/edit on behalf (if permitted; see Open Questions).
- **Scheduler / Dispatch:** Consumes availability status; frontend must reflect blocked time in relevant views.
- **Payroll/HR System (downstream):** Ultimately receives approved travel time (not implemented here unless APIs confirmed).
- **Store/Location Manager:** Needs auditability for on-behalf edits and corrections.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in POS frontend.
- A **mobile** assignment exists and is accessible to the user (assignment identifier available in route params or context).
- Backend provides endpoints to:
  - Load assignment context (or load segments by assignment)
  - Create/start segment
  - End segment
  - List segments

### Dependencies (blocking if absent)
- Backend API contract for travel segments (paths, request/response, enums) is not defined in the provided frontend issue; backend reference #67 provides conceptual model but not definitive REST paths for segment start/stop.
- UI entry point location (where in app this lives) is not specified.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From a **Mobile Assignment / Mobile Appointment** detail screen: “Travel Time” action/tab
- From a **Work Order (mobile)** detail screen: “Travel Time” action/tab (if work order is the primary context)

### Screens to create/modify
1. **New screen:** `apps/pos/screen/mobile/TravelSegmentList.xml` (name indicative)
   - Lists segments for the current assignment
   - Shows active segment (if any) with “End Segment” primary action
   - Shows “Start Segment” action when no segment is in progress
2. **Modify existing screen(s)** (if present in repo):
   - Mobile assignment detail screen: add navigation to Travel Segment screen
   - Work order detail (mobile): add navigation to Travel Segment screen

### Navigation context
- Route contains `mobileWorkAssignmentId` (preferred) or `workOrderId` with a way to resolve mobile assignment reference.
- Breadcrumb: Mobile Assignments → Assignment Detail → Travel Time

### User workflows
**Happy path**
1. User opens Travel Time screen for an assignment.
2. Sees current segments and whether one is in progress.
3. Clicks “Start Segment” → selects segment type → confirms.
4. UI shows segment as `IN_PROGRESS` with start timestamp.
5. On arrival, user clicks “End Segment” → confirms.
6. UI shows completed segment with duration.

**Alternate paths**
- Start blocked because there is already an in-progress segment → UI shows error and highlights active segment.
- Assignment is not mobile → UI shows “Travel time can only be logged for mobile assignments.”
- User is not authorized → UI shows access denied and disables actions.

---

## 6. Functional Behavior

### Triggers
- Screen load (route entry): fetch assignment + segments
- Start Segment action
- End Segment action

### UI actions
- **Start Segment**
  - Opens a modal/dialog to select `segmentType` (enum-driven)
  - Confirm submits to backend
- **End Segment**
  - Available only when exactly one segment is `IN_PROGRESS` (or backend indicates activeSegment)
  - Confirm submits to backend with segmentId or assignmentId (depending on API)

### State changes (frontend-local)
- Maintain `segments[]` list and `activeSegment` computed from `status === IN_PROGRESS`
- On successful start/end: refresh list (or optimistic update if response includes updated segment)

### Service interactions (Moqui screen actions)
- `GET` load segments for assignment
- `POST` create/start segment
- `POST` end/complete segment

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Cannot start a segment if an `IN_PROGRESS` segment exists for the same assignment.
- Cannot end a segment if none is `IN_PROGRESS`.
- Segment type must be selected (required).
- Travel time can only be logged for **mobile assignments**; otherwise block start/end actions and show error.

### Enable/disable rules
- “Start Segment” disabled if active segment exists.
- “End Segment” enabled only if active segment exists.
- Segment type selector lists only supported types (v1 per backend reference):
  - `DEPART_SHOP`
  - `ARRIVE_CUSTOMER_SITE`
  - `DEPART_CUSTOMER_SITE`
  - `ARRIVE_SHOP`
  - `TRAVEL_BETWEEN_SITES`
  - `DEADHEAD`

### Visibility rules
- If segment was created/edited “on behalf”, show an indicator and audit snippet (requires API fields; otherwise omit and raise question).
- Show an “In Transit” banner/indicator when active segment exists.

### Error messaging expectations
- Duplicate in-progress: “You already have an active travel segment. End it before starting a new one.”
- Non-mobile: “Travel time can only be logged for mobile assignments.”
- Unauthorized: “You do not have permission to record travel time for this technician.”

---

## 8. Data Requirements

### Entities involved (frontend view model)
- `TravelSegment`
- `MobileWorkAssignmentRef` (or `MobileWorkAssignment`)

### Fields
**TravelSegment**
- `travelSegmentId` (string/UUID) – required
- `mobileWorkAssignmentId` (string/UUID) – required
- `technicianId` (string/UUID) – required (may be implied by assignment)
- `segmentType` (enum string) – required
- `startAt` (ISO datetime, timezone/UTC) – required
- `endAt` (ISO datetime, nullable) – required for completed
- `status` (enum string: `IN_PROGRESS`, `COMPLETED`, `CANCELLED`) – required
- `durationMinutes` (number, derived) – read-only; display when completed

**Optional audit/on-behalf fields** (ONLY if backend supplies; otherwise Open Question)
- `createdBy`, `lastModifiedBy`, `lastModifiedAt`
- `actedByUserId`, `actedForPersonId`, `onBehalfReasonCode`

### Read-only vs editable
- Segments are not “editable” in this story; only start/end actions.
- Completed segments are read-only.

### Derived/calculated fields
- `durationMinutes` displayed if provided; otherwise compute in UI from `startAt/endAt` for display only (not persisted).

---

## 9. Service Contracts (Frontend Perspective)

> Blocking: exact API paths/schemas for travel segment capture are not defined in provided frontend issue; backend reference shows concepts and an HR event schema, but not the segment CRUD endpoints.

### Load/view calls
- `GET /api/mobile-assignments/{mobileWorkAssignmentId}` (or equivalent) to confirm mobile context (if needed)
- `GET /api/mobile-assignments/{mobileWorkAssignmentId}/travel-segments`
  - Response: list of TravelSegment ordered by `startAt` asc

### Create/update calls
- `POST /api/mobile-assignments/{mobileWorkAssignmentId}/travel-segments`
  - Request: `{ "segmentType": "DEPART_SHOP" }` (+ possibly `technicianId` if on-behalf)
  - Response: `201` with TravelSegment

### Submit/transition calls
- `POST /api/travel-segments/{travelSegmentId}/end`
  - Request: optional `{ "endAt": "<now>" }` (or none; server uses now)
  - Response: `200` with updated TravelSegment

### Error handling expectations
- `400` validation errors → show message from backend if provided; otherwise generic with field highlight.
- `401/403` → show access denied; disable actions.
- `404` assignment/segment not found → show not found state.
- `409` concurrency/active segment conflict → refresh list and show conflict message.

---

## 10. State Model & Transitions

### TravelSegment states (from backend reference)
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED` (not created by UI in this story unless API exists)

### Allowed transitions (UI-supported)
- Start segment: (no active) → creates `IN_PROGRESS`
- End segment: `IN_PROGRESS` → `COMPLETED`

### Role-based transitions
- Technician: start/end for self (assumed; confirm via Open Questions if needed)
- Mobile Lead/Service Advisor: on-behalf start/end (only if permitted; confirm)

### UI behavior per state
- `IN_PROGRESS`: show active banner + “End Segment”
- `COMPLETED`: show duration + timestamps; no actions
- `CANCELLED`: show as cancelled; no actions

---

## 11. Alternate / Error Flows

### Validation failures
- Missing segment type → prevent submit; show inline error “Segment type is required.”
- Non-mobile assignment → disable actions; show blocking message.

### Concurrency conflicts
- Another user started a segment → start returns `409`; UI refreshes and shows the now-active segment.
- Segment ended elsewhere → end returns `409/404`; UI refreshes and removes end action.

### Unauthorized access
- User lacks permission to access assignment or record travel → show `403` screen state; hide action buttons.

### Empty states
- No segments yet → show empty state with “Start Segment” CTA (if permitted).

---

## 12. Acceptance Criteria

### Scenario 1: Start a travel segment successfully
**Given** I am a Mobile Technician assigned to a mobile work assignment  
**And** there is no existing travel segment in `IN_PROGRESS` for that assignment  
**When** I open the Travel Time screen  
**And** I click “Start Segment” and select `DEPART_SHOP`  
**Then** a new travel segment is created with status `IN_PROGRESS` and a recorded `startAt` timestamp  
**And** the UI displays an “In Transit” indicator and an enabled “End Segment” action.

### Scenario 2: End an in-progress travel segment successfully
**Given** a travel segment for my mobile assignment is in status `IN_PROGRESS`  
**When** I click “End Segment”  
**Then** the segment transitions to `COMPLETED` with `endAt` populated  
**And** the UI shows the segment duration (from `durationMinutes` or computed from timestamps)  
**And** the “In Transit” indicator is no longer shown.

### Scenario 3: Prevent starting a new segment when one is already active
**Given** a travel segment for the assignment is in status `IN_PROGRESS`  
**When** I attempt to start another travel segment  
**Then** the system rejects the action with a clear error message  
**And** the UI keeps the existing active segment visible and does not create a second active segment.

### Scenario 4: Block travel time logging for non-mobile assignments
**Given** I navigate to Travel Time from a non-mobile work order/assignment  
**When** the Travel Time screen loads  
**Then** the UI shows “Travel time can only be logged for mobile assignments.”  
**And** Start/End actions are disabled or hidden.

### Scenario 5: Unauthorized user cannot record travel time
**Given** I am logged in without permission to create or end travel segments for this technician/assignment  
**When** I open the Travel Time screen  
**Then** I can view segments only if permitted  
**And** any record actions are hidden/disabled  
**And** attempting the action results in a `403` error message.

---

## 13. Audit & Observability

- UI must display basic audit metadata if provided by backend (created/modified timestamps and “on behalf” indicator).
- Frontend should ensure requests include correlation/request ID headers if the project convention exists (Moqui typically supports trace headers; confirm in repo).
- Log (client-side) only non-PII diagnostic info on failures (endpoint, status code, assignmentId, segmentId).

---

## 14. Non-Functional UI Requirements

- **Performance:** Travel Time screen loads segments within 2s on typical mobile network for ≤100 segments (pagination if needed).
- **Accessibility:** All actions keyboard accessible; dialog has focus trap; labels announced.
- **Responsiveness:** Mobile-first layout; primary actions reachable on small screens.
- **i18n/timezone:** Display timestamps in user’s local timezone; store/send ISO/UTC per backend.

---

## 15. Applied Safe Defaults

- **SD-UX-EMPTY-STATE**
  - Assumed: Provide an explicit empty state with a primary CTA when there are no segments.
  - Why safe: UI-only ergonomics; no domain policy implied.
  - Impacted sections: UX Summary, Alternate / Error Flows, Acceptance Criteria.
- **SD-ERR-HTTP-MAP**
  - Assumed: Standard mapping of HTTP 400/401/403/404/409 to inline/toast errors plus optional refresh on 409.
  - Why safe: Pure error handling; consistent with common frontend practice without altering domain behavior.
  - Impacted sections: Service Contracts, Alternate / Error Flows.

---

## 16. Open Questions

1. **API contracts (blocking):** What are the exact backend endpoints and payloads for:
   - listing travel segments,
   - starting/creating a segment,
   - ending/completing a segment,
   and what are the exact enum values for `status` and `segmentType`?
2. **Primary context identifier (blocking):** Should the frontend route be keyed by `mobileWorkAssignmentId`, `workOrderId`, or both? If both, which is canonical for segment association?
3. **Permissions (blocking):** Confirm the RBAC rules for:
   - technician self-capture,
   - Mobile Lead/Advisor on-behalf capture,
   - whether viewing segments requires separate permission.
4. **On-behalf workflow (blocking):** If on-behalf edits are allowed, what UI is required to select “acted for” technician and mandatory `reasonCode`? Provide the allowed reason code enum list.
5. **Cancellation (non-blocking unless required):** Is cancelling a segment in-scope for frontend v1? If yes, what are the rules and endpoint?
6. **Availability indicator source (blocking):** Should availability “In Transit” be derived solely from active segment state, or does backend expose a separate availability/status API that must be used?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Timekeeping: Capture Mobile Travel Time Separately  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/131  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Timekeeping: Capture Mobile Travel Time Separately

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Mobile Lead**, I want to record travel time for a mobile appointment so that availability and payroll are accurate.

## Details
- Travel segments depart/arrive/return.
- Policies may auto-apply buffers.

## Acceptance Criteria
- Segments recorded.
- Sent to HR.
- Availability blocked during travel.

## Integrations
- Shopmgr→HR TravelTime events/API.

## Data / Entities
- TravelSegment, MobileAssignmentRef

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