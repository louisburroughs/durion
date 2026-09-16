# SPEC: Service Capability Requirements

Specifies how a catalog service declares the bay capability it requires, so that
"eligible bay" becomes a data join rather than an inference. Companion to
`louisburroughs/durion-positivity-backend#2024` (catalog declaration) and
`#2022` (duration-aware eligible opening search).

Capability: **CAP-325**. Modules: `pos-catalog` (owner), `pos-location`,
`pos-shop-manager`, `pos-domain-events`.

Deliberately **not** an ADR. This records a concrete data-and-contract design
against the existing schema; it introduces no new architectural principle. The
constraints it works within are already accepted — ADR-0044 §6 (replicas fed by
facts), ADR-0026 (module boundaries), ADR-0027 (UUID identifiers), ADR-0062
(tenancy). Where this spec chooses, it chooses inside those.

---

## 1. Summary of Findings

Verified against `durion-positivity-backend` at the time of writing.

| Finding | Detail |
|---|---|
| Catalog declares no requirement | Exhaustive grep of `pos-catalog/src` for `capabilit\|skill\|competenc\|certificat\|requirement` finds nothing modelling a service requirement. `ServiceDto` carries seven fields, none of them a requirement |
| A capability registry exists, unexposed | `service_location_capabilities` in `pos-location` — `id`, unique `code`, `name`, `active`, uppercase-normalized on write (`ServiceLocationCapabilityEntity.java:57-71`), tenant-scoped unique `(tenant_id, code)` (`V1__baseline_location.sql:178-186, 303-304`). **20 rows seeded** (`R__seed_location_1_reference.sql:304-365`). No controller, no listing endpoint, no write API |
| Bays store capability **codes**, not ids | `BayEntity.serviceCapabilityIds` is `List<String>` persisted as JSON `TEXT` (`BayEntity.java:82-90`, column at `V1__baseline_location.sql:29`), resolved by `serviceLocationCapabilityRepository.findByCodeIn(...)` (`BayServiceImpl.java:322-330`), uppercased on write (`:314-320`), unknown codes rejected (`:298-300`). The `@Schema(example = …)` on the field shows UUIDs and is wrong |
| **No bay anywhere holds a capability** | `service_capability_ids` is populated by zero rows in any migration, and `scripts/fixtures/seed/alpha/location/bays.csv` has columns `locationCode,name,bayType,maxConcurrentVehicles` — no capability column. The consumer side of the join is empty |
| Mobile units use a different identifier space | `mobile_unit_capabilities` is a real join table on UUID (`V1__baseline_location.sql:119-123`), accepting either form on input (`MobileUnitServiceImpl.java:536-575`), with no FK to the registry. One module, one registry, two identifier spaces |
| `operationCode` is nullable | `ServiceEntity.java:35` — "a dealer-created one-off service may never join a guide taxonomy". Unique only when present (partial index) |
| A service fact already exists and already carries `operationCode` | `CatalogServiceUpdatedV1` (`catalog.service.updated` on `catalog.events.v1`), schema version 2, carries `serviceId`, `name`, `shortDescription`, `longDescription`, `active`, `createdAt`, `updatedAt`, `operationCode`, `operationCategory`, `defaultLaborHours`. Published by `CatalogFactPublisher.java:235-250` |
| Two consumers of that fact already exist | `pos-marketing` (`ext_catalog`) and `pos-workorder`, both via a `CatalogEventsListener`. `pos-shop-manager` has no catalog listener; `pos-location` has no catalog replica at all |
| A cross-reference precedent already lives in catalog | `service_operation_xref` maps vendor labor-guide codes onto Durion `operation_code` (`V1__baseline_catalog.sql:455-463`), seeded by a repeatable migration that joins `service` on `operation_code` with md5-derived deterministic ids (`R__seed_reference_catalog_6_labor_guide.sql:22-34`) |
| `operationCategory` is not a substitute | Four values (`REPAIR, DIAGNOSTIC, MAINTENANCE, TIRE_SERVICE`), describing kind of work rather than equipment, with no `ALIGNMENT` member while `BayType` has one |

