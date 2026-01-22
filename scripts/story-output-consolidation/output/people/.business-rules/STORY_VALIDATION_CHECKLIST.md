# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates People-domain story implementations (People/HR, Users,
Timekeeping UI) against the domain’s normalized decisions. It is designed to
prevent UI-side policy drift, ensure error handling and authorization are
consistent, and ensure list/report screens are safe (paging, idempotency, and
non-leaky authorization).

## Completed items

- [x] Rewrote checklist to required template structure
- [x] Added acceptance criteria for every previously listed open question

## Scope/Ownership

- [ ] Feature belongs to People domain UI and does not re-implement WorkExec/
      ShopMgr business rules.
- [ ] Read-only screens do not expose mutation controls.
- [ ] Mutations are done only via backend contracts (no local business rule
      computation).
- [ ] Timekeeping approvals/adjustments/exceptions UI is treated as **People-domain owned** (not WorkExec); any cross-domain data (e.g., work order display) is read-only and sourced from backend contracts.
- [ ] UI does not “lock” or “finalize” time locally; it reflects server-side status transitions (e.g., `PENDING_APPROVAL` → `APPROVED`) after mutation responses/refresh.
- [ ] UI does not allow editing original time entry timestamps when creating adjustments; adjustments are created as separate records only.

## Data Model & Validation

- [ ] Required fields validated client-side; 400 field errors rendered without
      data loss.
- [ ] Date validations enforced:
  - [ ] Range filters: `from <= thru`.
  - [ ] Effective dating: `effectiveStartAt <= effectiveEndAt`.
- [ ] Enums validated against authoritative values where available.
- [ ] Effective dating uses half-open interval semantics for “active”
      determination. (Decision ID: DECISION-PEOPLE-014)
- [ ] Approvals queue filters validate input formats:
  - [ ] `workDate` is a valid `YYYY-MM-DD` date before issuing requests.
  - [ ] `workOrderId` (if present) is treated as an opaque identifier (no parsing/assumptions).
- [ ] Approve/Reject selection validation:
  - [ ] Verify only entries in actionable status (e.g., `PENDING_APPROVAL`) can be selected for approve/reject.
  - [ ] Verify mixed-status selections are blocked with a clear message (no partial client-side “best effort”).
- [ ] Reject validation:
  - [ ] Verify rejection reason is required (non-empty, non-whitespace) before submit; server-side 400 is rendered if returned.
- [ ] Exception gating validation (UI-side guardrails, server remains authoritative):
  - [ ] Verify Approve is disabled when any selected entry has `BLOCKING` exceptions in `OPEN` status (based on fetched exception data).
  - [ ] Verify UI explains which blocking exceptions prevent approval (by code/label) without inventing policy thresholds.
- [ ] Adjustment create validation:
  - [ ] Verify `reasonCode` is required.
  - [ ] Verify **exactly one** adjustment input mode is submitted:
    - [ ] proposed timestamps mode: both `proposedStartAtUtc` and `proposedEndAtUtc` are present together.
    - [ ] delta mode: `minutesDelta` is present.
  - [ ] Verify UI prevents submitting both modes simultaneously.
  - [ ] Verify UI prevents submitting neither mode.
  - [ ] Verify `minutesDelta` is an integer (no decimals) and can be positive or negative only if backend allows; otherwise UI blocks per backend-provided constraints.
- [ ] Derived fields are display-only:
  - [ ] Verify `durationMinutesDisplay` (if computed) is not used for decisions, totals, or payloads.
- [ ] Audit metadata rendering:
  - [ ] Verify decision/audit fields (submitted/decided/created/resolved) are rendered only when provided; UI does not infer missing audit values.

## API Contract

- [ ] People APIs follow consistent REST conventions for paging and error
      schema. (Decision ID: DECISION-PEOPLE-021, DECISION-PEOPLE-018)
- [ ] Break start/end derives identity/session from auth context and treats 409
      as refresh-required. (Decision ID: DECISION-PEOPLE-022)
