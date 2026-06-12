# Specification: Inventory-by-Location Frontend View (Hierarchy Rollup)

**Status:** DRAFT — clarifications resolved 2026-06-12; ready for review
**Date:** 2026-06-12
**Target:** `durion-positivity-frontend` (Angular)
**Domain:** Inventory Control (consumes Location Management contracts indirectly)
**Backend spec:** `domains/inventory/SPEC-inventory-location-rollup.md` (delivered: PRs durion-positivity-backend#660 merged, #661 merge-ready)
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

### 4.1 Models (consumer-side, mirror DTOs exactly)
```ts
interface RollupQuantities { onHand: number; allocated: number; available: number; }
interface StorageLocationRollupNode {
  storageLocationId: string; name: string; type: string; status: string;
  own: RollupQuantities; rolledUp: RollupQuantities;
  children: StorageLocationRollupNode[];
}
interface SiteInventoryRollup { siteId: string; totals: RollupQuantities; nodes: StorageLocationRollupNode[]; }
interface SiteRollupSummary { siteId: string; siteName: string | null; totals: RollupQuantities; }
interface LocationInventoryRollup { locationId: string; parentType: string; totals: RollupQuantities; sites: SiteRollupSummary[]; }
```
Field names are pinned by the backend contract tests — do not rename.

### 4.2 Services & state
- `InventoryRollupApiService`: two typed methods wrapping the endpoints;
  ApiError mapped to a discriminated union (`not-found | upstream-down |
  validation | unknown`) for the views.
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

- API service: URL/param construction (sku, includeEmpty), ApiError mapping
  for 404/503/403, request cancellation on re-query.
- Tree component: 3-level rendering with own vs rolledUp values, negative
  available badge + auto-expand path, non-ACTIVE-with-stock badge,
  includeEmpty toggle re-query, keyboard navigation (arrow/Enter), empty
  site state.
- Overview: sites table sort, row navigation carries siteName, grand total
  rendering, empty state, location picker behavior.
- a11y assertions: roles/aria attributes on the tree.
- Coverage per frontend module policy.

## 8. Story Slicing (proposed, CAP-218 frontend lineage)

| # | Story | Depends on |
| --- | --- | --- |
| F1 | API service + models + error mapping + route scaffolding/guard | — |
| F2 | Site Inventory Tree screen (tree, badges, SKU filter, includeEmpty, a11y) | F1 |
| F3 | Location Inventory Overview (picker, sites table, totals, navigation to F2) | F1 |

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
