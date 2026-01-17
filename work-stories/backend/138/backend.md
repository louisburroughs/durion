Title: [BACKEND] [STORY] Mapping: Configure EventType → Posting Rule Set
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/138
Labels: type:story, domain:accounting, status:ready-for-dev, agent:story-authoring, agent:accounting

## Story Intent
**As a** Financial Controller or System Administrator,
**I want to** configure and manage versioned posting rule sets that map business `EventType`s to general ledger postings,
**so that** journal entries are generated accurately, remain balanced, and are traceable to the exact rules version used.

**Rewrite Variant:** integration-conservative

## Actors & Stakeholders
- **Primary Actor:** Financial Controller / System Administrator
- **Accounting Service (`domain:accounting`):** Owns storage, versioning, validation, and execution of posting rules.
- **Upstream Event Producers (e.g., Sales/Inventory/etc.):** Own `EventType` semantics and event payload contracts.
- **Auditors:** Require traceability from journal entries back to posting rules and event inputs.
- **Developers:** Need a predictable contract for introducing new events + mappings.

## Preconditions
1. A Chart of Accounts (CoA) is defined and accessible to the Accounting service.
2. The actor is authenticated and authorized to manage accounting configuration.
3. The producing domain(s) for events in scope provide versioned event contract documentation (schema/repo/doc) that defines `EventType` and required payload fields.

## Functional Behavior
1. **CRUD + Listing:** Provide an API to create, read, update (via new version), and list `PostingRuleSet`s.
2. **Rule Set Model:** A `PostingRuleSet` maps a single `EventType` to a set of posting rules.
3. **Versioning + Immutability:**
   - Any modification creates a new immutable version.
   - Versions that have been used for posting must not be edited or deleted.
4. **Validation (atomic):**
   - Validate referenced GL accounts exist.
   - Validate referenced `EventType` is recognized for the configured integration scope.
   - Validate the rule set produces balanced entries (Total Debits = Total Credits) for supported conditions.
   - Reject invalid configurations with clear, actionable errors.
5. **Conditional Logic:** Rules within a set support conditional selection based on event payload attributes (e.g., `isTaxable`, `isInventoryItem`, `itemType`).
   - Conditional attributes must be present in the event payload (or derivable from it).
6. **Execution Traceability:** When processing an event:
   - Select rule set by `EventType` + condition(s).
   - Generate a `JournalEntry` that immutably references `postingRuleSetId` + `postingRuleSetVersion`.
7. **Operational safety (recommended):** Add a fail-fast validation at startup (or CI) to detect unknown `EventType` values in configuration.

## Alternate / Error Flows
- **Unbalanced rule set:** Reject with `400 Bad Request` and details describing the imbalance/condition that failed.
- **Invalid GL account reference:** Reject with `400 Bad Request` and identify invalid account ID(s).
- **Invalid/unknown `EventType`:** Reject with `400 Bad Request` and identify the invalid `EventType`.

## Business Rules
1. **Double-entry invariant:** Generated journal entries must be balanced.
2. **Rule version immutability:** Published/used versions cannot be modified; new changes require a new version.
3. **Traceability:** Every journal entry must link to the specific ruleset+version used.
4. **Deterministic posting:** Posting must be deterministic and replayable; avoid synchronous cross-service lookups during posting.

## Data Requirements
- **PostingRuleSet**
  - `ruleSetId` (UUID)
  - `version` (int)
  - `eventType` (string)
  - `rulesDefinition` (JSONB)
  - `status` (DRAFT | PUBLISHED | ARCHIVED)
  - `createdAt`, `updatedAt`
- **JournalEntry**
  - `journalEntryId` (UUID)
  - `sourceTransactionId` (UUID)
  - `postingRuleSetId` (UUID)
  - `postingRuleSetVersion` (int)
  - `journalLines` (debit/credit lines)
  - `postedAt` (timestamp)

## Acceptance Criteria
- **AC1: Balanced rule set creation**
  - Given a defined `EventType` and a valid CoA
  - When a published ruleset is created that balances
  - Then the system returns `201 Created` and persists version 1 as `PUBLISHED`

- **AC2: Reject unbalanced rule set**
  - Given a defined `EventType`
  - When a ruleset is created that does not balance
  - Then the system returns `400` with a clear validation failure message

- **AC3: Conditional logic application**
  - Given a ruleset that conditionally includes lines based on a payload attribute
  - When an event is processed with that attribute set
  - Then the resulting journal entry includes the correct conditional line(s)

- **AC4: Journal entry traceability**
  - Given an active ruleset at version N
  - When a matching event is processed
  - Then the journal entry references `postingRuleSetId` and `postingRuleSetVersion = N`

## Audit & Observability
- **Audit trail:** Log any creation/new-version/publish/archive action for posting rulesets with actor, timestamp, and before/after snapshot.
- **Logging:** Log successful and failed validations with `eventType` and failure reason.
- **Metrics:**
  - `posting_rule_evaluations_total{eventType,result}`
  - `posting_rule_validation_errors_total{eventType,reason}`

## Resolved Decisions (from issue comments)
These decisions were applied from the resolution comment posted on 2026-01-14 ("Decision Doc — Issue #138", generated by `clarification-resolver.sh`):
1. **Domain ownership:** `domain:accounting` is the primary domain for posting rules and journal entry generation.
2. **`EventType` authority:** Upstream event producers own canonical `EventType` semantics; Accounting owns the mapping configuration and should not invent `EventType`s.
3. **Conditional attributes:** Required condition keys must be present in the event payload; do not add synchronous cross-service lookups during posting.

---
## Original Story (Unmodified – For Traceability)
# Issue #138 — [BACKEND] [STORY] Mapping: Configure EventType → Posting Rule Set

## Current Labels
- backend
- story-implementation
- inventory

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Mapping: Configure EventType → Posting Rule Set

**Domain**: inventory

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Maintain Chart of Accounts and Posting Categories

## Story
Mapping: Configure EventType → Posting Rule Set

## Acceptance Criteria
- [ ] Posting rules are versioned and referenced on every journal entry
- [ ] Rules produce balanced debit/credit outputs for representative test fixtures
- [ ] Rules support conditional logic (taxable/non-taxable, inventory/non-inventory)
- [ ] Publishing rules that don’t balance is blocked


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