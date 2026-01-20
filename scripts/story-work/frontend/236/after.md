STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:story-authoring
- agent:workexec

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Estimate: Calculate Taxes and Totals on Estimate

### Primary Persona
Service Advisor (POS user) viewing and editing an Estimate

### Business Value
Ensure estimates always display accurate, up-to-date subtotal, taxes, fees, discounts, and grand total with an auditable calculation snapshot, preventing approval when required tax configuration is missing.

---

## 2. Story Intent

### As a / I want / So that
**As a** Service Advisor,  
**I want** estimate totals (subtotal, taxes, fees, discounts, grand total) to recalculate automatically whenever estimate line items change,  
**so that** I can confidently present correct pricing to customers and the system can retain a reproducible calculation snapshot for disputes and downstream processes.

### In-Scope
- Frontend behavior to trigger totals/tax recalculation when estimate line items are created/modified (parts/labor/fees/discounts).
- Display of calculated totals and per-line tax/taxability feedback as returned by backend.
- Display and navigation to view the latest **Calculation Snapshot** (read-only) associated to the estimate.
- Frontend blocking behavior to prevent proceeding to “Approval” (or equivalent) when backend indicates missing required tax configuration (per policy and error codes).
- Clear error states for missing tax code / missing jurisdiction configuration.

### Out-of-Scope
- Implementing the tax/fee/discount calculation logic itself (owned by backend pricing/tax services).
- Creating or editing TaxRule/FeeRule configuration.
- Defining rounding policy, default tax code policy, or tax-inclusive vs tax-exclusive behavior (must be provided by backend contract/policy).
- Invoice generation/accounting posting.

---

## 3. Actors & Stakeholders
- **Primary User Actor:** Service Advisor
- **System Actor:** Pricing/Tax calculation service (invoked via Moqui service calls from frontend screens)
- **Stakeholders:**
  - Store Manager (needs reliable totals before approval)
  - Accounting/Audit stakeholders (need snapshot traceability)
  - Workexec domain owners (estimate lifecycle and transitions)

---

## 4. Preconditions & Dependencies

### Preconditions
- An Estimate exists and is open in a mutable state (e.g., Draft).
- Estimate has a location/jurisdiction context (directly or indirectly via location on the estimate).
- Estimate has 0..N line items.

### Dependencies (Blocking if undefined)
- A backend endpoint/service to “calculate totals” for an estimate and return:
  - Updated totals (subtotal, taxTotal, feeTotal, discountTotal, grandTotal, roundingAdjustment)
  - Per-line tax info (at minimum taxAmount and/or taxCode/taxability result)
  - A `calculationSnapshotId` (or equivalent) for audit
  - Deterministic error codes for missing configuration (e.g., `ERR_CONFIG_JURISDICTION_MISSING`, `ERR_MISSING_TAX_CODE`)
- A backend read endpoint/service to load calculation snapshot details for display.
- Estimate state transition endpoint/service for “Proceed to Approval” (or equivalent) that will fail if tax configuration is missing.

---

## 5. UX Summary (Moqui-Oriented)

### Entry Points
- From Estimate detail screen where line items are added/edited (parts/labor/fees/discounts).
- From Estimate totals panel/summary section.

### Screens to Create/Modify
1. **Modify:** `EstimateDetail` screen (existing)
   - Add/extend totals summary panel to show:
     - Subtotal
     - Discount total
     - Fee total
     - Tax total
     - Rounding adjustment (if non-zero or always visible per design)
     - Grand total
     - “Last calculated at” timestamp (if provided) and “Snapshot ID” link
   - Hook line item create/update/delete actions to trigger recalculation.
2. **Create or Modify:** `EstimateCalculationSnapshotView` screen (new or existing)
   - Read-only view of the latest snapshot associated with the estimate (and optionally historical snapshots if backend supports).
   - Shows inputs and outputs sufficient for audit (“persist enough context to explain totals later”).

### Navigation Context
- `EstimateDetail` includes a link/button “View Calculation Details” which routes to `EstimateCalculationSnapshotView?estimateId=...&snapshotId=...`.

### User Workflows
#### Happy Path
1. User adds/edits/removes a line item.
2. UI saves the line item change.
3. UI triggers totals recalculation.
4. UI refreshes totals and displays new values + snapshot link.

#### Alternate Paths
- User changes multiple line items quickly → UI queues/debounces recalculation and shows “Recalculating…” state.
- Backend returns a configuration error → UI shows a blocking banner and disables “Proceed to Approval”.

---

## 6. Functional Behavior

### Triggers
- After successful create/update/delete of any Estimate line item (parts/labor/fees/discounts), trigger a totals recalculation for that estimate.
- On initial load of `EstimateDetail`, load and display current totals and the latest snapshot reference (if any).

