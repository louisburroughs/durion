Title: [BACKEND] [STORY] Execution: Issue and Consume Parts
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/158
Labels: type:story, domain:workexec, status:needs-review

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec
- agent:story-authoring


---
**Rewrite Variant:** workexec-structured
---

## Story Intent
As a Technician or Parts Counter Staff, I need to record the issuance and consumption of parts for a specific work order, so that inventory levels are accurately updated, work progress is tracked, and financial records (WIP/COGS) are correctly impacted via downstream systems.

## Actors & Stakeholders
- **Primary Actors:**
  - `Technician`: Performs the work and installs the parts.
  - `Parts Counter Staff`: Gathers and issues parts from inventory for a specific job.
- **System Actors:**
  - `Work Execution Service`: Owns the state and lifecycle of the work order.
  - `Inventory Service`: A downstream consumer that adjusts on-hand inventory based on issuance events.
- **Stakeholders:**
  - `Accounting Department`: Consumes financial events (`InventoryIssued`) to correctly track Cost of Goods Sold (COGS) or Work-in-Progress (WIP) asset value.
  - `Service Advisor`: Monitors work order status, including delays due to parts availability.

## Preconditions
- A `Workorder` exists in a state that permits part allocation (e.g., `APPROVED`, `IN_PROGRESS`).
- The `Workorder` has one or more `WorkorderItem` line items that specify the required `productId` and an authorized `quantity`.
- The actor is authenticated and has the necessary permissions to modify the specified `Workorder`.
- The system has a defined inventory of parts that can be queried for availability.

## Functional Behavior
This story covers the linked actions of issuing (committing from stock) and consuming (installing on the job) a part.

1.  **Trigger:** The actor initiates the "Issue/Consume Parts" action for a specific `WorkorderItem` on a `Workorder`.
2.  The system validates that the `Workorder` is in a mutable state (e.g., not `COMPLETED` or `CANCELLED`).
3.  The actor specifies the `productId` and the `quantity` being issued and consumed.
4.  **Validation:**
    - The system verifies that the `quantity` does not exceed the remaining authorized quantity for that `WorkorderItem`.
    - The system checks for available on-hand inventory for the requested `quantity`.
5.  **State Change:**
    - Upon successful validation, the system creates an immutable `PartUsageEvent` with `eventType: ISSUED_AND_CONSUMED`.
    - The system updates the `WorkorderItem` to reflect the consumed quantity and may update its completion status (e.g., `PARTS_COMPLETE`).
6.  **Integration Event:**
    - The system constructs and emits an `InventoryIssued` event.
    - The event payload includes all data required by downstream systems, including the source `Workorder`, part details, quantity, and a hint for the accounting model (WIP vs. COGS).
    - The event must include a deterministic idempotency key to prevent duplicate processing by consumers.

## Alternate / Error Flows
- **Insufficient Inventory:**
  - If the requested `quantity` is greater than the available on-hand inventory, the transaction is blocked.
  - The system will flag the `Workorder` and/or `WorkorderItem` with a status of `WAITING_FOR_PARTS`.
  - A notification may be generated for the Parts Department or Service Advisor.
- **Consumption Exceeds Authorization:**
  - If the requested `quantity` exceeds the authorized quantity on the `WorkorderItem`, the transaction is blocked.
  - The system should direct the user to an approval workflow to increase the authorized quantity (Note: The approval workflow itself is out of scope for this story).
- **Invalid Work Order State:**
  - If the `Workorder` is in a terminal state (e.g., `COMPLETED`, `CANCELLED`), the system rejects the request with an error indicating the work order is locked.
- **Downstream Event Failure:**
  - If the `InventoryIssued` event fails to publish to the event bus, the core state change (creation of `PartUsageEvent`) must still be committed successfully.
  - The system must guarantee eventual delivery of the event using a mechanism like the transactional outbox pattern.

## Business Rules
- **BR1:** All part movements against a work order must generate an immutable `PartUsageEvent` record for audit and traceability.
- **BR2:** The total quantity issued/consumed for a part on a work order item cannot exceed the authorized quantity without a separate, explicit approval.
- **BR3:** An `InventoryIssued` event must be emitted for every `ISSUED_AND_CONSUMED` `PartUsageEvent`. This event is the source of truth for downstream inventory and accounting systems.
- **BR4:** The idempotency key for the `InventoryIssued` event must be unique and deterministic for each distinct issuance action, as specified in the Data Requirements.
#### BR-ATOMIC-1: Atomic Transaction Requirement
**Rule:** Part issue and consume operations MUST be executed within a single local database transaction that includes:
- Work order part consumption state update
- Inventory ledger entry creation
- Outbox record creation for event publishing

**Rationale:** Ensures data consistency by preventing partial updates. If any step fails, all changes are rolled back.

**Authority:** Workexec domain

