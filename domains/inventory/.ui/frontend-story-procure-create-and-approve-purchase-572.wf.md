# [FRONTEND] [STORY] Procure: Create and Approve Purchase Order
## Purpose
Enable purchasing users to create, review, approve, revise, and cancel Purchase Orders in a controlled lifecycle so receiving can execute only from approved commitments and downstream accounting can consume stable PO state for matching and accrual workflows.

## Components
- Breadcrumb: Inventory / Procurement / Purchase Orders
- PO list view
  - Filters: status, vendor, date range, location, PO number
  - Table columns: PO number, vendor, status, total, open balance, expected delivery, updated at
  - Actions: Create PO, View, Approve, Revise, Cancel (gated by status/permission)
- PO create/edit form
  - Header fields: vendor, currency, ship-to location, expected delivery, payment terms, notes
  - Line table: SKU, description, quantity, unit cost, tax code, line total
  - Totals summary: subtotal, tax, grand total
- Approval panel
  - Approval eligibility/status
  - Approve action with confirmation
  - Approval metadata: approver, timestamp, version
- Revision workflow panel
  - Create revision action
  - Version badge and change summary
- Status timeline
  - DRAFT, SUBMITTED_FOR_APPROVAL, APPROVED, PARTIALLY_RECEIVED, FULLY_RECEIVED, CLOSED, CANCELLED
- Error and technical details area
  - Banner errors for forbidden/conflict/validation/system errors
  - Collapsible technical section for correlation ID

## Layout
- Top: Page title + PO number/status badge + primary actions
- Left/main: PO header and line items
- Right rail: totals, lifecycle timeline, approval/revision cards
- Bottom action bar (form mode): Cancel | Save Draft | Submit for Approval | Approve (when eligible)

## Interaction Flow
1. User opens PO list and selects Create PO.
2. User enters header and line data; totals update from backend-authoritative responses.
3. Save Draft persists PO in `DRAFT`; user can continue edits.
4. Submit for approval transitions PO to review state.
5. Approver opens PO detail and confirms approval.
6. Backend transitions status to `APPROVED`; UI locks immutable pricing/qty fields.
7. If changes are needed post-approval, user starts revision workflow:
  1. Backend creates new version.
  2. UI shows version increment and updated status/approval requirements.
8. User can cancel only in eligible states; UI requires confirmation and surfaces reason.
9. PO detail displays receiving progress and open balance as receipts occur (read-only in this screen).
10. Any validation/permission/conflict error maps to deterministic banners and inline fields where applicable.

## Notes
- Receiving against non-approved POs is blocked and surfaced as conflict.
- UI does not calculate accounting postings; it only presents PO lifecycle state and totals returned by backend.
- All money fields are rendered in currency-aware format from minor-unit API values.
- Permission gating must hide or disable approval/revision/cancel actions for unauthorized users.
- Correlation IDs are shown in technical details for supportability.
