---
name: 'Feature Development Orchestration & Lifecycle Planning'
agent: 'agent'
description: 'Orchestrate the complete feature development lifecycle: analyze ready-for-development stories, update architecture documentation, build features, document changes, test, and deploy. Guides end-to-end workflow from issue to production.'
model: 'Claude Sonnet 4.5 (copilot)'
---

# Feature Development Orchestration & Lifecycle Planning

## Goal

Act as a senior Technical Project Lead and Feature Architect. Your role is to take a story marked with `status:ready-for-development` and produce a **comprehensive, implementation-ready planning document** that a code generator can consume to build the feature.

**Output a structured planning file** (in YAML/Markdown) that includes:
- Complete feature specifications and acceptance criteria
- Architecture decisions and integration points
- Detailed code generation requirements (modules, services, entities, APIs, UI components)
- Database schema and migration requirements
- Testing strategy and test requirements
- Deployment and monitoring requirements

This planning document becomes the input for a code generator prompt that will implement the actual code changes.

## Workflow Overview

```
Issue Review & Specification Validation
    ↓
Architecture Assessment & Design
    ↓
Detailed Code Requirements Analysis
    ↓
Generate Planning Document
    ↓
Output: Implementation-Ready Planning File
```

The output is a single, structured planning document that contains everything needed for code generation.

---

## Phase 1: Issue Review & Specification Validation

### Input Requirements

**Ready-for-Development Story** must include:
- Clear user story format: "As a [user], I want to [capability], so that [benefit]"
- Acceptance criteria (at least 3-5)
- Business context and priority
- Related issues and dependencies (blocked by / blocks)
- **Required Labels**: 
  - `status:ready-for-development` (workflow status)
  - `domain:{domain}` (single domain scope - e.g., `domain:crm`, `domain:accounting`, `domain:inventory`)
  - `type:story` or `type:feature` (issue type)

**Domain Label Requirement**:
Each story MUST have exactly **one** `domain:*` label. This ensures focused, single-domain development and clear bounded context ownership.

**Available Domain Labels**:
- `domain:crm` - Customer Relationship Management
- `domain:accounting` - Financial Accounting, GL, AR/AP
- `domain:inventory` - Inventory Management, Stock Levels, Locations
- `domain:billing` - Billing, Invoicing, Revenue Recognition
- `domain:payment` - Payment Processing, Gateways, Transactions
- `domain:order` - Order Management, Fulfillment
- `domain:product` - Product Catalog, Pricing
- `domain:workexec` - Work Execution, Shop Operations
- `domain:shipping` - Shipping, Logistics, Carriers
- `domain:platform` - Cross-cutting platform features, security, observability

### Steps

1. **Read the GitHub issue completely**: Title, description, acceptance criteria, comments, linked issues, and **labels** (especially `domain:*`).
2. **Validate specification completeness**:
   - ✓ User story is clear and testable
   - ✓ Acceptance criteria are unambiguous
   - ✓ **Exactly ONE `domain:*` label is present** (e.g., `domain:crm`)
   - ✓ No blocking dependencies remain open
   - ✓ Story size is reasonable (not an epic disguised as a story)
   - ✓ Story scope is limited to the single labeled domain
3. **Extract domain context**: Identify the primary bounded context from the `domain:*` label. This determines which modules/components will be modified.
4. **Identify any gaps**: If specifications are incomplete or domain is unclear, flag them and request clarification before proceeding.
5. **Document pre-conditions**: Note what must exist for this story to succeed (APIs, entities, services, permissions) **within the target domain**.

### Decision Gate

- **Proceed**: All requirements clear, dependencies resolved → Advance to Phase 2.
- **Blocked**: Missing clarity or open blockers → Request updates via issue comment; pause workflow.
- **Refactor**: Story is too large → Suggest breaking into smaller stories; pause workflow.

---

## Phase 2: Architecture Assessment & Documentation Update

### Goals

