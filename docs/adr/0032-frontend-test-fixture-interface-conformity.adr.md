# ADR-0032: Frontend Test Fixture Interface Conformity

**Status:** ACCEPTED **Date:** 2026-03-29 **Deciders:** Frontend Architecture Team, QA Lead **Affected Issues:** PR #12 review findings — threads r3006417688, r3006417693,
r3006417712, r3006417718, r3006417773; CAP-165, CAP-166, CAP-167, CAP-168, CAP-170

---

## Context

During PR review of Wave I-a (PR #12), 4 of 18 review threads identified spec mock objects using field names that do not exist on the corresponding TypeScript interface:

- `sampleMsrp` used `price` — the `Msrp` interface defines `amount` and `currency`, not `price`
- `InventoryAvailability` mocks used `quantityOnHand` — the interface defines `totalOnHand`, `totalReserved`, `totalAtp`
- `sampleAvailability` used `quantityOnHand` and `leadTimeDays` — the `SkuAvailability` interface defines `onHand`, `reserved`, `atp`, `asOf`, `locationId`

In all cases the spec fixtures were declared as untyped object literals (or typed as `any`). TypeScript could not catch the invalid field names at compile time.

The consequence is that specs appear to test the real component/service behavior but are actually testing against phantom fields, providing false confidence and failing to
catch real regressions when model shapes change.

---

## Decision

### 1. Typed Fixture Declarations

**Decision:** ✅ **Resolved** — All test fixture constants (sample objects, mock return values) must be explicitly typed as the exact domain interface. No `any`, `object`, or
structurally inferred types.

Required pattern:

```typescript
// CORRECT — TypeScript enforces field names at compile time
const sampleMsrp: Msrp = {
  id: "msrp-1",
  productSku: "SKU-001",
  amount: 49.99,
  currency: "USD",
  effectiveAt: "2026-01-01T00:00:00Z",
  endAt: null,
};

// WRONG — untyped; phantom fields are silently accepted
const sampleMsrp = { id: "msrp-1", price: 49.99 };

// WRONG — typed as any; phantom fields are silently accepted
const sampleMsrp: any = { id: "msrp-1", price: 49.99 };
```

### 2. Mock Return Value Typing

**Decision:** ✅ **Resolved** — When using `vi.fn()` / `mockReturnValue()` / `mockResolvedValue()`, the resolved value must match the mocked method's return type. Prefer
storing the mock value in a typed constant first.

```typescript
// CORRECT
const availability: InventoryAvailability = {
  totalOnHand: 5,
  totalReserved: 1,
  totalAtp: 4,
  locationBreakdown: [],
};
mockService.getAvailability.mockReturnValue(of(availability));

// WRONG — shape is unknown until runtime
mockService.getAvailability.mockReturnValue(of({ quantityOnHand: 5 }));
```

### 3. Service Method Coverage Minimum

**Decision:** ✅ **Resolved** — Every public method on a `*Service` class must have at least one test in the sibling `*.service.spec.ts` file.

Minimum test requirements for HTTP-calling service methods:

1. Asserts the correct HTTP verb and URL are used (via `HttpTestingController` or mock assertion)
2. Asserts the correct value is emitted on success

This minimum applies at the time of the PR that introduces the method. Test coverage gaps on new service methods are a blocking finding in PR review.

### 4. Scope

This ADR applies to:

- All `*.spec.ts` files in `src/app/features/**`
- All `*.spec.ts` files in `src/app/core/**`

It does not apply to:

- E2E / Playwright test fixtures (governed separately)
- Type testing files (e.g., `*.type-test.ts`)

---

## Alternatives Considered

1. **Rely on TypeScript's structural typing without explicit annotations**: Objects satisfying the interface shape are accepted by TypeScript even without
   `const x: Interface =`. Rejected: structural inference allows extra fields and does not catch omitted required fields until they are actually used.

2. **Integration tests instead of unit tests for shape coverage**: Surface field regressions through integration or E2E tests. Rejected as insufficient: integration tests are
   slower and less targeted; unit tests with typed fixtures catch shape regressions at the fastest possible feedback point.

3. **Code generation from OpenAPI specs for fixtures**: Auto-generate factory functions from SDK types. Accepted as a complementary improvement (durion-positivity-sdk already
   generates interface types), but does not replace the requirement for explicit typing in spec files.

---

## Consequences

### Positive ✅

- TypeScript compile-time errors immediately surface when models change and fixtures are not updated.
- Spec fixtures serve as living documentation of the expected model shape.
- Eliminates a category of PR review comments (4 of 18 in Wave I-a).
- Prevents false-passing tests that test phantom fields.

### Negative ⚠️

- Slightly more verbose fixture declarations when all required fields must be specified.
- Existing specs with untyped fixtures require a one-time audit pass.

### Neutral

- Aligns with TypeScript strict mode already required by `tsconfig.json`.

---

## Implementation Notes

- Add to `durion-positivity-frontend/AGENTS.md` as a mandatory PR checklist item.
- TypeScript strict mode (`strict: true`) in `tsconfig.spec.json` is already active; enforcing explicit types on fixture constants is a convention-level complement to that
  setting.
- When `durion-positivity-sdk` generates updated interface types, search all `*.spec.ts` files for usages of the updated type and verify fixture shapes are still conformant.
