Title: [BACKEND] [STORY] GL: Provide Trial Balance and Drilldown to Source
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/134
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
As a Controller, I need to generate a Trial Balance report and be able to drill down from any balance to its source transaction, so that I can verify the integrity of the General Ledger, perform financial analysis, and support internal/external audits.

## Actors & Stakeholders
- **Controller (Primary Actor):** Responsible for the accuracy and integrity of the company's financial records. They will generate, review, and analyze the trial balance.
- **Finance Manager / Accountant (Secondary Actor):** Uses the trial balance for period-end closing procedures and financial reporting.
- **Auditor (External Stakeholder):** Requires access to financial data and traceability to source events to conduct audits.
- **System (System Actor):** The General Ledger service that provides the data for the report.

## Preconditions
- The user is authenticated and has the necessary permissions to access the General Ledger reporting features.
- A Chart of Accounts has been configured in the system.
- Fiscal periods (e.g., monthly, quarterly) are defined and closed or open as appropriate.
- Journal entries have been posted to the General Ledger for the periods being reported on.
- Source systems (e.g., Billing, Work Execution) generate events with unique identifiers that are linked to journal entries.

## Functional Behavior

### 4.1 Generate Trial Balance Report
1.  **Trigger:** The Controller navigates to the financial reporting section and requests a new Trial Balance report.
2.  **Process:**
    a. The system presents the Controller with options to filter the report. Required filters include `Fiscal Period`. Optional filters include `Account` ranges and `Dimension(s)`.
    b. The Controller specifies the desired filter criteria and initiates the report generation.
    c. The system queries the General Ledger to calculate the opening, debit, credit, and closing balances for every account that matches the filter criteria for the specified period.
    d. The system presents the Trial Balance on screen, with totals for debits and credits.
    e. The system provides an option to export the displayed report to a CSV file.

### 4.2 Drill Down to Source Event
1.  **Trigger:** The Controller clicks on a specific balance (e.g., the closing balance for the "Accounts Receivable" account) within the generated Trial Balance report.
2.  **Process:**
    a. **Level 1 (Trial Balance -> GL Account Lines):** The system displays all the individual General Ledger transaction lines (debits and credits) that make up the selected balance for that account within the specified period.
    b. **Level 2 (GL Account Line -> Journal Entry):** The Controller clicks on a specific transaction line. The system displays the full Journal Entry (including all its lines, header information, and a reference to the source event) that the line belongs to.
    c. **Level 3 (Journal Entry -> Source Event):** The Controller clicks on the source event reference. The system retrieves and displays the details of the original business event (e.g., the specific customer invoice, repair order, or inventory movement) that triggered the accounting entry.

## Alternate / Error Flows
- **Invalid Period:** If the user enters a non-existent or malformed fiscal period, the system displays an input validation error and prevents report generation.
- **No Data:** If no transactions exist for the selected filters, the system displays a "No data found for the selected criteria" message instead of an empty report.
- **Permission Denied:** If the user attempts to generate a report for accounts or dimensions they are not authorized to view, the system either filters out the restricted data or displays a clear "Access Denied" error message, as defined by security policies.
- **Broken Source Link:** If the drill-down to a source event fails (e.g., the source record was purged or the link is invalid), the system displays an error message indicating the source event could not be retrieved, along with the source event ID for manual investigation.
- **Unbalanced GL:** If the total debits do not equal total credits in the trial balance, the report is still generated but displays a prominent warning indicating an imbalance in the General Ledger. This is a critical system integrity issue.

## Business Rules
- The Trial Balance must always show that total debits equal total credits for the selected population of accounts.
- Balances are calculated based on all `Posted` journal entries within the specified fiscal period. `Draft` or `Pending` entries are excluded.
- The drill-down path must be immutable and reflect the exact data at the time of the transaction.
- Access controls are non-negotiable and must be enforced at the API level, not just in the user interface.
- CSV export format must be standardized and include a header row.