### UI Actions
- Line item form submit (create/update) → save → recalc → update totals panel.
- Line item delete → confirm → delete → recalc → update totals panel.
- “Proceed to Approval” action:
  - Calls the estimate transition/submit service.
  - If backend indicates missing tax config, show actionable error and remain in current state.

### State Changes (Frontend-visible)
- Totals panel transitions:
  - `idle` → `recalculating` → `updated`
  - `recalculating` → `error` (with error banner and resolution hints)
- Estimate “approval readiness” indicator:
  - `ready` when no blocking tax/config errors are present
  - `blocked` when backend returns missing config errors

### Service Interactions
- After line item change: call backend “calculate totals” service with `estimateId`.
- After calculation: refresh estimate view model with returned totals and snapshot id.
- Snapshot view: call backend “get snapshot details” service by `snapshotId` (and/or `estimateId`).

---

## 7. Business Rules (Translated to UI Behavior)

> Note: Monetary/tax logic is authoritative in backend; frontend must enforce **workflow and validation messaging** based on backend responses.

### Validation
- Line item edits must not be considered “complete” until recalculation succeeds OR the UI clearly marks totals as “stale” (see Open Questions).
- If backend returns `ERR_CONFIG_JURISDICTION_MISSING`:
  - UI must display a blocking error (banner) stating tax configuration is missing for the estimate’s jurisdiction/location.
  - UI must disable/hide “Proceed to Approval” action (or allow click but show blocking modal) until resolved.
- If backend returns `ERR_MISSING_TAX_CODE` (or equivalent):
  - UI behavior depends on policy (default tax code vs fail). Must be clarified; until then treat as blocking error.

### Enable/Disable Rules
- Disable “Proceed to Approval” when:
  - Latest calculation attempt failed due to missing tax configuration.
  - Calculation is currently in progress (prevent approving while totals are being recomputed).
- Disable “View Calculation Details” when no `calculationSnapshotId` is available.

### Visibility Rules
- Show “Rounding adjustment” row only if non-zero **unless** backend/UX requires always visible (Open Question).
- Show per-line tax amount only when backend provides it (do not derive).

### Error Messaging Expectations
- Errors must display:
  - A concise title
  - The backend-provided error code
  - User action guidance (e.g., “Contact admin to configure tax jurisdiction for location X”)
- Do not expose stack traces or sensitive details.

---

## 8. Data Requirements

### Entities Involved (Frontend-consumed)
- `Estimate`
- `EstimateItem` (parts/labor/fees/discounts)
- `CalculationSnapshot` (read-only view)
- (Referenced configuration entities like `TaxRule`, `FeeRule` are not edited here)

### Fields (Type, Required, Defaults)
#### Estimate (view model)
- `estimateId` (UUID, required)
- `locationId` (UUID, required for tax jurisdiction context; source may be implicit)
- `subtotal` (DECIMAL(19,4), required display)
- `taxTotal` (DECIMAL(19,4), required display)
- `feeTotal` (DECIMAL(19,4), required display)
- `discountTotal` (DECIMAL(19,4), required display)
- `roundingAdjustment` (DECIMAL(19,4), optional display)
- `grandTotal` (DECIMAL(19,4), required display)
- `calculationSnapshotId` (UUID, optional)
- `totalsCalculatedAt` (datetime, optional; only if backend provides)

#### EstimateItem (minimum for triggering calc and display)
- `estimateItemId` (UUID, required)
- `itemType` (enum: PART|LABOR|FEE|DISCOUNT, required)
- `quantity` (DECIMAL, required)
- `unitPrice` (DECIMAL(19,4), required)
- `taxCode` (string, optional/required depends on policy)
- `taxAmount` (DECIMAL(19,4), optional—backend-derived)
- `isTaxable` (boolean, optional—backend-derived)

#### CalculationSnapshot (read-only)
- `snapshotId` (UUID, required)
- `estimateId` (UUID, required)
- `calculationTimestamp` (datetime, required)
- `inputLineItems` (structured/JSON, required)
- `appliedRules` (structured/JSON, required)
- `outputSubtotal`, `outputTaxTotal`, `outputFeeTotal`, `outputDiscountTotal`, `outputGrandTotal`, `outputRoundingAdjustment` (DECIMAL(19,4), required)

### Read-only vs Editable by State/Role
- All totals fields are **read-only** in UI (backend-calculated).
- Calculation snapshot is **read-only**.
- Line items editable only in estimate mutable states (exact states depend on workexec lifecycle; see Open Questions).

### Derived/Calculated Fields
- All totals and tax amounts are derived from backend calculation results; frontend must not compute.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui naming is provisional; exact service names/paths must align to backend integration.

