Title: [BACKEND] [STORY] AR: Issue Credit Memo / Refund with Traceability
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/131
Labels: payment, type:story, domain:accounting, status:needs-review, status:ready-for-dev

STOP: Conflicting domain guidance detected
STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- status:needs-review
- blocked:domain-conflict
- blocked:clarification

### Recommended
- agent:accounting
- agent:payment
- agent:story-authoring

### Blocking / Risk
- blocked:domain-conflict
- blocked:clarification

---
**Rewrite Variant:** integration-conservative
---

## ⚠️ Domain Conflict Summary
- **Candidate Primary Domains:** `domain:accounting`, `domain:payment`
- **Why conflict was detected:** The story combines the creation of a financial instrument (Credit Memo, an Accounting concern) with the execution of a financial transaction (Refund, a Payment concern) without specifying which is the primary action and which is the side-effect. The system of record for the credit balance (Accounts Receivable) and the system of record for the refund transaction are distinct.
- **What must be decided:**
    1.  Is the primary user action creating a Credit Memo, which *may* lead to a refund? (Implies `domain:accounting` is primary).
    2.  Or is the primary user action issuing a Refund, which *must* be backed by a Credit Memo? (Implies `domain:payment` is primary).
    3.  Which domain owns the lifecycle state of the overall credit/refund process?
- **Recommended split:** Yes. Create two separate, linked stories:
    1.  An `accounting` story (this one) for creating and approving a Credit Memo, which establishes a credit on the customer's account.
    2.  A subsequent `payment` story for consuming that credit to either issue a cash refund or apply it to a future invoice.

---
## Story Intent
**As an** Accounts Receivable Clerk,
**I want to** create a traceable Credit Memo against a specific, previously finalized invoice,
**so that** I can formally correct billing errors or account for returned goods, ensuring all financial impacts are accurately recorded in the General Ledger and a clear audit trail is maintained.

## Actors & Stakeholders
- **AR Clerk** (Primary Actor): The user who initiates and creates the Credit Memo.
- **Accounting System** (System): The system of record for the General Ledger (GL), invoices, and Accounts Receivable (AR) balances.
- **Auditor** (Stakeholder): Requires a clear, immutable record of the transaction, its justification, and its link to the original invoice.
- **Payment System** (Downstream System): May be invoked in a subsequent process to issue a cash refund based on the credit established by this memo.

## Preconditions
- The user (AR Clerk) is authenticated and has the necessary permissions (`AR:CreateCreditMemo`).
- A finalized, posted invoice with a non-zero outstanding balance exists in the system.
- A set of valid `Reason Codes` for issuing credit memos is configured in the `Accounting` domain settings (e.g., 'Returned Goods', 'Pricing Error', 'Service Level Credit').

## Functional Behavior
**Trigger:** The AR Clerk selects a finalized invoice from the system and initiates the "Issue Credit Memo" action.

### Process
1.  The system presents the details of the original invoice for confirmation.
2.  The user specifies the amount to be credited, which can be a full or partial amount of the invoice total.
3.  The user **must** select a predefined `Reason Code` from a dropdown list.
4.  The user may add an optional free-text `Justification Note` for auditing purposes.
5.  Upon submission, the system validates the request against the business rules.
6.  On successful validation, the system:
    a. Creates a new `Credit Memo` entity with a unique identifier and a `Posted` status.
    b. Establishes a permanent link between the new `Credit Memo` and the original `Invoice`.
    c. Generates the corresponding, balanced General Ledger (GL) journal entries to:
        -   Debit a Revenue account (reversing revenue).
        -   Debit a Sales Tax Payable account (reversing tax).
        -   Credit the Accounts Receivable (AR) account for the customer, reducing the amount they owe.
    d. Atomically updates the outstanding balance of the original `Invoice`.

## Alternate / Error Flows
- **Error - Invalid Invoice State:** If the selected invoice is not in a `Posted` or `Finalized` state, the system rejects the action with an error message: "Credit Memos can only be issued against finalized invoices."
- **Error - Credit Exceeds Balance:** If the requested credit amount is greater than the outstanding balance of the invoice, the system rejects the action with an error: "Credit amount cannot exceed the invoice's outstanding balance."
- **Error - Missing Reason Code:** If the user submits without selecting a `Reason Code`, the system prevents submission and displays a validation error: "A reason code is required to issue a credit memo."

## Business Rules
- **BR1: Traceability:** A Credit Memo must be immutably linked to a single, existing, finalized Invoice.
- **BR2: Financial Integrity:** The total credited amount against an invoice cannot exceed the original total of that invoice.
- **BR3: Justification:** A `Reason Code` from the centrally managed list is mandatory for all Credit Memos.
- **BR4: Double-Entry Accounting:** All GL journal entries generated by the Credit Memo must be balanced (total debits must equal total credits).
- **BR5: Period-Close Handling:** If the original invoice's accounting period is closed, the system must post the Credit Memo's journal entries to the current open accounting period and correctly flag them as prior period adjustments, per defined accounting policy.

