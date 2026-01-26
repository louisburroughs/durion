# [FRONTEND] [STORY] Execution: Apply Role-Based Visibility in Execution UI
## Purpose
Apply role/capability-based visibility rules to the Execution (work order) UI so users only see fields they are authorized to view. Restricted users must be able to view operational work order and line item details without any pricing, cost, margin, or derived totals that could reveal hidden values. Authorized users should see financial fields exactly as returned by the backend (no client-side recomputation required for margin).

## Components
- Page header (Work Order identifier, status, location, assigned technician)
- Work order summary panel (read-only fields)
- Line items list/table (read-only for this story)
- Line item detail rows/columns (operational fields always visible)
- Financial fields group (Pricing / Cost / Margin) with conditional rendering
- Totals/subtotals summary area with conditional rendering
- Loading state (skeleton/spinner for work order + capabilities)
- Error states (403 Forbidden access, generic load failure)
- Empty state for optional financial values (only when authorized and values absent)
- Optional “Access denied” banner/page for unauthorized work order visibility

## Layout
- Top: Header with Work Order title/ID + key metadata (status, location, assigned-to)
- Main: Work order operational details (read-only) above line items
- Main (below): Line items table/list (read-only)
- Right or bottom summary: Totals/subtotals panel (only if financial visibility allows)
- Inline grouping within each line item: Operational fields always; Financial group appears only when authorized

## Interaction Flow
1. User navigates to Execution work order screen for an existing work order.
2. UI initiates Work Order load (includes work order + line items) and resolves user capabilities (embedded in response or fetched separately).
3. If user lacks permission to view the work order per role rules:
   1. Show “403 Forbidden / Access denied” state.
   2. Do not render any work order details or line items.
4. If user can view the work order:
   1. Render operational work order fields and line items (read-only).
   2. Evaluate capability gates for financial visibility (pricing/cost/margin).
5. Scenario: Restricted user (capabilities do not include financial visibility):
   1. Do not render Pricing fields (unit price, extended price, discounts, taxes).
   2. Do not render Cost fields (unit cost, extended cost).
   3. Do not render Margin fields (margin amount, margin %).
   4. Do not render totals/subtotals or any summary elements that would reveal hidden values.
6. Scenario: Authorized user (capabilities include financial visibility):
   1. Render Pricing/Cost/Margin fields where present in backend response.
   2. Display margin values exactly as provided by backend (no client-side recomputation).
   3. Render totals/subtotals only when they do not violate visibility rules (i.e., only when underlying financial fields are visible).
7. Edge cases:
   1. Financial fields are optional: if authorized but a value is absent, show blank/“—” (do not infer or compute).
   2. Partial presence (e.g., pricing present but discounts/taxes absent): show only provided fields.
   3. Capabilities load delayed: keep financial sections hidden until capabilities resolved; avoid flashing sensitive fields.

## Notes
- Line items are read-only for this story; no editing controls should appear.
- Work order load must provide work order + line items and either embedded capabilities or a separate capabilities fetch path.
- Acceptance criteria (restricted users):
  - Operational fields render; pricing/cost/margin fields do not render at all.
  - Totals/subtotals that could reveal hidden values must not render.
- Acceptance criteria (authorized users):
  - Pricing/cost/margin fields render where present and match backend response.
  - Margin display must not rely on client-side recomputation.
- Role-based visibility rules for work order access:
  - Technician: only self-assigned work orders.
  - Supervisor: all work orders for their location.
  - Service Advisor: work orders for their location + associated customers.
  - Shop Manager: all work orders for their location + cross-location reports (UI here focuses on work order view).
  - Customer (portal): only their own work orders (read-only).
- Test fixtures/expected behaviors:
  - Technician sees only self-assigned; attempting supervisor view returns 403.
  - Supervisor sees all location work orders.
  - Customer portal shows only their work orders (read-only).
  - Service Advisor customer list is populated from visible work orders (ensure work order visibility drives related UI data).
