## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:shopmgmt
- status:draft

### Recommended
- agent:shopmgmt-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] Appointment: Show Assignment (Bay/Mobile + Mechanic) with Near-Real-Time Updates

### Primary Persona
Service Advisor

### Business Value
Service Advisors can quickly confirm where work will be performed (bay vs mobile) and who is assigned (mechanic), improving customer expectation setting and reducing operational confusion.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to view the current appointment assignment (bay or mobile unit, mechanic, and notes)  
- **So that** I can set accurate expectations with the customer and coordinate internally.

### In-scope
- Read-only display of assignment details on Appointment detail view (and a compact summary in list contexts if present in current UI).
- Near-real-time refresh behavior (push subscription if available; polling fallback; manual refresh).
- Role-based access control for viewing assignments.
- Display assignment notes read-only for most roles; (optional) allow edit only for permitted roles *if frontend supports the write endpoint*.
- Degraded-mode display if assignment/mechanic identity cannot be loaded.

### Out-of-scope
- Creating/updating appointments, scheduling, or assignment decision logic.
- Automated customer notifications on assignment changes.
- Reverse geocoding mobile GPS coordinates (unless already available as a backend field).
- Mechanic HR profile management.

---

## 3. Actors & Stakeholders

### Primary
- **Service Advisor** (views assignment to communicate with customer)

### Secondary
- **Shop Manager / Dispatcher** (may have additional visibility and possible note-edit permissions)
- **Mechanic** (stakeholder of assignment, not primary UI user here)
- **Customer** (impacted indirectly)

### Systems
- **shopmgmt backend**: system of record for assignment data
- **People/HR backend**: system of record for mechanic identity (name/photo/certs) as referenced by shopmgmt
- **Audit**: captures viewing-sensitive actions and any overrides/edits (notes edits if enabled)

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in POS frontend.
- User has permission to view appointment details for a given facility.
- Appointment exists and is accessible within facility scope.

### Dependencies
- Backend endpoint(s) to fetch `AssignmentView` for an appointment.
- Backend strategy for real-time updates:
  - Push (WebSocket/SSE) channel/event name(s) for `ASSIGNMENT_UPDATED`, **or**
  - Polling endpoint that returns updated `AssignmentView`.
- Optional backend endpoint to update assignment notes (only if enabling notes edit).

