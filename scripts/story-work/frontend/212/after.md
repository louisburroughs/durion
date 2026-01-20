## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Invoicing: Calculate Taxes, Fees, and Totals on Draft Invoice (Audit Snapshot + Variance Surfacing)

### Primary Persona
System (automated calculation process) with visibility for Service Advisor / Accountant reviewing results in the POS UI.

### Business Value
Ensures draft invoices display accurate, explainable totals (subtotal, tax, fees, rounding adjustment, grand total) based on authoritative rules; creates an auditable calculation snapshot and variance records so invoices are compliant and reviewable before issuance.

---

## 2. Story Intent

### As a / I want / So that
- **As the System**, I want invoice totals to be recalculated whenever a draft invoice is created or its line items change, **so that** the invoice shows correct, auditable totals and is blocked from issuance when tax basis data is incomplete.

### In-scope
- Frontend UI behavior to:
  - Trigger/submit totals calculation for a **draft invoice** (on create and on line changes).
  - Display calculated totals, per-line tax amounts, rounding adjustment, and calculation status.
  - Display variance vs estimate snapshot (amount + reason code), including “requires approval” indicators if returned by backend.
  - Display actionable validation errors for missing tax basis fields.
  - Display audit/trace references for calculation snapshot (IDs/timestamps) when available.
- Moqui screen/form wiring (screens, transitions, forms) for the above.

### Out-of-scope
- Defining tax rules/fee rules or performing calculations client-side.
- Issuing invoices (owned by Billing domain) except for **UI-level blocking** indicators based on calculation state.
- Approval workflow UX for variances exceeding thresholds (only surface status/need; approval action is not defined here).
- Credit memo creation/issuance.
- Editing/maintaining Chart of Accounts, Posting Rules, or Journal Entries.

---

## 3. Actors & Stakeholders
- **Actors**
  - `System`: initiates calculation when triggered by invoice/line changes.
  - `Service Advisor`: reviews totals/variance before discussing with customer.
  - `Accountant/Finance`: relies on auditability and variance traceability.
- **Stakeholders**
  - `Customer`: indirectly benefits from accurate invoice totals and transparency.
  - `Support/Operations`: needs clear error states and retriable behavior when tax service is down.

---

## 4. Preconditions & Dependencies

### Preconditions
- A draft `Invoice` exists and is viewable in the POS UI.
- Invoice has zero or more `InvoiceItem` lines.

### Dependencies
- Backend endpoint(s) exist to:
  - Load invoice + invoice items + current calculation status and totals.
  - Trigger totals calculation and return updated invoice/calculation snapshot/variance data OR return validation errors.
- Backend enforces:
  - Mandatory tax basis validation (`taxCode`, `jurisdiction`, `pointOfSaleLocation`, `productType`).
  - Rounding policy: HALF_UP, currency scale; round per-line then sum; persist rounding deltas.
  - State transitions to `TotalsCalculated` or `CalculationFailed` as applicable.

### Frontend tech dependencies
- Vue 3 + TypeScript + Quasar components.
- Moqui screen framework conventions (screen paths, transitions, forms/services).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Invoice Draft detail screen (existing or to be created) where invoice lines are added/edited/removed.
- Invoice creation flow (draft invoice created → should immediately attempt calculation if lines exist, or when first line is added).

### Screens to create/modify
- **Modify**: `apps/pos/screen/invoice/InvoiceDetail.xml` (or equivalent invoice detail screen in this repo)
  - Add a “Totals & Taxes” section bound to invoice totals + calculation state.
  - Add “Variance vs Estimate” section when estimate snapshot exists and variance records exist.
  - Add “Calculation Snapshot” metadata display (snapshot id, calculatedAt) if present.
- **Optional new sub-screen**: `invoice/InvoiceTotalsPanel.xml` (embedded) to isolate totals/variance rendering.

> If actual screen paths differ in the repo, implementation should follow the project README screen organization conventions.

### Navigation context
- From invoice list → invoice detail.
- From work/estimate context → invoice detail (if supported).
- Calculation is not a separate page; it is an operation initiated from invoice detail.

