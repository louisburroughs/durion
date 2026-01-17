Title: [BACKEND] [STORY] Accounting: Reverse Completion on Workorder Reopen
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/118
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
**As the** Accounting System,
**I need to** atomically reverse all financial entries and state changes associated with a workorder's completion when it is reopened,
**So that** the General Ledger remains accurate, premature invoicing is prevented, and financial audit trails are complete.

## Actors & Stakeholders
- **System Actor**: `Accounting Service` - The microservice responsible for processing accounting-related events and maintaining the financial integrity of workorders.
- **Triggering System**: `Work Execution System` - The system of record for workorder status, which emits the `WorkorderReopened` event.
- **Stakeholders**:
  - `Accountant` - Relies on the accuracy of the General Ledger (GL) and the `InvoiceReady` status for billing operations.
  - `Financial Controller` - Responsible for the overall integrity of financial records and reporting.

## Preconditions
1.  A `WorkorderCompleted` event for the target `workorderId` has been successfully processed by the `Accounting Service`.
2.  General Ledger journal entries exist, reflecting the transfer of value from a Work-in-Progress (WIP) account to a Finished Goods or Cost of Goods Sold (COGS) account for the workorder.
3.  The workorder's accounting record is marked with `InvoiceReady = true`.
4.  An invoice has **not** yet been generated for this workorder.

## Functional Behavior
### Trigger
The `Accounting Service` consumes a `WorkorderReopened` event from the enterprise message bus (e.g., Kafka topic `workexec.workorder.events`).

#### Expected Event Payload (`WorkorderReopened`)
```json
{
  "eventId": "uuid",
  "eventType": "WorkorderReopened",
  "eventTimestamp": "ISO-8601",
  "correlationId": "uuid",
  "payload": {
    "workorderId": "uuid",
    "reopenedByUserId": "string",
    "reopenedTimestamp": "ISO-8601",
    "reasonCode": "string",
    "reasonText": "string | null"
  }
}
```

### Main Success Scenario
1.  **Consume Event**: The `Accounting Service` consumes the `WorkorderReopened` event.
2.  **Idempotency Check**: The service checks if this `eventId` has been processed before. If so, it acknowledges the message and stops (see Alternate Flows).
3.  **Fetch State**: The service retrieves the current accounting state for the `workorderId`, including the original completion journal entry ID.
4.  **Validate Business Rules**: The service confirms that no invoice has been generated for this workorder. If an invoice exists, the process fails (see Error Flows).
5.  **Generate Reversal Journal Entry**: A new, offsetting General Ledger transaction is created. This transaction reverses the original completion entry (e.g., DEBIT `WIP Inventory` and CREDIT `Finished Goods Inventory`). The new entry must reference the original journal entry ID for auditability.
6.  **Update Workorder Accounting State**: The internal record for the workorder is updated:
    - `status` is changed from `Completed` to `Reopened` (or `InProgress`).
    - `isInvoiceReady` flag is set to `false`.
7.  **Persist Audit Log**: A detailed, immutable audit record is created documenting the reversal.
8.  **Acknowledge Event**: The message is acknowledged on the bus.
9.  **(Optional) Emit Event**: The service may emit a `WorkorderAccountingCompletionReversed` event for downstream consumers (e.g., reporting).

## Alternate / Error Flows
- **Duplicate Event**: If the `eventId` has been processed before, the system logs a warning, acknowledges the message, and takes no further action.
- **Workorder Not Found**: If the `workorderId` does not exist in the accounting system, log a critical error and move the event to a dead-letter queue (DLQ) for manual investigation.
- **Workorder Already Invoiced**: If the workorder has an associated invoice, the process **must fail**. A critical error is logged, the event is moved to a DLQ, and an alert is raised. The financial state is not changed.
- **Original Completion Entry Not Found**: If the original journal entry cannot be located, the process fails. A critical error is logged, the event is moved to a DLQ, and an alert is raised.

## Business Rules
- A workorder completion **cannot** be reversed if an invoice linked to it has been generated. This is a hard-stop financial control.
- All reversal journal entries must explicitly link back to the original journal entry they are reversing.
- The `reopenedByUserId` and `reasonCode` from the triggering event must be stored in the audit log for the reversal transaction.
- The reversal transaction (GL entry, state update, audit log) must be atomic. If any part fails, the entire operation must be rolled back.

