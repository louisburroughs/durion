Title: [BACKEND] [STORY] Audit: Maintain Immutable Ledger Audit Trail and Explainability
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/124
Labels: type:story, domain:accounting, status:ready-for-dev

## Story Intent
**As a** Financial Controller,
**I need** every financial posting to be immutable and fully traceable back to its source business event and the accounting mapping/rules versions that generated it,
**so that** audits, reconciliation, and regulatory compliance are achievable and explainable.

## Actors & Stakeholders
- **System (Primary Actor):** Accounting Service (creates JEs / ledger lines)
- **Financial Controller / Auditor:** audits and explains postings
- **Upstream Domains:** Sales / Inventory / Payments (authoritative sources for their business events)

## Preconditions
- Chart of Accounts exists and is accessible.
- Accounting mapping versions and rule versions are versioned and retrievable.
- Upstream domains publish uniquely identified business events.
- Accounting defines a “Posted” state for entries.

## Functional Behavior
1. **Select rule + mapping versions:** When processing a business event, Accounting identifies the applicable `mappingVersionId` and `ruleVersionId`.
2. **Create posting records:** Generate `JournalEntry` and `LedgerLine` records with full traceability fields.
3. **Persist traceability (resolved):** Persist these immutable UUID identifiers on every JE:
   - `sourceEventId` (UUIDv7 from upstream event)
   - `mappingVersionId` (UUIDv7 from Accounting mapping configuration)
   - `ruleVersionId` (UUIDv7 from Accounting rule set)
4. **Capture event snapshot (resolved):** Persist an immutable `sourceEventSnapshot` (JSONB) on the JE containing the details required for audit/posting.
5. **Post atomically:** Validate balanced debits/credits and transition to `POSTED` within a single transaction.
6. **Enforce immutability:** Once `POSTED`, prohibit direct update/delete of JEs and ledger lines.
7. **Correct via reversal:** Corrections are made by creating a new reversing JE referencing `originalJournalEntryId`.

## Alternate / Error Flows
- Missing traceability identifiers or versions → fail the posting; no partial persistence.
- Attempted UPDATE/DELETE of `POSTED` records → reject and emit a high-severity audit event.

## Business Rules
- **Immutability:** `POSTED` JournalEntries and LedgerLines are immutable.
- **Mandatory traceability:** All `POSTED` JournalEntries must include non-null `sourceEventId`, `mappingVersionId`, `ruleVersionId`.
- **Reversal-only corrections:** Corrections must be new reversing entries referencing the original.

## Data Requirements
- Traceability IDs are **UUIDs (RFC 4122 v7)**.
- `JournalEntry` includes:
  - `journalEntryId` (UUIDv7)
  - `sourceEventId` (UUIDv7)
  - `sourceEventType` (string)
  - `mappingVersionId` (UUIDv7)
  - `ruleVersionId` (UUIDv7)
  - `originalJournalEntryId` (UUIDv7, nullable)
  - `sourceEventSnapshot` (JSONB, immutable)
  - `status` (DRAFT/POSTED/REVERSED)
  - `postedTimestamp` (UTC)

## Explainability (resolved)
- **v1.0 (this story):** No public-facing explainability endpoint required; data model + internal audit tooling must support traceability queries.
- **v2.0 (future enhancement):** Optional public API like `GET /accounting/v1/traceability/{journalEntryId}` (out of scope here).

## Event Data Authority (resolved)
- Each upstream domain remains authoritative for its own event details.
- Accounting stores immutable event snapshots needed for posting/audit and links back to source domains for drilldown.

## Acceptance Criteria
- **AC1 (Immutability - Update Reject):** UPDATE attempt on `POSTED` entry is rejected (e.g., `409 Conflict`) and audited.
- **AC2 (Immutability - Delete Reject):** DELETE attempt on `POSTED` entry is rejected (e.g., `409 Conflict`) and audited.
- **AC3 (Traceability Present):** `POSTED` JE contains `sourceEventId`, `mappingVersionId`, `ruleVersionId`.
- **AC4 (Internal Traceability Query):** Internal tooling can retrieve chain: JE → sourceEventId → mappingVersionId → ruleVersionId → ledger lines → audit log.
- **AC5 (Reversal Traceability):** Reversal JE references original JE via `originalJournalEntryId`.

## Audit & Observability
- Emit high-severity audit event for attempted mutation of posted records.
- Structured logs for JE posting include full traceability context.
- Metrics include `journal_entries_posted_total` and `journal_entries_reversed_total` tagged by `sourceEventType`.

## Open Questions
None (resolved via clarification #290).

---

## Original Story (Unmodified – For Traceability)
# Issue #124 — [BACKEND] [STORY] Audit: Maintain Immutable Ledger Audit Trail and Explainability

## Current Labels
- backend
- story-implementation
- general

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Audit: Maintain Immutable Ledger Audit Trail and Explainability

**Domain**: general

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Reconciliation, Audit, and Controls

## Story
Audit: Maintain Immutable Ledger Audit Trail and Explainability

## Acceptance Criteria
- [ ] Ledger lines and JEs are immutable once posted (corrections via reversal)
- [ ] Store rule version and mapping version used for each posting
- [ ] Provide explainability view: event → mapping → rules → JE → ledger lines
- [ ] Full traceability from any GL line to source event/business document


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