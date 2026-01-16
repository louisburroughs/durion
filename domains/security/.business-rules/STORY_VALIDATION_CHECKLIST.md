```markdown
# STORY_VALIDATION_CHECKLIST.md (Domain: security)

Checklist for engineers/reviewers validating story implementations in the **security** domain (e.g., user provisioning, RBAC/permissions registry, authorization decisions, audit).

---

## Scope/Ownership
- [ ] The initiating API endpoint is hosted in the correct service for the story (e.g., `pos-security-service` for security-owned orchestration).
- [ ] Canonical ownership of each entity is respected (e.g., `User` in Security; `Person`/`UserPersonLink` in People).
- [ ] Cross-domain writes are avoided; integration occurs via events or explicitly approved internal APIs.
- [ ] Out-of-scope concerns are not silently added (e.g., automatic user deactivation on downstream failure unless explicitly required).
- [ ] Clear boundaries exist for “source of truth” vs “derived/read model” data.

---

## Data Model & Validation
- [ ] All persisted security entities have explicit uniqueness constraints aligned to business rules (e.g., `permission_key` unique; `role_name` unique; link uniqueness on `userId` or `(userId, personId)` as defined).
- [ ] Input validation is enforced server-side for all endpoints (required fields, formats, length limits, enums).
- [ ] Permission keys are validated against the naming standard `domain:resource:action` (reject non-conforming keys with `400`).
- [ ] Role/permission assignment operations validate referential integrity (no grants to non-existent roles/permissions; clear `404` vs `400` behavior).
- [ ] Tenant scoping is enforced consistently (if applicable): stored data includes `tenantId`, queries are tenant-filtered, and uniqueness constraints include tenant where required.
- [ ] Sensitive fields are not stored in plaintext (e.g., credentials/secrets); only store hashes/opaque identifiers as appropriate.
- [ ] Audit log schema captures required fields (actor/admin id, target ids, event type, timestamp, correlation id, outcome).

---

## API Contract
- [ ] Endpoints follow least-privilege access: only authorized admins/services can call provisioning/role/permission mutation APIs.
- [ ] Request/response schemas are explicit and stable (documented fields, types, and error shapes).
- [ ] Provisioning endpoint returns success after user creation without waiting for downstream linking completion (async behavior is explicit).
- [ ] Idempotency behavior is defined and implemented for write endpoints where retries are expected (e.g., provision user, register permissions).
- [ ] Error handling is consistent:
  - [ ] `400` for validation failures (e.g., invalid permission key format)
  - [ ] `401` for unauthenticated
  - [ ] `403` for unauthorized
  - [ ] `404` for missing referenced resources (where applicable)
  - [ ] `409` for uniqueness/conflict (where applicable)
- [ ] Internal endpoints (e.g., permission registration) are protected from public access (network policy and authn/z).
- [ ] Correlation/request IDs are accepted/propagated (header or field) and returned where applicable.

---

## Events & Idempotency
- [ ] Event-driven flows use a **transactional outbox** (or equivalent) to ensure “effectively exactly-once” publication.
- [ ] Event payload includes required minimum fields when applicable:
  - [ ] `userId`
  - [ ] `personId` (if linking)
  - [ ] `tenantId` (if applicable)
  - [ ] `correlationId`
  - [ ] `occurredAt`
  - [ ] `eventVersion`
- [ ] Event names are consistent and versioned (e.g., `UserCreated` vs `UserProvisioned` is chosen and documented).
- [ ] Consumers are idempotent:
  - [ ] Deduplication strategy exists (unique constraint + upsert, or explicit idempotency key).
  - [ ] Reprocessing the same event does not create duplicates or corrupt state.
- [ ] Partial failure behavior matches requirements:
  - [ ] If downstream linking fails, the user is not automatically deleted/deactivated unless explicitly required.
  - [ ] Retries are configured; on exhaustion, message goes to DLQ.
  - [ ] DLQ handling includes alerting and a documented remediation path.
- [ ] Event ordering assumptions are not required, or are explicitly handled (e.g., tolerate out-of-order delivery).
- [ ] Backward/forward compatibility is considered for event evolution (versioning, optional fields, tolerant readers).

---

## Security
- [ ] Authentication is enforced for all endpoints; service-to-service calls use approved mechanisms (mTLS/JWT/workload identity as applicable).
- [ ] Authorization is deny-by-default:
  - [ ] Mutating endpoints require explicit admin/system permissions.
  - [ ] Authorization decision APIs return `deny` when roles/permissions are missing.
- [ ] RBAC decisions are based on stable identifiers (permission keys are immutable; role names/ids are stable).
- [ ] Permission registration endpoint is restricted to trusted services (not end users) and is idempotent upsert.
- [ ] No sensitive data is logged (tokens, credentials, secrets, raw PII beyond what’s required); logs are scrubbed/redacted.
- [ ] Audit events are emitted for security-relevant actions (as applicable):
  - [ ] `USER_PROVISIONED` (includes adminId, userId, personId, roles, timestamp, correlationId)
  - [ ] `role.created`, `role.updated`
  - [ ] `permission.registered`
  - [ ] `role.permission.grant` / `role.permission.revoke`
  - [ ] `principal.role.assign`
  - [ ] `authorization.decision` and/or `access.denied` (per policy)
- [ ] Rate limiting / abuse controls are considered for high-risk endpoints (provisioning, authz decision) where applicable.
- [ ] Multi-tenant isolation is verified (no cross-tenant reads/writes; tenant derived from trusted claims, not user input).

---

## Observability
- [ ] Structured logs include `correlationId`, `tenantId` (if applicable), actor/principal id, and operation name.
- [ ] Metrics exist and are tagged appropriately:
  - [ ] Provisioning success/failure counts
  - [ ] Event publish success/failure (outbox lag, publish retries)
  - [ ] Consumer success/failure, retry counts, DLQ counts
  - [ ] Authorization latency and allow/deny rates (if applicable)
- [ ] Tracing spans cover API → outbox publish → consumer processing (where tracing is available).
- [ ] Alerts are defined for:
  - [ ] DLQ growth / non-zero DLQ
  - [ ] Elevated provisioning failures
  - [ ] Elevated consumer failures/retries
  - [ ] Authorization service latency/error budget breaches (if applicable)
- [ ] Audit logs are queryable and include outcome (success/deny/failure) and actor/target identifiers.

---

## Performance & Failure Modes
- [ ] Provisioning endpoint latency does not depend on downstream consumers (async boundary enforced).
- [ ] Database constraints and indexes support expected query patterns (e.g., lookup by `permission_key`, `role_name`, `userId`, `tenantId`).
- [ ] Retry policies are bounded (max attempts, backoff) and do not create thundering herds.
- [ ] Consumer handles poison messages safely (validation, DLQ routing, no infinite retry loops).
- [ ] System behavior under partial outages is defined and tested:
  - [ ] Broker unavailable (outbox accumulates; publish resumes)
  - [ ] People service unavailable (events retry; DLQ after exhaustion)
  - [ ] Duplicate event delivery (idempotent handling)
- [ ] Concurrency is safe (e.g., two admins provisioning same user; two services registering same permission manifest).
- [ ] Any caching (e.g., authz decisions) has explicit TTL/invalidation strategy and does not weaken security guarantees.

---

## Testing
- [ ] Unit tests cover validation rules (permission key format, required fields, tenant scoping).
- [ ] Unit tests cover authorization rules (deny-by-default, allow when role grants permission).
- [ ] Integration tests cover transactional outbox behavior (write + event publish) and consumer idempotency.
- [ ] Contract tests (or equivalent) validate event schema and versioning expectations between producer/consumer.
- [ ] Tests cover partial failure scenarios:
  - [ ] User created but link creation fails → user remains; retries occur; DLQ after exhaustion.
  - [ ] Duplicate events → no duplicate links/assignments.
- [ ] Security tests verify:
  - [ ] Unauthorized callers receive `403`
  - [ ] Unauthenticated callers receive `401`
  - [ ] Internal endpoints are not reachable publicly (where testable)
- [ ] Audit emission is tested for key actions (provision, role/permission changes, access denied if required).

---

## Documentation
- [ ] API documentation includes endpoints, auth requirements, request/response examples, and error codes.
- [ ] Event documentation includes name, purpose, producer, consumers, schema (fields + types), `eventVersion`, and compatibility rules.
- [ ] Runbook exists for operational remediation:
  - [ ] How to inspect/replay DLQ messages safely
  - [ ] How to correlate provisioning requests with downstream link creation (via `correlationId`)
- [ ] Permission naming convention and registration process are documented for domain teams (manifest format, idempotency, failure behavior).
- [ ] Audit event catalog is documented (event types, required fields, retention/access expectations).
```
