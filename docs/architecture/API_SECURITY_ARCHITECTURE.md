# API Security Architecture

## 1. Scope and Source of Truth

This document describes the current Durion platform API security architecture across:

- `durion-positivity-frontend` (Angular 21 SPA + SSR shell)
- `pos-api-gateway` (single external HTTP entry point)
- `pos-security-service` (identity, token, role, and permission authority)
- downstream Spring Boot services that authorize on gateway-provided context

This document is governed by the accepted ADRs below and reflects the current Angular code structure rather than the earlier Moqui-era design:

- [ADR-0010](../adr/0010-frontend-domain-responsibilities-guide.adr.md) - frontend architecture and ownership
- [ADR-0011](../adr/0011-api-gateway-security-architecture.adr.md) - gateway auth boundary and token contract
- [ADR-0014](../adr/0014-gateway-internal-service-security.adr.md) - whitelist-only gateway exposure
- [ADR-0036](../adr/0036-frontend-security-audit-model-ownership-boundary.adr.md) - frontend security-domain ownership
- [ADR-0037](../adr/0037-frontend-spa-navigation-policy.adr.md) - SPA route semantics
- [ADR-0040](../adr/0040-roles-jwt-permission-governance-policy.adr.md) - roles vs permissions policy
- [ADR-0041](../adr/0041-frontend-angular-sdk-api-transport-policy.adr.md) - Angular SDK transport boundary

## 2. Architecture Summary

```text
Browser / SSR shell
        |
        |  /login, /app/**, /forbidden, /not-found
        v
Angular frontend (`durion-positivity-frontend`)
  - AuthService
  - authInterceptor
  - authGuard + rolesChildGuard
  - feature services / generated Angular SDK clients
        |
        |  Authorization: Bearer <access token>
        v
API Gateway (`pos-api-gateway`)
  - validates JWT
  - derives trusted downstream authorities
  - strips untrusted inbound identity headers
  - forwards only whitelisted routes
        |
        v
Spring Boot services
  - trust gateway-authenticated context
  - authorize with `@PreAuthorize(hasAuthority(...))`

Identity / token / role / permission source of truth:
`pos-security-service`
```

## 3. Component Responsibilities

### 3.1 Frontend

The active frontend is Angular, not Moqui.

Current security-relevant frontend structure:

- `src/app/app.routes.ts`
  - public routes: `/`, `/login`, `/forbidden`, `/not-found`
  - protected shell: `/app/**`
  - compatibility alias: `/chat -> /app`
- `src/app/core/services/auth.service.ts`
  - login, refresh, logout, session validation
  - token persistence
  - role extraction from JWT `roles`
- `src/app/core/interceptors/auth.interceptor.ts`
  - attaches bearer token
  - handles `401 -> refresh -> retry once`
- `src/app/core/guards/auth.guard.ts`
  - blocks unauthenticated access to `/app/**`
- `src/app/core/guards/roles.guard.ts`
  - enforces route `data.roles` for coarse UI gating
- `src/app/features/shell/services/navigation-registry.service.ts`
  - filters admin/security nav items based on JWT `roles`

Current lazy-loaded protected feature routes under `/app`:

- `admin`
- `crm`
- `workexec`
- `accounting`
- `billing`
- `people`
- `location`
- `inventory`
- `product`
- `order`
- `security`
- `shopmgmt`
- `bulk-import`

Per ADR-0041, frontend-to-backend domain API calls must use `durion-positivity-sdk-angular` as the canonical transport boundary. The current codebase already provisions generated SDK configuration in `src/app/app.config.ts` and many features inject `@durion-sdk/*` services. Some feature paths still use `ApiBaseService`; those are migration debt, not target architecture.

### 3.2 `pos-security-service`

Per ADR-0011, `pos-security-service` is the authoritative system for:

- user identity records
- role definitions
- permission definitions
- role/permission assignment lifecycle
- access-token and refresh-token issuance semantics

It is the only backend service that owns token issuance and auth lifecycle APIs.

### 3.3 API Gateway

The API Gateway is the authentication enforcement boundary and the only public backend entry point.

Responsibilities:

- validate access tokens
- reject invalid, expired, or malformed requests
- derive trusted downstream authorities from token permission claims
- establish authenticated request context for downstream services
- centralize externally exposed routes

### 3.4 Downstream Services

Backend services:

- trust the gateway-authenticated context
- do not own identity, role, or token lifecycle logic
- authorize using canonical permissions via Spring Security and `@PreAuthorize`

## 4. Trust Model

### 4.1 External boundary

All external HTTP traffic enters through `pos-api-gateway`. Internal services are private by default.

Per ADR-0014:

- `spring.cloud.gateway.discovery.locator.enabled` must remain disabled
- only explicitly declared gateway routes are externally reachable
- services without explicit gateway routes are not public APIs

### 4.2 Auth ownership

Per ADR-0011 and ADR-0040:

- `pos-security-service` owns identities, roles, permissions, and token issuance
- frontend uses `roles` for route/nav/view gating
- backend authorization uses permissions, not roles
- gateway derives downstream authorities from permission claims, not from frontend UI roles

### 4.3 Header trust

The gateway is the trust boundary for caller identity.

