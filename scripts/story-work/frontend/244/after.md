STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Fulfillment: Mechanic Executes Picking (Scan + Confirm)

### Primary Persona
Mechanic / Technician (shop-floor user fulfilling parts for a work order)

### Business Value
Reduce picking errors and improve fulfillment traceability by enabling mechanics to scan items/locations, confirm picked quantities, and record an auditable pick confirmation tied to a work order.

---

## 2. Story Intent

### Narrative (As a / I want / So that)
- **As a** Mechanic
- **I want** to pick required parts for a work order by scanning and confirming items
- **So that** the system records an accurate, auditable “picked” confirmation and downstream steps (consumption/installation) can proceed with correct inventory state.

### In-Scope
- Moqui UI flow to:
  - Load a work order’s pick list (or equivalent fulfillment task)
  - Guide picking via scan inputs
  - Confirm quantities per line
  - Submit “pick confirmation” to backend
- Client-side validation and error handling for common scan/quantity issues
- Display of pick status per line and overall task status as returned by backend

### Out-of-Scope
- Reservation/allocation logic, substitution rules, backorders (must be defined elsewhere)
- Inventory consumption (decrement) and costing
- Creating/maintaining work orders, parts lists, or storage locations
- Permissions model definition (only enforcement based on backend responses)

---

## 3. Actors & Stakeholders

### Actors
- **Mechanic (Primary):** performs scan + confirm.
- **Inventory Manager (Secondary):** cares about accuracy/audit but not primary UI user here.
- **System:** validates scans, updates pick state, emits any events.

### Stakeholders
- **Work Execution domain** (work order lifecycle; dependency for task/line states)
- **Inventory domain** (item identity, quantities, location associations if applicable)
- **Audit/Security domains** (audit trails; authorization)

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in POS frontend.
- A work order exists with parts to pick (a “pick list” or “fulfillment task” exists).
- Backend supports:
  - Fetching pick task / pick lines for a work order
  - Validating scan inputs (barcode/SKU/bin) and returning matched line(s)
  - Submitting pick confirmation with idempotency/optimistic locking

### Dependencies
- **Backend API contract is not provided in the inputs.** This story cannot be fully buildable without confirming:
  - endpoints, payloads, identifiers, and state model (see Open Questions)
- Scanner input method (keyboard wedge vs device integration) must align with existing frontend patterns in the repo.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Work Order detail screen: action/button **“Pick Parts”** opens picking execution screen for that work order.
- Optional: from a Fulfillment/Picking queue list (if exists) -> open specific task.

### Screens to create/modify (Moqui)
1. **Modify** existing Work Order detail screen to add navigation to picking:
   - `screen: WorkOrderDetail` (name TBD by repo conventions)
   - Add transition to `PickExecute` with parameter `workOrderId` (or `pickTaskId`)
2. **Create** new picking execution screen:
   - `screen: Fulfillment/PickExecute.xml` (path/name TBD)
   - Contains:
     - Header: work order reference + status
     - Scan input form
     - Pick lines grid/list with per-line confirm controls
     - Submit/Complete action

### Navigation context
- Breadcrumb: Work Orders → Work Order {id} → Pick Parts
- Back action returns to Work Order detail, preserving state if possible.

### User workflows
#### Happy path
1. Mechanic opens “Pick Parts”
2. UI loads pick lines (required part, qty required, qty already picked, pick location if provided)
3. Mechanic scans an item barcode (and optionally location/bin barcode)
4. UI matches scan to a pick line (single match) and focuses that line
5. Mechanic confirms quantity picked (default 1 or remaining qty; see Open Questions)
6. UI submits confirmation (per-line or batch) and refreshes line status
7. Mechanic completes picking task when all required quantities are picked

#### Alternate paths
- Scan matches multiple lines → UI prompts user to select the intended line
- Scan doesn’t match any line → show error and allow retry/manual search
- Partial pick allowed → user can save progress without completing (must confirm)
- Over-pick attempt → block with validation error
- Network/API error → show retry, keep unsent changes locally in-memory

---

## 6. Functional Behavior

