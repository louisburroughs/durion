# AGENT_GUIDE.md — Domain: `audit`

## Purpose
Provide a cross-cutting, **immutable** and **searchable** audit trail for operational and compliance-relevant changes across the POS system. The audit service is the **system of record** for audit entries (storage, query, export, retention), while source domains remain authoritative for operational state.

Primary initial producers/consumers:
- **Producers:** `workexec` (schedule + mechanic assignment changes), `inventory` (inventory movements + workorder link/unlink).
- **Consumers:** Shop Managers, Compliance Auditors, Support/Operations, UI/API Gateway.

## Domain Boundaries
### Audit domain owns
- **Ingestion** of audit events (asynchronous/event-driven).
- **Persistence** in append-only/WORM-like manner.
- **Immutability guarantees** (no update/delete via application APIs).
- **Query/search** and **export** capabilities.
- **Retention policy** enforcement (default 7 years; tenant-configurable).
- **Reason code registry** (managed list / enum-like configuration).

### Audit domain does *not* own
- Operational truth for schedules, assignments, work orders, appointments, mechanics, inventory movements.
- Transactional enforcement of “audit must succeed” for operational writes (explicitly **eventual consistency**).

### Integration posture (required)
- **Asynchronous ingestion**. Source domains must not block critical-path transactions on audit availability.
- **Outbox pattern** in producer domains (e.g., `workexec`) to ensure reliable delivery.

## Key Entities / Concepts
### `AuditLog` (stored record)
Minimum fields (from story requirements):
- `auditLogId` (UUID/string)
- `timestamp` (UTC; when change was committed / occurred)
- `actorId` (user/system identifier)
- `entityType` (enum/string; e.g., `WORK_ORDER_ASSIGNMENT`, `APPOINTMENT_SCHEDULE`)
- `entityId` (stable identifier of changed entity)
- `changeSummary` (JSON/text)
- `reasonCode` (optional, **managed code**)
- `reasonNotes` (optional free text)

Additional fields implied by integrations (inventory story / event envelope):
- `eventId` (idempotency key; unique)
- `schemaVersion`
- `eventType` (e.g., `ASSIGNMENT_CREATED`, `SCHEDULE_MODIFIED`, inventory movement/link events)
- `occurredAt` vs `emittedAt` (if provided by envelope)
- `tenantId`
- `locationId` (required filter)
- `correlationId`
- `sourceSystem`
- `actor` (type/id/displayName) if available
- `aggregate` (type/id) for generalized cross-domain querying
- `rawPayload` (JSON) and optionally raw envelope

### Reason Code Registry (Audit-owned)
- Managed/configured list (not hard-coded per change).
- Namespaced codes (examples from requirements): `workexec:CUSTOMER_REQUEST`, `workexec:MECHANIC_UNAVAILABLE`.
- Fields: `code`, `displayName`, `description`, `domain`, `isActive`.

### Change summary formats
- `changeSummaryText`: human-readable string for UI.
- `changePatch`: structured diff (preferred **JSON Patch RFC 6902** or equivalent `{field, oldValue, newValue}` list).
- For create/delete: include full snapshot (create = new snapshot; delete = final snapshot before deletion).

## Invariants / Business Rules
- **All** create/update/delete operations for:
  - work schedules
  - mechanic-to-work-order assignments  
  **MUST** be audited (producer responsibility).
- Audit records are **immutable**:
  - No update/delete APIs.
  - Any attempt to modify/delete must be rejected and treated as a security-relevant event.
- **Eventual consistency**:
  - Producer transactions must **not** roll back if audit is unavailable.
  - Reliability is achieved via outbox + retries.
- **Idempotent ingestion**:
  - Duplicate deliveries must not create duplicate stored records (dedupe by `eventId`).
- **Retention**:
  - Default **7 years** minimum retention.
  - Tenant/location configurable; cannot delete within retention window.
  - Post-retention archive/purge workflows supported (implementation details TBD).
- **Query safety/performance**:
  - Queries should require bounded time (dateTimeRange strongly recommended as mandatory; default last 90 days if omitted per requirement).
  - Require at least one indexed filter to avoid unbounded scans.

## Events / Integrations
### Producer → Audit ingestion (required)
- **Transport:** preferred Kafka for inventory story; for workexec story, integration is via outbox → event bus (Kafka implied; exact bus TBD).
- **Envelope (minimum, versioned):**
  - `schemaVersion`, `eventId`, `eventType`, `occurredAt`, `emittedAt`, `sourceSystem`
  - `tenantId`
  - `actor` (type/id/displayName)
  - `correlationId`
  - `aggregate` (type/id)
  - `payload` (event-specific fields, including diffs/snapshots and reason code/notes where applicable)
- **Partitioning:** by `aggregate.id` to preserve per-aggregate ordering (inventory requirement).

### Failure handling
- Consumer retries with backoff.
- Dead-letter mechanism for:
  - missing required fields
  - unknown/incompatible `schemaVersion`
  - repeated processing failures
- Alerting on:
  - ingestion failures
  - dead-letter volume
  - backlog/lag thresholds (e.g., >1000 pending or >1 hour latency — threshold values configurable)

