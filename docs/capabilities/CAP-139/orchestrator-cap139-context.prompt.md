---
name: "CAP-139 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-139 Timekeeping Integration for Assigned Work."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-139 runs.

## Capability Scope

- Capability: `CAP:139` — Timekeeping Integration for Assigned Work
- Parent issue: `louisburroughs/durion#139`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#68`
  - `louisburroughs/durion-positivity-backend#67`
  - `louisburroughs/durion-positivity-backend#66`

## Canonical Context Sources (Read First)

1. CAP manifest:
   - `durion/docs/capabilities/CAP-139/CAPABILITY_MANIFEST.yaml`
2. Story issues:
   - `#66`, `#67`, `#68` in `durion-positivity-backend`
3. Domain/ADR context:
   - `durion/docs/adr/0006-workexec-domain-ownership-boundaries.adr.md`
   - `durion/docs/adr/0017-api-controller-http-response-codes.adr.md`
   - `durion/docs/adr/0018-audit-actor-fields-from-security-context.adr.md`
   - `durion/docs/adr/0026-service-contract-boundary-policy.adr.md`
   - `durion/domains/workexec/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/DOMAIN_NOTES.md`

## Module and Ownership Guidance

- Primary backend module for CAP-139 is `pos-workorder` (workexec SoR per ADR-0006).
- `WorkSession` and `BreakSegment` are owned by `workexec`; do not split ownership to `pos-people`.
- `TravelSegment` for mobile assignments is also owned by `workexec` (`pos-workorder`); do not create parallel models in `pos-shop-manager` unless a cross-domain contract is explicitly required and planned.
- `TimeEntry`, `TimeEntryAdjustment`, and `TimeException` are owned by `workexec` (`pos-workorder`).
- The API path hint `/v1/people/workSessions/...` in story `#68` originates from the original draft; **verify against ADR-0006** before implementing. Prefer `/v1/workorders/workSessions/...` for workexec-owned resources unless the ADR explicitly places sessions under the people domain.
- `pos-people` may be read for person/identity lookups only — it must not own or persist session state.
- Do not create a new dedicated timekeeping module unless explicitly blocked by module boundary constraints.

## OpenAPI and Contract Path Resolution

- Manifest points to generated spec: `durion-positivity-backend/pos-workorder/openapi.yaml`
- Do not manually edit generated OpenAPI artifacts.
- Use contract guides for behavior and OpenAPI/API reference for schema/status code detail:
  - `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`

## Story Dependency Graph (Hard Planning Constraint)

Required story order for CAP-139:

1. `#68` (WorkSession start/stop — core time capture foundation)
2. `#67` (Mobile travel segment capture — parallel time capture variant building on session model)
3. `#66` (Time entry approval workflow — consumes data from `#68` and `#67`)

- `#66` must not be treated as complete unless `#68` and `#67` session/segment contracts are implemented or formally blocked with explicit remediation.
- `#67` may proceed in parallel with `#68` once core `WorkSession` entity schema is committed.

## Story-Specific Non-Negotiables

### Story `#68` — WorkSession Start/Stop

- `workexec` (`pos-workorder`) is the authoritative system of record for `WorkSession`. Downstream domains (payroll, job costing) only consume events.
- **Overlap policy — default DENY:** overlapping `WorkSession`s must be rejected unless ALL three conditions met:
  1. Config flag `company.timekeeping.allowOverlappingSessions = true` (or location-level equivalent)
  2. Actor has permission `timekeeping:overlap_override`
  3. Override path used, with explicit audit fields persisted: `overrideReason`, `overriddenByUserId`, `overrideAt`
- All timestamps must be recorded in UTC.
- `BreakSegment`s must be manually entered, fully contained within the session window, non-overlapping, and immutable once session is `APPROVED` (`locked = true`).
- Minimal approved state model: `IN_PROGRESS` → `COMPLETED` (mechanic stops) → `APPROVED` (manager approves, sets `locked = true`). No edits allowed when locked.
- Approval/locking endpoint may follow in a separate story; however, the entity schema must include approval support fields now: `approvedAt`, `approvedByUserId`, `approvalNotes`, `lockedAt`.
- Emit events after commit (not inside the transaction):
  - `workexec.WorkSessionStarted`
  - `workexec.WorkSessionStopped` (or `workexec.WorkSessionCompleted`)

### Story `#67` — Mobile Travel Time Capture

