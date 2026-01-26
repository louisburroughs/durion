# [FRONTEND] [STORY] Estimate: Create Draft Estimate
## Purpose
Enable a Service Advisor to create a new Estimate in Draft state from customer/vehicle contexts or a global “New Estimate” entry point. The UI must collect Customer and Vehicle via selection flows (not raw IDs), validate required inputs, and handle backend errors without navigating away. On success, route the user into the Estimate Workspace for the newly created estimate and display key header summary details.

## Components
- Entry points
  - “Create Estimate” action in Customer detail context
  - “Create Estimate” action in Vehicle detail context
  - Global “New Estimate” action (prompts for Customer + Vehicle)
- Create Estimate Screen
  - Page header/title: “Create Estimate”
  - Customer selector (picker/navigation-based)
  - Vehicle selector (picker/navigation-based)
  - Inline validation messages (missing customer/vehicle)
  - Backend field error display (field-specific)
  - Global error banner/alert area (non-field errors)
  - Primary button: “Create Estimate”
  - Secondary action: “Cancel” / “Back” (if applicable)
  - Loading state on submit (spinner/disabled controls)
- Estimate Workspace Screen (existing, modified header)
  - Header summary block: estimate identifier (preferred display number; fallback internal id), status (Draft), customer summary, vehicle summary, created metadata (date/by)
  - Main workspace content area (existing)
- Error/empty states
  - Access denied message (403)
  - Record missing / re-select prompt (404)
  - Retry prompt for network/server errors
  - Conflict/duplicate handling message (409) if surfaced

## Layout
- Create Estimate Screen
  - Top: Page title + brief helper text (optional)
  - Main: Customer selector row (label + selected value + “Select/Change”)
  - Main: Vehicle selector row (label + selected value + “Select/Change”)
  - Main: Inline guidance under missing fields when disabled
  - Bottom: [Cancel/Back] (left) | [Create Estimate] (right, primary)
  - Top or inline: Error banner area for non-field errors
- Estimate Workspace Screen (header)
  - Top header: Identifier + Status (Draft) + Customer + Vehicle + Created metadata (when available)

## Interaction Flow
1. Entry: Customer detail → user clicks “Create Estimate”.
2. Entry: Vehicle detail → user clicks “Create Estimate”.
3. Entry: Global “New Estimate” → user is taken to Create Estimate Screen and must select both Customer and Vehicle.
4. On Create Estimate Screen, user selects Customer via picker/navigation; selected customer summary is shown in the field row.
5. User selects Vehicle via picker/navigation; selected vehicle summary is shown in the field row.
6. Client-side validation:
   1. If Customer missing: disable “Create Estimate”; show inline “Select a customer.”
   2. If Vehicle missing: disable “Create Estimate”; show inline “Select a vehicle.”
7. User clicks “Create Estimate”:
   1. UI enters loading state; prevent double-submit.
   2. Send create request with customerId and vehicleId.
   3. Include Idempotency-Key header; reuse the same attempt key on retry to prevent duplicates.
8. Success (201):
   1. Verify returned estimate status represents Draft; if Draft, route to Estimate Workspace for returned estimateId.
   2. In workspace header, display estimate identifier (preferred display number; fallback estimateId), status (Draft), and customer/vehicle summaries if available.
   3. Show created metadata (created date/by) when available from response or subsequent load.
9. Backend validation error (400):
   1. Show field-specific errors on relevant selectors/rows.
   2. Remain on Create Estimate Screen; allow corrections and resubmit (same idempotency key on retry).
10. Unauthorized (403):
   1. Show access denied message.
   2. Remain on Create Estimate Screen; do not navigate.
11. Not found (404) for referenced customer/vehicle:
   1. Show “record missing” message and indicate which selection is invalid if known.
   2. Allow user to re-select Customer and/or Vehicle; remain on screen.
12. Conflict/duplicate (409):
   1. Show conflict message; if idempotency collision, keep user on screen and allow retry (same attempt key).
13. Network/server error:
   1. Show retry option; do not navigate.
   2. On retry, reuse same idempotency key.

## Notes
- BR-UI-4: Newly created estimates must be Draft; if response status is not Draft, treat as an error and require user action (do not proceed into workspace as if valid).
- Customer and Vehicle inputs are required and must be selected via pickers/navigation (no raw ID entry).
- Workspace header visibility rules after creation (when available): identifier (preferred display number; fallback internal id), status (Draft), customer summary, vehicle summary, created date/by.
- Error handling requirements:
  - 400: field-specific errors; stay on screen.
  - 403: access denied; stay on screen.
  - 404: record missing; allow re-select.
  - 409: conflict/duplicate; idempotency prevents duplicates; reuse attempt key on retry.
  - Network/server: show retry; do not navigate.
- Ensure submit is disabled when required selections are missing; show inline guidance rather than only a banner.
- Loading state should prevent double-click and clearly indicate request in progress.
