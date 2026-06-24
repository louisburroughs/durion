# Design — Workexec typeahead finders (find estimate / workorder by customer name or id)

**Date:** 2026-06-23 (rev 2026-06-24)
**Status:** Approved (design) — pending spec review

> **Rev 2026-06-24 — scope expansion (confirmed):** dropdown rows now show **customer
> name + vehicle label + truncated VIN + estimate number + status**, and the
> **workorder gains a stored human `workorderNumber`** (was an out-of-scope follow-up).
> See §"Workorder number" and the revised DTOs below.
**Repos:** durion-positivity-backend (pos-workorder), durion-positivity-sdk-angular, durion-positivity-frontend

## Context / problem

On `/app/workexec`, every entry point currently requires pasting a raw UUID (`workorderId`,
`estimateId`, and ~12 estimate launch cards). Users don't know UUIDs; they know the **customer** and
the **estimate number**. Replace the raw-id entry with **typeahead finders** that search by customer
name (and estimate number / record id) and navigate to the selected record.

Backend constraint: `pos-workorder` stores only `customerId` on estimates/workorders (estimates also
have a human `estimateNumber`; workorders have **no** human number — UUID only). Customer **name** is
mastered in `pos-customer` (ADR-0015 §6 I2). `pos-workorder` already enriches id→name via
`CustomerReferenceService.resolveAll(ids)` (used by WIP); it has no name→id path yet.

## Decisions (confirmed)

- **Scope:** two unified finders on the landing — **Find Estimate** and **Find Workorder** — not
  per-card inline conversion. Existing raw-id launch cards stay for now.
- **Search architecture:** backend **unified `q`** search per entity (one frontend call). Backend
  resolves name→customerIds via pos-customer, unions with local number/id match, enriches `customerName`.
- **Navigation:** estimate → `/app/workexec/estimates/{id}/summary`; workorder → `/app/workexec/workorders/{id}`.
- **Depth:** standard coverage, shipped as **chained PRs** (backend → SDK → frontend), like CAP-316.

## Workorder number (new, stored)

Add a human-readable `workorderNumber` (column on `Workorder`, format `WO-YYYY-NNNN`,
unique per `locationId` like `estimateNumber`). Populate **at creation** in
`WorkorderServiceImpl.doCreateWorkorder`:

- **Created from an estimate** → swap the estimate's prefix: `EST-2026-1001` → `WO-2026-1001`,
  **iff** that value is free at the workorder's location. This makes the WO number "match the
  estimate number except the prefix" in the normal 1:1 case.
- **No estimate, or the swapped value collides** (estimate revised into a 2nd workorder) →
  generate via an independent `WO-YYYY-NNNN` sequence, mirroring `generateEstimateNumber`
  (`do { n = prefix+seq; seq++ } while (existsByLocationIdAndWorkorderNumber)`).

**Migration (Flyway):** add `workorder.workorder_number` (nullable initially), unique
constraint `(locationId, workorder_number)`, then **backfill** existing rows — prefix-swap
from the joined estimate where present and free, else assign sequentially per location.
After backfill, number is set on every row. (Pre-production: no compatibility shim.)

Consequence: workorder search (below) can now match `workorderNumber ILIKE %q%`.

### 1. Backend (pos-workorder)

**Customer name → ids (new client capability).** Add `searchCustomerIdsByName(String q, int limit)` to the
workorder→customer client (alongside `CustomerValidationClient` / `CustomerReferenceService`), calling
pos-customer's existing party browse (`GET /v1/.../parties?name={q}`, `browseParties`). Returns an ordered
`List<CustomerRef{customerId, customerName}>` (cap ~10). Fail-soft: on pos-customer error, name matching
yields empty (id/number matching still works), consistent with existing resilience trade-off.

**Estimate search — extend `EstimateSearchController` `GET /v1/workexec/estimates/search`:**
- Add `@RequestParam(required=false) String q`.
- Resolution when `q` present: estimates where `estimateNumber ILIKE %q%` **OR** `customerId IN (name
  matches)` **OR** `id = q` (when `q` parses as UUID). Existing `customerId`/`vehicleId` filters still
  honored (AND-combined when present).
- Response: existing `EstimateSummaryResponse` **+ `customerName` + `vehicleLabel` + `vin`** (customer via
  `CustomerReferenceService.resolveAll`, vehicle via **existing** `VehicleReferenceService.resolveAll` →
  `{vehicleInfo, vin}`), paginated (`PageableDefault(size=25)`, finder uses ~10). `estimateNumber`/`status`
  already present.
- Permission unchanged: `workorder:estimate:view`.

**Workorder search — new `GET /v1/workorders/search?q=` (WorkorderController or a new search controller):**
- `q` matches `workorderNumber ILIKE %q%` **OR** `customerId IN (name matches)` **OR** `id = q` (UUID).
  (`workorderNumber` is the new human number — see §"Workorder number".)
