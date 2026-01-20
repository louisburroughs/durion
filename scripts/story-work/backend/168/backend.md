Title: [BACKEND] [STORY] Approval: Submit Estimate for Customer Approval
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/168
Labels: type:story, domain:workexec, status:needs-review

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
**As a** Service Advisor,
**I want to** submit a completed work estimate for customer approval,
**so that** I can formalize the proposed scope of work and pricing, and trigger the customer consent process.

## Actors & Stakeholders
- **Service Advisor (Primary Actor)**: The user who prepares the estimate and initiates the submission process.
- **System (Actor)**: The POS backend system that validates the estimate, manages state transitions, creates immutable records, and logs audit events.
- **Customer (Stakeholder)**: The external party whose approval is being requested. Their information is used to generate the approval request.
- **Auditor (Stakeholder)**: Requires a verifiable and immutable record of when an estimate was submitted for approval and by whom.

## Preconditions
- The Service Advisor is authenticated and has the necessary permissions to create and submit estimates.
- An `Estimate` entity exists and is in the `Draft` state.
- The `Estimate` is associated with a valid `Customer` record.

## Functional Behavior
1.  **Trigger**: The Service Advisor initiates the "Submit for Approval" action for an estimate in the `Draft` state.
2.  **Validation**: The system performs a "completeness check" on the estimate. This validation is defined by `BR-1`.
3.  **State Transition**: Upon successful validation, the system transitions the `Estimate` status from `Draft` to `PendingApproval`.
4.  **Snapshot Creation**: The system creates an immutable `EstimateSnapshot` record. This snapshot captures the exact state of the estimate at the moment of submission (including all line items, pricing, taxes, terms, and totals). The new snapshot is versioned and linked to the `Estimate`.
5.  **Payload Generation**: The system generates an `ApprovalRequest` payload. This payload contains all necessary information for a downstream notification service to contact the customer, such as a unique approval token/link, customer contact details, and consent language.
6.  **Auditing**: The system logs a structured `EstimateSubmittedForApproval` event, capturing the details of the action as defined in the **Audit & Observability** section.

## Alternate / Error Flows
- **Flow 1: Validation Failure**
    - **Trigger**: The estimate fails the completeness check (`BR-1`).
    - **Outcome**: The system rejects the submission. The `Estimate` remains in the `Draft` state.
    - **System Feedback**: The system returns an error response detailing the specific validation rule(s) that failed (e.g., "Estimate must contain at least one line item," "Customer contact information is missing").

- **Flow 2: Invalid State**
    - **Trigger**: The user attempts to submit an estimate that is not in the `Draft` state (e.g., it is already `PendingApproval` or `Approved`).
    - **Outcome**: The system rejects the submission.
    - **System Feedback**: The system returns an error response indicating the action is invalid for the current state (e.g., "Estimate is already pending approval and cannot be submitted again").

## Business Rules
- **BR-1 (Estimate Completeness)**: An estimate can only be submitted for approval if it meets all completeness criteria. The exact criteria are pending clarification (see `OQ-1`).
- **BR-2 (State Immutability)**: Only an estimate in the `Draft` state can be transitioned to `PendingApproval` via this action.
- **BR-3 (Snapshot Immutability)**: The `EstimateSnapshot` created upon submission is immutable. Any changes to the estimate after submission require retracting the current approval request and creating a new estimate version, which then requires a new submission and snapshot.
- **BR-4 (Audit Trail)**: Every submission attempt, whether successful or failed, must be auditable. Successful submissions must generate a formal audit event.

## Data Requirements
- **Estimate**:
    - `estimateId`: Unique identifier.
    - `status`: State field, transitions from `Draft` -> `PendingApproval`.
    - `version`: Integer, incremented for each new revision.
    - `customerId`: Foreign key to the Customer entity.
- **EstimateSnapshot**:
    - `snapshotId`: Unique identifier.
    - `estimateId`: Foreign key to the parent Estimate.
    - `snapshotVersion`: Integer, corresponds to the estimate version at time of creation.
    - `snapshotData`: A structured, immutable representation (e.g., JSONB) of the complete estimate details.
    - `createdAt`: Timestamp of creation.
    - `submittedByUserId`: Identifier of the user who submitted the estimate.
