# ADR-0025: Permissions Manifest (`permissions.yaml`) Registration Policy

**Status:** ACCEPTED  
**Date:** 2026-02-26  
**Deciders:** Architecture, Security Domain, Backend Leads  
**Affected Issues:** Platform-wide permission registration consistency

---

## Context

- **Current State:** Permission definitions are split across inline Java lists, constants, and legacy registries.
- **The Problem:** Inconsistent declaration style causes drift between registered permissions and `@PreAuthorize` checks, and makes extraction/automation unreliable.
- **Drivers:** single source of truth, deterministic tooling, safer permission audits, easier onboarding of new modules.
- **Scope:** all `durion-positivity-backend` modules that define or enforce domain permissions.

---

## Decision

### 1. Canonical Source

**Decision:** ✅ **Resolved** - Each module that registers permissions MUST define them in `src/main/resources/permissions.yaml`.  
Java constants remain allowed for controller/service authorization checks, but manifest content is authoritative for registration.

### 2. Registration Mechanism

**Decision:** ✅ **Resolved** - Permission registration at startup MUST read from `permissions.yaml` and register via `PermissionRegistrationSupport` (or a shared manifest-backed subclass/helper in `pos-security-common`).

### 3. Legacy Pattern Handling

**Decision:** ✅ **Resolved** - Inline `PermissionDefinition.of(...)` lists and ad-hoc initializer/registry registration code are deprecated. Existing implementations are migrated incrementally but no new module may adopt legacy style.

### 4. Validation and CI

**Decision:** ✅ **Resolved** - CI must validate:
- manifest schema,
- uniqueness of permission names per module,
- and parity between manifest entries and authorities enforced in code.

---

## Alternatives Considered

1. Keep Java-only lists/constants  
Rejected: easy to drift, hard to extract reliably.

2. Keep mixed mode indefinitely  
Rejected: preserves ambiguity and duplicates tooling complexity.

3. Security-service owned global manifest only  
Rejected: weak module ownership and slower local iteration.

---

## Consequences

### Positive ✅

- Single canonical permission source per module.
- Deterministic extraction/reporting and audit tooling.
- Reduced registration drift and copy/paste errors.
- Easier onboarding and review of permission changes.

### Negative ⚠️

- Migration effort across modules.
- Temporary dual-maintenance during transition windows.
- Requires shared manifest loader support and CI rules.

### Neutral

- Permission constants for authorization checks can remain where helpful.
- Existing permission names do not need to change unless semantically incorrect.

---

## Implementation Notes

- Standard manifest location: `src/main/resources/permissions.yaml`
- Recommended manifest shape:
  - `domain`
  - `serviceName`
  - `version`
  - `permissions[]` with `name`, `description`
- `PermissionRegistrationSupport` should receive parsed manifest entries rather than inline hardcoded lists.
- Add module tests/rules to prevent hardcoded registration lists after migration.

---

## Remediation Plan (All Modules)

### Phase 0 — Platform Enablement (by 2026-03-05)

1. Add shared manifest parser/validator in `pos-security-common`.
2. Add CI check for manifest schema + duplicate permission names.
3. Add CI check/report that compares manifest names vs enforced authorities.
4. Add `pos-archunit` rule preventing new inline permission registration lists.

### Phase 1 — Track A: Migrate Existing Registration Classes (by 2026-03-12)

Migrate these modules from inline `PermissionDefinition.of(...)` lists to `permissions.yaml`:

- `pos-accounting`
- `pos-catalog`
- `pos-customer`
- `pos-order`
- `pos-people`
- `pos-price`
- `pos-shop-manager`
- `pos-workorder`

### Phase 2 — Track B: Adopt Manifest Where Authorization Exists (by 2026-03-19)

Introduce `permissions.yaml` and manifest-backed registration where applicable:

- `pos-documents`
- `pos-inventory`
- `pos-invoice`
- `pos-location`
- `pos-mcp-server`

