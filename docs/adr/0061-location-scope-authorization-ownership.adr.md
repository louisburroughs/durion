# ADR-0061: Location Scope Authorization — Ownership, Token Shape, and Effective Dating

**Status:** PROPOSED — §2 dimension decision outstanding
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

### 1. Ownership of location scope

**Decision:** ✅ **Resolved** — scope is split between two owners, and
`role_assignments.scope_type` / `role_assignment_scope_locations` are retired.

| Question | Owner |
| --- | --- |
| Is this role location-scoped? | pos-security-service — new `roles.location_scope` (`ALL` \| `LOCATION`) |
| Which node(s) is this employee assigned to? | pos-people — `employee_location_assignment` |
| What lies beneath a node? | pos-location — the hierarchy, replicated as a materialised ancestor set |

`role_assignments` keeps effective-dated user→role assignment. Scope leaves it entirely.

**Why the discriminator belongs on the role.** #1373's INVENTORY_MANAGER /
INVENTORY_CONTROLLER pair holds identical grants on purpose; the roles differ by *reach*, not
by *grants*. One column on `roles` expresses that once, where the existing seed comment already
says the distinction lives. Encoding it per permission (`*_all_locations` twins) would say the
same thing up to 357 times.

**Why the location itself belongs to pos-people.** That model is populated, effective-dated,
event-published, and its locations are resolvable — `ExtLocationReplica`, fed by
`LocationEventsListener`, already gives pos-people, pos-inventory, pos-invoice and pos-workorder
an event-consistent location view. pos-security-service has no location replica, and seeding a
scoped assignment there would require hardcoding pos-location's UUIDs — invisible cross-service
coupling with no referential guarantee, which is why the `felicia.grant` fixture was deferred
during #1512.

**Scope is assigned to a location node and covers that node and every descendant.** A role
assigned at HQ or Region level can call location-scoped APIs targeting any child location
beneath it. This is what supplies the middle management tier: without it, reach is one shop or
everything, and a manager over some-but-not-all shops has no representation.

An employee may hold more than one assigned node, for coverage that does not fit a single
subtree, but hierarchy is expected to carry the normal case so counts stay at one or two.

**Runtime access only.** Assignment at a parent confers the right to *call* location-scoped APIs
against descendants. It confers no administrative right to grant scope to others; scoped
delegation is a separate concern on the role-assignment surface and is out of scope for this ADR.

This ends the condition #1375 set out to end — a scope model in the schema enforced nowhere —
by deleting it rather than by building a second enforcement path for it.

### 2. Token shape

**Decision:** ✅ **Resolved** — two new **additive** claims. `perm_bits` semantics are
unchanged and `CATALOG_VERSION` is **not** bumped.

| Claim | Value |
| --- | --- |
| `perm_bits` | unchanged — every permission the caller holds |
| `loc_bits` | the subset of `perm_bits` granted *only* by `LOCATION`-scoped roles |
| `loc_scope` | discriminated: `"ALL"`, or the assigned node ids; omitted when `loc_bits` is empty |

`loc_bits` reuses `PermissionBitsetCodec` and the same bit indexes as `perm_bits`, so it is
covered by the existing `perm_ver` and needs no catalog version bump.

**Enforcement rule.** At an endpoint checking permission `P` for location `L`:
`P ∉ loc_bits` → allow (grant is global); else `loc_scope == ALL` → allow; else
`loc_scope ∩ ancestors(L) ≠ ∅` → allow; else deny. `ancestors(L)` is the materialised ancestor
set of `L` **inclusive of `L`**, so a directly assigned node matches without a special case.

**The hierarchy is multi-dimensional, and scope must name its dimension.** `Location` holds
`Set<LocationParent> parents`, unique on `(child_id, parent_type)` — several overlapping trees,
one parent per dimension. `ParentType` has seven values (`HOME_OFFICE`, `HEADQUARTERS`,
`REGION`, `DISTRICT`, `PHYSICAL`, `ORGANIZATIONAL`, `FINANCIAL`); both traversal APIs take one
and `getDescendantsDto` defaults to `PHYSICAL`. "Covers descendants" is undefined until the
authorization dimension is chosen, and the default is wrong for this purpose — `FINANCIAL`
would grant reach along a reporting line, `PHYSICAL` along a building's geography, and the union
of all seven is the broadest possible reading. **This decision is deferred and blocks
enforcement; it is not settled by this ADR.**

