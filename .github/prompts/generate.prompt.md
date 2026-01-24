---
name: 'Feature Code Generation from Planning Document'
agent: 'agent'
description: 'Transform implementation planning document into production-ready code. Generates all backend services, frontend components, tests, migrations, and configuration in a single output file before applying changes to project repositories.'
model: 'Claude Sonnet 4.5 (copilot)'
---

# Feature Code Generation from Planning Document

## Goal

Act as a Senior Full-Stack Code Generation Specialist. Your role is to take a comprehensive **implementation planning document** (output from `planning.prompt.md`) and generate **all production-ready code** for the feature in a **single, well-organized output file**.

**Do NOT directly modify project files.** Instead, generate a complete code output that can be:
1. Reviewed as a cohesive unit before implementation
2. Validated against acceptance criteria
3. Broken into individual files for application to actual projects
4. Tested in isolation before merge

This approach ensures code quality, enables code review, and allows human approval before touching live repositories.

---

## Input Requirements

You must receive a complete **planning document** with these sections:

- **Feature Overview**: User story, acceptance criteria
- **Architecture & Design**: Bounded contexts, integration points, ADRs
- **Backend Implementation**: Services, entities, APIs, events, migrations
- **Frontend Implementation**: Components, screens, TypeScript interfaces
- **Testing Strategy**: Unit, integration, and acceptance test requirements
- **Cross-Cutting Concerns**: Security, performance, observability
- **Documentation Requirements**: Code docs, architecture updates, user guides
- **Deployment & Rollout**: Strategy, backward compatibility
- **Branching Strategy**: Feature branch name, destination, merge strategy

Without a complete planning document, **request clarification before proceeding**.

---

## Pre-Generation Validation

Before generating code, validate the planning document:

### ✓ Completeness Checks
- [ ] All backend services are fully specified (inputs, outputs, logic)
- [ ] All entities have complete field definitions and relationships
- [ ] All REST APIs have request/response schemas
- [ ] All Vue components have props, emits, and state defined
- [ ] Testing strategy covers all acceptance criteria
- [ ] Branching strategy and commit conventions are clear

### ✓ Feasibility Checks
- [ ] No architectural conflicts with existing patterns
- [ ] All dependencies are documented
- [ ] Database migrations are reversible
- [ ] Security requirements are clear
- [ ] Performance constraints are defined

### ✓ Alignment Checks
- [ ] Backend code follows Java/Spring Boot conventions (durion-positivity-backend)
- [ ] Frontend code follows Groovy/Moqui conventions (durion-moqui-frontend)
- [ ] Backend services use Spring @Service, @RestController, JPA entities
- [ ] Frontend services follow `{domain}.{service-type}#{Action}` naming (Moqui pattern)
- [ ] Backend APIs follow `/api/{domain}/{resource}` pattern (Spring Boot REST)
- [ ] Frontend APIs follow `/rest/api/v{version}/{resource}/{id}/{action}` pattern (Moqui REST)
- [ ] Components follow Vue 3 Composition API patterns
- [ ] Backend tests use JUnit 5, Mockito, TestContainers
- [ ] Frontend tests use Spock (Groovy services), Jest (Vue components)

### Decision Gate

- **Validation Passes**: Generate code → Continue to Code Generation
- **Gaps Found**: Request planning document updates → Pause
- **Conflicts**: Identify and request architectural review → Pause

---

## Code Generation Output Format

Generate a **single, comprehensive code file** with clear section separators and file path markers. The output file should be named:

```
{issue-number}-{feature-name}-CODE-GENERATION.md
```

### Output File Structure

