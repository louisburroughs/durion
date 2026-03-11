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

- File: [pos-people/src/test/java/com/positivity/people/ContractBehaviorIT.java](../../durion-positivity-backend/pos-people/src/test/java/com/positivity/people/ContractBehaviorIT.java)
  - Why exemplar: Comprehensive contract/integration spec that seeds domain state, mocks external clients via `@MockitoBean`, and uses `MockMvc` to exercise REST endpoints with gateway-style headers. Good model for writing deterministic contract tests that include happy, validation, auth and dependency-failure scenarios.
  - Key patterns demonstrated: `BaseContractIntegrationTest` usage, `@MockitoBean` for lightweight stubbing of downstream clients, explicit seeding helpers for domain entities, and clear `@DisplayName`-annotated test methods.
  - Snippet:

```java
@MockitoBean
private WorkexecJobTimeClient workexecJobTimeClient;

// seed domain entities then stub external client
seedTechnician(technicianId, "Jane", "Doe");
when(workexecJobTimeClient.getJobTimeTotals(...)).thenReturn(List.of(jobTime(...)));

mockMvc.perform(withAuth(get("/v1/people/reports/attendanceJobtimeDiscrepancy")
                .param("startDate", "2026-02-16")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].isFlagged").value(true));
```

- File: [pos-customer/src/test/java/com/positivity/customer/PersonServiceContractBehaviorIT.java](../../durion-positivity-backend/pos-customer/src/test/java/com/positivity/customer/PersonServiceContractBehaviorIT.java)
  - Why exemplar: Customer module contract tests show concise seeding of person/commercial fixtures, clear use of `withAuth` helpers, and focused assertions against JSON response shape. Useful when validating API contracts across modules.
  - Snippet:

```java
mockMvc.perform(withAuth(get("/v1/crm/persons/{id}", personId)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.id").value(personId.toString()));
```

- File: [pos-documents/src/test/java/com/positivity/documents/controller/DocumentRenderControllerIT.java](../../durion-positivity-backend/pos-documents/src/test/java/com/positivity/documents/controller/DocumentRenderControllerIT.java)
  - Why exemplar: Integration test covering controller behavior for document rendering, including file streaming and large payload handling. Demonstrates use of `MockMvc` for `multipart` or `application/json` requests and assertions on response content types and sizes.
  - Snippet:

```java
mockMvc.perform(withAuth(post("/v1/documents/render")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(renderRequest))))
    .andExpect(status().isOk())
    .andExpect(content().contentType("application/pdf"));
```

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

- File: [pos-documents/src/test/java/com/positivity/documents/service/PdfRenderingServiceTest.java](../../durion-positivity-backend/pos-documents/src/test/java/com/positivity/documents/service/PdfRenderingServiceTest.java)
  - Why exemplar: Focused unit tests for rendering logic and edge cases (large payloads, error handling). Good example of mocking rendering engine and asserting returned bytes and error propagation.
  - Snippet:

```java
when(pdfEngine.render(template)).thenReturn(byteArray);
byte[] result = service.render(template);
assertThat(result).isNotEmpty();
```

- File: [pos-document-helper/src/test/java/com/positivity/documents/helper/TemplateUtilsTest.java](../../durion-positivity-backend/pos-document-helper/src/test/java/com/positivity/documents/helper/TemplateUtilsTest.java)
  - Why exemplar: Deterministic utility tests covering template parsing/normalization including null/malformed inputs. Good model for simple, fast unit tests with clear expectations.
  - Snippet:

```java
assertEquals("expected", TemplateUtils.normalize(" input ")); 
```

- File: [pos-document-helper/src/test/java/com/positivity/documents/TemplateRegistrationTest.java](../../durion-positivity-backend/pos-document-helper/src/test/java/com/positivity/documents/TemplateRegistrationTest.java)
  - Why exemplar: Verifies initialization and registration of templates at startup; demonstrates clear setup/teardown and assertions against in-memory registry.
  - Snippet:

```java
initializer.register(template);
assertTrue(registry.contains(templateId));
```

- File: [pos-vehicle-fitment/src/test/java/com/positivity/vehiclefitment/service/VehicleApplicabilityHintServiceTest.java](../../durion-positivity-backend/pos-vehicle-fitment/src/test/java/com/positivity/vehiclefitment/service/VehicleApplicabilityHintServiceTest.java)
  - Why exemplar: Service-level unit test that mocks repositories/clients and asserts business rule outcomes across threshold branches. Good example of arranging mocks and verifying outputs.
  - Snippet:

