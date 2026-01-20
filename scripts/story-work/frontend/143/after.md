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

## 1. Story Header

### Title
[FRONTEND] [STORY] Timekeeping: Export Approved Time for Accounting/Payroll

### Primary Persona
Accounting Clerk (Accounting user)

### Business Value
Enable payroll and cost accounting processing by exporting **only** approved time entries with payroll-facing identifiers, with a complete audit trail of export activity.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Accounting Clerk  
- **I want** to export approved time entries by date range and location(s) in CSV or JSON  
- **So that** payroll processing and cost accounting can be performed using payroll-facing identifiers and a stable definition of “APPROVED”.

### In-scope
- A Moqui screen flow to:
  - Choose **date range** (inclusive) and **one or more locations**
  - Choose **format** (CSV or JSON)
  - Request an export
  - Download the resulting file (or receive the file directly, depending on backend behavior)
- Display export outcome:
  - records exported count
  - records skipped due to missing mappings (if available)
  - correlation/export id (if provided)
- User-visible audit visibility (at least “Export requested by/when/parameters/status”)

### Out-of-scope
- Creating/maintaining payroll identity mappings (employee/location → payroll IDs)
- Approving/unapproving time entries
- Scheduling recurring exports
- Editing/exporting non-approved time entries
- Building backend export logic (referenced backend story only)

---

## 3. Actors & Stakeholders
- **Primary Actor:** Accounting Clerk
- **Stakeholders:**
  - Payroll processors (downstream consumers of export file)
  - Cost accounting analysts
  - Auditors/Compliance (require immutable audit record)
  - People/Timekeeping domain owners (system of record for time entry + approval)

---

## 4. Preconditions & Dependencies
- User is authenticated in the Moqui frontend.
- User has permission to export time for accounting/payroll (exact permission string **TBD**, see Open Questions).
- Backend provides an export capability supporting:
  - date range (inclusive)
  - location(s)
  - format (CSV/JSON)
  - exporting **only APPROVED** time entries
  - skipping unmapped entries and recording a remediation artifact (backend behavior)
- Locations list is available to the frontend (either via accounting export API “locations” endpoint or existing location directory endpoint).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Accounting → Timekeeping Exports** (menu item)
- Optional deep link: `/accounting/timekeeping/export` (route to be confirmed with repo conventions)

### Screens to create/modify
- **New Screen:** `accounting/timekeeping/ExportApprovedTime.xml` (name illustrative; final per repo conventions)
  - Form to capture export parameters
  - Results panel for last-run outcome
  - Download link/button when file is ready/returned
- **Optional New Screen:** `accounting/timekeeping/ExportHistory.xml`
  - List prior export requests (if backend provides history/audit query)
- **Optional Component:** reusable “download file” handler if repo uses common patterns

### Navigation context
- Breadcrumb: Accounting → Timekeeping Exports → Export Approved Time
- From this screen, user can:
  - Run a new export
  - (Optional) view export history / audit log

### User workflows
**Happy path (synchronous download):**
1. User opens Export Approved Time screen
2. Selects start date, end date, location(s), and format
3. Clicks “Export”
4. System validates inputs client-side and submits
5. Backend responds with file payload/stream
6. Browser downloads file; UI shows success + counts

**Alternate path (async job + later download):**
1–3 same
4. Backend responds with `exportId` + status “QUEUED/PROCESSING”
5. UI polls or provides “Refresh status”
6. When READY, user clicks Download

**Alternate path (empty result):**
- Export completes successfully; file contains CSV header only or empty JSON array; UI indicates “0 exported”

---

## 6. Functional Behavior

### Triggers
- User clicks **Export** on the export form.

### UI actions
- Date pickers for `startDate` and `endDate`
- Multi-select for `locationId` (one or more)
- Format selector: `CSV` | `JSON`
- Export button
- Status area:
  - success/failure message
  - exported count
  - skipped count (if provided)
  - download action (if applicable)

### State changes (frontend)
- `idle` → `validating` → `submitting` → `complete` or `error`
- If async: `complete` may be replaced by `queued/processing/ready/error`

### Service interactions (Moqui)
- On screen load: fetch available locations (dependency; endpoint TBD)
- On export submit:
  - invoke backend export service/endpoint
  - handle binary download or job response
- On history display (optional): fetch audit/history list

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `startDate` required
- `endDate` required
- `endDate >= startDate` (inclusive range)
- At least one `locationId` required (unless backend supports “all locations”; TBD)
- `format` required (CSV default allowed only if confirmed as safe; otherwise no default)

