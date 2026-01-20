Title: [BACKEND] [STORY] Workexec: Retrieve and Display Estimates for Customer/Vehicle
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/15
Labels: type:story, domain:workexec, status:ready-for-dev, agent:story-authoring, agent:workexec

## 🏷️ Labels (Applied)

- type:story
- domain:workexec
- agent:workexec
- agent:story-authoring
- status:ready-for-dev

---

## Story Intent

Enable Service Advisors to retrieve and display open/draft/approved estimates for a specific customer or vehicle, showing estimate summaries with totals, status, and last updated time. This supports the workflow from quote to appointment creation or checkout, allowing advisors to review work scope and customer approval before proceeding.

---

## Summary of Decisions (from comments)

- Workexec defines a canonical estimate state machine and is authoritative for estimate status and transitions.
- Canonical statuses: `DRAFT`, `OPEN`, `PENDING_CUSTOMER`, `APPROVED`, `DECLINED`, `EXPIRED`, `SCHEDULED`, `INVOICED`, `CANCELLED`, `ARCHIVED`.
- Editable states: `DRAFT`, `OPEN`, `PENDING_CUSTOMER`; locked states: `APPROVED`, `SCHEDULED`, `INVOICED`, `CANCELLED`, `ARCHIVED`.
- Validity: default 30 days (configurable per location); expired estimates block conversion actions; managers can extend validity with permission.
- Clone: deep copy with repricing; clone creates new `estimateId` and `DRAFT` status.
- Cardinality: default one active appointment per estimate (unique `linkedAppointmentId`).
- Approval: customer approval is primary; staff may record approval on behalf with evidence + permission; manager-level approval required above configured thresholds.
- Invoice creation: billing controls invoicing; estimates typically transition to `INVOICED` when billing links the invoice (normal path at/after work completion).
- Duration: `estimatedDurationMinutes` is a scheduling hint derived from line items; appointment may override planned duration without mutating estimate.
- Sync: event-driven updates (SSE) preferred; polling fallback with acceptable staleness ≤60s; server-side caches must invalidate on events.

---

## Acceptance Criteria

1. **Given** Service Advisor searches for customer by name, **When** query executes, **Then** all estimates for that customer in viewable states (DRAFT, OPEN, PENDING_CUSTOMER, APPROVED, QUOTED) are displayed within 2 seconds as a sortable/filterable list showing estimate ID, status, service description, grand total, and last updated date.

2. **Given** Service Advisor searches for vehicle by VIN or license plate, **When** query resolves vehicle, **Then** all estimates linked to that vehicle are displayed with vehicle context (year/make/model/VIN/license plate) at top and grouped by customer or displayed flat as selected.

3. **Given** Service Advisor clicks on estimate in list to view details, **When** detail panel opens, **Then** full estimate information is displayed including customer info, vehicle info, itemized service lines with unit prices and line totals, subtotal, tax breakdown by tax type, fee breakdown, grand total, service notes, validity date, approval status, and related appointments/invoices.

4. **Given** Estimate has passed validity date, **When** detail panel displays, **Then** warning banner shows "Estimate expired on [date]" and "Create Appointment" button is disabled. "Clone Estimate" option is available to create new estimate.

5. **Given** Service Advisor applies filters (status, date range, amount range, approval status), **When** filters are applied, **Then** estimate list is updated within 1 second showing only matching estimates and count of filtered vs. total results is displayed.

6. **Given** Estimate status changes externally (e.g., customer approves via portal), **When** Service Advisor is viewing estimate list, **Then** estimate row updates within 5 seconds showing new status, and notification is optional but recommended (e.g., "Estimate #123 approved").

7. **Given** Service Advisor clicks "Create Appointment" on APPROVED/QUOTED estimate, **When** appointment creation flow initiates, **Then** form is pre-populated with customer info, vehicle info, service description, estimated duration, and service cost from estimate. Estimate ID is captured as reference link. Estimated duration validation and conflict detection proceed as normal appointment creation.

8. **Given** Estimate requires manager-level approval (amount ≥ configured threshold), **When** Service Advisor opens estimate detail, **Then** warning displays "Estimate exceeds approval threshold; requires manager approval" and "Create Appointment" action is disabled until approved.

9. **Given** Service Advisor has view permission for only one location but searches for estimate, **When** query executes, **Then** only estimates from permitted location are displayed. Attempt to view estimate from other location shows permission denial message.

10. **Given** Work Execution Service becomes unavailable, **When** Service Advisor attempts to load estimates, **Then** error message displays "Unable to load estimates; service temporarily unavailable" with option to "Retry Now" or "View cached estimates from [timestamp]" if recent cache available.

---

## Implementation Notes / Suggested Endpoints

- `GET /estimates?customerId={id}&status=OPEN,APPROVED,PENDING_CUSTOMER` -> returns `EstimateSummary[]` (paginated)
- `GET /estimates?vehicleId={id}` -> returns `EstimateSummary[]` for vehicle
- `GET /estimates/{estimateId}` -> returns `EstimateDetail` with `lineItems`, `taxDetails`, `auditLog`
- SSE endpoint: `/events/estimates?locationId={id}` -> emits `EstimateCreated`, `EstimateUpdated`, `EstimateStatusChanged` events
- Materialized read model recommended for low-latency list reads; invalidate on events

---

## Next Steps

- Remove `blocked:clarification`, add `status:ready-for-dev`, assign domain and agent labels.
- Create follow-up implementation tasks: list endpoint, detail endpoint, SSE subscription support, clone/clone-with-reprice flow, permission checks for recording approvals, and tests.
- Hand off to backend dev with this issue as canonical source.