### User workflows
**Happy path**
1. User opens Draft invoice detail.
2. User adds/edits/removes an invoice line.
3. UI triggers “Recalculate totals” (automatic with debounce) or user clicks “Recalculate” if auto is disabled.
4. UI shows in-progress state; on success, totals update and status shows `TotalsCalculated`.
5. If variance exists, UI displays variance amount and reason code.

**Alternate paths**
- Missing tax basis: UI shows `CalculationFailed` and field-level/actionable errors; totals are not updated (or show last known totals with a clear stale indicator).
- Tax service unavailable: UI shows failure with retriable “Retry calculation” action; remains blocked from issuance.
- Concurrency/optimistic lock: UI prompts to reload invoice and retry.

---

## 6. Functional Behavior

### Triggers
- **T1**: Draft invoice is created (and has at least one line item) → request calculation.
- **T2**: Invoice line is added/modified/removed on a Draft invoice → request calculation.
- **T3**: User manually clicks “Recalculate totals” → request calculation.

### UI actions
- Display a calculation status indicator derived from `Invoice.status`:
  - `TotalsCalculationRequired` or “dirty” client state → indicates calculation needed.
  - `TotalsCalculated` → show computed totals normally.
  - `CalculationFailed` → show error panel with reasons and remediation guidance.
- Disable any “Issue invoice” entry point/button **in the invoice UI** when:
  - status is `CalculationFailed`, or
  - status is not `TotalsCalculated`, or
  - backend indicates blocking conditions (if such a flag is returned).
  - (Note: actual issuance action is out-of-scope; this is purely UI gating if the control exists.)

### State changes (frontend perspective)
- After a successful calculate call:
  - Refresh invoice header and items from response and render updated totals.
  - Render calculation snapshot reference (id/timestamp) if returned.
- After a failed calculate call:
  - Render `CalculationFailed` status and show error details.
  - Keep the invoice screen usable for fixing line tax basis fields (e.g., editing taxCode) if those fields are editable in Draft.

### Service interactions (Moqui)
- Use Moqui `transition` actions from InvoiceDetail screen:
  - `transition name="recalculateTotals"` → calls a service that triggers calculation and returns updated invoice context.
- Ensure transitions are idempotent from the UI standpoint (multiple clicks should not create duplicate variance records beyond backend rules).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- When calculation is triggered, if backend returns missing mandatory tax basis fields, UI must:
  - Show a summary error: “Cannot calculate totals: missing tax basis information.”
  - List missing fields by name and identify impacted line(s) when provided (line index/id).
  - Keep invoice in a “blocked” visual state (status `CalculationFailed`).

Mandatory fields to surface (from authoritative rules):
- `taxCode`
- `jurisdiction`
- `pointOfSaleLocation`
- `productType`

### Enable/disable rules
- Recalculate action enabled only when invoice is in Draft-related statuses (Draft, TotalsCalculationRequired, TotalsCalculated, CalculationFailed) and user has access to invoice detail.
- If invoice is `Issued`, hide/disable recalculation controls and display totals as read-only (immutability).

### Visibility rules
- Show “Variance vs Estimate” section only if:
  - `estimateSnapshotId` exists AND
  - variance data exists OR backend returns “no variance” explicitly.
- Show calculation snapshot metadata only if `calculationSnapshotId`/`calculatedAt` exists.

### Error messaging expectations
- Missing tax basis: actionable, field-specific.
- Service unavailable: “Tax service unavailable. Try again.” plus a retry action.
- Conflicts: “Invoice was updated elsewhere. Reload to see latest.”

---

## 8. Data Requirements

### Entities involved (read via backend; not authored client-side)
- `Invoice`
- `InvoiceItem`
- `CalculationSnapshot`
- `EstimateSnapshot` (reference for variance comparison)
- `Variance`

### Fields (frontend display/edit needs)

