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
- [ ] Verify financial exception audit UI is treated as **audit-domain data** (read model) with **security-owned permission gating** (no write-side behavior added in security).
- [ ] Confirm UI navigation placement does not imply backend ownership (e.g., “Security → Audit Trail (Financial Exceptions)” is acceptable, but APIs remain under `/api/v1/audit/*`).
- [ ] Confirm v1 scope exclusions are enforced in UI and API surface:
  - [ ] No principal-role assignment UI routes/screens (v1 deferred).
  - [ ] No permission create/edit/delete UI (permission registry is code-first, read-only).
  - [ ] No location-scoped RBAC/ABAC fields accepted or stored (tenant-scoped only).
  - [ ] No approval workflow endpoints/state transitions introduced in security.

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
- [ ] Validate RBAC grant/revoke payloads:
  - [ ] `permissionKeys[]` is required and non-empty for grant/revoke requests.
  - [ ] Every `permissionKey` exists in the permission registry; unknown keys are rejected with `400 VALIDATION_FAILED` and `fieldErrors[]`.
  - [ ] Duplicate keys in `permissionKeys[]` are handled deterministically (either de-duped server-side or rejected with validation error).
- [ ] Validate audit list filters (financial exceptions) before calling backend:
  - [ ] If both `dateFrom` and `dateTo` are provided, verify `dateFrom <= dateTo` (block request and show inline error).
  - [ ] Allow open-ended ranges when only one boundary is provided.
- [ ] Verify audit entries are immutable/append-only in UI and API:
  - [ ] No edit/delete endpoints are exposed for audit entries.
  - [ ] UI does not render edit/delete actions for audit entries (security-owned RBAC audit and audit-domain financial exceptions).

## API Contract

- [ ] Security endpoints are versioned under `/api/v1/security/*` and document request/response schemas.
- [ ] List endpoints support pagination: `pageIndex`, `pageSize`, `totalCount`.
- [ ] Error responses use the canonical envelope: `code`, `message`, `correlationId`, optional `fieldErrors[]`.
- [ ] Verify canonical error envelope also supports optional `details` (when present) and UI does not break if `details` is omitted.
- [ ] Verify RBAC admin endpoints exist and match the documented paths and semantics:
  - [ ] `GET /api/v1/security/roles` supports `search` and pagination.
  - [ ] `GET /api/v1/security/roles/{roleId}` returns role detail.
  - [ ] `POST /api/v1/security/roles` creates role; returns `409 ROLE_NAME_TAKEN` on normalized conflict.
  - [ ] `PUT /api/v1/security/roles/{roleId}` updates description only; rejects `roleName` updates.
  - [ ] `GET /api/v1/security/permissions` lists permission registry (read-only) with pagination/search.
  - [ ] `GET /api/v1/security/roles/{roleId}/permissions` (if implemented) paginates role grants.
  - [ ] `POST /api/v1/security/roles/{roleId}/permissions/grant` accepts `{ permissionKeys: string[] }`.
  - [ ] `POST /api/v1/security/roles/{roleId}/permissions/revoke` accepts `{ permissionKeys: string[] }`.
- [ ] Verify audit-domain financial exception endpoints (under `/api/v1/audit/*`) are versioned, documented, and return paginated lists with canonical error envelope:
  - [ ] List endpoint supports filters used by UI (date range, eventType, actor, references) and pagination.
  - [ ] Detail endpoint returns curated fields by default.
  - [ ] Export endpoint contract is explicit (sync file vs async job) and includes correlationId on failures.

## Events & Idempotency

- [ ] Provisioning is idempotent on `idpSubject`.
- [ ] Provisioning publishes an outbox-backed event; API returns without waiting for `people`.
- [ ] RBAC grant/revoke endpoints are idempotent and safe under retry.
- [ ] Verify RBAC grant/revoke idempotency is observable and consistent:
  - [ ] Re-granting an already-granted permission returns success (2xx) and does not create duplicates.
  - [ ] Re-revoking an already-revoked permission returns success (2xx) and does not error.
