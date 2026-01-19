# STORY_VALIDATION_CHECKLIST.md  
**Domain:** Order (POS)

---

## Scope/Ownership
- [ ] Verify the story is owned by **domain:order** (POS Order) and not misrouted under `domain:payment` / “Point of Sale” labels in the frontend repo.
- [ ] Confirm cancellation orchestration remains backend-owned; frontend only **initiates** cancellation and **renders** outcomes.
- [ ] Ensure no refund/void execution logic is implemented in the frontend (display + messaging only).
- [ ] Verify Work Execution and Payment/Billing are treated as authoritative for their statuses; UI does not infer or override them.
- [ ] Confirm the UI scope includes: cancel action from Order Detail, cancellation dialog, status rendering, and cancellation summary/audit display.
- [ ] Confirm out-of-scope items are not implemented: operator/admin failure-resolution dashboard, accounting/GL flows, refund processing UI.

---

## Data Model & Validation
- [ ] Verify `orderId` is present in the Order Detail context and is passed unchanged to the cancel service.
- [ ] Verify client-side validation blocks submit when `reason` is missing.
- [ ] Verify client-side validation enforces `comments` length ≤ 2000 characters (and server-side validation still rejects >2000).
- [ ] Verify `reason` values are validated against the allowed set (enum or backend-provided list) and unknown values are rejected safely.
- [ ] Verify the UI handles missing/empty cancellation metadata by rendering a safe empty state (e.g., “No cancellation activity recorded”).
- [ ] Verify the UI can render cancellation summary fields when present:
  - [ ] `cancellationId`
  - [ ] `cancelledBy`
  - [ ] `cancelledAt`
  - [ ] `reason`
  - [ ] `comments` (if returned/allowed)
  - [ ] `paymentVoidStatus`
  - [ ] `workRollbackStatus`
  - [ ] correlation id(s) **only if explicitly exposed**
- [ ] Verify unknown/unmapped `order.status` values are displayed safely (raw value + non-blocking warning) without breaking the screen.
- [ ] Verify terminal/non-cancellable order states disable/hide cancel action in UI (at minimum: `COMPLETED`, `CLOSED`, `CANCELLED`; plus any backend-defined equivalents).

---

## API Contract
- [ ] Confirm the Moqui frontend uses the correct service/transition for **loading order detail** including cancellation summary (if available).
- [ ] Confirm the Moqui frontend uses the correct service/transition for **submitting cancellation** with inputs: `orderId`, `reason`, `comments`.
- [ ] Verify cancel submit response mapping renders these fields when returned:
  - [ ] `orderId`
  - [ ] `status`
  - [ ] `message`
  - [ ] `cancellationId`
  - [ ] `paymentVoidStatus`
  - [ ] `workRollbackStatus`
- [ ] Verify HTTP/service error mapping is deterministic and user-safe:
  - [ ] `400` shows a validation/work-block message (only if user-safe; otherwise generic + log correlation id)
  - [ ] `403` shows “You do not have permission to cancel orders.”
  - [ ] `409` shows “Cancellation already in progress” and triggers a refresh of order detail
  - [ ] `500` shows “Cancellation failed; manual intervention required” (avoid leaking internals)
- [ ] Verify the UI can handle both patterns for errors:
  - [ ] structured error schema (e.g., `errorCode`, `fieldErrors`)
  - [ ] message-only errors (fallback mapping)
- [ ] Verify idempotent success responses are handled (e.g., “already cancelled” returns `200` and UI renders current state).
- [ ] Verify the UI refreshes order detail after submit (or updates from response + then refreshes) to avoid stale state.

---

## Events & Idempotency
- [ ] Verify the UI prevents duplicate submissions via UX controls (disable submit + spinner while request is in-flight).
- [ ] Verify repeated clicks/double-submit cannot create multiple cancellation attempts from the UI (client-side guard + backend idempotency reliance).
- [ ] Verify `409 Conflict` is treated as non-fatal: UI refreshes and renders in-flight state (`CANCELLING`/equivalent).
- [ ] Verify the UI renders an “in progress” state when backend exposes it (`CANCELLING` or equivalent), and disables/hides cancel action accordingly.
- [ ] Verify the UI does not automatically retry cancellation on failures; it only displays the outcome and guidance.

---

## Security
- [ ] Verify server-side permission enforcement exists for `ORDER_CANCEL` (UI gating is not sufficient).
- [ ] Verify the UI gates visibility/enabled state of “Cancel Order” based on the repo’s standard permission exposure mechanism.
- [ ] Verify the UI does not expose sensitive payment details (PAN, tokens, processor responses); only show high-level statuses like `REFUND_REQUIRED`.
- [ ] Verify error messages displayed to users do not leak internal subsystem details unless explicitly approved.
- [ ] Verify correlation IDs (if shown) are copyable but do not include secrets; do not log session tokens or PII in console/app logs.
- [ ] Verify direct navigation / crafted requests cannot bypass UI gating (expect `403` and safe UI handling).

