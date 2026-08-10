# ADR-0051: Supplier Protocol Adapter and Codec Versioning Policy

**Status:** PROPOSED — revised 2026-08-10 (PRCR-009)  
**Date:** 2026-08-10  
**Deciders:** Architecture, Backend Lead, Positivity (Integrations) Domain  
**Affected Issues:** durion#372 (CAP-317), durion-positivity-backend#1221

---

## Context

- **Current State**: The same business service exists in multiple EDIWheel norm generations (order in A2.5 and C1.0; order status in A2.5, C1.0, and C1.1; stock report in B2.1
  and C1.0; the emerging C1.2 JSON API), and vendors support different subsets. Non-EDIWheel vendors (Michelin S2S) add proprietary formats.
- **The Problem**: Multiple protocol versions of the same capability must coexist behind one interface, selectable per vendor per deployment, without forking business logic.
- **Drivers**: Supplier architecture goals 1–2 (reusable modules, reuse beyond EDIWheel); the high likelihood of implementing several versions of the same service per vendor;
  the backend package policy requiring everything except service contract interfaces and the application class under `internal`.
- **Scope**: Adapter organization, codec registration/resolution, and version-evolution rules inside `pos-supplier`.

---

## Decision

### 1. Package layout follows the repository internal-package policy

**Decision:** ✅ **Resolved** — `pos-supplier` follows the backend rule that only `com.positivity.supplier.service..` (contract interfaces, ADR-0026) and the application
class live outside `internal`. The supplier-specific packages are therefore:

- `com.positivity.supplier.internal.domain` — canonical model and orchestration
- `com.positivity.supplier.internal.spi` — capability ports adapters implement
- `com.positivity.supplier.internal.registry` — adapter registry and resolution
- `com.positivity.supplier.internal.adapter.<family>` — one package per protocol family
- `com.positivity.supplier.internal.web`, `internal.persistence`, `internal.config`, `internal.events` — per platform conventions

All layout references in the architecture document and CAP-317 stories use these `internal.*` paths.

### 2. Adapters per wire-format family, not per vendor

**Decision:** ✅ **Resolved** — One adapter package per protocol family: `EDIWHEEL_A25`, `EDIWHEEL_C1`, `EDIWHEEL_B`, `EDIWHEEL_JSON` (C1.2), `MICHELIN_S2S`, and future
families for proprietary distributors. Two vendors speaking the same norm share the adapter with different profile bindings. Adapter code may depend on `internal.spi` and
`internal.domain` model types only; vendor wire types never escape `internal.adapter.**` (ArchUnit-enforced).

### 3. Registry keyed by (capability, protocolFamily, version)

**Decision:** ✅ **Resolved** — Codecs register against the triple `(capability, protocolFamily, version)` (e.g. `ORDER_STATUS × EDIWHEEL_C1 × C1_1`). The vendor profile's
binding names the triple to use; switching a vendor's order status from C1.0 to C1.1 is a configuration change. Duplicate registrations fail startup; an unbound capability
resolves to `CAPABILITY_NOT_CONFIGURED`.

### 4. Version evolution rules

**Decision:** ✅ **Resolved**

- A new norm version = a new codec class in the existing family, registered under the new version key; older codecs remain until no deployment binds them.
- The canonical model changes only when the **business** capability grows, never to mirror a wire format. Fields a given norm cannot express are recorded per exchange as
  `unmappedFields` in the exchange audit (diagnosable, never silently dropped).
- Version-specific quirks (e.g. C1.1 adding `Geolocation`/`ErrorHead` structures) stay inside the codec.
- Every codec ships golden-file round-trip tests derived from the vendor specs (`docs/ediwheel/`), plus a sandbox smoke test where the vendor offers one.

### 5. Wire-format technology

**Decision:** ✅ **Resolved** — XML norms bind via JAXB models generated or hand-written from the EDIWheel message implementation guidelines; JSON families use Jackson. Codec
output is bytes + content-type handed to the shared base client; codecs perform no I/O.

---

## Consequences

**Positive:** vendor #2 on an existing norm is configuration only; new norms are additive codec work; business logic never forks per version; module encapsulation matches
every other backend module.
**Negative / accepted:** the registry triple becomes a stable public naming scheme (renames are migrations); golden-file fixtures need upkeep as vendors revise norms.

## References

- `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md` §5–§6, §10
- ADR-0049, ADR-0050, ADR-0026 (service contract boundary)
