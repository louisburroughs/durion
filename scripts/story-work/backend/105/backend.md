Title: [BACKEND] [STORY] Vehicle: Create Vehicle Record with VIN and Description
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/105
Labels: payment, type:story, domain:crm, status:ready-for-dev

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** crm-pragmatic
---

## Story Intent
As a **Service Advisor**, I want **to create a unique vehicle record by providing its essential identifiers (like VIN and Unit Number)** so that **I can accurately track all associated service history, work orders, and billing against a stable and reliable entity**.

## Actors & Stakeholders
- **Service Advisor**: The primary user who creates and manages vehicle records on behalf of a customer.
- **System (CRM Domain)**: The system of record responsible for creating, persisting, and ensuring the integrity of vehicle data.
- **Downstream Systems (Workorder Execution, Billing)**: External systems that consume the stable `vehicleId` to associate their own entities (e.g., Work Orders, Invoices) with a specific vehicle.

## Preconditions
- The Service Advisor is authenticated and has the necessary permissions to create new vehicle records.
- The Service Advisor is operating within a customer account context where a new vehicle can be added.

## Functional Behavior
1.  The Service Advisor initiates the "Create Vehicle" action from the user interface.
2.  The system presents a form requesting the vehicle's details.
3.  The Service Advisor provides the required information: VIN, Unit Number, and a free-text Description (intended for Make/Model/Year). They may optionally provide the License Plate number.
4.  Upon submission, the system validates the provided data against the defined business rules (e.g., field presence, basic VIN format).
5.  If validation passes, the system generates a new, unique, and immutable system-wide `vehicleId`.
6.  The system persists the new vehicle record to the database, associating it with the current customer account.
7.  The system confirms successful creation to the Service Advisor.

## Alternate / Error Flows
- **Duplicate Vehicle**: If the submitted VIN (or other unique identifier) violates the defined uniqueness rule, the system will reject the creation request and display an error message indicating that the vehicle may already exist.
- **Invalid Input**: If any required fields are missing or if the VIN fails basic format validation (e.g., incorrect length or invalid characters), the system will reject the request and display a specific validation error message.
- **System Failure**: If a database or system error occurs during the creation process, the transaction will be rolled back, and a generic system error message will be displayed to the user.

## Business Rules
- **BR-1: Required Fields**: `vin`, `unitNumber`, and `description` are mandatory for creating a new vehicle record.
- **BR-2: Optional Fields**: `licensePlate` is an optional field.
- **BR-3: Vehicle ID Generation**: The `vehicleId` must be a system-generated, unique, and immutable identifier (e.g., UUID) that serves as the primary key and stable reference for all integrations.
- **BR-4: VIN Format Validation**: A basic sanity check must be performed on the VIN to ensure it meets general format requirements (e.g., 17 characters, no invalid characters like 'I', 'O', 'Q'). A full external VIN decoding service is out of scope for this story.
- **BR-5: Vehicle Uniqueness**: The uniqueness constraint for a Vehicle's VIN is subject to clarification. See OQ-1.

## Data Requirements
The following data attributes must be captured for the `Vehicle` entity:

| Field | Type | Constraints | Description |
|---|---|---|---|
| `vehicleId` | UUID | Primary Key, Not Null, Immutable | System-generated unique identifier for the vehicle record. |
| `accountId` | UUID | Foreign Key, Not Null, Indexed | Reference to the owning customer account. |
| `vin` | String(17) | Not Null, Indexed | Vehicle Identification Number. Uniqueness scope to be defined. |
| `unitNumber` | String | Not Null, Indexed | A business-specific identifier for the vehicle (e.g., fleet number). |
| `description` | String | Not Null | Free-text field for Make, Model, Year, etc. |
| `licensePlate` | String | Nullable | The vehicle's license plate number and state/province. |
| `createdAt` | Timestamp | Not Null | Timestamp of when the record was created. |
| `updatedAt` | Timestamp | Not Null | Timestamp of the last update to the record. |

## Acceptance Criteria
**AC-1: Successful Vehicle Creation**
- **Given** a Service Advisor is authenticated and has permission to create vehicles
- **When** they submit a form with valid and unique values for `vin`, `unitNumber`, and `description`
- **Then** the system creates a new `Vehicle` record in the database
- **And** the new record is assigned a unique, system-generated `vehicleId`
- **And** the system returns a success confirmation.

**AC-2: Attempt to Create a Duplicate Vehicle**
- **Given** the VIN uniqueness rule is defined (e.g., "globally unique")
- **And** a vehicle with VIN `123ABC456DEF789G0` already exists
- **When** a Service Advisor attempts to create another vehicle with the same VIN `123ABC456DEF789G0`
- **Then** the system must reject the request
- **And** the system must display an informative error message stating the vehicle already exists.

**AC-3: Attempt to Create with Invalid VIN Format**
- **Given** a Service Advisor is on the "Create Vehicle" form
- **When** they submit the form with a VIN that is only 10 characters long
- **Then** the system must reject the request due to a format validation failure
- **And** the system must display an error message explaining the VIN format is invalid.

**AC-4: Attempt to Create with Missing Required Fields**
- **Given** a Service Advisor is on the "Create Vehicle" form
- **When** they submit the form without providing a `unitNumber`
- **Then** the system must reject the request
- **And** the system must display a validation error indicating that the `unitNumber` field is required.

## Audit & Observability
- **Event Emission**: A `VehicleCreated` event shall be emitted to a message bus upon successful creation.
- **Event Payload**: The event must contain at a minimum: `vehicleId`, `accountId`, `vin`, and `eventTimestamp`.
- **Logging**:
    - **INFO**: Log successful vehicle creation with `vehicleId` and `accountId`.
    - **WARN**: Log any validation failures, including the fields that failed and the reason.
    - **ERROR**: Log any exceptions or failures during the database persistence step.

## Open Questions
- **OQ-1: VIN Uniqueness Scope**: What is the business rule for VIN uniqueness? Is a VIN required to be **globally unique** across all customer accounts, or only **unique within a single customer's account**? This decision is critical as it impacts database constraints, API design, and the business logic for duplicate detection.

## Original Story (Unmodified – For Traceability)
# Issue #105 — [BACKEND] [STORY] Vehicle: Create Vehicle Record with VIN and Description

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Vehicle: Create Vehicle Record with VIN and Description

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **to create a vehicle record with VIN, unit number, and description** so that **future service and billing can be linked to the correct vehicle**.

## Details
- Capture VIN, make/model/year (free text initially), unit number, license plate (optional).
- Minimal VIN format validation; external decode optional stub.

## Acceptance Criteria
- Can create vehicle with VIN.
- Vehicle has stable Vehicle ID.
- VIN uniqueness rule defined (global or per account).

## Integration Points (Workorder Execution)
- Workorder Execution selects vehicle for estimate/workorder; Vehicle ID stored on workorder.

## Data / Entities
- Vehicle
- VehicleIdentifier (VIN, unit, plate)

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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