# Journey Map Seed

This document is a starter artifact for grouping normalized stories into prototype journeys.

It is intentionally lightweight and editable. The goal is to create a practical planning layer above `canonical_persona`, not to lock the team into a final process model too early.

## Why This Layer Exists

- `canonical_persona` tells us who primarily owns a story.
- `persona_family` lets us roll multiple roles into one planning lane.
- `journey_family` gives us the larger operational area or value stream.
- `journey` gives us the specific narrative thread or prototype scenario where a story belongs.
- `stage` helps us place stories in a sequence that feels like a realistic day, workflow, or cross-team handoff.

This makes it easier to:

- storyboard the prototype
- define swimlanes by persona
- identify gaps and overlaps
- decide what should be demoed together

## Definitions

- `journey_family`: A broad operating area or value stream that contains multiple related journeys.
- `journey`: A specific end-to-end scenario with a recognizable start, end, and user value outcome.
- `stage`: A meaningful step inside a journey.
- `canonical_persona`: The primary lane owner for a story within that stage.

A working journey should usually have:

- one clear triggering intent
- a recognizable beginning and end
- a scope small enough to storyboard or demo coherently
- one dominant actor plus explicit handoffs where needed

## Working Model

Use this hierarchy:

1. `journey_family`
2. `journey`
3. `stage`
4. `canonical_persona`
5. `story_id`

A story can appear in more than one journey when needed, but it should usually have one primary home.

## Markdown Template

Use this template for future journey docs:

```md
## Journey Family: <family title>

### Purpose

<one or two sentences on the broader value stream or operating area>

### Candidate Journeys

- <journey 1>
- <journey 2>

## Journey: <journey title>

### Purpose

<one or two sentences on what user value or operational outcome this journey represents>

### Stages

- <stage 1>
- <stage 2>
- <stage 3>

### Swimlanes

| Canonical Persona | Stage   | Expected Events    | Notes           |
| ----------------- | ------- | ------------------ | --------------- |
| <persona>         | <stage> | `[Action, Object]` | <optional note> |
```

## Seeded Journey Families and Journeys

These are suggested first-pass journey groupings seeded from:

- local story titles
- local `canonical_persona` metadata
- the persona taxonomy in [personas.md](/home/louis-burroughs/IdeaProjects/durion/personas.md)

### Journey 1: Customer Intake to Estimate

#### Purpose

This journey covers the customer-facing path from initial context lookup through estimate authoring, review, approval, and promotion toward execution.

#### Stages

- Customer context and intake
- Quote and estimate authoring
- Review and approval
- Promotion to work order

#### Swimlanes

| Canonical Persona            | Stage                        | Expected Events                                                              | Notes                                                                            |
| ---------------------------- | ---------------------------- | ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `Service Advisor`            | Customer context and intake  | `[View, CustomerContext]`, `[Create, DraftEstimate]`, `[Retrieve, Estimate]` | Dominant lane for CRM snapshot, draft estimate creation, and estimate retrieval. |
| `Customer Support Associate` | Customer context and intake  | `[Initialize, Intake]`                                                       | Front-counter and checkout-adjacent intake behaviors.                            |
| `Service Advisor`            | Quote and estimate authoring | `[Add, LineItem]`, `[Calculate, Pricing]`, `[Revise, Estimate]`              | Authoring, pricing, taxes, product context, and estimate revision.               |
| `Service Advisor`            | Review and approval          | `[Submit, Estimate]`, `[Approve, Estimate]`                                  | Submission, revision effects, and approval handling.                             |
| `Location Manager`           | Review and approval          | `[Override, Approval]`, `[Expire, Estimate]`                                 | Expiration and higher-authority approval behavior.                               |
| `Service Advisor`            | Promotion to work order      | `[Create, WorkOrder]`, `[Check, Idempotency]`                                | Promotion flows, idempotency, and work order creation from approved scope.       |
| `Location Manager`           | Promotion to work order      | `[View, PromotionAudit]`                                                     | Promotion audit visibility and management oversight.                             |

### Journey 2: Scheduling, Dispatch, and Shop Coordination

#### Purpose

This journey covers planning and coordination work that places vehicles, technicians, bays, and appointments into an executable operational schedule.

