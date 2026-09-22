# Execution plan — People employee register backend (#2155–#2159)

**Covers:** `durion-positivity-backend` issues #2155, #2156, #2157, #2158, #2159
**Date:** 2026-09-22
**Repos:** `durion-positivity-backend` (all code), `durion-positivity-frontend` (SDK regeneration only)
**Design canvas:** https://claude.ai/artifact/PA1Xk14WRbzb9KP8Pnrz2K
**Module:** `pos-people` (plus a replica extension fed from `pos-people-contact`)
**Status:** Not started — Wave 0 decisions outstanding.

---

## 1. Shape of the work

Five stories behind one page: the employee register at `/app/people/employees`. They are not
independent — #2159 cannot be specified until #2156 settles a permission, and #2155 cannot project a
field #2157 has not added yet.

```
   ┌──────────── Wave 1: parallel, disjoint files ────────────┐
   │                                                          │
   A. #2157 jobRole      B. #2158 filter/sort    C. #2156 enable endpoint
      (reference list)      (searchEmployees)       (POST /{id}/enable)
        │                        │                        │
        └────────────┬───────────┘                        │
                     ▼                                    │
   ═══════ API Artifacts Sync (GATE — specs + both SDKs) ══════
                     ▼                                    │
            D. #2155 register projection                  │
               (replica extension + include=)             │
                     │                                    │
                     └──────────────┬─────────────────────┘
                                    ▼
                        E. #2159 allowedActions
                                    ▼
                                  Done
```

**Critical path:** A → D → E, with C joining before E. B is on the critical path only because it
edits the same method as D; it is otherwise free-standing and is the single cheapest change that
makes today's register correct.

**Dependency rationale**

| Story | Depends on | Why |
| ----- | ---------- | --- |
| #2157 jobRole | — | Independent. Its "include on the register projection" bullet is deferred into #2155. |
| #2158 filter/sort | — | Independent, but edits `EmployeeServiceImpl#searchEmployees`, which #2155 also edits. Serialized to avoid a conflict, not a logical dependency. |
| #2156 enable | — | Independent. New endpoint, new service method. |
| #2155 projection | #2157, #2158 | Projects `jobRole`; shares the search method and its signature. |
| #2159 allowedActions | #2156, #2155 | Needs `ENABLE` to mean something, needs the register row shape to carry the field. |

---

## 2. Branches and PRs

Per `durion/CLAUDE.md` §Git Workflow — one branch and one PR per story, named
`feat/people-employee-register-<story>` (or `cap/<cap-id>-...` once these get a capability id).
Waves 1 and 2 are separate PRs so the `API Artifacts Sync` gate between them is unambiguous.

---

## 3. Wave 0 — decisions (blocks Wave 1)

Three of the four are unresolved and two of them change acceptance criteria already written into the
issues. They need answering before code, not during.

| Story | Decision | Status |
| ----- | -------- | ------ |
| #2157 | Free text vs tenant-scoped reference list for `jobRole` | **Resolved** — tenant-scoped reference list ([#2157 comment](https://github.com/louisburroughs/durion-positivity-backend/issues/2157#issuecomment-5780495842)) |
| #2156 | `people:employee:deactivate` vs a new `people:employee:reactivate` | Open — #2159's acceptance criteria assume the former |
| #2156 | Concurrency token: `EmployeeProfileDto` exposes `updatedAt`, DECISION-PEOPLE-017 names `lastUpdatedStamp` | Open |
| #2155 | Widen `EmployeeSummaryDto` behind `include=` vs a new `GET /v1/people/employees/register` | Open |

**Recommendations**

- **#2156 permission — reuse `people:employee:deactivate`.** It is the symmetric authority over the
  same `ACTIVE ⇄ DISABLED` edge, and it keeps #2159's acceptance criteria correct as written. A new
  permission costs a bit assignment and a seed migration for no additional reach control, since any
  role that may disable an employee may sensibly restore one.
- **#2156 token — use the existing `updatedAt`.** Adding a second timestamp field whose value always
  equals an existing one is a contract cost with no behavioural gain. Satisfy DECISION-PEOPLE-017's
  intent (client submits the token, server returns 409 on mismatch) and say explicitly in the
  OpenAPI description that `updatedAt` is that token.
- **#2155 shape — widen `EmployeeSummaryDto` behind `include=`.** A separate `/register` endpoint
  duplicates #2158's filter, sort and pagination work, and leaves `HrFacadeTool.searchEmployees`
  pointing at an endpoint the register no longer uses. The `include=` parameter already satisfies
  the issue's own requirement that existing thin-payload callers are unaffected.