**Hierarchy is evaluated at check time, never expanded at issuance.** The token carries the
assigned node, not its members, so a Region manager holds one id whether the region has 3 shops
or 300. `ExtLocationReplica` gains the ancestor set, fed by the existing `LocationEventsListener` —
new work in every module: only pos-people, pos-invoice and pos-workorder replicate locations at
all (18 of the 77 endpoints), pos-inventory's `ExtStorageLocationReplica` models intra-site
storage rather than the site hierarchy, and **no replica carries a parent link today**.
Two deliberate consequences: the claim stays small, and hierarchy edits take effect on the next
request with no token re-issue — correct for an org-chart change, but it makes hierarchy edits
security-relevant operations needing tight permissions and an audit trail. pos-location must
guarantee acyclicity or ancestor materialisation does not terminate — `StorageLocationServiceImpl`
has `wouldCreateCycle` / `existsCycleForParent`, but `LocationServiceImpl` has no equivalent.

**Two claims rather than one** because a user may hold both a `LOCATION`-scoped and an
`ALL`-scoped role. A single user-level flag would let one global role silently widen every
location-scoped role the same user holds. `loc_bits` keeps the distinction per permission.

**Union semantics.** A permission granted by both a `LOCATION` role and an `ALL` role is
global — its bit is not set in `loc_bits`. The broader grant wins, consistent with how
`perm_bits` already composes.

**Fail closed.** A caller holding `LOCATION`-scoped roles with no resolvable assigned node
gets `loc_bits` set and `loc_scope` absent, which denies. Absence must never widen to
unrestricted reach. `V3__backfill_primary_location_assignments.sql` records that employees with
several active assignments and no primary exist and are "genuinely ambiguous" — that population
is exactly this case.

**Measured** (`scripts/measure-scope-claim-size.py`, reproducing `PermissionBitsetCodec` and the
`JwtServiceImpl` claim set). Baseline ≈ 650 B; one assigned node **857 B**, a **+204 B** delta
and **1.31%** of the 65 514 B header budget. Additional nodes: 2 → 909 B, 4 → 1 013 B,
8 → 1 221 B, 16 → 1 637 B. `loc_bits` is bounded by the catalog at 86 characters; `loc_scope`
grows only with assigned-node count, which hierarchy keeps at 1–2.

A cap of ~8 assigned nodes is adopted as an **assertion that the hierarchy was modelled
correctly**, not as a size limit. Exceeding it surfaces as a configuration error; there is no
`DEFERRED` state and no size-driven runtime fallback.

Rejected: **expanding the hierarchy at issuance**. Putting a Region's member shops in the token
reintroduces the bloat this design avoids — 1 385 B at 25 shops, and a per-location bitset map
would reach 86 011 B at 500, breaching `server.tomcat.max-http-header-size: 65536`.

Rejected (deferred): **encoding locations as a bitset**, indexed the way `PermissionCode` is. A
location bitset costs `maxIndex/6` characters *regardless of coverage*, so it couples every
user's token size to the platform's total location count — opening the 5 000th shop makes a
single-shop technician's token ~834 characters heavier — whereas an id list costs ~22 characters
per node and scales with that user's actual assignment. At the 1–2 nodes hierarchy produces, the
id list wins outright. `PermissionCode` gets away with a bitset because it is a compile-time enum
under `CATALOG_VERSION` lockstep; locations are runtime business data, so a location bitset would
need an append-only, replicated index registry — a permanent distributed invariant — to compress
something hierarchy already compresses. `loc_scope` is discriminated, so this remains adoptable
later without a version bump if measured cardinality ever justifies it.

Rejected: **catalog encoding** (`*_all_locations` twins). 357 of 445 parseable codes sit in
location-touching domains, so this more than doubles a 510-code catalog; per the `WipController`
finding it still would not enforce the narrow case; and it is strictly worse than
`roles.location_scope`, which states the same distinction once per role.

### 3. Enforcement boundary

**Decision:** ✅ **Resolved** — the owning service enforces the intersection; the gateway does not.

The gateway passes both claims through as `X-Loc-Bits` and `X-Loc-Scope` and strips inbound
copies alongside the existing identity headers. It has no domain knowledge of which request
parameter denotes a location, so it cannot make the decision. `pos-security-common` provides one
shared helper (`LocationScope.covers(permission, locationId)`) so 77 endpoints do not each
invent one.

Existing `@PreAuthorize` annotations are unchanged: they answer "may this caller do X", which
stays true. Scope answers "…here", a second check applied only where a `locationId` is accepted.

`GET /v1/roles/check-permission` is **retired**. Because hierarchy keeps assigned-node counts at
1–2 the claim always fits, so there is no `DEFERRED` case and no size-driven fallback, so nothing in this design calls it — and nothing outside
pos-security-service calls it today either. Leaving it in place would preserve a third,
unused way to ask an authorization question. Removal is folded into the cleanup issue.

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

