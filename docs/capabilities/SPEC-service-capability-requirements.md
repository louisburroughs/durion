---
title: "SPEC: Scheduling Eligibility Model — service capability requirements (CAP-325..329)"
updated_utc: "2026-09-16T17:20:00Z"
generated_by: "Shop Management domain review, recorded by Claude Code"
capabilities: ["CAP-325", "CAP-326", "CAP-327", "CAP-328", "CAP-329"]
stories: ["louisburroughs/durion#482", "louisburroughs/durion#483", "louisburroughs/durion#484", "louisburroughs/durion#485", "louisburroughs/durion#486"]
---

## SPEC: Scheduling Eligibility Model

Specifies how the platform answers "can this bay and this technician do this job",
so that eligibility becomes a data join rather than an inference.

Revised 2026-09-16 after Shop Management domain review. The first revision scoped
only the bay-capability axis and deferred mechanic skill; the review found that
decision wrong on the merits and found three false code claims in §9.1. Both are
corrected here. The review's evidence is incorporated throughout; where this spec
now disagrees with `louisburroughs/durion-positivity-backend#2024` or `#2022`, this
spec is the later word and the stories need amending.

Capabilities: **CAP-325** (bay capability axis — the only part ready to execute),
with **CAP-326** through **CAP-329** specified here and sequenced in §9.
Modules: `pos-catalog`, `pos-location`, `pos-shop-manager`, `pos-people`,
`pos-domain-events`.

Deliberately **not** an ADR. This records a concrete data-and-contract design. The
constraints it works within are already accepted — ADR-0044 §6 (replicas fed by
facts), ADR-0026 (module boundaries), ADR-0027 (UUID identifiers), ADR-0059 §3
(Durion-owned codes, vendor codes cross-referenced onto them), ADR-0062 (tenancy).
Where this spec chooses, it chooses inside those.

**The one escalated decision is now answered.** The severity of a skill mismatch was
undecided by the Shop Management domain and not a story author's to settle; it was
resolved by `louisburroughs/durion-positivity-backend#2035` on 2026-09-16 and is
recorded at D10. Nothing in this spec is now pending an external decision.

---

## 1. Summary of Findings

Verified against `durion-positivity-backend` at the time of writing. Findings
marked **†** were established or corrected by the domain review and contradict
claims made in `#2024`, `#2022` or this spec's first revision.

### 1.1 The capability axis

| Finding | Detail |
|---|---|
| Catalog declares no requirement | Exhaustive grep of `pos-catalog/src` for `capabilit\|skill\|competenc\|certificat\|requirement` finds nothing modelling a service requirement. `ServiceDto` carries seven fields, none of them a requirement |
| A capability registry exists, unexposed | `service_location_capabilities` in `pos-location` — `id`, unique `code`, `name`, `active`, uppercase-normalized on write (`ServiceLocationCapabilityEntity.java:57-71`), tenant-scoped unique `(tenant_id, code)` (`V1__baseline_location.sql:178-186, 303-304`). **20 rows seeded** (`R__seed_location_1_reference.sql:304-365`). No controller, no listing endpoint, no write API |
| Bays store capability **codes**, not ids | `BayEntity.serviceCapabilityIds` is `List<String>` persisted as JSON `TEXT` (`BayEntity.java:82-90`, column at `V1__baseline_location.sql:29`), resolved by `findByCodeIn(...)` (`BayServiceImpl.java:322-330`), uppercased on write (`:314-320`), unknown codes rejected (`:298-300`). The `@Schema(example = …)` on the field shows UUIDs and is wrong |
| **No bay anywhere holds a capability** | `service_capability_ids` is populated by zero rows in any migration, and `scripts/fixtures/seed/alpha/location/bays.csv` is `locationCode,name,bayType,maxConcurrentVehicles` across 24 rows. The consumer side of the join is empty |
| **†** `BayType` mixes two kinds of predicate | `ALIGNMENT`, `TIRE_SERVICE`, `INSPECTION`, `WASH_DETAIL` are equipment predicates; `HEAVY_DUTY` is a vehicle-class ceiling; `GENERAL_SERVICE` is neither. One enum, three meanings — see D5 |
| Mobile units use a different identifier space | `mobile_unit_capabilities` is a real join table on UUID (`V1__baseline_location.sql:119-123`), accepting either form on input (`MobileUnitServiceImpl.java:536-575`), with no FK to the registry |
| `operationCode` is nullable | `ServiceEntity.java:35` — "a dealer-created one-off service may never join a guide taxonomy". Unique only when present |
| A service fact already exists carrying `operationCode` | `CatalogServiceUpdatedV1` (`catalog.service.updated` on `catalog.events.v1`), schema v2: `serviceId`, `name`, descriptions, `active`, timestamps, `operationCode`, `operationCategory`, `defaultLaborHours`. Published at `CatalogFactPublisher.java:235-250` |
| Two consumers of that fact exist | `pos-marketing` (`ext_catalog`) and `pos-workorder`, both via a `CatalogEventsListener`. `pos-shop-manager` has none; `pos-location` holds no catalog replica |
| A cross-reference precedent lives in catalog | `service_operation_xref` maps vendor labor-guide codes onto Durion `operation_code` (`V1__baseline_catalog.sql:455-463`), seeded by a repeatable migration joining `service` on `operation_code` with md5-derived deterministic ids (`R__seed_reference_catalog_6_labor_guide.sql:22-34`) |
| `operationCategory` is not a substitute | Four values (`REPAIR, DIAGNOSTIC, MAINTENANCE, TIRE_SERVICE`), describing kind of work rather than equipment, with no `ALIGNMENT` member while `BayType` has one |
| **†** A broken service reference already exists in shop-manager | `ShopServiceEntry.java:38-39` — `// Reference to ServiceEntity in pos-catalog` / `private Long serviceEntityId`, column `bigint` (`V1__baseline_shop_manager.sql:375-382`). Catalog's key is a UUID (`ServiceEntity.java:23-24`). ADR-0027 violation, and §4 adds a *second* service-reference table to the same module |

### 1.2 The skill axis

| Finding | Detail |
|---|---|
| **†** Two competence tables exist, not one | `mechanic_skill` (`skill_code`, `proficiency_level`, `certified_date`, `expiration_date`; `V1__baseline_shop_manager.sql:279-289`) **and** `certification(tenant_id, id, technician_id, ase_code, description)` (`:116-124`, `Certification.java:36`), reachable from `Technician.certifications`, with an empty `CertificationRepository` (`:7`), zero rows, zero writers and **no date columns at all**. `#2024` F6's "there is no skill registry anywhere in the platform" is wrong: ASE codes were already modelled once as certifications held by a technician, then abandoned |
| **†** Two person identities inside one module | `Mechanic.personId` is a `String` (`Mechanic.java:41`); `Technician.personId` is a `UUID` (`Technician.java:40`). The roster query joins them with `mechanic.personId = CAST(technician.personId AS string)` in a comma cross-join (`TechnicianRepository.java:20-32`). `MechanicSkill` hangs off `Mechanic`, `Certification` off `Technician` — competence is reachable by two graphs with no guarantee they agree, on the hot path for a 30-day search |
| Nothing writes the expiry columns | `MechanicSyncServiceImpl.replaceMechanicSkills` (`:186-194`) does `deleteAllByMechanicId` then rebuilds from `skillCode` + `proficiencyLevel` only, so the HR payload is narrower than the table it replaces. `MechanicSkillBulkIngestController.java:139-141` likewise. Fixture header is `employeeNumber,skillCode,proficiencyLevel`. The columns are unreachable **by construction**, and a local write would be destroyed by the next sync |
| The roster projection drops what it has | `MechanicRosterQueryServiceImpl.java:73-83` — `Collectors.mapping(MechanicSkill::getSkillCode, …)`. Proficiency, certification date and expiry never reach `MechanicRosterEntryResponse` |
| Skill matching is case-sensitive and untrimmed | `TechnicianRepository.java:26-30` — bare `skill.skillCode = :skillCode`. `pos-location` uppercases before lookup. An ingested `t4-brakes` never matches a query for `T4-BRAKES` |
| Proficiency is unread | `MechanicSkill.java:51-52` is an `int` on a 1–5 scale documented only at `MechanicSkillBulkIngestController.java:90`, read by no query or service |
| The vocabulary lives in a fixture and is two parallel ASE series | `scripts/fixtures/seed/alpha/shop-manager/mechanic-skills.csv` — 23 rows, 7 mechanics, 12 codes. A-series (`A4-SUSPENSION`, `A5-BRAKES`, `A6-ELECTRICAL`, `A8-ENGINE-PERF`) is light-vehicle; T-series (`T1-GAS-ENGINE`, `T2-DIESEL-ENGINE`, `T3-DRIVE-TRAIN`, `T4-BRAKES`, `T5-STEERING`, `T6-ELECTRICAL`, `T7-HVAC`, `T8-PMI`) is medium/heavy truck. `A5-BRAKES` and `T4-BRAKES` are the same competence on different vehicle classes |
| No registry validates it | `MechanicSkillBulkIngestController.java:67`'s own OpenAPI example uses `T3-ALIGN` — in neither the fixture nor the ASE taxonomy. The module's documentation contains a fabricated code |
| **†** Expiry is a `LocalDate`, openings are `Instant`s | `MechanicSkill.java:57-58`. DECISION-SHOPMGMT-015 makes facility timezone authoritative, so the comparison must be against the opening's facility-local date or it is wrong by up to a day at the boundary |
| **†** `shop_qualification` exists and is unqueried | `V1__baseline_shop_manager.sql:365-373` — `shop_id`, `name`, `description`. Inspection authority is arguably a shop qualification as much as a person's |

### 1.3 The enforcement tier

| Finding | Detail |
|---|---|
| **†** `AWAITING_SKILL_FULFILLMENT` is not in the code | `AssignmentStatusEnum.java:4-8` is `CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED`, duplicated verbatim at `internal/service/enums/AssignmentStatus.java:3-7`. DECISION-SHOPMGMT-010's six members are not implemented and the state exists only in the decision record. **This spec's first revision asserted otherwise** |
| **†** `conflict_resource_type` is not in the schema | It exists only in `DOMAIN_NOTES.md:154`'s proposed DDL. `V1__baseline_shop_manager.sql` has zero `CREATE TYPE` statements and no `conflict_rule`, `scheduling_conflict` or `conflict_override` table. **This spec's first revision asserted otherwise** |
| **†** DECISION-SHOPMGMT-002 assigns no severity to `SKILL` | Its seeded rules are `BAY_DOUBLE_BOOKED`/HARD/BAY, `MECHANIC_UNAVAILABLE`/HARD/MECHANIC, `MECHANIC_OVERTIME`/SOFT/MECHANIC, `FACILITY_NEAR_CAPACITY`/SOFT/CAPACITY. The severity every design in flight assumed was never granted. Resolved by `#2035`: SOFT, warning-only, advisor-overridable, with a new `MECHANIC_NOT_CERTIFIED` rule — see D10 |
| **†** The decision's own audit is unrunnable | `override_record` (`:298-308`) records `override_reason`, `conflict_details`, `overridden_by_user_id` — neither a severity nor a rule reference. "Find HARD conflicts with overrides; should be zero" cannot be executed |
| Conflict detection is a no-op with no caller | `ConflictDetectionServiceImpl.java:16-41` — all four methods; `isWithinOperatingHours` returns `true` unconditionally. Only reference anywhere is `InternalServiceImplementationsTest.java:28`. Appointment creation performs no operating-hours, bay double-booking or mechanic check |
| **†** And a pre-check is contractually advisory | DECISION-SHOPMGMT-011: *"Canonical contract is 'conflicts returned on submit'. A separate pre-check may exist for UX but must not be required for correctness."* So `#2022`'s opening search is advisory **by the domain's own definition** until the submit-time tier exists |

### 1.4 The vehicle axis

| Finding | Detail |
|---|---|
| Duty class does not exist | Zero hits for `vehicleClass`/`vehicle_class`/`LIGHT_DUTY`/`MEDIUM_DUTY` in non-test sources. `pos-vehicle-fitment`'s `VehicleType` (`:16-34`) is `@TenantGlobal`, make-scoped, free-text `vehicleTypeName`. The NHTSA module has no GVWR or body-class column. `#2022`'s contract has `vehicleClass` as a query parameter for a concept nothing models |
| **†** But the appointment→vehicle link exists and is mandatory | `Appointment.java:63-64` — `crmVehicleId`, `nullable = false`. `ExtVehicleReplica` carries `vin`, `make`, `model`, `model_year`, `unit_number` (`:32-58`) — a VIN is present, so a decode has something to work from |
| **†** The catalog already forks by vehicle class — for tire work only | `TIRE-INSTALL-SET-4` (passenger), `TIRE-INSTALL-LT-SET-4` (light truck), `TIRE-INSTALL-COMMERCIAL-SINGLE` (commercial) fork at the SKU because labor time and price differ. But `BRAKE-PAD-REPLACE-FRONT`, `OIL-CHANGE-FULL-SYNTHETIC`, `TRANSMISSION-SERVICE`, `WHEEL-ALIGNMENT-4-WHEEL`, `COOLANT-SYSTEM-FLUSH`, `BATTERY-REPLACEMENT` are unforked and sellable against a Civic or an F-750. **The axis is not absent — it is half in service identity and half nowhere**, and the A5-vs-T4 problem exists precisely and only for the unforked services |