### Triggers
- Enter picking screen with `workOrderId` (or `pickTaskId`) in URL parameters.
- Scan submitted (Enter key or “Process Scan” action).
- Quantity confirmation action (per line).
- Complete/submit action (batch).

### UI actions
- **On load:**
  - Call load service to retrieve pick task and lines
  - Render list with statuses
- **On scan:**
  - Capture scan string
  - Call backend “resolve scan” (or attempt client-side match if backend provides normalized codes list; prefer backend)
  - If match: select line; prefill qty to pick; optionally auto-confirm if configured (not assumed)
- **On confirm qty:**
  - Validate qty is > 0 and <= remaining required (unless partial rules differ)
  - Submit pick confirmation for the line
  - Update UI with returned line state (picked qty, status, version)
- **On complete:**
  - Validate all required lines satisfied (unless partial completion allowed)
  - Submit “complete picking” transition
  - Navigate back to Work Order detail with success message

### State changes (frontend-observable)
- Pick line: `remainingQty` decreases; `pickedQty` increases; status changes (e.g., OPEN → PICKED/PARTIAL)
- Pick task: status changes (e.g., IN_PROGRESS → COMPLETED)

### Service interactions (Moqui)
- Use `transition` actions wired to backend REST via Moqui services (or direct HTTP if that’s the repo pattern).
- Each mutating action must pass correlation/request id (observability) if supported.

---

## 7. Business Rules (Translated to UI Behavior)

> Many fulfillment rules are domain-policy and not provided. Only enforce what is explicit; otherwise defer to backend and surface errors.

### Validation
- Scan input is required and trimmed.
- Quantity input:
  - Must be numeric
  - Must be > 0
  - Must not exceed remaining required quantity **unless backend explicitly supports over-pick/backorder/substitution** (unknown → block over-pick by default? **Not allowed as safe default**; see Open Questions)

### Enable/disable rules
- Confirm actions disabled when:
  - Line is already fully picked (backend indicates)
  - Task is completed/cancelled (backend indicates)
- Complete action disabled when:
  - Task not in completable state
  - Outstanding required qty remains (unless partial completion allowed)

### Visibility rules
- If backend provides “expected pick location” show it as read-only reference.
- Show “already picked” qty if any.

### Error messaging expectations
- Display backend validation error messages verbatim when safe (no sensitive data).
- Use consistent error banner/toast pattern used in repo.
- For scan mismatch: “Scan not recognized for this pick list” (exact text TBD).

---

## 8. Data Requirements

> Entities are described conceptually; exact Moqui entities depend on backend contract.

### Entities involved (conceptual)
- `WorkOrder` (reference only)
- `PickTask` / `FulfillmentTask`
- `PickLine`
- `Product` / `InventoryItem` reference
- `StorageLocation` reference (optional)

### Fields (type, required, defaults)
#### Screen parameters
- `workOrderId` (string/UUID, required) **OR** `pickTaskId` (string/UUID, required) — must choose one.

#### PickTask (read-only)
- `pickTaskId` (id, required)
- `workOrderId` (id, required)
- `status` (enum/string, required)
- `version` (number/string, required for optimistic locking if used)

#### PickLine (read-only + editable qty input)
- `pickLineId` (id, required)
- `productId` (id, required)
- `productDisplayName` (string, required)
- `requiredQty` (decimal, required)
- `pickedQty` (decimal, required; default 0)
- `uom` (string, required)
- `suggestedLocationId` (id, optional)
- `suggestedLocationLabel` (string, optional)
- `lineStatus` (enum/string, required)
- `version` (required if optimistic locking)

#### User inputs
- `scanValue` (string, required)
- `confirmQty` (decimal, required at confirm time)

### Read-only vs editable by state/role
- Mechanic can edit/enter `confirmQty` only when task/line is open/in-progress.
- All identifiers/status fields are read-only.

### Derived/calculated fields (frontend)
- `remainingQty = requiredQty - pickedQty` (display only; source-of-truth backend)
- `isComplete = remainingQty <= 0` (display only)

---

## 9. Service Contracts (Frontend Perspective)

> **Blocking**: no backend endpoints are defined in provided inputs. Below are *placeholders* that must be aligned with actual backend.

