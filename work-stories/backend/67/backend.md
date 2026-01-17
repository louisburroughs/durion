Title: [BACKEND] [STORY] Timekeeping: Capture Mobile Travel Time Separately
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/67
Labels: type:story, domain:workexec, status:ready-for-dev

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:ready-for-dev

### Recommended
- agent:workexec
- agent:story-authoring

---
**Rewrite Variant:** workexec-structured
---
## Story Intent
**As a** Mobile Lead,
**I want to** accurately record travel time segments associated with a mobile work assignment,
**So that** technician availability is correctly blocked in the scheduling system and payroll calculations are accurate.

This story establishes the core capability to capture and manage the lifecycle of travel time for off-site work, ensuring it is treated as a first-class component of a mobile assignment.

## Actors & Stakeholders
- **Mobile Technician**: The primary actor who performs the travel and whose time is being recorded.
- **Mobile Lead / Service Advisor**: The actor responsible for overseeing mobile assignments and ensuring time is logged correctly. May have permissions to review or correct entries.
- **Scheduler**: A stakeholder who consumes technician availability data to plan future work.
- **Payroll System**: A downstream system that consumes travel time data to process compensation.
- **System**: The POS/Shop Management platform itself.

## Preconditions
1. A valid, scheduled `MobileWorkAssignment` exists in the system.
2. A `MobileTechnician` is assigned to the `MobileWorkAssignment`.
3. The system has a defined set of `TravelSegment` types (e.g., DEPART_FROM_SHOP, ARRIVE_AT_CUSTOMER, DEPART_FROM_CUSTOMER, ARRIVE_AT_SHOP).

## Functional Behavior
1. **Initiate Travel**: The assigned Mobile Technician can initiate a "travel" activity against the active Mobile Work Assignment.
2. **Select Segment Type**: The system will prompt the technician to select the type of travel segment they are starting (e.g., "Depart from Shop to Customer").
3. **Start Travel Segment**: Upon selection, the system creates a new `TravelSegment` record associated with the `MobileWorkAssignment`.
    - The `startTime` is recorded as the current timestamp.
    - The status of the segment is set to `InProgress`.
    - The technician's availability status is updated to `InTransit`.
