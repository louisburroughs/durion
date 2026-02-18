# CAP-168 Backend Implementation (pos-catalog)

## Scope

Implemented backend story for CAP-168 (`durion-positivity-backend` issue #52): location store pricing overrides with guardrails, approval/rejection flow, effective-price resolution, and provider behavioral contract tests.

## Implementation Checklist

- [x] Validate CAP-168 contract requirements from manifest, backend issue #52, and contract guide.
- [x] Ensure backend work is on capability branch `cap/CAP168`.
- [x] Confirm endpoint/service implementation alignment to OpenAPI source-of-truth (`pos-catalog/openapi.json`) for CAP-168 paths.
- [x] Add provider behavioral contract tests in `ContractBehaviorIT` for happy path, validation, auth failures, idempotency, and optimistic-lock conflict.
- [x] Use contract-guide examples in tests (`$95`, `$88`, hard-guardrail reject case).
- [x] Run focused test verification for changed behavior.
- [x] Run Sonar analysis on changed Java file and check security issues.
- [x] Commit and push branch.

## Workspace-relative Files Changed

- `durion-positivity-backend/pos-catalog/src/test/java/com/positivity/catalog/contract/ContractBehaviorIT.java`
- `durion/docs/capabilities/CAP-168/CAP-168-backend-implementation.md`

## Existing CAP-168 Backend Components Verified

The following existing components already satisfy the core endpoint/service contract:

- Controller: `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/controller/ProductController.java`
- Service: `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/service/LocationPriceOverrideService.java`
- Repository: `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/repository/LocationPriceOverrideRepository.java`
- Repository: `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/repository/GuardrailPolicyRepository.java`
- Exception mapping: `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/controller/CatalogExceptionHandler.java`

## Critical Snippets

### Controller Signature (existing)

```java
@PostMapping("/pricing/location-overrides")
public ResponseEntity<LocationPriceOverrideResponseDto> createLocationPriceOverride(
        @RequestBody LocationPriceOverrideCreateRequestDto request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(locationPriceOverrideService.createOverride(request));
}
```

### Service Method (existing)

```java
@Transactional
public LocationPriceOverrideResponseDto createOverride(@NonNull LocationPriceOverrideCreateRequestDto request) {
    validateCreateRequest(request);
    validateProductExists(request.getProductId());
    GuardrailPolicyEntity policy = guardrailPolicyRepository
            .findTopByScopeAndScopeIdOrderByCreatedAtDesc(GuardrailPolicyScope.LOCATION, request.getLocationId())
            .orElseThrow(() -> new CatalogValidationException("GUARDRAIL_POLICY_NOT_FOUND: ..."));
    // hard-guardrail checks + ACTIVE/PENDING_APPROVAL decision + approval request creation
}
```

### Repository Query (existing)

```java
Optional<LocationPriceOverrideEntity> findTopByLocationIdAndProductIdAndStatusOrderByCreatedAtDesc(
        UUID locationId,
        UUID productId,
        PriceOverrideStatus status);
```

### Provider Contract Test (added)

```java
@Test
@DisplayName("CP-011: Create pending location override above auto-approval threshold")
void testCreateLocationOverride_PendingApproval() throws Exception {
    // base=100, cost=50, override=88 -> PENDING_APPROVAL
    mockMvc.perform(withAuth(post("/v1/products/pricing/location-overrides"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(locationOverridePayload(locationId, productId, actorUserId, 100.00, 50.00, 88.00)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
}
```

## CAP-168 Behavioral Coverage Added

Added to `ContractBehaviorIT`:

- Happy path:
  - `CP-010` active override within threshold (`$95`)
  - `CP-011` pending approval above threshold (`$88`)
  - `CP-012` approve pending override
  - `CP-013` reject pending override with rejection metadata
- Validation/errors:
  - `VE-010` min-margin hard guardrail violation
  - `VE-011` max-discount hard guardrail violation
  - `VE-012` invalid product returns `404`
  - `VE-013` forbidden for unauthorized role
  - `VE-014` reject request missing notes returns `400`
- Idempotency/read invariants:
  - `ID-004` repeated effective-price lookup is stable
- Concurrency invariant:
  - `CC-004` stale-version approve returns `409`

## OpenAPI Contract Handling

- `pos-catalog/openapi.json` treated as read-only authoritative source.
- No regeneration and no manual edits performed.
- Implementation aligned against existing CAP-168 OpenAPI paths and response semantics.

## Configuration Changes

- No new configuration properties, feature flags, or event-type registration changes required.
- Existing CAP-168 events were already present (`CATALOG_GUARDRAIL_POLICY_UPSERT`, `CATALOG_PRICE_OVERRIDE_CREATE`, `CATALOG_PRICE_OVERRIDE_APPROVED`, `CATALOG_PRICE_OVERRIDE_REJECTED`).

## Verification

- Focused test (changed file):
  - `ContractBehaviorIT`: **36 passed, 0 failed**
- Sonar/Security:
  - Sonar analysis invoked for changed test file.
  - `sonarqube_list_potential_security_issues`: **0 issues**.
- Broader module test run (`pos-catalog` selected tests) currently shows pre-existing failures in `ProductLifecycleContractBehaviorIT` unrelated to CAP-168 changes.

## Completion Details

- Branch: `cap/CAP168`
- Commit hash: `2cba483100b532ddb105dd936a2d2e297152bf4c`
- Files changed (`main...cap/CAP168`):
  - `pos-catalog/src/test/java/com/positivity/catalog/contract/ContractBehaviorIT.java`
- Test results summary:
  - `ContractBehaviorIT`: 36 pass / 0 fail
  - `ProductLifecycleContractBehaviorIT`: failing in current baseline run (unrelated)
  - Module-selected run (`ContractBehaviorIT`, `ProductLifecycleContractBehaviorIT`, `ArchitectureTest`): 36 pass / 6 fail (all failures in `ProductLifecycleContractBehaviorIT`, unrelated to CAP-168)
