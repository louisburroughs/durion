# [FRONTEND] [STORY] Estimate: Calculate Taxes and Totals on Estimate
## Purpose
Enable the Estimate Edit screen to request backend-calculated totals (subtotal, taxes, fees, discounts, rounding, grand total) after line item changes, and display the latest calculation snapshot. Prevent client-side tax computation and ensure “Proceed to Approval” is blocked when tax configuration is missing or totals are invalid. Provide clear loading, success, and error states while avoiding race conditions from overlapping recalculation calls.

## Components
- Page header: Estimate identifier + status (editable vs non-editable)
- Line Items section
  - Line item list/table (PART, LABOR, etc.)
  - Add line item button (editable states only)
  - Edit line item action (editable states only)
  - Read-only row display (non-editable states)
  - Field-level validation error display on save failure
- Totals panel (read-only, backend-driven)
  - Subtotal
  - Tax total
  - Fee total
  - Discount total
  - Rounding adjustment (optional)
  - Grand total
  - Calculation status indicator (idle/loading/success/error)
  - Retry action (for API error states)
- Snapshot access
  - “View latest calculation snapshot” link (enabled only when latestSnapshot exists)
  - Snapshot details view (read-only; shows timestamp + structured snapshot payload summary)
- Primary actions footer
  - Proceed to Approval button (disabled when blocking totals/tax errors or while calculating)
  - Save/Cancel controls for line item edits (within edit form/modal)
- Modals/alerts
  - Concurrency conflict modal (“This estimate was modified…”) for 409
  - Inline banner or panel error for missing tax configuration

## Layout
- Top: Header (Estimate title/ID, status badge)
- Main (two-column): Left = Line Items list + add/edit; Right = Totals panel + Snapshot link
- Bottom: Sticky footer actions (Proceed to Approval; context actions)

## Interaction Flow
1. Load Estimate Edit screen.
   1. Render line items (editable or read-only based on state).
   2. Render totals panel using values present in loaded model when available; otherwise show placeholders.
   3. If latestSnapshot exists, enable snapshot link; otherwise keep it disabled.
2. Add a new PART line item (editable state).
   1. User clicks “Add line item,” completes form, and saves.
   2. If backend rejects save: keep user in form, show field-level errors, do not trigger recalculation.
   3. If save succeeds: trigger “Calculate totals” call for the estimate.
   4. Totals panel enters Loading state (spinner); Proceed to Approval disabled while pending.
   5. On success response: update totals panel with returned values (including optional rounding adjustment) and enable snapshot link to returned latestSnapshot.
3. Edit an existing LABOR line item (editable state).
   1. User edits and saves line item.
   2. On successful save, trigger totals calculation; update totals panel from backend response.
4. Mixed taxable/non-taxable items.
   1. After recalculation, display tax total exactly as returned by backend.
   2. Do not compute, infer, or override tax values locally (no client-side tax math).
5. Serialize recalculation calls (race prevention).
   1. If multiple mutations occur quickly, queue/serialize calculate calls.
   2. Ignore out-of-order responses; only the latest successful response updates the totals panel/snapshot link.
6. Recalculation failure handling.
   1. If tax configuration missing for location: totals panel shows specific error (“Tax configuration missing for this location”), snapshot link behavior remains based on latestSnapshot, and Proceed to Approval is disabled (blocking).
   2. If line item tax code required error is returned: show inline guidance (and/or surface in totals panel if returned there) and keep Proceed to Approval disabled when blocking.
   3. If generic API error (400/500): totals panel shows error state with Retry; apply exponential backoff on repeated retries.
7. Concurrency/version mismatch (409).
   1. Show modal indicating the estimate was modified and user must refresh/reload; prevent proceeding until resolved.
8. Non-editable states.
   1. Line items are read-only; totals panel is read-only.
   2. Snapshot view remains accessible if latestSnapshot exists.
9. Empty estimate (zero line items).
   1. Show “No items yet” in line items section.
   2. Totals show backend-provided values if present; otherwise placeholders until first successful calculation.
   3. Snapshot link disabled until a snapshot exists.

## Notes
- Totals must come from backend calculation response; UI must not compute taxes/totals locally.
- Required model context: Estimate must include locationId (tax jurisdiction context) and identifiers needed for calculate/transition calls.
- Totals panel state machine:
  - Idle (show latest snapshot totals)
  - Loading (spinner; disable Proceed)
  - Success (updated totals + snapshot link)
  - Tax config missing (blocking error; disable Proceed)
  - API error (retry with exponential backoff)
- Proceed to Approval must fail/disable when tax config missing or totals invalid; UI should reflect blocking state before attempting transition when possible.
- Validation behavior:
  - Line item save failures: stay in edit UI, show field-level errors, do not trigger recalculation.
  - Recalculation failures: show totals panel error state; disable Proceed when blocking.
- Error codes/messages are partially blocking/unspecified; implement mapping placeholders with TODO to confirm exact codes and required fields (e.g., missing required fields, tax config missing, tax code required, concurrent policy version mismatch, unexpected server error).
- Snapshot view should display timestamp and a readable summary of structured payloads (inputs/outputs) without allowing edits.
