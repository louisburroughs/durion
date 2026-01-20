Title: [BACKEND] [STORY] Master: Create Product Record (Part/Tire) with Identifiers and Attributes
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/57
Labels: type:story, domain:inventory, status:ready-for-dev

## Story Intent
As a Product Administrator, create and manage an authoritative master record for each product (parts, tires) so all systems reference a single, consistent source of truth for product information.

## Actors & Stakeholders
- **Product Administrator (Primary):** Creates, edits, and manages the product master catalog.
- **System (POS Backend):** Enforces business rules and persists product data.
- **Inventory System (Consumer):** Uses product master for stock tracking.
- **Work Execution System (Consumer):** Uses product master to schedule/execute jobs (e.g., tire install).
- **Auditor (Stakeholder):** Reviews product changes for compliance/traceability.

## Preconditions
- Product Administrator is authenticated with `product:manage` permission.
- Manufacturers exist and are referenceable by unique `manufacturerId`.
- Product Categories exist.

## Functional Behavior
### 1) Create Product
- Trigger: Admin submits "Create Product" request.
- Process: validate data; check identifier uniqueness; generate system-wide unique `productId`; create with default `status: ACTIVE`; audit creation.
- Outcome: product available to dependent systems.

### 2) Update Product
- Trigger: Admin submits "Update Product" for a `productId`.
- Process: retrieve record; validate new data; enforce immutability rules (SKU immutable); apply changes; audit field-level changes.
- Outcome: product updated.

### 3) Change Status (Deactivate/Reactivate)
- Trigger: Admin initiates status change.
- Process: locate product; verify allowed; update status to `INACTIVE` or `ACTIVE`; audit status change; when deactivating with open dependencies, flag work orders and notify PO admin (see Business Rules).
- Outcome: status changed; downstream modules see availability impact.

### 4) Search Products
- Trigger: search query via UI/API.
- Process: support exact search by SKU, MPN; keyword search across description, category name, attributes, and product name.
- Outcome: return matching products.

## Alternate / Error Flows
- Duplicate identifier: reject create when SKU already exists or Manufacturer+MPN already exists (`409 Conflict`).
- Invalid manufacturer: reject when `manufacturerId` not found (`400 Bad Request`).
- Missing required fields: reject create with validation errors.

## Business Rules
1. SKU is globally unique and immutable after creation.
2. Manufacturer + MPN pair must be unique across products.
3. The application owns its primary key (`productId`); external identifiers (e.g., UPC) are not primary keys.
4. SKU is immutable; `manufacturerId`, `mpn`, and other fields are editable.
5. Product status is `ACTIVE` or `INACTIVE`; default on create is `ACTIVE`.
6. Deactivation is permitted even when inventory exists, work orders are open, or purchase orders include the product; deactivation must flag affected work orders and notify the PO admin for impacted POs.
7. Tire size/spec is captured within the product description (not a separate structured schema).
8. Searchable fields for keyword queries: description, category name, attributes, product name.

## Data Requirements
| Field | Type | Constraints / Notes | Entity |
| --- | --- | --- | --- |
| productId | UUID | PK, system-generated | Product |
| status | Enum | `ACTIVE`, `INACTIVE` | Product |
| name | String | Required | Product |
| description | String | Required, may include tire size/spec | Product |
| unitOfMeasure | String | Required (e.g., "Each", "Pair") | Product |
| manufacturerId | UUID | FK to Manufacturer | Product |
| categoryId | UUID | FK to Category | Product |
| sku | String | Required, Unique, Immutable, Indexed | ProductIdentifier |
| mpn | String | Required, Indexed; unique in combination with manufacturerId | ProductIdentifier |
| upc | String | Optional; may be unique but not a primary key | ProductIdentifier |
| attributes | JSONB / Text | Optional key-value attributes | ProductAttribute |
| createdAt | Timestamp | System-managed | Product |
| updatedAt | Timestamp | System-managed | Product |

## Acceptance Criteria
**AC1: Create product succeeds**
- Given an authenticated Product Admin with `product:manage`
- And manufacturer `mfg-123` exists
- When creating a product with unique SKU `ABC-1001`, MPN `XYZ-2002`, valid `manufacturerId`, required fields, and description (with tire spec if applicable)
- Then the system returns `201 Created`, persists the product with `status: ACTIVE`, and audits creation.

**AC2: Duplicate identifiers rejected**
- Given a product exists with SKU `ABC-1001` or Manufacturer+MPN `mfg-123`/`XYZ-2002`
- When creating another product with the same SKU or same Manufacturer+MPN pair
- Then the request is rejected with `409 Conflict` indicating the duplicate identifier.

**AC3: Deactivate with dependencies**
- Given product `prod-456` is `ACTIVE` and is referenced by inventory, open work orders, or open purchase orders
- When setting `prod-456` to `INACTIVE`
- Then status updates to `INACTIVE`, work orders referencing `prod-456` are flagged, PO admin is notified for impacted POs, and the action is audited.

**AC4: Reactivate product**
- Given product `prod-789` is `INACTIVE`
- When setting status to `ACTIVE`
- Then status updates to `ACTIVE` and the change is audited.

**AC5: Search across keyword fields**
- Given products with descriptive text, category names, attributes, and names
- When performing keyword search
- Then results include matches from description, category name, attributes, and product name; SKU/MPN exact search remains supported.

## Audit & Observability
- Audit all create, update, and status changes with: `productId`, event type (`PRODUCT_CREATED`, `PRODUCT_UPDATED`, `PRODUCT_STATUS_CHANGED`), actor ID, timestamp, and before/after field values for updates.
- Emit metrics for creation/update rates and errors (validation failures, conflicts).

## Open Questions
- None. Clarification #246 resolved prior questions.

---
## Original Story (Unmodified – For Traceability)
# Issue #57 — [BACKEND] [STORY] Master: Create Product Record (Part/Tire) with Identifiers and Attributes

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Master: Create Product Record (Part/Tire) with Identifiers and Attributes

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Product Admin**, I want to create a product with SKU/MPN, manufacturer, type, and attributes so that all modules reference a consistent product master.

## Details
- Identifiers: internal SKU, manufacturer part number, optional UPC.
- Attributes: description, category, tire size/spec, UOM, active/inactive.

## Acceptance Criteria
- Create/update/deactivate product.
- Search by SKU/MPN/keywords.
- Changes audited.

## Integrations
- Inventory and Workexec reference productId consistently.

## Data / Entities
- Product, ProductIdentifier, ProductAttribute, ManufacturerRef, AuditLog

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