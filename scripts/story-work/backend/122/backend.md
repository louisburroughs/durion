Title: [BACKEND] [STORY] Controls: Route Unmapped or Failed Events to Suspense Queue and Reprocess
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/122
Labels: type:story, domain:accounting, status:ready-for-dev, agent:story-authoring, agent:accounting

## Story Intent
**As an** Accountant or System Administrator,
**I want** unmapped / business-rule posting failures to be routed into an accounting-owned suspense queue,
**so that** failures can be investigated and safely reprocessed to protect financial completeness and integrity.

## Actors & Stakeholders
- **Accountant:** Resolves accounting-rule/mapping failures and validates final outcomes.
- **System Administrator:** Investigates technical causes and triggers reprocessing (where authorized).
- **Event Ingestion / Posting Pipeline:** Detects failures and creates suspense entries.
- **General Ledger Posting (Accounting):** Downstream posting consumer for successful reprocessing.

## Preconditions
- An event ingestion + accounting posting pipeline exists.
- Accounting mapping / posting rules exist and are externally correctable (rule correction mechanism is out of scope here).
- Users are authenticated and authorized to view and reprocess suspense entries.

## Functional Behavior
### 1) Detect and Suspend (Accounting controls)
1. **Trigger:** The ingestion/posting pipeline processes an incoming event and encounters a recoverable **business-rule/mapping** failure.
2. **Classification:** Assign a structured `failure_reason_code` (e.g., `UNMAPPED_EVENT_TYPE`, `INVALID_MAPPING_VERSION`, `RULE_CONFLICT`).
3. **Persist suspense entry:**
   - MUST NOT discard the event.
   - MUST create a suspense entry with immutable original event payload (or immutable reference), failure code/details, timestamps, attempt counters, and status `SUSPENDED`.
   - SHOULD record accounting-relevant context (e.g., event type, intended posting intent, mapping/rule version attempted).

### 2) Reprocess (Manual or automated trigger)
1. **Trigger:** An authorized user (or an automated job) requests reprocessing for a specific suspense entry.
2. **Attempt logging:**
   - Increment `attempt_count` and write a reprocessing-attempt history record with timestamp + actor.
3. **Re-run logic:** Re-run mapping/posting using **current** rules by default.
4. **On success:**
   - Post successfully to the downstream ledger/posting component.
   - Update suspense entry status to `PROCESSED`.
   - Store `final_posting_reference_id` and `processed_at/resolved_at` for permanent traceability.
5. **On failure:**
   - Keep status `SUSPENDED` (or optionally move to `FAILED` after policy threshold).
   - Update latest error details + timestamps.

## Alternate / Error Flows
- **Reprocess terminal entry:** If reprocess is requested for a `PROCESSED` (or other terminal) entry, reject with `409 Conflict` and do not create duplicate postings.
- **Unauthorized access:** If the caller lacks permission to reprocess, reject with `403 Forbidden`.

## Business Rules
- **BR-1 (Domain ownership):** This suspense queue is an **accounting controls** mechanism (business-rule suspense), not a generic `workexec` DLQ.
- **BR-2 (Payload immutability):** Original event payload/reference in suspense is immutable.
- **BR-3 (Idempotency):** Reprocessing MUST be idempotent; a single suspense entry can produce at most one successful downstream posting.
- **BR-4 (Auditability):** Maintain an indelible history of reprocess attempts (who/when/outcome/details).

## Data Requirements
### Suspense Entry
- `suspense_entry_id` (UUID, PK)
- `original_event_payload` (JSONB/TEXT) or `original_event_ref` (immutable reference)
- `status` (ENUM: `SUSPENDED`, `PROCESSED`, optionally `FAILED`)
- `failure_reason_code` (TEXT/ENUM)
- `failure_details` (TEXT)
- `event_type` (TEXT)
- `mapping_version_attempted` (TEXT, nullable)
- `created_at`, `updated_at`, `processed_at` (timestamps)
- `attempt_count` (int)
- `final_posting_reference_id` (TEXT, nullable)
- `resolved_by_user_id` (UUID/TEXT, nullable)

### Reprocessing Attempt History
- `attempt_id` (PK)
- `suspense_entry_id` (FK)
- `attempted_at` (timestamp)
- `triggered_by_user_id` (UUID/TEXT)
- `outcome` (`SUCCESS` | `FAILURE`)
- `outcome_details` (TEXT)

## Retention & Purge (Resolved)
- `PROCESSED`: retain 90 days, then purge eligible.
- `SUSPENDED`: retain 365 days (or until processed), then purge eligible.
- `FAILED` (if used): retain 365 days, then purge eligible.
- Implement a scheduled cleanup job (e.g., daily) to delete by status + age.

## Acceptance Criteria
- **AC-1: Mapping failure creates suspense entry**
  - Given an incoming event that cannot be mapped/posted due to accounting-rule/mapping failure
  - When processed
  - Then a suspense entry is created as `SUSPENDED` with `failure_reason_code` and immutable original payload/reference

- **AC-2: Successful reprocess posts and closes entry**
  - Given a `SUSPENDED` entry and the underlying mapping/rule is corrected externally
  - When an authorized user triggers reprocessing
  - Then posting succeeds, the entry becomes `PROCESSED`, and `final_posting_reference_id` is stored

- **AC-3: Idempotent reprocess**
  - Given an entry is `PROCESSED`
  - When reprocess is requested again
  - Then the system returns `409 Conflict` and makes no duplicate posting

- **AC-4: Attempt history maintained**
  - Given reprocess is attempted
  - When it succeeds or fails
  - Then a history record is stored with timestamp, actor, outcome, and details, and `attempt_count` is incremented

## Audit & Observability
- Structured audit log for suspense state changes and reprocess attempts.
- Metrics: created count by `failure_reason_code`, reprocess attempts by outcome, active suspended gauge, and alerting on sustained growth.

## Open Questions
None. (Resolved in decision comment generated by `clarification-resolver.sh` on 2026-01-14.)

## Original Story (Unmodified – For Traceability)
# Issue #122 — [BACKEND] [STORY] Controls: Route Unmapped or Failed Events to Suspense Queue and Reprocess

## Current Labels
- backend
- story-implementation
- admin

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Controls: Route Unmapped or Failed Events to Suspense Queue and Reprocess

**Domain**: admin

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Reconciliation, Audit, and Controls

## Story
Controls: Route Unmapped or Failed Events to Suspense Queue and Reprocess

## Acceptance Criteria
- [ ] Unmapped/failed events go to Suspense with actionable missing-key details
- [ ] Admin can correct mapping/rules and reprocess
- [ ] Reprocess is idempotent (no duplicate postings)
- [ ] Attempt history and final posting references are retained


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