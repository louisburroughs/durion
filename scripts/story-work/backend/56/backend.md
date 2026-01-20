Title: [BACKEND] [STORY] Master: Manage UOM and Pack/Case Conversions
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/56
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
**I want to** define and manage standard Unit of Measure (UOM) conversions for products,
**So that** inventory, work execution, and sales transactions can be processed consistently and accurately, regardless of whether an item is handled as an individual unit ('Each'), a 'Pack', or a 'Case'.

This capability establishes the system of record for how different units of measure relate to one another for any given product category.

## Actors & Stakeholders

-   **Primary Actor**: `Product Administrator` - Responsible for defining and maintaining accurate product master data, including UOM conversions.
-   **System Stakeholders**:
    -   `Inventory System`: Consumes UOM conversions to accurately track stock levels (e.g., receiving a case and incrementing stock by the number of individual units).
    -   `Work Execution System`: Consumes UOM conversions to correctly allocate parts to work orders (e.g., a job requires 2 individual units, but parts are pulled from a pack of 6).
    -   `Pricing System`: May consume UOM conversions to calculate per-unit pricing when items are sold in different packaging.
-   **Observing Actor**: `Auditor` - Reviews change logs for UOM conversions to ensure data integrity and compliance.

## Preconditions

1.  The `Product Administrator` is authenticated and has the required permissions to manage UOM data.
2.  A foundational list of base Units of Measure (e.g., 'EA' for Each, 'CS' for Case, 'PK' for Pack) exists in the system.

## Functional Behavior

### 1. Define a New UOM Conversion

-   **Trigger**: The `Product Administrator` initiates the creation of a new UOM conversion.
-   **Action**: The administrator specifies a "From" UOM, a "To" UOM (typically the base unit, e.g., 'Each'), and a numerical `conversionFactor`.
    -   *Example*: From 'Case' (CS) to 'Each' (EA) with a factor of 12.
-   **Outcome**: The system validates the input against the business rules and, if valid, persists the new conversion record. The conversion is immediately available for use by other systems.

### 2. View UOM Conversions

-   **Trigger**: The `Product Administrator` navigates to the UOM management interface.
-   **Action**: The system presents a queryable list of all defined UOM conversions.
-   **Outcome**: The administrator can view, sort, and filter existing conversions by 'From' UOM, 'To' UOM, or other attributes.

### 3. Update an Existing UOM Conversion

-   **Trigger**: The `Product Administrator` selects an existing conversion to modify.
-   **Action**: The administrator changes the `conversionFactor`. (Note: Changing the 'From' or 'To' UOMs is typically disallowed; a new conversion should be created instead).
-   **Outcome**: The system validates and saves the change, creating an audit trail of the modification.

### 4. Deactivate a UOM Conversion

-   **Trigger**: The `Product Administrator` identifies a conversion that is no longer in use or was created in error.
-   **Action**: The administrator deactivates the UOM conversion. Hard deletion is disallowed to maintain historical data integrity.
-   **Outcome**: The conversion is marked as inactive and is no longer used for new transactions. Existing historical transactions remain unaffected.

## Alternate / Error Flows

-   **Invalid Conversion Factor**: If the user attempts to create or update a conversion with a zero, negative, or non-numeric factor, the system must reject the change and present a clear error message.
-   **Duplicate Conversion**: If the user attempts to create a conversion for a 'From'/'To' UOM pair that already exists, the system must reject the creation and notify the user that a conversion for this pair is already defined.
-   **Self-Referential Conversion**: The system must prevent the creation of a conversion where the 'From' UOM and 'To' UOM are the same, unless the factor is exactly 1.

## Business Rules

| Rule ID   | Description                                                                                                                              |
| :-------- | :--------------------------------------------------------------------------------------------------------------------------------------- |
| R-UOM-01  | The `conversionFactor` must be a positive, non-zero decimal number.                                                                        |
| R-UOM-02  | A unique conversion rule must exist for any given pair of a 'From' UOM and a 'To' UOM.                                                   |
| R-UOM-03  | The system must ensure that conversions are implicitly reversible. If 1 CS = 12 EA, the system must be able to calculate that 1 EA = 1/12 CS. |
| R-UOM-04  | UOM conversions cannot be hard-deleted to preserve the integrity of historical records. They may only be deactivated.                       |

