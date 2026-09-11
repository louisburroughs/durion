---
title: Security Backend Contract Guide
domain: security
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-security-service/openapi.yaml
openapi_commit: ca7fadc3
last_verified_utc: 2026-02-24T14:23:11Z
last_updated: 2026-02-24
api_reference_generated: domains/security/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Security Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Security domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-security-service/openapi.yaml`
- Generated API reference: `domains/security/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/security/.business-rules/AGENT_GUIDE.md`

## How To Use This Guide

Backend coder workflow:

1. Read `Domain Invariants` and the relevant capability section.
2. Validate behavior constraints before implementing endpoint changes.
3. Use `operationId` mappings here, then confirm payload details in generated API reference.
4. Ensure tests cover each changed behavioral assertion.

Frontend developer workflow:

1. Start with `Frontend API Lookup` and identify the `operationId` for the UI action.
2. Open generated API reference for exact payload and response details.
3. Implement error handling and headers described in this guide.

## Domain Invariants

- Security behavioral rules are authoritative in backend services, not inferred from frontend state.
- Mutating operations require explicit permission enforcement and auditable outcomes.
- Error responses and correlation headers must be deterministic and traceable across requests.
- Cross-domain interactions must go through API/event contracts, not direct data coupling.
- A user holds a role only through an effective-dated `role_assignments` row, with no undated grant, and token issuance, `getUserPermissions`, the
  assignments listing and the person-decision resolve the same effective set for the same user and instant (ADR-0061 amendment 2026-09-09; implemented in
  `durion-positivity-backend#1916`).

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-253 | `durion#253` | stable-for-ui | [CAP] Roles, Permissions, and Audit Controls |
| CAP-275 | `durion#275` | draft | [CAP] Login & Token Handling (ADR-0011) |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Revoke JWT token | `revokeToken` | DELETE | `/v1/auth/revoke` | Refer to generated API reference for payload details |
| Revoke role assignment | `revokeRoleAssignment` | DELETE | `/v1/roles/assignments/{assignmentId}` | Refer to generated API reference for payload details |
| Delete a user | `deleteUser` | DELETE | `/v1/users/{id}` | Refer to generated API reference for payload details |
| Extract authorities from JWT token | *(not in OpenAPI — nearest shipped: `getRoles`, `getSubject`)* | GET | `/v1/auth/authorities` | Refer to generated API reference for payload details |
| Extract roles from JWT token | `getRoles` | GET | `/v1/auth/roles` | Refer to generated API reference for payload details |
| Extract subject from JWT token | `getSubject` | GET | `/v1/auth/subject` | Refer to generated API reference for payload details |
| Validate JWT token | `validateToken` | GET | `/v1/auth/validate` | Refer to generated API reference for payload details |
| Get all registered permissions | `listPermissions_1` | GET | `/v1/permissions` | Refer to generated API reference for payload details |
| Get permissions by domain | `getPermissionsByDomain` | GET | `/v1/permissions/domain/{domain}` | Refer to generated API reference for payload details |
| Check if permission exists | `permissionExists` | GET | `/v1/permissions/exists/{permissionName}` | Refer to generated API reference for payload details |
| Validate permission name format | `validatePermissionName` | GET | `/v1/permissions/validate/{permissionName}` | Refer to generated API reference for payload details |
| Get all roles | `getAllRoles` | GET | `/v1/roles` | Refer to generated API reference for payload details |
| Get user role assignments | `getUserRoleAssignments` | GET | `/v1/roles/assignments/user/{userId}` | Refer to generated API reference for payload details |
| Get user permissions | `getUserPermissions` | GET | `/v1/roles/permissions/user/{userId}` | Refer to generated API reference for payload details |

### Platform Provisioning & Support (ADR-0062 WS2b-3, WS2b-4, WS8)