- Ensure the feature aligns with system architecture and bounded contexts
- Update architecture docs to reflect new components/patterns
- Identify integration points and cross-cutting concerns

### Steps

1. **Map to System Architecture (Domain-Focused)**:
   - **Primary Bounded Context**: Identify from the `domain:*` label (e.g., `domain:crm` → CRM bounded context)
   - **Single-Domain Constraint**: This story should ONLY modify code within the primary domain's bounded context
   - **Integration Points**: If the story requires cross-domain integration, identify:
     - Which other domains are involved?
     - What integration patterns apply? (REST APIs, domain events, shared data)
     - Are cross-domain contracts stable, or do they need updates?
   - **Backend Module**: Map domain to backend module (e.g., `domain:crm` → `pos-crm` service)
   - **Frontend Component**: Map domain to frontend component (e.g., `domain:crm` → `durion-crm` component)

2. **Validate Domain Boundaries**:
   - Ensure the story respects bounded context boundaries
   - If the story spans multiple domains, flag it as potentially requiring breakdown
   - Confirm that cross-domain dependencies use well-defined integration patterns (not direct DB access)

3. **Review Existing ADRs & Patterns**:
   - Check [docs/adr/](../../docs/adr/README.md) for domain-specific architectural decisions
   - Review domain-specific documentation in component READMEs (e.g., `pos-crm/README.md`, `durion-crm/README.md`)
   - Confirm alignment with DDD principles and established patterns for this domain

3. **Update Architecture Documentation** (if needed):
   - Create or update architecture documentation under [.github/docs/architecture/](../.github/docs/architecture/) if this story introduces new patterns, services, or major integrations
   - Document new entities, services, or APIs in the appropriate component README
   - Update C4 diagrams or architecture overview if the feature significantly changes system structure

4. **Document Integration Points**:
   - API contracts (endpoints, request/response schemas)
   - Database entities and migrations
   - Event schemas (if event-driven)
   - Security/authorization rules
   - External service dependencies

### Decision Gate

- **Architecture Aligned**: Documentation updated, no conflicts → Advance to Phase 3.
- **Architecture Mismatch**: Feature conflicts with existing decisions → Propose ADR or architectural adjustment; pause.
- **New Pattern Needed**: Existing patterns don't fit; propose new pattern via ADR.

---

## Phase 3: Detailed Code Requirements Analysis

### Goals

- Define exactly what code needs to be generated
- Specify integration points, APIs, and data contracts
- Provide implementation details without writing the code itself
- Create a "blueprint" for the code generator

### Steps

1. **Branching & Code Integration** (Foundational):
   - Determine which branch to work on following [.github/docs/governance/branching-strategy.md](../../.github/docs/governance/branching-strategy.md)
   - **Identify domain-specific feature branch name**: `feature/{issue-number}-{domain}-{kebab-case-description}`
     - Example: `feature/42-crm-customer-context-loading` for `domain:crm`
     - Example: `feature/108-inventory-atp-calculation` for `domain:inventory`
   - Confirm PR workflow: destination branch (develop or main), required reviewers, merge strategy
   - Verify commit message conventions: Follow Conventional Commits format with **domain scope**
     - Example: `feat(crm): add customer context lazy loading`
     - Example: `fix(inventory): correct ATP calculation for reserved items`

2. **Backend Requirements** (if applicable):
   - **Services**: List each service that needs to be created/modified
     - Service name: `{domain}.{service-type}#{Action}`
     - Input parameters and types
     - Output/return types
     - Business logic summary
   - **Entities/Models**: Define each entity
     - Field names, types, constraints
     - Relationships to other entities
     - Validation rules
   - **REST APIs**: Define endpoints
     - HTTP method, path, version
     - Request schema (JSON)
     - Response schema (JSON)
     - Error codes and messages
     - Authorization rules (role-based)
   - **Events**: If event-driven
     - Event name and type
     - Event payload structure
     - Handlers that listen to this event
   - **Database Migrations**: SQL scripts or migration steps needed