```markdown
# Code Generation Output: {Feature Name}
**Issue**: #{issue-number}  
**Feature**: {feature-name}  
**Generated Date**: {ISO-8601 date}  
**Branch**: feature/{issue-number}-{description}  

Table of Contents:
- [Backend Code](#backend-code)
  - [Services](#services)
  - [Entities/Models](#entities)
  - [REST APIs](#rest-apis)
  - [Events](#events)
  - [Database Migrations](#database-migrations)
  - [Configuration](#configuration)
- [Frontend Code](#frontend-code)
  - [Vue Components](#vue-components)
  - [Screens/Pages](#screens)
  - [TypeScript Interfaces](#typescript-interfaces)
  - [Store/State](#store)
- [Test Code](#test-code)
  - [Backend Tests](#backend-tests)
  - [Frontend Tests](#frontend-tests)
  - [Integration Tests](#integration-tests)
  - [API Contract Tests](#api-contract-tests)
- [Documentation](#documentation)
  - [Component README Updates](#component-readme)
  - [Architecture Documentation](#architecture-docs)
  - [Code Comments](#code-comments)
- [Deployment & Configuration](#deployment)
  - [Environment Variables](#environment-vars)
  - [Docker/Kubernetes Updates](#deployment-config)
  - [Rollback Procedures](#rollback)

---

## Backend Code (durion-positivity-backend - Java/Spring Boot)

### Services

#### File: `pos-{domain}/src/main/java/com/pos/{domain}/service/{ServiceName}Service.java`
**Purpose**: {Service purpose from planning}  
**Issue Reference**: #{issue-number}

\`\`\`java
// Full Spring Boot service with:
// - @Service annotation
// - Method signatures matching planning specs
// - Business logic implementation
// - Error handling with custom exceptions
// - @Transactional for transaction management
// - SLF4J logging
// - Javadoc comments on public methods
// - Constructor injection for dependencies

package com.pos.{domain}.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class {ServiceName}Service {
    // Inject dependencies via constructor
    private final {Repository}Repository repository;
    
    /**
     * {Method description from planning}
     * @param {param} {description}
     * @return {return description}
     * @throws {Exception} {when thrown}
     */
    @Transactional
    public {ReturnType} methodName({ParamType} param) {
        log.info("Processing request: {}", param);
        // Business logic implementation
        return result;
    }
}
\`\`\`

---

### Entities/Models

#### File: `pos-{domain}/src/main/java/com/pos/{domain}/entity/{EntityName}.java`
**Purpose**: {Entity purpose from planning}

\`\`\`java
// Complete JPA entity with:
// - @Entity annotation
// - All fields with types and constraints
// - @OneToMany, @ManyToOne, @ManyToMany relationships
// - Bean Validation annotations (@NotNull, @Size, etc.)
// - @Column mappings
// - @Table(indexes = {...}) for performance
// - Lombok annotations (@Data, @Entity, @Table)

package com.pos.{domain}.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "{table_name}", indexes = {
    @Index(name = "idx_{field}", columnList = "{field}")
})
@Data
public class {EntityName} {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @NotNull
    @Size(max = 255)
    @Column(nullable = false)
    private String fieldName;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_id")
    private RelatedEntity relatedEntity;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
\`\`\`

---

### REST APIs

#### File: `pos-{domain}/src/main/java/com/pos/{domain}/controller/{Resource}Controller.java`
**Endpoint**: {METHOD} /api/{domain}/{resource}/{id}/{action}  
**Purpose**: {API purpose from planning}

\`\`\`java
// Spring Boot REST controller with:
// - @RestController annotation
// - @RequestMapping for base path
// - @Valid for parameter validation
// - Request/response DTOs
// - @ResponseStatus for HTTP status codes
// - @PreAuthorize for authorization
// - Exception handling via @ExceptionHandler or ControllerAdvice
// - SLF4J logging

package com.pos.{domain}.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/{domain}/{resource}")
@RequiredArgsConstructor
@Slf4j
public class {Resource}Controller {
    private final {Service}Service service;
    
    /**
     * {API description from planning}
     * @param request {description}
     * @return {response description}
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_{domain}:write')")
    public ResponseEntity<{Response}DTO> create(@Valid @RequestBody {Request}DTO request) {
        log.info("{METHOD} /api/{domain}/{resource} - Request: {}", request);
        var result = service.methodName(request);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_{domain}:read')")
    public ResponseEntity<{Response}DTO> getById(@PathVariable UUID id) {
        log.info("GET /api/{domain}/{resource}/{} - Fetching", id);
        var result = service.findById(id);
        return ResponseEntity.ok(result);
    }
}
\`\`\`

---

### Domain Events

#### File: `pos-{domain}/src/main/java/com/pos/{domain}/event/{EventName}Event.java`
**Event Type**: {Full event class name}  
**Trigger**: {When/why emitted from planning}

\`\`\`java
// Spring ApplicationEvent with:
// - Extends ApplicationEvent or custom base event
// - Event payload structure
// - Timestamp and ID generation
// - Serialization support (@JsonSerialize if needed)
// - Javadoc documentation

package com.pos.{domain}.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * {Event description from planning}
 * Triggered when: {trigger condition}
 */
@Getter
public class {EventName}Event extends ApplicationEvent {
    private final UUID eventId;
    private final UUID aggregateId;
    private final String eventType;
    private final Instant timestamp;
    // Event-specific payload fields
    private final Object payload;
    
    public {EventName}Event(Object source, UUID aggregateId, Object payload) {
        super(source);
        this.eventId = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.eventType = this.getClass().getSimpleName();
        this.timestamp = Instant.now();
        this.payload = payload;
    }
}

// Event Publisher in Service:
// @Autowired ApplicationEventPublisher eventPublisher;
// eventPublisher.publishEvent(new {EventName}Event(this, aggregateId, payload));

// Event Listener:
// @EventListener
// public void handle{EventName}Event({EventName}Event event) {
//     log.info("Handling event: {}", event.getEventType());
// }
\`\`\`

---

### Database Migrations

#### File: `db/migration/V{version}__{description}.sql`
**Purpose**: {Migration purpose from planning}

\`\`\`sql
-- Create/alter tables with:
-- - Column definitions
-- - Constraints (PK, FK, CHECK)
-- - Indexes
-- - Comments

-- Rollback:
-- {Rollback SQL if needed}
\`\`\`

---

### Configuration

#### File: `pos-{domain}/src/main/resources/application.yml` (additions)
**Purpose**: Spring Boot configuration for services, datasource, and features

\`\`\`yaml
# Domain-specific configuration
pos:
  {domain}:
    feature:
      enabled: true
      {property}: {value}

# Database configuration (if new datasource)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pos_{domain}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

# Logging configuration
logging:
  level:
    com.pos.{domain}: DEBUG
\`\`\`

---

## Frontend Code

### Vue Components

#### File: `runtime/component/{component-name}/webapp/vue/{ComponentName}.vue`
**Purpose**: {Component purpose from planning}  
**Props**: {List from planning}  
**Emits**: {List from planning}

\`\`\`vue
<template>
  <!-- Complete Vue 3 template with:
       - Quasar components
       - v-model bindings
       - Event handlers
       - Conditional rendering
       - Loops
       - Accessibility attributes
  -->
</template>

<script setup lang="ts">
// Composition API setup with:
// - Props definition with types
// - Emits definition
// - Reactive state (ref, reactive)
// - Computed properties
// - Watchers
// - Lifecycle hooks
// - Event handlers
// - API calls
</script>

<style scoped>
/* Component-scoped styles
   Using Quasar design tokens
   Mobile-responsive
*/
</style>
\`\`\`

---

### Screens/Pages

#### File: `runtime/component/{component-name}/screen/{ScreenName}.xml`
**Route**: /path/to/screen  
**Purpose**: {Screen purpose from planning}

\`\`\`xml
<!-- Moqui screen definition with:
     - Actions for data loading
     - Subscreens for composition
     - Forms for input
     - Display sections
     - Transitions for navigation
-->
\`\`\`

#### File: `runtime/component/{component-name}/webapp/vue/{ScreenName}Page.vue`
**Purpose**: Vue implementation of screen from planning

\`\`\`vue
<template>
  <!-- Page component using sub-components
       Data fetching on mount
       Form submission
       Error handling
  -->
</template>

<script setup lang="ts">
// Page-level state and logic
</script>
\`\`\`

---

### TypeScript Interfaces

#### File: `runtime/component/{component-name}/webapp/types/{types-name}.ts`
**Purpose**: Type definitions for API responses and state from planning

\`\`\`typescript
// Complete type definitions matching planning:
// - Entity types
// - API request/response types
// - Component prop types
// - State types
// - Enum types

export interface {EntityName} {
  id: string;
  // ... all fields from planning
}

export interface API{Action}Request {
  // ... all request fields
}

export interface API{Action}Response {
  status: "SUCCESS" | "FAILED";
  data?: {EntityName};
  error?: string;
}
\`\`\`

---

### Store/State Management

#### File: `runtime/component/{component-name}/webapp/store/{domain}.ts`
**Purpose**: Vuex/Pinia store for {domain} state from planning

\`\`\`typescript
// State management with:
// - State definition
// - Getters/computed
// - Actions for async operations
// - Mutations for state changes
// - Type-safe actions
\`\`\`

---

## Test Code

### Backend Tests

#### File: `pos-{domain}/src/test/java/com/pos/{domain}/service/{ServiceName}ServiceTest.java`
**Purpose**: Unit tests for {ServiceName}Service

\`\`\`java
// JUnit 5 test with:
// - @SpringBootTest or @ExtendWith(MockitoExtension.class)
// - Happy path test for each method
// - Error scenario tests
// - Edge case tests
// - @BeforeEach/@AfterEach for setup/cleanup
// - @Mock/@InjectMocks for mocking dependencies
// - AssertJ assertions from planning test strategy

package com.pos.{domain}.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class {ServiceName}ServiceTest {
    @Mock
    private {Repository}Repository repository;
    
    @InjectMocks
    private {ServiceName}Service service;
    
    @BeforeEach
    void setUp() {
        // Setup test data
    }
    
    @Test
    @DisplayName("should {test description from planning}")
    void testMethodName() {
        // Arrange
        var input = createTestInput();
        when(repository.findById(any())).thenReturn(Optional.of(entity));
        
        // Act
        var result = service.methodName(input);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getField()).isEqualTo(expectedValue);
        verify(repository).save(any());
    }
}
\`\`\`

---

### Frontend Tests

#### File: `runtime/component/{component-name}/webapp/__tests__/{ComponentName}.spec.ts`
**Purpose**: Jest tests for {ComponentName}.vue

\`\`\`typescript
// Jest test suite with:
// - Component rendering tests
// - Prop passing tests
// - Event emission tests
// - User interaction tests
// - API call mocking
// - Assertions from planning test strategy

describe("{ComponentName}", () => {
  it("should {test description from planning}", () => {
    // Test implementation
  });
});
\`\`\`

---

### Integration Tests

#### File: `pos-{domain}/src/test/java/com/pos/{domain}/integration/{FeatureName}IntegrationTest.java`
**Purpose**: Integration tests validating {feature-name} end-to-end

\`\`\`java
// Spring Boot integration test with:
// - @SpringBootTest with TestContainers for database
// - Multiple service calls
// - Event emission and handling via @EventListener
// - API contract validation via MockMvc or TestRestTemplate
// - @Transactional for automatic cleanup

package com.pos.{domain}.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class {FeatureName}IntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    @DisplayName("should {acceptance criterion from planning}")
    void testEndToEndFlow() {
        // Arrange: Prepare test data
        
        // Act: Execute full feature flow
        var response = restTemplate.postForEntity(
            "/api/{domain}/{resource}",
            request,
            ResponseDTO.class
        );
        
        // Assert: Verify complete flow
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
    }
}
\`\`\`

---

### API Contract Tests

#### File: `pos-{domain}/src/test/java/com/pos/{domain}/controller/{Resource}ControllerTest.java`
**Purpose**: Contract tests for {resource} REST API

\`\`\`java
// Spring Boot API contract tests with:
// - @WebMvcTest for controller layer testing
// - MockMvc for HTTP request/response testing
// - Request validation
// - Response schema validation via JSON assertions
// - Status code validation
// - Error response validation
// - @WithMockUser for authorization testing

package com.pos.{domain}.controller;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest({Resource}Controller.class)
class {Resource}ControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private {Service}Service service;
    
    @Test
    @DisplayName("should {HTTP method} {endpoint} with valid request")
    @WithMockUser(authorities = "SCOPE_{domain}:write")
    void testCreateResource() throws Exception {
        // Arrange
        var requestJson = """
            {
              "field": "value"
            }
            """;
        
        // Act & Assert
        mockMvc.perform(post("/api/{domain}/{resource}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.field", is("value")));
    }
}
\`\`\`

---

## Documentation

### Component README Updates

#### File: `runtime/component/{component-name}/README.md` (additions)
**Purpose**: Document new services, entities, screens from planning

\`\`\`markdown
## New Features Added (Issue #{issue-number})

### Services
- **{domain}.{service-type}#{Action}**: {Description}
  - Parameters: {List}
  - Returns: {Type}
  - Example: {JSON example}

### Entities
- **{EntityName}**: {Description}
  - Fields: {List}
  - Relationships: {List}

### Screens
- **/{screen-path}**: {Description}
  - Components: {List}
  - User flows: {Description}

### APIs
- **{METHOD} /rest/api/v{version}/{path}**: {Description}
  - Request: {JSON example}
  - Response: {JSON example}
  - Authorization: {Requirements}
\`\`\`

---

### Architecture Documentation

#### File: `.github/docs/architecture/{domain}-{feature}.md`
**Purpose**: Architecture documentation for {feature-name} if creating new patterns

\`\`\`markdown
# {Feature Name} Architecture

## Overview
{Architecture overview from planning}

## Service Interactions
{Mermaid diagram showing service communication}

## Data Flow
{Mermaid diagram showing data flow}

## Integration Points
{Description of integration with other bounded contexts}

## Design Decisions
{Why decisions were made from planning}

## Related ADRs
{Links to relevant ADRs}
\`\`\`

---

### Code Comments

All generated code includes:
- **Backend (Java)**: Javadoc comments explaining purpose, parameters, return value, exceptions
- **Frontend (Groovy)**: JSDoc/Groovy doc comments for Moqui services
- **Complex Logic**: Inline comments explaining "why" not "what"
- **Database Migration**: SQL comments explaining table purpose and column semantics
- **Vue Components**: Comments on non-obvious template logic or state management
- **Tests**: Comments explaining test scenario and expected behavior

Example (Backend - Java):
```java
/**
 * Calculate Available-to-Promise quantity for an item.
 * Implements ATP calculation per ADR-0001 using daily ledger summation.
 *
 * @param itemId The item identifier
 * @param date The date for which to calculate ATP (defaults to today)
 * @return ATP quantity (may be negative if over-allocated)
 * @throws EntityNotFoundException if item does not exist
 */
