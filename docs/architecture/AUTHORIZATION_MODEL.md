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
- [ADR-0061](../adr/0061-location-scope-authorization-ownership.adr.md) for location scope, effective dating, and (amendment of 2026-09-09) the single store of a user's roles

Code is the final authority when this document and older docs disagree. The runtime classes that currently define the contract are:

- `pos-security-service/.../JwtServiceImpl`
- `pos-security-service/.../RoleAuthorityServiceImpl`
- `pos-security-service/.../RoleManagementServiceImpl`
- `pos-security-service/.../EffectiveGrantResolverImpl`
- `pos-security-service/.../UserRoleGrantServiceImpl`
- `pos-security-service/.../UserServiceImpl`
- `pos-security-service/.../CustomUserDetailsService`
- `pos-api-gateway/.../SecurityGatewayConfig`
- `pos-api-gateway/.../GatewayPermissionCatalog`
- `pos-security-common/.../GatewayAuthoritiesFilter`

## Glossary

- **User**: the login account in `pos-security-service`.
- **Person**: the stable human identity record linked to a user account when available.
- **Role**: a bundle that delivers permission grants (ADR-0040 §1), such as `ADMIN` or `MANAGER`. A user holds a role only through an effective-dated row in
  `role_assignments` (ADR-0061, amendment 2026-09-09). Roles are used by the frontend for coarse UX gating and by token issuance as the starting point for authority
  expansion; they are not an authorization input anywhere else.
- **Permission**: a canonical `domain:resource:action` API authorization unit such as `security:user:create`.
- **Authority**: the Spring Security string checked by downstream `@PreAuthorize` rules. In current services this is usually the plain permission string, not a role.
- **`perm_bits`**: Base64URL-encoded permission bitset stored in access tokens.
- **`perm_ver`**: integer permission-catalog version used when decoding `perm_bits`.
- **`X-Perm-Bits`**: compact Base64URL-encoded permission bitset forwarded by the gateway to downstream services. Replaces the verbose `X-Authorities` CSV for
  gateway-to-service traffic. Decoded by `GatewayAuthoritiesFilter` using `DownstreamPermissionCatalog`.
- **`X-Perm-Ver`**: integer permission-catalog version accompanying `X-Perm-Bits`. Must equal `DownstreamPermissionCatalog.CATALOG_VERSION` for the filter to use the compact
  decode path.
- **`X-Authorities`**: legacy/fallback comma-separated authority header. Still used by service-to-service REST clients (which inject 1–3 plain permission strings) and
  integration tests. Recognised by `GatewayAuthoritiesFilter` as a fallback when `X-Perm-Bits` is absent. Not the primary gateway-to-service forwarding mechanism.
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
  - forwards raw perm_bits as X-Perm-Bits + X-Perm-Ver
  - injects X-User, X-User-Id, X-Roles
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
3. Effective roles are resolved from the user's currently effective role assignments (see [Step 1](#step-1-effective-roles-are-resolved)).
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

### Role-assignment change

