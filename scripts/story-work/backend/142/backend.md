Title: [BACKEND] [STORY] Events: Implement Idempotency and Deduplication
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/142
Labels: general, type:story, domain:accounting, status:ready-for-dev

**Rewrite Variant:** events-idempotency-audit
**Status:** Ready-for-dev (clarification #401 applied)

## Story Intent
As a **System Auditor**, I want the Accounting Event Ingestion service to be idempotent, so that events replayed due to network/client/broker redelivery do not create duplicate financial entries in the General Ledger (GL), ensuring data integrity and auditability.

## Actors & Stakeholders
- **Event Ingestion Service**: Receives accounting events, enforces idempotency/dedup, and triggers GL-impacting postings.
- **Source Systems / Producers**: Internal or external systems that generate accounting-relevant events.
- **Finance / Accounting Ops**: Own exception handling for idempotency conflicts and audit investigations.
- **System Auditors**: Verify integrity of financial transaction processing.
- **SRE / Support**: Assist in operational triage (DLQ, alerts, infra).

## Preconditions
- Event ingestion endpoint(s) exist and are secured.
- An idempotency store exists (primary DB table + archival storage tier).
- Source systems generate a unique `eventId` for each distinct business event.

## Functional Behavior
1) **Event ingestion + idempotency lookup**
- Service receives an accounting event with an `eventId` (idempotency key) and a domain payload.
- Service computes a canonical domain-payload hash (see Business Rules) and checks the idempotency store.

2) **New event processing (happy path)**
- If no record exists for `eventId`, create an idempotency record with status `PROCESSING`.
- Process the event and perform GL-impacting actions.
- On success: update idempotency record to `COMPLETED` and store response/GL reference.
- Return success (e.g., `201 Created` for HTTP ingestion, or commit offset/ack for broker ingestion).

3) **Replay (idempotent success)**
- If record exists for `eventId` with status `COMPLETED` and hashes match, do not re-process.
- Return the stored response (HTTP `200 OK`), or ack/commit in broker ingestion.

4) **Retry after failure**
- If record exists with status `FAILED` and hashes match, retry processing per the service’s retry policy.

5) **Conflicting duplicate (same `eventId`, different business facts)**
- If record exists for `eventId` but the incoming hash differs, treat as a high-severity **integrity incident**.
- Service must:
  1. Persist a conflict record (table-backed exceptions queue)
  2. Publish a conflict message to DLQ
  3. Emit CRITICAL alert/metric
  4. ACK/commit the original message/request to avoid poison-message hot loops

## Alternate / Error Flows
- **Concurrent first-write race**: If two requests arrive simultaneously, DB uniqueness on `eventId` prevents duplicate idempotency records; the loser re-reads the winning record and follows replay/processing behavior.
- **Conflict detected**: Return `409 Conflict` for HTTP ingestion; for broker ingestion, publish to DLQ + ack/commit (no re-drive loop).
- **Archival unavailable**: Do not fail ingestion solely due to archival/tiering outage; continue writing to hot store and emit a warning/metric (archival is eventual). (Retention still must be met once storage recovers.)

## Business Rules
### BR1: Idempotency key
- `eventId` is the sole idempotency key.

### BR2: Conflict handling (authoritative, from clarification #401)
- **DLQ is required** for conflicting duplicates.
- After persisting conflict + publishing to DLQ + emitting CRITICAL alert/metric, the consumer **ACKs/commits** the original message/request.
- Conflict resolution is an **Accounting Ops** workflow (exceptions queue), not an automated overwrite.

Conflict record state machine:
- `OPEN` → `TRIAGED` → terminal:
  - `RESOLVED_ACCEPT_ORIGINAL`
  - `RESOLVED_ACCEPT_NEW` (only via compensating/reversal accounting events; never overwrite original)
  - `RESOLVED_INVALID_PRODUCER`

### BR3: Payload hashing scope (authoritative, from clarification #401)
- Hash **domain payload only** (business event content), not transport/broker envelope.
- Canonicalization: canonical JSON normalization (sorted keys, stable number/string formatting, no whitespace).
- Exclude from hash only transport/observability volatility (explicit examples):
  - `correlationId`, `traceId`, `spanId`, `receivedAt`, `deliveryAttempt`, `retryCount`, `partition`, `offset`, `brokerMessageId`, `producerSendAt`
  - `meta.*` blocks explicitly documented as non-semantic observability metadata
