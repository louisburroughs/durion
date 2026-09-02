# Design Specification — Shop Manager Dashboard

**Domain:** `shopmgmt` · **Surface:** frontend (`durion-positivity-frontend`) + one new backend read endpoint
**Route:** `/app/shopmgmt/shop-dashboard`
**Status:** DRAFT — ready for story extraction
**Date:** 2026-09-02

---

## 1. Purpose

A Shop Manager needs one screen that answers, at a glance, *"what is every repair unit at this
site doing right now?"* — for both fixed bays and mobile units.

Today that answer is spread across the Daily Dispatch Board (workorder + mechanic rows, no bay
detail, no vehicle), the Location Bays page (bay inventory, no workorder), and the Mobile Units
page (unit inventory, no workorder). None of them shows the vehicle on the ramp.

This dashboard replaces that triangulation with a **repair-unit-centric card grid**: one card per
bay and per mobile unit, each carrying the assigned workorder, its vehicle, the mechanic on it,
and a status-coloured header.

Beneath the grid, an **open-workorder roster** lists every vehicle with open work at the site —
including work not currently on any unit. The grid answers "what is each unit doing?"; the roster
answers "what work is outstanding, and what has nowhere to go?"

**Primary actor:** Shop Manager
**Secondary actors:** Service Advisor (read-only situational awareness), Dispatcher

---

## 2. Concept

### 2.1 Full page

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│  SHOP MANAGEMENT                                                                          │
│  Shop Manager Dashboard                                          Last updated 10:42 AM ⟳  │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ ┃ ┌──────────────────────────────────────────────────────────────────────────────────┐   │
│ ┃ │ Location  [ Northgate Service Center      ▾ ]   Date [2026-09-02]   [ Refresh ]   │   │
│ ┃ │           └─ only locations with bays or mobile units                             │   │
│ ┃ └──────────────────────────────────────────────────────────────────────────────────┘   │
│ ┃                                                                                         │
│ ┃  12 units    ● 5 active   ▲ 2 blocked   ◆ 1 ready   ○ 4 idle                            │
│ ┃                                                                                         │
│ ┃  ┌── BAYS ─────────────────────────────────────────────────────────────────────────┐   │
│ ┃  │                                                                                  │   │
│ ┃  │  ┌────────────────────────┐  ┌────────────────────────┐  ┌────────────────────┐  │   │
│ ┃  │  │▓▓ ● WORK IN PROGRESS ▓▓│  │░░ ▲ AWAITING PARTS ░░░░│  │▒▒ ○ IDLE ▒▒▒▒▒▒▒▒▒▒│  │   │
│ ┃  │  │ Bay 1 · Alignment      │  │ Bay 2 · General        │  │ Bay 3 · Heavy      │  │   │
│ ┃  │  ├────────────────────────┤  ├────────────────────────┤  ├────────────────────┤  │   │
│ ┃  │  │ WO-10428               │  │ WO-10431               │  │                    │  │   │
│ ┃  │  │                        │  │                        │  │   No workorder     │  │   │
│ ┃  │  │ 2021 Ford F-150        │  │ 2019 Toyota Camry      │  │   assigned         │  │   │
│ ┃  │  │ VIN ···· 8823          │  │ VIN ···· 4417          │  │                    │  │   │
│ ┃  │  │                        │  │                        │  │   Bay available    │  │   │
│ ┃  │  │ 👤 M. Alvarez          │  │ 👤 D. Chen             │  │                    │  │   │
│ ┃  │  └────────────────────────┘  └────────────────────────┘  └────────────────────┘  │   │
│ ┃  │                                                                                  │   │
│ ┃  └──────────────────────────────────────────────────────────────────────────────────┘   │
│ ┃                                                                                         │
│ ┃  ┌── MOBILE UNITS ─────────────────────────────────────────────────────────────────┐   │
│ ┃  │  ┌────────────────────────┐  ┌────────────────────────┐                          │   │
│ ┃  │  │▓▓ ◆ READY FOR PICKUP ▓▓│  │▒▒ ○ IDLE ▒▒▒▒▒▒▒▒▒▒▒▒▒▒│                          │   │
│ ┃  │  │ Van 4 · North Region   │  │ Van 7 · East Region    │                          │   │
│ ┃  │  ├────────────────────────┤  ├────────────────────────┤                          │   │
│ ┃  │  │ WO-10402               │  │                        │                          │   │
│ ┃  │  │ 2023 RAM 2500          │  │   No workorder         │                          │   │
│ ┃  │  │ VIN ···· 1190          │  │   assigned             │                          │   │
│ ┃  │  │ 👤 T. Okafor           │  │                        │                          │   │
│ ┃  │  └────────────────────────┘  └────────────────────────┘                          │   │
│ ┃  └──────────────────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Open-workorder roster (bottom of page)

