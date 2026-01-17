Title: [BACKEND] [STORY] Locations: Create Mobile Units and Coverage Rules
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/76
Labels: type:story, domain:location, status:ready-for-dev

**Rewrite Variant:** inventory-flexible  
**Status:** Ready-for-dev (clarification #404 applied)

## Story Intent
As a **Shop Administrator**, I need to configure Mobile Service Units as distinct operational resources (capabilities, geographic coverage, and travel buffer policies), so that the Work Execution system can accurately schedule mobile appointments and account for travel logistics.

## Actors & Stakeholders
- **Shop Administrator**: configures mobile units, coverage, and policies.
- **Work Execution System (`workexec` domain)**: consumes mobile unit eligibility + policy data for scheduling.
- **Product/Service Catalog (`product/service-catalog` domain)**: authoritative source of `ServiceCapability`.

## Preconditions
- Caller is authenticated and authorized.
- A base location exists in the Location system (`pos-location`), referenced by `baseLocationId`.
- Service Catalog is reachable for capability validation.

## Functional Behavior
1) **Manage Mobile Units**
- Create, view, and update Mobile Units.
- Status lifecycle is controlled via status (no hard delete in v1).

2) **Assign Capabilities**
- Mobile Units reference capabilities by ID only.
- On create/update, the service validates capability IDs against Service Catalog.

3) **Define Coverage**
- Coverage is defined by linking Mobile Units to one or more Service Areas.
- Coverage rules support priority and effective date ranges.
- Coverage replacement should be atomic to avoid partial/inconsistent config.

4) **Define Service Areas**
- Service Areas are defined as **postal-code sets** (no polygons/geofencing in v1).

5) **Define Travel Buffer Policies**
- Support policy types:
  - `FIXED_MINUTES`
  - `DISTANCE_TIER`

For `DISTANCE_TIER`:
- Policy configuration stores **numeric distance tiers** (not a discrete class).
- Tier selection input is provided by the caller as `estimatedDistanceKm` (preferred) or `estimatedDistanceMi` (allowed, normalized internally to km).
- Location service stores/validates/returns the configuration; it does **not** compute distance for v1.

6) **Eligibility Query for Scheduling**
- Provide an eligibility query by postal code and timestamp:
  - Finds ServiceArea(s) containing the postal code.
  - Returns **ACTIVE** Mobile Units with an effective coverage rule at the timestamp.
  - Does not compute calendar availability (workexec owns that).

## Alternate / Error Flows
- **Invalid base location**: reject if `baseLocationId` does not exist (`400 Bad Request`).
- **Missing required fields**: reject with `400 Bad Request`.
- **Duplicate name (within scope)**: reject with `409 Conflict`.
- **Unknown capability IDs**: reject with `400 Bad Request`.
- **Service catalog unavailable during validation**: reject with `503 Service Unavailable`.
- **Invalid effective window**: reject if `effectiveEndAt <= effectiveStartAt` (`400 Bad Request`).
- **Invalid DISTANCE_TIER configuration**: reject with `400 Bad Request` (see rules below).

## Business Rules
- A Mobile Unit is schedulable only when `status = ACTIVE`.
- A Mobile Unit must be associated with exactly one base location.
- `maxDailyJobs` is a hard constraint for scheduling.
- `INACTIVE` and `OUT_OF_SERVICE` units are not schedulable.
- Service capabilities are **structured entities** owned by Service Catalog (not free-text tags).
- Service areas are **postal-code sets** (not polygons/geofencing) in v1.
- For v1, authorization is location-scoped (see “Data Requirements / scoping” and “Security”).

`DISTANCE_TIER` rules:
- Tiers are ordered by ascending `maxDistance` (null means catch-all/infinity).
- Tier selection chooses the **first** tier where `distanceKm <= maxDistance`; otherwise falls through to the `maxDistance=null` tier (required).
- Validation: `bufferMinutes >= 0`; `maxDistance` must be strictly increasing where not null; at least one tier must exist; the catch-all tier must exist.

Ownership rule:
- Location service is the system of record for the **policy configuration**.
- The caller (workexec/scheduling) is responsible for supplying the operational distance input used to apply the policy.

## Data Requirements
### Identifier + scoping conventions (confirmed from repo)
- The existing Location API in this repo (`pos-location`) uses `Location.id` as `Long`.
- No `orgId/tenantId` convention was found in backend code; authorization/scoping patterns in `pos-security-service` use `locationId`.

### Entities
- **MobileUnit**
  - `mobileUnitId`: UUID (PK)
  - `name`: string (required)
  - `status`: enum (`ACTIVE`, `INACTIVE`, `OUT_OF_SERVICE`) (required)
  - `baseLocationId`: Long (required; references `pos-location` Location)
  - `travelBufferPolicyId`: UUID (required when `ACTIVE`, optional otherwise)
  - `maxDailyJobs`: integer (required, >= 0)
  - `createdAt`, `updatedAt`

- **MobileUnitCapability** (join table)
  - `mobileUnitId`, `capabilityId`

- **ServiceArea**
  - `serviceAreaId`: UUID (PK)
  - `name`: string (required)
  - `countryCode`: ISO 3166-1 alpha-2 (required)
  - `postalCodes[]`: strings (normalize into child table preferred)
  - `cities[]`: optional display-only

