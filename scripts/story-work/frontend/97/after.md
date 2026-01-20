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

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] Receiving: Direct-to-Workorder Receiving (Cross-dock) from Distributor

### Primary Persona
Inventory Manager

### Business Value
Enable urgent parts to be received from a distributor and immediately issued to a specific workorder (cross-dock), reducing job lead time by bypassing put-away/pick while preserving an auditable receive+issue trail and notifying Work Execution.

---

## 2. Story Intent

### As a / I want / So that
**As an** Inventory Manager,  
**I want** to receive items from a supplier shipment and link them directly to a workorder line with immediate issue allocation,  
**so that** urgent jobs can be fulfilled without standard put-away and picking delays.

### In-scope
- Frontend screens/flows to:
  - Select an inbound shipment/receipt to receive.
  - Enter/confirm received quantities per receiving line.
  - Link a receiving line to `workorderId` and `workorderLineId`.
  - Execute a “cross-dock receive + issue” action (manual confirm or auto-issue depending on policy).
  - Display result (receipt + issue created) and audit-relevant references (supplier shipment ref, workorder refs, issue mode, confirmer).
- Error/alternate flows surfaced to user (mismatch, closed WO, permission denied, notification failures surfaced as non-blocking).
- Moqui screen/form/service wiring consistent with project conventions.

### Out-of-scope
- Defining or changing backend domain logic (ledger posting rules, costing formula, event emission internals).
- Put-away/bin assignment for these lines (explicitly bypassed).
- Creating/editing workorders or workorder demand lines (Workexec-owned).
- Defining substitution mapping behavior beyond what backend supports (UI will only expose if contract exists).
- Accounting UI or job-cost UI.

---

## 3. Actors & Stakeholders
- **Primary user:** Inventory Manager (receives shipments, confirms issuance)
- **Secondary users:** Service Advisor (visibility only), Technician (downstream beneficiary)
- **External systems:**
  - **Workexec** (workorders/demand; must be notified when issue completes)
  - **Distributor/Supplier** (shipment reference)
- **Internal systems:**
  - Inventory services (receipt + issue transaction; inventory ledger)
  - AuthN/AuthZ (permissions to issue/override if applicable)

---

## 4. Preconditions & Dependencies
### Preconditions
- User is authenticated in POS frontend.
- A target workorder exists in Workexec with at least one line demanding a product/SKU.
- An inbound shipment/receiving document exists in inventory/receiving context with at least one line to receive.

### Dependencies (blocking where contract unknown)
- Backend endpoints/services for:
  - Listing/loading receiving shipments and lines
  - Searching/selecting workorders and workorder lines (from Workexec or via POS proxy)
  - Performing cross-dock “receive+issue” transaction atomically
  - Returning validation errors with machine-readable codes
- Backend indicates policy for:
  - manual confirmation vs auto-issue eligibility
  - part mismatch override support and required permission/reason code
- Permissions model: names/scopes for issuing and overrides must match backend.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Receiving** → **Inbound Shipments** (or existing receiving entry in the app)
- From a shipment detail: action **Receive** for a line, with option **Direct-to-Workorder (Cross-dock)**

### Screens to create/modify
1. **Modify** existing receiving shipment detail screen (if present):
   - Add per-line action “Cross-dock to Workorder”
2. **Create** `Receiving/CrossDockReceive` screen (or sub-screen under shipment):
   - Workorder line selection
   - Quantity receive input
   - Confirmation step (if manual)
   - Result summary

> Moqui artifacts expected:
- Screen(s) with transitions for: select WO line → validate → confirm (optional) → submit
- Forms for linking and submission; parameter passing via transition context

### Navigation context
- Breadcrumb: Receiving → Shipment `<shipmentRef>` → Line `<product>` → Cross-dock
- “Back” returns to shipment detail with updated line state.

