Title: [BACKEND] [STORY] Security: Immutable Audit Trail for Price/Cost Changes and Rule Evaluations
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/41
Labels: backend, story-implementation, user, type:story, domain:audit, status:ready-for-dev

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:audit
- status:draft

### Recommended
- agent:audit
- agent:pricing
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** security-strict
---

## Story Intent
As an **Auditor**, I need an immutable audit trail of all price and cost modifications, and a detailed trace of how pricing rules were evaluated for a specific quote, so that I can provide definitive explanations for profit margins, investigate discrepancies, and resolve financial disputes with verifiable evidence.

## Actors & Stakeholders
- **Auditor (Primary Actor)**: A user responsible for reviewing and verifying financial data, system changes, and calculation logic.
- **System (Primary Actor)**: The POS platform responsible for capturing, processing, and storing audit events in a secure and immutable manner.
- **Pricing Domain (Stakeholder)**: The source of the events to be audited. Its changes to price books, costs, and its rule evaluations must be reliably logged.
- **Work Execution Domain (Stakeholder)**: Consumes a reference (`snapshotId`) to link repair orders/estimates to the specific pricing evaluation that occurred at a point in time.
- **Finance/Accounting Team (Stakeholder)**: Relies on the integrity of the audit trail for financial reporting, margin analysis, and reconciliation.

## Preconditions
1. An "Auditor" user role with appropriate read-only permissions to the audit data exists.
2. The Pricing domain is capable of emitting structured events for price/cost changes and rule evaluations.
3. The Work Execution domain has a mechanism to store a `snapshotId` against estimate or work order line items.

## Functional Behavior