Because `loc_bits` and `loc_scope` are new claims rather than a change to `perm_bits`, a service
that does not read them behaves exactly as today. Order: add `roles.location_scope` and the replicated ancestor set → issue
the claims → pass them through the gateway → adopt per module → remove the pos-security-service scope columns and
`check-permission` once no reader remains. Each step is
independently deployable and reversible.

---

## Alternatives Considered

| Alternative | Why not |
| --- | --- |
| Keep scope in `role_assignments`, enforce it | Needs a location replica and event listener in pos-security-service, seed fixtures that cannot reference pos-location's UUIDs without invisible coupling, and an admin surface duplicating staffing assignment. Leaves two divergent answers to the same question. |
| `location_scope` on the employee rather than the role | Reach is a property of what a role authorises, not of who holds it. #1373's identical-grant role pair differs by reach; an employee-level flag cannot express that a person is global in one role and confined in another. |
| A single user-level scope flag (any `ALL` role wins) | Smallest claim (+68 B), but over-grants: one global role silently widens every location-scoped role the same user holds. Rejected on correctness, not size. |
| Forbid users from holding both `ALL` and `LOCATION` roles | Keeps the claim simple, but constrains role design for an implementation convenience and needs a migration sweep for users who already mix. |
| Expanding a parent assignment into member shops at issuance | Reintroduces token bloat (1 385 B at 25 shops) and makes hierarchy edits require token re-issue rather than taking effect on the next request. |
| Encoding locations as a bitset (indexed like `PermissionCode`) | Costs `maxIndex/6` chars regardless of coverage, coupling every user's token size to the platform's total location count; needs an append-only replicated location-index registry, a permanent distributed invariant, to compress what hierarchy already compresses. Deferred behind the `loc_scope` discriminator, not refused. |
| Per-location permission bitset map | 86 011 B at 500 locations, breaching the 64 KB header cap; forces catalog-version lockstep across 1 086 `@PreAuthorize` sites. |
| Catalog encoding (`*_all_locations` twins) | Up to 357 new codes against a 510-code catalog; does not enforce the narrow case (see `WipController`); says once per permission what `roles.location_scope` says once per role; cannot express hierarchy at all. |
| `check-permission` on every location-scoped endpoint | A synchronous security-service hop on 77 endpoints; availability coupling and latency on the common path. |
| Do nothing | Not available: multi-location is a live requirement, and #1373's role pair is already a correctness defect. |

---

## Consequences

### Positive ✅

- Location scope becomes enforceable for the first time; the INVENTORY_MANAGER /
  INVENTORY_CONTROLLER distinction becomes real, expressed by one column rather than by
  duplicate permission codes.
- One source of truth for each half: the role says whether, pos-people says where.
- No `CATALOG_VERSION` bump, no `perm_bits` change, no flag-day.
- Token growth is bounded — +204 B for one assigned node, 1.31% of the header budget — and
  independent of how many locations lie beneath that node, because hierarchy is evaluated at
  check time rather than expanded into the token.
- Middle management is expressible: a Region or HQ assignment reaches every location beneath it.
- Retiring `check-permission` removes a third, unused authorization path.

### Negative ⚠️

- 77 endpoints need a scope check added, module by module — the long tail of the work.
- pos-security-service takes a new dependency on pos-people's staffing events (mitigated: it
  already runs four `@KafkaListener`s, including a people-contact one).
- Token revocation becomes security-load-bearing, so Redis unavailability changes from a
  degradation to a security decision requiring an explicit fail-open/fail-closed policy.
- Deleting `role_assignments.scope_type` is destructive; it is safe only because no seed
  populates it and nothing enforces it, which must be re-verified at execution time.
- Hierarchy edits become security-relevant: moving a shop under a different Region changes who
  can see it on the next request, with no token re-issue. Needs tight permissions and an audit
  trail on the location tree.
- `ExtLocationReplica` must carry a materialised ancestor set, and pos-location must guarantee
  acyclicity at the `Location` level (it does not today). This is new replication work in
  **every** module on the critical path: 3 modules replicate locations, none with a parent link,
  and 35 of the 77 endpoints sit in modules with no location replica at all.

### Neutral

- `is_primary` as an implied default location for endpoints that currently require an explicit
  `locationId` is a UX question left open.
- **Scoped administration** — whether assignment at a parent should also confer the right to
  *grant* scope within that subtree — is a separate concern on the role-assignment surface, and
  is where privilege escalation would live. Not addressed here.
- **Node granularity for irregular coverage.** Multi-node assignment handles a set that does not
  fit one subtree. Whether such cases should instead get a dedicated group node — keeping
  assignment at a single node — is a modelling question left open. The multi-dimensional model
  offers a third option: a second `LocationParent` row on a different `parent_type`.
- **Which `ParentType` dimension(s) authorization traverses** is deferred and blocks enforcement.

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
