# CAP-167 Backend Implementation (Story Fulfillment)

## Scope
Implemented backend support for location-specific store price overrides with guardrails in `pos-catalog` for backend child issue #52 under CAP-167.

## Implementation Checklist
- Added pricing override domain model: `LocationPriceOverride`, `GuardrailPolicy`, and `ApprovalRequest` entities + enums.
- Added repositories for guardrail policy lookup, override retrieval, and approval request retrieval.
- Added DTO contracts for guardrail upsert, override create/decision, and effective price response.
- Implemented `LocationPriceOverrideService` for guardrail validation, auto-approval, pending approval routing, rejection lifecycle, and optimistic locking.
- Added REST endpoints in `CatalogController` with OpenAPI annotations and `@EmitEvent` mappings.
- Updated event type registry in `CatalogEventTypes` for CAP-167 events.
- Extended provider behavioral tests in `ContractBehaviorIT` for happy path, validation, auth failure, idempotency, and concurrency.
- Added optimistic-lock exception unwrapping in `CatalogExceptionHandler` so conflict responses return HTTP 409.

## Files Changed
### Modified
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/config/CatalogEventTypes.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/controller/CatalogController.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/controller/CatalogExceptionHandler.java`
- `durion-positivity-backend/pos-catalog/src/test/java/com/positivity/catalog/contract/ContractBehaviorIT.java`

### Added
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/service/LocationPriceOverrideService.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/dto/GuardrailPolicyUpsertRequestDto.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/dto/LocationPriceOverrideCreateRequestDto.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/dto/LocationPriceOverrideDecisionRequestDto.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/dto/LocationPriceOverrideResponseDto.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/dto/EffectiveLocationPriceResponseDto.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/entity/LocationPriceOverrideEntity.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/entity/GuardrailPolicyEntity.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/entity/ApprovalRequestEntity.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/entity/PriceOverrideStatus.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/entity/GuardrailPolicyScope.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/entity/ApprovalRequestStatus.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/repository/LocationPriceOverrideRepository.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/repository/GuardrailPolicyRepository.java`
- `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/repository/ApprovalRequestRepository.java`

## Critical Code Snippets

### Controller signature example
```java
@PostMapping("/pricing/location-overrides")
@EmitEvent(id = "CATALOG_PRICE_OVERRIDE_CREATE", apiVersion = "1")
public ResponseEntity<LocationPriceOverrideResponseDto> createLocationPriceOverride(
        @RequestBody LocationPriceOverrideCreateRequestDto request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(locationPriceOverrideService.createOverride(request));
}
```

### Service orchestration example
```java
@Transactional
public LocationPriceOverrideResponseDto createOverride(@NonNull LocationPriceOverrideCreateRequestDto request) {
    validateCreateRequest(request);
    GuardrailPolicyEntity policy = guardrailPolicyRepository
            .findTopByScopeAndScopeIdOrderByCreatedAtDesc(GuardrailPolicyScope.LOCATION, request.getLocationId())
            .orElseThrow(() -> new CatalogValidationException("GUARDRAIL_POLICY_NOT_FOUND..."));

    BigDecimal discountPercent = calculateDiscountPercent(request.getBasePrice(), request.getOverridePrice());
    BigDecimal marginPercent = calculateMarginPercent(request.getOverridePrice(), request.getCost());

    if (discountPercent.compareTo(policy.getAutoApprovalThresholdPercent()) <= 0) {
        override.setStatus(PriceOverrideStatus.ACTIVE);
    } else {
        override.setStatus(PriceOverrideStatus.PENDING_APPROVAL);
    }

    LocationPriceOverrideEntity savedOverride = locationPriceOverrideRepository.save(override);
    ApprovalRequestEntity approvalRequest = savedOverride.getStatus() == PriceOverrideStatus.PENDING_APPROVAL
            ? createApprovalRequest(savedOverride)
            : null;

    return toResponse(savedOverride, approvalRequest);
}
```

### Repository query example
```java
Optional<LocationPriceOverrideEntity> findTopByLocationIdAndProductIdAndStatusOrderByCreatedAtDesc(
        UUID locationId,
        UUID productId,
        PriceOverrideStatus status);
```

### ContractBehaviorIT scenario example
```java
@Test
@DisplayName("CP-006: Auto-approval creates ACTIVE location override")
void testCreateLocationOverride_AutoApproved() throws Exception {
    UUID locationId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();

    upsertLocationGuardrailPolicy(locationId, new BigDecimal("15.0"), new BigDecimal("25.0"), new BigDecimal("10.0"));

    mockMvc.perform(withAuth(post("/v1/products/pricing/location-overrides"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(locationOverridePayload(locationId, productId, actorId, "100.00", "50.00", "95.00")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
}
```

## Config / Observability Changes
- Added event registrations in `CatalogEventTypes`:
  - `CATALOG_GUARDRAIL_POLICY_UPSERT`
  - `CATALOG_PRICE_OVERRIDE_CREATE`
  - `CATALOG_PRICE_OVERRIDE_APPROVED`
  - `CATALOG_PRICE_OVERRIDE_REJECTED`
- No secrets or credential handling changes.
- No feature flags added.

## Validation Results
- `./mvnw -pl pos-catalog -Dtest=ContractBehaviorIT test` → **PASS** (19 tests, 0 failures, 0 errors).
- `./mvnw -pl pos-catalog test` → **PASS**.
- Sonar security issue scan on changed core files → **0 potential security issues / 0 vulnerabilities**.

## Completion Details
- Branch: `cap/CAP167`
- Commit hash: `PENDING`
- Files changed vs main: `PENDING`
- Push verification (`git ls-remote --heads origin cap/CAP167`): `PENDING`

## Notes
- `OPENAPI_PATH` (`pos-catalog/openapi.json`) was treated as read-only and not manually edited.
- Contract behavior tests include examples derived from issue acceptance criteria ($100/$50 base-cost setup with $95 auto-approval and $88 pending approval cases).
