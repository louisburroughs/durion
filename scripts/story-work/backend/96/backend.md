Title: [BACKEND] [STORY] Promotions: Define Eligibility Rules (Account/Vehicle)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/96
Labels: type:story, domain:pricing, status:ready-for-dev

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

---
**Rewrite Variant:** pricing-strict
---

## Story Intent
As an Account Manager or Pricing Analyst, I want to define granular eligibility rules for promotions based on customer account and vehicle attributes, so that promotional discounts are applied accurately and exclusively to the intended target audience.

## Actors & Stakeholders
- **Primary Actor:** `Pricing Analyst / Account Manager` - The user responsible for configuring promotions and their associated business rules.
- **System Actor:** `Pricing Engine` - The automated service that consumes eligibility rules during price calculation for work orders and estimates.
- **Secondary Domains (Data Sources):**
  - `domain:crm` - Provides authoritative data for customer accounts, including fleet size.
  - `domain:inventory` - Provides authoritative data for vehicle attributes, such as category tags (e.g., 'tractor', 'trailer').
- **Stakeholders:**
  - `Service Advisor` - Benefits from the system automatically and correctly applying promotions during customer interactions.
  - `Customer` - Receives accurate, targeted discounts they are eligible for.

## Preconditions
1. The user is authenticated and has the necessary permissions to create and modify promotions.
2. A promotion entity already exists to which these eligibility rules can be attached.
3. APIs are available to query customer account data (from CRM) and vehicle data (from Inventory) required for rule evaluation.

## Functional Behavior

### 1. Configure Promotion Eligibility Rules
- **Trigger:** A Pricing Analyst navigates to the configuration screen for a specific promotion and chooses to add or edit an eligibility rule.
- **Behavior:**
    - The system provides a UI to define one or more rule conditions.
    - Each condition consists of a type, an operator, and a value.
    - Supported rule types include:
        - `Account ID List`: Matches if the customer's account ID is in a specified list.
        - `Vehicle Tag`: Matches if the vehicle in context has a specific tag.
        - `Account Fleet Size`: Matches if the customer's account meets a numeric threshold (e.g., greater than or equal to 10 vehicles).
- **Outcome:** The set of rules is validated and persisted, associated with the parent promotion ID. The system is now capable of evaluating these rules.

### 2. Evaluate Promotion Eligibility
- **Trigger:** The Pricing Engine requests an eligibility check for a given promotion during a pricing calculation (e.g., for a work order estimate). The request includes the context: `accountId` and `vehicleId`.
- **Behavior:**
    1. The Promotion service retrieves all active eligibility rules for the specified promotion.
    2. It fetches necessary contextual data (e.g., account's fleet size, vehicle's tags) from the CRM and Inventory domains using the provided IDs.
    3. It evaluates the context against each rule condition based on the configured logic (see Business Rules).
- **Outcome:** The service returns a structured response to the Pricing Engine, indicating eligibility and the reason.
    - **Success Response:** `{"isEligible": true, "reasonCode": "ELIGIBLE"}`
    - **Failure Response:** `{"isEligible": false, "reasonCode": "ACCOUNT_MISMATCH"}` or `{"isEligible": false, "reasonCode": "FLEET_SIZE_TOO_SMALL"}`

## Alternate / Error Flows
- **Invalid Rule Configuration:** If a user tries to save a rule with invalid data (e.g., a fleet size threshold that is not a number), the system displays a validation error and prevents saving.
- **Missing Evaluation Context:** If the Pricing Engine calls the eligibility service without a required piece of context (e.g., `accountId`), the service returns an error response with a `BAD_REQUEST` status and a reason code like `MISSING_ACCOUNT_CONTEXT`.
- **Data Source Unavailable:** If the CRM or Inventory services are unavailable during evaluation, the eligibility check fails with an internal error, which should be logged. The default eligibility in this case must be `false` to prevent incorrect discounting.

## Business Rules
- **Rule Combination Logic:** The logic for combining multiple rules for a single promotion must be explicitly defined. A safe default is that **ALL** conditions must be met (`AND` logic). This requires clarification.
- **Authoritative Data:** The CRM domain is the system of record for account data (including fleet size). The Inventory domain is the system of record for vehicle data (including tags).
- **Fail-Safe Principle:** In case of ambiguity or system error during evaluation, the promotion shall be considered **not eligible**.
- **Reason Codes:** The system must return a specific, machine-readable reason code for every eligibility decision (both positive and negative) to support auditing and troubleshooting.

