Title: [BACKEND] [STORY] Approval: Capture In-Person Customer Approval
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/206
Labels: type:story, layer:functional, kiro, domain:workexec, status:ready-for-dev

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
---

## Story Intent
**As a** Service Advisor,
**I want to** capture a customer's in-person approval for a work estimate directly in the POS system,
**so that** I have an official, timestamped record of their consent and the associated work can proceed to the next stage.

## Actors & Stakeholders
- **Service Advisor (Primary Actor)**: The system user who interacts with the customer and records their approval in the system.
- **Customer (External Actor)**: The individual providing verbal or physical consent for the work.
- **System**: The POS application that orchestrates the state change and creates the audit trail.
- **Mechanic/Technician (Stakeholder)**: Is notified or can see that work is approved and can be started.
- **Billing Department (Stakeholder)**: Is interested in the approval as a prerequisite for future invoicing.
- **Auditor (Stakeholder)**: Requires a clear and immutable record of customer approvals for compliance and dispute resolution.

## Preconditions
- The Service Advisor is authenticated and has the necessary permissions to manage Work Orders.
- A Work Order (or a similar entity like an Estimate) exists in the system.
- The Work Order is in a state that requires customer approval (e.g., `Pending Approval`).
- The customer is physically present with the Service Advisor.

## Functional Behavior
1.  **Trigger**: The Service Advisor navigates to the relevant Work Order screen within the POS application.
2.  **Action**: The Service Advisor selects an action to "Capture In-Person Approval".
3.  **Confirmation**: The system presents a confirmation dialog to the Service Advisor (e.g., "Confirm that the customer has approved all work items and the total amount?").
4.  **Approval Granularity**: The approval is for the entire work order/estimate. However, the Service Advisor (as editor) can mark certain line items as declined before the approval is completed.
5.  **Approval Capture**: The system executes the approval logic:
    - It validates that the Work Order is in a valid state for approval.
    - It updates the Work Order's status to `Approved`.
    - It records the current timestamp, the ID of the Service Advisor who performed the action, and the approval method.
    - **Approval Method**: Configurable by location and customer. Default behavior is click-to-confirm (Service Advisor click on confirmation button).
6.  **Feedback**: The system displays a success message confirming the approval has been recorded and the Work Order status has been updated.
7.  **Side-Effects**: The system generates an immutable audit event for the approval.

## Alternate / Error Flows
- **Invalid State**: If the Work Order is not in a `Pending Approval` state (e.g., it is already `Approved`, `In Progress`, or `Canceled`), the "Capture In-Person Approval" action shall be disabled or, if triggered via API, return an error. The system will display a message like, "This Work Order is not in a state that can be approved."
- **Concurrent Modification**: If another user modifies the Work Order while the approval is being processed, the system should detect the conflict and return an error, preventing the state change.
- **System Failure**: If the database or a downstream service is unavailable during the save operation, the entire transaction must be rolled back. The Work Order's state will remain `Pending Approval`, and the system will display an appropriate error message to the Service Advisor.

## Business Rules

### Approval States
The work order follows this state machine progression:
- **Approved**: Estimate is approved by the customer. The estimate has been accepted but may not yet be scheduled for work.
- **Ready for Work**: Approved estimate with parts, mechanic, and location available or scheduled. All prerequisites for work execution are in place.
- **Scheduled**: Parts, mechanic, and location are confirmed as available or scheduled. (Note: This state may apply to individual tasks or the overall work order depending on domain definition.)

### Decline Workflow
Decline behavior is scoped as follows:
- A "Declined" estimate never becomes a work order.
- A "Declined" estimate can be changed to "Approved" within X days (where X is configurable).
- Decline behavior for a work order that was already approved is separate and out of scope for this story.
- Full decline workflow details are covered in a separate story.

### Approval Record Rules
- A Work Order can only be moved to an `Approved` state from a pre-defined set of preceding states (e.g., `Pending Approval`).
- The approval record is immutable once created. It must be permanently associated with the Work Order.
- The identity of the employee recording the approval (`approvingUserId`) and the exact UTC timestamp (`approvalTimestamp`) are non-nullable and mandatory fields.
- The type of approval method (`approvalType`) must be recorded as `InPerson`.

