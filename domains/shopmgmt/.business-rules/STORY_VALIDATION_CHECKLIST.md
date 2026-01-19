# Story Validation Checklist for `shopmgmt` Domain

This checklist is intended for engineers and reviewers to validate story implementations within the `shopmgmt` domain, updated to cover new frontend stories for: **Show Assignment**, **Reschedule Appointment**, and **Create Appointment from Estimate/Work Order**.

---

## Scope/Ownership

- [ ] Verify the story behavior is within `shopmgmt` domain boundaries (appointments, scheduling, assignment visibility) and does not implement WorkExec/HR/Notification business logic in the frontend.
- [ ] Confirm the UI is implemented in the correct Moqui screens/routes for this repo (Appointment Detail, Appointment Create, Appointment Reschedule, Estimate Detail, Work Order Detail).
- [ ] Verify entry points are permission-gated (buttons/links hidden or disabled when permission info is available) and still enforced by backend on call.
- [ ] Confirm facility scoping is consistently applied across all screens and service calls (no cross-facility data display).
- [ ] Validate out-of-scope items are not accidentally implemented (e.g., assignment decision logic, HR profile management, notification template management).
- [ ] Confirm degraded-mode UX is implemented where dependencies may fail (People/HR identity lookup, push updates, conflict check).

---

## Data Model & Validation

- [ ] Verify `appointmentId`, `sourceType`, `sourceId`, and datetime fields are validated for presence and type before calling backend services.
- [ ] Verify `scheduledStartDateTime` / `newScheduledDateTime` is submitted in an agreed ISO-8601 format including timezone/offset (no ambiguous local-only timestamps).
- [ ] Verify reschedule reason is required and constrained to the backend-supported enum values (no free-form reason codes).
- [ ] Verify reschedule notes are required when `reason=OTHER` (client-side) and still validated server-side.
- [ ] Verify override reason is required when overriding hard conflicts or policy blocks (client-side) and still validated server-side.
- [ ] Verify assignment notes (if editing is enabled) enforce max length **500 characters** client-side and handle server-side validation errors.
- [ ] Verify the UI never allows editing immutable links (estimateId/workOrderId) during reschedule or create flows.
- [ ] Verify “unassigned”/partial assignment states render safely when any of bay/mobile/mechanic fields are null/missing.
- [ ] Verify mobile GPS staleness state is derived only from backend-provided timestamps (do not infer location validity without `lastUpdatedAt`).
- [ ] Verify conflict results are rendered using backend-provided severity (`HARD` vs `SOFT`) and overridable flags (do not reclassify in UI).
- [ ] Verify “source already linked to active appointment” is handled as a distinct error state and (if provided) links to the existing appointment.

---

## API Contract

- [ ] Verify exact Moqui service names/routes are implemented for:
  - [ ] load `AssignmentView` by `appointmentId`
  - [ ] load appointment snapshot for reschedule
  - [ ] conflict check for reschedule (if separate)
  - [ ] submit reschedule
  - [ ] load source defaults/eligibility for create
  - [ ] conflict check for create (if separate)
  - [ ] create appointment from source
- [ ] Verify request/response schemas match the implemented UI model (field names, nullability, enums, timestamp formats).
- [ ] Verify error mapping is consistent and actionable:
  - [ ] 400 validation → field-level errors (datetime, reason, notes, overrideReason)
  - [ ] 401/403 → permission banner and disable/hide actions
  - [ ] 404 → navigate back or show “not found” state without leaking identifiers
  - [ ] 409 → conflict/concurrency handling (reload prompt, show conflicts, or version mismatch messaging)
  - [ ] 5xx/timeout → retry banner; preserve user inputs
- [ ] Verify facility scoping contract is honored (explicit `facilityId` param vs inferred from session/context) and is consistent across all calls.
- [ ] Verify People/HR mechanic identity lookup contract is implemented only if required (i.e., only call HR if `AssignmentView` lacks display fields).
- [ ] Verify create/reschedule calls support idempotency if the backend supports it (clientRequestId/idempotency key) and retries do not create duplicates.
- [ ] Verify conflict-check UX matches backend contract:
  - [ ] if conflict check is required before submit, UI enforces it (token/version if required)
  - [ ] if conflict check is optional/embedded, UI handles conflicts returned on submit without losing form state

---

## Events & Idempotency

- [ ] Verify near-real-time assignment updates use the agreed mechanism (WebSocket/SSE) and subscribe using the correct identifiers (appointmentId/facilityId/topic).
- [ ] Verify polling fallback is implemented when push is unavailable, fails, or disconnects (poll interval per story: **30s** while screen visible).
- [ ] Verify assignment update handling is idempotent:
  - [ ] ignore duplicate events using `eventId` or `version`/`lastUpdatedAt` when provided
  - [ ] do not regress UI state when out-of-order updates arrive
