# Story: Persist ReprocessingAttemptHistory and Audit Trail for Posting Attempts

## Description
Persist detailed `ReprocessingAttemptHistory` records and audit trail entries for each posting attempt performed by the engine (success and failure) to satisfy compliance and traceability requirements.

## Acceptance Criteria
- Each engine attempt creates a `ReprocessingAttemptHistory` entry with attemptedAt, outcome, mappingVersionUsed, triggeredByUserId, and outcomeDetails.
- Audit trail entries are written describing rule evaluation, selected mappings, and any errors (truncated stacktrace/details allowed).
- Queries exist to fetch attempt history by `eventId` ordered most-recent-first.

## Tasks
- [ ] Extend or reuse `ReprocessingAttemptHistoryRepository` and mapper
- [ ] Instrument engine to write attempt history at start and update outcome on completion
- [ ] Ensure audit entries are created for both success and failure
- [ ] Add integration tests validating persisted records

## Dependencies
- `ReprocessingAttemptHistoryRepository`
- `AuditTrailEntryRepository`

## Notes for Agents
Follow data retention rules in CAP-055: keep processed attempts for 90 days, suspended for 365 days (or update manifest if policy changes).