---

## 2. Decisions

### D1 — `pos-catalog` owns the requirement

The requirement is an attribute of the service, and `pos-catalog` owns services.

The decisive consideration is transport cost. `#2022`'s opening search is keyed by
`serviceId`. If the mapping lives in `pos-location`, answering "what does service X
require" from `pos-shop-manager` needs `serviceId → operationCode` (a catalog
replica) and then `operationCode → capability` (a location replica): two replicas,
two fact streams, a double hop per query, and a **new** fact contract to carry the
map. With the mapping in `pos-catalog` it rides `catalog.service.updated`, which
already exists and already carries both the `serviceId` and the `operationCode`
— one additive schema bump, one stream, and both consumers read it directly.

`pos-location` keeps the capability **vocabulary** (`service_location_capabilities`).
Ownership of the vocabulary and ownership of the per-service requirement are
separate, and separating them is what makes D2 necessary.

### D2 — the vocabulary crosses the boundary as `code`, and catalog validates against a replica

`pos-catalog` stores capability **codes** and validates them against an
`ext_service_capability` replica fed by a new `location.service-capability.updated`
fact (ADR-0044 §6 — never a synchronous read). The code is what `BayResponse`
already returns and what `BayServiceImpl` already resolves by; making the wire form
a UUID would require `pos-catalog` to resolve registry UUIDs it has no other reason
to hold, and would change the shipped bay contract for no gain.

This narrows rather than contradicts the "exchange UUIDs" preference: the
**service** identifier on the wire is `serviceId` (UUID v7, ADR-0027) everywhere,
including the join key of the requirement table itself (D3). The capability code is
a reference-data vocabulary key, in the same class as `source_code` on
`service_operation_xref` — which is also a `varchar`, also cross-referenced, and
also already accepted.

### D3 — the requirement table is keyed by `service_id`, seeded by `operation_code`

`operationCode` is nullable, so an `operationCode`-keyed table can never cover
every service. `service_capability_requirement(service_id, capability_code)` keeps
the join total; the data load resolves `operation_code → service_id` at seed time,
exactly as `R__seed_reference_catalog_6_labor_guide.sql` already does.

### D4 — null and empty are different answers

A service with **no row** means *not yet configured* — a consumer warns and does not
deny. A service with an explicit **empty marker** means *unconstrained* — any bay
will do, and a consumer must not warn. This is the distinction `#2024` AC4 requires
and `#2022`'s `SERVICE_REQUIREMENTS_NOT_CONFIGURED` reason depends on. It is
expressed on the DTO and the fact as `requiredCapabilityCodes: null` versus `[]`,
and in the table as absence of rows versus a single row with the sentinel
`capability_code = '__NONE__'`.

Without the sentinel the two cases are indistinguishable in a row-per-capability
table, and the frontend's `eligibilityIsApproximate` banner can never be retired.

### D5 — capability granularity now, with a known second step

A bay declares equipment classes (`ALIGNMENT`), not services. A service declares
which classes it needs. The alternative — a bay enumerating every service it can
perform — was rejected: it churns on every catalog addition, and a new service
would be performable by no bay until every bay at every location was edited.

The known limitation is discrimination, quantified in §3: 12 of 28 seeded services
require `TIRE_SERVICE`, so the filter separates alignment, DOT and oil work from the
rest and little else. That is honest for a tire shop and is not a reason to defer.
Finer granularity arrives by adding registry rows (§3.2), not by changing the model.

### D6 — mechanic skill is out of scope

`#2024` Open Question 3 asked whether a skill registry is created and who owns it.
This spec does not answer it. The capability axis above is sufficient for
`#2022` AC2's "eligible bay" half and can land without it. The skill axis is
tracked separately — see §9.

---

## 3. The Mapping

Source: the 28 rows of `scripts/fixtures/seed/alpha/catalog/tier0-services.csv`
against the 20 rows of `R__seed_location_1_reference.sql:304-365`.

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
| `TIRE-INSTALL-COMMERCIAL-SINGLE` | `TIRE_SERVICE` |
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

