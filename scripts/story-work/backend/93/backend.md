Title: [BACKEND] [STORY] Integration: Emit CRM Reference IDs in Workorder Artifacts
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/93
Labels: type:story, domain:workexec, status:ready-for-dev

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** workexec-structured
---
## Story Intent
As the Workorder Execution System, I want to durably store immutable CRM entity references (`partyId`, `vehicleId`, `contactIds`) within Workorder and Estimate artifacts. This ensures that every work-related record has a permanent, traceable link back to the authoritative CRM records they were created for, enabling reliable reporting and integration even if the original CRM entities are later merged or aliased.

## Actors & Stakeholders
- **Actors**:
  - `Workorder Execution Service`: The primary system responsible for creating, updating, and persisting Workorder and Estimate entities.
  - `CRM Service`: The authoritative System of Record for customer, vehicle, and contact entities and their identifiers.
  - `Client System` (e.g., POS Terminal): The system that initiates the creation of an Estimate or Workorder and supplies the necessary CRM identifiers.

- **Stakeholders**:
  - `Reporting & Analytics System`: Consumes Workorder data and relies on the stable CRM references to join data across domains.
  - `Auditors`: Require a clear, traceable link between work performed and the customer/vehicle it was for.
  - `Integration Partners`: May consume Workorder events or data and use the CRM references to correlate information.

## Preconditions
- A request to create or update a Workorder or Estimate is being processed.
- The request payload from the `Client System` contains validly formatted identifiers for `crmPartyId`, `crmVehicleId`, and `crmContactIds`.
- The `Workorder Execution Service` has network access to its persistence layer (database).

## Functional Behavior
### Scenario: Creating a New Estimate with CRM References
- **Trigger**: The `Workorder Execution Service` receives a `POST /estimates` request from a `Client System`.
- **Process**:
  1. The request payload is deserialized and validated. The validation MUST confirm the presence of `crmPartyId`, `crmVehicleId`, and `crmContactIds`.
  2. The service maps the payload to an internal `Estimate` entity.
  3. The `crmPartyId`, `crmVehicleId`, and `crmContactIds` are stored in their designated fields on the `Estimate` entity.
  4. The service persists the `Estimate` entity to the database.
- **Outcome**: A new Estimate record is created in the database, with the provided CRM reference IDs stored immutably as part of the record. A success response (`201 Created`) is returned to the client.

## Alternate / Error Flows
- **Flow 1: Missing Required CRM Identifiers**
  - **Scenario**: A create/update request is received, but one or more of `crmPartyId` or `crmVehicleId` are null or missing.
  - **Outcome**: The request is rejected with a `400 Bad Request` HTTP status. The response body clearly indicates which required fields were missing.

- **Flow 2: Database Persistence Failure**
  - **Scenario**: The service attempts to save the Workorder/Estimate entity, but the database write operation fails.
  - **Outcome**: The transaction is rolled back. The service returns a `500 Internal Server Error` response.

## Business Rules
- **Rule-1**: All created `Workorder` and `Estimate` entities MUST store a non-null reference to a `crmPartyId` and a `crmVehicleId`.
- **Rule-2**: The `crmContactIds` field may be an empty list but must not be null.
- **Rule-3**: The stored CRM identifiers are point-in-time references. The `Workorder Execution Service` is NOT responsible for updating these IDs if the associated CRM entities change.
- **Rule-4**: The `Workorder Execution Service` is NOT responsible for resolving CRM entity merges or aliases. It stores the IDs as provided. Downstream consumers are responsible for using the `CRM Service`'s resolution capabilities to find the canonical entity for a given ID.

## Data Requirements
- **Affected Entities**: `Estimate`, `Workorder`
- **New Fields/Schema Changes**:
  - `crm_party_id`:
    - **Type**: To be confirmed (e.g., UUID, VARCHAR). See Open Questions.
    - **Constraints**: Non-nullable, Indexed.
  - `crm_vehicle_id`:
    - **Type**: To be confirmed (e.g., UUID, VARCHAR). See Open Questions.
    - **Constraints**: Non-nullable, Indexed.
  - `crm_contact_ids`:
    - **Type**: Array of the contact ID type or JSONB.
    - **Constraints**: Non-nullable (can be an empty array `[]`).