### User workflows
#### Happy path (manual confirm)
1. User opens shipment, chooses a line, clicks **Cross-dock to Workorder**
2. User searches/selects **Workorder** and **Workorder Line**
3. User enters **received quantity**
4. System shows confirmation: what will be issued to which WO/line
5. User confirms → system posts receive+issue
6. UI shows success and audit references; returns to shipment detail

#### Happy path (auto-issue)
Same as above, but after entering quantity + selecting WO line, UI indicates auto-issue will occur and submits without confirmation step (or confirmation screen becomes informational “Auto-issuing…”).

#### Alternate paths
- Workorder is closed/cancelled → blocked with message
- Product mismatch between receiving line and WO line → blocked or override path (if permitted)
- Network/service error → no partial state shown; actionable error; allow retry safely

---

## 6. Functional Behavior

### Triggers
- User chooses “Cross-dock to Workorder” on a receiving line.
- User submits cross-dock transaction (manual confirm or auto-submit).

### UI actions (explicit)
- **Search Workorder**: user can query by workorder number/id and optionally customer name (only if backend supports).
- **Select Workorder Line**: user selects a line showing demanded product/SKU, remaining qty, and status.
- **Enter Received Quantity**: numeric input with UoM display.
- **Submit**:
  - If manual confirmation required: “Review & Confirm Issue” step required.
  - If auto-issue eligible and policy enabled: submit immediately.

### State changes (frontend-visible)
- Receiving line becomes linked to `workorderId` and `workorderLineId` (persisted by backend).
- Inventory ledger records receive and issue (not editable in UI; visible as references if backend returns).
- UI displays `issueMode` and `confirmedBy` after completion.

### Service interactions (high level)
- Load shipment + lines
- Load workorder + lines (search)
- Validate cross-dock eligibility (may be part of submit response)
- Submit cross-dock transaction (atomic)
- Optionally poll/load updated shipment line status after submit (if backend doesn’t return updated model)

---

## 7. Business Rules (Translated to UI Behavior)

> Note: Backend reference story resolves several policies; frontend must reflect them. Any gaps in frontend/backend contract become Open Questions.

### Validation
- Received quantity:
  - Must be > 0
  - Must not exceed remaining receivable quantity on the receiving line (if provided)
- Workorder eligibility:
  - Workorder must be in an allowed state (e.g., Open/In-Progress); otherwise block submission
- Product match:
  - Default: receiving line product must match selected workorder line product; otherwise block with error code `PART_MISMATCH_WITH_WORKORDER`
  - If override is supported and user has permission: allow override flow with required reason code; override must force manual confirmation (never auto-issue)

### Enable/disable rules
- “Confirm Issue” button disabled until:
  - workorder line selected
  - valid quantity entered
  - no blocking validation errors
- Override controls only visible when mismatch occurs *and* backend indicates override is permitted for the current user/session.

### Visibility rules
- Show supplier shipment reference prominently on review/confirm step (audit requirement).
- Show “Auto-issue will occur” banner when backend indicates auto-issue eligibility.

### Error messaging expectations
- Permission denied → show “You do not have permission to issue parts” (map 403)
- Validation errors → inline field errors + top summary; include backend error code where safe
- Non-blocking notification failure:
  - If backend returns a warning status, show “Issued successfully; notification pending/retry” (must not imply failure of inventory movement)

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- Receiving/Shipment entity (name TBD)
- `ReceivingLine` (must include or be able to display):
  - `receivingLineId`
  - `productId` / SKU / part number display fields
  - `quantityOrdered` (optional)
  - `quantityReceivedToDate` (optional)
  - `quantityRemainingToReceive` (optional)
  - **link fields:** `workorderId` (nullable), `workorderLineId` (nullable)
  - `supplierShipmentRef` or shipment-level ref
- Workorder summary + lines (from Workexec/proxy):
  - `workorderId`
  - `workorderNumber` (display)
  - `status`
  - `workorderLineId`
  - `productId`/SKU/part number
  - `quantityDemanded`, `quantityIssuedToDate` or `quantityRemaining`
