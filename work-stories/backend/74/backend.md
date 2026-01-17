Title: [BACKEND] [STORY] Scheduling: View Schedule by Location and Resource
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/74
Labels: type:story, domain:workexec, status:ready-for-dev

**Rewrite Variant:** workexec-structured  
**Status:** Ready-for-dev

## Story Intent
As a **Dispatcher**, I want to query and view a daily schedule organized by location, bay, and mobile resources (and person lanes if modeled), so I can manage shop capacity, assign work, and proactively identify and resolve scheduling conflicts.

## Actors & Stakeholders
- **Primary actor:** Dispatcher
- **Scheduling Service:** backend microservice responsible for constructing the daily schedule view
- **Secondary stakeholders:** Service Advisor, Shop Manager
- **Optional integration:** HR/People system for availability overlay

## Preconditions
1. Dispatcher is authenticated and authorized to view schedules for the requested location.
2. Location resources (bays, mobile resources, and optionally people/technicians) exist with operating hours.
3. Scheduled events exist (appointments/work orders/time blocks) for the requested date.

## API Contract
**Endpoint:** `GET /api/v1/schedules/view`

**Required query params**
- `locationId`
- `date` (`YYYY-MM-DD`) — interpreted in the **location’s timezone**

**Optional query params**
- `resourceType` = `BAY | MOBILE_TECHNICIAN | PERSON` (omit `PERSON` if not supported)
- `resourceId` = filter to a single resource
- `includeAvailabilityOverlay` = `true|false` (default `false`)
- `range` = `LOCATION_HOURS | FULL_DAY` (default `LOCATION_HOURS`)
- (future, not required for v1) `startAt`, `endAt` for custom windows

**Time representation rules**
- `dayStartAt/dayEndAt` are computed from location operating hours in the location timezone, then serialized as **UTC instants** (`...Z`).
- All event timestamps (`startTime/endTime`) are returned as **UTC instants**.

## Functional Behavior
1) Validate inputs and authorization.
2) Resolve the day window:
- Default: `[location.openTime, location.closeTime)` in location timezone.
- Fallback if hours missing: fixed window 06:00–18:00 local time.
- `range=FULL_DAY` expands window to 00:00–24:00 local time.
3) Query event sources for the window (see Data Sources).
4) Normalize all sources into `ScheduleEvent` items per resource.
5) Run conflict detection using the defined conflict policies.
6) If `includeAvailabilityOverlay=true`, call HR overlay (soft dependency) and merge best-effort.
7) Return `ScheduleView`.

## Policies & Business Rules
### Data Sources & Normalization (v1)
- Include: `Appointment`, `WorkOrder` (only if it has scheduled start/end), `TimeBlock`.
- Normalize to a single `ScheduleEvent` shape:
  - `eventId` (stable, prefixed by source: `APT-`, `WO-`, `TB-` recommended)
  - `eventType` (at minimum: `APPOINTMENT | WORK_ORDER | TIME_BLOCK`)
  - `subType` (recommended): `NOTE_BLOCK | SOFT_HOLD | BUFFER | TRAINING_SHADOW | ON_CALL | ...`
  - `startTime`, `endTime` (UTC)
  - `title`
  - `hasConflict`, `severity`, `conflictDetails.conflictingEventIds`

### Filtering & Resource List
- No filters: return **all schedulable resources** at the location for the date.
- `resourceType`: return **all resources of that type** at the location.
- `resourceId`:
  - If not found or not associated to location → `404 Not Found`
  - Otherwise return only that resource in `resources[]`

**Resource ordering (stable)**
- Bays: `displayOrder` then name
- Mobile: name
- People: name (if applicable)

### Conflict Detection
- Use half-open intervals: `[startAt, endAt)`.
- Any overlap **≥ 1 minute** counts as a conflict.
- Conflict dimensions:
  - **Resource conflicts (hard):** same bay/mobile resource overlaps
  - **Person conflicts (hard):** same mechanic/person overlaps (based on assignments)
- Allowed co-existence (no conflict): `NOTE_BLOCK`, `SOFT_HOLD`, `BUFFER`
- Conditional (configurable):
  - `TRAINING_SHADOW` may overlap only if it does not consume an additional bay/resource
  - `ON_CALL` may overlap with any event
- Severity:
  - `BLOCKING`: hard overlaps (person/bay/mobile)
  - `WARNING`: overlaps involving soft holds/buffers, training shadow overlaps

