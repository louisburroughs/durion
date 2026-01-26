# [FRONTEND] [STORY] Completion: Validate Completion Preconditions
## Purpose
Enable users to complete a Work Order only when backend-defined completion preconditions are satisfied. The UI must validate eligibility, display a structured checklist of failures, and block the completion transition until all checks pass. The experience must clearly surface pending approval-gated change requests and unreconciled parts/labor issues, while remaining concurrency-safe and double-submit-safe.

## Components
- Work Order detail/edit screen header with Work Order identifier and status
- Primary action button: Complete Work Order (enabled/disabled/hidden per rules)
- Completion checklist panel/modal (embedded widget/section; reusable)
- Checklist header: “Completion Preconditions” + “Last evaluated” timestamp (optional)
- Checklist list grouped by severity/category (BLOCKER/WARN/INFO) with rows:
  - Check message (user-readable)
  - Code badge (opaque string)
  - Category tag (e.g., APPROVAL, PARTS, LABOR, STATE, PERMISSION, OTHER)
  - Related item label (optional) and optional deep link target (if provided)
- Inline callouts for two key failure categories:
  - Pending approval-gated change requests
  - Unreconciled parts usage / labor entries
- Buttons within checklist:
  - Validate / Re-check
  - Proceed to Complete (primary; gated)
  - Cancel / Close
- Loading indicators for validation and completion in-flight states
- Error banner/toast area using standard error envelope messaging (400/403/404/409/5xx)
- Optional: Board/list row action “Complete” (opens WorkOrderEdit with checklist open)

## Layout
- Top: Work Order header (ID, status, key metadata) + actions area (Complete Work Order)
- Main: Existing Work Order edit/detail content
- Overlay/Right panel (preferred reusable widget): Completion checklist dialog/panel
  - Header: title + last evaluated
  - Body: checklist groups + key category callouts
  - Footer: Cancel | Re-check | Proceed to Complete (right-aligned)

## Interaction Flow
1. User opens Work Order edit/detail screen.
2. UI determines visibility/availability of “Complete Work Order”:
   1) Hide/disable if Work Order already completed/closed (backend-defined terminal state).
   2) If capability/permission signal exists, hide/disable when not permitted; otherwise allow click and handle 403 on action.
3. User clicks “Complete Work Order”.
4. UI opens the completion checklist panel/modal and immediately invokes validation (Moqui service preferred; REST alternative allowed).
5. While validation is in-flight:
   1) Show loading state in checklist.
   2) Disable “Proceed to Complete” and disable re-check to prevent double-submit.
6. Validation returns 200 with checklist response model:
   1) Display “Last evaluated” if timestamp provided.
   2) Render each failed check row with message, code, category, and related item label (if present).
   3) If no failures, show “All checks passed” state and enable “Proceed to Complete”.
   4) If failures exist, keep “Proceed to Complete” disabled; highlight the two key categories when present:
      - Pending approval-gated change requests: show count/list and related labels (e.g., CR-123).
      - Unreconciled parts usage / labor entries: show affected items/labels (e.g., Line 4, Labor #12).
7. User optionally clicks “Re-check” after resolving issues elsewhere; UI re-runs validation and refreshes checklist.
8. When eligible, user clicks “Proceed to Complete”:
   1) Send completion transition request with idempotency key header.
   2) Disable all actionable buttons and show in-flight state to prevent double submit.
9. Completion success (200):
   1) Update Work Order summary/status in the underlying screen.
   2) Close checklist panel/modal (or show success state with “Done”).
10. Error handling (validation or completion):
   1) 403 FORBIDDEN: show permission error; keep completion blocked; allow close.
   2) 404 NOT_FOUND: show “Work Order not found/invalid”; disable actions.
   3) 409 CONFLICT (state changed/stale/not eligible): show conflict message; prompt re-check; re-run validation on user action.
   4) 400 VALIDATION_FAILED on completion: display returned checklist (if provided) and keep blocked.
   5) 5xx: show generic failure; allow retry validation/completion as appropriate.
11. Optional board/list entry point:
   1) User selects row action “Complete” (only if permitted).
   2) Navigate to WorkOrderEdit with checklist panel/modal opened and validation auto-run (no direct completion from board unless explicitly enabled).

## Notes
- Must respect Work Order state machine rules; completion must not bypass validation (backend authoritative).
- Prefer embedding a reusable checklist widget/section in the existing Work Order edit/detail screen; avoid new top-level routes unless necessary.
- “Proceed to Complete” is disabled until validation returns and while any request is in-flight.
- “Complete Work Order” entry action hidden/disabled if already in terminal completed state; permission gating via capability signal if available, otherwise handle 403 gracefully.
- Checklist rendering must treat check codes as opaque strings; unknown codes/categories must still render generically.
- Severity defaults to BLOCKER when omitted; only BLOCKER failures gate completion.
- Concurrency-safe UX: handle 409 by prompting re-validation; do not assume client-side state is current.
- Double-submit-safe: include idempotency key header on completion submit; disable buttons during in-flight.
- Use standard error envelope (DECISION-INVENTORY-011) for consistent messaging across 400/403/404/409/5xx.
- TODO (backend contract alignment): confirm exact terminal status values, allowed-from states, and whether deep-link targets for related items will be provided in checklist items.