- [ ] Verify RBAC mutations emit security-owned audit entries (append-only) with correlationId linkage:
  - [ ] ROLE_CREATED
  - [ ] ROLE_UPDATED (description changes)
  - [ ] ROLE_PERMISSION_GRANTED
  - [ ] ROLE_PERMISSION_REVOKED
- [ ] Verify export (financial exception audit) is safe under retry:
  - [ ] If async export uses a job id, repeated identical requests do not create unbounded duplicate jobs (either idempotency key supported or documented as non-idempotent with rate limiting).
  - [ ] If sync export streams a file, retries do not mutate server state.

## Security

- [ ] Authorization is deny-by-default.
- [ ] Admin actions are gated by Security-owned permission keys (documented in `AGENT_GUIDE.md`).
- [ ] Audit APIs return curated fields by default; any raw payload is gated and redacted.
- [ ] Verify UI deny-by-default posture for security admin screens:
  - [ ] Menu/route entries require corresponding `*:view` permissions.
  - [ ] If authorization state cannot be determined (e.g., permission lookup fails), UI defaults to read-only and shows an error banner.
  - [ ] Mutation controls are hidden/disabled without the specific mutation permission, and backend 403 is still handled safely.
- [ ] Verify permission keys used for gating match the documented matrix and are not hardcoded inconsistently across screens:
  - [ ] `security:role:view`, `security:role:create`, `security:role:update`
  - [ ] `security:permission:view`
  - [ ] `security:role_permission:grant`, `security:role_permission:revoke`
  - [ ] `security:audit_entry:view` (security-owned RBAC audit)
  - [ ] `security:audit_entry:export` (financial exception audit export)
- [ ] Verify tenant scoping is enforced server-side and UI does not accept `tenantId` as input or query parameter.
- [ ] Verify 403 handling does not leak existence of protected resources (e.g., audit entry detail should not reveal whether an id exists when forbidden).

## Observability

- [ ] Every mutation and provisioning request logs/returns `correlationId`.
- [ ] Security emits audit entries for RBAC/provisioning changes.
- [ ] Outbox/consumer failures route to DLQ with alerting.
- [ ] Verify UI surfaces `correlationId` for all non-2xx responses (banner/toast + copyable value).
- [ ] Verify audit list/detail screens display correlationId where present (especially for error states).
- [ ] Verify export flows provide traceability:
  - [ ] If async: UI displays `exportJobId` and status (if returned) and preserves correlationId on failure.
  - [ ] If sync: UI handles file download without logging sensitive content; failures show correlationId.

## Performance & Failure Modes

- [ ] Verify list screens use server-side pagination and do not attempt to load full datasets:
  - [ ] Roles list paginated.
  - [ ] Permission registry paginated.
  - [ ] Security audit entries paginated.
  - [ ] Financial exception audit list paginated.
- [ ] Verify safe UI defaults for page size and sorting are applied and verifiable:
  - [ ] Default sort for audit lists is most-recent-first (e.g., `eventTs desc`).
- [ ] Verify network/timeouts are handled without assuming mutation success:
  - [ ] UI shows retry affordance on network failure/timeouts.
  - [ ] UI refreshes from backend after any reported success (no client-side caching assumptions).
- [ ] Verify conflict handling for RBAC mutations:
  - [ ] On `409`, UI shows conflict banner with `code` and `correlationId` and provides a “Reload” action.
  - [ ] UI does not attempt auto-merge of role/grant state.
- [ ] Verify export failure modes are handled:
  - [ ] 403 shows “not permitted” (no export permission).
  - [ ] 413/422 (too large/constraints) instructs user to narrow filters.
  - [ ] 5xx shows generic failure with correlationId; filters remain intact for retry.

## Testing

- [ ] Add/verify API contract tests for:
  - [ ] Role create/update (roleName immutable; description editable).
  - [ ] Role name normalization conflict (`ROLE_NAME_TAKEN`).
  - [ ] Permission registry list is read-only (no mutation endpoints).
  - [ ] Grant/revoke idempotency (no duplicates; safe under retry).
  - [ ] Canonical error envelope on all non-2xx responses (includes `correlationId`).