### Phase 3 — Track C: Legacy Cleanup and Enforcement Hardening (by 2026-03-26)

1. Remove deprecated initializer/registry registration paths.
2. Remove dead/duplicated permission registry code where superseded by manifest loading.
3. Enforce “no new inline permission registration lists” rule across modules.

### Phase 4 — Governance and audit readiness (by 2026-04-02)

1. Generate consolidated permission inventory from manifests in CI artifacts.
2. Add periodic drift report (registered vs enforced vs documented).
3. Make manifest review mandatory in PR checklist for security-sensitive changes.

### Module-by-Module Remediation Matrix

| Module | Remediation Action | Target Phase |
|---|---|---|
| `pos-accounting` | Migrate registration to `permissions.yaml` | Phase 1 |
| `pos-api-gateway` | Validate pass-through auth behavior; no module registry | Phase 4 |
| `pos-archunit` | Add architecture rule to ban inline registration lists | Phase 0 |
| `pos-catalog` | Migrate registration to `permissions.yaml` | Phase 1 |
| `pos-customer` | Migrate registration to `permissions.yaml`; remove legacy registry path | Phase 1 / 3 |
| `pos-dependencies` | No direct action (BOM/aggregation module) | N/A |
| `pos-document-helper` | No direct registration; ensure consumers own manifests | Phase 4 |
| `pos-documents` | Add `permissions.yaml` + manifest-backed registration | Phase 2 |
| `pos-event-receiver` | Verify if any endpoint authorization exists; add manifest only if needed | Phase 2 |
| `pos-events` | No direct registration; provide shared helper compatibility | Phase 0 |
| `pos-image` | Verify endpoint auth use; add manifest if permissions are enforced | Phase 2 |
| `pos-inquiry` | Verify endpoint auth use; add manifest if permissions are enforced | Phase 2 |
| `pos-inventory` | Add `permissions.yaml` + manifest-backed registration | Phase 2 |
| `pos-invoice` | Add `permissions.yaml` + manifest-backed registration | Phase 2 |
| `pos-location` | Add `permissions.yaml` + manifest-backed registration | Phase 2 |
| `pos-mcp-server` | Add `permissions.yaml` + manifest-backed registration for protected tools/routes | Phase 2 |
| `pos-order` | Migrate registration to `permissions.yaml` | Phase 1 |
| `pos-people` | Migrate registration to `permissions.yaml` | Phase 1 |
| `pos-price` | Migrate registration to `permissions.yaml` | Phase 1 |
| `pos-security-common` | Implement parser/validator + shared loader APIs | Phase 0 |
| `pos-security-service` | Enforce manifest validation server-side and emit drift diagnostics | Phase 0 / 4 |
| `pos-service-discovery` | No direct action | N/A |
| `pos-shared-dtos` | No direct registration; publish DTOs for manifest parsing errors if needed | Phase 0 |
| `pos-shop-manager` | Migrate registration to `permissions.yaml` | Phase 1 |
| `pos-tax` | Verify endpoint auth use; add manifest if permissions are enforced | Phase 2 |
| `pos-tax-common` | No direct action (shared library) | N/A |
| `pos-vehicle-fitment` | Verify endpoint auth use; add manifest if permissions are enforced | Phase 2 |
| `pos-vehicle-inventory` | Verify endpoint auth use; add manifest if permissions are enforced | Phase 2 |
| `pos-vehicle-reference-carapi` | No direct action unless API is secured by module permissions | Phase 4 |
| `pos-vehicle-reference-nhtsa` | No direct action unless API is secured by module permissions | Phase 4 |
| `pos-workorder` | Migrate registration to `permissions.yaml` | Phase 1 |

---

## References

- Superseded ADR: `0002-crm-permission-taxonomy.adr.md`
- Related: `0011-api-gateway-security-architecture.adr.md`
- Related: `0014-gateway-internal-service-security.adr.md`
- Related: `0017-api-controller-http-response-codes.adr.md`
