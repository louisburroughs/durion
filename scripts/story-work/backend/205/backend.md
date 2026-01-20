Title: [BACKEND] [STORY] Approval: Record Partial Approval
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/205
Labels: type:story, layer:functional, kiro, domain:workexec, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:ready-for-dev

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
As a Service Advisor, I need to record a customer's partial approval on a Work Order so that I can proceed with the authorized work while maintaining a clear record of the declined items for future reference and reporting.

## Actors & Stakeholders
- **Service Advisor**: The primary user who interacts with the customer and records their approval decision in the system.
- **Customer**: The individual who owns the vehicle/asset and provides approval for the work.
- **Technician / Mechanic**: Consumes the output of this process to understand which specific tasks are authorized to be performed.
- **System**: The POS application responsible for persisting the state of the Work Order and its line items.

## Preconditions
1.  A Work Order (or Estimate) exists in the system.
2.  The Work Order has a status of `AwaitingApproval`.
3.  The Work Order contains at least two line items (e.g., services, parts) that require individual approval.
4.  The Service Advisor is authenticated and has the necessary permissions to modify Work Orders.

## Functional Behavior
1.  **Trigger**: The Service Advisor initiates the "Record Approval" action for a Work Order that is in the `AwaitingApproval` state.
2.  The system presents a view of all line items associated with the Work Order.
3.  For each line item, the Service Advisor can independently set an approval status, typically `Approved` or `Declined`, based on the customer's feedback.
4.  **Approval Method Capture**:
    - The system determines the approval method based on configuration:
      - **Store-level default**: Default approval method for the location
      - **Customer-level override**: Customer-specific approval requirements
      - **Precedence rule**: Customer requirements take precedence over store requirements
    - Approval methods: **Digital Signature** or **Electronic Approval by Service Advisor**
    - The selected method identifier must be stored with the approval record
5.  The Service Advisor confirms the set of approvals and declinations for the entire Work Order.
6.  Upon confirmation, the system performs the following actions atomically:
    a.  Persists the `approvalStatus` (`Approved`/`Declined`) for each line item.
    b.  Stores the approval method identifier and proof (signature data or Service Advisor confirmation).
    c.  Recalculates the `totalApprovedAmount` for the Work Order based on the sum of `Approved` line items.
    d.  Updates the overall Work Order status from `AwaitingApproval` to `ApprovedForWork` if at least one item was approved.
    e.  If all items are declined, the system transitions the Work Order to terminal `Declined` state.

## Alternate / Error Flows
- **Flow 1: Attempt to approve a Work Order in an invalid state**
    - **Given** a Work Order has a status of `InProgress`.
    - **When** the Service Advisor attempts to record an approval.
    - **Then** the system must reject the action and display an error message: "Approval can only be recorded for Work Orders awaiting approval."

- **Flow 2: User cancels the operation**
    - **Given** the Service Advisor is in the process of marking line items as `Approved` or `Declined`.
    - **When** the Service Advisor cancels the operation before final confirmation.
    - **Then** the system must discard all changes, and the Work Order and its line items must remain in their original state.

- **Flow 3: System error during confirmation**
    - **Given** the Service Advisor has confirmed the partial approval.
    - **When** a system or network error prevents the successful saving of all changes.
    - **Then** the entire transaction must be rolled back, and the system must inform the user of the failure, allowing them to retry the operation.

- **Flow 4: Re-Approval within Approval Window**
    - **Given** a line item has been marked as `Declined`.
    - **And** the estimate is still within its **Approval Window** (configurable time period).
    - **When** the customer changes their mind and wants to approve the previously declined item.
    - **Then** the Service Advisor can change the item status from `Declined` to `Approved`.
    - **And** the Work Order schedule must be amended to ensure location and mechanic availability for the new work item.

## Business Rules

### State Transitions
- A Work Order's line items can only have their approval status modified when the parent Work Order is in the `AwaitingApproval` state.
- Once a Work Order moves to `ApprovedForWork` or a subsequent state, this approval flow cannot be used again. Any changes would require a new, distinct business process (e.g., "Amend Approved Work Order").
- **Terminal Status**: When all line items are declined, the Work Order transitions to `Declined` state (confirmed as correct).

### Approval Window & Re-Approval
- A `Declined` item may be changed to `Approved` as long as the change occurs within the estimate's **Approval Window** (configurable time period).
- When a declined item is re-approved, the Work Order schedule **must be amended** to ensure:
  - Location availability
  - Mechanic availability
  - Resource allocation for the newly approved work item

### Financial Calculations
- `Declined` line items must be excluded from all financial calculations related to the authorized work total.
- The `totalApprovedAmount` on the Work Order must always equal the sum of the prices of its `Approved` line items.

### Approval Method Configuration
- Approval method is configurable at two levels:
  1. **Store-level**: Default approval method for all customers at that location
  2. **Customer-level**: Customer-specific approval requirements
- **Precedence**: Customer requirements take precedence over store requirements
- **Methods**: Digital Signature OR Service Advisor Electronic Approval

## Data Requirements
This story will modify the following domain entities:

