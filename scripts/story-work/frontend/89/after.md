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
[FRONTEND] Allocations: Handle Shortages with Backorder or Substitution Suggestion

### Primary Persona
Service Advisor

### Business Value
Prevent work stoppage and reduce customer wait time by presenting actionable shortage resolutions (substitute, external availability, backorder) when allocation quantity exceeds available-to-promise (ATP), while capturing an auditable decision that downstream domains can act on.

---

## 2. Story Intent

### As a / I want / So that
**As a** Service Advisor,  
**I want** shortage handling during part allocation that suggests substitutes, external availability, or backorder options,  
**so that** I can choose how to proceed and keep work moving with a recorded, auditable decision.

### In-scope
- Detecting/displaying an allocation shortage in the POS UI when ATP is insufficient.
- Calling a Moqui backend endpoint to retrieve shortage resolution options in deterministic order.
- Rendering substitute / external availability / backorder options with ranking/sorting as provided (or per explicit rules below).
- Capturing the user’s decision (including cancel) and submitting it to backend for audit/event emission.
- UI degradation behavior when dependent option categories fail (banner + remaining options).
- Persisting and showing “shortage pending” state on the affected allocation line until resolved (UI representation; backend is SoR).

### Out-of-scope
- Defining substitution rules (owned by Product domain).
- Computing external availability (owned by Positivity domain).
- Updating work order/estimate contents (owned by Work Execution domain) beyond submitting the resolution decision.
- Inventory valuation/costing decisions beyond displaying provided cost impact fields.
- Backorder lead-time sourcing logic beyond displaying provided fields (SoR backend).

---

## 3. Actors & Stakeholders
- **Service Advisor (primary user):** resolves shortages during allocation.
- **Inventory service (backend / Moqui services):** detects shortage, aggregates options, records decision.
- **Product domain (dependency):** provides approved substitutes + pricing deltas.
- **Positivity domain (dependency):** provides external availability sources.
- **Work Execution domain (consumer):** receives decision outcome to update estimate/WO.
- **Audit/Reporting:** requires immutable record of decision and context.

---

## 4. Preconditions & Dependencies
- User is authenticated in POS frontend and has access to a Work Order allocation workflow.
- The user attempts to allocate a part/product to a work order with a requested quantity.
- Backend can compute ATP for the requested item at the relevant location.
- Backend exposes:
  1) a “check shortage / get resolution options” operation for an allocation attempt, and  
  2) a “submit shortage resolution decision” operation that records an audit event and returns updated allocation state.
- Dependency degradation rules exist (product timeout 800ms, positivity timeout 1200ms) and backend returns an indicator that some options could not be retrieved (for UI banner).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From the Work Order screen where parts are allocated (e.g., “Add Part” / “Allocate” action on a parts line).
- From an existing parts line marked as “Shortage/Pending Resolution” (re-open resolution modal).

### Screens to create/modify
- **Modify** Work Order Allocation screen (existing) to:
  - intercept allocation submit if backend returns shortage response
  - display shortage indicator on the line item
- **Create** a shortage resolution dialog/screen fragment:
  - `AllocationShortageResolve` (modal/popup or sub-screen)
- **Optional Create** read-only audit history panel for the allocation line (if not already present in WO UI)

### Navigation context
- User remains in the Work Order context (`workOrderId` in screen path or parameters).
- Shortage resolution occurs without losing unsaved work on the WO screen.

### User workflows
**Happy path**
1. Service Advisor requests allocation of part (qty > ATP).
2. UI receives shortage response with ordered options.
3. UI opens Shortage Resolution dialog showing:
   - shortage summary (requested, ATP, shortfall)
   - options grouped by type (Substitute, External, Backorder) in required order
4. User selects an option and confirms.
5. UI submits decision; on success:
   - dialog closes
   - affected parts line updates to resolved state (or updated SKU if substitute selected)
   - shortage flag cleared.

**Alternate paths**
- If product/positivity lookups fail or timeout: dialog shows banner “Some availability options could not be retrieved at this time.” and still shows remaining options.
- User cancels dialog: allocation line remains in “Shortage/Pending” state; no decision recorded.
- No substitutes or external options: dialog shows only backorder option (if provided).

---

## 6. Functional Behavior

