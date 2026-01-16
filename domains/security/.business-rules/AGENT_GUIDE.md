# AGENT_GUIDE.md — Domain: `security`

## Purpose
The `security` domain provides the POS system’s **authorization foundation** (RBAC: roles, permissions, assignments, authorization decisions) and **user provisioning orchestration** (create/resolve a `User` and emit an event so `people` can link the user to an existing `Person`).

It is implemented primarily in `pos-security-service`.

---

## Domain Boundaries

### What `security` owns (authoritative)
- **User** (system of record for credentials/identity representation used by the POS platform).
- **RBAC framework and registry**
  - Permission registry (stable permission keys, validation, query)
  - Roles
  - Role ↔ Permission grants
  - Principal ↔ Role assignments (user/group principals)
- **Authorization decisioning** (evaluate principal → roles → permissions → allow/deny).
- **Provisioning orchestration API** (admin-initiated) and event publication (`UserCreated`/`UserProvisioned`) via transactional outbox.

### What `security` does NOT own
- **Person** and **UserPersonLink** are owned by `pos-people`.
- Authentication source of truth is an **external IdP** (JWT/principal is assumed verifiable upstream).

### Cross-domain contract
- `security` emits a user provisioning event containing `userId` + `personId` so `people` can create/ensure the canonical `UserPersonLink`.

---

## Key Entities / Concepts
- **User**: Security-owned representation of a system user (linked to IdP principal; exact fields TBD).
- **Principal**: The authenticated subject (user or group) used for authorization decisions.
- **Permission**: Stable identifier string with enforced naming convention:  
  `domain:resource:action` (e.g., `pricing:price_book:edit`)
- **Role**: Named collection of permissions.
- **RolePermission**: Many-to-many mapping between roles and permissions.
- **PrincipalRole**: Many-to-many mapping between principals and roles.
- **Permission Manifest**: Code-first list of permissions declared by each domain service and registered with `security`.
- **User provisioning event**: `UserCreated` or `UserProvisioned` (name TBD; semantics are “user exists and should be linked to person”).

---

## Invariants / Business Rules
- **Deny by default**: absence of an explicit grant results in `deny`.
- **Permission key immutability**: permission keys are stable identifiers; do not repurpose keys.
- **Permission naming validation**: reject permission registration that does not match `domain:resource:action` with snake_case resource/action.
- **Idempotency**
  - Permission registration is idempotent upsert (no duplicates).
  - User provisioning is idempotent (“create or resolve” user).
  - Event publication is effectively exactly-once via **transactional outbox** + idempotent consumers.
- **Provisioning is async with respect to linking**
  - Provisioning API returns success after user creation + event enqueue; it **must not wait** for `people` to complete linking.
  - If linking fails, **do not** auto-delete/deactivate the user; rely on retries/DLQ/alerts.

---

## Events / Integrations

### Outbound events (from `security`)
- **`UserCreated` / `UserProvisioned`** (event name TBD)
  - Minimum payload:
    - `userId`
    - `personId`
    - `tenantId` (if applicable)
    - `correlationId`
    - `occurredAt`
    - `eventVersion`
  - Delivery: transactional outbox (DB-backed) → message broker (TBD)

### Inbound integrations (to `security`)
- **Permission registration** from domain services (internal call on startup/deploy).
- **Authorization decision requests** from domain services (internal call per request or cached strategy; exact pattern TBD).
- **Admin actions** (role/permission management, provisioning) via authenticated/authorized admin principal.

### Downstream consumers
- `pos-people` consumes provisioning event and creates/ensures `UserPersonLink` idempotently.
- Other services (workexec/timekeeping/shopmgr/etc.) consume the link from `people` for attribution (not directly from `security`).

---

## API Expectations (high-level)
> Do not treat these as final; concrete request/response schemas are **TBD** unless already defined elsewhere.

### Admin: Provision user (orchestration endpoint)
- Endpoint: `POST /users/provision` (example from story; final path TBD)
- Behavior:
  - Validate admin authorization to provision users.
  - Create or resolve `User` idempotently.
  - Emit `UserCreated/UserProvisioned` via outbox.
  - Return success without waiting for `people` link completion.