- Cross-dock transaction result:
  - receipt identifier(s) (e.g., `receiptId`)
  - issue ledger entry id (optional)
  - `issueMode` (`MANUAL_CONFIRM` | `AUTO_ON_RECEIPT`)
  - `confirmedBy` (userId or SYSTEM)
  - event publication status (success/pending) if available

### Fields (type, required, defaults)
- Input fields:
  - `workorderId` (string/UUID) **required**
  - `workorderLineId` (string/UUID) **required**
  - `quantityReceived` (decimal) **required**
  - `uom` (string) **read-only** (from receiving line/workorder line)
  - `overridePartMismatch` (boolean) optional, default false
  - `overrideReasonCode` (string) required *only if* override is true
- Read-only vs editable
  - Workorder identifiers: read-only after selection; editable via “Change selection”
  - Quantity: editable until submit; locked during submission

### Derived/calculated fields (frontend-only)
- `isAutoIssueEligible` (boolean) computed from backend response if provided; do not infer without contract
- `quantityRemainingOnWorkorderLine` computed if backend provides demanded/issued numbers

---

## 9. Service Contracts (Frontend Perspective)

> Moqui typically uses services invoked by transitions/actions. Exact service names must match backend; currently unclear for frontend repo. Contracts below specify required semantics.

### Load/view calls
1. **Get shipment detail + lines**
   - Input: `shipmentId` (or similar)
   - Output: shipment header (incl. supplier shipment ref) + receiving lines
2. **Search workorders**
   - Input: query string, paging
   - Output: list of workorders with status
3. **Get workorder lines**
   - Input: `workorderId`
   - Output: lines with product and remaining qty

### Create/update calls
- **Link receiving line to workorder line (optional pre-save)**
  - If backend requires a separate step, UI must call it before submit.
  - Otherwise include link fields in submit.

### Submit/transition calls (atomic)
- **Cross-dock receive+issue**
  - Input:
    - `receivingLineId`
    - `workorderId`, `workorderLineId`
    - `quantityReceived`
    - `issueModeRequested` (optional; if backend decides, omit)
    - `overrideReasonCode` (if overriding mismatch)
  - Output:
    - success status
    - persisted link fields
    - `issueMode`, `confirmedBy`
    - identifiers (receiptId, ledgerEntryIds) if available
    - optional warning about notification retry

### Error handling expectations
- `400` validation errors return structured field errors and/or `errorCode` values:
  - `PART_MISMATCH_WITH_WORKORDER`
  - `WORKORDER_NOT_ISSUABLE` (example)
  - `QUANTITY_EXCEEDS_REMAINING`
- `403` unauthorized/forbidden for missing permissions (e.g., issue/override)
- `409` conflict for concurrent updates (line already received/linked elsewhere)

---

## 10. State Model & Transitions

### Allowed states (frontend-level, for the cross-dock flow)
- **Draft (local UI state):** user selecting WO line and entering quantity
- **Review (manual confirmation only):** user confirming issuance
- **Submitting:** request in-flight; disable inputs
- **Completed:** show result summary
- **Failed:** show error; allow retry or return

### Role-based transitions
- Inventory Manager with issue permission:
  - Draft → Review → Submitting → Completed
  - Draft → Submitting → Completed (auto-issue path)
- User without issue permission:
  - Draft → (submit) → Failed (403), stay in Draft with error messaging
- Override permission (if supported):
  - Draft (mismatch) → Review (forced manual) when override selected

### UI behavior per state
- Draft: editable; shows live validation
- Review: read-only summary; confirm/cancel
- Submitting: spinner/progress; prevent double-submit
- Completed: show receipt/issue references and “Return to shipment”
- Failed: show error + retry

---

## 11. Alternate / Error Flows

1. **Part mismatch**
   - On selection of WO line, if SKU mismatch detected by backend or by comparing ids provided:
     - Show blocking error
     - If override supported and user permitted: show override controls requiring reason code; force manual confirmation step

2. **Workorder closed/cancelled**
   - Block selection or block submit with clear message
   - Do not proceed to confirmation

