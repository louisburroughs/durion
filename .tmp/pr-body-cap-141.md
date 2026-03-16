## Capability & Traceability
- Capability: `cap:141`
- Parent STORY (durion): louisburroughs/durion#141
- Child issues:
  - louisburroughs/durion-positivity-backend#61 — Audit Trail for Schedule and Assignment Changes (pos-shop-manager)
  - louisburroughs/durion-positivity-backend#62 — Define Shop Roles and Permission Matrix (pos-security-service)
- Domain: `domain:shopmgmt` / `domain:security`

## Contract References (REQUIRED for backend PRs touching API/event behavior)
- Contract guide entries (durion): `domains/shopmgmt/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- Domain notes: `domains/shopmgmt/.business-rules/DOMAIN_NOTES.md`

## Scope

**Story #61 — `pos-shop-manager`: Audit Trail for Schedule and Assignment Changes**
- Added `ShopAuditEntry` entity and `ShopAuditRepository` with immutable records (`@Immutable`, `updatable=false`)
- Implemented `ShopAuditService`/`ShopAuditServiceImpl` with `recordScheduleChange`, `recordAssignmentChange`, and `search` (with 90-day default window and mandatory filter)
- Added `ShopAuditController` for `GET /v1/shop/audit` and `GET /v1/shop/audit/{id}` (no DELETE/PATCH per immutability requirement)
- Actor always sourced from `SecurityContextHolder` per ADR-0018 — no caller-supplied actor fields
- Added `IllegalArgumentException → 400` mapping to `GlobalExceptionHandler`
- 7-year default retention on all entries

**Story #62 — `pos-security-service`: Define Shop Roles and Permission Matrix**
- Implemented `RoleManagementService`/`RoleManagementServiceImpl` with full RBAC management:
  - Role CRUD: create (with case-insensitive duplicate check → 409), read by UUID, read by name, delete (cascades to role_permission + user_role records)
  - Assign/revoke permissions to/from roles
  - Assign/revoke roles to/from users (actor from SecurityContext per ADR-0018)
  - Effective permissions = union of all user roles
- Extended `RoleController` with `getRoleById(UUID)` and `deleteRole(UUID)` endpoints
- Created `UserRoleController` at `/v1/users` for user-role assignment endpoints (avoids path ambiguity with `UserController`)
- Candidate Role Matrix v0 seeded: `READ_ONLY_SCHEDULER`, `DISPATCHER`, `SHOP_MANAGER`, `SECURITY_ADMIN`
- `ROLE_MATRIX.md` documents role definitions, personas, permission bundles, audit scope

**Why:**
- CAP-141 requires both audit trail infrastructure for shop mutations and a capability-level RBAC model so that only authorized roles can perform scheduling, assignment, and RBAC administration actions.

## Tests
- [x] Unit tests added/updated
- [x] Integration tests added/updated (pos-security-service)
- [x] Provider behavioral contract tests added/updated
- [ ] Consumer/UI tests (out of scope for this backend-only PR)
- How to run:
  ```bash
  bash .github/hooks/test-run-hook.sh --repo ~/IdeaProjects/durion-positivity-backend --module pos-shop-manager --goal test
  bash .github/hooks/test-run-hook.sh --repo ~/IdeaProjects/durion-positivity-backend --module pos-security-service --goal test
  ```
- Module verify (both PASS):
  ```bash
  bash .github/hooks/module-verify-hook.sh --repo ~/IdeaProjects/durion-positivity-backend --modules pos-shop-manager,pos-security-service
  ```

## Risk & Rollback
- Risk level: Medium — new audit, RBAC, and permission management surfaces; all guarded by `@PreAuthorize`
- Rollback plan: Revert PR; Flyway migration `V3__seed_candidate_roles.sql` can be rolled back with corresponding DOWN migration in the next sprint

## Checklist
- [x] Branch name matches `cap/141`
- [x] PR title starts with `cap/141:`
- [x] Links to parent + child issues are present
- [x] Contract guide reviewed (behavior assertions consistent with `BACKEND_CONTRACT_GUIDE.md`)
- [x] Required CI checks: module-verify-hook PASS for pos-shop-manager and pos-security-service
- [x] Coverage >= 80% confirmed for both modules (Story #61: 100%, Story #62: 97%)
- [x] ADR-0017 compliance: 200, 201, 204, 400, 401, 403, 404, 409 codes implemented and tested
- [x] ADR-0018 compliance: actor always from SecurityContextHolder — no caller-supplied actor fields
