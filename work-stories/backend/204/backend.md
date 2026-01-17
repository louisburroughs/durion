Title: [BACKEND] [STORY] Approval: Handle Approval Expiration
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/204
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
As a System Administrator, I want the system to automatically transition pending approvals to a cancelled state after a defined period, so that work orders do not remain indefinitely blocked waiting for a decision, ensuring process continuity and timely workflow progression. Additionally, I want to configure optional alerts for estimates approaching expiration to proactively notify stakeholders.

## Actors & Stakeholders
- **Primary Actor:** `System (Scheduled Job Processor)`: A non-human, automated process responsible for identifying and processing expired approvals.
- **Affected Actor:** `Service Advisor`: The user role that may have created the approval request, which is now considered expired.
- **Stakeholder:** `Shop Manager`: Interested in workflow efficiency and ensuring work is not stalled due to inaction on approvals.
- **Stakeholder:** `Alert Configuration Administrator`: Manages alert recipients and thresholds for expiration notifications.

## Preconditions
- An `Estimate` entity exists in the system.
- The `Estimate` entity has a `status` field in `AwaitingApproval` state.
- The `Estimate` entity has a non-null `approvalExpirationTimestamp` field, populated with a UTC timestamp.
- A scheduled job is configured to run at a regular interval (e.g., every 5 minutes for expiration processing, every 1 hour for alert generation).
- Optional: `ApprovalAlertConfiguration` records exist for generating proactive notifications.

## Functional Behavior

### Flow 1: Automatic Expiration of Pending Approvals
1. **Trigger:** The `ApprovalExpirationJob` is initiated by the system scheduler.
2. **Query:** The job queries the database for all `Estimate` records where `status` is `AwaitingApproval` and the `approvalExpirationTimestamp` is less than or equal to the current UTC time.
3. **Process:** For each `Estimate` record returned by the query:
   - a. The system initiates a transaction for the specific `Estimate`.
   - b. The status of the `Estimate` is updated to `CANCELLED` with a reason field set to `'expired'`.
   - c. An audit event is generated to record the state transition, capturing the reason (`APPROVAL_EXPIRED`), the old state (`AwaitingApproval`), and the new state (`CANCELLED`).
4. **Completion:** The job completes its run. If any records were processed, it logs a summary (e.g., "Processed 5 expired estimates"). If no records were found, it logs that no action was taken.

### Flow 2: No Work Order Until Approved
1. **Prerequisite:** An `Estimate` exists in `AwaitingApproval` status.
2. **Expiration Occurs:** The `ApprovalExpirationJob` runs and transitions the `Estimate` to `CANCELLED` with reason `'expired'`.
3. **Work Order State:** No `WorkOrder` is created from this `Estimate`.
4. **Authority:** The `Estimate` is the system of record for approval status. A `WorkOrder` is never created until the `Estimate` is explicitly approved and its status transitions to `APPROVED`.

### Flow 3: Configurable Expiration Alerts
1. **Alert Generation Trigger:** An `ApprovalAlertJob` runs periodically (e.g., every 1 hour).
2. **Query:** The job queries for `Estimate` records where:
   - `status` is `AwaitingApproval`
   - `approvalExpirationTimestamp - now()` is less than or equal to a configurable threshold (e.g., 24 hours remaining)
3. **Configuration Lookup:** For each `Estimate`, the system:
   - a. Checks for a customer-level `ApprovalAlertConfiguration` (takes precedence).
   - b. Falls back to a store-level `ApprovalAlertConfiguration` if no customer override exists.
4. **Alert Generation:** If an `ApprovalAlertConfiguration` is found and alerts are enabled:
   - a. An internal alert event is published (e.g., `APPROVAL_EXPIRING_SOON`) with details:
      - `estimateId`, `expirationTimestamp`, `recipientList`, `timeRemainingHours`
   - b. The alert is NOT directly sent via email/SMS by this system.
5. **Notification System Integration:** A separate external notification system subscribes to the alert event and handles:
   - Sending notifications to configured recipients (email, SMS, in-app, etc.).
   - Honoring recipient preferences and do-not-disturb settings.
   - Retry and failure handling.

## Alternate / Error Flows

- **No Expired Approvals:** If the job runs and finds no `Estimate` records meeting the expiration criteria, it finishes successfully without making any state changes. This is the expected "happy path" for most runs.
- **No Estimates Approaching Expiration (Alert Job):** The alert job runs but finds no `Estimate` records within the alert threshold. The job completes without publishing any events.
- **Database Transaction Failure (Expiration):** If the system fails to update an `Estimate`'s status or create an audit log within the transaction, the entire transaction for that record must be rolled back. The `Estimate` will remain in the `AwaitingApproval` state, and an error will be logged with high severity. The job will continue to process any other identified records.
- **No Alert Configuration Found (Alert Job):** If an `Estimate` is approaching expiration but no store or customer-level `ApprovalAlertConfiguration` exists, no alert is published and the estimate is silently monitored.
- **External Notification System Unavailable:** If the notification system is not listening to alert events, alerts are published but not delivered. This is acceptable as the system operates independently; the external system is responsible for availability.

