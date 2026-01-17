Title: [BACKEND] [STORY] Appointment: Show Assignment (Location/Bay/Mobile + Mechanic)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/10
Labels: type:story, domain:shopmgmt, status:ready-for-dev

## Story Intent

Enable **Service Advisors** to view the current assignment of shop resources (bay, mobile unit) and mechanic for each appointment, allowing them to set accurate customer expectations for service timing, location, and technician availability, while ensuring updates are reflected in near real-time as assignments change.

This story provides **read-only visibility** into shop resource allocation without modifying assignment logic, supporting operational awareness and customer communication.

---

## Actors & Stakeholders

### Primary Actors
- **Service Advisor**: Views assignment details to communicate expectations to customer
- **Shop Management Service**: System of record for assignments (bay, mobile unit, mechanic)
- **People/HR Service**: Provides mechanic identity information (name, photo, certifications)
- **Appointment Service**: Provides appointment context (customer, vehicle, service type)

### Secondary Stakeholders
- **Customer**, **Shop Manager**, **Mechanic**, **Dispatcher**, **Audit Service**, **Finance Team**

---

## Preconditions

- Appointment exists in a schedulable or active state (SCHEDULED, IN_PROGRESS, AWAITING_PARTS)
- Service Advisor is authenticated and has `VIEW_ASSIGNMENTS` permission scoped to their facility
- Shop Management Service is available (or fallback to cached assignment)
- Assignment data exists for the appointment (may be null if not assigned)

---

## Resolved: Answers to Open Questions (canonical decisions)

### Q1: Source of truth — Decision
`shopmgmt` (Shop Management Service) is authoritative for assignments. All writes go through `shopmgmt`, which persists state and emits `ASSIGNMENT_UPDATED` events; consumers (POS) treat it as the system of record.

---

### Q2: Refresh strategy — Decision
Hybrid approach:
- Preferred: WebSocket/SSE subscription to `ASSIGNMENT_UPDATED` for currently viewed appointments (target p95 latency <5s).
- Fallback: Poll every 30s for visible appointment views.
- Manual `Refresh` always available; immediate fetch on entering detail view.

---

### Q3: Permission model — Decision
RBAC + facility scoping:
- Roles with `VIEW_ASSIGNMENTS`: Service Advisor (facility-scoped), Shop Manager (facility-scoped), Dispatcher (org-scoped).
- Cashiers and Parts Clerks denied by default.
- Allow viewing for SCHEDULED, IN_PROGRESS, AWAITING_PARTS, READY; exclude CANCELLED unless user has `VIEW_CANCELLED_APPOINTMENTS`.

---

### Q4: Mobile GPS tracking — Decision
Mobile units report GPS to `shopmgmt` (recommended every 5 minutes). Display last-known coords and `lastUpdated`; warn if >30 minutes stale and mark unavailable if >60 minutes. Reverse geocoding optional and feature-flagged.

---

### Q5: Assignment notes ownership — Decision
`shopmgmt` owns notes; editable by roles with `EDIT_ASSIGNMENT_NOTES` (Shop Manager, Dispatcher). Notes: optional, 500-char limit, audited (versioned) in assignment history. Service Advisors view-only.

---

### Q6: Bay vs Mobile mutual exclusivity — Decision
An appointment is either BAY or MOBILE_UNIT (mutually exclusive). Shopmgmt enforces this rule; future multi-leg work should be modeled as separate work items.

---

### Q7: Customer communication on changes — Decision
This story is internal visibility only: POS shows prominent banner on assignment changes so advisors can notify customers; automated customer notifications are out-of-scope and should be a separate story.

---

## Functional Behavior (summary)
- Appointment detail shows `AssignmentView` with `location`, `mechanic`, `assignmentNotes`, `assignedAt`, `lastUpdatedAt`.
- List views show compact assignment summaries (e.g., `Bay 3 - J. Doe`, `Unassigned`).
- Real-time updates via events or polling; UI displays a notification banner on changes.
- Degraded behavior: when `shopmgmt` or `people/hr` unavailable, display cached last-known assignment or mechanic ID with a warning.

---

## Data Requirements (referenced models)
- `AssignmentView`, `LocationRef`, `MechanicRef`, `AssignmentUpdatedEvent` (as defined in original story). `assignmentNotes` limited to 500 chars and versioned.

---

## Acceptance Criteria (high level)
- Assignments visible for assigned/partially-assigned/unassigned appointments per AC1–AC10.
- Real-time or near-real-time updates reflected (p95 <5s for event push; polling fallback 30s).
- Permissions and facility scoping enforced.
- Mobile GPS staleness warnings shown per thresholds.
- Assignment notes editable only by manager/dispatcher and audited.

---

*Status: ready for development.*
