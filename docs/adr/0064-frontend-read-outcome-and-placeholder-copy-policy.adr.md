---
type: ADR
title: 'ADR-0064: Frontend Read Outcome and Placeholder Copy Policy'
description: Degradable reads must return an outcome, not an empty collection, and placeholder copy may only state what a read actually answered.
status: stable
adr_status: accepted
created: '2026-09-18'
related: [ADR-0030, ADR-0031, ADR-0035, ADR-0063]
tags: [adr, frontend]
---
# ADR-0064: Frontend Read Outcome and Placeholder Copy Policy

**Status:** ACCEPTED **Date:** 2026-09-18 **Deciders:** Frontend Architecture Team, UX Content Lead
**Affected Issues:** PR review findings on durion-positivity-frontend [#274](https://github.com/louisburroughs/durion-positivity-frontend/pull/274),
[#275](https://github.com/louisburroughs/durion-positivity-frontend/pull/275),
[#282](https://github.com/louisburroughs/durion-positivity-frontend/pull/282),
[#289](https://github.com/louisburroughs/durion-positivity-frontend/pull/289)

---

## Context

About 30 review findings on the Shop Capacity Calendar, Dispatch Board and Workexec pick list traced to one root cause: `catchError(() => of(new Map()))`
(or `of([])`) makes an outage and a genuinely empty answer the same value. `DispatchBoardService.getTechnicianRoster` collapsed a roster outage into an
empty map, read by `freeHoursReasonKey` as "not on the location roster" for every mechanic; `getClockStates` did the same for an availability outage,
announced per button via `aria-describedby` before the first clock read had even landed. A failed re-read then wrote that empty map over good state,
bouncing mechanics between rails on one transient 503; once fixed, the retained stale map still drove write offers. `CapacityCalendarService` treated a
`dashboard.bays` projection as the complete roster when `listBays` is the roster of record, and showed "Every bay is full" for a shop with no bays.
`LOCATION_NOT_FOUND` was mapped to "enter a location," and a 403 on a read was indistinguishable from an outage. `WorkexecService.getWorkorderPickList`'s
`forkJoin({ header, tasks })` read a 404 from either leg as "no pick list." The month grid's per-day `viewSchedule` fan-out (durion#474) fired 42 requests
that all 404 for a location with no shop record, rendering as 42 console errors plus a "capacity could not be loaded" banner over an empty calendar (PR
#274); a partial 404/200 mix was shown as valid, and a genuine `degraded` warning was hidden behind the new no-shop state. Three placeholder strings each
named one cause while their shared sentinel was reachable from all three. `BREAK_READ_ONLY` kept blaming HR after the board gained break controls;
`ERROR_CLOCK_FORBIDDEN` named clocking when the 403 came from a break write. `?? 0` treated an unresolvable assignment as zero commitment; `!== undefined`
and a truthy check disagreed on a nullable id.

No ADR previously said a read's outcome is data the UI must keep, not a detail the service layer may swallow. `TechnicianRoster.ok` / `ClockRead.ok` and
the `{ bays, ok }` / `{ roster, ok }` shape of `loadBays` / `loadTechnicians` were each invented locally for one instance; this ADR generalizes the pattern
and covers the copy-truthfulness half a shape fix alone does not.

---

## Decision

### 1. Degradable reads return an outcome, never an empty collection alone

**Decision:** ✅ **Resolved** — Every degradable read (enrichment/decoration/secondary data a page composes past its primary `state`/`errorKey`, per
ADR-0031) returns `{ data, ok }` or a status union, never a bare empty array/map on failure. The component keeps a per-source status signal typed
`'PENDING' | 'OK' | 'FAILED'` (`clockRead` also carries `'NOT_TODAY'`) distinguishing **not yet landed**, **failed**, **answered empty**, and **answered
but this row withheld** (e.g. pos-people nulling `clockState` for a row the caller lacks `people:timekeeping:view` over):

```typescript
// CORRECT (getClockStates) — outcome travels with the data
map(rows => ({ states: toClockStateMap(rows), ok: true })),
catchError(() => of({ states: new Map<string, ClockState>() as ClockStates, ok: false })),
// FORBIDDEN — outage and "nobody" are the same value
catchError(() => of(new Map()))
```

### 2. A failed re-read never overwrites good state; retained state may only place, never authorize

**Decision:** ✅ **Resolved** — When a re-read of an already-succeeded source fails, the component keeps the previously-good data and flips only its status
signal to `FAILED`. Retained data may still say **where something sits** (which rail, which bay) but must never be read as **whether a write on it may
proceed** — placement and write-authorization are separate predicates:

```typescript
// clockIsActionable answers "may this write proceed", never "where does it sit"
readonly clockIsActionable = computed(() => this.isViewingToday() && this.clockRead() === 'OK');
```

A card still renders on its rail from retained roster data while `clockRead()` is `FAILED` or `PENDING`; every clock-in/clock-out/break control still gates
on `clockIsActionable()`.

### 3. 404-as-absence is caught on the one request whose contract promises it

**Decision:** ✅ **Resolved** — A 404 is read as absence only on the single request whose documented contract makes that promise, never on a `forkJoin`,
which cannot tell which leg 404'd. `CapacityCalendarService.loadSchedules` is the pattern: `isScheduleAbsent` is checked per date inside the fan-out's
per-request `catchError`, counting `ABSENT` vs `FAILED` separately (`ScheduleLoad.absent` / `.failed`). Partial absence (`isPartiallyAbsent`) is
**degraded**, not valid; only `absent === total` is unambiguous (`locationHasNoSchedule`). `WorkexecService.getWorkorderPickList`'s `forkJoin({ header,
tasks })` does not yet catch either leg's 404; bringing it into line means catching 404 on `header` alone (the leg whose contract means "no pick list") and
leaving `tasks` 404s as errors:

```typescript
// CORRECT — 404 caught only on the leg whose contract means "absent"; tasks' 404 stays an error
header: this.workorderPickFacade.getWorkorderPickList(workorderId).pipe(
  map(header => ({ header, ok: true as const })),
  catchError((e: unknown) => e instanceof HttpErrorResponse && e.status === 404
    ? of({ header: undefined, ok: true as const, absent: true as const })
    : of({ header: undefined, ok: false as const })),
),
```

### 4. Sentinel truthfulness: one message key, one true claim

**Decision:** ✅ **Resolved** — A key reachable from N causes must be true for all N, or split into N keys; a fix adding a new cause to an existing sentinel
re-reads that sentinel's copy before shipping. `freeHoursReasonKey` splits `CLOSED`, `OFF_ROSTER`, and the generic `UNKNOWN`/`UNKNOWN_COMMITMENT` fallback
into three keys; `binNoteKey` separates `BREAK_OTHER_DAY` (never instructs a drag the page refuses) from `BREAK_DRAG_HINT` (the live branch) from
`BREAK_NO_PERMISSION` (names the missing grant, not HR); `clockHintKey` separates `CLOCK_TODAY_ONLY` (blames the date, never permissions),
`CLOCK_STATE_UNREAD` (a read that has not landed — every card is here on the first round trip of every load), and `NOT_AVAILABLE_CLOCK_STATE` (the one hint
that really is permissions):

```typescript
// FORBIDDEN — asserts a cause the read never confirmed
if (!clock) return 'NOT_AVAILABLE_CLOCK_STATE';
// CORRECT — branches on the read's own outcome first
if (this.clockRead() !== 'OK') return 'CLOCK_STATE_UNREAD';
if (!clock) return 'NOT_AVAILABLE_CLOCK_STATE'; // read succeeded; this row is withheld
```

No "not on roster" unless `rosterRead()` answered `OK` and the person is genuinely absent; no "clock them in first" unless the read said there is no open
session; no zero for an unknown quantity — `entry.shiftMinutes ?? null`, surfaced as `'UNKNOWN_COMMITMENT'`, never coerced. A nullable id is tested the
same way everywhere in one read path — `!== undefined` and a truthy check are never mixed for the same field.

### 5. Never surface a raw identifier as display text or an accessible name

**Decision:** ✅ **Resolved** — A UUID or other internal id is never rendered as visible text or an accessible name when the real label is unavailable; use
`COMMON.NOT_AVAILABLE` or the feature's `NOT_AVAILABLE_*` key (the shop-dashboard "never surface a raw UUID" precedent, extended to every read this ADR
governs). `WorkexecService.toLocationTechnician`'s fallback to `p.personId` when no name resolves is the pattern this decision prohibits.

### 6. Read-path failures route through ADR-0031; polling stops on 403

**Decision:** ✅ **Resolved** — A failure on a page's *primary* read (initial load, unknown location, 403 on the primary resource) goes through the ADR-0031
`state`/`errorKey` machine, naming the actual condition — enrichment reads (section 1) are the carved-out exception. A polling loop reading a resource that
answers 403 stops until a later load succeeds; retrying on interval against a permission the caller lacks produces no new information.

### 7. One test per outcome branch; copy claims are asserted against real locale bundles

**Decision:** ✅ **Resolved** — A component/service exercising this ADR's outcome union has one test per branch (`OK`-empty, `FAILED`, `PENDING`,
`OK`-but-withheld where applicable). Placeholder claims are asserted against real locale bundles per ADR-0035 §8, not a `TranslateModule.forRoot()`
key-echo. `dispatch-board-page.i18n.spec.ts` is the reference: it imports the real `src/assets/i18n/*.json` files and asserts what a string *claims*
("blames the roster for the OFF_ROSTER placeholder"), not merely that the key resolves to some string.

---

## Alternatives Considered

1. **Throw from every enrichment read, caught by a page-level error boundary** — Rejected; decoration failing must not take the whole board down.
2. **A single page-level `degraded: boolean` with no per-source detail** — Rejected; restates the status quo — every placeholder still has to guess which of N causes applies.
3. **Silent empty collections on failure (status quo)** — Rejected; the root cause in Context, and unfixable at the copy layer once the information needed to choose correctly is already discarded.

---

## Consequences

### Positive ✅

- ✅ Outage and genuinely-empty are always distinguishable, at the service boundary and in copy.
- ✅ A transient failure cannot erase good state, and retained data cannot be misread as write authorization.
- ✅ Placeholder copy cannot outlive the cause it names — a new-cause addition triggers a copy re-read.
- ✅ A fan-out's partial failure is visible instead of laundered into false confidence or false alarm.

### Negative ⚠️

- ⚠️ More signals per page — one status signal per enrichment source beyond the ADR-0031 pair, mitigated by keeping each a plain `'PENDING' | 'OK' | 'FAILED'` signal.
- ⚠️ More placeholder keys — splitting one sentinel into N truthful ones grows locale files, though the old single key was already wrong for N−1 of its N causes.

---

## Compliance

### PR Checklist Addition

Add to `durion-positivity-frontend/AGENTS.md` PR checklist under Reads & Placeholder Copy:

- [ ] Every degradable read returns `{ data, ok }` or a status union — never a bare empty array/map
- [ ] A failed re-read does not overwrite good state; retained state gates placement only, never write authorization
- [ ] 404-as-absence is caught on the one request whose contract documents it, never on a `forkJoin`
- [ ] No placeholder key is reachable from more than one cause unless true for all; a new cause on an existing sentinel triggers a copy re-read
- [ ] No raw UUID/id rendered as display text or accessible name — use `COMMON.NOT_AVAILABLE` or a feature `NOT_AVAILABLE_*` key
- [ ] Primary-read failures route through ADR-0031 naming the actual condition; polling stops on 403 until a later load succeeds
- [ ] Tests cover each outcome branch, and copy claims are asserted against real locale bundles (ADR-0035 §8), not a key-echo test double

### Common Violations

| Violation                                                     | Correct Pattern                                                              |
| ---------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `catchError(() => of([]))` / `of(new Map())`                  | `catchError(() => of({ data: [], ok: false }))`                             |
| `if (!clock) return 'NOT_AVAILABLE_CLOCK_STATE'`               | Branch on `clockRead()` first; withheld-row case only after `OK`            |
| `forkJoin({header, tasks}).pipe(catchError(404→null))`         | Catch the 404 on `header` alone; leave `tasks` failures as errors           |
| A failed poll writes its empty result over the last good map  | Keep the last good map; set only the status signal to `FAILED`              |
| `entry.shiftMinutes ?? 0`                                      | `entry.shiftMinutes ?? null`, surfaced as `'UNKNOWN_COMMITMENT'`             |

---

## Implementation Notes

`capacity-calendar.service.ts` (`loadBays`, `loadTechnicians`, `loadSchedules`, `isScheduleAbsent`/`isPartiallyAbsent`/`degraded`/`locationHasNoSchedule`)
and `dispatch-board.service.ts` (`TechnicianRoster.ok`, `ClockRead.ok`) are the reference implementations of sections 1 and 3 — copy their shape for new
degradable reads. `dispatch-board-page.component.ts` (`rosterRead`, `clockRead`, `clockIsActionable`, `freeHoursReasonKey`, `binNoteKey`, `clockHintKey`)
is the reference implementation of sections 2 and 4. `dispatch-board-page.i18n.spec.ts` is the reference implementation of section 7's copy-claim testing;
new placeholder keys get an equivalent assertion in their feature's i18n contract spec. `workexec.service.ts`'s `getWorkorderPickList` does not yet
implement section 3's per-leg 404 handling — bring its `forkJoin({ header, tasks })` into line as a follow-up.

---

## References

- ADR-0030 — Frontend loading/error/empty state conventions
- ADR-0031 — Routed page state machine (`state`/`errorKey`, `onCleanup()`)
- ADR-0035 §8 — i18n testing against real locale bundles
- ADR-0063 — related frontend policy
- `src/app/features/shopmgmt/services/capacity-calendar.service.ts`, `src/app/features/shopmgmt/services/dispatch-board.service.ts`
- `src/app/features/shopmgmt/pages/dispatch-board/dispatch-board-page.component.ts`, `dispatch-board-page.i18n.spec.ts`
- `src/app/features/workexec/services/workexec.service.ts` — `getWorkorderPickList` follow-up target
- durion#474 — the per-day `viewSchedule` fan-out section 3's degradation applies to

