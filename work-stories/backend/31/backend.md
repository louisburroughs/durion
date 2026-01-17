Title: [BACKEND] [STORY] Putaway: Execute Put-away Move (Staging → Storage)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/31
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

---
**Rewrite Variant:** inventory-flexible
**Clarification Applied:** #229 (handling invalid/full destinations; handling zero on-hand at source)
---

## Story Intent
As a Stock Clerk, I need to execute a confirmed put-away move from a staging area to a final storage location, so that inventory location and on-hand are updated atomically and become available for downstream processes like picking.

## Actors & Stakeholders
- **Stock Clerk (Primary Actor):** Uses mobile scanning to move inventory and record the transaction.
- **Inventory Service (System Actor):** Validates, posts ledger movement, updates task state, and audits.
- **Work Execution (Downstream Stakeholder):** Consumes updated location/on-hand for picking tasks.

## Preconditions
- Stock Clerk is authenticated.
- An active `PutawayTask` exists with status `IN_PROGRESS` or `PENDING_EXECUTION`.
- Physical inventory is present at the staging source location.
- Source and destination locations are scannable and known to the system.

## Functional Behavior

### Happy Path
1. Clerk starts “Execute Put-away Move” for an active `PutawayTask`.
2. Clerk scans source (staging) location.
3. System validates scanned source matches task source.
4. Clerk scans item/pallet identifier.
5. System validates scanned item matches task.
6. Clerk scans destination storage location.
7. System validates destination type is `STORAGE` and passes SKU compatibility rules.
8. Clerk confirms move summary.
9. System commits an atomic transaction:
   - decrement on-hand at source
   - increment on-hand at destination
   - create immutable ledger entry with transaction type `PUTAWAY`
   - mark `PutawayTask` as `COMPLETED`
   - emit audit entry
10. Clerk sees success.

## Alternate / Error Flows (Resolved)

### A) Destination scanned but invalid for SKU
Examples: zone restriction, hazardous mismatch, temperature class mismatch, unauthorized bin.

**Default behavior (mandatory):**
- Block the putaway transaction.
- Return/display error `LOCATION_NOT_VALID_FOR_SKU`.
- Clerk must choose a different valid location.

**Override (optional, disabled by default):**
- Allowed only with permission `OVERRIDE_LOCATION_COMPATIBILITY`.
- Requires mandatory reason code + free-text justification.
- Emits audit event `PutawayOverrideLocationRule`.

### B) Destination scanned but at full capacity
**Default behavior:**
- Block the putaway to that location.
- Prompt clerk to choose a different valid location or split quantity across multiple locations.

**Optional override:**
- Allowed only if permission `OVERRIDE_LOCATION_CAPACITY` is present.
- Must be within configured overfill tolerance (e.g., 5–10%).
- Must capture justification.

**Audit requirements if overridden:**
- `previousCapacity`, `newCapacity`, `overrideReasonCode = CAPACITY_OVERRIDE`, `approvedBy`.

### C) Source scanned but system shows zero on-hand
This indicates a data consistency error.

**Default behavior (mandatory):**
- Block the putaway transaction.
- Return/display error `NO_ON_HAND_AT_SOURCE_LOCATION`.
- System must not proceed or silently create inventory.

**Recovery / reconciliation flow (permission-gated):**
1. Initiate cycle count/recount:
   - Permission `INITIATE_CYCLE_COUNT`
   - Creates reconciliation task for the source location.
2. Inventory adjustment (exceptional):
   - Permission `ADJUST_INVENTORY`
   - Requires explicit reason code (e.g., `MISPLACED_STOCK`, `UNRECORDED_RECEIPT`)
   - Requires manager approval if above threshold
   - Adjustment must be completed before putaway proceeds.

Explicitly disallowed:
- proceeding without correcting inventory records
- “assume quantity exists” behavior

## Business Rules
- Put-away moves originate from `STAGING` and end in `STORAGE`.
- On-hand cannot become negative.
- Every inventory movement is recorded in the immutable `InventoryLedger`.
- `PutawayTask` can be `COMPLETED` only after successful ledger commit.

## Data Requirements
- **Ledger entry (create):** transactionId, SKU/productId, quantity, fromLocationId, toLocationId, `transactionType = PUTAWAY`, actorId, timestamp.
- **On-hand update:** decrement `(SKU, fromLocationId)`, increment `(SKU, toLocationId)`.
- **PutawayTask update:** status transitions to `COMPLETED` on success.
- **Override auditing (if enabled):** include override permission used, reason codes, justification, approver.

## Acceptance Criteria

**AC1: Successful Put-away**
- Given a `PutawayTask` is `IN_PROGRESS` for 10 units of `ABC-123` from `STAGING-01` to `A-01-B-03`
- When the clerk scans source, item, destination and confirms
- Then the ledger entry `PUTAWAY` is created and on-hand updates are applied atomically
- And the task is marked `COMPLETED` and audited.

**AC2: Invalid Destination for SKU Blocks Move**
- When destination is not valid for the SKU
- Then the system blocks with `LOCATION_NOT_VALID_FOR_SKU`
- And no ledger/on-hand changes occur.

**AC3: Destination Full Blocks Move (Default)**
- When destination is full
- Then the system blocks and prompts for alternate location or split quantity
- And no ledger/on-hand changes occur.

**AC4: Source Has Zero On-hand Blocks Move**
- When source shows zero on-hand for the item
- Then the system blocks with `NO_ON_HAND_AT_SOURCE_LOCATION`
- And user must initiate reconciliation or approved adjustment before retrying.

## Audit & Observability
- Audit every successful putaway: transactionId, actorId, timestamp, SKU, qty, from/to.
- Log failed validation at `WARN`; transaction failures at `ERROR` with correlation id.
- Metrics for successful putaways and failed attempts.

## Open Questions
None.

## Original Story (Unmodified – For Traceability)
## Backend Implementation for Story

**Original Story**: [STORY] Putaway: Execute Put-away Move (Staging → Storage)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Stock Clerk**, I want to execute put-away moves so that inventory becomes available for picking.

## Details
- Scan from/to locations.
- Update ledger with movement PutAway.

## Acceptance Criteria
- Ledger entry created.
- On-hand updated per destination.
- Task marked complete.
- Audited.

## Integrations
- Workexec sees accurate pick locations.

## Data / Entities
- InventoryLedgerEntry(PutAway), PutawayTaskState, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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