# ADR-0011: API Gateway Security Architecture

**Status:** ACCEPTED  
**Date:** 2026-02-01  
**Last Updated:** 2026-02-27  
**Deciders:** Backend Architecture, Security Team  
**Affected Issues:** Cross-service authentication and authorization

---

## Context

The backend platform requires a single, consistent ownership model for authentication and authorization.

Previously, identity and role ownership were split across systems, causing:
- duplicate authentication and role resolution logic
- unclear ownership of role and permission lifecycle
- inconsistent authorization behavior across services
- operational complexity during incident response and audits

The platform needs a centralized model where:
1. `pos-security-service` is the source of truth for identities, roles, permissions, and assignments
2. the API Gateway is the authentication enforcement boundary
3. backend services focus on authorization decisions (`@PreAuthorize`) using gateway-provided context
4. internal services are not directly exposed to external callers

---

## Decision

✅ **Resolved** - Adopt gateway-enforced security with `pos-security-service` ownership for identity and role management.

### 1. Ownership and Trust Model

**Decision:** ✅ **Resolved** - `pos-security-service` is the authoritative system for:
- user identity records
- role definitions
- permission definitions
- role-to-user assignments
- token issuance and revocation semantics

- **pos-security-service role:**
  - issues signed JWT access tokens
  - manages role and permission APIs
  - provides canonical authority data used by gateway and services

- **API Gateway role:**
  - single external entry point for backend traffic
  - validates JWTs and required claims
  - establishes authenticated principal and authorities for downstream services
  - forwards only authenticated requests to service routes

- **Backend service role:**
  - trusts gateway-authenticated context
  - enforces endpoint authorization with `@PreAuthorize`
  - does not own role lifecycle logic

### 2. JWT Contract and Claims

**Decision:** ✅ **Resolved** - Tokens accepted by gateway must meet this contract.

```
Header:
- alg: HS256 (or configured signing algorithm)
- typ: JWT

Claims:
- iss: "pos-security-service"
- aud: "api-gateway"
- sub: <stable user/person identifier>
- exp: <expiration timestamp>
- iat: <issued-at timestamp>
- jti: <unique token identifier>
- authorities: [list of canonical authority strings]

Optional (as needed):
- roles
- locationId / storeId
- sessionId
- organizationId
```

### 3. Role and Permission Management Boundary

**Decision:** ✅ **Resolved** - Role and permission management is exclusively owned by `pos-security-service`.

Rules:
- Role/permission CRUD endpoints live in `pos-security-service`
- Assignment workflows (user-role, scope bindings) live in `pos-security-service`
- Other modules may define or register domain permissions, but the authoritative registry and assignment lifecycle remain in `pos-security-service`
- No other service may implement a parallel role-management subsystem

### 4. Gateway Request Validation Flow

**Decision:** ✅ **Resolved** - Gateway authentication pipeline:

1. Extract `Authorization: Bearer <token>`
2. Verify signature with configured key material
3. Validate required claims (`iss`, `aud`, `exp`, `sub`, `jti`)
4. Perform replay/revocation checks where configured
5. Resolve canonical authorities from token (and mapped policy rules if configured)
6. Create authenticated principal in gateway security context
7. Forward authenticated request with security headers/context to downstream service

### 5. Backend Authorization Standard

**Decision:** ✅ **Resolved** - Backend services authorize on canonical authorities.

```java
@PreAuthorize("hasAuthority('order:price_override:approve')")
public ResponseEntity<?> approve(...) { ... }
```

Backend code reads authenticated user identity from `Authentication.getName()` (or equivalent principal abstraction) and must not perform role ownership logic.

### 6. Network and Exposure Constraints

**Decision:** ✅ **Resolved** - Gateway remains whitelist-routed and internal services remain private by default.

- Public exposure only through explicit API gateway routes
- Internal-only services are not externally routed
- CORS policy is centralized at gateway

(Aligned with ADR-0014.)

---

## Alternatives Considered

1. **Split ownership (identity in one system, roles in another)**
   - Rejected: creates policy drift and ambiguous ownership.

2. **Per-service authentication/authorization stacks**
   - Rejected: duplicates logic and increases attack surface.

3. **Expose role management in multiple domain services**
   - Rejected: violates single source of truth and complicates audits.

---

## Consequences

### Positive ✅

- ✅ clear ownership for identity and role lifecycle in `pos-security-service`
- ✅ consistent gateway authentication behavior across all services
- ✅ simpler backend services focused on business authorization checks
- ✅ improved auditability and incident triage for auth failures
- ✅ reduced circular dependencies and duplicate security logic

### Negative ⚠️

- ⚠️ `pos-security-service` becomes a high-criticality dependency
- ⚠️ migration effort required for any legacy externalized role-management flows
- ⚠️ strict authority naming and registration governance is required

### Neutral

- ℹ️ requires disciplined contract/version management for security APIs and token claims

---

## Implementation Notes

### Required Components
- Spring Cloud Gateway
- Spring Security (gateway and services)
- JWT validation and key management
- Optional replay/revocation cache for distributed deployments

### Configuration
- `security.jwt.issuer=pos-security-service`
- `security.jwt.audience=api-gateway`
- `security.jwt.signing-key` (or equivalent key/JWKS configuration)
- `security.jwt.token-ttl`
- `security.jwt.replay-cache-ttl` (if replay checks enabled)

### Testing Strategy
- Unit: token parsing/signature/claim validation
- Integration: `pos-security-service -> api-gateway -> protected service`
- Negative-path: expired token, invalid issuer/audience, insufficient authority
- Regression: role-assignment changes reflected in newly issued tokens

### Rollout Plan
1. Migrate all role-management ownership to `pos-security-service`
2. Align gateway claim validation with the canonical JWT contract
3. Remove legacy role-management paths in other systems/modules
4. Enforce policy in CI and architecture tests where applicable

---

## References

- **Related ADRs**:
  - [ADR-0009: Backend Domain Responsibilities](0009-backend-domain-responsibilities-guide.adr.md)
  - [ADR-0014: Gateway Internal Service Security](0014-gateway-internal-service-security.adr.md)
  - [ADR-0025: Permissions Manifest Registration Policy](0025-permissions-yaml-registration-policy.adr.md)

- **Related Repo Guidance**:
  - [Backend AGENTS.md](../../durion-positivity-backend/AGENTS.md)

- **External Resources**:
  - [Spring Cloud Gateway Security](https://spring.io/projects/spring-cloud-gateway)
  - [Spring Security Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/)
  - [JWT Best Practices (RFC 8725)](https://www.rfc-editor.org/rfc/rfc8725)

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Architecture | [Pending] | 2026-02-27 | Security ownership consolidated in pos-security-service |
| Backend Lead | [Pending] | 2026-02-27 | Gateway boundary and service authorization model confirmed |
| Security | [Pending] | 2026-02-27 | JWT contract and role lifecycle ownership confirmed |

---

## Timeline

- **Proposed**: 2026-02-01
- **Accepted**: 2026-02-01
- **Updated**: 2026-02-27
