Title: [BACKEND] [STORY] Pricing: Maintain MSRP per Product with Effective Dates
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/194
Labels: type:story, layer:functional, kiro, domain:pricing, status:needs-review

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:pricing
- status:draft

### Recommended
- agent:pricing
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:financial-inference

**Rewrite Variant:** pricing-strict
## ⚠️ Domain Conflict Summary
- **Candidate Primary Domains:** `domain:pricing`, `domain:inventory`
- **Why conflict was detected:** The story title "Maintain MSRP per Product" implies a tight coupling between the Product entity (owned by Inventory) and the MSRP data (owned by Pricing). Without a clear definition of which domain is the System of Record for the combined concept, implementation could lead to data integrity issues. The original story was auto-generated and lacks this clarity.
- **What must be decided:**
    1. Which domain is the ultimate authority for product-price relationships? (Presumed `pricing`)
    2. What is the exact inter-domain contract for validating that a `ProductID` from a pricing request is a valid, existing product? Is it a synchronous API call, an event-driven cache, or a direct database check?
    3. How are product lifecycle events (e.g., product discontinuation in Inventory) propagated to the Pricing domain to manage related MSRPs?
- **Recommended split:** No, splitting is not recommended. Instead, the inter-domain contract between `pricing` and `inventory` must be explicitly defined.

## Story Intent
**As a** Pricing Manager,
**I want to** define and maintain a Manufacturer's Suggested Retail Price (MSRP) for each product, with specific effective start and end dates,
**so that** the correct, time-sensitive MSRP is available for downstream pricing calculations, reporting, and compliance verification.

## Actors & Stakeholders
- **Pricing Manager (User):** The primary actor responsible for creating, updating, and managing MSRP records.
- **System (Pricing Engine):** A consumer of MSRP data. It must be able to query the active MSRP for a given product on a specific date.
- **System (Inventory Domain):** The authoritative source (System of Record) for Product definitions, including the `ProductID`. The Pricing domain depends on the Inventory domain for product existence validation.
- **Auditor (User):** A stakeholder who needs to review the history of MSRP changes for compliance and financial audits.

## Preconditions
- The user is authenticated and possesses the necessary authorization grants (e.g., `pricing:msrp:manage`).
- A product catalog exists in the Inventory domain, and products are identifiable by a stable, unique `ProductID`.
- The system has a reliable mechanism to resolve the current date/time (UTC).

## Functional Behavior
### 1. Create a New MSRP Record
A Pricing Manager can create a new MSRP for a specific `ProductID`. The creation process must capture:
- **`ProductID`**: A valid identifier referencing an existing product in the Inventory domain.
- **`Amount`**: The monetary value of the MSRP.
- **`Currency`**: The ISO 4217 currency code (e.g., USD, EUR).
- **`effectiveStartDate`**: The date on which this MSRP becomes active.
- **`effectiveEndDate` (Optional)**: The date on which this MSRP is no longer active. If null, the MSRP is considered active indefinitely from the start date.

The system MUST validate that the proposed effective date range does not overlap with any existing MSRP records for the same `ProductID`.

### 2. Update an Existing MSRP Record
A Pricing Manager can modify an existing MSRP record. The following fields may be updated:
- `Amount`
- `Currency`
- `effectiveStartDate`
- `effectiveEndDate`

Any update to date fields must trigger the same overlap validation as in the creation process. Modifying historical records (where `effectiveEndDate` is in the past) may be restricted or require special permissions.

### 3. Retrieve Active MSRP for a Product
The system provides an endpoint to query the currently active MSRP for a given `ProductID` and an optional date.
- If no date is provided, the query defaults to the current date.
- The endpoint will return the single MSRP record where the query date falls between the `effectiveStartDate` and `effectiveEndDate`.

## Alternate / Error Flows
- **Error - Product Not Found:** If an MSRP is created or updated with a `ProductID` that does not exist in the Inventory domain, the system must reject the request with a `404 Not Found` or `400 Bad Request` error.
- **Error - Overlapping Date Range:** If an attempt is made to create or update an MSRP that results in an overlapping effective date range for the same `ProductID`, the system must reject the request with a `409 Conflict` error.
- **Error - Invalid Date Logic:** If `effectiveEndDate` is provided and is before `effectiveStartDate`, the system must reject the request with a `400 Bad Request` error.
- **Error - Insufficient Permissions:** If the user making the request lacks the required permissions, the system must reject the request with a `403 Forbidden` error.

## Business Rules
- **BR1: Temporal Uniqueness:** For any given `ProductID`, there can be only one active MSRP record at any single point in time. Date ranges cannot overlap.
- **BR2: Forward-Only Indefinite Pricing:** An MSRP with a null `effectiveEndDate` can only exist if it is the latest-starting record for that `ProductID`.
- **BR3: Inventory Authority:** The Inventory domain is the sole System of Record for product existence. All `ProductID` values must be validated against it.
- **BR4: Historical Immutability (To Be Confirmed):** MSRP records whose `effectiveEndDate` is in the past should be treated as immutable to preserve the historical audit trail.

## Data Requirements
The `ProductMSRP` entity within the Pricing domain shall contain the following fields:

| Field Name         | Data Type        | Constraints                                     | Description                                          |
|--------------------|------------------|-------------------------------------------------|------------------------------------------------------|
| `msrpId`           | UUID             | Primary Key, Not Null                           | Unique identifier for the MSRP record.               |
| `productId`        | UUID             | Not Null, Indexed, Foreign Key (Logical)        | Identifier of the product from the Inventory domain. |
| `amount`           | DECIMAL(19, 4)   | Not Null, Positive                              | The monetary value of the MSRP.                      |
| `currency`         | VARCHAR(3)       | Not Null, ISO 4217 code                         | The currency of the amount.                          |
| `effectiveStartDate` | DATE             | Not Null                                        | The first date the MSRP is active.                   |
| `effectiveEndDate`   | DATE             | Nullable                                        | The last date the MSRP is active. Null = indefinite. |
| `createdAt`        | TIMESTAMP WITH TZ| Not Null, System-managed                        | Timestamp of record creation.                        |
| `updatedAt`        | TIMESTAMP WITH TZ| Not Null, System-managed                        | Timestamp of the last record update.                 |
| `updatedBy`        | VARCHAR(255)     | Not Null                                        | Identifier of the user/system that made the change.  |

## Acceptance Criteria

### AC1: Successful Creation of a Time-Bound MSRP
**Given** a Pricing Manager is authenticated and has `pricing:msrp:manage` permissions
**And** a product with `ProductID` "PROD-123" exists in the Inventory system
**And** no other MSRP exists for "PROD-123" between '2025-01-01' and '2025-12-31'
**When** the manager submits a request to create an MSRP for "PROD-123" with amount 99.99 USD, `effectiveStartDate` '2025-01-01', and `effectiveEndDate` '2025-12-31'
**Then** the system shall create the new MSRP record successfully and return a `201 Created` status.
**And** the record must be persisted in the database with the correct values.

### AC2: Successful Creation of an Indefinite MSRP
**Given** a Pricing Manager is authenticated
**And** a product with `ProductID` "PROD-456" exists
**When** the manager submits a request to create an MSRP for "PROD-456" with amount 150.00 EUR, `effectiveStartDate` '2026-01-01', and a null `effectiveEndDate`
**Then** the system shall create the record successfully.

### AC3: Failure Due to Overlapping Date Range
**Given** an MSRP already exists for `ProductID` "PROD-123" from '2025-01-01' to '2025-12-31'
**When** a user attempts to create a new MSRP for "PROD-123" with `effectiveStartDate` '2025-06-01'
**Then** the system shall reject the request with a `409 Conflict` error and a message indicating a date range overlap.

### AC4: Failure Due to Non-Existent Product
**Given** a Pricing Manager is authenticated
**And** no product exists with `ProductID` "PROD-999"
**When** the manager attempts to create an MSRP for "PROD-999"
**Then** the system shall reject the request with a `400 Bad Request` or `404 Not Found` error.

### AC5: Retrieve the Currently Active MSRP
**Given** two MSRPs exist for `ProductID` "PROD-789":
- MSRP-A: 50.00 USD, from '2024-01-01' to '2024-12-31'
- MSRP-B: 55.00 USD, from '2025-01-01' to null
**When** a system requests the active MSRP for "PROD-789" on '2025-03-15'
**Then** the system shall return the details for MSRP-B (55.00 USD).

## Audit & Observability
- **Events:** The system MUST emit domain events for the following actions, including the user, timestamp, and changed data:
  - `MSRP.Created`
  - `MSRP.Updated`
- **Logging:** All API requests and business logic decisions (e.g., overlap validation failure) MUST be logged with structured context (e.g., `ProductID`, `msrpId`, `userId`).
- **Metrics:** The system SHOULD expose metrics for:
  - Count of MSRP creation/update operations (successes and failures).
  - Latency for MSRP API endpoints.

## Open Questions
1.  **Inter-Domain Contract:** What is the specific, guaranteed contract for validating a `ProductID` against the Inventory domain? Is it a synchronous REST call, a replicated data cache, or another mechanism? This choice has significant performance and consistency implications.
2.  **Product Lifecycle Handling:** What should happen to existing or future-dated MSRPs when a product is discontinued or deleted in the Inventory domain? Should they be automatically expired, or flagged for manual review?
3.  **Historical Data Policy:** Is it permissible to *ever* modify an MSRP record once its `effectiveEndDate` has passed? If so, under what conditions and what are the auditing requirements? This has financial and compliance implications.
4.  **Timezone Handling:** All dates are assumed to be full dates (not timestamps). Is this correct? If so, what timezone defines the start and end of a day for `effectiveStartDate` and `effectiveEndDate` (e.g., UTC, store local time)?

## Original Story (Unmodified – For Traceability)
# Issue #194 — [BACKEND] [STORY] Pricing: Maintain MSRP per Product with Effective Dates

## Current Labels
- backend
- story-implementation
- type:story
- layer:functional
- kiro

## Current Body
## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #198 - Pricing: Maintain MSRP per Product with Effective Dates
**URL**: https://github.com/louisburroughs/durion/issues/198
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
*Generated by Missing Issues Audit System - 2025-12-26T17:38:43.56218742*