```
┌── VEHICLES WITH OPEN WORKORDERS (14) ────────────────────────────────────────────────┐
│                                                                                       │
│  Vehicle                    VIN                 Workorder   Status            Unit    │
│ ─────────────────────────────────────────────────────────────────────────────────────│
│  2021 Ford F-150            1FTFW1E85MFA88823   WO-10428    ● Work in progress Bay 1  │
│  2019 Toyota Camry          4T1B11HK5KU714417   WO-10431    ▲ Awaiting parts   Bay 2  │
│  2023 RAM 2500              3C6UR5DL8PG501190   WO-10402    ◆ Ready for pickup Van 4  │
│  2018 Honda Civic           2HGFC2F59JH512260   WO-10433    ◔ Assigned         —      │
│  2020 Chevrolet Silverado   1GCUYDED4LZ118904   WO-10435    ◔ Approved         —      │
│  2017 Subaru Outback        4S4BSANC1H3204471   WO-10437    ▲ Awaiting approval —     │
│                                                                                       │
│  Sorted: unassigned first, then by status band. "—" = not on a bay or mobile unit.    │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

Under `48rem` the table collapses to stacked rows:

```
┌─────────────────────────────────┐
│ 2018 Honda Civic                │
│ VIN 2HGFC2F59JH512260           │
│ WO-10433  ◔ Assigned  ·  —      │
└─────────────────────────────────┘
```

### 2.3 Card anatomy

```
┌───────────────────────────────────┐
│▓▓▓▓ ● WORK IN PROGRESS ▓▓▓▓▓▓▓▓▓▓▓│ ← header: status band colour + icon + TEXT
│ Bay 1 · Alignment                 │
├───────────────────────────────────┤
│ WORKORDER                         │
│ WO-10428                       →  │ ← routerLink /app/workexec/workorders/:id
│                                   │
│ VEHICLE                           │
│ 2021 Ford F-150                   │
│ VIN 1FTFW1E85MFA88823             │ ← monospace, selectable
│                                   │
│ MECHANIC                          │
│ M. Alvarez                        │
└───────────────────────────────────┘
```

### 2.4 Idle card

```
┌───────────────────────────────────┐
│▒▒▒▒ ○ IDLE ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒│
│ Bay 3 · Heavy Duty                │
├───────────────────────────────────┤
│                                   │
│      No workorder assigned        │
│      Bay available                │
│                                   │
└───────────────────────────────────┘
```

---

## 3. Layout rules

| Viewport | Grid |
|---|---|
| `>= 90rem` | 4 columns |
| `>= 64rem` | 3 columns |
| `>= 48rem` | 2 columns |
| `< 48rem` | 1 column, filter bar stacks |

- Card grid: `display: grid; grid-template-columns: repeat(auto-fill, minmax(17rem, 1fr)); gap: var(--space-4);`
- Cards are **equal height** within a row (`align-items: stretch`); the idle card centres its
  placeholder text vertically so the grid does not look ragged.
- Sections (`BAYS`, `MOBILE UNITS`) are `<section>` elements with `<h2>` headings. A section with
  zero units is omitted entirely rather than rendered empty.
- The **open-workorder roster** sits below both unit sections, inside the same elevated panel,
  separated by a `1px solid var(--border-color)` rule and `--space-6` of top margin. It is a real
  `<table>` (semantic rows/columns, `<caption>` carrying the count) wrapped in an
  `overflow-x: auto` container. Under `48rem` the table switches to a stacked-row card list via
  CSS only — the DOM order and semantics do not change.
- Page shell mirrors `dispatch-board-page.component.css`: `.page-header` + `.dur-elevation-2`
  panel with the 2px `--brand-accent` left rail, `--space-6` padding collapsing to `--space-4`
  under `64rem`.

---

## 4. Status → colour band

The card header is the only colour-coded element. Colour communicates the **band**; the header
text always names the **specific** status, so colour is never the sole carrier of meaning
(ADR-0039 §2, WCAG 1.4.1).

| Band | `WorkorderStatus` values | Icon | Background token | Foreground token |
|---|---|---|---|---|
| `idle` | *(no workorder assigned)* | `○` | `--durion-graphite-100` | `--currentTextColor` |
| `queued` | `DRAFT`, `APPROVED`, `ASSIGNED` | `◔` | `--status-info-bg` | `--status-info-fg` |
| `active` | `WORK_IN_PROGRESS` | `●` | `--status-success-bg` | `--status-success-fg` |
| `blocked` | `AWAITING_PARTS`, `AWAITING_APPROVAL` | `▲` | `--status-warning-bg` | `--status-warning-fg` |
| `ready` | `READY_FOR_PICKUP` | `◆` | `--status-ready-bg` *(new)* | `--status-ready-fg` *(new)* |
| `closed` | `COMPLETED` | `✓` | `--status-neutral-bg` *(new)* | `--status-neutral-fg` *(new)* |
| `cancelled` | `CANCELLED` | `✕` | `--status-error-bg` | `--status-error-fg` |

### 4.1 New tokens required in `src/styles.css`

Add to the light `:root` block and the dark theme block, following the existing
`--status-*-bg`/`--status-*-fg` pairs at lines 141–148 and 187–194:

```css
/* light */
--status-ready-bg:   #e4f2f1;
--status-ready-fg:   #17605c;   /* 7.0:1 on its tint */
--status-neutral-bg: #eceff1;
--status-neutral-fg: #37474f;   /* 8.9:1 on its tint */

