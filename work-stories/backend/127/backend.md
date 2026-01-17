Title: [BACKEND] [STORY] Close: Open/Close Accounting Periods with Locks
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/127
Labels: reporting, type:story, domain:accounting, status:needs-review

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** accounting-strict
---

## Story Intent
**As a** Finance or Accounting Manager,
**I want to** create and manage accounting periods (open/close) for each business unit,
**so that** I can enforce financial controls, ensure data integrity for reporting, and prevent transactions from being posted to closed financial periods.

## Actors & Stakeholders
- **Actors:**
  - `Accounting Manager`: A user with privileges to manage the lifecycle of accounting periods (create, close, reopen).
  - `System`: The POS backend and accounting services that enforce period lock rules.
  - `Auditor`: A user who reviews the audit trail for period management actions, especially reopening.
- **Stakeholders:**
  - `Finance Department`: Primary beneficiary, responsible for the accuracy of financial records.
  - `Business Unit Managers`: Rely on accurate reporting derived from controlled accounting periods.
  - `Development Team`: Implements the period locking and enforcement logic.

## Preconditions
1. A system for managing user roles and permissions is in place.
2. A robust audit logging service is available and can be called by the accounting service.
3. The concept of a `Business Unit` is defined and available as a data entity to associate with accounting periods.
4. Transaction posting services are designed to incorporate a validation step against the accounting period status.

## Functional Behavior

### 1. Create Accounting Period
An `Accounting Manager` can define a new accounting period for a specific `Business Unit`.
- **Trigger:** User initiates the "Create Period" action via an API endpoint.
- **Process:**
  - The user provides a `Business Unit ID`, `Start Date`, and `End Date`.
  - The system validates that the new period does not overlap with any existing periods for the same `Business Unit`.
  - Upon successful validation, the system creates the period with an initial status of `Open`.
- **Outcome:** A new accounting period record is created and is ready to accept transactions.

### 2. Close Accounting Period
An `Accounting Manager` can close an `Open` accounting period.
- **Trigger:** User initiates the "Close Period" action for an existing `Open` period.
- **Process:**
  - The system validates that the user has the necessary permissions.
  - The period's status is changed from `Open` to `Closed`.
  - The system records the user ID of the person who closed the period and the timestamp of the closure.
- **Outcome:** The period is now `Closed`. The system will reject any subsequent attempts to post transactions with a date falling within this period's date range.

### 3. Reopen Accounting Period
A specially-privileged `Accounting Manager` can reopen a `Closed` accounting period.
- **Trigger:** User with elevated permissions initiates the "Reopen Period" action.
- **Process:**
  - The user must provide a mandatory `Reason` for reopening the period.
  - The system validates the user has `ACCOUNTING_PERIOD_REOPEN` permission.
  - The period's status is changed from `Closed` to `Open`.
  - The system generates a high-severity audit log event capturing the `periodId`, `reopenedByUserId`, `timestamp`, and the `reason`.
- **Outcome:** The period is `Open` again, and new transactions can be posted to it. An indelible audit trail of this exceptional action is created.

### 4. Posting Enforcement
The `System` automatically enforces the period status during any financial transaction posting.
- **Trigger:** Any service attempts to post a financial transaction.
- **Process:**
  - The posting service retrieves the transaction's date and associated `Business Unit`.
  - It queries the accounting period service to determine the status of the period corresponding to the transaction date for that `Business Unit`.
  - If the period status is `Closed`, the posting request is rejected with a specific error.
- **Outcome:** Financial integrity is maintained by preventing posts to closed periods.

## Alternate / Error Flows
- **Posting to Closed Period:**
  - If a transaction posting is attempted to a date within a `Closed` period, the API MUST reject the request with a clear error code (e.g., `409 Conflict`) and a descriptive message (e.g., `ERR_ACCOUNTING_PERIOD_CLOSED`).
- **Unauthorized Period Management:**
  - If a user without the required permissions attempts to create, close, or reopen a period, the API MUST reject the request with a `403 Forbidden` error.
- **Creating Overlapping Periods:**
  - If a user attempts to create a new period with a date range that overlaps with an existing period for the same `Business Unit`, the API MUST reject the request with a `409 Conflict` error.
- **Reopening Without a Reason:**
  - If a user attempts to reopen a period without providing a reason, the API MUST reject the request with a `400 Bad Request` error.

## Business Rules
- A `Business Unit` can have multiple, non-overlapping accounting periods.
- A period is defined by a `startDate` and an `endDate`, inclusive.
- The lifecycle of a period is `Open` -> `Closed` -> `Open`.
- A reason is **mandatory** for transitioning a period from `Closed` to `Open`.
- Permission to create/close periods (`ACCOUNTING_PERIOD_MANAGE`) is distinct from the permission to reopen a closed period (`ACCOUNTING_PERIOD_REOPEN`). The latter should be more restricted.
- The period-locking logic must be deterministic and consistently enforced across all transaction-posting services.