---

## 4. Wave 1 — three parallel tracks

The three tracks touch disjoint files and can be worked concurrently.

### Track A — #2157 `jobRole` as a tenant-scoped reference list

The skill registry is the in-repo exemplar for a reference list in this module:
`pos-people/src/main/resources/db/migration/V3__skill_registry.sql`,
`internal/entity/Skill.java`, `internal/service/SkillRegistryService.java`,
`internal/controller/SkillController.java`.

**One deliberate departure from that exemplar.** Skill is platform-global by design — no
`tenant_id`, no RLS, and both its tables are listed in `src/main/resources/db/tenancy-global-tables.txt`, because an
ASE certification means the same thing in every shop. Job role is the opposite: "Lead Technician" is
a label a tenant chooses. It is therefore tenant-scoped per ADR-0062 — `tenant_id` column, row-level
security, **not** added to `src/main/resources/db/tenancy-global-tables.txt`.

1. `V6__job_role.sql` — tenant-scoped `job_role` table; `employee.job_role_id` nullable FK, scoped
   by tenant per `TENANCY_SCHEMA.md`'s add-a-table checklist. Existing rows default null.
2. `JobRole` entity, `JobRoleRepository`, `JobRoleService` / `Impl`, `JobRoleController`
   (list + create), following the `Skill*` shapes.
3. `PeoplePermissions.JOBROLE_VIEW` (`people:jobRole:view`) and `JOBROLE_MANAGE`
   (`people:jobRole:manage`); register both in the permission registry.
4. `jobRole` on `EmployeeProfileDto`, `CreateEmployeeRequest`, `UpdateEmployeeRequest`; round-trip
   through create, update and read.
5. `@EmitEvent` on the mutating job-role endpoints, with ids in `internal/config/EventTypes.java`.

