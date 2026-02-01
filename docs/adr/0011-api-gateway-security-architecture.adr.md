# ADR-0011: API Gateway Security Architecture

**Status:** ACCEPTED  
**Date:** 2026-02-01  
**Deciders:** Backend Architecture, Security Team  
**Affected Issues:** Cross-service authentication and authorization  

---

## Context

The Durion platform consists of two primary systems:
- **Moqui Frontend**: System of record for user identities, authentication, and role assignment
- **Spring Boot Backend**: Collection of independently deployable microservices handling business logic

Previously, individual backend services attempted direct validation of user credentials and tokens, creating:
- **Circular dependencies**: Services calling Moqui security services during request processing
- **Duplicate authentication logic**: Each service implementing token validation
- **Scalability concerns**: Multiple services making redundant security service calls
- **Operational complexity**: Inconsistent security policies across services
- **Trust model ambiguity**: Unclear boundaries between frontend and backend authentication

The platform needed a unified, centralized security model that:
1. Treats Moqui as the authoritative source for user identity and roles
2. Centralizes token validation at the network boundary (API Gateway)
3. Allows backend services to focus on authorization, not authentication
4. Eliminates circular dependencies and redundant validation calls

---

## Decision

✅ **Resolved** - Implement gateway-based security architecture with the following design:

### 1. Trust Model

**Decision:** ✅ **Resolved** - Establish Moqui as the system of record and the API Gateway as the authentication enforcement boundary. Services are NOT directly exposed to external networks; all requests route through the gateway.

- **Moqui's Role**: 
  - Source of truth for user identities and role assignments
  - Issues cryptographically signed JWT assertions with a shared secret (never transmitted)
  - Each assertion contains userId, roles, expiration, and anti-replay data

- **API Gateway's Role**:
  - Single entry point for all backend requests
  - Validates Moqui assertions using the shared secret (HMAC/HS256)
  - Performs replay detection using assertion JTI (unique token ID)
  - Maps Moqui roles to Spring Security authorities
  - Injects authenticated principal and authorities into downstream requests

- **Backend Services' Role**:
  - Trust the gateway as the authentication boundary
  - Use `@PreAuthorize` annotations for fine-grained authorization
  - Do NOT call external services for identity or role validation during request processing

### 2. Shared Secret Handling

**Decision:** ✅ **Resolved** - Use a single shared secret provisioned to both Moqui and the API Gateway, stored securely (encrypted at rest, injected at runtime), never transmitted or logged.

- Secret is used exclusively for **HMAC signing** of JWT assertions (HS256 algorithm)
- Never included in request/response payloads or logs
- Provisioned via environment variables or secrets manager (e.g., Vault, K8s Secrets)

### 3. Assertion Format (JWT)

**Decision:** ✅ **Resolved** - Assertions are JWT tokens with the following required claims:

```
Header:
- alg: HS256
- typ: JWT

Claims:
- iss: "moqui" (issuer identifier)
- aud: "api-gateway" (audience identifier)
- sub: <userId> (authenticated Moqui user ID)
- roles: [list of Moqui roles]
- iat: <issued-at timestamp>
- exp: <expiration timestamp> (short-lived, e.g., 5-15 minutes)
- jti: <unique UUID for replay detection>

Optional (if needed):
- tenantId
- storeId / locationId
- sessionId
```

### 4. Request Flow

**Decision:** ✅ **Resolved** - Implement the following request validation pipeline at the API Gateway:

1. Extract JWT from `Authorization: Bearer <token>` header
2. Verify JWT signature using shared secret
3. Validate claims (`iss`, `aud`, `exp`, `sub`, `roles`, `jti`)
4. Check replay cache: reject if `jti` has been seen before (until expiry)
5. Map Moqui roles → Spring authorities (e.g., `SHOP_MGR` → `ROLE_SHOP_MGR`)
6. Create authenticated Spring Security principal with mapped authorities
7. Populate Spring `SecurityContext`
8. Forward request to backend service with established context

### 5. Backend Authorization

**Decision:** ✅ **Resolved** - Backend services use Spring Security `@PreAuthorize` annotations against gateway-derived authorities:

```java
@PreAuthorize("hasRole('SHOP_MGR')")
public ResponseEntity<ShopData> getShopData(@PathVariable Long shopId) { ... }

@PreAuthorize("hasAuthority('ROLE_ACCOUNTING_CLERK')")
public ResponseEntity<JournalEntry> createEntry(@RequestBody EntryRequest req) { ... }
```

