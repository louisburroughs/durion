# [FRONTEND] [STORY] Billing: Define Account Billing Rules
## Purpose
Provide a frontend screen to define and manage billing rules at the account level so billing and earnings processing behaves consistently. Enable users to configure how an account is billed (timing, method, and rule conditions) and ensure rules are clear, reviewable, and editable. Reduce ambiguity and incomplete requirements by presenting defaults, validations, and a preview of how rules will apply.

## Components
- Page header with title and account identifier (name/ID)
- Account selector or breadcrumb back to Account details
- Billing Rules list/table (rule name, status, priority/order, effective dates, last updated)
- “Add Billing Rule” primary button
- Rule editor form (inline panel or modal)
  - Rule name
  - Status toggle (Active/Inactive)
  - Effective start/end date fields
  - Priority/order field
  - Billing method/type selector (e.g., invoice/charge/other)
  - Billing schedule/timing selector (e.g., on event, periodic)
  - Conditions builder (simple dropdowns + add condition)
  - Earnings processing option(s) (how to process earnings)
  - Notes/description field
- Validation/error messaging area
- Save / Cancel buttons
- Delete rule action (with confirmation)
- Rule details drawer/expand row for read-only view
- “Preview/Simulate” panel (summary of resulting billing behavior)

## Layout
- Top: Header (Title + Account context) | Actions (Add Billing Rule)
- Main: Billing Rules table/list (left-to-right columns) with row actions (View/Edit/Delete)
- Right (or below list): Preview/Simulate panel showing selected rule impact
- Modal/side panel: Rule editor form with Save/Cancel at bottom

## Interaction Flow
1. User navigates to an Account’s Billing section and sees existing billing rules in a list with status and effective dates.
2. User clicks a rule row to view details; the preview panel updates to summarize billing and earnings processing behavior for that rule.
3. User clicks “Add Billing Rule” to open the rule editor.
4. User enters rule name, selects billing method/type and schedule/timing, sets effective dates, and configures conditions and earnings processing options.
5. User clicks Save:
   1. System validates required fields (name, method/type, timing, effective start) and logical constraints (end date after start, priority numeric).
   2. If validation fails, errors display inline and at the top of the form.
   3. If valid, rule is added to the list and becomes selectable; preview updates.
6. User edits an existing rule via Edit action:
   1. Form loads current values.
   2. Save updates the rule and refreshes list metadata (last updated).
7. User deletes a rule via Delete action:
   1. Confirmation dialog appears.
   2. On confirm, rule is removed (or marked inactive if soft-delete behavior is required).
8. Edge case: User attempts to create overlapping active rules with the same priority/effective period; system warns and blocks save or requires priority adjustment.
9. Edge case: User sets rule to inactive; it remains in list but is excluded from preview/simulation results.

## Notes
- Constraints/AC:
  - Must support creating, viewing, editing, and deleting (or deactivating) account billing rules.
  - Must include clear validation for dates, required fields, and priority/order conflicts.
  - Must provide a readable summary/preview of how billing and “process earnings” will behave for the selected rule.
- Important comment context: “Billing and proses earnings” implies rules must explicitly cover earnings processing behavior alongside billing configuration.
- Risk: requirements incomplete; design should favor discoverable defaults, tooltips/help text, and safe guardrails (warnings for conflicts/overlaps).
- TODO (design/dev):
  - Define exact billing method/type options and schedule/timing options available in the product.
  - Decide whether delete is hard delete or soft deactivate; align UI copy accordingly.
  - Define condition builder scope (simple fixed fields vs. advanced expression builder) and how it maps to backend rule representation.
