# ADR-0040: Roles, JWT Claims, and Permission Governance Policy

**Status:** ACCEPTED
**Date:** 2026-04-12
**Deciders:** Platform Architecture, Security Domain, Frontend Architecture, Backend Leads
**Affected Issues:** Token-claim contract drift, frontend RBAC gating consistency, PERM migration alignment

---

## Context

The platform currently uses two distinct authorization concepts:

- **Roles** (for user assignment/grouping and frontend role-gated UX)
- **Permissions/Authorities** (for backend API authorization via `@PreAuthorize`)

Research across `durion-positivity-frontend` and `durion-positivity-backend` shows contract drift:

- Frontend route and nav gating is role-based (`rolesChildGuard`, `AuthService.hasAnyRole`, role-gated nav items).
- Some frontend pages still read `claims.authorities` directly for permission-aware UI.
- `pos-security-service` issues compact permission claims (`perm_bits`, `perm_ver`) and may emit `roles`.
- Gateway authorization derives downstream `X-Authorities` from token permissions (`perm_bits`/`perm_ver`), not from token `roles`.
- Existing tests/docs are inconsistent about whether access tokens should contain `roles`.

This creates avoidable regressions and uncertainty:

- frontend UX gates can break if `roles` disappears,
- token contract tests can fail in opposite directions,
- teams may incorrectly treat `roles` as API authorization input.

This ADR defines a single policy so frontend UX and backend security can evolve without ambiguity.

---

## Decision

### 1. Roles vs Permissions Semantics

**Decision:** ✅ **Resolved** - Roles and permissions are both first-class, but they serve different responsibilities.

- **Roles** are user/group assignment abstractions and coarse UI entitlement signals.
- **Permissions** are canonical API authorization units used by backend `@PreAuthorize` checks.
- Backend authorization decisions must be permission-based; roles are not a substitute for API authorization.

### 2. Access Token Claim Contract

**Decision:** ✅ **Resolved** - Access tokens must carry both permission claims for backend enforcement and role claims for frontend UX gating.

Required access-token claims:

- `iss`, `aud`, `sub`, `jti`, `iat`, `exp`
- `uid` (stable user identifier)
- `perm_bits` (Base64URL permission bitset)
- `perm_ver` (permission catalog version)

Required compatibility claim for frontend UX:

- `roles`: array of normalized role names

Roles normalization rules:

- values must be uppercase,
- `ROLE_` prefix must appear exactly once,
- duplicates must be removed.

### 3. Refresh Token Claim Contract

**Decision:** ✅ **Resolved** - Refresh tokens remain minimal and must not carry authorization payloads.

Refresh tokens must **not** include:

- `roles`
- `authorities`
- `perm_bits`
- `perm_ver`

Refresh tokens may include only identity/lifecycle claims needed for refresh exchange (`sub`, `uid`, `type=refresh`, `jti`, `iat`, `exp`, plus issuer/audience claims).

### 4. Authorities Claim Policy

**Decision:** ✅ **Resolved** - New token issuance must not emit an `authorities` claim.

- `authorities` remains legacy-read compatibility only.
- Gateway may support temporary fallback handling only for controlled migration windows.
- Greenfield contract is `perm_bits` + `perm_ver` for authority derivation.

### 5. Gateway and Service Enforcement Boundary

**Decision:** ✅ **Resolved** - Gateway derives trusted downstream authorities strictly from permission claims.

- Gateway strips inbound identity headers and injects trusted identity headers.
- Downstream services continue authorizing via `hasAuthority(...)` / `hasAnyAuthority(...)`.
- Role-based checks in service code should be treated as migration debt and moved toward permission-based policy where feasible.

### 6. Frontend Authorization and UX Policy

**Decision:** ✅ **Resolved** - Frontend uses roles for navigation/view gating and treats permission checks as backend-authoritative.

- Route/nav/component visibility gating may use role checks (`roles` claim).
- Frontend must not depend on token `authorities` for long-term behavior.
- For permission-sensitive UX, prefer either:
  - backend-driven capability responses/403 outcomes, or
  - a shared permission-decoding contract based on `perm_bits`/`perm_ver`.