### Enable/disable rules
- Export button disabled while submitting
- Download button visible/enabled only when backend indicates file ready or a file blob is available

### Visibility rules
- If backend returns “empty dataset”, show informational message and still provide download (because output is meaningful/auditable)
- If backend reports skipped/unmapped entries count/details, show summary; do **not** display PII-heavy details by default

### Error messaging expectations
- Parameter validation errors: show inline field error + top-level summary
- Unauthorized (403): show “You do not have permission to export approved time.”
- Backend unavailable (503): show retry guidance
- Server validation: show backend error code and user-friendly message (do not expose sensitive data)

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **TimeEntry** (read/exported; owned by People domain; not directly edited here)
- **PayrollIdentityMap** (conceptual mapping; owned by Accounting; not managed here)
- **ExportAuditEvent / ExportRequest** (conceptual; for history/audit display; owned by Accounting)

### Fields (type, required, defaults)
**Export Request Inputs**
- `startDate` (date, required)
- `endDate` (date, required)
- `locationIds` (List<id>, required; min 1) — *TBD if optional*
- `format` (enum: `CSV` | `JSON`, required)
- (Optional) `timeZone` (IANA string) — if backend needs it; TBD
- (Optional) `businessUnitId` — if required for accounting scoping; TBD

**Export Response (minimum UI needs)**
- `status` (enum: SUCCESS|FAILED|QUEUED|PROCESSING|READY) — TBD
- `recordsExportedCount` (number)
- `recordsSkippedCount` (number) — especially missing mapping
- `correlationId` / `exportId` (string)
- `downloadUrl` (string) OR binary payload

### Read-only vs editable by state/role
- All response fields are read-only
- Inputs editable only when not submitting; editable after completion to run another export

### Derived/calculated fields
- Display-only “Date range (inclusive)” summary
- “Locations selected” summary

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract details are not provided in the frontend issue text; the backend story describes behavior but not concrete endpoints. Frontend implementation requires confirmation of actual Moqui service names / REST endpoints and response shapes.

### Load/view calls
- `GET /locations` (or equivalent) to populate location selector — **TBD**
- (Optional) `GET /accounting/timekeeping/exports` to show history — **TBD**

### Create/update calls
- Export request (one of the following patterns — **TBD which is correct**):
  1) `POST /accounting/timekeeping/exports` with JSON body → returns file stream OR job metadata
  2) Moqui service call (e.g., `accounting.time.exportApprovedTime`) returning `fileId`/`downloadUrl`

### Submit/transition calls
- If async:
  - `GET /accounting/timekeeping/exports/{exportId}` to check status
  - `GET /accounting/timekeeping/exports/{exportId}/download` to download file

### Error handling expectations
- `400` invalid params (e.g., endDate < startDate) → map to inline errors where possible
- `403` forbidden → show permission error
- `503` upstream unavailable (people/timekeeping) → show retry + do not cache failure as permanent
- Empty result should be `200` with empty dataset and still downloadable content

---

## 10. State Model & Transitions

### Allowed states (frontend-local)
- `IDLE`
- `VALIDATION_ERROR`
- `SUBMITTING`
- `SUCCESS_READY` (file ready / downloaded)
- `ASYNC_QUEUED` (if job created)
- `ASYNC_PROCESSING`
- `ASYNC_READY`
- `FAILED`

### Role-based transitions
- Only authorized Accounting users can move from `IDLE` → `SUBMITTING` (enforced by backend; frontend should also hide/disable entry points if permission is known)

### UI behavior per state
- `SUBMITTING`: disable inputs and show progress
- `SUCCESS_READY`: show success summary + download result (or “downloaded” confirmation)
- `ASYNC_*`: show status + refresh/poll controls + enable download only when READY
- `FAILED`: show error summary + keep user inputs for retry

---

## 11. Alternate / Error Flows

### Validation failures
- Missing dates, invalid range, no locations selected → prevent submit, show inline errors

### Concurrency conflicts
- Not expected for export request; if backend returns `409` due to idempotency conflict, show message:
  - “An export with these parameters is already in progress. Refresh status or download existing export.” (only if backend supports this concept; TBD)

### Unauthorized access
- If screen accessed without permission:
  - hide export form and show “Not authorized” message
  - do not call export endpoint

### Empty states
- No locations available:
  - show blocking empty state “No locations available to export. Contact an administrator.”
- No matching approved entries:
  - show success with 0 exported; allow download

---

## 12. Acceptance Criteria

