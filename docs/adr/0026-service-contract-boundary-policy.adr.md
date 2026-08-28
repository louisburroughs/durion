# ADR-0026: Service Contract Boundary Policy (All Backend Modules)

**Status:** ACCEPTED (amended 2026-08-27 — see §Amendments, D1–D5)  
**Date:** 2026-02-26  
**Deciders:** Backend Architecture Team, Platform Lead  
**Affected Issues:** Module encapsulation, service API boundaries, ArchUnit enforcement

> **Note:** §1–§4 below are the original 2026-02-26 decisions. The 2026-08-27 amendment (§Amendments)
> supersedes their reading of `{domain}.service` as a general public contract surface: membership in
> `{domain}.service` is now **by grant**, and ungranted service interfaces live in `internal.service`.
> The interface/implementation split (§2–§3) itself is retained.

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

## Amendments

### 2026-08-27 — Grant-surface model (D1–D5)

**Context.** The original §1–§3 fused two unrelated rules onto one package: §1 made
`{domain}.service..` the cross-module contract surface (an *exporting* rule), while §2/§3 required
every implementation to live in `internal.service` implementing an interface from `..service..` (a
dependency-injection and testability convention that applies to every service a module writes).
Pointing both at one package made the export surface identical to each module's internal wiring
seam: once every controller must call through the exported package, that package must name the
controllers' request/response DTOs, which correctly live in `internal.dto`. Compliance would have
required duplicating every DTO into `service.model` plus mappers — a cost §Consequences never
recorded. A 2026-08-27 census found 226 of 292 public service types depending on their own module's
`internal.*`. Meanwhile ADR-0044 removed synchronous domain-to-domain calls entirely: cross-module
Java imports of another module's `service` package are zero platform-wide. See
durion-positivity-backend issue #1541 and the originating review discussion on PR #1540.

**Decisions:**

- **D1 — Grant surface.** `{domain}.service` and `{domain}.service.model` are the **grant
  surface**: the published contract for operations another module is permitted to invoke,
  in-process or over REST. A specification surface first; an import surface only where an ADR
  grants in-process coupling.
- **D2 — Membership is by grant, not by convention.** A type may reside there only if a
  cross-module grant names it: an ADR-0044 scoped exception, or a future in-process grant recorded
  in an ADR. Utility-module APIs (ADR-0044 §1) are governed by their OpenAPI contract, not by
  package placement, and do not by themselves confer membership. Absent a grant, a service
  interface is internal.
- **D3 — The interface/implementation split stays and moves.** Ungranted interfaces live beside
  their implementations in `internal.service..` (subdomain splits `internal.{sub}.service..` are
  legitimate for large modules), and `internal.controller` depends on them there.
- **D4 — Grant-surface types must not depend on `internal.*`.** Parameters, returns and thrown
  types come from `{domain}.service.model`, `pos-shared-dtos`, `pos-domain-events`, or the JDK.
- **D5 — Enforcement is producer-side and gated at zero.** `pos-archunit` carries a cross-module
  rule that no type in a module's public `service`/`service.model` package depends on that
  module's `internal.*`; after the issue #1541 migration it is build-failing at zero, with no
  threshold parameter. Per-module `ArchitectureTest` classes carry the mirrored D4 assertion.

**Sole grant at amendment time:** `SupplierStockService` (pos-supplier), named by ADR-0044's
2026-08-10 amendment, together with its `service.model` request/response types. It is the exemplar:
`service.model`-only signatures, zero `internal.*` dependencies, javadoc recording approved
callers, ADR provenance and degradation contract. Residence in `{domain}.service` confers no call
permission by itself — the transport grant lives in ADR-0044 and still requires its
`DomainWallsTest` entry.

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