2. **Frontend Requirements** (if applicable):
   - **Vue Components**: List each component
     - Component name and path
     - Props (inputs) and emits (outputs)
     - Template structure (high-level)
     - State/reactive data
   - **Screens/Pages**: Define each screen
     - URL/route
     - Components used
     - Data fetching (API calls)
     - User interactions and handlers
   - **Styling**: Design tokens, Quasar components used
   - **TypeScript Interfaces**: Type definitions for API responses
   - **Store/State Management**: Vuex/Pinia state structure if needed

3. **Integration Requirements**:
   - **Cross-Domain**: How does this feature interact with other bounded contexts?
   - **External Services**: Any third-party API calls?
   - **Message Queues**: Any async messaging (Kafka, RabbitMQ)?
   - **Caching**: Where should caching be applied?

4. **Data Flow Diagram**:
   - Create a clear diagram showing how data flows through the feature
   - Show request/response cycles
   - Show event flows (if applicable)
   - Show database interactions

### Decision Gate

- **Requirements Complete**: All code generation requirements specified → Advance to Phase 4.
- **Gaps**: Missing implementation details → Clarify before proceeding.

---

## Phase 4: Generate Planning Document

### Output Format

Generate a **planning file** saved to a location the user specifies (or default: `/docs/ways-of-work/plan/{domain}/{feature-name}/planning.md`).

### Planning Document Structure

