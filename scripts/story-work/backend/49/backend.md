Title: [BACKEND] [STORY] Workexec: Handle Substitution Pricing for Part Substitutions
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/49
Labels: type:story, domain:workexec, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:ready-for-dev

### Recommended
- agent:workexec
- agent:story-authoring

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
As a Service Advisor, I need the system to automatically retrieve and price authorized part substitutes when an original part is unavailable. This ensures that work can proceed without delay, eliminates manual price lookups and calculations, and maintains estimate accuracy.

## Actors & Stakeholders
- **Actors:**
    - **Service Advisor:** The primary user who initiates the substitution process on a Work Order or Estimate.
    - **System:** The POS backend that orchestrates the queries for substitutes, availability, and pricing, and updates the Work Order.
- **Stakeholders:**
    - **Parts Manager:** Defines the part substitution policies (e.g., which substitutes are allowed for a given part).
    - **Service Manager:** Concerned with the efficiency of the service workflow and the accuracy of job costing.
    - **Customer:** Receives a timely and accurate estimate/invoice reflecting the parts actually used.

## Preconditions
1. A Work Order (or Estimate) exists in an open state.
2. The Work Order has a line item for a specific part.
3. The original part on the line item has been determined to be unavailable by the Inventory system.
4. Part substitution policies are configured in the Parts/Inventory domain.

## Functional Behavior
1.  **Trigger:** The Service Advisor initiates a "Find Substitutes" action for a specific part line item on the Work Order.
2.  **Request Substitutes:** The system sends a request to the Inventory/Parts domain, providing the original part number.
3.  **Receive Candidates:** The Inventory/Parts domain returns a list of authorized substitute part numbers, based on pre-configured substitution policies. This response should include availability status for each candidate.
4.  **Request Pricing:** For each *available* substitute candidate, the system requests a price from the Pricing domain. The request must include context necessary for accurate pricing (e.g., Customer ID, Contract ID).
5.  **Present Options:** The system presents the Service Advisor with a list of available, priced substitutes. Each option must display:
    - Substitute Part Number
    - Part Description
    - Final Customer Price
    - Availability Information (e.g., "In Stock")
    - Any relevant policy flags (e.g., "OEM", "Aftermarket")
6.  **Capture Selection:** The Service Advisor selects one of the substitute parts from the list.
7.  **Update Work Order:** The system updates the Work Order line item to reflect the selected substitute. The line item now contains the substitute part's number, description, and the quoted price. The original part number must be preserved on the line item for traceability.

## Alternate / Error Flows
- **No Substitutes Found:** If the Inventory/Parts domain returns no authorized substitutes for the original part, the system shall display a message to the Service Advisor: "No authorized substitutes found."
- **No Substitutes Available:** If substitutes exist but none are currently in stock, the system shall display a message: "No substitutes are currently available."
- **Pricing Service Failure:** If the Pricing domain fails to return a price for an available substitute, display the substitute with "Price Unavailable" status (do not exclude it from the list).
- **User Cancels Action:** The Service Advisor can cancel the substitution process at any time before confirming a selection, leaving the original line item unchanged.

## Business Rules
- The list of substitutes must be filtered according to centrally-managed substitution policies.
- The price displayed and applied must be the final, calculated price for the specific customer context, not a generic list price.
- The original part number and the fact that a substitution was made must be stored on the modified Work Order line item for reporting, auditing, and warranty purposes.
- Price is locked at time of selection; repricing only allowed via explicit, auditable action with permission.

## Data Requirements
- **Input to Process:**
    - `workOrderId`
    - `lineItemId`
    - `originalPartNumber`
    - `customerId` (for pricing context)
- **Data from Secondary Domains:**
    - **Inventory:** List of `SubstituteCandidate { partNumber, availabilityStatus, policyFlags }`
    - **Pricing:** `PriceQuote { partNumber, finalPrice, currency, priceStatus }`
