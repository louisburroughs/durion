# Permission Registry System Documentation

## Historical Status

This document is historical and not authoritative for the live authorization contract.

Use these documents instead:

- [Authorization Model](../../../docs/architecture/AUTHORIZATION_MODEL.md)
- [ADR-0040](../../../docs/adr/0040-roles-jwt-permission-governance-policy.adr.md)

## Why This Document Was Retired

The earlier version of this file described a cleaner, more fully data-driven RBAC system than the code currently enforces. In particular, it drifted from the live system by implying or assuming:

- older `/api/...` endpoint shapes rather than current `/v1/...` routes
- a purely registry-driven permission model
- request authorization behavior that did not explain `perm_bits`, `perm_ver`, gateway decoding, or `X-Authorities`

## What Remains True

These concepts are still useful background:

- permissions use `domain:resource:action` naming
- the platform has persisted roles, permissions, and role assignments
- scope and effective dating exist in the persisted RBAC model

## What Is Live Today

The live runtime path is:

1. `pos-security-service` issues access tokens with `roles`, `perm_bits`, and `perm_ver`
2. `pos-api-gateway` decodes `perm_bits` and injects `X-Authorities`
3. downstream services authorize with `@PreAuthorize`

The live implementation also still uses hardcoded role expansion in `RoleAuthorityServiceImpl` for token permission emission, so this file must not be used as a contract source.
