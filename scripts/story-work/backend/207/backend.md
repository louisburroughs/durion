Title: [BACKEND] [STORY] Approval: Capture Digital Customer Approval
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/207
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
**As a** Service Advisor,
**I want to** capture and persist a customer's digital approval for a work order or estimate,
**so that** I have a non-repudiable, auditable record that authorizes work to begin and ensures compliance with business policies.

## Actors & Stakeholders
- **Service Advisor (User):** The primary user initiating the approval process on behalf of the customer, or guiding the customer through it.
- **Customer (External Actor):** The individual providing the approval for the specified work and costs.
- **System (Internal Actor):** The POS backend system responsible for processing the approval, updating state, and storing the record.
- **Stakeholders:**
    - **Billing Domain:** Is notified of the approval to enable invoicing once work is complete.
    - **Audit Domain:** Requires a durable, immutable record of the approval event for compliance and dispute resolution.

## Preconditions
- A `Work Order` or `Estimate` exists within the system.
- The target entity is in a state that requires customer approval (e.g., `Pending Customer Approval`).
- The user (Service Advisor or an authenticated system process) has the necessary permissions to record an approval.

## Functional Behavior
This story describes the backend API functionality for capturing a digital approval.

### Entity Scope
- Approvals can be captured for both **Estimates** and **Work Orders**
- The endpoint should support both entity types

### Approval Flow
1.  **Trigger**: The system receives a `POST` request to a secure endpoint, such as:
    - `/api/v1/estimates/{estimateId}/approvals`
    - `/api/v1/work-orders/{workOrderId}/approvals`
2.  **Input Validation**: The system validates that the entity ID corresponds to an existing Estimate or Work Order and that the request payload contains all required approval data.
3.  **State Verification**: The system verifies that the target entity is currently in the `Pending Customer Approval` state. If not, the request is rejected.
4.  **Signature Capture**:
    - The system captures signature data in **JSON + PNG** format
    - **Stroke vectors** (x/y coordinates + timestamps) are captured alongside the rendered **PNG image**
    - The signature is bound to an **ApprovalPayload** containing:
      - `originType` / `originId` (estimate/workorder)
      - `originVersion` or `documentDigest`
      - `amount`, `currency`, key line items summary
      - `signerUserId`
      - `timestamp`
    - A `payloadHash` (SHA-256) is computed for tamper-evidence
5.  **Persistence**:
    - The system creates a new, immutable `Approval` record
    - This record contains the approval details (signature data, timestamp, IP address, user agent, payload hash, etc.)
    - The `Approval` record is atomically associated with the entity
6.  **State Transition**: The system transitions the status from `Pending Customer Approval` to `Approved`.
7.  **PDF Signing** (if applicable):
    - If the approval generates or attaches to a PDF (estimate/invoice/workorder), apply cryptographic PDF signature using Apache PDFBox or EU DSS
    - Include visible appearance with captured signature image
8.  **Event Emission**: The system publishes a `workexec.EntityApproved` domain event containing the entity ID, type, and approval ID.
9.  **Response**: The system returns a `201 Created` HTTP response, including the unique identifier of the newly created `Approval` record.

## Alternate / Error Flows
- **Error - Entity Not Found**: If the provided entity ID does not exist, the system returns a `404 Not Found` error.
- **Error - Invalid State**: If the entity is not in the `Pending Customer Approval` state (e.g., it is already `Approved`, `In Progress`, `Denied`, `On Hold`, or `Transferred`), the system returns a `409 Conflict` error with a message indicating the invalid state.
- **Error - Invalid Payload**: If the request payload is missing required fields (e.g., `customerSignatureData`, `approvalPayload`), the system returns a `400 Bad Request` error with details about the validation failure.
- **Error - Authorization Failure**: If the authenticated principal does not have permission to perform this action, the system returns a `403 Forbidden` error.
- **Error - Signature Integrity**: If the payload hash does not match the signed content, the system returns a `400 Bad Request` error indicating tampering.

## Business Rules

### State Machine
The approval process follows these state transitions:
- **Pre-approval state**: `Pending Customer Approval`
- **Post-approval state**: `Approved`
- **Other relevant states**: `Denied` (must have cause), `In process`, `On Hold`, `Transferred`

### Versioning & Mutability
- Estimates are **editable until `Approved`**
- Changes to an estimate in the `Approved` state **require a new version** of the estimate
- If a new version of the estimate is created, any work orders connected to the old version must be put into a `Transferred` status, with a reference to the new estimate
- A digital approval can only be captured once for a specific version of an entity
- The captured approval record is considered legally binding and must be stored immutably

### Retention Policy
**Retention varies by transaction type:**
- **B2B transactions**: Approval records are kept through invoicing and payment (for the lifetime of the invoice)
- **B2C transactions**: Signatures are archived for the entire lifecycle of the invoice

### Tamper-Evidence
- The system must capture sufficient metadata (timestamp, IP, payload hash, etc.) to support the validity of the digital approval
- Anti-replay protection: `intentId` is single-use and expires quickly

## Data Requirements

