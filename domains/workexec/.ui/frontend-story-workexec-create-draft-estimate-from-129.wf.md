# [FRONTEND] [STORY] Workexec: Create Draft Estimate from Appointment

## Purpose
Enable a Service Advisor to create a draft estimate directly from an appointment detail view using an idempotent “create-from-appointment” action. Prevent confusion by detecting when an estimate is already linked to the appointment and showing a “View Estimate” path instead of allowing duplicate creation. After creation, route the user to the estimate detail page and ensure the estimate is in DRAFT status.

## Components
- Appointment Detail header (appointment identifier, key summary fields)
- Linked Estimate status row (conditional)
  - Text: “Estimate: <estimateId>”
  - Button/link: “View Estimate”
- Primary CTA button: “Create Draft Estimate”
  - Disabled/hidden state when estimate already linked
  - Disabled state when required appointment context is missing (UI-only guard; backend enforces)
- Inline helper text / tooltip for disabled CTA (reason)
- Loading indicator (button spinner and/or page-level)
- Error banner/toast for mutation failures (e.g., APPOINTMENT_NOT_FOUND)
- Navigation action to Estimate Detail view (post-create)
- Estimate Detail view (read-only in this story)
  - Estimate header (estimateId, status)
  - Traceability fields (appointmentId reference)
  - Customer/vehicle summary (populated from appointment)
  - Line items/services list (populated/suggested per fixtures/notes)

## Layout
- Top: Appointment Detail header (title + key appointment metadata)
- Main content:
  - Row: Linked Estimate (if known) → “Estimate: <id>” (left) | “View Estimate” (right)
  - Row: Primary CTA area → “Create Draft Estimate” button (right-aligned or prominent)
  - Area: Inline messages (disabled reason, errors)
- Footer/secondary: (optional) audit/traceability snippet (appointmentId, customerId/vehicleId if present)

## Interaction Flow
1. Load Appointment Detail view.
2. Determine whether a linked estimate is known (from appointment payload or link lookup).
3. If linked estimate exists:
   1. Show “Estimate: <estimateId>” and “View Estimate”.
   2. Hide or disable “Create Draft Estimate” to avoid duplicate creation attempts.
   3. On “View Estimate”, navigate to the estimate detail route for that estimateId.
4. If no linked estimate exists:
   1. Show enabled “Create Draft Estimate” if appointmentId is present and user has capability.
   2. If required UI context is missing (e.g., appointmentId absent), disable CTA and show brief reason (backend still enforces).
5. Happy path creation:
   1. User clicks “Create Draft Estimate”.
   2. UI sends create-from-appointment mutation with:
      1. Header: Idempotency-Key (required)
      2. Input: appointmentId (required; no other appointment fields unless explicitly required)
   3. Show loading state; prevent double-click while request in-flight.
   4. On success response containing estimateId (and status expected to be DRAFT):
      1. Navigate to estimate detail route for that estimateId.
      2. Estimate detail loads and displays status = DRAFT.
6. Retry/idempotency behavior:
   1. If user retries with same idempotency key (e.g., due to transient failure), server returns same outcome; UI should handle as success and navigate to the returned estimateId.
   2. If response includes optional `created` boolean, UI may optionally tailor messaging (e.g., “Draft estimate created” vs “Existing draft opened”) without relying on HTTP status.
7. Error handling:
   1. If APPOINTMENT_NOT_FOUND (invalid appointment), show error banner/toast and keep user on appointment view with CTA available (unless appointment context is missing).
   2. For generic errors/timeouts, show error and allow retry (new idempotency key per new attempt unless intentionally retrying same request).

## Notes
- Visibility rule: when a linked estimate is known, show “Estimate: <estimateId>” + “View Estimate” and hide/disable “Create Draft Estimate” (server-side idempotency still protects).
- Appointment is read-only in this story; do not send client-supplied appointment fields beyond appointmentId to avoid system-of-record drift.
- Mutation contract:
  - Requires Idempotency-Key header.
  - Input requires appointmentId.
  - Output minimum: estimateId and status (must be DRAFT after create); optional `created` boolean recommended.
- Test fixtures/expectations:
  - Create-from-appointment populates customer and items from appointment details.
  - Estimate is linked back to appointment for traceability.
  - Estimate can be converted to workorder later (out of scope here; ensure no UI blocks future flow).
- Frontend UI placement: “Create Estimate” button in appointment detail view; keep copy consistent with story (“Create Draft Estimate”).
- Ensure capability gating: only users with permission to create estimates from appointments see/can use the CTA.
- TODO (implementation): decide whether linked-estimate detection uses appointment payload field vs separate lookup; ensure consistent loading states to avoid flicker (button briefly enabled before link known).
