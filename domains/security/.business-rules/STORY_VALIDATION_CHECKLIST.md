```markdown
# STORY_VALIDATION_CHECKLIST.md (Domain: security)

Checklist for engineers/reviewers validating story implementations in the **security** domain (e.g., user provisioning, RBAC/permissions registry, authorization decisions, approvals, audit trail UI).

---

## Scope/Ownership
- [ ] The initiating API endpoint is hosted in the correct service for the story (e.g., `pos-security-service` for RBAC/provisioning orchestration).
- [ ] Canonical ownership of each entity is respected (e.g., `User`/RBAC entities in Security; `Person`/`UserPersonLink` in People; Approval entity in its owning domain once confirmed).
- [ ] Cross-domain writes are avoided; integration occurs via events or explicitly approved internal APIs.
- [ ] RBAC Admin UI scope is explicit and enforced:
  - [ ] Role CRUD is in-scope only as specified (create/edit; delete only if explicitly required).
  - [ ] Permission keys are **not** created/edited in UI (registry is read-only).
  - [ ] Principal/User role assignment UI is implemented only if explicitly in-scope for the story.
- [ ] Audit Trail UI is **append-only** (no edit/delete actions or routes).
- [ ] Approval expiration handling is UI-only behavior; backend remains source of truth for status/expiration.
- [ ] Clear boundaries exist for “source of truth” vs “derived/read model” data (e.g., permission `domain` derived from `permissionKey` only for display/filtering).

---

## Data Model & Validation
- [ ] All persisted security entities have explicit uniqueness constraints aligned to business rules:
  - [ ] `permission_key` unique (and immutable).
  - [ ] `role_name` unique (confirm case sensitivity rules).
  - [ ] RolePermission uniqueness prevents duplicate grants (e.g., `(roleId, permissionKey)` unique).
  - [ ] PrincipalRole/UserRole uniqueness prevents duplicate assignments (e.g., `(principalId, roleId)` unique) if assignment is supported.
- [ ] Input validation is enforced server-side for all endpoints (required fields, formats, length limits, enums).
- [ ] Role create/update validation is enforced:
  - [ ] `roleName` required on create; trimmed; length/charset enforced server-side.
  - [ ] If `roleName` is immutable, update endpoints reject changes with a clear `400`/`409` and message.
  - [ ] `description` length limits enforced server-side.
- [ ] Permission keys conform to naming standard `domain:resource:action` and non-conforming keys are rejected with `400` (registry and any mutation endpoints).
- [ ] Role/permission assignment operations validate referential integrity:
  - [ ] Grant/revoke fails with clear `404` when role or permission does not exist.
  - [ ] Bulk grant/revoke (if supported) defines partial failure behavior (all-or-nothing vs per-item results).
- [ ] User provisioning validation is enforced server-side:
  - [ ] `personId` must reference an existing Person (or backend returns deterministic `404`/`400`).
  - [ ] `email` format validated; normalization rules (case/trim) are consistent.
  - [ ] `username` requirement/derivation is enforced consistently (backend authoritative).
  - [ ] `status` must be one of allowed enum values; default is defined.
  - [ ] Initial roles validation: role identifiers must exist; empty roles allowed/forbidden per rule.
- [ ] Approval actions validation is enforced server-side:
  - [ ] Approve/Deny requires `approvalId` and any required concurrency token/version if used.
  - [ ] Deny reason/comment validation (required/optional, min/max length) is enforced server-side if applicable.
- [ ] Audit log schema (read model) supports UI needs without leaking sensitive data:
  - [ ] Includes `occurredAt/timestamp`, `actorId`, `eventType`, `outcome`, `correlationId`, and safe `details/summary`.
  - [ ] For financial exception audit trail, includes safe references (order/invoice/payment identifiers) and reason fields per policy.

---

## API Contract
- [ ] Endpoints follow least-privilege access: only authorized principals/services can call RBAC mutations, provisioning, approval actions, and audit export.
- [ ] Request/response schemas are explicit and stable (documented fields, types, pagination shape, and error shapes).
- [ ] List endpoints used by UI support pagination and sorting:
  - [ ] Roles list supports paging and optional search by role name.
  - [ ] Permissions list supports paging and optional domain filter (or UI derives domain safely).
  - [ ] Audit list supports paging, sorting (default newest first), and filtering (date range, event type, actor, references).
- [ ] Error handling is consistent and machine-parseable where needed:
  - [ ] `400` for validation failures (field errors included when possible).
  - [ ] `401` for unauthenticated.
  - [ ] `403` for unauthorized (no data leakage in body).
  - [ ] `404` for missing referenced resources (role, permission, approval, audit entry).
  - [ ] `409` for uniqueness/conflict and concurrency conflicts (role name duplicate, stale version, duplicate grant).
  - [ ] Approval expiration returns a deterministic status and/or machine-readable error code (e.g., `APPROVAL_EXPIRED`) with a consistent HTTP status (`409`/`422` as defined).
- [ ] Provisioning endpoint returns success after user creation without waiting for downstream linking completion (async behavior is explicit).
- [ ] Audit export contract is explicit:
  - [ ] Export format(s) are defined (CSV/XLSX/PDF).
  - [ ] Delivery mode is defined (sync download vs async job + link).
  - [ ] Export respects current filters and authorization.
- [ ] Correlation/request IDs are accepted/propagated (header or field) and returned where applicable.

---

## Events & Idempotency
- [ ] Event-driven flows use a transactional outbox (or equivalent) to ensure “effectively exactly-once” publication.
- [ ] Provisioning is idempotent:
  - [ ] Repeated `POST /users/provision` with same identity does not create duplicates.
  - [ ] Response indicates whether an existing user was resolved (e.g., `resolvedExisting=true`) if supported.
- [ ] RBAC mutations are idempotent or safely retryable:
  - [ ] Granting an already-granted permission returns success or a deterministic `409` without duplicating state.
  - [ ] Revoking a non-existent grant returns success or deterministic `404`/`409` per contract (documented).
  - [ ] Replace-set semantics (if used) are idempotent and define canonical ordering/normalization.
- [ ] Approval actions are safe under retries:
  - [ ] Duplicate approve/deny attempts return deterministic conflict/terminal-state responses without double-applying.
- [ ] Event payload includes required minimum fields when applicable:
  - [ ] `userId`, `personId` (if linking), `tenantId`/`locationId` (if applicable), `correlationId`, `occurredAt`, `eventVersion`.
- [ ] Consumers are idempotent (dedupe strategy exists; reprocessing does not create duplicates).
- [ ] Partial failure behavior matches requirements:
  - [ ] If downstream linking fails, the user is not automatically deleted/deactivated unless explicitly required.
  - [ ] Retries are configured; on exhaustion, message goes to DLQ with alerting and runbook.

---

## Security
- [ ] Authentication is enforced for all endpoints; service-to-service calls use approved mechanisms (mTLS/JWT/workload identity as applicable).
- [ ] Authorization is deny-by-default:
  - [ ] RBAC admin screens: backend denies unauthorized access; UI reflects 401/403 by blocking/hiding actions.
  - [ ] Provision user screen and submit are gated by explicit permissions (do not invent permission keys).
  - [ ] Audit trail view/export are gated separately if required (view vs export permissions).
  - [ ] Approval approve/deny actions are gated by explicit permissions.
- [ ] UI does not rely solely on “hidden menu items” for security:
  - [ ] Direct navigation to protected routes returns an Access Denied state without leaking data.
- [ ] RBAC decisions are based on stable identifiers:
  - [ ] Permission keys are immutable.
  - [ ] Role identifier used in APIs is stable (`roleId` preferred); if `roleName` is used, mutability rules are enforced.
- [ ] Multi-tenant and/or location isolation is verified if applicable:
  - [ ] Tenant/location scope is derived from trusted claims/context, not user input.
  - [ ] Queries and mutations are scoped correctly (no cross-tenant reads/writes).
- [ ] Sensitive data handling:
  - [ ] No sensitive data is logged (tokens, credentials, secrets; avoid raw PII beyond what’s required).
  - [ ] Audit trail UI does not display raw payload JSON unless explicitly allowed; redaction rules are enforced.
- [ ] Audit events are emitted for security-relevant actions (as applicable):
  - [ ] `USER_PROVISIONED` (includes actor, userId, personId, roles, correlationId, outcome).
  - [ ] `role.created`, `role.updated` (and `role.deleted` only if supported).
  - [ ] `role.permission.grant` / `role.permission.revoke`.
  - [ ] `principal.role.assign` / `principal.role.revoke` (if assignment supported).
  - [ ] Approval decisions and expiration outcomes are auditable (approve/deny attempts on expired approvals recorded per policy).
  - [ ] Financial exception events (price override/refund/cancellation) are auditable with who/when/why and references.
- [ ] Rate limiting / abuse controls are considered for high-risk endpoints (provisioning, export, approval actions) where applicable.

---

## Observability
- [ ] Structured logs include `correlationId`, `tenantId` (if applicable), actor/principal id, and operation name.
- [ ] Metrics exist and are tagged appropriately:
  - [ ] RBAC mutation success/failure counts (create role, update role, grant, revoke, assign, revoke assignment).
  - [ ] Provisioning success/failure counts and idempotent-resolve counts.
  - [ ] Approval action success/failure counts, including expired/conflict outcomes.
  - [ ] Audit query/export success/failure counts and export size/latency (if available).
  - [ ] Event publish success/failure (outbox lag, publish retries), consumer retries, DLQ counts.
- [ ] Tracing spans cover API → outbox publish → consumer processing (where tracing is available).
- [ ] Alerts are defined for:
  - [ ] DLQ growth / non-zero DLQ.
  - [ ] Elevated provisioning failures.
  - [ ] Elevated RBAC mutation failures/conflicts.
  - [ ] Elevated approval action failures due to expiration/concurrency (if abnormal).
  - [ ] Elevated audit export failures or long-running exports.

---

## Performance & Failure Modes
- [ ] RBAC and audit list endpoints are paginated; UI does not require loading entire datasets.
- [ ] Default sorting is supported server-side for audit lists (newest first).
- [ ] Database constraints and indexes support expected query patterns:
  - [ ] Lookup by `permission_key`, `role_name`/`roleId`, `principalId`, `userId`, `tenantId`, `eventType`, `occurredAt`, and reference IDs (order/invoice/payment).
- [ ] Provisioning endpoint latency does not depend on downstream consumers (async boundary enforced).
- [ ] Export is safe for large datasets:
  - [ ] Backend enforces max rows/size or requires date range constraints (policy documented).
  - [ ] Export does not time out under expected loads (or is async).
- [ ] Retry policies are bounded (max attempts, backoff) and do not create thundering herds.
- [ ] Consumer handles poison messages safely (validation, DLQ routing, no infinite retry loops).
- [ ] System behavior under partial outages is defined and tested:
  - [ ] Broker unavailable (outbox accumulates; publish resumes).
  - [ ] People service unavailable (linking retries; DLQ after exhaustion).
  - [ ] Audit store unavailable (UI shows retryable error; no partial/incorrect data shown).
- [ ] Concurrency is safe:
  - [ ] Two admins creating same role name results in deterministic conflict.
  - [ ] Two admins editing same role handles optimistic locking if supported (ETag/version) or documents last-write-wins.
  - [ ] Approval action conflicts (already decided/expired) return deterministic responses.

---

## Testing
- [ ] Unit tests cover validation rules (required fields, formats, enums, date range validation for audit queries).
- [ ] Unit tests cover authorization rules (deny-by-default; view vs mutate vs export distinctions).
- [ ] Integration tests cover RBAC flows:
  - [ ] Create role → grant permission → revoke permission → verify final state.
  - [ ] Duplicate role name returns `409`.
  - [ ] Duplicate grant is idempotent or returns deterministic conflict without duplication.
- [ ] Integration tests cover provisioning:
  - [ ] Provision user success returns immediately; linking is async.
  - [ ] Idempotent retry resolves existing user without duplication.
  - [ ] Invalid personId/email/status/roles return deterministic `400/404`.
- [ ] Integration tests cover approval expiration handling:
  - [ ] Loading an expired approval returns non-actionable state.
  - [ ] Approve/deny after expiration returns machine-readable expired error and does not change state.
- [ ] Integration tests cover audit trail:
  - [ ] Query supports paging/sorting/filtering; invalid date range rejected.
  - [ ] Detail fetch returns safe fields only.
  - [ ] Export respects filters and authorization; large export returns deterministic error or async job response.
- [ ] Contract tests (or equivalent) validate event schema and versioning expectations between producer/consumer.
- [ ] Security tests verify:
  - [ ] Unauthorized callers receive `403` and no sensitive data in response.
  - [ ] Unauthenticated callers receive `401`.
  - [ ] Internal endpoints are not reachable publicly (where testable).
- [ ] Audit emission is tested for key actions (provision, role/permission changes, approval decisions/expired attempts, financial exception events if in scope).

---

## Documentation
- [ ] API documentation includes endpoints, auth requirements, request/response examples, pagination, and error codes (including machine-readable codes for approval expiration and export limits).
- [ ] RBAC admin UI behavior is documented:
  - [ ] Which operations are supported (create/edit/grant/revoke/assign).
  - [ ] Role name mutability rules.
  - [ ] How permission domain filtering works (server-provided vs derived from key).
- [ ] Event documentation includes name, purpose, producer, consumers, schema (fields + types), `eventVersion`, and compatibility rules.
- [ ] Runbook exists for operational remediation:
  - [ ] How to inspect/replay DLQ messages safely.
  - [ ] How to correlate provisioning requests with downstream link creation (via `correlationId`).
  - [ ] How to investigate RBAC changes using audit logs.
- [ ] Audit trail documentation includes:
  - [ ] Event types and required fields (who/when/why + references).
  - [ ] Retention policy and access controls (view vs export).
  - [ ] Redaction policy for sensitive payloads.

---

## Open Questions to Resolve
- [ ] What are the exact REST/Moqui service contracts (paths, request/response schemas, pagination, and error shapes) for:
  - [ ] Role CRUD
  - [ ] Permission registry list (and whether it is sourced from `permissions_v1.yml` via an endpoint)
  - [ ] Grant/revoke permissions to role (single vs bulk vs replace-set)
  - [ ] Assign/revoke roles to principal/user (if in scope)
  - [ ] Audit log query/detail/export (filters, fields safe to display)
- [ ] Which permission keys gate access to:
  - [ ] Viewing roles/permissions screens
  - [ ] Creating/updating roles
  - [ ] Granting/revoking permissions
  - [ ] Assigning/revoking roles to principals/users
  - [ ] Viewing audit logs
  - [ ] Exporting audit logs
  - [ ] Viewing Provision User screen and submitting provisioning
  - [ ] Viewing approvals and performing approve/deny actions
- [ ] Is `roleName` editable after creation, or immutable (description-only updates)?
- [ ] Is PrincipalRole/UserRole assignment UI in scope for the RBAC admin stories, or explicitly deferred?
- [ ] Is RBAC scoped by `tenantId` and/or `locationId`? If yes:
  - [ ] How is scope selected in UI and enforced in backend?
  - [ ] Do RolePermission grants support location overrides (ABAC-like), or are grants global?
- [ ] What are the authoritative role uniqueness and casing rules (case-insensitive uniqueness, normalization)?
- [ ] Approval domain ownership: is “Approval” truly `domain:security` or another domain/service?
- [ ] Approval state model:
  - [ ] What statuses exist and which are actionable?
  - [ ] How is expiration represented (`expiresAt`, `isExpired`, `status=EXPIRED`) and must UI rely solely on backend?
  - [ ] What timezone governs `expiresAt` display?
  - [ ] Does Deny require a reason/comment and what are the validation rules?
  - [ ] Is there a post-expiration “Request new approval” flow and where does it live?
- [ ] Audit trail specifics for financial exceptions:
  - [ ] Canonical event type codes and required fields per type (is “reason” mandatory?).
  - [ ] Which identifiers are authoritative for drilldowns (orderId vs external ref, invoiceId vs number, paymentId vs paymentRef)?
  - [ ] Should auditors see raw payload JSON/metadata or only curated fields, and what redaction is required?
  - [ ] Export format and delivery mode (sync vs async job/link) and any row/size limits.
```
