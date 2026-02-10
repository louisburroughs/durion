# Story: Integrate PostingRuleEvaluator with JournalEntryService (autoPost)

## Description
Integrate the `PostingRuleEvaluator` with `JournalEntryService` so that when a rule set indicates `autoPost=true` the generated journal entry is persisted and posted; when `autoPost=false` the engine returns a DRAFT journal entry for later manual posting.

## Acceptance Criteria
- When `autoPost=true`, `JournalEntryService.createAndPost()` (or equivalent) is called and returns a posting reference.
- The originating `AccountingEvent` is updated with `finalPostingReferenceId` and status `PROCESSED` on success.
- When `autoPost=false`, a `DRAFT` journal entry is returned (persisted as draft) and `AccountingEvent` remains `PROCESSING` or `SUSPENDED` per flow.
- Integration tests verify both auto-post and draft flows.

## Tasks
- [ ] Add `createAndPost` integration call in posting flow
- [ ] Persist journal entry draft when `autoPost=false`
- [ ] Update `AccountingEvent` fields on success/failure
- [ ] Add integration tests for both modes

## Dependencies
- `JournalEntryService` API
- `PostingRuleEvaluator` from Story 01

## Notes for Agents
Respect transaction boundaries and idempotency. Use existing audit trail patterns to record posting attempts.