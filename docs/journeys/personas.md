# Persona Reconciliation Draft

This file turns the extracted persona list into a reconciliation-friendly structure.

The goal is not to finalize every label yet. The goal is to create a stable markdown shape where we can:

- merge obvious synonyms
- group role variants under a canonical family
- separate specialist vs operator vs admin/manager responsibilities
- flag ambiguous labels that still need a naming decision

These personas are prototype user-journey archetypes, not security roles or permission models.
They are meant to help us identify compelling workflows, screen ownership, and narrative journeys for the prototype.

## Working Rules

- Treat exact job-title synonyms as one canonical persona.
- Keep functional subdomains when they affect workflow ownership.
- Prefer a domain + responsibility model over raw title sprawl.
- Keep plain `Admin` and plain `Manager` unresolved unless the story context clearly anchors the domain.
- Allow one raw label to map to a canonical family first, then optionally to a narrower canonical persona later.
- Do not interpret these labels as RBAC definitions; a single human user may participate in multiple journeys.

## Proposed Canonical Structure

### 1. Customer-Facing Service

#### 1.1 Service Advisor

- Journey focus: Detailed vehicle intake, estimate and work order coordination, and assignment of technicians and bays across shop management and work order execution.

- Canonical persona: `Service Advisor`
- Merge into this:
  - `Service Advisor`
- Keep adjacent but separate:
  - `CSR`
  - `Customer Service Representative`
  - `Customer Support Associate`
- Notes:
  - `Service Advisor` is already the dominant customer-facing execution persona and should stay distinct from generic customer support roles.

#### 1.2 Customer Support / Front Counter / Checkout

- Journey focus: Front-desk greeting, lightweight intake, customer lookup, customer-facing CRM work, and end-of-transaction checkout including invoice review, billing, payment collection, and closeout.

- Canonical persona family: `Customer Support Associate`
- Family members:
  - `Customer Service Representative`
  - `CSR`
  - `Customer Support Associate`
- Merge into the `Customer Support Associate` family:
  - `Counter Associate`
  - `Front Desk`
  - `Cashier`
  - `POS Cashier`
  - `POS Clerk`
- Notes:
  - `Customer Support Associate` is the family name for this overall front-counter and checkout journey.
  - `Customer Support / Front Counter / Checkout` should currently be treated as one persona journey family.
  - `CSR` and `Customer Service Representative` remain naming variants within the same overall family.

#### 1.3 Customer Account / Relationship Roles

- Journey focus: Ongoing customer relationship management, especially for commercial and long-term accounts, with CRM-facing ownership.

- Canonical persona family: `Account Management`
- Canonical persona: `Account Manager`
- Merge into `Account Manager`:
  - `Fleet Account Manager`
- Keep separate:
  - `Marketing Manager`
- Notes:
  - `Fleet Account Manager` should currently reconcile into `Account Manager`.
  - `Marketing Manager` remains separate.

### 2. Shop Operations

#### 2.1 Shop Leadership

- Journey focus: Location-level operational leadership, directing service advisors and coordinating shop management, people management, and work order execution.

- Canonical persona family: `Location Management`
- Canonical persona: `Location Manager`
- Merge into `Location Manager`:
  - `Shop Manager`
  - `Store Manager`
  - `Back Office`
  - `Back Office Manager`
  - `Manager`
  - `Approver`
- Keep adjacent but separate:
  - `Director`
- Notes:
  - `Location Manager` is the current roll-up for the primary location-level management journey.
  - `Approver` should currently reconcile into `Location Manager` rather than remain a standalone persona.

#### 2.2 Dispatch & Scheduling

- Journey focus: Scheduling and dispatch coordination, especially for mobile technicians, with a workflow adjacent to service advising.

- Canonical persona family: `Dispatch`
- Canonical persona: `Dispatcher`
- Role variant of `Dispatcher`:
  - `Scheduler`
  - `Mobile Lead`
- Notes:
  - `Scheduler` should currently reconcile into `Dispatcher`.
  - `Mobile Lead` should currently reconcile into the dispatch / service-advisor side of operations rather than a distinct lead-technician persona.

### 3. Shop Execution

#### 3.1 Technician

- Journey focus: Hands-on vehicle execution work, repair workflow progress, and technician-facing work order execution.

- Canonical persona: `Technician`
- Merge into this:
  - `Technician`
  - `Mechanic`
- Notes:
  - This is one of the strongest merges in the current set.
  - If needed later, `Mobile Technician` can become a child of `Technician`.

#### 3.2 Parts Roles

- Journey focus: Parts sourcing, issuance, availability, and counter support for work order and shop execution workflows.

- Canonical persona family: `Parts`
- Candidate children:
  - `Parts Manager`
  - `Parts Associate`
- Merge into `Parts Associate`:
  - `Parts Counter Staff`
- Notes:
  - These should remain distinct from `Technician` even when stories pair them together.

### 4. Inventory & Warehouse

#### 4.1 Inventory Management

