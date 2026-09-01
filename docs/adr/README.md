# Architecture Decision Records (ADR)

## What is an ADR?

An Architecture Decision Record (ADR) is a document that captures an important architectural decision made along with its context and consequences. ADRs help preserve the
reasoning behind significant technical choices, making it easier for current and future team members to understand why certain approaches were taken.

## When to Create an ADR

Create an ADR when making decisions about:

- System architecture and design patterns
- Technology choices (frameworks, databases, services)
- API contracts and data models
- Performance requirements and SLAs
- Security policies and implementations
- Integration patterns between services
- Data storage and retrieval strategies

## ADR Naming Convention

All ADR files follow this naming format:

```text
NNNN-domain-title.adr.md
```

- **NNNN**: Sequential number, zero-padded to 4 digits (e.g., `0001`, `0002`, `0043`)
- **domain**: Component or architectural area (e.g., `crm`, `api`, `db`, `inventory`, `gateway`)
- **title**: Hyphenated slug describing the decision (e.g., `optimistic-locking`, `cache-strategy`)

### Examples

- `0001-inventory-atp-computation.adr.md` — Inventory ATP calculation strategy
- `0005-crm-optimistic-locking.adr.md` — CRM concurrent edit conflict resolution
- `0010-api-versioning-strategy.adr.md` — REST API versioning approach

### Numbering

- ADRs are numbered sequentially starting from `0001`
- When creating a new ADR, use the next available number
- Do **not** skip numbers; gaps make the sequence harder to track
- If an ADR is deleted or archived, do not reuse its number

## ADR Format

Each ADR should include:

1. **Title**: Brief description of the decision (e.g., "ADR 0001: Inventory Ledger ATP Computation")
2. **Status**: PROPOSED, ACCEPTED, DEPRECATED, SUPERSEDED
3. **Context**: The situation and problem that triggered the need for a decision
4. **Decision**: The choice that was made and key details
5. **Consequences**: Positive and negative outcomes of the decision, including mitigations
6. **References**: Links to issues, pull requests, and related documentation

### Using the Template

A standard ADR template is available at [`TEMPLATE.adr.md`](./TEMPLATE.adr.md). Copy it and fill in your decision details to ensure consistency across all ADRs.

## ADR Numbering

ADRs are numbered sequentially starting from 0001. When creating a new ADR, use the next available number.

## Current ADRs

