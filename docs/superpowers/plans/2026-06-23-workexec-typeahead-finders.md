# Workexec Typeahead Finders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace raw-id entry on `/app/workexec` with two typeahead finders (Find Estimate / Find Workorder) that search by customer name or record id/number and navigate to the selected record.

**Architecture:** Backend `pos-workorder` gains a unified `q` search per entity — it resolves customer name → customerIds via pos-customer's party browse, unions with local `estimateNumber`/UUID matching, and enriches `customerName`. Regenerate the Angular SDK, then add a reusable accessible typeahead component + two finders on the workexec landing. Shipped as chained PRs (backend → SDK → frontend).

**Tech Stack:** Java 21 / Spring Boot (pos-workorder), JPA/H2 tests, OpenAPI + openapi-generator (typescript-angular), Angular standalone + signals + ngx-translate, Vitest.

**Spec:** `docs/superpowers/specs/2026-06-23-workexec-typeahead-finders-design.md`

---

## File Structure

**Backend (durion-positivity-backend, `pos-workorder`)** — worktree off `origin/main`:
- Modify `internal/service/CustomerReferenceService.java` — add `searchIdsByName(q, limit)`.
- Modify `internal/dto/EstimateSummaryResponse.java` — add `customerName`.
- Modify `service/EstimateService.java` + `internal/service/EstimateServiceImpl.java` — add `findEstimatesByQuery(q, pageable)`.
- Modify `internal/repository/EstimateRepository.java` — add `q` finders.
- Modify `internal/controller/EstimateSearchController.java` — add `q` param.
- Create `internal/dto/WorkorderSearchResult.java`.
- Create `internal/service/WorkorderSearchService.java` (+ impl) or add to an existing workorder service.
- Modify `internal/repository/WorkorderRepository.java` — add `findByCustomerIdIn`.
- Create `internal/controller/WorkorderSearchController.java`.
- Tests under `pos-workorder/src/test/...`.

**SDK (durion-positivity-sdk-angular)** — regen `workorder` module (generated files only).

**Frontend (durion-positivity-frontend)** — branch off `master`:
- Create `features/workexec/components/search-typeahead/workexec-search-typeahead.component.{ts,html,css,spec.ts}`.
- Modify `features/workexec/models/workexec.models.ts` — add `SearchResultItem`.
- Modify `features/workexec/services/workexec.service.ts` — add `searchEstimates`/`searchWorkorders`.
- Modify `features/workexec/pages/landing/workexec-landing-page.component.{ts,html}` — finders section.
- Modify `src/assets/i18n/{en-US,es-US,fr-CA,qps-ploc}.json`.

---

## PHASE 1 — Backend (PR #1)

Worktree: `git worktree add -b feat/workexec-search ../durion-positivity-backend-search origin/main` (run from the main backend checkout). All paths below are relative to that worktree.

### Task 1: Customer name → ids lookup

**Files:**
- Modify: `pos-workorder/src/main/java/com/positivity/workorder/internal/service/CustomerReferenceService.java`
- Test: `pos-workorder/src/test/java/com/positivity/workorder/internal/service/CustomerReferenceServiceTest.java`

- [ ] **Step 1: Write the failing test** (append to the existing test class, or create it mirroring the resolve tests; mock the RestClient chain like the existing tests do).

```java
@Test
void searchIdsByName_returnsPartyIdsFromBrowse() {
    // Arrange the mocked customerRestClient to return a SearchPartiesResponse-shaped body for
    // GET /v1/crm/accounts/parties?name=ace with two PartySummary rows (partyId p1, p2).
    // (Follow the existing mocking pattern in this test class for the RestClient .get().uri()... chain.)
    List<CustomerReferenceService.CustomerRef> refs = service.searchIdsByName("ace", 10);
    assertThat(refs).extracting(CustomerReferenceService.CustomerRef::customerId)
        .containsExactly(P1, P2);
    assertThat(refs.get(0).customerName()).isNotBlank();
}

@Test
void searchIdsByName_failsSoftToEmptyOnError() {
    // Arrange the mocked client to throw.
    assertThat(service.searchIdsByName("ace", 10)).isEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl pos-workorder test -Dtest=CustomerReferenceServiceTest`
