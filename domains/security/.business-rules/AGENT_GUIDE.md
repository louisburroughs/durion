```markdown
# AGENT_GUIDE.md — Domain: `security`

## Purpose
The `security` domain provides the POS system’s **authorization foundation** (RBAC: roles, permissions, assignments, authorization decisions) and **user provisioning orchestration** (create/resolve a `User` and emit an event so `people` can link the user to an existing `Person`).

It is implemented primarily in `pos-security-service`.

This guide is updated to reflect new frontend stories covering:
- RBAC Admin UI (roles, permissions, grants, optional assignments, audit visibility)
- User provisioning UI (person search + initial role assignment + async linking)
- Audit Trail UI for financial exceptions (read-only + export)
- “Approval expiration” UX (domain ownership is unclear; see Open Questions)

---

## Domain Boundaries

### What `security` owns (authoritative)
- **User** (system of record for platform user identity representation used by the POS platform; not the HR/person record).
- **RBAC framework and registry**
  - Permission registry (stable permission keys, validation, query)
  - Roles
  - Role ↔ Permission grants
  - Principal ↔ Role assignments (user/group principals) **if in scope for this product** (see Open Questions)
- **Authorization decisioning** (evaluate principal → roles → permissions → allow/deny).
- **Provisioning orchestration API** (admin-initiated) and event publication (`UserCreated`/`UserProvisioned`) via transactional outbox.
- **Audit emission for security-sensitive mutations**
  - RBAC mutations (role CRUD, grants/revokes, assignments)
  - Provisioning actions
  - Potentially authorization denials (policy-dependent; see Open Questions)

### What `security` does NOT own
- **Person** and **UserPersonLink** are owned by `pos-people`.
- Authentication source of truth is an **external IdP** (JWT/principal is assumed verifiable upstream).
- **Financial exception business logic** (price override/refund/cancellation) is owned by the relevant business domains (POS/order/payment/etc.). `security` may own the *audit read model* and access control for audit viewing/export, but not the write-side business rules.
- **Approvals**: domain ownership is **unclear**. The frontend story is labeled `domain:security`, but this may belong to another domain (workexec/accounting/general). See Open Questions.

### Cross-domain contracts
- `security` emits a user provisioning event containing `userId` + `personId` so `people` can create/ensure the canonical `UserPersonLink`.
- Domain services register permissions (code-first manifest) with `security` so the permission registry is queryable by admin UIs.
- Audit trail UIs require a query API/read model; ownership of the read model must be clarified:
  - RBAC audit events: owned by `security`
  - Financial exception audit events: **CLARIFY** whether owned by `security` or a separate audit/reporting domain

---

## Key Entities / Concepts

### Core RBAC entities
- **User**: Security-owned representation of a system user (linked to IdP principal; exact fields TBD).
- **Principal**: The authenticated subject (user or group) used for authorization decisions.
- **Permission**
  - Stable identifier string with enforced naming convention: `domain:resource:action` (e.g., `pricing:price_book:edit`)
  - Permissions are **code-first** (registered by services), **not created/edited** in the admin UI.
- **Role**: Named collection of permissions.
- **RolePermission**: Many-to-many mapping between roles and permissions.
- **PrincipalRole**: Many-to-many mapping between principals and roles (user/group assignment).

### Provisioning entities / integration concepts
- **Provisioning request**: Admin-initiated request to create/resolve a `User` and optionally assign initial roles.
- **User provisioning event**: `UserCreated` or `UserProvisioned` (name TBD; semantics are “user exists and should be linked to person”).
- **UserPersonLink**: Owned by `pos-people`; created asynchronously by consuming the provisioning event.

### Audit concepts (two distinct categories)
1. **Security/RBAC audit**
   - Events for role CRUD, grants/revokes, assignments, provisioning.
2. **Financial exception audit trail**
   - Events like price overrides, refunds, cancellations.
   - UI requires: query, detail, drilldown references (order/invoice/payment), export.
   - **CLARIFY** whether these events are stored/queryable via `pos-security-service` or another service.

### Relationships (high-level)
- `Role` 1—* `RolePermission` *—1 `Permission`
- `Principal` 1—* `PrincipalRole` *—1 `Role`
- `User` (security) 1—0..1 `UserPersonLink` (people) —1 `Person` (people)
- `AuditLog` entries reference:
  - actor principal (`actorId`)
  - subject (`subjectType`, `subjectId`) such as roleId, principalId, userId, orderId, invoiceId, paymentId
  - correlationId for traceability

---

## Invariants / Business Rules

### Authorization / RBAC invariants
- **Deny by default**: absence of an explicit grant results in `deny`.
- **Permission key immutability**: permission keys are stable identifiers; do not repurpose keys.
- **Permission naming validation**: reject permission registration that does not match `domain:resource:action` with snake_case resource/action.
- **Role uniqueness**:
  - Role name uniqueness rules (case-sensitive vs case-insensitive) are **CLARIFY** (frontend stories assume case-insensitive uniqueness).
- **Role name mutability**:
  - Whether `roleName` is editable after creation is **CLARIFY**.
  - If mutable, define constraints (rename impacts audit/history, references, and UI deep links).
- **Idempotency**
  - Permission registration is idempotent upsert (no duplicates).
  - User provisioning is idempotent (“create or resolve” user).
  - RolePermission grant/revoke should be idempotent:
    - Granting an already-granted permission should be a no-op or return a deterministic conflict (prefer no-op + 200/204).
    - Revoking a non-existent grant should be a no-op or deterministic 404/409 (prefer no-op + 200/204).
  - Event publication is effectively exactly-once via **transactional outbox** + idempotent consumers.

### Provisioning invariants
- **Provisioning is async with respect to linking**
  - Provisioning API returns success after user creation + event enqueue; it **must not wait** for `people` to complete linking.
  - If linking fails, **do not** auto-delete/deactivate the user; rely on retries/DLQ/alerts.
- **Identity rules** are **CLARIFY**:
  - Is `username` required or derived from email?
  - Must provisioned user email match the selected Person’s email?
- **Initial role assignment** is **CLARIFY**:
  - Are zero roles allowed at provision time?
  - Are roles identified by `roleId` or `roleName` in APIs?

### Audit invariants
- **Append-only**: audit entries are immutable; UI must not expose edit/delete.
- **Retention**: retention policy and query window constraints are **CLARIFY** (frontend needs to know what to expect and how to message “older data not available”).
- **Sensitive payload handling**:
  - If audit entries include raw payload JSON, define redaction rules and field-level access controls (**CLARIFY**).

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

### Integration patterns to support frontend stories
- **RBAC Admin UI**
  - Requires list/query endpoints for roles and permissions, plus mutation endpoints for role CRUD and grants/revokes.
  - Optional: endpoints for principal search and role assignment.
- **Audit UIs**
  - RBAC audit view: query by eventType/date/actor/subject.
  - Financial exception audit trail: query + detail + export + reference drilldowns.
  - **CLARIFY** whether both are served by the same audit API or separate APIs.

---

## API Expectations (high-level)
> Do not treat these as final; concrete request/response schemas are **TBD** unless already defined elsewhere. Frontend stories require these contracts to be explicit and stable.

### Common API patterns (recommended)
- **Pagination**: `pageIndex`, `pageSize`, `totalCount` (or cursor-based; pick one and document).
- **Filtering**: query params for `domain`, `search`, `eventType`, `dateFrom/dateTo`, `actorId`, `subjectId`, `locationId` (if supported).
- **Error shape**: return a consistent machine-readable structure:
  - `code` (stable string)
  - `message` (safe, user-displayable)
  - `fieldErrors` (optional map/list)
  - `correlationId` (always when possible)
  - **TODO**: publish canonical error schema for Moqui/REST consumers.

### Admin: Provision user (orchestration endpoint)
- Endpoint: `POST /users/provision` (example from story; final path TBD)
- Behavior:
  - Validate admin authorization to provision users.
  - Create or resolve `User` idempotently.
  - Apply initial role assignments if provided/allowed (**CLARIFY** whether this is part of provisioning or separate).
  - Emit `UserCreated/UserProvisioned` via outbox.
  - Return success without waiting for `people` link completion.
- Response should include:
  - `userId`, `personId`
  - `resolvedExisting` (boolean) if idempotent resolve occurred (recommended)
  - `eventEnqueued` (boolean) (recommended)
  - `correlationId`, `occurredAt` (recommended)

### Internal: Permission registration (code-first)
- Endpoint: `POST /internal/permissions/register` (from story)
- Behavior:
  - Validate permission key format.
  - Idempotent upsert of permissions.
  - Optionally fail-fast for callers if registration fails (policy/config TBD).

### Query/admin endpoints (RBAC management)
Frontend stories require at minimum:
- `GET /permissions` and `GET /permissions?domain=...`
- Role CRUD endpoints (paths TBD)
- RolePermission grant/revoke endpoints (paths TBD)
- Optional: principal role assignment endpoints (paths TBD)
- Audit log query endpoint (paths TBD)

**CLARIFY** whether the backend prefers:
- **Replace-set** semantics for role permissions (idempotent PUT of full set), or
- **Incremental** grant/revoke endpoints, or
- Both.

### Authorization decision API
- An internal API exists for “does principal P have permission X?”; exact endpoint and payload are TBD.
- Response semantics: `allow` / `deny` (caller maps deny to `403 Forbidden`).

---

## Security / Authorization Assumptions

### Authentication
- **Authentication** is handled by an external IdP; requests include a verifiable principal (e.g., JWT subject + claims).

### Authorization requirements surfaced by frontend stories
- RBAC admin screens must be protected:
  - Viewing roles/permissions
  - Creating/updating roles
  - Granting/revoking permissions
  - Viewing audit logs
  - Assigning/revoking roles to principals/users (**if in scope**)
- Provisioning screens must be protected:
  - Viewing Provision User screen
  - Submitting provisioning request
- Audit trail screens must be protected:
  - Viewing audit trail
  - Exporting audit trail
  - Drilling into referenced order/invoice/payment detail may require additional permissions (domain-owned)

**Important**: Per security agent contract, **do not invent permission names**. Permission keys that gate these actions are **CLARIFY** and must be provided by the backend/security policy.

### Service-to-service security
- Service-to-service calls (permission registration, authorization decisions) must be authenticated (mTLS/JWT/etc. TBD) and authorized as internal callers.
- Avoid “confused deputy”:
  - Internal endpoints should not be callable by end-user tokens unless explicitly intended.
  - Prefer separate auth audience/claims for internal callers.

### Data exposure controls
- Audit APIs must avoid leaking sensitive data:
  - Do not return raw tokens, credentials, or secrets.
  - Redact PII where not required for the auditor’s job function.
  - **CLARIFY** whether `payloadJson` is allowed and under what permission.

---

## Observability (logs / metrics / tracing)

### Audit events/logs (must capture)
#### RBAC + provisioning (security-owned)
- Provisioning:
  - `USER_PROVISIONED` audit record when user is created/resolved and event is enqueued.
  - Include: `actorId` (admin principal), `userId`, `personId`, initial roles (if assigned), timestamp, `correlationId`, outcome.
- RBAC changes:
  - `role.created`, `role.updated` (and `role.deleted` if supported)
  - `permission.registered`
  - `role.permission.grant`, `role.permission.revoke` (or `role.permissions.replaced`)
  - `principal.role.assign`, `principal.role.revoke` (if supported)
- Authorization:
  - `authorization.decision` (debug-level; sampling/volume controls)
  - `access.denied` when a protected operation is denied (**CLARIFY** whether exposed via audit query API or logs only)

#### Financial exception audit trail (ownership CLARIFY)
Frontend requires event types like:
- price override
- refund
- cancellation
Exact canonical codes and required fields are **CLARIFY**.

### Metrics (minimum)
- Provisioning:
  - `security_provision_requests_total{outcome=success|failure}`
  - `security_provision_latency_ms` (histogram)
  - outbox insert/publish success/failure counters
- RBAC admin mutations:
  - `security_role_mutations_total{type=create|update|delete,outcome=...}`
  - `security_role_permission_mutations_total{type=grant|revoke|replace,outcome=...}`
  - `security_principal_role_mutations_total{type=assign|revoke,outcome=...}` (if supported)
- Authorization:
  - `security_authz_decision_latency_ms`
  - `security_authz_decisions_total{decision=allow|deny,permissionDomain=...}`
  - Avoid tagging by raw `principalId`/`userId` (cardinality risk)

### Tracing
- Propagate `correlationId` from inbound admin request → DB transaction/outbox record → event headers/payload.
- For audit query/export:
  - Include correlationId in responses so frontend can display it for support.
- Include trace/span IDs in logs where supported.

### Logging guidance (secure-by-default)
- Never log tokens, credentials, or full raw payloads containing sensitive data.
- For audit export requests, log:
  - actorId, filter summary (date range, event types), result size, correlationId
  - Do not log exported row contents.

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
- Role uniqueness:
  - enforce uniqueness per chosen casing rule (**CLARIFY** expected behavior)
- RolePermission mutation semantics:
  - grant existing is idempotent
  - revoke missing is idempotent (preferred) or deterministic error (document)

### Integration tests (service + DB)
- Transactional outbox:
  - user creation and outbox insert are atomic
  - outbox publisher publishes once per record (effectively exactly-once)
- Provisioning endpoint:
  - returns success without waiting for downstream link
  - emits event with required fields
  - validates identity rules (username/email constraints) once clarified
- RBAC endpoints:
  - role CRUD happy path + conflict cases (duplicate role name)
  - grant/revoke endpoints return consistent status codes and error shapes
  - audit events emitted for each mutation

### Contract / E2E tests (cross-service)
- With `pos-people`:
  - Given a provisioning event, `people` creates `UserPersonLink` idempotently.
  - Failure mode: simulate `people` consumer failure → retries → DLQ after exhaustion; verify `security` user remains.
- With frontend expectations:
  - Ensure error shape includes machine-readable `code` and `correlationId` so UI can map 400/401/403/404/409 consistently.

### Performance / load tests (targeted)
- Permission registry list:
  - verify pagination and filtering by domain performs under expected dataset size.
- Audit query/export:
  - enforce server-side paging and export limits (max rows, max date range) with clear error codes/messages.

---

## Common Pitfalls
- **Coupling provisioning to linking**: do not block the provisioning API on `people` link completion.
- **Missing idempotency**:
  - provisioning endpoint must tolerate retries (client/network) without duplicating users
  - permission registration must be safe on repeated startup/deploy
  - grant/revoke endpoints should be idempotent to support UI retries
- **Skipping outbox**: publishing events outside the DB transaction risks “user created but no event” gaps.
- **Overly chatty audit/decision logs**: authorization decision logging can be high-volume; ensure sampling/level controls while still meeting audit requirements.
- **Permission key drift**: renaming or reusing permission keys breaks least-privilege and historical audit meaning—treat keys as immutable.
- **Unbounded metric cardinality**: tagging metrics by raw principal/userId can explode cardinality; prefer permission/domain aggregates.
- **UI-driven authorization assumptions**: frontend may hide controls, but backend must remain authoritative; always return 401/403 correctly.
- **Audit data leakage**: returning raw payloads or PII in audit query APIs without explicit policy/permissions.

---

## Open Questions from Frontend Stories
Consolidated questions that must be answered to finalize backend contracts and ensure frontend/backoffice screens can be implemented without guessing.

### A. RBAC Admin UI — endpoints, schemas, identifiers (blocking)
1. **Backend endpoints & schemas:** What are the exact REST paths (or Moqui services), request/response schemas, and error shapes for:
   - Role CRUD
   - Permission registry list
   - RolePermission grant/revoke (or replace-set)
   - (Optional) Assign/revoke roles to principal/user
   - AuditLog query (RBAC-related)
2. **Identifiers:** Are roles referenced by `roleId` or `roleName` in APIs? Are permissions referenced only by `permissionKey`?
3. **Error shape:** What is the canonical error response format for 400 field validation vs 409 conflicts vs 403 forbidden? Include machine-readable `code` and `correlationId`. **TODO** publish schema.

### B. RBAC Admin UI — authorization gating (blocking)
4. **Authorization scope:** Which permission(s) gate access to:
   - viewing roles
   - creating/updating roles
   - granting/revoking permissions
   - viewing audit logs
   - assigning/revoking user roles (if supported)
   (Per security agent contract, we cannot invent permission names.)

### C. Roles — mutability, uniqueness, scoping (blocking)
5. **Role name mutability:** Is `roleName` editable after creation, or immutable with only `description` editable?
6. **Role uniqueness & casing rules:** Is role name uniqueness case-insensitive? Are there normalization rules (trim, collapse whitespace)?
7. **Multi-tenant/location scoping:** Are roles/assignments scoped by `tenantId` and/or `locationId`? If yes:
   - how is scope selected in UI requests?
   - how is it enforced server-side?
8. **Location overrides / ABAC:** Stories mention “location overrides.” Is there any requirement now for location-scoped grants/overrides, or is this strictly global RBAC for this release?

### D. Role assignment UI scope (product decision)
9. **Role assignment UI:** Is PrincipalRole/UserRole assignment in scope for the RBAC admin UI stories, or explicitly deferred?

### E. Provision User — contracts and identity rules (blocking)
10. **Permissions/Access Control:** What permission(s) gate:
   - viewing the Provision User screen
   - submitting `POST /users/provision`
11. **Endpoint contracts:** What are the exact Moqui-accessible endpoints/services for:
   - Person search/select (people service) including response fields
   - Role list for selection (security service) including identifier type (roleId vs roleName)
   - Provision user request/response schema and error shape
12. **Identity rules:**
   - Is `username` required, or derived from email by backend?
   - Must provisioned user email match the selected Person’s email exactly, or can it differ?
13. **User status enumeration:** What are allowed `status` values and default?
14. **Initial role assignment rule:** Are zero roles allowed at provision time, or is at least one role required?
15. **Link status visibility:** Is there an API to query whether the `UserPersonLink` has been created (linked vs pending)? Should the frontend poll or provide a manual “Check status” action?

### F. Approvals — domain ownership and contract (blocking)
16. **Domain ownership / label:** Is “Approval” governed by `domain:security` or another domain (workexec/accounting/general)? Confirm the correct domain label and owning service.
17. **Backend contract:** What are the actual API endpoints/services used by the frontend to:
   - list approvals,
   - load approval detail,
   - approve,
   - deny?
   Include request/response schemas and error format (including a machine-readable error code for expiration).
18. **State model:** What are the authoritative approval statuses and which are actionable?
19. **Expiration definition:** Does backend expose `expiresAt`, `isExpired`, or only `status=EXPIRED`? Must client rely solely on backend status?
20. **Timezones:** Which timezone governs display of `expiresAt` (user profile, site/location, or UTC)?
21. **Deny requirements:** Does denying require a reason/comment? If yes, what validation rules?
22. **Post-expiration path:** Should UI offer “Request new approval”? If yes, what route/service handles it?

### G. Audit Trail (financial exceptions) — entities, event types, export, sensitivity (blocking)
23. **Backend contract:** What are the exact Moqui services (names, parameters, outputs) for listing audit entries, fetching detail, and exporting?
24. **Entity schema:** Are `AuditLog`, `ExceptionReport`, and `ReferenceIndex` real Moqui entities in this project? If yes, what are their primary keys and key fields?
25. **Event types:** What is the canonical set of auditable exception event types (exact codes) and required fields per type (e.g., is “reason” mandatory)?
26. **Authorization:** Which roles/permissions can:
   - view audit trail,
   - export,
   - drill into linked order/invoice/payment detail?
27. **Export format & delivery:** CSV vs XLSX vs PDF? Synchronous download vs async job with notification vs download link?
28. **Reference linking:** Which identifiers are authoritative for drilldowns (orderId vs external ref, invoiceId vs invoice number, paymentId vs paymentRef)?
29. **Sensitive payload visibility:** Should auditors see raw payload JSON/metadata, or only curated fields? Any redaction requirements?

---
```