**Invoice**
- `invoiceId` (String/UUID) — required, read-only
- `status` (Enum: `Draft`, `TotalsCalculationRequired`, `TotalsCalculated`, `CalculationFailed`, `Issued`) — read-only display, drives UI gating
- `subtotal` (Decimal, currency-scale) — read-only display
- `totalTax` (Decimal) — read-only
- `totalFees` (Decimal) — read-only
- `roundingAdjustment` (Decimal) — read-only; display even if 0.00
- `grandTotal` (Decimal) — read-only, emphasized
- `estimateSnapshotId` (String/UUID) — read-only
- `invoiceDate` (Timestamp) — display (existing)
- `jurisdiction` (String) — editable only if invoice is Draft and backend allows; otherwise read-only
- `pointOfSaleLocationId` (String/UUID) — editable only if Draft and backend allows; otherwise read-only
- `hasOverride` (Boolean) / `overrideReason` / `overriddenByUser` — display read-only if present (no override action defined here)

**InvoiceItem**
- `invoiceItemId` — required
- `productId` — required
- `taxCode` (String) — editable in Draft if backend permits; required for successful calculation
- `lineTotal` (Decimal) — read-only if derived from qty*unitPrice; otherwise display value returned
- `taxAmount` (Decimal) — read-only (returned after calculation)
- `isExempt` (Boolean) — display; editing rules not defined here
- `productType` (String/Enum) — required for tax basis; editability unclear (see Open Questions)

**CalculationSnapshot**
- `calculationSnapshotId` — display
- `calculatedAt` — display
- `calculationDetails` (JSON/Text) — not rendered raw by default; provide “View details” only if a dedicated screen/panel exists (not required in this story).

**Variance**
- `varianceAmount` (Decimal) — display
- `varianceReasonCode` (Enum list from backend) — display as code + human label
- `detectedAt` — display
- `notes` — display if present
- `approvedBy/approvedAt` — display if present

### Derived/calculated fields (UI only)
- “Needs recalculation” boolean derived from invoice status or local dirty flag.
- Currency formatting derived from `currencyUomId` if provided; otherwise fall back is **not assumed** (see Open Questions).

---

## 9. Service Contracts (Frontend Perspective)

> Backend names/paths are not provided in inputs; below are required contracts the frontend will call via Moqui transitions. If actual service names differ, implement adapters in Moqui facade services.

### Load/view calls
- **SVC1: Load Invoice Detail**
  - Input: `invoiceId`
  - Output: `Invoice` + `InvoiceItem[]` + optional `Variance[]` + optional `CalculationSnapshot` reference
  - Errors: 404 not found; 403 unauthorized

### Create/update calls (line edits)
- **SVC2: Update Invoice Items**
  - Input: `invoiceId`, line modifications (add/update/remove)
  - Output: updated invoice/items and sets invoice status to `TotalsCalculationRequired` (or equivalent)
  - Errors: validation; 409 conflict (optimistic lock)

### Submit/transition calls
- **SVC3: Calculate Invoice Totals**
  - Input: `invoiceId`
  - Output on success:
    - Updated `Invoice` totals fields
    - Updated `Invoice.status = TotalsCalculated`
    - `CalculationSnapshot` reference (id + calculatedAt)
    - `Variance[]` if created/updated
  - Output on failure:
    - `Invoice.status = CalculationFailed`
    - Structured error with missing field list and impacted entities
  - Error handling expectations:
    - 422 for validation failures (missing tax basis)
    - 503/502 for tax service unavailable
    - 409 for state conflict (invoice Issued, or optimistic lock)
    - Errors should include machine code(s) for UI mapping (examples from backend checklist):
      - `SCHEMA_VALIDATION_FAILED`
      - `INVOICE_TOTAL_NEGATIVE_REQUIRES_CREDIT_MEMO` (UI should display but action is out-of-scope)
      - `CONFIGURATION_ERROR`

### Frontend error handling mapping
- Validation (4xx/422): show inline + summary, keep user on invoice detail.
- Service unavailable (5xx/503): show non-destructive banner + retry button.
- Conflict (409): prompt reload; do not retry automatically without reload.

---

## 10. State Model & Transitions

### Allowed states (as rendered in UI)
- `Draft`
- `TotalsCalculationRequired`
- `TotalsCalculated`
- `CalculationFailed`
- `Issued` (read-only)

### Role-based transitions (frontend gating)
- Calculation is **System-initiated**; users can trigger via UI but backend authorizes operation.
- If invoice is `Issued`: do not show recalculation; do not allow edits.