Expected: FAIL — `searchIdsByName` / `CustomerRef` not found.

- [ ] **Step 3: Implement** — add to `CustomerReferenceService`:

```java
/** Search pos-customer parties by name; returns matching customer ids + display names (fail-soft). */
public @NonNull List<CustomerRef> searchIdsByName(@Nullable String name, int limit) {
    if (name == null || name.isBlank()) {
        return List.of();
    }
    try {
        Map<String, Object> body = customerRestClient
                .get()
                .uri(uri -> uri.path("/v1/crm/accounts/parties")
                        .queryParam("name", name)
                        .queryParam("size", limit)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        Object results = body == null ? null : body.get("results");
        if (!(results instanceof List<?> rows)) {
            return List.of();
        }
        List<CustomerRef> refs = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                Object id = map.get("partyId");
                if (id != null) {
                    String display = firstNonBlank(
                            String.valueOf(map.getOrDefault("displayName", "")),
                            String.valueOf(map.getOrDefault("legalName", "")),
                            "customer-" + id);
                    refs.add(new CustomerRef(UUID.fromString(String.valueOf(id)), display));
                }
            }
        }
        return refs;
    } catch (Exception ex) {
        log.debug("Customer name search failed for '{}': {}", name, ex.getMessage());
        return List.of();
    }
}

private static String firstNonBlank(String... values) {
    for (String v : values) {
        if (v != null && !v.isBlank()) {
            return v;
        }
    }
    return "";
}

/** A customer reference: id + resolved display name. */
public record CustomerRef(@NonNull UUID customerId, @NonNull String customerName) {}
```

Add imports as needed (`org.springframework.core.ParameterizedTypeReference`, `java.util.ArrayList`, `java.util.List`).

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -pl pos-workorder test -Dtest=CustomerReferenceServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add pos-workorder/src/main/java/com/positivity/workorder/internal/service/CustomerReferenceService.java \
        pos-workorder/src/test/java/com/positivity/workorder/internal/service/CustomerReferenceServiceTest.java
git commit -m "feat(workorder): customer name->ids search via party browse (fail-soft)"
```

### Task 2: Estimate `q` search — DTO field + repository

**Files:**
- Modify: `pos-workorder/src/main/java/com/positivity/workorder/internal/dto/EstimateSummaryResponse.java`
- Modify: `pos-workorder/src/main/java/com/positivity/workorder/internal/repository/EstimateRepository.java`

- [ ] **Step 1: Add `customerName` to the DTO** (additive, after `customerId`):

```java
@Schema(description = "Resolved customer display name", example = "Acme", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
private String customerName;
```

- [ ] **Step 2: Add repository finders** to `EstimateRepository`:

```java
@Query("SELECT e FROM Estimate e WHERE LOWER(e.estimateNumber) LIKE LOWER(CONCAT('%', :q, '%')) "
        + "OR e.customerId IN :customerIds OR (:idQuery IS NOT NULL AND e.id = :idQuery)")
Page<Estimate> searchByQuery(
        @Param("q") String q,
        @Param("customerIds") Collection<UUID> customerIds,
        @Param("idQuery") UUID idQuery,
        Pageable pageable);
```

Add imports: `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`, `java.util.Collection`.

- [ ] **Step 3: Compile**

Run: `./mvnw -q -pl pos-workorder compile`
Expected: BUILD SUCCESS (only Lombok warnings).

- [ ] **Step 4: Commit**

```bash
git add pos-workorder/src/main/java/com/positivity/workorder/internal/dto/EstimateSummaryResponse.java \
        pos-workorder/src/main/java/com/positivity/workorder/internal/repository/EstimateRepository.java
git commit -m "feat(workorder): estimate customerName field + q search finder"
```

### Task 3: Estimate `q` search — service

**Files:**
- Modify: `pos-workorder/src/main/java/com/positivity/workorder/service/EstimateService.java`
- Modify: `pos-workorder/src/main/java/com/positivity/workorder/internal/service/EstimateServiceImpl.java`
- Test: `pos-workorder/src/test/java/com/positivity/workorder/internal/service/EstimateSearchByQueryTest.java`

- [ ] **Step 1: Write the failing test** (Mockito; mock `EstimateRepository` + `CustomerReferenceService`):

```java
@Test
void findEstimatesByQuery_matchesNumberAndName_enrichesCustomerName() {
    UUID custA = UUID.randomUUID();
    when(customerReferenceService.searchIdsByName(eq("ace"), anyInt()))
        .thenReturn(List.of(new CustomerReferenceService.CustomerRef(custA, "Acme")));
    Estimate est = estimateWith(custA, "EST-2024-1001");
    when(estimateRepository.searchByQuery(eq("ace"), anyCollection(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(est)));
    when(customerReferenceService.resolveAll(anyCollection()))
        .thenReturn(Map.of(custA, new CustomerReferenceService.CustomerContact("Acme", null)));

    Page<EstimateSummaryResponse> page = service.findEstimatesByQuery("ace", PageRequest.of(0, 10));

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).getCustomerName()).isEqualTo("Acme");
    assertThat(page.getContent().get(0).getEstimateNumber()).isEqualTo("EST-2024-1001");
}