public BigDecimal calculateItemATP(UUID itemId, LocalDate date) {
    if (date == null) {
        date = LocalDate.now();
    }
    // Query ledger entries for all movements up to and including date
    List<LedgerEntry> entries = ledgerRepository.findByItemIdAndDateLessThanEqual(itemId, date);
    
    // Sum daily balances: beginning_balance + movements
    BigDecimal atp = entries.stream()
        .map(LedgerEntry::getBalance)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    
    return atp; // May be negative if over-allocated
}
```

---

## Deployment & Configuration

### Environment Variables

**New Environment Variables Required**:
```bash
# {Variable Name}: {Description from planning}
{VAR_NAME}={default-value}

# {Variable Name}: {Description from planning}
{VAR_NAME2}={default-value}
```

**Configuration Files to Update**:
- `application.yml` (Spring Boot backend)
- `.env.example` (Docker development)
- `.env.production` (Production environment)

---

### Docker/Kubernetes Updates

#### File: `docker-compose.yml` (additions)
**Purpose**: Add services or configuration from planning

\`\`\`yaml
# New services or updated configurations
# Environment variables
# Volumes
# Ports
# Networks
\`\`\`

---

### Rollback Procedures

**Rollback Steps** (if feature needs to be reverted):

1. **Database Rollback**:
   ```sql
   -- Run rollback migration from planning
   -- Restore backup if data loss occurred
   ```

2. **Code Rollback**:
   ```bash
   git revert <merge-commit-hash>
   git push origin main
   ```

3. **Configuration Rollback**:
   - Revert environment variables to previous values
   - Restart affected services

4. **Verification**:
   - Run smoke tests to confirm rollback success
   - Monitor logs for errors
   - Verify user-visible features work

---

## Code Quality & Compliance Checklist

Before applying code to projects, verify:

### ✓ Naming & Conventions
- [ ] Groovy services follow `{domain}.{service-type}#{Action}` pattern
- [ ] TypeScript files use PascalCase for components, camelCase for utilities
- [ ] SQL migration files have `V{version}__{description}.sql` format
- [ ] Commit message will follow Conventional Commits format

