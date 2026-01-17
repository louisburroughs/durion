Title: [BACKEND] [STORY] Estimate: Add Parts to Estimate
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/173
Labels: type:story, domain:workexec, status:needs-review

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

**As a** Service Advisor,
**I want to** add parts from the catalog or as non-catalog items to a work estimate,
**so that** I can provide a customer with an accurate and complete quote for the parts required for a service.

## Actors & Stakeholders

- **Primary Actor**:
  - `Service Advisor`: The user directly interacting with the POS to build and manage the estimate.
- **System Actors**:
  - `Work Execution Service`: Owns the lifecycle and state of the `Estimate`.
  - `Inventory Service`: Provides the product catalog for lookups.
  - `Pricing Service`: Provides list prices, discounts, and markups for catalog items.
- **Stakeholders**:
  - `Customer`: The recipient of the estimate.
  - `Service Manager`: Responsible for operational efficiency and profitability, and sets policies for price overrides.
  - `Parts Manager`: Responsible for the accuracy of the parts catalog and pricing.
  - `Auditor`: Requires a clear trail of all financial changes made to an estimate.

## Preconditions

1. A valid `Estimate` exists in a modifiable state (e.g., `DRAFT`).
2. The `Service Advisor` is authenticated and possesses the necessary permissions to modify the specified `Estimate`.
3. The system has access to the `Inventory Service` to search the parts catalog.
4. The system has access to the `Pricing Service` to retrieve default pricing information.

## Functional Behavior

### Scenario 1: Add a Catalog Part to an Estimate

- **Trigger**: The Service Advisor initiates the "Add Part" action on a `DRAFT` estimate.
- **Steps**:
  1. The system presents an interface to search the product catalog.
  2. The Service Advisor searches for a part by `partNumber`, `description`, or `category`.
  3. A list of matching parts is returned from the `Inventory Service`.
  4. The Service Advisor selects a part from the results and specifies a `quantity` (must be > 0).
  5. The `Work Execution Service` requests the default price for the selected part and quantity from the `Pricing Service`.
  6. The `Pricing Service` returns the `unitPrice`, including any applicable standard discounts or markups.
  7. The `Work Execution Service` creates a new `EstimateItem` of type `PART` and links it to the `Estimate`.
  8. The system recalculates all estimate totals (`subtotal`, `tax`, `grandTotal`) transactionally.
- **Outcome**: A new part line item is successfully added to the estimate, and all totals are updated.

### Scenario 2: Add a Part with an Overridden Price

- **Trigger**: The Service Advisor adds a part but needs to adjust the system-calculated price.
- **Steps**:
  1. Following the steps in Scenario 1, a part is selected and added.
  2. The Service Advisor initiates a price override action on the newly added line item.
  3. The system verifies if the Service Advisor has the `PRICE_OVERRIDE` permission.
  4. If permitted, the system allows the `unitPrice` field to be edited.
  5. The Service Advisor enters the new `unitPrice`.
  6. If the business policy requires a reason for overrides, the system prompts for an `overrideReasonCode` from a pre-configured list.
  7. The Service Advisor selects a valid reason.
  8. The system updates the `EstimateItem` with the new price and reason, and recalculates all estimate totals.
- **Outcome**: The part's price is updated, the reason is recorded, and estimate totals are recalculated.

### Scenario 3: Add a Non-Catalog Part

- **Trigger**: A required part is not found in the catalog search.
- **Steps**:
  1. The system detects a "part not found" condition during the search.
  2. The system checks if the `ALLOW_NON_CATALOG_PARTS` policy is enabled.
  3. If enabled, the system presents an interface for manual entry.
  4. The Service Advisor must provide a `description` and `unitPrice`. A `partNumber` may be entered but is not validated against the catalog.
  5. The Service Advisor enters a `quantity` (> 0).
  6. The system creates a new `EstimateItem` with the `isNonCatalog` flag set to `true`.
  7. The system recalculates all estimate totals.
- **Outcome**: A non-catalog part line item is added to the estimate with manually entered details, and totals are updated.

## Alternate / Error Flows

- **Part Not Found**:
  - **Condition**: A search yields no results.
  - **System Response**: If non-catalog entry is disabled, display a "Part not found" message. If enabled, provide the option to add a non-catalog part (see Scenario 3).
- **Invalid Quantity**:
  - **Condition**: User enters a quantity that is less than or equal to zero, or non-numeric.
  - **System Response**: Reject the input, display a validation error message (e.g., "Quantity must be a number greater than 0"), and prevent the item from being added.
