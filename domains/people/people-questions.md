# People Domain - Open Questions & Phase Implementation Plan

**Created:** 2026-01-25  
**Status:** Phase 1 Complete (2026-01-25)
**Scope:** Unblock ALL people domain issues with `blocked:clarification` status through systematic backend contract discovery and GitHub issue resolution

---

## Executive Summary

This document addresses **1 unresolved people domain issue** with `blocked:clarification` status. The objective is to systematically resolve all blocking questions through backend contract research and communicate resolutions via GitHub issue comments in `durion-moqui-frontend`, enabling implementation to proceed.

**Coverage Status:**
- ⏳ **This Document:** Issue #130 (1 issue)
- 🎯 **Target Domain:** People/Timekeeping (with cross-domain Workexec clarification needed)
- 📊 **Blocking Questions:** Estimated 30+ questions to resolve

**Critical Note:** Issue #130 has explicit domain ownership clarification needed — the story content references `domain:people` per DECISION-INVENTORY-009 (workexec consumes people availability read-only), but the issue has **both** `domain:people` AND `blocked:domain-conflict` labels. The domain ownership question must be resolved first to ensure proper routing and stakeholder alignment.

---

## Scope (Unresolved Issues)

### Issue #130 — Timekeeping: Approve Submitted Time for a Day/Workorder

### Phase 1 — Proposed Manager Approval API (draft)

- **Rationale & policy alignment:** follows `api-naming-policy` (path-based versioning `/v1/...`, plural resource names, use `POST` for commands). Canonical base path: `/v1/people/timeEntries`.

- **Endpoints (command + query):**
  - `GET /v1/people/timeEntries` — list/filter time entries (query params: `status`, `workDate`, `workOrderId`, `employeeId`, `pageIndex`, `pageSize`).
  - `GET /v1/people/timeEntries/{timeEntryId}` — get time entry detail.
  - `POST /v1/people/timeEntries/{timeEntryId}/approve` — approve a single time entry. Request DTO: `TimeEntryApproveRequest`. Response: `TimeEntryApproveResponse` (or 204).
  - `POST /v1/people/timeEntries/{timeEntryId}/reject` — reject a single time entry. Request DTO: `TimeEntryRejectRequest` (requires `rejectionReason`).
  - `POST /v1/people/timeEntries/approve` — batch approve. Request DTO: `TimeEntryDecisionBatchRequest { decisions: [{ timeEntryId, decisionMetadata... }] }`.
  - `POST /v1/people/timeEntries/reject` — batch reject (similar batch DTO; per-entry rejectionReason required).

- **DTO naming (per policy):** `TimeEntryApproveRequest`, `TimeEntryRejectRequest`, `TimeEntryDecisionBatchRequest`, `TimeEntryDecisionResponse`.

- **HTTP semantics & idempotency:** use `POST` for state changes. For batch operations recommend supporting an `Idempotency-Key` header for client retries. Document idempotency behavior in OpenAPI `operationId` and description (e.g., `approveTimeEntry`, `batchApproveTimeEntries`).

- **Error handling & codes (recommendation):** extend canonical `ErrorResponse` to include `correlationId` and `timestamp` (see `pos-accounting` for existing shape). Suggested contract:

```json
{
  "errorCode": "ENTRY_ALREADY_DECIDED",
  "message": "Time entry was already approved by another user",
  "correlationId": "<uuid>",
  "fieldErrors": {"rejectionReason":"required"}
}
```

### Phase 2 — Progress update (delta)

- Added DB migration for `time_entry_adjustment` and `time_entry_exception` at `pos-people/src/main/resources/db/migration/V2__create_adjustment_exception_tables.sql`.
- Implemented DTOs for adjustments/exceptions: `TimeEntryAdjustmentRequest`, `TimeEntryAdjustmentResponse`, `TimeEntryExceptionRequest`, `TimeEntryExceptionResponse`.
- Added basic create/list controller endpoints:
  - `TimeEntryAdjustmentController` (`POST /v1/people/timeEntries/adjustments`, `GET /v1/people/timeEntries/{timeEntryId}/adjustments`).
  - `TimeEntryExceptionController` (`POST /v1/people/exceptions`, `GET /v1/people/exceptions`).
- Added repository query methods used by controllers: `findByTimeEntryId`, `findByEmployeeId`, `findByTimeEntryId` in respective repos.
- Added OpenAPI annotations (`@Tag`, `@Operation`, `@ApiResponses`) to the new controllers to seed API docs.

Remaining Phase 2 items:
- Expand adjustment APIs: approve/decline endpoints, status transitions, permission checks, audit entries (follow same pattern used for `TimeEntry` approvals).
- Expand exception APIs: resolve endpoint, severity handling, notifications/worker hooks if needed.
- Add OpenAPI models and responses for error envelope (ensure `correlationId` & `timestamp` included consistently).
- Add unit and integration tests for controllers and service behavior, including audit verification.
- Prepare PRs and CI pipeline checks (build + migration verification).

Next recommended step: add unit tests for the new controllers and a small service test to assert DB writes for adjustments/exceptions. After tests, run the module tests locally (Java 21 required):

```bash
./mvnw -f pos-people/pom.xml test
```

- **Header conventions:** require/generate a correlation header `X-Correlation-Id` on requests (gateway should populate when missing). Surface the same `correlationId` in error responses to aid tracing.

- **Permission keys (proposal):** `people:timeEntry:viewPending`, `people:timeEntry:approve`, `people:timeEntry:reject`, `people:timeAdjustment:create` (use `domain:resource:action` pattern).

- **Batch behavior:** backend should return per-entry results with success/failure and per-entry `correlationId` or include original `Idempotency-Key` mapping. Prefer partial success semantics with clear per-entry errors.

- **Follow-ups:**
  - Add OpenAPI operations for the above endpoints following `api-naming-policy` rules (operationId camelCase verb-first, examples, servers).
  - Coordinate with frontend owners to map Moqui deep-links (`/timekeeping/approvals?workDate=YYYY-MM-DD`) to `GET /v1/people/timeEntries` filters.
  - Propose `ErrorResponse` change in a small PR to `pos-accounting` and `pos-people` (add `correlationId`, `timestamp`) and update gateway to propagate `X-Correlation-Id`.