- **MobileUnitCoverageRule**
  - `coverageRuleId`: UUID (PK)
  - `mobileUnitId`: UUID (FK)
  - `serviceAreaId`: UUID (FK)
  - `priority`: integer (lower = higher priority)
  - `effectiveStartAt`: timestamp (nullable = immediate)
  - `effectiveEndAt`: timestamp (nullable = indefinite)

- **TravelBufferPolicy**
  - `travelBufferPolicyId`: UUID (PK)
  - `name`: string (required)
  - `policyType`: enum (`FIXED_MINUTES`, `DISTANCE_TIER`) (required)
  - `policyConfiguration`: JSONB (required; validated by type)

`DISTANCE_TIER` policyConfiguration shape (stored in `pos-location`):

```json
{
  "unit": "KM",
  "tiers": [
    { "maxDistance": 5,  "bufferMinutes": 10 },
    { "maxDistance": 15, "bufferMinutes": 20 },
    { "maxDistance": 30, "bufferMinutes": 35 },
    { "maxDistance": null, "bufferMinutes": 50 }
  ]
}
```

## Acceptance Criteria
### Scenario 1: Successful Mobile Unit Creation
- Given I am authenticated with `location.mobile-unit.manage`
- And a base location with ID `1` exists
- When I `POST /v1/mobile-units` with a valid payload including `name`, `baseLocationId`, `maxDailyJobs`, and valid capability IDs
- Then the API returns `201 Created`
- And the response contains a `mobileUnitId`
- And the Mobile Unit is persisted.

### Scenario 2: Prevent Duplicate Name Within Base Location
- Given a Mobile Unit named `Mobile Van 1` already exists for `baseLocationId = 1`
- When I create another Mobile Unit with the same name for `baseLocationId = 1`
- Then the API returns `409 Conflict`.

### Scenario 3: Eligibility Query Returns ACTIVE Covered Units
- Given a ServiceArea exists containing postal code `12345` in country `US`
- And Mobile Unit `A` is `ACTIVE` and has an effective coverage rule for that ServiceArea at time `T`
- When workexec calls `GET /v1/mobile-units:eligible?postalCode=12345&countryCode=US&at=T`
- Then the response includes Mobile Unit `A`
- And results are ordered by `priority` ascending.

### Scenario 4: Create and Read a DISTANCE_TIER Travel Buffer Policy
- Given I am authenticated with `location.mobile-unit.manage`
- When I `POST /v1/travel-buffer-policies` with `policyType = DISTANCE_TIER` and a valid tier configuration (including a catch-all tier)
- Then the API returns `201 Created`
- And `GET /v1/travel-buffer-policies/{id}` returns the same tier configuration.

### Scenario 5: Reject Invalid DISTANCE_TIER Configuration
- Given I am authenticated with `location.mobile-unit.manage`
- When I `POST /v1/travel-buffer-policies` with `policyType = DISTANCE_TIER` and tiers that are not strictly increasing by `maxDistance` (or missing the catch-all tier)
- Then the API returns `400 Bad Request`.

### Scenario 6: Out-of-Scope (TravelTimeApproved Event)
- The `TravelTimeApproved` event contract is defined in “Resolved Questions” for cross-domain integration.
- Emitting that event is owned by the travel-capture/approval workflow (workexec/timekeeping), not by this story’s location configuration APIs.

## Audit & Observability
- Emit audit events for create/update/status-change on Mobile Units, coverage replacement, and travel buffer policy create/update.
- Include `X-Correlation-Id` on logs (generate if missing).
- Metrics: `mobile_units.total` partitioned by status.

## Resolved Questions
From clarification #259:

### RQ1: Travel Buffer Policy (Initial Types)
**Decision:** Support `FIXED_MINUTES` and `DISTANCE_TIER` in v1. No third-party traffic/mapping integration required.

### RQ2: Service Capabilities (Authoritative Source & Model)
**Decision:** Capabilities are structured and owned by Service Catalog; location stores only capability IDs.

### RQ3: Service Areas (Definition, Storage, Query)
**Decision:** Service areas use ZIP/postal-code sets in v1 (no polygons/geofencing).

### RQ4: HR Integration Contract (Travel Time Exchange)
**Decision:** Event-driven push to HR on approval; topic `timekeeping.travel.approved.v1`. (Contract preserved for downstream alignment; emission not in scope of this story.)

From clarification #404:

### RQ5: DISTANCE_TIER TravelBufferPolicy distance input + ownership
**Decision:** Tier selection uses a **numeric distance** input (`estimatedDistanceKm` preferred; `estimatedDistanceMi` allowed, normalized internally), supplied by the **caller (workexec/scheduling)**.

**System-of-record split (v1):**
- Location service stores/validates/returns policy configuration (tiers).
- Caller computes/provides operational distance input and applies policy when scheduling.

## Original Story (Unmodified – For Traceability)
# Issue #76 — [BACKEND] [STORY] Locations: Create Mobile Units and Coverage Rules

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Locations: Create Mobile Units and Coverage Rules

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want to define mobile units with service capabilities and coverage so that mobile appointments can be scheduled with travel buffers.
## Details
- Mobile unit attributes: capabilities, base location, service area tags, travel buffer policy, max daily jobs.

## Acceptance Criteria
- Mobile unit created and assignable.
- Coverage/buffers configurable.
- Out-of-service blocks scheduling.

## Integrations
- HR receives travel time.
- Workexec stores mobileUnitId context for mobile workorders.

## Data / Entities
- Resource(MobileUnit), CoverageRule, TravelBufferPolicy

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation