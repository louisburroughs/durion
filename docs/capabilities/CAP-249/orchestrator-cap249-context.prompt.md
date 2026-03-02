---
name: "CAP-249 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-249 Appointment Scheduling & Assignment (Shopmgr Coordination)."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-249 runs.

## Capability Scope
- Capability: `CAP:249` — Appointment Scheduling & Assignment (Shopmgr Coordination)
- Parent issue: `louisburroughs/durion#249`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#10` — Appointment: Show Assignment (Location/Bay/Mobile + Mechanic)
  - `louisburroughs/durion-positivity-backend#11` — Appointment: Reschedule Appointment with Notifications
  - `louisburroughs/durion-positivity-backend#12` — Appointment: Create Appointment from Estimate or Order

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-249/CAPABILITY_MANIFEST.yaml` (Assumed location)
2. Story issues:
   - `#10`, `#11`, `#12` in `durion-positivity-backend`
3. Domain guides and contract references:
   - `durion/domains/shopmgmt/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/shopmgmt/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/people/.business-rules/BACKEND_CONTRACT_GUIDE.md`

## Domain Connections & Ownership (`shop-manager`)

The `shop-manager` (`shopmgmt`) domain is the central orchestrator for appointment and scheduling-related activities. It has the following key dependencies and responsibilities:

### 1. `workexec` (Work Execution)
- **Role**: Authoritative source for service durations.
- **Interaction**:
    - `shopmgmt` **reads** planned duration from `workexec` when creating an appointment from a work order.
    - `shopmgmt` **emits events** (`AppointmentCreated`, `AppointmentRescheduled`) that `workexec` consumes to keep its own schedule synchronized.
- **Contract**: Event-driven. `shopmgmt` is the producer, `workexec` is the consumer.

### 2. `people` (People/HR)
- **Role**: System of record for mechanic qualifications, skills, and availability.
- **Interaction**:
    - `shopmgmt` **reads** mechanic profiles, skills, and certifications from the `people` service to validate assignments.
    - The `people` service is the source of truth for skill-based scheduling decisions made within `shopmgmt`.
- **Contract**: Request-response or cached read-model. `shopmgmt` is the consumer.

### 3. `notification`
- **Role**: Manages all outbound communications related to appointments.
- **Interaction**:
    - `shopmgmt` **triggers** the `notification` service to send SMS or email alerts to customers and mechanics.
    - Key triggers include appointment rescheduling, assignment changes, and confirmations.
- **Contract**: Asynchronous command. `shopmgmt` is the producer/caller.

### 4. `audit`
- **Role**: Central repository for logging significant business events.
- **Interaction**:
    - `shopmgmt` **sends detailed logs** to the `audit` service for all critical state changes.
    - This includes creating/rescheduling appointments, overriding scheduling conflicts, and modifying resource assignments.
- **Contract**: Asynchronous command/event. `shopmgmt` is the producer.

### 5. `workorder` / `order`
- **Role**: Provides the source documents (estimates, work orders) for appointments.
- **Interaction**:
    - `shopmgmt` **reads** customer, vehicle, and service details from estimates and work orders to initiate appointment creation.
    - `shopmgmt` **updates the status** of the source estimate/work order (e.g., to `SCHEDULED`) after an appointment is successfully created.
- **Contract**: Request-response and status updates. `shopmgmt` is both a consumer (reading source data) and a producer (updating status).

## Story-Specific Non-Negotiables

### Story #10 (Show Assignment)
- `shopmgmt` is the **authoritative source of truth** for all assignment data (bay, mobile unit, mechanic).
- Data refresh must use a hybrid model: WebSocket/SSE for real-time updates, with a polling fallback.
- Permissions (`VIEW_ASSIGNMENTS`) must be strictly enforced with facility-level scoping.

### Story #11 (Reschedule Appointment)
- A reschedule reason (from a mandatory enum) must be captured for every change.
- Hard vs. Soft conflicts must be clearly distinguished, with overrides requiring specific permissions (`OVERRIDE_SCHEDULING_CONFLICT`) and being fully audited.
- The `notification` service must be invoked to inform customers and mechanics of changes.
- The `workexec` service must be notified via an `AppointmentRescheduled` event.

### Story #12 (Create Appointment)
- Appointment creation must be sourced from an eligible estimate or work order.
- Duration must be validated against the `workexec` service if a work order exists.
- Mechanic assignment must be validated against skills from the `people` service.
- Bay/resource availability and other conflicts must be checked before creation.
- An `AppointmentCreated` event must be emitted to `workexec`.

## Blocker Policy for CAP-249
- Do not mark a story as complete if its external domain integrations (as defined above) are not implemented and tested.
- If blocked by a domain dependency, clearly state the missing contract, the impacted story, and the proposed remediation step.
- For example: "Story #11 is blocked by missing `AppointmentRescheduled` event contract with `workexec`. Proposed remediation: Define and implement the event contract, then update `shopmgmt` to emit the event on reschedule."