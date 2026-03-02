---
name: "CAP-140 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-140 Workorder Execution Context Linking."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-140 runs.

## Capability Scope
- Capability: `CAP:140` — Workorder Execution Context Linking
- Parent issue: `louisburroughs/durion#140`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#58` — CrossDomain: HR Ingests Work Sessions from Shopmgr (`domain:people`)
  - `louisburroughs/durion-positivity-backend#59` — CrossDomain: Workexec Displays Operational Context in Workorder View (`domain:workexec`)
  - `louisburroughs/durion-positivity-backend#63` — Workexec: Update Appointment Status from Workexec Events (`domain:workexec` → `shopmgmt`)
  - `louisburroughs/durion-positivity-backend#64` — Workexec: Propagate Assignment Context to Workorder (`domain:workexec`)
  - `louisburroughs/durion-positivity-backend#65` — Workexec: Create Draft Estimate from Appointment (`domain:workexec`)

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-140/CAPABILITY_MANIFEST.yaml`
2. Story issues:
   - `#58`, `#59`, `#63`, `#64`, `#65` in `durion-positivity-backend`
3. Domain guides and contract references:
   - `durion/domains/workexec/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/DOMAIN_NOTES.md`
   - `durion/domains/shopmgmt/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/shopmgmt/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/shopmgmt/.business-rules/DOMAIN_NOTES.md`
   - `durion/domains/people/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/people/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/people/.business-rules/DOMAIN_NOTES.md`

