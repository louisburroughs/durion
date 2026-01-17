Title: [BACKEND] [STORY] Location: Create/Update Location (pos-location) Including Timezone
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/87
Labels: type:story, domain:location, status:needs-review, status:ready-for-dev

STOP: Conflicting domain guidance detected
STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- status:needs-review
- blocked:domain-conflict
- blocked:clarification

### Recommended
- agent:location
- agent:people
- agent:story-authoring

### Blocking / Risk
- blocked:domain-conflict
- blocked:clarification

**Rewrite Variant:** integration-conservative

---
## ⚠️ Domain Conflict Summary
- **Candidate Primary Domains:** `domain:location`, `domain:people`
- **Why conflict was detected:** The story's primary action (Create/Update Location) belongs to the `domain:location`. However, a key acceptance criterion ("Inactive locations prevent new staffing assignments") defines enforcement logic that belongs to a consuming domain, such as `domain:people` or a scheduling service. This couples the implementation and testing of two distinct domains into a single story.
- **What must be decided:**
    1. Which domain is responsible for the full scope of this story as written?
    2. Should the cross-domain enforcement rule be defined in a separate story owned by the consuming domain (e.g., `domain:people`)?
- **Recommended split:** Yes. This story should be split.
    1. **Story 1 (domain:location):** Focus exclusively on the CRUD (Create, Read, Update, Delete) operations for the `Location` entity, including managing its `status` field.
    2. **Story 2 (domain:people/workexec):** Focus on the business logic of *consuming* location data, including how to handle `INACTIVE` locations when creating staffing assignments or work orders.

---

## Story Intent
As an Admin, I need to create and manage the lifecycle of business locations, so that dependent systems like scheduling, staffing, and timekeeping have an accurate, authoritative source for site-specific context, particularly timezones and operational status.

## Actors & Stakeholders
- **Admin:** A user with permissions to create and manage business locations.
- **System (Location Service):** The microservice responsible for owning and managing the `Location` entity as the system of record.
- **Stakeholders (People Service, Work Execution Service):** Downstream systems that consume location data to perform their functions (e.g., scheduling staff, assigning work orders). These systems rely on the accuracy and status of location data.

## Preconditions
- The Admin user is authenticated and authorized with `location:manage` permissions.
- The system's infrastructure (database, service runtime) is operational.

## Functional Behavior
### 1. Create a New Location
- **Trigger:** An Admin submits a `POST` request to the `/v1/locations` endpoint with valid location data.
- **Behavior:**
    - The system validates the request payload against the data requirements.
    - It verifies that the provided `code` is unique across all locations.
    - It ensures the `timezone` is a valid IANA Time Zone Database name (e.g., "America/New_York").
    - If a `parentLocationId` is provided, it validates that the parent location exists.
    - The system persists a new `Location` record in the database with a default status of `ACTIVE`.
    - It returns a `201 Created` response containing the full representation of the newly created location, including the system-generated `locationId`.
- **Outcome:** A new location is created and available for use by other systems.

### 2. Update an Existing Location
- **Trigger:** An Admin submits a `PUT` request to the `/v1/locations/{locationId}` endpoint with valid location data.
- **Behavior:**
    - The system locates the existing location by its `locationId`.
    - It validates the request payload. The `code` field cannot be changed after creation.
    - The system updates the specified fields (e.g., `name`, `address`, `timezone`, `status`, `parentLocationId`) for the location.
    - It returns a `200 OK` response with the full, updated representation of the location.
- **Outcome:** An existing location's attributes are updated. Changing the status to `INACTIVE` signals to consuming systems that it should not be used for new operational activities.

## Alternate / Error Flows
- **Invalid Data:** If required fields are missing or data formats are incorrect (e.g., invalid timezone string), the system rejects the request with a `400 Bad Request` and a descriptive error message.
- **Duplicate Location Code:** If an attempt is made to create a location with a `code` that already exists, the system rejects the request with a `409 Conflict`.
- **Location Not Found:** If an update request is made for a `locationId` that does not exist, the system returns a `404 Not Found`.
- **Unauthorized Access:** If the user lacks the required permissions, the system returns a `403 Forbidden`.

## Business Rules
- A `Location.code` must be unique and is immutable after creation.
- A `Location.timezone` must be a valid IANA Time Zone Database name.
- The `parentLocationId`, if provided, must reference the `locationId` of an existing Location.
- **[CONFLICT]** The business rule "Inactive locations prevent new staffing assignments" is a cross-domain concern. The `Location Service` is responsible for setting the `status`, but the enforcement of this rule belongs in the consuming service (e.g., `People Service`).