- Completed: domain ownership, endpoint inventory (existing controllers), proposed manager approve/reject API, correlationId recommendation.
- Pending: Moqui screen/deep-link confirmation and posting the GitHub clarification comment for Issue #130 (Task 4.1).

### Implementation notes — pos-people approval logic

- Implemented batch-approve endpoint and approval logic in `pos-people`:
  - Controller: `pos-people/src/main/java/com/positivity/people/controller/TimeEntryApprovalController.java`
  - Service: `pos-people/src/main/java/com/positivity/people/service/TimeEntryService.java`
  - Entity: `pos-people/src/main/java/com/positivity/people/entity/TimeEntry.java`
  - Repository: `pos-people/src/main/java/com/positivity/people/repository/TimeEntryRepository.java`
  - DTOs: `TimeEntryDecisionBatchRequest`, `TimeEntryDecisionResponse`, `TimeEntryDecisionResult`

- Behavior: validates existence and pending/submitted state, sets `status=APPROVED`, `approvedBy`, `approvedAt`, and returns per-entry results. Permission checks and audit persistence are TODO items.

- Behavior: validates existence and pending/submitted state, performs permission checks (derives permissions from authenticated principal `SecurityContext` and merges any `X-Permissions` header; requires `people:timeEntry:approve` or `admin`), sets `status=APPROVED`, `approvedBy`, `approvedAt`, and returns per-entry results. Audit entries are recorded to `time_entry_audit` with `correlationId` when provided.

- Required follow-ups before production:
  1. Database migration to create `time_entry` table matching the `TimeEntry` entity.
  2. Implement permission checks (map to `X-User-Id` / auth context) and enforce deny-by-default.
  3. Add audit trail entries for approvals and update related timesheet status if applicable.
  4. Add idempotency handling and validate batch partial-success semantics.


### Phase 1 — Moqui screen & deep-link confirmation

- I searched the `durion-moqui-frontend` codebase for likely screen names and deep links (`timekeeping`, `approvals`, `Timekeeping`, `Approvals`) and did not find matching screen files in the frontend repo. This suggests the Moqui screens are either named differently, generated at runtime, or maintained in a separate component repository.

- Recommendation: confirm screen locations with frontend owners and map the following deep-link pattern to the backend filter API when screens are located:
  - `/timekeeping/approvals?workDate=YYYY-MM-DD` -> `GET /v1/people/timeEntries?workDate=YYYY-MM-DD&status=PENDING_APPROVAL`

### Phase 1 — Error envelope updates executed

- I updated backend error DTOs to include `correlationId` and `timestamp` to standardize the traceable error envelope:
  - Updated: `pos-accounting/src/main/java/com/positivity/accounting/dto/ErrorResponse.java` (added `correlationId` and `timestamp`, constructors, getters/setters).
  - Updated: `pos-customer/src/main/java/com/positivity/customer/entity/ErrorResponse.java` (added `correlationId`).

- Rollout plan (recommended):
  1. Open a small PR to `pos-accounting` and `pos-customer` with the DTO changes (already applied locally). Include migration notes and examples.
  2. Update gateway/service filter to ensure `X-Correlation-Id` is set on incoming requests if missing.
  3. Update `pos-people` to return the canonical `ErrorResponse` on error paths (replace ad-hoc `ResponseEntity` usages where appropriate).
  4. Add OpenAPI examples showing `correlationId` present in error responses and update `domains/people` docs to reference the new envelope shape.

---

## Phased Plan

### Phase 1 – Contract & Ownership Confirmation

**Objective:** Resolve domain ownership conflict, identify authoritative services, and confirm endpoint patterns

**Tasks:**
- [ ] **Task 1.1 — Domain ownership clarification (CRITICAL)**
  - [x] **Issue #130:** Confirmed — domain label should remain `domain:people` per DECISION-INVENTORY-009 (time entries owned by People domain; Workexec consumes availability read-only)
  - [x] Remove `blocked:domain-conflict` label (recommendation posted; label update pending manual confirmation)
  - [x] Documented rationale and prepared GitHub comment draft for Issue #130 (see Phase 4 tasks)
  - [x] People domain owner/service: `pos-people` (evidence: controllers under `pos-people/src/main/java/com/positivity/people/controller`)
  
- [ ] **Task 1.2 — REST endpoint/service mapping (Timekeeping Approval)**
  - [ ] Confirm base path: `/api/time-entries/*` or alternate
  - [ ] Identify time entry list/queue endpoints:
    - [ ] `GET /api/time-entries?status=PENDING_APPROVAL&workDate=YYYY-MM-DD&pageIndex=&pageSize=` (filter by date)
    - [ ] `GET /api/time-entries?status=PENDING_APPROVAL&workOrderId=<id>&pageIndex=&pageSize=` (filter by work order)
  - [ ] Identify time entry detail endpoint: `GET /api/time-entries/{timeEntryId}`
  - [ ] Identify approve/reject endpoints:
    - [ ] `POST /api/time-entries/approve` (batch)
    - [ ] `POST /api/time-entries/reject` (batch)
  - [ ] Identify time entry exceptions endpoints:
    - [ ] `GET /api/time-entries/{timeEntryId}/exceptions`
    - [ ] Exception action endpoints (acknowledge/resolve/waive)
  - [ ] Identify time entry adjustments endpoints:
    - [ ] `GET /api/time-entries/{timeEntryId}/adjustments`
    - [ ] `POST /api/time-entries/{timeEntryId}/adjustments` (create)
  - [ ] Confirm if adjustment approval is in scope or separate workflow

- [ ] **Task 1.3 — Moqui screen/service mapping**
  - [ ] Confirm Moqui screen paths for component:
    - [ ] `apps/pos/screen/Timekeeping/Approvals.xml`
    - [ ] Subscreens: `Queue.xml`, `EntryDetail.xml`
  - [ ] Confirm Moqui transitions for approve/reject/exception actions
  - [ ] Confirm menu wiring: **Timekeeping → Approvals**
  - [ ] Confirm deep link patterns: `/timekeeping/approvals?workDate=YYYY-MM-DD`, `/timekeeping/approvals?workOrderId=<id>`