> Note: Backend reference story states these decisions are resolved, but the exact API paths/event channel identifiers are not provided in the input. This story is therefore blocked on contract clarification (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Appointment Detail** screen (primary).
- If Appointment List screen exists: show compact assignment summary per row (secondary, only if already present in the frontend IA).

### Screens to create/modify (Moqui)
- **Modify**: `apps/pos/screen/appointment/AppointmentDetail.xml` (or equivalent appointment detail screen)
  - Add an “Assignment” section driven by a `screen-service` call to load assignment data.
- **Optional Modify**: `apps/pos/screen/appointment/AppointmentList.xml` (or equivalent)
  - Add compact assignment summary column/line.

### Navigation context
- Within appointment context: requires `appointmentId` in parameters.
- Facility scoping: ensure facility context is included (from session/user context or explicit param), and passed to services if required by contract.

### User workflows

#### Happy path (assignment exists)
1. User opens Appointment Detail.
2. UI loads appointment context + assignment view.
3. UI shows:
   - Assignment type: **Bay** *or* **Mobile Unit**
   - Assigned bay/mobile identifier (and location if applicable)
   - Assigned mechanic identity
   - Notes (if any)
   - Timestamps (assigned at / last updated) if available
4. Assignment updates appear automatically (push) or within 30s (poll), with a visible “Updated” indicator/banner.

#### Alternate path: unassigned / partially assigned
- UI shows “Unassigned” for missing components (no bay/mobile, no mechanic).
- If assignment status indicates pending skills/awaiting fulfillment (if field exists), show it as read-only status.

#### Alternate path: mobile GPS staleness
- If mobile assignment includes `lastUpdatedAt` and coordinate fields, show last update time and warn when stale per backend rules (see Business Rules section).

---

## 6. Functional Behavior

### Triggers
- Enter Appointment Detail screen.
- User clicks “Refresh assignment”.
- Real-time update event received OR polling interval fires while screen is visible.
- (Optional) user with permission edits notes and saves.

### UI actions
- Load and display assignment block.
- Show a non-blocking banner/toast when assignment changes while viewing:
  - “Assignment updated: Bay changed from X to Y” (message content can be generic if diff not available).
- Provide manual refresh control.

### State changes (frontend)
- Local view model transitions:
  - `idle` → `loading` → `loaded`
  - `loaded` → `refreshing` (on event/poll/manual) → `loaded`
  - `error` with retry option
- Track last-seen assignment `version` or `lastUpdatedAt` if provided to detect changes.

### Service interactions
- Fetch assignment view from shopmgmt.
- If mechanic identity is not fully contained in assignment payload:
  - Fetch mechanic details from People/HR using mechanicId(s) present in AssignmentView.
- Subscribe to assignment update events for the current appointment (if supported).
- Fallback polling every 30 seconds when:
  - push is unavailable, or
  - user is not connected to push channel, or
  - subscription fails.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- No creation/update validation except:
  - If notes editing is enabled: enforce max length **500 characters** client-side (and still rely on server validation).
  - Require a non-empty “reason” only if backend requires it for note edits (unknown; open question).

### Enable/disable rules
- Assignment view section is visible only when user has `VIEW_ASSIGNMENTS` (or equivalent) permission.
- Notes field:
  - Read-only by default.
  - Editable only if user has `EDIT_ASSIGNMENT_NOTES`.

### Visibility rules
- Appointment states visible:
  - Allow viewing in `SCHEDULED`, `IN_PROGRESS`, `AWAITING_PARTS`, `READY`.
  - For `CANCELLED`: hide assignment unless user has `VIEW_CANCELLED_APPOINTMENTS`.
  - If appointment status is unknown/unavailable, rely on backend authorization and show “Not authorized” when denied.

### Error messaging expectations
- Permission denied: show “You don’t have permission to view assignments for this facility.”
- Backend unavailable: show “Assignment data is temporarily unavailable. Showing last known data.” (only if cached/previously loaded) else show retry CTA.
- HR data unavailable: show mechanic as “Mechanic ID: <id>” with warning “Mechanic details unavailable.”

### Mobile GPS staleness warnings (from backend decisions)
- If assignment type is `MOBILE_UNIT` and `lastUpdatedAt` present:
  - If `now - lastUpdatedAt > 30 minutes`: show “Location may be stale”.
  - If `now - lastUpdatedAt > 60 minutes`: show “Mobile unit unavailable (stale location)” and visually de-emphasize location fields.

> If the assignment payload does not include GPS fields, only show `lastUpdatedAt` staleness warning (no maps).

---

## 8. Data Requirements

### Entities / View Models (frontend-facing)
- `AssignmentView` (from shopmgmt)
- `LocationRef` (bay or mobile reference)
- `MechanicRef` (mechanic reference or ID)
- (Optional) `AssignmentUpdatedEvent` payload for live updates

### Fields (type, required, defaults)

#### AssignmentView (expected)
- `appointmentId` (string/UUID, required)
- `facilityId` (string/UUID, required or inferred by context)
- `assignmentType` (enum: `BAY` | `MOBILE_UNIT` | `UNASSIGNED`, required)
- `bay` (object|null)
  - `bayId` (string/UUID)
  - `bayNameOrNumber` (string)
  - `locationName` (string, optional)
- `mobileUnit` (object|null)
  - `mobileUnitId` (string/UUID)
  - `mobileUnitName` (string, optional)
  - `lastKnownLat` (number, optional)
  - `lastKnownLon` (number, optional)
  - `lastUpdatedAt` (datetime, optional)
- `mechanic` (object|null)
  - `mechanicId` (string/UUID, optional if unassigned)
  - `displayName` (string, optional if HR call needed)
  - `photoUrl` (string, optional)
- `assignmentNotes` (string, optional; max 500)
- `assignedAt` (datetime, optional)
- `lastUpdatedAt` (datetime, optional)
- `version` (number, optional but preferred for change detection)
- `assignmentStatus` (enum, optional; e.g., `AWAITING_SKILL_FULFILLMENT`)

**Read-only vs editable**
- All fields read-only for Service Advisor.
- Only `assignmentNotes` may be editable for roles with `EDIT_ASSIGNMENT_NOTES` *if supported by backend contract*.

**Derived/calculated**
- `gpsStalenessState` derived from `mobileUnit.lastUpdatedAt` vs current time.
- `displayAssignmentSummary` derived string for list:  
  - `Bay <name> - <mechanic>` / `Mobile - <mechanic>` / `Unassigned`

---

## 9. Service Contracts (Frontend Perspective)

> API paths, request/response schemas, and event channel names are not included in provided inputs. The following defines **required contract shape**; exact endpoints are Open Questions.

### Load/view calls
1. `GET AssignmentView by appointmentId`
   - Inputs: `appointmentId`, (optional) `facilityId`
   - Output: `AssignmentView`
   - Errors:
     - 401/403 unauthorized
     - 404 appointment not found or not accessible
     - 503 shopmgmt unavailable

2. `GET Mechanic by mechanicId` (only if AssignmentView lacks display fields)
   - Inputs: `mechanicId`
   - Output: `MechanicRef` (min: displayName, optional photo/certs summary)
   - Errors: degraded display if unavailable

### Create/update calls (optional)
- `PUT/PATCH Assignment Notes`
  - Inputs: `appointmentId`, `assignmentNotes`, (optional) `expectedVersion`
  - Requires permission: `EDIT_ASSIGNMENT_NOTES`
  - Errors:
    - 409 concurrency/version mismatch
    - 400 validation (length > 500)
    - 403 permission denied

### Submit/transition calls
- None (this is display-only; no appointment workflow transitions).

### Error handling expectations
- Map backend validation errors to field-level errors for notes editing.
- Map 409 to “This assignment changed while you were editing. Reload and try again.”
- Map network/503 to non-blocking banner + retry.

---

## 10. State Model & Transitions

### Allowed states (appointment context)
- Appointment status gating (view visibility):
  - Allowed: `SCHEDULED`, `IN_PROGRESS`, `AWAITING_PARTS`, `READY`
  - Conditional: `CANCELLED` only with `VIEW_CANCELLED_APPOINTMENTS`

### Role-based transitions
- None (read-only story). Optional notes edit is not a state transition.

### UI behavior per state
- If appointment is in a viewable status and user authorized:
  - Show assignment section.
- If appointment is CANCELLED and user lacks permission:
  - Hide assignment section and show “Not available for cancelled appointments.”
- If assignmentType is `UNASSIGNED`:
  - Show empty-state text and last updated if present.

---

## 11. Alternate / Error Flows

### Validation failures (notes edit only)
- Notes length > 500:
  - Prevent submit; show inline error.
- Server returns validation error:
  - Show inline error based on response message/code.

### Concurrency conflicts
- If backend returns 409/version mismatch on notes save:
  - UI reloads AssignmentView and informs user of updated value; user may retry.

### Unauthorized access
- If user lacks `VIEW_ASSIGNMENTS`:
  - Do not call assignment service (if permission is known client-side), or handle 403 gracefully.
  - Display permission error block in place of assignment.

### Empty states
- No assignment exists:
  - Show “No resources assigned yet.”
- Mechanic unassigned but bay/mobile assigned:
  - Show “Mechanic: Unassigned”
- Bay/mobile missing but mechanic assigned (should not happen but possible):
  - Show “Location: Unassigned” and still show mechanic.

### Service outage / degraded data
- shopmgmt unreachable:
  - If previous data exists in memory, keep showing it with warning and allow refresh.
  - If no data, show error state + retry.
- People/HR unreachable:
  - Show mechanicId only (if present), with warning.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View assignment on appointment detail (assigned bay + mechanic)
**Given** I am authenticated as a Service Advisor with `VIEW_ASSIGNMENTS` for Facility A  
**And** an appointment in Facility A exists with a BAY assignment and a mechanic assigned  
**When** I open the Appointment Detail screen for that appointment  
**Then** I see the assigned bay identifier  
**And** I see the assigned mechanic name (or mechanic ID with a warning if HR is unavailable)  
**And** I see assignment notes if present  
**And** the assignment section is read-only.

### Scenario 2: Unassigned appointment shows empty state
**Given** I am authenticated as a Service Advisor with `VIEW_ASSIGNMENTS`  
**And** an appointment exists with no bay/mobile and no mechanic assigned  
**When** I open Appointment Detail  
**Then** I see “No resources assigned yet”  
**And** no edit controls are shown.

### Scenario 3: Mobile unit assignment shows staleness warning
**Given** I am authenticated with `VIEW_ASSIGNMENTS`  
**And** an appointment has a MOBILE_UNIT assignment with `lastUpdatedAt` older than 30 minutes  
**When** I view the assignment section  
**Then** I see a warning that the mobile location may be stale  
**And** if `lastUpdatedAt` is older than 60 minutes, I see an “unavailable” indication for the mobile unit.

### Scenario 4: Near-real-time updates reflect changes while viewing
**Given** I am viewing an appointment’s assignment section  
**And** the assignment changes in shopmgmt (e.g., bay changes or mechanic changes)  
**When** the frontend receives an assignment update event or detects the change via polling within 30 seconds  
**Then** the assignment section updates to show the latest assignment  
**And** I see a visible banner/toast indicating the assignment was updated.

### Scenario 5: Permission denied prevents viewing assignments
**Given** I am authenticated as a user without `VIEW_ASSIGNMENTS`  
**When** I open Appointment Detail  
**Then** I do not see assignment details  
**And** I see a message indicating I lack permission to view assignments.

### Scenario 6 (Optional): Notes editable only for authorized roles
**Given** I am authenticated as a Shop Manager with `EDIT_ASSIGNMENT_NOTES`  
**And** the backend supports updating assignment notes  
**When** I edit the assignment notes and save  
**Then** the updated notes are displayed  
**And** the save action is audited (backend)  
**And** a user without `EDIT_ASSIGNMENT_NOTES` cannot edit notes on the same screen.

---

## 13. Audit & Observability

### User-visible audit data
- If `assignedAt` / `lastUpdatedAt` fields are provided, display them read-only.
- If notes are edited (optional), show “Last edited at/by” only if backend provides fields; otherwise omit (do not invent).

### Status history
- Out of scope to display full assignment history unless backend provides a history endpoint (not provided).

### Traceability expectations
- Frontend should include correlation/trace id headers if Moqui integration supports it (standard pattern).
- Log (client-side) subscription/polling failures and show non-blocking UI warning.

---

## 14. Non-Functional UI Requirements

- **Performance**: Assignment section load should not block the rest of Appointment Detail rendering; show skeleton/loading state.
- **Accessibility**: Status messages (errors, updated banners) must be announced via ARIA live region; controls keyboard accessible.
- **Responsiveness**: Assignment section should render correctly on tablet POS layouts; avoid dense tables.
- **i18n/timezone**: Display timestamps in facility/local timezone if available; otherwise user’s timezone. (Need confirmation from project convention—open question if strict.)
- **Security**: Do not expose assignment data across facilities; enforce scoping via backend and do not cache across facility context.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide explicit “Unassigned”/empty-state messaging for missing assignment fields; qualifies as UI ergonomics and does not affect domain policy. Impacted sections: UX Summary, Alternate / Error Flows, Acceptance Criteria.
- SD-UX-POLLING-FALLBACK: If push updates are unavailable, poll every 30s while screen is visible; matches backend decision and is a recoverable UI behavior. Impacted sections: Functional Behavior, Service Contracts, Acceptance Criteria.
- SD-UX-RETRY-ACTION: Provide a manual “Refresh assignment” action on error/loaded states; UI ergonomics only. Impacted sections: UX Summary, Alternate / Error Flows.

---

## 16. Open Questions

1. **API Contract**: What are the exact Moqui-accessible endpoints/services for:
   - Load `AssignmentView` by `appointmentId` (path/service name, request params, response schema)?
   - Load mechanic identity (is it embedded in `AssignmentView` or must we call People/HR; if so, endpoint/service name)?
2. **Real-time Updates**: What is the exact mechanism and identifiers for push updates in this frontend?
   - WebSocket vs SSE?
   - Channel/topic naming and event payload for `ASSIGNMENT_UPDATED` (and whether it includes full AssignmentView vs partial delta)?
3. **Facility scoping**: Is `facilityId` required explicitly in requests, or inferred from session/context in Moqui services?
4. **Appointment status source**: Do we already have appointment status available on the Appointment Detail screen to enforce “exclude CANCELLED unless permitted”, or should backend enforce this entirely?
5. **Notes editing scope**: Should this frontend story include notes editing for authorized roles now, or remain strictly read-only? If included, confirm:
   - endpoint/service and payload
   - whether optimistic concurrency uses `version` and returns 409
   - whether “reason” is required for note edits
6. **Timestamp formatting convention**: What is the project standard for timezone display (facility timezone vs user timezone) in the POS UI?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Appointment: Show Assignment (Location/Bay/Mobile + Mechanic) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/74

---

FRONTEND STORY (FULL CONTEXT)

Title: [FRONTEND] [STORY] Appointment: Show Assignment (Location/Bay/Mobile + Mechanic)  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/74  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Appointment: Show Assignment (Location/Bay/Mobile + Mechanic)

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative  
As a **Service Advisor**, I want to see assigned resources so that I can set expectations.

## Details  
- Display assigned bay/mobile unit and mechanic.  
- Show notes (optional).

## Acceptance Criteria  
- Assignment displayed.  
- Updates reflect changes.  
- Read-only for most roles.

## Integrations  
- Shopmgr provides assignment data; HR provides mechanic identity.

## Data / Entities  
- AssignmentView, LocationRef, MechanicRef

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