### ✓ Code Quality
- [ ] No hardcoded secrets (API keys, passwords, connection strings)
- [ ] All user inputs are validated and sanitized
- [ ] Error messages are user-friendly, not revealing system internals
- [ ] Code follows DRY principle (no duplication)
- [ ] Complexity is reasonable (no deeply nested logic)

### ✓ Security
- [ ] OWASP Input Validation: All user inputs validated
- [ ] SQL Injection: Using parameterized queries throughout
- [ ] XSS Prevention: Vue templates use safe data binding
- [ ] Authentication: Role-based access control enforced
- [ ] Authorization: Permission checks in services and APIs

### ✓ Performance
- [ ] No N+1 queries (use eager loading or batch queries)
- [ ] Indexes created for frequently queried fields
- [ ] Unnecessary loops eliminated
- [ ] Caching strategy documented where applicable
- [ ] Response times align with planning expectations

### ✓ Testing
- [ ] Unit tests have 80%+ coverage for new code
- [ ] Tests cover happy path and error scenarios
- [ ] API contract tests validate request/response schemas
- [ ] Integration tests verify cross-service interactions
- [ ] Test names clearly describe what is being tested

### ✓ Documentation
- [ ] All public methods have JSDoc/KDoc comments
- [ ] Complex business logic has inline comments
- [ ] README updated with new features/services
- [ ] API endpoints documented with examples
- [ ] Commit messages follow Conventional Commits