**Scope cut:** this track does not touch `searchEmployees`. The issue's "include it on the register
projection (#2155)" bullet is delivered in Wave 2, which is what keeps Track A and Track B from
colliding.

**Acceptance (from #2157):** `jobRole` round-trips; it appears in no permission evaluation path —
it is HR master data and can never widen access; ADR-0042 OpenAPI annotations.

### Track B — #2158 status filter and sort

Cheaper than the issue implies. `EmployeeServiceImpl#searchEmployees`
(`pos-people/src/main/java/com/positivity/people/internal/service/EmployeeServiceImpl.java:241-267`)
already loads every employee with `findAll()` and filters, sorts and pages **in memory** — a
deliberate choice documented at line 242 (employee counts are small; the same approach as
`pos-customer` `PartyServiceImpl#browseParties`). So the filter, the sort and the histogram are all
list operations over data already in hand, with no new query work.

1. Extend the signature to `searchEmployees(q, status[], sort, page, size)`.
2. Apply the status filter **before** `SEARCH_COMPARATOR` and before the window is taken — which
   makes `totalElements` reflect the filtered set for free, satisfying the issue's main acceptance
   criterion without special handling.
3. `sort` — `lastName,asc|desc` at minimum; default `lastName,asc`.
4. Ship the optional status histogram. It is a `groupingBy` over the same list; the issue marks it
   optional only because it assumed a per-status count query.
5. Leave a comment at the `findAll()` recording that Wave 2's enrichment must happen after
   windowing. This is the single most important note in the whole plan (see §7).

**Acceptance (from #2158):** `?status=DISABLED` returns the tenant's disabled count, not the page's;
`?sort=lastName,desc` orders across the whole result set; callers passing only `q`/`page`/`size` are
unchanged; ADR-0042 OpenAPI annotations.

### Track C — #2156 `POST /v1/people/employees/{employeeId}/enable`

Mirror `disableEmployee` — controller at
`internal/controller/EmployeeController.java:273`, service at
`internal/service/EmployeeServiceImpl.java:198`.

1. `enableEmployee` service method. Guards, all `ResourceStateConflictException` (409) per
   ADR-0017 §2, matching how disable already distinguishes stateful collisions from request-shape
   validation:
   - `TERMINATED` → 409 naming the terminal state (DECISION-PEOPLE-001: irreversible)
   - `ON_LEAVE` / `SUSPENDED` → 409 pointing at the profile update; those carry dates and a reason
     the register's switch does not collect
   - only `DISABLED` proceeds → `ACTIVE`, with a fresh `statusEffectiveAt`
2. Concurrency token per the Wave 0 decision; 409 on mismatch.
3. Publish the reactivation counterpart of the disable saga so the downstream replicas notified by
   #2119 / #2121 converge without a manual replay.
4. `@EmitEvent(id = "PEOPLE_EMPLOYEE_ENABLE", apiVersion = "1")`, registered in the module's
   `internal/config/EventTypes.java` registry with the `write` preset.
5. `@PreAuthorize` on the permission chosen in Wave 0; ADR-0042 OpenAPI annotations including
   `x-required-permissions`.

**Acceptance (from #2156):** `DISABLED → ACTIVE` succeeds and restores authentication;
`TERMINATED → ACTIVE` is 409, never 200; downstream consumers observe the reactivation without a
manual replay.

### Gate — API Artifacts Sync

All three tracks change controllers, DTOs or annotations, so per `CLAUDE.md` the sync runs once
after Wave 1 lands, not once per track:

```bash
gh workflow run api-artifacts-sync.yml --repo louisburroughs/durion-positivity-backend \
  --ref <branch> -f modules="pos-people"
```

The frontend needs this SDK to start on the register at all, which is why the gate sits here rather
than at the end.

---

## 5. Wave 2 — #2155 register projection

Codebase research materially changes this story from how it is written.

**What the issue assumes, and what is actually true**

| Column | Issue's assumption | Reality |
| ------ | ------------------ | ------- |
| Email, phone | 25 × `getEmployee` | **Already local.** `ExtPersonReplica` carries `primaryEmail`, `secondaryEmail`, `primaryPhone`, `secondaryPhone`, populated by `internal/service/PeopleContactEventsListener`. |
| Username | 25 × `getPersonById` | Not local. Lives on `pos-people-contact`'s `Person` / `PersonResponse`. |
| Application roles | 25 × `PeopleAccessControlService.listRoleAssignments` | Not local — no such service exists in `pos-people`. |
| Location | 25 × staffing assignments | Local to the module. |

There is also no `internal/client/` directory in `pos-people` at all: the module has no outbound
RestClient today. Its entire cross-module intake is the event listener above.

**So the real decision is transport, not endpoint shape — and the answer is to extend the replica.**
Adding a RestClient to fetch usernames and roles means N calls per page, which is precisely the
problem #2155 opens by describing. Extending the existing replica keeps the projection a pure local
join, reuses the established ADR-0044-clean path (events, not cross-module reads), and leaves the
in-memory search in §4 Track B working unchanged.

1. `V7__ext_person_username_and_roles.sql` — add `username` to `ext_person_replica`; add an
   `ext_person_role_assignment_replica` table carrying role, scope (`GLOBAL` / `LOCATION`,
   DECISION-PEOPLE-003) and effective dates.
2. Extend `PeopleContactEventsListener` to populate both from the events it already consumes.
   Coordinate with `pos-people-contact` on whether those fields are on the existing events or need
   adding — this is the one genuine cross-module conversation in the plan.
3. Widen `EmployeeSummaryDto` with `username`, `contactInfo`, `roleAssignments[]`, `primaryLocation`
   plus `otherLocationCount` (DECISION-PEOPLE-004 — the page renders `Charlotte Main · +1 more`),
   and `jobRole` from Track A.
4. `include=` query parameter; default omits every new field, so `HrFacadeTool.searchEmployees`
   keeps its thin payload untouched.
5. **Enrich the 25-row window only, never the full `findAll()` list.** See §7.
6. Role assignments default to active-only (DECISION-PEOPLE-026).
7. PII gating: omit `email` / `phone` for a caller without `people:employee_pii:view` and return
   200, never 403 — per #1898, where conflating `people:employee:view` with PII access exposed home
   addresses and emergency contacts to twelve roles including TECHNICIAN.

**Acceptance (from #2155):** one request renders a 25-row page with every column; a caller with
`people:employee:view` but not `people:employee_pii:view` gets rows with `email`/`phone` absent and
a 200; a caller without `people-contact:role:view` gets rows with `roleAssignments` absent;
`HrFacadeTool.searchEmployees` is unaffected.

---

## 6. Wave 3 — #2159 `allowedActions`

Now buildable: `ENABLE` has an endpoint, its permission is settled, and the row shape exists.

1. An `AllowedAction` enum — `VIEW_PII`, `UPDATE`, `DISABLE`, `ENABLE`. Typed rather than free
   strings, so the generated SDK carries the vocabulary and adding a value is a visible contract
   change rather than a silent string drift.
2. A single `EmployeeActionPolicy` computing the list from `(authorities, status)`. One class, so
   the transition table lives in exactly one place — which is the entire point of the story.
3. The field on both `EmployeeProfileDto` and the widened summary row.
4. The backend continues to enforce independently. The flags are a rendering hint and never the
   gate; say so in the OpenAPI description so no future reader mistakes them for authorization.

**Correction to the issue's acceptance criteria.** As written they cover `ACTIVE`, `DISABLED` and
`TERMINATED` only. But disable requires status *exactly* `ACTIVE`
(`EmployeeServiceImpl.java:211-215`: one guard rejects `DISABLED`/`TERMINATED`, a second rejects
everything that is not `ACTIVE`). So `DISABLE` must also be absent for `ON_LEAVE` and `SUSPENDED`,
and the criteria should say so. The full expected matrix, for a caller holding the deactivate
permission:

| Status | `DISABLE` | `ENABLE` |
| ------ | --------- | -------- |
| `ACTIVE` | yes | no |
| `DISABLED` | no | yes |
| `ON_LEAVE` | no | no |
| `SUSPENDED` | no | no |
| `TERMINATED` | no | no |

A caller without the permission gets neither, for every status.

**Open question to settle in this wave: scope-awareness.** The issue says flags are computed from
the caller's permissions and the record's state, and is silent on location scope — but scope is real
in this module (`StaffingAssignmentServiceImpl`, `PeopleAvailabilityServiceImpl`,
`WorkSessionAccessPolicy`). If a location-scoped caller sees an employee outside their locations,
should `DISABLE` appear? **Recommendation: keep the flags permission-and-status only for now and
document that limitation explicitly in the OpenAPI description.** Making them scope-aware reintro-
duces per-row assignment lookups and undoes Wave 2's whole design. If scope gating is wanted, it is
a follow-up story, not a line item here.

---

## 7. Risks

| Risk | Severity | Mitigation |
| ---- | -------- | ---------- |
| Wave 2 enrichment applied before windowing turns an O(n) in-memory scan into O(n) cross-module joins per request | **High** — this is the failure that would make the register slower than the 100-request version it replaces | Enrich the window only; the comment left at `findAll()` in Wave 1 Track B is the guard. Add a test asserting enrichment count equals page size, not total. |
| #2156 permission decision deferred into implementation | High | Wave 0 gate. #2159's criteria are written against one answer; discovering the other mid-Wave-3 invalidates them. |
| `pos-people-contact` events do not carry username / role assignments | Medium | Confirm during Wave 2 step 2, before writing the migration. If they need adding, that is a `pos-people-contact` change and should be raised as its own story rather than absorbed. |
| Replica lag shows a stale role or username on the register | Low | Already the accepted tradeoff for names — `EmployeeSummaryDto.firstName` is documented as null "when the replica has not caught up". Same treatment, same documentation. |
| SDK drift between waves | Low | `API Artifacts Sync` after Wave 1 and again after Wave 3, per `CLAUDE.md`. |
| Track A and Track B conflict in `EmployeeServiceImpl` | Low | Track A's scope cut — it does not touch `searchEmployees`. |

---

## 8. Verification

Per module conventions in `durion-positivity-backend/CLAUDE.md`:

```bash
./mvnw spotless:apply
./mvnw -pl pos-people -am test
./mvnw -pl pos-people -am verify          # *IT.java contract tests
./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test   # after the new packages in Track A
```

Per-wave evidence:

- **Wave 1 A** — `jobRole` round-trips create → update → read; the new tables carry `tenant_id` and
  RLS and are absent from `src/main/resources/db/tenancy-global-tables.txt`.
- **Wave 1 B** — `?status=DISABLED&page=1` returns the tenant's disabled count in `totalElements`;
  `?sort=lastName,desc` is verified across a two-page result, not within one.
- **Wave 1 C** — all five statuses exercised against `/enable`; the reactivation event observed
  downstream.
- **Wave 2** — a 25-row page renders every column in one request; the PII-less caller gets 200 with
  fields absent; enrichment count asserted equal to page size.
- **Wave 3** — the full status × permission matrix in §6, as a parameterised test.

---

## 9. Sequencing note

If the register is needed sooner than the full plan delivers, **Wave 1 Track B alone (#2158) makes
today's eight-column register correct**. Without it the page's status filter and its counts are
wrong in a way that looks right — filtering to "Disabled" shows the disabled rows out of page 1's
25, not out of all 214. Everything after Track B adds columns; Track B fixes a lie.