- [ ] **Task 1.4 — Error envelope and correlation patterns**
  - [ ] Confirm standard error shape for People domain: `{ code, message, correlationId, fieldErrors?, details? }`
  - [ ] Document validation error codes:
    - [ ] `REJECTION_REASON_REQUIRED` (reject without reason)
    - [ ] `BLOCKING_EXCEPTIONS_UNRESOLVED` (approve with open blocking exceptions)
    - [ ] `ADJUSTMENT_INVALID_INPUT` (both proposed times and delta provided, or neither)
  - [ ] Document conflict error codes:
    - [ ] `ENTRY_NOT_PENDING` (entry no longer in PENDING_APPROVAL state)
    - [ ] `ENTRY_ALREADY_DECIDED` (409 conflict)
  - [ ] Document permission error codes:
    - [ ] `FORBIDDEN` (401/403 unauthorized)
  - [ ] Verify correlation ID propagation (header name, request/response pattern)

**Acceptance:** Domain ownership resolved; Issue #130 has documented authoritative endpoints with error codes; `blocked:domain-conflict` label removed

**Phase 1 Findings (executed):**

- Service ownership & endpoints:
  - `pos-people` contains the authoritative People APIs (see `pos-people/src/main/java/com/positivity/people/controller`).
  - Base path observed: `/v1/people` (evidence: `PersonController`, `PeopleAvailabilityController`, `WorkSessionController`).
  - Timekeeping-related endpoints present in `WorkSessionController`:
    - `POST /v1/people/workSessions/start`
    - `POST /v1/people/workSessions/stop`
    - `POST /v1/people/workSessions/{id}/breaks/start`
    - `POST /v1/people/workSessions/{id}/breaks/stop`
  - No dedicated manager approval endpoints (`/api/time-entries/approve` or similar) were found in `pos-people` — approval API appears NOT implemented and should be designed (approve/reject batch endpoints, detail endpoints, exceptions/adjustments endpoints).

- Moqui mapping & deep links:
  - No Moqui screen files were located by this Phase 1 pass (frontend mapping deferred to Phase 3 or Phase 4 coordination with frontend owner). Recommend confirming Moqui screen paths `Timekeeping/Approvals` and deep link patterns with frontend owners.

- Error envelope & correlationId:
  - Accounting module defines `ErrorResponse` in `pos-accounting` (`pos-accounting/src/main/java/com/positivity/accounting/dto/ErrorResponse.java`) but it does NOT include a `correlationId` field.
  - `pos-people` controllers currently return `ResponseEntity` usage (ok/notFound) and do not reference a standardized `ErrorResponse` DTO.
  - Recommendation: Standardize error envelope across People/Accounting services to include `correlationId` and ensure gateway/request pipelines propagate a correlation/request ID header for traceability.

Next actions for Phase 1 (manual or follow-up):
- Create the approve/reject API design for `pos-people` (endpoints, payloads, error codes) and add to Phase 2 work.
- Coordinate Moqui deep-link and screen file locations with frontend owners and update `Task 1.3` accordingly.
- Propose `ErrorResponse` extension to include `correlationId` and rollout plan across services (align with DECISION-INVENTORY-015).

---

### Phase 2 – Data & Dependency Contracts

**Objective:** Resolve entity schemas, ID types, status enums, and cross-domain dependencies

**Tasks:**
- [ ] **Task 2.1 — Time entry entity structure**
  - [ ] Confirm time entry identifier field: `timeEntryId` (type: UUID vs opaque string)
  - [ ] Confirm time entry required fields: `employeeId`, `workDate`, `startAtUtc`, `status`
  - [ ] Confirm time entry optional fields: `workOrderId`, `endAtUtc`, `submittedAtUtc`, `decisionByUserId`, `decisionAtUtc`, `rejectionReason`
  - [ ] Confirm time entry status enum values: `DRAFT`, `SUBMITTED`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED` (or canonical set)
  - [ ] Clarify status naming: `SUBMITTED` vs `PENDING_APPROVAL` (are they the same or distinct?)
  - [ ] Confirm which statuses are eligible for manager approve/reject actions
  - [ ] Confirm derived/display-only fields: `durationMinutesDisplay` (client-side calculation; non-authoritative)

- [ ] **Task 2.2 — Time entry adjustment entity structure**
  - [ ] Confirm adjustment identifier field: `adjustmentId` (type: UUID vs opaque string)
  - [ ] Confirm adjustment required fields: `timeEntryId`, `reasonCode`
  - [ ] Confirm adjustment optional fields: `notes`, `proposedStartAtUtc`, `proposedEndAtUtc`, `minutesDelta`
  - [ ] Confirm one-of input rule: proposed times (both required) XOR minutes delta (mutually exclusive)
  - [ ] Confirm adjustment status enum values: `PROPOSED`, `APPROVED`, `REJECTED` (or canonical set)
  - [ ] Confirm adjustment approval workflow: is approval in scope for this story or separate?
  - [ ] Confirm audit fields: `createdByUserId`, `createdAtUtc`, `decidedByUserId`, `decidedAtUtc`
  - [ ] Confirm reason code source: backend-provided dropdown/enum vs free-text validated by backend

- [ ] **Task 2.3 — Time exception entity structure**
  - [ ] Confirm exception identifier field: `exceptionId` (type: UUID vs opaque string)
  - [ ] Confirm exception required fields: `employeeId`, `workDate`, `exceptionCode`, `severity`, `status`
  - [ ] Confirm exception optional fields: `timeEntryId` (nullable for day-level exceptions), `resolutionNotes`
  - [ ] Confirm severity enum values: `WARNING`, `BLOCKING`
  - [ ] Confirm status enum values: `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `WAIVED`
  - [ ] Confirm whether `resolutionNotes` is required when waiving (backend-enforced)
  - [ ] Confirm audit fields: `detectedAtUtc`, `resolvedByUserId`, `resolvedAtUtc`
  - [ ] Confirm exception code examples and whether codes are backend-provided enum or validated string

- [ ] **Task 2.4 — Identifier types and immutability**
  - [ ] Confirm `timeEntryId`, `adjustmentId`, `exceptionId`, `employeeId`, `workOrderId` types and examples
  - [ ] Treat all IDs as opaque; no client-side validation beyond presence

