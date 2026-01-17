Title: [BACKEND] [STORY] Integration: Submit Job Time to workexec as Labor Performed (Idempotent)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/81
Labels: type:story, domain:workexec, status:ready-for-dev

**Rewrite Variant:** integration-idempotent
**Status:** Ready-for-dev (clarification #402 applied)

## Story Intent
As the system, I want to idempotently submit a mechanic’s finalized job time (`TimeEntry`) to Work Execution (`workexec`) as `LaborPerformed`, so that billable labor is recorded against the correct Work Order without duplication.

## Actors & Stakeholders
- **System (People / Job-Time Capture)**: Tracks mechanic time entries and initiates submission to `workexec`.
- **Work Execution System (`pos-workexec`)**: Authoritative system of record for Work Orders and Labor Performed.
- **Mechanic/Technician**: Performs the work; finalization of time is the upstream trigger.

## Preconditions
1. A `TimeEntry` exists and has reached `FINALIZED`.
2. `timeEntryId` is unique, persistent, and safe for idempotency usage.
3. The associated WorkOrder exists in `workexec` and can be referenced by `workOrderId`.
4. Service-to-service authentication to `workexec` is configured.

## Functional Behavior
### Trigger + Delivery
- Trigger on `TimeEntry` transition to `FINALIZED`.
- Use **transactional outbox + async worker**:
  - Write an outbox/retry record in the same DB transaction as finalization.
  - Background worker delivers to `workexec` and applies retry/backoff.

### Submission (Workexec contract — resolved by clarification #402)
1. Build a `LaborPerformed` submission request mapped from `TimeEntry`.
2. Call:
   - `POST /api/workexec/labor-performed`
3. Always send required headers:
   - `Idempotency-Key: <timeEntryId>` (required)
   - `Content-Type: application/json`
   - `X-Correlation-Id` (optional; generated if missing)
4. Request payload (minimum canonical schema):

```json
{
  "workOrderId": "uuid",
  "technicianId": "uuid",
  "performedAt": "2026-01-15T17:30:00Z",
  "labor": {
    "quantity": 1.50,
    "unit": "HOURS"
  },
  "source": {
    "system": "people",
    "sourceReferenceId": "uuid"
  }
}
```

5. Interpret response (idempotency-aware):
   - `201 Created` → first successful record for the given idempotency key.
   - `200 OK` + `Idempotency-Replayed: true` → idempotent replay; treat as success.
   - `409 Conflict` → **never** replay; always terminal business conflict (see error taxonomy).

### Local Status Updates
- `FINALIZED` → `SUBMISSION_QUEUED` (optional but recommended)
- On success (`201` or `200` replay): `SUBMITTED_TO_WORKEXEC`
- On terminal failure: `SUBMISSION_FAILED`

## Alternate / Error Flows
### Terminal failures (no retry)
- Any `409` from this endpoint is terminal (mark `SUBMISSION_FAILED`, no retry).
- Validation/auth/not-found/business-invalid errors are terminal (see taxonomy below).

### Transient failures (retriable)
Retry for: HTTP `408`, `429`, `5xx`, and network timeouts/connection resets.

## Business Rules
### BR1: Idempotency
- All submissions MUST include `Idempotency-Key = timeEntryId`.
- Replay is signaled as **success**:
  - HTTP `200 OK`
  - Header `Idempotency-Replayed: true`
  - Response contains the existing `laborPerformedId` and canonical fields.

### BR2: External reference (traceability + conflict detection)
- `source.system = "people"`
- `source.sourceReferenceId = timeEntryId`
- Workexec enforces `UNIQUE(source.system, source.sourceReferenceId)`.

### BR3: Authoritative WorkOrder blocking states + retry policy (from Clarification #263)
- Workexec rejects labor submission when WorkOrder is in blocking states:
  - `CANCELLED`, `CLOSED`, `INVOICED`, `VOIDED`, `ARCHIVED`
  - `ON_HOLD`, `LOCKED_FOR_REVIEW`
  - `COMPLETED` (unless reopened)
- `REOPENED` (or any state returning to active execution) allows submissions again.

Retry classification:
- Retry only transient failures: HTTP `408`, `429`, `5xx`, network timeouts/connection resets.
- Do NOT retry terminal failures: HTTP `400`, `401`, `403`, `404`, `422`, and any `409` business conflict.
- Retry schedule:
  - Max attempts: 6 total (1 initial + 5 retries)
  - Backoff: exponential with jitter, base 250ms, cap 30s
  - After exhaustion: park in durable retry table/queue and alert if backlog age exceeds SLA (e.g., > 1 hour)

### BR4: Logging and redaction
- Log request/response with redaction (exclude credentials/tokens).
- Use structured logs keyed by `timeEntryId`, `workOrderId`, `attemptCount`, `statusCode`, `workexecErrorCode`.

## Data Requirements
### Outbox / Retry Record
- `timeEntryId` (idempotency key)
- `attemptCount`
- `nextAttemptAt`
- `lastStatusCode`
- `lastError`
- `lastResponseBody` (if safe/redacted)
- `createdAt`
- `alertedAt` (nullable)

### Submission Payload (minimum)
- `workOrderId`
- `technicianId`
- `performedAt` (ISO-8601)
- `labor.quantity` (decimal hours)
- `labor.unit` (`HOURS`)
- `source.system` (`people`)
- `source.sourceReferenceId` (`timeEntryId`)

## Acceptance Criteria
### AC1: Success path
- Given a finalized `TimeEntry`
- When it is delivered to `POST /api/workexec/labor-performed`
- Then workexec records `LaborPerformed`
- And local status becomes `SUBMITTED_TO_WORKEXEC`.

### AC2: Idempotent replay
- Given the same `timeEntryId` is submitted again with the same `Idempotency-Key`
- When workexec returns `200 OK` with header `Idempotency-Replayed: true`
- Then the system treats it as success and does not create duplicates.

### AC3: Blocking WorkOrder state
- Given WorkOrder is in a blocking state (per BR3)
- When submission is attempted
- Then workexec returns `409` with stable `WORKEXEC_CONFLICT_WORKORDER_STATE`
- And local status becomes `SUBMISSION_FAILED` and no retry occurs.

### AC4: Transient failure retry
- Given workexec returns a transient failure (`408`, `429`, `5xx`)
- When submission is retried with bounded backoff
- Then it retries up to 6 total attempts and parks with alerting on exhaustion.

## Audit & Observability
- Log each attempt with correlation id and idempotency key.
- Metrics:
  - success/failure counts by `workexec` error code
  - retry attempt counts
  - outbox/retry queue size and age
  - call latency

## Resolved Questions
From clarification #402:
- Endpoint: `POST /api/workexec/labor-performed`
- Idempotency:
  - Required `Idempotency-Key: timeEntryId`
  - Replay returns `200` + `Idempotency-Replayed: true` (treat as success)
  - First-time returns `201`
  - `409` is never replay; always terminal business conflict with stable `WORKEXEC_CONFLICT_*` codes
- External reference supported via `source.sourceReferenceId` and uniqueness enforced per `source.system`.
- Stable error envelope + terminal vs retriable classification defined.

From clarification #263:
- Blocking workorder states and retry policy.

## Original Story (Unmodified – For Traceability)
# Issue #81 — [BACKEND] [STORY] Integration: Submit Job Time to workexec as Labor Performed (Idempotent)

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Integration: Submit Job Time to workexec as Labor Performed (Idempotent)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Mechanic**, I want **to submit my job time to workexec** so that **labor performed lines are created/updated accurately**.

## Details
- Idempotent posting using timeEntryId as reference.
- Reject with actionable errors when workorder state disallows.

## Acceptance Criteria
- Posting creates or updates labor-performed without duplication.
- Failures provide stable error codes.
- Audit includes workorder reference.

## Integration Points (workexec)
- Outbound: JobTimePosted event and/or API call.

## Data / Entities
- LaborPerformed (workexec)
- TimeEntry (job)

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: People Management


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