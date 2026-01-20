Title: [BACKEND] [STORY] Adjustments: Create Manual Journal Entry with Controls
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/126
Labels: type:story, domain:accounting, status:ready-for-dev

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** accounting-strict
---

## Story Intent
**As an** authorized financial user (e.g., Accountant, Controller),
**I need to** create a controlled, manual journal entry with supporting details,
**So that** I can make necessary financial adjustments to the General Ledger, ensuring accuracy and full auditability.

## Actors & Stakeholders
- **Accountant / Controller (Primary Actor)**: A user with specific permissions to create and manage financial adjustments.
- **System**: The POS backend system responsible for enforcing accounting rules and recording transactions.
- **Auditor (Stakeholder)**: A user who reviews financial records for compliance and accuracy. They are a consumer of the immutable, auditable output.

## Preconditions
1. The user is authenticated and possesses the `ACCOUNTING_ADJUSTMENT_CREATE` permission.
2. The target financial period for the journal entry's `postingDate` is in an `Open` state.
3. A pre-configured list of General Ledger (GL) accounts is available in the system.
4. A pre-configured list of `Reason Codes` for manual adjustments is available in the system.

## Functional Behavior
1. The Accountant initiates the creation of a new manual Journal Entry (JE).
2. The user provides the required header-level information:
    - `postingDate`: The effective date of the entry for financial reporting.
    - `description`: A clear, human-readable summary of the entry's purpose.
    - `reasonCode`: A selection from the pre-configured list of reasons for the adjustment.
3. The user adds two or more Journal Entry Lines, each containing:
    - A valid `glAccountId`.
    - A `debitAmount` or `creditAmount` (but not both).
    - An optional `memo` for line-item specific details.
4. The user submits the completed JE for posting.
5. The System performs validation checks against all defined business rules (see below).
6. Upon successful validation, the System:
    - Persists the Journal Entry and its lines to the General Ledger.
    - Assigns the JE a permanent, unique identifier.
    - Sets the JE status to `POSTED`, rendering it immutable.
    - Generates an audit log entry for the creation event.

## Alternate / Error Flows
- **Error - Unbalanced Entry**: If the sum of `debitAmount`s does not equal the sum of `creditAmount`s, the System rejects the submission with a `VALIDATION_ERROR:UNBALANCED_ENTRY` and does not create the JE.
- **Error - Posting to Closed Period**: If the `postingDate` falls within a `Closed` or `Archived` financial period, the System rejects the submission with a `VALIDATION_ERROR:PERIOD_CLOSED`.
- **Error - Invalid GL Account**: If any `glAccountId` provided is not a valid, active account, the System rejects the submission with `VALIDATION_ERROR:INVALID_ACCOUNT`.
- **Error - Missing Required Fields**: If `postingDate`, `description`, or `reasonCode` are missing, the System rejects the submission with a `VALIDATION_ERROR:MISSING_REQUIRED_FIELD`.
- **Error - Insufficient Permissions**: If the user lacks the `ACCOUNTING_ADJUSTMENT_CREATE` permission, the System denies the initial action.

## Business Rules
- **BR1: Immutability of Posted Entries**: A `POSTED` Journal Entry cannot be edited or deleted. Corrections must be performed by creating a new, separate reversing Journal Entry.
- **BR2: Balance Requirement**: For any given Journal Entry, the total value of all debit lines must exactly equal the total value of all credit lines. A JE with a single line is invalid.
- **BR3: Period Control**: A Journal Entry can only be posted to a financial period that is currently in an `Open` state.
- **BR4: Reason Code Mandate**: Every manual Journal Entry must be associated with a valid, non-null `reasonCode` from the master list.

## Data Requirements
This section defines the logical data model required to support this story.

### Journal Entry Header (`JournalEntry`)
| Field | Type | Constraints | Description |
|---|---|---|---|
| `journalEntryId` | UUID | Primary Key, System-Generated | Unique identifier for the entry. |
| `postingDate` | Date | Required | The date the entry affects the General Ledger. |
| `transactionDate` | DateTime | Required, Default: `NOW()` | The date and time the entry was created. |
| `description` | String | Required, Not Null | A summary of the entry's purpose. |
| `reasonCode` | Enum/String | Required, FK | A code explaining the reason for the manual adjustment. |
| `status` | Enum | Required | The state of the entry (e.g., `POSTED`, `REVERSED`). |
| `createdByUserId`| UUID | Required, FK | The ID of the user who created the entry. |

