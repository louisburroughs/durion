# [FRONTEND] [STORY] Catalog: View Product Details with Price and Availability Signals
## Purpose
Enable an authenticated Service Advisor to view product details along with pricing and availability signals for a selected location. The screen should request pricing/availability only when a valid location is selected, and render clear states for successful, unavailable, and partial data responses. Prevent misleading price displays (e.g., null rendered as 0) while still showing availability when possible.

## Components
- Page header (Product name/title)
- Back navigation control
- Location context indicator (current selected location) + “Select location” prompt/action
- Product details section
  - Product description
  - Specifications list/table
- Pricing section
  - MSRP display (formatted currency)
  - Store price display (formatted currency)
  - “Price unavailable” indicator/state
- Availability section
  - Availability status indicator
  - Quantity available (numeric)
  - Quantity on order / incoming (numeric)
- Loading state (skeleton/spinner for pricing/availability area)
- Error/empty state messaging area (inline)

## Layout
- Top: Header row with Back + Product title; right side shows current Location (or “No location selected”)
- Main (stacked sections):
  - Product Description
  - Specifications
  - Pricing (MSRP + Store Price OR “Price unavailable”)
  - Availability (Status + quantities)
- Inline hint: [Header: Back | Product Title | Location] → [Description] → [Specs] → [Pricing] → [Availability]

## Interaction Flow
1. Successful retrieval (location selected)
   1. User (authenticated, permitted) navigates to Product Detail for a specific product.
   2. App location context already has a valid location id selected.
   3. On screen load, UI requests product pricing/availability for the product + selected location.
   4. While waiting, show loading state for pricing/availability (product description/specs may render immediately if available).
   5. When response is OK, UI displays product description and specifications.
   6. When pricing status is OK, UI displays MSRP and store price and formats values using currency formatting.
   7. When availability status is OK, UI displays availability status and quantity signals (e.g., available quantity and on-order/incoming quantity).

2. No location selected (block request until chosen)
   1. User navigates to Product Detail for a specific product.
   2. No location is selected in app location context.
   3. On render, UI does not call the backend pricing/availability endpoint.
   4. UI prompts user to select a location to view pricing and availability (clear inline message + action).
   5. User selects a valid location.
   6. UI requests pricing/availability for the product + newly selected location and then renders results per success/unavailable rules.

3. Pricing non-OK (explicit unavailable, no numeric prices)
   1. Backend returns a response for product + location where pricing status is not OK.
   2. UI renders Product Detail and shows a “Price unavailable” indicator in the Pricing section.
   3. UI does not display numeric MSRP/store price values (must not render null as 0).
   4. If availability status is OK, UI still displays availability quantities and status even when pricing is unavailable.

## Notes
- Pricing/availability request must be gated by presence of a valid selected location id; do not call endpoint when location is missing.
- Acceptance criteria:
  - OK pricing renders MSRP and store price with correct currency formatting.
  - Non-OK pricing renders “Price unavailable” and suppresses numeric price fields entirely.
  - Availability can render independently; if availability is OK, show quantities even if pricing is unavailable.
  - Product description and specifications must render when available regardless of pricing state.
- Ensure empty/null price values never appear as 0; treat null/undefined as “unavailable” and hide numeric output.
- Provide clear, explicit UI prompt when location is not selected; selecting a location triggers the request automatically.
