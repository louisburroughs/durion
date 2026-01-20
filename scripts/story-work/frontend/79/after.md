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

# 1. Story Header

## Title
[FRONTEND] [STORY] Workexec: Retrieve and Display Estimates for Customer/Vehicle

## Primary Persona
Service Advisor

## Business Value
Enable Service Advisors to quickly find and review estimates tied to a specific customer and/or vehicle, understand current status and totals, and navigate into an estimate detail view to support downstream actions (appointment creation, checkout) with correct context and permissions.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Service Advisor  
- **I want** to retrieve and view estimate lists for a given customer or vehicle, and drill into an estimate to see its details  
- **So that** I can confidently proceed from quote review to scheduling or checkout with accurate status, totals, and notes

## In-scope
- Customer- or vehicle-scoped estimate search entry points (from customer or vehicle context)
- Paginated list view of estimates with status, totals, and “last updated”
- Estimate detail view with line items and notes
- Permission-aware visibility (only show estimates user is allowed to view)
- Handling of “expired” estimates (warning + disable “Create Appointment” action) **if and only if** the backend provides expiry/validity fields needed to determine this
- “Retry” behavior and user-friendly error messages for service failures

## Out-of-scope
- Editing estimate line items or changing estimate status (approval/decline/reopen)
- Estimate cloning/repricing (backend reference mentions it; not requested in provided frontend story)
- Appointment creation UI itself (only link/transition with preserved parameters)
- SSE/event subscription implementation unless already standardized in this frontend repo patterns (unknown from provided inputs)

---

# 3. Actors & Stakeholders
- **Primary user:** Service Advisor
- **Secondary:** Shop Manager (viewing across locations; permission scope not defined here)
- **System actors:** Workexec backend APIs (estimate read)
- **Stakeholders:** Operations (speed), Compliance/Audit (traceability), Customer experience (accuracy)

---

# 4. Preconditions & Dependencies
- User is authenticated in the POS frontend.
- Workexec backend provides estimate read APIs (list + detail). (Exact paths/params must be confirmed; see Open Questions.)
- Customer and Vehicle context pages exist (or an equivalent global search exists) to provide `customerId` and/or `vehicleId`.
- Permission model exists server-side; frontend must handle 401/403 gracefully and avoid leaking cross-location data.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From **Customer** context: “Estimates” action/tab opens estimates list filtered by `customerId`.
- From **Vehicle** context: “Estimates” action/tab opens estimates list filtered by `vehicleId`.
- Optional: if a global search already exists, allow navigating into the same screen with query params.

## Screens to create/modify (Moqui)
Create/modify Moqui screens under the workexec area (exact location depends on repo conventions; to be aligned with existing menu/screen tree):
1. **Estimate List Screen**
   - Route accepts `customerId` and/or `vehicleId` (at least one required)
   - Displays list of `EstimateSummary`
2. **Estimate Detail Screen**
   - Route accepts `estimateId` (required)
   - Displays `EstimateDetail` including lines + notes
3. Update Customer/Vehicle screens to add navigation transition(s) into Estimate List Screen while preserving context.

## Navigation context
- Preserve query params on drilldown and “Back to list”:
  - `customerId`, `vehicleId`
  - Any applied filters/sort/page state (at minimum page + filters; mechanism depends on frontend conventions)

## User workflows
### Happy path
1. Service Advisor opens customer (or vehicle) record
2. Clicks “Estimates”
3. Sees a list of estimates (open/draft/approved per rules)
4. Clicks an estimate row
5. Sees estimate detail with lines and notes
6. Navigates back to the list, preserving filter/sort/page

### Alternate paths
- No estimates found → empty state explaining no results for that customer/vehicle
- Unauthorized estimate access → show permission denial state; do not reveal estimate content
- Backend unavailable → show retry + optional cached timestamp if supported by frontend caching layer

---

# 6. Functional Behavior

## Triggers
- Entering the estimate list screen with `customerId` and/or `vehicleId`
- Changing filters/sort/pagination on list
- Selecting an estimate row
- Entering the estimate detail screen with `estimateId`

## UI actions
### Estimate List
- Load list automatically on screen load
- Provide controls for:
  - Status filter (at minimum: Draft/Open/Approved if those exist)
  - Sort by last updated (default descending)
  - Pagination controls

### Estimate Detail
- Load detail automatically on screen load
- Show:
  - Header context (estimate identifier, status, last updated)
  - Line items
  - Notes (as provided)
- Provide:
  - “Back” navigation to list with preserved context

## State changes (frontend view-state)
- List screen maintains view-state: `loading | loaded | error | empty`
- Detail screen maintains view-state: `loading | loaded | error | notFound | forbidden`

## Service interactions
- List: call “estimate list” endpoint with either customerId or vehicleId
- Detail: call “estimate detail” endpoint by estimateId
- Error mapping based on HTTP status:
  - 401 → show “Session expired” pattern and route to login if that exists
  - 403 → show “You do not have access to this estimate”
  - 404 → show “Estimate not found”
  - 5xx / network → show “Unable to load estimates; service temporarily unavailable” with Retry

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- List screen requires at least one of:
  - `customerId` OR `vehicleId`
