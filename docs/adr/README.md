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

```
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
