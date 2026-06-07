# API Security Architecture

## Scope And Source Of Truth

This document describes the high-level API security boundary across:

- `durion-positivity-frontend`
- `pos-api-gateway`
- `pos-security-service`
- downstream Spring Boot services

For the detailed runtime explanation of roles, permissions, `perm_bits`, token claims, and gateway header propagation, use [Authorization Model](./AUTHORIZATION_MODEL.md). That document is the normative contract narrative for authorization behavior.

The governing decisions remain:

- [ADR-0011](../adr/0011-api-gateway-security-architecture.adr.md)
- [ADR-0014](../adr/0014-gateway-internal-service-security.adr.md)
- [ADR-0040](../adr/0040-roles-jwt-permission-governance-policy.adr.md)
- [ADR-0041](../adr/0041-frontend-angular-sdk-api-transport-policy.adr.md)

## Architecture Summary

```text
Browser / Angular frontend
        |
        | Authorization: Bearer <access token>
        v
API Gateway (`pos-api-gateway`)
  - validates JWT
  - strips spoofable identity headers
  - derives trusted X-Authorities and X-Roles
  - forwards only whitelisted routes
        |
        v
Spring Boot services
  - trust gateway-authenticated context
  - authorize with Spring Security
  - enforce permissions with @PreAuthorize

Identity, roles, token issuance, and permission encoding:
`pos-security-service`
```

## Responsibilities

### Frontend

The frontend is responsible for:

- initiating login and refresh flows
- attaching bearer tokens
- using `roles` for coarse route and navigation gating
- treating backend permission enforcement as authoritative

### `pos-security-service`

The security service is responsible for:

- user authentication
- token issuance and refresh
- user, role, permission, and assignment administration
- encoding access-token authorization state

### API Gateway

The gateway is responsible for:

- being the single public backend entry point
- validating tokens before forwarding requests
- deriving trusted downstream authorities from token claims
- injecting trusted user and authorization headers

### Downstream Services

Downstream services are responsible for:

- trusting only gateway-established auth context
- enforcing API authorization with permissions
- avoiding caller-supplied identity headers as a trust source

## Trust Boundary Rules

- External HTTP traffic enters through `pos-api-gateway`.
- Internal services are private by default.
- Caller-supplied identity headers are not trusted.
- Backend authorization is permission-based even when frontend UX remains role-gated.

## Token And Authorization Contract

The live token contract is documented in [Authorization Model](./AUTHORIZATION_MODEL.md). At a high level:

- access tokens carry `roles`, `perm_bits`, and `perm_ver`
- refresh tokens do not carry authorization payloads
- gateway decoding produces trusted `X-Authorities` and `X-Roles`
- downstream services authorize using Spring Security authorities

## Related Documents

- [Authorization Model](./AUTHORIZATION_MODEL.md)
- [Platform Auth and Token Usage Guide](../../../durion-positivity-backend/pos-security-service/docs/AUTH_TOKEN_USAGE_GUIDE.md)
- [Roles, JWT Claims, and Permission Governance Policy](../adr/0040-roles-jwt-permission-governance-policy.adr.md)
