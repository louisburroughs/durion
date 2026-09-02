---
title: People Backend Contract Guide
domain: people
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/people/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-people/openapi.yaml
openapi_commit: ca7fadc3
last_verified_utc: 2026-02-24T14:23:11Z
last_updated: 2026-09-02
api_reference_generated: domains/people/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# People Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for People domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-people/openapi.yaml`
- Generated API reference: `domains/people/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/people/.business-rules/AGENT_GUIDE.md`

## How To Use This Guide

Backend coder workflow:

1. Read `Domain Invariants` and the relevant capability section.
2. Validate behavior constraints before implementing endpoint changes.
3. Use `operationId` mappings here, then confirm payload details in generated API reference.
4. Ensure tests cover each changed behavioral assertion.

Frontend developer workflow:

1. Start with `Frontend API Lookup` and identify the `operationId` for the UI action.
2. Open generated API reference for exact payload and response details.
3. Implement error handling and headers described in this guide.

## Domain Invariants

- People behavioral rules are authoritative in backend services, not inferred from frontend state.
- Mutating operations require explicit permission enforcement and auditable outcomes.
- Error responses and correlation headers must be deterministic and traceable across requests.
- Cross-domain interactions must go through API/event contracts, not direct data coupling.

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-117 | `durion#117` | draft | [CAP] Identity Orchestration (User ↔ Person Linking) |
| CAP-118 | `durion#118` | draft | [CAP] Roles, Permissions, and Scoped Access (RBAC) |
| CAP-119 | `durion#119` | draft | [CAP] Location Management & Staffing Assignments |
| CAP-120 | `durion#120` | draft | [CAP] Timekeeping (Clock, Breaks, Approvals, Export) |
| CAP-121 | `durion#121` | draft | [CAP] Job Time Tracking (Workexec Linkage) |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| End (soft-delete) an assignment | `endAssignment` | DELETE | `/v1/people/staffing/assignments/{assignmentId}` | Refer to generated API reference for payload details |
| Unlink user from person | *(not in pos-people OpenAPI — user-person links moving to pos-people-contact, ADR-0044 §6)* | DELETE | `/v1/people/users/{userId}/link` | Refer to generated API reference for payload details |
| Delete a person | *(not in pos-people OpenAPI — person ownership moving to pos-people-contact, ADR-0044 §6)* | DELETE | `/v1/people/{personId}` | Refer to generated API reference for payload details |
| Revoke role assignment | *(not in pos-people OpenAPI — no access-assignment revoke endpoint shipped)* | DELETE | `/v1/people/{personUuid}/access/assignments/{roleCode}` | Refer to generated API reference for payload details |
| Get all people | *(not in pos-people OpenAPI — person ownership moving to pos-people-contact, ADR-0044 §6)* | GET | `/v1/people` | Refer to generated API reference for payload details |
| Get people availability | `getPeopleAvailability` | GET | `/v1/people/availability` | Refer to generated API reference for payload details |
| Get current user's primary location | `getMyPrimaryLocation` | GET | `/v1/people/me/primary-location` | Defaults to the top-level location when the caller has no person link or no active primary assignment; see CAP-119 behavioral assertions (backend#1636) |
| List current user's active location assignments | `listMyLocations` | GET | `/v1/people/me/locations` | Refer to generated API reference for payload details |
| Get a person's primary location | `getPersonPrimaryLocation` | GET | `/v1/people/{personId}/primary-location` | Strict 404 when no primary assignment — no top-level default |
| Get employee profile | `getEmployee` | GET | `/v1/people/employees/{employeeId}` | Refer to generated API reference for payload details |
| List exceptions, optional filter by employeeId | `listByEmployee` | GET | `/v1/people/exceptions` | Refer to generated API reference for payload details |
| Get approved time entries for accounting export | `getApprovedTimeForExport` | GET | `/v1/people/reports/approvedTime` | Refer to generated API reference for payload details |
| Get attendance and job time discrepancy report | `getAttendanceDiscrepancyReport` | GET | `/v1/people/reports/attendanceJobtimeDiscrepancy` | Refer to generated API reference for payload details |
| List assignments for person | `getAssignments` | GET | `/v1/people/staffing/assignments` | Refer to generated API reference for payload details |
| Get assignment by ID | `getAssignment` | GET | `/v1/people/staffing/assignments/{assignmentId}` | Refer to generated API reference for payload details |
| List adjustments for a time entry | `listForTimeEntry` | GET | `/v1/people/timeEntries/{timeEntryId}/adjustments` | Refer to generated API reference for payload details |
| Get links by person ID | *(not in pos-people OpenAPI — user-person links moving to pos-people-contact, ADR-0044 §6)* | GET | `/v1/people/user-links/{personId}` | Refer to generated API reference for payload details |
| Get person by user ID | *(not in pos-people OpenAPI — user-person links moving to pos-people-contact, ADR-0044 §6)* | GET | `/v1/people/users/{userId}/person` | Refer to generated API reference for payload details |

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-117: [CAP] Identity Orchestration (User ↔ Person Linking)