These need no special equipment and take the `__NONE__` sentinel of D4, **not**
absence of rows:

`AIR-FILTER-REPLACEMENT`, `CABIN-AIR-FILTER-REPLACEMENT`,
`SPARK-PLUG-REPLACEMENT`, `WIPER-BLADE-REPLACEMENT`

`SPARK-PLUG-REPLACEMENT` is unconstrained by omission rather than by nature: the
registry has `ENGINE_DIAGNOSTICS` but no engine **repair** row. If a shop wants to
confine engine work to specific bays, that is a new registry row, not a change here.

### 3.3 Known coarseness

Recorded so it is not rediscovered as a defect:

| Case | Coarseness | Resolution when it matters |
|---|---|---|
| `ROAD-FORCE-BALANCE-SET-4` | A road-force balancer is distinct equipment, collapsed into `TIRE_SERVICE` | Add a `ROAD_FORCE_BALANCE` registry row |
| `TIRE-INSTALL-COMMERCIAL-SINGLE` | Commercial tire work needs a truck-capable bay; the registry has no such row (`BayType.HEAVY_DUTY` exists but bay type is not the vocabulary) | Add a `COMMERCIAL_TIRE` registry row |
| `LUG-TORQUE-RECHECK`, `FLEET-TREAD-DEPTH-AUDIT`, `MICHELIN-*-INSPECTION` | Inspections and rechecks, arguably unconstrained rather than `TIRE_SERVICE` | Judgement call; mapped to `TIRE_SERVICE` on the grounds that they are performed in a tire bay in practice |
| Distribution | 12 of 28 services require `TIRE_SERVICE`; 11 of 20 registry rows (`SUSPENSION`, `ENGINE_DIAGNOSTICS`, `ELECTRICAL`, `FUEL_SYSTEM`, `EXHAUST`, `AC_SERVICE`, `HYDRAULICS`, `DRIVELINE`, `CLUTCH`, `TRAILER_REPAIR`, `ROADSIDE_SERVICE`) have no service at all | Expected; the registry is forward-looking |

---

## 4. Schema Changes

### 4.1 `pos-catalog` — the requirement table

```sql
CREATE TABLE public.service_capability_requirement (
    tenant_id uuid DEFAULT public.app_current_tenant() NOT NULL,
    id uuid NOT NULL,
    service_id uuid NOT NULL,
    capability_code character varying(100) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
```

- Unique `(tenant_id, service_id, capability_code)`.
- FK `service_id` → `service(id)` within the module, scoped by tenant.
- `capability_code` width matches the registry's `code` (100).
- No FK on `capability_code` — it is validated against the replica of §5.2, not a
  local table, because the vocabulary is owned by another module.
- Tenant-scoped per `docs/TENANCY_SCHEMA.md`; **not** a `db/tenancy-global-tables.txt`
  entry.
- Absence of rows for a `service_id` means *not configured*; a single row with
  `capability_code = '__NONE__'` means *unconstrained* (D4).

### 4.2 `pos-catalog` — the capability replica

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

### 4.3 `pos-shop-manager` — replica extension

`ext_bay_replica` gains `bay_type`, `service_capability_codes` (JSON text, same
`StringListJsonConverter` treatment as `BayEntity`) and `max_concurrent_vehicles`.
`ext_catalog_service` is new, carrying `service_id`, `name`, `operation_code`,
`required_capability_codes`, `active`, `aggregate_version`, `updated_at`.

`pos-shop-manager` has no `CatalogEventsListener` today; one is added alongside its
existing `LocationEventsListener`.

---

## 5. Fact Contract Changes

Both are additive within their schema, per ADR-0044 §3 — existing consumers are
unaffected and new-version consumers treat the fields as absent on old events.

### 5.1 `CatalogServiceUpdatedV1` → schema version 3

Append `requiredCapabilityCodes: @Nullable List<String>`. Null means *not
configured*; empty list means *unconstrained* (D4). `CatalogFactPublisher` populates
it from `service_capability_requirement`, collapsing the `__NONE__` sentinel row to
an empty list so the sentinel never leaves the module.

The delete tombstone (`active = false`) publishes `null`.