- **Data Source**: The values for these fields are provided in the API request payload from the `Client System`.
- **Data Authority**: The `CRM Service` is the authority for the entities these IDs represent. The `Workorder Execution Service` is the authority for the stored reference copies within its domain.

## Acceptance Criteria
- **AC-1: Successful Estimate Creation**
  - **Given** a valid API request to create a new Estimate containing `crmPartyId: "party-abc"`, `crmVehicleId: "vehicle-123"`, and `crmContactIds: ["contact-x", "contact-y"]`.
  - **When** the `Workorder Execution Service` successfully processes the request.
  - **Then** a new Estimate record is created in the database.
  - **And** the record's `crm_party_id` column contains `"party-abc"`, the `crm_vehicle_id` column contains `"vehicle-123"`, and the `crm_contact_ids` column contains the array `["contact-x", "contact-y"]`.

- **AC-2: Workorder Conversion Preserves IDs**
  - **Given** an existing Estimate with `crmPartyId: "party-abc"` and `crmVehicleId: "vehicle-123"`.
  - **When** that Estimate is converted into a Workorder.
  - **Then** the newly created Workorder record also contains `crm_party_id: "party-abc"` and `crm_vehicle_id: "vehicle-123"`.

- **AC-3: Request Fails with Missing Vehicle ID**
  - **Given** an API request to create an Estimate that is missing the `crmVehicleId` field.
  - **When** the `Workorder Execution Service` receives the request.
  - **Then** the service must reject the request with a `400 Bad Request` status.
  - **And** the API response body must indicate that `crmVehicleId` is a required field.

- **AC-4: Data Integrity for Downstream Traceability**
  - **Given** a Workorder has been stored with `crmPartyId: "party-old-id"`.
  - **And** the `CRM Service` later merges `"party-old-id"` into a canonical `"party-new-id"`.
  - **When** a `Reporting System` queries the Workorder and retrieves the stored `crm_party_id` (`"party-old-id"`).
  - **Then** the `Reporting System` must be able to use a separate `CRM Service` mechanism (e.g., an alias resolution API) to successfully resolve `"party-old-id"` to `"party-new-id"`.

## Audit & Observability
- **Audit Logging**: On successful creation of a Workorder or Estimate, an audit log must be generated containing the `workorderId`/`estimateId` and the associated `crmPartyId` and `crmVehicleId`.
- **Metrics**:
  - `workorder.creation.success`: Counter for successfully created Workorders.
  - `workorder.creation.failure`: Counter for failed Workorder creations, tagged by error type (e.g., `validation`, `database`).
- **Distributed Tracing**: Traces for create/update operations must include the `crmPartyId` and `crmVehicleId` as attributes for easier debugging and analysis.

## Open Questions
- **Q1 (Alias Resolution Contract)**: The original story mentions "CRM provides alias resolution endpoint if needed". What is the specific contract (URL, request/response format) for this endpoint? Does it already exist? We are assuming consumers of Workorder data (like reporting) are responsible for calling this endpoint, not the Workorder service itself. Please confirm this separation of concerns.

- **Q2 (Data Types and Constraints)**: What are the exact data types and formats for `partyId`, `vehicleId`, and `contactId`? Are they UUIDs, `BIGINT`, or formatted strings (e.g., `party-xxxx`)? This is critical for defining the database schema and API contracts.

- **Q3 (Handling of Empty Contacts)**: Is it permissible for `crmContactIds` to be an empty list (`[]`), or must there always be at least one contact associated? The current assumption is that an empty list is valid. Please confirm.

## Original Story (Unmodified – For Traceability)
# Issue #93 — [BACKEND] [STORY] Integration: Emit CRM Reference IDs in Workorder Artifacts

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Integration: Emit CRM Reference IDs in Workorder Artifacts

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **System**, I want **workorders/estimates to store CRM partyId, vehicleId, and contactIds** so that **traceability and reporting are possible**.

## Details
- Workorder domain stores foreign references to CRM.
- CRM merges must not break references (aliases/redirects).

## Acceptance Criteria
- Estimate/WO persist CRM references.
- References resolvable back to CRM after merges.

## Integration Points (Workorder Execution)
- Workorder Execution persists CRM IDs; CRM provides alias resolution endpoint if needed.

## Data / Entities
- WO/Estimate reference fields
- PartyAlias (optional)

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