### Triggers
- User clicks “Allocate” / “Confirm allocation” for a part line with requested quantity.
- User opens shortage resolution dialog from a flagged line.

### UI actions
- On allocation submit:
  - Call backend allocation attempt endpoint.
  - If success without shortage: proceed as normal.
  - If shortage response: render shortage dialog with options.
- In shortage dialog:
  - Select option (single selection).
  - Confirm decision → submit resolution.
  - Cancel → close dialog, do not submit.

### State changes (frontend view-state)
- Parts line gets a `shortageFlag=true` and `shortageStatus=PENDING` when shortage detected (until resolved).
- After successful resolution submit:
  - update line to reflect backend result (resolved / backordered / substituted / external purchase requested)
  - clear pending shortage UI markers as appropriate.

### Service interactions
- `AllocationAttempt` (or equivalent) returns either:
  - normal allocation success, or
  - shortage payload including ordered `resolutionOptions` and shortage details.
- `SubmitShortageResolution` records decision (audit) and returns updated allocation/workorder line representation.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- User must select exactly one resolution option before enabling “Confirm”.
- If selected option is `SUBSTITUTE`, UI must display substitute metadata (quality tier, fitment confidence, priceDifference) and require explicit confirmation (same confirm button; no extra policy assumed).
- If selected option is `EXTERNAL_PURCHASE`, UI must display source, lead time, additional cost, confidence.
- If selected option is `BACKORDER`, UI must display lead time, source, confidence; **if missing lead time fields**, option must not be displayed (backend should omit; UI also guards and hides malformed option).

### Enable/disable rules
- Confirm button disabled until option selected.
- If backend indicates no valid options: show blocking error state and offer “Close” (cannot proceed).

### Visibility rules
- Show option categories only when present.
- Always show backorder only if present in response (do not fabricate).
- Show informational banner when backend indicates partial option retrieval failure.

### Error messaging expectations
- Validation errors: inline (“Please select an option to continue.”).
- Backend 4xx: show returned message; keep dialog open.
- Backend 5xx/network: show “Unable to resolve shortage right now. Try again.” with Retry button.

---

## 8. Data Requirements

### Entities involved (frontend concepts; backend SoR)
- WorkOrder (reference by `workOrderId`)
- Allocation Request/Line (reference by `allocationLineId` or equivalent)
- ShortageFlag (backend concept; UI representation)
- SubstituteSuggestion (backend option data)
- ExternalAvailabilityRef (backend option data)
- Audit event for decision (read-only display if surfaced)

### Fields (type, required, defaults)
**Shortage context (from backend)**
- `workOrderId` (string/UUID, required)
- `productId` or `partSku` (string, required) — see Open Questions about canonical identifier
- `requestedQuantity` (number/decimal, required)
- `atpQuantity` (number/decimal, required) (needed for UI display; not shown in backend example but implied)
- `shortfallQuantity` (number/decimal, required)

**ResolutionOption (list, required when shortage)**
- `type` (enum: `SUBSTITUTE` | `EXTERNAL_PURCHASE` | `BACKORDER`, required)
- `partSku` (string, required)
- For `SUBSTITUTE`:
  - `substituteProductId` (string/UUID, required)
  - `qualityTier` (enum, required)
  - `brand` (string, optional)
  - `fitmentConfidence` (enum, required)
  - `priceDifference.amount` (decimal, optional)
  - `priceDifference.currency` (string, optional)
  - `notes` (string, optional)
  - `availableQuantity` (number/decimal, optional/required?) (backend example includes it)
- For `EXTERNAL_PURCHASE`:
  - `sourceId` (string, required)
  - `sourceType` (enum, required)
  - `availableQuantity` (number/decimal, required)
  - `estimatedLeadTimeDays` (int, required)
  - `additionalCost.amount` (decimal, optional)
  - `additionalCost.currency` (string, optional)
  - `confidence` (enum, required)
- For `BACKORDER`:
  - `estimatedLeadTimeDays` (int, required)
  - `source` (string/enum, required)
  - `confidence` (enum, required)

**Decision submission (to backend)**
- `workOrderId` (required)
- `allocationLineId` (required) — if applicable
- `originalPartSku` and/or `originalProductId` (required)
- `decisionType` (required)
- `resultingPartSku` / `resultingProductId` (required for substitute; same as original for other types)
- `quantity` (required)
- `selectedExternalSourceId` (required for external purchase)
- `clientRequestId` (string, required for idempotency; frontend-generated UUID)

