# ADR-0011: API Gateway Security Architecture

**Status:** ACCEPTED **Date:** 2026-02-01 **Last Updated:** 2026-03-17 **Deciders:** Backend Architecture, Security Team **Affected Issues:** Cross-service authentication and
authorization

---

## Amendment

This ADR still records the gateway ownership decision, but it is no longer the live claim-contract reference.

- Claim semantics in this ADR that describe access tokens carrying `authorities` are superseded by [ADR-0040](0040-roles-jwt-permission-governance-policy.adr.md) and
  [Authorization Model](../architecture/AUTHORIZATION_MODEL.md).
- Current runtime behavior uses `perm_bits` plus `perm_ver` as the primary authority payload, with a temporary legacy `authorities` fallback in gateway code for older tokens.
- Read this ADR for trust-boundary and ownership intent, not for the exact access-token field contract.

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

The platform now also has shared reference artifacts that make this security boundary more explicit for consumers and tooling:

- `pos-security-service/docs/AUTH_TOKEN_USAGE_GUIDE.md` for gateway auth, token lifecycle, and required header usage
- `pos-security-service/docs/permissions-aggregate.yaml` for aggregated canonical permissions across service manifests
- `pos-api-gateway/docs/openapi-aggregate.yaml` as the aggregate gateway-facing API reference artifact
- `com.positivity.shared.error.ApiError` and `docs/ERROR_ENVELOPE.md` as the common non-2xx error contract returned by backend APIs

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

```text
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

Gateway consumer requests should consistently use:

- `Authorization: Bearer <token>` for protected endpoints
- `X-API-Version` for explicit API version routing
- `X-Correlation-Id` for request tracing
- `Idempotency-Key` for retry-safe mutation endpoints where supported

### 5. Backend Authorization Standard

**Decision:** ✅ **Resolved** - Backend services authorize on canonical authorities.

```java
@PreAuthorize("hasAuthority('order:price_override:approve')")
public ResponseEntity<?> approve(...) { ... }
```

Backend code reads authenticated user identity from `Authentication.getName()` (or equivalent principal abstraction) and must not perform role ownership logic.

The aggregated permission catalog in `pos-security-service/docs/permissions-aggregate.yaml` is the consumer-facing reference for canonical permission names exposed by platform
modules.

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
- Shared security/API contract documentation for consumers and SDK tooling

### Configuration

- `security.jwt.issuer=pos-security-service`
- `security.jwt.audience=api-gateway`
- `security.jwt.signing-key` (or equivalent key/JWKS configuration)
- `security.jwt.token-ttl`
- `security.jwt.replay-cache-ttl` (if replay checks enabled)

### Consumer Contract Artifacts

- `pos-security-service/docs/AUTH_TOKEN_USAGE_GUIDE.md` is the canonical consumer guide for login, refresh, validate, revoke, and gateway header usage patterns
- `pos-security-service/docs/permissions-aggregate.yaml` is the canonical aggregated permission reference for SDK/docs generation and consumer-facing permission lookup
- `pos-api-gateway/docs/openapi-aggregate.yaml` is the aggregate API discovery/reference artifact for gateway-routed APIs
- `com.positivity.shared.error.ApiError` is the canonical backend error envelope for auth and authorization failures, including correlation IDs and validation/guidance fields
  where applicable

### Testing Strategy

- Unit: token parsing/signature/claim validation
- Integration: `pos-security-service -> api-gateway -> protected service`
- Negative-path: expired token, invalid issuer/audience, insufficient authority
- Regression: role-assignment changes reflected in newly issued tokens
- Contract: auth endpoints, gateway-protected APIs, and auth failures remain aligned with the shared `ApiError` envelope and documented consumer header conventions

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
  - [Backend AGENTS.md](https://github.com/louisburroughs/durion-positivity-backend/blob/main/AGENTS.md)
  - [Platform Auth and Token Usage Guide](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-security-service/docs/AUTH_TOKEN_USAGE_GUIDE.md)
  - [Aggregated Permissions Catalog](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-security-service/docs/permissions-aggregate.yaml)
  - [Gateway Aggregate API Reference](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-api-gateway/docs/openapi-aggregate.yaml)
  - [Shared Error Envelope](https://github.com/louisburroughs/durion-positivity-backend/blob/main/docs/ERROR_ENVELOPE.md)
  - [ApiError.java](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-shared-dtos/src/main/java/com/positivity/shared/error/ApiError.java)

- **External Resources**:
  - [Spring Cloud Gateway Security](https://spring.io/projects/spring-cloud-gateway)
  - [Spring Security Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/)
  - [JWT Best Practices (RFC 8725)](https://www.rfc-editor.org/rfc/rfc8725)

---

## Timeline

- **Proposed**: 2026-02-01
- **Accepted**: 2026-02-01
- **Updated**: 2026-03-17