/* dark */
--status-ready-bg:   #1e3937;
--status-ready-fg:   #7fd8d1;
--status-neutral-bg: #2c3236;
--status-neutral-fg: #c3ccd1;
```

Every pair must be verified at `>= 4.5:1` in both themes before merge (ADR-0039 §1, §3).
Header text is 0.8125rem semibold — small text, so the 4.5:1 threshold applies, not 3:1.

### 4.2 Unknown status

A status value not in the table renders in the `queued` band with the raw server string as the
header text. The page never blanks a card because the backend added an enum member.

---

## 5. Data contract

### 5.1 New backend endpoint (required)

No current endpoint carries bay + mobile-unit assignment with structured vehicle detail:

- `GET /v1/workexec/dashboard/today` returns `bays[]` with `assignedWorkorderId` but **no
  vehicle** and **no mobile units**.
- `GET /v1/workexec/wip` returns `vehicleInfo` as an **unstructured string** — cannot be split
  into year/make/model/VIN reliably.
- Structured vehicle data exists only at `GET /v1/crm/snapshot/vehicle/{vehicleId}` — one call
  per card, an N+1 fan-out.

Spec a single aggregate read:

```
GET /v1/shopmgmt/shop-dashboard?locationId={uuid}&date={yyyy-MM-dd}
```

| Parameter | In | Required | Notes |
|---|---|---|---|
| `locationId` | query | yes | UUID (ADR-0013, ADR-0027) |
| `date` | query | no | ISO date-only string; defaults to the location's local today (ADR-0038) |

**Responses:** `200` OK · `400` invalid `locationId`/`date` · `403` caller lacks
`shopmgmt:dashboard:view` · `404` unknown location (ADR-0017).

```jsonc
// 200 ShopDashboardResponse
{
  "locationId": "…",
  "locationName": "Northgate Service Center",
  "date": "2026-09-02",
  "generatedAt": "2026-09-02T10:42:11Z",
  "units": [
    {
      "unitId": "…",
      "unitType": "BAY",                    // BAY | MOBILE_UNIT
      "unitName": "Bay 1",
      "unitSubtitle": "Alignment",          // bayType, or coverage region for a mobile unit
      "unitStatus": "ACTIVE",               // operational status of the bay/unit itself
      "workorder": {                        // null when nothing is assigned
        "workorderId": "…",
        "workorderNumber": "WO-10428",
        "status": "WORK_IN_PROGRESS",       // WorkorderStatus enum
        "vehicle": {                        // null when the workorder has no vehicle
          "vehicleId": "…",
          "vin": "1FTFW1E85MFA88823",
          "year": 2021,
          "make": "Ford",
          "model": "F-150"
        },
        "mechanic": {                       // null when unassigned
          "personId": "…",
          "displayName": "M. Alvarez"
        }
      }
    }
  ],
  "openWorkorders": [
    {
      "workorderId": "…",
      "workorderNumber": "WO-10433",
      "status": "ASSIGNED",
      "vehicle": {
        "vehicleId": "…",
        "vin": "2HGFC2F59JH512260",
        "year": 2018,
        "make": "Honda",
        "model": "Civic"
      },
      "mechanic": { "personId": "…", "displayName": "R. Bell" },
      "unitId": null,                       // null = not on any bay or mobile unit
      "unitName": null,
      "promisedAt": "2026-09-02T16:00:00Z"  // optional, for future sort/overdue work
    }
  ]
}
```

**`openWorkorders` semantics.** Every workorder at the location whose status is *open* —
`DRAFT`, `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`,
`READY_FOR_PICKUP`. `COMPLETED` and `CANCELLED` are excluded. The list is a **superset** of the
work shown on the cards: a workorder assigned to a unit appears in both, with `unitId`/`unitName`
populated; a workorder with no unit appears only here, with both null.

Backend sorts and pages the list: unassigned (`unitId = null`) first, then by status band
(`blocked` → `queued` → `active` → `ready`), then by `promisedAt` ascending, then by
`workorderNumber`. The endpoint caps the list at 200 rows and sets `openWorkordersTruncated: true`
when the cap is hit, so a very busy site cannot inflate the payload without bound. Client-side
re-sorting is out of scope for this story.

**Backend composition** (owner: `workexec`, per ADR-0006 workorder-assignment ownership):
location service supplies bays and mobile units for the site; workexec supplies the assignment
and status; CRM supplies the vehicle projection; people supplies the mechanic display name.
Cross-domain reads follow ADR-0026 service-contract boundaries and ADR-0044 event-only domain
walls — no direct table reads.

**Contract chain (mandatory, CLAUDE.md):** controller OpenAPI annotations (ADR-0042) →
regenerate `OpenAPI.yaml` → regenerate the Angular SDK. The frontend consumes the generated SDK
service only (ADR-0041); it must not inject `HttpClient`.

### 5.2 Location filter — repair capability

"Repair capability" is not a flag on `Location` today. The picker derives it client-side:

1. `LocationService.getAllLocations()`
2. `LocationService.listMobileUnits()` — one paged call, global; group by `baseLocationId`
3. `LocationService.listBays(locationId)` per active location — fan-out

A location is offered **iff** it has at least one bay **or** at least one mobile unit based
there. Locations are also filtered to `active !== false`.

The derived set is cached in the service for the page's lifetime so switching location does not
re-fan-out. If the bay fan-out partially fails, locations whose bay call failed are still offered
when they have mobile units; a non-blocking inline notice states the list may be incomplete.

> **Known cost / follow-up.** The bay fan-out is O(number of locations). A backend
> `hasRepairCapability` projection (or `GET /v1/locations?capability=REPAIR`) should replace it.
> Tracked as a separate location-service story; the frontend must isolate the derivation behind
> `ShopDashboardService.listRepairLocations()` so the swap is a one-method change.

---

## 6. Frontend view model

`src/app/features/shopmgmt/models/shop-dashboard.models.ts`

```ts
export type RepairUnitType = 'BAY' | 'MOBILE_UNIT';