### Load/View Calls
- `Estimate.get` (or screen auto-load) by `estimateId` returns estimate header, line items, and latest totals + snapshot reference.
- `CalculationSnapshot.get` by `snapshotId` returns snapshot detail for display.

### Create/Update Calls
- `EstimateItem.create/update/delete` (existing workexec services)
  - On success, frontend triggers recalc.

### Submit/Transition Calls
- `Estimate.calculateTotals` (pricing integration)
  - Request: `{ estimateId }`
  - Response: totals + `calculationSnapshotId` + optional per-line tax breakdown + `totalsCalculatedAt`
  - Errors:
    - `ERR_CONFIG_JURISDICTION_MISSING` (blocking)
    - `ERR_MISSING_TAX_CODE` (policy-dependent)
    - generic validation errors (400)
- `Estimate.proceedToApproval` (workexec transition)
  - Must fail if totals invalid/missing config; frontend surfaces error.

### Error Handling Expectations
- Map backend error codes to:
  - blocking banner for config errors
  - inline form errors for line item validation errors (quantity, price, etc.)
- Preserve and display correlation/request id if returned in headers/body (for support).

---

## 10. State Model & Transitions

### Allowed States (Estimate)
- Must be confirmed from workexec domain; minimally:
  - `DRAFT` (editable, recalculation allowed)
  - `APPROVAL_PENDING` / `SUBMITTED` (transition target)
  - Other states (APPROVED, CANCELLED, etc.) are unknown here

### Role-based Transitions
- Service Advisor can trigger “Proceed to Approval” from editable state(s) only.
- If user lacks permission, UI must hide/disable the action and show “not authorized” on direct access.

### UI Behavior per State
- Editable states: line items editable; recalculation auto-triggers.
- Non-editable states: line items read-only; totals read-only; snapshot view remains accessible.

---

## 11. Alternate / Error Flows

### Validation Failures
- Invalid line item data (e.g., negative quantity/unitPrice) returned by backend:
  - Keep user on line item edit form
  - Show field-level errors if provided; otherwise show banner with message.

### Concurrency Conflicts
- If line items were updated elsewhere and recalc returns conflict (409):
  - Prompt user to reload estimate
  - Provide “Reload” action that refreshes estimate + line items + totals.

### Unauthorized Access
- If calculation or snapshot endpoints return 401/403:
  - Redirect to login (401) or show not-authorized screen (403)
  - Do not leak details of tax configuration.

### Empty States
- Estimate with zero line items:
  - Totals display as zero amounts (as returned by backend) or show “No items yet” and hide snapshot link.
  - No recalculation triggered unless backend requires it on load.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Totals recalculate after adding a taxable part line
**Given** an Estimate in an editable state is open in the Estimate Detail screen  
**When** the user adds a new PART line item and saves successfully  
**Then** the UI triggers a totals calculation for that estimate  
**And** the totals panel updates to display the returned subtotal, tax total, fee total, discount total, and grand total  
**And** the UI displays a link to the returned calculation snapshot id

### Scenario 2: Totals recalculate after modifying an existing labor line
**Given** an Estimate with existing line items is open  
**When** the user edits a LABOR line item and saves successfully  
**Then** the UI triggers a totals calculation  
**And** the totals panel reflects the updated totals returned by the backend

### Scenario 3: Mixed taxable and non-taxable items display updated totals
**Given** an Estimate contains a taxable PART line and a non-taxable LABOR line (as determined by backend tax rules)  
**When** the user triggers recalculation by editing either line item  
**Then** the UI displays the tax total returned by the backend consistent with only the taxable line being taxed  
**And** the UI does not compute or override tax values locally

### Scenario 4: Missing jurisdiction configuration blocks approval
**Given** an Estimate is associated with a location/jurisdiction with no configured tax rules  
**When** the user adds or edits a line item causing recalculation  
**And** the backend responds with error code `ERR_CONFIG_JURISDICTION_MISSING`  
**Then** the UI shows a blocking banner indicating tax configuration is missing  
**And** the “Proceed to Approval” action is disabled (or blocked) with a clear reason  
**And** the user can remain on the estimate and continue editing line items but cannot proceed to approval

### Scenario 5: Calculation snapshot is viewable and read-only
**Given** an Estimate has a `calculationSnapshotId` displayed  
**When** the user selects “View Calculation Details”  
**Then** the system navigates to the snapshot view screen  
**And** the snapshot screen loads and displays calculation timestamp, inputs, applied rules, and output totals as read-only data