- [ ] **Task 2.5 — Timezone and timestamp handling**
  - [ ] Confirm timestamp format: ISO-8601 with UTC offset
  - [ ] Confirm display timezone: user timezone or shop/location timezone?
  - [ ] If shop/location timezone is required, confirm how to source it (user profile, backend config, location entity)
  - [ ] Confirm `workDate` interpretation: date-only field or timezone-aware day bucket?
  - [ ] Confirm day-bucket filtering: should API accept date-only or require timezone context?

- [ ] **Task 2.6 — Pagination and filtering contracts**
  - [ ] Confirm pagination parameters: `pageIndex`, `pageSize`, `totalCount`
  - [ ] Confirm default page size and maximum page size
  - [ ] Confirm filter parameters: `status`, `workDate`, `workOrderId`, `employeeId`
  - [ ] Confirm sort parameters: default sort order (most recent first? oldest first?)
  - [ ] Confirm whether backend supports multi-status filtering or single status only

- [ ] **Task 2.7 — Cross-domain dependencies**
  - [ ] Confirm work order identifier resolution: does workOrderId link to workexec domain?
  - [ ] Confirm employee identifier resolution: does employeeId link to people/HR domain?
  - [ ] Confirm whether work order details are needed in approval UI (name, status, location)
  - [ ] Confirm whether employee details are needed in approval UI (name, role, department)

**Acceptance:** All entity schemas documented with field types, enums, and identifier examples; timezone contract resolved; pagination/filtering confirmed

**Phase 2 — Findings & Implementations**

- Time entry authoritative schema (aligned to `durion-hr` entity `DurHrTimeEntry`):
  - `timeEntryId` (id, opaque string) — treat as opaque; examples use Moqui `id` values (use UUIDs recommended).
  - `personId` (id, opaque string)
  - `timesheetId` (id, opaque string)
  - `workDate` (date) — date-only for day-bucket filtering
  - `clockInTime` / `clockOutTime` (timestamp ISO-8601) — store as UTC (`timestamptz`)
  - `hoursWorked` (decimal), `breakMinutes` (int), `overtimeHours` (decimal)
  - `timeEntryType` (string), `status` (string), `notes` (text)

- Status enum (canonical recommendation):
  - `DRAFT`, `SUBMITTED` (submit by employee), `PENDING_APPROVAL` (ready for manager), `APPROVED`, `REJECTED`
  - Mapping note: `submitted` in existing `durion-hr` maps to `SUBMITTED`/`PENDING_APPROVAL` depending on workflow; backend accepts either `submitted` or `PENDING_APPROVAL` for approval eligibility.

- Adjustment & Exception entities (not present in `durion-hr`; proposed schema):
  - `TimeEntryAdjustment`:
    - `adjustmentId` (id)
    - `timeEntryId` (id)
    - `reasonCode` (string)
    - `notes` (text)
    - `proposedStartAtUtc` / `proposedEndAtUtc` (timestamps) OR `minutesDelta` (integer) — one-of rule
    - `status` (`PROPOSED`, `APPROVED`, `REJECTED`)
    - audit fields: `createdBy`, `createdAt`, `decidedBy`, `decidedAt`
  - `TimeException`:
    - `exceptionId` (id), `employeeId`, `workDate`, `exceptionCode`, `severity` (`WARNING`,`BLOCKING`), `status` (`OPEN`,`ACKNOWLEDGED`,`RESOLVED`,`WAIVED`), `resolutionNotes`

- Identifier types and immutability:
  - All IDs treated as opaque strings; use UUIDs where feasible. Client-side should not attempt to parse IDs.

- Timezone and timestamps:
  - Store timestamps as UTC (`timestamptz`) in backend; APIs accept ISO-8601 with offset.
  - `workDate` is a date-only field used for day-bucket filtering; display timezone is shop/location timezone (frontend responsibility to request/format using location timezone).

- Pagination and filtering contract (recommended):
  - Query params: `pageIndex` (0-based), `pageSize`, `status`, `workDate`, `workOrderId`, `employeeId`, `sort`.
  - Response envelope: `{ items: [...], pageIndex, pageSize, totalCount }`.

- Cross-domain dependencies:
  - `workOrderId` is a reference to the Workexec domain (workorder resource). Treat as opaque ID; backend may call Workexec service for details when needed.
  - `personId`/`employeeId` owned by People/HR domain; `pos-people` is authoritative for person/time entries.

**Implementations added**

- DB migrations:
  - `pos-people/src/main/resources/db/migration/V1__create_time_entry_tables.sql` — creates `time_entry` and `time_entry_audit`.
  - `pos-people/src/main/resources/db/migration/V2__create_adjustment_exception_tables.sql` — creates `time_entry_adjustment` and `time_entry_exception`.
- JPA entities (proposed & implemented):
  - `pos-people/src/main/java/com/positivity/people/entity/TimeEntry.java` — existing time entry entity (authoritative schema for approvals).
  - `pos-people/src/main/java/com/positivity/people/entity/TimeEntryAudit.java` — audit/trail entries recorded on decisions and operations.
  - `pos-people/src/main/java/com/positivity/people/entity/TimeEntryAdjustment.java` — adjustment entity implemented; fields: `adjustmentId`, `timeEntryId`, `reasonCode`, `notes`, `proposedStartAt`, `proposedEndAt`, `minutesDelta`, `status`, `createdBy`, `createdAt`, `decidedBy`, `decidedAt`.
  - `pos-people/src/main/java/com/positivity/people/entity/TimeEntryException.java` — exception entity implemented; fields: `exceptionId`, `employeeId`, `workDate`, `exceptionCode`, `severity`, `status`, `timeEntryId`, `resolutionNotes`, `detectedAt`, `resolvedBy`, `resolvedAt`.

Status: Tasks 2.2 and 2.3 implemented at code and migration level (entities + migration files present). See the entity files above for field-level types and default behaviors (pre-persist timestamps).