### Approval Entity
An `Approval` entity will be created with the following attributes:
- `approvalId`: UUID, Primary Key
- `entityType`: Enum (`Estimate`, `WorkOrder`) - identifies what is being approved
- `entityId`: UUID, Foreign Key to the Estimate or Work Order
- `entityVersion`: String (optional) - captures version if versioning is enabled
- `approvalTimestamp`: ISO 8601 UTC string, timestamp of when the approval was submitted
- `customerSignatureData`: JSON object containing:
  - `signatureImage`: Base64-encoded PNG of rendered signature
  - `signatureStrokes`: Array of stroke vectors (x/y coordinates + timestamps) - optional for forensics
- `approvalPayload`: JSON object containing the approval intent (see Functional Behavior)
- `payloadHash`: String (SHA-256 hash of approvalPayload)
- `approverIpAddress`: String, IP address of the device used for approval
- `approverUserAgent`: String, User-Agent string of the browser/client used for approval
- `pdfSignatureReference`: String (optional), reference to signed PDF document
- `createdAt`: ISO 8601 UTC string, system timestamp
- `retentionCategory`: Enum (`B2B`, `B2C`) - determines retention policy

## Acceptance Criteria

**AC1: Successful Capture of Digital Approval for Work Order**
- **Given** a Work Order exists with ID `WO-123` and its status is `Pending Customer Approval`.
- **When** a valid POST request is made to `/api/v1/work-orders/WO-123/approvals` with all required signature data and approval payload.
- **Then** the system returns a `201 Created` response.
- **And** a new `Approval` record is created with entityType=`WorkOrder` and entityId=`WO-123`.
- **And** the status of Work Order `WO-123` is updated to `Approved`.
- **And** the `payloadHash` is computed and stored.
- **And** a `workexec.EntityApproved` event is emitted.

**AC2: Successful Capture of Digital Approval for Estimate**
- **Given** an Estimate exists with ID `EST-456` and its status is `Pending Customer Approval`.
- **When** a valid POST request is made to `/api/v1/estimates/EST-456/approvals` with all required data.
- **Then** the system returns a `201 Created` response.
- **And** a new `Approval` record is created with entityType=`Estimate` and entityId=`EST-456`.
- **And** the status of Estimate `EST-456` is updated to `Approved`.

**AC3: Attempt to Approve an Already Approved Entity**
- **Given** a Work Order exists with ID `WO-456` and its status is `Approved`.
- **When** a POST request is made to `/api/v1/work-orders/WO-456/approvals`.
- **Then** the system returns a `409 Conflict` error.
- **And** the state of Work Order `WO-456` remains `Approved`.

**AC4: Attempt to Approve an Entity in Invalid State**
- **Given** a Work Order exists with ID `WO-789` and its status is `In Progress`.
- **When** a POST request is made to `/api/v1/work-orders/WO-789/approvals`.
- **Then** the system returns a `409 Conflict` error.
- **And** the state of Work Order `WO-789` remains `In Progress`.

**AC5: Attempt to Approve with Incomplete Payload**
- **Given** a Work Order exists with ID `WO-123` in a `Pending Customer Approval` state.
- **When** a POST request is made to `/api/v1/work-orders/WO-123/approvals` but the `customerSignatureData` field is missing.
- **Then** the system returns a `400 Bad Request` error with a descriptive message.
- **And** the state of Work Order `WO-123` remains `Pending Customer Approval`.

**AC6: Versioning - New Estimate Version After Approval**
- **Given** an Estimate exists with ID `EST-100` in `Approved` state.
- **And** a Work Order `WO-200` is connected to `EST-100`.
- **When** a new version `EST-100-v2` of the estimate is created.
- **Then** Work Order `WO-200` transitions to `Transferred` status.
- **And** Work Order `WO-200` contains a reference to the new estimate `EST-100-v2`.

**AC7: Signature Integrity - Payload Hash Validation**
- **Given** a Work Order exists with ID `WO-999` in `Pending Customer Approval` state.
- **When** a POST request is made with signature data and approval payload.
- **Then** the system computes SHA-256 hash of the `approvalPayload`.
- **And** the hash is stored in the `Approval` record.
- **And** any tampering with the payload can be detected by hash mismatch.

## Audit & Observability
- **Logging:**
    - `INFO`: Log the successful creation of an approval record, including `approvalId`, `entityType`, `entityId`, and `payloadHash`.
    - `WARN`: Log failed approval attempts due to business rule violations (e.g., invalid state), including the reason for failure.
    - `ERROR`: Log any unexpected system errors during the approval process.
- **Metrics:**
    - Counter for `approvals.success` (tagged by entityType).
    - Counter for `approvals.failure` (tagged by reason, e.g., `invalid_state`, `not_found`, `integrity_error`).
- **Events:**
    - A domain event `workexec.EntityApproved` must be published to the message bus upon successful approval. The event payload should contain `entityType`, `entityId`, `approvalId`, `payloadHash`, and `timestamp`.
- **Retention:**
    - Approval records must be retained according to `retentionCategory` (B2B: invoice lifetime, B2C: full archive).

---
## Original Story (Unmodified – For Traceability)
# Issue #207 — [BACKEND] [STORY] Approval: Capture Digital Customer Approval

## Current Labels
- backend
- story-implementation
- type:story
- layer:functional
- kiro

## Current Body
## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #20 - Approval: Capture Digital Customer Approval
**URL**: https://github.com/louisburroughs/durion/issues/20
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
*Generated by Missing Issues Audit System - 2025-12-26T17:39:18.912027327*