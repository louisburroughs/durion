---
type: ADR
title: 'ADR-0035: Frontend Service Method Minimum Test Coverage'
description: 'During PR review of Wave I-a (PR #12), two public service methods introduced by the PR had zero unit tests:'
status: stable
adr_status: accepted
created: '2026-03-29'
tags: [adr, frontend]
---
# ADR-0035: Frontend Service Method Minimum Test Coverage

**Status:** ACCEPTED **Date:** 2026-03-29 **Deciders:** Frontend Architecture Team, QA Lead **Affected Issues:** PR #12 review findings — threads r3006417718
(getAllLocations), r3006417773 (getLocationInventory); CAP-165–170

---

## Context

During PR review of Wave I-a (PR #12), two public service methods introduced by the PR had zero unit tests:

- `ProductLocationService.getAllLocations()` (thread r3006417718)
- `ProductInventoryService.getLocationInventory()` (thread r3006417773)

The absence of tests for these methods means:

- Future regressions in URL construction, parameter encoding, or return-type handling go undetected until integration testing or production.
- Reviewers must manually verify method correctness by reading the implementation rather than relying on test evidence.
- SDK contract changes (e.g., a renamed path segment) would not be caught at the unit test layer.

This is a recurring pattern across feature PRs: new service methods ship without even a single smoke-test asserting the correct endpoint is called.

---

## Decision

### 1. Minimum Coverage Requirement

**Decision:** ✅ **Resolved** — Every public method on a `*Service` class introduced or modified in a PR must have at least one test in the sibling `*.service.spec.ts` file at
the time of PR merge.

For HTTP-calling service methods, the minimum test must assert:

1. The correct HTTP method (GET, POST, PUT, PATCH, DELETE) is called
2. The correct URL (or URL pattern) is used
3. The Observable emits the mocked backend response on success

Minimum example:

```typescript
describe("getAllLocations()", () => {
  it("calls GET /location/v1/locations and returns location array", () => {
    const expectedLocations: ProductLocation[] = [{ locationId: "loc-1", name: "Main", active: true }];

    service.getAllLocations().subscribe((result) => {
      expect(result).toEqual(expectedLocations);
    });

    const req = httpTesting.expectOne("/location/v1/locations");
    expect(req.request.method).toBe("GET");
    req.flush(expectedLocations);
  });
});
```

### 2. URL Parameter Encoding

**Decision:** ✅ **Resolved** — For service methods that encode user-supplied values into the URL path (e.g., SKU codes, location IDs), the minimum test must include one case
that verifies `encodeURIComponent` is applied when the value contains URL-unsafe characters (spaces, `/`, `+`, etc.).

```typescript
it("URL-encodes the sku parameter", () => {
  service.getLocationInventory("SKU WITH SPACES").subscribe();

  const req = httpTesting.expectOne((r) => r.url.includes("SKU%20WITH%20SPACES"));
  req.flush([]);
});
```

### 3. PR Review Gate

**Decision:** ✅ **Resolved** — A missing test for a public service method introduced in the PR is a **blocking** finding. No waiver is permitted on the grounds of "covered by
integration tests" unless the integration test explicitly tests the method in question and is part of the same PR. A missing **load-bearing test** for a new guard, branch,
or negative case (§5) is likewise a blocking finding, not merely a suggestion.

### 4. Scope

This ADR applies to:

- `src/app/features/**/*service.ts`
- `src/app/core/**/*service.ts`

Exclusions:

- Private methods (not part of the public API contract)
- Lifecycle hooks (`ngOnInit`, `ngOnDestroy`) tested indirectly via component specs
- Simple passthrough getters that have no assertion value

### 5. Load-Bearing Tests

**Decision:** ✅ **Resolved** — Every guard, branch, or negative case introduced by a change — and every fix for a review finding — must have a test that is shown to fail
when that fix or guard is reverted (a manual mutation check: revert the change, run the test, confirm it goes red, restore the change). A test that exists but does not
observably fail under the mutation it claims to defend does not satisfy §1 or §5. For a review-finding fix, state in the PR description that this check was performed
(e.g. "reverted the lockout condition, confirmed `should reject after 5 attempts` fails, restored it").

Evidence (PR review, Sept 2026): a mutation audit on the dispatch board found 11 of 22 deliberate production breakages survived the entire suite — the tests that were
supposed to guard those branches passed whether or not the guard existed. In the same audit, one test encoded a lockout bug as intended behavior; because no one had
reverted the fix and watched the test fail, the test asserted the bug rather than the guarantee.

### 6. Asynchronous Collaborators in Ordering Tests

**Decision:** ✅ **Resolved** — Any test of a sequence guard, stale-response guard, pending/in-flight guard, focus restoration after a readback, or settlement behavior
must drive the async dependency through an RxJS `Subject` (or another deferred-emission construct), never a synchronous `of(...)`. A synchronous stub resolves before the
guard under test can ever observe the "pending" state, making the guard unreachable by the test. Synchronous stubs remain acceptable only where ordering is irrelevant to
the assertion.

Evidence: 28 of 65 tests in the same audit stubbed an async read with `of(...)`, silently making every ordering guard in those specs unreachable.

### 7. Both Halves of a Split

**Decision:** ✅ **Resolved** — When behavior branches on a discriminator (permission A vs. permission B, read answered vs. read failed, today vs. another day, user told
vs. not told, etc.), tests must assert **both** branches, including the negative one. A test suite that only pins the positive half of a split has not tested the split.

Evidence: a permission split in the same audit was pinned only on its positive half, leaving the negative (denied) branch unverified.

### 8. Copy Assertions Use Real Bundles

**Decision:** ✅ **Resolved** — An assertion about what a user-facing string says or claims must load the real `src/assets/i18n/*.json` bundle files, not a copy of locale
strings pasted into the spec. `TranslateModule.forRoot()` configured with no loader renders translation keys verbatim, so a "rendered text" assertion made against a
spec-local copy of the strings is a key assertion by construction — it does not verify the copy shown to users, and drifts silently if the real bundle changes.

**Exemplar:** `dispatch-board-page.i18n.spec.ts`.

### 9. SDK Call Shape

**Decision:** ✅ **Resolved** — When a service method calls a generated SDK method using positional arguments, at least one test must assert the full argument list (not
just the return value). An unasserted positional argument list lets an inserted or reordered parameter shift silently through the call with no test failure.

---

## Alternatives Considered

1. **Enforce via coverage thresholds (`--coverage --threshold`)**: Add per-file line/function coverage thresholds to `angular.json` or `vitest.config.ts`. Accepted as a
   complementary enforcement mechanism; does not replace the semantic requirement for at least one test asserting the correct URL is called.

2. **Rely on component spec coverage for services**: Component `TestBed` setups mock services entirely (`vi.fn()`), so they do not exercise service implementation. Service
   unit tests using `HttpTestingController` are the only first-class mechanism for verifying HTTP contract correctness. Rejected as a valid substitute.

3. **Generate tests from OpenAPI spec**: Use the `durion-positivity-sdk` OpenAPI spec to autogenerate test stubs. Accepted as a useful future improvement but not a current
   blocker; the SDK is already used as the source of truth for interface types.

---

## Consequences

### Positive ✅

- SDK contract changes (path, method, parameter names) are caught immediately at the service layer.
- URL encoding correctness is tested, preventing `400 Bad Request` errors with special-character inputs.
- PR reviewers have test evidence to verify service method behavior rather than reading implementation code.
- Eliminates a recurrent category of PR comments across Wave PRs.

### Negative ⚠️

- Increases minimum test-writing obligation per PR. For a PR introducing five new service methods, five test blocks are required.
- `HttpTestingController` setup is boilerplate-heavy; teams may need a shared test setup helper per service spec file.

### Neutral

- Does not increase absolute test count significantly if service methods are small and focused (one HTTP call per method).

---

## Implementation Notes

- Add to `durion-positivity-frontend/AGENTS.md` as a mandatory PR checklist item.
- Add to frontend story templates: "Service methods introduced in this story have ≥1 test in `*.service.spec.ts` verifying HTTP verb and URL."
- For `HttpTestingController`-based specs, use `provideHttpClient()` + `provideHttpClientTesting()` in the `TestBed` setup (Angular 21 functional providers pattern).
- **Verified test command** (run targeted suite only):

  ```bash
  npx ng test --include="src/app/features/<domain>/**/*.spec.ts" --no-watch
  ```

- A frontend mutation-check helper — analogous to `durion/.claude/hooks/mutation-check-hook.sh` for the backend — that reverts a guard, runs the targeted spec, confirms
  red, and restores the file is a recommended follow-up to automate the §5 load-bearing-test check.

---

## Changelog

- **2026-09-18**: Added §5 (Load-Bearing Tests), §6 (Asynchronous Collaborators in Ordering Tests), §7 (Both Halves of a Split), §8 (Copy Assertions Use Real Bundles), and
  §9 (SDK Call Shape) based on a September 2026 mutation audit of the dispatch board PR (11 of 22 breakages survived the suite); updated §3 to make a missing load-bearing
  test a blocking finding.