## Data Requirements

The `UomConversion` entity shall contain at least the following attributes:

| Field Name         | Data Type | Description                                                                 | Required |
| :----------------- | :-------- | :-------------------------------------------------------------------------- | :------- |
| `id`               | UUID      | Unique identifier for the conversion record.                                | Yes      |
| `fromUomId`        | UUID      | Foreign key to the `UnitOfMeasure` table for the source unit (e.g., 'Case').  | Yes      |
| `toUomId`          | UUID      | Foreign key to the `UnitOfMeasure` table for the target unit (e.g., 'Each').  | Yes      |
| `conversionFactor` | Decimal   | The multiplier to convert from the `fromUom` to the `toUom`.                | Yes      |
| `isActive`         | Boolean   | A flag indicating if the conversion is currently active. Defaults to `true`.  | Yes      |
| `audit`            | Object    | Standard audit fields (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`). | Yes      |

## Acceptance Criteria

### AC-1: Successful Creation of a Valid UOM Conversion

-   **Given** I am an authenticated `Product Administrator`
-   **And** a UOM for 'Case' (CS) and 'Each' (EA) exists
-   **When** I create a new UOM conversion from 'CS' to 'EA' with a factor of `24`
-   **Then** the system successfully saves the new conversion
-   **And** a subsequent query for the conversion factor from 'CS' to 'EA' returns `24`.

### AC-2: Rejection of an Invalid (Zero-Factor) Conversion

-   **Given** I am an authenticated `Product Administrator`
-   **When** I attempt to create a UOM conversion from 'Pack' (PK) to 'Each' (EA) with a factor of `0`
-   **Then** the system must reject the request
-   **And** display an error message stating that the conversion factor must be a positive number.

### AC-3: Rejection of a Duplicate Conversion

-   **Given** I am an authenticated `Product Administrator`
-   **And** a conversion from 'Case' (CS) to 'Each' (EA) already exists
-   **When** I attempt to create another conversion from 'CS' to 'EA'
-   **Then** the system must reject the request
-   **And** display an error message indicating that this conversion already exists.

### AC-4: System Enforces Implicit Reversibility

-   **Given** a UOM conversion exists where 1 'Case' = `12` 'Each'
-   **When** an internal system component requests the conversion from 'Each' to 'Case'
-   **Then** the system must correctly calculate and provide the inverse factor (1/12 or `0.0833...`).

### AC-5: All Changes are Audited

-   **Given** I am an authenticated `Product Administrator`
-   **When** I create a new UOM conversion OR update an existing one
-   **Then** an entry must be created in the `AuditLog`
-   **And** the log entry must contain the entity ID, the user who made the change, the timestamp, and the before/after values for the changed fields.

## Audit & Observability

-   **Audit**: All create, update, and deactivate operations on `UomConversion` records MUST generate an audit log entry. The entry must capture the user, timestamp, entity ID, and a snapshot of the change (e.g., `oldValue`, `newValue`).
-   **Logging**: Application logs should be generated for successful operations and for any failed validation attempts to aid in troubleshooting.
-   **Metrics**: Consider exposing a metric for the total number of active UOM conversions.

## Open Questions

-   None at this time.

## Original Story (Unmodified – For Traceability)
# Issue #56 — [BACKEND] [STORY] Master: Manage UOM and Pack/Case Conversions

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Master: Manage UOM and Pack/Case Conversions

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Product Admin**, I want to define UOM conversions so that packs/cases vs each transact correctly.

## Details
- Base UOM plus alternate UOM conversions.
- Validate non-zero and reversible conversions.

## Acceptance Criteria
- UOM conversions created and queryable.
- Conversion rules enforced.
- Audited changes.

## Integrations
- Inventory uses UOM on stock moves; Workexec uses UOM on lines.

## Data / Entities
- UnitOfMeasure, UomConversion, AuditLog

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