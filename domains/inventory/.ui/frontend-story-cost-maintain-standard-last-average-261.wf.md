# [FRONTEND] [STORY] Cost: Maintain Standard/Last/Average Cost with Audit

## Purpose
Provide a Costs screen for an Inventory Item that displays Standard, Last, and Average costs with clear null handling (“Not set”). Allow authorized users to update Standard Cost only, requiring a reason code and recording the change in an audit history list. Prevent unauthorized edits and surface validation, authentication/authorization, and concurrency errors without changing displayed values.

## Components
- Page header: “Costs” + Inventory Item identifier/name context
- Read-only cost display fields:
  - Standard Cost (editable only when authorized and in edit mode)
  - Last Cost (read-only)
  - Average Cost (read-only)
- Null-state display text: “Not set” for any null cost value
- Standard Cost edit controls (authorized users only):
  - “Edit Standard Cost” action
  - Standard Cost numeric input (decimal)
  - Reason code selector/input (required)
  - Optional note/comment input (if supported by backend)
  - Save button
  - Cancel button
- Inline field validation messages
- Global error banner/toast area (auth/concurrency/service errors)
- Audit history table (read-only):
  - Columns: Changed At, Cost Type, Old Value, New Value, Reason Code, Changed By (if available), Note (if available)
  - Empty state message when no audit entries
- Optional hidden concurrency token handling (ETag/updatedAt)

## Layout
- Top: Page header (“Costs”) + item context (ID/name)
- Main (two stacked sections):
  - Costs panel: Standard Cost / Last Cost / Average Cost fields; Standard Cost edit actions aligned to the right
  - Audit History panel: table beneath costs with most recent entries first
- Footer area within Costs panel: Save/Cancel row appears only in edit mode

## Interaction Flow
1. View costs with null handling
   1. User opens Inventory Item → Costs screen.
   2. UI loads InventoryItem cost fields and audit list.
   3. For any null cost value, display “Not set” (not 0).
   4. Last Cost and Average Cost are always read-only.
2. Authorized user updates Standard Cost with reason code
   1. If user has permission, show “Edit Standard Cost”.
   2. User clicks “Edit Standard Cost” → Standard Cost becomes an editable decimal input; Reason Code field appears/enabled.
   3. Client-side validation on Save:
      1. If Standard Cost is 0 or negative → block save; show “Standard Cost must be greater than 0.”
      2. If Standard Cost is not a valid decimal → block save; show “Enter a valid number.”
      3. If Reason Code is blank → block save; show “Reason code is required.”
   4. On valid input, user clicks Save → call Update Standard Cost service with itemId, new standardCost, reasonCode, and optional concurrency token.
   5. On success:
      1. Update displayed Standard Cost value.
      2. Update stored/displayed concurrency token (if returned).
      3. Prepend a new audit row showing costType = Standard, old value, new value, reason code (and other returned fields if available).
      4. Exit edit mode (fields return to read-only display).
3. Unauthorized user cannot edit Standard Cost
   1. If user lacks permission, Standard Cost is displayed read-only and no edit/save actions are shown.
   2. If a save attempt occurs and backend returns unauthenticated/unauthorized:
      1. Show authorization error message in global error area.
      2. Do not change any displayed values or audit list.
4. Service error and concurrency edge cases
   1. If backend returns validation errors (e.g., invalid scale) → show error message; remain in edit mode; do not update displayed values.
   2. If backend returns concurrency conflict (when supported) → show conflict message; prompt user to refresh/reload costs; do not apply changes locally.

## Notes
- InventoryItem fields are read-only except via the Standard Cost update action; Last Cost and Average Cost must never be editable.
- Standard Cost update requires a reason code; enforce client-side before calling service.
- Display “Not set” for null costs; avoid rendering null as 0.
- Audit history is a read-only table; include a new entry after successful Standard Cost update (costType Standard, old/new values, reason code).
- Handle unauthenticated error code DECISION-INVENTORY-012 and missing permission with a clear authorization error and no UI state mutation.
- Concurrency token (timestamp/ETag) is optional but recommended; if supported, include it in update requests and update it on success; show a conflict message on mismatch.
- Requirements are marked incomplete; keep UI flexible for backend-provided fields (e.g., changedBy, correlationId, note) and for reason code source (enum vs free text).