### 5.2 `location.service-capability.updated` — new fact

Payload `LocationServiceCapabilityUpdatedV1(capabilityId, code, name, active,
createdAt, updatedAt)` on `location.events.v1`, published by `pos-location` after
every `ServiceLocationCapabilityEntity` mutation, with the same delete-tombstone
convention as `CatalogServiceUpdatedV1` (`active = false` rather than a silent
disappearance).

Kafka topic checks (`scripts/generate-kafka-topics.py --check`,
`scripts/check-kafka-topic-drift.sh`) must pass.

---

## 6. API Surface Changes

### 6.1 `pos-catalog` — `ServiceDto` grows

`ServiceDto` gains `requiredCapabilityCodes: List<String>` (nullable, D4). It serves
all three read endpoints, which all live on `ProductController` under
`@RequestMapping("/v1/products")` — `getServiceById` (`:799`), `listServicesByName`
(`:835`) and `searchCatalogServices` (`:862`) — so all three gain the field at once.
No new permission: the existing `catalog:service_type:view` covers it. These are
read-only projections and correctly carry no `@EmitEvent`; that does not change.

Writing a requirement is a state change and does need one. A new
`PUT /v1/products/services/{serviceId}/capability-requirements` carries
`@EmitEvent(id = "CATALOG_SERVICE_CAPABILITY_REQUIREMENT_SET", apiVersion = "1")`
with the `write` preset, registered in `pos-catalog`'s `{Module}EventTypes`, and
`@PreAuthorize` on a new `catalog:service_capability_requirement:manage` permission
added to `CatalogPermissionRegistry`.

Rejection of a code absent from `ext_service_capability` is `422` with the offending
value named, matching `BayServiceImpl.java:298-300`'s existing behaviour and
`domains/shopmgmt/.business-rules/AGENT_GUIDE.md:446`.

### 6.2 `pos-location` — the registry becomes readable

`GET /v1/service-capabilities` returning `{id, code, name, active}`, permission
`location:service_capability:read`. This is the `#2024` ready-now carve-out and has
no dependency on anything else in this spec.

Also in scope and independent: the `@Schema(example = …)` on
`BayResponse.serviceCapabilityIds` and `.skillRequirementIds` (`BayResponse.java:62,
68`) shows UUIDs for values that are codes — `BayController.java:50` shows the truth,
`"serviceCapabilityIds":["ALIGNMENT"]`. Fix the examples. Renaming the fields to
`serviceCapabilityCodes` / `skillRequirementCodes` is permitted without a shim by
the pre-production policy in `CLAUDE.md` and is recommended in the same change.

### 6.3 Contract chain

Per `CLAUDE.md`: regenerate `pos-catalog/openapi.yaml` and `pos-location/openapi.yaml`,
update `permissions.yaml` for the two new permissions (ADR-0025), run the
`API Artifacts Sync` workflow for `pos-catalog pos-location pos-shop-manager`, and
update the Angular SDK.

---

## 7. Data Loads

Three loads are required. Only the first is the mapping; the capability side of the
join is **empty today** and the feature returns nothing without the second.

### 7.1 Service → capability (`pos-catalog`, repeatable migration)

`R__seed_reference_catalog_8_capability_requirements.sql`, mirroring the shape
already proven by `R__seed_reference_catalog_6_labor_guide.sql:22-34`:

```sql
SET TIME ZONE 'UTC';

INSERT INTO service_capability_requirement (id, service_id, capability_code, created_at, updated_at)
SELECT md5('scr:' || s.operation_code || ':' || m.capability_code)::uuid,
       s.id,
       m.capability_code,
       NOW(),
       NOW()
FROM service s
JOIN (VALUES
    ('WHEEL-ALIGNMENT-4-WHEEL', 'ALIGNMENT'),
    ('OIL-CHANGE-FULL-SYNTHETIC', 'OIL_CHANGE'),
    -- … §3.1 in full, then §3.2 as ('AIR-FILTER-REPLACEMENT', '__NONE__'), …
) AS m (operation_code, capability_code) ON m.operation_code = s.operation_code
WHERE s.operation_code IS NOT NULL
ON CONFLICT (tenant_id, service_id, capability_code) DO NOTHING;
```

