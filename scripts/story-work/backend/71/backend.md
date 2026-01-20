Title: [BACKEND] [STORY] Dispatch: Determine Mechanic Availability for a Time Window
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/71
Labels: backend, story-implementation, user, type:story, domain:workexec, status:ready-for-dev, risk:external-dependency

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:external-dependency

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
As a **Dispatcher**, I need to query mechanic availability for a specific time window, considering their scheduled shifts, approved time off, and existing work assignments, so that I can confidently schedule new jobs without causing conflicts or double-booking. The system must provide clear reasons for any periods of unavailability to support my scheduling decisions.

## Actors & Stakeholders
- **Dispatcher (Primary Actor)**: The user responsible for scheduling and assigning work to mechanics.
- **Mechanic (Subject)**: The resource whose work schedule and availability are being queried.
- **Work Execution Service (System)**: The service responsible for aggregating availability data and enforcing scheduling business rules.
- **HR System (External Dependency)**: The external system of record for employee shifts and Paid Time Off (PTO).

## Preconditions
- The Dispatcher is authenticated and authorized to view mechanic schedules.
- The Work Execution Service has a valid network route and credentials to access the external HR System's availability endpoint.
- Mechanic profiles and identifiers are synchronized or mappable between the Work Execution Service and the HR System.

## Functional Behavior
1.  **Trigger**: The Dispatcher initiates an availability query for a specified time window (start and end timestamps). The query can be for all mechanics at a location or for a specific mechanic.
2.  **External Data Fetch**: The Work Execution Service queries the HR System for all relevant mechanics' shift schedules and approved PTO within the specified time window.
3.  **Internal Data Fetch**: The Work Execution Service queries its own database for any existing commitments for the same mechanics within the window. This includes:
    *   Confirmed work order assignments.
    *   Scheduled customer appointments.
    *   Explicitly created "travel blocks" for mobile mechanics.
4.  **Aggregation & Analysis**: The system aggregates and analyzes the data from all sources for each mechanic. It determines periods of unavailability by identifying conflicts where a mechanic is:
    *   Not on a scheduled shift.
    *   On approved PTO.
    *   Already assigned to a work order or appointment.
    *   Blocked by a travel reservation.
5.  **Response Generation**: The system generates a structured response that:
    *   Lists each mechanic queried.
    *   Provides an overall availability status for the requested window (e.g., `AVAILABLE`, `UNAVAILABLE`, `PARTIALLY_AVAILABLE`).
    *   Includes a detailed timeline of any unavailable blocks, each with a specific start time, end time, and a machine-readable reason code (e.g., `ON_PTO`, `OFF_SHIFT`, `ASSIGNED_WORK_ORDER`, `TRAVEL_BLOCK`).

## Alternate / Error Flows
- **Error: External HR System Unavailable**: If the Work Execution Service cannot connect to the HR System, the API request shall fail with a distinct error code (e.g., `503 Service Unavailable`) and a message indicating that an external dependency is down and availability cannot be reliably determined.
- **Error: Invalid Time Window**: If the request contains a start time that is after the end time, the API shall return a `400 Bad Request` error with a descriptive message.
- **Flow: No Conflicts Found**: If a mechanic is on shift and has no PTO or existing work assignments during the requested window, the system returns a status of `AVAILABLE` with an empty list of conflicting blocks.

## Business Rules
- A mechanic is considered "available" only when they are within a scheduled shift, not on approved PTO, and have no other work or travel commitments.
- Availability is calculated against a defined "Shop Calendar" to account for holidays and non-operating hours.
- Reason codes for unavailability must be from a standardized, enumerated list.
- All timestamps in API requests and responses must be in UTC.

## Data Requirements
- **Request Body (`AvailabilityQuery`)**:
    - `startTime`: `datetime` (UTC, ISO 8601)
    - `endTime`: `datetime` (UTC, ISO 8601)
    - `locationId`: `uuid` (required)
    - `mechanicIds`: `array<uuid>` (optional, filters for specific mechanics)