### Read-only vs editable by state/role
- All option data is read-only.
- Only editable input is option selection (and potentially quantity if backend allows—**not assumed**; see Open Questions).
- Only Service Advisor role (or any role with allocation permission) can submit decision; otherwise UI must show read-only and block submission.

### Derived/calculated fields
- UI may derive display-only:
  - `shortfallQuantity = requested - atp` if backend does not send (but backend story does; prefer backend).
  - “Cost impact” display from `priceDifference` / `additionalCost`.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui implementation may wrap/route to backend services; frontend needs stable screen transitions + service names. Exact service names/endpoints are **not provided** in inputs, so these are placeholders that must map to actual Moqui services.

### Load/view calls
1. **Get current allocation line status** (if reopening dialog):
   - Input: `workOrderId`, `allocationLineId`
   - Output: current line data + shortage status + last shortage payload if available

### Create/update calls
2. **Attempt allocation** (may return shortage):
   - Input: `workOrderId`, `productId/partSku`, `quantity`, `locationId`, `clientRequestId`
   - Output (success): updated allocation line
   - Output (shortage): shortage details + ordered `resolutionOptions` + `partialOptionsWarning=true|false`

### Submit/transition calls
3. **Submit shortage resolution decision**
   - Input: decision payload fields above
   - Output: updated allocation line + resolved state; may include audit reference id

### Error handling expectations
- 400: validation errors displayed inline in dialog.
- 403: show “You don’t have permission to resolve shortages.” and disable confirm.
- Timeout/503 from dependent domains should not block; backend should return partial options + warning flag. UI shows banner and continues.
- Network failure calling backend: show retry affordance; do not lose selected option.

---

## 10. State Model & Transitions

### Allowed states (frontend-visible)
For an allocation line:
- `ALLOCATED` (normal)
- `SHORTAGE_PENDING` (shortage detected; decision not recorded)
- `SHORTAGE_RESOLVED_SUBSTITUTE`
- `SHORTAGE_RESOLVED_EXTERNAL`
- `SHORTAGE_RESOLVED_BACKORDER`

> Backend is SoR for actual statuses; UI should map from backend fields.

### Role-based transitions
- Service Advisor (authorized) may transition:
  - `SHORTAGE_PENDING` → one of resolved states via submit decision
- Unauthorized user:
  - cannot submit; can view options if backend allows read

### UI behavior per state
- `SHORTAGE_PENDING`: show badge/flag on line + “Resolve” action opens dialog.
- Resolved states: show resolution summary (type and key details like resulting SKU/source/lead time).

---

## 11. Alternate / Error Flows

### Validation failures
- No option selected → prevent submit and show inline message.
- Malformed option (missing required fields) → hide that option and log client-side warning (no PII); if all hidden, show blocking error.

### Concurrency conflicts
- If allocation line changed since shortage was presented (backend returns 409 conflict):
  - UI shows “This allocation changed. Refresh options.” with Refresh button that re-calls attempt/refresh endpoint.

### Unauthorized access
- Backend 403 on submit:
  - dialog remains open in read-only mode; show permission message.

### Empty states
- No options returned:
  - show “No resolution options available” and guidance to contact manager; provide Close.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Shortage flagged and options shown in deterministic order
**Given** a Service Advisor is allocating a part to a work order  
**And** the requested quantity exceeds ATP for that part at the work location  
**When** the user submits the allocation  
**Then** the UI must display a shortage resolution dialog  
**And** the shortage must be visibly flagged on the allocation line as pending  
**And** resolution options must be presented in this category order: Substitute, External availability, Backorder (when present).

### Scenario 2: Only backorder available
**Given** a shortage occurs  
**And** backend returns only a BACKORDER option  
**When** the shortage dialog is shown  
**Then** only the backorder option is displayed  
**And** selecting it enables Confirm  
**And** confirming submits the decision and updates the line to a backorder-resolved state.

### Scenario 3: Dependent option category fails but flow continues
**Given** a shortage occurs  
**And** backend indicates partial option retrieval failure (e.g., substitutes omitted due to timeout)  
**When** the shortage dialog is shown  
**Then** the UI must show the banner message “Some availability options could not be retrieved at this time.”  
**And** the UI must still allow selecting and confirming among remaining options.

