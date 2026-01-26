# [FRONTEND] [STORY] Workexec: Display Invoice and Request Finalization (Controlled)
## Purpose
Provide a read-only Invoice Detail view within Work Execution that can be opened from a Work Order or via direct invoice deep link. Enable permitted users to create a draft invoice when none exists and to request invoice issuance (finalization) when policy allows. Surface issuance gating (blockers, elevation requirements) and traceability in a controlled, permission-gated way, with idempotent handling and backend-as-source-of-truth refresh after commands.

## Components
- Page header: Invoice identifier (if issued), status badge, customer/work order context, dates (issued/created), invoice number (if issued)
- Totals summary panel: subtotal, taxes, fees, discounts (if any), total, amount due/paid (as provided)
- Line items list (read-only): description, qty, unit price, line total
- Taxes/fees breakdown lists (read-only): grouped rows with labels and amounts
- Issuance gating panel:
  - Issuance summary (as provided by backend)
  - Blockers list (code/severity/message as provided)
  - Posting error summary (sanitized) + correlationId (when present; detail gated by role)
- Traceability snapshot panel (permission-gated): immutable snapshot/issuance metadata and audit-relevant fields
- Allowed actions panel (backend-driven): list of available actions and requirements (e.g., issuance allowed, elevation required)
- Primary actions:
  - “Create Draft Invoice” button (empty state only, permission-gated)
  - “Issue Invoice” button (permission + status + allowed action + no blockers)
  - “Manager Approval / Elevate” button (only when elevation required and user has permission)
- Confirmation modal: “Issue Invoice” confirm (and optional “Manager Approval” prompt when required)
- Inline alerts/toasts: success, conflict (409), validation/missing data (422), blocked issuance messaging
- Loading/refresh states: skeleton/spinner for initial load and post-command reload

## Layout
- Top: Page header with status badge + key identifiers; right-aligned primary action area (Issue / Elevate) when applicable
- Main (stacked panels): Totals summary → Line items → Taxes/fees breakdown → Issuance gating → Traceability (if permitted) → Allowed actions
- Empty state (when no invoice): centered message + optional “Create Draft Invoice” CTA; secondary guidance text when not permitted
- Inline ASCII hint: [Header + Actions] / [Totals] / [Line Items] / [Taxes & Fees] / [Issuance Gating] / [Traceability] / [Allowed Actions]

## Interaction Flow
1. Load Invoice Detail (entry from Work Order “Invoice/View Invoice”):
   1) UI requests invoice by workOrderId.
   2) If 200, render invoice detail panels (read-only) and compute button visibility/enabled from permissions + backend allowed actions.
   3) If 404, show empty state: “No invoice exists for this work order.”
      - If user permitted, show “Create Draft Invoice.”
      - If not permitted, show guidance: “Contact a manager or billing admin to create an invoice.”
2. Load Invoice Detail (deep link by invoiceId):
   1) UI requests invoice by invoiceId.
   2) On 200, render invoice detail as above (no empty state path).
3. Create Draft Invoice (user-initiated, idempotent):
   1) User clicks “Create Draft Invoice” (only visible when by-work-order returned 404 and user has permission).
   2) UI calls create-draft endpoint with workOrderId.
   3) On 200, navigate/render returned invoice detail and re-load invoice detail from backend (do not mutate locally).
   4) On 409, show deterministic conflict message (e.g., not invoice-ready / already issued/paid/void) and keep empty state.
   5) On 422, display missing billing data fields (as provided) and keep empty state.
4. View Draft Invoice with issuance blockers (blocked path):
   1) If invoice detail includes one or more blockers, render blockers list (code/severity/text).
   2) Disable “Issue Invoice” and show “Issuance blocked” messaging; keep blockers visible until resolved externally.
5. Issue Invoice (finalization) without elevation:
   1) Preconditions: user has permission, invoice status is Draft, backend indicates issuance allowed (no blockers / allowed action present).
   2) User clicks “Issue Invoice” → confirm modal.
   3) UI calls issuance command without elevation token.
   4) Handle idempotency/response:
      - 200: re-load invoice detail; show issued metadata (issuedAt, issuedBy, invoice number if provided).
      - 409/422: show backend-provided conflict/validation messaging; re-load invoice detail to reflect current state.
6. Issue Invoice with elevation (step-up approval):
   1) If backend indicates elevation required, show “Manager Approval / Elevate” (permission-gated).
   2) User initiates elevation; obtain elevation token (Shop Manager/Supervisor flow).
   3) Retry “Issue Invoice” including elevation token; then re-load invoice detail.
7. Immutable/terminal states:
   1) If status is Issued/Paid/Void (or equivalent), hide/disable issuance controls; show issuance metadata (issuedAt/issuedBy and other provided fields).
   2) If posting error exists, show sanitized error summary + correlationId; do not allow issuance unless backend explicitly allows via allowed actions.

## Notes
- Backend is source of record: after draft creation or issuance command, always re-fetch invoice detail rather than updating UI state locally (SD-UI-REFRESH-AFTER-COMMAND-01).
- Security: allowed actions must be derived from backend when present; UI may derive hints from status/permissions for UX only, never for enforcement.
- Issuance gating:
  - Blockers must be displayed exactly as provided (code/severity/message) and disable issuance when present.
  - Elevation is required only when policy/blockers indicate; elevation token must be used only when required.
- Idempotency and error handling:
  - Create draft: handle 200/409/422 with specified UI outcomes; keep empty state on non-200.
  - Issue invoice: handle 200/409/422; show sanitized posting error summary when present; role-targeted detail gating applies.
- Traceability snapshot is permission-gated and must be immutable/auditable; issuance/override actions must be auditable via displayed metadata.
- Work Execution prerequisite: draft creation should only be offered when Work Order completion prerequisites are satisfied as reflected by backend responses (e.g., 409/422 messaging).
