# CAP-056 Backend Implementation Plan

## Capability Summary
**CAP-056: Posting Rule Engine — Event Mapping & Journal Posting**

Provide a deterministic, auditable engine that evaluates posting rule sets against incoming business events and produces balanced journal entries which may be auto-posted to the GL.

## Capability Definition

### Description
What must be true for this capability to be considered complete?

- An engine exists that loads the active `PostingRuleSet` (by `organizationId` + `transactionDate`) and evaluates rules against an incoming event `payload`.
- For matched rules the engine deterministically generates a balanced `JournalEntry` (debits/credits) and returns a posting reference.
- When a rule set has `autoPost` enabled, the engine posts the generated `JournalEntry` via `JournalEntryService` and records the resulting reference on the originating `AccountingEvent`.
- The engine is idempotent (prevents duplicate postings for the same event content) and integrates with the `EventIngestionService` `reprocessEvent()` flow (replacing `attemptReprocessingLogic`).
- The engine writes audit trail entries, reprocessing attempt history, and exposes metrics for success/failure rates.

### Business Rules
- Only `PUBLISHED` rule versions are used for evaluation.
- Rule sets are effective-dated: a rule version must be effective on the event transaction date.
- Mapping selection is deterministic: exact dimension matches preferred, then fallbacks, then category default.
- Idempotency: the same event (content hash + org + source) must never produce multiple posted journal entries.
- If no mapping is found the event is marked `SUSPENDED` and a reason code such as `UNMAPPED_EVENT_TYPE` is recorded.
- Posting may be performed synchronously (auto-post) or produce a `DRAFT` journal entry for manual review depending on rule set settings.
- Reprocessing must be idempotent and guarded by optimistic locking to prevent duplicate postings from concurrent retries.

### Inputs
- Business event map containing: `eventType`, `organizationId`, `sourceSystem`, `transactionDate`, `payload` and optional `mappingVersionToUse` and `eventId`.
- `PostingRuleSet` and `PostingRuleVersion` data (rules, mappings, `autoPost` flag, effective dating)
- `JournalEntryService` API for entry creation/posting
- Idempotency service keys (content hash)

### Outputs
- Created `JournalEntry` (DRAFT or POSTED) with a unique posting reference
- Updated `AccountingEvent` status (`PROCESSED` / `SUSPENDED` / `FAILED`) and `finalPostingReferenceId` where appropriate
- `ReprocessingAttemptHistory` records and audit trail entries linked to the `AccountingEvent`
- Metrics (counters/gauges) for processed, suspended, failed, and reprocessing attempts

### Edge Cases
- Event payload invalid or schema mismatch → Validation error, event `FAILED` or rejected with clear reason
- No rule matches → mark `SUSPENDED`, store failure reason `UNMAPPED_EVENT_TYPE`
- Partial mapping (some dimensions map, others missing) → treat as mapping failure unless rule allows partial aggregation; log and suspend
- Concurrent reprocessing attempts → optimistic locking prevents duplicate postings; last writer wins pattern with clear error returned to caller
- Currency mismatch between event and GL/accounting expectations → reject or convert per configured policy (out of scope until policy defined)
- Rule evaluation throws runtime exception → mark `SUSPENDED` and record stacktrace/summary in attempt history

### Acceptance Criteria
- [ ] Engine loads the correct `PostingRuleVersion` for `organizationId` + `transactionDate` and respects effective dating
- [ ] Engine evaluates rules and returns deterministic mapping choices (exact → fallback → category default)
- [ ] For a matched mapping, engine produces a balanced `JournalEntry` (debits == credits)
- [ ] When `autoPost` is true, `JournalEntryService` is called to post and the `AccountingEvent` is updated to `PROCESSED` with `finalPostingReferenceId`
- [ ] Engine writes `ReprocessingAttemptHistory` and audit entries for each attempt
- [ ] Idempotency: repeated submissions of the same event do not create multiple posted journal entries
- [ ] Unit and integration tests cover successful posting, unmapped events (suspend), exception handling, and optimistic locking during concurrent reprocess attempts
- [ ] Integration: `EventIngestionService.attemptReprocessingLogic()` is replaced or integrated to call the new engine

### Decomposes Into (Stories)
- [ ] Story: Implement `PostingRuleEvaluator` core service that accepts an `AccountingEvent` + optional `mappingVersionToUse` and returns a `PostingResult` (success/failure, journal entry or reason)
- [ ] Story: Integrate `PostingRuleEvaluator` with `JournalEntryService` for entry creation and posting; implement `autoPost` behavior
- [ ] Story: Add idempotency checks (content hash integration) and ensure engine returns idempotent results
- [ ] Story: Persist `ReprocessingAttemptHistory` and create audit trail entries for mapping/posting attempts
- [ ] Story: Add metrics (Prometheus) and health checks for posting engine
- [ ] Story: Replace `EventIngestionService.attemptReprocessingLogic()` with engine call and update `reprocessEvent()` flow
- [ ] Story: Create integration tests for happy path posting, unmapped suspension, and concurrent reprocessing scenarios

## Notes for Agents
- Start from existing `PostingRuleService` / `PostingRuleSet` / `PostingRuleVersion` entities and `PostingRuleService` interface already present in the codebase.
- Follow CAP-055 notes (docs/capabilities/CAP-055) which describe the suspense queue and reprocessing design.
- Add an ApplicationRunner if any boot-time registration or cache warm-up is needed for rule metadata.
- Ensure `@EmitEvent` usage remains consistent for any controller endpoints that will trigger posting operations.
- Use the existing `IdempotencyService` for deduplication; update TTLs and key formats only if required and documented.
- Consider exposing a synchronous `POST /v1/accounting/events/{id}/reprocess` endpoint for manual reprocessing, and a scheduled job for periodic retries of failed events.
- Update `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` with new contract entries and examples once implementation stabilizes.

## Classification (confirm labels)
- Type: capability
- Layer: backend
- Domain: accounting

---

## Cross-References
- Source TODOs: `pos-accounting/src/main/java/com/positivity/accounting/service/EventIngestionService.java` (TODOs marked `[CAP-056]`)
- Related capability: CAP-055 (Suspense Queue & Reprocessing) — see `docs/capabilities/CAP-055/CAP-055-backend-implementation.md`

