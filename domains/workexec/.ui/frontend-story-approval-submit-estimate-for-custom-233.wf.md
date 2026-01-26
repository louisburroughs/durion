# [FRONTEND] [STORY] Approval: Submit Estimate for Customer Approval
## Purpose
Enable a Service Advisor to submit a loaded estimate for customer approval from the estimate view. The UI must only show/enable “Submit for Approval” when the estimate is eligible (status, loaded, and user permission). On submission, the UI confirms intent, calls the backend transition, updates the displayed approval references, and removes the action to prevent re-submission.

## Components
- Page header with estimate identifier and current status badge
- Estimate summary panel (customer, totals, optional notes)
- Line items list/table (read-only in this story, but used for validation context)
- “Submit for Approval” primary action button (conditional visibility/enabled state)
- Eligibility/validation hint area (inline message when disabled, optional)
- Confirmation modal/dialog (submit confirmation; optional reason field if supported later)
- Loading state indicator (button spinner and/or page-level progress)
- Success toast/notification
- Error alert/banner with field-level error list (for 422) and generic fallback
- Approval references display (snapshot id/version and approvalRequestId) shown post-submit

## Layout
- Top: Header row (Estimate # / Status badge / actions aligned right)
- Main (two-column on desktop, single-column on mobile):
  - Left/Main: Line items list/table + any validation hint area
  - Right/Side: Summary card (customer contact info, totals, tax, notes) + “Submit for Approval” button
- Footer/Bottom: Error banner area (sticky or inline near actions)

## Interaction Flow
1. Load estimate view.
2. Evaluate “Submit for Approval” visibility/enabled:
   1. Hidden/disabled if no estimate loaded.
   2. Hidden/disabled unless estimate status is eligible (Draft per scenario; pending-approval equivalent after submit).
   3. Hidden/disabled unless user has submit permission (permission name TBD; treat as a boolean capability from backend/auth layer).
3. User clicks “Submit for Approval”.
4. Show confirmation modal:
   1. Display key context (estimate id, customer, total amount if available).
   2. Confirm and Cancel actions.
5. User confirms submission:
   1. Disable actions and show loading indicator.
   2. Send submit/transition request for the estimate (reason optional/not specified).
6. On success (200/201):
   1. Update estimate data in UI with returned payload.
   2. Reflect post-submit state:
      - If state model applies: estimate status remains DRAFT, but approval record is created; UI should still remove “Submit for Approval” if already submitted.
      - Otherwise: estimate status updates to PENDING_APPROVAL (or configured equivalent).
   3. Display approval references:
      - Approval snapshot id and/or version.
      - approvalRequestId.
   4. Remove/disable “Submit for Approval” action to prevent re-submission.
   5. Show success notification.
7. On validation failure (422):
   1. Show structured field-level errors (list) and keep user on the page.
   2. Highlight relevant sections when possible (e.g., line items, customer contact info).
8. On optimistic concurrency / already submitted (409):
   1. Show message indicating it was already submitted and refresh/reload estimate state if needed.
   2. Ensure “Submit for Approval” is not available afterward.
9. On permission denial (403):
   1. Show permission error and keep action hidden/disabled.
10. On unknown estimateId (404):
   1. Show not-found error state for the page (or redirect to safe location).
11. On any other error:
   1. Show generic error: “Could not submit estimate.”

## Notes
- Frontend stack: Vue 3 + TypeScript, Quasar components, Moqui backend integration; must be responsive and accessible (keyboard navigation for modal, ARIA labels, focus management).
- Enable/Disable rules:
  - Estimate must be loaded.
  - Estimate status must be eligible (Draft per scenario; confirm configured pending-approval equivalent).
  - User must have permission to submit (permission name unknown; implement as a configurable/returned capability flag).
- Minimum estimate fields needed by UI (as available): id, status, customer reference, optional display fields (e.g., number), totals (recommended for confirmation), notes, backend-provided timestamps/config-derived approval method, and post-submit fields for snapshot/version and approvalRequestId.
- Validation checklist (expect 422 with field-level errors):
  - At least 1 line item.
  - Valid taxCode on each item.
  - Non-negative totals.
  - Customer contact info (email/phone) required when approval method requires remote approval.
- Acceptance criteria (scenario): from Draft, confirm submit, backend request sent, approval references shown, and “Submit for Approval” no longer available after successful submission.
- TODOs:
  - Confirm exact eligible statuses and post-submit state model (estimate remains DRAFT vs transitions to PENDING_APPROVAL).
  - Confirm endpoint/request/response contract names and fields for snapshot/version and approvalRequestId.
  - Confirm permission identifier and how it is exposed to the frontend.