## Business Rules

- An approval is considered expired if `now() >= approvalExpirationTimestamp`. All time comparisons must be performed in UTC.
- A cancelled approval (due to expiration) is a terminal state. Once an `Estimate` is in `CANCELLED` (reason: `'expired'`) state, it cannot be subsequently approved or declined.
- The expiration check process must only consider `Estimate` records in the `AwaitingApproval` state. It must not affect `Estimate`s in `APPROVED`, `DECLINED`, `CANCELLED`, or other terminal states.
- **Work Order Creation Authority:** A `WorkOrder` is only created when an `Estimate` transitions to `APPROVED` status. Expiration of an `Estimate` in `AwaitingApproval` prevents `WorkOrder` creation.
- **Alert Configuration Precedence:** Customer-level `ApprovalAlertConfiguration` takes precedence over store-level defaults. If a customer configuration disables alerts, no alert event is published, even if the store has alerts enabled.
- **Alert Threshold:** The alert threshold is configurable per store and per customer. Different stakeholders can have different alert advance-notice periods (e.g., 24 hours, 48 hours, 7 days).

## Data Requirements

- The `Estimate` entity must contain, at a minimum:
  - `estimateId` (UUID, Primary Key)
  - `customerId` (UUID, Foreign Key to Customer)
  - `locationId` (UUID, Foreign Key to Location)
  - `status` (Enum: `DRAFT`, `AwaitingApproval`, `APPROVED`, `DECLINED`, `CANCELLED`)
  - `statusReason` (String, optional): For `CANCELLED` status, the reason (e.g., `'expired'`, `'customer-requested'`)
  - `approvalExpirationTimestamp` (DATETIME, UTC, nullable)
  - `createdAt` (DATETIME, UTC)
  - `updatedAt` (DATETIME, UTC)

- The `AuditLog` entity must support recording approval expiration events, capturing:
  - `entityType`: `'Estimate'`
  - `entityId`: `estimateId`
  - `eventType`: `'STATE_TRANSITION'`
  - `details`: `{ fromStatus: 'AwaitingApproval', toStatus: 'CANCELLED', reason: 'APPROVAL_EXPIRED', expirationTimestamp: <UTC timestamp> }`
  - `createdBy`: `'SYSTEM_APPROVAL_JOB'`
  - `createdAt`: (DATETIME, UTC)

- **New Entity: `ApprovalAlertConfiguration`**
  - `configId` (UUID, Primary Key)
  - `entityType` (Enum: `'STORE'`, `'CUSTOMER'`)
  - `entityId` (UUID: storeId or customerId depending on entityType)
  - `alertsEnabled` (Boolean, default: true)
  - `expirationThresholdHours` (Integer, default: 24): How many hours before expiration to trigger an alert
  - `recipientRoles` (List of Strings): Roles to notify (e.g., `['SERVICE_ADVISOR', 'SHOP_MANAGER', 'CUSTOMER']`)
  - `createdAt` (DATETIME, UTC)
  - `updatedAt` (DATETIME, UTC)

- **Alert Event Structure** (published to internal event bus):
  - `eventId` (UUID)
  - `eventType`: `'APPROVAL_EXPIRING_SOON'`
  - `estimateId` (UUID)
  - `customerId` (UUID)
  - `locationId` (UUID)
  - `expirationTimestamp` (DATETIME, UTC)
  - `timeRemainingHours` (Integer)
  - `recipientRoles` (List of Strings, from configuration)
  - `publishedAt` (DATETIME, UTC)

## Acceptance Criteria

### AC1: Pending estimate transitions to cancelled (expired) state after expiration time
**Given** an `Estimate` record exists with `status: 'AwaitingApproval'`  
**And** its `approvalExpirationTimestamp` is set to a time in the past  
**When** the `ApprovalExpirationJob` is executed  
**Then** the `Estimate` record's `status` is updated to `'CANCELLED'`  
**And** its `statusReason` is set to `'expired'`  
**And** an audit event is created documenting the change from `AwaitingApproval` to `CANCELLED`  

### AC2: Pending estimate is not affected before expiration time
**Given** an `Estimate` record exists with `status: 'AwaitingApproval'`  
**And** its `approvalExpirationTimestamp` is set to a time in the future  
**When** the `ApprovalExpirationJob` is executed  
**Then** the `Estimate` record's `status` remains `'AwaitingApproval'`  
**And** no audit event for expiration is created for this record  