| Number | Title                                                                        | Status                | Date       |
| ------ | ---------------------------------------------------------------------------- | --------------------- | ---------- |
| 0001   | Inventory Ledger ATP Computation                                             | ACCEPTED              | 2026-01-12 |
| 0002   | CRM Domain Permission Taxonomy                                               | DEPRECATED (ADR-0025) | 2026-01-23 |
| 0003   | CRM Navigation Patterns                                                      | ACCEPTED              | 2026-01-24 |
| 0004   | Duplicate Detection UX Strategy                                              | ACCEPTED              | 2026-01-24 |
| 0005   | Optimistic Locking Conflict Resolution                                       | ACCEPTED              | 2026-01-24 |
| 0006   | WorkExec Domain Ownership Boundaries                                         | ACCEPTED              | 2026-01-25 |
| 0007   | Workorder/Estimate Approval workflow                                         | ACCEPTED              | 2026-01-08 |
| 0008   | Cost maintenance clarification diagram                                       | ACCEPTED              | 2026-01-13 |
| 0009   | Backend Domain responsibilities                                              | ACCEPTED              | 2026-01-29 |
| 0010   | Frontend Domain responsibilities                                             | ACCEPTED              | 2026-03-28 |
| 0011   | API Gateway Security Architecture                                            | ACCEPTED              | 2026-02-01 |
| 0012   | Vehicle-Party relationship ownwership                                        | ACCEPTED              | 2026-02-03 |
| 0013   | UUID v7 Identifier Strategy                                                  | ACCEPTED              | 2026-02-07 |
| 0014   | Gateway Internal Service Security                                            | ACCEPTED              | 2026-02-14 |
| 0015   | Identity Entity Relationships                                                | PROPOSED              | 2026-02-17 |
| 0016   | Location Entity Semantics and Definitions                                    | ACCEPTED              | 2026-02-17 |
| 0017   | Controller HTTP Response Code Standard                                       | ACCEPTED              | 2026-02-17 |
| 0018   | Audit Actor Fields from Security Context                                     | ACCEPTED              | 2026-02-18 |
| 0019   | Short-Lived Operational State Persistence                                    | ACCEPTED              | 2026-02-19 |
| 0020   | Centralized Document Creation                                                | ACCEPTED              | 2026-02-19 |
| 0021   | Tax API Consumption and Internal Access                                      | ACCEPTED              | 2026-02-21 |
| 0022   | Audit Stable Person Identifier Claim Policy                                  | ACCEPTED              | 2026-02-21 |
| 0023   | Remove tenantId / Single-Organization Context                                | ACCEPTED              | 2026-02-21 |
| 0024   | Entity createdAt/updatedAt Population Policy                                 | ACCEPTED              | 2026-02-23 |
| 0025   | Permissions YAML Registration Policy                                         | ACCEPTED              | 2026-02-26 |
| 0026   | Service Contract Boundary Policy                                             | ACCEPTED              | 2026-02-26 |
| 0027   | UUID-Typed Identifier Contract Policy                                        | ACCEPTED              | 2026-02-28 |
| 0028   | Inventory Stock Item Identifier Consistency Policy                           | ACCEPTED              | 2026-02-28 |
| 0029   | Frontend Accessibility Baseline Policy                                       | ACCEPTED              | 2026-03-28 |
| 0030   | Frontend Internationalization and Localization Policy                        | ACCEPTED              | 2026-03-28 |
| 0031   | Frontend Mutation Error State Convention                                     | ACCEPTED              | 2026-03-28 |
| 0032   | Frontend Test Fixture Interface Conformity                                   | ACCEPTED              | 2026-03-28 |
| 0033   | Angular Effect Observable Cancellation Policy                                | ACCEPTED              | 2026-03-28 |
| 0034   | Frontend Server-Generated Field Omission Policy                              | ACCEPTED              | 2026-03-28 |
| 0035   | Frontend Service Method Minimum Test Coverage                                | ACCEPTED              | 2026-03-28 |
| 0036   | Frontend Security Audit Model Ownership Boundary                             | ACCEPTED              | 2026-04-08 |
| 0037   | Frontend SPA Navigation Policy                                               | ACCEPTED              | 2026-04-10 |
| 0038   | Frontend Date-Only String Handling Policy                                    | ACCEPTED              | 2026-04-11 |
| 0039   | Frontend Information-Bearing Contrast Policy                                 | ACCEPTED              | 2026-04-11 |
| 0040   | Roles, JWT Permission Governance Policy                                      | ACCEPTED              | 2026-04-12 |
| 0041   | Frontend Angular SDK API Transport Policy                                    | ACCEPTED              | 2026-04-25 |
| 0042   | OpenAPI Annotation Standards for Backend Services                            | ACCEPTED              | 2026-05-12 |
| 0043   | User–Person Linkage Authority and Translation                                | ACCEPTED              | 2026-06-19 |
| 0044   | Event-Only Domain Walls and Module Communication Policy                      | ACCEPTED              | 2026-07-08 |
| 0045   | Autonomous Environment Lifecycle Management                                  | ACCEPTED              | 2026-07-10 |
| 0046   | Environment Log Level Policy                                                 | PROPOSED              | 2026-07-12 |
| 0047   | Ledger Inalterability and Fiscal Positions Are Accounting Non-Goals          | ACCEPTED              | 2026-07-17 |
| 0048   | Inventory-Owned Valuation with Configurable Costing Method                   | ACCEPTED              | 2026-07-23 |
| 0049   | Supplier Integration Module Boundary and Event Contracts (pos-supplier)      | ACCEPTED              | 2026-08-10 |
| 0050   | Supplier Vendor Profile Configuration Model                                  | ACCEPTED              | 2026-08-10 |
| 0051   | Supplier Protocol Adapter and Codec Versioning Policy                        | ACCEPTED              | 2026-08-10 |
| 0052   | Supplier Outbound Idempotency and Duplicate-Order Prevention                 | ACCEPTED              | 2026-08-10 |
| 0053   | Supplier PRICAT Ingestion, Effective Dating, and Price Precedence            | ACCEPTED              | 2026-08-10 |
| 0054   | Sell-Price System of Record Split (pos-price Quoting, pos-catalog Reference) | ACCEPTED              | 2026-08-10 |
| 0055   | Per-Product Inventory Quantity Divisibility                                  | ACCEPTED              | 2026-08-20 |
| 0056   | Platform Global Exception Handling and Persistence Error Mapping             | ACCEPTED              | 2026-08-23 |
| 0057   | Analytics Money-Measure Semantics and Ownership                              | ACCEPTED              | 2026-09-01 |

