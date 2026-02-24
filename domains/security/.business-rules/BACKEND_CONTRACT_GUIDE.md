---
title: Security Backend Contract Guide
domain: security
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-security-service/openapi.json
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

- OpenAPI: `durion-positivity-backend/pos-security-service/openapi.json`
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
| Extract authorities from JWT token | `getAuthorities` | GET | `/v1/auth/authorities` | Refer to generated API reference for payload details |
| Extract roles from JWT token | `getRoles` | GET | `/v1/auth/roles` | Refer to generated API reference for payload details |
| Extract subject from JWT token | `getSubject` | GET | `/v1/auth/subject` | Refer to generated API reference for payload details |
| Validate JWT token | `validateToken` | GET | `/v1/auth/validate` | Refer to generated API reference for payload details |
| Get all registered permissions | `getAllPermissions` | GET | `/v1/permissions` | Refer to generated API reference for payload details |
| Get permissions by domain | `getPermissionsByDomain` | GET | `/v1/permissions/domain/{domain}` | Refer to generated API reference for payload details |
| Check if permission exists | `permissionExists` | GET | `/v1/permissions/exists/{permissionName}` | Refer to generated API reference for payload details |
| Validate permission name format | `validatePermissionName` | GET | `/v1/permissions/validate/{permissionName}` | Refer to generated API reference for payload details |
| Get all roles | `getAllRoles` | GET | `/v1/roles` | Refer to generated API reference for payload details |
| Get user role assignments | `getUserRoleAssignments` | GET | `/v1/roles/assignments/user/{userId}` | Refer to generated API reference for payload details |
| Check user permission | `checkUserPermission` | GET | `/v1/roles/check-permission` | Refer to generated API reference for payload details |
| Get user permissions | `getUserPermissions` | GET | `/v1/roles/permissions/user/{userId}` | Refer to generated API reference for payload details |

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
- OpenAPI Source: `durion-positivity-backend/pos-security-service/openapi.json`

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

## CAP-275: [CAP] Login & Token Handling (ADR-0011)

### Capability Metadata

- Capability ID: CAP-275
- Parent Issue: https://github.com/louisburroughs/durion/issues/275
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-security-service/openapi.json`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Extract authorities from JWT token | `getAuthorities` | GET | `/v1/auth/authorities` |
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

- OpenAPI source: `durion-positivity-backend/pos-security-service/openapi.json`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:23:11Z`
- Generated API reference: `domains/security/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/security/.business-rules/AGENT_GUIDE.md`
- `domains/security/.business-rules/DOMAIN_NOTES.md`
- `domains/security/.business-rules/BACKEND_API_REFERENCE.generated.md`