- **Price Override Not Permitted**:
  - **Condition**: Service Advisor attempts to override a price without the required `PRICE_OVERRIDE` permission.
  - **System Response**: Block the action and display an informative message (e.g., "You do not have permission to override prices.").
- **Price Override Reason Required**:
  - **Condition**: User performs a permitted price override but fails to provide a reason code when one is required by policy.
  - **System Response**: Reject the change, display a validation error message (e.g., "A reason code is required for all price overrides."), and keep the original price.
- **Estimate Not in Modifiable State**:
  - **Condition**: User attempts to add a part to an estimate that is not in `DRAFT` status (e.g., it is `APPROVED` or `INVOICED`).
  - **System Response**: Block the action and display an error message (e.g., "This estimate cannot be modified as it has already been approved.").

## Business Rules

- **BR-1 (State Gate)**: Parts can only be added, edited, or removed while the `Estimate` is in the `DRAFT` state.
- **BR-2 (Transactional Totals)**: All estimate-level totals (`subtotal`, `tax`, `grandTotal`) must be recalculated within the same transaction as any line item addition, modification, or deletion. The estimate must remain consistent at all times.
- **BR-3 (Pricing Authority)**: The `Pricing Service` is the authority for the initial `unitPrice` of catalog items. Any deviation must be explicitly stored as an `overridePrice`.
- **BR-4 (Configurable Policies)**: The following behaviors must be controlled by configurable business policies:
  - `ALLOW_NON_CATALOG_PARTS` (Boolean)
  - `REQUIRE_REASON_FOR_OVERRIDE` (Boolean)
- **BR-5 (Permissions)**: Modifying an estimate item's price requires a specific, checkable user permission (e.g., `workexec.estimate.overridePrice`).
- **BR-6 (Non-Catalog Identifier)**: Non-catalog items must have a stable, system-generated internal identifier for traceability, even without a `productId`. The `isNonCatalog` flag must be set to `true`.

## Data Requirements

### `WorkExecution.EstimateItem` Entity

| Field Name | Type | Constraints | Description |
|---|---|---|---|
| `estimateItemId` | UUID | Primary Key, Not Null | Unique identifier for the estimate line item. |
| `estimateId` | UUID | Foreign Key, Not Null | Links to the parent `Estimate`. |
| `itemSeqId` | Integer | Not Null | Display order of the item within the estimate. |
| `itemType` | Enum | Not Null, Default: `PART` | Type of line item (e.g., 'PART', 'LABOR'). |
| `productId` | UUID | Nullable | FK to the `Inventory.Product` entity. Null for non-catalog items. |
| `description` | String | Not Null | Product description. Copied from catalog or manually entered. |
| `partNumber` | String | Nullable | Part number. Copied from catalog or manually entered. |
| `quantity` | Decimal | Not Null, > 0 | Quantity of the part being quoted. |
| `unitPrice` | Money | Not Null | The unit price fetched from the Pricing Service. |
| `overrideUnitPrice` | Money | Nullable | The manually overridden price, if applicable. |
| `overrideReasonCode` | String | Nullable | The reason code for the price override. |
| `isNonCatalog` | Boolean | Not Null, Default: `false` | Flag indicating if the part is from the catalog. |
| `taxCodeSnapshot` | String | Not Null | The tax code applicable at the time of addition. |

### Data Read from Other Domains
- **`Inventory.Product`**: `productId`, `partNumber`, `description`.
- **`Pricing.Price`**: `unitPrice`, `applicableDiscounts`.

## Acceptance Criteria

- **AC-1: Add a standard catalog part**
  - **Given** I am viewing an estimate in `DRAFT` status
  - **When** I search for a valid catalog part, select it, and specify a quantity of 2
  - **Then** a new line item for that part is added to the estimate
  - **And** the `unitPrice` is populated from the Pricing Service
  - **And** the estimate's `subtotal` and `grandTotal` are correctly recalculated.

- **AC-2: Override a part's price with permission and a reason code**
  - **Given** I am a Service Advisor with `PRICE_OVERRIDE` permission
  - **And** I have added a part to a `DRAFT` estimate
  - **And** the system policy requires a reason code for overrides
  - **When** I change the `unitPrice` of the part and select a valid `overrideReasonCode`
  - **Then** the `overrideUnitPrice` is saved with the new price
  - **And** the `overrideReasonCode` is recorded
  - **And** the estimate totals are recalculated based on the new price.

