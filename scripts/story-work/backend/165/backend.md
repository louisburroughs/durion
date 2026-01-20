Title: [BACKEND] [STORY] Promotion: Generate Workorder Items from Approved Scope
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/165
Labels: story-implementation, user, type:story, domain:workexec, status:ready-for-dev

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

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
As the System, I need to generate authorized `WorkorderItem` entities from an `Estimate`'s approved scope when it is promoted to a `WorkOrder`. This process must create an immutable snapshot of all pricing, tax, and traceability details, so that the work to be performed is accurately defined and can be reliably executed, tracked, and invoiced.

## Actors & Stakeholders
- **System (Primary Actor):** The automated process that executes the business logic to create `WorkOrder` and `WorkorderItem` records from an `Estimate`.
- **Service Advisor (Initiating Actor):** The user role that triggers the promotion of an `Approved` `Estimate` to a `WorkOrder`, initiating this automated process.
- **Technician (Downstream Consumer):** Relies on the accurately created `WorkorderItem`s to understand and perform the required services.
- **Billing / Accounting (Downstream Consumer):** Uses the snapshotted financial data on the `WorkorderItem`s as the authoritative source for generating customer invoices and performing financial reconciliation.

## Preconditions
- An `Estimate` record exists with a status of `Approved`.
- The `Estimate` contains one or more `EstimateItem` records that are part of the approved scope for work.
- Each `EstimateItem` in the approved scope has fully calculated and resolved pricing and tax information.
- A valid Tax Configuration is available in the system for the transaction's context (e.g., location, customer).

## Functional Behavior
### Trigger
A `PromoteEstimateToWorkOrder` command is issued for a specific `Estimate` that is in the `Approved` state.

### Main Success Scenario
1.  The System validates that the source `Estimate` has a status of `Approved`.
2.  A new `WorkOrder` header record is created, establishing a direct link to the source `Estimate` (e.g., via `originEstimateId`).
3.  The System begins a transaction to ensure all-or-nothing creation of `WorkorderItem`s.
4.  For each `EstimateItem` in the `Estimate`'s approved scope, the System performs the following:
    a. Creates a new `WorkorderItem` record.
    b. Generates a stable, unique identifier (`workorderItemId`).
    c. Populates the `WorkorderItem` by copying data from the `EstimateItem`, including `description`, `itemType` (e.g., `PART`, `LABOR`), `quantity`, and `productId`.
    d. Creates an immutable financial snapshot by copying the `unitPrice`, `discounts`, `taxCode`, `taxRate`, and `taxAmount` from the `EstimateItem` into corresponding fields on the `WorkorderItem`. This data is now locked and independent of any future changes to pricing or tax rules.
    e. Sets the `originEstimateItemId` field on the `WorkorderItem` to the ID of the source `EstimateItem` to ensure end-to-end traceability.
    f. Sets the initial status of the new `WorkorderItem` to `Authorized`.
5.  After processing all items, the System calculates the subtotal, tax total, and grand total of the new `WorkOrder` based on the created `WorkorderItem`s.
6.  The System validates that the calculated `WorkOrder` totals exactly match the corresponding totals from the source `Estimate`.
7.  The transaction is committed, persisting the new `WorkOrder` and all associated `WorkorderItem`s.
8.  The System emits a `WorkOrderCreated` event containing the `workOrderId` and `originEstimateId`.

## Alternate / Error Flows
- **Flow: Item Product ID is No Longer Valid**
    - **Trigger:** An `EstimateItem` refers to a `productId` that no longer exists or is inactive in the Product Catalog.
    - **Behavior:** The `WorkorderItem` is still created using the description and financial data snapshotted on the `EstimateItem`. A `requiresReview` flag on the `WorkorderItem` is set to `true`. The promotion process continues, but a warning is logged.

- **Error: Missing Tax Configuration**
    - **Trigger:** The system cannot resolve a valid tax basis for one or more items during the promotion process.
    - **Behavior:** The entire promotion process MUST fail atomically. The database transaction is rolled back, and no `WorkOrder` or `WorkorderItem` records are persisted. The system emits a `WorkOrderPromotionFailed` event with the reason `MissingTaxConfiguration`.

- **Error: Mismatched Totals**
    - **Trigger:** After creating all `WorkorderItem`s, the calculated `WorkOrder` totals do not match the source `Estimate`'s totals.
    - **Behavior:** The promotion process fails. The database transaction is rolled back. The system emits a `WorkOrderPromotionFailed` event with the reason `TotalMismatch` and logs the discrepancy details for investigation.

## Business Rules
- Only items explicitly included in the `Estimate`'s approved scope shall be converted into `WorkorderItem`s.
- All financial data on a `WorkorderItem` (price, tax, etc.) is an immutable snapshot from the moment of promotion and MUST NOT be recalculated or updated from external systems (e.g., Product Catalog, Tax Engine) after creation.
- Each `WorkorderItem` MUST maintain a non-nullable reference (`originEstimateItemId`) to its source `EstimateItem`.
- The initial status for all newly created `WorkorderItem`s MUST be `Authorized`.