Backend code accesses authenticated user via `Authentication.getName()` (returns Moqui `userId`).

### 6. CORS Configuration

**Decision:** ✅ **Resolved** - Centralize CORS policy in the API Gateway only (`WebFluxConfigurer` bean in `SecurityGatewayConfig`). Individual backend services do NOT define CORS configurations; they rely on the gateway to handle browser-to-gateway communication and assume all backend traffic is already authenticated.

---

## Alternatives Considered

1. **Direct Service-to-Service JWT Validation** (rejected)
   - Each service validates tokens independently
   - **Drawback**: Circular dependencies (services calling security service); scalability issues; duplicate logic
   
2. **Decentralized OAuth2 / OIDC Server** (rejected)
   - Separate OAuth2 provider for token exchange
   - **Drawback**: Additional infrastructure overhead; Moqui is already the identity source; adds latency

3. **No Shared Secret (Token in Request Body)** (rejected)
   - Send plaintext secret in requests
   - **Drawback**: Violates security principles; secret exposure risk; not suitable for HTTPS

4. **Backend Services Directly Query Moqui** (rejected)
   - Services call Moqui REST endpoints for role validation
   - **Drawback**: Circular dependencies; service degradation if Moqui is unavailable; performance impact

---

## Consequences

### Positive ✅

- ✅ **Centralized Authentication**: Single point of validation at the gateway eliminates redundancy
- ✅ **Simplified Service Logic**: Backend services focus on authorization; no authentication concerns
- ✅ **Scalability**: Gateway can handle token validation efficiently; services scale independently
- ✅ **Eliminated Circular Dependencies**: No service-to-service authentication calls needed
- ✅ **Consistent Security Policy**: All services inherit the same authentication and authorization rules
- ✅ **Clear Trust Boundaries**: Services trust only the gateway; network isolation is enforced
- ✅ **Audit Trail**: Gateway logs provide centralized audit of authentication/authorization decisions
- ✅ **Replay Protection**: Centralized replay detection via JTI cache prevents token reuse attacks

### Negative ⚠️

- ⚠️ **Gateway Becomes Single Point of Failure** (mitigated by: load balancing, high availability deployment, fallback error handling)
- ⚠️ **Tight Coupling to Moqui Roles** (mitigated by: mapping layer abstraction; roles can be evolved independently; versioning strategy for authority changes)
- ⚠️ **Eventual Consistency for Role Changes** (mitigated by: short token TTL ensures role changes propagate quickly; token refresh on each request window)
- ⚠️ **Replay Cache Memory Overhead** (mitigated by: cache TTL matching token expiration; distributed cache if needed for multi-instance deployments)

### Neutral

- ℹ️ **Requires Shared Secret Provisioning**: DevOps/operations must manage secure distribution of shared secret to both Moqui and gateway
- ℹ️ **HTTPS Enforcement**: All gateway ↔ service communication must use HTTPS to protect assertion tokens in transit

---

## Implementation Notes

### Required Components
- Spring Cloud Gateway (for reactive routing)
- Spring Security with JWT support (`spring-security-oauth2-resource-server`)
- JJWT or Spring's built-in JWT utilities for token parsing
- Distributed cache (Redis) for replay detection if multi-instance gateway deployment

### Configuration
- `moqui.issuer` = configured expected issuer identifier (e.g., `"moqui"`)
- `gateway.audience` = configured expected audience (e.g., `"api-gateway"`)
- `hmac.sharedSecret` = shared secret loaded from secure configuration
- `jwt.token-ttl` = expected token lifetime (e.g., 900 seconds / 15 minutes)
- `replay.cache-ttl` = replay cache retention (matches or exceeds token TTL)

### Testing Strategy
- **Unit Tests**: JWT parsing, signature verification, claim validation
- **Integration Tests**: Complete request flow (Moqui → gateway → backend) with valid and invalid tokens
- **Replay Detection Tests**: Verify JTI cache blocks duplicate tokens within TTL window
- **Authority Mapping Tests**: Ensure Moqui roles are correctly mapped to Spring authorities

### Rollout Plan
1. **Phase 1**: Implement and deploy updated `SecurityGatewayConfig` with gateway authentication filter
2. **Phase 2**: Deploy `GatewaySecurityConfig` to backend services (replacing individual CORS beans)
3. **Phase 3**: Gradual rollout using feature flags if transitioning from legacy authentication
4. **Phase 4**: Decommission direct Moqui authentication calls in backend services

