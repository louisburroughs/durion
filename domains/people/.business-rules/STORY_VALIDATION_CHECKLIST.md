# Story Validation Checklist for Domain: People

This checklist is intended for engineers and reviewers to validate story implementations within the **people** domain. It is updated to incorporate validation needs from the latest **frontend stories** (timekeeping entry visibility, mechanic roster read model, discrepancy report, manager approvals, break start/end, person-location assignments, employee profile CRUD, role assignments, and user disable flows).

---

## Scope/Ownership
- [ ] Verify the feature belongs to the **People** domain UI (People/HR, Timekeeping, Users) and does not implement WorkExec/ShopMgr business logic locally.
- [ ] Confirm the primary persona(s) and screens match the story scope (Payroll Clerk, Manager, Mechanic, HR Admin, Dispatch Manager).
- [ ] Verify read-only screens (ingested timekeeping entries, mechanic roster, discrepancy report) do **not** expose edit/mutation controls.
- [ ] Verify mutation screens (employee profile CRUD, approvals/rejections, breaks, assignments, disable user, role assignments) only perform actions explicitly in scope and via backend contracts.
- [ ] Confirm navigation/menu placement follows repo conventions and does not create duplicate/competing entry points (e.g., People vs POS vs Dispatch modules).
- [ ] Confirm cross-entity lookups (e.g., showing employee name for employeeId) are treated as explicit dependencies and not implemented via ad-hoc joins in the UI.

---

## Data Model & Validation
- [ ] Verify all UI forms enforce required fields before submit and still handle backend validation errors (400) without losing user input.
- [ ] Verify date/time range validation is enforced client-side where applicable:
  - [ ] `fromDateTime <= thruDateTime` for TimekeepingEntry list filters.
  - [ ] `startDate <= endDate` for discrepancy report filters.
  - [ ] `effectiveStartAt <= effectiveEndAt` for assignments.
  - [ ] `terminationDate >= hireDate` for employee profile.
- [ ] Verify enum fields are validated against authoritative values (prefer backend-provided lists when available):
  - [ ] `TimekeepingEntry.approvalStatus` (`PENDING_APPROVAL|APPROVED|REJECTED` at minimum).
  - [ ] `Mechanic.status` (`ACTIVE|INACTIVE|ON_LEAVE`).
  - [ ] `TimeEntry.status` (`DRAFT|SUBMITTED|PENDING_APPROVAL|APPROVED`) in manager approvals view.
  - [ ] `Break.breakType` (`MEAL|REST|OTHER`) and break state (`IN_PROGRESS|COMPLETED`) if returned.
  - [ ] `EmployeeProfile.status` (`ACTIVE|ON_LEAVE|SUSPENDED|TERMINATED`) and any backend-required statuses.
- [ ] Verify UI does not compute authoritative business rules that belong to backend (e.g., primary demotion, overlap detection, approval eligibility); it should display backend outcomes after refresh.
- [ ] Verify “primary” semantics for `PersonLocationAssignment` are displayed correctly and reflect backend results after create/update (including demotion behavior).
- [ ] Verify assignment “active/scheduled/ended” display logic matches backend definition (especially effective end inclusivity once clarified).
- [ ] Verify duplicate semantics are handled as **UI-visible outcomes**:
  - [ ] TimekeepingEntry list does not show duplicates for the same source key (backend dedupe).
  - [ ] Employee profile create/update shows 409 conflicts as blocking and warnings[] as non-blocking.
- [ ] Verify identifier handling:
  - [ ] Route params are validated (UUID/string format as expected) and invalid IDs do not trigger data leakage.
  - [ ] Mechanic detail route uses the correct stable identifier (`personId` vs `mechanicId`) consistently across list/detail.
- [ ] Verify phone/email normalization rules are applied safely:
  - [ ] Trim whitespace for all text inputs.
  - [ ] Lowercase emails before submit (if allowed by backend).
  - [ ] Do not “guess” phone formatting beyond safe normalization unless a standard library/pattern is mandated.