## Data Requirements
- **`AccountingPeriod` Entity:**
  - `id`: Unique identifier (UUID)
  - `businessUnitId`: Foreign key to the Business Unit entity
  - `startDate`: Date
  - `endDate`: Date
  - `status`: Enum (`OPEN`, `CLOSED`)
  - `createdAt`: Timestamp
  - `updatedAt`: Timestamp
  - `closedByUserId`: Foreign key to User entity (nullable)
  - `closedAt`: Timestamp (nullable)

- **`PeriodReopenAuditEvent` (for Audit Log):**
  - `eventId`: Unique identifier (UUID)
  - `accountingPeriodId`: Foreign key to the Accounting Period
  - `reopenedByUserId`: Foreign key to the User who performed the action
  - `reopenedAt`: Timestamp
  - `reason`: Text (non-nullable)
  - `clientIpAddress`: String

## Acceptance Criteria
**Scenario 1: Successfully closing an accounting period**
- **Given** an `Accounting Manager` is authenticated
- **And** an accounting period exists for Business Unit "HQ" with status `Open`
- **When** the manager submits a request to close that period
- **Then** the system successfully updates the period's status to `Closed`
- **And** records the manager's ID and the current timestamp as the closing details.

**Scenario 2: Rejecting a transaction posted to a closed period**
- **Given** an accounting period for Business Unit "HQ" covering last month is `Closed`
- **When** any system or user attempts to post a transaction with a date from last month to Business Unit "HQ"
- **Then** the API rejects the request with a `409 Conflict` status
- **And** the response body contains the error code `ERR_ACCOUNTING_PERIOD_CLOSED`.

**Scenario 3: Successfully reopening a closed period with proper authorization**
- **Given** a user with `ACCOUNTING_PERIOD_REOPEN` permission is authenticated
- **And** an accounting period for Business Unit "HQ" is `Closed`
- **When** the user submits a request to reopen that period with the reason "Correcting mis-categorized invoice #123"
- **Then** the system updates the period's status to `Open`
- **And** the system generates a detailed audit event for the reopening action.

**Scenario 4: Rejecting an attempt to reopen a period without permission**
- **Given** a standard `Accounting Manager` (without `ACCOUNTING_PERIOD_REOPEN` permission) is authenticated
- **And** an accounting period is `Closed`
- **When** the manager attempts to reopen the period
- **Then** the API rejects the request with a `403 Forbidden` error.

## Audit & Observability
- **Audit:**
  - A high-severity, immutable audit log entry MUST be created whenever a period is reopened. This log must include `who`, `what`, `when`, and `why` (the reason).
  - Log standard-severity events for period creation and closure.
- **Observability:**
  - **Metrics:** Track the number of rejected transactions due to closed periods (`postings.rejected.period_closed`).
  - **Alerting:** Configure a high-priority alert to notify the security/finance team whenever a `PeriodReopenAuditEvent` is logged.

## Open Questions
1.  **Permissions:** What are the specific user roles that will be granted `ACCOUNTING_PERIOD_MANAGE` and the more sensitive `ACCOUNTING_PERIOD_REOPEN` permissions? Please confirm these permission names are suitable.
2.  **Business Unit Definition:** What entity in our system represents a `Business Unit`? Is it a Location, a legal entity, or another construct? The implementation depends on the foreign key relationship.
3.  **Error Propagation:** Is the proposed error response (`409 Conflict` with code `ERR_ACCOUNTING_PERIOD_CLOSED`) sufficient for all upstream clients (e.g., POS terminals, third-party integrations) to handle this failure gracefully?
4.  **Period Temporality:** Can accounting periods be created for future dates? What is the policy on back-dating the creation of periods?
5.  **Domain Confirmation:** Please confirm that `domain:accounting` is the correct primary domain for this capability. The original issue had a `reporting` label, but the core function (controlling financial postings) is an accounting responsibility.

## Original Story (Unmodified – For Traceability)
# Issue #127 — [BACKEND] [STORY] Close: Open/Close Accounting Periods with Locks

## Current Labels
- backend
- story-implementation
- reporting

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Close: Open/Close Accounting Periods with Locks

**Domain**: reporting

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Period Close, Adjustments, and Reporting

## Story
Close: Open/Close Accounting Periods with Locks

## Acceptance Criteria
- [ ] Periods can be created and closed per business unit
- [ ] Closed periods block posting unless reopened with permission
- [ ] Reopen requires reason and is audit-logged
- [ ] Posting logic enforces period policy deterministically


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