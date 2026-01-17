Title: [BACKEND] [STORY] Availability: Normalize Distributor Inventory Feeds (Stub via Positivity)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/47
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:inventory
- status:ready-for-dev

### Recommended
- agent:inventory
- agent:story-authoring

---
**Rewrite Variant:** inventory-flexible
---

## Story Intent
As an Integration Operator, I need an automated pipeline to ingest raw distributor availability feeds (via Positivity), normalize disparate formats into a canonical internal model, and persist a unified, queryable source of truth for external parts availability. This unified availability is consumed downstream (e.g., Work Execution) to inform estimates and scheduling.

## Actors & Stakeholders
- **Actors**
  - **System**: Ingests, normalizes, and persists availability data.
  - **Integration Operator**: Monitors pipeline health and handles exceptions.
- **Stakeholders**
  - **Inventory Manager**: Uses availability and lead times for planning.
  - **Service Advisor**: Indirectly relies on accuracy through Work Execution.
  - **Positivity Domain**: Upstream source providing raw feed payloads.
  - **Work Execution Domain**: Primary downstream consumer of normalized availability.

## Preconditions
1. A SKU mapping mechanism exists to resolve `distributorSku` → internal `productId`.
2. A feed source exists (v1 supports a stub connector; see Resolved Questions).
3. An exception queue/topic exists for routing records that fail normalization/mapping.

## Functional Behavior
1. System is triggered to process a new distributor availability feed.
2. For each item record, system attempts to resolve `distributorSku` to internal `productId`.
3. **On successful SKU mapping**:
   - Normalize raw fields (quantity, lead time, ship-from region) to canonical schema.
   - Persist normalized record idempotently (UPSERT keyed by `productId + distributorId + shipFromRegionCode?` per schema choice).
   - Stamp `lastUpdatedAt` and `normalizationPolicyVersion`.
4. **On failed SKU mapping**:
   - Publish the original raw record to exception queue with `SKU_UNMAPPED`.
5. **On normalization parse failure** (lead time/region):
   - Publish the original raw record to exception queue with an appropriate reason code (see Resolved Questions).

## Alternate / Error Flows
- **Malformed Source Data**: Route to exception queue with `MALFORMED_DATA`.
- **Persistence Failure**: Fail the job, log error, and retry with backoff.
- **Exception Queue Publish Failure**: Retry with backoff; alert if retries exhausted.

## Business Rules
- **BR1: Idempotency is mandatory**: Reprocessing the same feed produces the same final DB state (no duplicates).
- **BR2: Mapping authority**: `DistributorSkuMap` is authoritative; no embedded mapping logic.
- **BR3: Record-level atomicity**: One bad record must not block processing of other records.
- **BR4: Explainable normalization**: Store `normalizationPolicyVersion` and preserve raw lead time/region fields for traceability and reprocessing.

## Data Requirements
### Input (Raw Feed)
- `distributorId`: string
- `asOf`: datetime (UTC)
- `items[]`:
  - `distributorSku`: string
  - `manufacturerPartNumber`: string (optional)
  - `availableQty`: number|string
  - `leadTimeRaw`: string
  - `shipFromRegionRaw`: string

### Output (Normalized Availability)
- `productId`: UUID
- `distributorId`: string
- `quantityAvailable`: int (>= 0)
- `leadTimeDaysMin`: int|null
- `leadTimeDaysMax`: int|null
- `shipFromRegionCode`: string (ISO 3166-2 preferred; ISO 3166-1 fallback)
- `normalizationPolicyVersion`: string
- `rawLeadTimeRaw`: string
- `rawShipFromRegionRaw`: string
- `lastUpdatedAt`: datetime (UTC)

### Exception Queue Message
- `payload`: raw record
- `reasonCode`: enum (`SKU_UNMAPPED`, `LEAD_TIME_UNPARSABLE`, `REGION_UNMAPPED`, `REGION_AMBIGUOUS`, `MALFORMED_DATA`)
- `errorMessage`: string
- `firstSeenAt`: datetime (UTC)
- `lastSeenAt`: datetime (UTC)
- `occurrenceCount`: int
- `distributorId`: string

## Acceptance Criteria
- **AC1: Mapped SKU produces normalized record**
  - **Given** a feed item whose SKU maps to `productId=P1`
  - **When** ingestion runs
  - **Then** a normalized availability record is upserted for that distributor and product
  - **And** the record includes `normalizationPolicyVersion` and `lastUpdatedAt`
  - **And** no exception message is published for that item.

- **AC2: Unmapped SKU routes to exception**
  - **Given** a feed item with an unmapped `distributorSku`
  - **When** ingestion runs
  - **Then** an exception message is published with `reasonCode=SKU_UNMAPPED`
  - **And** other valid items are still processed.

- **AC3: Lead time range normalization**
  - **Given** `leadTimeRaw="24-48 hours"`
  - **When** ingestion normalizes lead time
  - **Then** it stores `leadTimeDaysMin=1` and `leadTimeDaysMax=2`.

