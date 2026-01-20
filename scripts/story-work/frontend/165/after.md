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

### Title
[FRONTEND] [STORY] Vehicle: Ingest Vehicle Updates from Workorder Execution (Admin Processing Log UI)

### Primary Persona
- **CRM Data Steward / Support Engineer** (internal user)
- Secondary: **System** (event ingestion service) observed/operated by support staff

### Business Value
Provide an **operational UI in Moqui** to view and troubleshoot `VehicleUpdated` event ingestion outcomes (success/duplicate/error/pending review), enabling faster resolution of data sync issues and ensuring CRM vehicle data accuracy over time.

---

## 2. Story Intent

### As a / I want / So that
- **As a** CRM Data Steward / Support Engineer  
- **I want** a Moqui screen to search and inspect VehicleUpdated event processing logs (by vehicleId/workorderId/eventId/status/time) and drill into the affected Vehicle  
- **So that** I can audit ingestion, detect duplicates/errors, and take appropriate follow-up actions when conflicts or not-found errors occur.

### In-scope
- Moqui **screens + forms + transitions** for:
  - Listing/searching ingestion processing records (ProcessingLog)
  - Viewing a single processing record’s details (including workorder reference)
  - Linking to a Vehicle detail view (read-only for this story unless already exists)
- UI handling for empty states, loading, and error responses
- Role-gated access to these operational screens

### Out-of-scope
- Implementing the actual event consumer / ingestion logic (backend responsibility)
- Defining/implementing the conflict resolution policy (blocked)
- Manual remediation actions (e.g., “reprocess event”, “override vehicle”) unless explicitly clarified

---

## 3. Actors & Stakeholders

- **Actor:** CRM Data Steward / Support Engineer
- **Stakeholders:**
  - Service Advisors (indirect consumers of accurate vehicle data)
  - Engineering/Operations (monitoring ingestion health)
  - Workorder Execution domain team (producer of `VehicleUpdated` events)

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS/Moqui UI.
- User has permission to access CRM operational ingestion screens (role TBD).

### Dependencies
- Backend must expose data to support this UI, at minimum:
  - A queryable `ProcessingLog` store for `VehicleUpdated` ingestion attempts
  - Vehicle read endpoint/service for drill-in by `vehicleId`
- Backend must record the following (per original story):
  - `eventId`, `workorderId` (and/or estimateId), `vehicleId`, timestamps, status, and details/error payload

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation (suggested): **CRM → Vehicles → Ingestion Logs (VehicleUpdated)**  
  (Exact menu placement depends on existing Durion frontend conventions; if none, add under CRM admin/tools section.)

### Screens to create/modify
1. **New Screen:** `apps/crm/screen/vehicle/IngestionLog.xml` (list/search)
2. **New Screen:** `apps/crm/screen/vehicle/IngestionLogDetail.xml` (detail view)
3. **Modify (if exists):** Vehicle detail screen to add “Ingestion Logs” related link/tab (optional; clarify)

### Navigation context
- List screen supports drill-down to:
  - ProcessingLog detail
  - Vehicle detail (by `vehicleId`)
- Breadcrumbs:
  - CRM > Vehicles > Ingestion Logs
  - CRM > Vehicles > Ingestion Logs > Log Detail

### User workflows

#### Happy path (investigate recent ingestion)
1. User opens **Ingestion Logs**
2. Filters by **Status = ERROR_NOT_FOUND** and date range
3. Opens a log record, reviews `details`, copies `eventId`, clicks vehicle link (if vehicle exists)
4. Uses workorder reference to coordinate with Workorder Execution team

#### Alternate path (duplicate verification)
1. Filter Status = DUPLICATE
2. Confirm same `eventId` appears with prior SUCCESS
3. No remediation action; informational only

#### Alternate path (pending review)
1. Filter Status = PENDING_REVIEW
2. Open record and review conflict info in `details`
3. Follow runbook externally (out-of-scope) unless remediation actions are later defined

---

## 6. Functional Behavior

### Triggers
- User navigates to the ingestion log list screen.
- User submits search/filter form.
- User clicks a row to open detail.
- User clicks `vehicleId` link to navigate to vehicle view.

### UI actions
- List view:
  - Filter fields: `eventId`, `workorderId` (and estimateId if provided), `vehicleId`, `status`, `receivedTimestamp` range
  - Sort by `receivedTimestamp desc` default
  - Pagination
- Detail view:
  - Read-only display of all ProcessingLog fields
  - Display `details` in a readable format (pretty JSON if stored as JSON/text)
  - Provide copy-to-clipboard for `eventId` and `workorderId` (UI ergonomics; safe default)

### State changes
- UI itself does not mutate ingestion state in this story.
- (If later clarified) transitions for manual review resolution would be separate.

### Service interactions
- Query ProcessingLog list with filters/pagination
- Load single ProcessingLog by `logId` (or composite key)
- Load Vehicle by `vehicleId` for drill-in link validation (optional; can just navigate)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Search form:
  - If `eventId` provided: must be UUID format (client-side validation + server-side enforcement)
  - If date range: `from <= to`
  - If `status` provided: must be one of allowed enums