### AC3: Non-pending estimates are ignored by the expiration process
**Given** an `Estimate` record exists with a status of `'APPROVED'`  
**And** its `approvalExpirationTimestamp` is in the past  
**When** the `ApprovalExpirationJob` is executed  
**Then** the `Estimate` record's `status` remains `'APPROVED'`  

### AC4: Job handles an empty set of pending estimates gracefully
**Given** there are no `Estimate` records with `status: 'AwaitingApproval'`  
**When** the `ApprovalExpirationJob` is executed  
**Then** the job completes successfully  
**And** no database writes or errors occur  

### AC5: No work order is created when estimate expires without approval
**Given** an `Estimate` exists in `AwaitingApproval` status  
**And** no `WorkOrder` has been created from this estimate  
**When** the `ApprovalExpirationJob` runs and transitions the estimate to `CANCELLED` (reason: `'expired'`)  
**Then** no `WorkOrder` is ever created from this estimate  
**And** the `Estimate` is now in a terminal state with clear audit trail  

### AC6: Alert generated for estimate approaching expiration (customer config)
**Given** an `Estimate` exists with `status: 'AwaitingApproval'`  
**And** a customer-level `ApprovalAlertConfiguration` exists with `alertsEnabled: true` and `expirationThresholdHours: 24`  
**And** the `approvalExpirationTimestamp` is 20 hours in the future  
**When** the `ApprovalAlertJob` runs  
**Then** an `APPROVAL_EXPIRING_SOON` event is published  
**And** the event includes `timeRemainingHours: 20` and recipient roles from the configuration  

### AC7: Store-level alert configuration used when no customer override exists
**Given** an `Estimate` exists for a customer with no customer-level `ApprovalAlertConfiguration`  
**And** a store-level `ApprovalAlertConfiguration` exists with `alertsEnabled: true` and `expirationThresholdHours: 48`  
**And** the `approvalExpirationTimestamp` is 30 hours in the future  
**When** the `ApprovalAlertJob` runs  
**Then** an `APPROVAL_EXPIRING_SOON` event is published (using store threshold)  

### AC8: No alert generated when alerts are disabled
**Given** an `Estimate` exists  
**And** an `ApprovalAlertConfiguration` (customer or store) exists with `alertsEnabled: false`  
**And** the estimate is approaching its expiration threshold  
**When** the `ApprovalAlertJob` runs  
**Then** no `APPROVAL_EXPIRING_SOON` event is published  
**And** the system continues monitoring the estimate silently  

## Audit & Observability

- **Audit Event (Expiration):** A structured audit event MUST be generated for every `Estimate` that is automatically expired. The event must include the `estimateId`, `customerId`, `locationId`, the previous status (`AwaitingApproval`), the new status (`CANCELLED`), the reason (`'expired'`), and the `expirationTimestamp`.
- **Audit Event (Alert Published):** When an alert event is published, it should be logged with: `eventId`, `estimateId`, `expirationTimestamp`, `timeRemainingHours`, `recipientRoles`, and `publishedAt`.
- **Logging:**
  - The `ApprovalExpirationJob` should log its start and end time.
  - It should log a summary count of how many estimates were processed (e.g., `Expired 7 estimates`).
  - Any errors encountered during processing must be logged with a high severity level, including the `estimateId` that failed.
  - The `ApprovalAlertJob` should log the number of alerts published (e.g., `Published 12 expiration alerts`).
- **Metrics:** 
  - A metric should be emitted for the number of estimates expired per job run: `approval.expirations.count`.
  - A metric should be emitted for the number of alerts published: `approval.alerts.published`.
  - A metric should be emitted for alert configuration cache hits/misses: `approval.alert.config.cache.*`.

## Original Story (Unmodified – For Traceability)

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
As a System Administrator, I want the system to automatically transition pending approvals to an expired state after a defined period, so that work orders do not remain indefinitely blocked waiting for a decision, ensuring process continuity and timely workflow progression.

## Actors & Stakeholders
- **Primary Actor:** `System (Scheduled Job Processor)`: A non-human, automated process responsible for identifying and processing expired approvals.
- **Affected Actor:** `Service Advisor`: The user role that may have created the work order or the approval request, which is now considered expired.
- **Stakeholder:** `Shop Manager`: Interested in workflow efficiency and ensuring work is not stalled due to inaction on approvals.

## Preconditions
- A `WorkApproval` entity exists in the system.
- The `WorkApproval` entity is in a `PENDING` state.
- The `WorkApproval` entity has a non-null `expirationTimestamp` field, populated with a UTC timestamp.
- A scheduled job is configured to run at a regular interval (e.g., every 5 minutes).

