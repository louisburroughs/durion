# [FRONTEND] [STORY] Workexec: Retrieve and Display Estimates for Customer/Vehicle
## Purpose
Enable Service Advisors to retrieve and view estimates scoped to a specific Customer or Vehicle, using canonical Workexec/Moqui navigation targets. Provide a paginated, permission-aware list with key summary fields and a drill-down detail view showing line items, notes, and backend-provided expiry indicators. Ensure resilient UX with clear error states and retry behavior when services fail.

## Components
- Page header with context (Customer or Vehicle name/identifier) and breadcrumb/back navigation
- Filters bar
  - Search input (free text)
  - Status dropdown filter
  - Optional location dropdown filter
  - Sort control (e.g., Last Updated, Total Amount)
- Estimates list table
  - Columns: Estimate ID, Status (badge), Grand Total (with currency if provided), Last Updated
  - Row click/tap navigation to detail
  - Optional row action: “Convert to Workorder” button (visible only if `canConvertToWorkorder`)
- Pagination controls (page buttons) and/or infinite scroll loader
- Loading states (skeleton/spinner for list and detail)
- Empty state (no estimates found for current filters)
- Error state panel with message + Retry button
- Estimate detail header
  - Estimate identifier, status badge, last updated, expiry indicator (if backend fields present)
- Line items list/table
  - Line description/name, type/category, quantity, unit price, line total
  - Declined indicator when `declined=true`
- Notes section (render string or list of note objects)
- Back button (returns to list preserving filters/sort/pagination)

## Layout
- Top: Breadcrumb + page title (“Estimates”) + context chip (Customer/Vehicle)
- Below top: Filters row (Search | Status | Location | Sort)
- Main: Estimates table + (right-aligned) pagination or infinite-scroll loader at bottom
- Detail screen main: Header summary → Line items block → Notes block → Footer back action

## Interaction Flow
1. Enter Estimates List from Customer context (has `customerId`).
2. On load, call list endpoint filtered by `customerId` with current pagination/sort; show loading state.
3. Render rows with ID, status label, grand total (format with currency if provided), and last updated timestamp.
4. User changes filters/sort/pagination:
   1) Update query parameters/state, 2) re-call list endpoint, 3) update table results.
5. Enter Estimates List from Vehicle context (has `vehicleId`):
   1) Call list endpoint filtered by `vehicleId`, 2) render same table fields and controls.
6. User selects an estimate row:
   1) Navigate to Estimate Detail screen for that `estimateId` (Moqui screen route where applicable),
   2) auto-load detail on screen load, show loading state.
7. Detail renders:
   1) Header: estimate identifier, status, last updated, expiry indicator if backend provides expiry fields,
   2) Line items list (stable keys via line item id; show declined state when provided),
   3) Notes section (as provided).
8. User taps Back:
   1) Return to list with preserved filters/sort/pagination and scroll position where feasible.
9. Error handling (list or detail):
   1) 401 → show “Session expired” pattern and route to login (if available).
   2) 403 → show “You do not have access to this estimate”.
   3) 404 → show “Estimate not found”.
   4) 400 → show validation message (and field errors if present).
   5) 5xx/network → show “Unable to load estimates; service temporarily unavailable” + Retry.
10. Retry behavior:
   1) Retry button re-issues the last failed request with the same parameters and restores loading state.

## Notes
- In-scope: customer- or vehicle-scoped entry points, paginated list, detail view with line items/notes, permission-aware visibility, backend-driven expiry display only, retry + user-friendly errors, Moqui Workexec screens as canonical navigation targets.
- Do not implement new expiry business logic; only display expired status/indicator when backend provides expiry fields (per Customer Approval Workflow reference).
- Data requirements (list): estimate id, status (display label), grand total (decimal string preferred), currency (required if formatting with symbol), last updated (ISO-8601).
- Data requirements (detail): id, status, last updated, line items array (id, type/category, description, qty, unit price, line total, declined flag), optional notes, optional expiry datetime, optional totals breakdown.
- Accessibility/responsiveness: table should be usable on smaller screens (stacked rows or responsive columns); status conveyed via text + color.
- Test fixtures to support: filter by customer, filter by status APPROVED, sort by TOTAL_AMOUNT desc, pagination pageSize=20; error cases for invalid customerId and service unavailable.
- TODO (blocking): confirm exact backend endpoints, pagination envelope, and response shapes for list-by-customer/vehicle and get-detail; align with DECISION-INVENTORY-011 error envelope and Moqui route conventions.
