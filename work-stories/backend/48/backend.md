Title: [BACKEND] [STORY] Availability: Expose On-hand and Available-to-Promise by Location (from Inventory)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/48
Labels: type:story, domain:inventory, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:ready-for-dev

### Recommended
- agent:inventory
- agent:story-authoring

---
**Rewrite Variant:** inventory-flexible
---

## Story Intent

**As a** Service Advisor using a Point of Sale (POS) or quoting tool,
**I want to** view the real-time on-hand quantity and available-to-promise (ATP) quantity for a specific product, broken down by fulfillment location,
**So that I can** confidently inform customers about product availability, set accurate fulfillment expectations, and avoid promising inventory that is already committed.

## Actors & Stakeholders

- **Primary Actor:** `POS System` (acting on behalf of the Service Advisor). This system is the direct client of the new API endpoint.
- **Primary Stakeholder:** `Service Advisor`. The end-user whose workflow is improved by having accurate availability data.
- **Secondary Stakeholders:**
    - `Inventory Manager`: Relies on the accurate reporting of inventory levels managed by this domain.
    - `Product/Catalog System`: Provides the canonical product identifiers (`productId`) used for lookup.

## Preconditions

1.  A canonical list of `Products` with unique identifiers (`productId`) exists and is managed by the Product domain.
2.  A canonical list of `Locations` (e.g., warehouses, stores) with unique identifiers (`locationId`) exists.
3.  The Inventory system is the designated System of Record for tracking product quantities (`on-hand`, `reserved`, `incoming`) at each location.
4.  The client system (e.g., POS) is authenticated and authorized to query inventory availability.

## Functional Behavior

This story describes the creation of a new, read-only API endpoint that exposes inventory availability for a given product across all relevant locations.

### 1. Query Availability by Product

- **Trigger:** An authorized client system sends an HTTP `GET` request to the Availability API endpoint.
- **Endpoint (Proposed):** `GET /api/v1/inventory/availability`
- **Query Parameter:** `productId` (string, required) - The unique identifier for the product being queried.
- **System Process:**
    1.  The system receives the request and validates the `productId` parameter.
    2.  It queries the inventory data store for the specified `productId`.
    3.  For each location where the product exists, the system retrieves the current on-hand quantity.
    4.  The system calculates the Available-to-Promise (ATP) quantity for each location based on defined business rules.
    5.  The system constructs and returns a JSON response containing a list of availability details per location.
- **Outcome:** The client receives a `200 OK` response with the availability data.

## Alternate / Error Flows

- **Flow 1: Product Not Found**
    - **Trigger:** A request is made for a `productId` that does not exist in the Product/Inventory system.
    - **Outcome:** The API returns an `HTTP 404 Not Found` status with a clear error message.

- **Flow 2: Product Has No Inventory Records**
    - **Trigger:** A request is made for a valid `productId` that has never been stocked or currently has no inventory records at any location.
    - **Outcome:** The API returns an `HTTP 200 OK` status with an empty list in the response body, indicating zero availability everywhere.

- **Flow 3: Invalid Request**
    - **Trigger:** The `productId` query parameter is missing from the request.
    - **Outcome:** The API returns an `HTTP 400 Bad Request` status with a clear error message.

## Business Rules

- **BR1: On-Hand Quantity Definition**
    - The `onHandQuantity` MUST represent the total physical quantity of the product currently present at a given location. It is a direct reflection of the inventory ledger for that SKU/location pair.
- **BR2: Available-to-Promise (ATP) Calculation**
    - The `availableToPromiseQuantity` is a calculated value defined as: **ATP = On-Hand − Reservations**
    - Expected replenishments are explicitly excluded from v1 ATP calculation.

## Data Requirements

The API endpoint shall return a JSON object with the following structure.

### `AvailabilityView` (Response Body)

```json
{
  "productId": "SKU-12345",
  "locations": [
    {
      "locationId": "LOC-WH-01",
      "locationName": "Main Warehouse",
      "onHandQuantity": 100,
      "availableToPromiseQuantity": 85
    },
    {
      "locationId": "LOC-STORE-02",
      "locationName": "Downtown Store",
      "onHandQuantity": 12,
      "availableToPromiseQuantity": 10
    }
  ]
}
```

### Field Definitions

| Field                        | Type    | Description                                                                                             | Required |
| ---------------------------- | ------- | ------------------------------------------------------------------------------------------------------- | -------- |
| `productId`                  | String  | The unique identifier of the product that was queried.                                                  | Yes      |
| `locations`                  | Array   | A list of objects, each representing inventory at a specific location. May be empty.                    | Yes      |
| `locations.locationId`       | String  | The unique identifier for the location.                                                                 | Yes      |
| `locations.locationName`     | String  | The human-readable name of the location.                                                                | Yes      |
| `locations.onHandQuantity`   | Integer | The current physical stock count at the location. Cannot be negative.                                   | Yes      |
| `locations.availableToPromiseQuantity` | Integer | The calculated quantity available for new sales commitments. Can be negative. | Yes      |

## Acceptance Criteria

