# [FRONTEND] [STORY] Receiving: Direct-to-Workorder Receiving (Cross-dock) from Distributor
## Purpose
Enable an Inventory Manager to cross-dock an inbound receiving line directly to a WorkExec workorder line by performing an atomic “receive + issue” transaction. The screen guides the user through selecting a workorder/line, entering a received quantity, reviewing a confirmation summary, and submitting while handling validation, conflicts, and permission-based overrides. The outcome must provide a clear result summary (including references and correlation id) and support returning to refreshed shipment detail.

## Components
- Page header: “Cross-dock to Workorder” + breadcrumb/back to shipment detail
- Context panel (read-only): supplier shipment reference, shipment/receiving line identifiers, productSku, remaining qty (if provided), UoM
- Workorder search:
  - Search input (workorder number/id; optional customer name if supported)
  - Search button
  - Results list (paginated; no assumed totals) with select action
- Workorder details:
  - Selected workorder summary (number/id, status, customer display if available)
  - Workorder line list with select action (line id, productSku, remaining demand/qty if provided, status)
- Quantity entry:
  - Decimal quantity input (required)
  - UoM display (read-only)
  - Inline validation messages
- Mismatch/override area (conditional):
  - Product mismatch warning (when detectable)
  - “Override mismatch” toggle/checkbox (only if backend indicates permitted)
  - Override reason code input (required when override enabled)
- Review step (panel or modal):
  - Read-only summary: productSku, quantity, supplier shipment ref, workorder id/number, workorder line id, issue mode, override reason (if any)
  - Confirm button, Cancel/Edit button
- Submit/progress:
  - In-flight spinner/progress indicator
  - Disabled state for all inputs/actions to prevent double-submit
- Result summary (Completed state):
  - Success banner
  - References: receipt/issue identifiers (if provided), workorder/workorder line, supplier shipment ref
  - Timestamp + confirmed by (user vs SYSTEM) if provided
  - Correlation id display
  - “Return to shipment” button
  - Optional “Reload shipment” button
- Error handling UI:
  - Error banner with correlation id (if available)
  - Field-level errors (quantity, workorder, workorder line, override reason)
  - Forbidden state (403) with “Back to shipment list”
  - Timeout message (after ~8s) with Retry + duplicate-risk warning

## Layout
- Top: breadcrumb/back + page title; right side shows correlation id when available
- Main (stacked):
  - Context panel (shipment + receiving line details)
  - Workorder search + results list
  - Selected workorder summary + workorder line picker list
  - Quantity entry + UoM + inline validation
  - Conditional mismatch/override section
  - Footer actions: Review/Confirm (or Continue), Submit (when eligible), Cancel/Return
- State swap: Draft/Review/Submitting/Completed/Failed replace the main action area and messaging

## Interaction Flow
1. Load context (Draft):
   1) User opens “Cross-dock to Workorder” from a receiving line.
   2) UI loads shipment header + receiving line details (productSku, remaining qty if present, supplierShipmentRef, UoM).
   3) UI shows empty workorder selection and disabled Submit/Confirm.
2. Search and select workorder:
   1) User enters workorder number/id (optionally customer name if supported) and runs search.
   2) UI displays paginated results; user selects a workorder.
   3) UI loads and displays workorder lines for the selected workorder.
3. Select workorder line:
   1) User selects a workorder line.
   2) UI displays line details (line id, productSku, remaining demand/qty if provided, status).
   3) If both SKUs are present and mismatch is detected, show a warning; keep backend as source of truth.
4. Enter received quantity:
   1) User enters decimal quantity; UI validates required/decimal/positive and (if remaining qty provided) quantity ≤ remaining.
   2) Submit/Confirm remains disabled until workorder + line selected and quantity valid with no blocking errors.
5. Review & confirm (manual confirmation required path):
   1) User clicks “Review” to open the confirmation summary (always available).
   2) Summary is read-only; user can Cancel/Edit to return to Draft.
   3) Confirm is required when override is used; otherwise still available/expected unless backend explicitly indicates auto-issue eligibility.
6. Submit (atomic receive+issue):
   1) User confirms; UI POSTs cross-dock command (workOrderId, workOrderLineId, quantityReceived, override flags/reason if used).
   2) While in-flight: disable all inputs/actions; show progress; prevent double-submit.
   3) On success: show Completed result summary with references (receipt/issue ids if provided), issue mode (manual vs auto), confirmed by, timestamp, and correlation id.
   4) User clicks “Return to shipment” and UI navigates back; if response lacks updated model, UI reloads shipment detail.
7. Edge case: Product mismatch override (permission + reason):
   1) If mismatch exists and backend indicates override permitted, show “Override mismatch”.
   2) When enabled, require reason code before enabling Confirm; force Review step (no auto-issue).
   3) Submit includes override reason; Completed view displays override reason if returned.
8. Error/exception flows:
   1) 400 validation: show banner + field errors; keep user in Draft/Review as appropriate; show correlation id if available.
   2) 401 unauthenticated: trigger session refresh/redirect per proxy behavior; return user to flow after auth if possible.
   3) 403 forbidden: show forbidden state without additional data; provide “Back to shipment list”.
   4) 409 conflict: show concurrency message; offer “Reload shipment” to refresh context and return to Draft.
   5) Timeout/network/server: after ~8s show timeout message with Retry; include warning that retry may duplicate if idempotency is unknown; show correlation id if available.

## Notes
- Backend contracts are incomplete/blocked: exact Moqui proxy endpoints/services needed for (1) load shipment + receiving lines, (2) load receiving line context by line id, (3) search workorders + load workorder lines, (4) submit atomic cross-dock receive+issue, (5) reload shipment detail after submit.
- Validation rules:
  - Quantity required, decimal, and must satisfy backend constraints; if remaining qty provided, enforce quantity ≤ remaining.
  - Workorder must be in an issuable state; if backend returns non-issuable status, block submit and show message.
  - Product match: UI may warn on mismatch only when both SKUs present; backend must validate on submit.
- Enable/disable rules:
  - Submit/Confirm disabled until workorder + line selected, quantity valid, and no blocking errors.
  - During submission, disable all inputs/actions; prevent double-submit.
  - Override controls hidden unless mismatch exists AND backend indicates override permitted; override requires reason code and forces Review.
- State model: Draft → Review → Submitting → Completed; Draft → Submitting → Completed only if backend explicitly indicates auto-issue eligibility; Failed returns to Draft with errors and retry options.
- Error schema must be deterministic plain JSON with field errors; always surface correlation id when available.
- Sensitive data policy: do not log quantities or payloads to client telemetry/console; still display quantity to the user in UI summaries.
- User-visible audit data in result summary (and optionally after reload on shipment line): supplier shipment reference, workorder/workorder line reference, quantity issued, issue mode (manual vs auto), confirmed by (user vs SYSTEM), timestamp (if provided), and optional notification status if backend returns it.