md5-derived ids keep reruns deterministic; `ON CONFLICT` keeps the repeatable
migration idempotent when its checksum changes.

Tenancy: the seed binds a tenant transaction-locally, as
`R__seed_location_1_reference.sql:2` already does with
`set_config('app.current_tenant', '01900000-0000-7000-8000-000000000001', true)`.

### 7.2 Bay → capabilities (`pos-location`, fixture + seed)

**This is the load without which nothing works.** No bay in any environment holds a
capability. Two changes:

- `scripts/fixtures/seed/alpha/location/bays.csv` gains a `serviceCapabilityCodes`
  column (pipe-separated within the field), populated for all 24 rows.
- A bootstrap from `bay_type` for bays created before the column existed. The
  24 fixture bays are 8 `GENERAL_SERVICE`, 5 `TIRE_SERVICE`, 3 `ALIGNMENT`,
  3 `HEAVY_DUTY`, 3 `INSPECTION`, 2 `WASH_DETAIL`.

Proposed `bay_type` → capability bootstrap, **subject to review** — this is a
business judgement, not a mechanical translation:

| `BayType` | Capabilities |
|---|---|
| `ALIGNMENT` | `ALIGNMENT`, `SUSPENSION` |
| `TIRE_SERVICE` | `TIRE_SERVICE` |
| `INSPECTION` | `DOT_INSPECTION`, `PM_SERVICE` |
| `HEAVY_DUTY` | `TIRE_SERVICE`, `HYDRAULICS`, `DRIVELINE`, `CLUTCH`, `TRAILER_REPAIR` |
| `GENERAL_SERVICE` | `OIL_CHANGE`, `BRAKE_SERVICE`, `TIRE_SERVICE`, `COOLING_SYSTEM`, `TRANSMISSION`, `ELECTRICAL`, `BATTERY`, `EXHAUST`, `FUEL_SYSTEM`, `AC_SERVICE` |
| `WASH_DETAIL` | none |

`GENERAL_SERVICE` is a third of the fleet, and what it can do is precisely the
question a capacity calendar exists to answer. Granting it everything makes the
eligibility filter a no-op for those bays; granting it too little makes the shop
look full. The list above is "everything a general bay can do without special
equipment", which is the defensible reading, but a shop owner should confirm it.

### 7.3 Mechanic skill → service

Not specified here. See §9.

---

## 8. Test Matrix

Beyond the per-AC coverage in `#2024` and `#2022`:

| Case | Expectation |
|---|---|
| Service with one required capability | Eligible bays are those holding the code |
| Service with several | Intersection semantics stated and tested (all-of, not any-of) |
| Service with `__NONE__` | Every active bay eligible; **no** warning to the client |
| Service with no rows | `requiredCapabilityCodes` is null; client warns, does not deny |
| Service with a null `operationCode` | Reachable by `serviceId`; the seed simply does not cover it, so it reads as not configured |
| Unknown capability code on write | `422`, offending value named |
| Lower-case code on write | Normalized to upper before validation, matching `BayServiceImpl.normalizeServiceCapabilityIds` |
| Required capability that no bay at a location holds | Eligible capacity legitimately zero — distinct from *not configured* |
| Registry row deactivated after a service referenced it | Defined behaviour; a tombstoned capability must not silently make a service unschedulable without a distinguishable reason |
| Seed rerun | Idempotent; no duplicate rows, no id churn |
| New tenant provisioned | §9 — currently no requirement rows are copied |

---

## 9. Open Items

Neither blocks the capability. Both are recorded so they are not lost.

### 9.1 Mechanic skill mapping

`#2024` Open Question 3 is unanswered by design (D6). The state of the world:

- `MechanicSkill` (`pos-shop-manager/.../entity/MechanicSkill.java`) carries
  `skillCode` (`:48-49`), `proficiencyLevel` (`:51-52`), `certifiedDate` (`:54-55`)
  and `expirationDate` (`:57-58`). `varchar(255)`, no FK, no CHECK, no registry.
