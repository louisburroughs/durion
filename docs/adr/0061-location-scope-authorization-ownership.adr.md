# ADR-0061: Location Scope Authorization — Ownership, Token Shape, and Effective Dating

**Status:** PROPOSED
**Date:** 2026-09-07
**Deciders:** Chief Architect, Security & Authorization Domain, People & Roles Domain, Platform Engineering
**Affected Issues:** [#1375](https://github.com/louisburroughs/durion-positivity-backend/issues/1375), #1372, #1373, #1499, #1512

---

## Context

### Current State

Two models describe a user's location reach, and they disagree about which one matters.

`pos-security-service.role_assignments` carries `scope_type` (`GLOBAL` / `LOCATION`),
effective dating, and a `role_assignment_scope_locations` child table.
`RoleAssignment.coversLocation(...)` honours it and `RoleManagementServiceImpl.userHasPermission`
uses it. **None of it reaches an enforcement point.** `UserServiceImpl.resolveEffectiveRoleNames`
collapses scoped and unscoped assignments into one `Set<String>` of role names before
`RoleAuthorityService` resolves grants and `JwtServiceImpl` encodes a single flat `perm_bits`
bitset. A `LOCATION`-scoped assignment therefore grants the same authority everywhere as a
`GLOBAL` one. #1372 documented this; documenting it did not enforce it.

`pos-people.EmployeeLocationAssignment` carries `person_id → location_id` with `is_primary`,
`effective_from` / `effective_to` and `status`. It is populated, queried in production by
availability and staffing code, and already emits `PEOPLE_STAFFING_ASSIGNMENT_CREATE` /
`_UPDATE` / `_END` events plus `PeopleEventPublisher.publishStaffingAssignmentUpdated`.

### The Problem

Location scope is enforced **nowhere in the platform**. A spike against the current tree
(`durion-positivity-backend/docs/location-scope-effective-dating-spike-2026-09.md`) found
**77 endpoints across 15 modules** that accept a caller-supplied `locationId` and authorize it
with a flat permission carrying no location dimension. None validates the id against anything
the caller holds.

The one pattern cited as the existing answer does not close the hole. `WipController.listWip`
gates only the cross-location *widening* flag on `workorder:wip:view_all_locations`; in the
narrow case the client-supplied `locationId` is unchecked, so a technician at shop A reads
shop B's WIP board by changing a query parameter.

The scope model is also unexercised: zero `LOCATION`-scoped rows exist in any migration and
`role_assignment_scope_locations` is never populated, so nothing demonstrates even the
existing model working.

### Drivers

- Multi-location operation is a live near-term requirement, so "do nothing" is not available.
- INVENTORY_MANAGER and INVENTORY_CONTROLLER hold identical grants **by design** (#1373), on
  the stated understanding that `scope_type` distinguishes them. Because it does not, the two
  roles are indistinguishable in every code path — a correctness defect in the role model.
- Sustaining two divergent answers to "which locations does this person cover" is the
  two-model problem #1372 removed from `role_permissions`, reappearing one table over.

### Scope

`pos-security-service`, `pos-people`, `pos-api-gateway`, `pos-security-common`, and the 15
modules exposing location-parameterised endpoints. Amends [ADR-0040](0040-roles-jwt-permission-governance-policy.adr.md)
§2 (access-token claim contract). Relates to [ADR-0016](0016-location-entity-semantics.adr.md).

---

## Decision

### 1. Ownership of user → location reach

**Decision:** ✅ **Resolved** — `pos-people.EmployeeLocationAssignment` is the single source of
truth. `role_assignments.scope_type` and `role_assignment_scope_locations` are retired.

`role_assignments` keeps effective-dated user→role assignment. Scope leaves it entirely.

Rationale: the people model is populated, queried, effective-dated, event-published, and its
locations are resolvable — `ExtLocationReplica`, fed by `LocationEventsListener`, already gives
pos-people, pos-inventory, pos-invoice and pos-workorder an event-consistent view of locations.
pos-security-service has no location replica, and seeding a scoped assignment there would
require hardcoding pos-location's UUIDs — invisible cross-service coupling with no referential
guarantee, which is why the `felicia.grant` fixture was deferred during #1512.

This ends the condition #1375 set out to end — a scope model in the schema enforced nowhere —
by deleting it rather than by building a second enforcement path for it.

### 2. Token shape

**Decision:** ✅ **Resolved** — location reach travels as a new, **additive** `loc_scope` claim.
`perm_bits` semantics are unchanged and `CATALOG_VERSION` is **not** bumped.

`loc_scope` is one of:

- `"GLOBAL"` — unrestricted reach;
- a Base64URL string of concatenated raw 16-byte location UUIDs — the explicit reach set;
- `"DEFERRED"` — reach exceeds the configured cardinality cap; the service must ask
  `GET /v1/roles/check-permission`.

Measured (`scripts/measure-scope-claim-size.py`, reproducing `PermissionBitsetCodec` and the
`JwtServiceImpl` claim set): baseline token ≈ 650 B; +50 B at 1 location, 1 385 B at 25,
3 519 B at 100, 14 896 B at 500. Token size is near-independent of permission count and linear
in location count, so location cardinality is the only size risk. Initial cap: **64 locations**
(≈ 2 KB).

Rejected: a per-location permission bitset map. It reaches 86 011 B at 500 locations, breaching
`server.tomcat.max-http-header-size: 65536`, and it is the only encoding that forces the
`PermissionCode` / `GatewayPermissionCatalog` / `PermissionBitsetCodec` / `CATALOG_VERSION`
lockstep cost across 1 086 `@PreAuthorize` sites in 308 files.

Rejected: catalog encoding (`*_all_locations` twins). 357 of 445 parseable codes sit in
location-touching domains, so this more than doubles a 510-code catalog — and per the
`WipController` finding it still would not enforce the narrow case.

### 3. Enforcement boundary

**Decision:** ✅ **Resolved** — the owning service enforces the intersection; the gateway does not.

The gateway passes `loc_scope` through as `X-Loc-Scope` and strips inbound copies alongside the
existing identity headers. It has no domain knowledge of which request parameter denotes a
location, so it cannot make the decision. `pos-security-common` provides one shared helper
(`LocationScope.covers(locationId)`) so 77 endpoints do not each invent one.

Existing `@PreAuthorize` annotations are unchanged: they answer "may this caller do X", which
stays true. Scope answers "…here", a second check applied only where a `locationId` is accepted.

`GET /v1/roles/check-permission` is retained solely as the `DEFERRED` fallback. It is not the
general mechanism: nothing calls it today, and a synchronous pos-security-service hop on 77
endpoints would couple availability and add latency to the common path.

### 4. Effective dating

**Decision:** ✅ **Resolved** — clamp token lifetime, and revoke on assignment change.

- Issue `exp = min(now + 3600s, earliest effective_to among the assignments contributing to the
  token)`. Issuer-side only, no new infrastructure, and it bounds the stale window to zero.
- Revoke live tokens on staffing-assignment change via the existing `TokenRevocationManager` +
  `jwt_token` path, driven by the events pos-people already publishes. Blast radius is per-`jti`
  in Redis, so only the affected person's tokens are touched.
- **Not** a separate "not valid after" claim — it duplicates `exp` and every validator would
  need teaching.
- **Not** a shortened global TTL — it taxes every user to fix a case affecting assignment
  holders only.

Today's 3 600 s window is harmless because nothing reads scope. It stops being harmless the
moment `loc_scope` gates access, so this ships with §2, not after it.

### 5. Migration

**Decision:** ✅ **Resolved** — additive and per-module. No flag-day deploy.

Because `loc_scope` is a new claim rather than a change to `perm_bits`, a service that does not
read it behaves exactly as today. Order: issue the claim → pass it through the gateway → adopt
per module → remove the pos-security-service scope columns once no reader remains. Each step is
independently deployable and reversible.

---

## Alternatives Considered

| Alternative | Why not |
| --- | --- |
| Keep scope in `role_assignments`, enforce it | Needs a location replica and event listener in pos-security-service, seed fixtures that cannot reference pos-location's UUIDs without invisible coupling, and an admin surface duplicating staffing assignment. Leaves two divergent answers to the same question. |
| Per-location permission bitset map in the token | Breaches the 64 KB header cap at 500 locations; forces catalog-version lockstep across 1 086 `@PreAuthorize` sites. |
| Catalog encoding (`*_all_locations` twins) | Up to 357 new codes against a 510-code catalog, and does not enforce the narrow case (see `WipController`). |
| `check-permission` on every location-scoped endpoint | A synchronous security-service hop on 77 endpoints; availability coupling and latency on the common path. Retained only as the `DEFERRED` fallback. |
| Do nothing | Not available: multi-location is a live requirement, and #1373's role pair is already a correctness defect. |

---

## Consequences

### Positive ✅

- Location scope becomes enforceable for the first time; the INVENTORY_MANAGER /
  INVENTORY_CONTROLLER distinction becomes real.
- One source of truth for location reach, already populated and event-driven.
- No `CATALOG_VERSION` bump, no `perm_bits` change, no flag-day.
- Common-case token growth is ~50 B.

### Negative ⚠️

- 77 endpoints need a scope check added, module by module — the long tail of the work.
- pos-security-service takes a new dependency on pos-people's staffing events (mitigated: it
  already runs four `@KafkaListener`s, including a people-contact one).
- Token revocation becomes security-load-bearing, so Redis unavailability changes from a
  degradation to a security decision requiring an explicit fail-open/fail-closed policy.
- Deleting `role_assignments.scope_type` is destructive; it is safe only because no seed
  populates it and nothing enforces it, which must be re-verified at execution time.

### Neutral

- `is_primary` as an implied default location is a UX question left open.
- Location hierarchy — whether "covers L" should mean "covers L or an ancestor" given
  `LocationController.getDescendants` / `getAllChildren` — is left open; the claim carries ids
  either way.

---

## References

- Spike findings: `durion-positivity-backend/docs/location-scope-effective-dating-spike-2026-09.md`
- Measurement: `durion-positivity-backend/scripts/measure-scope-claim-size.py`
- [ADR-0040](0040-roles-jwt-permission-governance-policy.adr.md) — amended by §2
- [ADR-0016](0016-location-entity-semantics.adr.md) — location entity semantics
- `durion-positivity-backend/docs/rbac-permission-role-audit-2026-08.md`
- Issues #1375, #1372, #1373, #1499, #1512

---

## Sign-Off

| Role | Name | Date | Decision |
| --- | --- | --- | --- |
| Chief Architect | | | |
| Security & Authorization Domain | | | |
| People & Roles Domain | | | |

---

## Timeline

| Date | Event |
| --- | --- |
| 2026-08-19 | #1375 opened — spike requested |
| 2026-08-26 | Evidence added from the #1499/#1512 RBAC audit |
| 2026-09-07 | Spike executed; findings and this ADR proposed |