Decided by the ADR-0061 amendment of 2026-09-09 (§4 applied to role assignments) and implemented in
[durion-positivity-backend#1916](https://github.com/louisburroughs/durion-positivity-backend/pull/1916):

- access-token `exp` is clamped to `min(now + 3600s, earliest effective_end_date among the role assignments contributing to the token)`, extending the clamp
  `JwtServiceImpl` already applies for location reach
- revoking a role assignment revokes the holder's live tokens through `TokenRevocationManager` and the `jwt_token` table, per `jti`

Revocation reaches the token layer through a `RoleAssignmentRevokedEvent` handled after the revoking transaction commits
(`RoleAssignmentTokenRevocationListener`), so a rolled-back revocation never revokes a token and a committed one always does. The internal token-pair endpoint,
which takes client-supplied roles, gets no assignment clamp. Redis unavailability keeps `TokenRevocationManager`'s existing behaviour: the cache fails open, the
`jwt_token` row is still deleted, so the bearer path refuses the token.

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

`role_assignments` is the only store of a user's roles (ADR-0061 §1, amendment 2026-09-09). A user's effective roles at an instant are the roles of the assignments
whose half-open window `[effective_start_date, effective_end_date)` contains that instant. One resolver in `pos-security-service` computes that set and the permissions
it carries, and every decision point goes through it:

- `CustomUserDetailsService.loadUserByUsername` — granted authorities on every authenticated request to the security service
- login and refresh token issuance (`UserServiceImpl`)
- `AuthorizationServiceImpl.authorizePerson` — the off-session approver check behind `GET /v1/users/authorization/person-decision`
- `RoleManagementServiceImpl.userHasPermission` and `getUserPermissions`

Only the resolver may call the effective-assignment query (an ArchUnit rule), and an agreement test asserts the permission set from each decision point is identical
for the same user and instant.

Implemented in [durion-positivity-backend#1916](https://github.com/louisburroughs/durion-positivity-backend/pull/1916): `EffectiveGrantResolver` is the
one resolver, `EffectiveGrantAgreementIT` is the agreement test, and the module's `ArchitectureTest` carries the rule. The undated `user_roles` join table
was migrated into open-ended assignments and dropped by `V40`; every provisioning path (`createUser`, bulk ingest, self-registration, the operational seed,
`PUT /v1/users/{username}/roles` as a reconcile, `assignUserRole`) writes assignments through `UserRoleGrantService`, and `assignUserRole` is idempotent on an
already-effective pair.

### Step 2: roles are expanded to authorities

`RoleAuthorityServiceImpl` is the runtime expansion layer. It:

- preserves each role as a `ROLE_*` authority (still consumed by the frontend's coarse `ROLE_ADMIN` fallback; not an API authorization input)
- adds the permission authorities granted to each role in the persisted `role_permissions` table (`RoleRepository.findPermissionNamesByRoleNames`)

Since [#1372](https://github.com/louisburroughs/durion-positivity-backend/issues/1372) the persisted `role_permissions` table is the only source of role grants; an
unknown role, or a role with no grants, contributes nothing and the user fails closed. There is no hardcoded expansion table.

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
   - `X-Perm-Bits` (raw Base64URL-encoded permission bitset from the token `perm_bits` claim)
   - `X-Perm-Ver` (permission catalog version from the token `perm_ver` claim)
   - `X-Roles`

### Legacy compatibility

If a token has no `perm_ver` but does contain an `authorities` claim, the gateway still supports a temporary legacy fallback and forwards those authorities directly. New
issuance is not supposed to rely on this.

## Downstream Spring Security Behavior

`GatewayAuthoritiesFilter` in `pos-security-common` rebuilds Spring authentication from gateway headers.

Important behavior:

- it uses a two-path authority resolution with explicit precedence:
  1. **Preferred path**: if both `X-Perm-Bits` and `X-Perm-Ver` are present and `X-Perm-Ver` equals `DownstreamPermissionCatalog.CATALOG_VERSION`, the filter decodes the
     bitset using `DownstreamPermissionCatalog` to recover authorities.
  2. **Fallback path**: if `X-Perm-Bits` is absent, the filter falls back to parsing the `X-Authorities` CSV. This covers service-to-service REST clients and integration
     tests that inject plain permission strings directly.
- it trusts `X-Roles` and `X-User` from the gateway in both paths
- it parses the bearer token payload to recover `uid`
- each `PERM_<code>` authority decoded from the bitset is expanded into both:
  - the raw `PERM_<code>` form
  - the plain permission string expected by existing `@PreAuthorize("hasAuthority('...')")` checks

This is why downstream services can continue using plain permission strings even though the gateway forwards a compact bitset.

## Current Reality Vs Intended Model

The codebase currently contains two authorization models at once.

### Persisted RBAC model

`RoleManagementServiceImpl` and related controllers support:

- persisted `roles`
- persisted `role_permissions`
- persisted `role_assignments` — the only store of a user's roles (ADR-0061 amendment 2026-09-09)
- `roles.location_scope` (`ALL` | `LOCATION`) — location reach is a property of the role, evaluated against pos-people's staffing assignment (ADR-0061 §1); the former
  per-assignment `scope_type` is retired
- effective dating and revocation history
- permission queries such as `getUserPermissions(...)` and `userHasPermission(...)`

This is the data-driven RBAC model many older docs describe.

### Runtime token-emission model

`JwtServiceImpl` derives token permissions from `RoleAuthorityServiceImpl`, which reads the persisted `role_permissions` table
([#1372](https://github.com/louisburroughs/durion-positivity-backend/issues/1372)). A change to a role's grants is reflected in the next token issued; tokens already
issued carry the permissions they were issued with until they expire or are revoked.

## Legacy And Non-Primary Paths

`AuthorizationController.getPersonDecision` (`GET /v1/users/authorization/person-decision`) answers "does the user linked to this person hold this permission now"
for off-session approvers — for example pos-invoice's manager-override check, where the approver is not the authenticated caller. It resolves through the same
effective-assignment resolver as token issuance and is not a request-authorization path.

The string-keyed principal matrix — `PrincipalRoleController` (`assignPrincipalRole`), `AuthorizationController.getDecision` (`getAuthorizationDecision`), the
`PrincipalRole` entity and the `principal_roles` table — was **removed** in
[durion-positivity-backend#1916](https://github.com/louisburroughs/durion-positivity-backend/pull/1916) (ADR-0061 amendment 2026-09-09, extending §3; `V41`
dropped the table). It mapped an unvalidated principal string to roles that no user or assignment operation wrote, and nothing outside
pos-security-service's own contract tests called it. Service actors assert permissions directly through `X-Authorities`.

Normal request authorization is:

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
- Updates `pos-security-common/.../DownstreamPermissionCatalog.java` to match, so the filter can decode the new bits in downstream services
- Bumps `CATALOG_VERSION` by 1 in all three files
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

Grant the permission to the roles that should carry it: in the `role_permissions` seed (`R__seed_role_permissions.sql`) for baseline roles, or through the
role-permission admin API (`PUT /v1/roles/{roleId}/permissions/{permission}`) at runtime. Token issuance reads the persisted grants; there is no code table to edit.

---

### Manual path

Use this when you need precise control over the bit index or section grouping.

#### Step 1: Annotate the controller (Same as Before)

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

Grant the permission to the roles that should carry it: in the `role_permissions` seed (`R__seed_role_permissions.sql`) for baseline roles, or through the
role-permission admin API (`PUT /v1/roles/{roleId}/permissions/{permission}`) at runtime. Token issuance reads the persisted grants; there is no code table to edit.

---

### Deployment order

`pos-security-service` must deploy before `pos-api-gateway`. The gateway's `PermissionVersionStartupCheck` polls the security service on startup and throws
`IllegalStateException` if `CATALOG_VERSION` values do not match.

---

## Known Drift And Open Risks

All five tracked items have been resolved. No open documentation drift remains.

### Resolved

1. ~~`PermissionCode.CATALOG_VERSION` was `7` while `GatewayPermissionCatalog.CATALOG_VERSION` was `6`.~~ Fixed: both are now `8`. Twenty-three previously-dark permissions
   were also added to `PermissionCode` and the gateway catalog.
2. ~~`POST /v1/auth/token-pair` was `permitAll()`.~~ Fixed: the endpoint now requires `security:token:issue_internal`.
3. ~~Some older docs described access tokens as carrying an `authorities` claim rather than `perm_bits` plus `perm_ver`.~~ Fixed: `AUTH_TOKEN_USAGE_GUIDE.md`,
   `permissions-encoding.md`, and `security-service-guide.md` now describe the `perm_bits`/`perm_ver` contract exclusively. The legacy `authorities` claim path in the gateway
   is documented as a read-only fallback for pre-migration tokens only.
4. ~~Some older docs described the authorization model as fully data-driven.~~ Resolved the other way round: since
   [#1372](https://github.com/louisburroughs/durion-positivity-backend/issues/1372) `RoleAuthorityServiceImpl` reads the persisted `role_permissions` table and the
   hardcoded expansion is gone, so the model is data-driven and this document now says so.
5. ~~Three stores decided a user's roles, and only one was effective-dated~~
   ([#1914](https://github.com/louisburroughs/durion-positivity-backend/issues/1914)). `role_assignments` (dated), `user_roles` (undated, written by every
   provisioning path) and `principal_roles` (a matrix nothing else read) were read by eight decision points in four different combinations. Fixed by the ADR-0061
   amendment of 2026-09-09 and [durion-positivity-backend#1916](https://github.com/louisburroughs/durion-positivity-backend/pull/1916):
   `role_assignments` only, one resolver behind every decision point, `principal_roles` removed, §4's token clamp and revocation applied to role assignments.

## Related Documents

- [API Security Architecture](./API_SECURITY_ARCHITECTURE.md)
- [ADR-0040: Roles, JWT Claims, and Permission Governance Policy](../adr/0040-roles-jwt-permission-governance-policy.adr.md)
- [ADR-0011: API Gateway Security Architecture](../adr/0011-api-gateway-security-architecture.adr.md)
- [ADR-0061: Location Scope Authorization — Ownership, Token Shape, and Effective Dating](../adr/0061-location-scope-authorization-ownership.adr.md), including the
  2026-09-09 amendment on the single store of a user's roles
- `durion-positivity-backend/pos-security-service/docs/AUTH_TOKEN_USAGE_GUIDE.md`