## ADR Decision Matrix (When to Invoke + Agent Ownership)

Use this matrix during planning, implementation, and review to quickly decide which ADRs apply and which agents should be involved.

| ADR  | Invoke when...                                                                                                                                                                                                                                                                                      | Primary agents concerned           |
| ---- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- |
| 0001 | Inventory availability/ATP logic, reservation semantics, stock math changes                                                                                                                                                                                                                         | Coder, Test, Planner               |
| 0002 | CRM/RBAC permission taxonomy (historical reference; superseded by 0025)                                                                                                                                                                                                                             | Planner                            |
| 0003 | CRM navigation/routing/workflow UX structure changes                                                                                                                                                                                                                                                | Coder, Planner, Orchestrator       |
| 0004 | Duplicate detection rules, matching thresholds, merge/review flows                                                                                                                                                                                                                                  | Coder, Test, Planner               |
| 0005 | Concurrency, version conflicts, optimistic locking behavior                                                                                                                                                                                                                                         | Coder, Test, Planner               |
| 0006 | WorkExec ownership boundaries, cross-module responsibility shifts                                                                                                                                                                                                                                   | Planner, Coder, Orchestrator       |
| 0007 | Approval workflow states, transitions, authorization gates                                                                                                                                                                                                                                          | Coder, Test, Planner               |
| 0008 | Cost-maintenance domain behavior or related decision diagrams                                                                                                                                                                                                                                       | Planner, Coder                     |
| 0009 | Backend service/domain responsibility boundaries                                                                                                                                                                                                                                                    | Planner, Coder, Orchestrator       |
| 0010 | Frontend domain ownership and boundary decisions                                                                                                                                                                                                                                                    | Planner, Orchestrator              |
| 0011 | API gateway auth/authz, token handling, edge security architecture                                                                                                                                                                                                                                  | Coder, Test, Planner, Orchestrator |
| 0012 | Vehicle-party relationship ownership and source-of-truth decisions                                                                                                                                                                                                                                  | Planner, Coder, Test               |
| 0013 | Entity identifier strategy (UUID v7), ID generation and serialization                                                                                                                                                                                                                               | Coder, Test, Planner               |
| 0014 | Internal service-to-service security via gateway/service trust model                                                                                                                                                                                                                                | Coder, Test, Planner, Orchestrator |
| 0015 | Identity entity model relationships and lifecycle behavior                                                                                                                                                                                                                                          | Planner, Coder, Test               |
| 0016 | Location domain semantics, field meaning, ownership of location data                                                                                                                                                                                                                                | Planner, Coder, Test               |
| 0017 | HTTP status code semantics and error contract behavior in controllers                                                                                                                                                                                                                               | Coder, Test, Planner               |
| 0018 | Audit actor population from security context and traceability fields                                                                                                                                                                                                                                | Coder, Test, Planner               |
| 0019 | Short-lived operational state persistence vs in-memory decisions                                                                                                                                                                                                                                    | Coder, Test, Planner               |
| 0020 | Document creation ownership and service boundaries                                                                                                                                                                                                                                                  | Planner, Coder, Test, Orchestrator |
| 0021 | Tax API integration boundaries and internal access policy                                                                                                                                                                                                                                           | Coder, Test, Planner, Orchestrator |
| 0022 | Stable person identifier claims in audit/event payloads                                                                                                                                                                                                                                             | Coder, Test, Planner               |
| 0023 | tenantId removal and single-org assumptions across contracts/data                                                                                                                                                                                                                                   | Coder, Test, Planner, Orchestrator |
| 0024 | createdAt/updatedAt population rules, auditing policy, Clock-based time control                                                                                                                                                                                                                     | Coder, Test, Planner               |
| 0025 | Permission registration source-of-truth, `permissions.yaml` schema, and rollout                                                                                                                                                                                                                     | Coder, Test, Planner, Orchestrator |
| 0026 | Service interface-only public API boundary and internal implementation encapsulation                                                                                                                                                                                                                | Coder, Test, Planner, Orchestrator |
| 0027 | Identifier typing in service contracts/DTOs/entities (UUID for platform IDs, external ID exceptions)                                                                                                                                                                                                | Coder, Test, Planner, Orchestrator |
| 0028 | Inventory lookup key consistency across reallocation, reservations, and ledger on-hand computation (`stockItemId` as UUID)                                                                                                                                                                          | Coder, Test, Planner, Orchestrator |
| 0029 | Frontend accessibility behavior, keyboard/screen-reader support, and WCAG conformance expectations                                                                                                                                                                                                  | Coder, Test, Planner, Orchestrator |
| 0030 | Frontend i18n/l10n behavior, locale fallback, translation key strategy, and format localization                                                                                                                                                                                                     | Coder, Test, Planner, Orchestrator |
| 0039 | Frontend information-bearing text contrast thresholds (small text 4.5:1, large text 3:1) and ADA/WCAG alignment evidence                                                                                                                                                                            | Coder, Test, Planner, Orchestrator |
| 0040 | Roles semantics in frontend UX, JWT claim contract (`roles`, `perm_bits`, `perm_ver`), gateway authority derivation, and role-vs-permission enforcement boundaries                                                                                                                                  | Coder, Test, Planner, Orchestrator |
| 0031 | Frontend error state population and test expectation setup for mutation/async operations                                                                                                                                                                                                            | Coder, Test, Planner               |
| 0032 | Frontend service/mock test fixture typing and interface alignment for spec compliance                                                                                                                                                                                                               | Coder, Test, Planner               |
| 0033 | Angular `effect()` subscription lifecycle management, cleanup rules, and `takeUntilDestroyed` vs `onCleanup` semantics                                                                                                                                                                              | Coder, Test, Planner               |
| 0034 | Server-generated field omission from request DTOs and prevention of client-supplied server fields in API calls                                                                                                                                                                                      | Coder, Test, Planner               |
| 0035 | Service method HTTP verb + URL minimum test coverage expectations and verification strategy                                                                                                                                                                                                         | Coder, Test, Planner               |
| 0036 | Frontend security audit boundary: model ownership, principal propagation, and UI authorization gate validation                                                                                                                                                                                      | Coder, Test, Planner, Orchestrator |
| 0037 | In-app navigation routing (`routerLink`), button/link semantic correctness, and external link handling                                                                                                                                                                                              | Coder, Test, Planner               |
| 0038 | Date-only string (YYYY-MM-DD) parsing and timezone-safe local-date construction without UTC assumptions                                                                                                                                                                                             | Coder, Test, Planner               |
| 0041 | Frontend-to-backend Angular transport boundary, SDK-first API consumption, and `ApiBaseService` migration decisions                                                                                                                                                                                 | Coder, Test, Planner, Orchestrator |
| 0042 | OpenAPI operation description as tool invocation contract: 7-element structure for unambiguous LLM tool selection and error self-correction                                                                                                                                                         | Coder, Test, Planner, Orchestrator |
| 0043 | User↔Person link authority (`user_person_links` as sole source of truth), token `personId` derivation, mandatory translation (no userId/personId conflation), reconcile + seed-validation enforcement                                                                                               | Coder, Test, Planner, Orchestrator |
| 0044 | Cross-module communication: domain↔domain must be Kafka events (read replicas + command events); synchronous REST only toward utility modules (gateway, security, documents, image, tax, event-receiver, price); outbox/idempotency/backfill/reconciliation requirements; ArchUnit wall enforcement | Planner, Coder, Test, Orchestrator |
| 0045 | Tenant-cell operations automation: off-peak grooming, metric-driven right-sizing, cache warm-up, ramp SLO, autonomy/rollback contract, after-action reporting                                                                                                                                       | Planner, Coder, Test, Orchestrator |
| 0046 | Log level changes in any `application-{profile}.yml`, adding/removing logger overrides, temporary troubleshooting verbosity in deployed environments (alpha WARN; indus/prod ERROR; prod INFO+ needs Tech Lead signoff)                                                                             | Planner, Coder, Orchestrator       |
| 0047 | Accounting parity decisions about ledger tamper-evidence non-goals and fiscal-position non-goals, including revisit triggers and accounting-scope guardrails                                                                                                                                        | Planner, Coder, Test, Orchestrator |
| 0048 | Inventory valuation ownership, cost-bearing inventory facts, costing-method configurability, and the J1/K1 boundary between inventory valuation and accounting posting                                                                                                                              | Planner, Coder, Test, Orchestrator |
| 0049 | Supplier connectivity ownership: pos-supplier as the single module holding vendor credentials/wire formats, canonical supplier model, `supplier.*` event contract set, and the single approved synchronous stock-inquiry read surface                                                               | Planner, Coder, Test, Orchestrator |
| 0050 | Vendor profile configuration: capability bindings (absent = disabled), secret-reference-only credentials, canonical billing/delivery account roles with per-location delivery mapping, sandbox overlays, profile audit/permissions                                                                  | Planner, Coder, Test, Orchestrator |
| 0051 | Supplier protocol adapters and codecs: adapter-per-wire-format-family, registry keyed (capability, protocolFamily, version), norm-version coexistence, unmapped-field audit, golden-file test obligations                                                                                           | Planner, Coder, Test, Orchestrator |
| 0052 | Outbound supplier idempotency: deterministic document IDs, transactional outbox for order transmission, status-first reconciliation of ambiguous outcomes (never blind-retry an order create), MANUAL_REVIEW escalation, mandatory crash/retry tests                                                | Planner, Coder, Test, Orchestrator |
| 0053 | PRICAT supplier-cost policy: pos-catalog owns append-only effective-dated supplier price entries (replacing supplier_item_cost), market-not-location scope, structural no-override of sell prices, deterministic EAN/UPC/MPN matching with quarantine, manifest-chunked import events               | Planner, Coder, Test, Orchestrator |
| 0054 | Sell-price authority routing: pos-price is the transactional quoting SoR, pos-catalog narrows to list/MSRP reference (CUSTOMER_TIER book selection to be finished in the reference role); base-price history retention fix owned here                                                               | Planner, Coder, Test, Orchestrator |
| 0055 | Inventory quantity divisibility declared per product via `product_uom.precision_scale` (undeclared = integral); symmetric demand/supply enforcement, decimal-capable ledger gated by the declaration, UOM on the workorder part line                                                                | Planner, Coder, Test, Orchestrator |
| 0056 | Exception handling in any `pos-*` service: adding/changing a `@ControllerAdvice`, mapping `DataIntegrityViolationException`, catch-all handlers, error envelope/correlation-id behavior on unmapped failures, or a new controller-bearing module (must depend on pos-web-common or declare its own catch-all) | Planner, Coder, Test, Orchestrator |
| 0057 | Any analytics endpoint reporting a money measure: ownership follows the ledger not the artifact, `collected` is cash only (deposit/customer-credit draw-downs excluded), movement-basis corrections with no restatement, refunds measured separately (`REFUND` only), deposit-take invoices excluded from `invoiced`/revenue by `depositSourceType` (never netted against the settlement), replica-only cross-module transport under ADR-0044 | Planner, Coder, Test, Orchestrator |

