STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** crm-pragmatic

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] Vehicle: Store Vehicle Care Preferences (Capture, Edit, View, and Audit)

**Primary Persona:** Service Advisor

**Business Value:** Ensure vehicle service is consistently performed according to customer/vehicle-specific expectations (tire brand, rotation interval, alignment preference, torque notes, and general notes), and make these preferences visible wherever service decisions are made (estimate and work order), with an auditable change history.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to add and update vehicle-specific care preferences and service notes from the vehicle context  
- **So that** technicians and advisors can reliably see and follow these preferences during estimating and execution, reducing rework and improving customer satisfaction.

### In-scope
- A vehicle-level UI section to **view**, **create**, and **update** care preferences + service notes.
- Preferences displayed **read-only** in:
  - Estimate view (when vehicle present)
  - Work Order view (when vehicle present)
- **Audit visibility** of changes (at minimum: who/when; ideally before/after if provided).
- Validation and error handling on save.

### Out-of-scope
- Defining/implementing CRM backend data model/services (frontend consumes).
- Deduplication/merge rules, customer identity rules.
- Workflow/state logic inside Workorder Execution beyond *displaying* the preferences.
- Bulk-edit across vehicles or importing preferences.

---

## 3. Actors & Stakeholders
- **Service Advisor (Primary):** Captures/updates preferences and notes.
- **Technician/Mechanic (Secondary):** Consumes preferences on work order during execution.
- **Shop Manager (Stakeholder):** Wants consistency and accountability (audit).
- **CRM System (SoR):** Stores vehicle preferences/notes and audit trail.
- **Workorder Execution UI (Downstream consumer):** Displays preferences within Estimate/WO context.

---

## 4. Preconditions & Dependencies
1. User is authenticated in the POS frontend.
2. User has authorization to:
   - View vehicle record
   - Edit vehicle care preferences (exact permission/scope **TBD**, see Open Questions)
3. A **Vehicle** record exists and user can navigate to it (vehicleId available in route params).
4. Backend endpoints (Moqui services or REST resources) exist/are exposed for:
   - Loading vehicle preferences/notes
   - Saving vehicle preferences/notes
   - Loading audit history (or audit summary) for these changes
5. Estimate/Work Order screens can access vehicleId (or vehicle reference) to load preferences for display.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Vehicle Profile** screen: “Vehicle Care Preferences & Notes” section/card/tab.
- From **Estimate** view screen: read-only “Vehicle Preferences” panel.
- From **Work Order** view/execution screen: read-only “Vehicle Preferences” panel.

### Screens to create/modify
1. **Modify:** `Vehicle` profile screen  
   - Add a section for preferences/notes with:
     - Read-only display mode
     - Edit mode (form)
     - Save/Cancel actions
     - “Last updated by/at” summary
     - Link/button to “View change history” (audit)
2. **Modify:** `Estimate` view screen  
   - Add read-only rendering of preferences/notes for the vehicle on the estimate.
3. **Modify:** `Work Order` view/execution screen  
   - Add read-only rendering of preferences/notes for the vehicle on the WO.
4. **Add or Modify (optional depending on backend):** Audit modal/subscreen  
   - A modal dialog or child screen listing audit entries for this vehicle’s preferences.

### Navigation context
- Vehicle profile route includes `vehicleId`.
- Estimate/WO view routes include identifier for estimate/WO; vehicleId must be derivable:
  - either present directly, or fetched from estimate/WO header.

### User workflows
**Happy path (Vehicle profile)**
1. Service Advisor opens Vehicle profile.
2. System loads existing preferences/notes (if any) and displays them.
3. Advisor clicks “Edit”.
4. Advisor updates structured fields and/or free-form notes.
5. Advisor clicks “Save”.
6. System validates, saves, shows success toast, and returns to read-only view with updated summary + audit link.

**Alternate path: create first-time**
- If no preferences exist, section shows empty state with “Add preferences” CTA leading into edit mode.

**Alternate path: view audit**
- Advisor clicks “Change history” and sees list of changes (timestamp, actor, changed fields/values if available).

**Read-only in Estimate/WO**
- User sees preferences and notes; no editing actions exposed.

---

## 6. Functional Behavior

### Triggers
- Screen load of Vehicle profile, Estimate view, Work Order view.
- User actions: Edit, Save, Cancel, View History.

