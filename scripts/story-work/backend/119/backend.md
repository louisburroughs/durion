Title: [BACKEND] [STORY] Accounting: Ingest WorkCompleted Event
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/119
Labels: type:story, domain:accounting, status:ready-for-dev

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** accounting-strict
---
## Story Intent
As the Accounting System, I need to process a `WorkCompleted` event so that I can finalize any Work-In-Progress (WIP) accounting and mark the associated workorder as eligible for invoicing, ensuring a timely and accurate billing process.

## Actors & Stakeholders
- **Accounting System (Primary Actor)**: The system responsible for consuming the event, performing financial calculations, and updating the workorder's accounting state.
- **Work Execution System (Event Source)**: The upstream system that performs the physical or digital work and emits the `WorkCompleted` event upon its conclusion.
- **Billing System (Downstream Consumer)**: A subsequent system that will consume the `INVOICE_ELIGIBLE` status to initiate customer invoicing.
- **Accountant (Human Stakeholder)**: Relies on the accuracy of WIP reconciliation and the timely transition of workorders to an invoice-eligible state for financial reporting and revenue cycle management.

## Preconditions
1.  A `WorkCompleted` event is received via the enterprise message bus on the designated topic.
2.  The event payload conforms to the schema defined in the `Durion Accounting Event Contract v1`.
3.  The workorder referenced by the `workorderId` in the event exists within the Accounting System.
4.  The workorder's current accounting status is `WORK_IN_PROGRESS` or a similar state that permits this transition.

## Functional Behavior
### Happy Path: `WorkCompleted` Event Processing
1.  **Trigger**: The `Accounting System`'s event listener consumes a `WorkCompleted` event.
2.  **Event Validation**: The system validates the event payload against its required schema (e.g., presence and format of `eventId`, `workorderId`, `completionTimestamp`).
3.  **Workorder Retrieval**: The system retrieves the corresponding workorder's accounting record using the `workorderId` from the event.
4.  **Idempotency Check**: The system verifies that this `eventId` has not been processed previously for this workorder to prevent duplicate transactions.
5.  **Conditional WIP Reconciliation**:
    - The system checks the `WIP_ACCOUNTING_ENABLED` system configuration.
    - If `true`, the system executes the WIP reconciliation sub-process. This involves creating and posting journal entries to move accumulated costs from the WIP General Ledger (GL) asset account to a 'Cost of Goods Sold' (COGS) or 'Finished Work' expense account.
    - If `false`, this step is skipped.
6.  **Status Update**: The system updates the `accountingStatus` of the workorder record to `INVOICE_ELIGIBLE`.
7.  **State Persistence**: All changes, including new journal entries and the workorder status update, are committed atomically to the database.
8.  **Outcome**: The workorder is now flagged as ready to be invoiced, and its costs are correctly reflected in the general ledger. The event is acknowledged as successfully processed.

## Alternate / Error Flows
- **Invalid Event Payload**: If the event fails schema validation, the system will not process it. The event is routed to a Dead Letter Queue (DLQ) with a `validation_error` reason.
- **Workorder Not Found**: If the `workorderId` from the event does not match any record in the system, the event is routed to the DLQ with a `workorder_not_found` reason.
- **Invalid State Transition**: If the workorder is not in a valid state for this transition (e.g., it is already `INVOICED` or `CANCELLED`), the event is acknowledged but ignored to maintain state integrity, and a warning is logged. The idempotency check should handle cases where it is already `INVOICE_ELIGIBLE`.
- **WIP Reconciliation Failure**: If the creation of journal entries fails (e.g., due to a data issue or GL system error), the entire transaction is rolled back. The workorder status remains unchanged. The event is routed to a retry queue and, after exhausting retries, to the DLQ with a `wip_reconciliation_failed` reason for manual investigation.

## Business Rules
- **No Revenue Recognition**: This process finalizes costs but does **not** create an invoice, recognize revenue, or generate an Accounts Receivable (AR) entry. It is a preparatory step for the billing cycle.
- **Configurable WIP Handling**: The execution of WIP-related journal entries is strictly controlled by the `WIP_ACCOUNTING_ENABLED` system configuration. When disabled, this event only serves to update the workorder's status.
- **Idempotency**: Event processing must be idempotent. Receiving the same `WorkCompleted` event multiple times must not result in duplicate journal entries or errors after the first successful processing.