### Enable/disable rules
- “View Vehicle” link:
  - Enabled when `vehicleId` present
  - If vehicle load returns 404, show non-blocking message “Vehicle not found in CRM” and keep user on log detail

### Visibility rules
- Screen is visible only to authorized roles (see Open Questions)
- `details` content must be displayed but avoid exposing sensitive data if present (masking rules unclear; see Open Questions)

### Error messaging expectations
- Network/service error: show user-friendly banner “Unable to load ingestion logs. Try again.” with correlation/request id if available.
- Unauthorized (403): redirect to unauthorized screen or show “You do not have access.”

---

## 8. Data Requirements

### Entities involved
- `ProcessingLog` (CRM operational/audit entity for ingestion)
- `Vehicle` (CRM master entity, read-only for drill-in)

### Fields

#### ProcessingLog (read-only in UI)
- `logId` (string/UUID) — required
- `eventId` (string/UUID) — required, indexed
- `workorderId` (string) — required, indexed  
  - **Note:** Acceptance criteria mention Workorder/Estimate ID; if both exist, add `estimateId` field (see Open Questions)
- `vehicleId` (string/UUID) — required, indexed
- `receivedTimestamp` (datetime, ISO-8601) — required
- `processedTimestamp` (datetime, ISO-8601) — optional
- `status` (enum) — required  
  Allowed values (per backend reference):  
  `SUCCESS | DUPLICATE | ERROR_VALIDATION | ERROR_NOT_FOUND | PENDING_REVIEW`
- `details` (json/text) — optional but recommended

#### Vehicle (for navigation)
- `vehicleId` (string/UUID) — required
- Display fields for context (if vehicle screen exists): `vin`, `unitNumber`, `description`, `licensePlate`, `mileage` (types/ownership per CRM)

### Read-only vs editable by state/role
- All fields in this story are **read-only**.

### Derived/calculated fields
- `processingLatencySeconds = processedTimestamp - receivedTimestamp` (optional display; safe default only if timestamps exist)

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact Moqui service names/endpoints are not provided in inputs; this story defines required frontend-facing contracts and flags as clarification if naming differs.

### Load/view calls

1. **Search Processing Logs**
- **Service (proposed):** `crm.vehicle.ProcessingLogSearch`
- **Inputs:**
  - `eventId?`, `workorderId?`, `vehicleId?`, `status?`
  - `receivedFrom?` (datetime), `receivedTo?` (datetime)
  - `pageIndex` (int), `pageSize` (int)
  - `orderBy` (string, default `-receivedTimestamp`)
- **Outputs:**
  - `items[]` with ProcessingLog summary fields
  - `totalCount`

2. **Get Processing Log Detail**
- **Service (proposed):** `crm.vehicle.ProcessingLogGet`
- **Inputs:** `logId` (required)
- **Outputs:** full ProcessingLog record

3. **Get Vehicle (for drill-in)**
- **Service (existing or proposed):** `crm.vehicle.VehicleGet`
- **Inputs:** `vehicleId`
- **Outputs:** vehicle fields for vehicle detail screen

### Create/update calls
- None.

### Submit/transition calls
- None.

### Error handling expectations
- `400` invalid filter inputs → show inline validation messages (e.g., “Invalid UUID”)
- `401/403` → show unauthorized handling and prevent data display
- `404` on log detail → show “Log record not found” with back link
- `5xx` → generic error banner, preserve filters for retry

---

## 10. State Model & Transitions

### Allowed states (ProcessingLog.status)
- `SUCCESS`
- `DUPLICATE`
- `ERROR_VALIDATION`
- `ERROR_NOT_FOUND`
- `PENDING_REVIEW`

### Role-based transitions
- None (read-only UI).

### UI behavior per state
- List:
  - Display status badge for quick scanning
  - Default filter: none (shows recent by date) OR status not filtered (safe default; see Applied Safe Defaults)
- Detail:
  - For `PENDING_REVIEW`, show prominent notice “Requires review (policy pending)” and display details
  - For error statuses, show error summary extracted from `details` when possible

---

## 11. Alternate / Error Flows

### Validation failures
- User enters invalid UUID in `eventId` filter:
  - Block search submission client-side
  - Show “Event ID must be a UUID”

### Concurrency conflicts
- Not applicable (read-only).

### Unauthorized access
- User without required role opens URL directly:
  - Return 403 from backend; UI shows Unauthorized page/message

### Empty states
- No logs found for filters:
  - Show “No ingestion logs match your filters” and suggest clearing filters

---

## 12. Acceptance Criteria

### Scenario 1: View recent ingestion logs
**Given** I am an authorized CRM support user  
**When** I navigate to CRM → Vehicles → Ingestion Logs  
**Then** I see a paginated list of ProcessingLog records sorted by receivedTimestamp descending  
**And** each row shows eventId, workorderId, vehicleId, receivedTimestamp, status

