# [FRONTEND] [STORY] Execution: Request Additional Work and Flag for Approval
## Purpose
Enable a Technician to create a Change Request from an in-progress Work Order to request additional services and/or parts, optionally flagging items as emergency/safety. The UI must collect a required description and at least one requested item, submit the request, and then navigate to a Change Request detail view showing the created request and items. The flow must support idempotent creation to prevent duplicate Change Requests on retry/timeouts.

## Components
- Page header: Work Order context (Work Order ID), page title “Create Change Request”
- Read-only context fields: Work Order ID (from route), optional link to Work Order
- Form field: Description (required, multiline)
- Requested items section
  - Tabs or segmented control: “Services” / “Parts” (optional; can be a single combined list with type selector)
  - Add Service line button
  - Add Part line button
  - Line item cards/rows list (repeatable)
- Service line item row
  - Service ID selector/input (required)
  - Emergency/Safety toggle (boolean)
  - Conditional fields when emergency/safety is true (e.g., reason/notes; optional URL/attachment link if required by rules)
  - Remove line action
- Part line item row
  - Part ID selector/input (required)
  - Quantity input (required, min 1; decimals only if supported)
  - Emergency/Safety toggle (boolean)
  - Conditional fields when emergency/safety is true (e.g., reason/notes; optional URL/attachment link if required by rules)
  - Remove line action
- Validation and inline error messages (field-level + form-level)
- Primary actions: Submit Change Request, Cancel/Back
- Submission state UI: loading indicator, disabled submit while posting
- Post-create navigation target: Change Request Detail view
- Change Request Detail view components
  - Header: Change Request ID, status badge (AWAITING_ADVISOR_REVIEW)
  - Read-only metadata: created at (UTC), created by (if available), Work Order ID
  - Description (read-only)
  - Requested items list with per-item status (PENDING_APPROVAL) and emergency/safety flags
  - Optional link to related Work Order Service/Part items (if viewer route exists)

## Layout
- Top: Breadcrumb/back to Work Order + page title
- Main (single column):
  - Work Order context (read-only)
  - Description field
  - Requested items list (service/part rows) + “Add Service” / “Add Part” controls
  - Footer actions: [Cancel] (left) … [Submit Change Request] (right)
- Detail view: Top header with ID + status; below: metadata + description; then items table/list

## Interaction Flow
1. Technician opens a Work Order in status IN_PROGRESS and selects “Request Additional Work” (entry point to Create Change Request).
2. UI loads Create Change Request form with Work Order ID prefilled/read-only (from route).
3. Technician enters a non-empty Description.
4. Technician adds at least one requested item:
   1. Click “Add Service line” → select/enter Service ID → optionally toggle Emergency/Safety and complete any conditional fields → save row.
   2. Or click “Add Part line” → select/enter Part ID + Quantity (min 1) → optionally toggle Emergency/Safety and complete any conditional fields → save row.
5. Validation on submit:
   1. Block submit if Description is empty.
   2. Block submit if no service/part lines exist.
   3. Block submit if any line is missing required fields (serviceId/partId/quantity; emergency conditional fields as applicable).
6. On Submit:
   1. UI sends Create Change Request request including an Idempotency-Key.
   2. Show loading state; disable inputs and submit button.
7. Success response:
   1. UI navigates to Change Request detail view for the returned Change Request ID.
   2. Detail view shows status AWAITING_ADVISOR_REVIEW and each requested item with status PENDING_APPROVAL.
8. Network timeout / retry (idempotency edge case):
   1. If the initial request times out, UI retries with the same Idempotency-Key.
   2. If server returns the same created Change Request ID, UI navigates to that single Change Request detail (no duplicates).
9. Cancel:
   1. Technician clicks Cancel/Back → return to Work Order view; warn about unsaved changes if form is dirty.

## Notes
- Acceptance criteria:
  - Create requires non-empty description and at least one requested service or part.
  - Created Change Request status: AWAITING_ADVISOR_REVIEW.
  - Created requested items status: PENDING_APPROVAL.
  - After create, navigate to Change Request detail showing request + items.
  - Idempotent create: retries with same Idempotency-Key must not create duplicates; UI must handle “same outcome” response and navigate to the single created request.
- Data/field constraints (as reflected in UI):
  - Work Order ID is required and read-only from route.
  - Change Request ID is read-only (display after create).
  - Emergency/Safety is a boolean per line; when true, show conditional required fields per backend rules (exact conditions TBD).
  - Part quantity: min 1; decimals only if backend supports (TBD).
- Read-only fields to display on detail view when available: createdAt (UTC), status, and any backend-provided metadata; keep approve/decline-related fields out of this story’s editable UI.
- TODOs:
  - Confirm exact emergency/safety conditional fields and when URL/notes become required.
  - Confirm whether service/part selectors are free-text IDs or searchable pickers.
  - Confirm quantity decimal support and input formatting rules.
  - Confirm whether a viewer route exists for linked service/part items; if so, render IDs as links.