- **Response Body (`AvailabilityResult`)**:
    - `queryWindow`: `{ startTime, endTime }`
    - `mechanicAvailabilities`: `array<object>`
        - `mechanicId`: `uuid`
        - `overallStatus`: `enum` (`AVAILABLE`, `UNAVAILABLE`, `PARTIALLY_AVAILABLE`)
        - `conflicts`: `array<object>`
            - `startTime`: `datetime` (UTC, ISO 8601)
            - `endTime`: `datetime` (UTC, ISO 8601)
            - `reasonCode`: `enum` (`ON_PTO`, `OFF_SHIFT`, `ASSIGNED_WORK_ORDER`, `TRAVEL_BLOCK`)

## Acceptance Criteria
- **Given** a Dispatcher requests availability for a time window
  **And** a specific mechanic has a scheduled shift covering the entire window
  **And** the mechanic has no PTO or existing work assignments in that window
  **When** the availability API is called
  **Then** the system shall return a response with `overallStatus: AVAILABLE` for that mechanic and an empty `conflicts` array.

- **Given** a Dispatcher requests availability for a time window
  **And** a mechanic has approved PTO covering the entire window
  **When** the availability API is called
  **Then** the system shall return a response with `overallStatus: UNAVAILABLE` and a conflict entry with `reasonCode: ON_PTO`.

- **Given** a Dispatcher requests availability for a time window
  **And** a mechanic has an existing work order assigned during a portion of that window
  **When** the availability API is called
  **Then** the system shall return `overallStatus: PARTIALLY_AVAILABLE` and a conflict entry corresponding to the work order's time, with `reasonCode: ASSIGNED_WORK_ORDER`.

- **Given** the external HR System is unresponsive
  **When** the Dispatcher requests mechanic availability
  **Then** the API shall return an HTTP 503 error and a clear message that the dependency is unavailable.

- **Given** a Dispatcher provides a request where the `startTime` is after the `endTime`
  **When** the availability API is called
  **Then** the system shall return an HTTP 400 error with a validation message.

## Audit & Observability
- **Logging**:
    - Log every availability query request (excluding PII).
    - Log every failed attempt to connect to the external HR System, including latency and error details.
- **Metrics**:
    - `availability_query_latency`: Histogram measuring the duration of API requests.
    - `hr_system_integration_errors`: Counter for failures in communication with the HR system.
    - `availability_query_count`: Counter for the number of availability checks performed.

## Open Questions
1.  **CRITICAL**: What is the definitive API contract for the HR System's availability service? The story mentions both "queries HR availability endpoint" (implying REST) and "consumes AvailabilityChanged events" (implying async events). This must be clarified. Please provide the endpoint, authentication method, and request/response schema.
2.  **CRITICAL**: What is the source of truth and data model for "mobile travel blocks"? Are they created within our Work Execution system or sourced from another external system (e.g., a fleet management tool)?
3.  How should the system behave if the HR System is slow to respond (e.g., exceeds a 2-second timeout)? Should the request fail fast or wait longer?
4.  What is the required time granularity for availability checks (e.g., is precision to the minute required, or are 15-minute blocks sufficient)? This impacts query complexity and performance.

---
## Original Story (Unmodified – For Traceability)
# Issue #71 — [BACKEND] [STORY] Dispatch: Determine Mechanic Availability for a Time Window

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Dispatch: Determine Mechanic Availability for a Time Window

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want to see which mechanics are available for a time window so that I can assign work without double-booking.

## Details
- Availability includes shifts/PTO (HR), existing assignments, mobile travel blocks.
- Explainability via reason codes.

## Acceptance Criteria
- API returns availability + reasons.
- Conflicts detected.

## Integrations
- Shopmgr queries HR availability endpoint or consumes AvailabilityChanged events.

## Data / Entities
- AvailabilityQuery, AvailabilityResult

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