### ✓ Database
- [ ] Migrations are reversible (include rollback SQL)
- [ ] Foreign key constraints preserve data integrity
- [ ] Indexes optimize query performance
- [ ] No unnecessary data duplication

### ✓ Configuration
- [ ] All configurable values use environment variables
- [ ] Default values are sensible for development
- [ ] Production configuration is secure
- [ ] Sensitive config not logged or exposed

---

## Agent Collaboration & Code Review

### Recommended Workflow

After generating code, follow this agent-driven review process:

**Phase 1: Generated Code Review**
```
generate.prompt.md → Generated Code File
                   ↓
          [HUMAN REVIEW: Quick scan for obvious errors]
                   ↓
          Implementation Agent (backend or frontend)
                   ↓
        [AUTOMATED: Full code quality review]
```

### Implementation Agents

**For Backend (Java/Spring Boot) - durion-positivity-backend:**
- **Relevant Agents:**
  - [Primary Software Engineer Agent](../agents/primary-software-engineer.agent.md) - Provides principal-level guidance and implementation.
  - [Universal Janitor Agent](../agents/janitor.agent.md) - For code cleanup and tech debt.
  - [Backend Testing Agent](../../durion-positivity-backend/.github/agents/test.agent.md) - For QA and test development.
  - [Spring Boot 3.x Strategic Advisor](../agents/springboot.agent.md) - For Spring Boot best practices.
  - [PostgreSQL Database Administrator](../agents/postgresql-dba.agent.md) - For database schema and performance.
  - [Database Administrator Agent](../agents/dba.agent.md) - For general database administration.
  - [API Gateway & OpenAPI Architect](../agents/api-gateway.agent.md) - For gateway and API architecture.
  - [Senior Software Engineer - REST API Agent](../agents/api.agent.md) - For REST API development.
  - [Security Agent](../../durion-positivity-backend/pos-agent-framework/README.md) - Security & compliance validation
  - [Observability Agent](../../durion-positivity-backend/pos-agent-framework/README.md) - Performance and reliability review
  - [Architecture Agent](../../durion-positivity-backend/pos-agent-framework/README.md) - Architectural alignment

