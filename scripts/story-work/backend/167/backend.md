Title: [BACKEND] [STORY] Promotion: Validate Promotion Preconditions
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/167
Labels: story-implementation, user, type:story, domain:workexec, status:ready-for-dev

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
**As a** Service Advisor,
**I want** the system to validate all preconditions before promoting an approved estimate to a work order,
**so that** only valid, approved, and unique work orders are created, ensuring operational integrity and preventing duplicate work.

## Actors & Stakeholders
- **Primary Actor:**
  - `Service Advisor`: Initiates the promotion of an estimate to a work order.
- **Secondary Actors:**
  - `Back Office Staff`: May review promotion history and audit trails.
  - `System`: The automated agent responsible for executing the precondition validations.
- **Stakeholders:**
  - `Customer`: The beneficiary of the work order; relies on the accuracy of the promotion process.
  - `Workshop Manager`: Relies on the system to enforce that only approved work is scheduled.

## Preconditions
1.  An `Estimate` entity exists in the system with a status that allows promotion (e.g., `Approved`).
2.  The actor (`Service Advisor`) is authenticated and has the necessary permissions to promote estimates to work orders.
3.  The specific `Estimate` to be promoted has at least one associated `ApprovalRecord`.

## Functional Behavior
### Trigger: Promote Estimate to Work Order
An actor with sufficient permissions initiates a "Promote to Work Order" action via an API endpoint, providing the `estimateId` and the specific `snapshotVersion` of the estimate that was approved.

### System Validation Sequence
1.  **Locate Estimate & Approval:** The system retrieves the specified `Estimate` and its associated `ApprovalRecord(s)`.
2.  **Idempotency Check:** The system checks if a `WorkOrder` already exists that was created from this specific `estimateId` and `snapshotVersion`.
    -   If a `WorkOrder` already exists, the promotion is considered a duplicate request. The system halts further processing and returns a reference to the existing `WorkOrder`.
3.  **Approval Validity Check:** The system identifies the current, active `ApprovalRecord` for the given `estimateId`. It verifies that:
    -   The approval status is `Valid`.
    -   The approval has not expired (i.e., current time is before `expiresAt`).
    -   The approval has not been superseded by a more recent approval.
4.  **Approved Scope Check:** The system verifies that the valid `ApprovalRecord` has an associated `ApprovedScope` that references the same `snapshotVersion` being promoted.
5.  **Promotion Execution:** If all validations pass, the system proceeds with the creation of the `WorkOrder` (behavior for which is defined in a subsequent story). This story is concerned only with the successful validation outcome.
6.  **Outcome:**
    -   **On Success:** The system signals that all preconditions are met and allows the promotion process to continue.
    -   **On Failure:** The system blocks the promotion and returns a structured error response detailing the specific validation that failed.

## Alternate / Error Flows
| Flow | Trigger | System Response |
| :--- | :--- | :--- |
| **Duplicate Promotion** | An attempt is made to promote an `estimateId` / `snapshotVersion` that has already been successfully promoted. | - **Action:** Block promotion.<br>- **API Response:** `409 Conflict` status code.<br>- **Payload:** A structured error message including `errorCode: PROMOTION_ALREADY_EXISTS` and the `workorderId` of the existing work order. |
| **Approval Expired** | The most recent `ApprovalRecord` for the estimate has an `expiresAt` timestamp in the past. | - **Action:** Block promotion.<br>- **API Response:** `422 Unprocessable Entity` status code.<br>- **Payload:** A structured error message with `errorCode: APPROVAL_EXPIRED`, instructing the user to obtain a new approval. |
| **Approval Invalid** | The most recent `ApprovalRecord` is in a state other than `Valid` (e.g., `Revoked`, `Superseded`). | - **Action:** Block promotion.<br>- **API Response:** `422 Unprocessable Entity` status code.<br>- **Payload:** A structured error message with `errorCode: APPROVAL_INVALID`, instructing the user to review the estimate's approval status. |
| **No Approval Found** | No `ApprovalRecord` exists for the given `Estimate`. | - **Action:** Block promotion.<br>- **API Response:** `422 Unprocessable Entity` status code.<br>- **Payload:** A structured error message with `errorCode: APPROVAL_NOT_FOUND`. |

## Business Rules
- **BR1: Valid Approval Required:** An estimate can only be promoted to a work order if it is associated with a current, non-expired, and non-revoked `ApprovalRecord`.
- **BR2: Idempotency:** The promotion of a specific estimate `snapshotVersion` must be an idempotent operation. Subsequent valid requests for the same promotion must not create a new work order but instead return a reference to the original one.
- **BR3: Approved Scope Authority:** The scope of work for the resulting work order is strictly determined by the `ApprovedScope` linked to the valid `ApprovalRecord`. Any items not in the `ApprovedScope` must not be included.