- Include in hash: `eventType` and all business-relevant fields, including business timestamps that affect accounting semantics (e.g., `eventOccurredAt`, `postingDate`, `effectiveDate`).

### BR4: Retention (authoritative, from clarification #401)
- **GL-impacting idempotency records** retained for **≥ 7 years** (configurable higher; must not be set lower).
- **Conflict records** retained for **≥ 7 years**.
- Tiering default: keep **hot** in primary DB for **400 days**, then archive to cold storage until retention expiry.
- Deletion only after retention expiry; deletion must be logged/audited.

## Data Requirements
### Incoming Event (minimum)
- `eventId`: UUID (required)
- `eventType`: string (required)
- `payload`: JSON (required; domain payload)
- Optional transport metadata (excluded from hash as above)

### Idempotency store (hot tier)
- `eventId` (PK)
- `domainPayloadHash` (SHA-256 hex)
- `status` (`PROCESSING`, `COMPLETED`, `FAILED`)
- `responsePayload` (JSON)
- `glPostingReference` (string)
- `firstSeenAt`, `lastSeenAt`
- `archivedAt` (nullable)

### Conflict store (exceptions queue)
- `conflictId` (PK)
- `eventId`
- `originalDomainPayloadHash`
- `conflictingDomainPayloadHash`
- `conflictState` (`OPEN`, `TRIAGED`, `RESOLVED_ACCEPT_ORIGINAL`, `RESOLVED_ACCEPT_NEW`, `RESOLVED_INVALID_PRODUCER`)
- `flaggedAt`, `flaggedBy`
- `resolutionNotes` (nullable)
- `dlqMessageRef` (nullable)

### DLQ message
- Contains original event + conflict metadata + a link/reference to `conflictId`.

## Acceptance Criteria
### Scenario 1: New unique event processed
- Given no idempotency record exists for `eventId=ABC-123`
- When an event with `eventId=ABC-123` is ingested
- Then the event is processed and GL posting occurs once
- And the idempotency record is `COMPLETED`

### Scenario 2: Replay returns cached result
- Given `eventId=ABC-123` is `COMPLETED` and incoming domain hash matches
- When the same event is ingested again
- Then no new GL posting is created
- And the stored response is returned (or message is ACKed/committed as replay)

### Scenario 3: Conflicting duplicate is escalated and does not hot-loop
- Given an existing idempotency record for `eventId=XYZ-789`
- When an event with `eventId=XYZ-789` arrives with a different domain hash
- Then a conflict record is created (`OPEN`)
- And a DLQ message is published referencing the conflict record
- And CRITICAL alert/metric is emitted
- And the original message/request is ACKed/committed (no poison-message retry loop)

### Scenario 4: Retention + tiering policy enforced
- Given idempotency/conflict records are GL-impacting
- Then retention is configurable but never below 7 years
- And hot storage keeps records for ~400 days before archiving

## Audit & Observability
- Metrics:
  - `events.processed.count{status=new|replay|failed|conflict}`
  - `events.conflicts.count` (CRITICAL alert on >0)
  - `events.processing.latency`
  - `events.archive.backlog` / `events.archive.failures`
- Logging:
  - `INFO` new/replay
  - `WARN` failed eligible for retry
  - `ERROR` conflict detected (include `eventId`, `conflictId`, hashes)

## Resolved Questions
From clarification #401:
- Conflicts: DLQ required; persist conflict + alert; ACK/commit after recording/publishing; Accounting Ops resolves via exceptions workflow.
- Hashing: hash canonical domain payload only; exclude only transport/observability volatile metadata.
- Retention: idempotency + conflict records retained ≥ 7 years, with 400-day hot + archive default.

## Original Story (Unmodified – For Traceability)
# Issue #142 — [BACKEND] [STORY] Events: Implement Idempotency and Deduplication

## Current Labels
- backend
- story-implementation
- general

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Events: Implement Idempotency and Deduplication

**Domain**: general

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Ingest Accounting Events (Cross-Module)

## Story
Events: Implement Idempotency and Deduplication

## Acceptance Criteria
- [ ] Duplicate submissions of same eventId are detected and do not create duplicate GL impact
- [ ] Conflicting duplicates (same eventId, different payload) are rejected and flagged
- [ ] Replays return the prior posting reference when already posted
- [ ] Retry workflow exists for failed events without duplicating postings


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