### Scenario 1: Export CSV for date range and location(s) includes only approved entries
**Given** the user has permission to export approved time  
**And** there exist time entries in the selected date range and locations with mixed approval states  
**When** the user requests an export in `CSV` format  
**Then** the export result contains only entries in state `APPROVED`  
**And** the UI presents a successful completion message including `recordsExportedCount`  
**And** the user can download the CSV output.

### Scenario 2: Export JSON returns success with empty dataset when no approved entries match
**Given** the user has permission to export approved time  
**And** there are no `APPROVED` time entries in the selected date range and locations  
**When** the user requests an export in `JSON` format  
**Then** the request succeeds  
**And** the UI indicates `recordsExportedCount = 0`  
**And** the downloaded JSON content is an empty array (or equivalent empty dataset per contract).

### Scenario 3: Client blocks invalid date range
**Given** the user is on the Export Approved Time screen  
**When** the user sets `endDate` earlier than `startDate`  
**And** clicks Export  
**Then** the UI prevents submission  
**And** shows a validation error stating the end date must be on or after the start date.

### Scenario 4: Unauthorized user cannot export
**Given** the user lacks the required export permission  
**When** the user navigates to the Export Approved Time screen  
**Then** the export action is not available  
**And** any attempted export request results in a clear “not authorized” message (403-handling).

### Scenario 5: Missing payroll/location mapping causes entries to be skipped (reported outcome)
**Given** there is an `APPROVED` time entry in range  
**And** the backend determines it has missing employee and/or location mapping for payroll export  
**When** the user requests an export  
**Then** that entry is not included in the downloaded output  
**And** the UI displays that some entries were skipped due to missing mappings (count and generic reason at minimum).

### Scenario 6: Export activity is audited (user-visible confirmation)
**Given** the user successfully completes an export request  
**When** the export completes  
**Then** the UI displays a correlation/export identifier (if provided)  
**And** the export request appears in export history/audit view (if provided), including requester, timestamp, parameters, and outcome.

---

## 13. Audit & Observability

### User-visible audit data
- Show at minimum after completion:
  - Requested by (current user)
  - Requested at (timestamp)
  - Parameters (date range, locations, format)
  - Outcome (success/failure) and counts
  - Correlation/export ID (if provided)

### Status history
- If backend provides export history:
  - list includes: timestamp, requester, date range, locations, format, status, exported/skipped counts, correlation/export id

### Traceability expectations
- Frontend includes `X-Correlation-Id` header if the project standard exists (TBD)
- Ensure no export file contents are logged in browser console

---

## 14. Non-Functional UI Requirements
- **Performance:** locations list should load within 2 seconds under normal conditions; export submit should show immediate progress feedback
- **Accessibility:** keyboard navigable form controls; label all inputs; announce errors (ARIA) in Quasar components
- **Responsiveness:** usable on tablet-sized screens (accounting backoffice)
- **i18n/timezone:** dates displayed in user locale; clarify whether export date interpretation uses store/location timezone or user timezone (TBD)

---

## 15. Applied Safe Defaults
- none

---

## 16. Open Questions

1. **Backend contract (blocking):** What are the exact Moqui endpoints/services for:
   - requesting an export (path/service name, request/response schema)
   - downloading the file (binary vs URL)
   - async status polling (if applicable)?
2. **Delivery mode (blocking):** Is export **synchronous download** or an **async job** that becomes downloadable later (or both)?
3. **Authorization (blocking):** What permission(s)/scope(s) gate:
   - viewing the export screen
   - executing the export?
4. **Location selector source (blocking):** Which endpoint/service provides the list of locations the accounting clerk may export for, and what filtering (by business unit / user access) is required?
5. **Time zone semantics (blocking):** Are `startDate/endDate` interpreted in:
   - location timezone,
   - business unit timezone,
   - or user timezone?
6. **Skipped-entry reporting (non-blocking but important):** Should UI show only counts, or also provide a downloadable “skipped report” (e.g., missing mapping details) for remediation?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Timekeeping: Export Approved Time for Accounting/Payroll  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/143  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Timekeeping: Export Approved Time for Accounting/Payroll

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative
As an **Accounting user**, I want **to export approved time** so that **it can be used for payroll or cost accounting**.

## Details
- Export by date range and location.
- Provide CSV/JSON output.

## Acceptance Criteria
- Only approved time is exported.
- Export includes person identifiers and location.
- Export activity is audited.

## Integration Points (workexec/shopmgr)
- None required initially.

## Data / Entities
- TimeEntry

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: People Management

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