### Scenario 4: User cancels without decision
**Given** a shortage dialog is displayed  
**When** the user clicks Cancel/Close  
**Then** no decision submission call is made  
**And** the allocation line remains in a shortage pending state.

### Scenario 5: Submit decision is auditable (frontend evidence)
**Given** a shortage dialog is displayed with options  
**When** the user selects an option and confirms  
**Then** the UI must call the submit-decision service with workOrderId, original item identifier, quantity, decisionType, and clientRequestId  
**And** on success the UI must show a resolution summary on the line (type + key details) suitable for later auditing.

### Scenario 6: Unauthorized submit is blocked
**Given** the user lacks permission to resolve shortages  
**When** the user attempts to confirm a selected option  
**Then** the UI must display a permission error  
**And** must not mark the shortage as resolved in the UI.

---

## 13. Audit & Observability

### User-visible audit data
- On resolved allocation line, display:
  - decision type (Substitute/External/Backorder)
  - resulting SKU/product (if substitute)
  - external source + lead time (if external)
  - backorder lead time + source (if backorder)
  - timestamp and actor if backend supplies it (preferred)

### Status history
- If backend exposes decision history for the line, UI provides a “View history” expandable section listing immutable events.

### Traceability expectations
- Frontend includes `clientRequestId` on attempt and submit calls.
- UI logs (client-side) include correlation/clientRequestId for troubleshooting (no sensitive data).

---

## 14. Non-Functional UI Requirements
- **Performance:** shortage dialog should render within 200ms after response received; avoid N+1 calls (single aggregated shortage response).
- **Accessibility:** dialog is keyboard navigable; radio group has accessible labels; banner is announced to screen readers.
- **Responsiveness:** usable on tablet width typical for POS.
- **i18n/timezone/currency:** display currency codes provided by backend; timestamps shown in user locale/timezone if present.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging when no options are returned; qualifies as UI ergonomics. Impacted sections: UX Summary, Alternate/Error Flows.
- SD-UX-RETRY: Provide Retry action on transient backend/network failures; qualifies as standard error-handling mapping. Impacted sections: Service Contracts, Alternate/Error Flows.
- SD-OBS-CORRELATION-ID: Generate and pass `clientRequestId` UUID for idempotency/traceability; qualifies as observability boilerplate. Impacted sections: Data Requirements, Audit & Observability, Service Contracts.

---

## 16. Open Questions
1. **Canonical identifier:** Should the frontend use `productId` (UUID) or `partSku` as the primary identifier for allocation + shortage resolution payloads (or both)? Backend reference shows SKU in some places and productId in integration calls.
2. **Allocation line identity:** What is the backend’s identifier for the specific allocation being resolved (`allocationLineId`, `estimateLineId`, etc.) and is it required on submit?
3. **Backorder option omission:** Backend rule says “omit backorder option if no lead time source exists.” Should the frontend ever show a generic backorder without lead time, or must it strictly hide it if missing required fields?
4. **Decision persistence on cancel:** When user cancels, should the backend record a “cancelled/aborted” audit event, or must it record nothing (backend story says no decision recorded)?
5. **Sorting responsibility:** Are options already sorted/ranked by backend (preferred), or must frontend implement intra-category sorting rules (lead time asc, cost impact asc, quality tier desc)? If frontend must sort, confirm exact fields for lead time/cost across option types.
6. **Permissions:** What specific permission(s) gate resolving shortages in the Moqui layer (e.g., `inventory.allocation.resolveShortage`), and should users without it be prevented from even seeing options?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Allocations: Handle Shortages with Backorder or Substitution Suggestion — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/89


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Allocations: Handle Shortages with Backorder or Substitution Suggestion
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/89
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Allocations: Handle Shortages with Backorder or Substitution Suggestion

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want shortage handling so that work proceeds with backorders or approved substitutes.

## Details
- If ATP insufficient: propose external availability or substitute options.
- Link to product substitution rules.

## Acceptance Criteria
- Shortage flagged.
- Suggested actions returned.
- Decision captured and auditable.

## Integrations
- Product provides substitution/pricing; Positivity provides external availability; workexec updates estimate/WO.

## Data / Entities
- ShortageFlag, SubstituteSuggestion, ExternalAvailabilityRef

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

====================================================================================================