export type StatusBand =
  | 'idle' | 'queued' | 'active' | 'blocked' | 'ready' | 'closed' | 'cancelled';

export interface DashboardVehicle {
  readonly vehicleId: string;
  readonly vin?: string;
  readonly year?: number;
  readonly make?: string;
  readonly model?: string;
}

export interface DashboardMechanic {
  readonly personId: string;
  readonly displayName: string;
}

export interface DashboardWorkorder {
  readonly workorderId: string;
  readonly workorderNumber?: string;
  readonly status: WorkorderStatus;
  readonly vehicle?: DashboardVehicle;
  readonly mechanic?: DashboardMechanic;
}

export interface RepairUnitCard {
  readonly unitId: string;
  readonly unitType: RepairUnitType;
  readonly unitName: string;
  readonly unitSubtitle?: string;
  readonly unitStatus?: string;
  readonly workorder?: DashboardWorkorder;
}

/** One row of the open-workorder roster at the bottom of the page. */
export interface OpenWorkorderRow {
  readonly workorderId: string;
  readonly workorderNumber?: string;
  readonly status: WorkorderStatus;
  readonly vehicle?: DashboardVehicle;
  readonly mechanic?: DashboardMechanic;
  /** Undefined when the workorder is not on a bay or mobile unit. */
  readonly unitId?: string;
  readonly unitName?: string;
  readonly promisedAt?: string;
}

export interface ShopDashboardView {
  readonly locationId: string;
  readonly locationName?: string;
  readonly date: string;
  readonly generatedAt?: string;
  readonly units: readonly RepairUnitCard[];
  readonly openWorkorders: readonly OpenWorkorderRow[];
  readonly openWorkordersTruncated: boolean;
}

