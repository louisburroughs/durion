Title: [BACKEND] [STORY] Workexec: Persist Immutable Pricing Snapshot for Estimate/WO Line
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/50
Labels: type:story, domain:pricing, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:pricing
- status:ready-for-dev

---
**Rewrite Variant:** pricing-strict

---

## Story Intent
**As a** System,
**I want to** capture an immutable, detailed snapshot of the pricing components for a service or part at the moment it is added to an Estimate or Work Order,
**so that** historical financial records remain accurate and auditable, regardless of subsequent changes to pricing rules, costs, or list prices.

## Actors & Stakeholders
- **Primary Actor:** `System` (Specifically, a collaboration between the `Work Execution` and `Pricing` services).
- **Indirect User:** `Service Advisor` who adds items to documents and relies on price stability.
- **Stakeholder:** `Accountant/Auditor` who consumes this immutable data for margin reporting, financial reconciliation, and audits.

## Preconditions
- A valid and priceable item (part or labor) exists in the system.
- The `Pricing` service is available and can calculate a price for the item.
- An active Estimate or Work Order document exists in the `Work Execution` service.

## Functional Behavior
1.  **Trigger:** A user adds a new line item (part or labor) to an Estimate or Work Order.
2.  **Request:** The `Work Execution` service gathers the context (item ID, quantity, customer, document type) and makes a synchronous request to the `Pricing` service to "calculate and snapshot" a price.
3.  **Execution:** The `Pricing` service:
    a. Performs the price calculation based on all current rules, costs, and policies.
    b. Creates a new, immutable `PricingSnapshot` record containing the full pricing breakdown.
    c. Persists this snapshot to the `Pricing` database.
    d. Returns the unique, permanent `snapshotId` to the `Work Execution` service.
4.  **Confirmation:** The `Work Execution` service receives the `snapshotId` and persists it on the corresponding `DocumentLine` record. The final calculated price from the snapshot is also stored on the line for display purposes.

## Alternate / Error Flows
- **Error: Pricing Service Unavailable:** If the `Work Execution` service cannot reach the `Pricing` service, the action to add the line item MUST fail with a clear error message to the user. The line item is not added to the document.
- **Error: Invalid Item for Pricing:** If the `Pricing` service cannot find the item or a valid price for it, it returns a specific error. The `Work Execution` service prevents the line from being added and surfaces the error.
- **Error: Snapshot Persistence Failure:** If the `Pricing` service fails to persist the snapshot after calculation, it must not return a `snapshotId`. The overall operation fails, and the line is not added.

## Business Rules
- **Immutability:** Once a `PricingSnapshot` is written, it CANNOT be modified or deleted. Any change to a line item's pricing inputs (e.g., quantity, discount override) MUST generate a new snapshot.
- **Source of Truth:** The `PricingSnapshot` is the authoritative system of record for the historical price calculation of a given document line. The price displayed on the `Work Order` line should be considered a denormalized copy for performance.
- **Scope:** A snapshot is generated for every line item added to an Estimate (Quote) or a Work Order (Booking).

## Data Requirements
### `PricingSnapshot` Entity (Owned by `domain:pricing`)
| Field Name | Type | Description | Example |
|---|---|---|---|
| `snapshotId` | UUID | Primary key. The immutable identifier. | `a4e1c7f9-3d1b-4d7a-8c9f-2b1a3e0f9c8d` |
| `createdAt` | TimestampZ | ISO 8601 timestamp of snapshot creation. | `2023-10-27T10:00:00Z` |
| `sourceContext` | JSONB | Data provided by the calling service (e.g., `workOrderId`, `lineItemId`). | `{"workOrderId": "WO-123", "lineId": "L-456"}` |
| `itemIdentifier` | String | The SKU, Part #, or Labor Op Code. | `BKR-FL-PREM` |
| `quantity` | Number | The quantity of the item being priced. | `1` |
| `prices` | JSONB | Object containing all key price values. | `{"msrp": 100.00, "cost": 45.50, "finalPrice": 90.00}` |
| `appliedRules`| JSONB Array | A list of all pricing rules that were triggered and applied. | `[{"ruleId": "LOYALTY10", "discount": "10%"}]` |
| `policyVersion`| String | Identifier for the pricing policy version used. | `policy_v2.1_2023-10-01` |

## Acceptance Criteria
**AC-1: Successful Snapshot Creation for a New Work Order Line**
- **Given** a valid part `P-123` with a list price of $150 and a 10% customer discount rule applies
- **And** a `Work Order` `WO-101` exists
- **When** the System adds one unit of `P-123` to `WO-101`
- **Then** the `Pricing` service must create a new `PricingSnapshot`
- **And** the snapshot must contain the final price of $135 and a trace of the 10% discount rule
- **And** the `Work Execution` service must store the returned `snapshotId` on the new `WorkOrderLine` record.

**AC-2: Snapshot is Immutable**
- **Given** a `PricingSnapshot` with ID `S-XYZ` has been successfully created
- **When** an external system attempts to issue an `UPDATE` or `DELETE` command against `S-XYZ`
- **Then** the operation MUST be rejected at the application or database layer
- **And** an audit log MUST be generated for the unauthorized modification attempt.

