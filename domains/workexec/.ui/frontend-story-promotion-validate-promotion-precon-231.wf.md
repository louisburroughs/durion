# [FRONTEND] [STORY] Promotion: Validate Promotion Preconditions
## Purpose
Enable users to promote an Estimate to a Work Order only when required preconditions are met, using both client-derived gating and authoritative backend validation. Prevent confusing failures by showing clear disabled-state messaging and standardized error feedback when promotion is not allowed. Handle idempotent retries so duplicate promotions navigate users to the existing Work Order instead of showing a generic error.

## Components
- Page header: Estimate context (Estimate ID, status)
- Primary CTA button: “Promote to Work Order”
- Inline helper/disabled-state message area near CTA
- Loading indicator/spinner for promotion request
- Error panel/alert (standard error envelope rendering)
  - Error title/message
  - Optional details (correlation/request ID, field/metadata details when present)
  - Optional action button: “Open Work Order” (when already promoted / workOrderId available)
- (Optional) Secondary link/button: “View Work Order” (shown after success or idempotent detection)

## Layout
- Top: Estimate header (ID + status) and breadcrumb/back
- Main (right/top of main actions): Promote CTA + inline gating message beneath
- Main (content area): existing Estimate details (unchanged)
- Main (below actions): Error panel area reserved for promotion errors
- Inline sketch: Header | Actions (Promote CTA + message) | Content | Error Panel

## Interaction Flow
1. On Estimate load, compute “can attempt promotion” gating from derived fields:
   1) estimate loaded, 2) estimate status is eligible (approved), 3) snapshotVersion present, 4) not currently loading, 5) optional capability flag true (if present).
2. If status is not approved:
   1) Show “Promote to Work Order” disabled.
   2) Show message: “Estimate must be approved before promotion.”
3. If status is declined:
   1) Show “Promote to Work Order” disabled.
   2) Show message: “Declined estimates cannot be promoted.”
4. If gating conditions are met:
   1) Enable “Promote to Work Order”.
   2) On click, set loading state and call the promotion transition/service with required inputs (estimateId, snapshotVersion) and required header(s) (e.g., facilityId).
5. On success response:
   1) Receive workOrderId (and any other success fields).
   2) Redirect/navigate to Work Order detail page using workOrderId.
6. On error response (standard envelope):
   1) Stay on the Estimate page.
   2) Render error panel using envelope fields (type/code/message; include correlation/request ID when available; include details/metadata when provided).
   3) Clear loading state; keep CTA enabled/disabled based on current gating.
7. Idempotent retry / duplicate promotion scenario:
   1) If user clicks “Promote to Work Order” again for an already-promoted estimate and backend returns either:
      - Success with existing workOrderId, or
      - Error envelope indicating “already promoted” and includes workOrderId (when available),
   2) Do not show a generic failure.
   3) Show “Already promoted”.
   4) Navigate to the Work Order page immediately, or present an “Open Work Order” action that navigates using workOrderId.

## Notes
- Backend is authoritative; UI gating prevents obvious invalid attempts but must still handle server-side rejection via the standard error envelope.
- Promotion requires Estimate context inputs: estimateId (opaque string/id) and snapshotVersion; also requires a status enum for UI gating.
- Derived gating must consider loading state and presence of snapshotVersion; optionally respect a capability flag when present and true.
- Error panel must support optional fields (e.g., correlation/request ID, metadata/details object) and display them when available.
- Transition behavior: on success redirect to Work Order detail; on error remain on Estimate and show error panel.
- Acceptance criteria emphasis: disabled CTA with correct messages for not-approved and declined; idempotent retry must result in “Already promoted” and navigation to the existing Work Order (not a generic error).