### Load/view calls
- `GET /api/v1/workorders/{workOrderId}/pick-task` → returns pick task + lines
  - or `GET /api/v1/pick-tasks/{pickTaskId}`

### Create/update calls
- None (no creation in UI; task exists already)

### Submit/transition calls
1. Resolve scan:
   - `POST /api/v1/pick-tasks/{pickTaskId}:resolve-scan`
   - Request: `{ scanValue: string }`
   - Response: either matched `pickLineId` or list of candidates + normalized info
2. Confirm pick line:
   - `POST /api/v1/pick-tasks/{pickTaskId}/lines/{pickLineId}:confirm-pick`
   - Request: `{ qty: decimal, scanValue?: string, version?: ... }`
   - Response: updated line + task summary
3. Complete pick task:
   - `POST /api/v1/pick-tasks/{pickTaskId}:complete`
   - Request: `{ version?: ... }`
   - Response: updated task status

### Error handling expectations
- `400/422` validation errors: show field-level error where possible + banner summary.
- `401`: redirect to login per app standard.
- `403`: show “Not authorized to pick” and disable actions.
- `404`: show not found empty state with back link.
- `409` conflict (optimistic locking): prompt user to refresh; discard local staged values after user confirms refresh.

---

## 10. State Model & Transitions

> **Blocking**: actual states are not specified. Define UI to be driven by backend statuses.

### Allowed states (expected)
- Pick task: `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` (names TBD)
- Pick line: `OPEN`, `PARTIAL`, `PICKED` (names TBD)

### Role-based transitions
- Mechanic can:
  - move task to `IN_PROGRESS` implicitly on first confirm (if backend does)
  - confirm picks on lines
  - complete task
- If backend restricts completion to certain roles, UI must honor `403`.

### UI behavior per state
- `COMPLETED/CANCELLED`: all inputs disabled; show summary only.
- `OPEN/IN_PROGRESS`: scan input enabled; lines actionable depending on remaining qty.

---

## 11. Alternate / Error Flows

### Validation failures
- Qty invalid → inline error on qty field; no API call.
- Scan empty → inline error on scan input.

### Concurrency conflicts
- Backend returns 409/version mismatch after confirm:
  - UI shows dialog: “This pick list changed. Refresh to continue.”
  - On refresh: reload task/lines; clear current scan value; retain no unsubmitted qty edits.

### Unauthorized access
- 403 on load: show access denied state; no line data rendered.

### Empty states
- No pick lines: show “No parts to pick for this work order” with back link.
- All lines complete but task not completed: show prompt to “Complete Picking” if allowed.

---

## 12. Acceptance Criteria

### Scenario 1: Load pick list for a work order
Given I am an authenticated Mechanic  
And a work order with an associated pick task exists  
When I navigate to “Pick Parts” for that work order  
Then the system loads and displays the pick task header (work order reference and status)  
And the system displays all pick lines with required quantity and picked quantity  
And actions are enabled/disabled based on the task status returned by the backend

### Scenario 2: Scan matches a single pick line and confirm quantity
Given I am on the picking screen for a pick task in an actionable state  
And a pick line exists for product “P1” with remaining quantity > 0  
When I scan a barcode that the backend resolves to that pick line  
And I confirm a quantity less than or equal to the remaining quantity  
Then the UI submits the confirm request for that line  
And the UI updates the line’s picked quantity and remaining quantity based on the response  
And the UI shows a success confirmation state for that line

### Scenario 3: Scan does not match any pick line
Given I am on the picking screen  
When I scan a value that does not resolve to any pick line in the task  
Then the UI displays an error message indicating the scan is not recognized for this pick list  
And no quantities are changed  
And I can retry scanning

### Scenario 4: Attempt to confirm invalid quantity
Given I am on the picking screen with a selected pick line  
When I enter a non-numeric quantity or a quantity <= 0  
Then the UI shows a field-level validation error  
And the confirm action does not call the backend

### Scenario 5: Backend rejects confirm due to business rule (e.g., over-pick)
Given I am on the picking screen  
When I attempt to confirm a quantity that the backend rejects  
Then the UI displays the backend validation error message  
And the line quantities remain unchanged in the UI after refresh/reload