- **WorkOrder**
    - `workOrderId` (UUID, PK)
    - `status` (Enum: `Draft`, `AwaitingApproval`, `ApprovedForWork`, `InProgress`, `Declined`, `Completed`)
    - `totalApprovedAmount` (Money/Decimal)
    - `approvalWindowEnd` (DateTime) - Configurable deadline for re-approval changes
    - `approvalMethodConfig` (String) - Reference to store/customer approval method configuration

- **LineItem**
    - `lineItemId` (UUID, PK)
    - `workOrderId` (UUID, FK)
    - `description` (String)
    - `price` (Money/Decimal)
    - `approvalStatus` (Enum: `PendingApproval`, `Approved`, `Declined`)
    - `approvalTimestamp` (DateTime) - When approval status was set
    - `approvalMethodUsed` (Enum: `DigitalSignature`, `ServiceAdvisorElectronic`)
    - `approvalProofId` (UUID, nullable) - Reference to signature data or confirmation record

- **ApprovalConfiguration**
    - `configId` (UUID, PK)
    - `entityType` (Enum: `Store`, `Customer`)
    - `entityId` (UUID) - Store or Customer ID
    - `approvalMethod` (Enum: `DigitalSignature`, `ServiceAdvisorElectronic`)
    - `approvalWindowDuration` (Integer) - Duration in hours/days for re-approval eligibility

## Acceptance Criteria
**AC1: Successful Partial Approval**
- **Given** a Work Order is `AwaitingApproval` and contains three line items, each `PendingApproval`.
- **When** the Service Advisor marks Line Item 1 and Line Item 2 as `Approved`, and Line Item 3 as `Declined`, and confirms the changes.
- **Then** the system must:
    - Update Line Item 1 and 2 `approvalStatus` to `Approved`.
    - Update Line Item 3 `approvalStatus` to `Declined`.
    - Store the approval method used for each line item.
    - Update the Work Order `status` to `ApprovedForWork`.
    - Update the Work Order `totalApprovedAmount` to be the sum of the prices of Line Item 1 and 2.

**AC2: All Line Items Are Declined**
- **Given** a Work Order is `AwaitingApproval` and contains two line items.
- **When** the Service Advisor marks both line items as `Declined` and confirms.
- **Then** the system must:
    - Update both line items' `approvalStatus` to `Declined`.
    - Update the Work Order `status` to `Declined` (terminal state).
    - Update the Work Order `totalApprovedAmount` to zero.

**AC3: Action on Work Order in Invalid State**
- **Given** a Work Order exists with a status of `InProgress`.
- **When** a user attempts to access the "Record Approval" function for that Work Order.
- **Then** the system must prevent the action and display an informative error.

**AC4: Approval Method Configuration - Customer Precedence**
- **Given** a Store has a default approval method of `ServiceAdvisorElectronic`.
- **And** a Customer has an override approval method of `DigitalSignature`.
- **When** recording approval for that customer's Work Order.
- **Then** the system must use `DigitalSignature` method (customer takes precedence).

**AC5: Re-Approval Within Approval Window**
- **Given** a Work Order with Line Item A marked as `Declined` at time T.
- **And** the estimate's Approval Window is 48 hours.
- **And** current time is T+24 hours (within window).
- **When** the Service Advisor changes Line Item A from `Declined` to `Approved`.
- **Then** the system must:
    - Update Line Item A `approvalStatus` to `Approved`.
    - Trigger schedule amendment workflow to verify location and mechanic availability.
    - Recalculate `totalApprovedAmount` to include Line Item A.

**AC6: Re-Approval Outside Approval Window**
- **Given** a Work Order with Line Item B marked as `Declined` at time T.
- **And** the estimate's Approval Window is 48 hours.
- **And** current time is T+72 hours (outside window).
- **When** the Service Advisor attempts to change Line Item B from `Declined` to `Approved`.
- **Then** the system must reject the change and display an error: "Approval Window has expired. A new estimate/work order is required."

## Audit & Observability
- **Audit Log**: An immutable audit event must be generated upon successful confirmation of an approval decision. The event must contain:
    - `eventId`, `timestamp`, `eventType` (`WorkOrderApprovalRecorded`)
    - `workOrderId`
    - `userId` (of the Service Advisor)
    - `approvalMethod` (DigitalSignature or ServiceAdvisorElectronic)
    - `approvalProofId` (if applicable)
    - `correlationId`
    - A snapshot of the changes, including the previous and new `approvalStatus` for each affected line item.
    - For re-approvals: include `previousApprovalTimestamp` and `approvalWindowRemaining`
- **Metrics**: The system should emit metrics to track:
    - Rate of approved vs. declined line items
    - Re-approval rate within approval window
    - Approval method usage (signature vs. electronic)
    - Schedule amendment triggers from re-approvals

## Original Story (Unmodified – For Traceability)
# Issue #205 — [BACKEND] [STORY] Approval: Record Partial Approval

## Current Labels
- backend
- story-implementation
- type:story
- layer:functional
- kiro

## Current Body
## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #22 - Approval: Record Partial Approval
**URL**: https://github.com/louisburroughs/durion/issues/22
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
*Generated by Missing Issues Audit System - 2025-12-26T17:39:13.309796384*