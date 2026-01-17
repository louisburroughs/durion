Title: [BACKEND] [STORY] Reconciliation: Support Bank/Cash Reconciliation Matching
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/123
Labels: payment, type:story, domain:accounting, status:needs-review, status:ready-for-dev

STOP: Conflicting domain guidance detected
STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- status:needs-review

### Recommended
- agent:accounting
- agent:story-authoring

### Blocking / Risk
- blocked:domain-conflict
- blocked:clarification

## ⚠️ Domain Conflict Summary
- **Candidate Primary Domains:** `domain:accounting`, `domain:payment`
- **Why conflict was detected:** The issue has a legacy `payment` label, but the functional requirements explicitly describe a core accounting process (reconciliation). The `/kiro` block correctly identifies the domain as `accounting`. The reconciliation process consumes payment data but is owned and governed by accounting principles and workflows.
- **What must be decided:** Confirm that `domain:accounting` is the primary (authoritative) domain for the Bank Reconciliation feature. The `payment` domain will be a secondary, data-providing domain.
- **Recommended split:** No split needed. The story should be owned entirely by the `accounting` domain. The `payment` label should be removed and replaced with `domain:accounting`.

**Rewrite Variant:** accounting-strict

## Story Intent

**As an** Accountant,
**I need to** perform bank reconciliation by matching imported bank statement transactions with system-recorded payments and receipts,
**so that I can** ensure financial accuracy, identify discrepancies, and maintain a complete and auditable financial record for a given period.

## Actors & Stakeholders

- **Accountant (Primary User):** Performs the reconciliation, creates adjustments, and finalizes the reconciliation report.
- **System (POS Platform):** Acts as the source system for payment and receipt transactions to be reconciled.
- **Auditor (Indirect Consumer):** Consumes the finalized reconciliation reports and associated audit trails to verify financial integrity.

## Preconditions

1.  The user is authenticated as an 'Accountant' or a role with equivalent permissions for financial reconciliation.
2.  A chart of accounts, including specific General Ledger (GL) accounts for bank fees and interest, is configured in the system.
3.  A set of unreconciled payment and receipt transactions exists within the system for the period being reconciled.
4.  The bank statement for the corresponding period is available for import in a supported format (e.g., CSV, OFX).

## Functional Behavior

### 1. Initiate Reconciliation
- The Accountant selects a bank account and a date range to start a new reconciliation process.
- The system creates a `Reconciliation` entity in a `Draft` state, pre-populating the starting balance from the previous period's closing balance.

### 2. Import Bank Statement
- The Accountant uploads a bank statement file.
- The system parses the file, validates its format, and imports each line item as a `BankStatementLine` linked to the draft reconciliation.
- The system displays a two-sided view: imported bank statement lines on one side and unreconciled system transactions on the other.

### 3. Match Transactions
- The system may suggest potential matches based on date, amount, and reference number.
- The Accountant can manually match one or more system transactions to a single bank statement line (and vice-versa).
- As items are matched, they are moved from the "unmatched" to the "matched" state within the current reconciliation view.

### 4. Create Adjustments
- If a bank statement line (e.g., a bank fee or interest earned) has no corresponding system transaction, the Accountant can create an `Adjustment`.
- Creating an adjustment requires selecting a predefined reason (e.g., "Bank Fee"), associating it with the correct GL Account, and providing a description.
- This action generates the necessary journal entries to ensure the books remain balanced.

### 5. Finalize Reconciliation
- Once the difference between the adjusted book balance and the bank statement's closing balance is zero, the Accountant can finalize the reconciliation.
- The system transitions the `Reconciliation` entity and all associated matches/adjustments to a `Finalized` state.
- Once finalized, the reconciliation becomes immutable and cannot be edited or reopened.

### 6. Generate Report
- Upon finalization, the system generates a Reconciliation Report detailing the starting balance, all matched transactions, all adjustments, and the closing balance.

## Alternate / Error Flows

- **Import Failure:** If the uploaded bank statement file is in an unsupported format or is corrupted, the system displays a descriptive error message and does not import any data.
- **Mismatched Balances:** If the Accountant attempts to finalize a reconciliation where the adjusted book balance does not equal the statement's closing balance, the system prevents finalization and highlights the discrepancy.
- **Invalid Adjustment:** If an Accountant tries to create an adjustment without selecting a valid GL account, the system displays a validation error and prevents the adjustment from being saved.
- **Permission Denied:** If a user without the required permissions attempts to initiate or finalize a reconciliation, the system denies the action.

## Business Rules

- A `Reconciliation` can only be finalized if the calculated `(Book Balance + Adjustments - Statement Balance)` difference is zero.
- Once a `Reconciliation` is `Finalized`, it is read-only. Any corrections require reversing and re-issuing journal entries in a subsequent period, per standard accounting practices.
- All `Adjustments` must be mapped to a valid General Ledger account.
- A single system transaction can only be matched within one `Reconciliation`.

## Data Requirements