export interface RepairLocationOption {
  readonly locationId: string;
  readonly name: string;
  readonly bayCount: number;
  readonly mobileUnitCount: number;
}
```

Pure helpers, unit-testable in isolation:

```ts
export function statusBand(status?: WorkorderStatus | string): StatusBand;
export function vehicleLabel(v?: DashboardVehicle): string;  // "2021 Ford F-150", '' when empty
export function isOpenStatus(status?: WorkorderStatus | string): boolean;
```

`isOpenStatus` is the single client-side definition of "open" (everything except `COMPLETED` and
`CANCELLED`). The roster trusts the server's filtering; the helper exists so the summary counts
and any future client-side filter cannot drift from the server's definition.

`vehicleLabel` joins `year make model` skipping absent parts; when all three are absent it returns
`''` and the template falls back to the `VEHICLE_UNKNOWN` key rather than rendering a bare VIN row
with no heading.

---

## 7. Page state machine

Follows the mandatory two-signal convention (frontend `AGENTS.md`, ADR-0031):

```ts
readonly state    = signal<PageState>('idle');   // idle|loading|ready|error
readonly errorKey = signal<string | null>(null);
```

`state.set('error')` **always precedes** `errorKey.set(...)`. Data loading runs in an `effect()`
keyed on `locationId()` + `date()`, and every subscription is torn down through `onCleanup()`
(ADR-0033) so a fast location switch cannot land a stale response.

| State | Trigger | Render |
|---|---|---|
| `idle` (location-required) | no `locationId` | Picker plus prompt `SHOP_DASHBOARD.LOCATION_REQUIRED` |
| `loading` | fetch in flight | Skeleton card grid (6 placeholders), `aria-busy="true"` |
| `ready` + units | 200, `units.length > 0` | Card grid + roster |
| `ready` + empty | 200, `units.length === 0` | Empty state: "No repair units configured at this location" with a link to Location → Bays. The roster still renders if `openWorkorders` is non-empty — open work at a site with no configured units is exactly the condition a manager needs to see. |
| `error` | 4xx/5xx | `role="alert"` panel + Retry, keyed message |

Error keys: `ERROR_LOAD`, `ERROR_FORBIDDEN` (403), `ERROR_NOT_FOUND` (404),
`ERROR_LOCATIONS_LOAD` (picker source failed).

`locationId` and `date` are mirrored to query params (`queryParamsHandling: 'merge'`) so a shop
manager's view is bookmarkable and shareable, matching the bays/mobile-units pages.

The roster is part of the same response, so it shares one `state`/`errorKey` pair — there is no
second loading spinner and no partial-failure path. When `openWorkorders` is empty the roster
section renders its own inline empty line (`ROSTER.EMPTY`) rather than being omitted, because an
absent section reads as "not loaded" where an explicit "no open workorders" reads as good news.
When `openWorkordersTruncated` is true, a non-blocking notice below the table states the list is
capped.

**Refresh:** manual button only in this story. No polling — auto-refresh is deferred to a
follow-up so the cadence can be decided against real load.

---

## 8. Navigation & landing wiring

### 8.1 Route

`src/app/features/shopmgmt/shopmgmt.routes.ts` — add above `dispatch-board`:

```ts
{
  path: 'shop-dashboard',
  loadComponent: () =>
    import('./pages/shop-dashboard/shop-dashboard-page.component').then(
      m => m.ShopDashboardPageComponent,
    ),
},
```

Inherits `authGuard` from the `/app` parent. Add `data: { roles: [...] }` only if the shop-manager
role is enumerated for the other shopmgmt routes; today they carry none, so this route matches.

### 8.2 Landing page

`src/app/features/shopmgmt/pages/landing/shopmgmt-landing.config.ts`:

1. **Hero primary CTA** becomes the dashboard; Dispatch Board moves to the secondary CTA.

```ts
primaryCta: {
  labelKey: 'SHOPMGMT.LANDING.HERO_CTA_SHOP_DASHBOARD',
  icon: 'factory',
  route: '/app/shopmgmt/shop-dashboard',
},
secondaryCta: {
  labelKey: 'SHOPMGMT.LANDING.HERO_CTA_DISPATCH',
  route: '/app/shopmgmt/dispatch-board',
},
```

2. **First card** in the existing *Dispatch & Schedule* section, ahead of Dispatch Board:

```ts
{
  kind: 'direct',
  icon: 'factory',
  titleKey: 'SHOPMGMT.LANDING.CARD.SHOP_DASHBOARD.TITLE',
  descriptionKey: 'SHOPMGMT.LANDING.CARD.SHOP_DASHBOARD.DESCRIPTION',
  ctaKey: 'SHOPMGMT.LANDING.ACTION.OPEN_PAGE',
  route: '/app/shopmgmt/shop-dashboard',
},
```

```
SECTION: Dispatch & Schedule
  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
  │ 🏭 Shop Manager  │ │ 📋 Dispatch Board│ │ 📅 Schedule      │
  │    Dashboard     │ │                  │ │                  │
  │ Live bay & mobile│ │ Daily workorder  │ │ Appointment      │
  │ unit status      │ │ and mechanic view│ │ calendar         │
  │      Open page → │ │      Open page → │ │      Open page → │
  └──────────────────┘ └──────────────────┘ └──────────────────┘
