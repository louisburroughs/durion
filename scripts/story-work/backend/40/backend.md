Title: [BACKEND] [STORY] Topology: Sync Locations from durion-hr
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/40
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory, layer:domain

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:ready-for-dev

### Recommended
- agent:inventory
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** inventory-flexible

## Story Intent
Keep the system-of-record for physical locations (shops, mobile sites) synchronized from the authoritative HR topology source (`durion-hr`) so inventory is always scoped and validated against a current roster of active locations.

## Actors & Stakeholders
- `durion-hr` service (authoritative source of locations)
- Inventory service (primary consumer; owns local `LocationRef` and enforces location-level constraints)
- Service Advisors / Warehouse Staff (use locations in stock movements)
- Finance/Operations (rely on accurate location scoping for reporting)
- SRE/Operations (monitor sync health)

## Preconditions
- `durion-hr` exposes either a roster API and/or emits location lifecycle events (create/update/deactivate) that can be consumed.
- Inventory service has database and permissions to write `LocationRef` records.
- An established correlation/identity mapping exists between HR location identifiers and Inventory location references.

## Functional Behavior
1. Source-of-truth: `durion-hr` is authoritative for location identity, name, status, timezone, and tags.
2. Sync Modes (both supported):
   - Event-driven (preferred): `durion-hr` publishes location events (LocationCreated/LocationUpdated/LocationDeactivated) which Inventory consumes. Inventory processes events idempotently and updates local `LocationRef`.
   - Bulk/REST sync: Inventory calls HR roster API to reconcile full or incremental roster on schedule or on-demand.
3. Idempotent Upsert: For each incoming record/event, Inventory upserts a `LocationRef` by `hrLocationId` within tenant scope:
   - If not present -> INSERT with provided fields.
   - If present -> UPDATE changed fields and bump `version`.
   - Always write an immutable `SyncLog` entry recording payload, source event id, and outcome.
4. Deactivation semantics (resolved):
   - On HR deactivation, Inventory marks `LocationRef.is_active=false`, records `deactivated_at`, `deactivated_by`, and links `hr_event_id`.
   - Inventory prevents **new inbound** stock movements to that location.
   - Remaining on-hand quantities are marked as **`PendingTransfer`** (derived state) for manual reconciliation; no automatic transfers in v1.
   - Reconciliation actions (manual, authorized roles only): transfer-out, adjust/write-off, dispose/RTV.
   - Default reconciliation SLA: **5 business days** before escalation.
5. Identity mapping (resolved):
   - `hrLocationId` is unique within a tenant. Use composite key: `tenantId + ":" + hrLocationId` (or unique constraint on `(tenant_id, hr_location_id)`).
6. Reconciliation job and FK integrity: A periodic reconciliation job compares HR roster to local `LocationRef` and emits alerts for drift. Orders and transactions reference `LocationRef.id` (not raw HR id) to maintain FK integrity.
7. Backpressure & retries: Event consumption uses at-least-once delivery with idempotency on `hr_event_id`, exponential backoff with jitter, max retries 10 over ~30 minutes, and DLQ/escalation on repeated failures.

## Alternate / Error Flows
- Missing HR field: If an incoming record lacks required fields, record `SyncLog` with `INVALID_PAYLOAD` and flag for manual review; do not overwrite existing fields with nulls.
- Transient failures: Retry with exponential backoff; after retries exhausted, write `SyncFailure`, send to DLQ/quarantine, and alert.
- Unknown HR location referenced by inventory operations: Default v1 behavior is to **reject with 422**; optional config can allow `PENDING` location creation when explicitly enabled.

## Business Rules
- HR owns location lifecycle and authoritative attributes; Inventory enforces local constraints derived from that model.
- Inventory must not invent or permanently rename HR-provided identifiers; any local display names may include local suffixes but must store the canonical `hr_location_id` and tenant.
- Deactivated locations cannot receive NEW stock movements; existing stock is `PendingTransfer` and requires manual reconciliation per SLA.
- Sync must be auditable and idempotent; every applied change must create a `SyncLog` entry with source event id and `appliedAt`.
- Reconciliation job must run at configurable intervals and emit `location.sync.drift.count` when discrepancies are found.