```java
when(repo.find(...)).thenReturn(List.of(entity));
assertThat(service.calculateHints(...)).hasSize(1);
```

---

## Test Exemplars

- File: [pos-catalog/src/test/java/com/positivity/catalog/contract/ContractBehaviorIT.java](../../durion-positivity-backend/pos-catalog/src/test/java/com/positivity/catalog/contract/ContractBehaviorIT.java)
  - Why: Provider contract-style integration tests that validate endpoint semantics and error behavior against the implementation.
  - What it demonstrates: end-to-end controller + service verification using MockMvc/TestRestTemplate, clear scenario names, and focus on HTTP contract assertions.
  - Snippet:

    ```java
    @Test
    public void getProduct_notFound_returns404() throws Exception {
        mockMvc.perform(get("/v1/products/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
    ```

- File: [pos-catalog/src/test/java/com/positivity/catalog/contract/ProductLifecycleContractBehaviorIT.java](../../durion-positivity-backend/pos-catalog/src/test/java/com/positivity/catalog/contract/ProductLifecycleContractBehaviorIT.java)
  - Why: Focused lifecycle behavior tests that mirror business rules (discontinued immutability, replacements, effective-date validation).
  - What it demonstrates: arranging preconditions, invoking lifecycle endpoints, and asserting domain-specific error messages and response payload shapes.
  - Snippet:

    ```java
    @Test
    public void updateLifecycle_discontinued_withoutPermission_returns403() throws Exception {
        // arrange: create product in DISCONTINUED
        // act: PUT /v1/products/{id}/lifecycle without override authority
        // assert: 403 Forbidden
    }
    ```

- File: [pos-catalog/src/test/java/com/positivity/catalog/BaseIntegrationTest.java](../../durion-positivity-backend/pos-catalog/src/test/java/com/positivity/catalog/BaseIntegrationTest.java)
  - Why: Shared test harness that sets up security headers, MockMvc, test profile, and helper methods like `withAuth()` used by many integration tests.
  - What it demonstrates: consistent test setup, base utilities, and patterns for simulating authenticated calls with roles and authorities.
  - Snippet:

    ```java
    protected RequestPostProcessor withAuth(String username, String... authorities) {
        return request -> {
            request.addHeader("X-User", username);
            request.addHeader("X-Authorities", String.join(",", authorities));
            return request;
        };
    }
    ```

- File: [pos-catalog/src/test/java/com/positivity/catalog/config/TestSecurityConfig.java](../../durion-positivity-backend/pos-catalog/src/test/java/com/positivity/catalog/config/TestSecurityConfig.java)
  - Why: Test-only security configuration that simplifies authentication and authority injection for integration tests.
  - What it demonstrates: isolating security concerns in tests to avoid brittle integration setups while preserving the production security contract.

- ArchUnit & Contract Tests (pattern)
  - Why: Architecture rules and contract tests are used to enforce module boundaries and API guarantees.
  - What it demonstrates: writing architecture tests to prevent layering violations and contract tests to ensure backward-compatible HTTP semantics.

### Test Exemplar Recommendations

- Prefer `BaseIntegrationTest` for shared helpers: centralize auth, MockMvc setup, and reusable fixtures.
- Write contract tests that assert status codes and error messages, not just happy-path payloads. Use `BaseIntegrationTest.withAuth()` to test role-based guards.
- Follow ADR-0017 auth semantics: unauthenticated requests must assert `401 Unauthorized`; authenticated callers lacking permission must assert `403 Forbidden` (see `docs/adr/0017-api-controller-http-response-codes.adr.md`).
- For business-rule-heavy endpoints (like lifecycle transitions), add dedicated focused tests mirroring the service validations to catch regression early.

## How to use these exemplars

- Follow the seeding patterns (direct repository writes) for contract/integration tests so tests are self-contained and deterministic.
- Use MockMvc with gateway-style paths (`/v1/...`) and `withAuth` helpers to simulate gateway-provided headers.
- Prefer `AssertJ` for expressive assertions in service/unit tests and `jsonPath` matchers in MockMvc contract tests.
- Add ArchUnit rules to each module to protect layering and package encapsulation.

---

## Notes

- All file paths above are relative to the repository root and verified to exist at scan time.
- This file is a living document — add more exemplars over time for new modules or test patterns.