#### Stages

- Appointment creation
- Schedule management
- Assignment and coordination
- Work visibility

#### Swimlanes

| Canonical Persona            | Stage                       | Expected Events                                  | Notes                                                               |
| ---------------------------- | --------------------------- | ------------------------------------------------ | ------------------------------------------------------------------- |
| `Service Advisor`            | Appointment creation        | `[Create, Appointment]`                          | Customer-linked appointment creation and assignment visibility.     |
| `Dispatcher`                 | Appointment creation        | `[View, Schedule]`                               | Dispatcher-owned scheduling entry point.                            |
| `Dispatcher`                 | Schedule management         | `[Update, Schedule]`, `[Cancel, Appointment]`    | Mobile dispatch and schedule manipulation.                          |
| `Location Manager`           | Assignment and coordination | `[Assign, Technician]`, `[Override, Assignment]` | Assignment context, overrides, and technician assignment decisions. |
| `Dispatcher`                 | Assignment and coordination | `[Assign, Technician]`                           | Shares assignment ownership with shop/location leadership.          |
| `Customer Support Associate` | Work visibility             | `[View, ActiveWork]`                             | Customer-facing or front-counter visibility into active work.       |
| `Service Advisor`            | Work visibility             | `[Review, WorkStatus]`                           | Invoice/finalization awareness while work is in progress.           |

### Journey 3: Work Order Execution and Completion

#### Purpose

This journey covers the technician-facing execution loop plus the service-advisor and management actions that close work and hand it off to billing.

#### Stages

- Execution context
- In-progress execution updates
- Parts handling during execution
- Completion and billing handoff

#### Swimlanes

| Canonical Persona  | Stage                           | Expected Events                                                | Notes                                                                         |
| ------------------ | ------------------------------- | -------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `Technician`       | Execution context               | `[View, WorkContext]`, `[View, FieldContext]`                  | Technician-facing work context and field visibility.                          |
| `Technician`       | In-progress execution updates   | `[Start, Work]`, `[Log, AdditionalRequest]`                    | Additional work requests, start work, and in-progress reasons.                |
| `Technician`       | Parts handling during execution | `[Substitute, Part]`, `[Consume, Part]`, `[Return, Part]`      | Substitutions, returns, issue/consume, and picked-item consumption.           |
| `Parts Manager`    | Parts handling during execution | `[View, InventoryLine]`                                        | Inventory view from work order lines and parts-centric operational decisions. |
| `Service Advisor`  | Completion and billing handoff  | `[Complete, Work]`, `[Finalize, Billing]`, `[Resolve, Change]` | Completion, finalize-for-billing, and approval-gated change resolution.       |
| `Location Manager` | Completion and billing handoff  | `[Reopen, WorkOrder]`                                          | Controlled reopen and post-completion management workflow.                    |

## Journey Family 4: Inventory, Warehouse, and Fulfillment

#### Purpose

This is better treated as a journey family than a single journey. It spans planning, warehouse-floor movement, fulfillment, and returns, which are related but not one clean narrative.

#### Candidate Journeys

- Cycle count planning to approval
- Receiving to putaway
- Pick fulfillment to execution issue
- Return unused parts to stock

#### Family Overview

| Candidate Journey                     | Dominant Personas                                  | Expected Events                               | Notes                                                                        |
| ------------------------------------- | -------------------------------------------------- | --------------------------------------------- | ---------------------------------------------------------------------------- |
| `Cycle count planning to approval`    | `Inventory Control Manager`                        | `[Plan, CycleCount]`, `[Approve, CycleCount]` | Count governance and topology/control work fit one managerial journey.       |
| `Receiving to putaway`                | `Warehouse Associate`, `Inventory Control Manager` | `[Receive, Shipment]`, `[Putaway, Inventory]` | Physical receipt and placement form a stronger end-to-end warehouse journey. |
| `Pick fulfillment to execution issue` | `Dispatcher`, `Parts Manager`, `Technician`        | `[Pick, Parts]`, `[Fulfill, Order]`           | This is the execution-support branch of the family.                          |
| `Return unused parts to stock`        | `Warehouse Manager`, `Technician`                  | `[Return, Parts]`                             | Small but valid reverse-flow journey.                                        |

