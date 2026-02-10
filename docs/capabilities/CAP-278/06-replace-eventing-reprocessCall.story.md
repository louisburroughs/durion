# Story: Replace `EventIngestionService.attemptReprocessingLogic()` with Posting Engine call

## Description
Replace the placeholder `attemptReprocessingLogic()` implementation in `EventIngestionService` with a call into the new Posting Rule Engine (or adapter). Ensure the `reprocessEvent()` flow uses the engine, updates attempt history, and respects optimistic locking.

## Acceptance Criteria
- `EventIngestionService.reprocessEvent()` delegates to the Posting Engine for mapping/posting
- The engine result is handled consistently: success => `PROCESSED` + postingRef, failure => `SUSPENDED` + failure details
- Optimistic locking behavior preserved and tested with concurrent reprocess attempts
- Unit/integration tests validate the replacement behavior

## Tasks
- [ ] Implement adapter call from `EventIngestionService` to engine
- [ ] Remove simulation code and add clear TODO removal
- [ ] Add tests for success, failure, and optimistic locking cases

## Dependencies
- `PostingRuleEvaluator` implementation
- Existing `reprocessEvent` tests and `ReprocessingAttemptHistory` logic

## Notes for Agents
Keep changes minimal and localized in `EventIngestionService` to ease review; update CAP-055 docs to reference engine integration (CAP-278).