Title: [BACKEND] [STORY] Estimate: Create Draft Estimate
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/208
Labels: backend, story-implementation, type:story, layer:functional, kiro, domain:workexec, status:ready-for-dev

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
**As a** Service Advisor,
**I need to** create a new, empty draft estimate for a specific customer and their vehicle,
**so that** I can begin the process of building a service quote.

## Actors & Stakeholders
- **Primary Actor:**
  - `Service Advisor`: The user responsible for creating and managing estimates for customers.

- **Stakeholders:**
  - `Customer`: The owner of the vehicle who will eventually receive the estimate.
  - `Shop Manager`: Oversees all work execution activities and may need to review new estimates.
  - `System`: The POS application responsible for generating unique identifiers and ensuring data integrity.

## Preconditions
1.  The `Service Advisor` is authenticated and has the necessary permissions to create estimates.
2.  The `Customer` for whom the estimate is being created already exists in the system.
3.  The `Vehicle` to be serviced already exists in the system and is associated with the `Customer`.

## Functional Behavior
1.  The `Service Advisor` initiates the "Create New Estimate" action from the user interface.
2.  The system requires the `Service Advisor` to provide the unique identifiers for the target `Customer` and `Vehicle`.
3.  Upon receiving a valid request, the system generates a new `Estimate` entity in the persistence layer.
4.  The new estimate is created with an initial `status` of `Draft`.
5.  The system generates and assigns a unique, sequential, and human-readable identifier (e.g., `EST-10021`) to the `estimateNumber` field.
6.  The system records the `createdAt` timestamp and associates the creating user's ID with the new estimate.
7.  The system returns a success response containing the full representation of the newly created `Estimate` object, including its system-generated `estimateId` and `estimateNumber`.

## Alternate / Error Flows
- **Error Flow 1: Invalid Customer or Vehicle ID**
  - **Trigger:** The provided `customerId` or `vehicleId` does not correspond to an existing record.
  - **Outcome:** The system rejects the request with a `404 Not Found` error and a clear message (e.g., "Customer with ID [id] not found.").

- **Error Flow 2: Missing Permissions**
  - **Trigger:** The authenticated user does not have the "Create Estimate" permission.
  - **Outcome:** The system rejects the request with a `403 Forbidden` error.

- **Error Flow 3: Database Failure**
  - **Trigger:** The system is unable to commit the new estimate record to the database.
  - **Outcome:** The system rejects the request with a `500 Internal Server Error` and logs the detailed error for investigation. The operation is fully rolled back.

## Business Rules
- A new `Estimate` must be associated with exactly one `Customer` and one `Vehicle`.
- The initial state of every newly created estimate must be `Draft`.
- The `estimateNumber` must be unique across all estimates in the system.
- The `estimateNumber` must follow a consistent, predefined format (e.g., `EST-` prefix followed by a number).

## Data Requirements
The following data attributes must be captured for a new `Estimate` entity:

| Field Name       | Type      | Constraints / Description                               |
| ---------------- | --------- | ------------------------------------------------------- |
| `estimateId`     | UUID      | Primary Key, System-generated.                          |
| `estimateNumber` | String    | Unique, human-readable identifier (e.g., `EST-10021`).  |
| `customerId`     | UUID      | Foreign Key to the `Customer` entity. Not nullable.     |
| `vehicleId`      | UUID      | Foreign Key to the `Vehicle` entity. Not nullable.      |
| `status`         | Enum      | Initial value must be `DRAFT`.                          |
| `createdAt`      | Timestamp | Automatically set on creation. Not nullable.            |
| `updatedAt`      | Timestamp | Automatically set on creation and subsequent updates.   |
| `createdById`    | UUID      | Foreign Key to the `User` who created the record.       |

## Acceptance Criteria

### AC-1: Successful Creation of a Draft Estimate
**Given** a Service Advisor is authenticated and has "Create Estimate" permissions
**And** a `Customer` with ID `C-123` and an associated `Vehicle` with ID `V-456` exist in the system
**When** the Service Advisor submits a request to create a new estimate for customer `C-123` and vehicle `V-456`
**Then** the system creates a new `Estimate` record in the database
**And** the new estimate has its `customerId` set to `C-123` and `vehicleId` set to `V-456`
**And** the `status` of the new estimate is `Draft`
**And** the system returns a `201 Created` response containing the details of the new estimate, including its unique `estimateId` and `estimateNumber`.

### AC-2: Attempt to Create Estimate for Non-Existent Customer
**Given** a Service Advisor is authenticated and has "Create Estimate" permissions
**When** the Service Advisor submits a request to create an estimate with a non-existent `customerId` `C-999`
**Then** the system does not create an `Estimate` record
**And** the system returns a `404 Not Found` error with a message indicating the customer was not found.

### AC-3: Attempt to Create Estimate Without Authorization
**Given** a user is authenticated but lacks "Create Estimate" permissions
**When** the user submits a request to create a new estimate
**Then** the system does not create an `Estimate` record
**And** the system returns a `403 Forbidden` error response.

## Audit & Observability
- **Logging:**
  - On success, log an `INFO` message: `Estimate [estimateNumber] created for Customer [customerId] by User [userId].`
  - On failure, log a `WARN` or `ERROR` message with relevant context, such as the invalid ID or permission failure.
- **Events:**
  - Upon successful creation, the system must emit an `EstimateCreated` domain event. The event payload must include the `estimateId`, `customerId`, `vehicleId`, and `createdById`.

## Original Story (Unmodified – For Traceability)
# Issue #208 — [BACKEND] [STORY] Estimate: Create Draft Estimate

## Current Labels
- backend
- story-implementation
- type:story
- layer:functional
- kiro

## Current Body
## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #13 - Estimate: Create Draft Estimate
**URL**: https://github.com/louisburroughs/durion/issues/13
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for backend development
- Coordinate with corresponding frontend implementation if needed

### Technical Requirements
**Backend Implementation Requirements:**
- Use Spring Boot with Java 21
- Implement RESTful API endpoints following established patterns
- Include proper request/response validation
- Implement business logic with appropriate error handling
- Ensure database operations are transactional where needed
- Include comprehensive logging for debugging
- Follow security best practices for authentication/authorization


### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Backend agents: Focus on Spring Boot microservices, Java 21, REST APIs, PostgreSQL. Ensure API contracts align with frontend requirements.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `backend` - Implementation type

---
*Generated by Missing Issues Audit System - 2025-12-26T17:39:21.498380741*