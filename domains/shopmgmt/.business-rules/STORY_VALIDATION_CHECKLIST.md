
# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates shopmgmt story implementations across UI (Moqui screens) and backend contracts.
It is updated to reflect shopmgmt decision IDs, including facility-scoped authorization, submit-time conflict payloads,
idempotency, and timezone handling.

## Completed items

- [x] Updated acceptance criteria for each resolved open question

## Scope / Ownership

- [ ] Confirm story labeled domain:shopmgmt
- [ ] Verify primary actor(s) and permissions
- [ ] Verify behavior stays within shopmgmt boundaries (appointments, assignments, schedule policy)

## Data Model & Validation

- [ ] Validate required inputs and types (appointmentId, facilityId, sourceType, sourceId)
- [ ] Verify date/time and timezone semantics for shopmgmt scheduling
- [ ] Quantity and rounding rules: N/A (shopmgmt does not own numeric money/quantity rounding policies)
- [ ] Validate assignmentNotes max length (500) and notesEditReason presence (if editing enabled)

## API Contract

- [ ] Verify endpoints, pagination (where relevant), error handling, per-row errors
- [ ] Verify 422 vs 400 semantics (policy vs syntactic validation)
- [ ] Verify 409 semantics (conflict payload or concurrency)

## Events & Idempotency

- [ ] Create/reschedule include idempotency keys (clientRequestId) and are safe on retry
- [ ] Assignment updates include version/lastUpdatedAt; UI ignores out-of-order updates

## Security

- [ ] Permission gating for sensitive payloads and raw payload redaction
- [ ] Facility scoping enforced server-side for all reads and writes

## Observability

- [ ] Ensure trace identifiers and audit fields surface in UI and logs (no PII)
- [ ] UI sends X-Request-Id and avoids logging free-text fields

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

## End

End of document.
