# [FRONTEND] [STORY] Promotions: Record Promotion Redemption from Invoicing
## Purpose
Provide CRM users a way to view Promotion Redemptions, primarily via a list filtered by `promotionId`, and to open a redemption detail view. Ensure optional redemption identifiers and timestamps are displayed only when present. Block unauthorized users by showing an access denied state with no data.

## Components
- Breadcrumb navigation: CRM → Promotions → Redemptions
- Page header: “Redemptions”
- Filter/search form
  - Promotion ID (UUID) input (required for the primary flow)
  - Submit/Search button
  - Reset/Clear filters button
- Results area
  - Empty-results state (standard guidance per SD-UX-EMPTY-STATE)
  - Paginated table/list of redemptions
  - Sort controls (table header sort)
  - Pagination controls (page size, next/prev)
- Row actions
  - Click row / View details link
- Detail view panel/page
  - Key fields display (required + optional)
  - Back to list link
- Error/guard states
  - Access denied message (403)
  - Generic error message area (non-403 failures)

## Layout
- Top: Breadcrumb + Page title
- Main (stacked):
  - Filter bar: [Promotion ID input] [Search] [Clear]
  - Results section: table (or empty state) + pagination footer
- Detail route: Title + key/value field list; back link at top-left of content

## Interaction Flow
1. Navigate to Redemptions list
   1. User opens CRM → Promotions → Redemptions.
   2. UI shows filter form and an initial empty-results guidance state until a search is run (or shows empty state if no results).
2. Search redemptions by `promotionId` (primary scenario)
   1. User enters a valid UUID `promotionId` and submits search.
   2. UI calls the list service with `promotionId` plus pagination and sort parameters.
   3. UI renders a paginated list of results.
   4. Each row displays required identifiers: `promotionRedemptionId` and `promotionId`.
   5. Optional fields (e.g., `customerId` canonical when present, other optional UUID identifiers, and timestamps) appear only if present in the response.
3. Open redemption detail
   1. User clicks a row (or “View details”).
   2. UI navigates to the redemption detail route.
   3. UI displays required fields and any optional identifiers.
   4. Timestamp display rule: show `redeemedDate` and/or `createdDate` (or equivalent) if present; if both exist, show both in detail.
   5. User can return to list via Back link (preserve last search filters when possible).
4. Unauthorized access blocked (list)
   1. Authenticated user without permission navigates to the list route.
   2. Backend returns 403 for list call.
   3. UI transitions to an access denied state, shows an access denied message, and displays no redemption data (no table rows).
5. Unauthorized access blocked (detail)
   1. Authenticated user without permission navigates directly to a detail route.
   2. Backend returns 403 for detail call.
   3. UI shows access denied message and no redemption data.

## Notes
- Navigation context: Breadcrumb is CRM → Promotions → Redemptions; include routes for List, Detail, and a shortcut filtered list that redirects to list with `promotionId` filter applied (final URL mapping per repo conventions; route paths are TODO).
- Data model (PromotionRedemption DTO, minimum for UI):
  - Required: `promotionRedemptionId` (UUID), `promotionId` (UUID).
  - Optional/nullable: `customerId` (UUID; canonical customer identifier when present), additional optional UUID identifiers (treated as UUID), and optional ISO-8601 datetime fields.
- Acceptance criteria highlights:
  - List search submits filters + pagination + sort to list service.
  - List rows always show `promotionRedemptionId` and `promotionId`.
  - Optional fields render conditionally only when present.
  - Detail shows both timestamps if both exist.
  - 403 responses for list/detail must result in access denied UI with no data shown.
- SD-UX-EMPTY-STATE: Provide a standard empty-results state with guidance (UI-only ergonomics).
- Risk/requirements gaps: service names, exact route paths, exact optional field names, and which timestamp fields exist are incomplete; implement defensively with conditional rendering and clear TODOs in code/design.