### Metrics & Monitoring
- **Authentication Success Rate**: `gateway.auth.success` counter
- **Authentication Failures**: `gateway.auth.failure` counter (broken down by reason: expired, invalid signature, replay)
- **Token Validation Latency**: `gateway.auth.validation_duration_ms` histogram
- **Replay Cache Hit Rate**: `gateway.replay.cache_hits` counter
- **Backend Service Authorization**: `backend.authz.success` / `backend.authz.failure` per endpoint

---

## References

- **Related Documentation**:
  - [API Security Architecture](../architecture/API_SECURITY_ARCHITECTURE.md)
  - [Backend AGENTS.md](../../durion-positivity-backend/AGENTS.md) — @NonNull and @EmitEvent requirements
  - [Copilot Instructions](../../.github/copilot-instructions.md) — Security and package structure guidelines
  
- **Related ADRs**:
  - [ADR-0009: Backend Domain Responsibilities](0009-backend-domain-responsibilities-guide.adr.md) — Backend service structure
  - [ADR-0010: Frontend Domain Responsibilities](0010-frontend-domain-responsibilities-guide.adr.md) — Frontend responsibilities
  
- **External Resources**:
  - [Spring Cloud Gateway Security](https://spring.io/projects/spring-cloud-gateway)
  - [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/)
  - [JWT Best Practices (RFC 8725)](https://tools.ietf.org/html/rfc8725)
  - [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Architecture | [Pending] | 2026-02-01 | Centralizes authentication at gateway boundary; eliminates circular dependencies |
| Backend Lead | [Pending] | 2026-02-01 | Services use @PreAuthorize; no service-to-service auth calls |
| Security | [Pending] | 2026-02-01 | Shared secret handling; replay detection; HTTPS enforcement |

---

## Timeline

- **Proposed**: 2026-02-01
- **Under Review**: [Pending]
- **Accepted**: [Pending]

## Addendum

### **Responses to clarifying implementation questions**

### Q1: Which secret store should be used in production for the shared HMAC secret?

**Decision:** Use **AWS Secrets Manager** in production.

* Store a single secret value (the HMAC key) under a well-known name (e.g., `/security/moqui-hmac-key`).
* Grant read access via IAM role to **Moqui** and **API Gateway** runtimes only.
* Rotation: managed rotation can be added later; initial implementation can be manual rotation with dual-key support (current/previous) if needed.

**Local testing:** Use a **local file** (substitute vault) loaded at startup (excluded from source control), e.g., `./secrets/moqui-hmac.key`.

---

### Q2: What is the canonical `iss` and `aud` values to embed? Should `aud` vary per environment or be fixed?

**Decision:**

* `iss` (canonical, fixed): `moqui`
* `aud` (canonical, environment-scoped): `api-gateway:<env>`

Where `<env>` is one of: `local`, `dev`, `stage`, `prod` (or your AWS account/region naming convention).

**Rationale (for validation correctness):**

* Environment-scoped `aud` prevents token reuse across environments (e.g., a `dev` assertion accepted by `prod`).
* Gateway validates exact `iss` and exact `aud`.

---

### Q3: Should Moqui persist `AssertionAudit` records by default or leave as opt-in?

**Decision:** **Opt-in** for Moqui persistence.

* Default behavior: Moqui does **not** store assertion audit records.
* Primary audit trail lives at the **API Gateway** (request logs + authentication decision logs) in AWS logging (e.g., CloudWatch).

If enabled, Moqui `AssertionAudit` should be **minimal** (no full token storage):

* `jti`, `sub` (userId), roles, `iat`, `exp`, `aud`, outcome (issued/failed), timestamp.

---

### Q4: Is there an expected mapping table for Moqui roles → Spring authorities, and where should it live?

**Decision:** Yes, there is an expected mapping, and it lives in the **API Gateway** configuration.

**Rules:**

* Moqui emits roles as-is (e.g., `SHOP_MGR`, `TECH`, `ACCOUNTING_CLERK`).
* Gateway maps roles → Spring authorities deterministically:

  * `X` → `ROLE_X`

**Why the gateway is the canonical mapping location:**

* Keeps backend authorization semantics centralized at the enforcement boundary.
* Avoids coupling Moqui to Spring Security conventions.
* Allows changes to authorization policy without modifying Moqui role definitions.