### UI behavior per state
- **Draft**: totals may be empty or stale; show “Recalculate” affordance when lines exist.
- **TotalsCalculationRequired**: show “Needs recalculation”; allow recalc; optionally auto-trigger.
- **TotalsCalculated**: show totals normally; show variance section if present.
- **CalculationFailed**: show blocking error panel; highlight missing tax basis; allow user to correct fields and retry.
- **Issued**: everything read-only; show calculation snapshot reference; no actions.

---

## 11. Alternate / Error Flows

### Validation failures (missing tax basis)
- User triggers recalc.
- Backend returns missing fields (and ideally line identifiers).
- UI:
  - Sets/reflects status `CalculationFailed`.
  - Shows list of missing fields (grouped by invoice-level vs line-level if provided).
  - Keeps totals display but marks as “not calculated / stale” if backend does not return totals.

### Concurrency conflicts
- If line edits or calculate return 409:
  - UI shows “Invoice updated elsewhere” and provides “Reload invoice” action.
  - After reload, user may retry recalc.

### Unauthorized access
- If 403 on load: show access denied screen.
- If 403 on calculate: keep invoice visible but disable calculate and show “You don’t have permission to recalculate totals.”

### Empty states
- Draft invoice with zero lines:
  - Totals section shows zeros or “—” per project convention; do not auto-calculate.
  - Recalculate action disabled until at least one line exists.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Calculate totals for a standard taxable invoice
**Given** a Draft invoice exists with one line item priced at 100.00 with quantity 2  
**And** the line item has required tax basis fields including `taxCode`, `productType`  
**And** the invoice has required tax basis fields including `jurisdiction` and `pointOfSaleLocation`  
**When** the user triggers “Recalculate totals” (or the system auto-triggers after line change)  
**Then** the UI requests the Calculate Invoice Totals service for that invoice  
**And** on success the UI displays `subtotal = 200.00`  
**And** the UI displays `totalTax` and `grandTotal` values returned by the backend  
**And** the invoice status displayed is `TotalsCalculated`  
**And** the UI displays `roundingAdjustment` explicitly (including 0.00).

### Scenario 2: Mixed-tax invoice displays correct aggregated totals
**Given** a Draft invoice exists with two line items: one taxable and one non-taxable  
**When** totals are recalculated successfully  
**Then** the UI displays a single invoice-level `totalTax` equal to the backend response  
**And** the UI displays per-line `taxAmount` values returned for each line  
**And** the UI displays `grandTotal` equal to `subtotal + totalTax + totalFees + roundingAdjustment` as returned (no client recomputation is authoritative).

### Scenario 3: Variance is surfaced when estimate snapshot differs
**Given** a Draft invoice is linked to an `estimateSnapshotId`  
**And** recalculation returns a `Variance` record with `varianceReasonCode = TAX_RULE_CHANGE` and a non-zero `varianceAmount`  
**When** the invoice detail is rendered after calculation  
**Then** the UI displays a “Variance vs Estimate” section  
**And** it shows `varianceAmount` and `varianceReasonCode`  
**And** it shows `detectedAt` timestamp.

### Scenario 4: Block calculation and issuance UI when mandatory tax basis is missing
**Given** a Draft invoice exists where at least one required tax basis field is missing (e.g., an item missing `taxCode`)  
**When** totals calculation is triggered  
**Then** the UI displays invoice status `CalculationFailed`  
**And** the UI shows an actionable error listing the missing field(s)  
**And** the UI disables/hides any “Issue invoice” action on this screen (if present) due to calculation failure.

### Scenario 5: Tax service unavailable shows retriable error
**Given** a Draft invoice exists with complete tax basis fields  
**When** totals calculation returns a service-unavailable error (5xx/503)  
**Then** the UI shows a non-destructive error banner indicating calculation could not be completed  
**And** the UI provides a “Retry calculation” action  
**And** the invoice remains not issuable in the UI until calculation succeeds.

### Scenario 6: Issued invoice is immutable in UI
**Given** an invoice is in `Issued` state  
**When** the invoice detail screen is opened  
**Then** the UI does not show invoice-line editing controls  
**And** the UI does not show “Recalculate totals” controls  
**And** the UI displays totals and (if present) calculation snapshot reference as read-only.

