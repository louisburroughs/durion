---
type: Plan
title: 'People Employee Register Backend Execution Plan'
description: 'Execution plan for the People employee register backend stories and DECISION-PEOPLE-017 amendment.'
created: 2026-09-22
updated: 2026-09-23
status: active
---
## Execution plan — People employee register backend (#2155–#2159)

**Covers:** `durion-positivity-backend` issues #2155, #2156, #2157, #2158, #2159, and #2160
(raised from this plan — the `pos-security-service` fact that #2155 depends on, §5).
**Date:** 2026-09-22 · **Revised** 2026-09-22 after reading the publisher side of
`people-contact.events.v1` and `security.events.v1` — see §4.
**Repos:** `durion-positivity-backend` (all code), `durion-positivity-frontend` (SDK regeneration)
**Design canvas:** https://claude.ai/artifact/PA1Xk14WRbzb9KP8Pnrz2K
**Modules:** `pos-people` (most of the work), `pos-security-service` (one new fact)
**Status:** **Wave 1 complete** on `claude/vigilant-pasteur-dux6jh` — #2156 (rename + endpoint),
#2157, #2158 and #2160 all implemented and pushed. `pos-people` runs 670 tests, 0 failures, 29
errors (all Testcontainers classes needing a Docker daemon the build environment lacks).

One item outstanding, needing a human: `RolePermissionBaselineTest.supportIsReadOnly` fails until
`('SUPPORT', 'people:jobRole:view')` is added to `R__seed_role_permissions.sql`. SUPPORT's grants
are derived, not declared — `SupportReadOnlyCeiling` admits every `:view`/`:read` the six floor
roles hold, and ADMIN is one of them, so granting the permission to anyone forces the SUPPORT row.
The seed's own comment makes widening SUPPORT a product decision.

Wave 2 (#2155) is next and is unblocked: #2160 has landed.

---

## 1. Shape of the work

Five stories behind one page: the employee register at `/app/people/employees`. They are not
independent — #2159 cannot be specified until #2156 settles a permission, #2155 cannot project a
field #2157 has not added, and #2155's roles column cannot exist until `pos-security-service`
publishes a fact it does not publish today.

```
   ┌──────────────────── Wave 1: parallel, disjoint modules/files ────────────────────┐
   │                                                                                  │
   A. #2157 jobRole   B. #2158 filter/sort   C. #2156 enable   D. #2160 role-assign   │
      (reference list)   (searchEmployees)      endpoint          fact (sec. svc)     │
        │                      │                    │                    │            │
        └───────────┬──────────┘                    │                    │            │
                    ▼                               │                    │            │
   ═══════ API Artifacts Sync (GATE — specs + both SDKs) ══════          │            │
                    ▼                               │                    │            │
           E. #2155 register projection ◄───────────┼────────────────────┘            │
              (replica joins + include=)            │                                 │
                    │                               │                                 │
                    └───────────────┬───────────────┘                                 │
                                    ▼                                                 │
                        F. #2159 allowedActions                                       │
                                    ▼                                                 │
                                  Done ◄─────────────────────────────────────────────┘
```

**Critical path:** #2160 → #2155 → #2159. Track D is the long pole — another module, another domain
owner — and #2155's roles column is blocked until it lands. **Start it first.**

**Dependency rationale**

| Story | Depends on | Why |
| ----- | ---------- | --- |
| #2157 jobRole | — | Independent. Its "include on the register projection" bullet is deferred into #2155. |
| #2158 filter/sort | — | Independent, but edits `EmployeeServiceImpl#searchEmployees`, which #2155 also edits. Serialized to avoid a conflict, not a logical dependency. |
| #2156 enable | — | Independent. New endpoint, new service method. |
| **#2160 role-assignment fact** | — | Independent, different module, different domain owner. Blocks #2155's roles column. |
| #2155 projection | #2157, #2158, #2160 | Projects `jobRole`; shares the search method; needs the roles fact to exist. |
| #2159 allowedActions | #2156, #2155 | Needs `ENABLE` to mean something, needs the register row shape to carry the field. |

---

## 2. Branches and PRs

Per `durion/CLAUDE.md` §Git Workflow — one branch and one PR per story, named
`feat/people-employee-register-<story>` (or `cap/<cap-id>-...` once these get a capability id).
#2160 gets its own branch and PR in `pos-security-service`.
Waves 1 and 2 are separate PRs so the `API Artifacts Sync` gate between them is unambiguous.

---

## 3. Wave 0 — decisions (blocks Wave 1)

| Story | Decision | Status |
| ----- | -------- | ------ |
| #2157 | Free text vs tenant-scoped reference list for `jobRole` | **Resolved** — tenant-scoped reference list ([#2157 comment](https://github.com/louisburroughs/durion-positivity-backend/issues/2157#issuecomment-5780495842)) |
| #2155 | How the register gets application roles | **Resolved** — new domain event from `pos-security-service` (#2160) + replica in `pos-people`. Not the REST fan-out the issue's cost table implies. |
| #2156 | One permission for both directions, or two | **Resolved** — one permission, **renamed to `people:employee:activation`** ([#2156 comment](https://github.com/louisburroughs/durion-positivity-backend/issues/2156#issuecomment-5781631118)). The rename is its own piece of work — §6 Track C. |
| #2156 | Concurrency token | **Resolved** — use `updatedAt`, and amend DECISION-PEOPLE-017 to accept any last-modified field ([#2156 comment](https://github.com/louisburroughs/durion-positivity-backend/issues/2156#issuecomment-5781656467)). Amendment applied to `durion/domains/people/.business-rules/AGENT_GUIDE.md` and `DOMAIN_NOTES.md`. |

---

## 4. What the cross-module events actually carry

Recorded here because #2155's cost table attributes the projection's expense to the wrong places,
and the first draft of this plan repeated the error. These are the facts, read from the publishers.

**`people-contact.events.v1`** — ten payload types in
`pos-domain-events/src/main/java/com/positivity/domainevents/peoplecontact/`, published by
`pos-people-contact` `internal/service/PeopleContactEventPublisher`. Two matter here:

```java
PersonUpdatedV1(personId, firstName, lastName, preferredName,
                List<ContactPointV1> contactPoints, PostalAddressV1 postalAddress,
                createdAt, updatedAt)
UserPersonLinkUpdatedV1(linkId, personId, username, status, ...)
```

`pos-people` already consumes both in `internal/service/PeopleContactEventsListener` into **two**
replicas: `ExtPersonReplica` (names, `primaryEmail`, `secondaryEmail`, `primaryPhone`,
`secondaryPhone`) and `ExtUserLinkReplica` (`linkId`, `personId`, `username`, `status`).

| Register column | Actually sourced from | Cost |
| --------------- | --------------------- | ---- |
| Names, status | `Employee` + `ExtPersonReplica` | local |
| Email, phone | `ExtPersonReplica` — already replicated | **local, no change** |
| Username | `ExtUserLinkReplica` — already replicated | **local, join on `personId`** |
| Job role | #2157, Track A | local once Track A lands |
| Location | staffing assignments, owned by `pos-people` | local |
| **Application roles** | **`pos-security-service`** — see below | **the only gap** |

**Application roles are not `pos-people-contact`'s to give.** `PeopleAccessControlService` is a REST
proxy: `PersonAccessController.listRoleAssignments(personUuid)` →
`SecurityServiceClient.getUserByUsername(...)` → `SecurityServiceClient.getUserRoleAssignments(userId)`.
Two calls, one service hop further out, per person.

**And `pos-security-service` publishes no role-assignment fact.** Its `security.events.v1` carries
`RolePersonaChangedV1` only (role *metadata*, for pos-mcp-server). `RoleAssignmentRevokedEvent`
(`internal/event/`) is an in-process Spring `ApplicationEvent`, not a Kafka fact.
`SECURITY_ROLE_ASSIGNMENT_CREATE` / `_REVOKE` in its `internal/config/EventTypes.java` are
`@EmitEvent` audit registrations to pos-event-receiver — an audit trail, not a consumable stream.

Hence Track D.

---

## 5. Track D (Wave 1) — #2160: role-assignment fact from `pos-security-service`

Raised from this plan as
[#2160](https://github.com/louisburroughs/durion-positivity-backend/issues/2160), against
`pos-security-service` and owned by the security domain. It is the critical path for #2155.

The in-repo exemplar is `internal/service/RolePersonaEventEmitter` — the same module already
publishes a fact to `security.events.v1` through `OutboxEventWriter.publish` (`MANDATORY`
propagation, so a fact cannot outlive a rolled-back write) wrapped in `DomainEventEnvelope.of(...)`.

1. `RoleAssignmentChangedV1` in `pos-domain-events/.../domainevents/security/`, carrying the
   assignment's current state rather than a delta, so a consumer that missed an earlier message
   still converges — same design note as `RolePersonaChangedV1`.
2. **Carry `username` in the payload, not just `userId`.** `RoleAssignment` binds a `User`
   (`internal/entity/RoleAssignment.java`, `@ManyToOne User` + `@ManyToOne Role`), but `pos-people`
   keys employees by `personId` and its `ExtUserLinkReplica` holds `personId` ↔ `username` with **no
   `userId`**. Without `username` in the payload the consumer cannot resolve the assignment to an
   employee without another cross-service call — which would defeat the whole exercise.
3. Payload shape: `assignmentId`, `userId`, `username`, `roleId`, `roleName`,
   `effectiveStartDate`, `effectiveEndDate`, `revokedAt`, `tenantId`.

   **No scope fields — this plan originally specified them in error.** `RoleAssignment` carries no
   location scope: the `scope_type` column and `role_assignment_scope_locations` table were dropped
   by V38 (#1875, folded into the flattened baseline), and location reach is now a property of
   `Role` (`locationScope`, `locationHierarchy`) combined with pos-people's staffing assignment
   (ADR-0061 §1). A consumer needing a role's location reach reads it off the role, not off this
   event.
4. Emit on assign and on revoke — `UserRoleGrantServiceImpl` is the write path;
   `RoleAssignmentTokenRevocationListener` shows where revokes are already observed in-process.
5. `RoleAssignment extends TenantScopedEntity`, so tenancy is already handled on the publisher side;
   the envelope must carry the tenant so the consumer's replica is scoped (ADR-0062).
6. Follow the exemplar's failure posture: a serialization failure must not take the role write with
   it.

**Acceptance:** assigning and revoking a role each produce exactly one fact on `security.events.v1`;
the payload resolves to a person without a callback; replaying a fact twice is a no-op.

---

## 6. Wave 1 — the three in-scope tracks

Disjoint files; work concurrently with Track D.

### Track A — #2157 `jobRole` as a tenant-scoped reference list

The skill registry is the in-repo exemplar:
`pos-people/src/main/resources/db/migration/V3__skill_registry.sql`,
`internal/entity/Skill.java`, `internal/service/SkillRegistryService.java`,
`internal/controller/SkillController.java`.

**One deliberate departure from that exemplar.** Skill is platform-global by design — no
`tenant_id`, no RLS, both tables listed in `src/main/resources/db/tenancy-global-tables.txt` —
because an ASE certification means the same thing in every shop. Job role is the opposite: "Lead
Technician" is a label a tenant chooses. So it is tenant-scoped per ADR-0062 — `tenant_id`, row-level
security, and **not** added to that whitelist.

1. `V6__job_role.sql` — tenant-scoped `job_role` table; `employee.job_role_id` nullable FK, scoped
   by tenant per `TENANCY_SCHEMA.md`'s add-a-table checklist. Existing rows default null, render `—`.
2. `JobRole` entity, repository, `JobRoleService` / `Impl`, `JobRoleController` (list + create),
   following the `Skill*` shapes.
3. `PeoplePermissions.JOBROLE_VIEW` (`people:jobRole:view`) and `JOBROLE_MANAGE`
   (`people:jobRole:manage`); register both in the permission registry.
4. `jobRole` on `EmployeeProfileDto`, `CreateEmployeeRequest`, `UpdateEmployeeRequest`.
5. `@EmitEvent` on the mutating job-role endpoints, ids in `internal/config/EventTypes.java`.

**Scope cut:** does not touch `searchEmployees`. The issue's "include it on the register projection"
bullet is delivered in Wave 2 — this is what keeps Tracks A and B from colliding.

**Acceptance (#2157):** `jobRole` round-trips create → update → read; it appears in no permission
evaluation path — it is HR master data and can never widen access; ADR-0042 OpenAPI annotations.

### Track B — #2158 status filter and sort

Cheaper than the issue implies. `EmployeeServiceImpl#searchEmployees`
(`pos-people/src/main/java/com/positivity/people/internal/service/EmployeeServiceImpl.java:241-267`)
already loads every employee with `findAll()` and filters, sorts and pages **in memory** — a
deliberate choice documented at line 242 (employee counts are small; same approach as `pos-customer`
`PartyServiceImpl#browseParties`). So the filter, the sort and the histogram are list operations over
data already in hand, with no new query work.

1. Extend to `searchEmployees(q, status[], sort, page, size)`.
2. Apply the status filter **before** `SEARCH_COMPARATOR` and before the window is taken — which
   makes `totalElements` reflect the filtered set for free, satisfying the main acceptance criterion
   without special handling.
3. `sort` — `lastName,asc|desc` at minimum; register default `lastName,asc`.
4. Ship the optional status histogram: a `groupingBy` over the same list. The issue marks it optional
   only because it assumed a per-status count query.
5. Leave a comment at the `findAll()` recording that Wave 2's enrichment must happen after
   windowing. See §9.

**Acceptance (#2158):** `?status=DISABLED` returns the tenant's disabled count in `totalElements`,
not the page's; `?sort=lastName,desc` orders across the whole result set; callers passing only
`q`/`page`/`size` are unchanged; ADR-0042 OpenAPI annotations.

### Track C — #2156 `POST /v1/people/employees/{employeeId}/enable`

Two pieces: a permission rename that reaches well beyond this module, and the endpoint itself. **Do
the rename first, as its own commit**, so the endpoint lands on the final permission name.

#### C1. Rename `people:employee:deactivate` → `people:employee:activation`

19 references across 8 modules. The permission carries **bit index 119**, and the bit must not move:
changing it would invalidate every issued token's bitset.

The schema makes this safe if done in the right order. `permissions` is keyed by `id` and
`role_permissions` references `permission_id`, **not** the name — so renaming the row in place
preserves every existing grant automatically.

The trap is the repeatable seed. `R__seed_reference_security.sql` upserts with `ON CONFLICT (name)`,
so editing the name there alone would **insert a second row** rather than rename the first, leaving
the old permission in place, still granted, and two rows contending for bit 119.

1. `V8__rename_employee_deactivate_to_activation.sql` — `UPDATE permissions SET name =
   'people:employee:activation', action = 'activation' WHERE name = 'people:employee:deactivate'`.
   Keeps id `a30123b1-473f-f626-ae28-bb94202f2e8a`, keeps bit 119, keeps every grant.
2. Then update the repeatable seeds so they stop re-inserting the old name:
   `R__seed_reference_security.sql`, `R__seed_role_permissions.sql` (three sites).
3. `PermissionCode.PEOPLE__EMPLOYEE__DEACTIVATE` → `PEOPLE__EMPLOYEE__ACTIVATION`, bit 119 unchanged.
4. `PeoplePermissions.EMPLOYEE_DEACTIVATE` → `EMPLOYEE_ACTIVATION`; `@PreAuthorize` on
   `disableEmployee`; the `scopes = {...}` in its OpenAPI annotation.
5. Catalogs: `GatewayPermissionCatalog`, `DownstreamPermissionCatalog` (both `PERM_`-prefixed),
   `.github/permissions/permissions_v2.yml`, `pos-people/src/main/resources/permissions.yaml`.
6. Tests: `pos-people` and **`pos-people-contact`** `BaseIntegrationTest` authority lists.
7. Generated artifacts (`pos-people/openapi.yaml`, `docs/permissions-report.yaml`,
   `pos-security-service/docs/permissions-aggregate.yaml`) regenerate — do not hand-edit.
8. Per pre-production policy, this is a clean rename, not a deprecation. Do **not** use the
   `deprecated` / `superseded_by` columns to keep the old name alive.
9. **Check the frontend** for the literal `people:employee:deactivate` — that repo is outside this
   plan's session scope and was not searched.

#### C2. The endpoint

Mirror `disableEmployee` — controller `internal/controller/EmployeeController.java:273`, service
`internal/service/EmployeeServiceImpl.java:198`.

1. `enableEmployee`. Guards, all `ResourceStateConflictException` (409) per ADR-0017 §2, matching how
   disable already distinguishes stateful collisions from request-shape validation:
   - `TERMINATED` → 409 naming the terminal state (DECISION-PEOPLE-001: irreversible)
   - `ON_LEAVE` / `SUSPENDED` → 409 pointing at the profile update; those carry dates and a reason
     the register's switch does not collect
   - only `DISABLED` proceeds → `ACTIVE`, with a fresh `statusEffectiveAt`
2. `updatedAt` as the concurrency token; 409 on mismatch; named as the token in the OpenAPI
   description, per the amended DECISION-PEOPLE-017.
3. Publish the reactivation counterpart of the disable saga so the replicas notified by #2119 / #2121
   converge without a manual replay.
4. `@EmitEvent(id = "PEOPLE_EMPLOYEE_ENABLE", apiVersion = "1")`, registered in
   `internal/config/EventTypes.java` with the `write` preset.
5. `@PreAuthorize("hasAuthority('" + PeoplePermissions.EMPLOYEE_ACTIVATION + "')")`; ADR-0042
   OpenAPI annotations including `x-required-permissions`.

**Acceptance (#2156):** `DISABLED → ACTIVE` succeeds and restores authentication;
`TERMINATED → ACTIVE` is 409, never 200; downstream consumers observe the reactivation without a
manual replay.

### Gate — API Artifacts Sync

Tracks A, B and C all change controllers, DTOs or annotations, so per `CLAUDE.md` the sync runs once
after they land, not once per track:

```bash
gh workflow run api-artifacts-sync.yml --repo louisburroughs/durion-positivity-backend \
  --ref <branch> -f modules="pos-people"
```

The frontend needs this SDK to start on the register, which is why the gate sits here. Note that
C1 changes a permission, which per `CLAUDE.md` independently obliges an `API Artifacts Sync` run —
this gate covers it.

---

## 7. Wave 2 — #2155 register projection

With §4's facts, this story is smaller than written in every respect but one.

1. **Roles replica** (needs #2160): `V7__ext_role_assignment_replica.sql` +
   `ExtRoleAssignmentReplica` + a `SecurityEventsListener` consuming `security.events.v1`, modelled
   on the existing `PeopleContactEventsListener` — idempotent via `processed_events` in the upsert
   transaction, transient DB errors rethrown for retry/DLQ, malformed payloads logged and skipped,
   `aggregateVersion` as an LWW hint. Join to the employee by `username` via `ExtUserLinkReplica`.
2. **No other replica work.** Email, phone and username are already local (§4). The first draft of
   this plan wrongly called for adding `username` to `ExtPersonReplica`; it is already on
   `ExtUserLinkReplica`.
3. Widen `EmployeeSummaryDto` with `username`, `contactInfo`, `roleAssignments[]`, `primaryLocation`
   plus `otherLocationCount` (DECISION-PEOPLE-004 — the page renders `Charlotte Main · +1 more`), and
   `jobRole` from Track A.
4. `include=` query parameter; default omits every new field, so `HrFacadeTool.searchEmployees` keeps
   its thin payload untouched.
5. **Enrich the 25-row window only, never the full `findAll()` list.** See §9.
6. Role assignments default to active-only (DECISION-PEOPLE-026). **Scope does not come from the
   assignment** — `RoleAssignment` has no scope column at all; it holds a user, a role, effective
   dates and revocation. Scope is an attribute of the *role*, per the ADR-0061 ADR-0062 amendment
   §1: "`location_scope` and `location_hierarchy` are attributes of a tenant's role row."
   **#2160 carries it.** `RoleAssignmentChangedV1.roleLocationScope` is sourced in
   `RoleAssignmentEventEmitter` from `assignment.getRole().getLocationScope()`, and
   `ExtRoleAssignmentReplica.roleLocationScope` stores it — so the register's roles column can show
   scope (DECISION-PEOPLE-003) off the replica with no second lookup and no call into
   pos-security-service. This closes the gap an earlier draft left open by deferring the decision.
   `Role.locationHierarchy` is deliberately *not* carried: it selects which parent dimension a
   `LOCATION` role is evaluated along (ADR-0061 §2, `FINANCIAL` vs `OTHER`), which is an input to
   the authorization decision, not something the register displays. Adding it later is a new field
   on the event, not a change to this one.
7. PII gating: omit `email` / `phone` for a caller without `people:employee_pii:view` and return 200,
   never 403 — per #1898, where conflating `people:employee:view` with PII access exposed home
   addresses and emergency contacts to twelve roles including TECHNICIAN.

**Acceptance (#2155):** one request renders a 25-row page with every column; a caller with
`people:employee:view` but not `people:employee_pii:view` gets rows with `email`/`phone` absent and a
200; a caller without `people-contact:role:view` gets rows with `roleAssignments` absent;
`HrFacadeTool.searchEmployees` is unaffected.

> **Note on the roles-permission criterion — OPEN, and currently unenforced.** The issue gates the
> roles column on `people-contact:role:view`. Under #2160 the data no longer comes from
> `pos-people-contact`, so either that permission is re-homed as a `pos-people` check over the
> replica, or the criterion names the security-domain permission instead. #2160's payload is now
> settled, so this is ready to decide — and it needs deciding, because **as built the column is not
> gated at all**: `searchEmployees` carries only
> `@PreAuthorize(hasAuthority(people:employee:view))`, so any caller who can view employees can
> pass `?include=ROLE_ASSIGNMENTS` and read every employee's role assignments. That is the precise
> shape of #1898, which this plan already cites two items above — a structural view permission
> being taken as permission for the sensitive payload behind it. The acceptance criterion below
> ("a caller without `people-contact:role:view` gets rows with `roleAssignments` absent") is
> therefore **not met**. Whichever permission name wins, the check has to exist before #2155
> closes; picking one is a naming decision, leaving it ungated is a defect.

---

## 8. Wave 3 — #2159 `allowedActions`

Now buildable: `ENABLE` has an endpoint, its permission is settled, the row shape exists.

1. An `AllowedAction` enum — `VIEW_PII`, `UPDATE`, `DISABLE`, `ENABLE`. Typed rather than free
   strings, so the generated SDK carries the vocabulary and adding a value is a visible contract
   change rather than silent string drift.
2. A single `EmployeeActionPolicy` computing the list from `(authorities, status)`. One class, so the
   transition table lives in exactly one place — the entire point of the story.
3. The field on both `EmployeeProfileDto` and the widened summary row.
4. The backend continues to enforce independently. The flags are a rendering hint and never the gate;
   say so in the OpenAPI description so no future reader mistakes them for authorization.

**Correction to the issue's acceptance criteria.** As written they cover `ACTIVE`, `DISABLED` and
`TERMINATED` only. But disable requires status *exactly* `ACTIVE`
(`EmployeeServiceImpl.java:211-215`: one guard rejects `DISABLED`/`TERMINATED`, a second rejects
everything that is not `ACTIVE`). So `DISABLE` must also be absent for `ON_LEAVE` and `SUSPENDED`.
The full expected matrix, for a caller holding the activation permission:

| Status | `DISABLE` | `ENABLE` |
| ------ | --------- | -------- |
| `ACTIVE` | yes | no |
| `DISABLED` | no | yes |
| `ON_LEAVE` | no | no |
| `SUSPENDED` | no | no |
| `TERMINATED` | no | no |

A caller without the permission gets neither, for every status.

**Open question to settle in this wave: scope-awareness.** The issue says flags are computed from the
caller's permissions and the record's state, and is silent on location scope — but scope is real in
this module (`StaffingAssignmentServiceImpl`, `PeopleAvailabilityServiceImpl`,
`WorkSessionAccessPolicy`). If a location-scoped caller sees an employee outside their locations,
should `DISABLE` appear? **Recommendation: keep the flags permission-and-status only for now and
document that limitation explicitly in the OpenAPI description.** Making them scope-aware reintroduces
per-row assignment lookups and undoes Wave 2's design. If scope gating is wanted it is a follow-up
story, not a line item here.

---

## 9. Risks

| Risk | Severity | Mitigation |
| ---- | -------- | ---------- |
| #2160 not scheduled — another module, another domain owner | **High** — it is the critical path; #2155's roles column cannot ship without it | Raised as #2160. Confirm the security domain picks it up before Wave 1 starts. |
| #2160's payload omits `username`, carrying only `userId` | **High** — `pos-people` has no `userId` anywhere; the consumer would need a cross-service call per row, defeating the design | §5 step 2. Fix it in the payload review, not after the replica is built. |
| Wave 2 enrichment applied before windowing turns an O(n) in-memory scan into O(n) joins per request | **High** — would make the register slower than the 100-request version it replaces | Enrich the window only; the comment left at `findAll()` in Track B is the guard. Test asserting enrichment count equals page size, not total. |
| Permission rename applied to the repeatable seed without the versioned migration first | **High** — `ON CONFLICT (name)` inserts a second row instead of renaming; the old permission survives, still granted, and two rows contend for bit 119 | §6 C1 step 1. The `V8__` migration is not optional and must precede the seed edits. |
| Bit index 119 moved during the rename | **High** — invalidates the bitset in every issued token | Keep `bit_index` untouched; the rename is a name change only. |
| Frontend references the old permission literal | Medium | §6 C1 step 9 — that repo was outside this session's scope and was not searched. Check before merging C1. |
| Roles replica lag shows a stale role on the register | Low | Already the accepted tradeoff for names — `EmployeeSummaryDto.firstName` is documented as null "when the replica has not caught up". Same treatment, same documentation. Reconciliation is mandatory per ADR-0044 §4. |
| SDK drift between waves | Low | `API Artifacts Sync` after Wave 1 and again after Wave 3. |
| Tracks A and B conflict in `EmployeeServiceImpl` | Low | Track A's scope cut — it does not touch `searchEmployees`. |

---

## 10. Verification

Per module conventions in `durion-positivity-backend/CLAUDE.md`:

```bash
./mvnw spotless:apply
./mvnw -pl pos-people -am test
./mvnw -pl pos-people -am verify                             # *IT.java contract tests
./mvnw -pl pos-people-contact -am test                       # C1 touches its BaseIntegrationTest
./mvnw -pl pos-security-service -am test                     # #2160
./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test     # after Track A's new packages
```

Per-track evidence:

- **A** — `jobRole` round-trips create → update → read; new tables carry `tenant_id` and RLS and are
  absent from `src/main/resources/db/tenancy-global-tables.txt`.
- **B** — `?status=DISABLED&page=1` returns the tenant's disabled count in `totalElements`;
  `?sort=lastName,desc` verified across a two-page result, not within one.
- **C1** — after migration, exactly one `permissions` row matches `people:employee:%activation%`,
  it holds bit 119 and id `a30123b1-…`, and its grant count equals the pre-rename count for
  `people:employee:deactivate`. No row named `people:employee:deactivate` remains.
- **C2** — all five statuses exercised against `/enable`; a stale `updatedAt` is 409; the
  reactivation event observed downstream.
- **D (#2160)** — assign and revoke each emit exactly one fact; payload resolves to a person with no
  callback; double-apply is a no-op.
- **Wave 2** — a 25-row page renders every column in one request; the PII-less caller gets 200 with
  fields absent; enrichment count asserted equal to page size.
- **Wave 3** — the full status × permission matrix in §8, parameterised.

---

## 11. Sequencing note

Two independent short paths, if the full plan is too long to wait for:

- **#2160 first, always.** It is the longest lead and blocks the most.
- **Track B alone (#2158) makes today's eight-column register correct.** Without it the page's status
  filter and its counts are wrong in a way that looks right — filtering to "Disabled" shows the
  disabled rows out of page 1's 25, not out of all 214. Everything after Track B adds columns;
  Track B fixes a lie.
