# [FRONTEND] [STORY] Promotion: Generate Workorder Items from Approved Scope

## Purpose
Enable authorized users to promote an Approved Estimate into a Work Order, generating Work Order Items from the approved scope and then navigating to the resulting Work Order. Ensure the UI handles in-flight state, stale estimate status, and “already promoted” concurrency outcomes. Provide clear blocking errors for tax configuration failures and other backend validation issues while keeping the user on the Estimate screen when promotion cannot proceed.

## Components
- Estimate header (estimate identifier, status badge)
- Primary action button: “Promote to Work Order”
- Inline helper text (permission/state-dependent)
- Loading/in-flight state indicator on Promote action (disabled button + spinner)
- Blocking error modal/dialog (title, message, expandable “Details” showing error code + message)
- Informational banner/toast on Work Order screen (“Opened existing work order.”)
- Work Order screen header (workOrderId, workexecId, status)
- Work Order totals summary (money totals)
- Work Order Items table/list
  - Columns: itemId, workOrderId, estimateItemId (traceability), quantity, itemType, description/name, productId (nullable), unit price (decimal), snapshot pricing/tax summary, status, isTaxExempt
- Read-only indicators for snapshot fields (pricing/tax snapshot vs catalog display)

## Layout
- Top: Estimate page header (ID + status) | Right: “Promote to Work Order” button
- Main: Estimate details (existing estimate UI) + scope summary (read-only)
- Overlay: Blocking error modal (center) with “Dismiss” action
- Work Order page: Header + totals (top), items list (main), banner area (top of main)

## Interaction Flow
1. Primary flow: successful promotion
   1. User views an Estimate with status Approved and has permission to promote.
   2. User clicks “Promote to Work Order”.
   3. UI sends promotion request for the estimate, including an Idempotency-Key header.
   4. UI disables the Promote action while request is in-flight (prevent double-submit).
   5. On success, UI navigates to the Work Order route for the returned workOrderId.
   6. Work Order screen loads and displays Work Order totals and Work Order Items matching the promoted estimate scope (including snapshot pricing/tax fields).

2. Validation failure: stale estimate status (invalid state)
   1. User clicks “Promote to Work Order” but estimate status changed since load.
   2. Backend returns 400/409 with an error code indicating invalid state.
   3. UI shows a blocking error modal explaining promotion cannot proceed due to estimate state.
   4. After dismissal, UI refreshes/reloads the Estimate to show current status and re-enables Promote (if applicable).

3. Concurrency/already promoted
   1. User clicks “Promote to Work Order” and backend indicates the estimate was already promoted.
   2. If response includes an existing workOrderId (or a field that is a workOrderId):
      1. UI navigates to that Work Order.
      2. Work Order screen shows informational banner: “Opened existing work order.”
   3. Else (no workOrderId provided):
      1. UI shows blocking error: “Estimate already promoted.”
      2. UI remains on the Estimate screen and re-enables Promote after dismissal.

4. Blocking failure: missing/invalid tax configuration
   1. User attempts to promote an Approved Estimate.
   2. Backend responds with standard error envelope including an error code and message indicating tax configuration is missing/invalid.
   3. UI shows a blocking error modal stating tax configuration must be corrected.
   4. UI remains on the Estimate screen; after dismissal, Promote is re-enabled.
   5. UI displays the backend error code in the error details section.

## Notes
- Promotion is only available when Estimate status is Approved and user has permission; otherwise hide or disable the Promote action with a brief reason.
- Must include Idempotency-Key header on promotion request; UI should generate a unique key per click attempt.
- Work Order Items must support decimal pricing (do not assume integers) and include traceability to estimate items plus snapshot pricing/tax fields (even if catalog/product is invalid later).
- Tax configuration is a backend validation dependency; UI should treat failures as blocking and show the backend error code in details.
- Work Order status taxonomy is separate from estimate/workexec; promotion does not modify workexec status.
- Ensure error handling supports 400/409 invalid state and “already promoted” outcomes distinctly, per backend error codes/fields.