#### BR-ASYNC-1: Asynchronous Event Publishing
**Rule:** Events (InventoryIssued, PartConsumed) MUST be published asynchronously after the local transaction commits successfully, using the transactional outbox pattern.

**Rationale:** Decouples event publishing from transaction processing, improving performance and reliability. The outbox pattern ensures guaranteed eventual delivery even if the message broker is temporarily unavailable.

**Authority:** Workexec domain

#### BR-IDEMPOTENCY-1: Idempotency Key Standard
**Rule:** All part consumption operations and events MUST use the idempotency key format: `{workorderId}-{workorderItemId}-{partUsageEventId}`. Consumers MUST handle retries idempotently using this key.

**Rationale:** Prevents duplicate processing in distributed systems, especially during retries or network failures.

**Authority:** Workexec domain (key format), Cross-cutting concern (idempotency handling)

#### BR-AUDIT-1: Audit Trail Requirements
**Rule:** All part consumption operations MUST capture:
- Actor identifier (who performed the action)
- Timestamp in UTC (when it occurred)
- Entity changes (what changed, original and new values)
- Idempotency key
- Request/event correlation identifiers

**Rationale:** Provides complete auditability and troubleshooting capability for compliance and operational needs.

**Authority:** Audit & Observability domain

#### BR-POLICY-1: WIP vs COGS Policy Configuration
**Rule:** The determination of whether inventory consumption impacts Work-In-Progress (WIP) or Cost of Goods Sold (COGS) accounts SHALL be configurable via policy. The policy configuration may be:
- System-wide default
- Per location override
- Per work order type
- Or other business-defined criteria

The workexec domain MUST evaluate the policy at consumption time and include the determination in the `InventoryIssued` event payload.

**Rationale:** Different business processes and locations may have different accounting treatment requirements. The policy provides flexibility while maintaining consistency within each configuration scope.

**Authority:** Accounting domain (policy definition), Workexec domain (policy evaluation and event payload)

## Data Requirements
- **Entity: `PartUsageEvent`**
  - `partUsageEventId`: (PK) Unique identifier for the event.
  - `workorderId`: (FK) The parent work order.
  - `workorderItemId`: (FK) The specific line item the part is for.
  - `productId`: (FK) The identifier of the part.
  - `quantity`: The amount issued and consumed.
  - `eventType`: `ISSUED_AND_CONSUMED`.
  - `eventTimestamp`: The UTC timestamp of the transaction.
  - `performedBy`: The ID of the user who performed the action.

- **Event DTO: `InventoryIssued`**
  - `eventId`: A unique ID for this event instance.
  - `idempotencyKey`: Composite key for consumer-side deduplication. Format: `{workorderId}-{workorderItemId}-{partUsageEventId}`.
  - `sourceDomain`: `workexec`.
  - `sourceEntityId`: The `partUsageEventId`.
  - `eventType`: `InventoryIssued`.
  - `eventVersion`: `1.0`.
  - `timestamp`: UTC timestamp of the event.
  - `payload`:
    - `workorderId`: ID of the source work order.
    - `productId`: ID of the part issued.
    - `quantityIssued`: The quantity.
    - `issuedBy`: User ID of the actor.
    - `accountingModel`: `WIP` or `COGS`, based on configuration.
#### PartUsageEvent
Represents a single part consumption event.

**Fields:**
- `partUsageEventId` (UUID, Primary Key): Unique identifier for this consumption event
- `workorderId` (UUID, Foreign Key): Work order being executed
- `workorderItemId` (UUID, Foreign Key): Specific line item on the work order
- `partId` (UUID, Foreign Key): Part/SKU being consumed
- `quantityConsumed` (Decimal): Quantity consumed in this event
- `unitCost` (Decimal): Cost per unit at time of consumption
- `totalCost` (Decimal): Total cost (quantityConsumed * unitCost)
- `actorId` (UUID): User who performed the consumption
- `consumedAtUtc` (Timestamp): When consumption occurred (UTC)
- `idempotencyKey` (String): Format: `{workorderId}-{workorderItemId}-{partUsageEventId}`
- `accountingPolicy` (String): WIP or COGS determination
- `auditMetadata` (JSONB): Additional audit context

#### OutboxEvent
Represents events to be published asynchronously.

**Fields:**
- `outboxEventId` (UUID, Primary Key): Unique identifier for this outbox entry
- `aggregateType` (String): Entity type (e.g., "WorkOrder")
- `aggregateId` (UUID): Entity instance identifier
- `eventType` (String): Event name (e.g., "InventoryIssued", "PartConsumed")
- `eventPayload` (JSONB): Complete event data
- `createdAtUtc` (Timestamp): When outbox record was created (UTC)
- `publishedAtUtc` (Timestamp, Nullable): When successfully published (NULL if pending)
- `publishAttempts` (Integer): Number of publish attempts
- `lastAttemptAtUtc` (Timestamp, Nullable): Last publish attempt timestamp
- `status` (String): PENDING, PUBLISHED, FAILED