### Scenario 6: Complete picking successfully
Given I am on the picking screen  
And the backend indicates the pick task is completable  
When I click “Complete Picking”  
Then the UI calls the complete endpoint  
And the UI reflects the task status as completed  
And the UI disables further scan/confirm actions

### Scenario 7: Concurrency conflict on confirm
Given I am on the picking screen  
And the pick line has been modified by another user/session  
When I submit a confirm request and the backend returns a 409 conflict  
Then the UI prompts me to refresh  
And after refresh the UI shows the latest task/line quantities from the backend

---

## 13. Audit & Observability

### User-visible audit data
- Display (read-only) “Last updated” timestamp for the pick task if provided.
- Optionally show “Confirmed by” per line if backend returns it (not assumed).

### Status history
- If backend provides status history, show a collapsible “History” section (read-only). Otherwise out of scope.

### Traceability expectations
- All confirm/complete calls include:
  - `pickTaskId`, `pickLineId` (as applicable)
  - correlationId/requestId header if standard in repo
- UI logs (frontend console) must not include sensitive tokens; only IDs and error codes.

---

## 14. Non-Functional UI Requirements

- **Performance:** initial load renders first meaningful content within 2s on typical shop network; avoid N+1 calls (single load call for lines).
- **Accessibility:** WCAG 2.1 AA; scan input and confirm controls keyboard-navigable; error messages announced to screen readers.
- **Responsiveness:** usable on tablet and narrow handheld widths; list view supports scrolling.
- **i18n:** all user-facing strings in i18n keys (per repo conventions).
- **Timezone:** display timestamps in site/user locale per existing app behavior (no new rules invented).

---

## 15. Applied Safe Defaults
- none

---

## 16. Open Questions

1. **Backend contract:** What are the exact backend endpoints and payloads for:
   - loading pick task/lines
   - resolving a scan
   - confirming a pick line
   - completing the pick task  
   (Provide URL paths, request/response JSON, error schema, and idempotency/locking mechanism.)

2. **Identifier to route by:** Should the frontend route use `workOrderId` or `pickTaskId` as the primary parameter?

3. **Scan semantics:** What can be scanned?
   - product barcode only, or also storage location/bin barcode, or both?
   - if both are allowed, what is the expected sequence and matching behavior?

4. **Multi-match handling:** If a scan matches multiple pick lines (same SKU appears multiple times), what is the correct disambiguation key (location, lot, work step)?

5. **Quantity rules:** Are partial picks allowed? Is completion allowed with remaining quantities? Are over-picks ever allowed (with approval), or must UI hard-block before calling backend?

6. **Serial/lot control:** Are items serialized or lot-controlled in this workflow? If yes, what additional capture is required at pick time?

7. **Permissions:** What permission(s) gate picking actions (view pick list, confirm picks, complete task), and how are they surfaced (roles/claims)? If unknown, should UI rely solely on 403?

8. **Task/line state model:** What are the canonical statuses for pick tasks and pick lines, and what transitions are allowed?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Fulfillment: Mechanic Executes Picking (Scan + Confirm)  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/244  
Labels: frontend, story-implementation, type:story, layer:functional, kiro

## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #235 - Fulfillment: Mechanic Executes Picking (Scan + Confirm)  
**URL**: https://github.com/louisburroughs/durion/issues/235  
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for frontend development
- Coordinate with corresponding backend implementation if needed

### Technical Requirements
**Frontend Implementation Requirements:**
- Use Vue.js 3 with Composition API
- Follow TypeScript best practices
- Implement using Quasar UI framework components
- Ensure responsive design and accessibility (WCAG 2.1)
- Handle loading states and error conditions gracefully
- Implement proper form validation where applicable
- Follow established routing and state management patterns

### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Frontend agents: Focus on Vue.js 3 components, TypeScript, Quasar UI framework. Coordinate with backend implementation for API contracts.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `frontend` - Implementation type

---  
*Generated by Missing Issues Audit System - 2025-12-26T17:36:39.200600573*