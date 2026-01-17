Title: [BACKEND] [STORY] Locations: Create and Maintain Shop Locations
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/78
Labels: type:story, domain:location, status:ready-for-dev, agent:story-authoring, agent:location

**Rewrite Variant:** inventory-flexible

## Story Intent
**As an** Admin,
**I want to** create, manage, and deactivate shop locations, including their addresses, operating hours, timezones, holiday closures, and appointment buffers,
**so that** customer-facing systems and internal operations (like scheduling and work execution) have an accurate and authoritative source of location information.

## Actors & Stakeholders
- **Admin**: Manages shop location data.
- **System**: POS backend service responsible for validation, persistence, and auditing.
- **Work Execution System (Consumer)**: References `locationId` on Estimates/Work Orders/Invoices and must validate location status.
- **HR System (Consumer)**: Filters staff availability/scheduling by location affiliation.

## Preconditions
- The Admin is authenticated and authorized for location management.
- The system supports validating IANA time zone IDs.
- Global default buffer configuration exists (system-wide defaults), with per-location overrides supported.

## Functional Behavior

### API Endpoints (Contract)
- `POST /locations`
  - Creates a new `Location` with `status=ACTIVE`
  - Returns `201 Created` with the full resource payload
- `GET /locations/{locationId}`
  - Returns `200 OK` with the full resource payload (including `status`)
  - Returns `404 Not Found` if the location does not exist
- `GET /locations?status=ACTIVE|INACTIVE|ALL` (default `ACTIVE`)
  - Returns a list of locations; by default only ACTIVE locations are returned
- `PUT /locations/{locationId}`
  - Full update (idempotent)
  - Returns `200 OK`
- `PATCH /locations/{locationId}`
  - Partial update
  - Returns `200 OK`
  - Used for soft-deactivation by setting `{ "status": "INACTIVE" }` (preferred over a dedicated `/deactivate` endpoint)

### 1. Create a New Shop Location
- The Admin submits location details.
- The System validates all provided data.
- If valid, the System persists the record as `ACTIVE` and generates a unique `locationId`.

### 2. Update an Existing Shop Location
- The Admin submits a full (`PUT`) or partial (`PATCH`) update.
- The System validates the update.
- If valid, the System persists the updated record.

### 3. Deactivate a Shop Location (Soft Deactivate)
- The Admin requests deactivation via `PATCH /locations/{locationId}` with `{ "status": "INACTIVE" }`.
- The System enforces the allowed transition `ACTIVE -> INACTIVE`.
- The location remains readable but is excluded from the active-only list by default.

## Alternate / Error Flows
- **Validation Failure**: Return `400 Bad Request` with a structured error containing `code`, `message`, and `field` where applicable.
  - Example codes: `INVALID_TIMEZONE`, `INVALID_OPERATING_HOURS`
- **Permission Denied**: Return `403 Forbidden`.
- **Not Found**: Return `404 Not Found` when `{locationId}` does not exist.
- **Name Uniqueness Conflict**: Return `409 Conflict` with code `LOCATION_NAME_TAKEN`.
- **Optimistic Lock Failure**: Return `409 Conflict` with code `OPTIMISTIC_LOCK_FAILED` for stale updates.

## Business Rules
- **Uniqueness**: `name` is unique across all locations regardless of status.
- **Normalization for uniqueness**: uniqueness is case-insensitive and trimmed.
  - `normalizedName = lower(trim(name))`
  - Unique constraint/index enforced on `normalized_name`.
- **Timezone**: `timezone` must be a valid IANA time zone ID (validated with `ZoneId.of(timezone)`).
- **Operating Hours**:
  - Hours are local times (`HH:mm`) in the location’s timezone.
  - Closed days are represented by *omitting* that day from `operatingHours`.
  - No duplicate day entries.
  - Overnight ranges (e.g., `22:00–02:00`) are not supported in v1.
  - For each day entry: `open < close` (strict).
- **Holiday Closures**:
  - Stored as `[{ date: "YYYY-MM-DD", reason?: string }]`.
  - No duplicate `date` entries per location.
- **Status**:
  - No hard deletes.
  - Allowed deactivation transition: `ACTIVE -> INACTIVE`.
- **Buffers**:
  - `checkInBufferMinutes` and `cleanupBufferMinutes` are integer minutes, `>= 0`.
  - Fields are nullable per-location overrides; effective values fall back to global defaults when null.

## Data Requirements
The `Location` resource contains:

| Field Name | Data Type | Description | Constraints |
|---|---|---|---|
| `locationId` | UUID | System-generated identifier | PK, not null |
| `name` | String | Display name | not null |
| `normalizedName` | String | Normalized name for uniqueness | not null, unique |
| `status` | Enum | Location status | not null (`ACTIVE`, `INACTIVE`) |
| `address` | JSONB / Object | Structured address | not null |
| `timezone` | String | IANA timezone ID | not null |
| `operatingHours` | JSONB / Array | Weekly hours entries | not null (empty list allowed) |
| `holidayClosures` | JSONB / Array | Date-only closures | nullable |
| `checkInBufferMinutes` | Integer | Per-location override | nullable, `>= 0` |
| `cleanupBufferMinutes` | Integer | Per-location override | nullable, `>= 0` |
| `version` | Integer/Long | Optimistic lock version | not null |
| `createdAt` | Timestamp | Created timestamp | not null |
| `updatedAt` | Timestamp | Updated timestamp | not null |

## Acceptance Criteria

### Scenario 1: Create Location (Happy Path)
- Given I am an authenticated Admin with permission to manage locations
- When I call `POST /locations` with valid name, address, timezone, and operating hours
- Then the system returns `201 Created` and the created location is `ACTIVE`

### Scenario 2: Update Location (Happy Path)
- Given an existing location
- When I call `PUT /locations/{locationId}` or `PATCH /locations/{locationId}` with valid updates
- Then the system returns `200 OK` and the stored data reflects the update

### Scenario 3: Deactivate Location
- Given an existing `ACTIVE` location
- When I call `PATCH /locations/{locationId}` with `{ "status": "INACTIVE" }`
- Then the system returns `200 OK` and the location is excluded from `GET /locations` default results

### Scenario 4: Reject Invalid Operating Hours
- Given I am an authenticated Admin with permission to manage locations
- When I submit operating hours where `close <= open`, duplicate days, or overnight hours
- Then the system returns `400 Bad Request` with code `INVALID_OPERATING_HOURS`

### Scenario 5: Reject Duplicate Name
- Given an existing location named "Downtown Auto Repair"
- When I create or rename another location to "  downtown auto repair  "
- Then the system returns `409 Conflict` with code `LOCATION_NAME_TAKEN`

### Scenario 6: Optimistic Lock Prevents Lost Update
- Given two clients load the same location version
- When one client updates the location and the other submits a stale update
- Then the stale update is rejected with `409 Conflict` and code `OPTIMISTIC_LOCK_FAILED`

### Scenario 7: Buffers Applied Correctly (Consumer Rule)
- Given a location has effective buffers of 10 minutes check-in and 10 minutes cleanup
- When an appointment is scheduled at 09:00 for 2 hours
- Then the effective resource occupancy is from 08:50 to 11:10

## Audit & Observability
- Emit an audit event for:
  - `LOCATION_CREATED`
  - `LOCATION_UPDATED`
  - `LOCATION_DEACTIVATED`
- Audit payload includes: `eventType`, `locationId`, `actorUserId`, `occurredAt`, and `before`/`after` (diff preferred; full snapshots acceptable).
- Expose metrics counters for locations created/updated/deactivated.

## Open Questions (if any)
- None.

## Resolved Questions

From Clarification Issue #261, the following answers were incorporated:

### RQ1: Buffer Specifics (Definitions, Data Type, Scope)

**Business Definitions**:
- **Check-in buffer**: A fixed time window **before the scheduled service start** reserved for customer arrival, vehicle intake, paperwork, and pre-service checks.
- **Cleanup buffer**: A fixed time window **after the scheduled service end** reserved for bay cleanup, tool reset, paperwork close-out, and vehicle staging.

**Data Type**: Buffers are durations in minutes (integers, `>= 0`)

**Scope & Override Model**: System-wide defaults with per-location nullable overrides

## Original Story (Unmodified – For Traceability)
# Issue #78 — [BACKEND] [STORY] Locations: Create and Maintain Shop Locations

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Locations: Create and Maintain Shop Locations

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want to create/edit shop locations with address, hours, and timezone so that appointments and scheduling rules are correct per site.

## Details
- Store location name/address/timezone/operating hours/holiday closures.
- Defaults: check-in and cleanup buffers.

## Acceptance Criteria
- Create/update/deactivate location.
- Hours/timezone validated.
- Changes audited.

## Integrations
- Workexec stores locationId on Estimate/WO/Invoice context.
- HR availability can be filtered by location affiliation.

## Data / Entities
- Location, OperatingHours, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
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