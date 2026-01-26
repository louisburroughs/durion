# [FRONTEND] [STORY] Workexec: Price Product for Estimate Line (Location + Customer Tier)

## Purpose
Enable Service Advisors to fetch and apply accurate, auditable product pricing while building estimate lines, based on the current location and the customer’s tier. Ensure pricing is calculated by the authoritative Pricing Service and displayed with clear breakdowns (unit, extended, totals) and applied rules. Prevent invalid requests by requiring a customer tier (prompting when missing) and provide user-friendly error handling when pricing is unavailable.

## Components
- Estimate Line Editor section (within Estimate Builder)
- Product selector (search/typeahead) with selected product summary
- Quantity input (integer > 0) with validation
- Location display (read-only from POS context)
- Customer tier field (required; dropdown/select) with “Select tier” prompt state
- “Get Price” / “Refresh Price” button
- Pricing summary panel (read-only):
  - Unit price (amount + currency)
  - Extended line price (amount + currency)
  - Totals/adjusted amounts (amount + currency)
- Applied pricing rules list (read-only; expandable details)
- Inline field-level validation messages (for 400 errors)
- Non-blocking alert/banner area for general errors (e.g., 404 price unavailable)
- Loading indicator/spinner for pricing fetch
- Modal or inline prompt for missing customer tier selection (if not already on screen)

## Layout
- Top: Estimate Line header (e.g., “Estimate Line Pricing”)
- Main (two-column):
  - Left: Product selector, Quantity, Location (read-only), Customer Tier (required), Get/Refresh Price button
  - Right: Pricing summary panel + Applied rules list
- Bottom: Inline validation/error area beneath relevant fields; general alert/banner near top of section

## Interaction Flow
1. User opens/edits an estimate line in the Estimate Builder.
2. User selects a product (productId required); selected product summary appears.
3. User enters quantity (must be integer > 0); inline validation appears if invalid.
4. System shows current locationId as read-only (from POS context).
5. If customer tier is missing in current customer profile/context:
   1. UI prompts user to select a customer tier (required by backend).
   2. User selects tier; prompt clears.
6. User clicks “Get Price” (or auto-fetch triggers after required fields are present, if supported).
7. UI calls Pricing Service with: productId, quantity, locationId, customerTierId, and optional effectiveDateTime (default now()).
8. While fetching, disable Get/Refresh button and show loading indicator.
9. On 200 OK:
   1. Render unit price, extended price, totals, and currency.
   2. Render applied pricing rules list (read-only).
   3. Mark pricing fields as read-only and “last updated” timestamp if available.
10. User changes product, quantity, or tier:
   1. UI indicates pricing is stale (e.g., “Price needs refresh” state).
   2. User clicks “Refresh Price” to re-fetch and update pricing.
11. On 400 Bad Request:
   1. Map errors to field-level messages (e.g., missing tier, invalid quantity).
   2. Keep user inputs editable; allow correction and retry.
12. On 404 Not Found (product or price missing):
   1. Show message: “Price unavailable — check product or contact pricing admin.”
   2. Keep estimate line editable; allow changing product/tier and retry.
13. If customer tier is cleared/removed after pricing:
   1. Immediately prompt for tier selection again and prevent pricing fetch until set.

## Notes
- Customer tier is mandatory for backend requests; frontend must ensure a non-null tier is present (prompt/select) before calling Pricing Service.
- Quantity must be an integer > 0; enforce client-side validation and mirror backend validation messaging.
- Pricing fields (unit/extended/totals/rules) are read-only outputs from Pricing Service; do not allow manual edits.
- Effective date/time is optional; default to now() in UTC if not explicitly provided by the UI.
- Acceptance criteria:
  - Correctly fetches pricing using productId, quantity, locationId, and customerTierId.
  - Displays pricing amounts with currency and shows applied rules.
  - Handles 200/400/404 responses as specified, including the exact 404 user message.
  - Prevents requests missing customer tier by prompting user selection.
