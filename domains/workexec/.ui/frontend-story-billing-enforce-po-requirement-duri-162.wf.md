# [FRONTEND] [STORY] Billing: Enforce PO Requirement During Estimate Approval
## Purpose
Ensure the Estimate approval/finalization flow enforces entry of a Purchase Order (PO) number when location/customer billing policy requires it. Prevent non-compliant estimates from being approved and ensure downstream billing/invoicing has the PO reference. Provide clear, real-time validation and display the stored PO data read-only after approval.

## Components
- Estimate Approval / Finalization header (estimate identifier, status)
- Policy indicator (e.g., “PO required for this location”)
- PO Number input field (text)
- PO Number help text (format/pattern hint and max length)
- Optional PO attachment reference field (if supported by backend shape/policy)
- Inline validation message area (per field)
- Approve/Finalize button (primary)
- Cancel/Back button (secondary)
- Loading state on submit (button spinner/disabled state)
- Error banner/toast for backend validation errors
- Approved estimate read-only section showing stored PO number (and attachment reference if present)

## Layout
- Top: Page header with estimate summary + current status
- Main: Approval/Finalization form
  - Policy callout (visible when PO required)
  - PO Number field + help text + inline error
  - Optional PO attachment reference field (if applicable)
- Bottom (footer area of form): [Cancel/Back] (left) and [Approve/Finalize] (right)

## Interaction Flow
1. On page load (or when location is selected/changes), fetch approval/billing policy for the estimate’s location.
2. If policy indicates PO is required:
   1. Show PO Number field as required and display help text with the allowed pattern (e.g., “Format: ABC-12345”) and max length.
   2. Validate in real time:
      - Empty value → show “PO number is required.”
      - Pattern mismatch (alphanumeric + hyphens per policy regex) → show “Invalid PO format.”
      - Exceeds max length → show “Invalid PO format” (per fixture) or length-specific message if allowed by UX.
3. If policy indicates PO is not required:
   1. Hide the PO requirement callout (and optionally the PO field), or show PO field as optional (implementation choice), allowing approval without PO.
4. User clicks Approve/Finalize:
   1. If PO is required and invalid/missing, block submission and focus the PO field.
   2. If valid (or not required), send approve request including PO reference fields in the backend-required shape.
   3. Include required request header (per backend contract).
   4. Show loading/disabled state until response returns.
5. On success:
   1. Refresh estimate data.
   2. Display updated status as Approved.
   3. Show stored PO number read-only on the approved estimate view (and include in finalization snapshot / sales order invoice view where applicable).
6. On backend error responses:
   1. PO_NUMBER_REQUIRED → show field-level error on PO Number and keep user on form.
   2. INVALID_PO_FORMAT → show field-level error on PO Number (covers invalid pattern and max length per fixtures).
   3. Preserve user-entered values for correction and resubmission.

## Notes
- Validation rules:
  - PO# cannot be empty if required by location policy.
  - PO# must match location pattern (alphanumeric + hyphens) provided by policy.
  - PO# length must be ≤ maxLength from policy; exceeding max length maps to INVALID_PO_FORMAT per test fixtures.
- UI behavior:
  - PO input visibility/required state is driven by fetched policy (or backend enforcement on approve); ensure UI handles both proactive policy and reactive backend enforcement.
  - Provide clear help text showing the expected format and constraints.
- Data/contract requirements:
  - Approve request must include PO reference fields in backend-required shape and include the required header.
  - After approval, PO number must be displayed read-only and appear in finalization snapshot and sales order invoice surfaces.
- Test fixtures to cover:
  - Required + valid PO → approval allowed.
  - Not required → approval allowed without PO.
  - Required + empty → PO_NUMBER_REQUIRED.
  - Invalid format → INVALID_PO_FORMAT.
  - Exceeds max length → INVALID_PO_FORMAT.
- TODOs:
  - Confirm exact backend request/response field names (“PO reference fields”) and required header name/value.
  - Confirm whether “PO attachment reference” is supported and when it should be shown (policy-driven vs always optional).
