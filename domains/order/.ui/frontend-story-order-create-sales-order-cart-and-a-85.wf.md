# [FRONTEND] [STORY] Order: Create Sales Order Cart and Add Items
## Purpose
Enable users to create a new DRAFT Sales Order “cart” and manage its line items (add, update quantity, remove) with server-side pricing and optional inventory checks. Provide a flow to link a source document (estimate/workorder) and merge lines idempotently. Handle pricing-service outages via cached pricing warnings or a permission-gated manual price fallback, with clear blocking errors when actions cannot proceed.

## Components
- Page header: “Sales Order” + status badge (DRAFT) + orderId display
- Primary action button: Create New Order
- Blocking error banner (dismissible or persistent until resolved)
- Non-blocking warning banner/toast area (e.g., cached price used, inventory warning)
- Order summary panel: subtotal (read-only), created/updated timestamps (read-only)
- Add Item form:
  - Identifier input (SKU/service code)
  - Quantity input (integer)
  - Add button
  - Conditional manual price section (shown only when required + permitted):
    - Unit price input (decimal)
    - Reason code input (required)
    - Retry/Submit button (or reuse Add button state)
- Order lines table/list:
  - Columns: identifier, description (read-only), quantity (inline editable), unit price (read-only except manual add-time), line subtotal/amount (if provided), flags (priceSource, inventory status)
  - Row actions: Remove (trash) with confirmation
  - Inline quantity editor with save/apply behavior
  - Line badges/indicators: priceSource (LIVE/CACHE/MANUAL), inventoryStatus (e.g., INSUFFICIENT), stale price indicator
- Link Source section:
  - Source type selector (Estimate / Workorder)
  - Source ID input
  - Link button
  - Merge results panel (merged vs added lines; idempotency message)
- Modal dialogs:
  - Confirm remove line
  - (Optional) Link source confirmation if destructive/merging is significant
- Permission/unauthorized message panel (403 handling)

## Layout
- Top: Page header + Create New Order button + status/orderId
- Main (two-column): Left = Add Item form + Link Source; Right = Order summary (subtotal, timestamps)
- Below main: Order lines table/list (full width) + banners above table for errors/warnings

## Interaction Flow
1. Create New Order
   1. User clicks “Create New Order”.
   2. UI calls create SalesOrder service.
   3. On success: navigate to Order Detail view with orderId; fetch order detail (header + lines).
   4. On failure: show blocking error banner with backend reason; disable add/link actions until resolved as appropriate.
2. Load/Refresh Order Detail
   1. On entering Order Detail (or after mutations), UI calls get order detail.
   2. Render header fields (orderId, status=DRAFT, facility/location context if returned), subtotal, timestamps, and lines.
3. Add Item (normal pricing)
   1. User enters Identifier and Quantity.
   2. Client validation: identifier non-empty; quantity integer > 0.
   3. UI calls add-line service (server resolves pricing/inventory; returns created line and updated totals or triggers refetch).
   4. On success: append/render line; refresh subtotal (from response or refetch).
4. Add Item (pricing service unavailable → cached price)
   1. Backend returns line with priceSource=CACHE (within TTL).
   2. UI adds line and shows warning: “Pricing service unavailable — using cached price (may be stale).”
   3. Line displays priceSource badge and stale indicator.
5. Add Item (pricing service unavailable → manual entry permitted)
   1. Backend indicates no price available and manual price is required.
   2. If user has ENTER_MANUAL_PRICE permission: reveal manual unit price + reason code inputs.
   3. Validate: unit price decimal > 0; reason code non-empty.
   4. Retry submit add-line with manual fields; on success show line with priceSource=MANUAL and store/display reason code as read-only (if surfaced).
6. Add Item (pricing service unavailable → manual entry not permitted)
   1. Backend indicates no price and manual required; user lacks permission.
   2. UI shows blocking error: “Pricing unavailable and manual price entry is not permitted.”
   3. Ensure manual price controls remain hidden/disabled.
7. Inventory insufficient handling (if backend provides policy/status)
   1. If policy=ALLOW: add line, set inventoryStatus=INSUFFICIENT, show warning on line and/or banner.
   2. If policy=PREVENT: do not add line; show blocking error with the exact backend-provided message.
8. Update Quantity
   1. User edits quantity inline for a line (integer > 0).
   2. On apply/save: call update-line service; on success refresh line + order subtotal (response or refetch).
   3. On validation error: revert field and show inline error or banner.
9. Remove Line
   1. User clicks Remove on a line; confirm modal appears.
   2. On confirm: call remove-line service; on success remove line and refresh subtotal.
10. Link Source (estimate/workorder) and merge idempotently
   1. User selects source type and enters sourceId; clicks Link.
   2. UI calls link/merge service.
   3. Show merge results: which items were merged vs added as separate lines.
   4. If backend indicates already linked: show “Already linked; no changes” and do not alter lines.
11. Unauthorized (403) for any action
   1. If any service returns 403: show permission error panel/banner.
   2. If the action was manual price entry: hide/disable manual controls after the response.

## Notes
- SalesOrder fields: orderId (required, read-only), status enum (read-only; DRAFT after create), customerId/locationId (derived from session; read-only), subtotal (read-only), createdAt/updatedAt (read-only).
- SalesOrderLine fields: lineId/orderId (read-only), identifier (required), description (read-only), quantity (editable), unitPrice (read-only except manual add-time), priceSource enum (read-only), inventoryStatus enum (read-only), manual reason code required when priceSource=MANUAL (assume read-only after add).
- Prefer backend to perform pricing/inventory checks and return created line + updated subtotal; otherwise refetch order detail after each mutation.
- Validation rules: identifier required; quantity integer > 0; manual unit price > 0 and reason code required when manual flow is active.
- Error handling: create failure and add-line hard failures should be blocking; cached-price and allow-add inventory insufficiency should be warnings.
- Idempotency: re-linking the same source must not duplicate lines; UI must surface backend “already linked” response clearly.
- Dependencies must exist or be stubbed: create order, get detail, add/update/remove line, link/merge source, pricing lookup/line add with server-side pricing, optional inventory availability check, permission check for manual price entry.
- Risk: requirements incomplete—ensure UI is resilient to missing fields (e.g., description, inventory policy) and relies on backend response flags for priceSource/inventoryStatus.