---

## Observability
- [ ] Verify cancellation attempts are logged client-side only via approved telemetry (if present), without sensitive data.
- [ ] Verify logs/telemetry (if implemented) include `orderId` and `cancellationId` (when available) and correlation id (when available/allowed).
- [ ] Verify UI surfaces a support-friendly identifier (correlation id or cancellation id) when available for troubleshooting.
- [ ] Verify unknown status values generate a non-fatal warning signal (console/app logger) to aid diagnosis without breaking UX.

---

## Performance & Failure Modes
- [ ] Verify UI shows immediate feedback (<250ms perceived): modal submit disables controls and shows loading state.
- [ ] Verify UI remains responsive during network delays (no blocking UI thread).
- [ ] Verify network timeout/failure is handled gracefully with a generic error and a retry suggestion (user-initiated retry only).
- [ ] Verify the UI handles partial/missing response fields without crashing (e.g., missing `paymentVoidStatus`).
- [ ] Verify the UI handles refresh failures after submit (e.g., cancel succeeded but reload failed) by showing the submit response and offering manual refresh.
- [ ] Verify tablet/POS viewport responsiveness for dialog and cancellation summary panel.

---

## Testing
- [ ] Test UI gating:
  - [ ] user without `ORDER_CANCEL` cannot see/execute cancel action
  - [ ] user with permission can see cancel action only in eligible states
- [ ] Test form validation:
  - [ ] missing `reason` blocks submit with field error
  - [ ] `comments` > 2000 blocks submit with field error
- [ ] Test success rendering:
  - [ ] `status=CANCELLED` renders success banner + cancellation summary
  - [ ] `status=CANCELLED_REQUIRES_REFUND` renders warning banner + “manual refund required” guidance
- [ ] Test conflict/in-flight:
  - [ ] `409` shows conflict message and refreshes to show `CANCELLING`/equivalent
- [ ] Test work-block validation:
  - [ ] `400` shows user-safe message and order remains not cancelled after refresh
- [ ] Test downstream failure:
  - [ ] `500` shows generic “manual intervention required” and renders `CANCELLATION_FAILED` after refresh (if returned/loaded)
- [ ] Test idempotency:
  - [ ] cancelling an already-cancelled order returns `200` and UI renders terminal state without errors
- [ ] Test accessibility:
  - [ ] modal focus trap, keyboard navigation, labeled inputs, error association to fields
- [ ] Test i18n/timezone:
  - [ ] `cancelledAt` displays in configured locale/timezone when present

---

## Documentation
- [ ] Document the canonical domain label and routing for this story in the frontend repo (`domain:order` vs other labels).
- [ ] Document the Moqui screen route/screen name for Order Detail and how `orderId` is passed.
- [ ] Document the Moqui service/transition names for:
  - [ ] loading order detail (with cancellation summary)
  - [ ] submitting cancellation
  - [ ] loading latest cancellation record (if separate)
- [ ] Document the definitive frontend-rendered status enums and their UI mappings (including in-flight and failure variants).
- [ ] Document the allowed cancellation reasons source (hardcoded enum vs backend/config-driven) and update process.
- [ ] Document user-facing message rules (what is safe to display verbatim vs generic).
- [ ] Document whether correlation IDs are user-visible and where they appear (UI vs logs/admin only).

---

## Open Questions to Resolve
- [ ] What is the canonical frontend domain label for this work: `domain:order` vs `domain:positivity` vs `domain:payment` / “Point of Sale”?
- [ ] What are the exact Moqui service names/endpoints and parameter mappings for:
  - [ ] loading order detail (including cancellation summary)
  - [ ] submitting cancellation
  - [ ] retrieving latest cancellation record (if separate)
- [ ] What is the definitive order status enum set the frontend will receive (e.g., `CANCELLING` vs `CANCEL_REQUESTED`/`CANCEL_PENDING`, `CANCELLATION_FAILED` vs `CANCEL_FAILED_*`)?
- [ ] How does the frontend determine `ORDER_CANCEL` capability in this repo (permissions API, session context, Moqui screen conditions)?
- [ ] Should the frontend call any advisory pre-check endpoint(s) before submit, or rely solely on submit response for work/payment blocking?
- [ ] For `CANCELLATION_FAILED`, should the UI offer a “Retry cancellation” action (admin-only) or only display status and guidance?
- [ ] Is `reason` a fixed enum list (hardcoded) or should it be loaded from backend/config?
- [ ] Should correlation IDs and downstream subsystem details be displayed in the UI, or restricted to logs/admin views?
- [ ] What is the exact Moqui screen route/screen name for Order Detail in this repo (for navigation and transitions)?

---

# End of Checklist
