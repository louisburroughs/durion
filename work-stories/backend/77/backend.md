Title: [BACKEND] [STORY] Locations: Create Bays with Constraints and Capacity
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/77
Labels: type:story, domain:location, status:ready-for-dev, agent:story-authoring, agent:location

**Rewrite Variant:** inventory-flexible

## Story Intent
**As a** Shop Administrator,
**I want to** define and manage service bays within a location, including their capabilities, constraints, capacity, and operational status,
**so that** scheduling and dispatch can assign work to an appropriate and available bay and avoid incorrect assignments.

## Actors & Stakeholders
- **Shop Administrator**: Configures bays and maintains bay status/constraints.
- **Scheduler/Dispatch (Consumer)**: Queries bays (and constraints) to select suitable bays for jobs.
- **Service Advisor / Shop Manager**: Uses accurate bay configuration to manage throughput and customer expectations.
- **Technician**: Relies on the assigned bay having the required equipment/capabilities.

## Preconditions
- Parent `Location` exists.
- Shop Administrator is authenticated and authorized to manage bays for the target location.
- Service Catalog domain and People/Skills domain provide authoritative IDs for services/capabilities and skills.

## Functional Behavior

### API Endpoints (Contract)
- `POST /locations/{locationId}/bays`
  - Create a new bay under the location.
  - Returns `201 Created` with the full bay payload.
- `GET /locations/{locationId}/bays`
  - List bays for admin use.
  - Supports filters: `status` (e.g., `ACTIVE`, `OUT_OF_SERVICE`), `bayType`.
- `GET /locations/{locationId}/bays?status=ACTIVE`
  - Scheduler/dispatch query shape: returns only ACTIVE bays.
- `GET /locations/{locationId}/bays/{bayId}`
  - Get bay details including constraints/capacity.
- `PATCH /locations/{locationId}/bays/{bayId}`
  - Update mutable fields (see Business Rules).

### Create Bay
- The Shop Administrator submits required bay details and optional constraints.
- The System validates:
  - Required fields are present.
  - Location exists.
  - Constraint references (service IDs / skill IDs) exist in their authoritative domains.
- If valid, the System persists the Bay and its constraint relationships.

### Update Bay
- The Shop Administrator submits a partial update.
- The System validates updates and persists changes.

### Availability Semantics
- A bay with `status=OUT_OF_SERVICE` is not considered available for new work assignments.
- Scheduler/dispatch must filter for available bays using `status=ACTIVE`.

## Alternate / Error Flows
- **Duplicate Bay Name in Location**: creating or renaming a bay to a name that already exists within the same location is rejected with `409 Conflict`.
- **Invalid Location**: creating/updating a bay under a non-existent location returns `404 Not Found`.
- **Missing/Invalid Fields**: validation failures return `400 Bad Request` with field-level errors.
- **Unknown Constraint References**: if any referenced `supportedServiceIds` or `skillId` values do not exist, return `400 Bad Request` including the invalid IDs.
- **Unauthorized**: not authenticated → `401`; authenticated but not authorized → `403`.

## Business Rules
- **Name uniqueness**: bay `name` must be unique within its parent `Location` (case-insensitive recommended).
- **Status**:
  - `ACTIVE` or `OUT_OF_SERVICE`.
  - `OUT_OF_SERVICE` bays must be excluded from availability queries.
- **Bay type**: fixed enum in v1 (see Resolved Questions).
- **Constraints are references (no free-text)**:
  - `supportedServiceIds[]` reference Service Catalog domain entities.
  - `requiredSkillRequirements[]` reference People/Skills domain entities (`{ skillId, minLevel? }`).
- **Capacity**:
  - Stored as a structured object with required `maxConcurrentVehicles` and optional physical constraints.
  - Only `maxConcurrentVehicles` is enforced in v1; other fields are stored for future/advisory use.
