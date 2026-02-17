# CAP-166 Backend Implementation (Issue #195)

## Summary

Implemented supplier/vendor item cost tier management in `pos-catalog` with CRUD endpoints, tier validation, optimistic-lock conflict handling, event registration, and provider contract behavioral tests.

## Implementation Checklist

- Added REST endpoints in controller for supplier-item cost tier create/read/update/delete.
- Added service orchestration with business-rule validation and optimistic-lock-safe persistence.
- Added JPA entities and repository for supplier-item cost structures and nested tiers.
- Added DTOs for create/update/response payload contracts.
- Registered `@EmitEvent` event types for CUD operations.
- Added/extended contract behavioral tests for happy path, validation errors, auth failure, idempotency, and concurrency invariants.
- Updated product backend contract guide with CAP-166 endpoint examples and validation rules.

## Files Changed

- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/controller/CatalogController.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/controller/CatalogExceptionHandler.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/config/CatalogEventTypes.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/service/SupplierItemCostService.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/repository/SupplierItemCostRepository.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/entity/SupplierItemCostEntity.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/entity/CostTierEntity.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/dto/CostTierDto.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/dto/SupplierItemCostCreateRequestDto.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/dto/SupplierItemCostUpdateRequestDto.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/dto/SupplierItemCostResponseDto.java`
- `durion-positivity-backend/pos-catalog/src/test/java/com/positivity/catalog/contract/ContractBehaviorIT.java`
- `durion/domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- `durion/docs/capabilities/CAP-166/CAP-166-backend-implementation.md`

## Critical Code Snippets

### Controller Signature

```java
@PostMapping("/costs/supplier-item")
@EmitEvent(id = "CATALOG_SUPPLIER_ITEM_COST_CREATE", apiVersion = "1")
public ResponseEntity<SupplierItemCostResponseDto> createSupplierItemCost(
        @RequestBody SupplierItemCostCreateRequestDto request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(supplierItemCostService.createSupplierItemCost(request));
}
```

### Service Method

```java
@Transactional
public SupplierItemCostResponseDto updateSupplierItemCost(
        @NonNull UUID supplierId,
        @NonNull UUID itemId,
        @NonNull SupplierItemCostUpdateRequestDto request) {
    validateUpdateRequest(request);
    SupplierItemCostEntity existing = supplierItemCostRepository.findBySupplierIdAndItemId(supplierId, itemId)
            .orElseThrow(() -> new CatalogNotFoundException(
                    "Supplier item cost not found for supplierId=" + supplierId + " and itemId=" + itemId));

    existing.setCurrencyCode(normalizeCurrencyCode(request.getCurrencyCode()));
    existing.setBaseCost(request.getBaseCost());
    existing.setCostTiers(toTierEntities(request.getTiers()));

    return toResponse(supplierItemCostRepository.save(existing));
}
```

### Repository Query

```java
Optional<SupplierItemCostEntity> findBySupplierIdAndItemId(UUID supplierId, UUID itemId);
```

### ContractBehaviorIT Example

```java
@Test
@DisplayName("VE-005: Reject overlapping supplier-item cost tiers")
void testCreateSupplierItemCost_OverlappingTiers() throws Exception {
    UUID supplierId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    mockMvc.perform(withAuth(post("/v1/products/costs/supplier-item"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(overlappingTierPayload(supplierId, itemId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$").value(org.hamcrest.Matchers.containsString("INVALID_TIER_STRUCTURE")));
}
```

## Configuration Changes

- Added three event type registrations in `CatalogEventTypes`:
  - `CATALOG_SUPPLIER_ITEM_COST_CREATE`
  - `CATALOG_SUPPLIER_ITEM_COST_UPDATE`
  - `CATALOG_SUPPLIER_ITEM_COST_DELETE`
- Added `409 CONFLICT` handling in `CatalogExceptionHandler` for optimistic locking exceptions.
- No secrets/config credentials added.

## OpenAPI Source-of-Truth Handling

- `OPENAPI_PATH` (`pos-catalog/target/openapi.json`) was treated as read-only.
- File was not edited manually and not regenerated.
- Implementation was added in source code with OpenAPI annotations in controller methods.

## Validation and Behavioral Coverage

- Contiguous and non-overlapping tier enforcement.
- Final tier open-ended (`maxQuantity = null`) enforcement.
- Positive `unit_cost`, non-negative `base_cost`, and 3-letter currency validation.
- Role-based auth failure test for missing edit authority.
- Idempotent read behavior test and sequential update invariants.

## Sonar/Lint Notes

- SonarQube analysis was triggered for changed core files using `sonarqube_analyze_file`.
- No Blocker/High/Security issues were surfaced inline by tool output in this session.

## Test Execution

- `./mvnw -pl pos-catalog test` ✅ PASS
- `./mvnw -pl pos-catalog -Dtest=ContractBehaviorIT test` ✅ PASS
- `./mvnw -pl pos-catalog -Dtest=ContractBehaviorIT,ProductLifecycleContractBehaviorIT test` ❌ FAIL (pre-existing failures in `ProductLifecycleContractBehaviorIT`, unrelated to CAP-166 cost-tier additions)

## Completion Details

- Branch: `cap/CAP166`
- Commit: `a6d19d5ea52a329d8bb00503d8c955f496ed0fe7`
- Push: successful (`origin/cap/CAP166`)
- Remote verification (`git ls-remote --heads origin cap/CAP166`):

```text
a6d19d5ea52a329d8bb00503d8c955f496ed0fe7        refs/heads/cap/CAP166
```

- Files changed vs main (`git diff --name-only main...cap/CAP166`):

```text
pos-catalog/src/main/java/com/positivity/catalog/internal/config/CatalogEventTypes.java
pos-catalog/src/main/java/com/positivity/catalog/internal/controller/CatalogController.java
pos-catalog/src/main/java/com/positivity/catalog/internal/controller/CatalogExceptionHandler.java
pos-catalog/src/main/java/com/positivity/catalog/internal/dto/CostTierDto.java
pos-catalog/src/main/java/com/positivity/catalog/internal/dto/SupplierItemCostCreateRequestDto.java
pos-catalog/src/main/java/com/positivity/catalog/internal/dto/SupplierItemCostResponseDto.java
pos-catalog/src/main/java/com/positivity/catalog/internal/dto/SupplierItemCostUpdateRequestDto.java
pos-catalog/src/main/java/com/positivity/catalog/internal/entity/CostTierEntity.java
pos-catalog/src/main/java/com/positivity/catalog/internal/entity/SupplierItemCostEntity.java
pos-catalog/src/main/java/com/positivity/catalog/internal/repository/SupplierItemCostRepository.java
pos-catalog/src/main/java/com/positivity/catalog/service/SupplierItemCostService.java
pos-catalog/src/test/java/com/positivity/catalog/contract/ContractBehaviorIT.java
```

- Test pass/fail count (`./mvnw -pl pos-catalog test`): `7 passed, 0 failed`
- Test pass/fail count (`./mvnw -pl pos-catalog -Dtest=ContractBehaviorIT test`): `24 passed, 0 failed`
- Test pass/fail count (`./mvnw -pl pos-catalog -Dtest=ContractBehaviorIT,ProductLifecycleContractBehaviorIT test`): `24 passed, 5 failed` (known pre-existing failures in lifecycle suite)