- [ ] Verify manual “Refresh assignment” triggers a single in-flight request (debounce/lock to prevent request storms).
- [ ] Verify reschedule/create submissions are safe on retry:
  - [ ] UI prevents double-submit (disable button while submitting)
  - [ ] if backend supports idempotency keys, UI reuses the same key for retries of the same user action
- [ ] Verify concurrency/version mismatch (409) is handled for editable fields (notes edit and/or reschedule if versioned):
  - [ ] UI prompts reload and preserves user-entered inputs where possible

---

## Security

- [ ] Verify backend authorization is enforced for all operations regardless of UI gating:
  - [ ] `VIEW_ASSIGNMENTS` for assignment display
  - [ ] `EDIT_ASSIGNMENT_NOTES` for notes editing (if enabled)
  - [ ] `RESCHEDULE_APPOINTMENT` for reschedule
  - [ ] `CREATE_APPOINTMENT` for create
  - [ ] `OVERRIDE_SCHEDULING_CONFLICT` for hard conflict override
  - [ ] `APPROVE_RESCHEDULE` / `APPROVE_DURATION_OVERRIDE` if policy exceptions are supported
- [ ] Verify facility scoping prevents cross-facility access (backend must validate; UI must not cache across facility context).
- [ ] Verify CANCELLED appointment assignment visibility is enforced:
  - [ ] UI hides assignment for CANCELLED unless user has `VIEW_CANCELLED_APPOINTMENTS` (if status is available client-side)
  - [ ] backend still enforces regardless of UI knowledge
- [ ] Verify no PII is logged client-side (customer names, phone/email, addresses, precise GPS coordinates unless explicitly allowed).
- [ ] Verify mechanic identity display is minimal and policy-compliant (e.g., displayName/photo only if authorized and provided).
- [ ] Verify override reasons and reschedule notes are treated as potentially sensitive:
  - [ ] do not log full free-text values in frontend logs
  - [ ] do not echo server error payloads that may contain sensitive details
- [ ] Verify input handling prevents injection in rendered fields (notes, messages): escape/encode output; no raw HTML rendering.

---

## Observability

- [ ] Verify frontend propagates correlation/request IDs if supported by repo conventions (e.g., `X-Request-Id`) for all service calls.
- [ ] Verify key user actions are observable (client-side logs/telemetry without PII):
  - [ ] opened Appointment Detail (appointmentId only)
  - [ ] assignment load success/failure
  - [ ] push subscribe success/failure and fallback-to-polling activation
  - [ ] conflict check invoked and result counts (hard/soft)
  - [ ] create submit success/failure (appointmentId on success)
  - [ ] reschedule submit success/failure (appointmentId)
- [ ] Verify UI surfaces partial-success outcomes when backend indicates them (e.g., reschedule succeeded but notifications failed/queued).
- [ ] Verify assignment “updated” banner/toast is shown when data changes while viewing and is accessible (ARIA live region).

---

## Performance & Failure Modes

- [ ] Verify assignment section loads asynchronously and does not block Appointment Detail rendering (skeleton/loading state).
- [ ] Verify polling runs only while the relevant screen is visible and stops on navigation/unmount.
- [ ] Verify push reconnect/backoff behavior does not cause rapid reconnect loops.
- [ ] Verify conflict check calls are debounced if triggered by datetime changes (avoid calling on every keystroke).
- [ ] Verify the UI handles backend outages gracefully:
  - [ ] shopmgmt unavailable → show retry; keep last known assignment if already loaded
  - [ ] People/HR unavailable → show mechanicId-only fallback with warning
  - [ ] notification/workexec degradation → show reschedule/create success with degraded warning if backend indicates partial failure
- [ ] Verify “Create” and “Reschedule” forms preserve user input on transient failures (timeouts/5xx).
- [ ] Verify the UI prevents request storms:
  - [ ] single in-flight conflict check per form
  - [ ] single in-flight submit per action
  - [ ] manual refresh throttled

---

## Testing

- [ ] Unit tests: validate client-side rules
  - [ ] required datetime/reason fields
  - [ ] notes required for OTHER and for override
  - [ ] assignment notes max length 500 (if editing enabled)
  - [ ] disable submit while submitting
- [ ] Integration tests (mock backend): API contract and error mapping
  - [ ] 400 field errors map correctly
  - [ ] 403 hides/disables actions and shows permission message
  - [ ] 404 navigates back or shows not-found state
  - [ ] 409 conflict payload renders hard/soft conflicts and override path
  - [ ] 409 version mismatch reload flow (notes edit and/or reschedule if applicable)
