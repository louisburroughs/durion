Title: [BACKEND] [STORY] Completion: Validate Completion Preconditions
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/154
Labels: backend, story-implementation, user, type:story, domain:workexec, status:ready-for-dev, clarification:domain

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
As the System, I must enforce all completion preconditions before a Work Order can be transitioned to the 'Completed' state, to ensure data integrity, prevent revenue leakage, and avoid customer disputes by validating that all work, parts, and approvals are finalized.

## Actors & Stakeholders
- **Primary Actor**: `System` (as the enforcer of the business rules).
- **Triggering Actor**: `Service Advisor` (or any user role with permissions to complete a work order).
- **Stakeholders**:
    - `Service Manager`: Relies on process integrity to ensure quality and operational efficiency.
    - `Billing Department`: Requires accurate and complete work orders to generate correct invoices.
    - `Customer`: Expects a transparent and accurate record of work performed.

## Preconditions
1. A `WorkOrder` exists in a status that permits completion (e.g., `WORK_IN_PROGRESS`, `AWAITING_COMPLETION`).
2. The `User` initiating the action has the necessary permissions to complete a `WorkOrder`.
3. The System has access to the `WorkOrder` and all its associated entities, including `WorkOrderItem`s, `PartUsageEvent`s, `LaborEntry`s, and `ChangeRequest`s.

## Functional Behavior
1. **Trigger**: A `User` with appropriate permissions initiates the "Complete Work Order" action for a specific `WorkOrder`.
2. **Precondition Check Execution**: The System executes a series of configured validation checks, known as the "Completion Gate," against the `WorkOrder` and its related data.
3. **Success Path**: If all validation checks pass, the System confirms the `WorkOrder` is eligible for completion, allowing the state transition to `WORK_COMPLETED` to proceed.
4. **Failure Path**: If one or more validation checks fail, the System blocks the completion action. It then generates and returns a structured response containing a comprehensive list of all failed checks and the reasons for failure, providing actionable feedback to the `User`.

## Alternate / Error Flows
- **Incomplete Work Items**: The completion is blocked if any `WorkOrderItem` designated as required for completion is not in a `COMPLETED` state. The error response will identify the specific incomplete items.
- **Unreconciled Parts Usage**: The completion is blocked if any associated `PartUsageEvent` is in an unreconciled state (e.g., `PENDING_ALLOCATION`, `QUANTITY_MISMATCH`). The error response will detail the parts requiring reconciliation.
- **Pending Gated Change Requests**: The completion is blocked if any associated `ChangeRequest` that is `isApprovalGated` is not in an `APPROVED` state. The error response will specify which approvals are pending.
- **User Lacks Permissions**: If the triggering `User` does not have the "Complete Work Order" permission, the action is denied with a `403 Forbidden` error before any precondition checks are run.

## Business Rules
- **BR-1 (Completion Gate is Mandatory)**: The state transition of a `WorkOrder` to `WORK_COMPLETED` MUST be gated by the successful execution of all configured completion precondition checks. Direct state changes that bypass this gate are forbidden.
- **BR-2 (Required Work Items Completion)**: All `WorkOrderItem`s where the `isRequiredForCompletion` flag is true MUST have a status of `COMPLETED`.
- **BR-3 (Gated Change Request Approval)**: All `ChangeRequest`s associated with the `WorkOrder` where the `isApprovalGated` flag is true MUST have a status of `APPROVED`.
- **BR-4 (Parts and Labor Reconciliation)**: All `PartUsageEvent`s and `LaborEntry`s must be in a reconciled and finalized state (e.g., `ALLOCATED`, `CONFIRMED`). No entries can be in a pending or draft state.
- **BR-5 (Comprehensive Failure Feedback)**: On validation failure, the system MUST return a complete list of all failing preconditions. It must not stop at the first failure.
- **BR-6 (Configurability of Checks)**: The specific set of checks that constitute the Completion Gate MAY be configurable based on `WorkOrder` type or `Location`, but the enforcement of the configured set is absolute.

## Data Requirements
- **Input**:
    - `workOrderId`: The unique identifier for the `WorkOrder` to be validated.
- **Entities for Validation**:
    - `WorkOrder`: `status`
    - `WorkOrderItem`: `status`, `isRequiredForCompletion`
    - `PartUsageEvent`: `status`
    - `LaborEntry`: `status`
    - `ChangeRequest`: `status`, `isApprovalGated`