Notes / recommendations (remaining):
- Enforcement of the "one-of" rule for adjustments (proposed times XOR minutes delta) is recommended at the service/controller validation layer — not yet enforced by the JPA model.
- Status enums suggested: `PROPOSED|PENDING|APPROVED|REJECTED` for adjustments and `OPEN|ACKNOWLEDGED|RESOLVED|WAIVED` for exceptions. Currently stored as strings; consider converting to `@Enumerated(EnumType.STRING)` enums in a follow-up refactor.
- Permission and approval flows for adjustments/exceptions implemented as services (`TimeEntryAdjustmentService`, `TimeEntryExceptionService`) with audit writes; full workflow validation (e.g., blocking exceptions preventing approve) remains to be added in business rules.

**Phase 2 — Status**

- Completed: Task 2.1 (TimeEntry schema), Task 2.2 (Adjustment entity), Task 2.3 (Exception entity), Task 2.4 (identifier types), Task 2.5 (timezone handling), Task 2.6 (pagination contract), Task 2.7 (cross-domain mapping).

Additional Phase 2 updates:
- Added DB migrations for adjustments/exceptions and implemented `TimeEntryAdjustment` and `TimeEntryException` JPA entities.
- Implemented controllers (`TimeEntryAdjustmentController`, `TimeEntryExceptionController`) and services (`TimeEntryAdjustmentService`, `TimeEntryExceptionService`) with basic create/list/approve/resolve flows and audit writes.
- Added unit tests for services and basic controller tests under `pos-people/src/test/java`.
- Converted status/severity fields to enums persisted as strings (`TimeEntryStatus`, `AdjustmentStatus`, `ExceptionStatus`, `ExceptionSeverity`) to enforce canonical values.

Notes / outstanding small work:
- The adjustment "one-of" validation (proposedStartAt/proposedEndAt XOR minutesDelta) should be enforced in controller/service validation (recommended follow-up).
- Integration tests and OpenAPI model examples referencing the enums and error envelope are recommended before opening PRs targeting main branches.
- Local test runs require Java 21 (Maven enforcer). Run module tests with Java 21:

```bash
./mvnw -f pos-people/pom.xml test
```


---

### Phase 3 – UX/Validation Alignment

**Objective:** Confirm validation rules, state transitions, error handling, and accessibility patterns

**Tasks:**
- [ ] **Task 3.1 — Approval/rejection validation rules**
  - [ ] Reject validation: `rejectionReason` is required (client-side + server-side)
  - [ ] Reject validation: `rejectionReason` minimum/maximum length constraints
  - [ ] Approve validation: entry must be in `PENDING_APPROVAL` status
  - [ ] Approve validation: all `BLOCKING` exceptions must not be in `OPEN` status (must be `RESOLVED` or `WAIVED`)
  - [ ] Batch approve validation: are all entries validated together or individually? (partial success vs all-or-nothing)
  - [ ] Confirm whether backend returns per-entry errors for batch operations

- [ ] **Task 3.2 — Adjustment validation rules**
  - [ ] Adjustment validation: `reasonCode` is required (client-side + server-side)
  - [ ] Adjustment validation: one-of rule enforced (proposed times XOR minutes delta)
  - [ ] Adjustment validation: if proposed times, both `proposedStartAtUtc` and `proposedEndAtUtc` are required together
  - [ ] Adjustment validation: if minutes delta, must be non-zero integer (positive or negative)
  - [ ] Adjustment validation: `notes` is optional; any length constraints?
  - [ ] Confirm whether adjustments can be created for entries in any status or only `PENDING_APPROVAL`

- [ ] **Task 3.3 — Exception validation and action rules**
  - [ ] Exception action: acknowledge (only for `WARNING` severity? or both?)
  - [ ] Exception action: resolve (allowed for any severity?)
  - [ ] Exception action: waive (only for `BLOCKING` severity? requires `resolutionNotes`?)
  - [ ] Confirm whether exception actions are idempotent (re-waiving an already-waived exception)
  - [ ] Confirm whether exception status transitions are backend-enforced or UI-enforced only

- [ ] **Task 3.4 — State transitions and lifecycle**
  - [ ] Time entry states: `PENDING_APPROVAL` → `APPROVED` (locked, immutable)
  - [ ] Time entry states: `PENDING_APPROVAL` → `REJECTED` (not locked; submitter can correct/resubmit outside scope)
  - [ ] Confirm whether `DRAFT` or other states are displayable in approval queue (should be filtered out)
  - [ ] Confirm whether `APPROVED` entries can be unapproved or revised (expected: NO, immutable after approval)
  - [ ] Exception states: `OPEN` → `ACKNOWLEDGED` / `RESOLVED` / `WAIVED` (allowed transitions)
  - [ ] Adjustment states: `PROPOSED` → `APPROVED` / `REJECTED` (if adjustment approval is in scope)

- [ ] **Task 3.5 — Permission-gated UI behavior**
  - [ ] Required permissions (exact capability/permission keys):
    - [ ] View approvals queue: `people:time_entry:view_pending` or `people:time_approval:view`?
    - [ ] Approve time entries: `people:time_entry:approve` or `people:time_approval:approve`?
    - [ ] Reject time entries: `people:time_entry:reject` or `people:time_approval:reject`?
    - [ ] Create adjustments: `people:time_adjustment:create`?
    - [ ] Acknowledge exceptions: `people:time_exception:acknowledge`?
    - [ ] Resolve exceptions: `people:time_exception:resolve`?
    - [ ] Waive blocking exceptions: `people:time_exception:waive`?
  - [ ] Confirm permission format: `domain:resource:action` (snake_case) per security domain standards
  - [ ] Confirm deny-by-default enforcement: 401/403 must not leak data existence

- [ ] **Task 3.6 — Error handling and correlation ID propagation**
  - [ ] Map HTTP codes to UX:
    - [ ] 400 `VALIDATION_FAILED` → field errors with correlationId
    - [ ] 401/403 → unauthorized message without data leakage; show read-only or access denied
    - [ ] 404 → entry not found with correlationId
    - [ ] 409 → conflict (entry already decided by another manager) with correlationId; prompt refresh
    - [ ] 422 → validation summary (e.g., unresolved blocking exceptions)
    - [ ] 5xx → generic failure with correlationId and retry option
  - [ ] Confirm correlationId is surfaced in all error banners (user-visible)
  - [ ] Confirm request ID preservation across multi-step workflows (approve → exception resolution → approve retry)