- [ ] Assignment list/create/end is implemented and primary semantics reflect
      server outcomes. (Decision ID: DECISION-PEOPLE-004, DECISION-PEOPLE-023)
- [ ] Role assignment list/create/end is implemented with active-only default
      and history toggle. (Decision ID: DECISION-PEOPLE-026)
- [ ] Report filter encoding matches the decided query encoding.
      (Decision ID: DECISION-PEOPLE-020)
- [ ] Timekeeping approvals queue contract is implemented and consistent:
  - [ ] Verify list endpoint supports paging (`pageIndex`, `pageSize`) and returns stable ordering (documented sort or server-provided).
  - [ ] Verify filtering by `workDate` and/or `workOrderId` is supported as documented (no client-side filtering of large result sets).
  - [ ] Verify detail endpoint returns all fields required for display (status, timestamps, decision metadata) without UI inference.
- [ ] Approve/Reject mutation contracts are implemented:
  - [ ] Verify approve supports batch payload `{ timeEntryIds: [...] }` (or documented alternative) and returns a response sufficient to refresh UI state.
  - [ ] Verify reject supports batch payload `{ timeEntryIds: [...], reason: "..." }` (or documented alternative) and server enforces reason required.
  - [ ] Verify actor identity is derived from auth context (UI does not send `decisionByUserId`).
- [ ] Exceptions contracts are implemented:
  - [ ] Verify exceptions list endpoint exists per entry (or documented alternative) and includes `severity` and `status`.
  - [ ] Verify exception action endpoints exist (acknowledge/resolve/waive) or UI is read-only for exceptions in v1 (no “fake” buttons).
  - [ ] Verify waive endpoint requires `resolutionNotes` only if backend enforces it; UI follows backend validation errors.
- [ ] Adjustments contracts are implemented:
  - [ ] Verify adjustments list endpoint exists per entry (or documented alternative).
  - [ ] Verify adjustment create endpoint accepts the one-of schema and returns created adjustment metadata (id/status/audit fields) or a location/id to refetch.
  - [ ] Verify UI does not attempt to update original time entry via adjustment endpoints.

## Events & Idempotency

- [ ] UI does not assume delivery semantics; it always re-renders based on
      server state.
- [ ] Retry-safe behaviors:
  - [ ] Approve/reject period actions are idempotent from the UI perspective.
  - [ ] Break start/end handles “already started/ended elsewhere” with a
        refresh flow.
- [ ] Approve/Reject/Exception/Adjustment actions are safe under retries:
  - [ ] Verify UI disables submit buttons while request is in-flight to prevent accidental double-submit.
  - [ ] Verify UI handles 409 conflicts by prompting refresh and reloading queue/detail before allowing reattempt.
  - [ ] If backend supports idempotency keys for mutations:
    - [ ] Verify UI sends an idempotency key per mutation attempt and reuses it on retry of the same user action.
  - [ ] If backend does **not** support idempotency keys:
    - [ ] Verify UI treats network timeouts as “unknown outcome” and forces refresh before reattempting.
- [ ] Batch approve/reject partial failure handling:
  - [ ] Verify UI can surface per-item failures if backend returns them; otherwise UI refreshes and shows a generic “some items may not have updated” message.

## Security

- [ ] Backend enforces authorization; UI gates by named permissions and
      capability flags.
- [ ] No resource existence leakage via error differences (403/404 handling
      consistent).
- [ ] `tenantId` is not displayed by default. (Decision ID: DECISION-PEOPLE-019)
- [ ] Timekeeping approvals permissions are explicit and least-privilege:
  - [ ] Verify separate gates exist (as applicable) for: view approvals queue, approve, reject, create adjustment, and exception waive/resolve/acknowledge.
  - [ ] Verify UI does not gate by role-name heuristics (e.g., “Shop Manager” string match); it uses named permissions/capabilities.