## Data Requirements
### Credit Memo Entity
- `creditMemoId` (PK, Unique Identifier)
- `originalInvoiceId` (FK, non-null, indexed)
- `customerId` (FK, non-null, indexed)
- `creditAmount` (Decimal, non-negative)
- `taxAmountReversed` (Decimal, non-negative)
- `totalAmount` (Decimal, non-negative, `creditAmount` + `taxAmountReversed`)
- `reasonCode` (String, from enumerated list, non-null)
- `justificationNote` (String, optional)
- `status` (Enum: `Draft`, `Posted`, `Applied`, `Voided`)
- `creationTimestamp` (DateTime)
- `postedTimestamp` (DateTime)
- `createdByUserId` (FK)

## Acceptance Criteria
### AC1: Successful Full Credit Memo Creation
- **Given** a finalized invoice #INV-123 for $110 ($100 subtotal + $10 tax) with a full outstanding balance.
- **And** the AR Clerk is authenticated and authorized.
- **When** the clerk creates a full credit memo against #INV-123 with the reason 'Returned Goods'.
- **Then** a new Credit Memo #CM-456 is created for a total of $110.
- **And** Credit Memo #CM-456 is traceably linked to Invoice #INV-123.
- **And** the outstanding balance of Invoice #INV-123 is updated to $0.
- **And** balanced GL journal entries are created to Debit Revenue for $100, Debit Sales Tax Payable for $10, and Credit Accounts Receivable for $110.

### AC2: Successful Partial Credit Memo Creation
- **Given** a finalized invoice #INV-123 for $110 with a full outstanding balance.
- **When** the user creates a partial credit memo for $55 against #INV-123 with the reason 'Pricing Error'.
- **Then** a new Credit Memo is created for a total of $55.
- **And** the outstanding balance of Invoice #INV-123 is updated to $55.
- **And** balanced GL journal entries are created that correctly reverse a proportional amount of revenue and tax.

### AC3: Rejection Due to Amount Exceeding Balance
- **Given** a finalized invoice #INV-123 for $110 with an outstanding balance of $50.
- **When** the user attempts to create a credit memo for $60.
- **Then** the system rejects the request with an error message "Credit amount cannot exceed the invoice's outstanding balance."
- **And** no Credit Memo or GL entries are created.

### AC4: Rejection Due to Missing Reason Code
- **Given** the user is on the 'Create Credit Memo' screen for a valid invoice.
- **When** the user provides a valid amount but attempts to submit without selecting a `Reason Code`.
- **Then** the submission is blocked, and a validation error is displayed on the screen.
- **And** no Credit Memo is created.

## Audit & Observability
- **Audit Trail:** An immutable audit log entry must be created for every Credit Memo creation event. The log must include: `timestamp`, `eventType` (`CREDIT_MEMO_POSTED`), `creditMemoId`, `originalInvoiceId`, `totalAmount`, `reasonCode`, and `actorUserId`.
- **Monitoring:** Emit metrics for `credit_memo.creation.success` and `credit_memo.creation.failure` (with failure reason as a tag).
- **Logging:** All GL posting activities must be logged with correlation IDs linking them back to the `creditMemoId` and `originalInvoiceId`.

## Open Questions
- **Q1 (Domain Conflict Resolution):** This story combines the creation of a `Credit Memo` (`accounting`) and implies a `Refund` (`payment`). To proceed, we need a decision on the recommended split. Is it acceptable to scope this story to *only* the `accounting` functions (creating the memo, adjusting AR), with a separate story to handle the `payment` refund?
- **Q2 (Refund Trigger):** Assuming Q1 is resolved in favor of splitting the stories, what is the business trigger to initiate a cash refund to the customer? Is it an automatic process when a credit memo is created for an already-paid invoice, or is it a separate manual user action in the Payment module?
- **Q3 (Period-Close Policy):** The original story states "Period-close policies handled". Please provide the specific accounting policy. Is it sufficient to post adjustments to the current open period, or are more complex rules for restating prior periods required?
- **Q4 (Approval Workflow):** Does the creation of a Credit Memo require an approval workflow? For example, do memos over a certain dollar amount require manager approval before they are `Posted` and impact the GL?

---
## Original Story (Unmodified – For Traceability)
# Issue #131 — [BACKEND] [STORY] AR: Issue Credit Memo / Refund with Traceability

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] AR: Issue Credit Memo / Refund with Traceability

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Accounts Receivable (Invoice → Cash Application)

## Story
AR: Issue Credit Memo / Refund with Traceability

## Acceptance Criteria
- [ ] Credit memo references original invoice and offsets balances
- [ ] GL postings reverse revenue/tax and reduce AR (or drive refund payment)
- [ ] Reason code required and actions audited
- [ ] Period-close policies handled (adjusting entries if needed)


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