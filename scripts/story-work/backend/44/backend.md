Title: [BACKEND] [STORY] Rules: Store Fitment Hints and Vehicle Applicability Tags (Basic)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/44
Labels: backend, story-implementation, user, type:story, domain:inventory, status:ready-for-dev

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** inventory-flexible
---

## Story Intent
**As a** Product Administrator,
**I want to** associate products with vehicle applicability hints (e.g., make, model, year range, tire size),
**so that** downstream systems, like the POS or Work Execution, can filter and suggest compatible products to a Service Advisor for a specific customer vehicle.

## Actors & Stakeholders
- **Product Administrator:** The primary user responsible for creating and maintaining product data, including vehicle applicability hints.
- **Service Advisor:** The end-user who consumes the fitment suggestions within a client application (e.g., POS) to select the correct parts for a customer's vehicle.
- **Work Execution System:** A downstream system that consumes product and applicability data to guide technicians or filter parts lists based on the vehicle being serviced.
- **System:** The inventory management service responsible for storing and providing this data via API.

## Preconditions
- The Product Administrator is authenticated and possesses the necessary permissions to manage product catalog data.
- Products (e.g., parts, tires) to which hints will be applied already exist in the system.
- A standardized, albeit basic, vocabulary for vehicle attributes (e.g., `make`, `model`, `year`, `tire_size`, `axle_position`) is defined.

## Functional Behavior

### 1. Managing Vehicle Applicability Hints
A Product Administrator, through an administrative interface or API, can perform full CRUD (Create, Read, Update, Delete) operations on Vehicle Applicability Hints for any given product/SKU.

- **Creation:** The administrator can create a new hint and associate it with one or more products. A hint consists of a collection of structured tags.
- **Updating:** The administrator can add, remove, or modify the tags within an existing hint.
- **Deletion:** The administrator can remove an entire applicability hint from a product.

### 2. Product Filtering by Vehicle Attributes
The system shall expose an API endpoint that allows a client (e.g., Work Execution System) to query for products based on a set of vehicle attributes.

- **Trigger:** An API request is received containing a set of key-value pairs representing the target vehicle's attributes (e.g., `{"make": "Honda", "model": "Civic", "year": "2021"}`).
- **Outcome:** The system returns a list of products whose associated applicability hints are a match for the provided vehicle attributes. This is a filtering mechanism, not a complex fitment engine.

## Alternate / Error Flows
- **Invalid Tag Data:** If a user attempts to create or update a hint with malformed or unrecognized tags, the system shall reject the request with a `400 Bad Request` error and a descriptive message.
- **Product Not Found:** If a hint is being applied to a non-existent product ID, the system shall return a `404 Not Found` error.
- **No Matching Products:** If a search query for products based on vehicle attributes yields no results, the system shall return an empty list with a `200 OK` status, not an error.

## Business Rules
1.  **Hint, Not a Guarantee:** The applicability data serves as a "hint" to guide selection. It is not a guaranteed fitment system and should not block a transaction if a Service Advisor chooses to override the suggestion.
2.  **Tag Structure:** Each tag within a hint shall be a key-value pair (e.g., `key: "make"`, `value: "Toyota"`).
3.  **Matching Logic:** A product is considered a match if its applicability hints are compatible with the vehicle attributes provided in a query. For this basic implementation, a simple intersection of tags is sufficient. For example, a query for `make:Toyota` and `model:Camry` would match a product tagged with `make:Toyota`, `model:Camry`, and `year_range:2018-2022`.
4.  **Attribute Extensibility:** The system should allow for new tag keys (e.g., 'engine_size', 'trim_level') to be added in the future without requiring a schema change.

## Data Requirements
### `VehicleApplicabilityHint`
- `hintId`: Unique identifier for the hint.
- `productId`: Foreign key to the associated Product/SKU.
- `fitmentTags`: A collection of `FitmentTag` objects.

### `FitmentTag`
- `tagType`: The type of attribute (e.g., `MAKE`, `MODEL`, `YEAR_RANGE`, `TIRE_SIZE`, `AXLE_POSITION`).
- `tagValue`: The value of the attribute (e.g., `Honda`, `Civic`, `2019-2022`, `225/45R17`, `FRONT`).

## Acceptance Criteria

### AC1: Create a Fitment Hint
- **Given** a Product Administrator is logged in and viewing an existing product
- **When** they add a new Vehicle Applicability Hint with tags for "make: Subaru", "model: Outback", and "year_range: 2020-2023"
- **Then** the system successfully saves the hint and associates it with the product
- **And** an audit event for `VEHICLE_HINT_CREATED` is generated.

### AC2: Update a Fitment Hint
- **Given** a product has an existing hint with the tag "year_range: 2020-2023"
- **When** the Product Administrator updates the tag to be "year_range: 2020-2024"
- **Then** the system saves the change to the existing hint
- **And** an audit event for `VEHICLE_HINT_UPDATED` is generated.

### AC3: Filter Products by Vehicle Attributes
- **Given** Product A is tagged with `make:Ford`, `model:F-150`
- **And** Product B is tagged with `make:Toyota`, `model:Camry`
- **When** a system queries the product API with the attributes `{"make": "Ford", "model": "F-150"}`
- **Then** the API response includes Product A
- **And** the API response does not include Product B.

### AC4: Filter Products with No Match
- **Given** no products are tagged with `make:Tesla`
- **When** a system queries the product API with the attributes `{"make": "Tesla"}`
- **Then** the system returns a `200 OK` status with an empty list of products.

## Audit & Observability
- All Create, Update, and Delete operations on `VehicleApplicabilityHint` and their associated `FitmentTag` entities MUST generate an audit log event.
- Each audit event must include the user ID performing the action, the timestamp, the product ID affected, and the change delta (before/after state of the hint).
- API endpoints for filtering products should be monitored for latency and error rates.

## Open Questions
- None at this time.

## Original Story (Unmodified – For Traceability)
# Issue #44 — [BACKEND] [STORY] Rules: Store Fitment Hints and Vehicle Applicability Tags (Basic)

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Rules: Store Fitment Hints and Vehicle Applicability Tags (Basic)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Product Admin**, I want fitment hints so that advisors can pick correct parts/tires for a vehicle.

## Details
- Basic tags: make/model/year ranges, tire size specs, axle position.
- Hints only (not full fitment engine).

## Acceptance Criteria
- Fitment tags stored and retrievable.
- Search/filter by tag.
- Audited.

## Integrations
- Workexec can pass vehicle attributes from CRM to filter candidates.

## Data / Entities
- FitmentTag, VehicleApplicabilityHint, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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