### Scenario 6: Missing tax code handling is surfaced (policy-dependent)
**Given** a line item requires a tax code but none is present  
**When** recalculation is triggered  
**Then** if the backend rejects with `ERR_MISSING_TAX_CODE`, the UI shows a blocking error message including the error code  
**And** the UI indicates which line(s) require attention if backend provides per-line details

---

## 13. Audit & Observability

### User-visible audit data
- Display `calculationSnapshotId` and `totalsCalculatedAt` (if provided) on `EstimateDetail`.
- Snapshot view provides read-only breakdown sufficient for disputes (“explain totals later”).

### Status history
- If backend provides a list/history of snapshots, show them in chronological order; otherwise show latest only.

### Traceability expectations
- Every successful recalculation must result in a snapshot id stored/returned and displayed.
- Frontend logs (console/network) must not include sensitive details; rely on correlation id for support.

---

## 14. Non-Functional UI Requirements

- **Performance:** Recalculation request should not block UI interactions; show loading state in totals panel. Avoid multiple concurrent recalculation calls (debounce/serialize).
- **Accessibility:** Error banners and disabled actions must be screen-reader accessible (aria-live for calculation errors; clear button labels).
- **Responsiveness:** Totals panel and snapshot view must render on tablet and desktop layouts.
- **i18n/timezone/currency:** Display monetary values using currency formatting provided by app conventions; do not assume currency code if not supplied (Open Question). Dates/times displayed in user locale/timezone if available.

---

## 15. Applied Safe Defaults
- SD-UX-1 (Empty State): Show “No items yet” and zeroed totals when estimate has no line items; safe as it’s purely presentational and does not change domain logic. Impacted sections: UX Summary, Alternate/Empty States.
- SD-UX-2 (Debounce/Serialize Requests): Debounce recalculation to avoid concurrent calls during rapid edits; safe as it only reduces duplicate requests and does not change calculation logic. Impacted sections: Functional Behavior, Non-Functional Performance.
- SD-ERR-1 (Standard Error Mapping): Map 401→login, 403→not authorized, 409→reload prompt, 5xx→generic retry banner; safe as it’s standard transport-level handling. Impacted sections: Error Flows, Service Contracts.

---

## 16. Open Questions

1. **Backend contract details (blocking):** What are the exact Moqui service names/REST endpoints and response schemas for:
   - calculate totals for estimate
   - load calculation snapshot details
   - estimate approval transition/submit  
   (Need error code list and how per-line tax details are returned.)

2. **Missing tax code policy (blocking):** For taxable items without a tax code, is the policy:
   - fail calculation (`ERR_MISSING_TAX_CODE`) or
   - apply a configured default tax code and proceed (and how is this flagged in UI/audit)?

3. **Tax-inclusive vs tax-exclusive (blocking):** Which modes must the frontend support now, and how does backend indicate the mode for an estimate/location?

4. **Line-level vs header-level taxes (blocking):** Will backend return line-level tax amounts, header-level only, or both? What should the UI display per line item?

5. **Estimate lifecycle states (blocking):** What are the exact editable vs non-editable estimate states and the exact transition name for “Proceed to Approval” in Moqui/workexec?

6. **Currency & formatting (blocking if multi-currency):** Is currency always implied by location/company, or must totals include an explicit currency code to display?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Estimate: Calculate Taxes and Totals on Estimate  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/236  
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] Estimate: Calculate Taxes and Totals on Estimate

**Domain**: general

### Story Description

/kiro  
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
System

## Trigger
Estimate line items are created or modified (parts/labor/fees/discounts).

## Main Flow
1. System identifies taxable basis per line item using tax codes and jurisdiction.
2. System calculates line-level and/or header-level taxes per configuration.
3. System applies discounts, fees (shop supplies, environmental), and rounding rules.
4. System updates estimate subtotal, tax total, and grand total.
5. System records calculation snapshot (inputs and outputs) for audit/reproducibility.

## Alternate / Error Flows
- Missing tax code on an item → apply default tax code or block based on policy.
- Tax region not configured → block submission for approval and surface configuration error.

## Business Rules
- Tax rules may vary by item type (parts vs labor vs fees).
- Support tax-inclusive and tax-exclusive modes.
- Persist enough calculation context to explain totals later (disputes).

## Data Requirements
- Entities: Estimate, EstimateItem, TaxRule, FeeRule, CalculationSnapshot
- Fields: taxCode, taxRate, taxAmount, subtotal, discountTotal, feeTotal, grandTotal, roundingAdjustment

## Acceptance Criteria
- [ ] Totals and taxes update correctly for mixed taxable/non-taxable items.
- [ ] System stores a calculation snapshot that can be reviewed.
- [ ] Estimate cannot proceed to approval if required tax configuration is missing (per policy).

## Notes for Agents
Ensure calculation snapshots can be reused during promotion/invoice variance explanations.

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