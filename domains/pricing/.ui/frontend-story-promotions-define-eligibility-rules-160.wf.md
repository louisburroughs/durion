# [FRONTEND] [STORY] Promotions: Define Eligibility Rules (Account/Vehicle)

## Purpose
Enable Pricing Analysts and Account Managers to view, create, and edit promotion eligibility rules scoped to a specific promotion. Provide a clear rules list with an empty state when none exist, and a rule editor that constrains operators based on condition type. Support traceability by making rule configuration explicit and consistent with backend evaluation semantics (AND across rules; no rules means eligible).

## Components
- Page header: “Eligibility Rules” + promotion identifier/context (name/ID)
- Rules list/table
  - Columns: Condition Type, Operator, Value, Actions
  - Row actions: Edit
- Empty state panel (no rules)
- Primary action button: “Add Rule”
- Rule editor modal/drawer (used for Add and Edit)
  - Read-only field: Promotion ID (shown; non-editable once scoped)
  - Select: Condition Type (supported: Account ID List, Fleet Size, Vehicle Tag)
  - Select: Operator (options constrained by condition type)
  - Text input: Value (string)
  - Buttons: Save, Cancel
- Loading state (for list load and evaluation-related loading if present)
- Error states/toasts
  - Access denied (401/403)
  - Promotion not found (404)

## Layout
- Top: Breadcrumb/back to Promotion + page title “Eligibility Rules” + “Add Rule” button (right-aligned)
- Main: Rules list/table (or empty state) occupying full width
- Overlay: Add/Edit Rule modal/drawer centered/right with form fields stacked and Save/Cancel in footer
- Inline ASCII hint: Header (Title | Add Rule) → Content (Rules Table / Empty State) → Modal (Add/Edit Rule)

## Interaction Flow
1. View eligibility rules (happy path)
   1. User navigates to a promotion’s “Eligibility Rules” screen.
   2. UI shows loading state for rules list.
   3. UI calls list rules endpoint for the promotion.
   4. On success, UI adapts response shape (either `{ items: [...] }` or `[...]`) and renders rules list.
2. View eligibility rules (empty state)
   1. If the returned list is empty, UI shows an empty state with brief guidance and an “Add Rule” CTA.
3. View eligibility rules (errors)
   1. If 401/403, show “Access denied” state/message and disable rule management actions.
   2. If 404, show “Promotion not found” state/message with a back navigation option.
4. Create rule (Account ID List)
   1. User clicks “Add Rule”.
   2. UI opens Add Rule modal/drawer with Promotion ID prefilled (read-only).
   3. User selects Condition Type = Account ID List.
   4. UI updates Operator options to only those allowed for Account ID List.
   5. User selects Operator, enters Value (string), clicks “Save”.
   6. UI calls create rule endpoint with `promotionId`, `conditionType`, `operator`, `value`.
   7. On success, modal closes and rules list refreshes; new rule appears.
5. Edit existing rule
   1. User clicks “Edit” on a rule row.
   2. UI opens Edit modal/drawer populated with existing rule data (ruleId present; promotionId read-only).
   3. User changes Operator and/or Value to valid inputs (operator constrained by condition type).
   4. User clicks “Save”.
   5. UI calls update rule endpoint for that rule.
   6. On success, modal closes and rules list refreshes showing updated values.
6. Loading behavior
   1. While list is loading, show standard loading state in the main content area.
   2. While save/update is in progress, disable Save and show in-modal loading indicator.

## Notes
- Eligibility semantics: rules combine with AND; if a promotion has no rules, it is eligible by default (ensure empty state messaging does not imply “ineligible”).
- Condition types supported in this story: Account ID List, Fleet Size, Vehicle Tag.
- Operator must be constrained by condition type; UI should prevent invalid combinations rather than relying on backend errors.
- Value is stored as a backend-defined string; UI edits it as a plain string (no special parsing assumed in this story).
- Promotion ID is required and must be read-only in the UI once scoped to a promotion screen.
- List rules API response may be either `{ items: [...] }` or an array directly; implement an adapter to normalize.
- Error handling requirements:
  - 401/403: show access denied.
  - 404: promotion not found.
- Traceability: rules list should clearly display condition type, operator, and value so Operations/Finance can understand why a promo may apply/deny (no audit log UI required in this story).