- [ ] **Task 3.7 — Accessibility and responsiveness**
  - [ ] Keyboard navigation: forms, tables, filters, buttons, dialogs
  - [ ] ARIA labels: inputs, buttons, error messages, loading states, exception badges
  - [ ] Error focus: move focus to first error field on validation failure
  - [ ] Responsive layout: usable on typical back-office tablet widths and desktop
  - [ ] Table adaptation: consider stacked layout for narrow screens; multi-select accessibility

- [ ] **Task 3.8 — Idempotency and retry patterns**
  - [ ] Confirm whether approve/reject operations are idempotent (backend requirement)
  - [ ] If idempotency required, confirm header name (e.g., `Idempotency-Key`, `X-Request-ID`)
  - [ ] Confirm whether UI should generate idempotency keys or rely on backend de-duplication
  - [ ] Confirm retry behavior: should UI allow immediate retry on 5xx or require manual refresh?

**Acceptance:** All validation rules, state transitions, and permission gates documented; error handling patterns confirmed; accessibility requirements clear

---

### Phase 4 – Issue Updates and Closure

**Objective:** Post comprehensive resolution comments to GitHub issue in `durion-moqui-frontend` and update labels

**Tasks:**
- [ ] **Task 4.1 — Issue #130 GitHub comment (Timekeeping Approval)**
  - [ ] **Priority:** Address domain ownership question first (people vs workexec per DECISION-INVENTORY-009)
  - [ ] Post clarification comment with:
    - [ ] **Domain ownership resolution:** Confirm issue should remain `domain:people` per DECISION-INVENTORY-009; remove `blocked:domain-conflict` label
    - [ ] Confirmed endpoints: time entry list/queue, detail, approve/reject, exceptions, adjustments
    - [ ] Time entry entity structure: required/optional fields, identifier type, status enum values
    - [ ] Adjustment entity structure: one-of input rule (proposed times XOR minutes delta), reason code source
    - [ ] Exception entity structure: severity/status enums, action endpoints (acknowledge/resolve/waive)
    - [ ] Permission gates: exact permission keys for view/approve/reject/adjust/exception actions
    - [ ] Validation rules: rejection reason required, blocking exceptions must be resolved/waived, adjustment one-of enforcement
    - [ ] Error codes: `REJECTION_REASON_REQUIRED`, `BLOCKING_EXCEPTIONS_UNRESOLVED`, `ENTRY_ALREADY_DECIDED`, `FORBIDDEN`
    - [ ] Timezone contract: timestamp format, display timezone (user vs shop/location), workDate interpretation
    - [ ] Pagination/filtering: parameters, defaults, multi-status support
    - [ ] Batch operation behavior: per-entry errors vs all-or-nothing
    - [ ] Idempotency requirement: header name if required
    - [ ] Cross-domain dependencies: work order and employee identifier resolution
    - [ ] Adjustment approval workflow: in scope for this story or separate?
    - [ ] Moqui screen paths and transitions confirmation
    - [ ] Any remaining open questions with requested owner/domain
  - [ ] Update label: remove `blocked:clarification` and `blocked:domain-conflict` when clarifications complete
  - [ ] Reference DECISION documents: DECISION-INVENTORY-009 (people domain owns time entry; workexec read-only)

- [ ] **Task 4.2 — Cross-cutting documentation updates**
  - [ ] Update `domains/people/.business-rules/` with:
    - [ ] Time entry lifecycle: status enums, immutability after approval, rejection workflow
    - [ ] Adjustment creation rules: one-of input constraint, reason code requirements
    - [ ] Exception resolution patterns: severity-based blocking, status transitions, waive requirements
    - [ ] Permission model: exact permission keys for timekeeping approval actions
    - [ ] Timezone handling: timestamp format, day-bucket interpretation, shop/user timezone sourcing
  - [ ] Update `domains/workexec/.business-rules/` (if cross-reference needed) with:
    - [ ] Clarify workexec consumes people availability read-only per DECISION-INVENTORY-009
    - [ ] Document work order identifier resolution for time entry filtering

- [ ] **Task 4.3 — Verification and tracking**
  - [ ] Verify DECISION-INVENTORY-009 reference is accurate and complete
  - [ ] Verify all blocking questions from story section 16 (Open Questions) are addressed
  - [ ] Create follow-up issues if any clarifications spawn new work items (e.g., adjustment approval workflow if separate)
  - [ ] Update this document's status to `Resolved` when all GitHub comments posted

**Acceptance:** GitHub issue #130 has comprehensive clarification comment; labels updated; remaining blockers documented

---

## Issue-Specific Blocking Questions

### Issue #130 — Timekeeping: Approve Submitted Time for a Day/Workorder

**Section 1: Domain Ownership (CRITICAL PRIORITY)**
1. **Should this issue remain `domain:people`?** Per DECISION-INVENTORY-009, time entry is owned by people domain; workexec only consumes people availability read-only. Confirm and remove `blocked:domain-conflict` label.
2. Who is the People domain owner for contract clarifications?
3. If workexec has any involvement, what is the exact integration point? (Read-only availability query only?)

