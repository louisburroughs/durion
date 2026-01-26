# [FRONTEND] [STORY] Promotions: Apply Offer During Estimate Pricing
## Purpose
Enable Service Advisors to apply a single promotion code to an estimate during pricing and immediately see updated totals and a promotion adjustment line item. Provide clear success, replacement, and ineligibility/error feedback while ensuring totals/adjustments are rendered strictly from backend-provided money objects. Surface applied promotion metadata (code/label and sourceId) for support and audit visibility.

## Components
- Estimate Detail screen header (estimate identifier/state context)
- Promotion section (new/modified)
  - Promotion code input field
  - Apply button/action
  - Inline error/warning message area (for apply failures or state restrictions)
  - Applied promotion summary (read-only display of code and label if provided)
  - Applied promotion details area (read-only sourceId if provided)
- Totals/Adjustments area (modified)
  - Line items list including promotion adjustment line(s)
  - Promotion adjustment line item (negative Money amount)
  - Applied promotion metadata indicator near totals (code/label)
- Loading/disabled states for Apply action (in-progress request)
- Accessibility helpers (field labels, error association, focus management)

## Layout
- Top: Estimate header (title + key estimate context/state)
- Main (stacked sections):
  - Promotion section (above totals): [Promotion Code Input] [Apply] + inline message + applied summary/details
  - Totals/Adjustments section: list of adjustments (incl. promotion) + totals summary
- Footer/Bottom of main: any existing estimate actions (unchanged)

## Interaction Flow
1. View estimate detail screen with at least one priceable line item.
2. Apply valid promotion (success):
   1. User enters a promotion code in the Promotion code input.
   2. User clicks Apply.
   3. UI sends apply promotion request to backend (per existing integration pattern).
   4. While pending, disable Apply (and optionally input) and show loading state.
   5. On success, UI re-renders totals/adjustments using only backend response money objects.
   6. UI shows applied promotion indicator (code and label if provided).
   7. Totals/adjustments include a promotion adjustment line with a negative Money amount.
   8. UI shows sourceId in the promotion details area if provided.
3. Reject valid code not applicable (HTTP 400):
   1. User enters code and clicks Apply.
   2. Backend responds HTTP 400 with an error identifier/message payload.
   3. UI maps backend error to a user-friendly message and displays it inline in the Promotion section.
   4. No new promotion adjustment line is added; totals remain unchanged.
4. Replace existing promotion (single promotion per estimate):
   1. If a promotion is already applied, user enters a different code and clicks Apply.
   2. UI sends apply request; backend replaces existing promotion.
   3. On success, UI displays the new promotion as applied (code/label) and updates sourceId if provided.
   4. Adjustments/totals reflect only one active promotion outcome (no stacked/duplicate promotion lines).
5. Estimate state disallows applying promotions:
   1. If estimate is in a state where promotions cannot be applied, show applied promotion (if any) read-only.
   2. Disable promotion input and Apply button.
   3. Display inline message: “Promotions can’t be applied in the current estimate state.”

## Notes
- Single promotion per estimate; applying a different code replaces the existing promotion (no stacking).
- Totals and adjustment amounts must be rendered strictly from backend response money objects; do not recompute client-side.
- Promotion section must support three UI states: no promotion applied, promotion applied (read-only summary), and disabled due to estimate state.
- Inline error/warning area should handle mapped backend errors (not applicable/eligibility failures) and remain within the Promotion section.
- Display user-visible audit/support data when provided by backend: applied promotion code, label, and sourceId (in a details area).
- Implement with Vue.js 3 + TypeScript using Quasar components; ensure responsive layout and accessibility (labels, focus on error, ARIA for messages).
- Risk note: requirements may be incomplete; keep UI flexible for additional backend error mappings and future promotion metadata fields.
