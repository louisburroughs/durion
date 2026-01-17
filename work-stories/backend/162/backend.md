Title: [BACKEND] [STORY] Promotion: Record Promotion Audit Trail
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/162
Labels: type:story, domain:workexec, status:needs-review

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:ready-for-dev

### Recommended
- agent:workexec
- agent:story-authoring

---

**Rewrite Variant:** workexec-structured

## Story Intent
As a Shop Manager or Auditor, I need a non-repudiable audit trail created whenever a service estimate is promoted to a work order, so that I can trace the exact state of what was approved, who approved it, and when it was converted into executable work, ensuring accountability and resolving potential customer disputes.

## Actors & Stakeholders
- **Primary Actor:**
  - **System:** The automated process responsible for creating and persisting the audit record upon a successful promotion event.
- **Stakeholders:**
  - **Service Advisor:** The user role that typically initiates the promotion of an estimate to a work order.
  - **Shop Manager / Auditor:** The user roles that review and rely on the audit trail for operational oversight, quality control, and dispute resolution.
  - **Customer:** The indirect beneficiary who is assured that work is performed based on an agreed-upon and versioned estimate.

## Preconditions
- A valid \`Estimate\` exists in a promotable state (e.g., \`APPROVED\`).
- An \`ApprovalRecord\` associated with the \`Estimate\` exists, capturing customer consent.
- The user initiating the promotion is authenticated and has the necessary permissions (e.g., \`WORKORDER_CREATE\`).
- The system has successfully validated that the \`Estimate\` can be converted into a \`WorkOrder\`.

## Functional Behavior
### Trigger
The \`EstimatePromotedToWorkOrder\` business event is successfully completed within the Work Execution domain.

### Main Success Scenario
1.  Upon successful creation of a \`WorkOrder\` from an \`Estimate\`, the system initiates the audit trail creation process within the same transaction.
2.  The system creates a new, immutable \`PromotionAuditEvent\` record.
3.  The system populates the audit record with the following information:
    - A unique identifier for the audit event (\`auditEventId\`).
    - The identifier of the user who initiated the promotion (\`promotingUserId\`).
    - A precise, server-generated timestamp (\`eventTimestamp\`).
    - A reference to the newly created \`WorkOrder\` (\`workorderId\`).
    - A reference to the source \`Estimate\` (\`estimateId\`).
    - A **critical** reference to the specific, versioned snapshot of the estimate that was promoted (\`estimateSnapshotId\`). This ensures the audit trail points to the exact state of the estimate at the moment of promotion.
    - A reference to the \`ApprovalRecord\` that authorized the work (\`approvalId\`).
4.  The system calculates and stores a summary of the promoted items (e.g., \`{"total_cost": 1250.75, "labor_items": 3, "part_items": 5}\`) within the audit record for efficient retrieval and display.
5.  The system transactionally commits the new \`WorkOrder\`, its related state changes, and the \`PromotionAuditEvent\` record together.

## Alternate / Error Flows
- **Audit Record Creation Fails:**
  - If, for any reason (e.g., database connection issue, constraint violation), the \`PromotionAuditEvent\` record cannot be persisted, the entire parent transaction for the work order promotion **MUST** be rolled back.
  - No new \`WorkOrder\` will be created, and the source \`Estimate\` will revert to its pre-promotion state.
  - The system will log a \`CRITICAL\` error detailing the failure to create the audit record, as this represents a failed business-critical transaction.

## Business Rules
- **Atomicity of Promotion:** The creation of a \`WorkOrder\` from an \`Estimate\` and the creation of its corresponding \`PromotionAuditEvent\` are an atomic, all-or-nothing operation.
- **Immutability:** Once created, a \`PromotionAuditEvent\` record is immutable and cannot be altered or deleted through standard application workflows.
- **Traceability Mandate:** The audit trail must always link to a versioned, point-in-time snapshot of the estimate, not a live or mutable version.
- **Access Control:** The visibility of promotion audit records in the UI or via APIs is restricted to authorized roles (e.g., \`SHOP_MANAGER\`, \`SYSTEM_AUDITOR\`).

## Data Requirements
- **Entity:** \`PromotionAuditEvent\`
- **Fields:**
  - \`auditEventId\`: (PK) Unique identifier for the audit record.
  - \`workorderId\`: (FK, Indexed) Reference to the \`WorkOrder\` entity.
  - \`estimateId\`: (FK, Indexed) Reference to the source \`Estimate\` entity.
  - \`estimateSnapshotId\`: (FK, Indexed) **Non-negotiable** reference to the immutable version of the estimate.
  - \`approvalId\`: (FK) Reference to the \`ApprovalRecord\` entity.
  - \`promotingUserId\`: (FK, Indexed) Reference to the \`User\` who initiated the event.
  - \`eventTimestamp\`: (TimestampTZ) High-precision timestamp of the event.
  - \`eventType\`: (Text) A constant value, e.g., \`ESTIMATE_PROMOTED_TO_WORK_ORDER\`.
  - \`promotionSummary\`: (JSONB) A structured object containing key metrics like total cost, item counts, etc., for performance reasons.

## Acceptance Criteria
### Scenario 1: Successful Promotion and Audit Trail Creation
- **Given** an \`Estimate\` with ID \`E-101\` and snapshot ID \`ES-v3\` is in an \`APPROVED\` state
- **And** a user with sufficient permissions is logged in
- **When** the user successfully promotes \`Estimate\` \`E-101\` to a new \`WorkOrder\` \`WO-552\`
- **Then** the system MUST create a \`PromotionAuditEvent\` record
- **And** the audit record MUST reference \`workorderId: WO-552\`
- **And** the audit record MUST reference \`estimateSnapshotId: ES-v3\`
- **And** the audit record MUST contain the ID of the logged-in user
- **And** the \`promotionSummary\` field in the audit record must accurately reflect the totals from \`Estimate\` snapshot \`ES-v3\`.

### Scenario 2: Promotion Fails if Audit Record Cannot Be Saved
- **Given** a user is promoting an \`APPROVED\` \`Estimate\` \`E-102\`
- **And** the system successfully creates a \`WorkOrder\` in-memory
- **When** the system attempts to save the \`PromotionAuditEvent\` but encounters a database write error
- **Then** the entire promotion transaction MUST be rolled back
- **And** no new \`WorkOrder\` corresponding to \`E-102\` will exist in the database
- **And** the \`Estimate\` \`E-102\` MUST remain in the \`APPROVED\` state
- **And** a critical error MUST be logged indicating the audit failure.

## Audit & Observability
- **Logging:**
  - \`INFO\`: Log the successful creation of a \`PromotionAuditEvent\` including the \`auditEventId\`, \`workorderId\`, and \`promotingUserId\`.
  - \`ERROR\` / \`CRITICAL\`: Log any failure to create a \`PromotionAuditEvent\`, as this indicates a failed core business transaction. Include the stack trace and relevant context (e.g., \`estimateId\`).
- **Metrics:**
  - \`promotions.success.count\`: (Counter) Increment on every successful promotion and audit record creation.
  - \`promotions.failure.audit.count\`: (Counter) Increment when a promotion is rolled back due to an audit write failure.
- **Alerting:**
  - Configure a high-priority alert if the \`promotions.failure.audit.count\` metric increments, as this may signal a systemic issue with the database or audit subsystem.

## Domain Conflict Resolution

**Decision: Confirmed — \`domain:workexec\` is the single, authoritative domain.**

**Rationale:**
The promotion audit trail records a **state transition within the estimate → work order lifecycle**, which is owned by **Workorder Execution**. The \`domain:user\` reference found in the original story metadata was a classification error. While user context (actor identity) is captured as an attribute of the audit record, this does not make it the owning domain. The business process of promoting an estimate to a work order is fundamentally a work execution lifecycle operation.

**Classification Correction:**
- **Primary Domain:** \`domain:workexec\` ✅
- **Incorrect Reference:** \`domain:user\` (removed) ❌
- **No Story Split Required:** This is a single, cohesive story. The conflict was purely a metadata classification error, not a functional ambiguity.

**Status:** Unblocked and ready for implementation.

## Original Story (Unmodified – For Traceability)
# Issue #162 — [BACKEND] [STORY] Promotion: Record Promotion Audit Trail

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Promotion: Record Promotion Audit Trail

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
A promotion event completes successfully.

## Main Flow
1. System records an audit event including who initiated promotion and when.
2. System records the estimate snapshot version and approval reference used.
3. System stores a summary of items promoted (counts, totals) for quick review.
4. System links audit record to the workorder and estimate.
5. System exposes audit record in UI for authorized roles.

## Alternate / Error Flows
- Audit write fails → fail promotion or retry per strictness policy (recommended: fail).

## Business Rules
- Promotion must be auditable and traceable.
- Audit must reference the exact snapshot promoted.

## Data Requirements
- Entities: AuditEvent, Workorder, Estimate, ApprovalRecord
- Fields: eventType, actorUserId, timestamp, estimateId, snapshotVersion, approvalId, workorderId, summaryTotals

## Acceptance Criteria
- [ ] Promotion event is stored and retrievable.
- [ ] Audit record references estimate snapshot and approval.
- [ ] Audit record shows summary totals and item counts.

## Notes for Agents
Audit isn't optional—this protects you in customer disputes.


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
