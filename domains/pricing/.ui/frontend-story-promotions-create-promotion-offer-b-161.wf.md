# [FRONTEND] [STORY] Promotions: Create Promotion Offer (Basic)
## Purpose
Enable an Account Manager to create a basic Promotion Offer with a code, description, discount type/value, validity window, and optional usage limit, initially created as non-active per backend response. Provide a detail view to review the created promotion and allow activation/deactivation when permitted. Ensure deterministic client-side validations and clear handling of server-side uniqueness and concurrency conflicts.

## Components
- Page header with title and breadcrumb/back link to Promotions list
- Create Promotion form
  - Promotion Code input (with helper text + validation messaging)
  - Description input (text)
  - Discount Type select (FIXED_AMOUNT, PERCENT_DISCOUNT, BOGO)
  - Discount Value input (numeric/decimal)
  - Valid From (startDate) datetime input
  - Valid To (endDate) datetime input (optional)
  - Usage Limit input (optional numeric) and/or “Unlimited” toggle (if supported)
  - Locations selector (optional; null = all locations) if exposed by backend
- Form actions: Create/Submit, Cancel
- Inline field error messages + form-level error banner
- Promotion Offer Detail view (read-only fields)
- Status badge (DRAFT/ACTIVE/INACTIVE)
- Action buttons: Activate, Deactivate (enabled/disabled based on capability flags + status)
- Toast/alert messages (success, error, conflict)
- Audit section (read-only): createdAt/createdBy, lastUpdatedAt/lastUpdatedBy (if provided)
- Change History list (read-only) with timestamp, user, field, old/new values (if provided)

## Layout
- Top: Breadcrumb/back + Page title; on Detail also show Status badge on the right
- Main (Create): Single-column form with grouped sections: Code/Description; Discount; Validity; Limits/Scope; Actions at bottom
- Main (Detail): Read-only “Details” panel; right/top actions (Activate/Deactivate); below details show Audit + Change History (if available)

## Interaction Flow
1. Navigate to “Create Promotion” screen (authenticated Account Manager with manage promotions permission).
2. Enter Promotion Code; on blur/submit, trim whitespace and validate: length 1–32 and matches regex `[A-Z0-9_-]+` (show helper text and inline error if invalid).
3. Enter Description (required).
4. Select Discount Type (required).
5. Enter Discount Value:
   1. Must be > 0; show message on zero/negative: “Promotion value must be greater than $0.00”.
   2. If type = PERCENT_DISCOUNT, constrain to 0–100 (client-side) while still enforcing > 0.
6. Set Valid From and optional Valid To:
   1. Validate startDate < endDate when endDate present.
   2. Validate endDate is in the future or null.
7. Optionally set Usage Limit (if present); validate non-negative numeric and required only if field is shown as enabled.
8. Click Create/Submit:
   1. If client-side validation fails, prevent submit and show field errors.
   2. If submit succeeds, backend returns created PromotionOffer with status (UI does not assume); navigate to Promotion Offer Detail screen for returned ID.
9. On Detail screen:
   1. Display promotion code read-only (as stored/normalized by backend, expected uppercase).
   2. Display all returned fields (type, value, validity window, usage limit, locations if present).
   3. Display audit data if provided; render Change History list if provided by backend.
10. Activation/Deactivation:
   1. Enable Activate button only if capability flag allows and status = DRAFT.
   2. Enable Deactivate button only if capability flag allows and status = ACTIVE.
   3. On click, call corresponding backend action; on success, reload detail and show success message.
11. Server-side duplicate code error on create:
   1. If backend rejects with duplicate/constraint error (case-insensitive uniqueness), show inline field error on Promotion Code: “Code must be unique” (or equivalent), and do not navigate away.
12. Concurrency conflict on activation/deactivation:
   1. If backend responds HTTP 409, reload Promotion Offer detail, show message indicating it was updated by another user, and do not show success state for the attempted action.

## Notes
- Code constraints: trim whitespace; length 1–32; regex `[A-Z0-9_-]+`; backend normalizes to UPPERCASE; uniqueness is case-insensitive.
- Discount value: BigDecimal, scale 4; must be > 0; use HTML5 numeric input; for PERCENT_DISCOUNT accept 0–100 (client-side) while enforcing > 0.
- Date window: startDate required; endDate optional; enforce startDate < endDate; endDate must be future or null.
- Status handling: initial status is returned by backend; UI must not assume DRAFT on create.
- Capability flags from API response drive button enabled/disabled states; also require user permission to manage promotions.
- Out of scope: authoring eligibility rules, applying promotions to estimates/work orders, stacking logic beyond single promotion, approvals, bulk import/export, editing existing promotion fields.
- Moqui wiring: screens/transitions/service calls needed for create, fetch detail, activate, deactivate; update endpoint exists but editing fields is out of scope here.
- Risk note: requirements may be incomplete; implement defensively (graceful handling of missing optional fields like locations/audit/history).