- **State Changes:**
    - The target `WorkOrderLineItem` entity is updated with:
        - `partNumber` (set to the substitute's number)
        - `description` (set to the substitute's description)
        - `price` (set to the substitute's quoted price)
        - `lockedUnitPrice`, `currency`, `priceSource`, `priceListId`, `policyVersion`, `pricedAt`
        - `priceLockStatus = LOCKED`
        - `isSubstituted` (flag set to true)
- **Substitution History:**
    - `WorkOrderPartSubstitution` entity (1-to-many per line):
        - `substitutionId`
        - `workOrderId`
        - `workOrderLineItemId`
        - `originalProductId`
        - `originalPartNumberSnapshot`
        - `substituteProductId`
        - `substitutePartNumberSnapshot`
        - `selectedBy`
        - `selectedAt`
        - `reasonCode`
        - `pricingSnapshot`
        - `status` (APPLIED | REVERSED | SUPERSEDED)

## Acceptance Criteria
**Scenario 1: Successful Substitution**
- **Given** a Work Order line item for part `ABC-123` which is unavailable
- **And** part `XYZ-789` is an authorized and available substitute with a price of $50.00
- **When** the Service Advisor requests substitutes for `ABC-123`
- **And** selects `XYZ-789` from the presented list
- **Then** the Work Order line item is updated to show part `XYZ-789` with a locked price of $50.00
- **And** the line item retains a record that the original part was `ABC-123`
- **And** a `WorkOrderPartSubstitution` history record is created.

**Scenario 2: No Authorized Substitutes Exist**
- **Given** a Work Order line item for part `ABC-123` which is unavailable
- **And** there are no authorized substitutes configured for `ABC-123`
- **When** the Service Advisor requests substitutes
- **Then** the system displays a message "No authorized substitutes found."
- **And** the original Work Order line item remains unchanged.

**Scenario 3: Substitutes Exist But Are Not Available**
- **Given** a Work Order line item for part `ABC-123` which is unavailable
- **And** part `XYZ-789` is an authorized substitute but is not in stock
- **When** the Service Advisor requests substitutes
- **Then** the system displays a message "No substitutes are currently available."
- **And** the original Work Order line item remains unchanged.

**Scenario 4: Pricing Service Fails for a Substitute**
- **Given** a Work Order line item for part `ABC-123` which is unavailable
- **And** part `XYZ-789` is an authorized and available substitute
- **And** the Pricing service fails to return a price for `XYZ-789`
- **When** the Service Advisor requests substitutes
- **Then** the system displays `XYZ-789` with `priceStatus = UNAVAILABLE` and `unitPrice = null`
- **And** the Service Advisor can view it but cannot finalize selection/commit without a resolved price (unless they have `ENTER_MANUAL_PRICE` permission).

## Audit & Observability
- **Audit Log:** A structured log event must be created upon successful substitution, capturing `workOrderId`, `lineItemId`, `originalPartNumber`, `selectedSubstitutePartNumber`, `price`, and the `userId` of the Service Advisor.
- **Domain Event:** The system shall emit a `workexec.WorkOrder.PartSubstituted` event upon successful substitution.
- **Metrics:** The system should instrument counters to track the frequency of part substitutions (successes and failures).

## Resolved Questions

### Question 1: Pricing Service Failure for a Substitute

**Question:** What is the expected system behavior if the Pricing service is unavailable or returns an error for a specific, available substitute part?

**Answer:** **Display the substitute with "Price Unavailable"**, do not exclude it.

**Rules:**
- If Inventory/availability indicates the substitute is available but Pricing fails:
  - Include the substitute in results with:
    - `priceStatus = UNAVAILABLE`
    - `unitPrice = null`
    - `pricingErrorCode` (optional, non-sensitive)
- Advisor can **view** it but cannot **finalize** selection/commit without a resolved price, unless they have manual price permission.

**Guardrails:**
Selection/commit requires either:
- successful Pricing response, or
- manual price entry with permission `ENTER_MANUAL_PRICE` and audit reason.

This preserves decision-making while preventing silent incorrect pricing.

### Question 2: Traceability Storage (Preserve Original Part Number)

**Question:** What is the preferred data model for preserving the original part number?

**Answer:** Use a **separate related entity** for substitution history (authoritative), plus an optional convenience field on the line item.

**Data Model:**
- `WorkOrderLineItem`
  - `currentProductId`
  - `currentPartNumber` (derived/denormalized OK)
  - optional `originalProductId` (only if you want quick access)

- `WorkOrderPartSubstitution` (new entity, 1-to-many per line)
  - `substitutionId`
  - `workOrderId`
  - `workOrderLineItemId`
  - `originalProductId`
  - `originalPartNumberSnapshot`
  - `substituteProductId`
  - `substitutePartNumberSnapshot`
  - `selectedBy`
  - `selectedAt`
  - `reasonCode`
  - `pricingSnapshot`
  - `status` (APPLIED | REVERSED | SUPERSEDED)

**Rationale:** A single field loses multiple substitutions over time, reversal history, who/when/why, and audit defensibility.

### Question 3: Price Locking Policy for Substitutes

**Question:** When a substitute is selected and its price is applied to the Work Order line, is that price considered locked?

**Answer:** **Lock price at time of selection**, with an explicit controlled "reprice" action.

**Default Behavior:**
When substitute is selected and applied:
- store `lockedUnitPrice`, `currency`, `priceSource`, `priceListId`, `policyVersion`, `pricedAt`
- mark `priceLockStatus = LOCKED`

**When Repricing is Allowed:**
Only via explicit user action and permission:
- `REPRICE_WORKORDER_LINE`
- Reprice is allowed until invoicing is finalized, but it must:
  - create an audit event (`LINE_REPRICED`)
  - optionally require manager approval above thresholds

**Why Lock by Default:**
- Prevents surprises for customers and advisors
- Keeps estimate/approval traceability intact
- Avoids retroactive policy changes affecting in-flight work

---
## Original Story (Unmodified – For Traceability)
# Issue #49 — [BACKEND] [STORY] Workexec: Handle Substitution Pricing for Part Substitutions

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Workexec: Handle Substitution Pricing for Part Substitutions

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want substitutes priced when originals are unavailable so that work continues without manual math.

## Details
- Return substitute candidates with availability + prices.
- Enforce allowed substitution types.

## Acceptance Criteria
- Candidates returned with policy flags.
- Selection captured on estimate/WO line.

## Integrations
- Workexec integrates with substitution + availability queries.

## Data / Entities
- SubstituteLink, SubstitutePolicy, PriceQuoteResponse

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