---

## 13. Audit & Observability

### User-visible audit data
- Display (when available from backend):
  - `CalculationSnapshot.calculatedAt`
  - `calculationSnapshotId`
  - Variance `detectedAt`, and approval fields if present (`approvedBy`, `approvedAt`).

### Status history
- If backend exposes invoice status history/audit events, provide a collapsible “History” panel (optional). If not exposed, do not invent.

### Traceability expectations
- All calculate actions should include identifiers in logs/telemetry:
  - `invoiceId`
  - resulting `calculationSnapshotId` (if success)
  - variance identifiers (if returned)
- Moqui screen transition should log outcome (success/failure) at INFO/WARN with sanitized error code.

---

## 14. Non-Functional UI Requirements

- **Performance**: Recalculation trigger should be debounced on rapid line edits (avoid firing on every keystroke); only trigger after the edit is committed.
- **Accessibility**: Error summary must be screen-reader accessible; focus should move to error summary on failure.
- **Responsiveness**: Totals section must render well on tablet sizes used in POS.
- **i18n/timezone/currency**:
  - Use locale-aware formatting consistent with existing app conventions.
  - Do not assume currency; format using currency from backend if provided (see Open Questions).

---

## 15. Applied Safe Defaults
- SD-UI-ERG-01 (Debounced auto-trigger): Automatically debounce recalculation after line changes to reduce API churn; qualifies as UI ergonomics and does not change business rules. Impacted sections: UX Summary, Functional Behavior, Non-Functional.
- SD-ERR-MAP-01 (Standard HTTP error mapping): Map 422/409/503 to inline validation, reload prompt, and retry banner patterns; qualifies as standard error-handling mapping. Impacted sections: Service Contracts, Alternate/Error Flows.

---

## 16. Open Questions
1. What are the **actual Moqui service names and screen paths** for:
   - loading invoice detail,
   - updating invoice items,
   - triggering totals calculation?
2. Does the backend return a **currency identifier** (e.g., `currencyUomId`) on Invoice/CalculationSnapshot for correct formatting? If not, what is the frontend’s authoritative currency source?
3. For missing tax basis validation, will the backend return **line-level pointers** (e.g., `invoiceItemId` + missing fields), or only a flat list of missing fields?
4. Are `jurisdiction`, `pointOfSaleLocationId`, and `productType` **editable in the invoice UI** while in Draft, or are they sourced from upstream entities (work order, location, product catalog) and read-only?
5. If variances exceed thresholds and require approval, does the backend return an explicit **“requiresApproval” flag** or approval status to render, or should the frontend infer only from presence of `approvedBy/approvedAt`?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Invoicing: Calculate Taxes, Fees, and Totals on Invoice  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/212  
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] Invoicing: Calculate Taxes, Fees, and Totals on Invoice

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
Invoice draft is created or invoice lines are adjusted.

## Main Flow
1. System applies tax and fee rules to invoice lines based on snapshot/tax config.
2. System calculates subtotal, taxes, fees, rounding adjustments, and grand total.
3. System compares invoice totals to estimate snapshot totals and records variance reasons where applicable.
4. System updates invoice totals and stores calculation snapshot.
5. System prevents issuing invoice if tax basis is incomplete (policy).

## Alternate / Error Flows
- Tax configuration changed since estimate → flag variance and require review.
- Missing tax codes → block issuance and show actionable errors.

## Business Rules
- Tax calculation must be explainable and auditable.
- Mixed-tax scenarios must be supported.

## Data Requirements
- Entities: Invoice, InvoiceItem, TaxRule, CalculationSnapshot, EstimateSnapshot
- Fields: taxCode, taxRate, taxAmount, feeTotal, roundingAdjustment, varianceAmount, varianceReason

## Acceptance Criteria
- [ ] Invoice totals compute correctly for mixed-tax scenarios.
- [ ] System records variance vs estimate snapshot when applicable.
- [ ] Invoice cannot be issued when required tax basis is missing.

## Notes for Agents
Variance explanations reduce disputes—capture them automatically when possible.

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