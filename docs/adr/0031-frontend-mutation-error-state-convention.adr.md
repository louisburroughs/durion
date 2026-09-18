---
type: ADR
title: 'ADR-0031: Frontend Mutation Error State Convention'
description: The Angular frontend uses a state = signal<PageState>('idle' | 'loading' | 'empty' | 'ready' | 'error') pattern in feature components. The page error banner is only rendered when state() === 'error'.
status: stable
adr_status: accepted
created: '2026-03-29'
related: [ADR-0029]
tags: [adr, frontend]
---
# ADR-0031: Frontend Mutation Error State Convention

**Status:** ACCEPTED **Date:** 2026-03-29 **Deciders:** Frontend Architecture Team, QA Lead **Affected Issues:** PR #12 review findings — threads r3006417696, r3006417722,
r3006417728, r3006417740, r3006417745, r3006417749, r3006417780; CAP-165, CAP-166, CAP-167, CAP-168, CAP-170

---

## Context

The Angular frontend uses a `state = signal<PageState>('idle' | 'loading' | 'empty' | 'ready' | 'error')` pattern in feature components. The page error banner is only rendered
when `state() === 'error'`.

During PR review of Wave I-a (PR #12), 10 of 18 review threads were variations of the same defect: mutation `subscribe({ error })` handlers set `errorKey` but not
`state.set('error')`. As a result, backend failures during mutations were silently swallowed — `errorKey` held an error message that the template never showed.

This pattern was repeated independently across five components (`product-detail`, `msrp`, `price-books`, `location-overrides`, `locations-roster`), demonstrating the absence
of a documented coding convention.

---

## Decision

### 1. Mutation Error Handler Structure

**Decision:** ✅ **Resolved** — Every `subscribe({ error })` block that sets an `errorKey` signal **must** call `this.state.set('error')` on the line immediately before
`this.errorKey.set(...)`.

Required pattern:

```typescript
this.someService
  .doMutation(payload)
  .pipe(takeUntilDestroyed(this.destroyRef))
  .subscribe({
    next: (result) => {
      /* handle success */
    },
    error: () => {
      this.state.set("error"); // REQUIRED — always first
      this.errorKey.set("DOMAIN.FEATURE.ERROR.KEY"); // REQUIRED — always second
    },
  });
```

**Prohibited patterns:**

```typescript
// WRONG — errorKey without state
error: () => this.errorKey.set('DOMAIN.FEATURE.ERROR.KEY'),

// WRONG — wrong order (state may render briefly before errorKey is set)
error: () => {
  this.errorKey.set('DOMAIN.FEATURE.ERROR.KEY');
  this.state.set('error');
},
```

### 2. Scope of This Convention

**Decision:** ✅ **Resolved** — This convention applies to all component-level RxJS subscriptions to mutation operations (create, update, delete, lifecycle transitions,
approvals).

Exclusions:

- **Non-fatal inline errors** (e.g., field validation that renders inline without changing page state): `errorKey` alone may be used if the template renders it independently
  of `state`.
- **Router-level data loads** in `constructor` / `ngOnInit`: error handling pattern is determined per-component based on whether the error is recoverable from without a full
  page state transition.

### 3. Test Requirement

**Decision:** ✅ **Resolved** — Every error path test for a mutation that uses `errorKey` must assert **both** `state() === 'error'` and the correct `errorKey()` value.

```typescript
it("doSomething() sets error state on failure", () => {
  mockService.doSomething.mockReturnValueOnce(throwError(() => new Error()));
  component.doSomething(payload);

  expect(component.state()).toBe("error"); // REQUIRED
  expect(component.errorKey()).toBe("DOMAIN.FEATURE.ERROR.X"); // REQUIRED
});
```

### 4. Operational-Board Refusal Pattern

**Decision:** ✅ **Resolved** — A mutation on a live, polling operational board that keeps cached data visible (e.g. the dispatch board, shop dashboard, capacity calendar,
mechanic availability) **may** report a per-row refusal — a named `409`/`422` refusal or a generic write failure — through an assertive live region (`role="alert"` /
`aria-live="assertive"`, a toast signal) **without** calling `this.state.set('error')`, provided all three conditions hold:

1. The refusal triggers a re-read of the affected data, so the board never continues to display state the backend has just contradicted.
2. The test for the refusal asserts the message key, the tone (e.g. `error`/`warn` styling), the live-region role, **and** that `state()` remains `'ready'` and the board
   stays interactive (rows remain actionable, no full-page error takeover).
3. Read-path failures — the initial load, or a `403` on a subsequent read — still follow §1 (`state.set('error')` first, then `errorKey.set(...)`). This exception governs
   the write path only.

This is a **decided exception** to §1 for the operational-board case, adopted via
[issue #492](https://github.com/louisburroughs/durion/issues/492) (Option A, recommended by both reviewers). It does not relax §1 for any component outside a live,
polling operational board.

**Exemplar:** `durion-positivity-frontend/src/app/features/shopmgmt/pages/dispatch-board/dispatch-board-page.component.ts`, the `run()` error branch; its refusal tests in
`dispatch-board-page.component.spec.ts`; and `dispatch-board-page.a11y.spec.ts`, introduced in
[PR #275](https://github.com/louisburroughs/durion-positivity-frontend/pull/275).

### 5. Signal Write Order

**Decision:** ✅ **Resolved** — Whenever a handler writes both `state` and `errorKey`, `state` is written **first**, in both directions: setting into the error state
(§1) and clearing back out of it. `errorKey` must never be cleared before `state` has moved off `'error'`, and must never be set before `state` has moved onto `'error'`.

```typescript
// WRONG — errorKey cleared before state moves off 'error'
this.errorKey.set(null);
this.state.set("ready");

// CORRECT
this.state.set("ready");
this.errorKey.set(null);
```

Found in the [PR #275](https://github.com/louisburroughs/durion-positivity-frontend/pull/275) review thread: a retry-success handler cleared `errorKey` a tick before
`state` left `'error'`, leaving a one-frame window where the template's `state() === 'error'` check and `errorKey()` disagreed.

---

## Alternatives Considered

1. **Inline error UI independent of `state`**: Maintain a separate `mutationError = signal<string | null>(null)` for mutation-level errors, decoupled from page state.
   Rejected: introduces a parallel state channel that conflicts with the existing `state/errorKey` two-signal pattern currently used in all feature components.
   _2026-09-18 amendment:_ §4 carves out the operational-board refusal case specifically because that parallel channel (an assertive live region, not `state`) is the
   correct tool when re-reading keeps the board itself truthful — the general rejection above still holds everywhere else.

2. **Abstract error handler utility function**: Create a shared `handleMutationError(state, errorKey, key)` helper. Rejected for pre-production: over-abstraction for a
   two-line pattern; adds indirection without benefit at current codebase scale. Revisit if the pattern diversifies significantly.

3. **ESLint custom rule**: Enforce via lint. Accepted as a complementary enforcement mechanism, not a replacement for this ADR. See Implementation Notes.

---

## Consequences

### Positive ✅

- Mutation failures are always visible to users — the error banner renders reliably.
- Consistent error handler structure reduces cognitive load across all feature components.
- Error path test assertions become deterministic and reviewable against a known standard.
- Eliminates the single largest category of PR review comments in Wave I-a (10 of 18 threads).

### Negative ⚠️

- Requires audit of all existing feature components to identify handlers already in production that violate this pattern (pre-production scope: low risk, but audit is needed
  before beta launch).

### Neutral

- Pattern is already implemented correctly in all components touched by PR #12 remediation.

---

## Implementation Notes

- Add this convention to `durion-positivity-frontend/AGENTS.md` as a mandatory PR checklist item.
- Optional (post-Wave II): implement a custom ESLint rule that detects `subscribe({ error })` blocks that call `errorKey.set(` without a preceding `state.set('error')` on the
  same error-handler line.
- Cross-reference with ADR-0029 (accessibility): `state === 'error'` is what triggers the `role="alert"` error banner, so this convention is part of the accessibility
  contract.
- Add the §4/§5 checklist items to `durion-positivity-frontend/AGENTS.md`: (1) an operational-board write-path refusal uses an assertive live region and re-reads instead
  of `state.set('error')`, with a test asserting message key, tone, live-region role, and `state() === 'ready'`; (2) `state` is always written before `errorKey`, on
  clearing as well as on setting.

---

## Changelog

- **2026-09-18**: Added §4 (Operational-Board Refusal Pattern) and §5 (Signal Write Order) per
  [issue #492](https://github.com/louisburroughs/durion/issues/492) (Option A) and the
  [PR #275](https://github.com/louisburroughs/durion-positivity-frontend/pull/275) review thread; annotated Alternative 1 with the carve-out rationale.
