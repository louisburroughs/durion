# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates shopmgmt story implementations across UI (Moqui screens) and backend contracts.
It is updated to reflect shopmgmt decision IDs, including facility-scoped authorization, submit-time conflict payloads,
idempotency, and timezone handling.

## Completed items

- [x] Updated acceptance criteria for each resolved open question

## Scope/Ownership

- [ ] Confirm story labeled domain:shopmgmt
- [ ] Verify primary actor(s) and permissions
- [ ] Verify behavior stays within shopmgmt boundaries (appointments, assignments, schedule policy)
- [ ] Verify story does not introduce cross-domain policy ownership (operating hours from Location, mechanic SoR from People, notifications from Notification) and only consumes read models as documented
- [ ] Verify “create appointment from source” flow does not add out-of-scope post-create editing (bay/mobile/mechanic/notes) beyond existing Appointment Detail behavior
- [ ] Verify UI does not require direct People/Location/Notification domain calls for correctness (shopmgmt must provide required read model fields)

## Data Model & Validation

- [ ] Validate required inputs and types (appointmentId, facilityId, sourceType, sourceId)
- [ ] Verify date/time and timezone semantics for shopmgmt scheduling
- [ ] Quantity and rounding rules: N/A (shopmgmt does not own numeric money/quantity rounding policies)
- [ ] Validate assignmentNotes max length (500) and notesEditReason presence (if editing enabled)
- [ ] Verify `sourceType` is constrained to allowed values (`ESTIMATE` | `WORK_ORDER`) and rejects unknown values with 400
- [ ] Verify `scheduledStartDateTime` is required for create and is validated as ISO-8601 **with offset** (reject missing offset with 400)
- [ ] Verify `clientRequestId` is required for create and validated for type/length (reject empty/invalid with 400)
- [ ] Verify override fields validation:
  - [ ] `overrideSoftConflicts=false` (or omitted) must ignore/forbid `overrideReason` as appropriate (no silent acceptance of inconsistent state)
  - [ ] When `overrideSoftConflicts=true`, `overrideReason` must be non-empty and within a documented max length (reject with 400/422 per contract)
- [ ] Verify eligibility validation is server-authoritative:
  - [ ] Estimate eligible statuses limited to APPROVED/QUOTED; ineligible returns 422 `ESTIMATE_NOT_ELIGIBLE`
  - [ ] Work order ineligible statuses include COMPLETED/CANCELLED; ineligible returns 422 `WORK_ORDER_NOT_ELIGIBLE`
- [ ] Verify `facilityTimeZoneId` is present on create model and success response and is a valid IANA timezone ID
- [ ] Verify any `serviceSummary`/source summary fields returned in the create model are PII-safe (no customer name/phone/email/address unless explicitly authorized and documented)
- [ ] Verify `existingAppointmentId` (if returned) is permission-gated server-side and does not leak cross-facility existence

## API Contract

- [ ] Verify endpoints, pagination (where relevant), error handling, per-row errors
- [ ] Verify 422 vs 400 semantics (policy vs syntactic validation)
- [ ] Verify 409 semantics (conflict payload or concurrency)
- [ ] Verify “load create model from source” contract is documented and implemented:
  - [ ] Inputs include `sourceType` + `sourceId` (and `facilityId` if required by backend)
  - [ ] Response includes `facilityId`, `facilityTimeZoneId`, `sourceStatus`, `isEligible`, and safe ineligibility details when applicable
- [ ] Verify “create appointment from source” contract is documented and implemented:
  - [ ] Request includes `facilityId`, `sourceType`, `sourceId`, `scheduledStartDateTime`, `clientRequestId`
  - [ ] Success response includes `appointmentId`, `appointmentStatus` (opaque), `scheduledStartDateTime`, `facilityTimeZoneId`, and source linkage field (`estimateId` or `workOrderId`) when applicable
