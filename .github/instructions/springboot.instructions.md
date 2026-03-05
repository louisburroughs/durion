---
description: 'Guidelines for building Spring Boot base applications'
applyTo: '**/*.java, **/*.kt'
---

# Spring Boot Development

## General Instructions

- Make only high confidence suggestions when reviewing code changes.
- Write code with good maintainability practices, including comments on why certain design decisions were made.
- Handle edge cases and write clear exception handling.
- For libraries or external dependencies, mention their usage and purpose in comments.

## Spring Boot Instructions

### Dependency Injection

- Use constructor injection for all required dependencies.
- Declare dependency fields as `private final`.

### Configuration

- Use YAML files (`application.yml`) for externalized configuration.
- Environment Profiles: Use Spring profiles for different environments (dev, test, prod)
- Configuration Properties: Use @ConfigurationProperties for type-safe configuration binding
- Secrets Management: Externalize secrets using environment variables or secret management systems

### Code Organization

- Package Structure: Organize by feature/domain rather than by layer. Follow ADR-0026 service contract boundaries.
- Public Contract Boundaries: `com.positivity.{domain}.service` must contain interfaces only (ADR-0026).
- Separation of Concerns: Concrete service implementations must reside in `com.positivity.{domain}.internal.service` packages. Controllers and internal components depend only on service interfaces, never on concrete implementations across boundaries. Keep controllers thin, services focused, and repositories simple.
- Utility Classes: Make utility classes final with private constructors.
- Single-Organization Context: Do not use or reference `tenantId` in service contracts, payloads, or data models (ADR-0023).

### Identifiers & Domain Entities

- Platform Identifiers (ADR-0013, ADR-0027): Use `UUID` (v7) types for all platform entity identifiers in service lookup contracts, DTOs, and JPA Entity fields. `String` is only permitted for external business identifiers (e.g., `sku`, `partNumber` from external systems).
- Audit Actor Fields (ADR-0018): `createdBy`, `updatedBy`, and `changedBy` values must be derived from the authenticated context using `SecurityContextHelper` in the service layer, not from request payloads. Persist these fields as `String` since they represent actor identities (e.g., usernames).
- Entity Timestamps (ADR-0024): All mutable JPA entities must include both `createdAt` and `updatedAt`. Standardize on Spring Data JPA auditing (`@CreatedDate`, `@LastModifiedDate` with `@EntityListeners(AuditingEntityListener.class)`). Do not call `Instant.now()` directly; use an injected `Clock` via `DateTimeProvider`.

### Controllers & HTTP Responses

- HTTP Response Codes (ADR-0017): Stick strictly to canonical status codes:
  - `200 OK` (reads and mutations with a body)
  - `201 Created` (successful resource creation)
  - `204 No Content` (success with no body, e.g., delete)
  - `400 Bad Request` (malformed payload or validation shape errors)
  - `401 Unauthorized` / `403 Forbidden`
  - `404 Not Found`
  - `409 Conflict` (state collisions, version mismatches, idempotency issues)
  - `422 Unprocessable Entity` (semantically valid requests violating domain policies)
- Error Envelopes: Standardize non-2xx responses to include `code`, `message`, `status`, `timestamp`, and `correlationId`. Use `fieldErrors[]` collection for field-level validation issues. Always propagate `X-Correlation-Id`.

### Service Layer

- Place business logic in `@Service`-annotated classes.
- Services should be stateless and testable.
- Inject repositories via the constructor.
- Service method signatures should use domain IDs or DTOs, not expose repository entities directly unless necessary.

### Logging

- Use SLF4J for all logging (`private static final Logger logger = LoggerFactory.getLogger(MyClass.class);`).
- Do not use concrete implementations (Logback, Log4j2) or `System.out.println()` directly.
- Use parameterized logging: `logger.info("User {} logged in", userId);`.

### Security & Input Handling

- Use parameterized queries | Always use Spring Data JPA or `NamedParameterJdbcTemplate` to prevent SQL injection.
- Validate request bodies and parameters using JSR-380 (`@NotNull`, `@Size`, etc.) annotations and `BindingResult`

## Build and Verification

- After adding or modifying code, verify the project continues to build successfully.
- If the project uses Maven, run `mvn clean package`.
- Ensure all tests pass as part of the build.

## Useful Commands

| Maven Command                     | Description                                   |
|:----------------------------------|:----------------------------------------------|
|`./mvnw spring-boot:run`           | Run the application.                          |
|`./mvnw spring-boot:repackage`     | Package the application as a JAR.             |
|`./mvnw spring-boot:build-image`   | Package the application as a container image. |
