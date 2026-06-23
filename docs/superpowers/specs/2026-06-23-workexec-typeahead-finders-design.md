# Design — Workexec typeahead finders (find estimate / workorder by customer name or id)

**Date:** 2026-06-23
**Status:** Approved (design) — pending spec review
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

## Architecture

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
- Response: existing `EstimateSummaryResponse` **+ `customerName`** (enriched via `resolveAll`), paginated
  (`PageableDefault(size=25)`, finder uses ~10).
- Permission unchanged: `workorder:estimate:view`.

**Workorder search — new `GET /v1/workorders/search?q=` (WorkorderController or a new search controller):**
- `q` matches `customerId IN (name matches)` **OR** `id = q` (UUID). (No estimateNumber analogue; workorder
  has no human number.)
- Lightweight DTO `WorkorderSearchResult { workorderId, status, customerName, vehicleLabel?, createdAt }`,
  `customerName` enriched. Paginated.
- Permission: `workorder:view` (confirm exact scope against existing WorkorderController).

**Repositories:** add finders for `customerId IN (:ids)` + `estimateNumber ILIKE` (estimates) and
`customerId IN (:ids)` (workorders), both with paging and `endTime`-agnostic active filtering N/A here.
Dimension/JSON-free, portable to H2.

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
  `primary` (customer name) + `secondary` (estimate number / workorder id-short + status). Loading,
  empty ("no matches"), and error states. No raw-id requirement.
- `SearchResultItem { id, primary, secondary }` — a thin view model the page maps SDK rows into.

**Facade (`WorkexecService`):** `searchEstimates(q): Observable<SearchResultItem[]>` and
`searchWorkorders(q): Observable<SearchResultItem[]>`, mapping SDK rows → `SearchResultItem` (primary =
customerName, secondary = estimateNumber / short workorder id + status).

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
- **Backend:** unit/contract — q matches estimateNumber; q matches customer name (mocked customer client
  → ids → rows); q as UUID exact match; customerName enrichment present; pos-customer-down fail-soft;
  permission 403. (H2; Flyway-disabled — seed via repos.)
- **SDK:** builds; new operation + DTO present.
- **Frontend:** typeahead component spec (debounce, keyboard nav, select emits id, loading/empty/error);
  facade mapping spec; landing wiring (select → navigate). `ng build --configuration alpha`; a11y smoke.

## Out of scope / follow-ups
- Per-card inline typeahead conversion of the remaining launch cards.
- Relevance ranking, dropdown pagination/load-more, recent/favorites.
- Workorder human-readable number (would simplify workorder search; separate change).

## Ship order
Backend PR (search + tests) → SDK PR (regen workorder) → frontend PR (finders), merged in order.
