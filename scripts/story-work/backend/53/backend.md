Title: [BACKEND] [STORY] StorePrice: Sync Locations from durion-hr for Pricing Scope
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/53
Labels: type:story, domain:location, status:ready-for-dev

## Story Intent
Ensure pricing rules and other business logic are applied only to valid, active business locations by synchronizing a local representation of location data from the authoritative `durion-hr` system.

## Actors & Stakeholders
- **Primary Actor:** `System (Location Service)` — executes synchronization and maintains the local location store.
- **Data Source:** `durion-hr System` — authoritative source of truth for company location data.
- **Key Consumer:** `Pricing Service` — validates and scopes pricing overrides using synchronized location data.
- **Stakeholder:** `System Administrators` — monitor sync health.

## Preconditions
1. Network path and credentials for the Location Service to access `durion-hr` API/event stream are securely configured.
2. Data contract (API schema or event structure) for location data from `durion-hr` is defined and available.
3. Local database schema for `Location` and `SyncLog` entities is deployed.

## Functional Behavior
A recurring or event-driven process synchronizes location data from `durion-hr`.
1. **Trigger:** Sync starts via schedule (e.g., nightly) or by subscribing to location change events.
2. **Extraction:** Fetch all relevant location records from `durion-hr`.
3. **Transformation & Loading:** For each source record:
   - Use `locationId` to find an existing local `Location` record.
   - **If local exists:** Update fields (`name`, `status`, `region`, `tags`) from source.
   - **If local missing:** Create a new `Location` record with source data.
4. **Missing from feed:** If a previously known location is absent from the source feed, set the local record to `INACTIVE` indefinitely (no hard delete) to preserve referential integrity and signal ineligibility for pricing scope.
5. **Logging:** Log outcome (success/failure) and counts (created/updated/skipped) into `SyncLog`.
6. **Idempotency:** Running the sync with unchanged source data does not create duplicates or unintended changes.

## Alternate / Error Flows
- **Source System Unavailable:** If `durion-hr` is unreachable, log the failure, alert administrators after configurable consecutive failures, and leave local data unchanged.
- **Invalid Data Received:** If a record is missing required fields (`locationId`, `status`) or malformed, skip it, log the error with `locationId`, and continue.

## Business Rules
- `durion-hr` is the source of truth for location identity and status; local data is a replica.
- No hard deletes on local `Location` records; historical references must remain intact.
- If a location is absent from the `durion-hr` feed, mark the local record `INACTIVE` indefinitely (do not delete).
- A location with status `INACTIVE` (or any non-`ACTIVE` status) is invalid for new pricing overrides.
- Existing pricing overrides for a location that becomes `INACTIVE` must be disabled so they cannot be applied.

## Data Requirements
### `Location` Entity
- `locationId` (String, PK)
- `name` (String)
- `status` (Enum: `ACTIVE`, `INACTIVE`, etc.)
- `region` (String, Nullable)
- `tags` (List, Nullable)
- `createdAt` (Timestamp)
- `updatedAt` (Timestamp)

### `SyncLog` Entity
- `syncId` (UUID, PK)
- `syncStartedAt` (Timestamp)
- `syncFinishedAt` (Timestamp)
- `status` (Enum: `SUCCESS`, `PARTIAL_FAILURE`, `FAILURE`)
- `recordsProcessed` (Integer)
- `recordsCreated` (Integer)
- `recordsUpdated` (Integer)
- `recordsSkipped` (Integer)
- `notes` (Text, Nullable)

## Acceptance Criteria
**AC1: Sync new and existing locations**
- Given `durion-hr` has Location A (`id:"loc-1"`, `name:"Main St"`, `status:"ACTIVE"`) not in local DB and Location B (`id:"loc-2"`, `name:"Old Name"`, `status:"ACTIVE"`) already local
- And source data for Location B is now (`id:"loc-2"`, `name:"Central Hub"`, `status:"INACTIVE"`)
- When the sync runs successfully
- Then local DB contains Location A with `status:"ACTIVE"`
- And local Location B is updated to `name:"Central Hub"`, `status:"INACTIVE"`

**AC2: Idempotent sync**
- Given a successful sync completed and source data unchanged
- When sync runs again
- Then `updatedAt` remains unchanged for existing records
- And `SyncLog` reports 0 created / 0 updated

**AC3: New overrides rejected for INACTIVE locations**
- Given a local location `id:"loc-deactivated"` has `status:"INACTIVE"`
- When an external system attempts to create a price override for `loc-deactivated`
- Then the request is rejected because the location is not valid for new overrides

**AC4: Sync failure handled**
- Given `durion-hr` is unreachable
- When sync runs
- Then no local location data changes
- And a `SyncLog` entry is recorded with `status:"FAILURE"` and connection error details

**AC5: Missing-from-feed locations become INACTIVE**
- Given a local location `id:"loc-3"` exists from prior syncs
- And the current `durion-hr` feed no longer contains `loc-3`
- When sync processes the feed
- Then the local `loc-3` record is set to `status:"INACTIVE"` and retained (no deletion)

**AC6: Existing overrides disabled when location becomes INACTIVE**
- Given a location `id:"loc-override"` has active price overrides
- And `loc-override` is synchronized with `status:"INACTIVE"` (either explicitly from source or due to missing-from-feed handling)
- When sync completes
- Then all price overrides linked to `loc-override` are disabled so they cannot be applied until the location returns to `ACTIVE`

## Audit & Observability
- **Logging:** Log sync start/completion/outcome at `INFO`; record-level errors at `WARN`/`ERROR` with `locationId`.
- **Metrics:**
  - `location_sync_duration_seconds`
  - `location_sync_total_runs` (counter with status tag)
  - `locations_processed_total` (counter)
- **Alerting:** Alert when sync fails for 3 consecutive runs.

## Open Questions
- None. Clarification #244 resolved prior questions.

---
## Original Story (Unmodified – For Traceability)
# Issue #53 — [BACKEND] [STORY] StorePrice: Sync Locations from durion-hr for Pricing Scope

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] StorePrice: Sync Locations from durion-hr for Pricing Scope

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want to sync location identifiers from durion-hr so that store pricing can be scoped to valid locations.

## Details
- Import locationId, name, status.
- Optional region/tags.

## Acceptance Criteria
- Locations present in product domain.
- Deactivated locations cannot receive new overrides.
- Sync idempotent.

## Integrations
- HR → Product location roster API/events.

## Data / Entities
- LocationRef, SyncLog

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