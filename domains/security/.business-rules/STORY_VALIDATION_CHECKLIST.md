# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates implementations of Security domain stories (RBAC admin, provisioning orchestration, and security-owned audit) against the normative decisions in `AGENT_GUIDE.md`. It also includes testable acceptance criteria for each previously-open question so reviewers can verify contracts, permissions, idempotency, and data handling.

## Completed items

- [x] Updated acceptance criteria for each resolved open question

## Scope / Ownership

- [ ] Confirm story labeled `domain:security` (and not mislabeled `domain:workexec` / `domain:audit` when appropriate).
- [ ] Verify primary actor(s) and permissions are explicit for each UI screen and API.
- [ ] Confirm entity ownership:
  - [ ] Security: `Role`, `Permission` (registry), `User` (security identity record), RBAC/provisioning audit.
  - [ ] People: `Person`, `UserPersonLink`.
  - [ ] Audit: financial exception audit read model.
  - [ ] Workexec: approvals (workflow).

## Data Model & Validation

- [ ] Enforce uniqueness constraints:
  - [ ] `permissionKey` is unique and immutable.
  - [ ] `roleNameNormalized` is unique per tenant.
  - [ ] `(roleId, permissionKey)` unique.
- [ ] Enforce role name normalization: trim + collapse whitespace + case-insensitive uniqueness.
- [ ] Reject roleName updates after creation; allow description updates.
- [ ] Provisioning input validation:
  - [ ] `personId` exists.
  - [ ] Email matches selected Person email (case-insensitive).
  - [ ] `idpSubject` is present and stable.
  - [ ] `status` is `ACTIVE|DISABLED` (default `ACTIVE`).
  - [ ] If roles provided, `roleIds[]` exist.

## API Contract

- [ ] Security endpoints are versioned under `/api/v1/security/*` and document request/response schemas.
- [ ] List endpoints support pagination: `pageIndex`, `pageSize`, `totalCount`.
- [ ] Error responses use the canonical envelope: `code`, `message`, `correlationId`, optional `fieldErrors[]`.

## Events & Idempotency

- [ ] Provisioning is idempotent on `idpSubject`.
- [ ] Provisioning publishes an outbox-backed event; API returns without waiting for `people`.
- [ ] RBAC grant/revoke endpoints are idempotent and safe under retry.

## Security

- [ ] Authorization is deny-by-default.
- [ ] Admin actions are gated by Security-owned permission keys (documented in `AGENT_GUIDE.md`).
- [ ] Audit APIs return curated fields by default; any raw payload is gated and redacted.

## Observability

- [ ] Every mutation and provisioning request logs/returns `correlationId`.
- [ ] Security emits audit entries for RBAC/provisioning changes.
- [ ] Outbox/consumer failures route to DLQ with alerting.

## Acceptance Criteria (per resolved question)

### Q: Backend endpoints & schemas — what are the concrete REST/Moqui contracts and error shapes?

- Acceptance: Contracts exist for RBAC, provisioning, and security audit under `/api/v1/security/*`, with documented request/response fields and pagination.
- Test Fixtures:
  - Tenant `t1`, admin principal with `security:role:view`.
- Example API request/response:

```http
GET /api/v1/security/roles?pageIndex=0&pageSize=25
```

```json
{
  "items": [{"roleId": "...", "roleName": "Price Manager", "description": "..."}],
  "pageIndex": 0,
  "pageSize": 25,
  "totalCount": 1
}
```

### Q: Identifiers — are roles referenced by `roleId` or `roleName` in APIs?

- Acceptance: All role mutation endpoints accept `roleId` and reject mutation-by-roleName.
- Test Fixtures:
  - Existing role: `roleId=r1`, `roleName=Manager`.
- Example API request/response:

```http
PUT /api/v1/security/roles/r1
Content-Type: application/json

{"roleName": "New Name", "description": "x"}
```

```json
{"code": "ROLE_NAME_IMMUTABLE", "message": "roleName cannot be changed", "correlationId": "..."}
```

### Q: Error shape — what is the canonical error response format?

- Acceptance: Every non-2xx response returns `code`, `message`, and `correlationId`.
- Test Fixtures:
  - Submit invalid payload to any endpoint.
- Example API request/response:

```http
POST /api/v1/security/roles
Content-Type: application/json

{"roleName": ""}
```

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Validation failed",
  "correlationId": "...",
  "fieldErrors": [{"field": "roleName", "message": "required"}]
}
```

### Q: Authorization scope — which permission keys gate RBAC admin, provisioning, and audit?

- Acceptance: Backend checks and enforces the documented permissions, returning `403` for insufficient grants.
- Test Fixtures:
  - User A: no grants.
  - User B: has `security:role:create`.
- Example API request/response:

```http
POST /api/v1/security/roles
```

```json
{"code": "FORBIDDEN", "message": "Access denied", "correlationId": "..."}
```

### Q: Role name mutability — is `roleName` editable?

- Acceptance: `roleName` cannot be changed after create; only `description` can be updated.
- Test Fixtures:
  - Existing role `roleId=r1`.
- Example API request/response:

```http
PUT /api/v1/security/roles/r1
Content-Type: application/json