## Data Requirements
- **Journal Entry Schema**:
    - `journalEntryId` (PK)
    - `transactionDate`
    - `description` (e.g., "Reversal of WO-123 completion")
    - `originalJournalEntryId` (FK)
    - `lines`: `[{accountId, debit, credit}]`
- **Workorder Accounting Record**:
    - `workorderId` (PK)
    - `isInvoiceReady` (boolean)
    - `accountingStatus` (e.g., `InProgress`, `Completed`, `Reopened`, `Invoiced`)
- **Audit Log Schema**:
    - `auditId` (PK)
    - `timestamp`
    - `eventType`: `WORKORDER_COMPLETION_REVERSED`
    - `entityId`: `workorderId`
    - `userId`: `reopenedByUserId`
    - `details`: JSON blob containing context like `reasonCode`, `originalJournalEntryId`, and `reversalJournalEntryId`.

## Acceptance Criteria
- **Given** a workorder is in a `Completed` accounting state with `isInvoiceReady = true` and has an associated WIP-to-Finished-Goods journal entry
  **When** a valid `WorkorderReopened` event is received for that workorder
  **Then** a new reversing journal entry is created in the General Ledger, the workorder's `isInvoiceReady` flag is set to `false`, and a detailed audit log is persisted.

- **Given** a workorder has been `Invoiced`
  **When** a `WorkorderReopened` event is received for that workorder
  **Then** the operation is rejected, a critical error is logged stating the "already invoiced" conflict, and no financial records are altered.

- **Given** a `WorkorderReopened` event has already been successfully processed
  **When** the same event with the same `eventId` is received again
  **Then** the system identifies it as a duplicate, takes no action, and acknowledges the message successfully.

- **Given** a valid `WorkorderReopened` event is received
  **When** the corresponding original completion journal entry cannot be found
  **Then** the operation is rejected, a critical error is logged, and the event is routed to a dead-letter queue for investigation.

## Audit & Observability
- **Audit**: An immutable audit trail must be recorded for every successful reversal. This log must contain: `workorderId`, `reopenedByUserId`, `reopenedTimestamp`, `reasonCode`, `originalJournalEntryId`, and the new `reversalJournalEntryId`.
- **Logging**:
    - INFO: Event received (`eventId`, `workorderId`).
    - WARN: Duplicate event detected.
    - ERROR: Validation failed (e.g., already invoiced, workorder not found).
    - INFO: Reversal successfully processed.
- **Metrics**:
    - `workorder.completion.reversals.success` (counter)
    - `workorder.completion.reversals.failure` (counter, tagged by `reason:invoiced`, `reason:not_found`, etc.)
    - `workorder.completion.reversals.latency` (timer)

## Open Questions
1.  **OQ1 (Critical)**: What are the specific General Ledger (GL) account numbers/IDs for the standard Work-in-Progress (WIP) and Finished Goods/COGS accounts that this process must use for the reversal entries?
2.  **OQ2 (Authorization)**: The original story mentions "Validate reopen authorization". Is trust in the incoming event from the `Work Execution System` sufficient, or does the `Accounting Service` need to perform its own authorization check by calling an external service (e.g., an IAM or Permissions service)?
3.  **OQ3 (Data Consistency)**: What is the expected behavior if the `workorderId` from the event is not found in the accounting system's database? Should this be treated as a transient error to be retried, or a permanent failure to be moved to a DLQ? The current assumption is DLQ.

---
## Original Story (Unmodified – For Traceability)
# Issue #118 — [BACKEND] [STORY] Accounting: Reverse Completion on Workorder Reopen

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Accounting: Reverse Completion on Workorder Reopen

**Domain**: user

### Story Description

/kiro
Safely reverse completion-related accounting state.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `WorkorderReopened` event

## Main Flow
1. Validate reopen authorization
2. Reverse WIP/finished postings if present
3. Mark workorder as not invoice-ready
4. Record reversal audit trail

## Acceptance Criteria
- [ ] Accounting state matches reopened workorder
- [ ] Reversal is fully auditable


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