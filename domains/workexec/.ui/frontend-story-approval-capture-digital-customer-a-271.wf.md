# [FRONTEND] [STORY] Approval: Capture Digital Customer Approval
## Purpose
Enable Service Advisors to capture a customer’s digital signature and submit an approval for either a Work Order or an Estimate. Provide a consistent approval capture screen that validates a non-empty signature, posts to the correct canonical approval endpoint, and refreshes the entity detail to reflect approved status. Handle common API error states (validation, permission, not found, conflict, network) with clear guidance and safe retry behavior.

## Components
- Page header with title “Capture Digital Approval” and back navigation
- Entity summary panel (type + key display fields: id, status, optional summary fields)
- Signature capture canvas (“Digital Signature”)
- Optional signature metadata capture (stroke points array; hidden/behind-the-scenes unless needed for debugging)
- Inline validation message area (e.g., “Signature is required.”)
- Submit button (“Submit Approval”)
- Secondary actions: “Clear signature”, “Cancel/Back”
- Status/alert banners for API responses (400/403/404/409/5xx)
- Conflict resolution prompt with “Refresh status” action
- Permission error details expander/area (shows backend message when provided)
- Success confirmation (banner/toast) and auto-refresh/redirect behavior

## Layout
- Top: Header (Back link) + page title
- Main: Entity summary card (Entity type + ID + Status + optional summary fields)
- Main: Signature section (label “Digital Signature” + large signature canvas) + “Clear signature”
- Bottom/footer: Validation/error banner area + actions row (Cancel/Back left, Submit Approval right)

## Interaction Flow
1. Entry/navigation
   1. User opens “Capture Digital Approval” from a Work Order or Estimate detail/edit screen.
   2. Route params include `entityType` (enum), `entityId` (opaque string), and optional `backLink` (if absent, compute default back navigation to the entity detail screen).
   3. Screen loads entity detail (Work Order: GET /v1/workorders/{workorderId}; Estimate: GET /v1/workorders/estimates/{estimateId}) to display summary and current status.
2. Capture and submit (Work Order success)
   1. User draws a non-empty signature in the canvas.
   2. “Submit Approval” becomes enabled once signature is non-empty.
   3. On submit, POST to /v1/workorders/{workorderId}/approval with required header and payload including signature string and required approval fields (per contract).
   4. Show success confirmation.
   5. Refresh Work Order detail and display approved status plus audit data (timestamp/approvalId if available; method label “Digital Signature”).
3. Capture and submit (Estimate success)
   1. User draws a non-empty signature and submits.
   2. POST to /v1/workorders/estimates/{estimateId}/approval.
   3. Refresh Estimate detail and display approved status plus audit data (timestamp/approvalId if available; method label “Digital Signature”).
4. Prevent empty signature submission
   1. If signature area is empty, keep “Submit Approval” disabled.
   2. If user attempts submission via other means, show inline error “Signature is required.” and highlight the signature area.
5. Error handling (API-driven)
   1. 400 Validation: show validation message; if signature-related, highlight signature and keep submit disabled until corrected.
   2. 403 Forbidden: show permission error, disable submit; show backend error message (when provided) in a details area for support; do not mark local state as approved.
   3. 404 Not found: show not found message and provide back navigation to computed/explicit back link.
   4. 409 Conflict (invalid state): show conflict message indicating entity is no longer awaiting approval; offer “Refresh status”.
      1. On “Refresh status”, re-fetch entity detail.
      2. If no longer eligible, return to entity detail screen.
   5. 5xx/network: show retry option; on retry reuse the same signature string for the same attempt (do not force re-sign unless user clears).
6. Optional: View existing approval (if API provides)
   1. If approval read endpoint is available (e.g., GET /v1/workorders/estimates/{estimateId}/approval), optionally display existing approval audit info and prevent duplicate submission when already approved.

## Notes
- Entity types supported: Work Order and Estimate; route param `entityType` determines which detail and approval endpoints to use.
- Eligibility: Only DRAFT estimates are eligible for approval; statuses include DRAFT, APPROVED, DECLINED, EXPIRED (no PENDING_APPROVAL). Work Order eligible statuses are implied by backend; UI must handle 409 when state changes.
- Signature capture requirements: signature string is required; optional stroke points array may be captured if backend requires (each point includes x, y, t; confirm whether `t` is epoch or relative to capture start).
- Concurrency token fields exist but are unspecified; do not block implementation—handle 409 with refresh flow.
- User-visible audit data after success: approved status, approval timestamp (if available), approvalId (if available), and method label “Digital Signature”.
- UX constraints: clear, accessible error banners; submit disabled on 403; provide safe back navigation even when `backLink` is absent.
- Tech constraints: implement in Vue 3 + TypeScript + Quasar; follow established workexec UI patterns for detail refresh and notifications.
