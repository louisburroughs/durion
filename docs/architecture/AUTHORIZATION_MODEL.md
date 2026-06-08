# Authorization Model

## Scope And Ownership

This document is the canonical cross-repo description of how Durion authorization works today across:

- `durion-positivity-frontend`
- `pos-security-service`
- `pos-api-gateway`
- downstream services that import `pos-security-common`

It is implementation-authoritative for runtime behavior and should be read with:

- [API Security Architecture](./API_SECURITY_ARCHITECTURE.md) for the broader trust boundary
- [ADR-0040](../adr/0040-roles-jwt-permission-governance-policy.adr.md) for policy intent
- [ADR-0011](../adr/0011-api-gateway-security-architecture.adr.md) for the gateway ownership decision

Code is the final authority when this document and older docs disagree. The runtime classes that currently define the contract are:

- `pos-security-service/.../JwtServiceImpl`
- `pos-security-service/.../RoleAuthorityServiceImpl`
- `pos-security-service/.../RoleManagementServiceImpl`
- `pos-security-service/.../UserServiceImpl`
- `pos-security-service/.../CustomUserDetailsService`
- `pos-api-gateway/.../SecurityGatewayConfig`
- `pos-api-gateway/.../GatewayPermissionCatalog`
- `pos-security-common/.../GatewayAuthoritiesFilter`

## Glossary

- **User**: the login account in `pos-security-service`.
- **Person**: the stable human identity record linked to a user account when available.
- **Role**: a coarse assignment label such as `ADMIN` or `MANAGER`. Roles are used by the frontend for UX gating and by token issuance as the starting point for authority
  expansion.
- **Permission**: a canonical `domain:resource:action` API authorization unit such as `security:user:create`.
- **Authority**: the Spring Security string checked by downstream `@PreAuthorize` rules. In current services this is usually the plain permission string, not a role.
- **`perm_bits`**: Base64URL-encoded permission bitset stored in access tokens.
- **`perm_ver`**: integer permission-catalog version used when decoding `perm_bits`.
- **`X-Authorities`**: trusted comma-separated authority header injected by the gateway after token validation.
- **`X-Roles`**: trusted comma-separated normalized role header injected by the gateway after token validation.

## High-Level Flow

```text
login / refresh
    |
    v
pos-security-service
  - authenticates user
  - resolves effective roles
  - expands roles to authorities
  - encodes permission bitset into access token
    |
    v
client sends Bearer access token
    |
    v
pos-api-gateway
  - validates issuer, audience, signature, expiry
  - rejects mismatched or malformed permission catalog claims
  - decodes perm_bits into authorities
  - injects X-User, X-User-Id, X-Authorities, X-Roles
    |
    v
downstream services
  - trust gateway headers
  - rebuild Spring Authentication
  - authorize with @PreAuthorize("hasAuthority('domain:resource:action')")
```

## Login, Refresh, Validation, And Revocation

### Primary user login

The normal credential flow is `POST /v1/auth/login` in `AuthController`.

1. The caller sends username and password.
2. The authentication service authenticates the user.
3. Effective roles are resolved from both directly assigned roles and active role assignments.
4. `JwtServiceImpl.generateTokenPair(...)` issues an access token and a refresh token.

### Token pair issuance endpoint

`POST /v1/auth/token-pair` in `JwtController` also issues an access token and refresh token, but its request model is different:

- request body uses `subject`
- request body may include explicit `roles`
- the endpoint is currently `permitAll()`

This endpoint is not the normal credential-login contract and should be treated as a specialized or unsafe compatibility path until remediated.

### Refresh

`POST /v1/auth/refresh` accepts a refresh token and returns a new token pair.

Refresh validation currently checks:

- JWT signature and structure
- expiration
- revocation cache
- persistence in the JWT token table

The old token pair is revoked as part of refresh.

### Validate

`GET /v1/auth/validate?token=...` validates a token and returns `{ "valid": true|false }`.

This is a `GET`, not a `POST`.

### Revoke

`DELETE /v1/auth/revoke?token=...` revokes a token.

This is a `DELETE`, not a `POST`.

## Access-Token Claim Contract

`JwtServiceImpl` currently emits these access-token claims:

- `iss`
- `aud`
- `sub`
- `jti`
- `iat`
- `exp`
- `uid`
- `username`
- `roles`
- `perm_bits`
- `perm_ver`
- optional `personId`

The `roles` claim is normalized to uppercase `ROLE_*` values with duplicates removed.

The `perm_bits` claim is produced by:

1. expanding roles to authorities in `RoleAuthorityServiceImpl`
2. mapping recognized authorities to `PermissionCode`
3. encoding the resulting permission set with `PermissionBitsetCodec`

### Refresh-token claim contract

Refresh tokens intentionally omit authorization payloads. They currently include:

- `iss`
- `aud`
- `sub`
- `jti`
- `iat`
- `exp`
- `uid`
- `type=refresh`

Refresh tokens do not include `roles`, `perm_bits`, or `perm_ver`.

## How Roles Become API Authorizations

### Step 1: effective roles are resolved

`UserServiceImpl.resolveEffectiveRoleNames(...)` and `CustomUserDetailsService.loadUserByUsername(...)` both merge:

- direct user roles from `user.roles`
- active role assignments from `role_assignments`

That merged set is the role input used for login and refresh token issuance.

### Step 2: roles are expanded to authorities

`RoleAuthorityServiceImpl` is the runtime expansion layer. It:

- preserves each role as a `ROLE_*` authority
- adds hardcoded permission authorities for known business roles
- includes full admin security authorities for `ADMIN`

This hardcoded expansion is what drives `perm_bits` in issued access tokens today.

### Step 3: permissions are encoded

Only authorities that map to `PermissionCode` values become bits in `perm_bits`. The token therefore carries a compact permission payload rather than a string list of
authorities.

## Gateway Decoding And Forwarding

`SecurityGatewayConfig` is the external trust boundary.

For bearer-token requests it currently:

1. validates required identity claims
2. rejects unknown or mismatched `perm_ver`
3. rejects malformed or missing `perm_bits` when token identity is required
4. strips spoofable inbound identity headers
5. decodes `perm_bits` with `GatewayPermissionCatalog`
6. injects trusted downstream headers:
   - `X-User`
   - `X-User-Id`
   - `X-Authorities`
   - `X-Roles`

### Legacy compatibility

If a token has no `perm_ver` but does contain an `authorities` claim, the gateway still supports a temporary legacy fallback and forwards those authorities directly. New
issuance is not supposed to rely on this.

## Downstream Spring Security Behavior

`GatewayAuthoritiesFilter` in `pos-security-common` rebuilds Spring authentication from gateway headers.

Important behavior:

- it trusts `X-Authorities`, `X-Roles`, and `X-User` from the gateway
- it parses the bearer token payload to recover `uid`
- it converts `PERM_<code>` authorities into both:
  - the raw `PERM_<code>` form
  - the plain permission string expected by existing `@PreAuthorize("hasAuthority('...')")` checks

This is why downstream services can continue using plain permission strings even though the gateway transmits a prefixed authority format.

## Current Reality Vs Intended Model

The codebase currently contains two authorization models at once.

### Persisted RBAC model

`RoleManagementServiceImpl` and related controllers support:

- persisted `roles`
- persisted `role_permissions`
- persisted `role_assignments`
- scope-aware assignments (`GLOBAL`, `LOCATION`)
- effective dating and revocation history
- permission queries such as `getUserPermissions(...)` and `userHasPermission(...)`

This is the data-driven RBAC model many older docs describe.

### Runtime token-emission model

`JwtServiceImpl` does not currently derive token permissions from persisted `role_permissions`. It derives them from `RoleAuthorityServiceImpl`, which is a hardcoded
role-to-authority expansion table.

That means:

- the token payload used by the gateway is not solely driven by persisted role-permission data
- docs that describe the system as fully data-driven are ahead of current runtime behavior
- changes to persisted role permissions do not automatically imply matching token content unless the hardcoded expansion also matches

This is the main reason prior docs drifted.

## Legacy And Non-Primary Paths

Two controllers can be confused with the primary request-authorization path but are not the main runtime model:

- `PrincipalRoleController`
- `AuthorizationController`

These expose specialized or legacy RBAC-matrix style operations. They are not how ordinary API requests are authorized at runtime. Normal request authorization is:

1. token issuance in `pos-security-service`
2. token validation and authority derivation in `pos-api-gateway`
3. `@PreAuthorize` checks in downstream services using gateway-provided context

## Adding a New Permission

There are two ways to add a permission: via the script (recommended) or manually. Both require Step 1. All steps must be complete before deploying.

### Script path (recommended)

#### Step 1: Annotate the controller

Add `@PreAuthorize` to the controller method using the new permission string:

```java
@PreAuthorize("hasAuthority('domain:resource:action')")
```

Use `hasAnyAuthority(...)` when more than one permission should grant access.