## Decision Precedence Rules (Hard)
- Story bodies contain resolved clarifications (e.g., Clarification #406 for issue #58). Treat those as binding implementation rules — do not re-debate decisions already resolved in issue comments.
- If a story body contains a "Resolved Decisions" or "Decision Record" section, those decisions are authoritative and must be cited in the plan slice that implements them.
- ADRs 0006 (workexec boundaries), 0017 (HTTP response codes), and 0018 (audit actor from security context) are all in scope.

## Module and Ownership Guidance
- `pos-workorder`: owns Estimate and Workorder lifecycle. Target for stories #59, #64, #65.
  - `Workorder` entity already exists with `status`, `estimateId`, `customerId`, `vehicleId`, `shopId`.
  - `Estimate` entity already exists with `status`, `customerId`, `vehicleId`, `locationId`. No `appointmentId` field yet.
  - `WorkorderStatus` enum: `DRAFT`, `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`.
- `pos-shop-manager`: owns Appointment lifecycle. Target for story #63.
  - `Appointment` entity already exists with `AppointmentStatus` enum: `SCHEDULED`, `CONFIRMED`, `IN_PROGRESS`, `AWAITING_PARTS`, `COMPLETED`, `CANCELLED`.
  - Story #63 requires expanding `AppointmentStatus` to the full 10-value set from the issue.
- `pos-people`: owns TimekeepingEntry and HR payroll lifecycle. Target for story #58.
  - `WorkSession` entity and `WorkSessionService` already exist. No `TimekeepingEntry` yet.
  - Event ingestion (WorkSessionCompleted → TimekeepingEntry) is new infrastructure for this module.

## Story Sequencing (Planner Input)
Stories are across three modules and can be partially parallelised. Recommended ordering:

1. **#65** (pos-workorder): Create Draft Estimate from Appointment — self-contained, only adds `appointmentId` field and idempotency logic to existing Estimate infrastructure.
2. **#64** (pos-workorder): Propagate Assignment Context to Workorder — adds `locationId`, `resourceId`, `mechanicIds` fields and `AssignmentUpdated` event consumer. Depends on Workorder entity; no cross-story dependency.
3. **#59** (pos-workorder): Operational Context Display — adds OperationalContext read model from Shopmgmt REST. Depends on Workorder entity being stable.
4. **#63** (pos-shop-manager): Update Appointment Status from Workexec Events — separate module from #64/#65 but consumes `WorkorderStatusChanged` events that workexec emits. Can proceed after #64 work defines the event shape.
5. **#58** (pos-people): HR Ingests Work Sessions — fully independent domain (people). Can proceed any time; schedule last to avoid blocking pos-workorder work.

## Story-Specific Non-Negotiables

### Story #65 — Create Draft Estimate from Appointment
- Exactly one estimate per appointment. Idempotent: if an estimate already exists for `appointmentId`, return the existing `estimateId` without creating a new one.
- Required fields on the incoming `CreateEstimateFromAppointment` command: `idempotencyKey` (UUID), `appointmentId`, `customerId`, `vehicleId`, `locationId`. `requestedServices` is optional.
- New Estimate starts in `DRAFT` status.
- `appointmentId` must be stored indexed on the `Estimate` entity for traceability (add column + Flyway migration).
- Response: `{ estimateId, status: "DRAFT" }` — HTTP 201 on create, HTTP 200 on idempotent return.

### Story #64 — Propagate Assignment Context to Workorder
- Event consumer: `AssignmentUpdated` (from Shopmgmt). Do not implement as a REST endpoint.
- Fields to add to `Workorder`: `locationId` (UUID), `resourceId` (UUID), `mechanicIds` (LIST / ARRAY). Add Flyway migration for all three columns.
- **State-gated mutability (BR1/BR2)**: Only `DRAFT`, `APPROVED`, `ASSIGNED` states allow assignment context updates. `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED` are immutable. Rejected updates = WARN log + ack (no DLQ).
- **Full replace semantics (BR4)**: each `AssignmentUpdated` replaces the entire context; not a patch.
- **Mandatory auditing (BR5)**: every successful write must create an `AuditLog` entry with `entityType="Workorder"`, `changeType="AssignmentContextUpdated"`, `oldValue`, `newValue`, `actor="System:ShopManagementService"`. Add `AuditLog` entity and Flyway migration if not already present.
- Event for non-existent workorder → ERROR log + DLQ. Malformed event → ERROR log + DLQ.

### Story #59 — Workexec Displays Operational Context in Workorder View
- Shopmgr is the system of record for `OperationalContext`; workexec fetches read-only via REST.
- GET endpoint in pos-workorder: `GET /v1/workorders/{workOrderId}/operationalContext` — proxies or caches from `GET /v1/shopmgr/workorders/{workOrderId}/operationalContext`.
- Manager override endpoint: `POST /v1/workexec/workorders/{workOrderId}/operationalContext/override` — requires manager-level permission.
- Context is locked at work start: `POST /v1/workexec/workorders/{id}/start` records `operationalContextVersion` and `workStartedAt` on the workorder.
- If a `WorkorderExecution` sub-model exists, it must track `operationalContextVersion`. Otherwise add `operationalContextVersion` and `workStartedAt` fields to `Workorder` entity with Flyway migration.
- Read-only fields must not be mutatable after `workStartedAt` is set.

### Story #63 — Update Appointment Status from Workexec Events
- Event consumers in pos-shop-manager: `WorkorderStatusChanged` and `InvoiceIssued` (from workexec).
- `AppointmentStatus` enum must be expanded to: `SCHEDULED`, `CONFIRMED`, `CHECKED_IN`, `WORK_IN_PROGRESS`, `WAITING_FOR_PARTS`, `QUALITY_CHECK`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`, `INVOICED`, `REOPENED`. Add Flyway migration for any new values if stored as string.
- Implement the full 23-entry Workexec→Appointment status mapping table from the issue body. Map must be a named, testable lookup (e.g., a `Map<WorkorderStatus, AppointmentStatus>` constant or a mapping method).
- Idempotency: deduplicate on `sourceEventId` stored in a `statusTimeline` (append-only log). Duplicate events → no-op.
- `reopenFlag` is a one-way latch: once set `true`, never reset to `false`.
- Appointment lookup: resolve `workOrderId` → `appointmentId` via `WorkOrderAppointmentMapping` table. If not found → DLQ + alert "Orphaned Work Order".

### Story #58 — HR Ingests Work Sessions from Shopmgr
- Event consumer in pos-people: `WorkSessionCompleted` (from shopmgr / workexec).
- New entity: `TimekeepingEntry` with fields: `timekeepingEntryId` (UUID), `tenantId`, `sourceSystem='shopmgr'`, `sourceSessionId`, `employeeId`, `sessionStartTime`, `sessionEndTime`, `approvalStatus` (default `PENDING_APPROVAL`), `associatedWorkOrderId` (nullable).
- Add `TimekeepingEntry` entity, repository, service interface + impl, and Flyway migration.
- **Idempotency key**: `(tenantId, sourceSessionId)` — enforce as unique constraint at the DB level on `(tenantId, sourceSystem, sourceSessionId)`.
- Duplicate delivery → no-op + metric log. Do not create or modify entries.
- Missing `tenantId` or `sessionId` → reject to DLQ. Invalid schema → reject to DLQ.
- `WorkSessionCorrected` events must create an immutable adjustment entry (not silently overwrite the original `TimekeepingEntry`).
- Audit each `TimekeepingEntry` creation with source event identifiers, timestamp, and actor = system principal.

## Cross-Domain Integration Contracts

### WorkexecStatusChanged event → pos-shop-manager
- Producer: pos-workorder (or workexec service)
- Consumer: pos-shop-manager
- Shape: `{ eventId, workorderId, previousStatus, newStatus, timestamp }`
- Story #64 should define/confirm this event record, and Story #63 implements the consumer.

### WorkSessionCompleted event → pos-people
- Producer: pos-shop-manager (shopmgr)
- Consumer: pos-people
- Shape: `{ tenantId, sessionId, employeeId, startTime, endTime, workOrderId? }`
- Both `tenantId` and `sessionId` are required; reject if either is absent.

### AssignmentUpdated event → pos-workorder
- Producer: pos-shop-manager (shopmgr)
- Consumer: pos-workorder
- Shape: `{ eventId, timestamp, workorderId, payload: { locationId, resourceId, mechanicIds } }`

## Audit and Observability Requirements
- `pos-workorder`:
  - Emit `ESTIMATE_CREATED_FROM_APPOINTMENT` event on successful estimate creation from appointment (Story #65).
  - Emit `ASSIGNMENT_CONTEXT_UPDATED` event and write AuditLog on successful assignment propagation (Story #64).
- `pos-shop-manager`:
  - Emit `APPOINTMENT_STATUS_UPDATED` event with source `workorderId` and `sourceEventId` on each status write (Story #63).
- `pos-people`:
  - Metric counters: ingestion success, ingestion failure (tagged by reason: `missing_required_fields`, `schema_invalid`, `mapping_error`, `duplicate`).
  - Audit each `TimekeepingEntry` creation with `sourceSystem`, `sourceSessionId`, `tenantId`, and system principal (Story #58).
- Use `@EmitEvent` annotation where workexec emits audit-significant operations; register event type codes in the module's `EventTypeInitializer`.

## CAP-140 Execution Deliverables (Per Story)
- Story-level RED evidence (failing test) mapped to each AC.
- GREEN evidence (passing test) for the same AC scope.
- Flyway migration for every new column or table.
- ArchUnit test coverage for internal package encapsulation (no cross-module internal access).
- Idempotency verified by test for all event-consuming stories (#58, #63, #64).
- Cross-module event contract documented in domain contract guide before marking done.

## Blocker Policy for CAP-140
- Do not mark a story done if its event consumer is not implemented (stories #58, #63, #64) or its idempotency rule is not tested.
- If blocked, return:
  - exact missing contract or dependency
  - impacted story IDs
  - fallback attempted
  - smallest completed unblocked slice
  - next concrete remediation step
