Title: [BACKEND] [STORY] Estimate: Create Draft Estimate
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/174
Labels: backend, story-implementation, user, type:story, domain:workexec, status:ready-for-dev

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

**Rewrite Variant:** workexec-structured

---
## Story Intent
**As a** Service Advisor,
**I need to** create a new draft estimate for a customer's vehicle,
**so that** I can begin building a quote for requested services and establish the initial record for a potential job.

## Actors & Stakeholders
- **Primary Actor:**
  - `Service Advisor`: The user directly creating the estimate in the POS system.
- **System Actors:**
  - `POS Backend System`: The system responsible for processing the request, applying business rules, and persisting the new estimate.
- **Indirect Stakeholders:**
  - `Customer`: The individual or entity for whom the estimate is being created.
  - `Shop Manager`: Reviews estimates for operational oversight.
  - `Mechanic/Technician`: Will eventually consume the estimate details if it is converted to a work order.
  - `Accounting`: Relies on the estimate data for downstream financial reporting.

## Preconditions
1. The `Service Advisor` is authenticated and has an active session in the POS system.
2. The `Service Advisor` has the necessary permissions to create estimates (e.g., `ESTIMATE_CREATE` permission).
3. The system is configured with default `Location`, `Currency`, and `TaxRegion` settings for the user's session or assigned location.
4. The `Customer` and `Vehicle` either already exist in the system or can be created during this flow.

## Functional Behavior
### Happy Path - Create Draft Estimate
1.  **Trigger:** The `Service Advisor` initiates the "Create Estimate" action from the user interface.
2.  **Step 1 - Customer & Vehicle Association:**
    - The system prompts for and requires the association of an existing or newly created `Customer` record.
    - The system prompts for and requires the association of an existing or newly created `Vehicle` record.
3.  **Step 2 - System Creates Estimate Record:**
    - Upon receiving the `Customer` and `Vehicle` context, the system generates a new `Estimate` entity.
    - The system assigns a unique, human-readable identifier to the estimate, scoped to the business location (e.g., `EST-2024-1001`).
    - The system sets the initial `status` of the new `Estimate` to `Draft`.
    - The system populates default values for `locationId`, `currencyUomId`, and `taxRegionId` based on the Service Advisor's session context or system configuration.
4.  **Step 3 - Record Auditing:**
    - The system generates an `EstimateCreated` audit event.
5.  **Outcome:** A new `Estimate` record is successfully persisted in the database with a `Draft` status. The user is redirected to the estimate workspace/editor to begin adding line items.

## Alternate / Error Flows
- **Flow 1: Incomplete Customer or Vehicle Data**
  - **Trigger:** The `Service Advisor` attempts to finalize the estimate creation step without providing the required `Customer` or `Vehicle` identifiers.
  - **System Response:** The system rejects the request with a `400 Bad Request` status and a clear error message indicating which required associations are missing (e.g., `customerId is required`).
- **Flow 2: Insufficient Permissions**
  - **Trigger:** An authenticated user without the `ESTIMATE_CREATE` permission attempts to create an estimate.
  - **System Response:** The system rejects the request with a `403 Forbidden` status. The failed access attempt is logged for security monitoring.
- **Flow 3: System or Data Integrity Failure**
  - **Trigger:** The system encounters an unexpected error during persistence (e.g., database connection loss, constraint violation).
  - **System Response:** The system aborts the transaction, ensuring no partial data is saved. It returns a `500 Internal Server Error` response and logs the detailed error stack trace for investigation.

## Business Rules
- **BR1: Initial State:** All newly created estimates MUST have an initial status of `Draft`.
- **BR2: Uniqueness Constraint:** The human-readable estimate identifier (`estimateNumber`) MUST be unique within a given `locationId`. The system-generated primary key (`estimateId`) must be globally unique (UUID).
- **BR3: Contextual Defaults:** The `locationId`, `currencyUomId`, and `taxRegionId` MUST be defaulted from the user's current session or location context if not explicitly provided in the creation request. These defaults are non-negotiable for creation.
- **BR4: Immutability on Creation:** The `createdByUserId` and `createdAtTimestamp` fields MUST be set upon creation and MUST be immutable.

## Data Requirements
### `Estimate` Entity
| Field               | Type          | Constraints                                          | Description                                                        |
| ------------------- | ------------- | ---------------------------------------------------- | ------------------------------------------------------------------ |
| `estimateId`        | UUID          | `PK`, Not Null, System-generated                     | Unique system identifier for the estimate.                         |
| `estimateNumber`    | String        | Not Null, `UNIQUE(locationId, estimateNumber)`       | Human-readable identifier, unique per location.                    |
| `status`            | Enum          | Not Null, Default: `Draft`                           | The current lifecycle state of the estimate (e.g., Draft, Approved). |
| `customerId`        | UUID          | `FK`, Not Null                                       | Reference to the `Customer` entity.                                |
| `vehicleId`         | UUID          | `FK`, Not Null                                       | Reference to the `Vehicle` entity.                                 |
| `locationId`        | UUID          | `FK`, Not Null                                       | The business location (shop) where the estimate was created.       |
| `currencyUomId`     | String(3)     | Not Null                                             | The currency code (ISO 4217) for the estimate (e.g., 'USD').       |
| `taxRegionId`       | UUID          | `FK`, Not Null                                       | Reference to the applicable tax region for calculations.           |
| `createdByUserId`   | UUID          | `FK`, Not Null, Immutable                            | The user who created the estimate.                                 |
| `createdAtTimestamp`| Timestamp(TZ) | Not Null, Immutable                                  | The exact date and time the estimate was created.                  |

