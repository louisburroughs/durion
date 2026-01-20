Title: [BACKEND] [STORY] GL: Build Balanced Journal Entry from Event
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/137
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
As the General Ledger (GL) System, I need to consume domain events and transform them into complete, balanced, and auditable draft Journal Entries. This ensures that all financially significant activities occurring in other systems are accurately and reliably captured as formal accounting records, forming the foundation for financial reporting.

## Actors & Stakeholders
- **Primary Actor**: GL Subledger System (The automated system responsible for processing events and creating journal entries).
- **Stakeholders**:
    - **Accountant / Auditor**: Reviews the generated draft Journal Entries for accuracy and compliance before posting.
    - **System Administrator**: Configures and maintains the event-to-GL mapping rules that govern the transformation logic.
    - **Upstream System**: The source system that publishes the financially significant domain events (e.g., POS, Inventory, Billing).

## Preconditions
- A financially significant domain event has been published by an upstream system and is available for consumption.
- A valid and active event-to-GL-mapping rule exists for the specific event type and version.
- The Chart of Accounts (including all referenced Accounts, Categories, and Dimensions) is configured and accessible to the GL Subledger System.
- The system has a clearly defined policy for handling processing failures.

## Functional Behavior
1.  **Trigger**: The GL Subledger System consumes a new domain event from an event stream or queue.
2.  **Rule Lookup**: The system identifies the event type and version, and retrieves the corresponding active mapping rule.
3.  **Header Creation**: The system constructs a new Journal Entry (JE) header, populating it with metadata from the event, such as the transaction date, source system, and unique `eventId`. The header is also stamped with the version of the mapping rule used for the transformation.
4.  **Line Item Generation**: The system iterates through the instructions in the mapping rule to generate all required Journal Entry lines from the event payload. Each line specifies:
    - The target GL Account, Category, and any required Dimensions.
    - The amount and currency.
    - The entry type (Debit or Credit).
5.  **Balance Validation**: After generating all lines, the system calculates the sum of all debits and the sum of all credits, grouped by currency. It validates that for each currency, `SUM(Debits) = SUM(Credits)`.
6.  **Persistence**: Upon successful balance validation, the system saves the complete Journal Entry (header and all lines) to the database with a status of `Draft`.

## Alternate / Error Flows
- **Error - No Mapping Rule**: If no active mapping rule is found for the consumed event type, the process is aborted. No Journal Entry is created, and the event is routed for failure handling per system policy.
- **Error - Invalid Data**: If the event payload is missing data required by the mapping rule, or if a referenced GL Account/Dimension is invalid or inactive, the process is aborted. No Journal Entry is created, and the event is routed for failure handling.
- **Error - Unbalanced Entry**: If the balance validation check fails (debits do not equal credits for any currency), the system discards the partially constructed entry. The process is aborted, and the source event is routed for failure handling.

## Business Rules
- **BR1: Atomicity of Transformation**: A single source event must result in exactly one complete, balanced Journal Entry, or it must be rejected entirely. No partial or incomplete journal entries shall be persisted.
- **BR2: Balance Requirement**: For every currency represented within a single Journal Entry, the sum of all debit amounts must strictly equal the sum of all credit amounts.
- **BR3: Initial State**: All successfully generated Journal Entries must be created in a `Draft` state, indicating they are awaiting review and posting.
- **BR4: Failure Handling Policy**: Events that cannot be processed successfully must be handled according to a deterministic system policy (e.g., routing to a suspense account or a dead-letter queue). This policy is critical for audit and reconciliation.

## Data Requirements
- **Journal Entry Header**:
    - `journalEntryId` (PK, System-generated)
    - `sourceEventId` (FK, Traceability)
    - `sourceSystem` (e.g., 'POS', 'BILLING')
    - `mappingRuleVersionId` (FK, Traceability)
    - `transactionDate`
    - `status` (Enum: `Draft`, `Posted`, `Cancelled`)
    - `creationTimestamp`
- **Journal Entry Line**:
    - `journalEntryLineId` (PK, System-generated)
    - `journalEntryId` (FK to Header)
    - `glAccountId` (FK to Chart of Accounts)
    - `glCategoryId` (FK to Chart of Accounts)
    - `dimensionReferences` (JSONB or dedicated columns, e.g., `locationId`, `departmentId`)
    - `amount` (Decimal)
    - `currency` (ISO 4217 code)
    - `entryType` (Enum: `Debit`, `Credit`)

## Acceptance Criteria
**AC1: Successful Creation of a Balanced Journal Entry**
- **Given** a financially significant domain event is consumed
- **And** a valid and active mapping rule exists for the event type
- **When** the GL system processes the event
- **Then** a single new Journal Entry is created and persisted in a `Draft` state
- **And** the Journal Entry header contains a non-null reference to the `sourceEventId` and the `mappingRuleVersionId`
- **And** for each currency within the Journal Entry, the sum of all debit line amounts equals the sum of all credit line amounts
- **And** each Journal Entry line contains valid references to a GL Account, Category, and all required Dimensions.

**AC2: Rejection of an Event with No Matching Rule**
- **Given** a domain event is consumed
- **And** no active mapping rule exists for that event type
- **When** the GL system attempts to process the event
- **Then** no Journal Entry is created in the database
- **And** the event is routed to the configured failure handling mechanism (e.g., dead-letter queue).

**AC3: Rejection of a Logically Unbalanced Entry**
- **Given** a domain event is consumed
- **And** its corresponding mapping rule generates a set of lines where debits do not equal credits for a given currency
- **When** the GL system processes the event and performs the balance validation
- **Then** the validation fails
- **And** no Journal Entry is created in the database
- **And** the event is routed to the configured failure handling mechanism.

## Audit & Observability
- **Audit Logging**: Log the successful creation of every `Draft` Journal Entry, including the `journalEntryId`, `sourceEventId`, and `mappingRuleVersionId`.
- **Error Logging**: Log any processing failures with `ERROR` severity. The log must include the `sourceEventId`, the full event payload, and a clear reason for the failure (e.g., `NO_MAPPING_RULE_FOUND`, `UNBALANCED_ENTRY_ERROR`).
- **Metrics**:
    - `journal_entries.created.count`: Counter for successfully created draft JEs.
    - `journal_entries.failed.count`: Counter for events that failed processing, tagged by failure reason.
    - `journal_entry.processing.time`: Histogram measuring the latency from event consumption to JE persistence.

## Open Questions
- **OQ1: Failure Handling Policy**: The story states "Mapping failures route to suspense or rejection per policy". This policy must be explicitly defined before implementation.
    - a) Does the system post a pre-defined entry to a "Suspense Account"? If so, what are the rules for constructing that entry?
    - b) Or does the system reject the event to a dead-letter queue (DLQ) for manual review and reprocessing? This is the safer default.
    - c) Can this policy be configured globally, or does it vary per event type or source system?

## Original Story (Unmodified – For Traceability)
# Issue #137 — [BACKEND] [STORY] GL: Build Balanced Journal Entry from Event

## Current Labels
- backend
- story-implementation
- general

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] GL: Build Balanced Journal Entry from Event

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
GL: Build Balanced Journal Entry from Event

## Acceptance Criteria
- [ ] Mapped events create a draft journal entry with header refs (eventId/source refs/rule version)
- [ ] JE is balanced per currency (debits=credits)
- [ ] Each JE line includes category, account, and dimension references
- [ ] Mapping failures route to suspense or rejection per policy (no partial postings)


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