## Data Requirements
- **Trial Balance Report:**
  - `ReportID`
  - `GenerationTimestamp`
  - `FiltersApplied` (Period, Dimensions, etc.)
  - `LineItems[]`:
    - `AccountID`
    - `AccountName`
    - `OpeningBalance`
    - `TotalDebits`
    - `TotalCredits`
    - `ClosingBalance`
  - `TotalDebits` (for the entire report)
  - `TotalCredits` (for the entire report)

- **Journal Entry:**
  - `JournalEntryID`
  - `PostingDate`
  - `Description`
  - `SourceEventID`
  - `SourceSystem`
  - `Status` (e.g., Posted, Draft)
  - `Lines[]`:
    - `AccountID`
    - `DebitAmount`
    - `CreditAmount`

## Acceptance Criteria

**AC1: Generate an Accurate Trial Balance for a Fiscal Period**
- **Given** journal entries have been posted for the fiscal period "2024-08"
- **When** the Controller requests a Trial Balance for "2024-08" with no other filters
- **Then** the system generates a report containing every GL account with activity in that period
- **And** the total debits for the report must exactly equal the total credits.

**AC2: Successful Drill-Down from Balance to Source Event**
- **Given** a Trial Balance report is displayed for "2024-08"
- **When** the Controller clicks the closing balance for the "Revenue" account
- **And then** clicks on a specific journal line from the resulting list
- **And then** clicks on the source event link in the full journal entry view
- **Then** the system displays the details of the original source event (e.g., Invoice #INV-123).

**AC3: Filter Trial Balance by a Dimension**
- **Given** journal entries have been posted and tagged with a "Location" dimension
- **When** the Controller requests a Trial Balance for "2024-08" filtered by `Location: "Main Street"`
- **Then** the report only includes balances derived from journal entries tagged with that specific dimension value.

**AC4: Export Report to CSV**
- **Given** a Trial Balance report is displayed on the screen
- **When** the Controller clicks the "Export to CSV" button
- **Then** a CSV file is downloaded that contains the exact data displayed in the report, including a header row.

**AC5: Enforce Access Control**
- **Given** the Controller's role is restricted from viewing "Executive Payroll" accounts
- **When** they generate a Trial Balance for a period containing entries to those accounts
- **Then** the report must not contain any data (no account lines, no amounts) related to the "Executive Payroll" accounts.

## Audit & Observability
- **Audit Log:**
  - Log every instance of a Trial Balance report generation, including the user who ran it and the filters applied.
  - Log every CSV export action and the user who initiated it.
  - Log any failed access attempts to restricted financial data with high severity.
- **Metrics:**
  - `report_generation_time_ms`: Histogram tracking the latency for generating trial balance reports.
  - `drilldown_requests_total`: Counter for each level of drill-down.
  - `permission_denied_errors_total`: Counter for access control failures.

## Open Questions
1.  **Dimensions:** The story mentions filtering by "dimensions". Which specific dimensions must be supported for the initial release? (e.g., Location, Department, Project, Cost Center).
2.  **Access Control Granularity:** The requirement for "access controls" is high-level. What is the precise model? Is it role-based access to specific accounts, specific dimensions, or a combination? Please provide the rules.
3.  **CSV Format:** What is the exact column layout, ordering, and naming convention required for the CSV export to support controller workflows?
4.  **Period Definition:** How is a "period" officially defined? Does it align strictly with calendar months, or do we need to support custom fiscal periods (e.g., 4-4-5 calendar)?
5.  **Source Event Schema:** While this story doesn't implement the source systems, what is the contract for the "Source Event" payload? What key fields must be displayed when a user drills down to the source?

---
## Original Story (Unmodified – For Traceability)
# Issue #134 — [BACKEND] [STORY] GL: Provide Trial Balance and Drilldown to Source

## Current Labels
- backend
- story-implementation
- general

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] GL: Provide Trial Balance and Drilldown to Source

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
GL: Provide Trial Balance and Drilldown to Source

## Acceptance Criteria
- [ ] Trial balance can be generated by period/account/dimensions
- [ ] Drilldown exists: balance → ledger lines → journal entry → source event
- [ ] Exports supported (CSV) for controller workflows
- [ ] Access controls enforced for sensitive accounts/dimensions


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