4. **End Travel Segment**: When the technician completes a travel leg (e.g., arrives at the customer's location), they end the current travel segment.
    - The system records the `endTime` on the `TravelSegment` record.
    - The status is updated to `Completed`.
    - The technician's availability status is updated (e.g., to `OnSite` or `Available`).
5. **System Integration**: Upon successful completion of a `TravelSegment`, the system will generate and publish a `TravelTimeRecorded` event to be consumed by downstream systems, such as the Payroll System.

## Alternate / Error Flows
- **Invalid State Transition**: If a technician attempts to start a travel segment when one is already `InProgress` for the same assignment, the system will return an error indicating an active travel leg must be completed first.
- **Non-Mobile Assignment**: If a user attempts to log travel time against a work assignment that is not designated as "mobile," the system will reject the request with an informative error.
- **Authorization Failure**: If a user other than the assigned technician or an authorized lead attempts to modify a travel segment, the request will be denied.

## Business Rules
- Travel time is considered billable and/or paid time, as per company policy.
- A technician's availability MUST be blocked and reflected as `InTransit` on the scheduling calendar for the duration of any `InProgress` or `Completed` travel segment.
- The creation, start, and end of travel segments must be auditable events.
- Business rules for applying automatic time buffers (e.g., adding 15 minutes before departure) must be configurable but are not defined for this story. See Resolved Questions.

## Data Requirements
**Entity: `TravelSegment`**
- `travelSegmentId`: (UUID, PK) Unique identifier for the segment.
- `mobileWorkAssignmentId`: (UUID, FK) Reference to the parent work assignment.
- `technicianId`: (UUID, FK) Reference to the assigned technician.
- `segmentType`: (Enum) The type of travel (e.g., `DEPART_SHOP_TO_CUSTOMER`, `DEPART_CUSTOMER_TO_SHOP`).
- `startTime`: (Timestamp with Timezone) The recorded start of the travel segment.
- `endTime`: (Timestamp with Timezone, Nullable) The recorded end of the travel segment.
- `durationMinutes`: (Integer, Calculated) Calculated duration upon completion.
- `status`: (Enum) `InProgress`, `Completed`, `Cancelled`.

**Event: `TravelTimeRecorded`**
- `eventId`: (UUID)
- `eventTimestamp`: (Timestamp)
- `payload`:
    - `travelSegmentId`: (UUID)
    - `mobileWorkAssignmentId`: (UUID)
    - `technicianId`: (UUID)
    - `startTime`: (Timestamp)
    - `endTime`: (Timestamp)
    - `durationMinutes`: (Integer)

## Acceptance Criteria
**Scenario 1: Successfully Record a Travel Segment**
- **Given** a Mobile Technician is assigned to an active Mobile Work Assignment
- **When** the technician initiates a "Depart from Shop" travel segment
- **And** they later end that segment upon arrival at the customer location
- **Then** the system must create a `TravelSegment` record with a `segmentType` of `DEPART_SHOP_TO_CUSTOMER`.
- **And** the record must have an accurate `startTime` and `endTime`.
- **And** the technician's availability in the scheduling system for that time block must show as `InTransit`.

**Scenario 2: Downstream System is Notified of Travel Time**
- **Given** a `TravelSegment` has been successfully completed with a start and end time
- **When** the system processes the completion of the segment
- **Then** a `TravelTimeRecorded` event must be published to the designated integration point (e.g., message queue or webhook).
- **And** the event payload must contain the `technicianId`, `startTime`, `endTime`, and `durationMinutes`.

**Scenario 3: Attempt to Log Travel for a Non-Mobile Job**
- **Given** a technician is assigned to a standard, in-shop Work Order
- **When** the technician attempts to start any type of travel segment against that order
- **Then** the system must reject the request with an error message stating "Travel time can only be logged for mobile assignments."

## Audit & Observability
- All state changes to a `TravelSegment` (create, start, complete, cancel) must be recorded in an immutable audit log, including the acting user and timestamp.
- The publication of the `TravelTimeRecorded` event must be logged, and system monitoring should include alerts for repeated publication failures to the HR/Payroll integration endpoint.
- Metrics should be captured for the average duration of travel segments to help with future scheduling estimates.

## Resolved Questions

### RQ1 (Travel Segment Types)
**Question:** What is the definitive, enumerated list of travel segment types we must support?

**Resolution:** The following travel segment types are definitive for **v1**:

**Required Segment Types (v1):**
1. **`DEPART_SHOP`** - Technician leaving shop/base location to travel
2. **`ARRIVE_CUSTOMER_SITE`** - Technician arriving at customer location
3. **`DEPART_CUSTOMER_SITE`** - Technician leaving customer location
4. **`ARRIVE_SHOP`** - Technician returning to shop/base location
5. **`TRAVEL_BETWEEN_SITES`** - Direct travel from one customer site to another (no return to shop)
6. **`DEADHEAD`** - Travel not directly tied to customer/work order (e.g., repositioning vehicle, moving between territories)

**Each Segment Records:**
- `startAt`: Timestamp (required)
- `endAt`: Timestamp (required when completed)
- `fromLocationId`: UUID (nullable for customer sites without location records)
- `toLocationId`: UUID (nullable for customer sites)
- `workOrderId`: UUID (optional, for customer-specific travel; not applicable to DEADHEAD)

**Optional Future Segment Types (NOT required for v1):**
- `FUEL_STOP` - Stop for vehicle refueling
- `PARTS_RUN` - Emergency parts pickup during mobile assignment

**Typical Flow Examples:**
- **Single Customer Visit:** `DEPART_SHOP` → `ARRIVE_CUSTOMER_SITE` → `DEPART_CUSTOMER_SITE` → `ARRIVE_SHOP`
- **Multi-Customer Route:** `DEPART_SHOP` → `ARRIVE_CUSTOMER_SITE` → `DEPART_CUSTOMER_SITE` → `TRAVEL_BETWEEN_SITES` → `ARRIVE_CUSTOMER_SITE` → `DEPART_CUSTOMER_SITE` → `ARRIVE_SHOP`
- **Repositioning:** `DEPART_SHOP` → `DEADHEAD` → `ARRIVE_SHOP`

**Rationale:** This comprehensive set covers the full lifecycle of mobile operations while maintaining simplicity. The six types enable accurate tracking for scheduling (availability blocking), payroll (compensable travel time), and analytics (route efficiency). Future segment types can be added without breaking existing data model.

---

### RQ2 (Buffer Policies)
**Question:** What are the specific policies for automatically applying time buffers? Who defines them?

**Resolution:** Buffer policies are **policy-driven, owned by domain:people / timekeeping (HR policy)**, and configurable per location.

**Required Policies (v1):**
1. **Minimum Travel Rounding Increment**
   - Purpose: Round travel time to nearest increment for payroll
   - Example: Round to nearest 5 minutes
   - Config: `roundingIncrementMinutes` (default: 5)

2. **Minimum Billable Travel Floor**
   - Purpose: Minimum compensable travel time per segment
   - Example: If travel > 0 minutes, minimum 10 minutes paid
   - Config: `minTravelMinutes` (default: 0, can be set to 5, 10, 15)

3. **Standard Prep/Close Buffer**
   - Purpose: Auto-add time for first departure and last return of day
   - Example: +5 minutes at day start (loading tools), +5 at day end (unloading)
   - Config: `dayStartBufferMinutes` (0-5), `dayEndBufferMinutes` (0-5)

4. **Inter-Site Travel Buffer**
   - Purpose: Account for customer site parking, access delays
   - Example: +2 minutes for `TRAVEL_BETWEEN_SITES`
   - Config: `betweenSitesBufferMinutes` (0-2)

**Configuration Data Model:**
```json
{
  "locationId": "uuid",
  "policyVersion": "v1.2",
  "effectiveFrom": "2025-01-01T00:00:00Z",
  "roundingIncrementMinutes": 5,
  "minTravelMinutes": 0,
  "dayStartBufferMinutes": 5,
  "dayEndBufferMinutes": 5,
  "betweenSitesBufferMinutes": 2
}
```

**Policy Management:**
- Owned by: HR / Payroll administrator role
- Configured via: People / Timekeeping admin UI or API
- Versioned: Changes create new policy version (for audit)
- Applied at: Time approval or daily rollup (not real-time during capture)

**Application Timing:**
- Raw travel time captured exactly as recorded by technician
- Buffers applied during:
  1. Daily time summary calculation
  2. Approval workflow (visible to manager)
  3. Export to HR/Payroll system
- Store both: `rawMinutes` and `bufferedMinutes` for audit

**Rationale:** Policy-driven approach allows business rules to evolve without code changes. Per-location configuration accommodates different union agreements, state laws, or business practices. Explicit versioning and separate raw/buffered fields maintain full audit trail and transparency.

---

### RQ3 (HR/Payroll Integration Contract)
**Question:** What is the precise data schema and transport mechanism for sending travel time to the HR system?

**Resolution:** **Asynchronous, fire-and-forget event** via message queue. No synchronous HR response required for operational workflow.

**Integration Pattern:**
- **Transport:** Message queue topic: `timekeeping.travel.approved.v1`
- **Delivery:** At-least-once (HR must handle idempotency by `eventId`)
- **Timing:** Event published when travel segments are approved (not on every segment completion)

**Event Schema - TravelTimeApproved:**
```json
{
  "eventId": "uuid",
  "eventType": "TravelTimeApproved",
  "schemaVersion": "1.0.0",
  "occurredAt": "2025-01-15T18:30:00Z",
  "payload": {
    "personId": "uuid",
    "workDate": "2025-01-15",
    "locationId": "uuid",
    "segments": [
      {
        "travelSegmentId": "uuid",
        "type": "DEPART_SHOP",
        "startAt": "2025-01-15T08:00:00Z",
        "endAt": "2025-01-15T08:25:00Z",
        "minutes": 25,
        "workOrderId": "WO-123"
      },
      {
        "travelSegmentId": "uuid",
        "type": "ARRIVE_CUSTOMER_SITE",
        "startAt": "2025-01-15T08:25:00Z",
        "endAt": "2025-01-15T08:27:00Z",
        "minutes": 2,
        "workOrderId": "WO-123"
      }
    ],
    "totals": {
      "rawMinutes": 167,
      "bufferedMinutes": 180,
      "policyVersion": "v1.2"
    },
    "approval": {
      "approvedBy": "uuid",
      "approvedAt": "2025-01-15T18:30:00Z"
    }
  }
}
```

**Key Fields:**
- `segments[]`: Array of all travel segments for the day (detailed breakdown)
- `rawMinutes`: Total travel time as recorded (before buffers)
- `bufferedMinutes`: Total travel time after policy buffers applied
- `policyVersion`: Which buffer policy was applied (for audit)
- `approvedBy` / `approvedAt`: Approval metadata

**Excluded from Event:**
- ❌ Pay rates (HR/Payroll owns compensation rules)
- ❌ Dollar amounts (Timekeeping provides minutes only)
- ❌ Tax implications (Payroll responsibility)

**Error Handling:**
- Timekeeping: Logs event publication, retries on transient failures
- HR/Payroll: Consumes asynchronously, handles own retry/DLQ
- **No synchronous acknowledgment required** - operational workflow not blocked

**Ingestion Guarantee:**
- HR system must handle duplicate events (idempotency by `eventId`)
- HR system responsible for its own error recovery

**Rationale:** Async integration prevents operational workflow (travel capture, approval) from being blocked by HR system downtime. Clean domain separation: Timekeeping tracks time worked; HR/Payroll handles compensation. Including both raw and buffered minutes provides transparency for audits and dispute resolution.

---

### RQ4 (Permissions)
**Question:** Can a Mobile Lead or Service Advisor create, edit, or delete travel segments on behalf of a technician? Under what conditions?

**Resolution:** Yes, **on-behalf edits are allowed** under strict conditions with full audit trail.

**Permission Model:**
- **Technician:** `timekeeping:travel_segment:create`, `timekeeping:travel_segment:edit_self`, `timekeeping:travel_segment:delete_self`
- **Mobile Lead / Service Advisor:** `timekeeping:travel_segment:create_any`, `timekeeping:travel_segment:edit_any`, `timekeeping:travel_segment:delete_any`

**Conditions for On-Behalf Edits:**
1. **Status Restriction:** On-behalf edits allowed only when segment `status = DRAFT` or `SUBMITTED`
   - NOT allowed when `status = APPROVED`
2. **Scope Restriction:** Editor's role must have scope covering the technician's assigned location
   - Example: Mobile Lead at Location A cannot edit segments for technician at Location B
3. **Audit Requirement:** Every on-behalf action MUST record:
   - `actedByUserId`: ID of the person making the change
   - `actedForPersonId`: ID of the technician whose time is being changed
   - `reasonCode`: Required enum (e.g., `TECHNICIAN_UNAVAILABLE`, `FORGOT_TO_CLOCK`, `DATA_ENTRY_ERROR`)
   - `timestamp`: When the on-behalf action occurred

**Approved Segment Edits (Post-Approval):**
If a segment is already `APPROVED` and needs correction:
- **Cannot** directly edit/delete the segment
- **Must** create an `TravelSegmentAdjustment` record (similar to TimeEntryAdjustment pattern)
- Adjustment requires:
  - Manager approval
  - Explicit audit reason
  - Links to original segment
- Original segment remains unchanged for audit trail

**Data Model Extension:**
```
TravelSegment {
  // ... existing fields ...
  createdBy: UUID
  lastModifiedBy: UUID
  lastModifiedAt: Timestamp
  actedByUserId: UUID (nullable, populated for on-behalf edits)
  actedForPersonId: UUID (nullable, populated for on-behalf edits)
  onBehalfReasonCode: Enum (nullable)
}
```

**UI Indicators:**
- Display badge/indicator when segment was created/edited on-behalf
- Show "Edited by [Name] on behalf of [Technician]" in audit trail
- Require reason selection for all on-behalf actions

**Rationale:** On-behalf editing addresses real operational needs:
- Technicians forget to clock travel segments
- Technical issues prevent mobile app access
- Data entry corrections needed before approval

Strict conditions and full audit trail prevent abuse while enabling operational flexibility. Post-approval correction via adjustment records maintains immutable audit trail for compliance and dispute resolution.

## Original Story (Unmodified – For Traceability)
# Issue #67 — [BACKEND] [STORY] Timekeeping: Capture Mobile Travel Time Separately

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Timekeeping: Capture Mobile Travel Time Separately

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Mobile Lead**, I want to record travel time for a mobile appointment so that availability and payroll are accurate.

## Details
- Travel segments depart/arrive/return.
- Policies may auto-apply buffers.

## Acceptance Criteria
- Segments recorded.
- Sent to HR.
- Availability blocked during travel.

## Integrations
- Shopmgr→HR TravelTime events/API.

## Data / Entities
- TravelSegment, MobileAssignmentRef

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