### Off-hours Behavior
- Return only events that intersect the returned window `[dayStartAt, dayEndAt)`.
- If an event partially overlaps the window: include original times; UI can infer off-hours by comparing to dayStart/dayEnd (optional future: add `isOffHours` flag).

### HR Availability Overlay (Soft Dependency)
- Only when `includeAvailabilityOverlay=true`.
- Call: `GET /people/v1/availability/overlay?locationId=...&date=YYYY-MM-DD`
- Timeout: **200 ms**.
- On timeout/error:
  - Return `200 OK` with schedule view
  - `availabilityOverlayStatus = UNAVAILABLE`
  - `warnings += ["HR_SYSTEM_UNAVAILABLE"]`
- On success:
  - `availabilityOverlayStatus = AVAILABLE`
  - Merge overlay by `personId` onto person resources if people are modeled as resources (recommended).

### Error Handling
- `400 Bad Request`: missing/invalid `locationId`, missing/invalid `date`, invalid enum values for `resourceType`/`range`.
- `403 Forbidden`: not authorized for `locationId`.
- `404 Not Found`: `locationId` not found; `resourceId` not found or not associated to location.

### Performance SLA
- P50 ≤ 150 ms, P95 ≤ 400 ms, P99 ≤ 800 ms.
- Scope: single location/day view (locationId + date + resourceType filter), warm cache assumed, excludes HR overlay.
- Implementation expectations:
  - Avoid N+1 queries (resources in 1 query; events in 1–3 queries)
  - Conflict detection O(n log n) per resource (sort + sweep)
  - Indexes (minimum): appointments/time blocks by `(location_id, start_at)` and assignment tables by `(person_id, start_at)` if needed for person conflicts

## Data Requirements (ScheduleView Read Model)
```json
{
  "locationId": "LOC-123",
  "date": "2024-07-29",
  "viewGeneratedAt": "2024-07-29T10:00:00Z",
  "dayStartAt": "2024-07-29T07:00:00Z",
  "dayEndAt": "2024-07-29T19:00:00Z",
  "warnings": ["HR_SYSTEM_UNAVAILABLE"],
  "availabilityOverlayStatus": "UNAVAILABLE",
  "resources": [
    {
      "resourceId": "BAY-01",
      "resourceType": "BAY",
      "resourceName": "Bay 1",
      "events": [
        {
          "eventId": "APT-456",
          "eventType": "APPOINTMENT",
          "startTime": "2024-07-29T09:00:00Z",
          "endTime": "2024-07-29T11:00:00Z",
          "title": "Brake Inspection - John Doe",
          "severity": "BLOCKING",
          "hasConflict": true,
          "conflictDetails": { "conflictingEventIds": ["APT-457"] }
        }
      ]
    }
  ]
}
```

## Acceptance Criteria
1) Retrieve a schedule for a location returns `200 OK` with correct events within SLA.
2) Overlapping appointments for the same bay (or person) set `hasConflict=true` with `severity=BLOCKING` and include mutual `conflictingEventIds`.
3) Invalid location/date → `400` or `404` as specified; unauthorized → `403`.
4) HR overlay failure still returns `200 OK` with `availabilityOverlayStatus=UNAVAILABLE` and warning.
5) Day window respects location hours (fallback 06:00–18:00 if not configured).

## Audit & Observability
- Log (INFO): userId, request params, response time, result counts (#resources, #events).
- Log (WARN): HR overlay failure (timeout vs non-timeout).
- Avoid per-conflict spam at INFO; emit summary at DEBUG or as a structured event.
- Metrics:
  - `schedule.view.requests.count{locationId}`
  - `schedule.view.response.latency{locationId, overlayRequested}`
  - `schedule.view.error.rate{errorType}`
  - `schedule.view.conflicts.detected{dimension=resource|person, severity}`

## Original Story (Unmodified – For Traceability)
# Issue #74 — [BACKEND] [STORY] Scheduling: View Schedule by Location and Resource

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Scheduling: View Schedule by Location and Resource

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want a daily schedule view by location/bay/mobile so that I can manage capacity and avoid conflicts.

## Details
- Views: location calendar, bay lanes, mobile list.
- Conflict highlighting and filters.

## Acceptance Criteria
- Filter by date/location/resource.
- Conflicts flagged.
- Loads within SLA.

## Integrations
- Optional HR availability overlay.

## Data / Entities
- ScheduleView(read model), ConflictFlag

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