**AC-3: Graceful Failure when Pricing Service is Down**
- **Given** the `Pricing` service is unavailable
- **And** a Service Advisor is attempting to add a part to an Estimate
- **When** the `Work Execution` service calls the `Pricing` service to generate a snapshot
- **Then** the request to add the part MUST fail
- **And** the user interface must display a clear error message indicating that pricing is currently unavailable
- **And** the line item MUST NOT be added to the Estimate.

**AC-4: Snapshot Retrieval API**
- **Given** a `PricingSnapshot` with ID `S-ABC` exists
- **When** an authorized client calls `GET /pricing/v1/snapshots/S-ABC`
- **Then** the system returns the snapshot with full pricing breakdown including resolved prices, policy identifiers, rule outcomes, versioning, and timestamps.

## Audit & Observability
- **Audit Trail:** An event `PricingSnapshotCreated` shall be published to the message bus upon successful creation of a snapshot. This event must contain the `snapshotId` and key context.
- **Logging:** All requests to create snapshots, both successful and failed, must be logged with structured context (e.g., `workOrderId`, `itemIdentifier`).
- **Metrics:** The `Pricing` service must expose metrics for:
    - `pricing.snapshot.creation.success.count`
    - `pricing.snapshot.creation.failure.count`
    - `pricing.snapshot.creation.latency.ms` (histogram)

## Resolved Questions

### Question 1: Domain Ownership (RESOLVED)

**Question:** Which domain, `domain:pricing` or `domain:workexec`, is the primary owner for this story's implementation and delivery?

**Answer:** **domain:pricing** is the **primary owner** of this story.

**Rationale:**
- The core artifact is an **immutable pricing snapshot** (policy evaluation, price resolution, versioning).
- Pricing owns:
  - price calculation
  - policy/version semantics
  - snapshot immutability and retrieval
- Workexec is a **consumer** that attaches the snapshot to a line item and enforces downstream behavior.

**Primary domain label:** `domain:pricing`
**Secondary/integrating domain:** `domain:workexec`

### Question 2: Story Splitting (RESOLVED)

**Question:** As recommended in the conflict summary, should this story be split into two separate, sequenced stories?

**Answer:** **Yes—split into two sequenced stories.**

**Story A — Pricing (foundational capability)** [THIS STORY]
- **Owner:** domain:pricing
- **Delivers:**
  - Snapshot creation API
  - Snapshot immutability guarantees
  - Snapshot retrieval (read-only) via `GET /pricing/v1/snapshots/{id}`
  - Error semantics and versioning

**Story B — Workexec (integration)** [FUTURE STORY]
- **Owner:** domain:workexec
- **Delivers:**
  - Line item creation that **requires** a snapshot
  - Storage of `pricingSnapshotId` on estimate/WO lines
  - UI/UX handling based on snapshot state

**Rationale:** Prevents circular dependencies and keeps ownership clean.

### Question 3: Error Handling Contract (RESOLVED)

**Question:** The proposed behavior is to hard-fail the line item addition if a snapshot cannot be created. Is this the desired business behavior?

**Answer:** **Hard-fail if a snapshot cannot be created. "Price Pending" is explicitly out of scope and not allowed.**

**Required Behavior:**
- When adding a line item:
  - Workexec **must call Pricing** to create a snapshot
  - If snapshot creation fails (Pricing unavailable, policy error):
    - **Reject the line item add** with a clear, actionable error

**Why not "Price Pending":**
- Breaks estimate approval integrity
- Complicates audit, repricing, and invoicing paths
- Introduces partial states across domains

**Explicit Rule:** A line item **cannot exist** without an attached immutable pricing snapshot.

### Question 4: Drilldown Requirement (RESOLVED)

**Question:** What are the specific requirements for "Drilldown supported"?

**Answer:** This is an **API requirement in Pricing**, not a UI story.

**Required API (Pricing-owned):**
- `GET /pricing/v1/snapshots/{snapshotId}`

**Response must include:**
- resolved prices (unit/extended)
- price list / policy identifiers
- rule outcomes (applied/blocked)
- versioning (`policyVersion`, `priceListVersion`)
- timestamps and currency
- enough metadata to explain *why* the price is what it is

**Out of scope:**
- Any specific UI implementation
- Visualizations or user flows

Workexec/UI will simply **link to or consume** this endpoint for drilldown when needed.

---
## Original Story (Unmodified – For Traceability)
## Backend Implementation for Story

**Original Story**: [STORY] Workexec: Persist Immutable Pricing Snapshot for Estimate/WO Line

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want an immutable pricing snapshot per document line so that later price changes don't alter history.

## Details
- Snapshot includes price, cost-at-time, MSRP-at-time, applied rules, timestamp, policy decisions.

## Acceptance Criteria
- Snapshot written on quote and/or booking.
- Immutable.
- Drilldown supported.

## Integrations
- Workexec stores snapshotId on lines; Accounting may consume for margin reporting (optional).

## Data / Entities
- PricingSnapshot, DocumentLineRef, PricingRuleTrace

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