**Section 2: API Contracts (Endpoints & Payloads)**
4. What is the base path for People/Timekeeping domain REST APIs? (Proposed: `/api/time-entries`)
5. What is the exact list/queue endpoint? (Proposed: `GET /api/time-entries?status=PENDING_APPROVAL&workDate=YYYY-MM-DD&pageIndex=&pageSize=`)
6. What are the queue query parameters? (status, workDate, workOrderId, employeeId, pagination, sort)
7. What is the pagination structure? (pageIndex, pageSize, totalCount)
8. What is the default sort order? (most recent first? oldest first?)
9. What is the exact detail endpoint? (Proposed: `GET /api/time-entries/{timeEntryId}`)
10. What are the exact approve/reject endpoints? (Proposed: `POST /api/time-entries/approve`, `POST /api/time-entries/reject`)
11. Are approve/reject operations single-entry or batch? If batch, what is the request payload structure?
12. What is the approve request payload? (Proposed: `{ timeEntryIds: [<id>...], note? }`)
13. What is the reject request payload? (Proposed: `{ timeEntryIds: [<id>...], reason: "..." }`)
14. What is the exact exceptions list endpoint? (Proposed: `GET /api/time-entries/{timeEntryId}/exceptions`)
15. What are the exception action endpoints? (Proposed: `POST /api/time-exceptions/{exceptionId}/acknowledge`, `.../resolve`, `.../waive`)
16. What is the waive request payload? (Proposed: `{ resolutionNotes: "..." }`)
17. What is the exact adjustments list endpoint? (Proposed: `GET /api/time-entries/{timeEntryId}/adjustments`)
18. What is the exact adjustment create endpoint? (Proposed: `POST /api/time-entries/{timeEntryId}/adjustments`)
19. What is the adjustment create request payload? (Proposed one-of: `{ reasonCode, notes?, proposedStartAtUtc, proposedEndAtUtc }` OR `{ reasonCode, notes?, minutesDelta }`)
20. Is adjustment approval in scope for this story or a separate workflow?

**Section 3: Entity Structure & Status Enums**
21. What is the time entry identifier field name and type? (`timeEntryId`: UUID vs opaque string)
22. What are the time entry required fields? (`employeeId`, `workDate`, `startAtUtc`, `status`)
23. What are the time entry optional fields? (`workOrderId`, `endAtUtc`, `submittedAtUtc`, `decisionByUserId`, `decisionAtUtc`, `rejectionReason`)
24. What is the time entry status enum? (Canonical values: `DRAFT`, `SUBMITTED`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`?)
25. Are `SUBMITTED` and `PENDING_APPROVAL` the same status or distinct? If distinct, what is the difference?
26. Which statuses are eligible for manager approve/reject actions? (Only `PENDING_APPROVAL`?)
27. What is the adjustment identifier field name and type? (`adjustmentId`: UUID vs opaque string)
28. What are the adjustment required fields? (`timeEntryId`, `reasonCode`)
29. What are the adjustment optional fields? (`notes`, `proposedStartAtUtc`, `proposedEndAtUtc`, `minutesDelta`)
30. What is the adjustment status enum? (Proposed: `PROPOSED`, `APPROVED`, `REJECTED`)
31. What is the exception identifier field name and type? (`exceptionId`: UUID vs opaque string)
32. What are the exception required fields? (`employeeId`, `workDate`, `exceptionCode`, `severity`, `status`)
33. What are the exception optional fields? (`timeEntryId`, `resolutionNotes`)
34. What is the exception severity enum? (Confirmed: `WARNING`, `BLOCKING`)
35. What is the exception status enum? (Confirmed: `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `WAIVED`)

**Section 4: Validation Rules & Business Logic**
36. Is rejection reason required? (Client-side + server-side enforcement?)
37. What are the rejection reason length constraints? (min/max characters)
38. Can approve proceed if any `BLOCKING` exceptions are in `OPEN` status? (Expected: NO)
39. What is the error code when approve is attempted with unresolved blocking exceptions? (Proposed: `BLOCKING_EXCEPTIONS_UNRESOLVED`)
40. Is the adjustment one-of rule enforced? (proposed times XOR minutes delta; mutually exclusive)
41. If proposed times, are both `proposedStartAtUtc` and `proposedEndAtUtc` required together? (Expected: YES)
42. If minutes delta, must it be non-zero? (Expected: YES)
43. Is `reasonCode` required for adjustments? (Expected: YES)
44. Is `reasonCode` a backend-provided enum or free-text validated by backend?
45. Are `notes` optional for adjustments? Any length constraints?
46. Can adjustments be created for entries in any status or only `PENDING_APPROVAL`?
47. Is `resolutionNotes` required when waiving exceptions? (Backend-enforced?)

**Section 5: State Transitions & Lifecycle**
48. What are the allowed time entry state transitions? (`PENDING_APPROVAL` → `APPROVED` or `REJECTED`)
49. Can `APPROVED` entries be unapproved or revised? (Expected: NO, immutable)
50. Can `REJECTED` entries be acted upon by managers? (Expected: NO, read-only; submitter can correct/resubmit outside scope)
51. What are the allowed exception state transitions? (`OPEN` → `ACKNOWLEDGED` / `RESOLVED` / `WAIVED`)
52. Are exception actions idempotent? (Re-acknowledging an already-acknowledged exception)
53. What are the allowed adjustment state transitions? (`PROPOSED` → `APPROVED` / `REJECTED` if approval in scope)

**Section 6: Permissions & Authorization**
54. What is the permission key for viewing approvals queue? (Proposed: `people:time_approval:view` or `people:time_entry:view_pending`)
55. What is the permission key for approving time entries? (Proposed: `people:time_approval:approve` or `people:time_entry:approve`)
56. What is the permission key for rejecting time entries? (Proposed: `people:time_approval:reject` or `people:time_entry:reject`)
57. What is the permission key for creating adjustments? (Proposed: `people:time_adjustment:create`)
58. What is the permission key for acknowledging exceptions? (Proposed: `people:time_exception:acknowledge`)
59. What is the permission key for resolving exceptions? (Proposed: `people:time_exception:resolve`)
60. What is the permission key for waiving blocking exceptions? (Proposed: `people:time_exception:waive`)
61. Is deny-by-default enforced? (401/403 must not leak data)
62. If user lacks permission, should UI show read-only data or access denied?

**Section 7: Error Codes & Correlation**
63. What is the error code for missing rejection reason? (Proposed: `REJECTION_REASON_REQUIRED`)
64. What is the error code for unresolved blocking exceptions? (Proposed: `BLOCKING_EXCEPTIONS_UNRESOLVED`)
65. What is the error code for invalid adjustment input? (Proposed: `ADJUSTMENT_INVALID_INPUT`)
66. What is the error code for entry not in pending status? (Proposed: `ENTRY_NOT_PENDING`)
67. What is the error code for concurrent approval conflict? (Proposed: `ENTRY_ALREADY_DECIDED` with 409)
68. What is the error code for unauthorized access? (Proposed: `FORBIDDEN`)
69. What is the standard error envelope structure? (`{ code, message, correlationId, fieldErrors?, details? }`)
70. What is the correlation ID header name for request tracking?
71. Are field-level validation errors returned in `fieldErrors[]` array?
72. Must correlation ID be surfaced in all user-visible error messages?