---

## API Contract
- [ ] Verify exact endpoints/services used match the agreed Moqui integration pattern (service-call vs REST) and are consistent across People screens.
- [ ] Verify list endpoints support paging and filtering as required:
  - [ ] TimekeepingEntry list supports `pageIndex/pageSize` (or `page/pageSize`) and filters (employeeId, approvalStatus, date range, locationId, workOrderId).
  - [ ] Mechanic roster list supports search (`q`), status filter, skill filter, paging, and sort.
- [ ] Verify detail endpoints return all fields required for display without additional client-side inference:
  - [ ] TimekeepingEntry detail includes source metadata (sourceSystem, sourceSessionId) and approval status.
  - [ ] Mechanic detail includes skills snapshot.
- [ ] Verify report endpoint contract is implemented exactly:
  - [ ] `GET /api/people/reports/attendance-jobtime-discrepancy` accepts required params (startDate, endDate, timezone).
  - [ ] Query serialization for `technicianIds` matches backend expectation (repeated vs comma-separated vs JSON string).
- [ ] Verify manager approvals contracts:
  - [ ] Employee picker endpoint returns only manager-authorized employees (server-enforced).
  - [ ] Time periods endpoint returns status fields needed for gating.
  - [ ] Approve/reject endpoints are period-atomic and return consistent success payloads (or UI re-fetches after 200).
- [ ] Verify break contracts:
  - [ ] “Current context” endpoint returns `isClockedIn`, `activeBreak`, and `breaksToday[]` (or equivalent).
  - [ ] Start break accepts `breakType` (+ notes if supported).
  - [ ] End break does not require client to guess identifiers if backend can derive from authenticated user/session.
- [ ] Verify assignment contracts:
  - [ ] Location picker endpoint returns active locations for selection.
  - [ ] Create/update/end assignment endpoints return updated assignment state (including demoted primary if applicable) or UI re-fetches.
- [ ] Verify employee profile CRUD contracts:
  - [ ] Create returns 201 and includes new `employeeId`.
  - [ ] Update returns 200 and includes updated audit fields.
  - [ ] Edit screen has an authoritative “get employee” endpoint/schema.
- [ ] Verify error handling is consistent and mapped to UI states:
  - [ ] 401 → login flow (per app convention).
  - [ ] 403 → access denied without leaking resource existence.
  - [ ] 404 → not found state for detail routes.
  - [ ] 409 → conflict banner with actionable next step (refresh/retry) and any blocking IDs if provided.
  - [ ] 5xx/timeout → retryable error state.

---

## Events & Idempotency
- [ ] Verify UI does not assume event delivery semantics; it only displays backend state (especially for ingested sessions and HR-synced roster).
- [ ] Verify idempotent action patterns where applicable:
  - [ ] Approve/reject period actions can be safely retried (no duplicate approvals) and UI handles repeated success responses.
  - [ ] Break start/end actions handle “already started/ended elsewhere” conflicts by refreshing context.
- [ ] Verify UI supports displaying stable source identifiers for traceability:
  - [ ] TimekeepingEntry shows `sourceSystem` + `sourceSessionId` (and ingestion event id if provided).
- [ ] Verify UI handles backend-provided requestId/correlationId fields (if present) by surfacing them in error details for support (without logging sensitive payloads).

---

## Security
- [ ] Verify every screen and action is gated by the correct permission/role checks (server-enforced; client hides menu entries where capability info exists):
  - [ ] View timekeeping entries (Payroll Clerk).
  - [ ] View mechanic roster (Dispatch/Shop Manager).
  - [ ] Run discrepancy report (Manager).
  - [ ] Approve/reject time entries (Manager with `timekeeping:approve` or equivalent).
  - [ ] Record breaks (Mechanic self-service).
  - [ ] Manage person-location assignments (Manager/admin).
  - [ ] Create/update employee profiles (HR Admin).
  - [ ] Manage role assignments (Admin).
  - [ ] Disable user (Admin/security).
- [ ] Verify unauthorized users cannot infer existence of resources via error differences (e.g., return generic 403 handling before showing “not found” details when appropriate).
- [ ] Verify sensitive fields are handled per policy:
  - [ ] Do not display `tenantId` unless explicitly allowed.
  - [ ] Do not log PII (names, emails, phone numbers) in client logs; prefer IDs and counts.
- [ ] Verify confirmation UX for destructive/privileged actions meets policy:
  - [ ] Disable user requires explicit confirmation and captures required reason codes if mandated.
- [ ] Verify status-based restrictions are enforced:
  - [ ] Approved time entries are read-only in manager view.
  - [ ] Disabled/terminated users cannot be acted on in ways prohibited by backend (UI must handle 403/409 gracefully).

---

## Observability
- [ ] Verify frontend includes correlation/request IDs in outbound requests if the repo has a standard mechanism (headers) and propagates them to error UI when present.
- [ ] Verify client-side logging (if enabled in this repo) is structured and minimal:
  - [ ] Logs include operation name, HTTP status, and non-PII identifiers (employeeId/personId/timePeriodId).
  - [ ] Logs do not include full payloads, notes fields, or rejection notes.
- [ ] Verify error banners include enough context for support without exposing sensitive data:
  - [ ] Show correlationId/requestId in expandable “Details” section when available.
- [ ] Verify audit visibility requirements are met where requested:
  - [ ] TimekeepingEntry detail shows ingestion timestamps and approval history if provided.
  - [ ] Approvals screen shows decision history (TimePeriodApproval) including processedAt and reason metadata for rejection.
  - [ ] Assignments show created/updated metadata and version if provided.
  - [ ] Mechanic detail shows lastSyncedAt/version if provided.

---

## Performance & Failure Modes
- [ ] Verify list/report screens use paging/virtualization to avoid rendering stalls (e.g., default pageSize=25; virtual scroll for large tables if standard).
- [ ] Verify “Run Report” and other actions are disabled while loading to prevent duplicate requests.
- [ ] Verify UI preserves last successful results when a subsequent report run fails (per discrepancy report story).
- [ ] Verify conflict handling triggers a refresh path:
  - [ ] 409 on approve/reject prompts refresh and recomputes eligibility.
  - [ ] 409 on break start/end refreshes current context.
  - [ ] 409 on assignment update indicates version mismatch and offers reload.
- [ ] Verify upstream dependency failures are surfaced clearly:
  - [ ] Discrepancy report shows a specific message when backend indicates WorkExec dependency unavailable (if errorCode provided).
- [ ] Verify timeouts/network failures show retry affordances and do not leave UI in inconsistent state.

---

## Testing
- [ ] Unit tests cover client-side validation rules (date ranges, required fields, enum handling).
- [ ] Component tests verify loading/empty/error states for each screen (list/detail/report/forms).
- [ ] Integration/API-mocking tests verify correct query serialization:
  - [ ] Paging params and filters for TimekeepingEntry list.
  - [ ] `technicianIds` encoding for discrepancy report.
  - [ ] Status filters and search for mechanic roster.
- [ ] Tests verify authorization handling:
  - [ ] Menu entry hidden (if capability map exists) and direct route shows access denied.
  - [ ] 403 does not leak resource existence.
- [ ] Tests verify conflict/idempotency behavior:
  - [ ] Approve/reject retry does not duplicate UI history entries.
  - [ ] Break start/end handles “already in progress/no active break” responses.
  - [ ] Assignment update handles version conflict and refresh.
- [ ] Tests verify duplicate warning/conflict UX for employee profile:
  - [ ] warnings[] displayed non-blocking after success.
  - [ ] 409 conflict blocks save and highlights relevant fields when possible.
- [ ] Accessibility checks:
  - [ ] Dialog focus trap (reject dialog, start break modal).
  - [ ] Error messages announced (ARIA live) where Quasar patterns support it.
  - [ ] Tables have accessible headers and keyboard navigation.

---

## Documentation
- [ ] Document the final endpoint/service names used for each People screen (list/detail/actions) in the repo (README/module doc).
- [ ] Document required permissions/roles for each screen and action (including view vs manage distinctions).
- [ ] Document timezone handling rules used across People/Timekeeping screens (display vs submit).
- [ ] Document error payload shape assumptions and mapping strategy (fieldErrors, errorCode, message, correlationId).
- [ ] Document routing/menu conventions for `pos-people` and other modules touched (People, Dispatch, Reports, Timekeeping).
- [ ] Document any UI-safe defaults applied (pageSize defaults, active-only filters, preserving prior results on error).

---

## Open Questions to Resolve
- [ ] What are the exact Moqui service names / REST endpoints (and schemas) for listing and fetching `TimekeepingEntry` (filters + paging, and by `timekeepingEntryId`)?
- [ ] What permission/authorization hook does this frontend repo use for People screens, and which roles include Payroll Clerk?
- [ ] Is `tenantId` considered sensitive in UI (hide always, show only to admins, or show to all authorized users)?
- [ ] Does `TimekeepingEntry` include rejection metadata (reason code/notes) and/or approval audit history fields that must be displayed on detail?
- [ ] Should TimekeepingEntry list resolve `employeeId` to employee name (requires People lookup), or display ID only?
- [ ] What are the exact endpoints/services (payloads + status/error codes) for break flows: load current context, start break, end break?
- [ ] Are breaks tied to a Timecard, ClockInSession, or generic TimeEntry in the API, and must the UI pass an identifier or is it derived from auth context?
- [ ] For `breakType=OTHER`, are `notes` required, optional, or unsupported?
- [ ] What timezone standard should the UI use for displaying break times and timekeeping timestamps (user profile vs location vs browser)?
- [ ] Should break type default to “last used,” and if so does backend provide it or must frontend infer?
- [ ] What are the exact endpoints/services for person-location assignments (list/create/update/end) and for listing locations for selection?
- [ ] Is `role` required for `PersonLocationAssignment` and what are allowed values (enum vs free text vs picker)?
- [ ] Is “exactly one primary” enforced per person overall or per person+role?
- [ ] Is `effectiveEndAt` inclusive or exclusive for “active” determination (authoritative rule for UI display)?
- [ ] Are assignments editable after creation, or must changes be made by ending and creating a new assignment? If editable, which fields?
- [ ] What permissions gate view vs manage actions for location assignments in this frontend?
- [ ] Is `changeReasonCode` required/available for assignment create/update/end, and where do valid codes come from?
- [ ] What is the authoritative endpoint/service and response schema to load an employee profile for edit?
- [ ] What is the standard error response format for 400/409 in this stack (field error keys, errorCode, correlationId)? Provide an example.
- [ ] What are the routing/menu conventions for `pos-people` (screen path/module) and where should navigation entries live?
- [ ] What is the default employee status on create (ACTIVE/ON_LEAVE/explicit selection)?
- [ ] Are TERMINATED employees editable, and if so which fields and under what permission?
- [ ] Does the backend require optimistic concurrency (etag/version/lastUpdatedStamp) on update for employee profiles, assignments, role assignments, or disable user?
- [ ] What are the exact endpoints/services for role assignment list/create/end, and does ending support future/backdated end dates?
- [ ] Is a reasonCode required to end/revoke role assignments, and what are valid values?
- [ ] Should role assignments list default to active only or include history?
- [ ] What are the exact endpoints/services for user detail load and disable action, and how does backend communicate allowed disable options (leave assignments active vs end at date)?
- [ ] Is `statusReasonCode` required/optional/conditional for disable user, and what are allowed values/labels?
- [ ] What confirmation UX is required by policy for disabling a user (typed confirmation vs checkbox vs simple confirm)?
- [ ] After disabling a user, should UI remain on detail (refreshed) or navigate back to list?
- [ ] For discrepancy report: what is the expected encoding for `technicianIds`, and is there a maximum date range the UI should enforce?

---
