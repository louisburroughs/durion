# ADR-0033: Angular Effect Observable Cancellation Policy

**Status:** ACCEPTED **Date:** 2026-03-29 **Deciders:** Frontend Architecture Team **Affected Issues:** PR #12 review finding — thread r3006417734; CAP-165–170

---

## Context

Angular Signals' `effect()` function re-executes its body whenever a reactive dependency (signal read) changes. If an `effect()` body initiates an RxJS subscription (e.g., to
`forkJoin`, `combineLatest`, or a service call), and the signal changes again before the previous subscription completes, a new subscription is created while the old one is
still in-flight.

This produces a race condition:

1. Signal changes to value A → subscription to `serviceCall(A)` starts
2. Signal changes to value B (before A resolves) → subscription to `serviceCall(B)` starts
3. `serviceCall(A)` completes and overwrites state with stale result A — rendering wrong data

In PR #12, `price-books.component.ts` contained an `effect()` that subscribed to `forkJoin({ priceBook, rules })` without cancelling the previous subscription, creating this
exact race condition.

---

## Decision

### 1. Required Cleanup Pattern for Subscriptions in Effects

**Decision:** ✅ **Resolved** — Any `effect()` body that creates an RxJS `Subscription` must register `onCleanup(() => subscription.unsubscribe())` within the same effect run.

Required pattern:

```typescript
import { effect, signal, inject } from '@angular/core';
import { Subscription } from 'rxjs';

constructor() {
  effect((onCleanup) => {
    const id = this.selectedId();   // reactive dependency
    if (!id) return;

    this.state.set('loading');

    const sub: Subscription = this.service
      .loadData(id)
      .subscribe({
        next: data => { this.data.set(data); this.state.set('ready'); },
        error: () => { this.state.set('error'); this.errorKey.set('...'); },
      });

    onCleanup(() => sub.unsubscribe());  // REQUIRED — cancels inflight request on re-run
  }, { allowSignalWrites: true });
}
```

**Prohibited pattern:**

```typescript
// WRONG — no cleanup; stale-result race condition
effect(() => {
  const id = this.selectedId();
  this.service.loadData(id).subscribe({ ... });  // previous sub never cancelled
}, { allowSignalWrites: true });
```

### 2. `takeUntilDestroyed` Scope

**Decision:** ✅ **Resolved** — `takeUntilDestroyed(this.destroyRef)` is the correct pattern for subscriptions outside `effect()` (e.g., in constructor route-param
subscriptions, or mutation pipelines). It must not be used inside `effect()` bodies as a substitute for `onCleanup` — `onCleanup` fires on each reactive re-run, whereas
`takeUntilDestroyed` only fires on component destruction.

Summary table:

| Subscription context                     | Required cancellation pattern                |
| ---------------------------------------- | -------------------------------------------- |
| Inside `effect()` body                   | `onCleanup(() => sub.unsubscribe())`         |
| Constructor / `ngOnInit` (non-effect)    | `.pipe(takeUntilDestroyed(this.destroyRef))` |
| Mutation handler (`subscribe` in method) | `.pipe(takeUntilDestroyed(this.destroyRef))` |

### 3. `forkJoin` and `combineLatest` Specifically

`forkJoin` and `combineLatest` inside an `effect()` must always use `onCleanup`. These are the highest-risk patterns because they involve multiple concurrent HTTP requests
that can all resolve out of order relative to reactive re-triggers.

---

## Alternatives Considered

1. **Use `toSignal()` with async pipe instead of explicit subscription**: Converts an Observable to a Signal directly, automatically handling subscription lifecycle. Accepted
   as the preferred pattern for _read-only data display_ in future greenfield components. For components that need to react to a signal value and load associated data into
   multiple signals, the `effect` + `onCleanup` pattern remains appropriate.

2. **Use `switchMap` with a Subject**: Emit the new signal value into a Subject and pipe through `switchMap` to auto-cancel. Rejected: adds more RxJS boilerplate in a
   Signals-first architecture; the `onCleanup` callback is the idiomatic Angular 17+ solution.

3. **Rely on `takeUntilDestroyed` inside `effect()`**: Only cancels on component destroy, not on reactive re-run. Does not prevent stale-result races. Rejected.

---

## Consequences

### Positive ✅

- Eliminates stale-result race conditions on reactive data loads.
- Clear pattern that is idiomatic with Angular 17+ Signals API.
- Prevents unnecessary concurrent HTTP requests.

### Negative ⚠️

- Requires explicit `Subscription` variable and `onCleanup` registration — slightly more verbose than an inline `.subscribe()` call.
- Developers unfamiliar with the `onCleanup` API may omit it; must be caught in PR review.

### Neutral

- `allowSignalWrites: true` is still required when the effect writes back to signals (e.g., `state.set(...)`, `data.set(...)`).

---

## Implementation Notes

- Add to `durion-positivity-frontend/AGENTS.md` as a code pattern reference.
- Include in frontend story templates: "If loading data in `effect()`, ensure `onCleanup` cancellation is present."
- Add to PR review checklist: "Effect bodies that call `.subscribe()` register `onCleanup`."
