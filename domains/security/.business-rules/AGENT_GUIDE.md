# AGENT_GUIDE.md

## Summary

This guide defines the normative, implementation-ready business rules for the `security` domain: RBAC (roles, permissions, assignments), authorization decisions, and user provisioning orchestration. It resolves the prior “CLARIFY/TODO” items into explicit decisions so frontend and backend teams can implement admin screens and APIs without guessing. A non-normative rationale companion exists in `DOMAIN_NOTES.md`.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `DOMAIN_NOTES.md`
- [x] Reconciled todos from original AGENT_GUIDE

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-INVENTORY-001 | Deny-by-default authorization |
| DECISION-INVENTORY-002 | Permission key format and immutability |
| DECISION-INVENTORY-003 | Role name normalization and uniqueness |
| DECISION-INVENTORY-004 | Role name immutability (rename-by-recreate) |
| DECISION-INVENTORY-005 | RBAC mutation semantics (grant/revoke + replace-set) |
| DECISION-INVENTORY-006 | Permission registry is code-first (UI read-only) |
| DECISION-INVENTORY-007 | Principal-role assignments (model supports effective dating; UI deferred) |
| DECISION-INVENTORY-008 | Tenant scoping (no location-scoped RBAC in v1) |
| DECISION-INVENTORY-009 | Provisioning identity key (IdP subject) and email match policy |
| DECISION-INVENTORY-010 | Provisioning initial roles (optional; roleId identifiers) |
| DECISION-INVENTORY-011 | Provisioning linking via outbox event; link-status visibility |
| DECISION-INVENTORY-012 | Audit ownership split (RBAC audit vs financial exception audit) |
| DECISION-INVENTORY-013 | Audit payload redaction and permissions |
| DECISION-INVENTORY-014 | Approvals are not security-owned (security only gates access) |
| DECISION-INVENTORY-015 | Canonical REST error envelope and status codes |

## Domain Boundaries

### What `security` owns (system of record)

- RBAC administration primitives:
  - Roles (create/update; delete is optional and off by default)
  - Role ↔ Permission grants
  - Permission registry (query + validation; permissions are registered by services)
- Authorization decisioning semantics (deny-by-default; deterministic evaluation)
- User provisioning orchestration:
  - Create/resolve a Security `User`
  - Publish a provisioning event (transactional outbox)
- RBAC/provisioning audit events and query API (append-only)

### What `security` does *not* own

- `Person` and `UserPersonLink` (owned by `people`)
- Authentication source of truth (external IdP)
- Approval workflows and state machines (owned by `workexec` or another workflow domain) (**Decision DECISION-INVENTORY-014**)
- Financial exception business rules (price overrides/refunds/cancellations) and their authoritative audit events (owned by the relevant business domains and exposed via the `audit` domain read model) (**Decision DECISION-INVENTORY-012**)

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| User | Security-owned user identity record, keyed by IdP subject; links to Person asynchronously. |
| Role | Named bundle of permissions (grant set). |
| Permission | Code-first registry entry identified by immutable `permissionKey`. |
| RolePermission | Grant mapping `(roleId, permissionKey)` (unique). |
| PrincipalRole | Assignment mapping `(principalId, roleId)` with optional effective dating (model). |
| AuditEntry | Append-only record for security-owned mutations; financial exception audit entries are owned by `audit`. |

## Invariants / Business Rules

### Authorization / RBAC

- Deny-by-default: absence of a grant results in `DENY`. (**Decision DECISION-INVENTORY-001**)
- Permission keys are immutable and validated as `domain:resource:action` using snake_case. (**Decision DECISION-INVENTORY-002**)
- Roles are referenced by immutable `roleId` in APIs; `roleName` is display-only. (**Decision DECISION-INVENTORY-004**)
- Role names are normalized (trim + collapse whitespace + lowercase) for uniqueness checks. (**Decision DECISION-INVENTORY-003**)
- Role permission updates support:
  - Idempotent incremental grant/revoke, and
  - Optional replace-set semantics for “set exactly these permissions”. (**Decision DECISION-INVENTORY-005**)

### Provisioning

- Provisioning creates/resolves a Security `User` idempotently using IdP subject as the identity key. (**Decision DECISION-INVENTORY-009**)
- Provisioning is asynchronous with respect to `people` linking:
  - Provisioning returns success after writing the user + outbox record.
  - `people` consumes the event and creates `UserPersonLink` idempotently.
  - Provisioning must not block on linking. (**Decision DECISION-INVENTORY-011**)
