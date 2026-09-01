# ADR-0058: Labor-Time Sourcing Architecture (pos-catalog Estimated Service Time)

**Status:** PROPOSED
**Date:** 2026-09-01
**Deciders:** Architecture, Backend Lead, Pricing & Fees Domain, Workorder Execution Domain
**Affected Issues:** durion-positivity-backend#1569, durion-positivity-backend#1573, durion-positivity-backend#1575

---

## Context

- **Current State**: Nothing in the platform stores an estimated service time. `WorkorderSummary.estimatedLaborHours` is declared and always null; `workorder_service` has quantity and price but no hours; the pos-catalog `service` table carried only naming until V17. Actual technician time (time on task) already flows — `WorkorderLaborEntry.hoursWorked` sums into `totalLaborHours` — but there is no estimate to compare it against.
- **The Problem**: Book time (flat-rate time) is per operation *and* per vehicle, comes from licensable third-party guides (MOTOR, Mitchell 1, ALLDATA), OEM warranty manuals and parts-manufacturer tables, and must be defensible on an invoice — which requires source and revision attribution. A single scalar column models none of that.
- **Drivers**: Estimate defaulting for `LABOR` estimate items; estimate-vs-actual variance; scheduling capacity input; licensing terms that differ per source and constrain persistence and replication; ADR-0026/ADR-0044 module walls.
- **Scope**: Where estimated service times live, how they are sourced, and how consumers reach them. The full worked plan is `durion-positivity-backend/pos-catalog/docs/service-time-sourcing-plan.md`; this ADR fixes its load-bearing decisions. The owner-confirmed three-record taxonomy on #1569 (time on task, attendance time entry, estimated service time) bounds this ADR to the third record only.

---

## Decision

### 1. System of record and vehicle keying

**Decision:** ✅ **Resolved** — pos-catalog's `ServiceEntity` is the system of record for estimated service time (owner decision recorded on #1569, 2026-08-29). Vehicle-specific times live in a **vehicle-keyed child table inside pos-catalog** (`service_labor_standard`: year/make/model/submodel/engine key with null-as-wildcard, decimal hours in tenths, time type, overlap and included-operation metadata, source + revision provenance, append-and-supersede lifecycle). The rejected alternative — a service-fitment analogue of `PartFitmentEntity` in pos-vehicle-fitment — would re-split the record and put a second cross-module hop on the quote path; pos-vehicle-fitment contributes vehicle *vocabulary*, not rows.

### 2. Operation taxonomy

**Decision:** ✅ **Resolved** — `service` gains `operation_code` (Durion-owned identity, unique when present; vendor codes map onto ours, never the reverse), `operation_category` (REPAIR | DIAGNOSTIC | MAINTENANCE | TIRE_SERVICE) and `default_labor_hours` (vehicle-agnostic fallback only, deliberately second-class to the standards table). Naming rules are in ADR-0059.

### 3. Sourcing pattern

**Decision:** ✅ **Resolved** — Adopt the pos-supplier shape (ADR-0049/ADR-0050): a provider SPI (`LaborTimeProviderPort`) with vendor adapters *inside pos-catalog*, config-driven per-source profiles with sandbox base-url override, typed degradation statuses, chunked-manifest ingestion cloned from the ADR-0053 supplier-price import. Per-vendor microservices are rejected — the dead `pos-vehicle-reference-*` modules demonstrate that failure mode. Phasing: a local mock provider proves the whole pipeline first, then one licensed aggregator, then multi-source (OEM/manufacturer primary, aggregator backstop) with a data-driven precedence policy.

### 4. Licensing gates persistence and transport

**Decision:** ✅ **Resolved** — Each source's license terms are settled **before** its adapter is built, and select one of two modes: **STORE** (feed ingested into `service_labor_standard` with import-manifest bookkeeping) or **QUERY_ONLY** (live SPI call, TTL-bounded cache, never persisted). Whether any licensed-derived value may ride a Kafka fact is likewise a per-source license question.

### 5. Transport to pos-workorder

**Decision:** PENDING — Proposed split, to be ratified when the consumer side is built: a scoped REST edge (ADR-0044 file-scoped grant, `ServiceLaborTimeService` in `catalog.service`) for vehicle-specific resolution at quote time, plus a `CatalogServiceUpdatedV2` fact carrying only the vehicle-agnostic `default_labor_hours` for degraded-mode replicas. Requires its own ADR-0044 amendment naming the grant before any cross-module code lands.

### 6. Timekeeping boundary

**Decision:** ✅ **Resolved** — Estimated service time never reads or writes `work_session`, `time_entry`, or `TimekeepingEntry` (owner ruling on #1573: time entry is clock-in/out and breaks; workorder time comes from service times). The only contact is variance reporting, which compares the estimate total against the already-computed time-on-task `totalLaborHours` in pos-workorder.

---

## Consequences

**Positive:**

- Estimates become authorable now (DURION-source rows) and importable later without reshaping storage.
- Every stored time is attributable to a source and revision; corrections supersede rather than overwrite, so quoted numbers stay explainable.
- Licensing constraints are architectural inputs, not retrofits.

**Negative / Risks:**

- The standards table is write-heavy at aggregator scale (millions of rows once vehicle-keyed); partitioning is deferred to the aggregator phase.
- Until the transport decision (5) is ratified and built, pos-workorder still shows null `estimatedLaborHours`.

**Neutral:**

- The mock provider stays alive permanently as the SPI contract-test double.

---

## References

- `durion-positivity-backend/pos-catalog/docs/service-time-sourcing-plan.md` — the full phased plan this ADR pins.
- ADR-0026 (module grant surfaces), ADR-0044 (cross-module transport), ADR-0049/0050 (supplier SPI pattern), ADR-0053 (chunked-manifest import), ADR-0059 (naming/taxonomy).