### UI actions
- **Vehicle Profile**
  - `Edit` toggles form inputs enabled.
  - `Save` submits to backend.
  - `Cancel` discards unsaved changes and reverts to last loaded state.
  - `View change history` opens modal/subscreen that loads audit entries.
- **Estimate/WO**
  - Render a consistent, compact, read-only view:
    - Preferred tire brand/line
    - Rotation interval (+ unit)
    - Alignment preference
    - Torque spec notes
    - Service notes

### State changes (UI-level)
- `loading` (initial fetch)
- `empty` (no record exists)
- `view` (record exists, read-only)
- `edit` (form dirty/clean)
- `saving`
- `error` (load/save failures)

### Service interactions
- On load: call a “get preferences by vehicleId” service.
- On save:
  - If record exists: update service.
  - If missing: create service (or upsert if backend supports).
- On audit open: call “get audit entries by vehicleId” service.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `rotationInterval` must be numeric integer if provided; reject non-numeric input.
- Email/phone/VIN not in scope here (do not validate beyond field-level requirements below).

**Blocking clarification:** whether rotation interval is required, allowed min/max, and unit enum are not fully defined (see Open Questions). Until clarified, frontend must only enforce “integer if present” and rely on backend for additional constraints.

### Enable/disable rules
- Edit controls enabled only if user has edit permission (TBD permission check mechanism).
- Save button disabled when:
  - Form is pristine (no changes), OR
  - Currently saving, OR
  - Client-side validation fails (e.g., rotation interval not integer)

### Visibility rules
- Estimate/WO: section visible if vehicle exists on the document.
- If no preferences exist: show “No vehicle preferences recorded” and hide empty fields; still show notes area as “None” (or omitted) per UI ergonomics.

### Error messaging expectations
- Field-specific error for rotation interval invalid input: “Rotation interval must be a whole number.”
- Backend validation errors should be surfaced at:
  - field level if backend returns field mapping
  - otherwise as a banner/toast with backend message.

---

## 8. Data Requirements

### Entities involved (frontend perspective; backend SoR = CRM)
- `Vehicle` (already exists; provides `vehicleId`)
- `VehiclePreference` (or `VehicleCarePreference`) **TBD exact name**
- `VehicleNote` (if separate entity) **TBD**
- `AuditLog` / `ChangeHistory` entries **TBD**

### Fields (type, required, defaults)

**Vehicle Care Preferences record (view/edit)**
- `vehicleId` (UUID/string) — required, read-only (from route/context)
- `preferredTireBrand` (string) — optional
- `preferredTireLine` (string) — optional
- `rotationInterval` (integer) — optional (client validates integer if provided)
- `rotationIntervalUnit` (enum string: `MILES`/`KM` etc.) — optional; default behavior **TBD**
- `alignmentPreference` (string) — optional
- `torqueSpecNotes` (string) — optional (short text)
- `serviceNotes` (text) — optional (multi-line)
- `lastUpdatedAt` (datetime) — read-only (if provided)
- `lastUpdatedBy` (user display) — read-only (if provided)

**Audit entry (read-only)**
- `changedAt` (datetime)
- `changedBy` (user identifier/display)
- `changeType` (CREATED/UPDATED)
- `diff` (before/after or changed fields) — **TBD based on backend**

### Read-only vs editable by state/role
- Vehicle profile:
  - Editable: preference fields + notes (only in edit mode and with permission)
  - Read-only: vehicleId, audit summary fields
- Estimate/WO:
  - All fields read-only

### Derived/calculated fields
- “Display rotation interval” derived string: `${rotationInterval} ${rotationIntervalUnit}` when both present; otherwise display whichever exists.
- “Last updated summary” derived from audit or record metadata if available.

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is not provided in this frontend issue. The story requires explicit Moqui service names/paths to be implementable; until clarified, the UI should be structured to call these services via a single adapter/composable.

### Load/view calls
- **Get vehicle preferences**
  - Input: `vehicleId`
  - Output: preference record or “not found/empty”
  - Expected outcomes:
    - 200 with record
    - 404/empty meaning no preferences exist yet (treated as empty state)