- [ ] Sensitive data handling:
  - [ ] Verify rejection reasons, adjustment notes, and exception resolution notes are not displayed to unauthorized users if backend restricts them.
  - [ ] Verify identifiers (`employeeId`, `timeEntryId`, `exceptionId`) are treated as opaque and not guessable in UI logic (no sequential assumptions).

## Observability

- [ ] Correlation/request IDs are surfaced in error UI when present.
- [ ] UI logs do not contain PII, notes, or full payloads.
- [ ] Timekeeping approvals actions are traceable without leaking sensitive content:
  - [ ] Verify UI includes non-PII identifiers in logs/error context when safe: `timeEntryId`, `exceptionId`, `adjustmentId`, and correlationId (if provided).
  - [ ] Verify UI does **not** log free-text fields (rejection reason, notes, resolution notes) or full request bodies.
  - [ ] Verify error UI provides correlationId/requestId to support troubleshooting when backend includes it.

## Performance & Failure Modes

- [ ] Lists are paged and stable-sorted; no unbounded loads.
- [ ] Report preserves last successful results on subsequent failure.
- [ ] Clear UI states for 401/403/404/409 and retryable failures.
- [ ] Approvals queue performance:
  - [ ] Verify queue uses pagination for large days (no “load all” for > pageSize).
  - [ ] Verify filter changes cancel/ignore stale in-flight requests to prevent out-of-order rendering.
- [ ] Failure modes for approvals/adjustments/exceptions:
  - [ ] Verify 401 redirects to login without losing user-entered form state where feasible (e.g., rejection reason draft).
  - [ ] Verify 403 renders access denied and hides mutation controls.
  - [ ] Verify 404 shows “Entry no longer exists” and provides navigation back to queue.
  - [ ] Verify 409 triggers refresh guidance and reloads affected data.
  - [ ] Verify 400/422 validation errors render field-level messages and preserve user input.
- [ ] Empty states:
  - [ ] Verify “no pending entries” empty state is explicit and suggests changing filters.

## Testing

- [ ] Unit tests cover client-side date validation, required fields, and query
      serialization.
- [ ] Integration tests validate error schema parsing (400/409) and
      refresh-on-409 flows.
- [ ] Timekeeping approvals tests:
  - [ ] Unit tests cover approve/reject enablement rules (status gating, selection gating).
  - [ ] Unit tests cover reject reason required validation (non-empty, non-whitespace).
  - [ ] Unit tests cover adjustment one-of validation (timestamps mode vs delta mode).
  - [ ] Integration tests cover:
    - [ ] queue paging + filter query serialization (`workDate`, `workOrderId`, `status`).
    - [ ] approve/reject success updates UI state after refresh.
    - [ ] 422/400 returned for unresolved blocking exceptions is rendered and blocks approval.
    - [ ] exception action endpoints (ack/resolve/waive) success and error handling.
    - [ ] partial failure behavior for batch approve/reject if backend supports per-item results.

## Documentation

- [ ] Document final endpoint/service names and permissions for each screen.
- [ ] Document timezone display rules and any server-side report range limits.
- [ ] Document timekeeping approvals screen routes, deep links, and supported query params:
  - [ ] `/timekeeping/approvals`
  - [ ] `/timekeeping/approvals?workDate=YYYY-MM-DD`
  - [ ] `/timekeeping/approvals?workOrderId=<id>`
