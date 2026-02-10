# Story: Add idempotency checks to Posting Rule Engine

## Description
Ensure the posting engine is idempotent: repeated submissions of identical event content (content hash + org + source) must not create duplicate posted journal entries.

## Acceptance Criteria
- Engine consults `IdempotencyService` before creating/posting entries.
- If a previously-processed key exists, engine returns existing posting reference and does not create a new journal entry.
- Integration tests cover duplicate submission and concurrent submission scenarios.

## Tasks
- [ ] Define idempotency key format for posting engine
- [ ] Integrate `IdempotencyService` lookups/registrations into engine flow
- [ ] Implement tests for duplicate detection and concurrent race conditions

## Dependencies
- `IdempotencyService` in `pos-accounting`

## Notes for Agents
Follow existing idempotency TTL and key conventions in `EventIngestionService` and `InvoicePaymentService`.