- [ ] Verify 409 conflict response shape matches contract:
  - [ ] `errorCode=SCHEDULING_CONFLICT`
  - [ ] `conflicts[]` includes `severity`, `code`, `message` (safe), `overridable`
  - [ ] `suggestedAlternatives[]` (if present) contains time slots only with `startDateTime` and `endDateTime` (ISO-8601 with offset)
- [ ] Verify 403/404 responses do not leak cross-facility existence (generic messaging; no “exists but forbidden” distinctions)
- [ ] Verify UI uses backend-provided safe `message`/`safeMessage` for conflicts and does not invent conflict text
- [ ] Verify UI maps `fieldErrors[]` to specific form fields and focuses the first invalid field

## Events & Idempotency

- [ ] Create/reschedule include idempotency keys (clientRequestId) and are safe on retry
- [ ] Assignment updates include version/lastUpdatedAt; UI ignores out-of-order updates
- [ ] Verify create-from-source is idempotent by `clientRequestId`:
  - [ ] Retrying the same request returns the original success (same `appointmentId`) and does not create duplicates
  - [ ] Backend behavior is defined for “same clientRequestId, different payload” (must return deterministic error or original result; no partial duplicates)
- [ ] Verify conflict override submission is also safe on retry (same `clientRequestId` reused for the same user submit attempt)
- [ ] Verify UI generates `clientRequestId` once per user-initiated submit attempt and persists it across retries until terminal success/failure

## Security

- [ ] Permission gating for sensitive payloads and raw payload redaction
- [ ] Facility scoping enforced server-side for all reads and writes
- [ ] Verify permissions are enforced server-side for:
  - [ ] Creating appointments (e.g., `CREATE_APPOINTMENT`)
  - [ ] Overriding SOFT conflicts (e.g., `OVERRIDE_SCHEDULING_CONFLICT`)
  - [ ] Viewing audit entries (if exposed)
- [ ] Verify SOFT conflict override requires both permission and non-empty reason; without permission, override path is blocked even if conflicts are marked overridable
- [ ] Verify HARD conflicts are never overridable (UI and backend)
- [ ] Verify UI does not log or display free-text overrideReason unless explicitly authorized and documented
- [ ] Verify error handling for 403/404 uses generic copy and does not confirm existence of cross-facility sources/appointments

## Observability

- [ ] Ensure trace identifiers and audit fields surface in UI and logs (no PII)
- [ ] UI sends X-Request-Id and avoids logging free-text fields
- [ ] Verify UI sends `X-Request-Id` on both load-create-model and create submit calls
- [ ] Verify client telemetry (if implemented) is non-PII and includes enough context to debug:
  - [ ] screen_opened (sourceType, sourceId, facilityId)
  - [ ] create_submitted (clientRequestId)
  - [ ] create_conflicts_returned (counts hard/soft)
  - [ ] create_succeeded (appointmentId)
  - [ ] create_failed (errorCode/status)
- [ ] Verify logs/telemetry do not include customer PII, raw conflict payloads with sensitive data, or free-text overrideReason

## Performance & Failure Modes

- [ ] Verify create screen load and submit meet target latency (e.g., 2s p95 under normal conditions) or have documented expectations
- [ ] Verify UI handles network timeouts on submit:
  - [ ] Offers retry
  - [ ] Reuses the same `clientRequestId`
  - [ ] Does not auto-resubmit in a loop
- [ ] Verify UI handles 409 conflicts without losing user-entered scheduledStartDateTime and without generating a new `clientRequestId` unless the user initiates a new attempt
- [ ] Verify UI handles empty `suggestedAlternatives[]` gracefully (clear empty-state message; user can choose a different time)
- [ ] Verify UI remains usable on tablet widths and does not require hover for critical actions

## Testing

- [ ] Add/verify unit tests for request building:
  - [ ] `scheduledStartDateTime` formatting includes offset
  - [ ] `clientRequestId` generation and reuse across retries
  - [ ] override request includes `overrideSoftConflicts=true` and `overrideReason` only when applicable
