Title: [BACKEND] [STORY] Promotions: Record Promotion Redemption from Invoicing
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/94
Labels: type:story, domain:crm, status:ready-for-dev

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** crm-pragmatic
---

## Story Intent
As the CRM System, I want to idempotently consume a `PromotionRedeemed` event, create a permanent record of the redemption, and update relevant usage counters. This ensures that promotion usage is accurately tracked for analytics, reporting, and enables other systems to enforce usage limits.

## Actors & Stakeholders
- **System (CRM Domain):** The primary actor responsible for processing redemption events and maintaining the state of promotion usage.
- **System (Workorder Execution Domain):** The upstream actor that emits the `PromotionRedeemed` event when an invoice is finalized with a promotion applied.
- **Marketing Manager (Stakeholder):** Relies on accurate redemption data to measure promotion effectiveness and ROI.
- **Customer Service Representative (Stakeholder):** May need to query redemption history to resolve customer inquiries.

## Preconditions
- A `Promotion` entity exists in the CRM database, identifiable by a unique `promotionId`.
- The event-driven messaging infrastructure (e.g., Kafka, RabbitMQ) is operational.
- The CRM service is subscribed to the topic where `PromotionRedeemed` events are published.
- The schema for the `PromotionRedeemed` event is defined and agreed upon with the Workorder Execution domain.

## Functional Behavior
### Event-Triggered Redemption Recording
1.  **Trigger:** The CRM service's event listener consumes a `PromotionRedeemed` event.
2.  **Idempotency Check:** The service extracts a unique key from the event (e.g., a composite of `promotionId` and `workOrderId`, or a unique event ID) and checks if a `PromotionRedemption` record with this key already exists.
    - If a record exists, the event is considered a duplicate. The service logs this, discards the event, and processing stops (see Alternate Flows).
3.  **Validation:** If the event is not a duplicate, the service validates the `promotionId` from the payload against the CRM database to ensure the promotion exists and is in an active state.
4.  **Record Creation:** The service creates a new `PromotionRedemption` entity instance. This record links the `promotionId`, `customerId`, `workOrderId`, and `invoiceId` from the event payload.
5.  **Counter Updates:** In the same transaction as the record creation, the service atomically increments the `totalUsageCount` on the parent `Promotion` entity. If customer-specific limits are tracked, the corresponding customer usage counter is also incremented.
6.  **Confirmation:** The transaction is committed, and the event is acknowledged as successfully processed.

## Alternate / Error Flows
- **Duplicate Event Received:**
    - **Trigger:** An event is consumed for which a `PromotionRedemption` record already exists.
    - **Outcome:** The system logs an informational message (e.g., "Duplicate PromotionRedeemed event ignored for workOrderId: [ID]") and acknowledges the message to prevent reprocessing. No database changes are made.
- **Promotion Not Found:**
    - **Trigger:** The `promotionId` in the event payload does not match any existing `Promotion` in the CRM database.
    - **Outcome:** The system logs a critical error, does not create a redemption record, and moves the message to a Dead-Letter Queue (DLQ) for manual investigation.
- **Invalid Event Payload:**
    - **Trigger:** The event message is malformed, fails schema validation, or is missing a required field (e.g., `workOrderId`).
    - **Outcome:** The system logs a critical error, rejects the message, and moves it to the DLQ.

## Business Rules
- **Idempotency is Mandatory:** The system MUST guarantee that processing the same `PromotionRedeemed` event multiple times results in exactly one `PromotionRedemption` record and one counter increment. The unique key for idempotency is the combination of `promotionId` and `workOrderId`.
- **Transactional Integrity:** The creation of the `PromotionRedemption` record and the update of the `Promotion` usage counters MUST occur within a single atomic transaction. A failure in one part must roll back the entire operation.
- **System of Record:** The CRM domain is the authoritative system of record for all promotion usage data.

## Data Requirements
### `PromotionRedemption` Entity
- **`promotionRedemptionId`**: (PK) Unique identifier for the redemption record.
- **`promotionId`**: (FK, Indexed) Reference to the `Promotion` that was redeemed.
- **`customerId`**: (Indexed) Identifier for the customer who redeemed the promotion.
- **`workOrderId`**: (Indexed, Unique with `promotionId`) Reference to the work order where the promotion was applied.
- **`invoiceId`**: Reference to the finalized invoice.
- **`redemptionTimestamp`**: The timestamp of the original redemption event from the source system.
- **`createdAt`**: The timestamp when this record was created in the CRM database.