## Data Requirements
**Entity: `PromotionEligibilityRule`**
```json
{
  "ruleId": "uuid", // Primary Key
  "promotionId": "uuid", // Foreign Key to Promotion entity
  "conditionType": "ENUM", // ACCOUNT_ID_LIST, VEHICLE_TAG, ACCOUNT_FLEET_SIZE
  "operator": "ENUM", // IN, NOT_IN, EQUALS, GREATER_THAN_OR_EQUAL_TO
  "value": "string" // e.g., "CUST-001,CUST-002", "trailer", "10"
}
```
**API Response: `EligibilityDecision`**
```json
{
  "isEligible": "boolean",
  "reasonCode": "string" // e.g., "ELIGIBLE", "ACCOUNT_MISMATCH", "VEHICLE_TAG_MISMATCH", "FLEET_SIZE_TOO_SMALL", "MISSING_CONTEXT"
}
```

## Acceptance Criteria

### Scenario 1: Configure an Account-Specific Rule
- **Given** a Pricing Analyst is configuring the "National Fleet Discount" promotion
- **When** they add an eligibility rule of type `ACCOUNT_ID_LIST`, operator `IN`, and value `CUST-001,CUST-007`
- **Then** the system saves the rule and successfully associates it with the "National Fleet Discount" promotion.

### Scenario 2: Eligible based on Account and Vehicle Tags
- **Given** a promotion has two rules with `AND` logic:
  1. `ACCOUNT_ID_LIST` `IN` `CUST-001`
  2. `VEHICLE_TAG` `EQUALS` `tractor`
- **And** account `CUST-001` exists and vehicle `VEH-555` has the tag `tractor`
- **When** the Pricing Engine requests an eligibility check for the promotion with context `accountId: CUST-001` and `vehicleId: VEH-555`
- **Then** the service returns `{"isEligible": true, "reasonCode": "ELIGIBLE"}`.

### Scenario 3: Ineligible due to Vehicle Tag Mismatch
- **Given** the same promotion rules as Scenario 2
- **And** account `CUST-001` exists and vehicle `VEH-666` has the tag `trailer`
- **When** the Pricing Engine requests an eligibility check for the promotion with context `accountId: CUST-001` and `vehicleId: VEH-666`
- **Then** the service returns `{"isEligible": false, "reasonCode": "VEHICLE_TAG_MISMATCH"}`.

### Scenario 4: Ineligible due to Fleet Size
- **Given** a promotion has a rule: `ACCOUNT_FLEET_SIZE` `GREATER_THAN_OR_EQUAL_TO` `20`
- **And** account `CUST-002` has a fleet size of `15`
- **When** the Pricing Engine requests an eligibility check for the promotion with context `accountId: CUST-002`
- **Then** the service returns `{"isEligible": false, "reasonCode": "FLEET_SIZE_TOO_SMALL"}`.

### Scenario 5: Evaluation Fails due to Missing Context
- **Given** any promotion with eligibility rules
- **When** the Pricing Engine requests an eligibility check without providing an `accountId`
- **Then** the service returns an error response with reason code `MISSING_ACCOUNT_CONTEXT`.

## Audit & Observability
- **Audit Trail:** All create, update, and delete operations on `PromotionEligibilityRule` entities must be logged with user attribution and timestamps.
- **Structured Logging:** Every eligibility evaluation event must be logged with the `promotionId`, input context (`accountId`, `vehicleId`), the final decision (`isEligible`), and the `reasonCode`.
- **Metrics:** The service should expose key metrics for monitoring, including:
  - Latency of eligibility evaluation requests (p95, p99).
  - Rate of eligible vs. ineligible decisions per promotion.
  - Error rate for evaluation requests, categorized by error type (e.g., bad request, data source failure).

## Open Questions
1.  **Rule Combination Logic:** When a promotion has multiple rules (e.g., one for `Account ID` and one for `Vehicle Tag`), must **ALL** conditions be met (`AND` logic) or is meeting **ANY** condition sufficient (`OR` logic)? Is this logic configured per-promotion or is it a system-wide default?
2.  **Authoritative Data Sources:** What are the specific service endpoints and contracts for retrieving an account's fleet size and a vehicle's tags?
3.  **Reason Codes:** Please confirm the initial set of `reasonCode` enums required. Should we distinguish between `ACCOUNT_NOT_IN_LIST` and `ACCOUNT_IN_EXCLUSION_LIST` if that becomes a feature?

## Original Story (Unmodified – For Traceability)
# Issue #96 — [BACKEND] [STORY] Promotions: Define Eligibility Rules (Account/Vehicle)

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Promotions: Define Eligibility Rules (Account/Vehicle)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Account Manager**, I want **to restrict offers to certain accounts or vehicle categories** so that **promotions are applied correctly**.

## Details
- Eligibility: specific accounts, simple tags (e.g., trailer/tractor), optional fleet-size threshold.
- Return eligibility decision with reason.

## Acceptance Criteria
- Configure eligibility.
- Evaluate eligibility with explanation.

## Integration Points (Workorder Execution)
- Workorder Execution calls eligibility evaluation during estimate pricing.

## Data / Entities
- PromotionEligibilityRule

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