#### Step 2: Run generate-permissions with --sync

```bash
scripts/generate-permissions.sh --sync
```

This does everything in one pass:

- Scans all `@PreAuthorize` annotations and finds permissions not yet registered in `PermissionCode`
- Appends new enum constants at the next available bit indices in `pos-security-service/.../PermissionCode.java`
- Appends corresponding `"PERM_..."` entries to `AUTHORITY_BY_BIT` in `pos-api-gateway/.../GatewayPermissionCatalog.java`
- Bumps `CATALOG_VERSION` by 1 in both files
- Adds the permission to the owning module's `permissions.yaml`

Preview changes without writing:

```bash
scripts/generate-permissions.sh --sync --dry-run
```

Check for unregistered permissions (CI mode, exits non-zero if any are found):

```bash
scripts/generate-permissions.sh --sync --check
```

#### Step 3: Assign roles

Add the permission to `RoleAuthorityServiceImpl` for any roles that should receive it automatically at token issuance.

---

### Manual path

Use this when you need precise control over the bit index or section grouping.

#### Step 1: Annotate the controller

Same as the script path above.

#### Step 2: Assign a bit index and bump the catalog version

This step is required for the permission to be encoded in JWTs and decoded by the gateway. Both files must change together and their `CATALOG_VERSION` constants must end up
equal.

**`pos-security-service/.../PermissionCode.java`** — append the new constant at the next unused bit index and increment `CATALOG_VERSION`:

```java
DOMAIN__RESOURCE__ACTION(285, "domain:resource:action");
public static final int CATALOG_VERSION = 9;
```

**`pos-api-gateway/.../GatewayPermissionCatalog.java`** — append the matching entry to `AUTHORITY_BY_BIT` at the same index position and increment `CATALOG_VERSION`:

```java
"PERM_domain:resource:action",  // 285
```

```java
public static final int CATALOG_VERSION = 9;
```

Bit indices are **permanent**. Never renumber or remove an existing entry — issued tokens contain encoded bit positions and would decode incorrectly against a reordered array.

#### Step 3: Regenerate permissions.yaml

```bash
scripts/generate-permissions.sh
```

This scans the updated source and adds the new permission string to the owning module's `permissions.yaml`, which registers it with the security service at startup. See
[`scripts/README.md`](../../../durion-positivity-backend/scripts/README.md#generate-permissionssh) for full options.

#### Step 4: Assign roles

Add the permission to `RoleAuthorityServiceImpl` for any roles that should receive it automatically at token issuance.

---

### Deployment order

`pos-security-service` must deploy before `pos-api-gateway`. The gateway's `PermissionVersionStartupCheck` polls the security service on startup and throws
`IllegalStateException` if `CATALOG_VERSION` values do not match.

---

## Known Drift And Open Risks

All four tracked items have been resolved. No open documentation drift remains.

### Resolved

1. ~~`PermissionCode.CATALOG_VERSION` was `7` while `GatewayPermissionCatalog.CATALOG_VERSION` was `6`.~~ Fixed: both are now `8`. Twenty-three previously-dark permissions
   were also added to `PermissionCode` and the gateway catalog.
2. ~~`POST /v1/auth/token-pair` was `permitAll()`.~~ Fixed: the endpoint now requires `security:token:issue_internal`.
3. ~~Some older docs described access tokens as carrying an `authorities` claim rather than `perm_bits` plus `perm_ver`.~~ Fixed: `AUTH_TOKEN_USAGE_GUIDE.md`,
   `permissions-encoding.md`, and `security-service-guide.md` now describe the `perm_bits`/`perm_ver` contract exclusively. The legacy `authorities` claim path in the gateway
   is documented as a read-only fallback for pre-migration tokens only.
4. ~~Some older docs described the authorization model as fully data-driven.~~ Fixed: `security-service-guide.md` now explicitly states that token permissions are emitted from
   `RoleAuthorityServiceImpl`, a hardcoded role expansion layer. The migration from hardcoded expansion to persisted `role_permissions` data has not yet begun and is tracked
   separately.

## Related Documents

- [API Security Architecture](./API_SECURITY_ARCHITECTURE.md)
- [ADR-0040: Roles, JWT Claims, and Permission Governance Policy](../adr/0040-roles-jwt-permission-governance-policy.adr.md)
- [ADR-0011: API Gateway Security Architecture](../adr/0011-api-gateway-security-architecture.adr.md)
- `durion-positivity-backend/pos-security-service/docs/AUTH_TOKEN_USAGE_GUIDE.md`
