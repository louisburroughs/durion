# Story: Integration tests for Posting Rule Engine and reprocessing flow

## Description
Create integration tests that exercise end-to-end behavior of the posting rule engine integrated into `EventIngestionService` including successful auto-posting, suspended unmapped events, and concurrent reprocessing attempts to validate optimistic locking and idempotency.

## Acceptance Criteria
- Integration test for happy path: incoming event mapped, journal entry posted, `AccountingEvent` set to `PROCESSED`, `finalPostingReferenceId` populated
- Integration test for unmapped event: engine returns no match, event set to `SUSPENDED` with `UNMAPPED_EVENT_TYPE`
- Integration test for concurrent reprocess: two concurrent reprocess requests do not create duplicate posted entries; one succeeds, the other fails with a retryable/optimistic locking notice
- Tests run under `pos-accounting` integration profile and are stable reproducible

## Tasks
- [ ] Add test data fixtures for PostingRuleSet and PostingRuleVersion
- [ ] Implement happy path integration test
- [ ] Implement unmapped suspension test
- [ ] Implement concurrent reprocess test (use threads or test support utilities)

## Dependencies
- Running test profile and in-memory DB (H2) or testcontainers if available

## Notes for Agents
Follow testing patterns used in existing `*ContractBehaviorIT` files and ensure tests are isolated and idempotent.