## Journey Family 5: Billing, Accounting, and Finance

#### Purpose

This is a journey family, not a single journey. It mixes front-counter checkout, AR operations, accounting support, and finance governance, which are distinct user stories with different rhythms and actors.

#### Candidate Journeys

- Checkout to payment completion
- Invoice issuance to AR application
- Accounting event triage to journal resolution
- Period close and finance review

#### Family Overview

| Candidate Journey                               | Dominant Personas                                             | Expected Events                              | Notes                                                                    |
| ----------------------------------------------- | ------------------------------------------------------------- | -------------------------------------------- | ------------------------------------------------------------------------ |
| `Checkout to payment completion`                | `Customer Support Associate`, `Location Manager`              | `[Initiate, Checkout]`, `[Process, Payment]` | Clean counter-facing journey with a clear customer and cashier endpoint. |
| `Invoice issuance to AR application`            | `Service Advisor`, `Accounting Associate`, `Location Manager` | `[Issue, Invoice]`, `[Apply, Receivable]`    | Operational billing and receivables journey.                             |
| `Accounting event triage to journal resolution` | `Accounting Associate`                                        | `[Triage, Event]`, `[Resolve, Journal]`      | Back-office accounting operations journey.                               |
| `Period close and finance review`               | `Accounting Manager`, `Controller`, `Finance Manager`         | `[Close, Period]`, `[Review, Audit]`         | Better framed as a management journey cluster than as part of checkout.  |

## Journey: Checkout to Payment Completion

#### Purpose

This journey covers the front-counter path from invoice-ready work through payment collection, refund or void exceptions, and customer-facing closeout.

#### Stages

- Checkout and payment
- Exception handling
- Closeout confirmation

#### Swimlanes

| Canonical Persona            | Stage                 | Expected Events                        | Notes                                                                          |
| ---------------------------- | --------------------- | -------------------------------------- | ------------------------------------------------------------------------------ |
| `Customer Support Associate` | Checkout and payment  | `[Process, Payment]`, `[View, Status]` | Front-counter payment and status visibility behaviors.                         |
| `Location Manager`           | Exception handling    | `[Void, Invoice]`, `[Adjust, Invoice]` | Manager-controlled voids, refunds, and invoice adjustments.                    |
| `Accounting Associate`       | Exception handling    | `[Issue, Refund]`, `[Apply, AR]`       | Refund handling, AR application, and invoice traceability support.             |
| `Service Advisor`            | Closeout confirmation | `[Calculate, DraftInvoice]`            | Draft invoice calculation and totals confidence before final customer handoff. |

## Journey Family 6: Product, Pricing, and Promotions Governance

#### Purpose

This area is also better treated as a journey family. Product administration, pricing governance, and promotions management share data, but they are not one end-to-end day-in-the-life flow.

#### Candidate Journeys

- Product setup and maintenance
- Price book and rule governance
- Promotion design to redemption support

#### Family Overview

| Candidate Journey                        | Dominant Personas                                             | Expected Events                              | Notes                                                             |
| ---------------------------------------- | ------------------------------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| `Product setup and maintenance`          | `Product Administrator`, `Inventory Staff`                    | `[Create, Product]`, `[Maintain, Product]`   | Data stewardship and product maintenance.                         |
| `Price book and rule governance`         | `Pricing Administrator`, `Pricing Analyst`, `Service Advisor` | `[Create, Rule]`, `[View, Pricing]`          | Governance plus downstream visibility in customer-facing quoting. |
| `Promotion design to redemption support` | `Account Manager`, `Customer Support Associate`               | `[Design, Promotion]`, `[Redeem, Promotion]` | Lightweight cross-functional promo lifecycle.                     |

## Journey: Price Book and Rule Governance

#### Purpose

This journey covers how pricing administrators and analysts define pricing scope, rules, and eligibility so that customer-facing teams can quote and apply pricing correctly.

#### Stages

- Scope and visibility
- Pricing configuration
- Downstream quote application

#### Swimlanes

