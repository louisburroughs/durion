# [FRONTEND] [STORY] Invoicing: Generate Invoice Draft from Completed Workorder
## Purpose
Enable an AR Clerk to create a draft invoice directly from a Work Order that is Completed and invoiceReady. The UI must call the Billing draft creation command (idempotent) and then navigate to the resulting Invoice Detail view using backend-provided link metadata. The Invoice Detail page must display invoice header, line items, totals, and traceability snapshot, and handle backend errors deterministically with correlationId surfaced for support.

## Components
- Work Order Detail header (status, invoiceReady indicator)
- Primary action button: “Create Invoice”
- Inline eligibility messaging (e.g., disabled state reason if not Completed/invoiceReady)
- Create Invoice progress indicator (button loading state and/or page-level spinner)
- Error banner/panel with:
  - Human-readable error message
  - correlationId display (copyable)
  - Retry action (for 503)
- Invoice Detail header section (read-only fields):
  - invoiceId (UUIDv7)
  - status (InvoiceStatus)
  - workOrderId
  - customerAccountId
  - billingAccountId
- Totals summary panel (read-only):
  - subtotal, discounts, tax, fees, total, amountDue (as provided by backend)
- Line items list/table (read-only):
  - description, quantity, unitPrice, lineTotal
  - optional fields: notes, taxable flag, discount amount
- Anomaly panel: “No invoice items found” (when line items empty)
- Traceability Snapshot panel (read-only):
  - snapshotId, workOrderId, snapshotVersion/label (string)
  - optional IDs (e.g., serviceLocationId, vehicleId, relatedWorkItemIds)
  - artifactRefs indicator (view-only; no direct download in this story)
- Issuance blockers/policy panel (shown when status = DRAFT)
- Deep-link navigation handler (uses backend link metadata; no hardcoded routes)

## Layout
- Top: Page header (context-specific: Work Order Detail or Invoice Detail) + breadcrumbs/back link
- Work Order Detail main: Summary (left/main) + Actions (right) with “Create Invoice” primary button
- Invoice Detail main: Header fields (top) → Totals summary (right or top-right) → Line items table (center) → Traceability + Blockers panels (bottom)
- Inline ASCII hint: Top Header | Main: [Invoice Header + Totals] -> [Line Items] -> [Traceability/Blockers] | Footer: support info (correlationId on error)

## Interaction Flow
1. (Eligibility) User opens Work Order Detail.
2. UI evaluates Work Order state:
   1) If Completed + invoiceReady: enable “Create Invoice”.
   2) Otherwise: disable/hide action and show brief reason (not invoice-ready / not completed).
3. User clicks “Create Invoice”.
4. UI sends “Create Draft Invoice from Work Order” request (idempotent) with workOrderId.
5. While awaiting response: show loading state; prevent duplicate clicks.
6. On success:
   1) Accept either 201 Created or 200 OK (newly created or existing DRAFT).
   2) Use backend link metadata to navigate to Invoice Detail for returned invoiceId (no hardcoded routes).
7. Invoice Detail renders from returned snapshot (preferred) or fetches invoice detail if only invoiceId returned:
   1) Display invoiceId, status, workOrderId, customerAccountId, billingAccountId.
   2) Display totals exactly as provided by backend (authoritative).
   3) Display line items list; if empty, show “No invoice items found” anomaly panel.
   4) Display Traceability Snapshot fields (read-only).
   5) If status is DRAFT, display issuance blockers/policy panel (read-only).
8. Error handling on Create Invoice (deterministic):
   1) 404: show “Not found or you don’t have access.” + correlationId.
   2) 403: show access denied message + correlationId.
   3) 409: show conflict message (e.g., invoice already exists / state changed) + correlationId; offer “Go to Invoice” if invoice link/id provided, otherwise offer refresh Work Order.
   4) 422: show validation message (e.g., customer/account data incomplete) + correlationId; suggest reviewing customer/account details (no inline edit required in this story).
   5) 503: show service unavailable message + correlationId; provide Retry button; preserve current page state.
9. Error handling on Invoice Detail load (if separate fetch occurs): show error panel with correlationId and retry.

## Notes
- Navigation must rely on backend link metadata for deep-linking; do not hardcode invoice routes.
- Create Draft Invoice call is idempotent: UI must treat 200 OK as success (existing DRAFT) and still navigate to Invoice Detail.
- Invoice fields and totals are read-only; Billing is the source of record.
- Line items array may be empty; treat as anomaly and surface a dedicated panel rather than failing the page.
- Always surface correlationId for 409/422/403/404/503 to support troubleshooting; make it easy to copy.
- 503 retry must not lose context; keep Work Order page intact and allow re-attempt.
- Traceability snapshot is display-only; artifact retrieval is out of scope (no direct download actions).
- Issuance blockers/policy are informational when status is DRAFT; no remediation UI required in this story.
