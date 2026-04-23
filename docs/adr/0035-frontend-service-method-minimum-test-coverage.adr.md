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
integration tests" unless the integration test explicitly tests the method in question and is part of the same PR.

### 4. Scope

This ADR applies to:

- `src/app/features/**/*service.ts`
- `src/app/core/**/*service.ts`

Exclusions:

- Private methods (not part of the public API contract)
- Lifecycle hooks (`ngOnInit`, `ngOnDestroy`) tested indirectly via component specs
- Simple passthrough getters that have no assertion value

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
