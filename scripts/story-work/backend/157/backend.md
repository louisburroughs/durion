Title: [BACKEND] [STORY] Execution: Handle Part Substitutions and Returns
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/157
Labels: type:story, domain:workexec, status:ready-for-dev

## Story Intent
As a Technician or Parts Counter Staff, accurately record part substitutions and returns on a work order so inventory levels remain correct, billing is accurate, and a clear audit trail exists.

## Actors & Stakeholders
- **Technician / Parts Counter (Primary):** Performs substitutions/returns at point of work.
- **System (POS/Work Execution):** Processes changes, updates records, emits events.
- **Service Advisor:** Reviews and approves substitutions when they raise price; can also price match.
- **Inventory Manager:** Depends on accurate consumption data for reconciliation.
- **Customer:** Impacted by price changes from substitutions.
- **Accounting System (Downstream):** Consumes inventory adjustment events for COGS.

## Preconditions
1. Work Order exists and is `IN_PROGRESS`.
2. At least one part (`OriginalPart`) has been issued to the Work Order.
3. User is authenticated with permission to modify part usage on the Work Order.
4. The `SubstitutePart` exists in the product catalog.

## Functional Behavior
### Scenario 1: Part Substitution
1. Trigger: Technician selects an issued `OriginalPart` and chooses `Substitute Part`.
2. System prompts for `SubstitutePart` and quantity.
3. Technician selects and confirms.
4. System records substitution by:
   a. Decrementing `consumedQuantity` of `OriginalPart`.
   b. Creating a usage record for `SubstitutePart` with specified quantity.
   c. Creating `SubstitutionLink` between `OriginalPart` usage and `SubstitutePart` usage.
5. System evaluates financial impact (price/tax difference) against approval rules.
6. If price increases and is not price-matched, flag the Work Order/line as `PENDING_APPROVAL` and route to any Service Advisor for approval or price match.
7. If a Service Advisor price matches the higher-priced substitute down to the original price, do not require approval.
8. If approval is rejected, cancel the substitution line item (no change applied); maintain audit of the attempt.
9. Emit a single `InventoryAdjusted` event with adjustments for returned `OriginalPart` and consumed `SubstitutePart`.

### Scenario 2: Part Return
1. Trigger: Technician selects an issued part and chooses `Return Unused Part`.
2. System prompts for `returnQuantity`.
3. Validate `returnQuantity` does not exceed consumed quantity.
4. Update usage record by decrementing `consumedQuantity` by `returnQuantity`.
5. Emit `InventoryAdjusted` event for returned quantity.

## Alternate / Error Flows
- **Substitute Unavailable:** If `SubstitutePart` has insufficient inventory, block with error.
- **Substitution Not Allowed:** If policy disallows substitution for `OriginalPart`, block and inform user.
- **Return Exceeds Consumed:** If `returnQuantity` > consumed, reject with error; no event emitted.
- **Invalid Part Selection:** If part was not issued to the Work Order, block action.
- **Approval Rejected:** If price-increase approval is rejected, substitution line is canceled; original part usage remains.

## Business Rules
- Maintain auditable traceability between original and substituted parts via `SubstitutionLink`.
- Emit immutable events for substitutions/returns to support inventory and financial reconciliation.
- Approval policy:
  - Any price increase triggers approval unless a Service Advisor price matches the substitute to the original price.
  - Any Service Advisor may approve or perform price match.
  - Rejection of the price increase cancels the substitution line item (no billing change).

## Data Requirements
- **WorkOrderItemPartUsage:** `workOrderItemId`, `productId`, `issuedQuantity`, `consumedQuantity`, `returnedQuantity`
- **PartSubstitutionLink:** `sourceUsageId`, `substituteUsageId`, `substitutedQuantity`, `reasonCode` (optional), `timestamp`
- **WorkOrder:** `workOrderStatus` (e.g., `IN_PROGRESS`, `PENDING_APPROVAL`)
- **Event: InventoryAdjusted:** `eventId`, `idempotencyKey`, `sourceEntity`, `sourceId`, `adjustments[{productId, quantityDelta, reason}]`

