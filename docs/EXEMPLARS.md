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
    @Operation(summary = "Get product lifecycle by product ID")
    @ApiResponse(responseCode = "200", description = "Lifecycle found")
    @ApiResponse(responseCode = "404", description = "Product not found")
    @GetMapping("/{productId}/lifecycle")
    @EmitEvent(id = "CATALOG_PRODUCT_LIFECYCLE_GET", apiVersion = "1")
    public ResponseEntity<ProductLifecycleResponse> getProductLifecycle(
        @Parameter(description = "Product identifier", required = true) @PathVariable UUID productId) {
        return ResponseEntity.ok(productLifecycleService.getLifecycle(productId));
    }
    ```

- File: [pos-customer/src/main/java/com/positivity/customer/internal/controller/CustomerController.java](../../durion-positivity-backend/pos-customer/src/main/java/com/positivity/customer/internal/controller/CustomerController.java)
  - Why: Example of controller for modules that expose combined person/commercial APIs. Shows `@PreAuthorize` usage, OpenAPI annotations, and `@EmitEvent` for write operations.
  - What it demonstrates: routing decisions (commercial vs person service), thin controller logic, proper use of `ResponseEntity` to convey 200/201/204/404 semantics.
  - Snippet:

    ```java
    @Operation(summary = "Get customer by ID")
    @ApiResponse(responseCode = "200", description = "Customer found")
    @ApiResponse(responseCode = "404", description = "Customer not found")
    @GetMapping("/{id}")
    @EmitEvent(id = "CUSTOMER_CUSTOMER_GET", apiVersion = "1")
    @PreAuthorize("hasAuthority('crm:party:view')")
    public ResponseEntity<CustomerDTO> getCustomerById(
      @Parameter(description = "Customer identifier", required = true) @PathVariable UUID id) {
      return commercialService.getCustomerById(id)
          .or(() -> personService.getCustomerById(id))
          .map(ResponseEntity::ok)
          .orElse(ResponseEntity.notFound().build());
    }
    ```

- File: [pos-customer/src/main/java/com/positivity/customer/internal/service/PersonServiceImpl.java](../../durion-positivity-backend/pos-customer/src/main/java/com/positivity/customer/internal/service/PersonServiceImpl.java)
  - Why: Service implementation pattern that encapsulates transactional boundaries, validation, and repository orchestration. Good model for service methods that must coordinate multiple repositories or clients.
  - What it demonstrates: `@Service` with `@Transactional` methods, mapping between DTOs/entities, throwing domain-specific exceptions and emitting metrics/events as needed.
  - Snippet:

    ```java
    @Service
    public class PersonServiceImpl implements PersonService {
      @Transactional
      public CustomerDTO createPerson(CustomerDTO dto) {
        validate(dto);
        var entity = toEntity(dto);
        entity = repository.save(entity);
        return toDto(entity);
      }
    }
    ```

- File: [pos-catalog/src/main/java/com/positivity/catalog/internal/entity/ProductEntity.java](../../durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/internal/entity/ProductEntity.java)
  - Why: Canonical entity demonstrating JPA + Spring Data auditing patterns, UUIDv7 ID generation, and lifecycle defaults.
  - What it demonstrates: `@EntityListeners(AuditingEntityListener.class)`, `@UUIDv7Id` with `@GeneratedValue`, audited timestamps via `@CreatedDate`/`@LastModifiedDate`, and `@PrePersist` for default status/lifecycle values. Ids stored as UUID.
  - Snippet:

    ```java
    @Entity
    @EntityListeners(AuditingEntityListener.class)
    @Table(name = "product")
    @Schema(description = "Represents a product in the catalog")
    public class ProductEntity {
      @Id
      @GeneratedValue
      @UUIDv7Id
      @Column(columnDefinition = "UUID")
      private UUID id;

      @Column(nullable = false)
      private UUID foriegnKeyId;

      @CreatedDate
      @Column(nullable = false, updatable = false)
      private Instant createdAt;

      @LastModifiedDate
      @Column(nullable = false)
      private Instant updatedAt;

      @PrePersist
      public void applyDefaults() {
        if (status == null) {
          status = ProductStatus.ACTIVE;
        }
        if (lifecycleState == null) {
          lifecycleState = ProductLifecycleState.ACTIVE;
        }
      }
    }
    ```
  - Note: For deterministic time in tests, do not call `Instant.now()` directly in entities/services. Use Spring auditing with a `DateTimeProvider` backed by an injected `Clock`.

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
  - What it demonstrates: use of `@ElementCollection`, `@Enumerated`, audited lifecycle fields, and explicit product lifecycle metadata used in business logic.
  - Snippet:

    ```java
    @Enumerated(EnumType.STRING)
    private ProductLifecycleState lifecycleState = ProductLifecycleState.ACTIVE;

    private Instant lifecycleStateEffectiveAt;
    private UUID lastStateChangedBy;
    private Instant lastStateChangedAt;
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
  - Use of Instant.now(Clock)  with an injected clock is mandatory for performance testing and data loading

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
- When adding entities, mirror the `ProductEntity` approach: add lifecycle metadata, use `@UUIDv7Id` + `@GeneratedValue` for IDs, use `@PrePersist` for defaults, and rely on auditing (`@CreatedDate`/`@LastModifiedDate`) with an injected `Clock`-backed `DateTimeProvider` for timestamps.
- Add unit tests that mirror the contract tests pattern already present under `pos-catalog/src/test/java/.../contract`.

## API Documentation Annotation Standards

- DTOs: add `@Schema` annotations for request/response DTO objects and important fields so contract metadata is explicit in generated OpenAPI.
- Controllers: use OpenAPI annotations on endpoints and controllers (for example `@Tag`, `@Operation`, `@ApiResponse`, and `@Parameter`) unless already present and accurate.
- Do **not** use the `@ApiResponses` wrapper annotation; Sonar flags it as unnecessary in this codebase. Prefer repeatable `@ApiResponse` annotations directly on the endpoint.
- Keep annotations behavior-focused and synchronized with actual status codes, validation rules, and response shapes.
- Reuse existing exemplar patterns instead of introducing custom annotation styles:
  - `CatalogController.java` for controller-level and endpoint-level OpenAPI patterns.
  - `InventoryAvailabilityResponse.java` for field-level `@Schema` usage.

---

End of document.
