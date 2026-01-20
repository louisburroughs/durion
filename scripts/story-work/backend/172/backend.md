Title: [BACKEND] [STORY] Estimate: Add Labor to Estimate
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/172
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
As a Service Advisor, I want to add standardized or custom labor items to a draft estimate so that I can accurately quote the cost of services for a customer.

## Actors & Stakeholders
- **Primary Actor:** Service Advisor (The user performing the action).
- **System Actor:** Point of Sale (POS) System.
- **Beneficiary:** Customer (Receives the accurate estimate).
- **Stakeholder:** Pricing Domain (Provides the business rules and data for labor rate determination).
- **Stakeholder:** Audit & Compliance (Requires traceability of all financial changes to an estimate).

## Preconditions
1.  The user is authenticated as a Service Advisor with permissions to create and modify estimates.
2.  An `Estimate` entity exists with `status: Draft`.
3.  The Service Catalog is available and accessible to the system.
4.  Configured labor rate rules are available from the Pricing domain.

## Functional Behavior
### Main Success Scenario: Adding a Standard Labor Item
1.  **Trigger:** The Service Advisor initiates the "Add Labor" action on a `Draft` estimate.
2.  The system presents an interface to search the `ServiceCatalog`.
3.  The Service Advisor searches for a labor service by its service code or description.
4.  The Service Advisor selects a matching service from the results.
5.  If the service is time-based, the Service Advisor enters the number of labor units (e.g., hours).
6.  If the service is flat-rate, the units and price are pre-defined.
7.  The system requests the default labor rate from the Pricing service/module, providing context (e.g., `shopId`, `serviceCode`, `technicianClass`).
8.  The system creates a new `EstimateItem` of `type: LABOR`, linking it to the `Estimate`.
9.  The system populates the `EstimateItem` with the `serviceCode`, `description`, `laborUnits`, `laborRate`, and any applicable tax codes.
10. The system recalculates the `Estimate` sub-totals and grand total.
11. The Service Advisor can optionally add notes specific to this labor item.
12. **Outcome:** A new labor line item is successfully added to the estimate, and all totals are updated in real-time.

## Alternate / Error Flows
### Flow 1: Service Not Found (With Custom Entry Allowed)
1.  The Service Advisor searches for a service that does not exist in the `ServiceCatalog`.
2.  The system returns no results.
3.  The system presents an option to "Add Custom Labor" (contingent on business rule `BR-4`).
4.  The Service Advisor enters a mandatory `description`, `laborUnits`, and a `laborRate`.
5.  The system proceeds from step 8 of the Main Success Scenario, marking the `EstimateItem` with a specific source flag (e.g., `source: CUSTOM`).

### Flow 2: Invalid Labor Units
1.  The Service Advisor enters a non-positive value (e.g., `0` or `-1`) for `laborUnits`.
2.  The system rejects the input and displays a validation error message: "Labor units must be a positive number."
3.  The system blocks the addition of the line item until the error is corrected.

### Flow 3: Labor Rate Not Found
1.  The system requests a default labor rate from the Pricing service/module, but no matching rule is found for the given context.
2.  **[OPEN QUESTION]** The system's behavior is currently undefined. See Open Questions. The system should either block the addition with an error or allow a manual rate override, pending clarification.

## Business Rules
- **BR-1:** Labor items can only be added, edited, or removed while the `Estimate` is in `status: Draft`.
- **BR-2:** Each labor `EstimateItem` must be associated with a unique, system-generated `itemSeqId` within the context of its `Estimate`.
- **BR-3:** The determination of the default `laborRate` must be deterministic and based on a clearly defined hierarchy of rules managed by the Pricing domain.
- **BR-4:** The ability to add custom (non-catalog) labor items is controlled by a system-level configuration setting. If disabled, Flow 1 is not possible.
- **BR-5:** Any change to a labor line item (addition, update, removal) must trigger a full recalculation of the `Estimate` totals (subtotal, tax, grand total).
- **BR-6:** A custom labor item must have a non-empty `description`.

