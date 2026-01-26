# [FRONTEND] [STORY] Order: Apply Price Override with Permission and Reason
## Purpose
Enable authorized users to request or apply an order line unit price override by entering a requested price and selecting a required reason code. The UI must reflect backend-enforced permissions and override state (applied/pending/rejected) and display backend-provided pricing as authoritative. The flow must handle approval-required policies, validation errors, and concurrency/version conflicts without client-side price calculations.

## Components
- Order details header (order identifier/context)
- Order lines list/table
  - Line pricing display (unit price, currency; backend-authoritative)
  - Override status badge/indicator (none/pending/applied/rejected)
  - Override metadata preview (reason, requestedBy/At, approvedBy/At, rejectedBy/At)
  - “Override Price” action button/link (per-line, capability/permission-driven)
- Override Price modal/drawer
  - Requested unit price input (money; decimal-string entry)
  - Currency display (read-only; from line/order)
  - Reason code dropdown (required)
  - Optional free-text note/comment field (only if backend supports; otherwise omit)
  - Submit button
  - Cancel button
  - Inline field validation messages
- Toast/inline alerts (success, pending, permission, not found, conflict)
- Confirmation/info modal for conflict/concurrency (“processed by another user”)
- Loading/pending indicators (submit in-progress; line-level pending state)

## Layout
- Top: Order header + global messages area
- Main: Order Lines table/list (each row shows pricing + override status + action)
- Overlay: Override Price modal/drawer centered/right with form fields + actions
- Inline per-line: status badge + metadata beneath/next to unit price

## Interaction Flow
1. View Order lines
   1. UI renders each line’s unit price and currency using backend-provided money values.
   2. If line missing currency/unitPrice, hide/disable “Override Price” and show “Pricing not available for this line.”
   3. If override exists, show status indicator and available metadata (reason, requested/approved/rejected info, status message if provided).
2. Open Override Price (eligible + permitted)
   1. User clicks “Override Price” on a line only if backend capability flag indicates allowed and user is authorized.
   2. Modal opens with requested unit price input, currency display, and reason code dropdown.
   3. If reason code list is empty/unavailable, show “No override reasons available; contact admin.” and disable Submit.
3. Submit override request
   1. User enters requested unit price (decimal-string) and selects a reason code; submits.
   2. UI sends create line price override request including required identifiers (orderId/lineId), requestedUnitPrice (decimal-string), currency context (from order/line), reason code, and required user/context fields; include optional note only if supported.
   3. UI shows submit loading state; does not compute rounding, totals, or derived pricing client-side.
4. Handle backend response (success/applied)
   1. If backend returns status “applied” (e.g., 201), close modal and refresh/update order pricing subtree (or line pricing) from response.
   2. Update line’s displayed unit price using backend-provided money values; show “Override applied successfully.”
   3. Display override metadata (reason, requestedBy/requestedAt, approvedBy/approvedAt if present).
5. Handle backend response (pending approval)
   1. If backend returns status “pending” (e.g., 202), close modal and show “Override Pending Approval” on the line.
   2. Do not assume totals changed unless backend explicitly returns updated totals/pricing.
   3. Disable further override attempts for that line unless backend capability flag indicates allowed even while pending.
6. Handle backend response (rejected)
   1. If backend returns status “rejected,” show rejected indicator on the line and display backend-provided status message if present.
   2. Allow new override request only if backend capability flag indicates allowed.
7. Error handling
   1. 400/422 validation: keep modal open, highlight invalid/missing fields, show field-level errors.
   2. 403 forbidden: hide/disable override action; show “You don't have permission to apply price overrides.”
   3. 409 conflict (order version mismatch): show modal “This override was processed by another user.” and prompt refresh; on confirm, refresh order and re-render line state.
   4. 404 not found: show “Order or line item not found” and refresh or navigate back.

## Notes
- Backend is authoritative for all money values; UI must not compute rounding, totals, discounts, or derived pricing.
- Override action availability must be driven by backend capability flags and backend-enforced authorization (not just client session).
- Override states to support: none, pending, applied, rejected; each state has distinct indicators and action enablement rules.
- Empty reason list is a blocking state: show message and disable submit.
- Concurrency/versioning: handle 409 by prompting refresh and preventing silent overwrites.
- Data contract expectations:
  - Input includes order/line identifiers, requested unit price as decimal-string, reason code, and required context fields; optional note only if supported.
  - Display includes override id (optional), status enum (required when override exists), reason, requested/approved/rejected by/at, and optional backend status message.
  - Response should include summary (including status and reason) plus updated order pricing subtree or sufficient data to refresh line pricing.
- Approval/reject flows exist via separate endpoints; UI for manager approval is out of scope here unless surfaced elsewhere, but line state must reflect changes after refresh/fetch override details.