- **ApprovalRequest**:
    - `approvalRequestId`: Unique identifier.
    - `estimateId`: Foreign key.
    - `snapshotId`: Foreign key to the specific snapshot being approved.
    - `approvalToken`: A unique, secure token for the customer to use for approval.
    - `status`: `Issued`, `Viewed`, `Responded`, etc.
    - `consentText`: The specific legal/consent text shown to the customer.

## Acceptance Criteria
**Scenario 1: Successful Submission of a Valid Estimate**
- **Given** an estimate is in the `Draft` state
- **And** it meets all completeness validation criteria
- **When** the Service Advisor submits the estimate for approval
- **Then** the system transitions the estimate's status to `PendingApproval`
- **And** the system creates a new, immutable `EstimateSnapshot` linked to the estimate
- **And** the system generates a corresponding `ApprovalRequest` payload
- **And** the system logs an `EstimateSubmittedForApproval` audit event.

**Scenario 2: Submission of an Incomplete Estimate**
- **Given** an estimate is in the `Draft` state
- **But** it is missing a required field (e.g., has no line items)
- **When** the Service Advisor attempts to submit the estimate for approval
- **Then** the system rejects the request
- **And** the estimate's status remains `Draft`
- **And** the system returns an error message specifying the validation failure.

**Scenario 3: Attempt to Re-submit an Estimate Already Pending Approval**
- **Given** an estimate is already in the `PendingApproval` state
- **When** the Service Advisor attempts to submit it for approval again
- **Then** the system rejects the request
- **And** the estimate's status remains `PendingApproval`
- **And** the system returns an error message indicating it is already pending approval.

## Audit & Observability
- **Event**: `EstimateSubmittedForApproval`
- **Trigger**: Successful submission of an estimate.
- **Required Log Fields**:
    - `timestamp`: ISO 8601 timestamp of the event.
    - `eventType`: `EstimateSubmittedForApproval`.
    - `eventSource`: The service name (e.g., `workexec-service`).
    - `actorId`: The unique identifier of the Service Advisor who performed the action.
    - `entityId`: `estimateId` of the submitted estimate.
    - `entityVersion`: The version number of the estimate that was submitted.
    - `details`:
        - `snapshotId`: The ID of the newly created immutable snapshot.
        - `approvalRequestId`: The ID of the generated approval request.

## Open Questions
- **OQ-1**: What are the specific, non-negotiable fields and conditions that define a "complete" estimate ready for submission? Please provide a checklist.
    - _Example considerations: Must have at least one line item? Must have non-zero total? Must have taxes calculated and present? Must customer contact information (email/phone) be present? Are terms and conditions required?_

## Original Story (Unmodified – For Traceability)
# Issue #168 — [BACKEND] [STORY] Approval: Submit Estimate for Customer Approval

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Approval: Submit Estimate for Customer Approval

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
Service Advisor

## Trigger
A Draft estimate is complete and ready for customer consent.

## Main Flow
1. User selects 'Submit for Approval'.
2. System validates estimate completeness (required fields, items, taxes, totals, terms).
3. System transitions estimate to PendingApproval and freezes an approval snapshot.
4. System generates an approval request payload (method, link/token, consent text).
5. System logs the submission event for audit.

## Alternate / Error Flows
- Validation fails (missing taxes, missing items) → block and show actionable errors.
- Estimate already pending approval → prevent duplicate submissions and show status.

## Business Rules
- Only submit-ready estimates may enter PendingApproval.
- Submission creates an immutable approval snapshot version.
- Submission must be auditable (who/when).

## Data Requirements
- Entities: Estimate, ApprovalRequest, ApprovalSnapshot, AuditEvent
- Fields: status, approvalRequestId, snapshotVersion, consentText, submittedBy, submittedDate, approvalMethod

## Acceptance Criteria
- [ ] System blocks submission when required completeness checks fail.
- [ ] PendingApproval state is set and visible.
- [ ] An approval snapshot is created and referenced by the request.

## Notes for Agents
Approval snapshot must remain immutable; later revisions require resubmission.


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