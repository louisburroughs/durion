# [FRONTEND] [STORY] Payment: Initiate Card Authorization and Capture

## Purpose
Enable a cashier to start and complete a card payment from an invoice checkout/detail screen, supporting default SALE_CAPTURE and an optional AUTH_ONLY → CAPTURE flow within the same session. Provide clear payment progress, safe handling of errors/unknown outcomes via inquiry, and show an audit-friendly record of payment attempts without storing sensitive card data. After success, trigger receipt generation and provide an immediate entrypoint to view/print the receipt while refreshing invoice balances/status.

## Components
- Invoice header/summary (invoice number, customer, balance due, status)
- “Pay by Card” action button (permission-gated; disabled when not payable/no balance)
- Payments panel (read-only list of latest payment attempts for this invoice)
  - Attempt row: timestamp, flow type, amount, status, masked card metadata (brand/last4 if provided), gateway/transaction reference, terminal/actor metadata (if provided)
  - Attempt detail drawer/modal (optional): correlationId, idempotency key reference, backend messages (sanitized)
- Pay by Card modal/screen
  - Flow type selector (default SALE_CAPTURE; optional “Authorize only” when allowed)
  - Amount field (default full balance due; read-only or editable per policy)
  - Currency display (from invoice)
  - Optional step-up/elevation token input (only when required)
  - Primary submit button (“Pay” / “Authorize”)
  - Secondary actions: “Cancel/Return to invoice”
- Processing/progress state UI (spinner + status text)
- Capture section (shown when an auth intent is authorized and capture is allowed)
  - “Capture” button (new idempotency key per capture)
- Status banners/toasts (success, decline, validation, forbidden, conflict, unavailable, unknown outcome)
- Receipt actions (post-success)
  - “View receipt” / “Print receipt” entrypoint (opens receipt view)
- Inquiry/resume UI (for unknown outcome)
  - “Confirming payment status…” message
  - “Continue checking” / auto-retry indicator (no unsafe retry)

## Layout
- Top: Invoice header/summary + status badges
- Main: Invoice line items/checkout content; right/main sidebar section includes Payments panel
- Primary action area (near totals): [Pay by Card] button
- Modal overlay for payment initiation; inline banner area for errors/status
- Simple sketch: Top: Invoice Summary | Main: Details + Totals [Pay by Card] | Side/Below: Payments Panel (history)

## Interaction Flow
1. Load invoice checkout/detail screen
   1. Fetch invoice detail/summary (includes balance due, currency, payable status).
   2. Optionally fetch existing payments/intents for the invoice and render in Payments panel.
   3. If invoice is paid/void/unpayable or balance due is zero: disable/hide “Pay by Card” and show “No payment due”.
2. Start SALE_CAPTURE payment (primary flow)
   1. Cashier clicks “Pay by Card”.
   2. Modal opens with flow type defaulted to SALE_CAPTURE; amount defaults to full balance due.
   3. If partial payments not allowed: amount is read-only and must equal balance due; if allowed: validate amount > 0 and ≤ balance due.
   4. On submit: generate client idempotency key (UUID) and call payment initiation.
   5. Enter Processing state: disable primary actions to prevent duplicates; allow “Return to invoice” with warning that status is still being confirmed.
   6. On success (captured): show success summary (amount, status, masked brand/last4 if provided, transaction reference), then fetch receipt and show “View/Print receipt”; refresh invoice and Payments panel.
3. Start AUTH_ONLY (optional branch)
   1. “Authorize only” option is visible only when backend indicates allowed and user has permission.
   2. Submit authorization with idempotency key; show Processing state.
   3. On authorized outcome: show “Authorized” status and display “Capture” button only if backend indicates capture is allowed.
4. Capture an authorized intent (same-session)
   1. Cashier clicks “Capture”; generate a new idempotency key for capture command.
   2. Show Processing state; disable duplicate capture attempts.
   3. On captured: show success + receipt entrypoint; refresh invoice and Payments panel.
5. Unknown outcome / timeout handling (safe defaults)
   1. If initiation or capture returns timeout/unknown: show “Confirming payment status…” and start inquiry loop using intent id and/or idempotency key.
   2. Continue inquiry until definitive status or attempts exhausted; keep retry disabled until resolved.
   3. If still unknown after attempts: show “Unknown outcome; refresh or contact support” with correlationId and keep latest attempt visible in Payments panel.
6. Error handling and recovery
   1. 422 validation: show actionable message (invoice not payable, missing fields, amount invalid) using backend field/blocker codes when available.
   2. 409 conflict: show “Invoice already paid / payment already processed / state changed”; refresh invoice and payments.
   3. 403 forbidden: show “You don’t have permission to take card payments” and return to invoice context.
   4. 503 unavailable: show “Payment service unavailable; try again” and preserve form state.
   5. Gateway decline: show “Card declined” with non-sensitive reason if provided; do not expose raw gateway text.
   6. Retry is enabled only when backend explicitly indicates no capture occurred; otherwise route to inquiry/refresh.

## Notes
- Sensitive data: never store/log PAN/CVV/track data; no sensitive values in component state persistence, localStorage/sessionStorage, URLs, or analytics. Display only masked metadata (brand/last4) and gateway/token/transaction references if provided.
- Idempotency: client must generate a new UUID idempotency key per initiation attempt and per capture command; do not include PII.
- Status rendering: backend payment status is authoritative; unknown/unrecognized statuses render as “Unknown status; refresh or contact support”.
- Audit-friendly metadata: display who/when/terminal and correlationId when exposed by backend; keep messages user-safe.
- UX safety: while processing/inquiry, disable primary submit actions to prevent duplicate charges; allow navigation back with a warning and ensure the UI can resume inquiry on return.
- Receipt: after successful capture, retrieve receipt via preferred receipt endpoint and provide immediate “View/Print” entrypoint; delivery actions (print/email) are optional/out of scope unless already supported.
- TODO (backend contract): initiation/capture/inquiry endpoints and exact status enums are not defined here; UI must be built to tolerate unknown enums and rely on capability flags (e.g., auth-only allowed, capture allowed, partial payment policy, step-up required codes).
