# ADR-0051: Supplier Protocol Adapter and Codec Versioning Policy

**Status:** PROPOSED **Date:** 2026-08-10 **Deciders:** Architecture, Backend Lead, Positivity (Integrations) Domain **Affected Issues:** durion#372 (CAP-317),
durion-positivity-backend#1221

---

## Context

- **Current State**: The same business service exists in multiple EDIWheel norm generations (order in A2.5 and C1.0; order status in A2.5, C1.0, and C1.1; stock report in B2.1
  and C1.0; the emerging C1.2 JSON API), and vendors support different subsets. Non-EDIWheel vendors (Michelin S2S) add proprietary formats.
- **The Problem**: Multiple protocol versions of the same capability must coexist behind one interface, selectable per vendor per deployment, without forking business logic.
- **Drivers**: Supplier architecture goals 1–2 (reusable modules, reuse beyond EDIWheel); the high likelihood of implementing several versions of the same service per vendor.
- **Scope**: Adapter organization, codec registration/resolution, and version-evolution rules inside `pos-supplier`.

---

## Decision

### 1. Adapters per wire-format family, not per vendor

**Decision:** ✅ **Resolved** — One adapter package per protocol family: `EDIWHEEL_A25`, `EDIWHEEL_C1`, `EDIWHEEL_B`, `EDIWHEEL_JSON` (C1.2), `MICHELIN_S2S`, and future
families for proprietary distributors. Two vendors speaking the same norm share the adapter with different profile bindings. Adapter code may depend on `spi` and
`domain/model` only; vendor wire types never escape `adapter/**` (ArchUnit-enforced).

### 2. Registry keyed by (capability, protocolFamily, version)

**Decision:** ✅ **Resolved** — Codecs register against the triple `(capability, protocolFamily, version)` (e.g. `ORDER_STATUS × EDIWHEEL_C1 × C1_1`). The vendor profile's
binding names the triple to use; switching a vendor's order status from C1.0 to C1.1 is a configuration change. Duplicate registrations fail startup; an unbound capability
resolves to `CAPABILITY_NOT_CONFIGURED`.

### 3. Version evolution rules

**Decision:** ✅ **Resolved**

- A new norm version = a new codec class in the existing family, registered under the new version key; older codecs remain until no deployment binds them.
- The canonical model changes only when the **business** capability grows, never to mirror a wire format. Fields a given norm cannot express are recorded per exchange as
  `unmappedFields` in the exchange audit (diagnosable, never silently dropped).
- Version-specific quirks (e.g. C1.1 adding `Geolocation`/`ErrorHead` structures) stay inside the codec.
- Every codec ships golden-file round-trip tests derived from the vendor specs (`docs/ediwheel/`), plus a sandbox smoke test where the vendor offers one.

### 4. Wire-format technology

**Decision:** ✅ **Resolved** — XML norms bind via JAXB models generated or hand-written from the EDIWheel message implementation guidelines; JSON families use Jackson. Codec
output is bytes + content-type handed to the shared base client; codecs perform no I/O.

---

## Consequences

**Positive:** vendor #2 on an existing norm is configuration only; new norms are additive codec work; business logic never forks per version. **Negative / accepted:** the
registry triple becomes a stable public naming scheme (renames are migrations); golden-file fixtures need upkeep as vendors revise norms.

## References

- `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md` §6, §10
- ADR-0049, ADR-0050