- **Output (on validation failure)**: A structured response object containing:
    - `canComplete`: `false`
    - `failedChecks`: An array of objects, each detailing a failed precondition (e.g., `{ check: 'PENDING_APPROVALS', message: 'Change Request #CR-123 requires approval.', entityId: 'CR-123' }`).
- **Output (on validation success)**: A structured response object containing:
    - `canComplete`: `true`
    - `failedChecks`: An empty array.

## Acceptance Criteria
- **AC-1 (Success Path)**:
    - **Given** a `WorkOrder` has all `isRequiredForCompletion` items in a `COMPLETED` state
    - **And** all associated `isApprovalGated` `ChangeRequest`s are in an `APPROVED` state
    - **And** all associated `PartUsageEvent`s are in a reconciled state
    - **When** a user attempts to complete the `WorkOrder`
    - **Then** the system must validate all preconditions as passed and allow the completion process to proceed.

- **AC-2 (Failure: Incomplete Work Items)**:
    - **Given** a `WorkOrder` has at least one `isRequiredForCompletion` `WorkOrderItem` not in a `COMPLETED` state
    - **When** a user attempts to complete the `WorkOrder`
    - **Then** the system must block the completion
    - **And** return a failure message identifying the specific incomplete `WorkOrderItem`(s).

- **AC-3 (Failure: Pending Change Request)**:
    - **Given** a `WorkOrder` has an associated `isApprovalGated` `ChangeRequest` in a `PENDING_APPROVAL` state
    - **When** a user attempts to complete the `WorkOrder`
    - **Then** the system must block the completion
    - **And** return a failure message identifying the specific `ChangeRequest` that is pending approval.

- **AC-4 (Failure: Unreconciled Parts)**:
    - **Given** a `WorkOrder` has an associated `PartUsageEvent` in a `PENDING_ALLOCATION` state
    - **When** a user attempts to complete the `WorkOrder`
    - **Then** the system must block the completion
    - **And** return a failure message identifying the specific part that is unreconciled.

- **AC-5 (Failure: Multiple Issues)**:
    - **Given** a `WorkOrder` has both an incomplete `isRequiredForCompletion` item and a pending `isApprovalGated` `ChangeRequest`
    - **When** a user attempts to complete the `WorkOrder`
    - **Then** the system must block the completion
    - **And** return a failure message that lists *both* issues.

## Audit & Observability
- **Events**:
    - On successful validation, emit a `WorkOrderCompletionPreconditionsMet` event including `workOrderId` and `userId`.
    - On failed validation, emit a `WorkOrderCompletionPreconditionsFailed` event including `workOrderId`, `userId`, and a structured payload detailing each failed check.
- **Logging**:
    - Log every validation attempt at an `INFO` level, including the `workOrderId` and the outcome (pass/fail).
    - If validation fails, log the specific reasons at a `WARN` level.
- **Metrics**:
    - `workorder.completion.validation.attempts`: A counter for every time the validation service is called.
    - `workorder.completion.validation.success`: A counter for successful validations.
    - `workorder.completion.validation.failure`: A counter for failed validations, tagged by failure reason (e.g., `reason:incomplete_items`, `reason:pending_approval`).
    - `workorder.completion.validation.latency`: A timer to measure the duration of the validation process.

## Open Questions
- none

## Original Story (Unmodified – For Traceability)
# Issue #154 — [BACKEND] [STORY] Completion: Validate Completion Preconditions

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Completion: Validate Completion Preconditions

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
System

## Trigger
User attempts to complete a workorder.

## Main Flow
1. System checks required workorder items are marked complete per policy.
2. System verifies parts usage and labor entries are reconciled (no invalid quantities).
3. System checks there are no unresolved approval-gated change requests.
4. System generates a completion checklist and prompts for missing items.
5. System allows completion only when all checks pass.

## Alternate / Error Flows
- Pending approval exists → block completion and show what is pending.
- Unreconciled parts usage → block and show reconciliation steps.

## Business Rules
- Completion checks are configurable but must be enforced consistently.
- Completion requires resolving approval-gated items.

## Data Requirements
- Entities: Workorder, WorkorderItem, LaborEntry, PartUsageEvent, ChangeRequest
- Fields: status, completionChecklist, pendingApprovals, unreconciledItems

## Acceptance Criteria
- [ ] System blocks completion when required conditions are not met.
- [ ] System provides a clear checklist of what to fix.
- [ ] System allows completion when all conditions pass.

## Notes for Agents
Completion gate is the last chance to prevent invoice disputes and leakage.


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