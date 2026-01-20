Title: [BACKEND] [STORY] Events: Receive Events via Queue and/or Service Endpoint
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/143
Labels: type:story, domain:accounting, status:ready-for-dev

**Rewrite Variant:** events-ingestion-contract
**Status:** Ready-for-dev (clarification #400 applied)

## Story Intent
As the **Accounting Ingestion Service**, I want to provide reliable synchronous (API) and asynchronous (broker) ingestion endpoints for producing modules to submit accounting-relevant events, so that all financial data is captured immutably and traceably in a single, authoritative location for subsequent processing and auditing.

## Actors & Stakeholders
- **Accounting Ingestion Service**: Receives events, validates contract + auth, persists immutably.
- **Producing Modules (service principals)**: Publish accounting events (e.g., Sales, Inventory, Billing).
- **Accounting Ops / Finance**: Investigate ingestion conflicts and audit events.
- **Platform Engineering / SRE**: Operate brokers, DLQs, and alerts.

## Preconditions
1. Raw event storage schema is provisioned.
2. Broker infrastructure is provisioned and accessible.
3. Service-to-service authentication is configured for sync endpoint.

## Functional Behavior
### 1) Canonical event contract (independent of transport)
- The ingestion contract is **transport-independent** (applies to REST and broker ingestion).
- Transport adapters vary by broker; the envelope and validation rules do not.

### 2) Broker stance
- **Multi-broker pluggable** ingestion.
- Default/reference transport in this repo: **Kafka** (topic-based).
- **SQS supported** as an alternative transport adapter.

### 3) Canonical envelope (Option B) — resolved by clarification #400
Required fields:
- `specVersion` (required) e.g. `"durion.accounting.ingestion.v1"`
- `eventId` (required, UUIDv7)
- `eventType` (required, enumerated string)
- `sourceModule` (required, canonical producer/module identifier)
- `eventTimestamp` (required; when business event occurred)
- `schemaVersion` (required; payload schema version for this eventType)
- `payload` (required; domain payload only)

Optional fields:
- `correlationId` (optional; recommended; required only if producer already has one)

Notes:
- Broker/transport receipt metadata (topic, partition, offset, deliveryAttempt, receivedAt, etc.) is **receipt metadata** and is not part of the envelope.

### 4) Event ID authority
- **Producer generates `eventId`**.
- Requirements:
  - `eventId` MUST be UUIDv7
  - `eventId` MUST be globally unique per producer
- Exception (sync REST only): if a producer cannot generate UUIDv7, it may call the sync REST endpoint without `eventId` and the ingestion service may generate one for that sync path only.

### 5) Idempotency + duplicate handling
- Idempotency key: **`eventId`**.
- Conflict detection: compute a payload hash and compare.

Rules:
- **Replay** = same `eventId` + same payload hash → idempotent success.
- **Conflict** = same `eventId` + different payload hash → reject + flag for investigation.

Sync HTTP behavior:
- New event accepted → `202 Accepted` with acknowledgement including at least `eventId`, `status`, `receivedAt`.
- Replay → `200 OK` returning the original acknowledgement.
- Conflict → `409 Conflict` with stable error code `INGESTION_DUPLICATE_CONFLICT`.

Queue/broker behavior:
- Replay → ACK/commit and emit replay metric.
- Conflict → ACK/commit **after** recording conflict + alerting (aligns with #142 conflict handling stance).

### 6) Auth contract
- Required scope: `SCOPE_accounting:events:ingest`
- Producer identity binding:
  - `sourceModule` MUST match the authenticated service principal mapping (`client_id` → module name).
  - If mismatched: `403 Forbidden` with error code `INGESTION_FORBIDDEN_SOURCE_MISMATCH`.

## Alternate / Error Flows
- **Validation failure** (missing/invalid envelope fields): `400` `INGESTION_VALIDATION_FAILED`.
- **Unsupported schema** (unknown `schemaVersion` for `eventType`): `422` `INGESTION_SCHEMA_UNSUPPORTED`.
- **Dependency failure** (storage unavailable): `503` `INGESTION_DEPENDENCY_FAILURE`.

## Business Rules
- Envelope is canonical and transport-independent.
- Idempotency uses `eventId` only (not `(sourceModule,eventId)`), while auth enforces producer identity.

## Data Requirements
- Store raw envelope + receipt metadata immutably.
- Persist an acknowledgement/receipt record addressable by `eventId` to support `200` replay responses.

## Acceptance Criteria
- Sync API exists and accepts valid envelopes, returning `202` with acknowledgement.
- Broker consumer exists (Kafka default) and persists events before ACK/commit.
- Replays do not create duplicates and are treated as success.
- Conflicts are rejected/flagged and do not create poison-message hot loops.
- Auth enforces `SCOPE_accounting:events:ingest` and `sourceModule` matches authenticated principal mapping.

## Audit & Observability
- Metrics tagged by outcome:
  - accepted/new
  - replay (`INGESTION_DUPLICATE_REPLAY` as status)
  - conflict (`INGESTION_DUPLICATE_CONFLICT`)
  - validation/schema/dependency failures
- Logs include `eventId`, `eventType`, `sourceModule`, `correlationId` (if present).

## Resolved Questions
From clarification #400:
- Broker: multi-broker pluggable; Kafka default; SQS adapter supported.
- Event ID: producer-generated UUIDv7 (sync exception allowed only if producer cannot generate).
- Envelope: Option B canonical fields + receipt metadata excluded.
- Idempotency: `eventId` key + payload-hash conflict detection; explicit HTTP + queue semantics.
- Auth: required scope + `sourceModule` must match authenticated principal mapping.
- Stable error taxonomy adopted.

## Original Story (Unmodified – For Traceability)
# Issue #143 — [BACKEND] [STORY] Events: Receive Events via Queue and/or Service Endpoint

## Current Labels
- backend
- story-implementation
- general

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Events: Receive Events via Queue and/or Service Endpoint

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
Events: Receive Events via Queue and/or Service Endpoint

## Acceptance Criteria
- [ ] Provide a synchronous ingestion API endpoint for producing modules
- [ ] Provide an async ingestion channel (queue/topic) where configured
- [ ] Received events are persisted immutably before mapping/posting
- [ ] System returns acknowledgement with eventId and initial status


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