## Data Requirements
| Entity | Attribute | Type | Notes |
| :--- | :--- | :--- | :--- |
| `Estimate` | `estimateId` | UUID | Primary Key |
| | `status` | Enum | e.g., `Draft`, `Presented`, `Approved` |
| | `subTotal` | Money | Sum of all line item extended prices. |
| | `totalTax` | Money | Sum of all taxes. |
| | `grandTotal` | Money | `subTotal` + `totalTax`. |
| `EstimateItem` | `estimateItemId` | UUID | Primary Key |
| | `estimateId` | UUID | Foreign Key to `Estimate`. |
| | `itemSeqId` | Integer | Sequential identifier within the estimate. |
| | `itemType` | Enum | `LABOR` |
| | `serviceCode` | String | Foreign Key to `ServiceCatalog` or a controlled code for custom items. |
| | `description` | String | Populated from catalog or user-entered for custom. |
| | `laborUnits` | Decimal(10,2) | e.g., hours. Must be > 0. |
| | `unitPrice` | Money | The labor rate applied. |
| | `extendedPrice`| Money | `laborUnits` * `unitPrice`. |
| | `isFlatRate` | Boolean | Flag indicating if this is a flat-rate item. |
| | `source` | Enum | `CATALOG` or `CUSTOM`. |
| | `notes` | Text | Optional notes for the technician. |

## Acceptance Criteria
### Scenario 1: Add a Standard Time-Based Labor Item
- **Given** I am a Service Advisor viewing an estimate in "Draft" status.
- **When** I search for and select a time-based labor service with code "SVC-OIL-CHG".
- **And** I enter `1.5` for the labor units.
- **Then** a new labor line item for "SVC-OIL-CHG" is added to the estimate.
- **And** the line item shows `1.5` units and the correctly defaulted labor rate.
- **And** the estimate's subtotal and grand total are recalculated and displayed correctly.

### Scenario 2: Add a Custom Labor Item
- **Given** I am a Service Advisor viewing an estimate in "Draft" status.
- **And** the system is configured to allow custom labor entry.
- **When** I choose to add a custom labor item.
- **And** I enter the description "Diagnose engine noise", `2.0` labor units, and a manual rate of `$150.00`.
- **Then** a new labor line item is added to the estimate with the specified description and values.
- **And** the estimate's totals are correctly updated.

### Scenario 3: Attempt to Add Labor with Invalid Units
- **Given** I am a Service Advisor viewing an estimate in "Draft" status.
- **When** I select a labor service and enter `-1` for the labor units.
- **Then** the system displays an error message "Labor units must be a positive number."
- **And** the labor item is not added to the estimate.
- **And** the estimate's totals remain unchanged.

## Audit & Observability
- **Audit Log:** An immutable audit event `EstimateLaborItemAdded` must be recorded when a labor item is successfully added. The event payload must contain `estimateId`, `estimateItemId`, `serviceCode`, `laborUnits`, `laborRate`, and the `userId` of the Service Advisor.
- **Metrics:**
    - `estimate.labor_item.add.success`: Counter for successful additions.
    - `estimate.labor_item.add.failure`: Counter for failed attempts (e.g., validation errors).
    - `estimate.labor_item.add.latency`: Histogram measuring the duration of the end-to-end add operation.

## Open Questions
1.  **Labor Rate Precedence:** What is the precise hierarchy and fallback logic for determining the default labor rate? (e.g., Does a customer-specific rate override a shop-level rate? What happens if no rate rule matches at all? Should it fail, or prompt for manual entry?)
2.  **Flat-Rate Behavior:** For flat-rate labor items selected from the catalog, are the `laborUnits` and `laborRate` fields editable, or are they locked to the catalog definition?

---
## Original Story (Unmodified – For Traceability)
# Issue #172 — [BACKEND] [STORY] Estimate: Add Labor to Estimate

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Estimate: Add Labor to Estimate

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
A Draft estimate exists and labor/services need to be quoted.

## Main Flow
1. User searches service catalog by service code or description.
2. User selects a service and specifies hours/units or selects a flat-rate option.
3. System defaults labor rate based on shop, role/class, and pricing rules.
4. System adds the labor line item and recalculates totals.
5. User adds notes/instructions if required for execution.

## Alternate / Error Flows
- Service not found → allow controlled custom service entry (if enabled) with required description and labor units.
- Labor units invalid (<=0) → block and prompt correction.

## Business Rules
- Each labor line item references a service code (or controlled custom code).
- Labor rate defaulting must be deterministic (policy-driven).
- Totals must be recalculated on labor line changes.

## Data Requirements
- Entities: Estimate, EstimateItem, ServiceCatalog, LaborRateRule, AuditEvent
- Fields: itemSeqId, serviceCode, laborUnits, laborRate, flatRateFlag, notes, taxCode

## Acceptance Criteria
- [ ] User can add labor/service line items to a Draft estimate.
- [ ] Labor pricing defaults correctly per configured rules.
- [ ] Totals update immediately after adding/editing labor items.

## Notes for Agents
Keep labor structure compatible with time-based and flat-rate models.


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