- If neither provided:
  - Show blocking inline error “Select a customer or vehicle to view estimates.” and do not call backend.

## Enable/disable rules
- Drilldown row action is enabled only when row has a valid `estimateId`.
- If backend supplies `isExpired` or `expiresAt`:
  - Show an “Expired” warning banner on detail when expired.
  - Disable “Create Appointment” action if present on this screen (only if this story includes that action; see Open Questions).

## Visibility rules
- Only show estimates returned by backend; do not attempt to “merge” across locations client-side.
- If backend provides line-level internal-only fields, frontend must not render them (requires contract clarity; see Open Questions).

## Error messaging expectations
- Use consistent POS error banner/toast conventions (per repo).
- Do not expose backend stack traces or internal identifiers beyond estimateId.

---

# 8. Data Requirements

## Entities involved (frontend read models)
- `EstimateSummary`
- `EstimateDetail`
- `WorkexecRef` (only if needed for cross-links; unclear)

## Fields
Because the provided frontend story does not define schemas, this story requires a confirmed contract. **Minimum required fields** to satisfy the user-visible requirements:

### EstimateSummary (list)
- `estimateId` (string/number; required)
- `status` (string/enum; required)
- `grandTotal` (number/decimal string; required)
- `currencyUomId` (string; required if totals displayed with currency)
- `lastUpdatedAt` (ISO-8601 datetime; required)

Optional (if backend provides):
- `customerId`, `vehicleId`
- `summaryText` / `serviceDescription`
- `expiresAt` or `isExpired`

### EstimateDetail (detail)
- `estimateId` (required)
- `status` (required)
- `lastUpdatedAt` (required)
- `notes` (string/array; optional)
- `lineItems[]` (required for “shows lines”)
  - each line: `type` (service/part), `description`, `quantity`, `unitPrice`, `lineTotal`, `declined?` (if supported)

Optional:
- Totals breakdown (subtotal/taxes/fees) if backend provides; not strictly required by this frontend issue’s AC but referenced in backend story.

## Read-only vs editable
- All fields in this story are **read-only** (no editing).

## Derived/calculated fields
- Client may derive display-only values:
  - “Last updated” formatting in user timezone
  - Empty state counts
- Client must not recalculate totals; display backend totals as source of truth.

---

# 9. Service Contracts (Frontend Perspective)

> **Note:** Endpoint paths in backend reference are inconsistent with other docs in this prompt (e.g., `/api/estimates/...` vs `/estimates?...`). Contract must be confirmed.

## Load/view calls
- List by customer:
  - `GET <TBD>/estimates?customerId={customerId}&status=<optional>&pageIndex=<optional>&pageSize=<optional>&sort=<optional>`
- List by vehicle:
  - `GET <TBD>/estimates?vehicleId={vehicleId}&status=<optional>&pageIndex=<optional>&pageSize=<optional>&sort=<optional>`
- Detail:
  - `GET <TBD>/estimates/{estimateId}`

## Create/update calls
- None (read-only story)

## Submit/transition calls
- None (read-only story)

## Error handling expectations
- `200` list returns:
  - array + pagination metadata OR a paginated envelope (TBD)
- `200` detail returns EstimateDetail
- `400` for invalid query params (e.g., missing required identifiers)
- `401/403/404/409/5xx` handled as described in Functional Behavior

---

# 10. State Model & Transitions

This story is **display-only**; it does not initiate estimate state transitions.

## Allowed states (displayed)
- Must render whatever `status` values backend returns.
- Provide a stable mapping to user-readable labels (e.g., `DRAFT` → “Draft”), but do not hardcode a full state machine unless backend publishes canonical enum list for frontend.

## Role-based transitions
- None initiated by this UI in this story.

## UI behavior per state
- Always show list rows regardless of status, subject to filters and permissions.
- If status implies “locked” (approved/etc.), still viewable unless backend denies.

---

# 11. Alternate / Error Flows

## Validation failures
- Missing both `customerId` and `vehicleId`:
  - Show blocking message; do not call backend.

## Concurrency conflicts
- If detail endpoint returns `409 Conflict` (e.g., estimate version mismatch) for read calls:
  - Treat as retryable error with message “Estimate changed; reload to view latest.” (Only if backend actually uses 409 for reads; otherwise ignore.)

## Unauthorized access
- 403 on list:
  - Show “You do not have access to view estimates for this customer/vehicle.”
- 403 on detail:
  - Show “You do not have access to this estimate.” and do not show any estimate data.

## Empty states
- 200 with empty list:
  - Show “No estimates found for this customer/vehicle.”
  - Provide navigation back to customer/vehicle record.

---

# 12. Acceptance Criteria

### Scenario 1: View estimates for a customer
**Given** I am authenticated as a Service Advisor  
**And** I navigate to the Estimates list from a Customer record with `customerId`  
**When** the Estimates list screen loads  
**Then** the system requests estimates filtered by that `customerId`  
**And** I see a list of estimates including each estimate’s ID, status, grand total, and last updated timestamp