- **AC4: Unparseable lead time routes to exception**
  - **Given** `leadTimeRaw` cannot be parsed by supported patterns
  - **When** ingestion runs
  - **Then** an exception message is published with `reasonCode=LEAD_TIME_UNPARSABLE`.

- **AC5: Ship-from region normalization**
  - **Given** `shipFromRegionRaw="Dallas, TX"` and country context is available/inferable as US
  - **When** ingestion runs
  - **Then** it stores `shipFromRegionCode="US-TX"`.

- **AC6: Idempotent reprocessing**
  - **Given** a feed was processed successfully
  - **When** the exact same feed is processed again
  - **Then** the normalized DB state remains logically unchanged (UPSERT)
  - **And** `lastUpdatedAt` is refreshed.

## Audit & Observability
- **Logging**: Log start/end of each job with distributor, record counts (received/succeeded/failed).
- **Metrics**:
  - `normalized_records.count` (tag by distributor)
  - `exception_records.count` (tag by distributor and reasonCode)
  - `feed_processing.duration` (tag by distributor)
- **Alerting**: Alert when exception rate exceeds a configurable threshold (e.g., 5%) or backlog age exceeds SLA.

## Resolved Questions
Decisions applied from Clarification #240 (issuecomment-3739215330).

### RQ1: Normalization rule ownership
- **Decision**: Normalization rules are owned by the ingest/normalization service (availability) as a **versioned transformation policy**.
- Persist `normalizationPolicyVersion`, `rawLeadTimeRaw`, and `rawShipFromRegionRaw` so outputs are explainable and reprocessable.

### RQ2: Lead time normalization
- **Supported v1 formats**: simple durations (e.g., “2 days”, “48 hours”), ranges (“24-48 hours”, “2-3 days”), shorthand (“T+1”), qualitative (“same day”, “backorder”, “call for availability”).
- **Conversion**:
  - Hours → days: `ceil(hours/24)`
  - Ranges: normalize both ends; ensure `min <= max`
  - Non-integers: `ceil()` each end
  - Business days: treat as calendar days in v1 (no holiday calendar), but retain qualifier for future
  - Qualitative:
    - “same day” → 0 (preferred)
    - “in stock” without lead time → null (do not assume)
    - “backorder/call” → null and route to exception if lead time is required downstream
- **Failure policy**: unparseable → exception `LEAD_TIME_UNPARSABLE`.

### RQ3: Ship-from region normalization
- **Canonical**: Prefer **ISO 3166-2** (e.g., `US-TX`), fallback to **ISO 3166-1 alpha-2** (`US`).
- **Mapping**:
  1. Normalize tokens (trim/uppercase/remove punctuation)
  2. Attempt: ISO 3166-2 → known state/province name → abbreviations (only with country context) → country name
  3. Ambiguous (e.g., “CA”) without country context → exception `REGION_AMBIGUOUS`
  4. Unmappable → exception `REGION_UNMAPPED`
- Maintain a versioned mapping table (`rawToken → regionCode`) with `mappingVersion`.

### RQ4: Exception queue SLA and operational plan
- **Ownership**: primary monitoring by Operations / Inventory Data Steward; secondary by on-call for availability/integration service.
- **Recommended SLA tiers**:
  - P1 (blocks sales/fulfillment): 4 business hours
  - P2 (non-blocking): 2 business days
  - P3 (rare/info): 5 business days
- Exception message should include `firstSeenAt`, `lastSeenAt`, `occurrenceCount` and alerting on spikes/backlog age.

### RQ5: V1 stub connector scope
- **Decision**: v1 stub means **no live Positivity integration required**.
- Implement a stable input contract ingestible from local file/static endpoint/mocked topic.
- **Recommended stub contract**:
  - Input: JSON file (array) or NDJSON
  - Config: `AVAIL_FEED_STUB_PATH`
  - Trigger: manual or short dev polling schedule
  - Example payload:

  ```json
  {
    "distributorId": "UUIDv7",
    "asOf": "2026-01-12T00:00:00Z",
    "items": [
      {
        "distributorSku": "ABC123",
        "manufacturerPartNumber": "MPN-9",
        "availableQty": 10,
        "leadTimeRaw": "24-48 hours",
        "shipFromRegionRaw": "Dallas, TX"
      }
    ]
  }
  ```

## Open Questions (if any)
- None.

---
## Original Story (Unmodified – For Traceability)
## Backend Implementation for Story

**Original Story**: [STORY] Availability: Normalize Distributor Inventory Feeds (Stub via Positivity)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Integration Operator**, I want to ingest distributor availability feeds so that a unified availability view can be presented.

## Details
- Map distributor SKUs to internal productId.
- Normalize qty, lead time, ship-from region.
- Stub connector acceptable in v1.

## Acceptance Criteria
- Ingestion idempotent.
- Mapping errors routed to exception queue.
- Normalized availability queryable.

## Integrations
- Positivity connectors fetch feeds; product normalizes for inventory/workexec.

## Data / Entities
- ExternalAvailability, DistributorSkuMap, ExceptionQueue

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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