## Data Requirements
### `Location` Entity
| Field              | Type                | Constraints                                     | Description                                                    |
| ------------------ | ------------------- | ----------------------------------------------- | -------------------------------------------------------------- |
| `locationId`       | UUID                | Primary Key, Not Null                           | Unique system-generated identifier for the location.           |
| `code`             | String              | Not Null, Unique, Immutable                     | A user-defined, human-readable unique code for the location.   |
| `name`             | String              | Not Null                                        | The display name of the location.                              |
| `address`          | JSONB / Object      | -                                               | Structured address data (street, city, state, postal code).    |
| `timezone`         | String              | Not Null, IANA Format                           | The IANA timezone name (e.g., "Europe/London").                |
| `status`           | Enum (`ACTIVE`, `INACTIVE`) | Not Null, Default: `ACTIVE`               | The operational status of the location.                        |
| `parentLocationId` | UUID                | Nullable, Foreign Key -> `Location.locationId`  | The ID of the parent location, for hierarchical structures.    |
| `createdAt`        | Timestamp           | Not Null                                        | Timestamp of when the record was created.                      |
| `updatedAt`        | Timestamp           | Not Null                                        | Timestamp of when the record was last updated.                 |

## Acceptance Criteria
### Scope: `domain:location` (Recommended for this Story)
1.  **Given** an authorized Admin
    **When** they submit a `POST` request to `/v1/locations` with a valid name, a unique code, and a valid timezone
    **Then** the system shall create a new location, assign it a unique `locationId`, set its `status` to `ACTIVE`, and return a `201 Created` response with the new location's data.

2.  **Given** an existing location with `locationId` "loc-123"
    **When** an authorized Admin submits a `PUT` request to `/v1/locations/loc-123` to change the `status` to `INACTIVE`
    **Then** the system shall update the location's status to `INACTIVE` and return a `200 OK` response with the updated location data.

3.  **Given** a location with the code "MAIN-WH" already exists
    **When** an authorized Admin submits a `POST` request to create a new location with the code "MAIN-WH"
    **Then** the system shall reject the request and return a `409 Conflict` error.

4.  **When** an authorized Admin submits a request to update a location with a non-existent `locationId` "loc-999"
    **Then** the system shall return a `404 Not Found` error.

### Scope: Cross-Domain Enforcement (Requires Clarification & Likely a Separate Story)
5.  **Given** a location with `status` = `INACTIVE`
    **When** a user in the scheduling system attempts to create a new staff assignment for that location
    **Then** the scheduling system shall reject the request with an error indicating the location is not active.

## Audit & Observability
- **Audit Logs:** All create and update operations on locations must be logged, capturing the `adminId`, `locationId`, the changes made (diff), and a timestamp.
- **Domain Events:**
    - On successful location creation, the system shall emit a `pos.location.v1.LocationCreated` event.
    - On successful location update, the system shall emit a `pos.location.v1.LocationUpdated` event. This event must clearly indicate if the `status` changed.

## Open Questions
1.  **Domain Ownership & Story Splitting:** This story mixes responsibilities. Should this story be narrowed to *only* the `domain:location` CRUD functionality? (Recommendation: Yes). If so, a new story must be created for `domain:people` to handle the enforcement logic described in AC #5.
2.  **Enforcement of Inactive Rule:** Where exactly should the business rule "Inactive locations prevent new staffing assignments" be enforced? In the `Location Service` itself, or in the consuming `People`/`Scheduling` service? (The standard microservice pattern suggests the consuming service is responsible for this validation).
3.  **Parent Location Hierarchy:** What are the business rules for the `parentLocationId` hierarchy? Is there a maximum depth? Does changing a parent's status to `INACTIVE` automatically cascade to its children, or is that a separate business process?
4.  **Default Status:** Does creating a new location always default its status to `ACTIVE`, or should the `status` be an optional field in the creation payload?

---
## Original Story (Unmodified – For Traceability)
# Issue #87 — [BACKEND] [STORY] Location: Create/Update Location (pos-location) Including Timezone

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Location: Create/Update Location (pos-location) Including Timezone

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Admin**, I want **to create and update locations** so that **staffing, scheduling, and timekeeping are anchored to the correct site and timezone**.

## Details
- Location fields: code, name, address, timezone, status, optional parent.

## Acceptance Criteria
- Location can be created/updated.
- Inactive locations prevent new staffing assignments.

## Integration Points (workexec/shopmgr)
- shopmgr schedules are tied to locationId.
- workexec workorders reference a service location.

## Data / Entities
- Location

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: People Management


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