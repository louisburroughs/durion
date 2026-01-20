Title: [BACKEND] [STORY] Reporting: Daily Dispatch Board Dashboard
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/60
Labels: type:story, domain:workexec, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:ready-for-dev

---

## Story Intent
**As a** Shop Manager,
**I want to** view a real-time dashboard showing daily work orders, mechanic availability, and resource conflicts,
**so that** I can dispatch jobs efficiently and avoid over-scheduling bays or mechanics.

## Actors & Stakeholders
- **Primary Actor:** `Shop Manager` (views dashboard, makes dispatch decisions).
- **Supporting Actors:** `Dispatch Coordinator` (may assist with scheduling).
- **Indirect Users:** `Mechanics` (their availability is displayed).
- **Stakeholder:** `Shop Owner` (depends on dispatch efficiency).

## Preconditions
- The system has active work orders for today (or selected date).
- Mechanic schedule and availability data is current.
- Service bays are registered with availability status.

## Functional Behavior
1. **Load Dashboard:** Shop Manager opens the Dispatch Board dashboard for a selected date (default: today).
2. **Display Data:**
   - List of pending/in-progress work orders.
   - Available mechanics and their current status (clocked in, on break, on job, etc.).
   - Available service bays and their current occupancy.
   - Visual conflict indicators (color-coded warnings or blocks).
3. **Refresh Data:**
   - Dashboard polls the backend every 30 seconds to fetch updated data.
   - Manager can manually refresh data at any time (button).
   - Optional future enhancement (v2): WebSocket push notifications for real-time events (job start, clock-out, bay change).
4. **Conflict Detection:** The system continuously evaluates conflicts and displays them with severity levels (WARNING vs. BLOCKING).
5. **Dispatch Action:** Manager selects a work order and a mechanic, confirming the assignment. The system validates conflicts before assignment.

## Alternate / Error Flows
- **Stale Data:** If polling data is older than 2 minutes, the dashboard displays a visual indicator (e.g., "Last updated 5 minutes ago").
- **Offline Mode:** If the backend is unreachable, the dashboard displays a warning and retains cached data (read-only).

## Business Rules
- **Availability Source of Truth:** The `People` service owns the real-time availability signal (status, clock-in/out, PTO, schedule).
- **Conflict Severity:** Some conflicts are WARNING (planner can proceed); others are BLOCKING (must be resolved before assignment).
- **No Auto-Resolution:** The system does NOT automatically reassign or cancel conflicting jobs. Decisions are human.
- **Same-Location Only:** Dispatch logic currently assumes all jobs and mechanics are in the same location (single-location dispatch; multi-location is future enhancement).

## Data Requirements
### Dashboard Query Data (Read from multiple services)
| Source | Data | Purpose |
|---|---|---|
| Work Execution | Work Orders (date-filtered), line items, labor summary | Display pending jobs, estimated duration |
| People | Mechanic roster, real-time status, schedule, PTO | Display availability, flags, conflicts |
| Shop Management | Service bays, occupancy, availability | Display bay conflicts |
| Pricing (Optional) | Estimated labor cost per job | Display labor cost, priority by profitability |

### Conflict Definition (8 Enumerated Conditions)
See **Resolved Questions** section for detailed definitions.

## Acceptance Criteria
**AC-1: Dashboard Loads with SLA Performance**
- **Given** I am a Shop Manager
- **When** I open the Dispatch Board for today
- **Then** the initial data load completes within 1 second (P50) and 2 seconds (P95)
- **And** the dashboard displays all work orders, mechanics, and bay status.

**AC-2: Polling Refresh Every 30 Seconds**
- **Given** the Dispatch Board is open
- **When** 30 seconds have elapsed
- **Then** the system automatically fetches updated data from the backend
- **And** the display refreshes with any changes in mechanic status, job status, or bay occupancy.

**AC-3: Manual Refresh Button**
- **Given** the Dispatch Board is open
- **When** I click the "Refresh Now" button
- **Then** the system immediately fetches the latest data
- **And** the display updates.

**AC-4: Conflict Detection and Display**
- **Given** a mechanic is already assigned to a job
- **And** the dispatch planner attempts to assign the same mechanic to another overlapping job
- **Then** the system detects the conflict (BLOCKING: "Mechanic double-booked")
- **And** displays a clear warning preventing the assignment.

**AC-5: HR Availability Integration**
- **Given** a mechanic's shift ends at 5 PM
- **And** an open PTO entry exists for tomorrow 8 AM–12 PM
- **When** I view the Dispatch Board
- **Then** I see the mechanic as "AVAILABLE" today (clocked in)
- **And** see a future PTO indicator for tomorrow.