#### AccountingPolicyConfiguration
Configures WIP vs COGS determination rules.

**Fields:**
- `policyConfigId` (UUID, Primary Key): Unique identifier
- `scope` (String): SYSTEM_DEFAULT, LOCATION, WORK_ORDER_TYPE
- `scopeValue` (String, Nullable): Location ID or work order type if scoped
- `policyDecision` (String): WIP or COGS
- `effectiveFromUtc` (Timestamp): When this policy becomes effective (UTC)
- `effectiveToUtc` (Timestamp, Nullable): When this policy expires (NULL if current)
- `createdBy` (UUID): User who created this policy
- `createdAtUtc` (Timestamp): When policy was created (UTC)
- `auditMetadata` (JSONB): Additional audit context

## Acceptance Criteria
- **AC1: Successful Part Consumption**
  - **Given** a `Workorder` is `IN_PROGRESS` with an item authorizing 2 units of `PART-123`.
  - **And** there are 10 units of `PART-123` in stock.
  - **When** a Technician records the consumption of 2 units of `PART-123` for that item.
  - **Then** the system creates a `PartUsageEvent` for 2 units.
  - **And** an `InventoryIssued` event is published with a quantity of 2.
  - **And** the `WorkorderItem` reflects that 2 units have been consumed.

- **AC2: Insufficient Inventory**
  - **Given** a `Workorder` is `IN_PROGRESS` with an item authorizing 2 units of `PART-123`.
  - **And** there is only 1 unit of `PART-123` in stock.
  - **When** a Technician attempts to record the consumption of 2 units of `PART-123`.
  - **Then** the system rejects the transaction with an "Insufficient Inventory" error.
  - **And** the `Workorder` status is updated to `WAITING_FOR_PARTS`.
  - **And** no `PartUsageEvent` is created and no `InventoryIssued` event is published.

- **AC3: Exceeding Authorized Quantity**
  - **Given** a `Workorder` is `IN_PROGRESS` with an item authorizing 2 units of `PART-123`.
  - **And** there are 10 units of `PART-123` in stock.
  - **When** a Technician attempts to record the consumption of 3 units of `PART-123`.
  - **Then** the system rejects the transaction with an "Exceeds Authorized Quantity" error.
  - **And** no `PartUsageEvent` is created and no `InventoryIssued` event is published.

- **AC4: Idempotent Event Processing**
  - **Given** an `InventoryIssued` event with idempotency key `WO-1-W-OI-2-PUE-3` has already been successfully processed by the Inventory service.
  - **When** the same event with key `WO-1-W-OI-2-PUE-3` is delivered again.
  - **Then** the Inventory service must recognize it as a duplicate and discard it without reducing inventory a second time.

## Audit & Observability
- **Audit Log:** Every `PartUsageEvent` creation must be logged with all its fields for a permanent audit trail.
- **Structured Logging:**
  - `INFO`: Log successful part consumption events, including `workorderId`, `productId`, and `quantity`.
  - `WARN`: Log business rule validation failures, such as attempts to exceed authorized quantity or consume out-of-stock parts.
  - `ERROR`: Log failures in publishing the `InventoryIssued` event to the message bus after retries are exhausted.
- **Metrics:**
  - `parts.consumed.count`: Counter for the number of parts consumed, tagged by `productId`.
  - `parts.consumption.failures`: Counter for failed consumption attempts, tagged by `reason` (e.g., `insufficient_inventory`, `unauthorized_quantity`).
  - `events.published.duration`: A timer measuring the latency to publish the `InventoryIssued` event.
#### AC-ATOMIC-1: Single Transaction Guarantees Atomicity
**Given** a request to issue and consume a part for a work order item
**When** the part consumption is processed
**Then** all database changes (state update, ledger entry, outbox record) MUST occur within a single local transaction
**And** if any step fails, all changes MUST be rolled back
**And** no partial state MUST be committed

#### AC-ASYNC-1: Events Published Asynchronously
**Given** a successful part consumption transaction has committed
**When** the outbox processor runs
**Then** the `InventoryIssued` and `PartConsumed` events MUST be published to the message broker
**And** if publishing fails, the events MUST remain in the outbox for retry
**And** the original transaction MUST NOT be affected by publishing failures

#### AC-IDEMPOTENCY-1: Idempotency Keys Prevent Duplicate Processing
**Given** a part consumption request with idempotency key `{workorderId}-{workorderItemId}-{partUsageEventId}`
**When** the same request is submitted multiple times (e.g., due to retry)
**Then** the system MUST detect the duplicate using the idempotency key
**And** subsequent requests MUST return the same result without reprocessing
**And** only one part consumption record MUST be created

