# ADR-0026: Service Contract Boundary Policy (All Backend Modules)

**Status:** ACCEPTED — amended 2026-08-27 (§1–§4 revised: the service package is a grant surface, not the home for every service interface; see §Amendments)  
**Date:** 2026-02-26  
**Deciders:** Backend Architecture Team, Platform Lead  
**Affected Issues:** Module encapsulation, service API boundaries, ArchUnit enforcement, durion-positivity-backend#1541

---

## Context

`durion-positivity-backend` modules use package-level encapsulation where only the service layer is intended for cross-module contracts.

A module-specific ADR in `pos-inventory` documented this pattern locally, but the rule applies platform-wide and should be governed centrally.

ADR-0009 already defines backend domain responsibilities and layering, but this ADR makes the service contract boundary explicit as a standalone, enforceable platform policy.

---

## Decision

### 1. Service Package Is Contract-Only

> **Amended 2026-08-27.** Superseded in part — see §Amendments (D1–D5).

**Decision:** ✅ **Resolved** - `com.positivity.{domain}.service..` is the public contract surface and must contain interfaces only.

### 2. Implementations Stay Internal

> **Amended 2026-08-27.** Superseded in part — see §Amendments (D1–D5).

**Decision:** ✅ **Resolved** - Concrete service implementations must be placed in `com.positivity.{domain}.internal.service..` and implement interfaces from `..service..`.

### 3. Dependency Direction

> **Amended 2026-08-27.** Superseded in part — see §Amendments (D1–D5).

**Decision:** ✅ **Resolved** - Controllers and internal components depend on service interfaces, never on concrete service implementations across package boundaries.

Required direction:

- `internal.controller` -> `service` (interface)
- `internal.service` -> `internal.repository` / `internal.domain` / `internal.entity`
- No direct external module dependency on `internal.*` packages

### 4. Enforcement

> **Amended 2026-08-27.** Superseded in part — see §Amendments (D1–D5).

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

## Amendments

### 2026-08-27 — The service package is a grant surface, not the home for every service interface

**The finding.** 226 of the 293 types in a module's public `{domain}.service` package import that
module's own `internal.*` (counted 2026-08-27; durion-positivity-backend#1541). Of the 663 such
imports, 544 are `internal.dto` types, 53 are `internal.enums`, 41 are `internal.entity` — JPA
entities named in signatures this ADR calls the cross-module contract, spread over 31 interfaces in
11 modules — and 11 are `internal.exception`. Six modules have a `service.model` package; the rest
have nowhere compliant to put a parameter type. Eight of the 292 are not interfaces at all but
records, exceptions and result types (in pos-events, pos-invoice, pos-mcp-server and pos-price),
which §1 already disallows and the per-module `service_package_should_define_interfaces_only` rules
do not currently catch in those modules.

**This is what the clauses produce, not a failure to follow them.** §1 makes `service..` the public
contract surface. §2 requires every implementation to sit in `internal.service` and implement an
interface from `..service..`. §3 requires `internal.controller` to depend on that interface. §1 is
about *exporting*; §2 and §3 are a dependency-injection and testability convention that applies to
every service a module writes. Pointing both at one package makes the export surface identical to
the module's internal wiring seam: 41 of the 42 internal controllers in pos-inventory import
`com.positivity.inventory.service`. Once every controller must call through the exported package,
that package must name the controllers' request and response DTOs — which correctly live in
`internal.dto`, because they are HTTP shapes. The only compliant alternative was to duplicate every
DTO into `service.model` and map between the copies, a cost this ADR's Consequences section never
recorded (it anticipates only "additional boilerplate (interface + implementation split)"). The 226
are arithmetic.

**The enforcement gap is in this ADR, not in the ArchUnit code.** §4's minimum checks are three:
classes in `..service..` are interfaces (a shape assertion), nothing outside the module reaches into
`..internal..` (consumer-side), and controllers do not touch repositories or entities (layering).
None asks whether a module's own contract surface depends on that module's own internals. The
platform and per-module rules implement §4 faithfully; the same-module skips at
`pos-archunit/src/test/java/com/positivity/archunit/ArchitectureTests.java:152` and `:284` are
correct against the policy as written. The producer-side clause was never specified.

**What changed underneath.** [ADR-0044](0044-platform-event-only-domain-walls.adr.md) R1 forbids
synchronous domain-to-domain calls and §3 moved genuinely shared payload types into
`pos-domain-events`. Cross-module Java imports of another module's `service` package are currently
zero platform-wide. That is ADR-0044 working, not this boundary decaying. ADR-0044's four
amendments grant **synchronous REST edges only** — enforced in `DomainWallsTest` against
`internal.client` classes — and none of them grants an in-process import; `pos-catalog` and
`pos-order` hold the sole live grant and import no `com.positivity.supplier.*` type at all.

**But the grant surface is real, and one interface already demonstrates it.** ADR-0044's 2026-08-10
amendment does not say "pos-catalog may call pos-supplier". It names
`SupplierStockService`'s read API — an interface in pos-supplier's public `service` package. That
interface takes and returns `service.model` types, leaks nothing, and documents its approved
callers, its ADR provenance and its degradation contract. It is the specification the
`SupplierStockClientImpl` classes in pos-catalog and pos-order were built against without importing
it. pos-supplier is also the exemplar module (0 of 9 interfaces leaking) — not because it was
tidier, but because it is the only module whose `service` package has ever had an outside reader.