| Canonical Persona       | Stage                        | Expected Events            | Notes                                                                      |
| ----------------------- | ---------------------------- | -------------------------- | -------------------------------------------------------------------------- |
| `Pricing Administrator` | Scope and visibility         | `[View, PricingScope]`     | Pricing-scope location visibility and sync oversight.                      |
| `Pricing Analyst`       | Pricing configuration        | `[Configure, PricingRule]` | Price book rules, eligibility, and pricing governance.                     |
| `Service Advisor`       | Downstream quote application | `[Apply, Promotion]`       | Restriction visibility and promotion application in customer-facing flows. |

### Journey 7: Party, CRM, and Account Management

#### Purpose

This journey covers customer and account record creation, association, and stewardship outside of the immediate estimate-building flow.

#### Stages

- Person and account creation
- Account relationship management
- Record stewardship

#### Swimlanes

| Canonical Persona            | Stage                           | Expected Events              | Notes                                                    |
| ---------------------------- | ------------------------------- | ---------------------------- | -------------------------------------------------------- |
| `Customer Support Associate` | Person and account creation     | `[Create, Person]`           | Individual person creation in customer-facing workflows. |
| `Account Manager`            | Person and account creation     | `[Create, Account]`          | Commercial account creation.                             |
| `Account Manager`            | Account relationship management | `[Associate, PersonAccount]` | Associate individuals to commercial accounts.            |
| `System Administrator`       | Record stewardship              | `[Merge, Party]`             | Duplicate party search and merge.                        |

### Journey 8: Security, Administration, and Platform Operations

#### Purpose

This journey covers access control, security oversight, integration support, platform configuration, and non-customer-facing operational tooling.

#### Stages

- Security administration
- Audit and compliance
- Platform and integration support

#### Swimlanes

| Canonical Persona              | Stage                            | Expected Events               | Notes                                                        |
| ------------------------------ | -------------------------------- | ----------------------------- | ------------------------------------------------------------ |
| `System Administrator`         | Security administration          | `[Configure, SecurityRole]`   | Role matrix and admin security configuration.                |
| `Auditor`                      | Audit and compliance             | `[Audit, FinancialException]` | Financial exception audit trails.                            |
| `Compliance Auditor`           | Audit and compliance             | `[Audit, SecurityMovement]`   | Security and movement audit visibility.                      |
| `Integration Support Engineer` | Platform and integration support | `[Diagnose, Integration]`     | Operational support and inbound processing diagnostics.      |
| `Platform Engineer`            | Platform and integration support | `[Validate, Producer]`        | Ingestion tooling and producer validation support.           |
| `Domain Architect`             | Platform and integration support | `[Define, EventContract]`     | Canonical event envelope contract.                           |
| `Moqui Engineer`               | Platform and integration support | `[Issue, Assertion]`          | Signed assertion issuance and frontend platform integration. |

### Journey 9: People, Timekeeping, and Labor Flow

#### Purpose

This journey covers labor tracking, mobile work timing, time approval, and accounting/payroll handoff from worked time.

#### Stages

- Work start and timing
- Time approval and exception handling
- Export and downstream handoff

#### Swimlanes

| Canonical Persona      | Stage                                | Expected Events                                      | Notes                                                 |
| ---------------------- | ------------------------------------ | ---------------------------------------------------- | ----------------------------------------------------- |
| `Technician`           | Work start and timing                | `[Start, Timer]`, `[Stop, Timer]`, `[Submit, Labor]` | Start/stop work, mobile timers, and labor submission. |
| `Dispatcher`           | Time approval and exception handling | `[Coordinate, TravelTime]`                           | Mobile travel-time coordination.                      |
| `Location Manager`     | Time approval and exception handling | `[Approve, Time]`, `[Report, Discrepancy]`           | Time approval and discrepancy reporting.              |
| `Accounting Associate` | Export and downstream handoff        | `[Export, Time]`                                     | Approved time export for payroll and accounting.      |

## Suggested Next Pass

1. Choose one `journey_family` and one child `journey` as the prototype spine for the next demo.
2. Split the remaining broad families into similarly scoped child journeys before adding more swimlane detail.
3. Add `primary_journey_family`, `primary_journey`, and `primary_stage` beside `canonical_persona` in story metadata.
4. Decide which stories are truly cross-journey and which should have a single canonical home.
5. If desired, add a Mermaid diagram per journey after the markdown structure feels stable.
