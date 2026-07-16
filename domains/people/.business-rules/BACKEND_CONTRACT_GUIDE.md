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
last_updated: 2026-02-24
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

## CAP-120: [CAP] Timekeeping (Clock, Breaks, Approvals, Export)

### Capability Metadata

- Capability ID: CAP-120
- Parent Issue: https://github.com/louisburroughs/durion/issues/120
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-people/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get attendance and job time discrepancy report | `getAttendanceDiscrepancyReport` | GET | `/v1/people/reports/attendanceJobtimeDiscrepancy` |
| List assignments for person | `getAssignments` | GET | `/v1/people/staffing/assignments` |
| Get assignment by ID | `getAssignment` | GET | `/v1/people/staffing/assignments/{assignmentId}` |

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