### Scenario 2: View estimates for a vehicle
**Given** I am authenticated as a Service Advisor  
**And** I navigate to the Estimates list from a Vehicle record with `vehicleId`  
**When** the Estimates list screen loads  
**Then** the system requests estimates filtered by that `vehicleId`  
**And** I see a list of estimates including each estimate’s ID, status, grand total, and last updated timestamp

### Scenario 3: Drill into estimate details
**Given** I am viewing an Estimates list with at least one estimate row  
**When** I select an estimate row  
**Then** I am navigated to the Estimate detail screen for that `estimateId`  
**And** the system loads the estimate detail  
**And** I can see line items and notes returned by the backend

### Scenario 4: Preserve navigation context
**Given** I opened the Estimates list with a `customerId` (and optional filters/sort/page)  
**When** I navigate into an Estimate detail and then go back to the list  
**Then** the list screen restores the same `customerId` context  
**And** my previously selected filters/sort/page are preserved

### Scenario 5: No estimates found
**Given** I am viewing the Estimates list for a valid `customerId` or `vehicleId`  
**When** the backend returns an empty result set  
**Then** I see an empty state indicating no estimates were found  
**And** the UI does not display an error

### Scenario 6: Unauthorized access to estimate detail
**Given** I attempt to open an estimate detail  
**When** the backend responds with HTTP 403  
**Then** I see a permission denied message  
**And** no estimate line items or notes are displayed

### Scenario 7: Backend service unavailable
**Given** I am on the Estimates list screen  
**When** the backend request fails with a network error or HTTP 5xx  
**Then** I see an error message “Unable to load estimates; service temporarily unavailable”  
**And** I can select “Retry” to re-attempt loading

---

# 13. Audit & Observability

## User-visible audit data
- Display `lastUpdatedAt` on list and detail (as required by story)

## Status history
- Not required by this story (do not implement transitions/history UI)

## Traceability expectations
- Frontend logs should include:
  - screen name (estimateList / estimateDetail)
  - correlation/request id if available in existing HTTP client layer
  - identifiers used (customerId/vehicleId/estimateId) **only** as non-sensitive operational identifiers

---

# 14. Non-Functional UI Requirements

## Performance
- List and detail initial load should be non-blocking with loading indicators.
- Paginate results to avoid rendering very large lists.

## Accessibility
- All interactive elements keyboard-accessible.
- Status and error messages announced via accessible alerts per Quasar standards.

## Responsiveness
- Works on tablet and desktop POS layouts.

## i18n/timezone/currency
- Format datetimes in the user’s configured timezone.
- Display currency using provided `currencyUomId` (if contract supports); otherwise show totals as raw numbers and flag as clarification.

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standard empty-state copy and “Back” navigation; qualifies as safe UX ergonomics; impacts UX Summary, Error Flows, Acceptance Criteria.
- SD-UX-PAGINATION: Use standard pagination controls with default page size (repo standard); qualifies as safe UX ergonomics; impacts UX Summary, Functional Behavior, Service Contracts.
- SD-ERR-HTTP-MAP: Map 401/403/404/5xx to standard user messages; qualifies as safe because it is generic error handling and does not change domain policy; impacts Functional Behavior, Error Flows.

---

# 16. Open Questions

1. **API contract (blocking):** What are the exact backend endpoints and response shapes for:
   - list estimates by customer/vehicle
   - get estimate detail  
   (The prompt contains multiple conflicting examples: `/api/estimates/...` vs `/estimates?...`.)

2. **Status set (blocking):** What are the canonical estimate statuses that the frontend should display/filter on?  
   (Backend reference lists an extended set including `OPEN`, `PENDING_CUSTOMER`, etc., while the workexec approval doc lists `DRAFT/APPROVED/DECLINED/EXPIRED`.)

3. **Totals/currency (blocking):** Does `EstimateSummary` include `currencyUomId` and is `grandTotal` a decimal string or number? How should rounding be handled/displayed?

4. **Notes/lines schema (blocking):** What is the structure of “lines and notes” in `EstimateDetail` (fields, types, ordering)? Are any fields internal-only that must be excluded?

5. **Navigation/link targets (blocking):** What exact “links preserved” requirement applies:
   - preserve only list query params on back navigation, or
   - include deep-links to appointment creation / checkout flows from estimate detail?

6. **Caching requirement:** Should the frontend implement any client-side caching for list/detail (and show “View cached estimates from [timestamp]”), or is this out of scope for frontend and handled by backend/proxy?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Workexec: Retrieve and Display Estimates for Customer/Vehicle  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/79  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Retrieve and Display Estimates for Customer/Vehicle

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want to view estimates for a customer/vehicle so that I can move from quote to appointment or checkout.

## Details
- List open/draft/approved estimates.
- Show totals, status, and last updated.

## Acceptance Criteria
- Estimates list shown with statuses.
- Drilldown shows lines and notes.
- Links preserved.

## Integrations
- Workexec provides estimate read APIs/events.

## Data / Entities
- EstimateSummary, EstimateDetail, WorkexecRef

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