### Scenario 2: Filter logs by status and vehicleId
**Given** I am on the Ingestion Logs screen  
**When** I filter by `status = ERROR_NOT_FOUND` and `vehicleId = "V-999"` and submit  
**Then** only matching records are shown  
**And** the active filters remain visible after results load

### Scenario 3: View log detail
**Given** a ProcessingLog record exists with `logId = "L-1"`  
**When** I open the record detail view  
**Then** I see all ProcessingLog fields including details and timestamps  
**And** I can copy eventId and workorderId from the page

### Scenario 4: Vehicle drill-in link handles missing vehicle
**Given** I am viewing a ProcessingLog detail with `vehicleId = "V-999"`  
**And** the Vehicle does not exist in CRM  
**When** I click “View Vehicle”  
**Then** the UI shows “Vehicle not found in CRM”  
**And** I remain on the ProcessingLog detail view

### Scenario 5: Client-side validation prevents invalid search
**Given** I am on the Ingestion Logs screen  
**When** I enter `eventId = "not-a-uuid"` and submit  
**Then** the search is not executed  
**And** I see an inline validation message indicating the UUID format requirement

### Scenario 6: Access control enforced
**Given** I am authenticated but do not have the required CRM support permission  
**When** I navigate to the Ingestion Logs URL  
**Then** I receive an unauthorized experience (403 handling)  
**And** no ProcessingLog data is displayed

---

## 13. Audit & Observability

### User-visible audit data
- ProcessingLog records are the audit surface:
  - Show `receivedTimestamp`, `processedTimestamp`, `status`, and `workorderId` reference
  - Display correlation identifiers when available (eventId)

### Status history
- Not in scope unless ProcessingLog stores multiple attempts; if it does, UI may show “attempt count” (clarify).

### Traceability expectations
- From a log detail, user can:
  - Copy `eventId`
  - Copy `workorderId` (and `estimateId` if present)
  - Navigate to Vehicle (if exists)

---

## 14. Non-Functional UI Requirements

- **Performance:** List screen should load first page within 2 seconds for typical usage (assuming indexed fields).
- **Accessibility:** All interactive elements keyboard-navigable; status indicated with text (not color only).
- **Responsiveness:** Works on tablet viewport; list uses responsive table/card mode per Quasar defaults.
- **i18n/timezone:** Timestamps displayed in user locale/timezone (do not convert business logic; display only).
- **Security:** Do not render raw HTML from `details`; treat as text/JSON to prevent injection.

---

## 15. Applied Safe Defaults

- **SD-UX-EMPTY-STATE**
  - **Assumed:** Provide standard empty-state messaging and “Clear filters” action.
  - **Why safe:** UI-only ergonomics; does not affect domain policy.
  - **Impacted sections:** UX Summary, Alternate / Error Flows, Acceptance Criteria

- **SD-UX-PAGINATION**
  - **Assumed:** Default pagination (pageSize 25) and sort by `receivedTimestamp desc`.
  - **Why safe:** Presentation default; does not change stored data or policies.
  - **Impacted sections:** UX Summary, Functional Behavior, Service Contracts

- **SD-ERR-GENERIC-MAPPING**
  - **Assumed:** Map 400/401/403/404/5xx to standard UI banners and inline messages.
  - **Why safe:** Standard error handling consistent with backend HTTP semantics.
  - **Impacted sections:** Business Rules, Service Contracts, Alternate / Error Flows

---

## 16. Open Questions

1. **Blocking (Policy):** What is the conflict resolution policy for `VehicleUpdated` events (e.g., mileage decreases, VIN changes)? Does `PENDING_REVIEW` exist and when is it used vs last-write-wins?
2. **Blocking (Data):** Does the ProcessingLog include **estimateId** in addition to workorderId (story mentions Workorder/Estimate ID)? If yes, what field name should UI use?
3. **Blocking (Security):** What roles/scopes are required to view ingestion logs (e.g., `crm.audit.read`, `crm.vehicle.read`, internal support role)? Any restrictions by location/org?
4. **Blocking (Backend contract):** What are the actual Moqui service names / screen paths / endpoints to query ProcessingLog and Vehicle? Are these entity-backed (entity-find) or REST endpoints?
5. **Risk (PII/sensitive):** Can `details` contain sensitive customer data or notes? If yes, what masking/redaction rules apply in UI?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Vehicle: Ingest Vehicle Updates from Workorder Execution  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/165  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Vehicle: Ingest Vehicle Updates from Workorder Execution

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative
As a **System**, I want **to update vehicle details captured during service (VIN correction, mileage, notes)** so that **CRM remains accurate over time**.

## Details
- Accept updates via event envelope from Workorder Execution.
- Apply idempotency and audit against workorder reference.

## Acceptance Criteria
- Vehicle updates are processed once.
- Audit includes source Workorder/Estimate ID.
- Conflicts handled (last-write or review queue; define policy).

## Integration Points (Workorder Execution)
- Workorder Execution emits VehicleUpdated events.
- CRM persists updates and exposes updated snapshot.

## Data / Entities
- Vehicle
- EventEnvelope
- ProcessingLog

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