- inbound caller-supplied identity headers must not be trusted
- gateway strips untrusted identity headers and injects trusted downstream context
- backend services must authorize from gateway-established context only

## 5. Access Token Contract

Per ADR-0011 and ADR-0040, access tokens accepted by the gateway must include:

- `iss`
- `aud`
- `sub`
- `uid`
- `jti`
- `iat`
- `exp`
- `perm_bits`
- `perm_ver`
- `roles`

Semantics:

- `roles`
  - coarse-grained frontend UX entitlement signal
  - used by `AuthService`, `rolesChildGuard`, and navigation filtering
- `perm_bits` + `perm_ver`
  - canonical backend authorization input
  - used by the gateway to derive trusted downstream authorities

Rules:

- newly issued tokens must not emit `authorities`
- any legacy `authorities` handling is migration compatibility only
- refresh tokens must not carry `roles`, `authorities`, `perm_bits`, or `perm_ver`

## 6. Request Flow

### 6.1 Login and session bootstrap

1. The Angular app calls the security-service login endpoint through the configured gateway base URL, currently `POST /api/security-service/v1/auth/login` in frontend environments.
2. `AuthService` stores the access and refresh tokens.
3. `AuthService` decodes JWT claims and normalizes `roles` into `ROLE_*` values for UI checks.
4. Protected navigation under `/app/**` becomes available through `authGuard` and `rolesChildGuard`.

### 6.2 Authenticated frontend API calls

1. A feature page or service calls a generated Angular SDK client or, in migration cases, `ApiBaseService`.
2. `authInterceptor` attaches `Authorization: Bearer <access token>`.
3. On `401`, the interceptor triggers `AuthService.refreshTokens()` and retries once.
4. On refresh failure, the frontend clears session state and redirects to `/login` with `returnUrl` and `sessionExpired=true`.

### 6.3 Gateway validation and forwarding

On each protected request, the gateway:

1. extracts the bearer token
2. validates signature and required claims
3. rejects expired or invalid tokens
4. derives trusted authorities from `perm_bits` and `perm_ver`
5. establishes authenticated downstream context
6. forwards only to explicitly routed services

### 6.4 Backend authorization

Downstream services authorize against canonical permission names, for example:

```java
@PreAuthorize("hasAuthority('order:price_override:approve')")
```

Service code must treat role-based authorization as migration debt where permission-based policy is available.

## 7. Frontend Structure and Security Boundaries

### 7.1 Route ownership

Per ADR-0010 and ADR-0037:

- all protected business routes live under `/app`
- feature route files own their domain navigation contracts
- in-app navigation must use Angular router semantics, not bare `href`

### 7.2 RBAC in the Angular app

The current frontend uses route metadata and token `roles` for coarse UI gating:

- `/app/admin` requires `ROLE_ADMIN`
- `/app/security` requires `ROLE_ADMIN`
- shell navigation hides admin-only destinations when the role is absent

This is intentionally a UX concern only. Backend APIs remain authoritative and may still return `403` even if a page is visible.

### 7.3 Security-domain ownership

Per ADR-0036, security-specific models and UI concerns belong inside `src/app/features/security/**`, not in sibling feature domains. Security audit models, pages, and services must stay within the security feature boundary unless promoted to an explicitly shared core contract.

## 8. Transport Topology

The frontend currently runs in two relevant modes:

- browser/dev mode using `environment.apiBaseUrl`
- SSR/Express mode using the proxy routes in `src/server.ts`

Current SSR proxy behavior:

- `/api -> pos-api-gateway`
- `/mcp-server -> pos-api-gateway`

This keeps the gateway as the single backend ingress path even when the Angular app is served through the SSR shell.

## 9. Current State and Migration Notes

Target state:

- Angular SDK-first backend transport
- roles for frontend UX gating
- permission claims for gateway/backend authorization
- no Moqui-era signing or role-mapping assumptions

Current implementation notes:

- `src/app/app.config.ts` already configures generated Angular SDK packages with the frontend API base URL
- frontend JWT claim models already declare `roles`, `perm_bits`, and `perm_ver`, while treating `authorities` as legacy compatibility only
- some feature services still inject `ApiBaseService`; those paths should migrate opportunistically per ADR-0041

## 10. Operational and Audit Notes

- Do not log raw access or refresh tokens.
- Correlate auth failures using request/correlation identifiers rather than token dumps.
- Avoid logging PII beyond what is required for security auditability.
- Document any gateway-visible auth contract changes in the backend security-service and gateway consumer docs referenced by ADR-0011.

## 11. References

- [Workspace README](../../README.md)
- [Frontend README](../../../durion-positivity-frontend/README.md)
- [Frontend app routes](../../../durion-positivity-frontend/src/app/app.routes.ts)
- [Frontend app config](../../../durion-positivity-frontend/src/app/app.config.ts)
- [AuthService](../../../durion-positivity-frontend/src/app/core/services/auth.service.ts)
- [authInterceptor](../../../durion-positivity-frontend/src/app/core/interceptors/auth.interceptor.ts)
- [roles guard](../../../durion-positivity-frontend/src/app/core/guards/roles.guard.ts)
- [Navigation registry](../../../durion-positivity-frontend/src/app/features/shell/services/navigation-registry.service.ts)
- [SSR server proxy](../../../durion-positivity-frontend/src/server.ts)