- **AC-3: Add a non-catalog part**
  - **Given** the `ALLOW_NON_CATALOG_PARTS` policy is enabled
  - **And** I am viewing an estimate in `DRAFT` status
  - **When** I choose to add a non-catalog part and provide a description, a quantity of 1, and a unit price
  - **Then** a new line item is added to the estimate with `isNonCatalog` set to `true`
  - **And** the estimate totals are recalculated.

- **AC-4: Fail to add a part with invalid quantity**
  - **Given** I am viewing an estimate in `DRAFT` status
  - **When** I attempt to add a part with a quantity of 0
  - **Then** the system prevents the line item from being added
  - **And** a validation message "Quantity must be a number greater than 0" is displayed.

- **AC-5: Fail to override a price without permission**
  - **Given** I am a Service Advisor without `PRICE_OVERRIDE` permission
  - **And** I have added a part to a `DRAFT` estimate
  - **When** I attempt to change the `unitPrice` of the part
  - **Then** the action is blocked
  - **And** a message "You do not have permission to override prices" is displayed.

## Audit & Observability

- **Audit Logs**:
  - An immutable audit event **MUST** be created for:
    - `ESTIMATE_ITEM_CREATED`: On successful addition of any part. Log `estimateId`, `estimateItemId`, `productId` (if any), `quantity`, `unitPrice`.
    - `ESTIMATE_ITEM_MODIFIED`: On any change to a line item. Log `estimateItemId` and a `changeset` containing the field name, previous value, and new value (e.g., for `quantity`, `overrideUnitPrice`).
  - All audit events must include the `userId` of the performing `Service Advisor`, a `timestamp`, and the `source` service (`work-execution-service`).
- **Metrics**:
  - `estimate.parts.added.count`: Counter for parts added (can be tagged by `catalog` vs `non_catalog`).
  - `estimate.price_overrides.count`: Counter for every price override event.
- **Logging**:
  - `INFO` level logs for successful addition/modification of parts.
  - `WARN` level logs for failed attempts due to business rule violations (e.g., invalid quantity, permission denied).

## Open Questions

1.  **Price Override Permissions**: What is the specific mechanism for granting `PRICE_OVERRIDE` permission? Is it role-based (e.g., "Service Manager" role) or a direct permission assignment?
2.  **Override Reason Codes**: What is the definitive list of valid `overrideReasonCode` values (e.g., `PRICE_MATCH`, `CUSTOMER_GOODWILL`, `DAMAGED_ITEM`)? Is this list configurable?
3.  **Non-Catalog Policy Scope**: Is the `ALLOW_NON_CATALOG_PARTS` policy a system-wide setting, or can it be configured per location/dealership?
4.  **Tax Code Source**: The original story mentions a `taxCode` field. What is the source of truth for this value? Does it come from the `Product` entity, the `Customer` record, or a location-based tax rule engine? This needs to be defined before implementation.

---
## Original Story (Unmodified – For Traceability)
# Issue #173 — [BACKEND] [STORY] Estimate: Add Parts to Estimate

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Estimate: Add Parts to Estimate

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
Service Advisor

## Trigger
A Draft estimate exists and parts need to be quoted.

## Main Flow
1. User searches parts catalog by part number, description, or category.
2. User selects a part and specifies quantity.
3. System defaults unit price from configured price list and applies discounts/markups as configured.
4. User optionally overrides price if permitted and provides a reason code if required.
5. System adds the part line item and recalculates totals.

## Alternate / Error Flows
- Part not found → allow controlled non-catalog part entry (if enabled) with mandatory description.
- Quantity invalid (<=0) → block and prompt correction.
- Price override not permitted → block and show policy message.

## Business Rules
- Each part line item references a part number (or controlled non-catalog identifier).
- Totals must be recalculated on line change.
- Price overrides require permission and may require reason codes.

## Data Requirements
- Entities: Estimate, EstimateItem, Product, PriceList, DiscountRule, AuditEvent
- Fields: itemSeqId, productId, partNumber, quantity, unitPrice, discountAmount, taxCode, isNonCatalog, overrideReason

## Acceptance Criteria
- [ ] User can add a catalog part line item to a Draft estimate.
- [ ] Totals update immediately after adding or editing part items.
- [ ] Audit records who changed quantity/price.

## Notes for Agents
Model part items so later promotion preserves stable identifiers and tax snapshots.


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