```yaml
---
title: "{Feature Name} - Implementation Planning Document"
issue: "#{issue-number}"
domain:
  name: "{domain-slug}"  # e.g., "crm", "inventory", "accounting"
  label: "domain:{domain-slug}"  # The actual GitHub label
  bounded_context: "{Bounded Context Name}"  # e.g., "Customer Relationship Management"
  backend_module: "pos-{domain}"  # e.g., "pos-crm"
  frontend_component: "durion-{domain}"  # e.g., "durion-crm"
status: "ready-for-generation"
generated-date: "{date}"
branch:
  name: "feature/{issue-number}-{domain}-{description}"
  base: "develop"
  strategy: "feature-branch"
---

# {Feature Name} Implementation Plan

## 1. Feature Overview

### User Story
{Full user story with as/want/so-that}

### Business Value
{Why this feature matters to the business}

### Acceptance Criteria
{List of numbered acceptance criteria from the issue}

---

## 2. Architecture & Design

### Primary Bounded Context
**Domain**: {domain-name} (from `domain:{domain}` label)  
**Backend Module**: `pos-{domain}/`  
**Frontend Component**: `runtime/component/durion-{domain}/`  
**Ownership**: {Team/domain expert}  

**Description**: {Brief description of this domain's bounded context}  

### Integration with Other Bounded Contexts (if applicable)
- **{Context 1}**: {Integration pattern - REST API / Domain Events / Shared Data}
  - Why: {Reason for integration}
  - Contract: {API endpoint or event schema}
- **{Context 2}**: {Integration pattern}
  - Why: {Reason for integration}
  - Contract: {API endpoint or event schema}

### System Architecture
{Mermaid or text diagram showing how this feature integrates}

### Related ADRs & Decisions
- [ADR-XXXX: {Decision}](../../adr/XXXX-decision.md)
- {Reference other existing architectural decisions}

### Integration Points
| System | Type | Details |
|--------|------|---------|
| {System 1} | {API/Event/DB} | {Brief description} |
| {System 2} | {API/Event/DB} | {Brief description} |

---

## 3. Backend Implementation (if applicable)

### 3.1 Services

#### Service: {domain}.{service-type}#{Action}
**Purpose**: {Brief description of what this service does}

**Input Parameters**:
```typescript
{
  paramName: "type | description",
  paramName2: "type | description"
}
```

**Output/Return**:
```typescript
{
  returnField: "type | description",
  status: "SUCCESS | FAILED"
}
```

**Business Logic**:
- {Step 1}
- {Step 2}
- {Error handling}

**Authorization**: {Role-based access requirements}

---

### 3.2 Entities & Data Model

#### Entity: {EntityName}
**Purpose**: {What this entity represents}

**Fields**:
| Field | Type | Required | Unique | Default | Constraints |
|-------|------|----------|--------|---------|-------------|
| id | UUID | ✓ | ✓ | generated | PK |
| fieldName | type | ✓/○ | ○ | value | constraint |
| createdAt | timestamp | ✓ | ○ | NOW() | |

**Relationships**:
- `belongsTo`: {Related Entity} via `field_id`
- `hasMany`: {Related Entity}

**Validation Rules**:
- {Constraint 1}
- {Constraint 2}

**Database Indexes**:
- `idx_fieldName`: ON (field_name) for query performance

---

### 3.3 REST APIs

#### Endpoint: {METHOD} /rest/api/v{version}/{resource}/{id}/{action}
**Purpose**: {What this endpoint does}

**Request**:
```json
{
  "paramName": "type | description",
  "nested": {
    "field": "type"
  }
}
```

**Response (Success - 200)**:
```json
{
  "id": "uuid",
  "status": "SUCCESS",
  "data": {
    "field": "value"
  }
}
```

**Response (Error - 400/401/403/404/500)**:
```json
{
  "status": "FAILED",
  "code": "ERROR_CODE",
  "message": "Human-readable error message"
}
```

**Authorization**: {Role/permission required}

**Validation Rules**:
- Field X must match pattern {pattern}
- Field Y must be > 0

---

### 3.4 Domain Events

#### Event: {DomainEventName}
**Trigger**: {When/why this event is emitted}

**Payload**:
```json
{
  "eventId": "uuid",
  "eventType": "com.durion.domain.DomainEventName",
  "timestamp": "ISO-8601",
  "aggregateId": "uuid",
  "aggregateType": "{EntityType}",
  "data": {
    "field": "value"
  }
}
```

**Handlers/Subscribers**:
- {Service 1} listens and {action}
- {Service 2} listens and {action}

---

### 3.5 Database Migrations

**Migration File**: `{timestamp}_create_or_alter_{entity_name}.sql`

**SQL Script**:
```sql
-- Create table or alter existing
CREATE TABLE IF NOT EXISTS {table_name} (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  field_name type NOT NULL,
  CONSTRAINT constraint_name CHECK (condition)
);

-- Create indexes
CREATE INDEX idx_field_name ON {table_name}(field_name);
```

**Rollback Script**:
```sql
DROP TABLE IF EXISTS {table_name};
```

---

## 4. Frontend Implementation (if applicable)

### 4.1 Vue Components

#### Component: {ComponentName}.vue
**Path**: `{runtime/component/durion-xxx/webapp/...}`

**Purpose**: {What this component displays/does}

**Props**:
```typescript
interface Props {
  propName: string | number;
  required?: boolean;
}
```

**Emits**:
```typescript
type Emits = {
  eventName: [payload: PayloadType];
  update: [value: ValueType];
};
```

**Template Structure** (high-level):
```
<template>
  <q-card>
    <header>...</header>
    <body>
      <q-input v-model="form.field" @input="onFieldChange" />
      <q-btn @click="submitForm" />
    </body>
  </q-card>
</template>
```

**Reactive State**:
```typescript
const form = reactive({
  field1: "",
  field2: 0
});
```

**Hooks & Lifecycle**:
- onMounted: {Initialize component}
- watch(field, () => {...}): {React to changes}

**Methods**:
- `submitForm()`: {Description}
- `onFieldChange()`: {Description}

---

#### Screen/Page: {ScreenName}
**Path**: `{screen path}`

**Route**: `/path/to/screen`

**Components Used**:
- {ComponentName}
- {ComponentName}

**Data Fetching**:
```typescript
// On mount, fetch data from API
GET /rest/api/v1/resource
Stores result in: store.{domain}.{dataName}
```

**User Interactions**:
- Click "Save" button → calls {service method} → emits success/error

### 4.2 TypeScript Interfaces

```typescript
interface {EntityName} {
  id: string;
  field: type;
}

