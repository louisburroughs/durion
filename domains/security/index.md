---
type: Domain Guide
title: Security and Authorization Documentation
description: Owns the platform authorization contract — roles, permission codes and their permanent JWT bit indexes, role-to-permission grants, location scope, and user provisioning orchestration — and the retirement rules that keep an issued token decodable.
status: reference
---

# Security and Authorization Documentation

Security documentation lives in this domain directory. The backend module retains executable source, migrations
and runtime configuration; edit canonical documentation here. The generated
[security catalog entry](../../knowledge-catalog/domains/security.md) indexes these documents, and the
[pos-security-service catalog entry](../../knowledge-catalog/backend/pos-security-service.md) connects them to the
implementation. The gateway is the enforcement point:
[pos-api-gateway](../../knowledge-catalog/backend/pos-api-gateway.md) decodes the permission bitset into
`X-Authorities`, and [pos-security-common](../../knowledge-catalog/backend/pos-security-common.md) carries the
shared helpers downstream services check with.

## What this domain owns

Roles and role assignments; permission codes (`domain:resource:action`) and their **permanent** JWT bit indexes;
the role-to-permission grant seed; the permission catalog version that every service must agree on before a token
minted at the new version circulates; location scope on an authorization decision; and provisioning a user and
linking them to a person. It does **not** own what an individual endpoint requires — that is each module's
`@PreAuthorize` and its `permissions.yaml` manifest, registered at startup.

## Authority and status

Accepted [ADRs](../../docs/adr/) govern architecture. The business rules below define current domain contracts,
subject to later accepted ADRs. The backend owns migrations, the `PermissionCode` enum and the runtime.

| Source | Governs |
| --- | --- |
| [ADR-0040](../../docs/adr/0040-roles-jwt-permission-governance-policy.adr.md) | Roles, JWT claims and permission governance |
| [ADR-0025](../../docs/adr/0025-permissions-yaml-registration-policy.adr.md) | Per-module `permissions.yaml` registration |
| [ADR-0061](../../docs/adr/0061-location-scope-authorization-ownership.adr.md) | Who owns location scope and how it rides the token |
| [ADR-0002](../../docs/adr/0002-crm-permission-taxonomy.adr.md) | Permission-code naming taxonomy |
| [ADR-0062](../../docs/adr/0062-postgres-row-level-multitenancy.adr.md) | Tenant resolution at login; `tid` on both tokens |
| [Authorization Model](../../docs/architecture/AUTHORIZATION_MODEL.md) | Runtime shape of an authorization decision |
| [API Security Architecture](../../docs/architecture/API_SECURITY_ARCHITECTURE.md) | Gateway boundary and header contract |

## Business rules and contracts

- [Agent guide and security decisions](.business-rules/AGENT_GUIDE.md)
- [Backend contract guide](.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Generated backend API reference](.business-rules/BACKEND_API_REFERENCE.generated.md)
- [Design rationale and domain notes](.business-rules/DOMAIN_NOTES.md)
- [Story validation checklist](.business-rules/STORY_VALIDATION_CHECKLIST.md)

## Current documents

| Document | Status and use |
| --- | --- |
| [RBAC permission / role audit — August 2026](rbac-permission-role-audit-2026-08.md) | **Current.** The permission-retirement convention (§3, §4): bit indexes are permanent and never reused; a grant retires by versioned migration, the definition row and bit index never do. Records the ACCOUNT_MANAGER→AR / CONTROLLER→accounting-management split (§6) and the triage backlog (§7). Cited from `PermissionCode`, `R__seed_role_permissions.sql`, `RolePermissionBaselineTest` and `scripts/audit-rbac.py`. Its headline counts are as-of measurements; derive current ones with `python3 scripts/audit-rbac.py --check`. **Open security finding:** SYSTEM_ADMINISTRATOR is deliberately not a superuser (seed grants 40; alpha carried 398, granted out of band and outside version control, revoked by `V31`) and the actor who made those grants is still unidentified (§7 task 8). |
| [Location scope and effective dating in the JWT — spike findings](location-scope-effective-dating-spike-2026-09.md) | **Reference.** The evidence base ADR-0061 decided from, and the only place the endpoint inventory and the parser that produced it (§7) are written down. Its endpoint counts are as-of 2026-09-07; the same parser now runs in CI as `scripts/audit-rbac.py` section F. |

## Historical records

These predate the current authorization contract. They are kept for the shapes they name, not as instructions.
Each states its own historical status in its first section.

| Document | Why it is not current |
| --- | --- |
| [RBAC policy](docs/RBAC_POLICY.md) | Self-declared historical; superseded by ADR-0040 and the live seed. |
| [Permission registry system](docs/PERMISSION_REGISTRY.md) | Self-declared historical; superseded by ADR-0025's per-module manifests. |
| [Baseline permissions manifest](docs/BASELINE_PERMISSIONS.md) | Self-declared historical; not authoritative for current coverage. |
| [Break-glass access pattern](docs/BREAK_GLASS_PATTERN.md) | Design document, 2026-01-13. **Never implemented** — no `break-glass` construct exists in backend code, schema or config. Read as a proposal, not a platform capability. |
| [Policy engine design](docs/POLICY_ENGINE_DESIGN.md) | Design document, 2026-01-13. **Never implemented** — no policy engine exists in backend code. Thresholds are enforced today by permission codes and service logic. |
| [Inventory permission taxonomy examples](docs/INVENTORY_PERMISSIONS.md) | Illustrative taxonomy examples from issue #37; the platform policy is ADR-0025 and ADR-0002. |
| [Security domain open questions](security-questions.md) | Clarification backlog opened 2026-01-25; entries are resolved or superseded piecemeal and none is verified here. |

## Maintaining this documentation

Add canonical documentation here and link it from this index. Every visible Markdown document under this
directory is included automatically in the security knowledge-catalog entry because this index declares
`type: Domain Guide` — including `docs/` — so give each new document `type`, `title`, `description` and `status`
frontmatter, and mark anything historical as historical in its own first section rather than relying on this
table. Hidden directories (`.business-rules/`, `.ui/`) are not swept; business rules keep their own catalog
section.

Regenerate with `python3 scripts/generate-knowledge-catalog.py` from the `durion` root after source changes;
`--dry-run` first, `--check` before pushing. Do not hand-edit generated catalog entries.