- [ ] Add/verify integration tests (or contract tests) for API semantics:
  - [ ] 400 missing `facilityId`
  - [ ] 422 ineligible estimate/work order codes
  - [ ] 409 conflicts payload parsing and rendering (HARD blocks; SOFT override gated)
  - [ ] 201/200 success navigates to Appointment Detail with returned `appointmentId`
- [ ] Add/verify idempotency tests:
  - [ ] Same `clientRequestId` retried returns same `appointmentId`
  - [ ] Same `clientRequestId` with different payload is handled deterministically (as per documented contract)
- [ ] Add/verify security tests:
  - [ ] Without create permission, create is rejected (403) and UI shows generic access denied
  - [ ] Without override permission, SOFT conflicts cannot be overridden
  - [ ] Cross-facility access does not leak existence (403/404 indistinguishable to user)
- [ ] Add/verify accessibility tests:
  - [ ] Conflict list keyboard navigable
  - [ ] Focus moves to first invalid field on validation errors

## Documentation

- [ ] Update story/ADR references with applicable DECISION-SHOPMGMT IDs used by the implementation
- [ ] Document Moqui screen routes/params used for:
  - [ ] Estimate Detail → Create Appointment
  - [ ] Work Order Detail → Create Appointment
  - [ ] Appointment Create
  - [ ] Appointment Detail
- [ ] Document backend service names/endpoints and payload schemas for:
  - [ ] Load AppointmentCreateModel from source
  - [ ] Create appointment from source (success, 400, 409, 422, 403/404)
- [ ] Document permission identifiers used by POS security model for create and override
- [ ] Document which fields are considered PII-safe in source summaries and conflict messages, and what is redacted by default

## Acceptance Criteria (per resolved question)

### Q: What is the source of truth timezone for scheduling input/output and display?

- Decision ID: DECISION-SHOPMGMT-015
- Acceptance: All scheduling timestamps are sent as ISO-8601 with offset; UI displays in facility timezone using facilityTimeZoneId from payload.
- Test Fixtures: facilityTimeZoneId=America/New_York, scheduledStartDateTime=2026-01-19T09:00:00-05:00
- Example API request/response (code block)

```json
{
  "facilityId": "fac-123",
  "scheduledStartDateTime": "2026-01-19T09:00:00-05:00"
}
```

### Q: Are HARD conflicts overridable?

- Decision ID: DECISION-SHOPMGMT-002
- Acceptance: When backend returns severity=HARD conflicts on submit, UI shows a blocking state and does not render an override action.
- Test Fixtures: conflict={severity:HARD, code:"BAY_DOUBLE_BOOKED"}
- Example API request/response (code block)

```json
{
  "status": 409,
  "errorCode": "SCHEDULING_CONFLICT",
  "conflicts": [{"severity": "HARD", "code": "BAY_DOUBLE_BOOKED", "overridable": false}]
}
```

### Q: How are SOFT conflicts overridden?

- Decision IDs: DECISION-SHOPMGMT-002, DECISION-SHOPMGMT-007
- Acceptance: UI allows override only when OVERRIDE_SCHEDULING_CONFLICT is present and overrideReason is non-empty; backend records audit entry for the override.
- Test Fixtures: soft conflict + permission + overrideReason="Approved by manager"
- Example API request/response (code block)

```json
{
  "overrideReason": "Approved by manager",
  "overrideSoftConflicts": true
}
```

### Q: Is conflict checking separate or returned on submit?

- Decision ID: DECISION-SHOPMGMT-011
- Acceptance: UI is correct when conflicts are returned on submit (409 payload). No conflictCheckToken is required for correctness.
- Test Fixtures: submit returns 409 with conflicts
- Example API request/response (code block)

```json
{
  "status": 409,
  "conflicts": [{"severity": "SOFT", "code": "MECHANIC_OVERTIME"}]
}
```