- **Validation Checklist:**
  - Spring Boot annotations and dependency injection correctness
  - JPA entity relationships and constraints
  - REST controller request/response handling
  - Exception handling and error codes
  - Transaction management and data consistency
  - Security: @PreAuthorize, input validation, SQL injection prevention
  - Performance: N+1 queries, indexes, caching strategy
  - Test coverage: 80%+ for new code

**For Frontend (Vue/TypeScript + Groovy Services) - durion-moqui-frontend:**
- **Relevant Agents:**
  - [Primary Software Engineer Agent](../agents/primary-software-engineer.agent.md) - Provides principal-level guidance and implementation.
  - [moquiDeveloper-agent](.github/agents/moquiDeveloper-agent.md) - Moqui Framework & Groovy service patterns
  - [Vue Agent](.github/agents/vue-agent.md) - Vue 3 Composition API patterns
  - [TypeScript Agent](.github/agents/typescript-agent.md) - Type safety and TS best practices
  - [Quasar Agent](.github/agents/quasar-agent.md) - Quasar component usage
  - [Universal Janitor Agent](../agents/janitor.agent.md) - For code cleanup and tech debt.
  - [Frontend Testing Agent](../../durion-moqui-frontend/.github/agents/test.agent.md) - For QA and test development.
  - [API Gateway & OpenAPI Architect](../agents/api-gateway.agent.md) - For gateway and API architecture.
  - [Security Agent](../../durion-positivity-backend/pos-agent-framework/README.md) - Security & compliance validation
  - [Observability Agent](../../durion-positivity-backend/pos-agent-framework/README.md) - Performance and reliability review
  - [Architecture Agent](../../durion-positivity-backend/pos-agent-framework/README.md) - Architectural alignment