## Acceptance Criteria
- **AC-1: Successful Draft Estimate Creation**
  - **Given** a `Service Advisor` with `ESTIMATE_CREATE` permissions is logged in
  - **And** a valid `Customer` (ID: C-123) and `Vehicle` (ID: V-456) exist
  - **And** the system is configured with a default location, currency ('USD'), and tax region
  - **When** the `Service Advisor` submits a request to create a new estimate for customer 'C-123' and vehicle 'V-456'
  - **Then** the system MUST create a new `Estimate` record in the database
  - **And** the new estimate's `status` MUST be 'Draft'
  - **And** the estimate MUST be associated with `customerId` 'C-123' and `vehicleId` 'V-456'
  - **And** the `locationId`, `currencyUomId`, and `taxRegionId` fields MUST be populated with the correct default values
  - **And** an `EstimateCreated` audit event MUST be recorded.

- **AC-2: Attempt to Create Estimate with Missing Vehicle**
  - **Given** a `Service Advisor` with `ESTIMATE_CREATE` permissions is logged in
  - **And** a valid `Customer` (ID: C-123) exists
  - **When** the `Service Advisor` submits a request to create an estimate with `customerId` 'C-123' but a null `vehicleId`
  - **Then** the system MUST reject the request with a `400 Bad Request` status
  - **And** the response body MUST indicate that `vehicleId` is a required field
  - **And** NO new `Estimate` record is created in the database.

- **AC-3: Attempt to Create Estimate Without Permissions**
  - **Given** a user is logged in who does NOT have the `ESTIMATE_CREATE` permission
  - **When** the user submits a request to create a new estimate
  - **Then** the system MUST reject the request with a `403 Forbidden` status
  - **And** a security log entry MUST be generated for the failed authorization attempt
  - **And** NO new `Estimate` record is created in the database.

## Audit & Observability
- **Audit Log:**
  - An immutable `EstimateCreated` event MUST be logged upon successful creation.
  - The event MUST contain `estimateId`, `customerId`, `vehicleId`, `locationId`, `createdByUserId`, and `createdAtTimestamp`.
- **Logging:**
  - Log an `INFO` message on successful estimate creation, including the `estimateId` and `correlationId`.
  - Log a `WARN` message for failed validation attempts (e.g., missing fields).
  - Log an `ERROR` message with a full stack trace for any unhandled exceptions during the creation process.
- **Metrics:**
  - `estimates.created.success`: A counter metric incremented on each successful creation, tagged by `locationId`.
  - `estimates.created.failure`: A counter metric incremented on any failed creation attempt, tagged by `locationId` and `reason` (e.g., `validation`, `permission`, `system_error`).

## Open Questions
- None at this time.

---
## Original Story (Unmodified – For Traceability)
# Issue #174 — [BACKEND] [STORY] Estimate: Create Draft Estimate

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Estimate: Create Draft Estimate

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Service Advisor / Front Desk

## Trigger
A customer requests service or a quote for a vehicle (walk-in, phone, email, or fleet request).

## Main Flow
1. User selects or creates the Customer record.
2. User selects or creates the Vehicle record and captures context (VIN/plate, odometer, notes).
3. User clicks 'Create Estimate' and the system creates a Draft estimate with an identifier.
4. System sets default shop/location, currency, and tax region based on configuration.
5. User is taken to the Draft estimate workspace to add line items.

## Alternate / Error Flows
- Customer or Vehicle missing required fields → prompt user to complete required context.
- Estimate creation attempted without permissions → block and log access attempt.

## Business Rules
- Estimate starts in Draft state.
- Estimate identifier is unique per shop/location.
- Audit event is recorded on creation.

## Data Requirements
- Entities: Estimate, Customer, Vehicle, UserPermission, AuditEvent
- Fields: estimateId, status, customerId, vehicleId, shopId, currencyUomId, taxRegionId, createdBy, createdDate

## Acceptance Criteria
- [ ] A Draft estimate is created with required customer and vehicle context.
- [ ] Estimate status is Draft and visible.
- [ ] Creation is recorded in audit trail.

## Notes for Agents
Keep estimate creation decoupled from approval and workorder logic. Establish baseline validations and defaulting rules.


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