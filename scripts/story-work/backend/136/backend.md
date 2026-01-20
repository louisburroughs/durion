Title: [BACKEND] [STORY] GL: Post Journal Entry with Period Controls and Atomicity
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/136
Labels: general, type:story, domain:accounting, status:ready-for-dev

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
As the General Ledger System, I need to atomically post a valid journal entry while strictly enforcing accounting period controls, so that the company's financial records are updated accurately, consistently, and in the correct period.

## Actors & Stakeholders
- **System (Actor):** An automated system or service that originates a transaction requiring a journal entry (e.g., Sales, Invoicing, Payroll Sub-Ledger).
- **Accounting System (System):** The General Ledger (GL) system responsible for processing the posting request and maintaining the financial records.
- **Accountant / Controller (Stakeholder):** Responsible for the integrity and accuracy of the financial records, defining accounting period policies, and reviewing posting exceptions.

## Preconditions
- A well-formed and balanced journal entry (total debits equal total credits) exists and is ready for posting.
- The journal entry has a specified `posting_date` that determines its accounting period.
- The accounting periods (e.g., 'Open', 'Closed') are defined and their statuses are accessible to the system.
- All General Ledger accounts specified in the journal entry lines exist, are active, and are not locked.
- The source event or transaction that generated the journal entry is in a state that permits posting (e.g., 'Approved', 'Ready for Posting').

## Functional Behavior
1.  The Accounting System receives a request to post a `JournalEntry` from a source System.
2.  The system performs initial validation on the `JournalEntry` to ensure it is balanced and structurally valid.
3.  The system uses the `posting_date` to identify the corresponding `AccountingPeriod`.
4.  It verifies that the status of the identified `AccountingPeriod` is 'Open'.
5.  Upon successful validation, the system initiates a single, atomic database transaction.
6.  Within the transaction, the system performs the following operations:
    a. Creates immutable `LedgerEntry` records for each line item of the `JournalEntry`.
    b. Updates the balances of all affected GL accounts.
    c. Updates any relevant aggregate tables, such as a trial balance summary.
    d. Updates the status of the source event/document to 'Posted'.
    e. Records a permanent reference to the new `JournalEntryID` on the source event/document.
7.  The system commits the transaction.
8.  A success confirmation, including the new `JournalEntryID`, is returned to the calling system.

## Alternate / Error Flows
- **Unbalanced Journal Entry:** If the total debits do not equal total credits, the system rejects the request immediately with a `Validation:Unbalanced` error. No transaction is started.
- **Invalid GL Account:** If any line item references a non-existent, inactive, or locked GL account, the system rejects the request with a `Validation:InvalidAccount` error.
- **Closed Accounting Period:** If the `posting_date` falls within an `AccountingPeriod` that is 'Closed', the system rejects the request with a `BusinessRule:PeriodClosed` error. The rationale is logged for audit. (See Open Questions).
- **Transaction Failure:** If any operation within the database transaction fails (e.g., database connectivity loss, constraint violation), the entire transaction is rolled back. The ledger remains in its original state, and the source event status is unchanged. An error is logged and returned.

## Business Rules
- **Atomicity:** The posting of a journal entry is an all-or-nothing operation. All ledger lines must be created, and all balances updated, or the entire operation must be rolled back, leaving no trace in the GL.
- **Immutability of Posted Entries:** Once a journal entry is successfully posted, it cannot be modified or deleted. Any corrections must be made through a new, explicit reversing or correcting journal entry.
- **Strict Period Control:** Journal entries are forbidden from being posted to an `AccountingPeriod` with a 'Closed' status. The policy for handling such attempts must be strictly enforced.

## Data Requirements
- **`JournalEntry` (Input/Creation):**
    - `source_event_id`: UUID (Reference to the originating transaction)
    - `posting_date`: Date (Determines the accounting period)
    - `description`: String
    - `lines`: Array of `JournalEntryLine`
        - `account_id`: String (Identifier for a GL Account)
        - `debit`: Decimal (Must be >= 0)
        - `credit`: Decimal (Must be >= 0)
- **`LedgerEntry` (Output/Persistence):**
    - `ledger_entry_id`: UUID
    - `journal_entry_id`: UUID
    - `account_id`: String
    - `debit_amount`: Decimal
    - `credit_amount`: Decimal
    - `posted_timestamp`: DateTime
- **`AccountingPeriod` (Lookup):**
    - `period_id`: UUID
    - `start_date`: Date
    - `end_date`: Date
    - `status`: Enum ('Open', 'Closed')

## Acceptance Criteria
- **Scenario 1: Successful Posting to an Open Period**
    - **Given** a balanced journal entry with a `posting_date` that falls within an 'Open' accounting period
    - **When** the system receives a request to post the journal entry
    - **Then** a new journal entry is created in the GL, all corresponding ledger entries are recorded, the affected account balances are updated, the source event status is updated to 'Posted', and a success response is returned.

- **Scenario 2: Attempted Posting to a Closed Period**
    - **Given** a valid, balanced journal entry with a `posting_date` that falls within a 'Closed' accounting period
    - **When** the system receives a request to post the journal entry
    - **Then** the request is rejected with a `BusinessRule:PeriodClosed` error, no changes are made to the GL, and the source event status remains unchanged.

- **Scenario 3: Attempted Posting of an Unbalanced Entry**
    - **Given** a journal entry where total debits do not equal total credits
    - **When** the system receives a request to post the journal entry
    - **Then** the request is rejected with a `Validation:Unbalanced` error, and no financial records are altered.

- **Scenario 4: Transaction Fails Mid-Post**
    - **Given** a valid journal entry for an 'Open' accounting period
    - **When** the system attempts to post the entry but a database error occurs after some, but not all, lines are processed
    - **Then** the entire transaction is rolled back, the GL is restored to its pre-transaction state, and the source event status is not updated.

## Audit & Observability
- **Audit Trail:** Every posting attempt (successful or failed) must be logged with the `source_event_id`, initiating system/user, timestamp, and outcome.
- **Exception Reporting:** Failed postings due to closed periods must be recorded in an exception report or trigger a high-priority alert for the Accounting team to review.
- **Traceability:** A permanent, immutable link must exist between the `source_event_id` and the resulting `journal_entry_id` upon successful posting.

## Open Questions
- **OQ1: Closed Period Posting Policy:** The original story states "Closed periods block posting or redirect per policy". This policy is undefined and critical. What is the required behavior?
    - **A) Strict Block:** Always reject the posting. This is the safest accounting practice and the assumed default for this story.
    - **B) Post to Next Open Period:** Automatically change the posting date to the first day of the next available 'Open' period. This is risky as it can misrepresent financial activity timing.
    - **C) Allow with Override:** Block by default but allow posting if the request includes a specific override permission granted to a privileged user role. This would require an associated access control model.
    - **Decision Required:** Please confirm the policy. The current implementation will assume Strict Block (A).

## Original Story (Unmodified – For Traceability)
# Issue #136 — [BACKEND] [STORY] GL: Post Journal Entry with Period Controls and Atomicity

## Current Labels
- backend
- story-implementation
- general

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] GL: Post Journal Entry with Period Controls and Atomicity

**Domain**: general

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Post Journal Entries to the General Ledger

## Story
GL: Post Journal Entry with Period Controls and Atomicity

## Acceptance Criteria
- [ ] Posting is atomic (all lines committed or none)
- [ ] Closed periods block posting or redirect per policy (with recorded rationale)
- [ ] Posting updates ledger/trial-balance aggregates
- [ ] Source event status transitions to Posted with JE reference


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