interface API{Action}Request {
  param: type;
}

interface API{Action}Response {
  status: "SUCCESS" | "FAILED";
  data?: {EntityName};
  error?: string;
}
```

---

## 5. Testing Strategy

### 5.1 Unit Tests

#### Backend Services
- Test happy path: {description}
- Test error scenarios: {description}
- Test authorization: {description}

**Coverage Target**: 80%+

#### Frontend Components
- Test rendering: {description}
- Test user interactions: {description}
- Test prop changes: {description}

**Coverage Target**: 70%+

### 5.2 Integration Tests

- **API Contract Tests**: Verify {endpoint} returns correct schema
- **Database Tests**: Verify {entity} is persisted correctly
- **Component Integration**: Verify {component} integrates with API
- **Event Integration**: Verify {event} is emitted and handled correctly

### 5.3 Acceptance Criteria Tests

| Acceptance Criterion | Test Type | Test Case |
|---------------------|-----------|-----------|
| Criterion 1 | E2E | {Test description} |
| Criterion 2 | API | {Test description} |
| Criterion 3 | Component | {Test description} |

---

## 6. Cross-Cutting Concerns

### 6.1 Security

- **Authentication**: {What auth is required? (JWT, session, etc.)}
- **Authorization**: {Role-based access control rules}
- **Input Validation**: {What inputs need validation? How?}
- **Data Protection**: {Any PII or sensitive data handling?}

### 6.2 Performance

- **Query Optimization**: {Any N+1 queries to avoid? Indexes needed?}
- **Caching**: {What should be cached? TTL?}
- **Response Times**: {Expected latency for APIs?}

### 6.3 Observability & Monitoring

- **Logging**: {What events/errors should be logged?}
- **Metrics**: {What metrics should be emitted? (counters, gauges, histograms)}
- **Alerting**: {What should trigger alerts?}

---

## 7. Documentation Requirements

### 7.1 Code Documentation
- JSDoc/KDoc comments on all services and APIs
- Inline comments explaining complex business logic
- README updates if new component/service is created

### 7.2 Architecture Documentation
- Update {component} README if new entities/services added
- Update [.github/docs/architecture/](../../.github/docs/architecture/) if new patterns
- Create ADR if major architectural decision made

### 7.3 User Documentation
- User guide or help text for new feature
- API documentation (if new REST endpoints)

---

## 8. Deployment & Rollout

### 8.1 Deployment Strategy
{blue-green / canary / rolling / direct}

### 8.2 Backward Compatibility
- {Breaking change 1? Mitigation?}
- {Database schema changes? Migration strategy?}

### 8.3 Rollback Plan
- {Steps to rollback if deployment fails}
- {Data recovery if needed}

---

## 9. Acceptance & Sign-Off

### Issue Link
{Link to GitHub issue}

### Acceptance Criteria Mapping
- [ ] Criterion 1 → Implemented in {component/service}
- [ ] Criterion 2 → Implemented in {API endpoint}
- [ ] Criterion 3 → Implemented in {component}

### Ready for Code Generation
✓ All sections complete and validated
✓ No architectural blockers
✓ All dependencies identified
✓ Specifications are unambiguous

---
```

---

## Cross-Cutting Concerns

### Security

- **Authentication/Authorization**: Verify role-based access is enforced
- **Data Privacy**: PII and sensitive data are protected
- **Input Validation**: All user inputs are validated and sanitized
- **Dependencies**: No known CVEs in added dependencies

Reference: [.github/instructions/security-and-owasp.instructions.md](../.github/instructions/security-and-owasp.instructions.md)

