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

| Canonical Persona | Stage | Story IDs | Notes |
|---|---|---|---|
| <persona> | <stage> | <#123, #124> | <optional note> |
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

| Canonical Persona | Stage | Story IDs | Notes |
|---|---|---|---|
| `Service Advisor` | Customer context and intake | `#68`, `#79`, `#163`, `#239` | Dominant lane for CRM snapshot, draft estimate creation, and estimate retrieval. |
| `Customer Support Associate` | Customer context and intake | `#67`, `#85` | Front-counter and checkout-adjacent intake behaviors. |
| `Service Advisor` | Quote and estimate authoring | `#80`, `#84`, `#112`, `#234`, `#235`, `#236`, `#237`, `#238` | Authoring, pricing, taxes, product context, and estimate revision. |
| `Service Advisor` | Review and approval | `#232`, `#233`, `#269`, `#270`, `#271` | Submission, revision effects, and approval handling. |
| `Location Manager` | Review and approval | `#268` | Expiration and higher-authority approval behavior. |
| `Service Advisor` | Promotion to work order | `#227`, `#228`, `#229`, `#230`, `#231` | Promotion flows, idempotency, and work order creation from approved scope. |
| `Location Manager` | Promotion to work order | `#226` | Promotion audit visibility and management oversight. |

### Journey 2: Scheduling, Dispatch, and Shop Coordination

#### Purpose
This journey covers planning and coordination work that places vehicles, technicians, bays, and appointments into an executable operational schedule.

#### Stages
- Appointment creation
- Schedule management
- Assignment and coordination
- Work visibility

#### Swimlanes

| Canonical Persona | Stage | Story IDs | Notes |
|---|---|---|---|
| `Service Advisor` | Appointment creation | `#74`, `#139` | Customer-linked appointment creation and assignment visibility. |
| `Dispatcher` | Appointment creation | `#76` | Dispatcher-owned scheduling entry point. |
| `Dispatcher` | Schedule management | `#131`, `#137`, `#138` | Mobile dispatch and schedule manipulation. |
| `Location Manager` | Assignment and coordination | `#128`, `#133`, `#225` | Assignment context, overrides, and technician assignment decisions. |
| `Dispatcher` | Assignment and coordination | `#225` | Shares assignment ownership with shop/location leadership. |
| `Customer Support Associate` | Work visibility | `#78` | Customer-facing or front-counter visibility into active work. |
| `Service Advisor` | Work visibility | `#77` | Invoice/finalization awareness while work is in progress. |

### Journey 3: Work Order Execution and Completion

#### Purpose
This journey covers the technician-facing execution loop plus the service-advisor and management actions that close work and hand it off to billing.

#### Stages
- Execution context
- In-progress execution updates
- Parts handling during execution
- Completion and billing handoff

#### Swimlanes

| Canonical Persona | Stage | Story IDs | Notes |
|---|---|---|---|
| `Technician` | Execution context | `#123`, `#219` | Technician-facing work context and field visibility. |
| `Technician` | In-progress execution updates | `#220`, `#224` | Additional work requests, start work, and in-progress reasons. |
| `Technician` | Parts handling during execution | `#221`, `#222`, `#243` | Substitutions, returns, issue/consume, and picked-item consumption. |
| `Parts Manager` | Parts handling during execution | `#93` | Inventory view from work order lines and parts-centric operational decisions. |
| `Service Advisor` | Completion and billing handoff | `#215`, `#216`, `#217` | Completion, finalize-for-billing, and approval-gated change resolution. |
| `Location Manager` | Completion and billing handoff | `#214` | Controlled reopen and post-completion management workflow. |

## Journey Family 4: Inventory, Warehouse, and Fulfillment

#### Purpose
This is better treated as a journey family than a single journey. It spans planning, warehouse-floor movement, fulfillment, and returns, which are related but not one clean narrative.

#### Candidate Journeys
- Cycle count planning to approval
- Receiving to putaway
- Pick fulfillment to execution issue
- Return unused parts to stock