- `MechanicRosterQueryServiceImpl` flattens it to bare codes (`:80-82`), so
  proficiency, certification date and **expiry** are dropped from the roster
  projection. An expired certification currently reads as a held skill.
- `TechnicianRepository` matches `skill.skillCode = :skillCode` case-sensitively
  with no trim (`:26-30`); `MechanicRepository.findRoster` uses the same predicate
  (`:28-32`). `pos-location` uppercases before lookup. The same string is handled
  two ways.
- The de-facto vocabulary is ASE-shaped and lives in a fixture CSV
  (`scripts/fixtures/seed/alpha/shop-manager/mechanic-skills.csv`: `A4-SUSPENSION`,
  `A5-BRAKES`, `A6-ELECTRICAL`, `A8-ENGINE-PERF`, `T1-GAS-ENGINE`,
  `T2-DIESEL-ENGINE`, `T3-DRIVE-TRAIN`, `T4-BRAKES`, `T5-STEERING`,
  `T6-ELECTRICAL`, `T7-HVAC`, `T8-PMI`). No endpoint lists valid codes.
- `BayEntity.skillRequirementIds` is copied through with no lookup and no
  normalization (`BayServiceImpl.java:91-92`, `:178-179`).
- `conflict_resource_type` already includes `SKILL` and `AssignmentStatusEnum`
  already includes `AWAITING_SKILL_FULFILLMENT` (`domains/shopmgmt/.business-rules/DOMAIN_NOTES.md:154`,
  DECISION-SHOPMGMT-010), so the domain anticipated skill eligibility without
  defining the match.

The substantive question, which the capability axis does not answer: an ASE
certification is not the same kind of thing as "can perform service X". A
certification has an issuer, a proficiency level and an expiry, and
`#2022` AC8 depends on honouring that expiry. Mapping `skillCode` onto service or
capability identifiers would discard the part that makes it a certification. A
separate `skill` registry with a mapping to capabilities is the likelier shape, and
it needs its own decision.

Also to settle there, because it is cheap and currently a silent wrong answer: HR
ingestion accepts any non-blank string (`MechanicSyncServiceImpl.java:187-194`), so
an ingested `t4-brakes` never matches a query for `T4-BRAKES` and the advisor
concludes nobody is certified. One normalization rule — uppercase and trim on write,
applied to both sides of the predicate — fixes it independently of everything above.

### 9.2 Tenant provisioning and map maintenance

Both `service` and `service_location_capabilities` are tenant-scoped, so the map is
per-tenant. A tenant provisioned via `tenant.created` receives the capability
registry from the template seed but **no** `service_capability_requirement` rows
unless the provisioning path copies them. Decide before the second tenant exists.

Separately, the seed covers the 28 services that exist today. Service 29 gets no
row and correctly reads as *not configured* — but the map is hand-maintained until
the write endpoint of §6.1 has a UI. Acceptable; worth an explicit owner.

---

## 10. Traceability

| Artifact | Reference |
|---|---|
| Capability | CAP-325 |
| Catalog declaration story | `louisburroughs/durion-positivity-backend#2024` |
| Opening search story | `louisburroughs/durion-positivity-backend#2022` |
| Bay roster / per-date window / buffers | `louisburroughs/durion-positivity-backend#2023` |
| Bay fact precedent, incl. backfill | `louisburroughs/durion-positivity-backend#1668` |
| Cross-reference table precedent | `service_operation_xref`, ADR-0059 §3 |
| Transport constraint | ADR-0044 §6 (replicas fed by facts), §3 (additive schema) |
| Module boundaries | ADR-0026 D1–D5 — nothing here belongs on a grant surface |
| Identifiers, HTTP codes, OpenAPI, errors, tenancy | ADR-0013 / ADR-0027, ADR-0017, ADR-0042, ADR-0056, ADR-0062 |
| Nearest existing capabilities | CAP-165 (Product Master Data), CAP-136 (Locations, Bays, Mobile Units), CAP-137 (Appointment Scheduling), CAP-249 (Mechanic Roster & Availability) |