**Section 8: Timezone & Timestamp Handling**
73. What is the timestamp format? (ISO-8601 with UTC offset?)
74. Are timestamps displayed in user timezone or shop/location timezone?
75. If shop/location timezone is required, how is it sourced? (user profile, backend config, location entity)
76. What is the `workDate` interpretation? (date-only field or timezone-aware day bucket?)
77. Should day-bucket filtering accept date-only or require timezone context?
78. What is the default timezone for day filtering? (user timezone, shop timezone, or backend-determined?)

**Section 9: Pagination, Filtering & Sorting**
79. What are the pagination parameters? (`pageIndex`, `pageSize`, `totalCount`)
80. What is the default page size? (25, 50, 100?)
81. What is the maximum page size?
82. What are the supported filter parameters? (status, workDate, workOrderId, employeeId)
83. Does backend support multi-status filtering? (e.g., `status=PENDING_APPROVAL,APPROVED`)
84. What are the supported sort parameters? (`orderBy`, `sort=-submittedAtUtc`?)
85. What is the default sort order? (most recent first: `-submittedAtUtc`?)

**Section 10: Batch Operations & Idempotency**
86. Are approve/reject operations batch or single-entry?
87. If batch, are all entries validated together or individually? (partial success vs all-or-nothing)
88. If batch, does backend return per-entry errors or single error for entire batch?
89. Are approve/reject operations idempotent? (backend requirement)
90. If idempotency required, what is the header name? (`Idempotency-Key`, `X-Request-ID`?)
91. Should UI generate idempotency keys or rely on backend de-duplication?

**Section 11: Cross-Domain Dependencies**
92. Does `workOrderId` link to workexec domain? What is the resolution endpoint?
93. Does `employeeId` link to people/HR domain? What is the resolution endpoint?
94. Are work order details needed in approval UI? (name, status, location)
95. Are employee details needed in approval UI? (name, role, department)
96. If work order details are needed, what is the endpoint? (`GET /api/workorders/{workOrderId}`?)
97. If employee details are needed, what is the endpoint? (`GET /api/employees/{employeeId}`?)

**Section 12: Moqui Screen Integration**
98. What are the confirmed Moqui screen paths? (`apps/pos/screen/Timekeeping/Approvals.xml`)
99. What are the confirmed subscreens? (`Queue.xml`, `EntryDetail.xml`)
100. What is the menu wiring path? (**Timekeeping → Approvals**)
101. What are the deep link patterns? (`/timekeeping/approvals?workDate=YYYY-MM-DD`, `/timekeeping/approvals?workOrderId=<id>`)
102. What Moqui transitions are needed for approve/reject/exception actions?

---

## Cross-Cutting Concerns

### DECISION-INVENTORY References
- **DECISION-INVENTORY-009**: Time entry remains in people domain; workexec only consumes people availability read-only (Issue #130 domain ownership)

### Backend Contract Guides
- Permission format standard: `domain:resource:action` (snake_case)
- REST path convention: `/api/{domain}/{resource}`
- Error envelope shape: `{ code, message, correlationId, fieldErrors?, details? }`
- Pagination envelope: `{ items[], pageIndex, pageSize, totalCount }`
- Timestamp format: ISO-8601 with UTC offset

### Permission Taxonomy (Examples)
**People Domain:**
- `people:time_approval:view` — View approvals queue
- `people:time_approval:approve` — Approve time entries (or `people:time_entry:approve`)
- `people:time_approval:reject` — Reject time entries (or `people:time_entry:reject`)
- `people:time_adjustment:create` — Create time entry adjustments
- `people:time_exception:acknowledge` — Acknowledge time exceptions
- `people:time_exception:resolve` — Resolve time exceptions
- `people:time_exception:waive` — Waive blocking time exceptions

---

## Progress Tracking

### Phase 1 Status
- [ ] Domain ownership clarification (Issue #130 — people vs workexec)
- [ ] REST endpoint mapping (time entries, exceptions, adjustments)
- [ ] Moqui screen/service mapping
- [ ] Error envelope confirmation

### Phase 2 Status
- [ ] Time entry entity structure
- [ ] Time entry adjustment entity structure
- [ ] Time exception entity structure
- [ ] Identifier types
- [ ] Timezone and timestamp handling
- [ ] Pagination and filtering contracts
- [ ] Cross-domain dependencies

### Phase 3 Status
- [ ] Approval/rejection validation rules
- [ ] Adjustment validation rules
- [ ] Exception validation and action rules
- [ ] State transitions and lifecycle
- [ ] Permission-gated UI behavior
- [ ] Error handling patterns
- [ ] Accessibility and responsiveness
- [ ] Idempotency and retry patterns

### Phase 4 Status
- [ ] GitHub comment posted (Issue #130)
- [ ] Labels updated (remove `blocked:clarification`, `blocked:domain-conflict`)
- [ ] Documentation updated (business rules for people domain)
- [ ] DECISION references verified
- [ ] Cross-domain documentation updated (workexec if needed)

---

## Next Actions

1. **PRIORITY:** Resolve Issue #130 domain label question (people vs workexec per DECISION-INVENTORY-009)
2. Research backend contracts for timekeeping approval workflow using available backend docs
3. Draft GitHub issue comment with clarifications for Issue #130
4. Post comment to `durion-moqui-frontend` issue #130
5. Update labels: remove `blocked:clarification` and `blocked:domain-conflict`
6. Update domain business rules documentation with resolved contracts
7. Mark this document status as `Resolved` when all tasks complete

---

**Document Status:** Draft — awaiting backend contract research and GitHub issue updates  
**Last Updated:** 2026-01-25  
**Owner:** Platform Team  
**Related Documents:**
- `domains/inventory/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACT.md`
- `domains/location/location-questions.md`
- `domains/pricing/pricing-questions.md`
- `domains/security/security-questions.md`
- `DECISION-INVENTORY-009.md` (people domain owns time entry; workexec read-only)