#### Family Overview

| Candidate Journey | Dominant Personas | Story IDs | Notes |
|---|---|---|---|
| `Cycle count planning to approval` | `Inventory Control Manager` | `#90`, `#91`, `#97`, `#104` | Count governance and topology/control work fit one managerial journey. |
| `Receiving to putaway` | `Warehouse Associate`, `Inventory Control Manager` | `#94`, `#95`, `#96`, `#98`, `#99`, `#241` | Physical receipt and placement form a stronger end-to-end warehouse journey. |
| `Pick fulfillment to execution issue` | `Dispatcher`, `Parts Manager`, `Technician` | `#92`, `#93`, `#243`, `#244` | This is the execution-support branch of the family. |
| `Return unused parts to stock` | `Warehouse Manager`, `Technician` | `#242` | Small but valid reverse-flow journey. |

## Journey Family 5: Billing, Accounting, and Finance

#### Purpose
This is a journey family, not a single journey. It mixes front-counter checkout, AR operations, accounting support, and finance governance, which are distinct user stories with different rhythms and actors.

#### Candidate Journeys
- Checkout to payment completion
- Invoice issuance to AR application
- Accounting event triage to journal resolution
- Period close and finance review

#### Family Overview

| Candidate Journey | Dominant Personas | Story IDs | Notes |
|---|---|---|---|
| `Checkout to payment completion` | `Customer Support Associate`, `Location Manager` | `#67`, `#69`, `#70`, `#71`, `#72`, `#73` | Clean counter-facing journey with a clear customer and cashier endpoint. |
| `Invoice issuance to AR application` | `Service Advisor`, `Accounting Associate`, `Location Manager` | `#177`, `#178`, `#179`, `#180`, `#209`, `#210`, `#211`, `#212` | Operational billing and receivables journey. |
| `Accounting event triage to journal resolution` | `Accounting Associate` | `#181`, `#186`, `#190`, `#200`, `#201`, `#205`, `#206` | Back-office accounting operations journey. |
| `Period close and finance review` | `Accounting Manager`, `Controller`, `Finance Manager` | `#188`, `#189`, `#191`, `#198`, `#199`, `#202`, `#203`, `#204` | Better framed as a management journey cluster than as part of checkout. |

## Journey: Checkout to Payment Completion

#### Purpose
This journey covers the front-counter path from invoice-ready work through payment collection, refund or void exceptions, and customer-facing closeout.

#### Stages
- Checkout and payment
- Exception handling
- Closeout confirmation

#### Swimlanes

| Canonical Persona | Stage | Story IDs | Notes |
|---|---|---|---|
| `Customer Support Associate` | Checkout and payment | `#67`, `#69`, `#70`, `#71`, `#73` | Front-counter payment and status visibility behaviors. |
| `Location Manager` | Exception handling | `#72`, `#210` | Manager-controlled voids, refunds, and invoice adjustments. |
| `Accounting Associate` | Exception handling | `#179`, `#180`, `#211` | Refund handling, AR application, and invoice traceability support. |
| `Service Advisor` | Closeout confirmation | `#212` | Draft invoice calculation and totals confidence before final customer handoff. |

## Journey Family 6: Product, Pricing, and Promotions Governance

#### Purpose
This area is also better treated as a journey family. Product administration, pricing governance, and promotions management share data, but they are not one end-to-end day-in-the-life flow.

#### Candidate Journeys
- Product setup and maintenance
- Price book and rule governance
- Promotion design to redemption support

#### Family Overview

| Candidate Journey | Dominant Personas | Story IDs | Notes |
|---|---|---|---|
| `Product setup and maintenance` | `Product Administrator`, `Inventory Staff` | `#108`, `#109`, `#119`, `#120`, `#121` | Data stewardship and product maintenance. |
| `Price book and rule governance` | `Pricing Administrator`, `Pricing Analyst`, `Service Advisor` | `#107`, `#117`, `#118`, `#159`, `#160`, `#167` | Governance plus downstream visibility in customer-facing quoting. |
| `Promotion design to redemption support` | `Account Manager`, `Customer Support Associate` | `#158`, `#161` | Lightweight cross-functional promo lifecycle. |