### Internal: Permission registration (code-first)
- Endpoint: `POST /internal/permissions/register` (from story)
- Behavior:
  - Validate permission key format.
  - Idempotent upsert of permissions.
  - Optionally fail-fast for callers if registration fails (policy/config TBD).

### Query/admin endpoints (RBAC management)
- `GET /permissions` and `GET /permissions?domain=...` are expected (from story); other role/assignment endpoints are TBD.

### Authorization decision API
- An internal API exists for “does principal P have permission X?”; exact endpoint and payload are TBD.
- Response semantics: `allow` / `deny` (caller maps deny to `403 Forbidden`).

---

## Security / Authorization Assumptions
- **Authentication** is handled by an external IdP; requests include a verifiable principal (e.g., JWT subject + claims).
- `pos-security-service` enforces:
  - Admin-only access for provisioning and RBAC configuration.
  - Least-privilege defaults for all decisions.
- Service-to-service calls (permission registration, authorization decisions) must be authenticated (mTLS/JWT/etc. TBD) and authorized as internal callers.
- Audit logging is required for sensitive actions (see Observability).

---

## Observability (logs / metrics / tracing)

### Audit events/logs (must capture)
- On provisioning:
  - `USER_PROVISIONED` audit record when user is created/resolved and event is enqueued.
  - Include: `adminId`, `userId`, `personId`, roles (if assigned at provision time; TBD), timestamp, `correlationId`, outcome.
- RBAC changes:
  - `role.created`, `role.updated`
  - `permission.registered`
  - `role.permission.grant`, `role.permission.revoke`
  - `principal.role.assign`
- Authorization:
  - `authorization.decision` (at least for debugging/traceability; volume controls may be needed)
  - `access.denied` when a protected operation is denied (may be emitted by the protected service; policy TBD)

### Metrics (minimum)
- Provisioning:
  - count success/failure
  - outbox publish success/failure
- Consumer-side (owned by `people`, but `security` should still monitor publish health):
  - DLQ count/age (broker-level)
- Authorization:
  - decision latency
  - allow/deny rates
  - denied rate by permission/domain (cardinality controls required)

### Tracing
- Propagate `correlationId` from inbound admin request → DB transaction/outbox record → event headers/payload.
- Include trace/span IDs in logs where supported.

---

## Testing Guidance

### Unit tests
- Permission key validation:
  - accepts `pricing:price_book:edit`
  - rejects malformed keys (wrong segments, casing, invalid chars)
- Authorization evaluation:
  - deny-by-default
  - allow when role grants permission
- Idempotency:
  - permission registration upsert does not duplicate
  - provisioning “create or resolve” returns same user for same identity inputs (identity key TBD)

### Integration tests (Spring Boot + DB)
- Transactional outbox:
  - user creation and outbox insert are atomic
  - outbox publisher publishes once per record (effectively exactly-once)
- Provisioning endpoint:
  - returns success without waiting for downstream link
  - emits event with required fields

### Contract / E2E tests (with `pos-people`)
- Given a provisioning event, `people` creates `UserPersonLink` idempotently.
- Failure mode:
  - simulate `people` consumer failure → retries → DLQ after exhaustion; verify `security` user remains.

---

## Common Pitfalls
- **Coupling provisioning to linking**: do not block the provisioning API on `people` link completion.
- **Missing idempotency**:
  - provisioning endpoint must tolerate retries (client/network) without duplicating users
  - permission registration must be safe on repeated startup/deploy
- **Skipping outbox**: publishing events outside the DB transaction risks “user created but no event” gaps.
- **Overly chatty audit/decision logs**: authorization decision logging can be high-volume; ensure sampling/level controls while still meeting audit requirements.
- **Permission key drift**: renaming or reusing permission keys breaks least-privilege and historical audit meaning—treat keys as immutable.
- **Unbounded metric cardinality**: tagging metrics by raw principal/userId can explode cardinality; prefer permission/domain aggregates.

---