---

## Alternatives Considered

1. **Permission-only tokens (remove roles claim entirely)**
   - Rejected for current state because frontend role-gated UX depends on role semantics and would regress without an immediate replacement capability model.

2. **Role-only backend authorization**
   - Rejected because it weakens policy precision and conflicts with platform-wide authority checks and permissions catalog governance.

3. **Keep mixed/undefined behavior per module**
   - Rejected because it has already caused contract drift and contradictory tests.

---

## Consequences

### Positive ✅

- Clear and stable contract: roles for UX, permission bitset for API authorization.
- Preserves current frontend role-gated behavior without weakening backend authz controls.
- Reduces regressions from contradictory token-claim assumptions.
- Aligns gateway, security service, and frontend responsibilities.

### Negative ⚠️

- Requires contract and test alignment in modules currently asserting conflicting role-claim expectations.
- Some frontend pages that read `claims.authorities` need migration toward backend-driven permission awareness.
- Requires discipline to prevent new `hasRole(...)` backend checks from bypassing permission governance intent.

### Neutral

- Does not change ADR-0011 gateway trust boundary or ADR-0014 route exposure policy.
- Does not replace ADR-0025 permission registration governance.

---

## Implementation Notes

### Backend (`pos-security-service`)

- Keep emitting `roles` in access tokens with single-prefix normalization.
- Do not emit `authorities` in newly issued access or refresh tokens.
- Keep emitting `perm_bits` + `perm_ver` in access tokens.
- Ensure refresh exchange preserves this contract.

### Gateway (`pos-api-gateway`)

- Continue deriving downstream `X-Authorities` from `perm_bits` + `perm_ver`.
- Keep inbound identity-header stripping enabled by default.
- Keep legacy `authorities` fallback explicitly migration-scoped and observable.

### Frontend (`durion-positivity-frontend`)

- Continue role-based route/nav gating via `roles`.
- Migrate direct `claims.authorities` usage to backend-driven checks.
- Do not introduce new dependencies on raw `authorities` token claims.

### Testing and Contract Alignment

- Token contract tests must consistently assert:
  - access token contains `roles`, `perm_bits`, `perm_ver`,
  - refresh token contains none of those authorization claims.
- Add/keep tests for role normalization (no `ROLE_ROLE_*` outcomes).
- Add/keep frontend tests proving behavior with:
  - roles present,
  - no roles (non-admin UX),
  - permission-only tokens still yielding backend-enforced 403 behavior.

---

## References

- [ADR-0010: Frontend Domain Responsibilities Guide](0010-frontend-domain-responsibilities-guide.adr.md)
- [ADR-0011: API Gateway Security Architecture](0011-api-gateway-security-architecture.adr.md)
- [ADR-0014: Gateway Internal Service Security](0014-gateway-internal-service-security.adr.md)
- [ADR-0018: Audit Actor Fields from Security Context](0018-audit-actor-fields-from-security-context.adr.md)
- [ADR-0022: Audit Stable Person Identifier Claim Policy](0022-audit-stable-person-identifier-claim-policy.adr.md)
- [ADR-0025: Permissions Manifest Registration Policy](0025-permissions-yaml-registration-policy.adr.md)
- [Roles/JWT/Permissions Implementation Plan](../architecture/roles-jwt-permissions-implementation-plan.md)
- `durion-positivity-frontend/src/app/core/services/auth.service.ts`
- `durion-positivity-frontend/src/app/core/guards/roles.guard.ts`
- `durion-positivity-backend/pos-security-service/src/main/java/com/positivity/securityservice/internal/service/JwtServiceImpl.java`
- `durion-positivity-backend/pos-api-gateway/src/main/java/com/positivity/gateway/config/SecurityGatewayConfig.java`

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Architecture | [Pending] | 2026-04-12 | |
| Security Lead | [Pending] | 2026-04-12 | |
| Frontend Lead | [Pending] | 2026-04-12 | |
| Backend Lead | [Pending] | 2026-04-12 | |

---

## Timeline

- **Proposed**: 2026-04-12
