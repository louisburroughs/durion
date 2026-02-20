# Test Exemplars

This document collects representative, high-quality test exemplars from the durion-positivity-backend repository to help developers follow existing testing patterns.

## Table of Contents

- Unit Tests
- Service Tests
- Utility Tests
- Contract / Integration Tests
- Architecture Tests
- Test Fixtures / Base Test Patterns

---

## Unit Tests

- File: [pos-vehicle-inventory/src/test/java/com/positivity/vehicle/internal/util/VinUtilsTest.java](../../durion-positivity-backend/pos-vehicle-inventory/src/test/java/com/positivity/vehicle/internal/util/VinUtilsTest.java)
  - Why exemplar: Small, focused, pure-unit tests with clear assertions and good coverage of edge cases (normalization, validation, null/blank handling).
  - Snippet:

```java
@Test
void testNormalize() {
    assertEquals("1HGCM82633A004352", VinUtils.normalize("1hgcm82633a004352"));
    assertEquals("1HGCM82633A004352", VinUtils.normalize("1HG CM826 33A00 4352"));
}
```

- File: [pos-tax/src/test/java/com/positivity/tax/service/TaxCalculationServiceTest.java](../../durion-positivity-backend/pos-tax/src/test/java/com/positivity/tax/service/TaxCalculationServiceTest.java)
  - Why exemplar: Service-level unit tests using Mockito for external dependencies, strong assertion patterns with AssertJ, test-mode vs external-service branching, and validation/error tests.
  - Snippet:

```java
@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceTest {
    @Mock private ExternalTaxServiceClient externalClient;
    @BeforeEach void setUp() { /* configure TestModeTaxCalculator */ }
    @Test void shouldCalculateTaxInTestMode() {
        TaxCalculationResponse response = service.calculateTax(request);
        assertThat(response.getTotalTax()).isEqualByComparingTo("15.00");
        verifyNoInteractions(externalClient);
    }
}
```

---

## Service / Domain Tests

- File: [pos-workorder/src/test/java/com/positivity/workorder/contract/WorkexecJobTimeTotalsContractBehaviorIT.java](../../durion-positivity-backend/pos-workorder/src/test/java/com/positivity/workorder/contract/WorkexecJobTimeTotalsContractBehaviorIT.java)
  - Why exemplar: Full behavior-driven contract test that seeds domain entities, exercises REST endpoints, parses JSON responses and asserts domain-level aggregations. Shows seeding helpers and use of `objectMapper` for validation.
  - Snippet:

```java
String response = mockMvc.perform(get("/v1/workexec/job-time-totals")
        .param("startDate", "2026-02-14")
        .param("endDate", "2026-02-14"))
    .andExpect(status().isOk())
    .andReturn().getResponse().getContentAsString();
var json = objectMapper.readTree(response);
assertThat(json).hasSize(2);
```

---

## Contract / Integration Tests

- File: [pos-inventory/src/test/java/com/positivity/inventory/service/contract/InventoryAvailabilityContractBehaviorIT.java](../../durion-positivity-backend/pos-inventory/src/test/java/com/positivity/inventory/service/contract/InventoryAvailabilityContractBehaviorIT.java)
  - Why exemplar: Contract-style MockMvc tests that seed the ledger repository directly, call gateway-formatted endpoints (`/v1/...`), and assert response arrays and calculated fields (ATP). Good example of contract test seeds and auth headers.
  - Snippet:

```java
mockMvc.perform(withGatewayAuth(get("/v1/inventory/availability/{productId}", productId)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$", hasSize(1)))
    .andExpect(jsonPath("$[0].availableToPromiseQuantity").value(70));
```

- File: [pos-catalog/src/test/java/com/positivity/catalog/contract/ContractBehaviorIT.java](../../durion-positivity-backend/pos-catalog/src/test/java/com/positivity/catalog/contract/ContractBehaviorIT.java)
  - Why exemplar: Module-level contract harness; shows pattern for writing provider contract tests that integrate with shared base test configuration and feature toggles.

---

## Architecture / Policy Tests

- File: [pos-inventory/src/test/java/com/positivity/inventory/ArchitectureTest.java](../../durion-positivity-backend/pos-inventory/src/test/java/com/positivity/inventory/ArchitectureTest.java)
  - Why exemplar: ArchUnit-based enforcement of layering (controller → service → repository), package encapsulation, and service visibility. Useful as a canonical example for adding module architecture tests.
  - Snippet:

```java
@ArchTest
static final ArchRule controllers_should_not_access_repositories_directly = noClasses()
    .that().resideInAPackage("..internal.controller..")
    .should().dependOnClassesThat().resideInAPackage("..internal.repository..");
```

---

## Test Fixtures / Base Test Patterns

- File: [pos-people/src/test/java/com/positivity/people/BaseIntegrationTest.java](../../durion-positivity-backend/pos-people/src/test/java/com/positivity/people/BaseIntegrationTest.java)
  - Why exemplar: Centralized MockMvc setup, H2 test db properties, common `withAuth(...)` helper that sets `X-User`, `X-Authorities` and `X-Correlation-Id`. Use this as the canonical base for integration/contract tests requiring gateway-like headers.
  - Snippet:

```java
protected MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder) {
    return builder.header("X-User", TEST_USER)
                  .header("X-Authorities", TEST_AUTHORITIES)
                  .header("X-Correlation-Id", TEST_CORRELATION_ID);
}
```

---

## How to use these exemplars

- Follow the seeding patterns (direct repository writes) for contract/integration tests so tests are self-contained and deterministic.
- Use MockMvc with gateway-style paths (`/v1/...`) and `withAuth` helpers to simulate gateway-provided headers.
- Prefer `AssertJ` for expressive assertions in service/unit tests and `jsonPath` matchers in MockMvc contract tests.
- Add ArchUnit rules to each module to protect layering and package encapsulation.

---

## Notes

- All file paths above are relative to the repository root and verified to exist at scan time.
- This file is a living document — add more exemplars over time for new modules or test patterns.