## Data Requirements
- `LocationRef` table (inventory.schema.location_ref):
  - `id` (UUID PK)
  - `tenant_id` (string) -- tenant scope
  - `hr_location_id` (string, unique per tenant)
  - `name` (string)
  - `status` (ENUM: `ACTIVE`, `INACTIVE`, `PENDING`)
  - `timezone` (IANA tz)
  - `tags` (jsonb)
  - `is_active` (boolean)
  - `effective_from`, `effective_to` (timestamp)
  - `created_at`, `created_by`, `updated_at`, `updated_by`
  - `deactivated_at`, `deactivated_by`
  - `version` (optimistic lock)
  - Index: unique on `(tenant_id, hr_location_id)`, index on `is_active`

- `SyncLog` table:
  - `sync_id` (UUID PK)
  - `hr_event_id` (string)
  - `hr_payload` (jsonb)
  - `applied_at`, `applied_by`, `outcome` (OK/INVALID/RETRY/FAILED)
  - `error_message` (nullable)

- Reconciliation job state table for last-run timestamps and metrics.

## Acceptance Criteria
- Scenario: Event-driven creation
  - Given `durion-hr` emits `LocationCreated` with hrLocationId=L100 and required fields
  - When Inventory consumes the event
  - Then a `LocationRef` for L100 exists with `status=ACTIVE`, a `SyncLog` entry is recorded, and `location.sync.processed` metric increments

- Scenario: Idempotent update
  - Given the same `LocationUpdated` event is delivered twice
  - When processed
  - Then the second processing is a no-op (idempotent) and `SyncLog` shows duplicate delivery handled with same resulting `LocationRef`

- Scenario: Deactivation blocks receipts
  - Given HR marks L100 as deactivated
  - When an inbound stock movement to L100 is attempted
  - Then Inventory rejects the movement with a clear error (422) and records audit entry referencing `deactivated_at` and `hr_event_id`

- Scenario: Bulk reconcile finds drift
  - Given HR roster and local `LocationRef` differ
  - When reconciliation runs
  - Then discrepancies are logged, `location.sync.drift.count` metric increases, and a reconcile report is saved

## Audit & Observability
- `SyncLog` entries for every inbound HR payload and applied action.
- Metrics: `location.sync.processed`, `location.sync.failures`, `location.sync.drift.count`, `location.sync.lag_seconds`.
- Tracing: spans for event ingestion -> upsert -> reconciliation with `hrLocationId` tag.
- Alerts: high failure rates or large drift trigger Ops alerts per SLO.

## Resolved Decisions (from clarification #410)
- Deactivation policy (v1): **Option B** — mark existing stock as `PendingTransfer` and require manual reconciliation within **5 business days**.
- Identity mapping: use composite key `tenantId + ":" + hrLocationId` (unique per tenant); store `(tenant_id, hr_location_id)` with unique constraint.
- Sync SLA: **event-driven p95 ≤ 60s**; bulk reconcile freshness ≤ 15 minutes. Retry policy: at-least-once, exponential backoff, max 10 attempts over ~30 minutes, DLQ + alert on exhaustion.

## Open Questions
- Should Inventory create a configurable `HOLDING` virtual location for manual transfers, or rely solely on manual transfer-out to existing active locations? (Config keys provided in clarification; decide if `inventory.location.holding.enabled` should default to true.)

---

## Original Story (Unmodified – For Traceability)

## Backend Implementation for Story

**Original Story**: [STORY] Topology: Sync Locations from durion-hr

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want to sync location identifiers and metadata from durion-hr so that inventory is scoped to valid shops and mobile sites.

## Details
- Import locationId, name, status, timezone, and tags.
- Keep a local reference table for FK integrity.

## Acceptance Criteria
- Location refs created/updated idempotently.
- Deactivated locations cannot receive new stock movements.
- Audit sync runs.

## Integrations
- HR → Inventory location roster API/events.

## Data / Entities
- LocationRef, SyncLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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