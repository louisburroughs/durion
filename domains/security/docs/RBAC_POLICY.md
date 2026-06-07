# Role-Based Access Control (RBAC) Policy

## Historical Status

This document is historical and not authoritative for the live authorization contract.

Use instead:

- [Authorization Model](../../../docs/architecture/AUTHORIZATION_MODEL.md)
- [ADR-0040](../../../docs/adr/0040-roles-jwt-permission-governance-policy.adr.md)

## Why It Was Demoted

The previous version of this file described an intended RBAC policy that is only partially reflected in current code. In particular, it leaned on a fully data-driven model while the live token issuance path still depends on hardcoded role expansion in `RoleAuthorityServiceImpl`.

## Still-Useful Concepts

These principles still align with platform intent:

- permissions should remain explicit
- roles should bundle permissions
- least privilege and auditability remain goals
- scope-aware and effective-dated assignments are part of the persisted model

## What Changed

The live runtime contract now needs a separate authoritative explanation because API authorization depends on more than persisted RBAC tables:

- access tokens carry `roles`, `perm_bits`, and `perm_ver`
- the gateway decodes `perm_bits` into `X-Authorities`
- downstream services authorize on Spring Security authorities
- legacy compatibility for `authorities` claims still exists in gateway code

Use this file only as background policy history.
