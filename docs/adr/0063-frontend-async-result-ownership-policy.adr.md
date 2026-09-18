---
type: ADR
title: 'ADR-0063: Frontend Async Result Ownership Policy'
description: PR review across four PRs found repeated stale-result, mismatched-key, and dropped-obligation bugs that ADR-0033's cleanup rule alone does not prevent.
status: stable
adr_status: accepted
created: '2026-09-18'
related: [ADR-0031, ADR-0033]
tags: [adr, frontend]
---
# ADR-0063: Frontend Async Result Ownership Policy

**Status:** ACCEPTED **Date:** 2026-09-18 **Deciders:** Frontend Architecture Team **Affected Issues:** PR review findings on durion-positivity-frontend
[#275](https://github.com/louisburroughs/durion-positivity-frontend/pull/275),
[#282](https://github.com/louisburroughs/durion-positivity-frontend/pull/282),
[#288](https://github.com/louisburroughs/durion-positivity-frontend/pull/288),
[#278](https://github.com/louisburroughs/durion-positivity-frontend/pull/278)

---

## Context

ADR-0033 requires `onCleanup` on every subscription an `effect()` creates, which stops a superseded
subscription from continuing to run. It says nothing about what a subscription that **is** allowed to
finish — because it was never cancelled, or because cancellation only stops the read and not an
obligation the read owes — may do with its result. Across four PRs, review found roughly 47 instances of
that gap, all shaped the same way: a result applied itself without checking whether it still answered the
request it was asked for, or an obligation created by a write was released by the wrong read.

Representative findings, mostly in `dispatch-board-page.component.ts`:

- **Ordering races** — a slower earlier board response, or a background poll racing a post-write
  readback, could overwrite `dashboard` with stale data; the bay/roster/clock `forkJoin` in
  `loadEnrichment()` was applied without re-checking the `(locationId, date)` key it was issued for, so a
  response for a shop the dispatcher had left could still repaint bays and rosters.
- **Key re-derived at callback time** — an early `applySuccess` defaulted `key` to the live controls
  instead of taking it as a parameter, caching a response under whatever selection was on screen when it
  *landed*, not the one it was asked *for*.
- **Controls vs. data divergence** — nothing stopped the board rendering interactive rows for selection B
  while `dashboard` still held selection A's data (a drag-and-drop write could hit the wrong shop);
  `picker` (a `PickerRequest`) stayed open across a selection change; an `UndoStep` was armed from the
  optimistic write rather than the board the current read actually produced.
- **Pending guard released by a superseded readback, then stranded permanently.** `pendingWorkorderIds`
  and `pendingClockPersonIds` gate one write per row/person. Clearing a pending id on *any* landing read
  let a later read for the same row race a still-in-flight write; clearing it only on the *current* read
  then left the guard stuck forever when its own read was superseded before applying — permanent row
  lockout (PR #275, rounds 5–6). Resolved by `owedSettlements`/`owedClockSettlements`: `Set<() => void>`
  callbacks drained by whichever read is current when it completes, on every branch including failure.
- **Two writers sharing one sequence counter** — `clockStates` is written by both the enrichment
  `forkJoin` and the lighter clock re-read a clock write triggers. Bumping `enrichmentSeq` from the clock
  path too let an in-flight clock write starve an enrichment read, discarding bays and roster with
  nothing to replace them. Fixed by separate `enrichmentSeq` / `clockSeq` counters.
- Elsewhere: focus restoration scheduled from the write, before the target row's survival was confirmed;
  a chat reply resolved against the open conversation rather than the one that asked, silently dropping
  the answer on a switch; a list refresh with no sequence or identity guard repopulated history from an
  abandoned tenant; Web Speech callbacks from a retired `SpeechRecognition` instance mutated state after
  a new instance replaced it; and a deferred `queueMicrotask` in `AuthService` (PR #278) wrote token
  state unconditionally after logout had already completed.

`customer-list.component.ts` already has the minimal correct shape for the simplest case — one writer, no
cross-signal obligations — via `loadSeq` (`++this.loadSeq` at request start, `if (seq !== this.loadSeq)
return;` in both `next` and `error`). It does not scale once a page has more than one async writer to a
signal, or an obligation that must be paid regardless of which read turns out current. ADR-0033 covers
cancelling the subscription; this ADR covers what a result that was **not** cancelled may do.

---

## Decision

### 1. Every async result carries the request key and sequence it was requested for

**Decision:** ✅ **Resolved** — Every async apply function takes the request's identity key (location+date,
conversation id, tenant+subject) and sequence number as explicit parameters, captured when the request is
issued, never re-derived from current signals inside the callback:

```typescript
private applySuccess(response: DashboardResponse, seq: number, key: string): boolean {
  if (seq !== this.readSeq) return false;
  this.dashboard.set(response);
  this.cachedKey.set(key);   // the key the response ANSWERS, never the live controls
  return true;
}
```

`key` must never default to a live signal (`key = this.requestKey()`); a default re-evaluated at callback
time reintroduces the exact bug this ADR closes.

### 2. One owner per signal

**Decision:** ✅ **Resolved** — A signal written by two independent async sources gets one sequence
counter **per source**, or one documented coordinator both bump — never one counter shared by sources
that must not cancel each other. `enrichmentSeq` (bays/roster) and `clockSeq` (clock state, bumped by both
the enrichment `forkJoin` and the clock re-read) stay separate for this reason; justify the choice in a
comment, as `clockSeq`'s docblock does, before adding a second writer to any signal.

### 3. Controls and data agree

**Decision:** ✅ **Resolved** — A page never renders or enables actions against data whose key differs
from what the controls currently show. `dispatch-board-page.component.ts` gates on
`hasCachedData = computed(() => this.dashboard() !== null && this.cachedKey() === this.requestKey())` and
`showBoard = computed(() => this.hasCachedData() && (this.state() === 'ready' || this.loading()))`. Rows,
drag targets, and mutation entry points derive from a `hasCachedData`-shaped computed, not from the raw
data signal alone. A dialog or picker opened over the old key (`picker`, `dragging`) closes or refuses its
action when the request key changes underneath it — checked at the moment of action, not only at open.

### 4. Settlement ownership

**Decision:** ✅ **Resolved** — An obligation created by a write (pending guard, undo offer, focus
restoration, toast) is **owed**, and is paid by whichever read is current when it completes, including
that read's failure branch. A superseded read parks the obligation in a `Set<() => void>`
(`owedSettlements`) rather than dropping it; an abandoned read (selection cleared, no replacement issued)
still settles it. `loadEnrichment()` drains `owedClockSettlements` unconditionally on
`clockSeq === this.clockSeq`, *before* the separate check gating whether the board repaints on
`enrichmentSeq` and the request key — a mechanic's pending guard is owed to whichever clock read is
current whether or not that response also updates `bayInventory`/`technicianShifts`. Undo is offered only
over a board that already reflects the write's result (`rowReflects`) and re-validates row, permission,
and guard at click time (`canUndo`), not at the moment it was armed.

### 5. Polls and post-write readbacks refresh what the initial load refreshed

**Decision:** ✅ **Resolved** — A poll or post-write readback re-runs every signal path the initial load
populates, under the same key/sequence guards, enrichment included; refreshing `dashboard` but not
`clockStates`/`technicianShifts` leaves those signals answering a superseded state indefinitely.

### 6. Instance identity for browser APIs

**Decision:** ✅ **Resolved** — For long-lived callback-based browser objects (`SpeechRecognition`, timer
handles, `EventSource`), every handler checks identity against its own instance before mutating state
(`if (this.recognition !== instance) return;` before `this.recognition = null` in `onend`). A deferred
callback (`queueMicrotask`, `setTimeout`) re-checks the invariant when it *runs*, not only when scheduled
— the PR #278 finding was a pre-logout microtask that wrote token state unconditionally after logout.

### 7. Identity resets bump every sequence

**Decision:** ✅ **Resolved** — A tenant (`tid`) or subject (`sub`) change bumps every sequence counter on
the page and clears in-memory state **before** any new load starts, so a response in flight for the old
identity cannot land as if it answered the new one.

### 8. Tests drive ordering through a `Subject`, not `of(...)`

**Decision:** ✅ **Resolved** — A test proving an ordering guard drives the async dependency through a
`Subject` so it can resolve requests out of issue order; `of(...)` resolves synchronously and cannot
exercise the race the guard prevents. Every guard needs a test proven to fail without it (cross-reference
ADR-0035 §5–6).

---

## Alternatives Considered

1. **`switchMap` on a `Subject` everywhere** — Rejected as in ADR-0033: RxJS boilerplate in a
   Signals-first codebase, and it only solves the single-writer case, not settlement ownership (§4),
   shared-signal ownership (§2), or controls/data divergence (§3).
2. **A shared generic "latest-request" helper** (e.g. `LatestRequest<T>` wrapping seq/key/apply) —
   Accepted as an optional follow-up, not required; must not obscure §4's page-specific settlement rules.
3. **Block the UI during every in-flight read** — Rejected. The board polls every 30 seconds; disabling
   controls on each poll would make it unusable for a dispatcher working it continuously. `showBoard`
   already keeps stale-but-valid data interactive without blocking.

---

## Consequences

### Positive ✅

- Closes the class of stale-result and mismatched-key bugs found across PRs #275, #278, #282, #288 —
  beyond the subscription-lifecycle half ADR-0033 already covers.
- Makes "who owes this obligation" an explicit, reviewable object (`owedSettlements`) instead of an
  implicit assumption in one code path, and gives reviewers a named pattern to cite.

### Negative ⚠️

- ⚠️ **More state per page** — two independent writers now need two sequence counters plus a settlement
  set, rather than one `loadSeq`; justified by the bugs each omission caused.
- ⚠️ **Easy to under-scope** — a developer adding a second writer to an existing signal without reading §2
  will silently share a counter meant for one source; catch in PR review.

### Neutral

- The single-writer case (`customer-list.component.ts`'s `loadSeq`) needs no change — §1 and §8 already
  describe its shape; this ADR mainly adds obligations where a page has more than one writer or an
  obligation that outlives the read that started it.

---

## Compliance

### PR Checklist Addition

Add to `durion-positivity-frontend/AGENTS.md` PR checklist under Async/State:

- [ ] Async apply functions take request key and sequence as explicit parameters, never re-derived from
      current signals inside the callback
- [ ] A signal written by two async sources has one sequence counter per source (or a documented shared
      coordinator), never one counter shared across sources that must not cancel each other
- [ ] Rendering and mutation entry points gate on a `hasCachedData`-shaped computed, not the raw data
      signal alone; a dialog/picker opened over the old key closes or refuses on a key change
- [ ] Pending guards, undo offers, focus restoration, and toasts are settled by whichever read is current
      on completion (success and failure); a superseded or abandoned read still settles them
- [ ] A poll or post-write readback refreshes every signal path the initial load populated, under the
      same guards
- [ ] Browser-API callbacks check instance identity before mutating state, including deferred callbacks
- [ ] An identity change (tenant/subject) bumps every sequence and clears state before the new load starts
- [ ] Ordering/race tests drive the async dependency through a `Subject`, not `of(...)`, proven to fail
      without the guard tested

### Common Violations

| Violation | Correct Pattern |
| --------- | ---------------- |
| `applySuccess(res, key = this.requestKey())` | `applySuccess(res, requestKey)` — key captured at request-issue time, passed explicitly |
| `onSettled()` called unconditionally on response | Settle only if `seq === this.readSeq`; else add to `owedSettlements`/`owedClockSettlements` for the current read to drain |
| One `seq` shared by two independent writers to a signal | Separate counters per writer (`enrichmentSeq` / `clockSeq`), unless a coordinator is deliberately shared and documented |
| Pending guard cleared on "any read lands" | Clear only when the read is current; if superseded, move the obligation to `owedSettlements` |
| Rendering `dashboard()` rows directly | Render behind `hasCachedData()` / `showBoard()`, comparing `cachedKey()` to live `requestKey()` |
| `this.recognition = null` unconditionally in `onend` | `if (this.recognition !== instance) return;` before mutating state in any handler on a retired instance |
| `queueMicrotask(() => this.token.set(...))`, unguarded | Re-check the invariant inside the microtask when it runs, not only when scheduled |
| Race test built on `of(mockResponse)` | Build it on a `Subject<Response>()` to drive resolution out of issue order |

---

## Implementation Notes

- Add to `durion-positivity-frontend/AGENTS.md` alongside the ADR-0033 cleanup pattern — read together:
  ADR-0033 stops a superseded subscription from running further work, this ADR governs what a
  subscription that does complete may do.
- Reference `dispatch-board-page.component.ts` (`readSeq`, `enrichmentSeq`, `clockSeq`, `requestKey`,
  `cachedKey`, `hasCachedData`, `showBoard`, `owedSettlements`, `owedClockSettlements`,
  `pendingWorkorderIds`, `markClockPending`, `UndoStep`, `applySuccess`) as the canonical multi-writer
  example, and `customer-list.component.ts`'s `loadSeq` as the minimal single-writer baseline.
- PR review prompt: "Does this signal have more than one async writer? Does each obligation a write
  creates have a settlement path that runs on every branch, including a superseded or abandoned read?"

---

## References

- ADR-0031 — the two-signal `state`/`errorKey` state machine this ADR's apply functions write into.
- ADR-0033 — Angular Effect Observable Cancellation Policy; required reading first, extended not repeated.
- ADR-0035 §5–6 — mutation-proven test requirements, applied here to ordering/race guards (§8).
- `durion-positivity-frontend/src/app/features/shopmgmt/pages/dispatch-board/dispatch-board-page.component.ts`
- `durion-positivity-frontend/src/app/features/crm/pages/customer-list/customer-list.component.ts`