### Q: Is facilityId required explicitly or inferred?

- Decision ID: DECISION-SHOPMGMT-012
- Acceptance: All write operations include facilityId; backend rejects missing facilityId with 400.
- Test Fixtures: createAppointment without facilityId
- Example API request/response (code block)

```json
{
  "status": 400,
  "errorCode": "VALIDATION_ERROR",
  "fieldErrors": [{"field": "facilityId", "message": "required"}]
}
```

### Q: What is the idempotency key contract?

- Decision ID: DECISION-SHOPMGMT-014
- Acceptance: UI generates a single clientRequestId per submit attempt; on retry after timeout it reuses the same clientRequestId and does not create duplicates.
- Test Fixtures: clientRequestId fixed across retries
- Example API request/response (code block)

```json
{
  "clientRequestId": "req-uuidv7-123"
}
```

### Q: Are assignment notes editable and how is concurrency handled?

- Decision ID: DECISION-SHOPMGMT-005
- Acceptance: Notes edit is enabled only with EDIT_ASSIGNMENT_NOTES; backend requires expectedVersion and returns 409 on mismatch; UI prompts reload.
- Test Fixtures: expectedVersion=3, backend currentVersion=4
- Example API request/response (code block)

```json
{
  "status": 409,
  "errorCode": "VERSION_MISMATCH",
  "currentVersion": 4
}
```

### Q: What is the near-real-time update mechanism and fallback?

- Decision ID: DECISION-SHOPMGMT-006
- Acceptance: UI subscribes via SSE and updates within 5 seconds when events arrive; on SSE failure it falls back to polling every 30 seconds while visible.
- Test Fixtures: SSE disconnect, polling enabled
- Example API request/response (code block)

```json
{
  "eventType": "AssignmentUpdated",
  "appointmentId": "app-123",
  "version": 12,
  "lastUpdatedAt": "2026-01-19T14:02:00Z"
}
```

### Q: Are notification toggles supported?

- Decision ID: DECISION-SHOPMGMT-016
- Acceptance: UI does not send notifyCustomer/notifyMechanic toggles; backend may return notificationOutcomeSummary and UI renders it without treating reschedule as failed.
- Test Fixtures: reschedule succeeded, notification queued
- Example API request/response (code block)

```json
{
  "appointmentId": "app-123",
  "result": "SUCCESS",
  "notificationOutcomeSummary": {"result": "QUEUED"}
}
```

### Q: What audit fields are allowed to display in UI?

- Decision ID: DECISION-SHOPMGMT-017
- Acceptance: Audit view is permission-gated; response contains only actorId/action/occurredAt/reasonCode and identifiers. UI does not render customer PII or raw free-text notes unless explicitly authorized.
- Test Fixtures: audit list response with redacted fields
- Example API request/response (code block)

```json
{
  "entries": [{"action": "RESCHEDULED", "actorId": "u-1", "occurredAt": "2026-01-19T14:00:00Z"}]
}
```

## Open Questions to Resolve

- [ ] What are the exact Moqui screen paths and parameter names in this repo for:
  - [ ] Estimate Detail
  - [ ] Work Order Detail
  - [ ] Appointment Create
  - [ ] Appointment Detail
- [ ] What are the exact backend service names/endpoints and payload schemas for:
  - [ ] Load AppointmentCreateModel from source
  - [ ] Create appointment from source (including 409 conflict payload shape, 422 codes, and success response fields)
- [ ] What are the exact permission identifiers used by the POS security model for:
  - [ ] CREATE_APPOINTMENT
  - [ ] OVERRIDE_SCHEDULING_CONFLICT
- [ ] Does the backend return `existingAppointmentId` when a source is already linked, and is that field permission-gated? If not returned, what is the expected UI behavior (message only vs link)?
- [ ] Does the backend require `facilityId` for the load call as well, or only for writes?

## End

End of document.