Added 2026-09-11 (backend #1950, #1954, #1955). Platform-tenant-only operations, not part of a tenant's own
`/v1/**` surface.

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Mint a first-administrator activation token | *(operationId not stated in source PR body)* | POST | `/v1/platform/tenants/{tenantId}/administrators/{userId}/activation-token` | Requires `platform:tenant:provision`, platform-tenant binding only (403 `PLATFORM_TENANT_REQUIRED`). Refuses a user not `awaiting_activation` with 409 `USER_NOT_AWAITING_ACTIVATION`. Returns `{token, expiresAt}` once; token is one-time, 72-hour, hashed at rest |
| Activate the first administrator | `activate` | POST | `/v1/auth/activate` | Unauthenticated (`permitAll`); `{token, newPassword}`. Unknown/expired/used token is 401 `ACTIVATION_TOKEN_INVALID` |
| Mint a platform support (impersonation) token | `mintImpersonationToken` | POST | `/v1/platform/tenants/{tenantId}/impersonation-token` | Requires `platform:tenant:impersonate`, platform-tenant binding only. Target must be `ACTIVE`, not the platform tenant (409 `TENANT_NOT_IMPERSONABLE`). Returns a 15-minute, no-refresh token scoped to the target tenant's read-only `SUPPORT` role; audited in both tenants |
| Reconcile a tenant's roles against the platform template | `reconcileTemplate` | POST | `/v1/platform/tenants/{tenantId}/roles/reconcile-template` | Requires `platform:tenant:provision`, platform-tenant binding only. Creates missing template roles and unions new template grants onto existing roles; never removes a tenant's own edits; idempotent. Guarded by `PlatformGrantGuard` (added across review): a role cannot be marked template (`template_key` set) while it holds a `platform:*` permission, and a row named `PLATFORM_ADMIN` is refused outright regardless of its grants — either would otherwise have its grants copied into every tenant by this endpoint and by provisioning. Both refusals also apply to `provisionTemplateRole` (the platform bulk-load path that marks `roles.csv` rows as template); both answer 400 `VALIDATION_ERROR` |

### Tenant Registry — Internal Only (ADR-0062 WS4-2)

`GET /internal/v1/tenants[?status=ACTIVE]` is served by `pos-tenant`, not `pos-security-service`. It is
`@Hidden` (excluded from the public OpenAPI document and the Angular SDK), guarded by a shared-secret header
(`X-Tenant-Registry-Secret`), and is **internal/mesh-only**: the gateway does not route any `/<service>/internal/**`
path, so it is unreachable from outside the cluster. Used only by `pos-tenancy-common`'s `RemoteTenantRegistry`
for service-to-service tenant lookups; no frontend or external caller should reference it.

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-253: [CAP] Roles, Permissions, and Audit Controls

### Capability Metadata

- Capability ID: CAP-253
- Parent Issue: https://github.com/louisburroughs/durion/issues/253
- Capability Status: stable-for-ui
- OpenAPI Source: `durion-positivity-backend/pos-security-service/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Revoke JWT token | `revokeToken` | DELETE | `/v1/auth/revoke` |
| Revoke role assignment | `revokeRoleAssignment` | DELETE | `/v1/roles/assignments/{assignmentId}` |
| Delete a user | `deleteUser` | DELETE | `/v1/users/{id}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.
- Role assignments are the only way a user holds a role (`durion-positivity-backend#1916`). Every grant path (`createUser` with roles, bulk user ingest,
  self-registration, the operational seed, `PUT /v1/users/{username}/roles`, `assignUserRole`, `createRoleAssignment`) produces an effective-dated
  `role_assignments` row; `PUT /v1/users/{username}/roles` keeps its replace contract and reconciles assignments (assigns what is missing, revokes what is
  absent); `assignUserRole` is idempotent when an effective assignment for the pair already exists.
- The assignment window is half-open, `[effectiveStartDate, effectiveEndDate)`, evaluated against the current instant. A revocation stops the assignment
  contributing at the next authorization decision, revokes the holder's live tokens once the revocation commits, and the next token issued carries an `exp`
  clamped to the earliest remaining assignment end.
- `getPersonAuthorizationDecision` evaluates the person's linked user against the same effective assignments as token issuance, honouring the window.
- `assignPrincipalRole` and `getAuthorizationDecision` (the string-keyed principal matrix) were removed in `durion-positivity-backend#1916` and no longer
  exist in the API or the SDKs.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.
- To show or change what a user holds, use the assignment operations (`getUserRoleAssignments`, `assignUserRole`, `revokeUserRole`, `createRoleAssignment`,
  `revokeRoleAssignment`) or the People access surface that fronts them; there is no separate "direct roles" view.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.
- ADR-0040 §1: roles are bundles that deliver permission grants; backend authorization is permission-based.
- ADR-0061 §1, §3, §4 and the 2026-09-09 amendment: `role_assignments` is the only store of a user's roles; the principal matrix is retired; token lifetime is
  clamped to, and live tokens revoked on, role-assignment change.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-security-service/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-275: [CAP] Login & Token Handling (ADR-0011)

### Capability Metadata

- Capability ID: CAP-275
- Parent Issue: https://github.com/louisburroughs/durion/issues/275
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-security-service/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Extract authorities from JWT token | *(not in OpenAPI — nearest shipped: `getRoles`, `getSubject`)* | GET | `/v1/auth/authorities` |
| Extract roles from JWT token | `getRoles` | GET | `/v1/auth/roles` |
| Extract subject from JWT token | `getSubject` | GET | `/v1/auth/subject` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-security-service/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-security-service/openapi.yaml`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:23:11Z`
- Generated API reference: `domains/security/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/security/.business-rules/AGENT_GUIDE.md`
- `domains/security/.business-rules/DOMAIN_NOTES.md`
- `domains/security/.business-rules/BACKEND_API_REFERENCE.generated.md`