## Data Requirements
The following data attributes need to be captured or updated on the `WorkOrder` entity:
- `status`: (State Machine) Transition from `Pending Approval` to `Approved`.
- `approvalTimestamp`: `datetime` (UTC) - The exact moment the approval was confirmed in the system.
- `approvingUserId`: `UUID` - The unique identifier of the Service Advisor user who recorded the approval.
- `approvalType`: `enum` - Set to `InPerson`.
- `approvalMethod`: `enum` (configurable by location/customer) - Captures the method used (e.g., `ClickToConfirm`, `SignatureCapture`). Default: `ClickToConfirm`.
- `approvalNotes`: `text` (optional) - A field for the Service Advisor to add relevant notes about the interaction.

## Acceptance Criteria
**AC1: Successful In-Person Approval of a Work Order**
- **Given** a Work Order exists with a status of `Pending Approval`.
- **And** a Service Advisor is logged into the system.
- **When** the Service Advisor initiates and confirms the "Capture In-Person Approval" action for that Work Order.
- **Then** the system updates the Work Order's status to `Approved`.
- **And** the `approvalTimestamp` is set to the current UTC time.
- **And** the `approvingUserId` is set to the ID of the logged-in Service Advisor.
- **And** the `approvalType` is set to `InPerson`.
- **And** the `approvalMethod` is set according to the configured default (or customer/location override).
- **And** an audit event `workorder.approved` is published.

**AC2: Attempt to Approve an Already Approved Work Order**
- **Given** a Work Order exists with a status of `Approved`.
- **When** the Service Advisor attempts to use the "Capture In-Person Approval" action.
- **Then** the system prevents the action and displays an error message: "This Work Order has already been approved."
- **And** the Work Order's status and approval data remain unchanged.

**AC3: Attempt to Approve a Work Order in an Invalid State**
- **Given** a Work Order exists with a status of `Completed`.
- **When** the Service Advisor attempts to use the "Capture In-Person Approval" action.
- **Then** the system prevents the action and displays an error message: "This Work Order cannot be approved in its current state."
- **And** the Work Order's status remains `Completed`.

**AC4: Decline Individual Line Items Before Approval**
- **Given** a Work Order with multiple line items exists in `Pending Approval` status.
- **And** the Service Advisor has permission to edit the estimate.
- **When** the Service Advisor marks one or more line items as declined before confirming approval.
- **Then** the approval captures the final approved set of line items.
- **And** the Work Order transitions to `Approved` with only the approved items included.
- **And** the approval record reflects the final state of line items.

## Audit & Observability
- **Audit Event**: A `workorder.approved` event must be emitted to the audit stream upon successful completion.
- **Event Payload**: The event must contain `workOrderId`, `customerId`, `previousStatus`, `newStatus: 'Approved'`, `approvalType: 'InPerson'`, `approvalMethod`, `approvingUserId`, and `timestamp`.
- **Logging**:
    - INFO: Log the initiation and successful completion of the approval process, including `workOrderId` and `approvingUserId`.
    - ERROR: Log any failures during the approval process, including the error details, `workOrderId`, and a request correlation ID.

---
## Original Story (Unmodified – For Traceability)
## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #21 - Approval: Capture In-Person Customer Approval
**URL**: https://github.com/louisburroughs/durion/issues/21
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for backend development
- Coordinate with corresponding frontend implementation if needed

### Technical Requirements
**Backend Implementation Requirements:**
- Use Spring Boot with Java 21
- Implement RESTful API endpoints following established patterns
- Include proper request/response validation
- Implement business logic with appropriate error handling
- Ensure database operations are transactional where needed
- Include comprehensive logging for debugging
- Follow security best practices for authentication/authorization

### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Backend agents: Focus on Spring Boot microservices, Java 21, REST APIs, PostgreSQL. Ensure API contracts align with frontend requirements.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `backend` - Implementation type

---
*Generated by Missing Issues Audit System - 2025-12-26T17:39:15.989974434*