3. **Quantity invalid**
   - Inline error; disable submit

4. **Concurrency conflict**
   - If backend responds 409 (e.g., line already received/issued):
     - Show “This line was updated by another user. Reload to continue.”
     - Provide “Reload shipment” action

5. **Notification failure (non-blocking)**
   - If backend indicates inventory transaction succeeded but event publish queued/failed:
     - Show success state with warning banner
     - Do not roll back UI state to failed

6. **Offline/network/server error**
   - Show generic error with request id/correlation id if available
   - Allow retry; must be safe (backend should be idempotent—if not, Open Question)

---

## 12. Acceptance Criteria

### Scenario 1: Manual confirmation cross-dock succeeds
**Given** an inbound supplier shipment has a receiving line for product `P-ABC` with quantity to receive `2`  
**And** a workorder `WO-123` exists in an issuable state with a line demanding `P-ABC` and remaining quantity `2`  
**And** the current policy requires manual confirmation  
**When** the Inventory Manager selects “Cross-dock to Workorder” on the receiving line  
**And** selects workorder `WO-123` and the matching workorder line  
**And** enters received quantity `2` and proceeds to review  
**Then** the UI shows a confirmation summary including product, quantity, supplier shipment reference, workorder id/number, and workorder line  
**When** the user confirms issuance  
**Then** the UI submits the cross-dock transaction  
**And** on success the UI shows `issueMode = MANUAL_CONFIRM` and `confirmedBy = <currentUser>`  
**And** the shipment detail reflects the receiving line is linked to `workorderId` and `workorderLineId`

### Scenario 2: Partial quantity cross-dock
**Given** a receiving line for `P-ABC` has quantity `1` available to receive  
**And** workorder `WO-123` has remaining demand `2` for `P-ABC`  
**When** the user cross-docks quantity `1` to `WO-123` and confirms  
**Then** the UI shows success for issuing `1`  
**And** the workorder linkage is persisted on the receiving line

### Scenario 3: Auto-issue path
**Given** policy allows auto-issue and backend indicates the selection is eligible for auto-issue  
**When** the user selects the matching workorder line and enters quantity  
**Then** the UI indicates auto-issue will occur  
**And** submitting completes without a manual confirmation step (or confirmation is informational only)  
**And** the result shows `issueMode = AUTO_ON_RECEIPT` and `confirmedBy = SYSTEM`

### Scenario 4: Workorder not issuable (closed/cancelled)
**Given** a workorder `WO-456` is in status `Closed` (or non-issuable)  
**When** the user attempts to link a receiving line to `WO-456`  
**Then** the UI blocks the action with message “Cannot issue parts to a closed workorder.” (or backend-provided message)  
**And** no submit action is performed

### Scenario 5: Product mismatch blocked (no override)
**Given** a receiving line is for product `P-BBB`  
**And** selected workorder line demands product `P-AAA`  
**When** the user attempts to proceed  
**Then** the UI displays an error with code `PART_MISMATCH_WITH_WORKORDER`  
**And** the submit/confirm controls remain disabled

### Scenario 6: Product mismatch override requires permission and reason (if supported)
**Given** a product mismatch exists between receiving line and workorder line  
**And** the current user has permission to override part match  
**When** the user selects “Override”  
**Then** the UI requires a reason code before enabling confirmation  
**And** the flow forces manual confirmation (no auto-issue)  
**When** the user confirms  
**Then** the transaction succeeds and the UI shows success including the override reason in the result/audit view (if returned)

### Scenario 7: Permission denied to issue
**Given** the current user lacks issue permission  
**When** the user attempts to confirm/submits the cross-dock issuance  
**Then** the UI displays a permission error (403)  
**And** remains on the cross-dock screen without marking the line as completed

---

## 13. Audit & Observability

### User-visible audit data
- Show (in result summary and/or shipment line detail):
  - supplier shipment reference
  - workorder/workorder line reference
  - quantity issued
  - issue mode (`MANUAL_CONFIRM` vs `AUTO_ON_RECEIPT`)
  - confirmed by (user vs SYSTEM)
  - timestamp (from backend if available)