- Journey focus: Inventory control, stock governance, and operational oversight of inventory accuracy and movement policy.

- Canonical persona family: `Inventory Management`
- Candidate children:
  - `Inventory Control Manager`
  - `Inventory Staff`
- Role variants of `Inventory Control Manager`:
  - `Inventory Manager`
  - `Inventory Controller`
  - `Inventory Admin`
- Notes:
  - `Inventory Manager`, `Inventory Controller`, and `Inventory Admin` should currently reconcile into `Inventory Control Manager`.

#### 4.2 Warehouse Operations

- Journey focus: Physical receiving, stocking, storage, and warehouse-floor handling of materials and inventory.

- Canonical persona family: `Warehouse`
- Children:
  - `Warehouse Manager`
  - `Warehouse Associate`
- Specialist variants of `Warehouse Associate`:
  - `Receiver`
  - `Stock Clerk`
- Notes:
  - `Receiver` and `Stock Clerk` should be treated as specialist types of `Warehouse Associate`, not separate peer personas.

### 5. Accounting & Finance

This area benefits most from a structured hierarchy. The main divide should be:

- specialist
- operations
- admin / manager
- oversight / audit

#### 5.1 Accounting Specialist

- Journey focus: Day-to-day accounting execution, ledger-adjacent work, and transactional finance operations.

- Canonical persona: `Accounting Associate`
- Merge into `Accounting Associate`:
  - `Accountant`
  - `GL Accountant`
  - `GL Specialist`
  - `Accounting Clerk`
  - `Accounts Receivable Clerk`
  - `AP Clerk`
  - `Billing Specialist`
  - `Back Office Accountant`
  - `Payroll Clerk`
- Notes:
  - GL, AP, AR, billing, payroll, and general accountant labels should currently reconcile into `Accounting Associate`.

#### 5.2 Accounting Operations

- Journey focus: Monitoring, troubleshooting, and operational support for accounting ingestion, reconciliation, and finance-related processing flows.

- Canonical persona: `Accounting Associate`
- Merge into `Accounting Associate`:
  - `Accounting Operations User`
  - `Accounting OPS`
  - `Accounting OPS User`
  - `Accounting Operations Analyst`
  - `Accounting OPS Specialist`
  - `Accounting Integration Operator`
  - `Integration Operator`
  - `Finance OPS User`
- Notes:
  - Accounting operations labels should currently reconcile into `Accounting Associate`.

#### 5.3 Finance / Accounting Management

- Journey focus: Finance oversight, accounting policy ownership, approvals, and management-level control of finance processes.

- Canonical persona family: `Finance / Accounting Management`
- Children:
  - `Controller`
  - `Finance Manager`
  - `Accounting Manager`
- Merge into `Controller`:
  - `Financial Controller`
  - `Finance`
- Merge into `Finance Manager`:
  - `FinanceManager`
- Notes:
  - `Controller` is the current canonical roll-up for finance oversight and controller-style management journeys.
  - Finance/accounting admin labels should reconcile into `System Administrator`, not remain in the finance-management persona family.
  - This family is the right home for management, approvals, configuration authority, and policy ownership.

#### 5.4 Audit & Oversight

- Journey focus: Review, compliance, traceability, and audit-oriented visibility into sensitive or regulated financial activity.

- Canonical persona family: `Audit`
- Merge or map into this family:
  - `Auditor`
  - `Compliance Auditor`
  - `System Auditor`
- Notes:
  - Keep audit distinct from finance admin/manager because the stories describe review and control functions, not operational ownership.

### 6. Product, Pricing, and Business Administration

#### 6.1 Product Administration

- Journey focus: Product setup, maintenance, and administrative stewardship of product data used across the business.

- Canonical persona family: `Product Administration`
- Merge into this:
  - `Product Admin`
  - `Product Administrator`

#### 6.2 Pricing

- Journey focus: Pricing strategy, price maintenance, and pricing-governance workflows that shape sell-side behavior.

- Canonical persona family: `Pricing`
- Candidate children:
  - `Pricing Administrator`
  - `Pricing Analyst`
  - `Pricing Manager`

#### 6.3 General Administration

- Journey focus: System-level administration journeys, configuration ownership, and administrative maintenance of shared system capabilities.

- Canonical persona family: `System Administration`
- Canonical persona: `System Administrator`
- Merge into `System Administrator`:
  - `Admin`
  - `Admin User`
- Notes:
  - This bucket is now reserved for system-style administration roles.
  - `Admin` and `Admin User` should currently reconcile into `System Administrator`.

#### 6.4 Location-Scoped Administrative Management

- Journey focus: Administrative ownership of location and people operations that function more like business management than system administration.

- Canonical persona family: `Location Management`
- Canonical persona: `Location Manager`
- Merge into `Location Manager`:
  - `Shop Administrator`
  - `HR Administrator`
  - `OPS Admin`
- Notes:
  - These should be construed as management or operational-ownership roles rather than system-administration roles.
  - They should roll into the same location-level management journey as `Shop Manager`, `Store Manager`, and `Back Office`.