### Performance

- **Query Performance**: No N+1 queries; indexes in place
- **Response Times**: API endpoints respond < 500ms (95th percentile)
- **Resource Usage**: Memory and CPU within expected bounds
- **Caching**: Appropriate use of caching for repeated operations

Reference: [.github/instructions/performance-optimization.instructions.md](../.github/instructions/performance-optimization.instructions.md)

### Testing

- **Coverage**: 80%+ for new code
- **Test Types**: Unit, integration, and end-to-end tests
- **Flakiness**: Tests are deterministic; no flaky/intermittent failures

Reference: [durion-positivity-backend/.github/agents/test.agent.md](../../../durion-positivity-backend/.github/agents/test.agent.md)

### Documentation

- **Code Comments**: Complex logic explained; no obvious comments
- **README**: Updated with new features, services, or APIs
- **Architecture Docs**: ADRs and system design kept current

Reference: [.github/agents/docs.agent.md](../agents/docs.agent.md)

---

## Agent Collaboration Framework

This planning prompt coordinates with the code generation prompt. The output planning document is consumed by:

| Generator | Purpose |
|-----------|---------|
| `feature-implementation-generator.prompt.md` | Generates backend services, entities, APIs, and migrations |
| `frontend-component-generator.prompt.md` | Generates Vue components, screens, and TypeScript interfaces |
| `test-generator.prompt.md` | Generates unit and integration tests |

The planning document is **self-contained** and provides all context needed for code generation without requiring the generator to reference the original issue.

---

## Usage

### As a Copilot Chat Prompt

In GitHub Copilot Chat, use this prompt to generate a planning document:

```
@copilot Use the planning.prompt with issue #123 to generate a comprehensive 
implementation planning document ready for code generation.
```

### Manual Process

1. Provide the GitHub issue details (title, description, acceptance criteria)
2. Follow the four-phase workflow to build the planning document
3. Output the planning file to the specified location
4. Feed the planning file to a code generator prompt for implementation

---

## Planning Document Output Checklist

Before finalizing the planning document, verify:

- [ ] **Domain label verified**: Exactly one `domain:*` label present on issue
- [ ] **Single-domain scope confirmed**: Story modifies only one bounded context
- [ ] **Domain-to-module mapping complete**: Backend (`pos-{domain}`) and frontend (`durion-{domain}`) modules identified
- [ ] Branching strategy identified following [.github/docs/governance/branching-strategy.md](../../.github/docs/governance/branching-strategy.md)
- [ ] Feature branch name determined: `feature/{issue-number}-{domain}-{description}`
- [ ] PR destination branch identified (develop or main)
- [ ] Commit message scope includes domain: `feat({domain}): description`
- [ ] All acceptance criteria are addressed in the plan
- [ ] Backend requirements are detailed (services, entities, APIs, events, migrations)
- [ ] Frontend requirements are detailed (components, screens, types, interfaces)
- [ ] Integration points are clearly documented
- [ ] Data flow diagram is provided
- [ ] Testing strategy covers all acceptance criteria
- [ ] Cross-cutting concerns (security, performance, observability) are addressed
- [ ] Documentation requirements are specified
- [ ] Deployment strategy is defined
- [ ] Commit message format planned (Conventional Commits)
- [ ] No architectural conflicts identified
- [ ] All required context for code generation is present

---

## References

- **Project Structure**: See [durion/.github/copilot-instructions.md](../.github/copilot-instructions.md)
- **Architecture Decisions**: See [docs/adr/](../../docs/adr/README.md)
- **Code Conventions**: See [.github/instructions/](../.github/instructions/)
- **Agent Framework**: See [.github/agents/](../.github/agents/)
- **Related Prompts**: 
  - `breakdown-plan.prompt.md` - Detailed project planning
  - `breakdown-test.prompt.md` - Comprehensive test strategy
  - `create-architectural-decision-record.prompt.md` - ADR creation
