# [FRONTEND] [STORY] Completion: Finalize Billable Scope Snapshot
## Purpose
Enable authorized users to finalize a Work Order for billing by creating a Billable Scope Snapshot and marking eligible items invoice-ready. Provide a final review experience (totals + PO#) with confirmation, and handle backend-blocking variance by requiring explicit approval with a reason code. Support back-office review by listing and viewing read-only snapshot versions with line-item details and totals.

## Components
- Work Order header summary (Work Order ID, status, eligibility/finalized indicator, active snapshot indicator)
- Final Review panel
  - Totals summary (parts, labor, tax, grand total; currency-aware)
  - PO# input (required when backend requires; validation + error display)
  - “Finalize for Billing” primary button (permission-gated)
  - Inline error banner/field errors (backend error codes/messages)
- Confirmation dialog (pre-finalize)
  - Confirm / Cancel actions
- Variance blocking modal (when backend detects variance)
  - Variance details display (price/tax variance info from backend)
  - Variance reason code selector/input (required to enable approval)
  - “Approve Variance & Finalize” primary button + Cancel
- Success toast/banner (“Work order finalized; sales order created”)
- Billable Snapshot(s) entry point from Work Order (button/link)
- Snapshot list view
  - Table/list rows: version, status, totals, createdBy/createdAt, variance approval indicator
  - Pagination or virtualized list (for large item counts)
- Snapshot detail view (read-only)
  - Header: version, status, totals, createdBy/createdAt
  - Variance approval metadata (approvedBy/approvedAt/reasonCode when present)
  - Line items list (BillableScopeSnapshotItem): name/description, quantity (decimal), unit/line totals, tax, expandable structured details (basis/variance JSON)
  - Performance-friendly rendering (pagination/virtualization if needed)

## Layout
- Top: Work Order header + status/finalized indicator + “Billable Snapshot(s)” link
- Main: Final Review panel (Totals summary + PO# field + Finalize button) above/alongside Work Order items list (show completed/authorized + invoice-ready state)
- Overlay: Confirmation dialog; Variance blocking modal (takes precedence)
- Secondary pages/panels: Snapshot list → Snapshot detail (read-only)

## Interaction Flow
1. Happy path finalize (creates snapshot + marks invoice-ready)
   1. User (Service Advisor or Shop Manager) opens eligible Work Order.
   2. User reviews totals and enters PO# if required.
   3. User clicks “Finalize for Billing” → confirmation dialog appears.
   4. User confirms → UI sends finalize request with required header and optional body.
   5. On success, UI navigates to Snapshot Detail view showing version, status (active/backend-defined), totals, createdBy/createdAt.
   6. Work Order view reflects finalized state after refresh (active snapshot indicator/status) and included items show invoice-ready/billing-ready state.
   7. Show success message: “Work order finalized; sales order created”.
2. Variance detected (blocking) → approval required
   1. User clicks “Finalize for Billing” and confirms.
   2. Backend responds with variance-blocking details.
   3. UI opens variance modal showing variance details; “Approve Variance & Finalize” disabled until reason code provided.
   4. User selects/enters variance reason code → submits approval.
   5. UI re-attempts finalize with approval payload (reasonCode and any backend-required fields).
   6. On success, navigate to Snapshot Detail; display variance approval metadata (approvedBy/approvedAt/reasonCode).
3. View snapshot versions (back office review)
   1. User clicks “Billable Snapshot(s)” from Work Order.
   2. UI shows snapshot list with version, status, totals, created metadata (and variance approval indicator if applicable).
   3. User selects a snapshot → opens read-only Snapshot Detail with line items and totals.
   4. User expands a line (if available) to view structured basis/variance details per snapshot line.
4. Error handling (backend validation/eligibility)
   1. If Work Order not approved: show blocking error (WORKORDER_NOT_APPROVED) and keep user on review screen.
   2. If PO# missing when required: highlight PO# field + show error (MISSING_PO_NUMBER); prevent finalize until resolved.
   3. If totals invalid: show error banner (INVALID_TOTAL_AMOUNTS) and disable finalize until data refresh/issue resolved.
5. Permissions
   1. If user is Technician: hide or disable finalize controls; show read-only messaging as needed.

## Notes
- Data requirements (display + state):
  - WorkOrderItem must support invoice-ready confirmation and error display; include status with “completed” and authorization/billing-ready flags as provided by backend.
  - BillableScopeSnapshot header: version (sequential), status (active/backend-defined), totals (parts/labor/tax/grand), createdBy/createdAt; variance approval fields nullable unless approved (approvedBy/approvedAt/reasonCode).
  - BillableScopeSnapshotItem: quantity is decimal (do not assume integer); include optional structured JSON for basis/variance details; support expandable display.
- Finalize API:
  - Must send required request header (DECISION-INVENTORY-012).
  - Body optional; required when approving variance inline (reasonCode required; include any additional backend-required fields).
  - Success returns snapshot identifiers + totals and/or triggers Work Order re-fetch to update summary fields and item invoice-ready state.
- UX constraints:
  - Require confirmation dialog before finalize to prevent accidental clicks.
  - Snapshot list/detail must render within 2s for up to 200 snapshot items; if more, use pagination or virtualized rendering.
- Acceptance criteria coverage:
  - Scenario 1: snapshot created, navigates to detail, Work Order reflects finalized, items invoice-ready after refresh.
  - Scenario 4: variance blocks finalize until reason code provided; approval metadata visible on snapshot detail.
  - Scenario 6: snapshot versions list and read-only detail with line items and per-line details.
- TODO (implementation):
  - Ensure consistent currency formatting across totals.
  - Define UI mapping for backend error codes to user-friendly messages.
  - Decide whether Work Order refresh is immediate re-fetch or optimistic update after finalize response.
