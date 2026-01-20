Title: [BACKEND] [STORY] Workexec: Display Work In Progress Status for Active Workorders
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/14
Labels: type:story, domain:workexec, status:ready-for-dev, agent:story-authoring, agent:workexec

## 🏷️ Labels (Applied)

- type:story
- domain:workexec
- agent:workexec
- agent:story-authoring
- status:ready-for-dev

---

## Story Intent

Enable Counter Associates to view work-in-progress (WIP) status for active workorders in real-time, showing individual order status (waiting, in progress, parts pending, ready, completed), assigned mechanic and location, and estimated completion time. This supports accurate customer inquiries and order tracking.

---

## Actors & Stakeholders

- **Counter Associate** (Primary User): Queries WIP status to provide customer updates, manage customer expectations, escalate delays
- **Mechanic** (Work Executor): Executes work, updates work order status through Work Execution Service
- **Shop Manager / Dispatcher** (Resource Owner): Manages bay/mobile unit assignments, oversees shop floor operations, handles exceptions
- **Work Execution Service** (Authoritative Source): Owns work order status lifecycle, emits StatusChanged events
- **Shop Management Service** (Assignment Authority): Owns bay/mechanic assignment context, provides location/assignment details
- **Notification Service** (Integration): Delivers status change notifications (optional, if real-time push required)
- **Counter Management System** (UI Container): Front-end application hosting WIP view
- **Audit Service** (Compliance): Records all status views and state transitions

---

## Summary of Decisions (from comments)

- Default to real-time subscription via SSE with polling fallback (30s) and staleness indicator (>60s).
- Shop Management is authoritative for mechanic assignments; WIP shows UNASSIGNED and queue position when applicable.
- Canonical statuses are stored in Workexec; display labels are configurable per location (Shopmgmt).
- In AWAITING_PARTS state, show full part breakdown (Inventory is SoR; Workexec supplies blocking parts list).
- Escalation is notification/tasking only; Counter Associate cannot force state changes.
- No automatic customer notifications in this story; manual "Send update" is permitted (permissioned) and WIP emits events for downstream Notification Service.
- Default view is single-location; multi-location visibility enabled via permission `WIP_VIEW_ALL_LOCATIONS`.

---

## Acceptance Criteria

1. **Given** Counter Associate has opened WIP dashboard, **When** page loads, **Then** all active workorders (status in SCHEDULED, WAITING, IN_PROGRESS, AWAITING_PARTS, READY_FOR_PICKUP) are displayed within 2 seconds with current status, assigned mechanic, and location visible for each order.

2. **Given** Counter Associate is viewing WIP dashboard with real-time subscription active, **When** mechanic updates workorder status (e.g., "In Progress" → "Parts Pending"), **Then** UI updates within 5 seconds with new status, reason, and estimated completion time highlighted.

3. **Given** Counter Associate is viewing WIP dashboard with polling enabled, **When** 30 seconds elapse, **Then** data is automatically refreshed from Work Execution Service and any status changes are reflected in UI with "Last updated X seconds ago" timestamp visible.

4. **Given** Counter Associate clicks on a workorder row in WIP list, **When** detail panel opens, **Then** full workorder information is displayed including customer name/phone, vehicle info (year/make/model/VIN), service description, full status history with timestamps, assigned mechanic with photo/certifications, assigned bay/location, estimated completion time, parts status, and internal notes.

5. **Given** Counter Associate is viewing detail panel for unassigned workorder, **When** panel loads, **Then** status shows "Not yet assigned" with expected assignment time, available mechanic options are displayed with skill match indicators, and Counter Associate can see estimated wait time until assignment.

6. **Given** Counter Associate is viewing WIP dashboard, **When** applying filters (by mechanic, bay, status, or date), **Then** list is filtered correctly and count of visible/total workorders is displayed accurately.

7. **Given** Work Execution Service becomes unavailable, **When** WIP view detects service timeout after 3 retries, **Then** display shows cached data with red warning banner "Real-time updates unavailable; last updated [timestamp]", polling pauses, and "Retry Connection" button is available. When service recovers, automatic reconnect occurs within 10 seconds.

8. **Given** Multiple workorders exist for same customer vehicle, **When** Counter Associate views WIP list, **Then** workorders are displayed as separate rows with option to group by customer or vehicle, and "Compare Status" view can be toggled to show side-by-side details.

9. **Given** Mechanic updates status while Counter Associate has detail panel open, **When** status change event is received, **Then** detail panel is refreshed automatically with changed fields highlighted, and notification indicates "Status updated while viewing".

10. **Given** Counter Associate has location/role-based permission restrictions, **When** WIP dashboard loads, **Then** only permitted workorders are visible (filtered by location and/or role), restricted actions (escalate, notify, mark ready) are disabled, and explanation message is displayed for hidden workorders.

---

## Open Questions (answered)

- Q1 (Real-time vs Polling): Real-time (SSE) default with 30s polling fallback; staleness >60s.
- Q2 (Assignment Authority): Shop Management authoritative; display UNASSIGNED and queue position.
- Q3 (Status Mapping): Canonical statuses in Workexec; display mapping from Shop Management per location.
- Q4 (Parts Data): Inventory owns detailed parts data; Workexec provides blockingParts[]; WIP resolves details from Inventory.
- Q5 (Escalation): Notification/task only; default SLA triggers defined; Counter Associate cannot change state.
- Q6 (Customer Notifications): Manual send only in this story; automatic notifications out of scope.
- Q7 (Multi-location): Default single-location; multi-location via `WIP_VIEW_ALL_LOCATIONS` permission.

---

## Audit & Observability (required events + key metrics)

(omitted here for brevity — original content preserved in comments and previous body)

---

## Implementation Notes / Suggested Endpoints

- `GET /wip?locationId={id}&status=IN_PROGRESS,AWAITING_PARTS&includeAssignments=true` -> returns `WorkorderStatusView[]` (paginated)
- `GET /wip/{workorderId}` -> returns `WorkorderStatusView` with `statusHistory`, `partsConsumption`, `assignmentContext`
- SSE endpoint: `/events/wip?locationId={id}` -> emits `WorkorderStatusChanged` events
- Materialized Read Model recommended: `wip_view` updated by Workexec + Inventory + Shopmgmt sync for low-latency reads

---

## Next Steps

- Remove `blocked:clarification`, add `status:ready-for-dev`, assign domain and agent labels.
- Create follow-up implementation tasks: API endpoints, read-model migration, SSE gateway integration, access control checks, and tests.
- Hand off to backend dev with this issue as canonical source.