## Acceptance Criteria
**AC1: Successful substitution with price increase → approval**
- Given Work Order `IN_PROGRESS` with Part A ($10) consumed
- When substituting one unit with Part B ($15)
- Then new usage for Part B is created, Part A consumed decremented, `SubstitutionLink` recorded
- And Work Order/line is flagged `PENDING_APPROVAL`
- And one `InventoryAdjusted` event emits +1 for Part A, -1 for Part B

**AC2: Price match avoids approval**
- Given Work Order `IN_PROGRESS` with Part A ($10) consumed
- And Service Advisor price matches Part B ($15) down to $10 during substitution
- When substitution is submitted
- Then substitution records are created, `SubstitutionLink` recorded, no `PENDING_APPROVAL` flag, and one `InventoryAdjusted` event emitted

**AC3: Approval rejection cancels substitution**
- Given a substitution is pending approval
- When a Service Advisor rejects the price increase
- Then the substitution line is canceled, original part usage remains, no billing change is applied, and the rejection is audited

**AC4: Return unused parts**
- Given Work Order `IN_PROGRESS` with 5 units of Part C consumed
- When returning 2 units
- Then consumed quantity updates to 3
- And an `InventoryAdjusted` event emits +2 for Part C
- And no approval is required

**AC5: Prevent over-return**
- Given 3 units of Part D consumed
- When attempting to return 4 units
- Then the system rejects the operation; consumed quantity remains 3; no `InventoryAdjusted` event is emitted

**AC6: Event idempotency**
- Given an `InventoryAdjusted` event for a substitution was emitted with an idempotency key
- When a duplicate event with the same key is received
- Then it is ignored and inventory is not double-adjusted

## Audit & Observability
- Audit every substitution/return with event type (`PART_SUBSTITUTION`, `PART_RETURN`), actor `userId`, timestamp, part IDs, quantity change, reason (if provided), and approval outcome (approved, rejected, price-matched).
- Metrics: track substitution rate (% of parts), approval request frequency, and rejection rate.
- Monitor `InventoryAdjusted` event emission success/failure.

## Open Questions
- None. Clarification #323 resolved prior approval policy details.

---
## Original Story (Unmodified – For Traceability)
# Issue #157 — [BACKEND] [STORY] Execution: Handle Part Substitutions and Returns

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Execution: Handle Part Substitutions and Returns

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Parts Counter / Technician

## Trigger
A different part is used or unused parts are returned.

## Main Flow
1. User selects a part item and chooses 'Substitute' or 'Return'.
2. System records substitution linking original and substituted part references.
3. System records quantity returned and updates usage totals.
4. If substitution impacts price/tax, system flags for approval if required.
5. System records all events for audit and inventory reconciliation.

## Alternate / Error Flows
- Substitution not allowed by policy → block.
- Return would create negative consumed quantity → block.

## Business Rules
- Substitutions must preserve traceability to original authorized scope.
- Returns must be reconciled against issued/consumed quantities.
- Price/tax impacts may require customer approval.

## Data Requirements
- Entities: PartUsageEvent, WorkorderItem, SubstitutionLink, ChangeRequest
- Fields: originalProductId, substituteProductId, quantityReturned, eventType, requiresApprovalFlag

## Acceptance Criteria
- [ ] System records substitutions with traceability.
- [ ] Returns reconcile correctly without negative totals.
- [ ] Approval is triggered when substitution changes customer-visible totals (policy).
- [ ] Substituted or returned parts emit a single InventoryAdjusted event
- [ ] Adjustment references the original issued part record
- [ ] Inventory quantities reconcile correctly after adjustment
- [ ] COGS impact (if any) is reversible and auditable
- [ ] Duplicate adjustment events do not double-adjust inventory

## Integrations

### Accounting
- Emits Event: InventoryAdjusted
- Event Type: Non-posting (inventory / COGS correction)
- Source Domain: workexec
- Source Entity: WorkorderPartUsage
- Trigger: Part substitution or return after initial issue
- Idempotency Key: workorderId + originalPartId + adjustedPartId + adjustmentVersion

## Notes for Agents
Substitution is a classic variance driver—capture it cleanly for invoice explanations.


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