### 7. Technical / Platform Actors

- Journey focus: Technical implementation, integration, platform support, and architecture-facing workflows that enable the prototype ecosystem.

- Canonical persona family: `Technical / Platform`
- Candidate children:
  - `Backend Engineer`
  - `Domain Architect`
  - `Platform Engineer`
  - `Platform Integrator`
  - `Integration Support Engineer`
  - `Moqui Engineer`

### 8. Non-Human Actors

- Journey focus: Automated or runtime-driven system behavior that participates in workflows without representing a human journey persona.

- Canonical actor family: `System`
- Non-human actors:
  - `System`
  - `System User`
- Notes:
  - `System` should not be treated as a human persona in reporting.
  - This bucket is for automated actors, runtime actors, and system-owned behavior.

## High-Confidence Merges

- `Mechanic` -> `Technician`
- `CSR` -> `Customer Service Representative`
- `Counter Associate`, `Front Desk`, `Cashier`, `POS Cashier`, `POS Clerk` -> `Customer Support Associate`
- `Fleet Account Manager` -> `Account Manager`
- `Scheduler`, `Mobile Lead` -> `Dispatcher`
- `Parts Counter Staff` -> `Parts Associate`
- `Inventory Manager`, `Inventory Controller`, `Inventory Admin` -> `Inventory Control Manager`
- `Accountant`, `GL Accountant`, `GL Specialist`, `Accounting Clerk`, `Accounts Receivable Clerk`, `AP Clerk`, `Billing Specialist`, `Back Office Accountant`, `Payroll Clerk` -> `Accounting Associate`
- `Accounting Operations User`, `Accounting OPS`, `Accounting OPS User`, `Accounting Operations Analyst`, `Accounting OPS Specialist`, `Accounting Integration Operator`, `Integration Operator`, `Finance OPS User` -> `Accounting Associate`
- `Product Admin`, `Product Administrator` -> `Product Administrator`
- `Financial Controller`, `Controller`, `Finance` -> `Controller`
- `Shop Manager`, `Store Manager`, `Back Office`, `Back Office Manager`, `Manager`, `Approver`, `Shop Administrator`, `HR Administrator`, `OPS Admin` -> `Location Manager`
- `FinanceManager` -> `Finance Manager`
- `Admin`, `Admin User` -> `System Administrator`
- `Accounting Admin`, `Finance Admin` -> `System Administrator`
- `System User` -> `System`

## Keep Separate For Now

- `Service Advisor` vs `Customer Support Associate`
- `Dispatcher` vs `Service Advisor`
- `Parts Manager` vs `Parts Associate`
- `Inventory Control Manager` vs `Warehouse Manager`
- `Accounting Associate` vs `Finance / Accounting Management`

## Suggested Next Pass

1. Add a `canonical_persona` field to each story header or story metadata block.
2. Keep `primary_persona` as authored text, but add a normalized canonical label beside it.
3. Add a second field for `persona_family` so reporting can happen at either level.
4. Add a separate `actor_type = system` bucket for non-human actors and keep it distinct from personas.
5. When a story uses a title that sounds like an org role, map it by journey ownership rather than by permissions or security boundaries.
6. Normalize remaining boundary cases like `Director`, `Parts Manager`, and `Warehouse Manager` where story context is strong enough.

## Current Rollup Snapshot

These are the larger raw groupings after the current normalization pass. They are useful as a sanity check, not as final canonical reporting.

- Customer-facing service & sales: 65
- Shop operations leadership: 32
- Execution & parts: 22
- Inventory & warehouse: 16
- Accounting specialists: 27
- Accounting operations: 14
- Finance/admin/oversight: 21
- General admins: 26
- Commercial & pricing admins: 4
- Technical/system actors: 9

## Metadata Template

Use this template when adding normalized persona metadata to stories or related planning docs.

### Human Journey Persona

```md
- `primary_persona`: `<authored persona label from the story>`
- `canonical_persona`: `<normalized persona label>`
- `persona_family`: `<normalized family name>`
- `actor_type`: `human`
```

Example:

```md
- `primary_persona`: `Cashier`
- `canonical_persona`: `Customer Support Associate`
- `persona_family`: `Customer Support Associate`
- `actor_type`: `human`
```

### Non-Human Actor

```md
- `primary_persona`: `<authored actor label from the story>`
- `canonical_persona`: `<normalized actor label>`
- `persona_family`: `<normalized family name or leave equal to canonical>`
- `actor_type`: `system`
```

Example:

```md
### Persona definitions
- `primary_persona`: `System User`
- `canonical_persona`: `System`
- `persona_family`: `System`
- `actor_type`: `system`
```

### Field Intent

- `primary_persona`: The story's authored label as written.
- `canonical_persona`: The normalized journey persona or actor label used for reconciliation.
- `persona_family`: The broader roll-up grouping used for prototype planning and reporting.
- `actor_type`: Use `human` for user-journey personas and `system` for automated or non-human actors.
