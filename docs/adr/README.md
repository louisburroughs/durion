# Architecture Decision Records (ADR)

## What is an ADR?

An Architecture Decision Record (ADR) is a document that captures an important architectural decision made along with its context and consequences. ADRs help preserve the reasoning behind significant technical choices, making it easier for current and future team members to understand why certain approaches were taken.

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

| Number | Title                                      | Status            | Date       |
|--------|--------------------------------------------|-------------------|------------|
| 0001   | Inventory Ledger ATP Computation           | ACCEPTED          | 2026-01-12 |
| 0002   | CRM Domain Permission Taxonomy             | ACCEPTED          | 2026-01-23 |
| 0003   | CRM Navigation Patterns                    | ACCEPTED          | 2026-01-24 |
| 0004   | Duplicate Detection UX Strategy            | ACCEPTED          | 2026-01-24 |
| 0005   | Optimistic Locking Conflict Resolution     | ACCEPTED          | 2026-01-24 |
| 0006   | WorkExec Domain Ownership Boundaries       | ACCEPTED          | 2026-01-25 |
| 0007   | Workorder/Estimate Approval workflow       | ACCEPTED          | 2026-01-08 |
| 0008   | Cost maintenance clarification diagram     | ACCEPTED          | 2026-01-13 |
| 0009   | Backend Domain responsibilities            | ACCEPTED          | 2026-01-29 |
| 0010   | Frontend Domain responsibilities           | PENDING           | 2026-01-29 |
| 0011   | API Gateway Security Architecture          | ACCEPTED          | 2026-02-01 |
| 0012   | Vehicle-Party relationship ownwership      | ACCEPTED          | 2026-02-03 |
| 0013   | UUID v7 Identifier Strategy                | ACCEPTED          | 2026-02-07 |
| 0014   | Gateway Internal Service Security          | ACCEPTED          | 2026-02-14 |
| 0015   | Identity Entity Relationships              | PROPOSED          | 2026-02-17 |
| 0016   | Location Entity Semantics and Definitions  | ACCEPTED          | 2026-02-17 |
| 0017   | Controller HTTP Response Code Standard     | ACCEPTED          | 2026-02-17 |
| 0018   | Audit Actor Fields from Security Context   | ACCEPTED          | 2026-02-18 |
| 0019   | Short-Lived Operational State Persistence  | ACCEPTED          | 2026-02-19 |
| 0020   | Centralized Document Creation              | ACCEPTED          | 2026-02-19 |
| 0021   | Tax API Consumption and Internal Access    | ACCEPTED          | 2026-02-21 |
| 0022   | Audit Stable Person Identifier Claim Policy| ACCEPTED          | 2026-02-21 |
| 0023   | Remove tenantId / Single-Organization Context | ACCEPTED       | 2026-02-21 |
| 0024   | Entity createdAt/updatedAt Population Policy | PROPOSED        | 2026-02-23 |

## ADR Decision Matrix (When to Invoke + Agent Ownership)

Use this matrix during planning, implementation, and review to quickly decide which ADRs apply and which agents should be involved.

| ADR  | Invoke when... | Primary agents concerned |
|------|----------------|--------------------------|
| 0001 | Inventory availability/ATP logic, reservation semantics, stock math changes | Coder, Test, Planner |
| 0002 | CRM/RBAC permission model or permission taxonomy changes | Coder, Test, Planner, Orchestrator |
| 0003 | CRM navigation/routing/workflow UX structure changes | Coder, Planner, Orchestrator |
| 0004 | Duplicate detection rules, matching thresholds, merge/review flows | Coder, Test, Planner |
| 0005 | Concurrency, version conflicts, optimistic locking behavior | Coder, Test, Planner |
| 0006 | WorkExec ownership boundaries, cross-module responsibility shifts | Planner, Coder, Orchestrator |
| 0007 | Approval workflow states, transitions, authorization gates | Coder, Test, Planner |
| 0008 | Cost-maintenance domain behavior or related decision diagrams | Planner, Coder |
| 0009 | Backend service/domain responsibility boundaries | Planner, Coder, Orchestrator |
| 0010 | Frontend domain ownership and boundary decisions | Planner, Orchestrator |
| 0011 | API gateway auth/authz, token handling, edge security architecture | Coder, Test, Planner, Orchestrator |
| 0012 | Vehicle-party relationship ownership and source-of-truth decisions | Planner, Coder, Test |
| 0013 | Entity identifier strategy (UUID v7), ID generation and serialization | Coder, Test, Planner |
| 0014 | Internal service-to-service security via gateway/service trust model | Coder, Test, Planner, Orchestrator |
| 0015 | Identity entity model relationships and lifecycle behavior | Planner, Coder, Test |
| 0016 | Location domain semantics, field meaning, ownership of location data | Planner, Coder, Test |
| 0017 | HTTP status code semantics and error contract behavior in controllers | Coder, Test, Planner |
| 0018 | Audit actor population from security context and traceability fields | Coder, Test, Planner |
| 0019 | Short-lived operational state persistence vs in-memory decisions | Coder, Test, Planner |
| 0020 | Document creation ownership and service boundaries | Planner, Coder, Test, Orchestrator |
| 0021 | Tax API integration boundaries and internal access policy | Coder, Test, Planner, Orchestrator |
| 0022 | Stable person identifier claims in audit/event payloads | Coder, Test, Planner |
| 0023 | tenantId removal and single-org assumptions across contracts/data | Coder, Test, Planner, Orchestrator |
| 0024 | createdAt/updatedAt population rules, auditing policy, Clock-based time control | Coder, Test, Planner |

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
