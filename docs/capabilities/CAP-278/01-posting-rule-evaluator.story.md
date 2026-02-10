# Story: Implement PostingRuleEvaluator core service

## Description
Implement a core `PostingRuleEvaluator` service that accepts an `AccountingEvent` (or equivalent event request) and an optional `mappingVersionToUse`, then evaluates posting rules to produce a deterministic `PostingResult` containing either a balanced `JournalEntry` payload or a clear failure reason.

## Acceptance Criteria
- The service loads `PostingRuleVersion` (published) applicable for `organizationId` + `transactionDate`.
- Rule evaluation returns deterministic mapping (exact → fallback → category default).
- A `PostingResult` object is returned containing: success flag, journal entry DTO when success, failure reason code when not.
- Unit tests cover exact match, fallback match, category default, and no-match (unmapped) scenarios.

## Tasks
- [ ] Define `PostingResult` DTO (status, journalEntryDraft, failureReason, mappingVersionUsed)
- [ ] Implement `PostingRuleEvaluator` interface and default implementation
- [ ] Wire to existing `PostingRuleService` to query rule sets and versions
- [ ] Add unit tests for mapping selection logic

## Dependencies
- `PostingRuleService`, `PostingRuleSet`, `PostingRuleVersion` entities
- `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` guidance

## Notes for Agents
Follow domain conventions in `.github/agents/domains/accounting-domain.agent.md`. Ensure all returned journal entry drafts are balanced (debits == credits) in unit tests.