## Functional Behavior
1. **Trigger:** The `ApprovalExpirationJob` is initiated by the system scheduler.
2. **Query:** The job queries the database for all `WorkApproval` records where `status` is `PENDING` and the `expirationTimestamp` is less than or equal to the current UTC time.
3. **Process:** For each `WorkApproval` record returned by the query:
   - a. The system initiates a transaction for the specific `WorkApproval`.
   - b. The status of the `WorkApproval` is updated to the designated terminal state for expired items (e.g., `EXPIRED`).
   - c. An audit event is generated to record the state transition, capturing the reason (`EXPIRED_AUTOMATICALLY`), the old state (`PENDING`), and the new state.
4. **Completion:** The job completes its run. If any records were processed, it logs a summary (e.g., "Processed 5 expired approvals"). If no records were found, it logs that no action was taken.

## Alternate / Error Flows
- **No Expired Approvals:** If the job runs and finds no `WorkApproval` records meeting the expiration criteria, it finishes successfully without making any state changes. This is the expected "happy path" for most runs.
- **Database Transaction Failure:** If the system fails to update a `WorkApproval`'s status or create an audit log within the transaction, the entire transaction for that record must be rolled back. The `WorkApproval` will remain in the `PENDING` state, and an error will be logged. The job will continue to process any other identified records.

## Business Rules
- An approval is considered expired if `now() >= expirationTimestamp`. All time comparisons must be performed in UTC.
- An expired approval is a terminal state. Once an approval is in an `EXPIRED` state, it cannot be subsequently approved or declined.
- The expiration check process must only consider approvals in the `PENDING` state. It must not affect approvals in `APPROVED`, `DECLINED`, or other terminal states.

## Data Requirements
- The `WorkApproval` entity must contain, at a minimum:
   - `approvalId` (UUID, Primary Key)
   - `workOrderId` (UUID, Foreign Key)
   - `status` (Enum: `PENDING`, `APPROVED`, `DECLINED`, `EXPIRED`, `CANCELLED`)
   - `expirationTimestamp` (DATETIME, UTC)
   - `createdAt` (DATETIME, UTC)
   - `updatedAt` (DATETIME, UTC)

- The `AuditLog` entity must support recording this event, capturing:
   - `entityType`: 'WorkApproval'
   - `entityId`: `approvalId`
   - `eventType`: 'STATE_TRANSITION'
   - `details`: { `fromStatus`: 'PENDING', `toStatus`: 'EXPIRED', `reason`: 'AUTOMATED_EXPIRATION' }

## Acceptance Criteria

### AC1: Pending approval transitions to expired state after expiration time
**Given** a `WorkApproval` record exists with `status: 'PENDING'`  
**And** its `expirationTimestamp` is set to a time in the past  
**When** the `ApprovalExpirationJob` is executed  
**Then** the `WorkApproval` record's `status` is updated to `'EXPIRED'`  
**And** an audit event is created documenting the change from `PENDING` to `EXPIRED`.

### AC2: Pending approval is not affected before expiration time
**Given** a `WorkApproval` record exists with `status: 'PENDING'`  
**And** its `expirationTimestamp` is set to a time in the future  
**When** the `ApprovalExpirationJob` is executed  
**Then** the `WorkApproval` record's `status` remains `'PENDING'`  
**And** no audit event for expiration is created for this record.

### AC3: Non-pending approvals are ignored by the expiration process
**Given** a `WorkApproval` record exists with a status of `'APPROVED'`  
**And** its `expirationTimestamp` is in the past  
**When** the `ApprovalExpirationJob` is executed  
**Then** the `WorkApproval` record's `status` remains `'APPROVED'`.

### AC4: Job handles an empty set of pending approvals gracefully
**Given** there are no `WorkApproval` records with `status: 'PENDING'`  
**When** the `ApprovalExpirationJob` is executed  
**Then** the job completes successfully  
**And** no database writes or errors occur.

## Audit & Observability
- **Audit Event:** A structured audit event MUST be generated for every approval that is automatically expired. The event must include the `approvalId`, `workOrderId`, the previous status (`PENDING`), the new status (`EXPIRED`), and the reason for the change.
- **Logging:**
   - The `ApprovalExpirationJob` should log its start and end time.
   - It should log a summary count of how many approvals were processed (e.g., `Expired 7 approvals`).
   - Any errors encountered during processing must be logged with a high severity level, including the `approvalId` that failed.
- **Metrics:** A metric should be emitted for the number of approvals expired per job run.

## Open Questions
1. **Target State:** What is the authoritative, final status for an expired approval? The story assumes `EXPIRED`, but it could also be `DECLINED` or `CANCELLED`. This needs to be confirmed.
2. **Work Order Impact:** Does the expiration of a `WorkApproval` trigger a state change on the parent `WorkOrder`? For example, should the `WorkOrder` revert to a `NEEDS_REVIEW` state?
3. **Notifications:** Should any notifications be sent when an approval expires? If so, who are the recipients (e.g., the original requestor, the intended approver), and what is the required message content/format?