- [ ] UI tests: assignment display states
  - [ ] BAY assignment renders bay + mechanic
  - [ ] MOBILE_UNIT renders staleness warnings at >30m and >60m based on `lastUpdatedAt`
  - [ ] UNASSIGNED/partial assignment renders safe empty states
- [ ] UI tests: near-real-time updates
  - [ ] push event updates assignment view and shows “updated” banner
  - [ ] polling fallback updates within 30s when push unavailable
- [ ] UI tests: create appointment flow
  - [ ] ineligible source blocks submit with reason
  - [ ] soft conflicts allow create with warning
  - [ ] hard conflicts block without override permission
  - [ ] hard conflicts allow override with permission + required reason
  - [ ] “source already linked” shows link to existing appointment when provided
- [ ] UI tests: reschedule flow
  - [ ] not-reschedulable state disables/hides reschedule action (when state known)
  - [ ] notification failure surfaced without losing reschedule success
- [ ] Accessibility tests
  - [ ] errors announced and focus moves to first error on submit
  - [ ] banners/toasts use ARIA live region
  - [ ] keyboard navigation works for forms and conflict lists

---

## Documentation

- [ ] Document the implemented Moqui screens/routes and required parameters (`appointmentId`, `sourceType`, `sourceId`, facility context).
- [ ] Document the exact backend service contracts used (service names, request/response fields, error shapes).
- [ ] Document permission requirements per UI action and what the UI does when permission info is unknown client-side.
- [ ] Document real-time update mechanism (push vs polling fallback), subscription identifiers, and expected payload shape.
- [ ] Document timezone handling for datetime input/display and the chosen source of truth.
- [ ] Document idempotency behavior for create/reschedule (clientRequestId/header name if used) and retry guidance.
- [ ] Document degraded-mode behaviors (HR unavailable, shopmgmt unavailable, partial notification failures).
- [ ] Ensure documentation contains no secrets and no PII.

---

## Open Questions to Resolve

- [ ] What are the exact Moqui-accessible endpoints/services (names/routes) and schemas for:
  - [ ] loading `AssignmentView` by `appointmentId`
  - [ ] loading mechanic identity (embedded vs People/HR call)
  - [ ] loading appointment snapshot for reschedule
  - [ ] conflict checking for reschedule and create
  - [ ] submitting reschedule
  - [ ] loading defaults/eligibility for create from estimate/work order
  - [ ] creating appointment from source
- [ ] What is the real-time update mechanism for assignment updates (WebSocket vs SSE), and what are:
  - [ ] channel/topic naming conventions
  - [ ] subscription identifiers (appointmentId? facilityId?)
  - [ ] event payload shape (full `AssignmentView` vs delta) and ordering/idempotency fields (`eventId`, `version`, `lastUpdatedAt`)?
- [ ] Is `facilityId` required explicitly in requests, or inferred from session/context in Moqui services?
- [ ] Is appointment status reliably available on Appointment Detail for gating CANCELLED visibility, or must backend enforce entirely?
- [ ] Should assignment notes editing be included now or remain read-only?
  - [ ] If editable: what is the endpoint/service, payload, and validation rules?
  - [ ] Is optimistic concurrency required (`version`/ETag) and does backend return 409 on mismatch?
  - [ ] Is an edit “reason” required for notes updates?
- [ ] What is the timezone source of truth for scheduling input/display (facility timezone vs user profile vs browser), and is it returned in payloads?
- [ ] For reschedule: is conflict checking a separate call or only returned on submit (409 with conflict payload)?
  - [ ] If separate: is a conflictCheckToken/version required on submit?
- [ ] For reschedule: do `notifyCustomer` / `notifyMechanic` toggles exist, what are defaults, and when are they forced by backend policy?
- [ ] For reschedule policy exceptions (minimum notice, max reschedules): is approval represented purely by permission (`APPROVE_RESCHEDULE`) or is an explicit approver workflow/identity required?
- [ ] For create: are `durationMinutes`, `requiredSkills`, `bayTypeRequired`, and `isMobile` editable or strictly derived?
  - [ ] If duration editable: what override ranges and what permissions/reason requirements apply?
- [ ] What are the exact eligible status enum values for estimates and work orders for the “Create Appointment” entry point?
- [ ] Should the frontend generate and send a clientRequestId/idempotency key for create/reschedule? If yes, what field/header name?
- [ ] How should facility operating hours be surfaced when backend blocks scheduling outside hours (message only vs show hours/next available)?
- [ ] What fields are returned in `AppointmentRef` needed for confirmation (appointment number, scheduled time display, assignment summary)?
- [ ] Is there an API to retrieve audit entries for an appointment, and what audit fields are allowed to display (PII constraints)?

--- 

*End of checklist.*