- Initial roles are optional (zero roles allowed) and referenced by `roleId`. (**Decision DECISION-INVENTORY-010**)

### Audit

- Audit entries are append-only (no edit/delete).
- RBAC/provisioning audit is served by `security`; financial exception audit is served by `audit`, with access gated by security permissions. (**Decision DECISION-INVENTORY-012**)
- Raw payload visibility is restricted; default responses return redacted/curated fields only. (**Decision DECISION-INVENTORY-013**)

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-INVENTORY-001 | Missing grant implies deny | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-001--deny-by-default-authorization) |
| DECISION-INVENTORY-002 | `domain:resource:action` snake_case keys are immutable | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-002--permission-key-format-and-immutability) |
| DECISION-INVENTORY-003 | Role names are normalized and unique (case-insensitive) | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-003--role-name-normalization-and-uniqueness) |
| DECISION-INVENTORY-004 | Role rename requires recreate/migrate | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-004--role-name-immutability-rename-by-recreate) |
| DECISION-INVENTORY-005 | Idempotent grant/revoke + optional replace-set | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-005--rbac-mutation-semantics-grantrevoke--replace-set) |
| DECISION-INVENTORY-006 | Permission registry is code-first; UI read-only | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-006--permission-registry-is-code-first-ui-read-only) |
| DECISION-INVENTORY-007 | Assignment model supports effective dating; UI deferred | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-007--principal-role-assignments-effective-dating-ui-deferred) |
| DECISION-INVENTORY-008 | Tenant-scoped RBAC; no location-scoped RBAC in v1 | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-008--tenant-scoping-no-location-scoped-rbac-in-v1) |
| DECISION-INVENTORY-009 | Provisioning identity key is IdP subject; email must match Person | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-009--provisioning-identity-key-idp-subject-and-email-match) |
| DECISION-INVENTORY-010 | Initial roles optional; use roleId | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-010--provisioning-initial-roles-optional-roleid-identifiers) |
| DECISION-INVENTORY-011 | Outbox event + link-status visibility | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-011--provisioning-linking-via-outbox-event-link-status-visibility) |
| DECISION-INVENTORY-012 | RBAC audit in security; financial exceptions in audit domain | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-012--audit-ownership-split-rbac-vs-financial-exceptions) |
| DECISION-INVENTORY-013 | Raw payload redaction; gated full payload | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-013--audit-payload-redaction-and-permission-gating) |
| DECISION-INVENTORY-014 | Approvals are workflow-owned, not security-owned | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-014--approvals-are-not-security-owned-security-only-gates) |
| DECISION-INVENTORY-015 | Canonical error envelope + status semantics | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-015--canonical-rest-error-envelope-and-status-codes) |

## Open Questions (from source)

### Q: Backend endpoints & schemas — what are the concrete REST/Moqui contracts and error shapes?

- Answer: Security APIs are hosted by `pos-security-service` and use a versioned REST base path: `/api/v1/security/*`. Pagination uses `pageIndex/pageSize/totalCount`, and all errors use the canonical envelope defined in **Decision DECISION-INVENTORY-015**.
- Assumptions:
  - REST endpoints are accessible via Moqui as needed (via gateway or direct service bridge).
  - IDs are UUIDs unless explicitly documented otherwise.
- Rationale:
  - Stable contracts prevent frontend “guessing” and reduce cross-domain coupling.
- Impact:
  - Backend must document and implement endpoint schemas; frontend uses only documented fields.
- Decision ID: DECISION-INVENTORY-015

### Q: Identifiers — are roles referenced by `roleId` or `roleName` in APIs?

- Answer: Roles are referenced by immutable `roleId` (UUID). `roleName` is display-only and immutable (rename requires recreate). Permissions are referenced by immutable `permissionKey`.
- Assumptions:
  - Existing systems can store `roleId` references for assignments.
- Rationale:
  - Stable identifiers avoid broken deep links and audit ambiguity.
- Impact:
  - UI uses `roleId` for mutations; backend rejects role-name-based mutation contracts.
- Decision ID: DECISION-INVENTORY-004

### Q: Error shape — what is the canonical error response format?

- Answer: All error responses return a stable JSON envelope with: `code`, `message`, `correlationId`, optional `fieldErrors[]`, and optional `details`.
- Assumptions:
  - `correlationId` is generated if absent and returned in all responses.
