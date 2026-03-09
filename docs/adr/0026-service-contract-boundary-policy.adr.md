# ADR-0026: Service Contract Boundary Policy (All Backend Modules)

**Status:** ACCEPTED  
**Date:** 2026-02-26  
**Deciders:** Backend Architecture Team, Platform Lead  
**Affected Issues:** Module encapsulation, service API boundaries, ArchUnit enforcement

---

## Context

`durion-positivity-backend` modules use package-level encapsulation where only the service layer is intended for cross-module contracts.

A module-specific ADR in `pos-inventory` documented this pattern locally, but the rule applies platform-wide and should be governed centrally.

ADR-0009 already defines backend domain responsibilities and layering, but this ADR makes the service contract boundary explicit as a standalone, enforceable platform policy.

---

## Decision

### 1. Service Package Is Contract-Only

**Decision:** ✅ **Resolved** - `com.positivity.{domain}.service..` is the public contract surface and must contain interfaces only.

### 2. Implementations Stay Internal

**Decision:** ✅ **Resolved** - Concrete service implementations must be placed in `com.positivity.{domain}.internal.service..` and implement interfaces from `..service..`.

### 3. Dependency Direction

**Decision:** ✅ **Resolved** - Controllers and internal components depend on service interfaces, never on concrete service implementations across package boundaries.

Required direction:

- `internal.controller` -> `service` (interface)
- `internal.service` -> `internal.repository` / `internal.domain` / `internal.entity`
- No direct external module dependency on `internal.*` packages

### 4. Enforcement

**Decision:** ✅ **Resolved** - Every module must enforce this boundary with ArchUnit rules.

Minimum checks:

- classes in `..service..` are interfaces
- no dependencies from outside module into `..internal..`
- controllers do not directly depend on repositories/entities

---

## Consequences

### Positive ✅

- Clear public API surface per module.
- Safer refactoring of internal implementations.
- Stronger cross-module encapsulation.
- Consistent architecture review and tooling checks.

### Negative ⚠️

- Additional boilerplate (interface + implementation split).
- Requires ArchUnit maintenance as modules evolve.

### Neutral

- Existing modules already aligned can adopt with no runtime behavior change.

---

## References

- Related: `0009-backend-domain-responsibilities-guide.adr.md`
- Related: `0014-gateway-internal-service-security.adr.md`
- Source migrated from module ADR: `durion-positivity-backend/pos-inventory/docs/adr/0002-service-contract-boundary.md`