- **Validation Checklist:**
  - Vue 3 Composition API correctness (setup(), refs, computed, watchers)
  - TypeScript type safety: no `any` types, proper interfaces
  - Quasar component usage and theming
  - Groovy service naming: `{domain}.{service-type}#{Action}`
  - REST API calls via durion-positivity integration component
  - Component props/emits properly defined
  - Error handling and user feedback
  - Performance: API calls response time and efficiency, page load times
  - Test coverage: 80%+ for new components

### Using Implementation Agents

**In Copilot Chat:**
```
@moquiDeveloper-agent Review this generated code from generate.prompt.md 
for architectural alignment with Moqui Framework patterns.

@implementation-agent Review this generated Java/Spring Boot code 
for Java 21 and Spring Boot 3.2 best practices.

@vue-agent Review this Vue 3 code for Composition API correctness 
and TypeScript type safety.
```

**Output from Agent Review:**
1. Code quality feedback (style, naming, structure)
2. Security review findings
3. Performance recommendations
4. Test coverage gaps
5. Refactoring suggestions
6. Marked-up code with improvements

### Gate Before apply.prompt.md

**Only proceed to apply.prompt.md after:**
- ✓ Generated code reviewed (human scan for obvious errors)
- ✓ Implementation agent review completed
- ✓ Security audit passed (no vulnerabilities)
- ✓ Performance review approved (no anti-patterns)
- ✓ Test coverage validated (80%+ for new code)
- ✓ Architecture alignment confirmed (bounded contexts respected)
- ✓ All refactoring suggestions incorporated (or explicitly deferred)

If issues found:
- Regenerate code with corrections
- OR apply agent suggestions directly to generated output
- Re-validate before proceeding to apply.prompt.md

---

## Applying Code to Projects

### Step 1: Review Code Generation Output
1. Read through entire generated code file
2. Validate all acceptance criteria are addressed
3. Check for any syntax errors or logical issues
4. Ensure consistent with project conventions
5. **Submit to Implementation Agent for validation** (see Agent Collaboration section)

### Step 2: Create Feature Branch
```bash
git checkout develop
git pull origin develop
git checkout -b feature/{issue-number}-{domain}-{description}
```

### Step 3: Apply Code Files
For each file in the generated output:
1. Create file in correct location
2. Copy code from generation output (as validated by Implementation Agent)
3. Format and validate
4. Add to git: `git add {filepath}`

### Step 4: Run Quality Checks
```bash
# Backend (Java/Spring Boot)
cd durion-positivity-backend
./mvnw clean test -pl pos-{domain}

# Frontend (Groovy + Vue/TypeScript)
cd durion-moqui-frontend
npm run lint
npm run type-check
npm run test
```