- Rationale:
  - Frontend can reliably map errors to UI states and support can correlate logs.
- Impact:
  - Backend aligns controllers/exception mappers; tests assert presence of `code` and `correlationId`.
- Decision ID: DECISION-INVENTORY-015

### Q: Authorization scope — which permission keys gate RBAC admin, provisioning, and audit?

- Answer: Security defines and owns the permission keys used to gate these screens and endpoints. Minimum keys:
  - RBAC view: `security:role:view`, `security:permission:view`
  - Role CRUD: `security:role:create`, `security:role:update` (delete optional: `security:role:delete`)
  - Grants: `security:role_permission:grant`, `security:role_permission:revoke` (or `security:role_permission:replace`)
  - Provisioning: `security:user:provision`
  - Security audit view/export: `security:audit_entry:view`, `security:audit_entry:export`
- Assumptions:
  - Permission keys are registered on service startup and are immutable.
- Rationale:
  - Removes a blocking dependency (“don’t invent permission names”) by making Security the authoritative source.
- Impact:
  - Permission registry seeded; UI uses these keys for route gating and backend enforcement.
- Decision ID: DECISION-INVENTORY-002

### Q: Role name mutability — is `roleName` editable?

- Answer: `roleName` is immutable; only `description` (and other non-identifier metadata) may be edited. Renaming a role is done by creating a new role and migrating assignments/grants.
- Assumptions:
  - Existing role references are stored by `roleId`.
- Rationale:
  - Avoids audit/history ambiguity and broken references.
- Impact:
  - UI disables editing roleName; backend rejects roleName updates.
- Decision ID: DECISION-INVENTORY-004

### Q: Role uniqueness & casing — what normalization rules apply?

- Answer: Uniqueness is enforced on a normalized name computed as: trim leading/trailing whitespace, collapse internal whitespace runs to a single space, then lowercase.
- Assumptions:
  - UI may still show original casing as entered.
- Rationale:
  - Prevents confusing duplicates while preserving human-friendly display.
- Impact:
  - Database unique index on `roleNameNormalized` (or equivalent); create role returns `409 ROLE_NAME_TAKEN`.
- Decision ID: DECISION-INVENTORY-003

### Q: Multi-tenant/location scoping — are roles/assignments scoped by tenant/location?

- Answer: RBAC is tenant-scoped (roles, grants, and assignments belong to a tenant). Location-scoped grants/overrides are out-of-scope for v1.
- Assumptions:
  - Tenant context is derived from trusted auth claims or request context, not user input.
- Rationale:
  - Tenant isolation is mandatory; location-scoped ABAC requires more explicit policy design.
- Impact:
  - API queries/mutations require tenant context; add indexes by tenant.
- Decision ID: DECISION-INVENTORY-008

### Q: Location overrides / ABAC — is there any requirement for location-scoped permissions?

- Answer: No, not in v1. Any mention of “location overrides” is deferred until a separate story defines ABAC requirements, policy language, and enforcement model.
- Assumptions:
  - All role permissions apply globally within a tenant.
- Rationale:
  - Prevents accidental policy sprawl and inconsistent enforcement.
- Impact:
  - UI does not expose per-location overrides; backend does not accept location scope on grants.
- Decision ID: DECISION-INVENTORY-008

### Q: Role assignment UI — is PrincipalRole/UserRole assignment in scope?

- Answer: The data model supports assignments (including effective dating), but the RBAC Admin UI does not include principal-role assignment in v1 unless a story explicitly requires it.
- Assumptions:
  - Assignments can be managed via internal/admin tooling or a later UI iteration.
- Rationale:
  - Keeps initial admin UI focused and reduces permission management complexity.
- Impact:
  - If later enabled, add endpoints under `/api/v1/security/principals/*/roles` gated by `security:principal_role:*` permissions.
- Decision ID: DECISION-INVENTORY-007

### Q: Provision User — which permission gates provisioning screen and submit?

- Answer: Provisioning is gated by `security:user:provision` for submit. The screen may also be gated by `security:user:provision` (single permission covers view+submit for v1).
- Assumptions:
  - If finer-grained control is needed later, split into `security:user:provision:view` and `security:user:provision:execute`.
- Rationale:
  - Simple least-privilege for a high-risk operation.
- Impact:
  - Backend checks permission; UI hides route and blocks submit on 401/403.