### Create/update calls
- **Upsert or Create/Update vehicle preferences**
  - Input: `vehicleId` + preference payload fields
  - Output: saved record with updated metadata
  - Expected outcomes:
    - 200/201 success
    - 400 validation error (field-level if possible)
    - 403 unauthorized
    - 409 conflict (concurrency) **TBD if versioning is used**
    - 500 unexpected

### Submit/transition calls
- None (no lifecycle state transitions defined for preferences).

### Error handling expectations
- Map backend HTTP/service errors to:
  - 403: show “You don’t have permission to edit vehicle preferences.”
  - 404 on load: show empty state
  - 400: show validation banner + field errors if provided
  - 409: show “Preferences were updated by someone else. Reload to continue.” (requires concurrency info; TBD)
  - 500: generic “Unable to save vehicle preferences right now.”

---

## 10. State Model & Transitions

### Allowed states (UI)
- `EMPTY` (no preference record exists)
- `VIEW` (record exists, read-only)
- `EDIT` (record being edited)
- `SAVING`
- `ERROR_LOADING`
- `ERROR_SAVING`

### Role-based transitions
- Users with edit permission:
  - `EMPTY/VIEW -> EDIT` via Edit/Add
  - `EDIT -> SAVING -> VIEW/EMPTY` via Save
- Users without edit permission:
  - Cannot enter EDIT; always VIEW/EMPTY read-only

### UI behavior per state
- `EMPTY`: show CTA to add (if permitted) else show message only.
- `VIEW`: show fields in read-only plus “Edit” (if permitted).
- `EDIT`: inputs enabled, Save/Cancel shown.
- `SAVING`: disable inputs and buttons; show spinner.
- `ERROR_LOADING`: show retry action.
- `ERROR_SAVING`: keep user in EDIT with inline banner.

---

## 11. Alternate / Error Flows

1. **Validation failure (client-side)**
   - rotation interval contains non-numeric characters
   - Prevent save; show field message; keep in edit mode.

2. **Validation failure (backend)**
   - Backend returns validation error payload
   - Display banner “Please correct the highlighted fields.”
   - Apply field errors when mappings exist; otherwise show backend message.

3. **Vehicle not found**
   - Vehicle profile route has invalid vehicleId
   - Show “Vehicle not found” and hide preference editor.

4. **Concurrent update**
   - Backend indicates conflict (409) (if supported)
   - Prompt user to reload; do not silently overwrite.

5. **Unauthorized access**
   - User can view vehicle but cannot edit preferences
   - Hide edit actions; if user tries direct action (e.g., deep link), show permission message.

6. **Empty state on Estimate/WO**
   - If no preferences exist, show “No vehicle preferences recorded” (read-only).

---

## 12. Acceptance Criteria

### Scenario 1: Create vehicle care preferences from vehicle profile
**Given** I am authenticated as a Service Advisor with permission to edit vehicle preferences  
**And** I am viewing a vehicle profile for a valid `vehicleId` that has no saved preferences  
**When** I click “Add preferences”  
**And** I enter a preferred tire brand and service notes  
**And** I click “Save”  
**Then** the system saves the preferences associated to that `vehicleId`  
**And** I see the saved values in read-only mode  
**And** I see a success confirmation message

### Scenario 2: Update existing preferences
**Given** I am authenticated as a Service Advisor with permission to edit vehicle preferences  
**And** a vehicle has existing saved preferences  
**When** I click “Edit”  
**And** I change the rotation interval and torque spec notes  
**And** I click “Save”  
**Then** the updated values are persisted and displayed in read-only mode  
**And** the “last updated” summary reflects the latest change (if provided by backend)

### Scenario 3: Client-side validation prevents save
**Given** I am editing vehicle preferences  
**When** I enter “abc” into the rotation interval field  
**And** I click “Save”  
**Then** the save is not submitted  
**And** I see an inline error stating rotation interval must be a whole number

### Scenario 4: Preferences visible on Estimate view
**Given** a vehicle has saved preferences and notes  
**And** an Estimate exists that references that vehicle  
**When** I view the Estimate  
**Then** I see the vehicle preferences and notes displayed read-only on the Estimate screen

### Scenario 5: Preferences visible on Work Order view/execution
**Given** a vehicle has saved preferences and notes  
**And** a Work Order exists that references that vehicle  
**When** I view the Work Order  
**Then** I see the vehicle preferences and notes displayed read-only on the Work Order screen