## Journey: Price Book and Rule Governance

#### Purpose
This journey covers how pricing administrators and analysts define pricing scope, rules, and eligibility so that customer-facing teams can quote and apply pricing correctly.

#### Stages
- Scope and visibility
- Pricing configuration
- Downstream quote application

#### Swimlanes

| Canonical Persona | Stage | Story IDs | Notes |
|---|---|---|---|
| `Pricing Administrator` | Scope and visibility | `#117` | Pricing-scope location visibility and sync oversight. |
| `Pricing Analyst` | Pricing configuration | `#118`, `#160`, `#167` | Price book rules, eligibility, and pricing governance. |
| `Service Advisor` | Downstream quote application | `#107`, `#159` | Restriction visibility and promotion application in customer-facing flows. |

### Journey 7: Party, CRM, and Account Management

#### Purpose
This journey covers customer and account record creation, association, and stewardship outside of the immediate estimate-building flow.

#### Stages
- Person and account creation
- Account relationship management
- Record stewardship

#### Swimlanes

| Canonical Persona | Stage | Story IDs | Notes |
|---|---|---|---|
| `Customer Support Associate` | Person and account creation | `#175` | Individual person creation in customer-facing workflows. |
| `Account Manager` | Person and account creation | `#176` | Commercial account creation. |
| `Account Manager` | Account relationship management | `#174` | Associate individuals to commercial accounts. |
| `System Administrator` | Record stewardship | `#173` | Duplicate party search and merge. |

### Journey 8: Security, Administration, and Platform Operations

#### Purpose
This journey covers access control, security oversight, integration support, platform configuration, and non-customer-facing operational tooling.

#### Stages
- Security administration
- Audit and compliance
- Platform and integration support

#### Swimlanes

| Canonical Persona | Stage | Story IDs | Notes |
|---|---|---|---|
| `System Administrator` | Security administration | `#66`, `#87` | Role matrix and admin security configuration. |
| `Auditor` | Audit and compliance | `#65` | Financial exception audit trails. |
| `Compliance Auditor` | Audit and compliance | `#86` | Security and movement audit visibility. |
| `Integration Support Engineer` | Platform and integration support | `#156`, `#157` | Operational support and inbound processing diagnostics. |
| `Platform Engineer` | Platform and integration support | `#207` | Ingestion tooling and producer validation support. |
| `Domain Architect` | Platform and integration support | `#208` | Canonical event envelope contract. |
| `Moqui Engineer` | Platform and integration support | `#280` | Signed assertion issuance and frontend platform integration. |

### Journey 9: People, Timekeeping, and Labor Flow

#### Purpose
This journey covers labor tracking, mobile work timing, time approval, and accounting/payroll handoff from worked time.

#### Stages
- Work start and timing
- Time approval and exception handling
- Export and downstream handoff

#### Swimlanes

| Canonical Persona | Stage | Story IDs | Notes |
|---|---|---|---|
| `Technician` | Work start and timing | `#132`, `#145`, `#146`, `#147` | Start/stop work, mobile timers, and labor submission. |
| `Dispatcher` | Time approval and exception handling | `#131` | Mobile travel-time coordination. |
| `Location Manager` | Time approval and exception handling | `#130`, `#144` | Time approval and discrepancy reporting. |
| `Accounting Associate` | Export and downstream handoff | `#143` | Approved time export for payroll and accounting. |

## Suggested Next Pass

1. Choose one `journey_family` and one child `journey` as the prototype spine for the next demo.
2. Split the remaining broad families into similarly scoped child journeys before adding more swimlane detail.
3. Add `primary_journey_family`, `primary_journey`, and `primary_stage` beside `canonical_persona` in story metadata.
4. Decide which stories are truly cross-journey and which should have a single canonical home.
5. If desired, add a Mermaid diagram per journey after the markdown structure feels stable.