- Decision ID: DECISION-INVENTORY-002

### Q: Provision User — what are the endpoint contracts for person search, role list, and provision request/response?

- Answer: Person search is served by `people` (read-only), role list by `security`, and provisioning by `security`:
  - `GET /api/v1/people/persons?search=...` (returns `personId`, name, email)
  - `GET /api/v1/security/roles` (returns `roleId`, `roleName`, `description`)
  - `POST /api/v1/security/users/provision`
- Assumptions:
  - Gateway routes exist to reach each service.
- Rationale:
  - Preserves domain ownership while enabling orchestration.
- Impact:
  - Frontend uses these endpoints and does not infer hidden fields.
- Decision ID: DECISION-INVENTORY-011

### Q: Identity rules — is `username` required or derived, and must email match Person?

- Answer: `idpSubject` is the identity key. `username` is derived from Person email (local-part) unless explicitly provided. Provisioning requires the submitted email to match the selected Person email (case-insensitive) to prevent mismatched identity linking.
- Assumptions:
  - Person records have a canonical primary email.
- Rationale:
  - Minimizes the risk of provisioning a user tied to the wrong Person.
- Impact:
  - Backend validates email match; conflicts return `409 EMAIL_MISMATCH`.
- Decision ID: DECISION-INVENTORY-009

### Q: User status enumeration — what statuses exist and default?

- Answer: Security User status values are `ACTIVE` and `DISABLED`. Default on provisioning is `ACTIVE`.
- Assumptions:
  - Downstream systems use `DISABLED` to block access if needed.
- Rationale:
  - Keeps status model simple and predictable.
- Impact:
  - UI renders explicit statuses; backend rejects unknown status values.
- Decision ID: DECISION-INVENTORY-009

### Q: Initial role assignment — are zero roles allowed and how are roles identified?

- Answer: Zero roles are allowed at provision time. If roles are provided, they are identified by `roleId`.
- Assumptions:
  - Default access is minimal until roles are assigned.
- Rationale:
  - Supports phased onboarding and avoids forcing a “default role” policy.
- Impact:
  - Backend validates roleIds exist; UI allows empty selection.
- Decision ID: DECISION-INVENTORY-010

### Q: Link status visibility — is there an API to query link status?

- Answer: Yes. Security exposes `GET /api/v1/security/users/{userId}` returning `linkStatus` = `PENDING|LINKED|FAILED` plus timestamps. UI should provide a manual “Refresh status” action; no automatic polling in v1.
- Assumptions:
  - `people` emits a “link created” event or `security` can query `people` by userId.
- Rationale:
  - Avoids polling load while still giving admins confidence in eventual consistency.
- Impact:
  - Add linkStatus fields to response; add runbook for `FAILED`.
- Decision ID: DECISION-INVENTORY-011

### Q: Approvals — domain ownership and contract?

- Answer: Approvals are workflow-owned (default owner: `workexec`). Security does not own approval state or endpoints, but does define/host permission keys used to gate approval actions.
- Assumptions:
  - Existing approval endpoints live in a workflow service.
- Rationale:
  - Prevents Security from becoming a workflow engine.
- Impact:
  - Stories labeled `domain:security` for approvals must be relabeled to the owning domain.
- Decision ID: DECISION-INVENTORY-014

### Q: Audit Trail (financial exceptions) — ownership, export, references, and sensitivity?

- Answer: Financial exception audit data is owned by `audit` as a read model fed by domain events. Security enforces access via permissions. Export is asynchronous and produces CSV by default.
- Assumptions:
  - The platform has (or will have) a dedicated `audit` service/read model.
- Rationale:
  - Consolidates cross-domain audit queries without violating domain write ownership.
- Impact:
  - Implement `/api/v1/audit/exceptions/*` endpoints in audit domain; security implements view/export permissions.
- Decision ID: DECISION-INVENTORY-012

## Todos Reconciled

- Original todo: "publish canonical error schema for Moqui/REST consumers" → Resolution: Resolved in this guide via **Decision DECISION-INVENTORY-015**.
- Original todo: "CLARIFY permission keys gate access" → Resolution: Resolved by defining Security-owned permission keys (**Decision DECISION-INVENTORY-002**).
- Original todo: "CLARIFY replace-set vs grant/revoke" → Resolution: Support both semantics; grant/revoke required, replace-set optional (**Decision DECISION-INVENTORY-005**).

## End

End of document.