### Deep-linking / drilldown
Audit records should include enough metadata to navigate back to the authoritative source entity (e.g., movement/workorder in inventory). Exact URL format is TBD; store structured link metadata rather than hard-coded URLs where possible.

## API Expectations (high-level)
> Do not assume concrete paths or DTOs here; implementers should align with platform API conventions. Where unspecified, **TBD**.

### Ingestion API (optional)
- If ingestion is purely event-driven, REST ingestion may be **TBD** / not required.
- If a REST fallback exists, it must:
  - accept the standard envelope
  - enforce idempotency by `eventId`
  - validate required fields and schemaVersion

### Query APIs (required)
Capabilities:
- List/search audit records with filters (AND semantics):
  - `tenantId`
  - `dateTimeRange` (recommended mandatory)
  - `workOrderId` (exact)
  - `appointmentId` (exact)
  - `mechanicId` (exact)
  - `actorUserId`
  - `eventType`
  - `locationId`
  - `aggregate.type`, `aggregate.id`
  - `correlationId`
- Pagination required; sort default: **reverse chronological**.
- Detail lookup by `eventId` returning normalized fields + raw payload + schemaVersion.

### Export API (required for inventory story)
- Export filtered results to **CSV**.
- Include export metadata and a **SHA-256 digest** manifest over exported content.
- Signed exports (KMS-backed) explicitly out of MVP scope unless separately required (**TBD**).

### Reason code registry APIs (TBD)
- Domain owns registry; whether it is managed via API vs config is not fully specified.
- If API-managed, ensure changes to registry are themselves auditable.

## Security / Authorization Assumptions
- All requests/events are scoped by **tenantId**; enforce tenant isolation in storage and queries.
- Only authorized roles can query/export audit data (e.g., Shop Manager, Compliance, Support).
- Producers must provide a reliable **actor identity** from the security context.
- Audit persistence credentials must be **least privilege**:
  - application role has INSERT + SELECT
  - no UPDATE/DELETE on audit tables during retention window
- Attempts to modify/delete audit records must be denied and should generate a security alert signal (log/metric/event).

## Observability (logs / metrics / tracing)
### Logging (structured)
Include at minimum:
- `eventId`, `correlationId`, `tenantId`, `sourceSystem`, `eventType`, `aggregate.type`, `aggregate.id`
- On errors: error category (validation/schema/processing), exception class, retry count, DLQ reason.

Log levels:
- Successful ingestion: INFO or DEBUG (configurable).
- Failures to write/persist: ERROR (must trigger alert).

### Metrics
- Ingestion:
  - `audit_ingest_success_total`
  - `audit_ingest_failure_total` (by reason)
  - `audit_ingest_latency_ms` (p50/p95/p99)
  - consumer lag / backlog size
  - DLQ counts
- Query:
  - request counts, latency, result sizes
- Export:
  - export count, rows exported, export latency, failures
- Retention:
  - purge/archive job counts, failures (implementation TBD)

### Tracing
- Propagate `correlationId` from producer to audit.
- Create spans for: consume → validate → persist → ack; include `eventId` as span attribute.

## Testing Guidance
### Unit tests
- Envelope validation (required fields, schemaVersion handling).
- Idempotency: same `eventId` ingested twice results in one stored record.
- Immutability enforcement: update/delete operations rejected (service + repository layer).
- Reason code validation: reject unknown/inactive codes (if enforced at ingestion time; otherwise TBD).

### Integration tests (with DB)
- Persist + query filters:
  - by `workOrderId`, `appointmentId`, `mechanicId`, `actorUserId`, `locationId`, `eventType`, `correlationId`
  - date range bounding and defaulting behavior
- Index coverage sanity (explain plans in CI optional but recommended).

### Contract tests (producer ↔ audit)
- Schema/version compatibility tests for event envelope.
- Golden payload samples for:
  - schedule reschedule with reason code + notes + diff
  - mechanic assignment create/unassign with diff
  - inventory movement + workorder link/unlink events

### Resilience tests
- Simulate duplicate deliveries, out-of-order deliveries (ensure per-aggregate ordering assumptions are not required for correctness).
- Simulate DB outage / transient failures → retries + DLQ routing.

## Common Pitfalls
- **Blocking operational writes on audit**: violates required eventual consistency; use outbox + async ingestion.
- **Missing/weak idempotency**: duplicates will happen; dedupe by `eventId` is mandatory.
- **Unbounded queries**: require/strongly enforce date ranges and indexed filters; default to last 90 days if omitted.
- **Storing only human text**: keep structured diffs (`changePatch`) and raw payload for compliance/replay.
- **Free-text reason codes**: must be from managed registry; free-text only allowed in `reasonNotes`.
- **Tenant leakage**: always filter by `tenantId` in queries and exports; never allow cross-tenant access.
- **Accidental mutability via ORM**: ensure entities are not updated in-place; restrict DB privileges and avoid exposing JPA repositories that allow save/update semantics on existing rows.
- **Retention implemented as hard delete inside window**: prohibited; only archive/purge after retention and with auditable policy controls (details TBD).