### `Promotion` Entity (Fields to be updated)
- **`totalUsageCount`**: A numeric counter that is incremented upon each successful redemption.

## Acceptance Criteria
### Scenario 1: Successful First-Time Redemption
- **Given** a `Promotion` exists with `promotionId="PROMO123"` and a `totalUsageCount` of 5.
- **And** no `PromotionRedemption` record exists for `workOrderId="WO-ABC"`.
- **When** the CRM system consumes a valid `PromotionRedeemed` event with `promotionId="PROMO123"` and `workOrderId="WO-ABC"`.
- **Then** a new `PromotionRedemption` record is created linking `promotionId="PROMO123"` and `workOrderId="WO-ABC"`.
- **And** the `totalUsageCount` for `PROMO123` is updated to 6.

### Scenario 2: Idempotent Handling of Duplicate Event
- **Given** a `PromotionRedemption` record already exists for `promotionId="PROMO123"` and `workOrderId="WO-ABC"`.
- **And** the `totalUsageCount` for `PROMO123` is 6.
- **When** the CRM system consumes a second, identical `PromotionRedeemed` event for `workOrderId="WO-ABC"`.
- **Then** no new `PromotionRedemption` record is created.
- **And** the `totalUsageCount` for `PROMO123` remains 6.
- **And** an informational log message is generated indicating a duplicate event was ignored.

### Scenario 3: Event for Non-Existent Promotion
- **Given** no `Promotion` exists with `promotionId="FAKEPROMO"`.
- **When** the CRM system consumes a `PromotionRedeemed` event with `promotionId="FAKEPROMO"`.
- **Then** no `PromotionRedemption` record is created.
- **And** a critical error is logged stating the promotion was not found.
- **And** the event message is moved to a Dead-Letter Queue.

## Audit & Observability
- **Logging:**
    - `INFO`: Successful processing of each redemption event, including `promotionId` and `workOrderId`.
    - `INFO`: Ignored duplicate redemption events, including `promotionId` and `workOrderId`.
    - `ERROR`: Failed processing due to validation errors (e.g., promotion not found) or data persistence issues.
- **Metrics:**
    - `promotions.redemptions.processed` (Counter): Incremented for every successful redemption. Tagged by `promotionId`.
    - `promotions.redemptions.duplicates` (Counter): Incremented for every duplicate event ignored. Tagged by `promotionId`.
    - `promotions.redemptions.failures` (Counter): Incremented for every failed event. Tagged by `reason` (e.g., `not_found`, `invalid_payload`).

## Open Questions
1.  **Scope of Enforcement:** The original story's Acceptance Criteria mentions "Usage limits enforced when configured." Does this mean this service should *reject* the `PromotionRedeemed` event if a usage limit (e.g., total uses, uses per customer) has already been met? Or is its sole responsibility to *record* the redemption and increment counters, with enforcement happening in the Workorder Execution domain *before* the promotion is applied?
    -   **Recommendation:** This story's scope should be limited to recording the event and updating counters. The Workorder Execution domain should be responsible for checking usage limits (by querying a CRM API) *before* finalizing an invoice with a promotion. This maintains clear separation of concerns. Please confirm this direction.

## Original Story (Unmodified – For Traceability)
# Issue #94 — [BACKEND] [STORY] Promotions: Record Promotion Redemption from Invoicing

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Promotions: Record Promotion Redemption from Invoicing

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **System**, I want **to record when a promotion is redeemed against a finalized invoice/workorder** so that **we can track usage and prevent abuse**.

## Details
- On invoice finalization, Workorder Execution emits PromotionRedeemed.
- CRM records redemption once (idempotent) and updates counters.

## Acceptance Criteria
- Redemption recorded once.
- Usage limits enforced when configured.
- Redemption links to Workorder/Invoice reference.

## Integration Points (Workorder Execution)
- Workorder Execution emits PromotionRedeemed; CRM consumes and updates usage.

## Data / Entities
- PromotionUsage
- RedemptionEvent
- ProcessingLog

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