Title: [BACKEND] [STORY] CoA: Create and Maintain Chart of Accounts
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/140
Labels: general, type:story, domain:accounting, status:needs-review

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
**As a** Finance Manager or Accountant,
**I want to** create and maintain a Chart of Accounts (CoA) with clearly defined, effective-dated General Ledger (GL) accounts,
**so that** financial transactions can be accurately classified, aggregated, and reported according to standard accounting principles and regulatory requirements.

## Actors & Stakeholders
- **Primary Actor:**
    - `Finance Manager`: User persona responsible for defining, managing, and maintaining the financial structure and integrity of the Chart of Accounts.
- **System Actors:**
    - `Accounting Service`: The microservice that owns and manages the Chart of Accounts data and business logic.
    - `General Ledger System`: A consumer of the CoA, which uses GL accounts for posting journal entries.
- **Stakeholders:**
    - `Auditor`: Requires a clear, auditable history of all changes to the CoA.
    - `Reporting System`: Consumes the CoA structure to generate financial reports (e.g., Balance Sheet, Income Statement).

## Preconditions
- The Finance Manager is authenticated and authorized with `CoA:Manage` permissions.
- The Accounting service is running and accessible via its API.
- The canonical list of GL account types (`ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`) is defined and configured in the system.

## Functional Behavior
### 4.1. Create GL Account
A Finance Manager can create a new GL account by providing a unique account code, a descriptive name, and a valid account type. The account can be created with an immediate or future activation date (`activeFrom`). Upon creation, the account is active by default and has no expiration (`activeThru` is null).

- **Trigger:** API `POST /v1/gl-accounts` request.
- **Outcome:** A new `GLAccount` entity is created and persisted. The full entity, including system-generated fields (`accountId`, `createdAt`), is returned in the API response with an HTTP `201` status. An audit log entry is created for the event.

### 4.2. Retrieve GL Account(s)
A user can retrieve the details of a specific GL account by its unique system ID or account code. They can also retrieve a paginated list of all GL accounts, with support for filtering by `accountType` and `status` (e.g., active, inactive, scheduled).

- **Trigger:** API `GET /v1/gl-accounts/{id}` or `GET /v1/gl-accounts?filter=...` request.
- **Outcome:** The requested `GLAccount` object(s) are returned.

### 4.3. Update GL Account Name/Description
A Finance Manager can update mutable attributes of an existing GL account, such as its `accountName` or `description`. Core identifying attributes (`accountCode`, `accountType`) are immutable after creation.

- **Trigger:** API `PATCH /v1/gl-accounts/{id}` request.
- **Outcome:** The specified fields are updated, `updatedAt` and `updatedBy` are set, and an audit log is generated.

### 4.4. Deactivate GL Account
A Finance Manager can deactivate a GL account by setting its `activeThru` timestamp. This action is subject to deactivation policy rules. Deactivation does not delete the account; it makes it unavailable for future transactions after the specified date.

- **Trigger:** API `POST /v1/gl-accounts/{id}/deactivate` request with an `effectiveDate`.
- **Outcome:** If business rules are met, the account's `activeThru` field is updated. If rules are violated, the request is rejected with a clear error message.

## Alternate / Error Flows
- **Duplicate Account Code:** If a user attempts to create a GL account with an `accountCode` that already exists, the system must reject the request with a `409 Conflict` error.
- **Invalid Account Type:** If the provided `accountType` is not in the canonical list, the system must reject the request with a `400 Bad Request` error.
- **Invalid Effective Dates:** If `activeThru` is provided and is earlier than or equal to `activeFrom`, the request must be rejected with a `400 Bad Request` error.
- **Deactivation Policy Violation:** If an attempt is made to deactivate a GL account that does not meet the policy criteria (e.g., has a non-zero balance), the system must reject the request with a `422 Unprocessable Entity` error and a descriptive message explaining the reason.
- **Unauthorized Access:** Any attempt to perform CoA management functions without the required permissions must be rejected with a `403 Forbidden` error.

## Business Rules
- **BR1: Account Code Uniqueness:** `accountCode` must be globally unique and is case-insensitive for validation purposes but case-sensitive for storage and display.
- **BR2: Canonical Account Types:** `accountType` must be one of: `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`. This list is non-extendable without a system change.
- **BR3: Immutability:** `accountCode` and `accountType` are immutable after an account is created. To change these, the existing account must be deactivated and a new one created.
- **BR4: Effective Dating:** An account is considered "active" if the current system time is on or after `activeFrom` and before `activeThru`. A null `activeThru` implies the account is active indefinitely.
- **BR5: Deactivation Prerequisites:** An account cannot be deactivated (have its `activeThru` date set) if it meets conditions defined in the deactivation policy. See **Open Questions**.

## Data Requirements
The `GLAccount` entity must contain the following attributes:

| Field Name      | Type              | Constraints                                         | Description                                    |
|-----------------|-------------------|-----------------------------------------------------|------------------------------------------------|
| `accountId`     | UUID              | Primary Key, Not Null                               | System-generated unique identifier.            |
| `accountCode`   | String            | Not Null, Unique, Indexed                           | The human-readable, unique code for the account. |
| `accountName`   | String            | Not Null                                            | The descriptive name of the account.           |
| `accountType`   | Enum              | Not Null (`ASSET`, `LIABILITY`, etc.)               | The financial classification of the account.   |
| `description`   | String            | Nullable                                            | A detailed description of the account's purpose. |
| `activeFrom`    | Timestamp with TZ | Not Null                                            | The date and time the account becomes active.    |
| `activeThru`    | Timestamp with TZ | Nullable                                            | The date and time the account becomes inactive.  |
| `createdAt`     | Timestamp with TZ | Not Null, System-managed                            | Timestamp of creation.                         |
| `updatedAt`     | Timestamp with TZ | Not Null, System-managed                            | Timestamp of last update.                      |
| `createdBy`     | UUID / String     | Not Null, System-managed                            | Identifier of the user who created the record. |
| `updatedBy`     | UUID / String     | Not Null, System-managed                            | Identifier of the user who last updated it.    |

## Acceptance Criteria

**AC1: Successful Creation of a New Asset Account**
- **Given** I am an authorized Finance Manager
- **When** I submit a request to create a new GL account with a unique `accountCode`, `accountName`, and `accountType` of `ASSET`
- **Then** the system returns a `201 Created` status
- **And** the response body contains the full details of the newly created account, including a system-generated `accountId`.

**AC2: Prevent Creation of Duplicate Account**
- **Given** a GL account with the code "1010-CASH" already exists
- **And** I am an authorized Finance Manager
- **When** I submit a request to create another GL account with the `accountCode` "1010-CASH"
- **Then** the system returns a `409 Conflict` error
- **And** the error message indicates that the account code is already in use.

**AC3: Successful Deactivation of an Unused Account**
- **Given** an active GL account exists that meets all deactivation policy criteria
- **And** I am an authorized Finance Manager
- **When** I submit a request to deactivate that account effective tomorrow
- **Then** the system returns a `200 OK` status
- **And** the account's `activeThru` is updated to tomorrow's date.

**AC4: Prevent Deactivation of an Account with a Non-Zero Balance**
- **Given** an active GL account exists with a non-zero balance
- **And** I am an authorized Finance Manager
- **When** I attempt to deactivate that account
- **Then** the system returns a `422 Unprocessable Entity` error
- **And** the error message clearly states that an account with a balance cannot be deactivated.

**AC5: Inactive Account Cannot Be Used for New Postings**
- **Given** a GL account has been deactivated with an `activeThru` date of yesterday
- **And** the General Ledger system attempts to post a new journal entry to this account
- **When** the transaction is processed
- **Then** the Accounting Service must reject the posting with an error indicating the account is inactive.

## Audit & Observability
- **Audit Trail:** Every creation, update, and deactivation of a `GLAccount` must generate an immutable audit log entry. The entry must capture the `before` and `after` state of the entity, the principal who performed the action, the source IP, and a precise timestamp.
- **Metrics:** The Accounting service must emit metrics for:
    - `gl_account.created.count` (tagged by `accountType`)
    - `gl_account.updated.count`
    - `gl_account.deactivated.count`
- **Logging:** Key decision points, such as a failed deactivation due to a policy violation, must be logged at an `INFO` or `WARN` level with structured context (e.g., `accountId`, `reason`).

## Open Questions
- **OQ1: Deactivation Policy Definition:** The story states "Deactivation rules are enforced per policy (e.g., balances/usage)". This policy needs to be precisely defined.
    - Is checking for a non-zero balance in the General Ledger the only condition?
    - Are there other checks required, such as recent transaction activity within the last 'X' days?
    - Are there dependencies, such as the account being part of a recurring transaction template or a financial report definition, that should block deactivation?

## Original Story (Unmodified – For Traceability)
# Issue #140 — [BACKEND] [STORY] CoA: Create and Maintain Chart of Accounts

## Current Labels
- backend
- story-implementation
- general

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] CoA: Create and Maintain Chart of Accounts

**Domain**: general

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Maintain Chart of Accounts and Posting Categories

## Story
CoA: Create and Maintain Chart of Accounts

## Acceptance Criteria
- [ ] Accounts support types: Asset/Liability/Equity/Revenue/Expense
- [ ] Accounts are effective-dated (activeFrom/activeThru) and audit-logged
- [ ] Duplicate account codes are blocked
- [ ] Deactivation rules are enforced per policy (e.g., balances/usage)


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