## Data Requirements
| Entity | Field(s) | Description | Domain Owner |
| :--- | :--- | :--- | :--- |
| `Estimate` | `estimateId`, `snapshotVersion` | The unique identifier for the estimate and its specific version being promoted. | `workexec` |
| `ApprovalRecord` | `approvalStatus`, `expiresAt`, `snapshotVersionRef` | Records the state, validity period, and the specific estimate version it applies to. | `workexec` |
| `ApprovedScope`| `estimateId`, `approvedItems` | Defines the specific line items or services from the estimate that were approved. | `workexec` |
| `WorkOrder` | `workorderId`, `sourceEstimateId`, `sourceSnapshotVersion` | The resulting work order, with traceability back to the source estimate. | `workexec` |

## Acceptance Criteria
**Scenario 1: Successful Promotion with Valid Preconditions**
- **Given** an `Estimate` with a valid and non-expired `ApprovalRecord` for `snapshotVersion` "v3"
- **And** no `WorkOrder` has been previously created from this `Estimate` and `snapshotVersion`
- **When** the Service Advisor attempts to promote the `Estimate` for `snapshotVersion` "v3"
- **Then** the system validates all preconditions successfully
- **And** the system allows the promotion process to proceed.

**Scenario 2: Block Promotion due to Expired Approval**
- **Given** an `Estimate` whose only `ApprovalRecord` has an `expiresAt` date in the past
- **When** the Service Advisor attempts to promote the `Estimate`
- **Then** the system must block the promotion
- **And** the system must return an error response with `errorCode: APPROVAL_EXPIRED`.

**Scenario 3: Block Promotion due to Invalid Approval Status**
- **Given** an `Estimate` whose most recent `ApprovalRecord` has a status of `Revoked`
- **When** the Service Advisor attempts to promote the `Estimate`
- **Then** the system must block the promotion
- **And** the system must return an error response with `errorCode: APPROVAL_INVALID`.

**Scenario 4: Idempotent Handling of Duplicate Promotion Request**
- **Given** an `Estimate` for `snapshotVersion` "v2" has already been promoted, creating `WorkOrder` "WO-123"
- **When** the Service Advisor attempts to promote the same `Estimate` and `snapshotVersion` "v2" again
- **Then** the system must not create a new work order
- **And** the system must return a `409 Conflict` response containing the reference to the existing "WO-123".

## Audit & Observability
- **Event: `EstimatePromotionAttempted`**
  - **On Success:** Log the `estimateId`, `snapshotVersion`, `actorId`, and a success status.
  - **On Failure:** Log the `estimateId`, `snapshotVersion`, `actorId`, a failure status, and the specific reason for failure (e.g., `APPROVAL_EXPIRED`, `DUPLICATE_PROMOTION`). This is critical for troubleshooting and operational support.
- **Metrics:**
  - `promotions.validation.success`: Counter for successful precondition validations.
  - `promotions.validation.failure`: Counter for failed precondition validations, tagged by failure reason.

## Open Questions
- None. The requirements are clear and sufficient for implementation.

## Original Story (Unmodified – For Traceability)
# Issue #167 — [BACKEND] [STORY] Promotion: Validate Promotion Preconditions

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Promotion: Validate Promotion Preconditions

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
Service Advisor / Back Office

## Trigger
User attempts to promote an estimate to a workorder.

## Main Flow
1. User selects 'Promote to Workorder' on an estimate.
2. System verifies the estimate has a current valid approval (not expired, not superseded).
3. System verifies approved scope exists (full or partial) and references the correct snapshot version.
4. System checks that promotion has not already occurred for this estimate/snapshot (idempotency).
5. System either allows promotion or returns actionable errors.

## Alternate / Error Flows
- Approval missing/expired → block and instruct resubmission.
- Promotion already performed → return existing workorder reference.

## Business Rules
- Only current valid approvals can be promoted.
- Promotion must be idempotent.
- Approved scope governs what becomes executable work.

## Data Requirements
- Entities: Estimate, ApprovalRecord, ApprovedScope, Workorder, AuditEvent
- Fields: estimateId, snapshotVersion, approvalStatus, expiresAt, promotionRef, workorderId

## Acceptance Criteria
- [ ] System blocks promotion if approval is invalid or expired.
- [ ] System detects and handles re-tries without duplicates.
- [ ] System reports specific failed preconditions.

## Notes for Agents
Make precondition failures actionable—this saves operator time.


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