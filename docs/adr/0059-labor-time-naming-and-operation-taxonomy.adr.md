# ADR-0059: Labor-Time Naming and Service Operation Taxonomy

**Status:** PROPOSED
**Date:** 2026-09-01
**Deciders:** Architecture, Backend Lead, Workorder Execution Domain, People & Roles Domain
**Affected Issues:** durion-positivity-backend#1569, durion-positivity-backend#1573

---

## Context

- **Current State**: Two permanent `work_session` tables exist — pos-workorder's (a technician's task clock: time on task) and pos-people's (a person's attendance clock feeding `time_entry`). Both have live writers and neither is going away (#1564 outcome). The estimated-time feature arriving under #1569 is a third, distinct concept that must not collide with either.
- **The Problem**: The domain term "work session" is occupied twice, and #1569's owner-confirmed taxonomy distinguishes three records — time on task, attendance time entry, estimated service time — that code and schema must keep apart permanently.
- **Drivers**: `timekeeping:work_session:*` permission bits 274–277 are baked into issued JWT bitsets (rename = deprecate in place, never remove); vendor labor-guide codes must map onto stable Durion identifiers, not the reverse.
- **Scope**: Naming and taxonomy only; the sourcing architecture is ADR-0058.

---

## Decision

### 1. Three-record taxonomy is canonical

**Decision:** ✅ **Resolved** — The following records are distinct, deliberately kept, and never merged or cross-written:

| Record | Meaning | Home |
|---|---|---|
| Time on task | Actual duration a technician spends on a workorder task | pos-workorder `work_session` → `WorkorderLaborEntry` |
| Time entry (attendance) | Clock-in / clock-out / breaks for a person's day | pos-people `work_session` → `time_entry` |
| Estimated service time | Book-time baseline per operation + vehicle | pos-catalog (`service` taxonomy + `service_labor_standard`) |

Estimate-vs-actual variance compares estimated service time against **time on task** only; attendance never participates in workorder estimates.

### 2. Names for the estimate feature

**Decision:** ✅ **Resolved** — The estimate feature never uses the name `work_session`. Canonical terms: **labor time** (one vehicle-specific published time), **service labor standard** (`service_labor_standard`, the stored row with provenance), **estimated labor hours** (`estimatedLaborHours`, the workorder-level aggregate), **labor time provider** (the upstream feed abstraction). Unit is decimal hours in tenths (0.1 hr = 6 min) — never minutes or seconds.

### 3. Operation identity

**Decision:** ✅ **Resolved** — `service.operation_code` is the Durion-owned operation identity: uppercase alphanumeric segments joined by single dashes (e.g. `BRAKE-PAD-FRONT`), at most 64 characters, unique across services when present, nullable for services that never join a guide taxonomy. Vendor operation codes map onto Durion codes via cross-reference, never the reverse. `service.operation_category` is one of REPAIR, DIAGNOSTIC, MAINTENANCE, TIRE_SERVICE; diagnostic operations are sold as their own time-block lines, never folded into a repair operation's hours.

### 4. Permission-bit discipline

**Decision:** ✅ **Resolved** — Existing `timekeeping:work_session:*` bits (274–277) stay allocated whatever renaming ever happens around the clock tables; permission catalogs are append-only (bits are never reused), per the established `generate-permissions.sh --sync` flow.

---

## Consequences

**Positive:** The three vocabularies cannot collide again; reviewers can reject a misnamed table or field by pointing at this ADR.

**Negative / Risks:** The double-booked `work_session` name remains permanently — accepted, since both tables have live writers and issued JWTs pin the permission names.

---

## References

- durion-positivity-backend#1569 (taxonomy comment, 2026-08-29) and #1573 (timekeeping boundary ruling).
- `durion-positivity-backend/pos-catalog/docs/service-time-sourcing-plan.md` §1 (naming table), §8 (boundary).
- ADR-0058 (sourcing architecture).