### Scenario 1: Auditing Price and Cost Modifications
- **Trigger**: A user with the necessary permissions modifies a canonical data point in the Pricing domain (e.g., a price in a price book, a product's cost, or applies a manual override on an order).
- **Behavior**: The system atomically records an immutable audit event for the change as part of the same transaction. The event must capture what changed, who changed it, when it was changed, the value before the change, and the value after the change.
- **Outcome**: A new, permanent record is appended to the audit trail. The business operation succeeds only if the audit record is successfully created.

### Scenario 2: Tracing a Price Quote Evaluation
- **Trigger**: The Pricing domain's engine is invoked to calculate a price for a set of items (e.g., for a customer estimate).
- **Behavior**: The system generates a unique and immutable `PricingSnapshot` that captures the exact inputs (product IDs, quantities, customer info) and a corresponding `PricingRuleTrace`. The trace details every rule that was considered, which were applied, and which were rejected during the calculation.
- **Outcome**: A unique `snapshotId` is created that references the `PricingSnapshot`. This ID is returned to the calling service (e.g., Work Execution) for storage and future reference.

### Scenario 3: Retrieving Audit Data
- **Trigger**: An Auditor uses an interface to query the audit logs.
- **Behavior**: The system provides search functionality against the audit trail, allowing filtering by criteria such as product identifier, location, date range, user ID, and event type.
- **Outcome**: The system returns a set of matching, read-only audit records.

## Alternate / Error Flows
- **Error Flow 1: Audit Log Write Fails**
  - **Given**: A user attempts to save a price change.
  - **When**: The system fails to write the corresponding event to the audit log (e.g., due to a database connection error or constraint violation).
  - **Then**: The entire parent transaction, including the price change, MUST be rolled back. The user receives an error message indicating the operation could not be completed and to try again later. The data integrity of the system is preserved.

## Business Rules
- **BR1: Immutability**: Audit records are strictly append-only. Once an event is written to the audit log, it can never be modified or deleted through any application interface.
- **BR2: Transactional Atomicity**: The business operation (e.g., price change) and the creation of its corresponding audit log entry must occur within the same atomic transaction. Failure to log the event must result in the failure of the entire operation.
- **BR3: Traceability Link**: The `snapshotId` generated during a price evaluation must be stored by any consuming system (e.g., Work Execution domain) to create an unbreakable link between an estimate/order line and the evidence of its price calculation.

## Data Requirements
This story establishes the need for the following logical data entities within the Audit domain:

- **`AuditLogEvent`**:
  - `eventId` (UUID, Primary Key)
  - `timestamp` (UTC, Indexed)
  - `eventType` (String Enum, e.g., 'PRICE_BOOK_UPDATE', 'PRODUCT_COST_CHANGE')
  - `actorId` (Identifier for the user or system process)
  - `entityId` (Identifier for the modified entity, e.g., `productId`, Indexed)
  - `entityType` (String, e.g., 'Product')
  - `oldValue` (JSON blob of the state before the change)
  - `newValue` (JSON blob of the state after the change)
  - `context` (JSON blob for additional info, e.g., `locationId`, `sourceTransactionId`)

- **`PricingSnapshot`**:
  - `snapshotId` (UUID, Primary Key, Indexed)
  - `timestamp` (UTC)
  - `quoteContext` (JSON blob of all inputs to the pricing engine)
  - `finalPrice` (Monetary value of the calculated total)
  - `ruleTraceId` (FK to `PricingRuleTrace`)

- **`PricingRuleTrace`**:
  - `ruleTraceId` (UUID, Primary Key)
  - `evaluationSteps` (Array of JSON objects detailing each rule's evaluation: `ruleId`, `status` [APPLIED, REJECTED, SKIPPED], `inputs`, `outputs`)

## Acceptance Criteria
- **AC1: Price Change Audit**
  - **Given** a user is authorized to change product costs
  - **When** they change the cost of `PRODUCT-123` from `$10.00` to `$12.50` and save the change
  - **Then** a new, immutable `AuditLogEvent` is created containing the `productId`, the `oldValue` (`$10.00`), the `newValue` (`$12.50`), the user's ID, and the timestamp.

- **AC2: Immutability Guarantee**
  - **Given** an audit record with `eventId` 'abc-123' exists in the database
  - **When** any system or user attempts to execute an update or delete operation on that record via any API
  - **Then** the operation MUST fail with a "Forbidden" or "Method Not Allowed" error response.

- **AC3: Price Evaluation Traceability**
  - **Given** the Work Execution service requests a price quote for an estimate
  - **When** the Pricing service calculates the final price
  - **Then** a `PricingSnapshot` and `PricingRuleTrace` are created, and the unique `snapshotId` is returned and stored against the relevant estimate line.

- **AC4: Audit Retrieval**
  - **Given** an Auditor has an estimate line with a stored `snapshotId`
  - **When** they query the audit service with that `snapshotId`
  - **Then** the complete and unchanged `PricingSnapshot` and its associated `PricingRuleTrace` are returned.

- **AC5: Transactional Rollback on Audit Failure**
  - **Given** a user is saving a change to a price book
  - **And** the audit database service is temporarily unavailable
  - **When** the system attempts to commit the transaction
  - **Then** the entire operation MUST be rolled back, the price in the price book remains unchanged, and an error is logged.

- **AC6: Search Functionality**
  - **Given** multiple audit records exist for changes to `PRODUCT-456`
  - **When** an Auditor searches for events related to `PRODUCT-456` within a specific date range
  - **Then** the system returns all, and only, the relevant audit records matching the criteria.

## Audit & Observability
- **Metrics**: The system must expose metrics on the volume of audit events being generated per event type.
- **Alerting**: A critical-level alert must be triggered if the audit service fails to write an event, as this indicates that core business operations (like price changes) will be blocked.
- **Logging**: All failed attempts to write to the audit log must be logged with a `CRITICAL` severity, including the full payload that was attempted.

## Open Questions
- none

## Original Story (Unmodified – For Traceability)
# Issue #41 — [BACKEND] [STORY] Security: Immutable Audit Trail for Price/Cost Changes and Rule Evaluations

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Security: Immutable Audit Trail for Price/Cost Changes and Rule Evaluations

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Auditor**, I want immutable history and pricing-rule traces so that we can explain margins and resolve disputes.

## Details
- Append-only audit for price books, overrides, costs.
- Keep evaluation traces for pricing quotes (rule trace).

## Acceptance Criteria
- Audit is append-only.
- Drill estimate line → snapshot → rule trace.
- Search by product/location/date.

## Integrations
- Workexec stores snapshotId for traceability.

## Data / Entities
- AuditLog, PricingRuleTrace, PricingSnapshot

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