**AC-1: Successful Retrieval for In-Stock Product**
- **Given** a product with ID `SKU-123` has 50 units on-hand and 40 units ATP at "Warehouse A", and 10 units on-hand and 8 units ATP at "Store B";
- **When** a `GET /api/v1/inventory/availability?productId=SKU-123` request is made
- **Then** the system returns a `200 OK` status
- **And** the response body contains the `productId` `SKU-123`
- **And** the `locations` array contains two entries, one for "Warehouse A" and one for "Store B", with their respective `onHandQuantity` and `availableToPromiseQuantity` values.

**AC-2: Correct Handling for Product with No Stock**
- **Given** a product with ID `SKU-456` is a valid product but has no inventory records at any location
- **When** a `GET /api/v1/inventory/availability?productId=SKU-456` request is made
- **Then** the system returns a `200 OK` status
- **And** the response body's `locations` array is empty.

**AC-3: Error on Non-Existent Product**
- **Given** the product ID `SKU-999` does not exist
- **When** a `GET /api/v1/inventory/availability?productId=SKU-999` request is made
- **Then** the system returns a `404 Not Found` status.

**AC-4: Error on Missing Product ID**
- **Given** a client is authorized
- **When** a `GET /api/v1/inventory/availability` request is made without a `productId` parameter
- **Then** the system returns a `400 Bad Request` status.

**AC-5: ATP Calculation is Correct**
- **Given** the business rule for ATP calculation is defined as `ATP = On-Hand − Reservations`
- **And** a product has known quantities for on-hand and active reservations
- **When** an availability request is made for that product
- **Then** the returned `availableToPromiseQuantity` correctly reflects the formula.

## Audit & Observability

- **Logging:**
    - Log each API request with the `productId` and client identifier.
    - Log the response status code and latency for each request.
    - Log any errors encountered during data retrieval or calculation, including stack traces for exceptions.
- **Metrics:**
    - Monitor request volume (`requests_total`).
    - Monitor request latency (`request_latency_seconds`).
    - Monitor error rates by status code (`http_4xx_total`, `http_5xx_total`).

## Resolved Questions

### Question 1: ATP Formula (BLOCKER RESOLVED)

**Question:** What is the authoritative formula for calculating Available-to-Promise (ATP)?

**Answer:** 

**ATP = On-Hand − Reservations**

Expected replenishments are explicitly **OUT OF SCOPE for v1 ATP** and must not be included in the ATP calculation.

**Rationale:**
Including expected replenishments introduces purchasing assumptions, supplier reliability dependencies, date-sensitive promise logic, partial receipt complexity, and reconciliation risk. For v1, ATP must be ledger-derived, deterministic, explainable, and safe for commitment decisions.

**Component Definitions:**

**On-Hand (authoritative source):**
- **Source:** Inventory Ledger
- **Definition:** Net physical quantity currently in stock at a location/storage
- **Included movements:** `GOODS_RECEIPT`, `TRANSFER_IN`, `RETURN_TO_STOCK`, `ADJUSTMENT_IN`, `COUNT_VARIANCE_IN`
- **Minus:** `GOODS_ISSUE`, `TRANSFER_OUT`, `SCRAP_OUT`, `ADJUSTMENT_OUT`, `COUNT_VARIANCE_OUT`
- **Note:** On-Hand explicitly excludes allocations and reservations

**Reservations (included in ATP subtraction):**
- **Source:** Inventory Reservation / Allocation subsystem
- **Definition:** Quantities explicitly committed to downstream demand but not yet physically issued
- **Include only ACTIVE reservations:** `RESERVED`, `ALLOCATED`, `PICK_ASSIGNED` (if stock is logically locked), `ISSUE_PENDING`
- **Exclude:** `CANCELLED`, `RELEASED`, `EXPIRED`, `FULFILLED` (already issued → reflected in On-Hand)
- **Important:** Reservations are location-specific and must match the same `(productId, locationId, storageLocationId)` scope as On-Hand

**Expected Replenishments (explicitly excluded in v1):**
Not included in ATP:
- Purchase Orders (`PO_CREATED`, `PO_CONFIRMED`)
- Inbound ASNs
- In-transit transfers
- Supplier promises
- Manufacturer/distributor feeds
- Backorders
- Planned replenishment

**Optional future extension (v2+):** If/when included, they must be date-qualified (promise date), confidence-weighted, and exposed as a separate field (never silently added to ATP).

**Final Implementation Statement:**
> Available-to-Promise (ATP) is defined as current On-Hand quantity minus active Reservations. Expected replenishments are not included in ATP for this implementation. Reservations include only active, location-scoped commitments that have not yet resulted in physical issue.

---
## Original Story (Unmodified – For Traceability)
# Issue #48 — [BACKEND] [STORY] Availability: Expose On-hand and Available-to-Promise by Location (from Inventory)

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Availability: Expose On-hand and Available-to-Promise by Location (from Inventory)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want on-hand/ATP by location so that I can quote with realistic fulfillment expectations.

## Details
- Query inventory for on-hand and ATP.
- Optional reservations and expected replenishment.

## Acceptance Criteria
- Availability API returns quantities by location.
- Consistent productId mapping.
- SLA for estimate UI.

## Integrations
- Product → Inventory availability query; Inventory → Product responses/events.

## Data / Entities
- AvailabilityView, LocationQty, ReservationSummary

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