---

## 2. Decisions

### D1 — `pos-catalog` owns the requirement *(unchanged)*

The requirement is an attribute of the service, and `pos-catalog` owns services.
It rides `catalog.service.updated`, which already exists and already carries both
the `serviceId` and the `operationCode`, so the capability requirement needs one
additive schema bump and no new fact. This extends to the skill requirement (D8).

`pos-location` keeps the capability **vocabulary**. Ownership of a vocabulary and
ownership of a per-service requirement are separate.

### D2 — capability crosses the boundary as `code`; the skill registry is `@TenantGlobal`

`pos-catalog` stores capability **codes** and validates them against an
`ext_service_capability` replica fed by a new `location.service-capability.updated`
fact (ADR-0044 §6 — never a synchronous read). The code is what `BayResponse`
already returns and what `BayServiceImpl` already resolves by.

**Revised from the first revision:** the capability registry stays tenant-scoped
(equipment genuinely differs per tenant), but a **skill registry must be
`@TenantGlobal`** — precedent `VehicleType.java:16`. An ASE certification is issued
by a national body and means the same thing in every shop; routing competence
through a per-tenant vocabulary would let two tenants disagree about what
`T4-BRAKES` certifies. This is the decisive objection to using bay capability as the
middle term between service and skill (§2.1).

The service identifier on the wire is `serviceId` (UUID v7, ADR-0027) everywhere,
including the join key of the requirement table (D3). A capability code is a
reference-data vocabulary key, in the same class as `source_code` on
`service_operation_xref`.

### D3 — the requirement is keyed by `service_id`, seeded by `operation_code` *(unchanged)*

`operationCode` is nullable, so an `operationCode`-keyed table can never cover every
service. The data load resolves `operation_code → service_id` at seed time, exactly
as `R__seed_reference_catalog_6_labor_guide.sql` already does.

### D4 — unconstrained versus unconfigured is a header, not a sentinel *(revised)*

The tri-state is right and is required by `#2024` AC4 and `#2022`'s
`SERVICE_REQUIREMENTS_NOT_CONFIGURED`: *no requirement stated* must warn and not
deny, while *explicitly unconstrained* must neither warn nor filter.

The first revision encoded this as a `capability_code = '__NONE__'` sentinel row.
**That is withdrawn.** It required §5 to promise "the sentinel never leaves the
module" with nothing enforcing it, forced §6's `422` validation to special-case a
value absent from the registry it validates against — contradicting the table's own
justification that the column is validated against the replica — and put a magic
string in a vocabulary column where any direct reader would see it as a capability.
An unvalidated magic string in a vocabulary column is precisely the failure this
capability exists to prevent; `T3-ALIGN` is what it looks like in production.

Instead, header/detail. `service_requirement_profile(service_id, configured_at, …)`
is the header; capability and skill requirements are its children. Absent header =
not configured; header with no children = unconstrained. The distinction is a
property of the service, which is where it belongs, and the same header serves the
skill requirement when CAP-329 lands. `null` versus `[]` on the DTO and the fact then
has no in-band encoding anywhere.

### D5 — a bay declares equipment classes **and** a duty-class ceiling *(revised)*

A bay declares what equipment it has, not which services it can perform.
Enumerating services per bay was rejected: it churns on every catalog addition, and
a new service would be performable by no bay until every bay at every location was
edited. That still holds.

**Revised:** the first revision treated `BayType` as adequate for the rest.
It is not — it mixes equipment predicates with a vehicle-class ceiling (§1.1†). A
bay needs **both** `service_capability_codes[]` **and `max_duty_class`**.

This is the highest-value change in the revision. It dissolves both coarseness
cases the first revision listed as accepted limitations — `ROAD-FORCE-BALANCE-SET-4`
and `TIRE-INSTALL-COMMERCIAL-SINGLE` — without inventing registry rows: the
commercial tire service requires `MEDIUM_HEAVY`, the bay has a ceiling, done. It
also removes `BayType.HEAVY_DUTY`'s basis for granting five equipment capabilities
on the strength of a vehicle-class label. And it is **the same axis the skill model
needs** (D8), so one capability pays for it and two collect.

`BayType` becomes display-only once both columns exist.

### D6 — the skill axis is specified, and credentials live in `pos-people` *(replaces the deferral)*

The first revision deferred mechanic skill entirely. That was wrong: the deferral
rested on three false code claims (§1.3†) which were its whole evidence that "the
domain anticipated skill eligibility", and on an assumption that shop-manager would
own the vocabulary because it owns `mechanic_skill`.

**The domain has already decided against that ownership.** DECISION-SHOPMGMT-009:
*"Mechanic identity is a foreign reference to the People domain. Shopmgmt does not
own mechanic data."* A person's certifications are mechanic data, and it is HR that
ingests them.

- **Credential holdings → `pos-people`**, beside staffing and time. `pos-shop-manager`
  already consumes `ext_people_staffing_assignment` (`V1__baseline_shop_manager.sql:187`)
  and consumes `ext_person_credential` identically. This deletes **both**
  `mechanic_skill` and the orphaned `certification` table from shop-manager, and
  takes the `Mechanic`/`Technician` dual identity (§1.2†) with them.
- **The skill registry → platform reference data, `@TenantGlobal`** (D2).
- **The service → required skill declaration → `pos-catalog`** (D1).

Shop Management's job is to schedule *against* competence, not to store it.

### D7 — the first-class aggregate is the credential, not the skill

A **skill** is reference data: a registry row with a code, a name, a duty class and
an active flag. It has no lifecycle beyond active/inactive. Modelling it as an
aggregate is over-modelling.

A **credential** is an aggregate: issuer, issue date, expiry, proficiency,
lifecycle (`ACTIVE → EXPIRED → REVOKED → SUPERSEDED`), an evidence reference and an
audit trail. A renewal is a **new row, not an overwrite**, because "was this person
qualified on 3 March" is a question a DOT auditor asks and 49 CFR 396.19 requires
the shop to retain the evidence to answer it. `deleteAllByMechanicId` followed by
re-insert (`MechanicSyncServiceImpl.java:186`) guarantees it cannot be answered.

Corollary: **drop `min_proficiency` from the requirement side entirely.** A nullable
unread column on a requirement table is exactly how `certified_date` and
`expiration_date` reached their present state — present in the schema, written by
nothing, and read as authoritative. Proficiency stays on the credential as display
metadata until someone can say what a 3 means and a shop has a UI to set a
threshold.

### D8 — ASE codes are cross-references; duty class sits on the registry row and on the requirement

Following ADR-0059 §3 — *"vendor labor-guide codes map onto Durion codes via
cross-reference, never the reverse"* — and the `service_operation_xref` precedent:

- The registry holds **Durion-owned** skill identity, one row per
  (competence, duty class): `BRAKES-LIGHT` and `BRAKES-MEDIUM_HEAVY` are two rows.
- `skill_code_xref(skill_id, source_code, source_skill_code)` maps `A5-BRAKES` onto
  the first and `T4-BRAKES` onto the second, with `source_code = 'ASE'`. HR keeps
  sending ASE codes forever; the xref absorbs them, and an unresolvable code fails
  the xref loudly instead of becoming a skill nobody holds — which is what
  `T3-ALIGN` is today.
- The requirement carries the class-conditional selector:
  `service_skill_requirement(service_id, skill_id, applies_to_duty_class)`. This is
  the link that makes one unforked service answerable for both a Civic and an
  F-750, and it is the piece a `vehicle_axis` column on a capability→skill map
  cannot express.

Duty class on a `(capability, skill)` pair would be wrong in principle: the axis is
a property of the skill and of the job's vehicle, never of that pair.

### D9 — using bay capability as the middle term between service and skill is rejected

Recorded because it was the first revision's implicit direction and is the obvious
shortcut. `service → required capability → required skill` fails on four counts:

1. **It makes competence a function of shop equipment.** Adding a
   `ROAD_FORCE_BALANCE` registry row — which §3.3 contemplates — would silently
   change who is certified for `ROAD-FORCE-BALANCE-SET-4`. Equipment and competence
   are independent variables.
2. **It makes a national credential tenant-scoped** (D2).
3. **It produces false negatives against today's fixture data.** `TRANSMISSION` maps
   to `T3-DRIVE-TRAIN`, which is ASE *Medium/Heavy Truck* Drive Train and is held by
   3 of 7 seeded mechanics — so a passenger-car transmission fluid exchange reports
   no certified technician. `PM_SERVICE` maps to `T8-PMI`, also 3 of 7, so routine
   fleet PM does the same. A false negative is silent: the advisor is told there is
   no capacity, books nothing, and no one learns the answer was wrong. An over-match
   is at least visible — the technician refuses the job.
4. **The cardinality is wrong in both directions.** 11 of 20 capabilities have no
   service and would carry map rows nobody reads; every unforked service (§1.4†) has
   a competence requirement finer than its equipment requirement and cannot express
   it.

What survives from it, and is kept: no skill vocabulary on `ServiceDto` until a
registry exists, and the module owning competence data owns the resolution.

### D10 — a skill mismatch is SOFT, warns only, and is manager-overridable *(settled)*

**Answered by `louisburroughs/durion-positivity-backend#2035`, 2026-09-16**, with a
revision to answer 3 and a refinement to answer 2 the same day.

| Question | Answer |
|---|---|
| Severity of a skill mismatch | **SOFT** |
| Does a shortfall block anything? | **No** at scheduling. It warns. See D10.1 for assignment |
| Override authority | **MANAGER.** Answer 3's advisor-level override was withdrawn "for simplification" |
| DOT inspection authority | **A unique credential**, distinct from any PM competence |
| Does `MECHANIC_UNAVAILABLE` cover "rostered but not competent"? | **No.** It means no mechanic is present. Not-competent needs its own rule |

**Severity is a property of the rule, not of the (rule, operation) pair**, and **no
decision record needs amending.** With the override back at manager level,
DECISION-SHOPMGMT-002's own definition of SOFT — *"warning, can override with
manager approval"* — is satisfied literally. The variation across operations is a
matter of **surface**, not severity, and falls out of which decision record governs
each operation:

| Operation | Behaviour | Governed by |
|---|---|---|
| (a) `GET /v1/schedules/openings` | Never withhold. Contention → per-opening flag; rostered-absence → response-level advisory (D10.2) | DECISION-SHOPMGMT-002 (SOFT = warn) |
| (b) Create the appointment | Warn, allow, manager override recorded per DECISION-SHOPMGMT-007. Never block | DECISION-SHOPMGMT-002 (SOFT) |
| (c) Assign a technician to the workorder | Cannot complete when no competent candidate exists — the **candidate set is empty**. The assignment goes to `AWAITING_SKILL_FULFILLMENT` | DECISION-SHOPMGMT-010 |

DECISION-SHOPMGMT-002 governs *scheduling*; DECISION-SHOPMGMT-010 governs
*assignment*, which is a state-machine transition it never claimed. That is why a
rostered-absence can stop an assignment while the rule stays SOFT and while
"prevents nothing" remains true: **there is nothing to override to.** An empty
candidate set needs no severity to be empty. Answers 1, 2 and revised 3 all hold
simultaneously.

### D10.1 — ROSTERED and AVAILABLE are two rules, both SOFT, manager override on both

The two conditions differ in remedy and in consequence at assignment, so one rule
cannot carry both. Answer 5's instruction that absence and incompetence need
separate rules applies one level down: *absent* competence and *contended*
competence are separate rules.

| `conflict_rule.code` | Resource | Severity | Override | (a) search | (b) booking | (c) assignment |
|---|---|---|---|---|---|---|
| `COMPETENT_MECHANIC_UNAVAILABLE` | `SKILL` | SOFT | MANAGER | per-opening `skillFulfillment: AWAITING` | warn, allow | assignable to a non-certified technician |
| `NO_COMPETENT_MECHANIC_ROSTERED` | `SKILL` | SOFT | MANAGER | response-level `staffingAdvisory` | warn, allow | candidate set empty → `AWAITING_SKILL_FULFILLMENT` |

**ROSTERED is deliberately not HARD.** Two reasons. First, the manager gate already
supplies the friction: a manager may legitimately book work nobody at the location
can staff — send it to the other branch tomorrow, call a contractor, book knowing
the certified technician returns Thursday. All three are correct business decisions
and a HARD block forbids all three. Second and more decisive, **a HARD ROSTERED
would render `AWAITING_SKILL_FULFILLMENT` dead for its most important case.** If a
competent technician is merely busy you barely need a parked state — you book and
assign later the same day. The case that most needs a parked assignment is exactly
rostered-absence: booked, nobody here can do it, waiting on a transfer, a hire or
training. DECISION-SHOPMGMT-010 defined that state deliberately; making ROSTERED
HARD at booking contradicts the record that supplies the mechanism.

**One namespace: the response uses these exact codes.** A `conflict_rule.code` and
the reason code the API returns for it are the same string, so there is no mapping
table and no way for the two to drift. `staffingAdvisory.code` is
`NO_COMPETENT_MECHANIC_ROSTERED`; a per-opening `AWAITING` is caused by
`COMPETENT_MECHANIC_UNAVAILABLE`.

