Title: [BACKEND] [STORY] Promotions: Create Promotion Offer (Basic)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/97
Labels: type:story, domain:pricing, status:needs-review

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
**As an** Account Manager,
**I want to** define and create a new promotion with specific discount rules, validity dates, and usage limits,
**so that** this promotion can be made available for application to work orders for eligible customers, driving sales and customer engagement.

## Actors & Stakeholders
- **Account Manager**: The primary actor who creates and manages promotions.
- **Workorder Execution System**: A downstream system that needs to query and retrieve active promotions to apply them to customer work orders or estimates.
- **Customer**: The beneficiary of the promotion.

## Preconditions
- The Account Manager is authenticated and has the necessary permissions to access the Promotion Management feature.
- The system is in a state ready to accept new promotion definitions.

## Functional Behavior
### 4.1. Promotion Creation
The Account Manager initiates the creation of a new promotion. The system presents a form to capture the promotion's definition, including:
- **Promotion Code**: A unique, human-readable identifier (e.g., `SUMMER2024`).
- **Description**: An internal-facing description of the promotion's purpose.
- **Store Code**: An identifier for the store or location where the promotion is valid.
- **Start Date & End Date**: The date range during which the promotion is considered active.
- **Promotion Type & Value**:
    - Percentage discount on labor (`%`)
    - Percentage discount on parts (`%`)
    - Fixed monetary amount discount on the total invoice (`$`)
- **Usage Limit**: An optional numeric value defining the maximum number of times the promotion can be used in total.
- **Initial State**: A new promotion is created in a `Draft` state by default and is not publicly available until explicitly activated.

### 4.2. Promotion State Management
- **Activation**: The Account Manager can activate a `Draft` or `Inactive` promotion. Upon activation, its state changes to `Active`, making it available for application by downstream systems, provided the current date is within its valid date range.
- **Deactivation**: The Account Manager can deactivate an `Active` promotion. Its state changes to `Inactive`, immediately preventing any new applications of the promotion, regardless of the current date.

## Alternate / Error Flows
- **Duplicate Promotion Code**: If the Account Manager attempts to create a promotion with a `Promotion Code` that already exists, the system shall reject the request and display a "Promotion code must be unique" error.
- **Invalid Date Range**: If the specified `Start Date` is after the `End Date`, the system shall reject the request and display a "Start date must be on or before the end date" error.
- **Invalid Value**: If a negative value is entered for a discount amount or usage limit, the system shall reject the request with a validation error.
- **Activation Attempt on Expired Promotion**: If an attempt is made to activate a promotion whose `End Date` is in the past, the system shall prevent the activation and indicate that the promotion has expired.

## Business Rules
- A `Promotion Code` must be unique across all promotions in the system.
- The `Start Date` of a promotion must be on or before its `End Date`.
- A promotion is only considered "available for use" if its state is `Active` AND the current system date is within its `Start Date` and `End Date` (inclusive).
- Once a promotion is created, its `Promotion Code` cannot be changed.
- Promotions are created in a `Draft` state and must be explicitly moved to `Active` to become available.

## Data Requirements
The system must persist a `PromotionOffer` entity with the following attributes:

| Field | Type | Required | Description |
|---|---|---|---|
| `promotionOfferId` | UUID | Yes | Unique system identifier for the promotion. |
| `promotionCode` | String | Yes | Unique, human-readable code for the promotion. |
| `description` | String | Yes | Internal description of the promotion. |
| `storeCode` | String | Yes | Identifier of the location where the promotion is valid. |
| `startDate` | Date | Yes | The first day the promotion is valid. |
| `endDate` | Date | Yes | The last day the promotion is valid. |
| `promotionType` | Enum | Yes | Type of discount. Values: `PERCENT_LABOR`, `PERCENT_PARTS`, `FIXED_INVOICE`. |
| `promotionValue` | Decimal | Yes | The numeric value of the discount (e.g., 10.00 for 10% or $10). |
| `usageLimit` | Integer | No | The total number of times the promotion can be used. Null means unlimited. |
| `status` | Enum | Yes | The current state of the promotion. Values: `DRAFT`, `ACTIVE`, `INACTIVE`, `EXPIRED`. |

## Acceptance Criteria

### Scenario: Successfully Create a New Percentage-Based Promotion
- **Given** I am an Account Manager with permissions to create promotions
- **When** I submit a new promotion with a unique code `LABOR15`, a `promotionType` of `PERCENT_LABOR`, a `promotionValue` of 15, and a valid start/end date range
- **Then** the system successfully creates the new promotion
- **And** the promotion is assigned a unique `promotionOfferId`
- **And** its initial status is `DRAFT`.

### Scenario: Successfully Activate a Draft Promotion
- **Given** a promotion exists with the code `LABOR15` and a status of `DRAFT`
- **When** I request to activate this promotion
- **Then** the system updates the promotion's status to `ACTIVE`.

### Scenario: Attempt to Create a Promotion with a Duplicate Code
- **Given** a promotion with the code `SUMMER2024` already exists
- **When** I attempt to create a new promotion with the code `SUMMER2024`
- **Then** the system rejects the request
- **And** I receive an error message stating "Promotion code must be unique".

### Scenario: Attempt to Create a Promotion with an Invalid Date Range
- **Given** I am creating a new promotion
- **When** I set the `Start Date` to "2024-08-01" and the `End Date` to "2024-07-31"
- **Then** the system rejects the request
- **And** I receive an error message stating "Start date must be on or before the end date".

## Audit & Observability
- **Audit Log**: Every creation, activation, and deactivation of a promotion must be logged. The log entry should include the `promotionOfferId`, the acting user's ID, the timestamp, and the change made (e.g., "status: DRAFT -> ACTIVE").
- **Metrics**: The system should expose a metric for the total number of promotions in each status (`DRAFT`, `ACTIVE`, `INACTIVE`).

## Open Questions
1.  **Customer Eligibility**: The original story mentions applying promotions to "eligible customers". How is eligibility defined? Is it a list of customer IDs, a customer segment/tag, or another attribute that must be attached to the promotion? This contract is critical for the Workorder domain integration.
2.  **Promotion Type Specificity**: The story mentions "% off labor", "% off parts", and "fixed amount off invoice". Can a single promotion offer contain rules for more than one of these (e.g., 10% off parts AND 15% off labor), or is a promotion strictly one type? This rewrite assumes a promotion is only one type.
3.  **Store Code Scope**: Is the `storeCode` a mandatory field? Does an empty/null `storeCode` imply the promotion is valid at all locations?
4.  **Usage Limit Scope**: Is the optional `usageLimit` a global counter for the promotion, or is it tracked on a per-customer basis?

---
## Original Story (Unmodified – For Traceability)
# Issue #97 — [BACKEND] [STORY] Promotions: Create Promotion Offer (Basic)

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Promotions: Create Promotion Offer (Basic)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Account Manager**, I want **to create a simple promotion (discount amount/percent) with start/end dates** so that **I can apply it to estimates for eligible customers**.

## Details
- Offer types: % off labor, % off parts, fixed amount off invoice.
- Store code, description, active dates, optional usage limit.

## Acceptance Criteria
- Create/activate/deactivate offer.
- Validate date range.
- Unique code enforced.

## Integration Points (Workorder Execution)
- Workorder Execution can query active offers for a customer.

## Data / Entities
- PromotionOffer

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