```

The existing `HERO_CTA_SCHEDULE` key is retired from the config; it stays in the locale files only
if still referenced elsewhere, otherwise it is removed (pre-production policy — no dead keys).

### 8.3 Outbound links

The workorder number links to `/app/workexec/workorders/{workorderId}` via `routerLink`
(ADR-0037 — no `window.location`, no full page reload). The card body is **not** wholly
clickable; only the workorder line is a link, so the card stays a plain `<article>` and does not
create a nested-interactive-control trap.

---

## 9. Accessibility (ADR-0029, ADR-0039)

- Each card is an `<article>` with `aria-labelledby` pointing at its header `<h3>`, so a screen
  reader announces "Bay 1, Alignment, Work in progress" as the card's accessible name.
- Section headings `<h2>` (Bays / Mobile Units) under the page `<h1>`; no skipped levels.
- The status band is **never** conveyed by colour alone: header carries an icon (`aria-hidden`,
  decorative) plus the translated status text.
- The roster is a semantic `<table>` with a `<caption>` naming it and its count, `<th scope="col">`
  headers, and `<th scope="row">` on the vehicle cell. At narrow widths the visual stacking is
  pure CSS, so the table semantics and reading order survive.
- Roster status cells repeat the card convention: coloured chip + icon + translated status text,
  never colour alone. The "no unit" cell renders an em dash with an `aria-label` of
  `ROSTER.NO_UNIT` so a screen reader hears "not assigned to a unit", not "dash".
- The roster's `<caption>` and the summary counts render in an `aria-live="polite"` region so a refresh announces the new
  totals once, not once per card or row.
- Loading grid sets `aria-busy="true"` on the grid container; the error panel is `role="alert"`.
- VIN uses `<span class="vin">` with `font-family: var(--font-mono)` and `user-select: text`;
  the full VIN is always in the DOM. Truncation (`···· 8823`) is CSS-only at narrow widths so
  copy/paste and screen readers get all 17 characters.
- All interactive targets `>= 44px` and carry `:focus-visible` outlines matching the existing
  `.btn-primary` / `.btn-text` treatment.
- `npm run a11y:smoke` must include the new route; no serious/critical axe violations.

---

## 10. Internationalisation (ADR-0030)

Every string via `| translate`. New keys under `SHOPMGMT.SHOP_DASHBOARD.*`, added to **all six**
locale files (`en-US`, `es-US`, `es-MX`, `fr-CA`, `fr-FR`, `qps-ploc`).

| Key | en-US |
|---|---|
| `SECTION_LABEL` | Shop Management |
| `TITLE` | Shop Manager Dashboard |
| `PANEL_ARIA` | Repair unit status by location |
| `LOCATION` | Location |
| `LOCATION_HINT` | Only locations with bays or mobile units |
| `LOCATION_REQUIRED` | Select a location to view its repair units. |
| `DATE` | Date |
| `REFRESH` / `REFRESH_ARIA` | Refresh / Refresh shop dashboard |
| `LAST_UPDATED` | Last updated |
| `LOADING` | Loading repair units… |
| `SECTION.BAYS` | Bays |
| `SECTION.MOBILE_UNITS` | Mobile Units |
| `SUMMARY.UNITS` | `{count} units` |
| `SUMMARY.ACTIVE` / `BLOCKED` / `READY` / `IDLE` | active / blocked / ready / idle |
| `CARD.WORKORDER` | Workorder |
| `CARD.VEHICLE` | Vehicle |
| `CARD.MECHANIC` | Mechanic |
| `CARD.VIN` | VIN |
| `CARD.NO_WORKORDER` | No workorder assigned |
| `CARD.UNIT_AVAILABLE` | Available |
| `CARD.NO_MECHANIC` | Unassigned |
| `CARD.VEHICLE_UNKNOWN` | Vehicle details unavailable |
| `CARD.OPEN_WORKORDER_ARIA` | `Open workorder {number}` |
| `STATUS.DRAFT` … `STATUS.CANCELLED` | Draft … Cancelled (9 values) |
| `ROSTER.TITLE` | Vehicles with open workorders |
| `ROSTER.CAPTION` | `Vehicles with open workorders ({count})` |
| `ROSTER.COL.VEHICLE` | Vehicle |
| `ROSTER.COL.VIN` | VIN |
| `ROSTER.COL.WORKORDER` | Workorder |
| `ROSTER.COL.STATUS` | Status |
| `ROSTER.COL.UNIT` | Unit |
| `ROSTER.NO_UNIT` | Not assigned to a unit |
| `ROSTER.EMPTY` | No open workorders at this location. |
| `ROSTER.TRUNCATED` | `Showing the first {count} open workorders.` |
| `EMPTY_TITLE` | No repair units configured |
| `EMPTY_DESC` | This location has no bays or mobile units yet. |
| `ERROR_LOAD` | Unable to load the shop dashboard. |
| `ERROR_FORBIDDEN` | You do not have permission to view this dashboard. |
| `ERROR_NOT_FOUND` | That location no longer exists. |
| `ERROR_LOCATIONS_LOAD` | Some locations could not be checked; the list may be incomplete. |
| `RETRY` / `RETRY_ARIA` | Retry / Retry loading the shop dashboard |

Landing keys: `SHOPMGMT.LANDING.HERO_CTA_SHOP_DASHBOARD`,
`SHOPMGMT.LANDING.CARD.SHOP_DASHBOARD.TITLE`, `…DESCRIPTION`.

Card headers must not truncate translated status text — French and Spanish status labels run
~40% longer than English. The header wraps to two lines rather than clipping.
`npm run i18n:check` must pass with no missing keys.

---

## 11. File manifest

**New — `durion-positivity-frontend`**

```
src/app/features/shopmgmt/
  models/shop-dashboard.models.ts
  services/shop-dashboard.service.ts
  services/shop-dashboard.service.spec.ts          (required — ADR-0035)
  components/repair-unit-card/
    repair-unit-card.component.ts
    repair-unit-card.component.html
    repair-unit-card.component.css
    repair-unit-card.component.spec.ts
  components/open-workorder-roster/
    open-workorder-roster.component.ts
    open-workorder-roster.component.html
    open-workorder-roster.component.css
    open-workorder-roster.component.spec.ts
  pages/shop-dashboard/
    shop-dashboard-page.component.ts
    shop-dashboard-page.component.html
    shop-dashboard-page.component.css
    shop-dashboard-page.component.spec.ts