@Test
void findEstimatesByQuery_uuidQueryUsesIdMatch() {
    UUID id = UUID.randomUUID();
    when(customerReferenceService.searchIdsByName(any(), anyInt())).thenReturn(List.of());
    when(estimateRepository.searchByQuery(eq(id.toString()), anyCollection(), eq(id), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    service.findEstimatesByQuery(id.toString(), PageRequest.of(0, 10));
    verify(estimateRepository).searchByQuery(eq(id.toString()), anyCollection(), eq(id), any(Pageable.class));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl pos-workorder test -Dtest=EstimateSearchByQueryTest`
Expected: FAIL — `findEstimatesByQuery` not defined.

- [ ] **Step 3: Add to the interface** (`EstimateService`):

```java
/** Search estimates by a free-text query matching estimate number, customer name, or estimate id. */
@NonNull
Page<EstimateSummaryResponse> findEstimatesByQuery(@NonNull String q, @NonNull Pageable pageable);
```

- [ ] **Step 4: Implement** in `EstimateServiceImpl` (reuse its existing estimate→summary mapper; set `customerName` from the enrichment map):

```java
@Override
@Transactional(readOnly = true)
public @NonNull Page<EstimateSummaryResponse> findEstimatesByQuery(@NonNull String q, @NonNull Pageable pageable) {
    List<UUID> nameMatchIds = customerReferenceService.searchIdsByName(q, 10).stream()
            .map(CustomerReferenceService.CustomerRef::customerId)
            .toList();
    UUID idQuery = tryParseUuid(q);
    Page<Estimate> page = estimateRepository.searchByQuery(
            q, nameMatchIds.isEmpty() ? List.of(ZERO_UUID) : nameMatchIds, idQuery, pageable);

    Map<UUID, CustomerReferenceService.CustomerContact> names = customerReferenceService.resolveAll(
            page.getContent().stream().map(Estimate::getCustomerId).filter(Objects::nonNull).toList());

    return page.map(est -> {
        EstimateSummaryResponse dto = toEstimateSummaryResponse(est); // existing mapper
        CustomerReferenceService.CustomerContact c = names.get(est.getCustomerId());
        dto.setCustomerName(c != null ? c.name() : null);
        return dto;
    });
}

private static UUID tryParseUuid(String s) {
    try { return UUID.fromString(s); } catch (Exception e) { return null; }
}

private static final UUID ZERO_UUID = new UUID(0L, 0L); // IN-clause needs a non-empty collection
```

> If `toEstimateSummaryResponse` is private/named differently, reuse the existing mapper used by
> `searchEstimates`; do not duplicate mapping logic (DRY).

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q -pl pos-workorder test -Dtest=EstimateSearchByQueryTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add pos-workorder/src/main/java/com/positivity/workorder/service/EstimateService.java \
        pos-workorder/src/main/java/com/positivity/workorder/internal/service/EstimateServiceImpl.java \
        pos-workorder/src/test/java/com/positivity/workorder/internal/service/EstimateSearchByQueryTest.java
git commit -m "feat(workorder): estimate q search service with customer-name enrichment"
```

### Task 4: Estimate search controller — `q` param

**Files:**
- Modify: `pos-workorder/src/main/java/com/positivity/workorder/internal/controller/EstimateSearchController.java`
- Test: `pos-workorder/src/test/java/com/positivity/workorder/contract/EstimateSearchContractBehaviorIT.java` (new or extend existing estimate-search IT)

- [ ] **Step 1: Write the failing contract test** — seed an estimate (customerId C, estimateNumber `EST-IT-1`) and assert `GET /v1/workexec/estimates/search?q=EST-IT-1` returns it with a `customerName`. Use the module's contract-IT base (gateway `X-User`/`X-Authorities` with `workorder:estimate:view`), mirroring an existing estimate IT. Mock/seed the customer name source as the module's ITs do (or assert `customerName` is present, possibly the `customer-<id>` fallback when pos-customer is not wired in tests).

- [ ] **Step 2: Run it — expect FAIL** (`q` ignored / 400).

Run: `./mvnw -q -pl pos-workorder test -Dtest=EstimateSearchContractBehaviorIT`

- [ ] **Step 3: Add `q` to the controller**:

```java
@GetMapping("/search")
@PreAuthorize("hasAuthority('workorder:estimate:view')")
@EmitEvent(id = "WORKORDER_ESTIMATE_SEARCH", apiVersion = "1")
public Page<EstimateSummaryResponse> searchEstimates(
        @Parameter(description = "Free-text query: estimate number, customer name, or estimate id")
                @RequestParam(required = false) @Nullable String q,
        @Parameter(description = "Filter by customer UUID (optional)") @RequestParam(required = false) @Nullable UUID customerId,
        @Parameter(description = "Filter by vehicle UUID (optional)") @RequestParam(required = false) @Nullable UUID vehicleId,
        @Parameter(schema = @Schema(implementation = Pageable.class)) @PageableDefault(size = 25) Pageable pageable) {
    if (q != null && !q.isBlank()) {
        return estimateService.findEstimatesByQuery(q.trim(), pageable);
    }
    return estimateService.searchEstimates(customerId, vehicleId, pageable);
}
```

- [ ] **Step 4: Run it — expect PASS.**

- [ ] **Step 5: Commit**

```bash
git add pos-workorder/src/main/java/com/positivity/workorder/internal/controller/EstimateSearchController.java \
        pos-workorder/src/test/java/com/positivity/workorder/contract/EstimateSearchContractBehaviorIT.java
git commit -m "feat(workorder): GET /v1/workexec/estimates/search?q (number/name/id)"
```

### Task 5: Workorder search — DTO, repo, service, controller

**Files:**
- Create: `pos-workorder/src/main/java/com/positivity/workorder/internal/dto/WorkorderSearchResult.java`
- Modify: `pos-workorder/src/main/java/com/positivity/workorder/internal/repository/WorkorderRepository.java`
- Create: `pos-workorder/src/main/java/com/positivity/workorder/internal/service/WorkorderSearchService.java` + `WorkorderSearchServiceImpl.java`
- Create: `pos-workorder/src/main/java/com/positivity/workorder/internal/controller/WorkorderSearchController.java`
- Test: `pos-workorder/src/test/java/com/positivity/workorder/internal/service/WorkorderSearchServiceTest.java`, `.../contract/WorkorderSearchContractBehaviorIT.java`

- [ ] **Step 1: Create the DTO**:

```java
package com.positivity.workorder.internal.dto;

import com.positivity.workorder.internal.enums.WorkorderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Lightweight workorder row for finder/search results")
public class WorkorderSearchResult {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID workorderId;
    private WorkorderStatus status;
    @Schema(description = "Resolved customer display name")
    private String customerName;
    private Instant createdAt;
}
```

- [ ] **Step 2: Repo finder** — add to `WorkorderRepository`:

```java
@org.springframework.data.jpa.repository.Query(
        "SELECT w FROM Workorder w WHERE w.customerId IN :customerIds "
        + "OR (:idQuery IS NOT NULL AND w.id = :idQuery)")
Page<Workorder> searchByQuery(
        @org.springframework.data.repository.query.Param("customerIds") java.util.Collection<UUID> customerIds,
        @org.springframework.data.repository.query.Param("idQuery") UUID idQuery,
        Pageable pageable);
```

- [ ] **Step 3: Write the failing service test** — mirror Task 3's structure: name → ids → `searchByQuery` → enrich `customerName`; assert a `WorkorderSearchResult` with the right id, status, customerName. UUID query path verified like Task 3.

- [ ] **Step 4: Run — expect FAIL.**

Run: `./mvnw -q -pl pos-workorder test -Dtest=WorkorderSearchServiceTest`

- [ ] **Step 5: Implement service** (`WorkorderSearchServiceImpl`, `@Transactional(readOnly=true)`):

```java
@Override
public @NonNull Page<WorkorderSearchResult> search(@NonNull String q, @NonNull Pageable pageable) {
    List<UUID> ids = customerReferenceService.searchIdsByName(q, 10).stream()
            .map(CustomerReferenceService.CustomerRef::customerId).toList();
    UUID idQuery = tryParseUuid(q);
    Page<Workorder> page = workorderRepository.searchByQuery(
            ids.isEmpty() ? List.of(ZERO_UUID) : ids, idQuery, pageable);
    Map<UUID, CustomerReferenceService.CustomerContact> names = customerReferenceService.resolveAll(
            page.getContent().stream().map(Workorder::getCustomerId).filter(Objects::nonNull).toList());
    return page.map(w -> WorkorderSearchResult.builder()
            .workorderId(w.getId())
            .status(w.getStatus())
            .customerName(names.getOrDefault(w.getCustomerId(),
                    CustomerReferenceService.CustomerContact.empty()).name())
            .createdAt(w.getCreatedAt())
            .build());
}
// tryParseUuid + ZERO_UUID as in Task 3.
```

> Confirm `Workorder` getters (`getCreatedAt`) exist; if the audit timestamp field differs, use the actual getter.

- [ ] **Step 6: Create the controller**:

```java
@RestController
@RequestMapping("/v1/workorders")
@RequiredArgsConstructor
@Tag(name = "Workorder Search", description = "Workorder finder search")
public class WorkorderSearchController {
    private final WorkorderSearchService workorderSearchService;

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('workorder:workorder:view')")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth", scopes = {"workorder:workorder:view"})
    @EmitEvent(id = "WORKORDER_SEARCH", apiVersion = "1")
    @Operation(summary = "Search workorders", description = "Search by customer name or workorder id.")
    @ApiResponse(responseCode = "200", description = "Page of workorder search results")
    public Page<WorkorderSearchResult> search(
            @Parameter(description = "Free-text query: customer name or workorder id")
                    @RequestParam(required = false) @Nullable String q,
            @Parameter(schema = @Schema(implementation = Pageable.class)) @PageableDefault(size = 25) Pageable pageable) {
        return workorderSearchService.search(q == null ? "" : q.trim(), pageable);
    }
}
```

> Register the `WORKORDER_SEARCH` event id in the pos-workorder `EventTypes`/event registration if the module
> requires every `@EmitEvent` id to be registered (mirror the existing reporting/event entries — see how
> sibling controllers register theirs). Add an event-registration entry in this task.

- [ ] **Step 7: Write the failing contract test** — seed a workorder for customer C, assert `GET /v1/workorders/search?q=<uuid>` returns it (200) with `workorderId`; 403 without `workorder:workorder:view`.

- [ ] **Step 8: Run service + contract tests — expect PASS.**

Run: `./mvnw -q -pl pos-workorder test -Dtest=WorkorderSearchServiceTest,WorkorderSearchContractBehaviorIT`

- [ ] **Step 9: Commit**

```bash
git add pos-workorder/src/main/java/com/positivity/workorder/internal/dto/WorkorderSearchResult.java \
        pos-workorder/src/main/java/com/positivity/workorder/internal/repository/WorkorderRepository.java \
        pos-workorder/src/main/java/com/positivity/workorder/internal/service/WorkorderSearchService*.java \
        pos-workorder/src/main/java/com/positivity/workorder/internal/controller/WorkorderSearchController.java \
        pos-workorder/src/main/java/com/positivity/workorder/internal/config/*EventTypes*.java \
        pos-workorder/src/test/java/com/positivity/workorder/internal/service/WorkorderSearchServiceTest.java \
        pos-workorder/src/test/java/com/positivity/workorder/contract/WorkorderSearchContractBehaviorIT.java
git commit -m "feat(workorder): GET /v1/workorders/search?q (customer name or id)"
```

### Task 6: OpenAPI + full module verify, PR

- [ ] **Step 1: Regenerate OpenAPI**

Run: `scripts/generate-openapi.sh pos-workorder`
Then **revert the aggregate clobber**: `git checkout HEAD -- pos-api-gateway/docs/openapi-aggregate.yaml`
Verify `pos-workorder/openapi.yaml` contains `/v1/workorders/search`, the `q` param on `/v1/workexec/estimates/search`, `WorkorderSearchResult`, and `customerName` on the estimate summary.

- [ ] **Step 2: Spotless + full module tests**

Run: `./mvnw -q -pl pos-workorder spotless:apply` (then revert any unrelated reformatted files — keep only files this plan touched).
Run: `./mvnw -pl pos-workorder test`
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 3: Commit + push + PR**

```bash
git add pos-workorder/openapi.yaml
git commit -m "chore(workorder): regenerate OpenAPI for search endpoints"
git push -u origin feat/workexec-search
gh pr create --base main --title "feat(workorder): estimate/workorder finder search (q by customer name or id)" --body "<summary + spec link>"
```
Merge after CI (squash). Then update the local main checkout for the SDK step.

---

## PHASE 2 — SDK (PR #2)

Repo: `durion-positivity-sdk-angular` (branch `main`). The generator reads `../durion-positivity-backend/pos-workorder/openapi.yaml`; ensure that sibling checkout is on merged `main` (or temporarily overlay the merged spec, then restore).

### Task 7: Regenerate workorder SDK

- [ ] **Step 1:** `git checkout -b feat/workexec-search-sdk main`
- [ ] **Step 2:** `./scripts/generate-openapi.sh --module workorder`
- [ ] **Step 3:** `npm run build --workspace packages/sdk-workorder` → expect success.
- [ ] **Step 4:** Verify new symbols exist: `grep -rl "WorkorderSearchResult\|search.*Workorder\|customerName" packages/sdk-workorder/src` and a `searchEstimates`/`searchWorkorders` operation with a `q` param.
- [ ] **Step 5:** Commit only `packages/sdk-workorder` (not node_modules symlink); push; PR to `main`; merge.

```bash
git add packages/sdk-workorder
git commit -m "feat(sdk-workorder): regenerate for estimate/workorder finder search"
git push -u origin feat/workexec-search-sdk
gh pr create --base main --title "feat(sdk-workorder): finder search client" --body "Regenerated for durion-positivity-backend finder search."
```

---

## PHASE 3 — Frontend (PR #3)

Repo: `durion-positivity-frontend`, branch off `master`. First `git checkout -b feat/workexec-finders master && npm run sdk:install` (commit only the refreshed `durion-sdk-workorder` tarball + manifest; revert unrelated tarball churn).

### Task 8: Search view model + facade methods

**Files:**
- Modify: `src/app/features/workexec/models/workexec.models.ts`
- Modify: `src/app/features/workexec/services/workexec.service.ts`
- Test: `src/app/features/workexec/services/workexec.service.spec.ts`

- [ ] **Step 1: Add the view model** to `workexec.models.ts`:

```typescript
export interface SearchResultItem {
  id: string;
  primary: string;   // customer name
  secondary: string; // estimate number / short workorder id + status
}
```

- [ ] **Step 2: Write the failing facade test** (Vitest; stub the SDK workorder search service like the existing spec stubs others):

```typescript
it('searchEstimates maps SDK rows to SearchResultItem', () => {
  workorderSearchStub.searchEstimates.mockReturnValueOnce(of({ content: [
    { id: 'e1', estimateNumber: 'EST-1', customerName: 'Acme', status: 'DRAFT' } ] }));
  let out: SearchResultItem[] | undefined;
  service.searchEstimates('ace').subscribe(r => (out = r));
  expect(out).toEqual([{ id: 'e1', primary: 'Acme', secondary: 'EST-1 · DRAFT' }]);
});
```

- [ ] **Step 3: Run — expect FAIL.** `npx ng test --no-watch --filter WorkexecService`

- [ ] **Step 4: Implement** the facade methods (inject the generated search service; map `content ?? []`):

```typescript
searchEstimates(q: string): Observable<SearchResultItem[]> {
  return this.estimateSearchApi.searchEstimates(q).pipe(
    map(page => (page.content ?? []).map(e => ({
      id: e.id!, primary: e.customerName ?? '', secondary: [e.estimateNumber, e.status].filter(Boolean).join(' · '),
    }))));
}

searchWorkorders(q: string): Observable<SearchResultItem[]> {
  return this.workorderSearchApi.search(q).pipe(
    map(page => (page.content ?? []).map(w => ({
      id: w.workorderId!, primary: w.customerName ?? '', secondary: [w.workorderId?.slice(0, 8), w.status].filter(Boolean).join(' · '),
    }))));
}
```

> Use the actual generated service/method names from Phase 2 (e.g. `searchEstimates` on the estimate-search
> service, `search` on the workorder-search service). Provide those services in the spec's TestBed providers.

- [ ] **Step 5: Run — expect PASS. Commit.**

```bash
git add src/app/features/workexec/models/workexec.models.ts \
        src/app/features/workexec/services/workexec.service.ts \
        src/app/features/workexec/services/workexec.service.spec.ts
git commit -m "feat(workexec): facade searchEstimates/searchWorkorders -> SearchResultItem"
```

### Task 9: Reusable typeahead component

**Files:**
- Create: `src/app/features/workexec/components/search-typeahead/workexec-search-typeahead.component.{ts,html,css}`
- Test: `.../workexec-search-typeahead.component.spec.ts`

- [ ] **Step 1: Write the failing component spec** — covers: typing ≥2 chars (after debounce) calls the `search` fn and renders options; ArrowDown+Enter emits the option id; empty result shows the empty state; `<2` chars makes no call.

```typescript
it('debounced query renders options and selecting emits id', fakeAsync(() => {
  const search = vi.fn().mockReturnValue(of([{ id: 'e1', primary: 'Acme', secondary: 'EST-1' }]));
  component.search = search;
  let emitted: string | undefined;
  component.selected.subscribe(v => (emitted = v));
  component.onInput('ace');
  tick(300);
  fixture.detectChanges();
  const option = fixture.nativeElement.querySelector('[role="option"]');
  expect(option.textContent).toContain('Acme');
  option.click();
  expect(emitted).toBe('e1');
}));
```

- [ ] **Step 2: Run — expect FAIL.** `npx ng test --no-watch --filter WorkexecSearchTypeahead`

- [ ] **Step 3: Implement the component** — standalone, OnPush, `CommonModule`+`TranslatePipe`. Inputs `label`, `placeholder`, `search: (q: string) => Observable<SearchResultItem[]>`, `minChars=2`, `debounceMs=250`. A `Subject<string>` piped through `debounceTime + distinctUntilChanged + switchMap(search)` into a `results` signal; `state` signal `idle|loading|loaded|empty|error`; `activeIndex` signal for keyboard nav. `onInput(value)`, `onKeydown(event)` (ArrowUp/Down/Enter/Escape), `choose(item)` emits `selected`. Use `takeUntilDestroyed`.

- [ ] **Step 4: Implement the template** — `role="combobox"` input bound to `aria-expanded`/`aria-activedescendant`; a `role="listbox"` `<ul>` of `role="option"` `<li>` items (`id="opt-{{i}}"`, `aria-selected`), each showing `primary` + `secondary`; loading/empty rows via `@switch (state())`. All text via `translate`.

- [ ] **Step 5: Run — expect PASS. Commit.**

```bash
git add src/app/features/workexec/components/search-typeahead/
git commit -m "feat(workexec): accessible reusable search typeahead component"
```

### Task 10: Landing finders + i18n + route nav

**Files:**
- Modify: `src/app/features/workexec/pages/landing/workexec-landing-page.component.{ts,html}`
- Modify: `src/assets/i18n/{en-US,es-US,fr-CA,qps-ploc}.json`
- Test: `.../workexec-landing-page.component.spec.ts`

- [ ] **Step 1: Add i18n keys** (en-US authoritative; mirror to the other 3 with English placeholders), under `WORKEXEC.FINDERS`: `TITLE`, `ESTIMATE_LABEL`, `ESTIMATE_PLACEHOLDER`, `WORKORDER_LABEL`, `WORKORDER_PLACEHOLDER`, `LOADING`, `EMPTY`, `ERROR`. Use a script like the CAP-316 i18n injection.

- [ ] **Step 2: Write the failing landing test** — selecting an estimate result navigates to `['/app','workexec','estimates', id, 'summary']`; a workorder result to `['/app','workexec','workorders', id]`. Stub `WorkexecService.searchEstimates/searchWorkorders` + spy `Router.navigate`.

- [ ] **Step 3: Wire the landing** — import `WorkexecSearchTypeaheadComponent`; add `onEstimateSelected(id)` → `router.navigate(['/app','workexec','estimates', id, 'summary'])` and `onWorkorderSelected(id)` → `router.navigate(['/app','workexec','workorders', id])`; pass `search` fns bound to the facade. Add a Finders section at the top of the template with the two `<app-workexec-search-typeahead>`s.

- [ ] **Step 4: Run landing + component + facade specs — expect PASS.**

Run: `npx ng test --no-watch --filter "Workexec"`

- [ ] **Step 5: Build**

Run: `npx ng build --configuration alpha` → expect "Application bundle generation complete".

- [ ] **Step 6: a11y smoke** `npm run a11y:smoke` (covers `/app/workexec`).

- [ ] **Step 7: Commit + push + PR**

```bash
git add src/app/features/workexec src/assets/i18n .sdk-tarballs/durion-sdk-workorder-0.1.0-alpha.tgz .sdk-tarballs/manifest.json
git commit -m "feat(workexec): typeahead finders for estimate/workorder on landing"
git push -u origin feat/workexec-finders
gh pr create --base master --title "feat(workexec): estimate/workorder typeahead finders" --body "<summary + spec link>"
```
Merge after CI + a11y gate (squash).

---

## Verification (end to end)
- Backend: `./mvnw -pl pos-workorder test` green; OpenAPI contains the two search operations + `customerName`.
- SDK: `npm run build --workspace packages/sdk-workorder` green; symbols present.
- Frontend: `npx ng test --no-watch --filter Workexec` green; `ng build --configuration alpha` clean; a11y gate pass.
- Manual (alpha, post-merge): `/app/workexec` → type a customer name in Find Estimate → matching estimates appear (customer + number) → select → lands on the estimate summary; same for Find Workorder → workorder detail. pos-customer-down → name search empty, id/number still works.

## Self-review notes (gaps to confirm during execution)
- `toEstimateSummaryResponse` mapper name — reuse the existing one in `EstimateServiceImpl`; do not duplicate.
- `Workorder.getCreatedAt()` getter name — confirm against the entity.
- `WORKORDER_SEARCH` / reuse of `WORKORDER_ESTIMATE_SEARCH` event ids — register new ids if the module enforces registration.
- Generated SDK service/method names (Phase 2) feed Task 8's injected services — use the actual generated names.
