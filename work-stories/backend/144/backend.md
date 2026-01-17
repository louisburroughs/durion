Title: [BACKEND] [STORY] Events: Define Canonical Accounting Event Envelope
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/144
Labels: order, type:story, domain:accounting, status:ready-for-dev

## 🏷️ Labels (Current)
### Required
- type:story
- domain:accounting
- status:ready-for-dev

### Recommended
- agent:accounting
- agent:story-authoring

---
**Rewrite Variant:** integration-conservative
**Conflict Resolution:** Resolved - domain:accounting confirmed as primary owner
---

## Story Intent
As a Domain Architect, I want to define and establish a standardized, versioned, and robust data contract for all accounting-related events generated across the system. This "Canonical Accounting Event Envelope" will ensure that financial data is communicated consistently and reliably between all producer modules (like Orders, Billing, Inventory) and the consumer module (Accounting), enabling accurate financial reporting and auditable traceability.

## Actors & Stakeholders
- **System (Event Producer):** Any microservice that generates a financial transaction (e.g., Order Service, Billing Service, Inventory Service). Producers are responsible for populating and publishing events that conform to this contract.
- **System (Event Consumer):** The Accounting Service, which is the authoritative system for financial data. It ingests events conforming to this contract to maintain the general ledger and other financial records.
- **Stakeholder (Accounting Team):** Defines the business requirements for financial data accuracy, completeness, and structure.
- **Stakeholder (Development Teams):** Engineering teams responsible for implementing event production in various microservices. They require a clear, stable, and accessible contract to adhere to.

## Preconditions
- A message bus or event streaming platform (e.g., Kafka, RabbitMQ) is established for inter-service communication.
- Core business entity concepts like `BusinessUnit` and `Currency` (with ISO 4217 codes) are defined and accessible system-wide.
- A decision has been made on where and how schemas will be published and managed (e.g., a schema registry, a shared code library).

## Functional Behavior
The core deliverable of this story is the formal definition of a data contract, not a runtime feature.

1.  **Schema Definition:** A formal, machine-readable schema for the `CanonicalAccountingEvent` will be created. This schema will serve as the single source of truth for the structure of all financial events.
2.  **Schema Publication:** The defined schema will be published to a shared, versioned Git repository (e.g., `durion-accounting-schemas`) under `/schemas/accounting/event-envelope/v{N}/` using JSON Schema (Draft 2020-12).
3.  **Schema Versioning:** The schema will be versioned using Semantic Versioning (SemVer, e.g., `1.0.0`). This allows the contract to evolve over time while providing backward-compatibility guarantees and a clear migration path.
4.  **Schema Governance:** Changes to schemas require PR review with mandatory accounting-domain approval to ensure financial semantics and audit requirements are maintained.

## Alternate / Error Flows
- **Schema Evolution:** Future changes to the schema must follow a managed process. Non-breaking changes (e.g., adding optional fields) can be introduced in minor versions. Breaking changes (e.g., removing fields, changing data types) require a major version increment and a coordinated upgrade plan for all producers and consumers.

## Business Rules
- **Rule-B1 (Authority):** The `domain:accounting` is the authoritative owner and arbiter of this canonical event contract. Other domains (order, billing, workexec) are producers that must conform.
- **Rule-B2 (Conformance):** All events with financial implications MUST conform to the published `CanonicalAccountingEvent` schema.
- **Rule-B3 (Uniqueness):** The `eventId` MUST be a globally unique identifier (e.g., UUID v4) for each event instance.
- **Rule-B4 (Timestamp Authority):** The `occurredAt` timestamp MUST be in UTC (ISO 8601 format) and represent the precise time the business event took place.
- **Rule-B5 (Traceability):** The combination of `sourceModule` and `sourceEntityRef` MUST provide an unambiguous, traceable link back to the originating entity.
- **Rule-B6 (Monetary Precision):** All monetary values MUST be represented in the smallest currency unit (e.g., cents) as integers to prevent floating-point precision errors.
- **Rule-B7 (Financial Breakdown):** The `lines` and `tax` arrays must accurately reflect the financial breakdown of the transaction. Negative values in `lines` should be used for discounts or returns.
- **Rule-B8 (Schema Publication):** Schemas stored in dedicated Git repository with versioned paths; changes via PR with accounting domain review.

## Data Requirements
The `CanonicalAccountingEvent` envelope shall contain the following fields:

| Field Name            | Data Type               | Description                                                                                             | Example                                     |
| --------------------- | ----------------------- | ------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| `eventId`             | UUID                    | Unique identifier for this specific event instance.                                                     | `"a1b2c3d4-..."`                            |
| `eventType`           | String (Enum)           | The specific business event that occurred.                                                              | `"INVOICE_FINALIZED"`                       |
| `schemaVersion`       | String (SemVer)         | The version of the event schema being used.                                                             | `"1.0.0"`                                   |
| `sourceModule`        | String (Enum)           | The originating microservice or domain.                                                                 | `"BILLING_SERVICE"`                         |
| `sourceEntityRef`     | String                  | The unique ID of the primary entity in the source module.                                               | `"inv-98765"`                               |
| `occurredAt`          | Timestamp (ISO 8601)    | The UTC timestamp when the business event happened.                                                     | `"2023-10-27T10:00:00Z"`                    |
| `businessUnitId`      | UUID                    | The identifier for the business unit or location associated with the event.                             | `"f5e4d3c2-..."`                            |
| `currencyUomId`       | String (ISO 4217)       | The three-letter currency code.                                                                         | `"USD"`                                     |
| `lines`               | Array[LineItem]         | An array of objects detailing the financial components of the event.                                    | `[{...}]`                                   |
| `tax`                 | Array[TaxItem]          | An array of objects detailing the tax components.                                                       | `[{...}]`                                   |
| `traceabilityContext` | Object                  | Optional key-value pairs for cross-domain entity references.                                            | `{"workOrderId": "wo-123", "poId": "po-456"}` |
| `metadata`            | Object                  | Optional key-value pairs for additional, non-essential context.                                         | `{"triggeringUserId": "u-abc"}`             |

