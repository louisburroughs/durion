```markdown
# STORY_VALIDATION_CHECKLIST.md (domain: audit)

Use this checklist to validate any story implementation in the **audit** domain (ingestion, storage, query/search, export, retention, and cross-domain integration). Items are written to be **actionable and verifiable**.

---

## Scope/Ownership

- [ ] Story explicitly states **domain ownership**: `domain:audit` owns immutable storage, query/search, export, retention; producer domains own authoritative state and event production.
- [ ] Integration boundaries are clear: producers **do not** write directly to audit storage tables; they publish events / outbox messages.
- [ ] The story defines the **source of truth** for operational entities (e.g., workexec/inventory) and audit only stores history.
- [ ] The implementation includes a versioned **event/envelope schema** contract (or references the canonical one) and documents compatibility expectations.
- [ ] Any new “reason code” requirements are owned by audit (registry/management), with producer domains using namespaced codes (e.g., `workexec:...`).

---

## Data Model & Validation

- [ ] Audit persistence model supports **append-only** records (no updates/deletes in normal flows).
- [ ] Stored record includes required normalized fields (as applicable to the story):
  - [ ] `eventId` (globally unique)
  - [ ] `schemaVersion`
  - [ ] `eventType`
  - [ ] `occurredAt` (UTC) and (if applicable) `emittedAt`
  - [ ] `tenantId`
  - [ ] `actor` (type + id; displayName optional)
  - [ ] `correlationId` (or trace id) when provided
  - [ ] `aggregate/entity` (type + id)
  - [ ] `locationId` when applicable
- [ ] Raw payload/envelope is stored (or explicitly justified if not) to enable replay/legal inspection.
- [ ] Change representation meets story requirements:
  - [ ] Supports structured diff (e.g., JSON Patch RFC 6902 or `{field, oldValue, newValue}` list)
  - [ ] Supports a human-readable summary text when required
  - [ ] For create/delete events, includes snapshot semantics (full snapshot or clearly documented alternative)
- [ ] Reason codes are validated against a **managed registry**:
  - [ ] `reasonCode` is from an allow-list/registry (not free-text)
  - [ ] `reasonNotes` (optional) is length-limited and sanitized/encoded for safe display
- [ ] All timestamps are stored in **UTC** and validated (no local-time ambiguity).
- [ ] Database constraints exist for correctness:
  - [ ] NOT NULL constraints for required fields
  - [ ] Unique constraint/index on `eventId` for idempotency
  - [ ] Foreign keys are avoided unless they would not block ingestion (audit should not depend on producer DB integrity)
- [ ] Retention metadata is stored per record or derivable deterministically:
  - [ ] Default retention is **>= 7 years**
  - [ ] Tenant-specific retention overrides are supported (or explicitly out of scope with rationale)

---

## API Contract

- [ ] Query/list endpoint(s) exist and are documented with:
  - [ ] Required `tenantId` scoping (implicit via auth context or explicit parameter)
  - [ ] Pagination (limit/offset or cursor) with stable ordering
  - [ ] Sort order defined (typically reverse chronological by `occurredAt`)
- [ ] Query filters required by audit stories are implemented and verified:
  - [ ] Date/time range filter supported; unbounded queries are prevented (mandatory or safe default like last 90 days)
  - [ ] Filter by `eventType`
  - [ ] Filter by `actor.id`
  - [ ] Filter by `aggregate.type` + `aggregate.id` (or entityType/entityId)
  - [ ] Filter by `correlationId`
  - [ ] Filter by `locationId` when applicable
- [ ] Query endpoints enforce “at least one indexed filter” (or equivalent guardrails) to prevent full scans.
- [ ] Detail endpoint exists to fetch a single audit record by `eventId` and returns:
  - [ ] normalized fields
  - [ ] raw payload/envelope
  - [ ] schemaVersion
  - [ ] deep-link metadata to source entity (when applicable)
- [ ] Any export endpoint is documented and includes:
  - [ ] Export format (e.g., CSV) and column definitions
  - [ ] Export metadata (filters used, exportedAt, exportedBy, rowCount)
  - [ ] SHA-256 digest/manifest over exported content
- [ ] Error responses are consistent and verifiable:
  - [ ] 401/403 for unauthorized/forbidden access
  - [ ] 400 for validation errors (with field-level details)
  - [ ] 429 for rate limiting (if enabled)
  - [ ] 5xx for server errors without leaking sensitive internals

---

## Events & Idempotency

- [ ] Producer integration uses **asynchronous** delivery (no synchronous audit calls in critical path unless explicitly approved).
- [ ] Producer uses **outbox pattern** (or equivalent) to guarantee “state change + audit request” durability.
- [ ] Audit ingestion is **idempotent**:
  - [ ] Deduplication key is `eventId`
  - [ ] Duplicate deliveries do not create duplicate stored records
- [ ] Ordering expectations are documented:
  - [ ] Partitioning key strategy defined (e.g., aggregate id) if using Kafka
  - [ ] Audit does not assume global ordering; only per-aggregate ordering if guaranteed
- [ ] Ingestion handles schema evolution:
  - [ ] Unknown/incompatible `schemaVersion` is routed to DLQ/quarantine with alerting
  - [ ] Backward-compatible changes are documented and tested
- [ ] Failure handling is implemented:
  - [ ] Retry with backoff for transient failures
  - [ ] Dead-letter/quarantine path for poison messages
  - [ ] Clear operator runbook signals (what to do when DLQ grows)

---

## Security

- [ ] Access control is enforced for all read/query/export endpoints:
  - [ ] Only authorized roles (e.g., Shop Manager, Compliance Auditor, Support) can access audit data
  - [ ] Tenant isolation is enforced server-side (no cross-tenant reads)
- [ ] Audit records are **immutable** by API:
  - [ ] No update/delete endpoints exist for audit records, or they are disabled/blocked
  - [ ] Any attempted modification returns a clear forbidden/error response
- [ ] Database permissions follow least privilege:
  - [ ] Application role cannot UPDATE/DELETE audit tables (or equivalent WORM controls)
- [ ] Sensitive data handling is defined and verified:
  - [ ] Payload fields are reviewed for PII/secrets; sensitive fields are minimized, redacted, or encrypted as required
  - [ ] Free-text fields (e.g., reasonNotes) are length-limited and safely rendered (no stored XSS)
- [ ] Export security is addressed:
  - [ ] Export action is authorized and audited (who/when/filters/digest)
  - [ ] Export files are not publicly accessible; access is time-bound if stored
- [ ] Security alerts exist for suspicious actions:
  - [ ] Attempts to modify/delete audit records trigger a security log/alert path (as required by story)

---

## Observability

- [ ] Structured logs include correlation fields:
  - [ ] `eventId`, `correlationId`, `tenantId`, `aggregate.type`, `aggregate.id`, `schemaVersion`
- [ ] Metrics exist and are validated (names and labels consistent with platform standards):
  - [ ] Ingestion success/failure counts
  - [ ] Deduplication count (duplicates detected)
  - [ ] DLQ/quarantine count
  - [ ] Consumer lag / outbox backlog age and size (where applicable)
  - [ ] Query latency and error rate
  - [ ] Export counts, duration, and failures
- [ ] Alerts are configured for high-severity conditions:
  - [ ] Sustained ingestion failures
  - [ ] DLQ growth beyond threshold
  - [ ] Backlog age/size beyond threshold (e.g., > 1 hour or > N messages)
- [ ] Tracing is wired through ingestion and query paths (where supported), linking producer correlationId to audit persistence.

---

## Performance & Failure Modes

- [ ] Query performance is protected by indexes aligned to required filters:
  - [ ] `(tenantId, occurredAt)`
  - [ ] `(tenantId, eventType, occurredAt)`
  - [ ] `(tenantId, aggregate.type, aggregate.id)`
  - [ ] `(tenantId, actor.id, occurredAt)` (or equivalent)
  - [ ] `(tenantId, correlationId)`
  - [ ] `(tenantId, locationId, occurredAt)` when applicable
- [ ] Unbounded queries are prevented:
  - [ ] Date range is mandatory or defaults to a safe window (e.g., last 90 days)
  - [ ] Pagination limits are enforced with maximum page size
- [ ] Ingestion throughput is validated:
  - [ ] Batch/transaction sizing is safe
  - [ ] Backpressure behavior is defined (consumer pause, rate limit, etc.)
- [ ] Failure modes are explicitly handled and tested:
  - [ ] DB unavailable (retry/backoff, circuit breaker if applicable)
  - [ ] Message broker unavailable (consumer recovery, no data loss)
  - [ ] Partial failures (e.g., raw payload too large) have deterministic handling (reject + DLQ vs truncate with policy)
- [ ] Retention/archival jobs are safe:
  - [ ] No deletion within retention window
  - [ ] Archival/purge is auditable and reversible where required
  - [ ] Jobs are rate-limited and do not impact ingestion/query SLAs

---

## Testing

- [ ] Unit tests cover:
  - [ ] Validation of required fields and schemaVersion handling
  - [ ] Reason code registry validation
  - [ ] Idempotency behavior (duplicate eventId)
- [ ] Integration tests cover:
  - [ ] End-to-end ingestion from event/outbox to persisted audit record
  - [ ] Query filters + pagination + ordering
  - [ ] Detail endpoint returns normalized + raw payload
- [ ] Security tests cover:
  - [ ] Tenant isolation (cannot read other tenant’s audit records)
  - [ ] Role-based access (403 for unauthorized)
  - [ ] Immutability enforcement (update/delete attempts fail)
- [ ] Failure-mode tests cover:
  - [ ] DLQ/quarantine routing on invalid payloads
  - [ ] Retry/backoff behavior on transient failures
- [ ] Export tests cover:
  - [ ] CSV correctness and stable formatting
  - [ ] Digest/manifest correctness (SHA-256 matches content)
  - [ ] Export action is itself audited
- [ ] Load/performance tests (or at least profiling) validate:
  - [ ] Query latency under expected cardinality
  - [ ] Ingestion throughput and dedupe overhead

---

## Documentation

- [ ] Public/internal API docs include request/response examples for:
  - [ ] Query list with filters + pagination
  - [ ] Detail by eventId
  - [ ] Export with manifest/digest
- [ ] Event schema documentation includes:
  - [ ] Envelope fields, required vs optional
  - [ ] schemaVersioning rules and compatibility guarantees
  - [ ] Example payloads for key eventTypes (e.g., schedule change, assignment change, inventory movement)
- [ ] Operational docs/runbooks exist for:
  - [ ] DLQ/quarantine handling and replay procedure
  - [ ] Backlog/lag alert response
  - [ ] Retention configuration and archival/purge process
- [ ] Reason code registry documentation exists:
  - [ ] How to add/disable codes (configuration process)
  - [ ] Namespacing rules and ownership
- [ ] Data retention policy is documented:
  - [ ] Default 7-year retention
  - [ ] Tenant-specific overrides and auditability of policy changes
```