### Journal Entry Line (`JournalEntryLine`)
| Field | Type | Constraints | Description |
|---|---|---|---|
| `journalEntryLineId`| UUID | Primary Key, System-Generated | Unique identifier for the line item. |
| `journalEntryId` | UUID | Required, FK to `JournalEntry` | The parent Journal Entry. |
| `glAccountId` | UUID | Required, FK to `GLAccount` | The affected General Ledger account. |
| `debitAmount` | Money | Not Null, Min: 0 | The debit value. Must be 0 if `creditAmount` > 0. |
| `creditAmount` | Money | Not Null, Min: 0 | The credit value. Must be 0 if `debitAmount` > 0. |
| `memo` | String | Optional | Line-item specific description. |

## Acceptance Criteria
**Scenario 1: Successful Creation of a Balanced Journal Entry**
- **Given** an Accountant is logged in with `ACCOUNTING_ADJUSTMENT_CREATE` permissions
- **And** the financial period for "today" is `Open`
- **When** the Accountant creates a new manual Journal Entry with:
    - A valid `postingDate`, `description`, and `reasonCode`
    - A line for account `1010` with a debit of `$100.00`
    - A line for account `2020` with a credit of `$100.00`
- **Then** the system successfully validates and posts the entry
- **And** a new Journal Entry with status `POSTED` exists in the General Ledger.

**Scenario 2: Rejection of an Unbalanced Journal Entry**
- **Given** an Accountant is logged in with `ACCOUNTING_ADJUSTMENT_CREATE` permissions
- **And** the financial period for "today" is `Open`
- **When** the Accountant attempts to create a Journal Entry with a total debit of `$100.00` and a total credit of `$99.00`
- **Then** the system rejects the submission with a `VALIDATION_ERROR:UNBALANCED_ENTRY`
- **And** no new Journal Entry is created in the General Ledger.

**Scenario 3: Rejection of Posting to a Closed Financial Period**
- **Given** an Accountant is logged in with `ACCOUNTING_ADJUSTMENT_CREATE` permissions
- **And** the financial period for last month has a status of `Closed`
- **When** the Accountant attempts to create a balanced Journal Entry with a `postingDate` from last month
- **Then** the system rejects the submission with a `VALIDATION_ERROR:PERIOD_CLOSED`
- **And** no new Journal Entry is created in the General Ledger.

**Scenario 4: Posted Journal Entries are Immutable**
- **Given** a Journal Entry with ID `JE-123` has a status of `POSTED`
- **When** any user, regardless of permissions, attempts to update or delete `JE-123`
- **Then** the system denies the request with an `IMMUTABLE_RECORD` error.

## Audit & Observability
- **Audit Trail**: The creation of every manual Journal Entry MUST be recorded in an immutable audit log. The log entry must include `journalEntryId`, `createdByUserId`, `timestamp`, and the full payload of the created entry.
- **Logging**:
    - `INFO`: Log successful JE creation with `journalEntryId`.
    - `WARN`: Log all validation failures with details of the failure (e.g., `UNBALANCED_ENTRY`, `PERIOD_CLOSED`) and the user who made the attempt.
- **Metrics**:
    - `manual_je_creations.success.count`: A counter for successfully posted manual JEs.
    - `manual_je_creations.failure.count`: A counter for failed submission attempts, tagged by reason (e.g., `reason:unbalanced`, `reason:period_closed`).

## Original Story (Unmodified – For Traceability)
# Issue #126 — [BACKEND] [STORY] Adjustments: Create Manual Journal Entry with Controls

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Adjustments: Create Manual Journal Entry with Controls

**Domain**: user

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
Adjustments: Create Manual Journal Entry with Controls

## Acceptance Criteria
- [ ] Authorized users can create manual JEs with reason code
- [ ] System blocks unbalanced manual JEs
- [ ] Posted manual JEs are immutable (corrections via reversal)
- [ ] Posting respects period controls and audit requirements


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