**LineItem Object Structure:**
- `lineType`: Enum (`PARTS`, `LABOR`, `FEES`, `DISCOUNT`)
- `description`: String
- `quantity`: Decimal
- `unitPrice`: Integer (in cents)
- `totalAmount`: Integer (in cents)

**TaxItem Object Structure:**
- `taxType`: String (e.g., `STATE_TAX`, `FEDERAL_TAX`)
- `taxRate`: Decimal (e.g., `0.0825`)
- `taxableAmount`: Integer (in cents)
- `taxAmount`: Integer (in cents)

## Acceptance Criteria
- **AC1: Correct Schema Structure**
  - **Given** the `CanonicalAccountingEvent` schema definition is finalized
  - **When** a developer or service inspects the contract in the Git repository
  - **Then** the schema MUST contain all the fields specified in the Data Requirements section with their correct data types and constraints.

- **AC2: Schema Versioning Compliance**
  - **Given** the `CanonicalAccountingEvent` schema is published
  - **When** the `schemaVersion` field is inspected
  - **Then** its value MUST conform to the Semantic Versioning format (e.g., "1.0.0", "1.1.0").

- **AC3: Support for Complex Transactions**
  - **Given** a transaction involving parts, labor, a fee, and a discount
  - **When** an event is constructed for this transaction
  - **Then** the `lines` array MUST contain four separate `LineItem` objects, one for each component, with the discount represented by a negative `totalAmount`.

- **AC4: Full Traceability**
  - **Given** an invoice is generated from a work order
  - **When** the `INVOICE_FINALIZED` event is created
  - **Then** the `traceabilityContext` object MUST contain both the `invoiceId` and the originating `workOrderId` to ensure a complete audit trail.

- **AC5: Schema Publication and Governance**
  - **Given** a schema change is proposed
  - **When** a PR is submitted to the `durion-accounting-schemas` repository
  - **Then** it MUST receive approval from the accounting domain reviewer before merge.

## Audit & Observability
- **Schema Lifecycle Audit:** Any creation or update to the schema version in the Git repository MUST be an auditable event (via Git commit history), logging who made the change and when.
- **Contract Accessibility:** The schema definition MUST be easily discoverable and accessible to all development teams via the shared Git repository.
- **Consumer Logging:** Event consumers (especially the Accounting Service) SHOULD log the `eventType` and `schemaVersion` of every event they process to aid in debugging and monitoring.
- **Validation Tracking:** Producers should validate at build-time (CI) and/or runtime; validation failures should be logged with event details.

## Resolved Questions

### Domain Ownership (Resolved)
**Decision:** `domain:accounting` is confirmed as the primary owner of the canonical accounting event contract.

**Rationale:**
- The canonical event envelope defines financial semantics, invariants, and audit requirements
- Accounting is the authoritative consumer and arbiter of financial data structures
- Other domains (order, billing, workexec) are producers that conform to the accounting-owned contract

### Schema Registry/Publication Strategy (Resolved)
**Decision:** Use a shared, versioned Git repository with JSON Schema as the source of truth (v1).

**Implementation Details:**
- **Repository:** Dedicated repo (e.g., `durion-accounting-schemas`)
- **Format:** JSON Schema (Draft 2020-12)
- **Versioning:** SemVer at schema level (`v1`, `v1.1`, `v2`)
- **Storage Path:** `/schemas/accounting/event-envelope/v{N}/`
- **Governance:** Changes via PR with mandatory accounting-domain review
- **Consumption:** Producers validate at build-time (CI) and/or runtime

**Future Option:** Confluent Schema Registry may be adopted later if Kafka becomes mandated transport. Migration is straightforward with curated JSON Schema versions.

---
## Original Story (Unmodified – For Traceability)
# Issue #144 — [BACKEND] [STORY] Events: Define Canonical Accounting Event Envelope

## Current Labels
- backend
- story-implementation
- order

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Events: Define Canonical Accounting Event Envelope

**Domain**: order

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Ingest Accounting Events (Cross-Module)

## Story
Events: Define Canonical Accounting Event Envelope

## Acceptance Criteria
- [ ] Event envelope includes eventId, eventType, sourceModule, sourceEntityRef, occurredAt, businessUnitId, currencyUomId, lines[], tax[], metadata, schemaVersion
- [ ] Supports multi-line totals (parts/labor/fees/discount/tax)
- [ ] Schema is versioned and published as a contract
- [ ] Traceability fields exist (workorderId, invoiceId, poId, receiptId, etc. where applicable)


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