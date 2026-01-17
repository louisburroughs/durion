Title: [BACKEND] [STORY] Dispatch: Import Mechanic Roster and Skills from HR
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/72
Labels: type:story, domain:people, status:ready-for-dev

## Story Intent
**As a** Dispatch/Shop Management backend,
**I want** to import and maintain the mechanic roster and mechanic skill/certification data from the Durion HR system,
**so that** dispatch decisions, work order assignments, and skill-based scheduling use the current authoritative roster and qualifications.

## Actors & Stakeholders
- **Durion HR System (SoR)**: authoritative source for employment status, roster, and certifications.
- **Shop Management / Dispatch Backend (this repo)**: owns the local read model used by dispatch/scheduling.
- **HR Administrator**: maintains roster/certifications in HR.
- **Dispatch Manager**: depends on accurate roster + skill data for assignments.

## Preconditions
- HR is the system of record for mechanic identity/status and skills/certifications.
- HR publishes events for roster and skill changes.
- This service can consume HR events and (optionally) call an HR REST API for reconciliation.

## Functional Behavior
### Primary integration: event-driven push (authoritative)
- HR publishes domain events when mechanic roster/skills change.
- This service consumes events and **upserts** mechanics and skills using `personId` as the external identity key.

**Supported event types (minimum):**
- `MechanicUpserted`
- `MechanicDeactivated`
- `MechanicSkillsUpdated`

**Event contract requirements (minimum):**
- `eventId` (UUID)
- `eventType`
- `personId` (string)
- Ordering field: `version` (int) **or** `effectiveAt` (timestamp)
- `occurredAt` (timestamp)
- `payload` containing mechanic details and skills (see payload strategy)

**Payload strategy (required for implementability):**
- Prefer **snapshot-style payloads** (not diffs):
  - `MechanicUpserted` includes full mechanic snapshot + full current skills list.
  - `MechanicSkillsUpdated` includes full current skills list.

### Monotonic update rule (ordering)
- Store `Mechanic.version` (preferred) or `Mechanic.lastEffectiveAt`.
- Processing:
  - If incoming ordering value is **<=** stored ordering value: **discard/no-op** (log as stale/out-of-order).
  - Else: apply update and persist ordering value.

### Idempotency / dedupe
- Consumers must be idempotent.
- Implement dedupe using both:
  1) ordering rule above, and
  2) `eventId` dedupe via an integration log table.

### Secondary integration: REST reconciliation/backfill (fallback only)
- A scheduled job (configurable cron; e.g., nightly) may call HR to reconcile ACTIVE mechanics.
- Example endpoint: `GET /hr/v1/mechanics?status=ACTIVE&modifiedSince=...` (or without `modifiedSince` if unsupported).

**Reconciliation rules:**
1. Fetch ACTIVE mechanics from HR.
2. Upsert each using the same monotonic ordering logic.
3. Any locally ACTIVE mechanic missing from the HR ACTIVE response is marked `INACTIVE`.

## Alternate / Error Flows
- **Malformed event** (missing `personId`, missing ordering field, unsupported schema version): log structured error, send to DLQ, emit alert.
- **Transient processing failure** (DB/network): retry via broker retry policy; DLQ only after retry exhaustion.
- **HR REST API unavailable during reconciliation**: exponential backoff retries; alert on final failure.

## Business Rules
- **BR1 (SoR):** HR is authoritative; this service must not provide local edit flows that contradict HR.
- **BR2 (Primary integration):** event-driven push is primary; REST is fallback/backfill only.
- **BR3 (Ordering):** only newer versions/effective timestamps may overwrite stored state.
- **BR4 (Idempotent):** reprocessing the same event must not create duplicates or corrupt state.
- **BR5 (Deactivation):** `MechanicDeactivated` sets `status=INACTIVE` (and `terminationDate` if provided); INACTIVE mechanics are excluded from new assignment queries.

## Data Requirements
### Mechanic (read model)
- `mechanicId` (UUID, PK)
- `personId` (string, unique; external upsert key)
- `firstName`, `lastName`
- `status` (Enum: `ACTIVE`, `INACTIVE`, `ON_LEAVE`)
- `hireDate`, `terminationDate?`
- `version` (int) or `lastEffectiveAt` (timestamp)
- `lastSyncedAt` (timestamp)

### MechanicSkill
- Unique constraint: `(mechanicId, skillCode)`
- Suggested fields: `skillCode`, `proficiencyLevel`, `certifiedDate`, `expirationDate?`
- Update semantics on snapshot events: **replace-set** (upsert present skills; remove missing skills or mark inactive—choose one implementation, default to hard delete unless history is required).

### Integration/audit tables
- `hr_integration_log(eventId PK, personId, eventType, receivedAt, processedAt, status, errorMessage, payloadHash)`
- `mechanic_audit_log(eventId, personId, eventType, before, after, appliedAt)` (immutable)

## Acceptance Criteria
- **AC1: New mechanic upsert** — consuming `MechanicUpserted` creates/updates the local `Mechanic` + skills; mechanic becomes available for dispatch queries.
- **AC2: Deactivation** — consuming `MechanicDeactivated` marks mechanic `INACTIVE` and excludes from new assignment queries.
- **AC3: Skills update** — consuming `MechanicSkillsUpdated` updates the mechanic’s skills so skill-based queries reflect the new snapshot.
- **AC4: Out-of-order handling** — an event with ordering value older/equal to stored value is discarded/no-op; stored record remains unchanged.
- **AC5: Malformed event** — missing required fields routes to DLQ and emits alert; no partial write occurs.
- **AC6: Reconciliation** — nightly reconciliation upserts ACTIVE roster and marks missing ACTIVE mechanics as `INACTIVE`.

## Audit & Observability
- Persist immutable audit entries for every applied change with `eventId`, `personId`, `eventType`, before/after, and timestamp.
- Metrics (minimum):
  - `hr_events_processed_total{type}`
  - `hr_events_dlq_total{type,reason}`
  - `hr_event_processing_latency_ms`
  - `hr_reconciliation_success_total`, `hr_reconciliation_failure_total`

## Open Questions
None.

## Traceability / Sources
- Clarification Issue #257 resolved the primary integration pattern: **event-driven push is definitive primary; REST pull is fallback/backfill only**.

## Original Story (Unmodified – For Traceability)
# Issue #72 — [BACKEND] [STORY] Dispatch: Import Mechanic Roster and Skills from HR

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Dispatch: Import Mechanic Roster and Skills from HR

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As the **Dispatch System**, I want to import the mechanic roster and skills from Durion-HR, so that assignments reflect current certifications and availability.

## Details
- Import roster: Name, status (active/inactive), skills, proficiency levels, cert dates.
- Either push (HR publishes events) or pull (scheduled or on-demand).

## Acceptance Criteria
- Mechanic data synchronized.
- Skills updated.
- Status changes reflected.

## Integrations
- Durion-HR is system-of-record; Dispatch/Shopmgr consumes.

## Data / Entities
- Mechanic, MechanicSkill, HRIntegrationLog

## Classification (confirm labels)
- Type: Story
- Layer: Integration
- Domain: Shop Management


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