- [ ] Add/verify authorization tests (deny-by-default):
  - [ ] 403 for missing `security:role:view` on roles list.
  - [ ] 403 for missing mutation permissions on create/update/grant/revoke.
  - [ ] 403 for missing audit view/export permissions.
- [ ] Add/verify UI tests (Moqui screens/transitions) for:
  - [ ] RoleName is not editable on Role Detail.
  - [ ] Mutation buttons disabled while submitting (prevents double-submit).
  - [ ] FieldErrors map to inline validation messages.
  - [ ] 401 triggers session-expired/login flow; 403 shows not-authorized state without data.
  - [ ] CorrelationId is displayed on error banners.
- [ ] Add/verify financial exception audit UI tests:
  - [ ] Date range validation blocks `dateFrom > dateTo`.
  - [ ] Drilldown from Order/Invoice detail passes correct filter params.
  - [ ] Export button hidden/disabled without export permission.
  - [ ] Export failure preserves filters and allows retry.
- [ ] Verify audit immutability in UI:
  - [ ] No edit/delete actions exist for audit entries (security-owned RBAC audit and audit-domain financial exceptions).

## Documentation

- [ ] Update/verify `AGENT_GUIDE.md` references are consistent with implemented permission keys and endpoint paths.
- [ ] Document UI routes/screens for RBAC admin:
  - [ ] `/admin/security/roles`
  - [ ] `/admin/security/roles/{roleId}`
  - [ ] `/admin/security/permissions`
  - [ ] `/admin/security/audit`
- [ ] Document financial exception audit UI routes/screens and drilldowns:
  - [ ] Security → Audit Trail (Financial Exceptions) entry point
  - [ ] Order Detail → “View Financial Exception Audit”
  - [ ] Invoice Detail → “View Financial Exception Audit”
- [ ] Document export behavior (sync vs async) and any job status/download endpoints once finalized.
- [ ] Document redaction/curation policy for audit fields and explicitly note that raw payload viewing is out-of-scope unless a gated permission and endpoint are introduced.

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

## Open Questions to Resolve

- [ ] Domain ownership / label conflict: Financial exception audit is owned by the `audit` domain read model. Should the story be relabeled to `domain:audit` with Security only providing permission gating? If not, document rationale and ensure reviewers validate correct ownership boundaries.
- [ ] Audit API contracts (blocking): Confirm exact endpoints, query params, sorting semantics, and response schemas for:
  - [ ] list: `/api/v1/audit/exceptions` (or final path)
  - [ ] detail: `/api/v1/audit/exceptions/{auditEntryId}` (confirm id field name)
  - [ ] export: path/method and response type (sync file vs async job)
- [ ] Event type codes (blocking): Define canonical `eventType` values for price overrides/refunds/cancellations and any subtypes (e.g., partial refund, void, line-item override) so UI filters and validation are deterministic.
- [ ] Reason requirements (blocking): Is `reasonText` required for all exception types? Is there a `reasonCode` enumeration? If yes, confirm validation rules and how UI should present/select them.
- [ ] Sensitive payload policy (blocking): Is there a permission-gated “raw payload” view for financial exception audit? If yes:
  - [ ] Define the permission key.
  - [ ] Define redaction rules and confirm curated-by-default behavior.
  - [ ] Define whether raw payload is returned in the same endpoint or a separate endpoint.
- [ ] Export behavior (blocking): Must export be asynchronous with job status + later download, or is synchronous CSV acceptable?
  - [ ] If async: define job status endpoint, download endpoint, retention period, and idempotency/rate limits.
- [ ] Reference linking rules (blocking): Confirm authoritative identifiers for navigation and display:
  - [ ] orderId vs external order number
  - [ ] invoiceId vs invoice number
  - [ ] paymentId vs processor reference
  - [ ] which target screens exist and what permissions gate them

## End

End of document.