{"description": "Updated"}
```

```json
{"roleId": "r1", "roleName": "Manager", "description": "Updated"}
```

### Q: Role uniqueness & casing — what normalization rules apply?

- Acceptance: Creating `" Manager "` when `"manager"` exists returns `409 ROLE_NAME_TAKEN`.
- Test Fixtures:
  - Existing roleName `manager`.
- Example API request/response:

```http
POST /api/v1/security/roles
Content-Type: application/json

{"roleName": "  MANAGER  "}
```

```json
{"code": "ROLE_NAME_TAKEN", "message": "Role name already exists", "correlationId": "..."}
```

### Q: Multi-tenant/location scoping — are roles/assignments scoped by tenant/location?

- Acceptance: Requests are scoped to the caller’s tenant context and cannot read or mutate another tenant’s roles.
- Test Fixtures:
  - Tenant `t1` role `r1`, tenant `t2` role `r2`.
- Example API request/response:

```http
GET /api/v1/security/roles/r2
```

```json
{"code": "NOT_FOUND", "message": "Role not found", "correlationId": "..."}
```

### Q: Location overrides / ABAC — is there any requirement for location-scoped permissions?

- Acceptance: RBAC grant endpoints do not accept location scope fields and document that grants are tenant-global.
- Test Fixtures:
  - Attempt to include `locationId` in grant request.
- Example API request/response:

```json
{"code": "VALIDATION_FAILED", "message": "locationId not supported", "correlationId": "..."}
```

### Q: Role assignment UI — is PrincipalRole/UserRole assignment in scope?

- Acceptance: If the story is v1 RBAC Admin UI, it includes role CRUD and grants only; no principal-role assignment UI routes exist.
- Test Fixtures:
  - Check UI menus/routes; verify no assignment screens.
- Example API request/response:

```json
{"note": "No principal-role assignment UI in v1"}
```

### Q: Provision User — which permission gates provisioning screen and submit?

- Acceptance: Provisioning submit requires `security:user:provision`; unauthorized callers receive `403`.
- Test Fixtures:
  - User without the permission.
- Example API request/response:

```http
POST /api/v1/security/users/provision
```

```json
{"code": "FORBIDDEN", "message": "Access denied", "correlationId": "..."}
```

### Q: Provision User — what are the endpoint contracts for person search, role list, and provision request/response?

- Acceptance: Person search is read-only in `people`; roles list is read-only in `security`; provisioning lives in `security`.
- Test Fixtures:
  - Person search by email fragment.
- Example API request/response:

```http
GET /api/v1/people/persons?search=doe
```

```json
{"items": [{"personId": "p1", "displayName": "John Doe", "email": "john.doe@example.com"}]}
```

### Q: Identity rules — is `username` required or derived, and must email match Person?

- Acceptance: Provisioning rejects mismatched emails with `409 EMAIL_MISMATCH`. Username is derived when not provided.
- Test Fixtures:
  - Person email `john.doe@example.com`, request email `other@example.com`.
- Example API request/response:

```json
{"code": "EMAIL_MISMATCH", "message": "Email must match Person email", "correlationId": "..."}
```

### Q: User status enumeration — what statuses exist and default?

- Acceptance: New provisioned users default to `ACTIVE`; invalid status values return `400 VALIDATION_FAILED`.
- Test Fixtures:
  - Request with `status=UNKNOWN`.
- Example API request/response:

```json
{"code": "VALIDATION_FAILED", "message": "Invalid status", "correlationId": "..."}
```

### Q: Initial role assignment — are zero roles allowed and how are roles identified?

- Acceptance: Provisioning accepts an empty `roleIds` array (or omitted field) and succeeds.
- Test Fixtures:
  - Provision request with no roles.
- Example API request/response:

```json
{"userId": "u1", "resolvedExisting": false, "eventEnqueued": true, "correlationId": "..."}
```

### Q: Link status visibility — is there an API to query link status?

- Acceptance: `GET /api/v1/security/users/{userId}` returns `linkStatus=PENDING|LINKED|FAILED` and UI offers manual refresh.
- Test Fixtures:
  - Immediately after provisioning.
- Example API request/response:

```json
{"userId": "u1", "personId": "p1", "linkStatus": "PENDING"}
```

### Q: Approvals — domain ownership and contract?

- Acceptance: Approval endpoints are not implemented in `pos-security-service`; security stories do not introduce approval state transitions.
- Test Fixtures:
  - Search for routes; verify approvals are routed to `workexec` service.
- Example API request/response:

```json
{"note": "Approvals are owned by workexec; security only gates access."}
```

### Q: Audit Trail (financial exceptions) — ownership, export, references, and sensitivity?

- Acceptance: Financial exception audit listing/detail/export is implemented in the `audit` domain; security gates access and audit APIs default to curated fields.
- Test Fixtures:
  - Audit view permission present, export permission absent.
- Example API request/response:

```json
{"code": "FORBIDDEN", "message": "Export not permitted", "correlationId": "..."}
```

## End

End of document.
