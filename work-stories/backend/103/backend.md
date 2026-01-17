Title: [BACKEND] [STORY] Vehicle: Vehicle Lookup by VIN/Unit/Plate
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/103
Labels: type:story, domain:crm, status:ready-for-dev

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

As a Service Advisor, I need a robust search capability to find vehicles by their Vehicle Identification Number (VIN), internal unit number, or license plate. This functionality is critical for quickly and accurately identifying a customer's asset to initiate a service workflow, such as creating a new repair estimate.

## Actors & Stakeholders

- **Service Advisor**: The primary user who performs the vehicle search to serve a customer.
- **System (CRM Domain)**: The system of record for customer and vehicle (asset) data, responsible for executing the search logic and returning results.
- **System (Workorder Execution Domain)**: A key downstream consumer of this functionality. It will invoke this search to associate a specific vehicle with a new estimate or work order.

## Preconditions

- The Service Advisor is authenticated and has the necessary permissions to search for and view vehicle and customer information.
- A data store (e.g., PostgreSQL, MySQL) exists, populated with vehicle records that include VIN, unit number, license plate, and associated owner account details.

## Functional Behavior

1.  **Trigger**: The Service Advisor initiates a vehicle search from a designated user interface within the Point-of-Sale (POS) system.
2.  The Advisor enters a full or partial search query into a search field. The query corresponds to a vehicle's VIN, Unit Number, or License Plate.
3.  The system sends a request to the search API endpoint with the provided query string.
4.  The backend service executes a query against the vehicle data store, matching the term against the `vin`, `unitNumber`, and `licensePlate` fields.
5.  The service returns a ranked list of matching vehicle summaries. Each summary must include sufficient information for the Advisor to uniquely identify the correct vehicle (e.g., Year/Make/Model, VIN, Unit #, Plate #, and Owner Name).
6.  If no vehicles match the query, the system returns an empty result set.
7.  The Advisor selects a specific vehicle from the returned list.
8.  The system makes a subsequent request to retrieve a detailed data snapshot for the selected vehicle and its associated owner.
9.  This detailed snapshot is then used by the calling system (e.g., Workorder Execution) to populate the context for a new estimate.

## Alternate / Error Flows

- **No Matches Found**: If the search query yields no results, the API must return a `200 OK` status with an empty array `[]` in the response body. The UI should display a "No vehicles found" message.
- **Ambiguous/Broad Search**: If a partial search term matches more than a predefined limit (e.g., 50 records), the API should return the truncated list and indicate that more results exist. The UI should prompt the user to provide a more specific term.
- **Invalid Input**: If the search term is empty or malformed, the API should return a `400 Bad Request` error with a clear message.
- **System Failure**: If the search service encounters an internal error (e.g., database connection failure), it must return a `500 Internal Server Error` and the failure should be logged.

## Business Rules

- Search must be performed against the following canonical fields: `vin`, `unitNumber`, `licensePlate`.
- The search must support partial matches. The exact matching strategy requires clarification (see Open Questions).
- The search results must be ranked according to a defined relevance logic (see Open Questions).
- The search API response must be paginated or limited to a maximum number of results (e.g., 50) to ensure performance.

## Data Requirements

### Search Request (API Endpoint)
```json
// POST /api/v1/vehicles/search
{
  "query": "TRK-123"
}
```

### Search Response (List of Summaries)
```json
// 200 OK
{
  "results": [
    {
      "vehicleId": "veh-uuid-001",
      "vin": "1A2B3C...8X9Y0Z",
      "unitNumber": "TRK-123A",
      "licensePlate": "CA-45678",
      "description": "2022 Kenworth T680",
      "owner": {
        "accountId": "acct-uuid-987",
        "name": "FleetCo Inc."
      }
    }
  ]
}
```

### Vehicle & Owner Snapshot (Returned on Selection)
The precise data contract for the snapshot returned after selecting a search result requires clarification (see Open Questions). A proposed structure is:
```json
// GET /api/v1/vehicles/{vehicleId}?include=owner
// 200 OK
{
  "vehicleId": "veh-uuid-001",
  "vin": "1A2B3C...8X9Y0Z",
  "unitNumber": "TRK-123A",
  "licensePlate": "CA-45678",
  "year": 2022,
  "make": "Kenworth",
  "model": "T680",
  "engineDetails": "...",
  "lastOdometerReading": 150234,
  "owner": {
    "accountId": "acct-uuid-987",
    "name": "FleetCo Inc.",
    "primaryContact": "John Doe",
    "phone": "555-123-4567"
  }
}
```

## Acceptance Criteria

**Scenario: Search with an Exact VIN Match**
- **Given** a vehicle exists in the system with the VIN `1FTFW1E54KFA12345`.
- **When** the Service Advisor performs a search with the query `1FTFW1E54KFA12345`.
- **Then** the system returns a result list containing exactly one vehicle summary.
- **And** the summary's VIN matches `1FTFW1E54KFA12345`.

**Scenario: Search with a Partial Unit Number**
- **Given** two vehicles exist with Unit Numbers `FLEET-A501` and `FLEET-A502`.
- **And** another vehicle exists with Unit Number `TRUCK-B900`.
- **When** the Service Advisor performs a search with the query `FLEET-A5`.
- **Then** the system returns a result list containing the two vehicles `FLEET-A501` and `FLEET-A502`.
- **And** the list does not contain `TRUCK-B900`.

**Scenario: Search with No Matching Results**
- **Given** no vehicle exists with a VIN, Unit Number, or License Plate containing the string `NONEXISTENT`.
- **When** the Service Advisor performs a search with the query `NONEXISTENT`.
- **Then** the system returns an empty list of results.

**Scenario: Selection of a Vehicle from Search Results**
- **Given** a search for `FLEET-A501` has returned a vehicle summary with `vehicleId` "veh-abc-123".
- **When** the user selects that vehicle to start an estimate.
- **Then** the system successfully retrieves the full vehicle and owner snapshot corresponding to `vehicleId` "veh-abc-123".

## Audit & Observability

- **Logging**: All search requests should be logged, including the search query and the number of results returned. Log the `vehicleId` selected for a new workflow.
- **Metrics**: Monitor and alert on key performance indicators for the search endpoint:
    - Search latency (p95, p99).
    - Request rate.
    - Error rate (4xx and 5xx).
- **Events**:
    - `vehicle.searched`: Emitted after a search is completed. Payload includes `query`, `resultCount`, `searchDurationMs`, `userId`.
    - `vehicle.selected_for_service`: Emitted when a user selects a vehicle from the search results to begin a workflow. Payload includes `vehicleId`, `accountId`, `userId`, `sourceWorkflow`.

## Open Questions

1.  **Ranking Logic**: What is the required ranking algorithm for search results? For example, should exact matches appear first, followed by "starts with" matches, then "contains" matches?
2.  **Partial Search Behavior**: What is the precise definition of a "partial" search? Should the query match any part of the string (`contains`) or only from the beginning (`starts with`)? Is there a minimum character length required to initiate a search?
3.  **Data Snapshot Contract**: What specific fields must be included in the "full vehicle + owner snapshot" that is returned upon selection? Please provide the definitive data contract for both the vehicle and owner entities.
4.  **Result Set Limit**: What is the maximum number of search results that should be returned in a single response? Does the API need to support full pagination for consumers who might need it?

---
## Original Story (Unmodified – For Traceability)
# Issue #103 — [BACKEND] [STORY] Vehicle: Vehicle Lookup by VIN/Unit/Plate

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Vehicle: Vehicle Lookup by VIN/Unit/Plate

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **to search vehicles by VIN, unit number, or plate** so that **I can quickly start an estimate for the correct asset**.

## Details
- Partial VIN and unit searches.
- Return matches including owner account context.

## Acceptance Criteria
- Search returns ranked matches.
- Selecting a match returns full vehicle + owner snapshot.

## Integration Points (Workorder Execution)
- Estimate creation uses vehicle search/selection.

## Data / Entities
- Vehicle search endpoint/index

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