```

**Modified — `durion-positivity-frontend`**

```
src/app/features/shopmgmt/shopmgmt.routes.ts               route
src/app/features/shopmgmt/pages/landing/shopmgmt-landing.config.ts   CTAs + card
src/styles.css                                             4 new status tokens x 2 themes
src/assets/i18n/{en-US,es-US,es-MX,fr-CA,fr-FR,qps-ploc}.json
```

**New/modified — backend + SDK**

```
ShopDashboardController (+ OpenAPI annotations)   durion-positivity-backend
OpenAPI.yaml                                      regenerated
@durion-sdk/shopmgmt                              regenerated Angular SDK
```

The repair-unit card and the open-workorder roster are both standalone presentational components
taking a typed input and emitting nothing — neither injects a service, so both are testable without
HTTP mocks and reusable if the dispatch board later adopts the same tile or table. The status band
helper and its CSS classes are shared between them, so a status colour is defined exactly once.

---

## 12. Test plan

**Service** (`shop-dashboard.service.spec.ts`) — every public method, ADR-0035:
- `getDashboard` maps a full payload; maps a payload with null `workorder`/`vehicle`/`mechanic`
- `getDashboard` propagates 403 and 404 distinctly
- `listRepairLocations` keeps a location with bays only, with mobile units only, and drops one
  with neither
- `listRepairLocations` degrades gracefully when the bay call rejects for one location
- date normalisation never shifts the day across a timezone boundary (ADR-0038)

**Card component**
- Each of the 7 bands renders its expected header class, icon and translated status text
- Idle card renders the placeholder and no workorder/vehicle/mechanic rows
- Missing mechanic → `NO_MECHANIC`; missing vehicle → `VEHICLE_UNKNOWN`
- Unknown status string renders the `queued` band with the raw value
- Full VIN present in the DOM regardless of visual truncation

**Roster component**
- Renders one row per `OpenWorkorderRow`, in the order supplied (no client re-sorting)
- A row with no `unitId` renders the em dash with the `ROSTER.NO_UNIT` accessible label
- A row with a `unitId` renders the unit name
- Status cell renders the correct band class, icon and translated text for all 7 open statuses
- Empty `openWorkorders` renders `ROSTER.EMPTY`, not an omitted section
- `openWorkordersTruncated` renders the `ROSTER.TRUNCATED` notice; false omits it
- Full VIN present in the DOM at every viewport
- Table exposes `<caption>`, `<th scope="col">` and `<th scope="row">`

**Page component**
- Renders location-required prompt with no `locationId`
- `loading` → `ready` transition renders the grid; `state` is `error` before `errorKey` is set
- Bays and Mobile Units render in separate sections; an empty section is omitted
- Roster renders below both unit sections and shares the page's single loading/error state
- A location with zero units but non-empty `openWorkorders` renders the units empty state **and**
  the roster
- Location change cancels the in-flight request (stale response must not paint)
- Query params round-trip `locationId` and `date`
- Retry re-issues the request

**Fixtures** must be typed to the real interfaces, not `any` or partials (ADR-0032).

**Gates:** `npx ng test --include="src/app/features/shopmgmt/**/*.spec.ts" --no-watch`,
`npm run i18n:check`, `npm run lint:css`, `npm run a11y:smoke:strict`.

---

## 13. ADR compliance

| ADR | How it is met |
|---|---|
| 0010 frontend domain responsibilities | Page lives in `shopmgmt`; workorder detail stays in `workexec` |
| 0029 accessibility baseline | §9 — WCAG 2.2 AA, axe smoke gate |
| 0030 i18n | §10 — six locale files, no hard-coded strings |
| 0031 mutation/error state | §7 — `state` then `errorKey`, keyed messages |
| 0032 test fixture conformity | §12 — typed fixtures |
| 0033 effect/observable cancellation | §7 — `onCleanup()` teardown on every effect subscription |
| 0034 server-generated field omission | Read-only page; no writes |
| 0035 service method test coverage | §12 — every public service method covered |
| 0037 SPA navigation | §8.3 — `routerLink` only |
| 0038 date-only handling | `date` is an ISO date-only string end to end |
| 0039 information-bearing contrast | §4 — token pairs verified ≥4.5:1 both themes; colour never sole carrier |
| 0041 SDK API transport | Generated SDK service only; no `HttpClient` in the feature |
| 0013 / 0027 UUID identifiers | `locationId`, `unitId`, `workorderId`, `vehicleId`, `personId` all UUID |
| 0017 controller response codes | §5.1 — 200/400/403/404 |
| 0042 OpenAPI annotations | Controller annotated, spec regenerated, SDK regenerated |
| 0044 event-only domain walls | Backend composes via service contracts, not cross-domain table reads |

---

## 14. Open items

1. **Mobile-unit assignment source.** The new endpoint assumes workexec can resolve a workorder
   assigned to a mobile unit. If assignment is modelled only against bays today, the backend story
   must extend the assignment model first — otherwise every mobile-unit card renders idle. **This
   is the highest-risk dependency and should be confirmed before the backend story is sized.**
2. **`hasRepairCapability` projection.** §5.2 fan-out is the interim approach; the location-service
   story replaces it.
3. **Auto-refresh cadence.** Deferred. Manual refresh only in this story.
4. **Multi-vehicle workorders.** The card assumes one vehicle per workorder, matching
   `WorkorderDetailResponse.vehicleId`. If that ever becomes a collection, the card shows the
   primary vehicle and a `+N` affordance.
5. **Roster scope across dates.** The roster is scoped to the selected location and filtered by
   open status; it is deliberately **not** filtered by the `date` parameter, since a workorder that
   has been open for three days is precisely what a manager needs to see. Confirm this with the
   shopmgmt domain owner — if the date must constrain the roster, the endpoint needs an explicit
   `rosterScope` parameter rather than an implicit change of meaning.
6. **Permission name.** `shopmgmt:dashboard:view` is proposed; it must be registered in
   `permissions.yaml` per ADR-0025 before the backend story lands.