- Lightweight DTO `WorkorderSearchResult { workorderId, workorderNumber, estimateNumber, status,
  customerName, vehicleLabel, vin, createdAt }`. `estimateNumber` via the `estimate` join; `customerName`
  and `vehicleLabel`/`vin` enriched as above. Paginated.
- Permission: `workorder:view` (confirm exact scope against existing WorkorderController).

**Repositories:** add finders for `customerId IN (:ids)` + `estimateNumber ILIKE` (estimates) and
`customerId IN (:ids)` + `workorderNumber ILIKE` (workorders), both paged. Vehicle/customer enrichment
is done per result page via the reference services (batch `resolveAll`), as WIP already does. H2-portable.

### 2. SDK (durion-positivity-sdk-angular)
Regenerate the `workorder` module: new `q` param on estimate search, new workorder search operation +
`WorkorderSearchResult`, `customerName` on the estimate summary schema. Build the workorder package.

### 3. Frontend (durion-positivity-frontend)

**Reusable `WorkexecSearchTypeaheadComponent` (standalone, OnPush)** under
`features/workexec/components/search-typeahead/`:
- Inputs: `label`, `placeholder`, a `search: (q) => Observable<SearchResultItem[]>` fn,
  `minChars=2`, `debounceMs=250`.
- Output: `selected: EventEmitter<string>` (the chosen record id).
- Behavior: debounced query; renders an accessible combobox/listbox (`role=combobox` input +
  `role=listbox` popup, `aria-activedescendant`, arrow/enter/escape keyboard nav); each option shows
  `primary` (customer name) + `secondary` (record number + status) + `tertiary` (vehicle label + truncated
  VIN). Loading, empty ("no matches"), and error states. No raw-id requirement.
- `SearchResultItem { id, primary, secondary, tertiary }` — thin view model the page maps SDK rows into.

**Facade (`WorkexecService`):** `searchEstimates(q)` and `searchWorkorders(q)` returning
`Observable<SearchResultItem[]>`, mapping SDK rows → `primary = customerName`,
`secondary = estimateNumber|workorderNumber + ' · ' + status`, `tertiary = vehicleLabel + ' · VIN …' +
last 8 of vin` (VIN truncation is a display concern — backend returns full `vin`, frontend shows the
trailing 8 chars).

**Landing (`workexec-landing-page`):** add a **Finders** section at the top with two
`WorkexecSearchTypeaheadComponent`s. `Find Estimate` → on select, navigate
`['/app','workexec','estimates', id, 'summary']`. `Find Workorder` → `['/app','workexec','workorders', id]`.
Existing launch cards unchanged.

**i18n:** new `WORKEXEC.FINDERS.*` keys in `en-US/es-US/fr-CA/qps-ploc` (en authoritative; placeholders
elsewhere).

## Components & boundaries

| Unit | Purpose | Depends on |
| --- | --- | --- |
| `searchCustomerIdsByName` (client) | name → customerIds | pos-customer party browse |
| Estimate/Workorder search service | union q-resolution + name enrichment | repos, customer client, `CustomerReferenceService` |
| Search controllers | HTTP `q` contract + permission | services |
| `WorkexecSearchTypeaheadComponent` | accessible debounced typeahead UI | a `search` fn only (entity-agnostic) |
| `WorkexecService.searchEstimates/Workorders` | SDK→view-model + call | generated SDK |
| Landing finders section | wiring + navigation | typeahead component, facade, Router |

## Error / edge handling
- `q` < 2 chars → no call (frontend), empty page (backend if hit directly).
- pos-customer unreachable → name matches empty, number/id matches still returned (fail-soft).
- No matches → empty-state in dropdown.
- 403 → finder shows access message; landing otherwise usable.
- UUID `q` → exact id match branch (both entities).
- Whole-result caps (~10) — `log` when truncated server-side (no silent cap).

## Testing
- **Backend:** unit/contract — q matches estimateNumber/workorderNumber; q matches customer name (mocked
  customer client → ids → rows); q as UUID exact match; customerName + vehicleLabel/vin enrichment present;
  pos-customer/pos-vehicle-down fail-soft; permission 403. **Workorder numbering:** prefix-swap from
  estimate (1:1), collision → own sequence, estimate-less → own sequence, backfill migration test.
  (H2; Flyway-disabled — seed via repos.)
- **SDK:** builds; new operation + DTO present.
- **Frontend:** typeahead component spec (debounce, keyboard nav, select emits id, loading/empty/error);
  facade mapping spec; landing wiring (select → navigate). `ng build --configuration alpha`; a11y smoke.

## Out of scope / follow-ups
- Per-card inline typeahead conversion of the remaining launch cards.
- Relevance ranking, dropdown pagination/load-more, recent/favorites.

## Ship order
Backend PR (search + tests) → SDK PR (regen workorder) → frontend PR (finders), merged in order.