This **retires** the wording carried in `#2022`'s original contract and in `#2035`
answer 5's phrasing — `NO_CERTIFIED_TECHNICIAN_ROSTERED` and
`NO_CERTIFIED_TECHNICIAN_AVAILABLE`. Two deliberate changes from those strings:

- **`MECHANIC`, not `TECHNICIAN`.** DECISION-SHOPMGMT-002's existing rules are
  `MECHANIC_UNAVAILABLE` and `MECHANIC_OVERTIME`, and `conflict_resource_type` has a
  `MECHANIC` member. The new rules join that vocabulary rather than starting a
  second one. (`Mechanic` and `Technician` are also two different entities in
  `pos-shop-manager` today — §1.2† — so the word is not free of meaning.)
- **`COMPETENT_MECHANIC_UNAVAILABLE`, not `NO_CERTIFIED_TECHNICIAN_AVAILABLE`.**
  Putting the qualifier first keeps it from reading as a variant of
  `MECHANIC_UNAVAILABLE`, which is the HARD rule for *nobody present* that
  answer 5 took pains to keep separate. The prefix is what distinguishes them:
  `MECHANIC_UNAVAILABLE` means no mechanic; `COMPETENT_MECHANIC_UNAVAILABLE` means a
  mechanic, not a competent one.

### D10.2 — rostered-absence is a response-level advisory, not a `noOpeningReason` and not a per-opening flag

`#2022`'s rostered-absence reason — originally spelled
`NO_CERTIFIED_TECHNICIAN_ROSTERED`, now `NO_COMPETENT_MECHANIC_ROSTERED` per D10.1 —
is withdrawn **from that field**, and the reason matters: not because the condition is unreal, but because
`noOpeningReason` is defined as "set only when openings is empty", and **skill never
empties the list, so it can never be the cause.** `noOpeningReason` keeps three
values: `NO_ELIGIBLE_BAY_AT_LOCATION`, `ALL_ELIGIBLE_BAYS_BOOKED`,
`SERVICE_REQUIREMENTS_NOT_CONFIGURED`.

Nor is it a per-opening flag. Every other finding in the conflict model is a
**contention** and depends on the proposed window — `BAY_DOUBLE_BOOKED` on (bay,
window), `MECHANIC_OVERTIME` on (person, week), competent-but-busy on (person set,
window). Rostered-absence depends on (location, required skills) and is **invariant
across the whole search**. Stamping it onto every opening across 30 days duplicates
it and falsely implies it could differ between them.

```jsonc
{
  "openings": [{ "…": "…",
    "skillFulfillment": "CERTIFIED",          // | "AWAITING" — per-opening, contention
    "unmetSkillCodes": [],
    "constraintsEvaluated": ["BAY","DURATION","BUFFER","ROSTER"]   // D11
  }],
  "noOpeningReason": null,                    // three values only
  "staffingAdvisory": {                       // response-level; only when a required skill is unheld
    "code": "NO_COMPETENT_MECHANIC_ROSTERED",
    "missingSkillCodes": ["A4-SUSPENSION", "T5-STEERING"],
    "absenceScope": "NOT_AT_THIS_LOCATION"    // | "NOT_ROSTERED_THIS_DAY"
  }
}
```

**The fixture proves the condition is live, and with a non-empty openings list.**
Cross-referencing `scripts/fixtures/seed/alpha/people/staffing-assignments.csv`
against `scripts/fixtures/seed/alpha/shop-manager/mechanic-skills.csv`:

| Location | Technicians | Alignment competence | Alignment bay |
|---|---|---|---|
| CLT-MAIN-001 | EMP-0005/6/7 | **none** | **yes** (Bay 02) |
| CLT-NORTH-001 | EMP-0010/11 | **none** | **yes** (Bay 04) |
| CLT-SOUTH-001 | EMP-0008/9 | `A4-SUSPENSION` + `T5-STEERING` (EMP-0008) — but **no brake competence at all** | no |
| ATX-RIV-001 | **zero** | — | no |

So `WHEEL-ALIGNMENT-4-WHEEL` at CLT-MAIN-001 today: the rack is free, openings are
returned, and nobody at that location can ever do the work. ATX-RIV-001 with zero
technicians is the genuine `MECHANIC_UNAVAILABLE` case and must **not** fire a
competence rule. The Charlotte locations have complementary gaps, so "CLT-SOUTH-001
has an alignment technician" is a correct, actionable answer computable from seeded
data now.

**`absenceScope` carries the remedy**, and one enum covers both the structural and
the daily case so the rule table stays at two:

| `absenceScope` | Meaning | Remedy |
|---|---|---|
| `NOT_ROSTERED_THIS_DAY` | A holder works here, not scheduled that day | Offer another day |
| `NOT_AT_THIS_LOCATION` | Nobody here holds it, ever | Offer another branch |
| `NOT_IN_TENANT` | Nobody employed anywhere holds it | Hire, train, subcontract, decline |

**`NOT_IN_TENANT` and an `alternateLocations[]` suggestion are out of scope for
CAP-325 and `#2022`.** DECISION-SHOPMGMT-012 makes facility scoping deny-by-default,
so naming another branch tells the caller that a facility exists and how it is
staffed. A **location-scoped caller must see `NOT_AT_THIS_LOCATION` for both cases**
or the staffing profile of facilities they cannot see leaks. Shipping the field now
makes that story additive rather than a contract break.

**Computing the split is an optimisation, not a cost.** Per §1.2's grain finding,
whether anyone at this location holds the required skills is window-invariant:
compute it **once per search, before enumerating any days**, and short-circuit the
expensive per-candidate minute-grain availability work when it fails. The bleakest
locations are the ones the search stops working on soonest. Under the current model
that query runs through `TechnicianRepository`'s un-indexable cast cross-join
(§1.2†) on every search, which is the strongest single argument for CAP-328
preceding `#2022`.

### D10.3 — what the answer leaves untouched

D1–D9 and D11 stand as written. **No amendment to DECISION-SHOPMGMT-002 or -010 is
needed** — worth stating so nobody opens one.

The credential model (CAP-328) is unaffected: a SOFT warning still has to name
*what* is unmet and know whether the credential behind it has lapsed, so expiry and
the registry matter exactly as much as under a hard filter. Answer 4 **ratifies**
the earlier recommendation not to collapse `DOT_INSPECTION` and `PM_SERVICE` onto
`T8-PMI`, so CAP-328 cites answer 4 as authority for two registry rows rather than
carrying it as a recommendation.

One forward note for CAP-328's DOT story, nothing to decide now: a *manager*
override of a regulatory credential documents the decision; it does not satisfy the
shop's 49 CFR 396.19 obligation to retain evidence that the inspector was qualified.
The override record is not the qualification.

### D12 — override authority resolves from the existing role headers; two prerequisites CAP-326 was missing

Established 2026-09-16 while assessing executability. Both were absent from this
spec's earlier revisions.

**Manager authority needs no new mechanism.** The gateway already propagates roles:
`X-Roles` carries `ROLE_ADMIN,ROLE_MANAGER` and `X-Authorities` carries roles and
permissions together, `ROLE_`-prefixed
(`pos-security-common/.../GatewaySecurityConstants.java:40, 47, 49`), with
`ROLE_PREFIX` at `:122` and `SecurityContextHelper.hasRole()` at `:277` handling the
prefix. `pos-security-service` seeds `LOCATION_MANAGER`, `GENERAL_MANAGER`,
`SHOP_MANAGER` and `MANAGER`, so `hasRole('LOCATION_MANAGER')` works today and
`hasRole('ADMIN')` is already the pattern on the catalog read endpoints
(`CatalogPermissions.java:59` usage).

Two open choices, both CAP-326's to record rather than this spec's to make:

- **Role check or permission.** `hasRole('LOCATION_MANAGER') or
  hasRole('GENERAL_MANAGER')` needs nothing new. A `shop:conflict:override`
  permission granted to those roles matches the platform's code-first convention
  (ADR-0025, `{Module}PermissionRegistry`) and lets the grant be re-delegated
  without a code change. **Recommend the permission** — who may override a booking
  conflict is exactly the grant that gets moved around, and with three roles holding
  it a permission is one `@PreAuthorize` and three template grants rather than a
  three-way `hasRole` disjunction repeated at every override site.
- **Which managers — settled 2026-09-16: all three.** `LOCATION_MANAGER`,
  `GENERAL_MANAGER` and `SHOP_MANAGER` may each override a SOFT conflict.
  Consequences to implement rather than re-decide:
  - **The role grants the capability; location scope still constrains where.** A
    `LOCATION_MANAGER` of one facility does not thereby override a conflict at
    another — DECISION-SHOPMGMT-012 is deny-by-default and the ADR-0061
    location-scope guard `#2022` already uses applies to the override endpoint too.
    `GENERAL_MANAGER`, being tenant-wide, is bounded only by the tenant.
  - The seeded role list also contains a generic **`MANAGER`**, plus
    `ACCOUNT_MANAGER` and `INVENTORY_MANAGER`. The latter two are other domains and
    are out. Generic `MANAGER` is **excluded** on the reading that "all three" named
    the three shop-facing roles; if it is meant to be included, say so — it is one
    grant, not a redesign.

**Operating hours do not reach `pos-shop-manager`, so DECISION-SHOPMGMT-008 has no
data path** *(historical — the finding as made on 2026-09-16; superseded by D18.1, which
records that `#2023` and CAP-326 carried `timezone`, `operating_hours` and
`holiday_closures` onto `LocationUpdatedV1` and `ExtLocationReplica`, so the D12
prerequisite is met and the HOURS rules evaluate)*. `ExtLocationReplica` carries `locationId`, `code`, `name`, `active`,
`aggregateVersion`, `syncedAt` and two ancestor sets — and nothing else
(`ExtLocationReplica.java:49-80`). The only operating-hours references anywhere in
the module are the no-op `ConflictDetectionService`, its implementation and two DTOs.
`#2023` adds the check-in and cleanup buffers to `LocationUpdatedV1` and the replica
but not the hours or holiday closures.

So CAP-326's "booking outside operating hours is rejected HARD, Location
authoritative" is **unimplementable as written**. It needs `LocationUpdatedV1` and
`ExtLocationReplica` extended with operating hours and holiday closures, on the same
pattern `#2023` uses for buffers and with the same backfill requirement (`#1668`).
Coordinate with `#2023` rather than adding a second path.

### D13 — the vehicle carries a GVWR class 1–8; competence keys on a class range

Settled 2026-09-16. Resolves both of CAP-327's open questions and supersedes the
`LIGHT | MEDIUM_HEAVY` token used in D5 and D8's first drafts.

**GVWR and duty class are vehicle characteristics.** GVWR is manufacturer-assigned
and stamped on the vehicle; duty class is derived from it. So the value lives on the
**vehicle record**, with the NHTSA/fitment layer supplying only the decode and the
operator supplying the override. CAP-327's "CRM vehicle record versus fitment layer"
question is answered: the vehicle record holds it.

**Store the class, not a category.** The vehicle carries `gvwr_class ∈ 1..8` — the
objective, manufacturer-derived fact — and `duty_category` is a derived view of it,
never the stored value. A two-value light/heavy flag would bake a contested line
into the data; "light duty" is ambiguous on its own, since EPA/CARB use 8,500 lb for
emissions purposes while FHWA uses 10,000 lb for light/medium.

| Duty category | Class | GVWR | Typical |
|---|---|---|---|
| **Light** | 1 | 0–6,000 lb | Small pickups, minivans, sedans |
| | 2 | 6,001–10,000 lb | Full-size pickups (F-150), large passenger vans |
| | 3 | 10,001–14,000 lb | Heavy-duty consumer pickups (F-350 SRW), large cargo vans |
| **Medium** | 4 | 14,001–16,000 lb | City delivery trucks, heavier utility cutaways |
| | 5 | 16,001–19,500 lb | Bucket trucks, large walk-in delivery vans |
| | 6 | 19,501–26,000 lb | Single-axle box trucks, school buses, beverage trucks |
| **Heavy** | 7 | 26,001–33,000 lb | City transit buses, heavy refuse/dump trucks |
| | 8 | 33,001+ lb | Semi-tractor trailers, cement mixers, heavy fire apparatus |

Note this grouping places **Class 3 in Light-Duty**. Some FHWA-derived tables group
Class 3 as Medium-Duty (Light = 1–2, Medium = 3–6). The grouping above is Durion's,
and it is the correct one for this platform for the reason below — cite it as ours,
not as FHWA's.

**Why the Class 4 line is the right one here: it is exactly where ASE's T-series
begins.** ASE scopes its Medium/Heavy Truck tests (T1–T8) to **Class 4 through
Class 8**; the Automobile tests (A1–A8) cover the rest. So the duty boundary that
matters for eligibility is not a weight threshold chosen for road policy — it is the
line the certification bodies themselves draw, and this grouping coincides with it.
Under the alternative grouping, Class 3 would sit above ASE's truck scope and below
"automobile", claimed by neither series. It is not an orphan here.