**The decision.**

- **D1 — `service` is the grant surface.** `com.positivity.{domain}.service` and
  `com.positivity.{domain}.service.model` hold the published contract for operations another module
  is permitted to invoke, whether that invocation arrives as an in-process call or over REST. It is
  a specification surface first and an import surface only where an ADR grants in-process coupling.
- **D2 — Membership is by grant, not by convention.** A type MAY reside in `{domain}.service` only
  if a cross-module grant names it: a scoped exception in ADR-0044 §Amendments, or a future
  in-process coupling grant recorded in an ADR. Utility-module APIs (ADR-0044 §1) are governed by
  their OpenAPI contract, not by Java package placement, and do not by themselves confer
  membership. Absent a grant, a service interface is internal.
- **D3 — The interface/implementation split stays, and moves.** §2 is amended: a service
  implementation MUST live in `internal.service..` and MUST implement an interface, but that
  interface lives beside it in `internal.service..` unless D2 places it in the grant surface. §3 is
  amended correspondingly: `internal.controller` depends on the interface in `internal.service..`
  for ungranted operations, and on `{domain}.service` only for granted ones.
- **D4 — The grant surface MUST NOT depend on `internal.*`.** Parameter, return and thrown types of
  a type in `{domain}.service` MUST come from `{domain}.service.model`, `pos-shared-dtos`,
  `pos-domain-events`, or the JDK. This is the producer-side rule §4 omitted.
- **D5 — Enforcement is producer-side and gated at zero.** §4's minimum checks are extended with a
  rule enforcing D4, in `pos-archunit` and mirrored per module. The rule MUST anchor the public
  package exactly — `com.positivity.(*).service` and `.service.model`, never a bare `..service..`,
  which ArchUnit also matches against `internal.service` and would fail every module on its first
  run. It ships gated at zero after the migration below, not as a ratchet.

**Why not ratchet down from 226.** A ratchet in the manner of `dtoSuffixMigrationReport`
(`ArchitectureTests.java:335`) would treat 226 leaking interfaces as the work item. Under D2 the
work item is different: of the 292 types in the public service packages, exactly one —
`SupplierStockService` — is granted, and the other 291 are in the wrong package regardless of
whether they leak. Ratcheting the leak count would institutionalise the wrong denominator and leave
the platform paying DTO-duplication costs to make internal wiring look like a public API.

**Why not retire `service` entirely.** ADR-0044 grants are live and growing, and each one needs a
written contract its callers can build against; `SupplierStockService` is that document today. A
curated list is also the only kind a module can honour — which is what would make a future
in-process coupling grant feasible. A 293-entry export list cannot be honoured by anyone, as the
226 demonstrate.

**What this does not license.** Residence in `{domain}.service` confers no permission by itself —
the grant lives in ADR-0044, and calling a granted interface still requires the ADR-0044 exception
and its `DomainWallsTest` entry. Widening a grant still requires amending ADR-0044. This amendment
does not retire the interface/implementation split, does not authorise migrating DTOs into
`service.model` for interfaces that are becoming internal, and does not license a module to publish
an interface in anticipation of a grant it has not been given.

**Migration.** Per module, in any order, each independently mergeable:

1. Move ungranted interfaces from `{domain}.service` to `{domain}.internal.service..`, updating
   imports. No signature, DTO or behaviour changes. In modules whose `internal.service` is already
   large (pos-inventory holds 131 classes), split by subdomain as pos-supplier does rather than
   flattening more into one package.
2. Leave granted interfaces in `{domain}.service`, moving their parameter and return types to
   `{domain}.service.model` to satisfy D4.
3. Land the D5 rule gated at zero once the last module is migrated; until then it reports.

The 31 interfaces naming `internal.entity` cease to be boundary violations once they are internal,
and become an ordinary design concern to address with their owning module rather than a blocker
for this migration.

**Required follow-ups.**

- `durion-positivity-backend/CLAUDE.md` §Module Structure and `AGENTS.md` — the "`service/` ←
  PUBLIC API surface" description and its templates state the superseded §1–§3 rule.
- ADR-0044 §7's sentence that per-module rules "extend, and do not alter, the intra-module package
  boundary rules of ADR-0026" remains true and needs no change, but its cross-reference should note
  this amendment.
- `pos-archunit` `ArchitectureTests` and each module's `ArchitectureTest` — add the D5 rule;
  `only_service_layer_should_be_public_api` and `service_package_should_define_interfaces_only`
  keep their shape assertions and gain the D4 dependency assertion.
- durion-positivity-backend#1541 — acceptance criteria are superseded by D1–D5 and the migration
  above; the ratchet and the 226-file migration described there are both withdrawn.

---

## References

- Related: `0009-backend-domain-responsibilities-guide.adr.md`
- Related: `0014-gateway-internal-service-security.adr.md`
- Related: `0044-platform-event-only-domain-walls.adr.md` — module classification, R1, and the scoped grants that define grant-surface membership (D2)
- Related: `0049-supplier-integration-module-boundary.adr.md` — `SupplierStockService`, the worked example of a granted contract surface
- Source migrated from module ADR: `durion-positivity-backend/pos-inventory/docs/adr/0002-service-contract-boundary.md`
