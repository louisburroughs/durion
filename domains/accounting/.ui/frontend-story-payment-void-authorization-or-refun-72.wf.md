# [FRONTEND] [STORY] Payment: Void Authorization or Refund Captured Payment
## Purpose
Enable authorized users to correct payment mistakes by voiding authorized payments or refunding captured payments from the invoice payment experience. Ensure actions are permissioned, capture required reasons/notes, and provide complete auditability via reversal history. Support async refund processing (e.g., PENDING) with clear status messaging and refresh, while keeping invoice/payment state accurate and reconcilable.

## Components
- Invoice Payments section header
- Payments list/table
  - Payment row summary (amount, method, status, captured/authorized state)
  - Eligibility indicators (void/refund available per backend)
  - Actions menu/buttons: “Void authorization”, “Refund”
- Empty state: “No payments found for this invoice.”
- Reversal History panel/list
  - Filters/association (by payment or invoice-level)
  - Reversal row: type (Void/Refund), amount (refund), reason, notes (if present), status, created by, timestamp, last updated time (if async)
  - Empty state: “No voids/refunds recorded.”
- Void Authorization modal/dialog
  - Read-only payment summary
  - Reason dropdown (from discovery endpoint categories)
  - Notes textarea (conditionally required)
  - Optional fields as required by backend/policy (e.g., additional reason details)
  - Submit, Cancel buttons
  - Inline validation + error banner area
- Refund modal/dialog
  - Read-only payment summary + refundable amount
  - Amount input (decimal string; default full refundable)
  - Currency display (read-only if inferred; input if required by backend)
  - Reason dropdown (from discovery endpoint categories)
  - Notes textarea (conditionally required)
  - Additional required fields (policy/back-end dependent; contract TBD)
  - Elevation prompt area (if required): “Manager elevation required” + token capture/flow trigger
  - Submit, Cancel buttons
  - Inline validation + error banner area
- Status banner/toast system
  - Success confirmation
  - Pending banner: “Refund pending” + last updated time + Refresh action
  - Conflict banner: “Payment state changed; refresh.”
- Refresh control (button/link) for invoice/payment snapshot and reversal status

## Layout
- Top: Invoice header/context (invoice identifier, status) + global messages area
- Main: Payments section
  - Payments list/table with per-row actions on the right
  - Below payments: Reversal History panel/list (or tab within Payments section)
- Modal overlays (center): Void Authorization dialog; Refund dialog
- Inline ASCII hint: [Invoice Header] -> [Payments List (actions right)] -> [Reversal History] ; Modals overlay main

## Interaction Flow
1. View invoice payments
   1. System loads invoice payment snapshot and displays payments list.
   2. If no payments exist, show “No payments found for this invoice.”
   3. System loads reversal history (embedded or separate query) and displays list; if none, show “No voids/refunds recorded.”

2. Void an authorized payment (successful)
   1. User selects “Void authorization” on an eligible authorized payment.
   2. Void dialog opens with payment summary and required fields.
   3. User selects a void reason; UI reveals Notes (and any other fields) if required by policy/backend for that reason.
   4. User submits; UI sends idempotent void command.
   5. On success, show success confirmation.
   6. UI refreshes invoice/payment snapshot (or uses returned updated snapshot) and refreshes reversal history.
   7. Reversal history shows new void record with user identity, reason, timestamp.

3. Refund a captured payment (async pending)
   1. User selects “Refund” on an eligible captured payment.
   2. Refund dialog opens with payment summary, refundable amount, amount input (default full), currency display, and reason selector.
   3. User enters/refines amount (must be valid decimal string) and selects refund reason; UI enforces conditional required fields (notes, additional fields).
   4. User submits; UI sends idempotent refund command.
   5. UI displays refund record status returned by backend in reversal history.
   6. If status is PENDING, show banner “Refund pending” with last updated time and a Refresh action.
   7. On Refresh, UI re-queries invoice/payment snapshot and/or reversal status and updates the displayed status.

4. Elevation required for refund
   1. Backend indicates elevation is required for the selected payment (pre-indicated or returned on submit attempt).
   2. If user attempts to submit without an elevation token, UI blocks submission and prompts for manager elevation.
   3. User completes elevation flow and receives an elevation token.
   4. User submits refund with elevation token; UI sends refund command and refreshes invoice/payment data and reversal history.

5. Concurrency conflict while dialog open
   1. User opens Void/Refund dialog; payment state changes in backend before submit.
   2. On submit, backend returns 409 conflict.
   3. UI shows “Payment state changed; refresh.” and provides a refresh action; after refresh, UI updates eligibility/actions and prevents invalid submissions.

## Notes
- Permissions: Only users with appropriate permissions can see/execute void/refund actions; ineligible payments must not show actionable controls (or must be disabled with explanation).
- Required fields are conditional: reason is required; notes and other fields are required iff policy/backend requires (including reason-dependent requirements).
- Refund amount must be validated (decimal string) and constrained to refundable amount; default to full refundable amount for the primary flow.
- Async refunds: If backend returns PENDING, UI must clearly indicate pending state, show last updated time, and allow manual refresh; reversal history must reflect status.
- Idempotency: Void/refund commands must be idempotent; UI should generate and send an idempotency key per submission attempt and avoid duplicate submissions (disable submit while in-flight).
- Data refresh strategy: After successful commands, UI must refresh invoice/payment snapshot and reversal history (either via response payload or follow-up fetch).
- Contract TBD / TODOs for implementation:
  - Confirm void authorization endpoint, request/response shape, and idempotency mechanism.
  - Confirm refund endpoint, request/response shape (including async status), and idempotency mechanism.
  - Confirm reversal status query options (by invoice, by payment, by reversalId) and whether reversal history is embedded in invoice detail.
  - Confirm discovery endpoint for reason categories and mapping to UI dropdowns.
  - Confirm elevation token acquisition UX and how token is passed to backend.
