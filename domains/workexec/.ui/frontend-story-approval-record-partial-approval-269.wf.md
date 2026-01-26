# [FRONTEND] [STORY] Approval: Record Partial Approval
## Purpose
Enable a Service Advisor to record partial approvals for a Work Order by making per-line approve/decline decisions and capturing any required approval method/proof. The UI must load approval-eligible line items, enforce backend-driven proof requirements, and submit all decisions atomically with idempotency. After submission, the UI shows the backend-returned Work Order status and approved total, plus read-only audit metadata.

## Components
- Work Order header summary (RO): Work Order ID, customer/location reference, current status, totals, last updated/version
- Line items list/table (services + parts)
  - Line item row: description/type, amount, current approval status (RO), eligibility indicator (RO), decision control (Approve/Decline)
  - Inline validation indicator for missing decision (per eligible item)
- Approved Total (Preview) display (client-side computed)
- Approval method/proof section (backend-driven)
  - Method label/value (RO)
  - Conditional inputs:
    - “Click confirm” checkbox (if required)
    - Advisor note / required note fields (if required)
    - Signature capture placeholder component + “Clear” action (if required)
    - Proof reference display (RO after submit)
- Global error banner area (standard error envelope messaging)
- Conflict/409 handling prompt (banner with “Reload” action)
- Primary actions: “Confirm Approval” (submit), “Cancel” (navigate back)
- Loading state (skeleton/spinner) and disabled states for actions during submit

## Layout
- Top: Page title “Record Approval” + breadcrumb/back to Work Order
- Main (stacked):
  - Work Order header summary card
  - Error banner area (hidden until needed)
  - Line items table (largest section)
  - Right/Below summary: “Approved Total (Preview)”
  - Approval method/proof card (dynamic fields)
- Footer sticky action bar: Cancel (left) | Confirm Approval (right, primary)

## Interaction Flow
1. Entry point (Work Order edit/detail screen):
   1. Show “Record Approval” button only if Work Order status allows approval entry and user has permission/capability.
   2. On click, navigate to Record Approval screen with required `workOrderId` parameter.
2. Load screen data:
   1. Fetch Work Order details including line items and current approval statuses/editability.
   2. Fetch approval requirements/config if not embedded in Work Order payload.
   3. Render only approval-eligible line items with decision controls; show non-eligible items as read-only (or omit per product convention).
3. Make decisions + preview:
   1. For each eligible line item, user selects Approved or Declined (decision required).
   2. UI updates “Approved Total (Preview)” based on selected Approved items (display-only).
4. Capture approval method/proof (backend-driven):
   1. Render method-specific inputs per backend requirements.
   2. If backend returns an unsupported method identifier:
      1. Show blocking error banner: “This approval method is not supported in this POS UI.”
      2. Disable “Confirm Approval.”
5. Confirm (atomic, idempotent submit):
   1. Validate: all eligible line items have a decision; required proof fields present per backend config.
   2. Submit a single request containing all line item decisions + method/proof.
   3. Include `Idempotency-Key` header; reuse the same key on retry of the same attempt.
   4. On success:
      1. Display updated Work Order status and backend-returned approved total.
      2. Update line item approval statuses from response.
      3. Show read-only audit metadata (method, timestamp, proof reference if any).
      4. Navigate back to Work Order detail/edit showing backend-returned status/amounts.
6. Error handling (standard envelope):
   1. Invalid state / approval window expired: show banner; disable confirm if no longer actionable; offer “Back” or “Reload.”
   2. Validation failures (missing decisions/proof): highlight fields/rows; keep user on screen.
   3. Auth/forbidden: show banner and prevent submission.
   4. Conflict (409 stale version/concurrent update):
      1. Show banner indicating the Work Order changed.
      2. Provide “Reload” action to refetch Work Order + requirements; discard local edits.
7. Cancel:
   1. Discard changes and return to Work Order edit/detail without submitting.

## Notes
- Submission must be a single atomic confirmation action; no per-line submits.
- Idempotency is required: UI generates and sends `Idempotency-Key` for submit; reuse on retry for the same attempt.
- Concurrency-safe behavior: handle 409 by prompting reload and refetching latest Work Order data before allowing resubmission.
- Approval method/proof UI is fully driven by backend-resolved configuration (customer > location > default precedence handled server-side).
- After successful submission, approval metadata becomes read-only (method, timestamp, proof reference); do not store or display raw signature payload.
- Line item amounts may be decimal; support services and parts; do not assume integer quantities/prices.
- “Record Approval” button visibility/enabled depends on Work Order status and user capability; backend remains source of truth for enforcement.
- Ensure deterministic error messaging mapped from the backend standard error envelope (invalid state, validation, window expired, forbidden, conflict).