**So `applies_to_duty_class` on the skill requirement, and `duty_class` on the skill
registry, are expressed as a class range rather than a token:**

| Skill series | Applies to | Registry rows |
|---|---|---|
| ASE A-series | `gvwr_class` 1–3 | `A1`–`A8` cross-reference onto the light-duty rows |
| ASE T-series | `gvwr_class` 4–8 | `T1`–`T8` cross-reference onto the medium/heavy rows |

Expressing it as a range makes the A/T split **one row of data** rather than a
threshold compiled into a decode, so moving Class 3 later is a data change. D5's
`max_duty_class` on a bay is likewise a **class ceiling** (`gvwr_class`), not a
token: a bay rated to Class 3 takes an F-350 and refuses a box truck.

**Verified ASE test titles**, so CAP-328's registry seed is sourced rather than
recalled: A1 Engine Repair, A2 Automatic Transmission/Transaxle, A3 Manual
Drivetrain & Axles, A4 Suspension & Steering, A5 Brakes, A6 Electrical/Electronic
Systems, A7 Heating & Air Conditioning, A8 Engine Performance; T1 Gasoline Engines,
T2 Diesel Engines, T3 Drive Train, T4 Brakes, T5 Suspension & Steering,
T6 Electrical/Electronic Systems, T7 HVAC, T8 Preventive Maintenance Inspection.
The fixture's codes map cleanly onto these. Master status in the truck series is
**T2–T8, excluding T1** — a real credential the model could carry if proficiency
ever needs to mean something, in preference to the invented 1–5 scale D7 drops.

#### D13.1 — the vPIC derivation *(researched; DEFERRED — no real VINs exist)*

> **Scope note, 2026-09-16.** There are no real VINs in the database, so a VIN decode
> cannot be exercised or tested. **CAP-327 ships `gvwr_class` as an operator-set
> field with no decode at all.** The decode is a later addition for when real
> vehicle data exists. Everything below is settled research, kept so it does not
> have to be redone — it is not in CAP-327's scope.
>
> This is consistent with D13's own principle: a decode was always a *default*, and
> the operator override was never optional. Removing the default first is the
> smaller, testable half.

vPIC's `DecodeVIN` returns **two** weight variables, and the field is not named
`GVWR`:

| vPIC variable | Example | Use |
|---|---|---|
| `GrossVehicleWeightRating` | `"Class 1D: 5,001 - 6,000 lb (2,268 - 2,722 kg)"` | **Display and audit only** — what the decode said, shown to an operator considering an override |
| `GrossVehicleWeightRatingFrom` | `5001` | **The derivation input** — numeric lower bound, mapped onto `gvwr_class` by D13's ranges |

**Derive from `GrossVehicleWeightRatingFrom`, never by parsing the string.** The
string carries vPIC's own sub-classes — NHTSA subdivides the lighter FHWA classes
into much tighter bands (`Class 1A`…`1H`, `2A`…`2G` and so on) — which are finer
than FHWA 1–8 and do not map onto them one-to-one. A numeric lower bound mapped
against D13's table is stable, testable, and immune to label changes.

Because vPIC's sub-classes **nest inside** the FHWA classes rather than spanning
them, a lower bound lands in exactly one `gvwr_class` and the straddle case raised
earlier largely dissolves. Verify that nesting against the enumerated values rather
than assuming it — see the task in D16, which the platform can answer from its own
cache.

Keep both fields on the vehicle: the numeric one drives eligibility, the string one
explains the answer. An operator overriding a decode should be able to see the
manufacturer band the decode reported.

### D16 — `pos-vehicle-reference-nhtsa` findings *(DEFERRED with D13.1, except the defect)*

> **Scope note.** Deferred along with the decode, with one exception: finding 2 is a
> live defect in shipped code and stays on the housekeeping list (§9.2) regardless.

CAP-327's decode belongs in `pos-vehicle-reference-nhtsa`: it owns the vPIC
`RestClient`, the caching pattern and the reference entities. But it has no VIN
decode today, and there are three findings to handle before extending it.

**1. There is no VIN decode anywhere in the platform.** The module calls six vPIC
*catalog* endpoints — `GetVehicleVariableList`, `GetVehicleVariableValuesList/{id}`,
`getallmanufacturers`, `GetMakeForManufacturer/{id}`, `GetModelsForMakeId/{id}`,
`GetVehicleTypesForMakeId/{id}` (`VehicleReferenceService.java:45-183`). All six
fetch the vPIC *dictionary*: which manufacturers, makes, models and vehicle types
exist, and which variables exist. A repo-wide grep for `DecodeVin` and for `GVWR`
returns **nothing**. So CAP-327 adds a decode capability; it does not call one.

**2. The vPIC base URL is wrong — confirmed.**
`VehicleReferenceService.java:29` sets
`NHTSA_API_BASE = "https://vpic.nhtsa.dot.gov/v1/vehicles"`. The current vPIC base
is `https://vpic.nhtsa.dot.gov/api/vehicles` — confirmed against a working request
of the form
`https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVin/5UXWX7C5*BA?format=xml&modelyear=2011`.
`/v1/` is not a vPIC path.

So **all six of the module's calls 404**, and the module has never run against live
vPIC — which is consistent with nothing in the repo having ever seen a GVWR value.
The fix is one line:

```java
private static final String NHTSA_API_BASE = "https://vpic.nhtsa.dot.gov/api/vehicles";
```

Worth doing as **standalone housekeeping ahead of CAP-327** rather than inside it: it
is a defect in shipped code, it unblocks finding 3, and it wants its own test that
does not mock the client into agreeing with a wrong URL — which is presumably how it
survived.

**Use `DecodeVinValues`, not `DecodeVin`.** The two variants differ in shape and it
matters for D13.1:

| Endpoint | Returns | Cost to this design |
|---|---|---|
| `DecodeVin/{vin}` | A **list** of `{Variable, VariableId, Value, ValueId}` rows | Must scan for the row whose `Variable` is `"Gross Vehicle Weight Rating From"`, matching on a display label or a magic `VariableId` |
| `DecodeVinValues/{vin}` | A **single flat object** with named keys, including `GrossVehicleWeightRating` and `GrossVehicleWeightRatingFrom` | Direct field access; the names in D13.1 are exactly these keys |

Use `DecodeVinValues/{vin}?format=json`, matching the module's existing
`?format=json` convention and its Jackson setup — the `format=xml` in the confirming
request above is incidental. Pass `&modelyear=` when decoding a **partial** VIN
(the `*` wildcard form), since year disambiguates the decode.

**3. The module can answer D13.1's remaining question from its own cache.**
`VehicleVariable` and `VehicleVariableValue` already model vPIC's variable
dictionary, and the module already calls `GetVehicleVariableValuesList/{variableId}`.
So the **enumerated list of GVWR bands is obtainable from vPIC itself** — fetch the
values list for the `GrossVehicleWeightRating` variable and build the band →
`gvwr_class` mapping from it, rather than hardcoding a table from a web page. That
also confirms or refutes the nesting assumption in D13.1. Do this first; it is the
cheapest way to get the derivation right.

**One boundary oddity to resolve while in there**, not introduced by this work:
the module is `pos-vehicle-reference-nhtsa` but its controller is
`@RequestMapping("/v1/vehicle-fitment")` (`VehicleReferenceController.java:14`) —
`pos-vehicle-fitment`'s path space. Either the path or the module is misnamed.

### D14 — bay eligibility is specialty-by-exception; general is the default *(settled)*

Settled 2026-09-16, and it supersedes D4's tri-state and §7.2's bootstrap table.
This is the largest simplification in the spec and it removes the one blocker that
could not be authored from data.

**The rule, in full:**

1. **A small set of `operationCode`s are specialty** and name the `BayType` that can
   perform them. That mapping is the entire configuration.
2. **Everything else is general work** and is performed in a `GENERAL_SERVICE` bay.
3. **`GENERAL_SERVICE` bays declare nothing.** Their equipment is not asserted,
   inventoried, or inferred from a type name.

**Why this is better than what it replaces.** The prior model had every service
declare `requiredCapabilityCodes` against a 20-row registry, and every bay declare
which of those it held. That required asserting what eight `GENERAL_SERVICE` bays
can do — which is not in any data, was the one acceptance criterion that could not
be authored honestly, and which the earlier bootstrap table got wrong in both
directions (granting a general bay refrigerant-recovery and welding work while
denying it routine fleet PM).

Specialty-by-exception asserts only what is actually knowable: an alignment rack is
in the alignment bay. What a general bay can do is then *defined* as "the rest"
rather than enumerated, which is both true and maintenance-free.

**It also dissolves the churn objection to the operationCode vocabulary.** The
original concern was that a bay enumerating services would go stale on every catalog
addition. Under a default, a new service is general unless someone declares it
specialty — so the common case needs no bay edit at all. This vindicates the
`serviceCapabilityIds`-as-operationCodes reading first proposed on `#2024`.

**Consequence: D4's tri-state collapses to two states.** Absence from the specialty
map is now a *definite answer* ("this is general work"), not an unknown. So:

- `service_requirement_profile` **as a capability header** — the per-service
  capability list and its `service_capability_requirement` child — is **withdrawn**.
  There is nothing to configure per service on the *bay* axis. The profile itself is
  **not** withdrawn: CAP-329 keeps it as the header for `service_skill_requirement`
  (§4.1, §4.5), because skill requirements are per service by nature and D4's
  "not configured is not requires nothing" still applies to them.
- `#2022`'s `SERVICE_REQUIREMENTS_NOT_CONFIGURED` reason is **withdrawn** —
  unreachable for the same reason. `noOpeningReason` keeps two values:
  `NO_ELIGIBLE_BAY_AT_LOCATION`, `ALL_ELIGIBLE_BAYS_BOOKED`.
- The frontend's `eligibilityIsApproximate` banner can die **without any per-service
  configuration pass at all**.

The residual risk moves rather than disappearing, and it is worth naming: a new
*specialty* service added to the catalog that nobody adds to the map silently reads
as general and can be booked into a bay that cannot perform it. That is a smaller
and more visible failure than the one it replaces, but it is real. Mitigate by
reviewing the map whenever a service is added in a category that already has
specialty members — not by reintroducing a per-service configured flag.

**Two questions this raises, answered here rather than left to an implementer:**

- **May a specialty bay perform general work?** Yes, and it must be **eligible but
  ranked last**. Physically an alignment bay can do an oil change; making it
  ineligible would manufacture "shop is full" answers, which is the exact failure
  mode D9 and D10 exist to prevent. Ranking it last keeps the rack free for
  alignment work without lying about capacity.
- **`WASH_DETAIL` is the exception to the default.** It is a specialty bay whose
  specialty set is currently empty, because the catalog seeds no wash services. It
  must **not** absorb general mechanical work. So the default in rule 2 is
  specifically `GENERAL_SERVICE`, not "any bay without a specialty claim".
  *Amended 2026-09-26 (`durion-positivity-backend#2245`, DECISION-LOCATION-025):*
  wash and detail services are ordinary catalog line items, not specialty
  operations, so the wash set stays empty and a `WASH_DETAIL` bay is never offered
  for appointments. `acceptsGeneralWork` on `BayType` replaces the type-name check.

**`HEAVY_DUTY` is not a specialty equipment set.** Per D13 it is a `gvwr_class`
ceiling. It carries no specialty `operationCode`s and takes general work within its
class range like any other bay.

#### D14.1 — the specialty map

Derived from `scripts/fixtures/seed/alpha/catalog/tier0-services.csv`. Specialty
means *the work cannot be done without equipment specific to that bay*, not merely
that it is customarily done there.

| `BayType` | Specialty `operationCode`s | Equipment that makes it specialty |
|---|---|---|
| `ALIGNMENT` | `WHEEL-ALIGNMENT-4-WHEEL` | Alignment rack and heads |
| `TIRE_SERVICE` | `TIRE-INSTALL-SET-4`, `TIRE-INSTALL-LT-SET-4`, `TIRE-INSTALL-COMMERCIAL-SINGLE`, `TIRE-REPAIR-PATCH-PLUG`, `WHEEL-BALANCE-SET-4`, `ROAD-FORCE-BALANCE-SET-4`, `NITROGEN-FILL-SET-4`, `TPMS-SENSOR-SERVICE`, `TPMS-SENSOR-REPLACE-SINGLE` | Mounting machine, balancer; road-force balancer; N₂ generator. All require dismounting a tire |
| `INSPECTION` | `DOT-ANNUAL-INSPECTION` | Inspection lane and the shop's inspection authority |
| `HEAVY_DUTY` | *(none — a `gvwr_class` ceiling per D13)* | — |
| `WASH_DETAIL` | *(none seeded — no wash services in the catalog)* | Not a mechanical bay; excluded from the general default |
| `GENERAL_SERVICE` | *(declares nothing — receives the default)* | — |

