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

---

## Alternatives Considered

1. **Inline error UI independent of `state`**: Maintain a separate `mutationError = signal<string | null>(null)` for mutation-level errors, decoupled from page state.
   Rejected: introduces a parallel state channel that conflicts with the existing `state/errorKey` two-signal pattern currently used in all feature components.

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