### Agent role shorthand

- **Coder**: apply ADR decisions in production code and migrations.
- **Test**: verify behavioral compliance, contract coverage, and regression safety.
- **Planner**: identify impacted ADRs up front and sequence implementation work.
- **Orchestrator**: coordinate multi-agent execution and enforce ADR checkpoints in handoffs.

## Superseding ADRs

When a decision is superseded, update the old ADR's status to "SUPERSEDED BY ADR-XXXX" and create a new ADR explaining the new decision and why the change was made.

## Contributing

When adding a new ADR:

1. Use the naming convention: `NNNN-domain-title.adr.md` (see [ADR Naming Convention](#adr-naming-convention))
2. Copy [`TEMPLATE.adr.md`](./TEMPLATE.adr.md) as your starting point
3. Use the next sequential number (check the Current ADRs table below)
4. Fill in all required sections (Title, Status, Date, Deciders, Context, Decision, Consequences, References)
5. **Decision Format:** Use subsections with ✅ **Resolved** markers to document each sub-decision clearly
   - Example: `**Decision:** ✅ **Resolved** - Use proposed approach. [Brief justification and notes].`
   - This makes it easy for reviewers to scan which decisions are finalized
6. Update the Current ADRs table in this README with your new entry
7. Ensure the file name matches the ADR number and domain—agents will validate this during review
8. Submit as part of your pull request
