# Code Exemplars — durion-positivity-backend

Purpose: identify high-quality, representative code examples for Java backend developers working in this repository. These exemplars demonstrate architecture, service patterns, controller design, validation, and testing practices used across the project.

## Table of contents
- Java Exemplars
- Architecture Layer Exemplars
- Recommendations

---

## Java Exemplars

- File: [pos-catalog/src/main/java/com/positivity/catalog/internal/controller/CatalogController.java](../../durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/controller/CatalogController.java)
  - Why: Clean REST controller patterns, role-based `@PreAuthorize` usage, documented OpenAPI annotations, and `@EmitEvent` usage for audit/event logging.
  - What it demonstrates: thin controller delegating to services, consistent HTTP responses (200/201/204/404/400), and proper use of annotation-driven security and observability.
  - Snippet:
    ```java
    @GetMapping("/{productId}/lifecycle")
    @EmitEvent(id = "CATALOG_PRODUCT_LIFECYCLE_GET", apiVersion = "1")
    public ResponseEntity<ProductLifecycleResponse> getProductLifecycle(@PathVariable UUID productId) {
        return ResponseEntity.ok(productLifecycleService.getLifecycle(productId));
    }
    ```

- File: [pos-catalog/src/main/java/com/positivity/catalog/service/ProductLifecycleService.java](../../durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/service/ProductLifecycleService.java)
  - Why: Good example of service-layer orchestration, transactional boundaries, validation, custom exceptions, and metrics via Micrometer.
  - What it demonstrates: defensive validation, separation of concerns, use of repository abstractions, and metrics counters for success/denied events.
  - Snippet:
    ```java
    @Transactional
    public ProductLifecycleResponse updateLifecycle(UUID productId, ProductLifecycleUpdateRequest request) {
        if (request == null || request.getLifecycleState() == null) {
            lifecycleUpdateDeniedCounter.increment();
            throw new CatalogValidationException("lifecycleState is required");
        }
        ProductEntity product = findProduct(productId);
        // ... business rules and persistence
    }
    ```

- File: [pos-catalog/src/main/java/com/positivity/catalog/internal/entity/ProductEntity.java](../../durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/entity/ProductEntity.java)
  - Why: Example JPA entity design for complex domain objects with lifecycle metadata and helpful schema annotations.
  - What it demonstrates: `@PrePersist` ID generation, use of `@ElementCollection`, `@Enumerated`, and explicit lifecycle fields used in business logic.
  - Snippet:
    ```java
    @Enumerated(EnumType.STRING)
    private ProductLifecycleState lifecycleState = ProductLifecycleState.ACTIVE;

    private Instant lifecycleStateEffectiveAt;
    private UUID lastStateChangedBy;
    private Instant lastStateChangedAt;
    ```

- File: [pos-people/src/main/java/com/positivity/people/internal/controller/PersonController.java](../../durion-positivity-backend/pos-people/src/main/java/com/positivity/people/internal/controller/PersonController.java)
  - Why: Clear CRUD controller with `@EmitEvent` on create/update, straightforward response handling, and minimal controller logic.
  - What it demonstrates: consistent request/response patterns and use of service to transform entities/DTOs.
  - Snippet:
    ```java
    @EmitEvent(id = "PEOPLE_PERSON_CREATE", apiVersion = "1")
    @PostMapping
    public ResponseEntity<Person> createPerson(@RequestBody Person person) {
        Person saved = personService.savePerson(person);
        return ResponseEntity.status(201).body(saved);
    }
    ```

- File: [pos-people/src/main/java/com/positivity/people/service/PersonService.java](../../durion-positivity-backend/pos-people/src/main/java/com/positivity/people/service/PersonService.java)
  - Why: Service layer that converts between JPA entities and DTOs, with a small example of defensive validation and a TODO for external integration.
  - What it demonstrates: conversion helpers (`toDto` / `toEntity`) and transactional save semantics.
  - Snippet:
    ```java
    @Transactional
    public Person savePerson(Person person) {
        if (person.getUsername() != null && !validateUsernameWithSecurityService(person.getUsername())) {
            throw new IllegalArgumentException("Username is not valid or does not exist in security service");
        }
        com.positivity.people.internal.entity.Person saved = personRepository.save(toEntity(person));
        return toDto(saved);
    }
    ```

## Architecture Layer Exemplars

- Presentation Layer (Controllers)
  - Exemplars: `CatalogController.java`, `PersonController.java`
  - Strengths: thin controllers, clear OpenAPI annotations, consistent HTTP status usage, event emission with `@EmitEvent`.

- Business Logic Layer (Services)
  - Exemplars: `ProductLifecycleService.java`, `PersonService.java`
  - Strengths: transactional demarcation, repository abstraction, domain validation and custom exceptions, Micrometer metrics.

- Data Access Layer (Entities & Repos)
  - Exemplars: `ProductEntity.java` (entity modeling), repository patterns used across `pos-*` modules.
  - Strengths: clear JPA mappings, use of `@PrePersist` and `@ElementCollection` where appropriate.

- Cross-Cutting Concerns
  - Observability: consistent use of Micrometer counters in service code.
  - Events/Audit: `@EmitEvent` annotation used in controllers for write operations.
  - Security: annotation-based method security (`@PreAuthorize`) in controllers.

## Recommendations

- Use the controllers as canonical examples when adding new REST endpoints: keep them thin and delegate to services.
- Follow `ProductLifecycleService` pattern for business rules: validate early, throw descriptive custom exceptions, and record metrics for important state changes.
- When adding entities, mirror the `ProductEntity` approach: add lifecycle metadata, use `@PrePersist` for IDs, and document schema with OpenAPI annotations where DTOs are returned.
- Add unit tests that mirror the contract tests pattern already present under `pos-catalog/src/test/java/.../contract`.

---

End of document.

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
- For business-rule-heavy endpoints (like lifecycle transitions), add dedicated focused tests mirroring the service validations to catch regression early.