- **Mutability**:
  - Mutable: `name`, `status`, `bayType`, `capacity`, `supportedServiceIds`, `requiredSkillRequirements`.
  - Immutable: `bayId`, `locationId`, `createdAt`.

## Data Requirements

### Bay
| Field | Type | Constraints |
| --- | --- | --- |
| `bayId` | UUID | PK |
| `locationId` | UUID | not null (FK to Location) |
| `name` | String | not null, unique per `locationId` (case-insensitive recommended) |
| `bayType` | Enum | not null |
| `status` | Enum | not null (`ACTIVE`, `OUT_OF_SERVICE`), default `ACTIVE` |
| `capacity` | JSON / JSONB | must include `maxConcurrentVehicles` |
| `createdAt` | Timestamp | not null |
| `updatedAt` | Timestamp | not null |

### Constraint relationships
- `bay_service_capability(bay_id, service_id)`
- `bay_skill_requirement(bay_id, skill_id, min_level)`

## Acceptance Criteria

### Scenario 1: Create Bay (Happy Path)
- Given a location exists and I am authorized to manage bays
- When I call `POST /locations/{locationId}/bays` with a valid name, bayType, status, and capacity
- Then the system returns `201 Created` and the bay is associated with the location

### Scenario 2: Reject Duplicate Bay Name Within Location
- Given a bay named "Alignment Rack 1" exists under the location
- When I create or rename another bay under that same location to "Alignment Rack 1"
- Then the system returns `409 Conflict`

### Scenario 3: OUT_OF_SERVICE Bays Are Not Returned For Availability
- Given a bay has `status=OUT_OF_SERVICE`
- When Scheduler calls `GET /locations/{locationId}/bays?status=ACTIVE`
- Then the response does not include that bay

### Scenario 4: Constraints Are Returned Via API
- Given a bay is configured with `supportedServiceIds` and `requiredSkillRequirements`
- When I call `GET /locations/{locationId}/bays/{bayId}`
- Then the response includes the constraints and capacity

### Scenario 5: Reject Unknown Service/Skill References
- Given I submit `supportedServiceIds` or `skillId` values that do not exist
- When I create/update a bay
- Then the system returns `400 Bad Request` including which IDs were invalid

## Audit & Observability
- Audit create/update/status change for Bay resources and include: `bayId`, `locationId`, action, timestamp, actor identity, and before/after (diff preferred).
- Metrics:
  - Bay count per location (gauge or derivable at scrape time)
  - Bay count by status (`ACTIVE`, `OUT_OF_SERVICE`)
- Standard HTTP metrics for latency, error rate, and request volume.

## Open Questions (if any)
- None.

## Resolved Questions

From Clarification Issue #260, the following answers were incorporated:

### RQ1: Source of Truth for Constraints
Constraints are foreign-key references (authoritative IDs from Service Catalog and People/Skills domains), not free-text.

### RQ2: Capacity Structure
`capacity` is a structured object; `maxConcurrentVehicles` is required and enforced in v1.

### RQ3: Bay Type Enum
Fixed enum in v1:
- `GENERAL_SERVICE`
- `ALIGNMENT`
- `TIRE_SERVICE`
- `HEAVY_DUTY`
- `INSPECTION`
- `WASH_DETAIL`

## Original Story (Unmodified – For Traceability)
# Issue #77 — [BACKEND] [STORY] Locations: Create Bays with Constraints and Capacity

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Locations: Create Bays with Constraints and Capacity

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want to define bays with constraints (lift/equipment) so that scheduler assigns the right work to the right bay.

## Details
- Bay attributes: type (lift/alignment), supported services, capacity, required skills, status (active/out-of-service).

## Acceptance Criteria
- Bay created under a location.
- Out-of-service blocks assignments.
- Constraints queryable.

## Integrations
- Dispatch validates assignments against bay constraints.
- Workexec displays bay context during execution.

## Data / Entities
- Resource(Bay), ResourceConstraint, ResourceStatus

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*