**AC-6: Break Overlap Grace Period**
- **Given** a mechanic is on a 15-minute break (expected to return 3:00 PM)
- **And** a job is assigned to start at 2:50 PM (10 minutes overlap)
- **When** the system detects this conflict
- **Then** it marks it as WARNING (not BLOCKING), with message: "Job overlaps with expected break time—proceed with caution."

---

## Resolved Questions

### Question 1: SLA Definition (RESOLVED)

**Question:** What is the performance target (SLA) for the Dispatch Board load time?

**Answer:** **P95<2.0s, P50<1.0s, P99<3.5s** for initial load (API gateway receipt → full JSON response)

**Assumptions (SLA Valid Under These Conditions):**
- Warm caches (no cold-start)
- Same-location data only
- Same-day work orders and schedule (no historical lookback)
- One location at a time

**Rationale:**
- Dispatch boards are **operational tools**—delays above 2 seconds feel broken.
- These targets are aggressive but achievable with proper indexing and caching.

**Implementation Notes:**
- Cache mechanic roster (invalidate on clock event, hire/fire, schedule change).
- Cache bay status (invalidate on occupancy change).
- Index work orders by `scheduledDate` and `location`.

### Question 2: Data Refresh Policy (RESOLVED)

**Question:** Should the dashboard use polling, push (WebSocket), or both?

**Answer:** **Hybrid model: Polling every 30 seconds (v1) + optional event-driven push (v2)**

**Version 1 (MVP) — Polling:**
- Backend exposes a single `GET /dashboard/v1/today` endpoint.
- Frontend polls this endpoint every 30 seconds.
- Manual "Refresh Now" button allows immediate refresh.
- Acceptable delay: displayed data may be up to 30 seconds stale.

**Version 2+ (Optional Enhancement) — Event-Driven Push:**
- Emit WebSocket events for high-urgency signals only:
  - Mechanic clock-in / clock-out
  - Job start / job completion
  - Bay status change (occupied → available)
  - Conflict detection
- Push replaces or supplements polling.
- Does NOT attempt full real-time streaming of minor status changes.

**Explicit Non-Goals:**
- Full real-time (millisecond) updates (not needed for dispatch).
- Streaming API for every state change.
- Multi-location merge (each load targets one location).

### Question 3: HR Integration Contract (RESOLVED)

**Question:** What is the contract with the People/HR service for mechanic availability?

**Answer:** **Structured availability signal via GET** `/people/v1/availability` 

**Request:**
```http
GET /people/v1/availability
  ?locationId=LOC-123
  &date=2023-11-01
  &includeSchedule=true
```

**Response Schema:**
```json
{
  "asOf": "2023-11-01T14:30:00Z",
  "location": "LOC-123",
  "people": [
    {
      "personId": "MECH-001",
      "firstName": "John",
      "lastName": "Doe",
      "currentStatus": "ON_JOB",
      "clock": {
        "clockedIn": true,
        "clockedInAt": "2023-11-01T08:00:00Z"
      },
      "break": {
        "onBreak": true,
        "breakStartedAt": "2023-11-01T12:00:00Z",
        "expectedReturnAt": "2023-11-01T13:00:00Z"
      },
      "pto": [
        {
          "ptoId": "PTO-456",
          "start": "2023-11-02T08:00:00Z",
          "end": "2023-11-02T12:00:00Z",
          "ptoType": "VACATION"
        }
      ],
      "scheduledAvailability": [
        {
          "scheduleId": "SCHED-789",
          "start": "2023-11-01T08:00:00Z",
          "end": "2023-11-01T17:00:00Z",
          "shift": "DAY"
        }
      ]
    }
  ]
}
```

**Field Semantics:**
- **`currentStatus`** (required): One of `CLOCKED_OUT`, `AVAILABLE`, `ON_JOB`, `ON_BREAK`, `PTO`.
  - Authoritative for "now"—use this to determine if mechanic can accept a new job.
- **`clock`** (conditional): Present if clocked in; shows when and if they are currently on the clock.
- **`break`** (conditional): Present if currently on break; shows expected return time.
- **`pto`** (array): Future PTO entries; advisory for planning.
- **`scheduledAvailability`** (array): Scheduled shifts; advisory for planning (non-binding—human breaks, changes, etc.).

**Contract Guarantees:**
- Response is **current as of `asOf` timestamp** (not stale beyond 30 seconds if cached).
- `currentStatus` reflects the most recent event (clock, job, break).
- PTO and schedule data are **advisory only**; do not use to block/prevent dispatch decisions. Use `currentStatus` for enforcement.

### Question 4: Conflict Rules (RESOLVED)

**Question:** What are the enumerated conflict conditions, and which are WARNING vs. BLOCKING?

**Answer:** **8 enumerated conditions across 4 categories**

#### Category 1: Mechanic Conflicts (4 conditions)