### Status history
- If backend exposes ledger/audit entries list, provide a “View transaction details” link to a read-only view (can be deferred if not available).

### Traceability expectations
- All submissions include correlation id (from frontend header if project standard) and display it on error states.
- Log frontend events (console/app log) for:
  - cross-dock started
  - submit attempted
  - submit success/failure with error code (no sensitive data)

---

## 14. Non-Functional UI Requirements
- **Performance:** Workorder search results should return within 2s for typical queries; paginate results.
- **Accessibility:** All form inputs have labels; errors announced; keyboard navigable confirmation.
- **Responsiveness:** Usable on tablet (receiving dock use case).
- **i18n/timezone/currency:** Dates/times shown in site/user timezone; no currency required.

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Provide empty-state messaging for “no workorders found” and “no lines available” because it is UI ergonomics and does not change domain policy. (Impacted: UX Summary, Alternate/Error Flows)
- SD-UI-PAGINATION: Paginate workorder search results with standard page size to avoid large payloads; purely UX/perf. (Impacted: UX Summary, Service Contracts)
- SD-ERR-MAP-HTTP: Map 400/403/409/500 to inline + banner errors with retry action; standard error-handling mapping. (Impacted: Business Rules, Alternate/Error Flows, Acceptance Criteria)
- SD-OBS-CORRELATION-ID: Include/display correlation/request id when available for supportability; observability boilerplate. (Impacted: Audit & Observability, Alternate/Error Flows)

---

## 16. Open Questions

1. **Backend service/API contract:** What are the exact Moqui service names (or REST endpoints) for:
   - loading shipment + receiving lines,
   - searching workorders and loading workorder lines,
   - submitting the atomic cross-dock receive+issue transaction?
2. **Identifier types:** Are `workorderId`, `workorderLineId`, `receivingLineId`, `receiptId` UUIDv7 strings (as per backend reference) or another format in this frontend/backoffice?
3. **Policy exposure:** How does the frontend determine whether **manual confirm** is required vs **auto-issue** eligible?
   - Is there a policy endpoint/flag returned in the shipment/line payload?
4. **Permissions mapping:** What are the exact permission strings enforced by Moqui/security layer for:
   - issuing parts (backend reference uses `ISSUE_PARTS`),
   - overriding part match (`OVERRIDE_PART_MATCH`)?
5. **Mismatch detection source of truth:** Should the UI do a client-side SKU comparison (best-effort) *in addition to* backend validation, or rely solely on backend validation to avoid false positives?
6. **Notification status surfaced?** Does the submit response include notification/event publication status (success/queued/failed), or is it always asynchronous with no UI feedback?
7. **Idempotency expectations:** If the user retries after a timeout, does the backend support idempotency keys to prevent duplicate receive+issue postings? If yes, what header/field should frontend send?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Receiving: Direct-to-Workorder Receiving (Cross-dock) from Distributor — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/97

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Receiving: Direct-to-Workorder Receiving (Cross-dock) from Distributor  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/97  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Receiving: Direct-to-Workorder Receiving (Cross-dock) from Distributor

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As an **Inventory Manager**, I want to receive items directly against a workorder so that urgent jobs can be fulfilled without normal put-away.

## Details  
- Receiving lines can be linked to workorderId and workorderLineId.  
- Items received can be immediately allocated/issued to that workorder.  
- Optionally bypass storage and go straight to issue.

## Acceptance Criteria  
- Receipt linked to workorder.  
- Allocation/issue auto or confirm.  
- Workexec notified.  
- Audit includes supplier shipment ref.

## Integrations  
- Workexec sends demand; Positivity provides shipment status; inventory posts receive+issue.

## Data / Entities  
- WorkorderReceiptLink, ReceivingLine, InventoryLedgerEntry(Receive/Issue)

## Classification (confirm labels)  
- Type: Story  
- Layer: Domain  
- Domain: Inventory Management

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