## Data Requirements
### Entity: `WorkorderItem`
| Field Name             | Type          | Constraints                                       | Description                                                                 |
| ---------------------- | ------------- | ------------------------------------------------- | --------------------------------------------------------------------------- |
| `workorderItemId`      | UUID          | Primary Key, Not Null                             | Unique identifier for the work order item.                                  |
| `workorderId`          | UUID          | Foreign Key (`WorkOrder`), Not Null, Indexed      | Links the item to its parent `WorkOrder`.                                   |
| `originEstimateItemId` | UUID          | Foreign Key (`EstimateItem`), Not Null, Indexed   | Traceability link to the source `EstimateItem`.                             |
| `itemSeqId`            | Integer       | Not Null                                          | Display and sorting order within the work order.                            |
| `itemType`             | Enum          | Not Null (`PART`, `LABOR`, `FEE`)                 | The classification of the work item.                                        |
| `description`          | String        | Not Null                                          | Snapshotted item description.                                               |
| `productId`            | UUID          | Foreign Key (`Product`), Nullable                | Reference to the product catalog, if applicable.                            |
| `quantity`             | Decimal       | Not Null, `> 0`                                   | The quantity of the item/service authorized.                                |
| `unitPrice`            | Money         | Not Null                                          | Snapshotted unit price at the time of promotion.                            |
| `totalPrice`           | Money         | Not Null                                          | Snapshotted total price (`quantity` * `unitPrice` - discounts).           |
| `taxSnapshot`          | JSONB/Entity  | Not Null                                          | Immutable record of tax code, rate, and calculated amount.                  |
| `status`               | Enum          | Not Null, Default: `Authorized`                   | The lifecycle status of the work item (e.g., `Authorized`, `InProgress`). |
| `requiresReview`       | Boolean       | Not Null, Default: `false`                        | Flag indicating if the item needs manual attention (e.g., invalid product). |

## Acceptance Criteria
**Scenario 1: Successful Promotion of a Standard Estimate**
- **Given** an `Estimate` with status `Approved` that contains one labor item and one part item in its scope.
- **When** a `PromoteEstimateToWorkOrder` command is executed for that estimate.
- **Then** a new `WorkOrder` record is created and linked to the source estimate.
- **And** exactly two `WorkorderItem` records are created and linked to the new `WorkOrder`.
- **And** each `WorkorderItem` has its `status` set to `Authorized`.
- **And** each `WorkorderItem` contains the correct, snapshotted price, quantity, and tax data from its corresponding `EstimateItem`.
- **And** each `WorkorderItem` has a valid `originEstimateItemId` referencing its source.

**Scenario 2: Promotion with a Non-Existent Product Catalog Item**
- **Given** an `Approved` `Estimate` containing an item for a `productId` that has been deactivated in the Product Catalog.
- **When** the estimate is promoted to a work order.
- **Then** a new `WorkOrder` and all corresponding `WorkorderItem`s are successfully created.
- **And** the specific `WorkorderItem` for the deactivated product has its `requiresReview` flag set to `true`.
- **And** all other `WorkorderItem`s have `requiresReview` set to `false`.

**Scenario 3: Promotion Fails Due to Missing Tax Configuration**
- **Given** an `Approved` `Estimate` ready for promotion.
- **And** the system's tax configuration is missing or invalid for the context of the estimate.
- **When** an attempt is made to promote the estimate to a work order.
- **Then** the promotion process must fail.
- **And** no `WorkOrder` or `WorkorderItem` records are created or persisted in the database.
- **And** a `WorkOrderPromotionFailed` event with reason `MissingTaxConfiguration` is emitted.

## Audit & Observability
- **Events:**
    - On success, a `WorkOrderCreated` event MUST be published to the system's event bus. The payload must include `workOrderId`, `originEstimateId`, and `customerId`.
    - On failure, a `WorkOrderPromotionFailed` event MUST be published. The payload must include `originEstimateId` and a structured `reason` code (e.g., `MissingTaxConfiguration`, `TotalMismatch`, `InvalidEstimateState`).
- **Logging:**
    - A structured log entry at the `INFO` level should be recorded upon successful creation of a `WorkOrder`.
    - A structured log entry at the `WARN` level should be recorded if a `WorkorderItem` is created with `requiresReview=true`.
    - A structured log entry at the `ERROR` level must be recorded for any failed promotion, including the reason and relevant identifiers.
- **Audit Trail:**
    - The creation of the `WorkOrder` and all `WorkorderItem`s must be recorded in an immutable audit log, capturing the timestamp and the principal (System/User) that initiated the action.

## Open Questions
- none

## Original Story (Unmodified – For Traceability)
# Issue #165 — [BACKEND] [STORY] Promotion: Generate Workorder Items from Approved Scope

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Promotion: Generate Workorder Items from Approved Scope

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
System

## Trigger
A workorder header is created from an approved estimate.

## Main Flow
1. System iterates through approved scope line items.
2. System creates Workorder Items for parts and labor with stable identifiers.
3. System copies pricing/tax snapshot fields needed for downstream invoicing and variance explanations.
4. System marks items as 'Authorized' and sets execution flags (e.g., required vs optional).
5. System validates totals and quantity integrity.

## Alternate / Error Flows
- Approved scope contains an item no longer valid in catalog → allow as snapshot item and flag for review.
- Tax configuration missing → block promotion and require correction.

## Business Rules
- Only approved items are created on the workorder.
- Snapshot pricing/tax fields are preserved.
- Workorder items maintain traceability to estimate items.

## Data Requirements
- Entities: WorkorderItem, ApprovedScope, EstimateItem, TaxSnapshot
- Fields: itemSeqId, originEstimateItemId, authorizedFlag, quantity, unitPrice, taxCode, taxAmount, snapshotVersion

## Acceptance Criteria
- [ ] Workorder items match approved scope in quantity and pricing.
- [ ] Workorder items carry traceability back to estimate items.
- [ ] Promotion fails if required tax basis is missing (policy).

## Notes for Agents
Design for variance explanations later: preserve the numbers, not just totals.


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