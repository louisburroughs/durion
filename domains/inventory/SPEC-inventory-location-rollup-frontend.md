# Specification: Inventory-by-Location Frontend View (Hierarchy Rollup)

**Status:** DRAFT — clarifications resolved 2026-06-12; ready for review
**Date:** 2026-06-12
**Target:** `durion-positivity-frontend` (Angular)
**Domain:** Inventory Control (consumes Location Management contracts indirectly)
**Backend spec:** `domains/inventory/SPEC-inventory-location-rollup.md` (delivered: PRs durion-positivity-backend#660 and #661 both merged)
**Contract source:** `domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md` — Story #658/#659 blocks
**Frontend ADR minimum set:** ADR-0010, ADR-0029–ADR-0035, ADR-0037, ADR-0038

---

## 1. Purpose & Persona

**Primary user:** parts/inventory manager doing daily stock oversight.

**Jobs to be done:**
1. See where stock physically sits across a building/place and its sites.
2. Drill from a parent location into a site's storage-location hierarchy
   (floor → shelf → bin/cage/truck) with quantities at every level.
3. Spot problems fast: **negative available** (over-allocation), stock parked
   in INACTIVE/MAINTENANCE/QUARANTINED locations, unexpectedly empty
   locations.
4. Answer "how much of SKU X do we have, and where?" via a SKU filter.

**v1 is strictly read-only.** No mutations, no inline actions. Navigation
deep-links are out of scope for v1 (candidate v1.1).

## 2. Backend Contracts Consumed (already delivered)

| Endpoint | Use |
| --- | --- |
| `GET /v1/inventory/locations/{locationId}/inventory-rollup?parentType&sku` | Overview screen: per-site summaries `{siteId, siteName, totals}` + grand total. **Always called WITHOUT `expand=tree`** (see §4.3) |
| `GET /v1/inventory/sites/{siteId}/inventory-rollup?sku&depth&includeEmpty` | Site detail screen: storage-location tree with `own` / `rolledUp` `{onHand, allocated, available}` per node |

Contract facts the UI must honor:
- `available = onHand − allocated`, **unclamped** — negative is a real
  over-allocation signal, never hide or floor it.
- Quantities reflect ledger events from backend deployment forward; no
  `unassigned` bucket in v1 (orphan ledger rows are excluded server-side).
- Site response carries no `siteName`; the overview response does. Pass the
  name through navigation state; fall back to the siteId short form.
- Node `status` values: ACTIVE, INACTIVE, MAINTENANCE, QUARANTINED (no
  RETIRED). Node `type`: FLOOR, SHELF, BIN, CAGE, TRUCK.
- Errors: `404 NOT_FOUND`, `400 VALIDATION_ERROR`, `503
  LOCATION_SERVICE_UNAVAILABLE` (retryable), `422
  ROLLUP_EXPANSION_TOO_LARGE` (only if `expand=tree` is ever sent — v1 never
  sends it). Error body is `ApiError {code, message, ...}`.
- Authority: `inventory:on_hand:view` — the route guard must mirror it
  (same claim the gateway forwards).

## 2a. REQUIRED PREREQUISITE — Contract regeneration and Angular SDK (Story F0)

The checked-in `pos-inventory/openapi.yaml` predates the rollup endpoints
(verified: zero `inventory-rollup` entries), and the frontend currently has
no generated API client. Before any view work:

1. **Regenerate `pos-inventory/openapi.yaml`** from merged `main` in
   `durion-positivity-backend` using the module's documented OpenAPI
   generation step (springdoc build output → checked-in yaml). Verify it
   contains `getSiteInventoryRollup` and `getLocationInventoryRollup`
   operations with the 200/400/403/404/422/503 responses and the
   `RollupQuantities` / `StorageLocationRollupNode` /
   `SiteInventoryRollupResponse` / `SiteRollupSummary` /
   `LocationInventoryRollupResponse` schemas. Commit to the backend repo.
2. **Regenerate `domains/inventory/.business-rules/BACKEND_API_REFERENCE.generated.md`**
   from the refreshed yaml via the `/backend-contract` flow (durion repo).
3. **Generate the Angular SDK** in `durion-positivity-frontend`:
   - add `@openapitools/openapi-generator-cli` (dev dependency) and an npm
     script, e.g. `"api:generate": "openapi-generator-cli generate -g
     typescript-angular -i <path-or-url-to-pos-inventory-openapi.yaml> -o
     src/app/api/pos-inventory --additional-properties=providedIn=root,
     serviceSuffix=ApiClient"`;
   - generated output is **committed** (reviewable diffs, no build-time
     network dependency) and excluded from lint/coverage;
   - document the regen procedure in the frontend `AGENTS.md`/README so
     future backend contract changes follow the same step.
4. **Import the SDK**: this feature MUST consume the generated client and
   generated models — no hand-written request/response interfaces. The
   thin `InventoryRollupApiService` facade (§4.2) wraps the generated
   client to apply error mapping and app-level concerns; views depend on
   the facade, never on the generated client directly.

Steps 1–2 are backend/durion commits; steps 3–4 land with Story F0 in the
frontend repo. F1–F3 are blocked until F0 merges.

## 3. Information Architecture & Routes

```
/inventory/by-location                          → Location Inventory Overview
/inventory/by-location/:locationId              → Overview scoped to a parent location
/inventory/by-location/site/:siteId             → Site Inventory Tree
```

Feature module/folder: `src/app/features/inventory-by-location/` following
the repo's existing `features/<name>/components|services|models` layout.
Lazy-loaded route per repo convention.

### Screen 1 — Location Inventory Overview
- **Location picker:** typeahead over parent locations (building/place).
  Source: pos-location roster (`GET /v1/locations/roster`) filtered
  client-side — OPEN QUESTION 1 confirms the filter criteria.
- **Grand total strip:** onHand / allocated / available for the selected
  location (parentType fixed to PHYSICAL in v1; no UI control).
- **Sites table:** one row per descendant site — siteName, onHand,
  allocated, available; sortable; row click navigates to Screen 2 with
  siteName in router state. Negative available renders the warning badge.
- **SKU filter** (shared component, §4.2) re-queries with `sku=`.
- Empty state: "No sites under this location" (200 with empty `sites`).

### Screen 2 — Site Inventory Tree
- **Header:** site name (from nav state or fallback), site totals strip,
  breadcrumb back to overview.
- **Tree:** expandable rows, one per storage location. Columns: name,
  type chip, status chip (non-ACTIVE statuses visually distinct), own
  (onHand/allocated/available), rolledUp (same triple). Children collapsed
  by default below depth 2; expand state preserved during SKU re-query.
- **Controls:** SKU filter; "show empty locations" toggle (maps to
  `includeEmpty`); no depth control in the UI — fetch full tree, collapse
  visually (server `depth` reserved for performance fallback, §6).
- **Problem affordances (persona-critical):**
  - negative `available` at any node → red badge + tree path auto-expanded
    to reveal it; count of over-allocated nodes shown in the totals strip;
  - non-ACTIVE locations holding stock (rolledUp.onHand > 0) → amber badge.
- Empty site: friendly empty state, totals zeros (200 contract).

## 4. Frontend Design Decisions

### 4.1 Models — generated, not hand-written (see §2a)
All request/response types come from the generated Angular SDK
(`src/app/api/pos-inventory`): `RollupQuantities`,
`StorageLocationRollupNode`, `SiteInventoryRollupResponse`,
`SiteRollupSummary`, `LocationInventoryRollupResponse`. Views and the
facade re-export what they need from the SDK; defining parallel hand-written
interfaces for these payloads is prohibited (drift risk). View-model types
(e.g. flattened tree rows for virtual scroll, badge descriptors) live in the
feature and may wrap SDK types.

### 4.2 Services & state
- `InventoryRollupApiService`: thin facade over the generated SDK client
  (§2a step 4); maps SDK errors/ApiError to a discriminated union
  (`not-found | upstream-down | validation | unknown`) for the views.
- Component-local state with Angular signals (match repo conventions; no
  new state library). SKU filter debounced 300ms; in-flight request
  cancellation on re-query.
- No caching/polling in v1 — data loads on navigation and on filter change;
  manual refresh button. (Backend NFR-2: read-committed snapshot, not live.)

### 4.3 Why drill-down instead of `expand=tree`
The overview always requests summaries (no `expand`), so the 25-site cap
and 422 can never hit the UI. Site trees load lazily on navigation — one
site per request, bounded payloads, no fan-out. `expand=tree` stays unused
by this feature.

### 4.4 i18n & accessibility
- All labels via ngx-translate keys under `inventoryByLocation.*`
  (per `angular-i18n` instructions); numbers locale-formatted.
- Tree implements WAI-ARIA `treegrid` semantics: arrow-key navigation,
  Enter to toggle, `aria-expanded`, `aria-level`; badges carry `aria-label`
  text (not color-only — WCAG 1.4.1).
- Status/type chips have text labels, not icons alone.

## 5. Error, Loading, Empty States (contract-driven)

| Condition | UI behavior |
| --- | --- |
| 404 on either endpoint | Inline "location/site not found" panel with back navigation (stale bookmark case) |
| 503 LOCATION_SERVICE_UNAVAILABLE | Non-destructive banner "Location service unavailable" + retry button; keep last rendered data visible if any |
| 400 | Should be unreachable from UI-constructed requests; toast + log |
| 403 | Route guard prevents entry; direct API 403 → "insufficient permissions" panel |
| Loading | Skeleton rows (table/tree shaped), no spinners-only |
| Slow site tree (>5k nodes) | See §6 performance fallback |

## 6. Performance

- Tree rendered with virtual scrolling once flattened-visible-rows > 200
  (CDK virtual scroll); recursive render below that.
- Fallback if a site tree response is too large for one paint: re-request
  with `depth=2` and lazy-fetch deeper levels on expand (the endpoint
  truncates display only; totals stay full-tree — children then fetched by
  re-querying with greater depth). Activate only if p95 render > 500ms in
  practice; not built speculatively.
- Budget: overview ≤ 1 request; site view ≤ 1 request per visit/filter change.

## 7. Testing Requirements (Vitest, per repo conventions)

- SDK integration (F0): `npm run api:generate` against the committed yaml
  produces a clean diff (drift check — candidate CI step); facade compiles
  against generated types only.
- Facade service: param pass-through (sku, includeEmpty), error mapping
  for 404/503/403, request cancellation on re-query (generated client
  mocked).
- Tree component: 3-level rendering with own vs rolledUp values, negative
  available badge + auto-expand path, non-ACTIVE-with-stock badge,
  includeEmpty toggle re-query, keyboard navigation (arrow/Enter), empty
  site state.
- Overview: sites table sort, row navigation carries siteName, grand total
  rendering, empty state, location picker behavior.
- a11y assertions: roles/aria attributes on the tree.
- Coverage per frontend module policy.

## 8. Story Slicing (proposed, CAP-218 frontend lineage)

| # | Story | Repo(s) | Depends on |
| --- | --- | --- | --- |
| F0 | **REQUIRED:** regenerate `pos-inventory/openapi.yaml` + API reference; add openapi-generator tooling; generate, commit, and wire the Angular SDK (§2a) | backend + durion + frontend | #661 merged ✓ |
| F1 | Facade service over SDK + error mapping + route scaffolding/guard | frontend | F0 |
| F2 | Site Inventory Tree screen (tree, badges, SKU filter, includeEmpty, a11y) | frontend | F1 |
| F3 | Location Inventory Overview (picker, sites table, totals, navigation to F2) | frontend | F1 |

F2 and F3 parallelizable after F1.

## 9. Open Questions

1. **Location picker source/filter:** which pos-location contract
   identifies "parent" locations (buildings/places) for the typeahead —
   roster filtered by `LocationType`? Needs confirmation against the
   location domain guide; may need a small pos-location filter param.
2. **Frontend authority mapping:** how `inventory:on_hand:view` surfaces in
   the Angular auth context (existing guard/claims service convention) —
   confirm against `durion-positivity-frontend/AGENTS.md` auth section.
3. **Navigation shell placement:** which nav section owns this route
   (Inventory menu?) and the i18n menu label.
4. **SKU input UX:** free-text (backend treats as exact stockItemId match)
   vs product-catalog typeahead. v1 assumption: free text with "no matches"
   empty state; catalog typeahead deferred.