**General by default, and deliberately so** — the remaining 17 of 28:
`OIL-CHANGE-FULL-SYNTHETIC`, `BRAKE-PAD-REPLACE-FRONT`, `BRAKE-PAD-REPLACE-REAR`,
`COOLANT-SYSTEM-FLUSH`, `TRANSMISSION-SERVICE`, `BATTERY-REPLACEMENT`,
`FLEET-PM-A-SERVICE`, `FLEET-PM-B-SERVICE`, `AIR-FILTER-REPLACEMENT`,
`CABIN-AIR-FILTER-REPLACEMENT`, `SPARK-PLUG-REPLACEMENT`, `WIPER-BLADE-REPLACEMENT`,
`TIRE-ROTATION`, `LUG-TORQUE-RECHECK`, `FLEET-TREAD-DEPTH-AUDIT`,
`MICHELIN-CASING-INSPECTION`, `MICHELIN-RETREAD-EVALUATION`.

Three judgement calls in that list, flagged rather than buried:

- **`TIRE-ROTATION`, `LUG-TORQUE-RECHECK`** need a lift and a torque wrench, not a
  tire machine. General.
- **`FLEET-TREAD-DEPTH-AUDIT`, `MICHELIN-CASING-INSPECTION`,
  `MICHELIN-RETREAD-EVALUATION`** are gauge-and-eye inspections. General. A tire
  shop will do them in the tire bay by habit, which the ranking rule accommodates
  without making them specialty.
- **`TRANSMISSION-SERVICE`** needs a fluid-exchange machine, which is arguably
  specialty equipment — but there is no bay type for it, and inventing one to hold a
  single service is worse than treating it as general. If a shop confines
  transmission work to specific bays, that is a new `BayType` and a new map row.

#### D14.1.1 — what the specialty map removes from the plan

| Withdrawn | Was |
|---|---|
| `service_requirement_profile` (§4.1) **as a capability header** | Header/detail to distinguish unconstrained from unconfigured *on the bay axis* — the profile stays as CAP-329's skill-requirements header |
| `service_capability_requirement` (§4.1) | Per-service capability rows |
| `ext_service_capability` replica (§4.2) | Catalog validating codes against a location-owned registry |
| `location.service-capability.updated` fact (§5) | Publishing that registry |
| `PUT /v1/products/services/{id}/requirements` (§6.1) | Per-service configuration endpoint |
| `requiredCapabilityCodes` on `ServiceDto` (§6.1) | Per-service declaration on the read model |
| §7.1's catalog seed | Mapping 24 services onto registry codes |
| §7.2's bay bootstrap and per-bay authoring | Asserting equipment for 24 bays |
| `SERVICE_REQUIREMENTS_NOT_CONFIGURED` | A third `noOpeningReason` value |

What survives, and is now the whole of CAP-325's capability axis: the specialty map
of D14.1, `max_duty_class` on the bay (D13), the `BayResponse` `@Schema` and rename
fixes, and `GET /v1/service-capabilities` **only if** the 20-row registry is still
wanted as a display vocabulary — it is no longer load-bearing for eligibility, and
CAP-325 should say explicitly whether it is kept or retired.

**The specialty map lives in `pos-location`**, keyed `(bay_type, operation_code)`,
because bay types are `pos-location`'s and the map is a statement about bays. It
holds catalog `operationCode`s, so `pos-location` needs an `ext_catalog_service`
replica off the existing `catalog.service.updated` fact to validate them — the one
piece of new cross-module plumbing that survives.

#### D14.3 — how the map and the per-bay list relate *(set during implementation)*

The map (D14.1) is type-level configuration; the per-bay `serviceCapabilityCodes`
is what consumers read from the fact. They relate by **defaulting**, with the
platform's usual null-versus-empty discipline:

| Caller sends | Result |
|---|---|
| `serviceCapabilityCodes` **absent** (null) on create | The bay takes its type's rows from the map. An `ALIGNMENT` bay gets `WHEEL-ALIGNMENT-4-WHEEL`; a `GENERAL_SERVICE`, `HEAVY_DUTY` or `WASH_DETAIL` bay gets nothing, because the seed gives those types no rows — which is exactly "declares nothing" |
| An **explicit list**, including an explicit `[]` | The caller's own claim, validated code-by-code against `ext_catalog_service` (active codes only), stored uppercased. An explicit `[]` on an `ALIGNMENT` bay means "this alignment bay has no working rack" and is honoured |
| A **patch that changes `bayType`** without stating codes | The bay **re-defaults** to the new type's rows. Otherwise a bay retyped to `GENERAL_SERVICE` would keep an alignment claim it no longer has the rack for, violating D14 rule 3 |
| A patch stating codes, with or without a type change | The stated list wins, validated as above |

*Amended 2026-09-26 (`durion-positivity-backend#2245`, DECISION-LOCATION-025,
DECISION-SHOPMGMT-021):* consumers **do** need the map. The per-bay list says what a
bay claims; only the map says whether an operation is specialty at all, and
deriving that from the active bays at a location turned missing equipment into
general work. `pos-location` publishes the map per tenant as
`location.bay-specialty-map.updated`. The retype-resets rule above stands.

Why defaulting rather than deriving at read time: the fact must carry a concrete
per-bay list so consumers never need the map, and a shop whose equipment differs
from its type's default (a tire bay with no balancer) needs somewhere to say so.
Deriving at read time would make the second impossible; storing without a default
would make every bay creation a data-entry task the map exists to remove.

Validation resolves against `ExtCatalogServiceReplicaRepository
.findByOperationCodeInAndActiveIsTrue`, so a code whose service pos-catalog has
retired fails the same way an unknown one does, while the replica keeps the
tombstoned row so the two remain distinguishable to anyone who asks.

### D14.2 — the location-owned capability registry is retired; mobile units claim operation codes *(ruled 2026-09-16)*

D14.1 left one question open: whether `service_location_capabilities` (the 20-row
registry `R__seed_location_1_reference.sql` seeded) survives as a display vocabulary.
It does not. Ruled on `#482` by the owner:

- **Retired.** `service_location_capabilities`, `mobile_unit_capabilities` and the
  registry's seed block are dropped (pos-location V5). Nothing scheduled against the
  registry once bays moved to the specialty map; a vocabulary nothing reads drifts.
- **Mobile units claim catalog operation codes**, exactly as bays do:
  `MobileUnitRequest/Response.serviceCapabilityCodes`, validated against the
  `ext_catalog_service` replica by the same `ServiceCapabilityCodeValidator` (active
  code, UPPER-DASH, case-insensitive; unknown or retired → 422), now also a PATCH key so
  an incomplete unit can be completed after creation. `MobileUnitUpdatedV1` is schema v2
  with the codes, additively (ADR-0044 §3); a consumer reads null as "claims nothing".
- **Existing unit claims are not carried across.** A registry capability
  (`BRAKE_SERVICE`) is coarser than an operation code; inventing the mapping in a
  migration would assert equipment nobody declared. The alpha seeder re-declares each
  unit's codes from `location/mobile-units.csv`, rewritten to Tier 0 operation codes a
  van can actually perform on site — alignment, transmission and DOT annual inspection
  registry codes were dropped rather than mapped, and the seeder's test pins every code
  to `tier0-services.csv`.
- **One vocabulary for "what can this resource perform".** When mobile units become
  schedulable, D14's eligibility rule applies to them unchanged.
  *Amended 2026-09-26 (`durion-positivity-backend#2245`, DECISION-SHOPMGMT-023):*
  except for the general default — a unit performs only the codes it claims, and
  only for its base location.

### D15 — credentials are re-ingested with real dates, not migrated *(settled)*

Settled 2026-09-16. The 23 rows in
`scripts/fixtures/seed/alpha/shop-manager/mechanic-skills.csv` carry no issuer and no
dates, and `person_credential.issued_on` is `NOT NULL` (§4.4), so there was a choice
between a sentinel date, a nullable column, and re-ingestion.

**Re-ingest.** The fixture gains `issuer`, `certifiedDate` and `expirationDate` with
real values, and the dateless rows are replaced rather than carried forward with a
fabricated `issued_on`. A sentinel would have been indistinguishable from a real
date to every downstream reader, and the whole point of D7's credential aggregate is
that "was this person qualified on date X" has a true answer — a sentinel makes it
confidently wrong instead of absent.

Requirements on the re-ingest:

- `issuer` is `ASE` for all 23 existing rows; the ASE cycle is five years, so
  `expirationDate` is `certifiedDate` + 5 years unless deliberately varied.