- Implement exactly six `TravelSegmentType` enum values for v1:
  `DEPART_SHOP`, `ARRIVE_CUSTOMER_SITE`, `DEPART_CUSTOMER_SITE`, `ARRIVE_SHOP`, `TRAVEL_BETWEEN_SITES`, `DEADHEAD`
  Do not add optional future types (`FUEL_STOP`, `PARTS_RUN`) to v1 implementation.
- Technician availability must be set to `InTransit` for the duration of any `InProgress` or `Completed` travel segment.
- Buffer policies are configurable per location and must be applied at approval/daily rollup time, NOT during real-time segment capture. Store both `rawMinutes` and `bufferedMinutes`.
- HR integration: async event on topic `timekeeping.travel.approved.v1` (`TravelTimeApproved`) — fire-and-forget, no synchronous ACK. HR must handle idempotency by `eventId`.
- On-behalf edits (by Mobile Lead / Service Advisor) are allowed only when segment `status = DRAFT` or `SUBMITTED`, with full audit trail: `actedByUserId`, `actedForPersonId`, `onBehalfReasonCode`.
- Post-approval corrections must go through `TravelSegmentAdjustment` records (same pattern as `TimeEntryAdjustment`); direct segment mutation is not allowed when `APPROVED`.
- All create/start/complete/cancel state changes must be recorded in an immutable audit log.

### Story `#66` — Approve Submitted Time

- Managers must not directly edit original `TimeEntry` records. Corrections go through separate `TimeEntryAdjustment` records (Option B — Separate Adjustment Record per resolved question RQ1). Original `TimeEntry` is immutable once approved.
- `TimeException` severity rules must be enforced at approval time:
  - `WARNING` severity: approval allowed with manager acknowledgment.
  - `BLOCKING` severity: must be `RESOLVED` or `WAIVED` (with `resolutionNotes`) before approval is permitted.
- Minimum v1 exception codes to implement: `OVERLAPPING_ENTRIES`, `MISSING_CLOCKOUT`, `DAILY_HOURS_EXCEEDED`, `OVERTIME_THRESHOLD`, `TIME_AGAINST_CLOSED_WORKORDER`, `TIME_DURING_PTO`, `LOCATION_MISMATCH`.
- HR integration: async event on topic `timekeeping.daily_totals.approved.v1` (`DailyTimeApproved`) — at-least-once delivery; fire-and-forget. Include `byWorkOrder` allocation breakdown for job costing.
- `TimeEntry` state machine: `PENDING_APPROVAL` → `APPROVED` or `REJECTED`. Approved entries are locked (immutable to further changes from this workflow).
- Actor fields (`approvedBy`, `decisionByUserId`) must be resolved from the authenticated security context, **not** from the request payload (ADR-0018).
- Required permissions: `TimeEntry:Approve`, `TimeEntry:Reject`.
- All state transitions must be logged with: `timeEntryId`, `previousState`, `newState`, `timestamp`, `actorUserId`, `rejectionReason` (if applicable).

## Error and Status Semantics

Follow ADR-0017:

- `409` for state/conflict collisions (e.g., overlap prevention, approving already-approved entry)
- `422` for semantic policy violations (e.g., BLOCKING exception not resolved before approval)
- `403` for permission failures (e.g., on-behalf edit outside scope, missing approval permission)
- `400` for malformed/invalid request payloads
- Error responses must include correlation ID and stable error envelope fields.

## Audit and Security Rules

Follow ADR-0018 for all actor fields:

- `approvedByUserId`, `decisionByUserId`, `actedByUserId`, `overriddenByUserId` — all populated from the authenticated security context, never from request payload.
- Persisted actor fields as strings.
- Ensure all new permissions required by CAP-139 are registered in module permissions configuration:
  - `TimeEntry:Approve`
  - `TimeEntry:Reject`
  - `timekeeping:overlap_override`
  - `timekeeping:travel_segment:create`
  - `timekeeping:travel_segment:edit_self`
  - `timekeeping:travel_segment:create_any`
  - `timekeeping:travel_segment:edit_any`
  - `timekeeping:travel_segment:delete_any`

## CAP-139 Execution Deliverables (Per Story)

- Story-level RED evidence with failing assertions tied to story ACs.
- GREEN evidence with passing tests for the same scope.
- Code review PASS evidence against story criteria and clarification decisions.
- Coverage evidence (`>= 80%` service/utility scope for touched module).
- Explicit ADR + resolved-question compliance section in each handoff.

## Blocker Policy for CAP-139

- Do not mark a story "done" if it relies on unresolved external assumptions or a prior story's schema not yet committed.
- If blocked, return:
  - exact missing contract input
  - impacted story IDs
  - attempted fallback path
  - smallest unblocked slice completed
  - next concrete remediation step