## Data Requirements
### Input: `WorkCompleted` Event
The event payload must contain, at a minimum:
- `eventId`: `UUID` - Unique identifier for this event instance.
- `eventTimestamp`: `ISO 8601` - Timestamp of when the event was generated.
- `workorderId`: `UUID` - The unique identifier of the workorder that was completed.
- `completionTimestamp`: `ISO 8601` - The business timestamp of when the work was officially completed.
- `finalCostSummary`: `Object` - A structured object detailing the final costs (e.g., labor, parts). *Clarification needed, see Open Questions*.

### State Changes: Workorder Accounting Entity
- `accountingStatus`: `WORK_IN_PROGRESS` ➡️ `INVOICE_ELIGIBLE`
- `completionProcessedTimestamp`: Set to the timestamp when processing is complete.
- Associated `JournalEntry` records created if WIP is enabled.

## Acceptance Criteria
- **AC1: Success with WIP Enabled**
  - **Given** the `WIP_ACCOUNTING_ENABLED` configuration is `true`
  - **And** a valid `WorkCompleted` event is received for a workorder with accumulated WIP costs
  - **When** the event is processed
  - **Then** the corresponding journal entries are created to move costs from the WIP GL account to the Finished Work GL account
  - **And** the workorder's `accountingStatus` is updated to `INVOICE_ELIGIBLE`.

- **AC2: Success with WIP Disabled**
  - **Given** the `WIP_ACCOUNTING_ENABLED` configuration is `false`
  - **And** a valid `WorkCompleted` event is received
  - **When** the event is processed
  - **Then** no new journal entries are created
  - **And** the workorder's `accountingStatus` is updated to `INVOICE_ELIGIBLE`.

- **AC3: Failure on Unknown Workorder**
  - **Given** a `WorkCompleted` event is received with a `workorderId` that does not exist
  - **When** the system attempts to process the event
  - **Then** the event is routed to the Dead Letter Queue with a `workorder_not_found` reason.

- **AC4: Idempotent Processing**
  - **Given** a `WorkCompleted` event for a specific workorder has already been successfully processed
  - **When** the exact same `eventId` is received again
  - **Then** the system acknowledges the event without making duplicate GL entries or state changes
  - **And** a log is generated indicating a duplicate event was handled.

## Audit & Observability
- **Audit Log**: An immutable audit trail must be created for the state transition, capturing the `workorderId`, the old status (`WORK_IN_PROGRESS`), the new status (`INVOICE_ELIGIBLE`), the responsible `system` principal, and the `eventId` that triggered the change.
- **Metrics**:
  - `events.work_completed.processed.success`: A counter, tagged by outcome (e.g., `wip_enabled`, `wip_disabled`).
  - `events.work_completed.processed.failure`: A counter, tagged by reason (e.g., `validation_error`, `not_found`, `wip_failure`).
  - `events.work_completed.processing.latency`: A timer/histogram measuring the duration from event consumption to acknowledgement.
- **Logging**: Structured logs containing the `workorderId` and `eventId` must be emitted for key stages: event consumption, validation success/failure, WIP processing start/end, and final outcome.

## Open Questions
1.  **Event Payload for WIP**: What specific fields are guaranteed to be in the `WorkCompleted` event's `finalCostSummary`, as defined by the `Durion Accounting Event Contract v1`? Does it contain a final, authoritative breakdown of costs (labor, parts, fees), or must the Accounting service look these up from another data source using the `workorderId`?
2.  **GL Account Determination**: What is the precise GL account number or logic to determine the 'Finished Work' or 'COGS' destination account for the WIP transfer? Is this a single system-wide account, or is it determined by properties of the workorder (e.g., business unit, work type, location)?

---
## Original Story (Unmodified – For Traceability)
# Issue #119 — [BACKEND] [STORY] Accounting: Ingest WorkCompleted Event

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest WorkCompleted Event

**Domain**: user

### Story Description

/kiro
Determine WIP finalization or readiness for invoicing.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `WorkCompleted` event from Workorder Execution

## Main Flow
1. Validate completion event and source workorder
2. If WIP accounting enabled:
   - Transfer WIP to Finished Work
3. Mark workorder as invoice-eligible
4. Persist completion accounting state

## Business Rules
- Completion does not create AR or revenue
- WIP handling is configuration-driven

## Acceptance Criteria
- [ ] WIP is reconciled (if enabled)
- [ ] Workorder marked invoice-ready

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## References
- Durion Accounting Event Contract v1

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299815/Durion_Accounting_Event_Contract_v1.pdf)

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