- **At least one deliberately expired row**, so `#2022` AC8 (now CAP-328's) has
  something to bite on rather than passing against empty data.
- At least one row with a **null** `expirationDate`, exercising "does not expire".
- Dates spread across technicians rather than uniform, so an expiry-boundary test is
  not accidentally testing one date.
- No `person_credential` row is created without an `issued_on` — the column stays
  `NOT NULL` and needs no migration shim, per the pre-production policy.

### D17 — the three CAP-326 items the mechanism ruling escalated, answered

The Shop Management domain agent ruled on the `BAY_DOUBLE_BOOKED` concurrency
mechanism (exclusion constraint as enforcement, the existing pre-check reshaped as
reporting — recorded in `durion#483`) and escalated three items. At the owner's
request it then drafted answers for ratification. Every checkable claim below was
verified against the repository.

**Item 1 — `maxConcurrentVehicles` is a physical attribute of the bay, not a booking
multiplicity.** *Recommendation for ratification; default if not ruled.* Every bay is
exactly one exclusive booking resource, `BAY_DOUBLE_BOOKED` is N=1 for all bays, and a
shop wanting two independently bookable stalls registers two bays. The DTO's wording
("serviced concurrently", example `2`) leans two-slot; everything else says one
resource: all 24 fixture bays are capacity 1, pos-location uses the value only for
`>= 1` validation and never publishes it, every shopmgmt record treats capacity as
facility-level and SOFT, and DECISION-SHOPMGMT-003 chose exclusive assignment *in
order to* keep capacity a facility count. When a field's name and its ecosystem
disagree, the ecosystem is the better witness. Under this reading the constraint is
correct for every bay forever; under the two-slot reading it would over-refuse a
capacity-N bay — fail-closed, visible, and no such bay exists. Tighten the pos-location
`@Schema` to: *"Number of vehicles the bay physically accommodates at once. A bay is a
single bookable resource regardless of this value; register separate bays for
independently bookable stalls."*

**Item 2 — "deterministically" means exactly one winner, not a specified winner.**
*Recommendation; default if not ruled.* No ordering rule or ordering key exists
anywhere: zero hits for priority, seniority or tie-break across the decision records
and `AppointmentsServiceImpl`; the request carries no priority field; the headers carry
roles, not rank. A specified winner would need a key nobody defined, a serialization
point on every write path, and a UI that discloses another advisor's booking to explain
a millisecond race. Every interleaving yields a permitted state (one booking), so which
party holds it is not an invariant the domain stated. `durion#483`'s criterion is
reworded: *"Two advisors booking the same bay and an overlapping window concurrently:
exactly one appointment is created. The other is refused with a HARD
`BAY_DOUBLE_BOOKED` conflict and no appointment row. Which wins is whichever commits
first; no ordering is specified or guaranteed."*

**Item 3 — the keyless exact resubmission was a latent double-booking. Decided.**
`AppointmentsServiceImpl.differsFromRequestedSlot` (`:246-255`) deliberately let a
same-bay, same-window resubmission without an `Idempotency-Key` create a **second
appointment** — the pinning test (`AppointmentsServiceNewBehaviorsTest:556-597`)
asserts a new id. It was a PR-review coverage follow-up with no story and no
recoverable rationale, and it produces exactly the outcome DECISION-SHOPMGMT-014
exists to prevent ("duplicates must not create duplicates … resilience to UI
double-submits"). **Delete the carve-out. A keyless exact duplicate replays the existing
appointment with 200** — not a new row, not a 409, and not a `BAY_DOUBLE_BOOKED`
conflict, which would tell an advisor their own booking blocks them and write a
phantom self-collision into the audit DECISION-002's override monitor reads. The
identity tuple is (location, resource, customer, vehicle, window, workorderLinkRef,
normalized serviceRequestIds) — the comparison `ensureIdempotentRequestMatches`
already makes, minus the key. In the `23P01` handler, re-query for an exact match in a
fresh transaction before writing a conflict row, so two simultaneous identical
keyless submits also replay rather than collide. Tests: invert the pinning test to
`never().save()` + existing id; retarget the five overlap tests from
`AppointmentValidationException` to `SchedulingConflictException(BAY_DOUBLE_BOOKED)`;
add non-terminal-status widening and Testcontainers coverage of the constraint.

**Three adjacent defects surfaced by item 3, all verified, all CAP-326's or on the
housekeeping list (§9.2):**

- **`SchedulingConflictException` is unmapped.** `GlobalExceptionHandler`
  (`internal/controller/`) handles `AppointmentValidationException` at `:86` and has no
  handler for the conflict exception, so it falls through to a 500 despite the create
  javadoc promising 409. CAP-326 adds the 409 mapping with the DECISION-002 envelope,
  and remaps a reused key with a *different* body from 400 to 409 — a conflict, not
  malformed input.
- **`Idempotency-Key` is optional** (`AppointmentsController:154`, `required = false`),
  and nothing in-repo sends one, so keyless is the common path, not the edge.
- **DECISION-SHOPMGMT-014 chose a body `clientRequestId`; the code implemented the
  rejected header option**, has no retention window, and
  `AppointmentCreateRequest:17` documents a field that does not exist. A client built to
  the decision record gets no idempotency at all. Not CAP-326's — amend the record to
  ratify the header, or add the field.

### D18 — the conflict model's three open points, ruled during implementation

Put to the Shop Management domain agent 2026-09-16 while CAP-326's model was being
built; every checkable claim was verified against the repository.

**D18.1 — the HOURS rules are seeded; the catalog is eight rows, not six.**
`#483` asks for both "six rules exactly as named" and "booking outside operating
hours is rejected HARD". A persisted conflict needs a `conflict_rule` row to
reference, and DECISION-SHOPMGMT-002's own DDL reserved `HOURS` in
`conflict_resource_type` for exactly this. Two rules join on the footing the two
`SKILL` rows already have — an extension of the seeded rules, not an amendment:

| `conflict_rule.code` | Resource | Severity | Fires when | Remedy |
|---|---|---|---|---|
| `OUTSIDE_OPERATING_HOURS` | `HOURS` | HARD | the window falls outside the day's open–close, or spans midnight | move the time |
| `FACILITY_CLOSED` | `HOURS` | HARD | the local date has no window (weekday closed, `"[]"` configured-closed) or is a dated closure | move the day |

One rule for both closure kinds because D10.1's test is remedy, and a Sunday and a
holiday share one. `FACILITY_CLOSED` rather than DECISION-008's illustrative
`FACILITY_CLOSED_HOLIDAY` because the same rule covers a plain closed weekday, and a
misnamed code in a one-namespace contract is permanent. A booking on a closed day is
refused as closed, never as full: 409, one HARD `FACILITY_CLOSED`, a
`scheduling_conflict` row with `appointment_id NULL`, no appointment. HOURS is
evaluated before BAY so a closed day never reaches the exclusion constraint. Time is
facility-local via `ExtLocationReplica.timezone` (DECISION-015). The parsing already in
`ScheduleCapacityServiceImpl` (`parseOperatingHours`, `parseHolidayClosures`) is
extracted and shared, not copied.

*Owner's call, default given:* hours never published, timezone blank or no replica
row — the HOURS rules do not fire, a WARN is logged, no conflict row is written. An
unknown fact is not a confirmed closure (the capacity read's own principle, and D11);
DECISION-008's "warning mode first" tolerates it. Never report the unknown case as
`FACILITY_CLOSED`.

**D12 is corrected:** operating hours *do* reach `pos-shop-manager` now — `#2023`'s
delivery (`V2__schedule_capacity_replica_columns.sql`) added `timezone`,
`operating_hours` and `holiday_closures` to `ext_location`, and the listener merges
them. The prerequisite D12 recorded is met.

**D18.2 — `conflict_rule` is platform reference data: `@TenantGlobal`.** One row set
for every tenant, no `tenant_id`, no RLS, listed in `tenancy-global-tables.txt`. The
rule code is the API contract and severity is a property of the rule (D10); a
tenant-scoped catalog would let a tenant deactivate `BAY_DOUBLE_BOOKED` while the
exclusion constraint kept enforcing it, and the audit query would join against rows a
tenant could edit. It is the same kind of thing as the permission catalog, and D2
settled the analogous question for the skill registry. A later per-tenant toggle is an
additive overlay table, never a re-scoping. `scheduling_conflict` and
`conflict_override` stay tenant-scoped; their FK to `conflict_rule` is single-column.

**D18.3 — `override_record` is retired; `ConflictOverrideService` writes
`conflict_override`.** Two override tables would be the parallel path `#483` forbids,
and the old one is the one whose audit cannot run (no severity, no rule). Its gate
was also wrong under D12: `shop:schedule:edit OR appointments:reschedule` is held by
`DISPATCHER`, so a non-manager can override today. The endpoint keeps its path and
post-hoc shape — a SOFT conflict at create/reschedule warns and allows, its rows are
persisted with `appointment_id` set, and the manager records the override against
them afterwards — with the body becoming `{conflictIds, overrideReason}`. Every
`conflictId` must belong to the appointment (400 otherwise) and be SOFT and not yet
overridden (409 with the DECISION-002 envelope otherwise, and no row — which is what
keeps "HARD with override = zero" true). Single-actor approval: `approved_by =
overridden_by`, `approved_at = created_at`, so DECISION-002's second audit query holds
without a two-step flow. Rows are immutable (DECISION-007); no separate
`conflict_override_audit` table — the join carries every column it lists.
`appointment.is_conflict_override` and `reschedule_history.conflict_overridden` are
dropped: the first is read by no response, the second is written unconditionally
`false` and never read. Gate: `shop:conflict:override` plus the ADR-0061 location-scope
guard, on controller and service interface both.

*Owner's call, default given:* a refused HARD attempt writes no row; the event stream
records it. A nullable `outcome` column is the later addition if rejected attempts
must be queryable.

**D17 is corrected:** the exclusion-constraint migration is not `V4`; `V5` is the
assignment-status migration, so the conflict tables and the constraint land at `V6+`.
And the role grants: `SHOP_MANAGER`'s goes in `R__seed_role_permissions.sql`;
`LOCATION_MANAGER`'s and `GENERAL_MANAGER`'s go in
`scripts/fixtures/seed/alpha/security/role-permissions.csv`, per that seed file's own
scope rule (#1613 D8) — `generate-permissions.sh --sync --grant` writes both.

### D11 — an opening names the constraints that were actually evaluated

Because the submit-time enforcement tier does not exist and is contractually the
authoritative one (§1.3†, DECISION-SHOPMGMT-011), no search result may imply a check
that did not run. Every opening carries
`constraintsEvaluated: ["BAY","DURATION","BUFFER","ROSTER", …]`.

This is the honest form of "ship the axis unenforced": an advertised omission rather
than an unenforced column. It is also what lets CAP-325 and `#2022` deliver value
before CAP-326 and CAP-328 land, without either claiming enforcement it does not have.

---

## 3. The Capability Mapping

Source: the 28 rows of `scripts/fixtures/seed/alpha/catalog/tier0-services.csv`
against the 20 rows of `R__seed_location_1_reference.sql:304-365`. Reviewed and
endorsed unchanged — all 24 mapped rows are defensible as equipment statements.

### 3.1 Mapped (24 of 28)

| `operation_code` | `capability_code` |
|---|---|
| `WHEEL-ALIGNMENT-4-WHEEL` | `ALIGNMENT` |
| `OIL-CHANGE-FULL-SYNTHETIC` | `OIL_CHANGE` |
| `BRAKE-PAD-REPLACE-FRONT` | `BRAKE_SERVICE` |
| `BRAKE-PAD-REPLACE-REAR` | `BRAKE_SERVICE` |
| `COOLANT-SYSTEM-FLUSH` | `COOLING_SYSTEM` |
| `TRANSMISSION-SERVICE` | `TRANSMISSION` |
| `BATTERY-REPLACEMENT` | `BATTERY` |
| `DOT-ANNUAL-INSPECTION` | `DOT_INSPECTION` |
| `FLEET-PM-A-SERVICE` | `PM_SERVICE` |
| `FLEET-PM-B-SERVICE` | `PM_SERVICE` |
| `TIRE-INSTALL-SET-4` | `TIRE_SERVICE` |
| `TIRE-INSTALL-LT-SET-4` | `TIRE_SERVICE` |
| `TIRE-INSTALL-COMMERCIAL-SINGLE` | `TIRE_SERVICE` (+ `MEDIUM_HEAVY` duty class, D5) |
| `TIRE-REPAIR-PATCH-PLUG` | `TIRE_SERVICE` |
| `TIRE-ROTATION` | `TIRE_SERVICE` |
| `WHEEL-BALANCE-SET-4` | `TIRE_SERVICE` |
| `ROAD-FORCE-BALANCE-SET-4` | `TIRE_SERVICE` |
| `NITROGEN-FILL-SET-4` | `TIRE_SERVICE` |
| `TPMS-SENSOR-SERVICE` | `TIRE_SERVICE` |
| `TPMS-SENSOR-REPLACE-SINGLE` | `TIRE_SERVICE` |
| `LUG-TORQUE-RECHECK` | `TIRE_SERVICE` |
| `FLEET-TREAD-DEPTH-AUDIT` | `TIRE_SERVICE` |
| `MICHELIN-CASING-INSPECTION` | `TIRE_SERVICE` |
| `MICHELIN-RETREAD-EVALUATION` | `TIRE_SERVICE` |

### 3.2 Deliberately unconstrained (4 of 28)

`AIR-FILTER-REPLACEMENT`, `CABIN-AIR-FILTER-REPLACEMENT`,
`SPARK-PLUG-REPLACEMENT`, `WIPER-BLADE-REPLACEMENT` need no special equipment.

Under D4 these get a **requirement profile header with no capability children** —
not a sentinel row. `SPARK-PLUG-REPLACEMENT` is unconstrained by omission rather
than by nature: the registry has `ENGINE_DIAGNOSTICS` but no engine *repair* row.

### 3.3 Known coarseness, and what D5 resolves

| Case | Coarseness | Status |
|---|---|---|
| `TIRE-INSTALL-COMMERCIAL-SINGLE` | Needs a truck-capable bay | **Resolved by D5** — requires `MEDIUM_HEAVY`; no new registry row |
| `ROAD-FORCE-BALANCE-SET-4` | A road-force balancer is distinct equipment | Add a `ROAD_FORCE_BALANCE` registry row *if* a shop distinguishes it. Note D9.1: under the rejected model this row would have silently changed who was certified |
| `LUG-TORQUE-RECHECK`, `FLEET-TREAD-DEPTH-AUDIT`, `MICHELIN-*-INSPECTION` | Inspections, arguably unconstrained | Mapped to `TIRE_SERVICE` on the grounds that they are performed in a tire bay in practice |
| `DOT_INSPECTION` | Not an equipment statement at all | See §6.4 — inspection authority is a credential and possibly a `shop_qualification` (§1.2†), never a bay capability alone |
| Distribution | 12 of 28 services require `TIRE_SERVICE`; 11 of 20 registry rows have no service | Expected. The registry is forward-looking, and the filter's job is to separate alignment, DOT and oil work from the rest |

---

## 4. Schema

### 4.1 `pos-catalog` — requirement profile (header) and its children

```sql
CREATE TABLE public.service_requirement_profile (
    tenant_id uuid DEFAULT public.app_current_tenant() NOT NULL,
    service_id uuid NOT NULL,          -- 1:1 with service
    configured_at timestamp with time zone NOT NULL,
    configured_by character varying(255),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT service_requirement_profile_pkey PRIMARY KEY (service_id),
    -- The tenant-scoped key every child and §7.1's ON CONFLICT (tenant_id, service_id) use.
    CONSTRAINT service_requirement_profile_tenant_key UNIQUE (tenant_id, service_id),
    CONSTRAINT service_requirement_profile_service_id_fkey
        FOREIGN KEY (tenant_id, service_id) REFERENCES public.service(tenant_id, id) ON DELETE CASCADE
);
-- As shipped: pos-catalog V5__service_requirement_profile.sql. Children reference
-- (tenant_id, service_id) — service_skill_requirement carries
-- FOREIGN KEY (tenant_id, service_id) REFERENCES service_requirement_profile(tenant_id, service_id)
-- and UNIQUE (tenant_id, service_id, skill_id).

CREATE TABLE public.service_capability_requirement (
    tenant_id uuid DEFAULT public.app_current_tenant() NOT NULL,
    id uuid NOT NULL,
    service_id uuid NOT NULL,          -- FK to the profile
    capability_code character varying(100) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
```

- Absent profile row = **not configured**. Profile with no children = **unconstrained** (D4).
- Unique `(tenant_id, service_id, capability_code)`; FK `service_id` → `service(id)`,
  tenant-scoped.
- `capability_code` width matches the registry's `code`. No FK on it — validated
  against the replica of §4.2, because the vocabulary is owned by another module.
  With the sentinel gone, that statement now holds without exception.
- Tenant-scoped per `docs/TENANCY_SCHEMA.md`; not a `tenancy-global-tables.txt` entry.
- `service_skill_requirement` (§4.5) is the profile's second child, added by CAP-329.

### 4.2 `pos-catalog` — capability replica

```sql
CREATE TABLE public.ext_service_capability (
    tenant_id uuid DEFAULT public.app_current_tenant() NOT NULL,
    capability_id uuid NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(255),
    active boolean NOT NULL,
    aggregate_version bigint NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
```

Unique `(tenant_id, code)`. Mirrors `ExtBayReplica`'s shape
(`pos-shop-manager/.../entity/ExtBayReplica.java:37-57`), including
`aggregate_version` as the monotonic stale-event guard.

### 4.3 `pos-location` — bay gains a duty-class ceiling (D5)

`bays` gains `max_duty_class` — a `gvwr_class` **ceiling** per D13 (nullable meaning
unconstrained), not a token — alongside the existing `service_capability_ids`. `BayType` becomes
display-only.

The existing `service_capability_ids` column and its JSON-text `List<String>`
treatment stay as they are; the field and its `@Schema` examples are renamed and
corrected per §6.2.

### 4.4 Skill registry and credentials *(CAP-328)*

```sql
-- platform reference data, @TenantGlobal (D2); precedent VehicleType.java:16
CREATE TABLE public.skill (
    id uuid NOT NULL,
    code character varying(64) NOT NULL,     -- BRAKES-LIGHT, BRAKES-MEDIUM_HEAVY
    name character varying(255) NOT NULL,
    competence_code character varying(64) NOT NULL,   -- BRAKES
    duty_class character varying(16) NOT NULL,        -- LIGHT | MEDIUM_HEAVY | ANY
    active boolean NOT NULL, …
);

-- ADR-0059 §3 / service_operation_xref shape (D8)
CREATE TABLE public.skill_code_xref (
    id uuid NOT NULL,
    skill_id uuid NOT NULL,
    source_code character varying(32) NOT NULL,          -- 'ASE'
    source_skill_code character varying(128) NOT NULL,   -- 'A5-BRAKES', 'T4-BRAKES'
    …
);
```

Credential holdings in **`pos-people`** (D6), as an aggregate (D7):

```sql
CREATE TABLE public.person_credential (
    tenant_id uuid DEFAULT public.app_current_tenant() NOT NULL,
    id uuid NOT NULL,
    person_id uuid NOT NULL,
    skill_id uuid NOT NULL,                  -- what it certifies
    issuer character varying(32) NOT NULL,   -- ASE | EPA_609 | STATE_xx | SHOP
    source_code character varying(32),       -- as received
    source_credential_code character varying(128),
    issued_on date NOT NULL,
    expires_on date,                         -- nullable: not every credential expires
    proficiency integer,                     -- display metadata only (D7)
    status character varying(16) NOT NULL,   -- ACTIVE|EXPIRED|REVOKED|SUPERSEDED
    evidence_ref uuid,                       -- 49 CFR 396.19 retained evidence
    source_system character varying(64), source_version character varying(64),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
```

Natural key `(person_id, skill_id, issuer, issued_on)` so **a renewal is a new row,
not an overwrite** (D7). `status` is derived from `expires_on` and recomputed on read
or by a daily job — never trusted from the feed.

`pos-shop-manager` holds `ext_person_credential`, mirroring
`ext_people_staffing_assignment`, and **deletes `mechanic_skill` and
`certification`**. Pre-production policy (`CLAUDE.md`) permits the removal without a
shim.

### 4.5 `pos-catalog` — skill requirement *(CAP-329)*

```sql
CREATE TABLE public.service_skill_requirement (
    tenant_id uuid DEFAULT public.app_current_tenant() NOT NULL,
    id uuid NOT NULL,
    service_id uuid NOT NULL,                        -- FK to the profile (§4.1)
    skill_id uuid NOT NULL,
    applies_to_duty_class    -- a gvwr_class range per D13, or ANY
    …
);
```

D8: the class sits here because this is the one place it is genuinely a property of
the pair. No `min_proficiency` column (D7).

### 4.6 `pos-shop-manager` — replica extensions

`ext_bay` gains `bay_type`, `service_capability_codes` (same
`StringListJsonConverter` treatment as `BayEntity`), `max_concurrent_vehicles` and
`max_duty_class`. `ext_catalog_service` is new: `service_id`, `name`,
`operation_code`, `requirement_profile_configured_at`, `required_capability_codes`,
`required_skill_ids`, `active`, `aggregate_version`, `updated_at`.

`pos-shop-manager` has no `CatalogEventsListener` today; one is added alongside its
existing `LocationEventsListener`.

**In the same change, resolve `shop_service.service_entity_id`** — a `bigint`
holding what is a UUID in catalog (§1.1†). Delete or re-key it. Adding a second
service-reference table to this module while the first cannot hold a valid reference
leaves the next reader to pick the wrong one.

---

## 5. Fact Contracts

Additive within schema, per ADR-0044 §3 — existing consumers unaffected, new-version
consumers treat the fields as absent on old events.

| Fact | Change |
|---|---|
| `CatalogServiceUpdatedV1` → **v3** | Append `requirementsConfiguredAt: @Nullable Instant` and `requiredCapabilityCodes: @Nullable List<String>`. Null `configuredAt` = not configured; present with an empty list = unconstrained (D4). No sentinel to collapse. The delete tombstone publishes nulls |
| `CatalogServiceUpdatedV1` → **v4** *(CAP-329)* | Append `requiredSkills: List<{skillId, appliesToDutyClass}>` |
| `location.service-capability.updated` | **New.** `LocationServiceCapabilityUpdatedV1(capabilityId, code, name, active, createdAt, updatedAt)` on `location.events.v1`, with the same delete-tombstone convention as `CatalogServiceUpdatedV1` |
| `BayUpdatedV1` | Append `serviceCapabilityCodes`, `maxConcurrentVehicles`, `maxDutyClass`. `bayType` is already published and currently dropped by `ExtBayReplica`. Backfill/replay so existing bays fill (`#1668` precedent) |
| `people.person-credential.updated` | **New** *(CAP-328)*. Carries the credential aggregate, including `expiresOn` and `status` |
| `skill` registry facts | **New** *(CAP-328)*. `@TenantGlobal` reference data; consumers hold a replica |

Kafka checks (`scripts/generate-kafka-topics.py --check`,
`scripts/check-kafka-topic-drift.sh`) must pass.

---

## 6. API Surface

### 6.1 `pos-catalog`

`ServiceDto` gains `requirementsConfiguredAt` and `requiredCapabilityCodes`
(nullable per D4), and later `requiredSkills`. It serves all three read endpoints —
`getServiceById` (`ProductController.java:799`), `listServicesByName` (`:835`),
`searchCatalogServices` (`:862`) — so all three gain the fields at once. No new read
permission: `catalog:service_type:view` covers it. These are read-only projections
and correctly carry no `@EmitEvent`.

Writing a requirement is a state change:
`PUT /v1/products/services/{serviceId}/requirements` carries
`@EmitEvent(id = "CATALOG_SERVICE_REQUIREMENTS_SET", apiVersion = "1")` with the
`write` preset, registered in `pos-catalog`'s `{Module}EventTypes`, gated on a new
`catalog:service_requirement:manage` permission in `CatalogPermissionRegistry`.
Writing an empty capability list is how a service is declared **unconstrained**;
it creates the profile header with no children.

A code absent from `ext_service_capability` is rejected `422` naming the offending
value, matching `BayServiceImpl.java:298-300` and
`domains/shopmgmt/.business-rules/AGENT_GUIDE.md:446`.

### 6.2 `pos-location`

`GET /v1/service-capabilities` returning `{id, code, name, active}`, permission
`location:service_capability:read`. This is the ready-now carve-out — no dependency
on anything else here.

Also independent: `BayResponse.serviceCapabilityIds` and `.skillRequirementIds`
(`BayResponse.java:62, 68`) carry `@Schema` examples showing UUIDs for values that
are codes — `BayController.java:50` shows the truth, `["ALIGNMENT"]`. Fix the
examples and rename the fields to `serviceCapabilityCodes` /
`skillRequirementCodes`; the pre-production policy permits the rename without a
shim. `skillRequirementCodes` is then **removed** when CAP-328 lands, since a bay does
not hold a competence requirement — the service does.

### 6.3 `#2022` — contract corrections

- **Drop `vehicleClass`; add optional `vehicleId`.** Nothing can populate a
  `vehicleClass` (§1.4), and it admits the meaningless
  `TIRE-INSTALL-COMMERCIAL-SINGLE` + `LIGHT` query. `Appointment.crmVehicleId` is
  already mandatory, so at booking time the vehicle is known; at *search* time there
  is no appointment yet, so `vehicleId` is optional and the server resolves the duty
  class. Absent `vehicleId`, the requirement resolves to the `ANY`-class skill and
  the response states that the class was not determined.
- **Add `constraintsEvaluated`** to every opening (D11).
- **Add `skillFulfillment: CERTIFIED | AWAITING`** and the unmet skill codes,
  ranked certified-first (D10 — settled).
- **Move the rostered-absence condition out of `noOpeningReason` — the condition is
  real, the field was wrong.** It is reported as
  `staffingAdvisory.code = NO_COMPETENT_MECHANIC_ROSTERED`, which retires the
  original `NO_CERTIFIED_TECHNICIAN_ROSTERED` spelling (D10.1: one namespace shared
  with `conflict_rule.code`). It is not unreachable: nobody-competent-rostered
  happens today at CLT-MAIN-001 and CLT-NORTH-001 (D10.2). But `noOpeningReason` is
  "set only when openings is empty", and a skill shortfall never empties the list,
  so it can never be the cause. It surfaces instead as a response-level
  `staffingAdvisory` returned **alongside a non-empty openings list**.
  `noOpeningReason` keeps three values: `NO_ELIGIBLE_BAY_AT_LOCATION`,
  `ALL_ELIGIBLE_BAYS_BOOKED`, `SERVICE_REQUIREMENTS_NOT_CONFIGURED` — the last now
  answerable from the profile header rather than from a null-versus-empty guess.
- **Add `staffingAdvisory { code, missingSkillCodes[], absenceScope }`** at the
  response level (D10.2).

### 6.4 DOT inspection authority

`DOT_INSPECTION` must not resolve to a preventive-maintenance competence alone. A
technician may do PM all day without being a qualified annual inspector; 49 CFR
396.19 qualifies the inspector by training or experience and requires the shop to
retain the evidence. Neither `mechanic_skill` (no writer for its dates) nor
`certification` (no dates at all) can hold an issuer, an expiry or an evidence
reference.

Under D7/D8 this is two registry rows — `PMI`, which ASE T8 cross-references, and
`DOT-INSPECTOR`, with no ASE xref, an issuer, an expiry and an `evidence_ref`. The
shop half may belong on the unqueried `shop_qualification` table (§1.2†).

Constraint on `#2022`: `noOpeningReason` and `skillFulfillment` must not imply
regulatory eligibility. Scheduling a qualified-looking technician is not a claim
that the inspection is lawful.

### 6.5 Contract chain

Regenerate `pos-catalog/openapi.yaml`, `pos-location/openapi.yaml`,
`pos-shop-manager/openapi.yaml` and, for CAP-328, `pos-people/openapi.yaml`; update
`permissions.yaml` (ADR-0025); run `API Artifacts Sync` for the touched modules;
update the Angular SDK.

---

## 7. Data Loads

### 7.1 Service → capability (`pos-catalog`, repeatable migration)

`R__seed_reference_catalog_8_capability_requirements.sql`, mirroring the shape
proven by `R__seed_reference_catalog_6_labor_guide.sql:22-34` — md5-derived
deterministic ids, `ON CONFLICT` idempotency, joined on `operation_code`:

```sql
SET TIME ZONE 'UTC';

-- Header first: every mapped service is "configured", including the §3.2 four.
INSERT INTO service_requirement_profile (service_id, configured_at, created_at, updated_at)
SELECT s.id, NOW(), NOW(), NOW()
FROM service s
WHERE s.operation_code IS NOT NULL
  AND s.operation_code IN ( /* §3.1 and §3.2 codes */ )
ON CONFLICT (tenant_id, service_id) DO NOTHING;

-- Children: §3.1 only. The §3.2 four get a header and no children (D4).
INSERT INTO service_capability_requirement (id, service_id, capability_code, created_at, updated_at)
SELECT md5('scr:' || s.operation_code || ':' || m.capability_code)::uuid,
       s.id, m.capability_code, NOW(), NOW()
FROM service s
JOIN (VALUES
    ('WHEEL-ALIGNMENT-4-WHEEL', 'ALIGNMENT'),
    ('OIL-CHANGE-FULL-SYNTHETIC', 'OIL_CHANGE')
    -- … §3.1 in full
) AS m (operation_code, capability_code) ON m.operation_code = s.operation_code
WHERE s.operation_code IS NOT NULL
ON CONFLICT (tenant_id, service_id, capability_code) DO NOTHING;
```

Tenancy: bind the tenant transaction-locally, as `R__seed_location_1_reference.sql:2`
already does with `set_config('app.current_tenant', …, true)`.

### 7.2 Bay → capabilities (`pos-location`, fixture + bootstrap) *(revised)*

**This is the load without which nothing works.** No bay in any environment holds a
capability.

**Author the capabilities per bay in `bays.csv`.** Twenty-four rows is an afternoon
and it is the honest artifact. DECISION-SHOPMGMT-012 makes facility scoping explicit
and deny-by-default: which bays *here* can do what is a statement each facility makes
about its own equipment, not a property of a type name. The model already supports
the per-bay statement — the gap is data, not model. `bays.csv` gains a
`serviceCapabilityCodes` column (pipe-separated) and a `maxDutyClass` column.

A `bay_type` bootstrap is still needed for bays created before the columns existed,
but it must be **minimal and conservative** — only what the type name literally
asserts:

| `BayType` | Capabilities | `max_duty_class` |
|---|---|---|
| `ALIGNMENT` | `ALIGNMENT` | `LIGHT` |
| `TIRE_SERVICE` | `TIRE_SERVICE` | `LIGHT` |
| `INSPECTION` | `DOT_INSPECTION` | — |
| `GENERAL_SERVICE` | `OIL_CHANGE`, `PM_SERVICE`, `BRAKE_SERVICE`, `TIRE_SERVICE`, `BATTERY`, `COOLING_SYSTEM` | `LIGHT` |
| `HEAVY_DUTY` | none | `MEDIUM_HEAVY` |
| `WASH_DETAIL` | none | — |

Changes from the first revision, and why:

- **`GENERAL_SERVICE` gains `PM_SERVICE`.** Without it, fleet PM A/B is unbookable
  at ATX-RIV-001, whose three bays are two `GENERAL_SERVICE` and one `TIRE_SERVICE`
  with no `INSPECTION` bay (`bays.csv:22-24`). PM A/B is oil, filters, lube and a
  checklist — a general bay's core work and recurring contract revenue, reported as
  zero capacity.
- **`AC_SERVICE`, `TRANSMISSION`, `EXHAUST` and `FUEL_SYSTEM` are dropped from
  `GENERAL_SERVICE`.** The first revision described the list as "everything a
  general bay can do without special equipment" and then included work needing a
  refrigerant recovery machine, a fluid exchange machine and a welder. `AC_SERVICE`
  was the worst entry: its *technician* requirement is regulatory and no model here
  expresses it.
- **`HEAVY_DUTY` grants no capabilities.** The first revision gave it five on the
  strength of a vehicle-class label. Under D5 it sets `max_duty_class` instead,
  which is what it actually asserts.
- **Direction of error matters.** Too few capabilities makes the shop look full — a
  visible error someone complains about. Too many makes the filter a no-op and
  produces unfulfillable bookings — a silent one. The first revision erred the wrong
  way for a third of the fleet.

### 7.3 Skill registry, xref and credentials *(CAP-328)*

- Seed the `skill` registry with one row per (competence, duty class) covering the
  12 fixture codes, **plus the four real ASE certifications the truck-heavy fixture
  omits** — A1 Engine Repair, A2 Automatic Transmission, A3 Manual Drivetrain,
  A7 Heating & AC — so the light column is not structurally empty.
- Seed `skill_code_xref` with `source_code = 'ASE'` for all of them.
- Widen `mechanic-skills.csv` to carry `issuer`, `certifiedDate` and
  `expirationDate`, including **at least one deliberately expired row** so `#2022`
  AC8 has something to bite on.
- `T3-ALIGN` (`MechanicSkillBulkIngestController.java:67`) is corrected in the same
  change; under D8 it would now fail the xref rather than becoming a held skill.

---

## 8. Test Matrix

Beyond the per-AC coverage in the stories:

| Case | Expectation |
|---|---|
| Service requiring one capability | Eligible bays are those holding the code |
| Service requiring several | Intersection semantics stated and tested (all-of, not any-of) |
| Profile header, no children | Every active bay eligible; **no** warning to the client |
| No profile header | `requirementsConfiguredAt` null; client warns, does not deny |
| Service with a null `operationCode` | Reachable by `serviceId`; the seed does not cover it, so it reads as not configured |
| Unknown capability code on write | `422`, offending value named |
| Lower-case code on write | Normalized upper before validation, per `BayServiceImpl.normalizeServiceCapabilityIds` |
| Required capability no bay at **this** location holds, while another location does | Eligible capacity legitimately zero here — distinct from *not configured* |
| Registry row deactivated after a service referenced it | Defined, distinguishable behaviour; never a silent unschedulable service |
| **Duty class** — `MEDIUM_HEAVY` service against a `LIGHT`-ceiling bay | Not eligible, with a reason distinct from a capability miss |
| **Class-conditional requirement** — one unforked service, a car and a truck | Resolves to different skills (D8) |
| **Expiry on the facility-local date boundary** | Compared against the opening's facility-local date, per DECISION-SHOPMGMT-015 |
| Credential renewed | New row; the superseded row remains queryable for "qualified on date X" (D7) |
| HR sync omitting skills entirely | Existing credentials preserved, per the null-vs-empty rule already at `MechanicSyncServiceImpl.java:177-185` |
| HR sync sending an unresolvable source code | Fails the xref loudly; no phantom skill |
| **Rostered-absence with a non-empty list** | `WHEEL-ALIGNMENT-4-WHEEL` at CLT-MAIN-001: openings returned, `noOpeningReason` null, `staffingAdvisory` present, `code: NO_COMPETENT_MECHANIC_ROSTERED`, `absenceScope: NOT_AT_THIS_LOCATION` |
| **Contention, not absence** | A competent technician rostered but busy: the opening is returned flagged `AWAITING` (cause `COMPETENT_MECHANIC_UNAVAILABLE`), with no `staffingAdvisory` |
| **Zero technicians is not a competence failure** | ATX-RIV-001 raises `MECHANIC_UNAVAILABLE` and **no** competence rule (#2035 answer 5) |
| **Scope narrowing** | A location-scoped caller sees `NOT_AT_THIS_LOCATION`, never `NOT_IN_TENANT` (DECISION-SHOPMGMT-012) |
| Seed rerun | Idempotent; no duplicate rows, no id churn |

---

## 9. Decomposition and Sequencing

| | Capability | Blocked on |
|---|---|---|
| **CAP-325** | Bay eligibility axis, **as reduced by D14**: the specialty map (D14.1) in `pos-location` with an `ext_catalog_service` replica to validate its codes, `max_duty_class` on the bay (D13), the `BayResponse` `@Schema` and rename fixes. **No per-service configuration, no per-bay equipment authoring.** | Nothing. **Ready** |
| **CAP-326** | HARD-conflict tier at submit: DECISION-SHOPMGMT-002's three tables *with* severity and rule references (DDL at `DOMAIN_NOTES.md:161-185`, audit queries at `:260-268`), **operating hours and holiday closures added to `LocationUpdatedV1` and `ExtLocationReplica` first (D12)**, bay double-booking, `AssignmentStatusEnum` corrected to -010's six members, duplicate enum deleted, the two new SOFT/`SKILL` rules of D10.1 (`COMPETENT_MECHANIC_UNAVAILABLE`, `NO_COMPETENT_MECHANIC_ROSTERED`). `ConflictDetectionServiceImpl` implemented **or** deleted — never a third thing beside it | Nothing — `#2035` answered |
| **CAP-327** | Vehicle duty class, **reduced**: `gvwr_class` as an operator-set field, plus `max_duty_class` on the bay if CAP-325 has not already landed it. **No VIN decode** — there are no real VINs to decode, so it is untestable and deferred (D13.1). The decode is a later addition | Nothing |
| **CAP-328** | Credential model: `skill` registry (`@TenantGlobal`), `skill_code_xref`, `person_credential` in `pos-people`; `mechanic_skill` and `certification` deleted from shop-manager; HR payload widened; delete-then-reinsert replaced with upsert-and-supersede; roster projection stops flattening. **`#2022` AC8 moves here** | Nothing. `#2035` confirms expiry still matters: a SOFT warning must know whether the credential behind it has lapsed |
| **CAP-329** | `service_skill_requirement` in catalog, on `ServiceDto` and the fact | CAP-327, CAP-328 |
| **`#2022`** | Duration-aware opening search | CAP-326, CAP-329. Ships before them only under D11, stating what it did not evaluate |

### 9.1 `#2022` amendments required

- **AC2 splits.** The bay ∩ duration ∩ buffer ∩ unbroken-in-one-bay half is
  deliverable now and is most of the story's value. The certification half moves to
  CAP-329's consuming story.
- **AC8 moves to CAP-328.** It is not merely unimplemented, it is unimplementable
  until the HR payload is widened. Shipping it as a checkbox against columns nothing
  writes yields a test that passes on empty data and a guarantee that is false in
  production. Cutting AC8 while leaving AC2 whole is the worst of the three options:
  the story would then claim to check certification while knowingly ignoring expiry.
- **AC10** (the `ConflictDetectionService` stub) moves to CAP-326, where the decision
  belongs.
- The contract corrections of §6.3.

### 9.2 Independent housekeeping, each separately valuable

- Normalize `TechnicianRepository.java:26-30`'s bare `=` to uppercase-and-trim on
  both sides. **A silent wrong answer today, one line, needed regardless of which
  model wins.**
- Re-key or delete `shop_service.service_entity_id` (§1.1†).
- Collapse the `Mechanic`/`Technician` dual identity (§1.2†) — subsumed by CAP-328.
- Delete the duplicate `AssignmentStatus` enum — subsumed by CAP-326.
- **Fix `pos-vehicle-reference-nhtsa`'s vPIC base URL.**
  `VehicleReferenceService.java:29` uses `https://vpic.nhtsa.dot.gov/v1/vehicles`;
  the current base is `/api/vehicles`, so all six of the module's calls 404 and it
  has never run against live vPIC. One line, plus a test that does not mock the
  client into agreeing with a wrong URL. **Independent of every capability here** —
  it survives D13.1's deferral because it is a defect, not a design.
- **Map `SchedulingConflictException` to 409** in `pos-shop-manager`'s
  `GlobalExceptionHandler` (D17). Today it falls through to a 500. CAP-326 owns it, but
  it is a one-handler fix and a live wrong status code.
- **Reconcile DECISION-SHOPMGMT-014 with the code** (D17): body `clientRequestId` in
  the record, `Idempotency-Key` header in the code, a phantom field in the DTO javadoc.
  Owner's call which way; either is one small change.

### 9.3 Open items

- ~~**`#2035`** — the severity of a skill mismatch.~~ **Answered 2026-09-16; see
  D10.** No consequence outstanding: with the override at manager level, no decision
  record needs amending, and CAP-326 extends DECISION-SHOPMGMT-002's rule table with
  two rows rather than altering the record.
- **`alternateLocations[]` and `NOT_IN_TENANT`** (D10.2) need the
  DECISION-SHOPMGMT-012 scope-filtering design and belong in their own story. The
  `absenceScope` field ships now so that story is additive.
- **Override gate: role check or permission, and which managers** (D12). Mechanism
  is settled; the policy is CAP-326's to record.
- **Concurrency on a contended bay and window.** "One must lose, deterministically"
  is stated as an edge case with no mechanism. Optimistic `@Version`, a Postgres
  advisory lock and an exclusion constraint on (bay, window) are three designs with
  different failure modes. CAP-326 proposes one; it is not a business decision.
- **Tenant provisioning.** Both `service` and `service_location_capabilities` are
  tenant-scoped, so requirements are per-tenant. Seed them from the platform
  template on `tenant.created`, rather than deferring the decision to the moment it
  becomes expensive. The `skill` registry, being `@TenantGlobal`, has no such
  problem — which is a further argument for D2.
- **Specialty-map maintenance** (D14). The map is small and hand-maintained. A new
  *specialty* service that nobody adds to it reads as general and can be booked into
  a bay that cannot do it. Review the map whenever a service is added in a category
  that already has specialty members. Needs an owner; does **not** need a
  per-service configured flag, which is what D14 removed.
- ~~Is the 20-row `service_location_capabilities` registry kept or retired?~~
  **Retired, settled 2026-09-16.** D14 removed its role in eligibility and it is not
  wanted as a display vocabulary. CAP-325 drops the table, its entity, its
  repository, its 20 seeded rows and the `GET /v1/service-capabilities` endpoint that
  was going to expose it — the pre-production policy permits removal without a shim.
  `BayEntity.serviceCapabilityIds` holds catalog `operationCode`s under D14, so
  `BayServiceImpl`'s `findByCodeIn` validation against the registry
  (`:298-300`, `:322-330`) is replaced by validation against the
  `ext_catalog_service` replica.
- **`TRANSMISSION-SERVICE`** (D14.1) is general today despite needing a
  fluid-exchange machine, because no bay type exists for it. If a shop confines that
  work, it is a new `BayType` and a new map row — not a reason to invent a bay type
  for one service now.

---

## 10. Traceability

| Artifact | Reference |
|---|---|
| Capability issues | `louisburroughs/durion#482` (CAP-325), `#483` (CAP-326 conflict tier), `#484` (CAP-327 vehicle duty class), `#485` (CAP-328 credential model), `#486` (CAP-329 service skill requirement) |
| Clarification | `louisburroughs/durion-positivity-backend#2035` (SKILL severity) — **answered 2026-09-16**, recorded at D10 |
| Catalog declaration story | `louisburroughs/durion-positivity-backend#2024` |
| Opening search story | `louisburroughs/durion-positivity-backend#2022` — see §9.1 |
| Bay roster / per-date window / buffers | `louisburroughs/durion-positivity-backend#2023` |
| Bay fact precedent, incl. backfill | `louisburroughs/durion-positivity-backend#1668` |
| Cross-reference precedent | `service_operation_xref`; ADR-0059 §3 |
| Transport constraint | ADR-0044 §6 (replicas fed by facts), §3 (additive schema) |
| Module boundaries | ADR-0026 D1–D5 — nothing here belongs on a grant surface |
| Domain decisions | DECISION-SHOPMGMT-002, -007, -008, -009, -010, -011, -012, -015 |
| Identifiers, HTTP codes, OpenAPI, errors, tenancy | ADR-0013 / ADR-0027, ADR-0017, ADR-0042, ADR-0056, ADR-0062 |
| Nearest existing capabilities | CAP-165 (Product Master Data), CAP-136 (Locations, Bays, Mobile Units), CAP-137 (Appointment Scheduling), CAP-249 (Mechanic Roster & Availability) |
