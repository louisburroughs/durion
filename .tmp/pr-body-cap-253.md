## Capability & Traceability
- Capability: `cap:253`
- Parent STORY (durion): louisburroughs/durion#253
- Child issues: louisburroughs/durion-positivity-backend#1, louisburroughs/durion-positivity-backend#2
- Domain: `domain:security`, `domain:accounting`

## Contract References (REQUIRED for backend PRs touching API/event behavior)
- Contract guide entries (durion): `domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md` → CAP-253 section
- Durion contract PR: N/A (contract guide updated locally in durion repo)

## Scope
- What changed:
  - **pos-security-service (Story #2 — Roles, Permissions, and Audit Controls):**
    - `GlobalExceptionHandler.java`: Added 403 Forbidden handling for `AccessDeniedException` (permission denied events)
    - `AuditController.java`: New REST controller exposing `GET /v1/security/audit/events` with `accounting:audit:view` permission enforcement
    - `AuditEventService.java` + `AuditEventServiceImpl.java`: New service interface and implementation for storing/querying audit log events
    - `AuditLogEventRepository.java`: New Spring Data JPA repository for audit log events
    - `RoleManagementServiceImpl.java`: Added `@EmitEvent` annotations on `assignRoleToUser` and `revokeRoleFromUser` for audit trail emission (`RoleAssignedToUser`, `RoleRevokedFromUser`)
    - `SecurityEventTypes.java`: Registered new event types for the audit controls workflow
    - `UserRoleController.java`: Wired audit emission on user-role assignment/revocation endpoints
    - `AuditLogEventRequest.java`, `ErrorResponse.java`: Supporting DTOs
  - **pos-accounting (Story #1 — Financial Audit Trail):**
    - `AuditTrailController.java`: New REST controller exposing `GET /v1/accounting/audit/trail` with `accounting:audit:view` permission enforcement
    - `AuditTrailServiceImpl.java`: Implemented audit trail querying with filtering by date range, actor, and resource type
    - `AuditTrailQueryService` + `PriceOverrideAuthorizationServiceImpl.java`: Price override authorization service implementation, capturing authorization decisions in the audit trail
    - `PriceOverrideRequest.java`: DTO for price override authorization requests
- Why:
  - CAP-253 requires roles and permission management with full audit trail support. Story #2 adds the audit trail for security events (role assignments, permission denials). Story #1 adds the accounting-side audit trail for financial actions (price overrides, forbidden category blocking, refunds, cancellations).

## Tests
- [x] Unit tests added/updated:
  - `pos-security-service`: `AuditEventServiceImplTest`, `RoleManagementControllerTest`, `UserServiceTest`, `JwtServiceImplTest`, `TokenRevocationManagerTest`
  - `pos-accounting`: `AuditTrailServiceTest`, `AuditTrailQueryServiceTest`, `PriceOverrideAuthorizationServiceTest`, and 8 additional service-layer coverage tests
- [x] Integration tests added/updated:
  - `pos-security-service`: `PermissionManagementContractIT` — 5 scenarios covering 403 enforcement, role assignment/revocation audit events, and audit event retrieval
  - `pos-accounting`: `AuditTrailContractBehaviorIT`, `AuditTrailContractIT` — price override authorization, forbidden category blocking, refund handling, cancellation snapshots
- [x] Provider behavioral contract tests added/updated: covered by integration tests above
- [ ] Consumer/UI tests added/updated: N/A (backend only; frontend work tracked in durion-moqui-frontend#65)
- How to run:
  ```bash
  # pos-security-service
  bash .github/hooks/test-run-hook.sh --repo . --module pos-security-service --goal verify

  # pos-accounting
  bash .github/hooks/test-run-hook.sh --repo . --module pos-accounting --goal verify
  ```

## Risk & Rollback
- Risk level: Medium
- Rollback plan: Revert branch `cap/253`. No schema migrations added (JPA auto-create only in test context). Audit event storage uses existing outbox infrastructure.

## Checklist
- [x] Branch name matches `cap/<cap-id>` — `cap/253`
- [x] PR title starts with `cap/253`
- [x] Links to parent + child issues are present
- [x] Contract guide updated (role/permission audit trail semantics documented in `domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md`)
- [x] Required CI checks passing — module-verify-hook.sh PASS | total=2 | passed=2 | failed=0 | ts=2026-03-10T23:02:28Z