### Capability Metadata

- Capability ID: CAP-117
- Parent Issue: https://github.com/louisburroughs/durion/issues/117
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-people/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| End (soft-delete) an assignment | `endAssignment` | DELETE | `/v1/people/staffing/assignments/{assignmentId}` |
| Unlink user from person | *(not in pos-people OpenAPI — user-person links moving to pos-people-contact, ADR-0044 §6)* | DELETE | `/v1/people/users/{userId}/link` |
| Delete a person | *(not in pos-people OpenAPI — person ownership moving to pos-people-contact, ADR-0044 §6)* | DELETE | `/v1/people/{personId}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-people/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-118: [CAP] Roles, Permissions, and Scoped Access (RBAC)

### Capability Metadata

- Capability ID: CAP-118
- Parent Issue: https://github.com/louisburroughs/durion/issues/118
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-people/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Revoke role assignment | *(not in pos-people OpenAPI — no access-assignment revoke endpoint shipped)* | DELETE | `/v1/people/{personUuid}/access/assignments/{roleCode}` |
| Get all people | *(not in pos-people OpenAPI — person ownership moving to pos-people-contact, ADR-0044 §6)* | GET | `/v1/people` |
| Get people availability | `getPeopleAvailability` | GET | `/v1/people/availability` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-people/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-119: [CAP] Location Management & Staffing Assignments

### Capability Metadata

- Capability ID: CAP-119
- Parent Issue: https://github.com/louisburroughs/durion/issues/119
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-people/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get employee profile | `getEmployee` | GET | `/v1/people/employees/{employeeId}` |
| List exceptions, optional filter by employeeId | `listByEmployee` | GET | `/v1/people/exceptions` |
| Get approved time entries for accounting export | `getApprovedTimeForExport` | GET | `/v1/people/reports/approvedTime` |
| Get current user's primary location | `getMyPrimaryLocation` | GET | `/v1/people/me/primary-location` |
| List current user's active location assignments | `listMyLocations` | GET | `/v1/people/me/locations` |
| Get a person's primary location | `getPersonPrimaryLocation` | GET | `/v1/people/{personId}/primary-location` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

#### Issue louisburroughs/durion-positivity-backend#1636 — Primary Location Top-Level Default

`GET /v1/people/me/primary-location` no longer answers 404 for a caller without an active
primary staffing assignment. It falls back to the platform's **top-level location**:

- A caller with an active assignment flagged primary today gets that assignment's
  `locationId` and `defaulted: false`.
- A caller with **no person link** or **no active primary assignment** gets the top-level
  default location and `defaulted: true` — the new optional `defaulted` boolean on
  `PrimaryLocationResponse` is how clients distinguish a real assignment from the
  substituted default (it is additive; clients that ignore it keep working).
- 404 remains only when no default can be resolved either (no active location is known to
  the replica yet — e.g. a brand-new deployment before the first location facts arrive).
- A missing security context is still an error, never defaulted.
- **Top-level location** is defined by the location domain (`getTopLevelLocation`,
  `GET /v1/locations/top-level` on pos-location): the active parent-child hierarchy root
  (a parent that is no location's child), else the oldest active location; deterministic
  (ties break on UUID v7 id order).
- pos-people resolves the same semantics locally from its event-fed `ext_location` and
  `ext_location_parent` replicas (fed by `location.location.updated` facts on
  `location.events.v1`, ADR-0044 §6) — no synchronous call to pos-location.
- `GET /v1/people/{personId}/primary-location` (service-to-service resolution by person id)
  keeps **strict 404** semantics: it never substitutes the default.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.
- Dispatch board / mechanic availability views: treat `defaulted: true` as "no explicit
  assignment — showing the platform default"; a 404 from `getMyPrimaryLocation` now means
  no location exists at all, not merely no assignment.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.
- Consumes `location.location.updated` / `location.location.deleted` facts
  (`location.events.v1`) into `ext_location` and `ext_location_parent` replicas; the
  parent-edge replica backs the top-level default resolution (backend#1636).

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-people/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.
- backend#1636 assertions: `PeopleAvailabilityServiceTest` (primary wins / top-level
  fallback / strict 404 cases), `LocationAndWorkorderEventsListenerTest` (parent-edge
  replication).

## CAP-120: [CAP] Timekeeping (Clock, Breaks, Approvals, Export)

### Capability Metadata

- Capability ID: CAP-120
- Parent Issue: https://github.com/louisburroughs/durion/issues/120
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-people/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| List attendance time entries for approval | `listTimeEntries` | GET | `/v1/people/timeEntries` |
| Get one attendance time entry | `getTimeEntry` | GET | `/v1/people/timeEntries/{timeEntryId}` |
| Get attendance and job time discrepancy report | `getAttendanceDiscrepancyReport` | GET | `/v1/people/reports/attendanceJobtimeDiscrepancy` |
| List assignments for person | `getAssignments` | GET | `/v1/people/staffing/assignments` |
| Get assignment by ID | `getAssignment` | GET | `/v1/people/staffing/assignments/{assignmentId}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.
- A time entry is **attendance**: clock-in, clock-out and the break minutes inside that window.
  It carries no workorder reference, and no operation here answers how long a job took — that is
  time on task, owned by pos-workorder and covered by CAP-121 below
  (durion-positivity-backend#1573).
- `listTimeEntries` filters are each optional and narrow independently: `status`, `workDate`,
  `employeeId`, `locationId`. Omitting one does not filter on it.
- `workDate` is a calendar day resolved in the `timeZone` query parameter, which defaults to UTC.
  The entries store instants, so the day a shift belongs to is only defined once a zone is chosen,
  and the same entry can fall on different `workDate` values for different zones.
- The queue is ordered oldest submission first, so a supervisor works entries in the order
  employees submitted them. An entry not yet submitted sorts last rather than jumping the queue.
- An entry with no clock-in is not returned by a day-filtered query: it has no day to be approved
  for.
- `decisionByUserId` / `decisionAtUtc` carry whichever decision was taken; `status` says which of
  the two it was, and `rejectionReason` is present only when `status` is `REJECTED`.
- Both read operations require the `people:timeEntry:view` authority.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.
- The approvals screen selects and acts on `PENDING_APPROVAL` entries; pass
  `status=PENDING_APPROVAL` to build the queue, and take the `timeEntryId` an approve or reject
  call needs from the rows it returns.
- Send the viewer's zone as `timeZone` when the shop does not operate in UTC, or a late shift will
  appear on the wrong day.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.
- Time entries are produced by work-session submission; nothing else writes them today, and a
  submitted session's timestamp becomes the entry's `submittedAtUtc`.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-people/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.
- Read surface: `internal/service/TimeEntryQueryServiceTest.java` (window derivation, decision
  collapsing) and `internal/repository/TimeEntryApprovalQueueTest.java` (filters, ordering, paging
  against a database).

## CAP-121: [CAP] Job Time Tracking (Workexec Linkage)

### Capability Metadata

- Capability ID: CAP-121
- Parent Issue: https://github.com/louisburroughs/durion/issues/121
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-people/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| List adjustments for a time entry | `listForTimeEntry` | GET | `/v1/people/timeEntries/{timeEntryId}/adjustments` |
| Get links by person ID | *(not in pos-people OpenAPI — user-person links moving to pos-people-contact, ADR-0044 §6)* | GET | `/v1/people/user-links/{personId}` |
| Get person by user ID | *(not in pos-people OpenAPI — user-person links moving to pos-people-contact, ADR-0044 §6)* | GET | `/v1/people/users/{userId}/person` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-people/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-people/openapi.yaml`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:23:11Z`
- Generated API reference: `domains/people/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/people/.business-rules/AGENT_GUIDE.md`
- `domains/people/.business-rules/DOMAIN_NOTES.md`
- `domains/people/.business-rules/BACKEND_API_REFERENCE.generated.md`