### Scenario 6: Audit history is accessible and shows changes
**Given** a vehicle has had its preferences created or updated  
**And** I am an authorized user to view audit history  
**When** I open “Change history” from the vehicle preferences section  
**Then** I see a list of changes including timestamp and actor  
**And** I can distinguish created vs updated entries (if provided)

### Scenario 7: Unauthorized user cannot edit
**Given** I am authenticated but do not have permission to edit vehicle preferences  
**When** I view a vehicle profile  
**Then** I can view existing preferences read-only  
**And** I do not see edit/add actions  
**And** if I attempt to save via any UI path, I receive a permission error and nothing is changed

---

## 13. Audit & Observability

### User-visible audit data
- Display “Last updated at” and “Last updated by” in Vehicle profile preferences section **if backend provides**.
- Provide “Change history” view showing:
  - timestamp
  - actor
  - change type (created/updated)
  - changed fields/before-after when available

### Status history
- Not applicable (no lifecycle states defined for preferences), but change history acts as status/audit trail.

### Traceability expectations
- All save operations should include correlation/trace id if the frontend framework supports it (pass through headers if configured).
- Do not log full free-form notes client-side in console logs (avoid PII leakage).

---

## 14. Non-Functional UI Requirements
- **Performance:** Preference load should not block entire vehicle profile rendering; load section asynchronously with a local spinner/skeleton.
- **Accessibility:** All form controls labeled; error messages associated with fields; keyboard navigable modal for audit history.
- **Responsiveness:** Works on tablet widths commonly used in bays; form fields stack appropriately.
- **i18n/timezone:** Display timestamps in shop/user locale/timezone (using existing frontend conventions). Currency not applicable.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Show a clear empty state (“No vehicle preferences recorded”) with an Add CTA when permitted; qualifies as safe because it’s pure UI ergonomics and does not alter domain policy. (Impacted: UX Summary, Alternate/Error Flows)
- SD-UX-ASYNC-SECTION-LOAD: Load preferences section asynchronously with local loading indicator; safe because it affects only perceived performance and not business behavior. (Impacted: UX Summary, Non-Functional)
- SD-ERR-GENERIC-BANNER: Use a generic save/load failure banner when backend does not provide field-mapped errors; safe because it’s standard error handling without changing rules. (Impacted: Service Contracts, Error Flows)

---

## 16. Open Questions

1. **Backend contract required (blocking):** What are the exact Moqui endpoints/services for:
   - load vehicle preferences by `vehicleId`
   - create/update (or upsert) vehicle preferences
   - load audit history  
   Please provide service names, parameter names, and response schema (including error format and field-level errors).

2. **Structured key/values vs fixed schema (blocking):** Are the preference fields a **fixed schema** (brand/line/interval/alignment/torque notes + free notes), or must the frontend support **dynamic key/value preferences** (EAV/JSON) that can change without UI redeploy?

3. **Authorization (blocking):** What permission(s)/role(s) govern:
   - viewing vehicle preferences
   - editing vehicle preferences
   - viewing audit history  
   Is this enforced via Moqui artifact auth, a permission service, or route-level guards?

4. **Audit payload detail (blocking):** Will audit history provide field-level diffs/before-after values, or only “who/when/what entity”? The UI design depends on this.

5. **Concurrency/versioning (blocking):** Does the preference record include a version field (optimistic locking) or `lastUpdatedStamp` that must be submitted on update? If yes, what is the expected 409 conflict behavior?

6. **Rotation interval unit enum (blocking):** What are the allowed units (MILES/KM/…)? Is unit required when interval is provided? Any min/max constraints?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Vehicle: Store Vehicle Care Preferences — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/166

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Vehicle: Store Vehicle Care Preferences
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/166
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Vehicle: Store Vehicle Care Preferences

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **to record vehicle-specific care preferences and service notes** so that **the shop can deliver service aligned with customer expectations**.

## Details
- Preferences: preferred tire brand/line, rotation interval, alignment preference, torque spec notes.
- Structured key/values + free-form notes.

## Acceptance Criteria
- Add/update preferences.
- Preferences visible on estimate/workorder.
- Changes audited.

## Integration Points (Workorder Execution)
- Workorder Execution displays preferences at estimate and during execution.

## Data / Entities
- VehiclePreference
- VehicleNote

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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

====================================================================================================