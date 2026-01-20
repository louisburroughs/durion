Title: [BACKEND] [STORY] AP: Approve and Schedule Payments with Controls
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/129
Labels: type:story, domain:accounting, status:ready-for-dev, agent:story-authoring, agent:accounting

## Story Intent
**As an** Accounts Payable (AP) Manager or Clerk,
**I want to** approve bills and schedule them for payment through a controlled workflow,
**so that** financial obligations are met accurately and securely, with a clear audit trail.

## Actors & Stakeholders
- **Actors:**
  - `AP Clerk`: Enters and prepares bills for approval.
  - `AP Manager`: Approves bills and/or schedules payments, often with higher monetary thresholds.
  - `System`: Enforces state transitions, thresholds, and audit logging.
- **Stakeholders:**
  - `Finance Department`: Depends on accurate AP ledger and schedules for cash flow.
  - `Auditors`: Require an immutable record of approvals and scheduling.
  - `Treasury/Payments`: Executes scheduled payments.

## Preconditions
- A `Bill` exists in `DRAFT` state in `domain:accounting`.
- The caller is authenticated and evaluated by the shared authorization layer (`domain:security` or existing auth module).
- Approval thresholds / approval policy is configured in `domain:accounting` (versioned/audited configuration).

## Functional Behavior
### 1) Approve Bill (Accounting SoR)
1. **Trigger:** A user initiates an approval action on a `DRAFT` bill.
2. **Process (accounting):**
   - Verify authorization for approval (permission check via shared auth).
   - Validate the bill amount against the approver’s threshold (accounting-owned approval policy).
   - Transition bill status `DRAFT → APPROVED`.
   - Record an immutable audit entry for the approval.
3. **Outcome:** The bill becomes eligible for payment scheduling.

### 2) Schedule Payment (Accounting SoR)
1. **Trigger:** A user initiates scheduling on an `APPROVED` bill.
2. **Process (accounting):**
   - Verify authorization for scheduling (permission check via shared auth).
   - Validate state prerequisite: bill must be `APPROVED`.
   - Capture scheduling inputs (e.g., `scheduledFor`, `paymentMethod`).
   - Transition bill status `APPROVED → SCHEDULED`.
   - Record an immutable audit entry for the scheduling.
   - Emit a versioned event for payment execution consumption by `domain:payment`.
3. **Outcome:** The bill is scheduled for payment execution.

### 3) Execute Payment (Payments domain)
- `domain:payment` consumes the scheduling event to execute the payment.
- `domain:payment` MUST treat the scheduling event as idempotent (no duplicate disbursements on redelivery).
- `domain:payment` emits execution outcome events back (e.g., `Payment.Executed.v1`, `Payment.Failed.v1`).
- `domain:accounting` consumes outcome events to reflect execution outcomes in the accounting-owned bill lifecycle (e.g., later states such as `PAID` / `FAILED` if/when modeled).

## Alternate / Error Flows
- **Approval without permission:** Reject with authorization error; bill remains `DRAFT`.
- **Approval exceeds threshold:** Reject with business rule violation; bill remains `DRAFT`.
- **Schedule without permission:** Reject with authorization error; bill remains `APPROVED`.
- **Schedule on non-approved bill:** Reject with business rule violation; bill state unchanged.

## Business Rules
- **BR-1 (System of Record):** `domain:accounting` is the SoR for Bill lifecycle and state transitions (`DRAFT → APPROVED → SCHEDULED`, and subsequent states if added).
- **BR-2 (No direct mutation by payments):** `domain:payment` MUST NOT mutate Bill state directly; it reacts to accounting events and reports outcomes via events.
- **BR-3 (State prerequisite):** A bill MUST be `APPROVED` to be scheduled.
- **BR-4 (Authorization):** Approve/schedule actions require explicit authorization (evaluated by shared auth).
- **BR-5 (Threshold policy):** Approval thresholds / approval policy is owned by `domain:accounting` and applied during approval.
- **BR-6 (Idempotency):** `domain:payment` execution MUST be idempotent for a given scheduled payment; use `paymentId` as the idempotency key.

## Data Requirements
### Bill (Accounting)
- `billId` (UUID)
- `billNumber` (string)
- `vendorId` (UUID)
- `status` (Enum: `DRAFT`, `APPROVED`, `SCHEDULED`, …)
- `amount` (money: currency + value)
- `approvedByUserId` (UUID, nullable)
- `approvedAt` (timestamp, nullable)

### Scheduling (Accounting)
- `paymentId` (UUID) (identifier used across domains)
- `scheduledFor` (date)
- `paymentMethod` (type + instrumentId)
- `scheduledByUserId` (UUID)
- `scheduledAt` (timestamp)

### Approval Policy (Accounting)
- Versioned policy stored in accounting (e.g., `policyId`, threshold definition, minimum approver role)
- Snapshot key details into scheduling events for immutable downstream context.

### Inter-domain Event Contract (Resolved)
- Scheduling event emitted by accounting: `Accounting.PaymentScheduled.v1`
- Outcome events emitted by payments: `Payment.Executed.v1` / `Payment.Failed.v1`

Minimum scheduling event fields include:
- `billId`, `billNumber`, `vendorId`
- `paymentId` (idempotency key)
- `scheduledFor`
- `amount` (currency + value)
- `paymentMethod` (type + instrumentId)
- `requestedByUserId`, `approvedByUserId`
- `approvalPolicySnapshot` (at least `policyId` + threshold)

## Acceptance Criteria
- **AC-1: Approve bill transitions state**
  - Given a bill in `DRAFT` and an authorized approver within threshold
  - When the approver approves the bill
  - Then the bill transitions to `APPROVED` and an approval audit entry is recorded

- **AC-2: Threshold enforcement**
  - Given a bill in `DRAFT` exceeding the caller’s approval threshold
  - When the caller attempts to approve
  - Then the request is rejected and the bill remains `DRAFT`

- **AC-3: Schedule requires approved state**
  - Given a bill in `DRAFT`
  - When a user attempts to schedule payment
  - Then the request is rejected and the bill remains `DRAFT`

- **AC-4: Schedule emits payment execution contract**
  - Given a bill in `APPROVED` and an authorized scheduler
  - When the bill is scheduled
  - Then the bill transitions to `SCHEDULED`, a scheduling audit entry is recorded, and `Accounting.PaymentScheduled.v1` is emitted containing `paymentId`

## Audit & Observability
- **Audit Log:** Every successful state transition MUST create an immutable audit entry including: `billId`, `oldStatus`, `newStatus`, `principalUserId`, and timestamp.
- **Events:** Emit `Accounting.PaymentScheduled.v1` on scheduling; consume payment outcome events to reflect execution outcomes.
- **Metrics:** Track counts of bills by state (`DRAFT`, `APPROVED`, `SCHEDULED`, …) and payment execution outcomes (`executed`, `failed`).

## Open Questions
None. (Resolved in decision comment generated by `clarification-resolver.sh` on 2026-01-14.)

## Original Story (Unmodified – For Traceability)
# Issue #129 — [BACKEND] [STORY] AP: Approve and Schedule Payments with Controls

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] AP: Approve and Schedule Payments with Controls

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Accounts Payable (Bill → Payment)

## Story
AP: Approve and Schedule Payments with Controls

## Acceptance Criteria
- [ ] Bill workflow supports Draft → Approved → Scheduled
- [ ] Approval thresholds and role permissions enforced
- [ ] Payment scheduling records date/method and audit trail
- [ ] Payment execution blocked unless approved


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