#### AC-AUDIT-1: Complete Audit Trail Captured
**Given** any part consumption operation
**When** the operation is executed
**Then** the system MUST capture:
- Actor identifier (who)
- UTC timestamp (when)
- Entity changes (what, before and after values)
- Idempotency key
- Request correlation identifier
**And** this audit trail MUST be queryable for compliance and troubleshooting

#### AC-POLICY-1: WIP vs COGS Determined by Configured Policy
**Given** a part consumption request
**When** the system evaluates accounting policy
**Then** the policy configuration MUST be queried based on:
- System-wide default
- Location-specific override (if applicable)
- Work order type-specific override (if applicable)
**And** the policy decision (WIP or COGS) MUST be determined according to the most specific applicable configuration
**And** if no policy is configured, the system MUST use the system-wide default or fail with a clear error

#### AC-POLICY-2: Policy Decision Included in InventoryIssued Event
**Given** a part consumption has been successfully processed
**When** the `InventoryIssued` event is created
**Then** the event payload MUST include:
- `accountingPolicy` field with value "WIP" or "COGS"
- Policy configuration identifier used for the determination
- Timestamp of policy evaluation (UTC)
**And** the Accounting service MUST consume this event and apply the specified policy without recomputation

### Audit & Observability Section
#### Event: PartConsumptionRequested
**Logged When:** Part consumption operation is initiated
**Payload:**
- Request identifier
- Idempotency key
- Work order ID
- Work order item ID
- Part ID
- Quantity requested
- Actor ID
- Timestamp (UTC)

#### Event: PartConsumptionTransactionCommitted
**Logged When:** Local database transaction commits successfully
**Payload:**
- Transaction identifier
- Idempotency key
- Part usage event ID
- Duration (milliseconds)
- Timestamp (UTC)

#### Event: PartConsumptionEventPublished
**Logged When:** Event successfully published to message broker
**Payload:**
- Outbox event ID
- Event type
- Publish attempt number
- Duration (milliseconds)
- Timestamp (UTC)

#### Event: PartConsumptionEventPublishFailed
**Logged When:** Event publishing fails
**Payload:**
- Outbox event ID
- Event type
- Error message
- Publish attempt number
- Next retry scheduled time
- Timestamp (UTC)

#### Metric: part_consumption_transaction_duration_ms
**Description:** Duration of part consumption transaction processing
**Type:** Histogram
**Labels:** work_order_type, location_id, success/failure

#### Metric: part_consumption_event_publish_duration_ms
**Description:** Duration of event publishing to message broker
**Type:** Histogram
**Labels:** event_type, success/failure, attempt_number

#### Metric: part_consumption_idempotency_key_duplicates_total
**Description:** Count of duplicate requests detected via idempotency key
**Type:** Counter
**Labels:** work_order_type, location_id

## Original Story (Unmodified – For Traceability)
# Issue #158 — [BACKEND] [STORY] Execution: Issue and Consume Parts

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Execution: Issue and Consume Parts

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299981/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Technician / Parts Counter

## Trigger
Parts are picked/issued for installation on a workorder.

## Main Flow
1. User selects a workorder part item.
2. User records parts issued (picked) and parts consumed (installed).
3. System validates quantities and updates on-hand commitments (if integrated).
4. System records consumption event with timestamp and user.
5. System updates item completion indicators where applicable.

## Alternate / Error Flows
- Insufficient inventory → flag and move workorder to waiting parts status.
- Consumption exceeds authorized quantity → block or require approval per policy.

## Business Rules
- Parts usage must be recorded as events (issue/consume/return).
- Consumption should not silently change authorized scope without approval.
- Traceability must be preserved.

## Data Requirements
- Entities: WorkorderItem, PartUsageEvent, InventoryReservation
- Fields: productId, quantityIssued, quantityConsumed, eventType, eventAt, performedBy, originEstimateItemId

## Acceptance Criteria
- [ ] Parts issued/consumed can be recorded and audited.
- [ ] System enforces quantity integrity and policy limits.
- [ ] Workorder status reflects parts availability issues.
- [ ] Each issued part emits exactly one InventoryIssued event
- [ ] Inventory on-hand quantity is reduced correctly
- [ ] COGS or WIP impact follows configured accounting model
- [ ] Issued quantities are traceable to workorder and technician
- [ ] Replayed events do not double-reduce inventory

## Integrations

### Accounting
- Emits Event: InventoryIssued
- Event Type: Non-posting or Posting (configurable: WIP vs immediate COGS)
- Source Domain: workexec
- Source Entity: WorkorderPartUsage
- Trigger: Part is issued/consumed for a workorder item
- Idempotency Key: workorderId + partId + usageSequence


## Notes for Agents
Keep parts usage consistent with promotion snapshot; changes route through approvals.


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