### Step 5: Commit Changes
```bash
# Follow Conventional Commits format from planning with domain scope
git commit -m "feat({domain}): add new feature (closes #123)"
git commit -m "test({domain}): add tests for new feature"
git commit -m "docs({domain}): update README with new feature"
```

### Step 6: Push & Create PR
```bash
git push origin feature/{issue-number}-{domain}-{description}
# Open PR in GitHub
# Reference: apply.prompt.md for automated PR creation
```

---

## Usage

### In Copilot Chat

```
@copilot Use generate.prompt to generate all code for this planning document.

@moquiDeveloper-agent Review the generated code for Moqui patterns and best practices.

@implementation-agent Review the generated Java/Spring Boot code for correctness.

@vue-agent Review the generated Vue components for Composition API correctness.
```

### Command Line Workflow

1. Generate planning document from issue using `planning.prompt.md`
2. Review planning document for completeness
3. Feed planning document to `generate.prompt.md` → generates code output file
4. **Submit generated code to Implementation Agent(s) for validation**
   - Backend implementation: moquiDeveloper-agent OR implementation-agent
   - Frontend components: vue-agent + typescript-agent
5. **Address agent feedback** (refactor generated code as needed)
6. Apply validated code to projects following "Applying Code to Projects" steps
7. Run all tests and quality checks
8. Create PR using `apply.prompt.md` for automated branch/commit/PR creation

### Full Development Pipeline

```
Issue #42 (ready-for-dev, domain:crm)
    ↓
planning.prompt.md → Planning Document
    ↓
generate.prompt.md → Generated Code (single markdown file)
    ↓
[HUMAN REVIEW: Quick scan]
    ↓
Implementation Agent(s) Review
├─→ moquiDeveloper-agent (backend services/Groovy)
├─→ implementation-agent (Spring Boot Java)
├─→ vue-agent (Vue 3 components)
├─→ security-agent (security audit)
├─→ performance-agent (optimization review)
└─→ architecture-agent (bounded context alignment)
    ↓
[APPLY FEEDBACK to generated code]
    ↓
apply.prompt.md → Create branch, write files, commit, push, open PR
    ↓
[HUMAN REVIEW: Code review on PR]
    ↓
Merge & Deploy
```

---

## References

### Planning & Generation
- **Planning Document**: Output from [planning.prompt.md](planning.prompt.md)
- **Application**: Next step is [apply.prompt.md](apply.prompt.md)
- **Branching Strategy**: [.github/docs/governance/branching-strategy.md](../docs/governance/branching-strategy.md)

### Implementation Agents (for code validation)
**Backend (durion-positivity-backend)**:
- [moquiDeveloper-agent](.github/agents/moquiDeveloper-agent.md) - Moqui Framework implementation patterns
- [implementation-agent](../pos-agent-framework/README.md) - Spring Boot development patterns
- [Security Agent](../pos-agent-framework/README.md) - Security & compliance
- [Performance Agent](../pos-agent-framework/README.md) - Performance optimization
- [Architecture Agent](../pos-agent-framework/README.md) - Architectural decisions

**Frontend (durion-moqui-frontend)**:
- [vue-agent](.github/agents/vue-agent.md) - Vue.js 3 patterns
- [TypeScript Agent](.github/agents/typescript-agent.md) - Type safety
- [Quasar Agent](.github/agents/quasar-agent.md) - Component library

### Code Standards
- **Code Review**: [code-review-generic.instructions.md](.github/instructions/code-review-generic.instructions.md)
- **Security & OWASP**: [security-and-owasp.instructions.md](.github/instructions/security-and-owasp.instructions.md)
- **Performance**: [performance-optimization.instructions.md](.github/instructions/performance-optimization.instructions.md)
- **Backend Java**: [java.instructions.md](.github/instructions/java.instructions.md)
- **Frontend Vue**: [vuejs3.instructions.md](.github/instructions/vuejs3.instructions.md)
- **TypeScript**: [typescript-5-es2022.instructions.md](.github/instructions/typescript-5-es2022.instructions.md)

### Project Copilot Instructions
- [durion-moqui-frontend](../durion-moqui-frontend/.github/copilot-instructions.md)
- [durion-positivity-backend](../durion-positivity-backend/.github/copilot-instructions.md)