- **Reconciliation:**
  - `reconciliationId` (UUID, PK)
  - `bankAccountId` (FK)
  - `periodStartDate`, `periodEndDate` (Date)
  - `openingBalance`, `closingBalance` (Decimal)
  - `status` (Enum: `DRAFT`, `FINALIZED`)
  - `finalizedByUserId` (FK)
  - `finalizedAt` (Timestamp)
- **BankStatementLine:**
  - `statementLineId` (UUID, PK)
  - `reconciliationId` (FK)
  - `transactionDate` (Date)
  - `description` (String)
  - `amount` (Decimal)
  - `type` (Enum: `DEBIT`, `CREDIT`)
  - `status` (Enum: `UNMATCHED`, `MATCHED`)
- **ReconciliationMatch:**
  - `matchId` (UUID, PK)
  - `reconciliationId` (FK)
  - `statementLineId` (FK)
  - `systemTransactionId` (FK to Payment/Receipt domain)
- **ReconciliationAdjustment:**
  - `adjustmentId` (UUID, PK)
  - `reconciliationId` (FK)
  - `statementLineId` (FK, optional)
  - `glAccountId` (FK)
  - `amount` (Decimal)
  - `description` (String)
  - `journalEntryId` (FK)

## Acceptance Criteria

**Scenario 1: Successful One-to-One Transaction Matching**
- **Given** an Accountant has started a reconciliation for July with an imported bank statement.
- **And** the bank statement shows a debit of $55.50 on July 10th.
- **And** the POS system has an unreconciled payment of $55.50 from July 10th.
- **When** the Accountant selects both the bank statement line and the system payment and clicks "Match".
- **Then** both items are marked as "Matched" for the current reconciliation.
- **And** the outstanding difference to be reconciled is updated correctly.

**Scenario 2: Creating an Adjustment for a Bank Fee**
- **Given** an Accountant is reconciling a period.
- **And** the bank statement shows a $15.00 "Monthly Service Fee" line that has no corresponding system transaction.
- **When** the Accountant selects the fee and chooses the "Create Adjustment" action.
- **And** they select the "Bank Fees" GL account and save the adjustment.
- **Then** a new adjustment transaction is created and linked to the reconciliation.
- **And** a corresponding journal entry is posted to the General Ledger.
- **And** the bank statement line is marked as "Matched".

**Scenario 3: Generating a Finalized Reconciliation Report**
- **Given** a reconciliation has been fully matched and the difference is zero.
- **When** the Accountant clicks the "Finalize" button.
- **Then** the reconciliation status is changed to `Finalized` and it becomes read-only.
- **And** the system generates a downloadable Reconciliation Report containing the opening/closing balances, cleared transactions, and all adjustments.

**Scenario 4: Attempting to Finalize with a Discrepancy**
- **Given** an Accountant is reconciling a period.
- **And** there is a remaining difference of $10.00 between the adjusted book balance and the statement balance.
- **When** the Accountant attempts to finalize the reconciliation.
- **Then** the system displays an error message stating "Reconciliation cannot be finalized with a non-zero difference".
- **And** the reconciliation remains in the `Draft` state.

## Audit & Observability

- **Audit Trail:** Every significant action (Reconciliation created, statement imported, item matched/unmatched, adjustment created, reconciliation finalized) must be logged with the user ID, timestamp, and relevant entity IDs.
- **Events:** The system should emit domain events upon key state changes, such as `ReconciliationFinalized`, to allow downstream systems (e.g., reporting) to react.
- **Metrics:** Track the number of reconciliations in `Draft` vs. `Finalized` state, and the average time to finalize a reconciliation.

## Open Questions

1.  **Import Formats:** What specific file formats must be supported for bank statement import (e.g., CSV, OFX, BAI2)? For CSV, what is the required column structure?
2.  **Matching Tolerance:** Is there a requirement for a matching tolerance (e.g., automatically matching a $100.00 payment to a $99.99 bank line due to minor fees)? If so, what is the tolerance threshold and how are discrepancies handled in the GL?
3.  **Default GL Accounts:** Can default GL accounts be pre-configured for common adjustment types like "Bank Fee" and "Interest Earned" to streamline the user workflow?
4.  **Inter-Domain Contract:** What is the precise contract for retrieving unreconciled transactions from the `payment` domain? Does the `accounting` service query an API, or does it consume events? What is the authoritative definition of a "reconcilable" transaction?
5.  **Report Content:** What specific fields, subtotals, and summaries must be included in the final, auditable Reconciliation Report?

---
## Original Story (Unmodified – For Traceability)
# Issue #123 — [BACKEND] [STORY] Reconciliation: Support Bank/Cash Reconciliation Matching

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Reconciliation: Support Bank/Cash Reconciliation Matching

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Reconciliation, Audit, and Controls

## Story
Reconciliation: Support Bank/Cash Reconciliation Matching

## Acceptance Criteria
- [ ] Import/enter bank statement lines and match to payments/receipts
- [ ] Track matched/unmatched items with audit trail
- [ ] Allow controlled adjustments (fees/interest) via proper entries
- [ ] Produce reconciliation report


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