| # | Condition | Detection | Severity | Message |
|---|---|---|---|---|
| 1a | **Double-Booked Mechanic** | Mechanic already assigned to job J1 (time T1..T2); dispatcher attempts J2 (time T1'..T2') with overlap. | 🔴 BLOCKING | "Mechanic [Name] is already assigned to job [WO-ID] during this time." |
| 1b | **Job Overlaps PTO** | Mechanic has confirmed PTO; dispatcher assigns job during PTO window. | 🔴 BLOCKING | "Mechanic [Name] has scheduled PTO from [start] to [end]." |
| 1c | **Job Overlaps Break (Grace Period)** | Job assigned overlaps with expected break by <15 min; break is confirmed (e.g., lunch approval). | 🟡 WARNING | "Job overlaps with expected break time [start-end]—proceed with caution." |
| 1d | **Clock-Out Mismatch** | Mechanic clocked in for one job, dispatcher assigns a different job without clock-out. | 🟡 WARNING | "Mechanic currently clocked in for job [WO-ID]—clock out before assigning new job?" |

#### Category 2: Resource Conflicts (2 conditions)

| # | Condition | Detection | Severity | Message |
|---|---|---|---|---|
| 2a | **Service Bay Double-Booked** | Bay B1 is assigned to job J1 (time T1..T2); dispatcher assigns J2 (time T1'..T2') with overlap. | 🔴 BLOCKING | "Bay [Name] is already assigned to job [WO-ID] during this time." |
| 2b | **Service Bay Unavailable** | Bay B1 is marked as unavailable (maintenance, broken), but dispatcher attempts assignment. | 🔴 BLOCKING | "Bay [Name] is currently unavailable (reason: [Maintenance/Broken])." |

#### Category 3: Location & Skill Conflicts (2 conditions)

| # | Condition | Detection | Severity | Message |
|---|---|---|---|---|
| 3a | **Location Mismatch** | Job located at LOC-A; mechanic's current location is LOC-B. | 🟡 WARNING | "Mechanic is currently at [Location B]—job is at [Location A]." |
| 3b | **Skill Mismatch (Soft)** | Job requires skill `Electric` (high-voltage work); mechanic's certified skills do not include `Electric`. | 🟡 WARNING | "Mechanic [Name] may not have required skill [Skill]—proceed with caution." |

---

**Severity Semantics:**

- **🟡 WARNING:** The planner is shown the alert but **can proceed** with the assignment. Useful for soft constraints (breaks, location distance, skill gaps).
- **🔴 BLOCKING:** The assignment is **rejected**. The planner **cannot proceed** without resolving the conflict. Used for hard constraints (double-booking, PTO, unavailable resources).

**Validation Rules:**

- Perform conflict checks **at dispatch time** (when mechanic/bay is assigned to a job).
- Display all applicable conflicts (a job may have multiple).
- If any BLOCKING conflict exists, reject the assignment with a clear message.
- Allow the planner to override WARNINGs with an optional comment (audit trail).

---

## Audit & Observability
- **Audit Trail:** All dispatch assignments and conflict detections must be logged with:
  - `workOrderId`, `mechanicId`, `bayId`
  - Conflicts detected and severity
  - User who made the assignment
  - Timestamp
- **Logging:** Structured logs for all dashboard queries and data refreshes.
- **Metrics:**
  - `dashboard.load.latency.ms` (histogram; track P50, P95, P99)
  - `dashboard.poll.frequency` (count of polls per hour)
  - `conflicts.detected.count` (by severity level)
  - `conflicts.user_override.count` (WARNINGs overridden by dispatcher)

---

## Original Story (Unmodified – For Traceability)
## [STORY] Reporting: Daily Dispatch Board Dashboard

**Description:**
A real-time dashboard for Shop Managers to view daily work orders, mechanic availability, and conflicts. The dashboard polls for updates every 30 seconds and detects scheduling conflicts (double-booked mechanics, bay conflicts, skill gaps, etc.) to help prevent overbooking and optimize dispatching.

**Domain**: Work Execution

**Actors:**
- Shop Manager
- Dispatch Coordinator (optional)

**Narrative:**
As a **Shop Manager**, I want a real-time dashboard showing pending work orders, available mechanics, and scheduling conflicts, so that I can make informed dispatch decisions and avoid over-scheduling.

**Acceptance Criteria (Initial):**
1. ✅ Dashboard shows today's pending work orders
2. ✅ Mechanic availability is current (real-time status, PTO, schedule)
3. ✅ Service bay occupancy is visible
4. ✅ Conflicts are detected and flagged with severity (WARNING vs. BLOCKING)
5. ✅ Data auto-refreshes every 30 seconds (polling)
6. ✅ Manual "Refresh Now" button available
7. ✅ Performance target: P95<2.0s load time
8. ✅ Conflict rules are 8 enumerated conditions (mechanic, resource, location/skill)

---
*This issue was automatically created by the Durion Workspace Agent*