- [ ] Document canonical status enums and which are actionable in UI (e.g., `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, others read-only).
- [ ] Document exception severity/status model and which transitions are supported (ACKNOWLEDGED/RESOLVED/WAIVED), including any required notes fields.
- [ ] Document adjustment create schema (one-of) and any constraints on `minutesDelta` and proposed timestamps.

## Acceptance Criteria (per resolved question)

### 1) TimekeepingEntry list/detail endpoints and schemas are defined

- Decision ID: DECISION-PEOPLE-021
- Verify:
  - [ ] List uses paging parameters consistently.
  - [ ] Detail endpoint returns all fields required for display without UI
        inference.

### 2) Permission hook and Payroll Clerk access are implementable

- Decision ID: DECISION-PEOPLE-013
- Verify:
  - [ ] UI gates by named permissions (not role-name heuristics).
  - [ ] Unauthorized users see access denied without leaking resource
        existence.

### 3) `tenantId` is treated as sensitive

- Decision ID: DECISION-PEOPLE-019
- Verify:
  - [ ] `tenantId` is not displayed unless explicitly enabled for admin/support.

### 4) TimekeepingEntry rejection metadata and approval history are handled

- Decision ID: DECISION-PEOPLE-006, DECISION-PEOPLE-021
- Verify:
  - [ ] Detail screen renders available rejection metadata/history if provided.
  - [ ] If fields are absent, UI does not invent/derive them.

### 5) Employee name display is deterministic

- Decision ID: DECISION-PEOPLE-012, DECISION-PEOPLE-021
- Verify:
  - [ ] If API provides display name, UI uses it; otherwise UI performs an
        explicit People lookup.

### 6) Break flow endpoints and error semantics are implemented

- Decision ID: DECISION-PEOPLE-022, DECISION-PEOPLE-018
- Verify:
  - [ ] Start/end break uses auth-derived context.
  - [ ] 409 conflicts trigger a refresh of current context.

### 7) Break identity/session identifiers are not client-guessed

- Decision ID: DECISION-PEOPLE-022
- Verify:
  - [ ] UI does not require passing timecard/session IDs unless explicitly
        required by backend.

### 8) `breakType=OTHER` notes handling matches policy

- Decision ID: DECISION-PEOPLE-016
- Verify:
  - [ ] Notes are optional; UI prompts but does not hard-block.

### 9) Timezone display is consistent

- Decision ID: DECISION-PEOPLE-015
- Verify:
  - [ ] UI displays in user timezone when available; otherwise falls back to
        location timezone.

### 10) “Last used break type” behavior is safe

- Decision ID: DECISION-PEOPLE-022
- Verify:
  - [ ] UI may store last-used locally; it does not require backend support.

### 11) PersonLocationAssignment endpoints exist (list/create/end + location picker)

- Decision ID: DECISION-PEOPLE-023
- Verify:
  - [ ] Location picker returns selectable active locations.
  - [ ] Create/end flows refresh and render server results.

### 12) Assignment `role` optionality and values are handled

- Decision ID: DECISION-PEOPLE-004
- Verify:
  - [ ] UI supports `role=null` and uses picker only if enum provided.

### 13) Primary uniqueness scope is enforced and observable

- Decision ID: DECISION-PEOPLE-004
- Verify:
  - [ ] Only one primary assignment per person at a time.
  - [ ] UI reflects automatic demotion after refresh.

### 14) Effective end semantics are exclusive

- Decision ID: DECISION-PEOPLE-014
- Verify:
  - [ ] “Active” logic uses half-open interval semantics.

### 15) Assignment editability follows end+create preference

- Decision ID: DECISION-PEOPLE-014, DECISION-PEOPLE-003
- Verify:
  - [ ] UI uses end+create rather than in-place edits for core fields.

### 16) Permissions gate view vs manage for assignments

- Decision ID: DECISION-PEOPLE-013
- Verify:
  - [ ] View-only users cannot access mutation actions.

### 17) Assignment reason codes are optional in v1

- Decision ID: DECISION-PEOPLE-023
- Verify:
  - [ ] UI does not hard-require reason code unless backend indicates it.

### 18) Employee profile edit endpoint/schema is authoritative

- Decision ID: DECISION-PEOPLE-021
- Verify:
  - [ ] Edit screen loads via a single authoritative endpoint/schema.

### 19) Standard error response format is used

- Decision ID: DECISION-PEOPLE-018
- Verify:
  - [ ] 400 renders field errors.
  - [ ] 409 renders conflict with actionable refresh guidance.

### 20) Routing/menu conventions for People screens are consistent

- Decision ID: DECISION-PEOPLE-021
- Verify:
  - [ ] Navigation does not create duplicate entry points across modules.

### 21) Default employee status on create is ACTIVE

- Decision ID: DECISION-PEOPLE-025
- Verify:
  - [ ] Create form defaults to ACTIVE unless user selects otherwise.

### 22) Terminated employee edit rules are enforced

- Decision ID: DECISION-PEOPLE-025
- Verify:
  - [ ] Terminated employees are read-only by default.

### 23) Optimistic concurrency is honored when provided

- Decision ID: DECISION-PEOPLE-017
- Verify:
  - [ ] UI submits version token and handles 409 by refresh.

### 24) Role assignment list/create/end endpoints exist and support dating

- Decision ID: DECISION-PEOPLE-026
- Verify:
  - [ ] Ending supports future-dated ends.
  - [ ] Backdated ends return 409 when invalid.

### 25) Role assignment end reason codes are not hard-required by default

- Decision ID: DECISION-PEOPLE-026
- Verify:
  - [ ] UI can capture reason text but does not block without reason unless
        backend requires it.

### 26) Role assignment list defaults active-only with history toggle

- Decision ID: DECISION-PEOPLE-026
- Verify:
  - [ ] History is visible only when user opts in.

### 27) Disable user detail load and disable action are implementable

- Decision ID: DECISION-PEOPLE-024
- Verify:
  - [ ] Backend capability/options are used to render disable options.

### 28) statusReasonCode is optional unless backend requires it

- Decision ID: DECISION-PEOPLE-024
- Verify:
  - [ ] UI does not hard-require reason code unless backend indicates required.

### 29) Disable confirmation UX is explicit

- Decision ID: DECISION-PEOPLE-024
- Verify:
  - [ ] A confirm step exists and is not bypassable.

### 30) Post-disable navigation stays on refreshed detail

- Decision ID: DECISION-PEOPLE-024
- Verify:
  - [ ] UI refreshes detail and remains on page.

### 31) Discrepancy report technician filter encoding and range behavior

- Decision ID: DECISION-PEOPLE-020
- Verify:
  - [ ] `technicianId=<id>` repeated query params are used.
  - [ ] UI handles server-enforced max date range and messages clearly.

## Open Questions to Resolve

- [ ] Confirm domain ownership/labeling for Timekeeping Approvals: this UI is People-domain owned (not WorkExec) and should be triaged accordingly.
- [ ] Confirm the actual backend endpoints/services available to Moqui for:
  - [ ] listing time entries by `workDate` and/or `workOrderId` with paging
  - [ ] loading time entry detail
  - [ ] approving/rejecting (single vs batch), including request/response schema
  - [ ] listing/creating adjustments and whether adjustments require separate approval in this UI
  - [ ] listing exceptions and performing exception actions (ack/resolve/waive)
- [ ] Confirm canonical enum values for time entry status (e.g., `SUBMITTED` vs `PENDING_APPROVAL`) and identify which statuses are actionable vs read-only.
- [ ] Confirm exception lifecycle support:
  - [ ] Are ACKNOWLEDGED/RESOLVED/WAIVED actions implemented as endpoints?
  - [ ] If not implemented, is exception handling read-only in v1 and enforced only by approval validation?
- [ ] Confirm permission/capability model (named permissions/capability flags) for:
  - [ ] view approvals queue
  - [ ] approve
  - [ ] reject
  - [ ] create adjustment
  - [ ] acknowledge/resolve/waive exceptions (especially waive blocking exceptions)
- [ ] Confirm adjustment input rules:
  - [ ] Must UI support both “proposed start/end” and “minutes delta” modes?
  - [ ] If both, confirm one-of enforcement and that proposed start/end requires both fields together.
  - [ ] Confirm any constraints on `minutesDelta` (allowed range, sign rules).
- [ ] Confirm timezone semantics:
  - [ ] Display timestamps in user timezone (preferred) vs location/shop timezone.
  - [ ] Which timezone is used for day-bucket filtering (`workDate`) and where that timezone is sourced from.
  - [ ] Whether backend provides shop/location timezone and how it is exposed.

## End

End of document.
