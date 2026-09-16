# SPEC: Scheduling Eligibility Model

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

**One decision here is escalated, not made.** The severity of a skill mismatch is
undecided by the Shop Management domain and is not a story author's to settle —
`louisburroughs/durion-positivity-backend#2035`. Everything in §6.4 and §9 CAP-329
is written against the recommended answer and is marked where it depends on it.

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
| **†** DECISION-SHOPMGMT-002 assigns no severity to `SKILL` | Its seeded rules are `BAY_DOUBLE_BOOKED`/HARD/BAY, `MECHANIC_UNAVAILABLE`/HARD/MECHANIC, `MECHANIC_OVERTIME`/SOFT/MECHANIC, `FACILITY_NEAR_CAPACITY`/SOFT/CAPACITY. The severity every design in flight assumes was never granted → `#2035` |
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

### D10 — a skill shortfall is returned flagged, not excluded *(depends on `#2035`)*

DECISION-SHOPMGMT-010's transitions are `ASSIGNED → AWAITING_SKILL_FULFILLMENT` and
`AWAITING_SKILL_FULFILLMENT → {ASSIGNED, CANCELLED}` — never `→ IN_PROGRESS`. Read
literally, the domain's position is that a skill shortfall **parks an assignment; it
does not block booking.** Work is bookable before competence is resolved, and the
resolution path is re-assignment.

A hard eligibility filter contradicts that: it removes the advisor's ability to book
the bay now and staff it before the date. Combined with D9's false negatives, the
customer is turned away.

So an opening with no certified technician is **returned and flagged** —
`skillFulfillment: CERTIFIED | AWAITING` plus the unmet skill codes, ranked
certified-first — and booking it creates the assignment in
`AWAITING_SKILL_FULFILLMENT`, which requires the enum corrected to the decision
record's six members.

**This is a recommendation pending `#2035`, not a settled rule.** DECISION-SHOPMGMT-002
assigns no severity to a `SKILL` conflict, and assigning one is neither this spec's
nor a story author's call. If the answer is HARD-blocks-booking, §6.4 and CAP-329's
acceptance criteria change; nothing else in this spec does.

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
    service_id uuid NOT NULL,          -- PK, 1:1 with service
    configured_at timestamp with time zone NOT NULL,
    configured_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

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

`bays` gains `max_duty_class` (`LIGHT | MEDIUM_HEAVY`, nullable meaning
unconstrained), alongside the existing `service_capability_ids`. `BayType` becomes
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
    applies_to_duty_class character varying(16) NOT NULL,   -- LIGHT|MEDIUM_HEAVY|ANY
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
  ranked certified-first (D10, pending `#2035`).
- **`noOpeningReason`** keeps its four values; `SERVICE_REQUIREMENTS_NOT_CONFIGURED`
  is now answerable from the profile header rather than from a null-versus-empty
  guess.

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
| Seed rerun | Idempotent; no duplicate rows, no id churn |

---

## 9. Decomposition and Sequencing

| | Capability | Blocked on |
|---|---|---|
| **CAP-325** | Bay capability axis: §4.1–§4.3, §5 rows 1 and 3–4, §6.1–§6.2, §7.1–§7.2 | Nothing. **Ready, with §7.2 as revised** |
| **CAP-326** | HARD-conflict tier at submit: DECISION-SHOPMGMT-002's three tables *with* severity and rule references, operating hours (-008), bay double-booking, `AssignmentStatusEnum` corrected to -010's six members, duplicate enum deleted. `ConflictDetectionServiceImpl` implemented **or** deleted — never a third thing beside it | `#2035` |
| **CAP-327** | Vehicle duty class: VIN-decoded default (the NHTSA module already holds the reference data) **plus** an operator-settable override, because upfits, GVWR derates and re-registration make decodes wrong, and pre-1981 and trailer VINs do not decode. Plus `max_duty_class` on bay if CAP-325 has not already landed it | Nothing |
| **CAP-328** | Credential model: `skill` registry (`@TenantGlobal`), `skill_code_xref`, `person_credential` in `pos-people`; `mechanic_skill` and `certification` deleted from shop-manager; HR payload widened; delete-then-reinsert replaced with upsert-and-supersede; roster projection stops flattening. **`#2022` AC8 moves here** | Nothing, though `#2035` shapes what consumes it |
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

### 9.3 Open items

- **`#2035`** — the severity of a skill mismatch, and whether a shortfall blocks
  booking or only assignment. D10 is written against the recommended answer.
- **Tenant provisioning.** Both `service` and `service_location_capabilities` are
  tenant-scoped, so requirements are per-tenant. Seed them from the platform
  template on `tenant.created`, rather than deferring the decision to the moment it
  becomes expensive. The `skill` registry, being `@TenantGlobal`, has no such
  problem — which is a further argument for D2.
- **Map maintenance.** §7.1 covers the 28 services that exist. Service 29 gets no
  profile and correctly reads as *not configured*, but the map is hand-maintained
  until §6.1's write endpoint has a UI. Needs an owner.

---

## 10. Traceability

| Artifact | Reference |
|---|---|
| Capability issues | `louisburroughs/durion#482` (CAP-325), `#483` (CAP-326 conflict tier), `#484` (CAP-327 vehicle duty class), `#485` (CAP-328 credential model), `#486` (CAP-329 service skill requirement) |
| Clarification | `louisburroughs/durion-positivity-backend#2035` (SKILL severity) |
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
