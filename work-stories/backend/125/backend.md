Title: [BACKEND] [STORY] Reporting: Produce Core Financial Statements with Drilldown
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/125
Labels: reporting, type:story, domain:accounting, status:ready-for-dev

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

**Rewrite Variant:** accounting-strict
## Story Intent
**As a** Financial Controller or Accountant,
**I want to** generate core financial statements (Profit & Loss and Balance Sheet) for a specified accounting period,
**So that I can** assess the company's financial health, perform period-end analysis, and meet regulatory reporting requirements.

## Actors & Stakeholders
- **Financial Controller / Accountant (User):** The primary user who generates, views, and analyzes the financial statements.
- **System Administrator:** Manages user roles and permissions, ensuring only authorized personnel can access sensitive financial data.
- **Auditor (Indirect):** A stakeholder who will consume the output of this functionality to perform financial audits. The reports must be accurate and reproducible.

## Preconditions
- A Chart of Accounts (COA) is configured in the system, defining accounts and their classifications (e.g., Asset, Liability, Equity, Revenue, Expense).
- The system contains posted General Ledger (GL) entries with associated accounts, dates, and amounts for the reporting period.
- The user is authenticated and has the necessary permissions to access the financial reporting module.

## Functional Behavior
1.  The user navigates to the financial reporting section of the application.
2.  The user selects the type of report to generate: "Profit & Loss Statement" or "Balance Sheet".
3.  The user specifies the required parameters for the report, such as the date range (for P&L) or a specific end date (for Balance Sheet).
4.  The system initiates a process to aggregate all relevant `Posted` GL entries based on the specified parameters and the account classifications in the COA.
5.  The system calculates the balances for each line item on the selected financial statement.
6.  The report is displayed to the user in a structured format.
7.  The user can interact with a line item on the statement (e.g., "Office Expenses") to "drill down".
8.  The drill-down view displays the list of general ledger accounts that contribute to that line item's total.
9.  From the account list, the user can further drill down to see the individual journal lines (GL entries) that make up that account's balance for the period.
10. The user has an option to export the generated report.

## Alternate / Error Flows
- **No Data for Period:** If no posted GL entries exist for the selected parameters, the system displays a message indicating "No data available for the selected period" instead of an empty or erroneous report.
- **Unauthorized Access:** If a user without the required permissions attempts to generate or view a report, the system denies access and displays an appropriate authorization error.
- **Invalid Parameters:** If the user provides invalid parameters (e.g., end date before the start date for a P&L), the system presents a validation error and prevents the report from being generated.

## Business Rules
- Financial statements must ONLY be generated from `Posted` General Ledger entries. Unposted or draft entries must be excluded.
- The structure of the P&L and Balance Sheet must conform to standard accounting principles. The specific structure and account mappings must be configurable or defined based on a specified standard.
- All calculations (e.g., summations, net income) must be precise and auditable.
- Access to financial reporting is role-based. Viewing, generating, and exporting can be controlled by distinct permissions.
- Reports for a closed accounting period must be immutable and reproducible. Running the same report for the same period at a later date must yield the exact same results.

## Data Requirements
- **GeneralLedgerEntry:** Requires fields for `EntryID`, `AccountID`, `TransactionDate`, `Amount` (Debit/Credit), `Status` (e.g., 'Posted', 'Draft'), and a link to the `SourceEventID`.
- **ChartOfAccounts:** Defines the hierarchy and classification of all financial accounts, including `AccountID`, `AccountName`, `AccountType` (Asset, Liability, Equity, Revenue, Expense), and `ParentAccountID`.
- **FinancialStatementDefinition:** A data structure or configuration that maps accounts from the Chart of Accounts to specific lines on the P&L and Balance Sheet.

## Acceptance Criteria
**Scenario 1: Generate a Profit & Loss Statement**
- **Given** a set of posted General Ledger entries for a specific accounting period, including revenue and expense transactions,
- **When** the Financial Controller requests a Profit & Loss statement for that period,
- **Then** the system generates a report showing total revenues, total expenses, and the resulting net income (or loss),
- **And** the figures correctly sum the amounts from the corresponding GL entries.

**Scenario 2: Generate a Balance Sheet**
- **Given** a set of posted General Ledger entries up to a specific date,
- **When** the Accountant requests a Balance Sheet as of that date,
- **Then** the system generates a report showing total assets, total liabilities, and total equity,
- **And** the fundamental accounting equation (Assets = Liabilities + Equity) is balanced.

**Scenario 3: Drill-down from a Statement Line to Source Transactions**
- **Given** a generated P&L statement is displayed,
- **When** the user clicks on the "Operating Expenses" line item,
- **Then** the system displays a list of all operating expense accounts (e.g., "Salaries", "Rent", "Utilities") and their balances for the period.
- **And When** the user then clicks on the "Rent" account,
- **Then** the system displays the individual journal lines that were posted to the "Rent" account within the period.

**Scenario 4: Unauthorized User Attempts to Access Reports**
- **Given** a user is logged in who does not have "View Financial Statements" permission,
- **When** they attempt to navigate to the financial reporting URL,
- **Then** the system presents an "Access Denied" error page and does not display any financial data.

**Scenario 5: Exporting a Financial Statement**
- **Given** a valid Balance Sheet has been generated for a specific date,
- **When** the user clicks the "Export" button,
- **Then** the system generates a file (e.g., CSV or PDF) containing the complete Balance Sheet data,
- **And** the downloaded file is correctly formatted and matches the on-screen report.

## Audit & Observability
- **Audit Log:** Every instance of a financial statement generation must be logged, capturing `UserID`, `ReportType`, `ReportParameters` (e.g., date range), `Timestamp`, and a `RequestID`.
- **Performance Monitoring:** The performance of the report generation queries should be monitored. Alerts should be configured for queries that exceed a predefined execution time threshold.
- **Data Integrity:** The system should have mechanisms (e.g., scheduled checks) to ensure the Balance Sheet always balances for any given period, flagging any anomalies.

## Open Questions
1.  **Chart of Accounts Mapping:** What is the specific Chart of Accounts (COA) structure to be used for mapping ledger accounts to statement lines? Is this a fixed structure, or does it need to be user-configurable?
2.  **Accounting Standard:** Which accounting standard (e.g., GAAP, IFRS) should the structure of the "basic" P&L and Balance Sheet follow? This impacts the naming and grouping of line items.
3.  **Access Control Granularity:** What are the specific roles and permissions required? Is there a difference between viewing reports and exporting them?
4.  **Export Formats:** What specific export formats are required for launch (e.g., PDF, CSV, XLSX)?
5.  **Multi-Entity/Currency:** How should the system handle multi-entity consolidation or multi-currency scenarios? (Assumption for now: This story covers a single entity in a single currency).

## Original Story (Unmodified – For Traceability)
# Issue #125 — [BACKEND] [STORY] Reporting: Produce Core Financial Statements with Drilldown

## Current Labels
- backend
- story-implementation
- reporting

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Reporting: Produce Core Financial Statements with Drilldown

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
Reporting: Produce Core Financial Statements with Drilldown

## Acceptance Criteria
- [ ] Produce P&L and Balance Sheet (basic) from posted ledger lines
- [ ] Drilldown: statement line → accounts → journal lines → source events
- [ ] Reports are reproducible for the same parameters
- [ ] Exports supported and access controls enforced


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