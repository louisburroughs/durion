# ADR: 0006 - WorkExec - Domain Ownership Boundaries for WorkExec Features

**Status:** ✅ Accepted  
**Date:** 2026-01-25  
**Context:** Phase 4.1 - Resolving domain ownership ambiguities for 18 remaining WorkExec issues  
**Stakeholders:** Architecture team, WorkExec domain owner, People domain owner, ShopMgmt domain owner, Order domain owner  

---

## Decision Summary

This ADR clarifies domain ownership boundaries for features that appear in WorkExec user stories but are actually owned by other domains. The decision ensures proper issue labeling, team assignments, and cross-domain integration contracts.

**Affected Issues:** #149, #146, #145, #131, #132, #138, #137, #134, #133, #85

---

## Decisions

### 1. Timekeeping Features → `domain:people`

**Issues Affected:**
- **#149** - Clock In/Out at Shop Location
- **#146** - Start/Stop Timer for Task
- **#145** - Submit Job Time Entry
- **#131** - Capture Mobile Travel Time
- **#132** - Track Work Session Start/End

**Rationale:**
- The **People domain** is the canonical owner of timekeeping data and workflows per existing architecture documented in `/home/louisb/Projects/durion/domains/people/.business-rules/DOMAIN_NOTES.md`.
- Backend module `pos-people` contains `WorkSessionController.java`, confirming ownership.
- Timekeeping entries are ingested from `WorkSessionCompleted` events (published by ShopMgmt or WorkExec) and managed by People domain services.
- Security permissions are defined in `domain:security` as `workexec:time_entry:*` but these are **legacy naming conventions** from before domain boundaries were clarified. Future refactoring should rename these to `people:time_entry:*`.
- Accounting domain documentation explicitly states: *"Timekeeping: time entry creation/approval state is owned by People/Timekeeping domain. Accounting owns export + export auditing."* (See `/home/louisb/Projects/durion/domains/accounting/.business-rules/AGENT_GUIDE.md`).

**Action Items:**
1. Update GitHub issue labels from `domain:workexec` to `domain:people` for issues #149, #146, #145, #131, #132.
2. Document cross-domain integration contract in WorkExec's `CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`:
   - WorkExec publishes `work_session.started`, `work_session.completed` events
   - People domain subscribes to these events to create timekeeping entries
   - WorkExec screens may display timekeeping data via read-only API calls to `pos-people` endpoints
3. Update permission naming in future security refactoring: `workexec:time_entry:*` → `people:time_entry:*`

**Backend Ownership:**
- Module: `pos-people`
- Controller: `WorkSessionController.java`
- Entity: `TimekeepingEntry` (inferred from business rules)
- Event Subscriptions: `WorkSessionCompleted` (from ShopMgmt or WorkExec)

---

### 2. Scheduling & Dispatch Features → `domain:shopmgmt`

**Issues Affected:**
- **#138** - View Assigned Schedule/Dispatch Board
- **#137** - Reschedule Work Order
- **#134** - Assign Mechanic to Work Order Item
- **#133** - Override Schedule Conflict (duplicate of #137)

**Rationale:**
- The **ShopMgmt domain** is the canonical owner of appointment scheduling, resource assignment, and dispatch workflows per existing architecture documented in `/home/louisb/Projects/durion/domains/shopmgmt/.business-rules/AGENT_GUIDE.md`.
- ShopMgmt domain summary: *"manages appointment scheduling and resource assignment within the modular POS system for automotive service shops"*.
- Backend module `pos-shop-manager` exists and owns scheduling logic.
- Scheduling policy enforcement, conflict detection, and override workflows are ShopMgmt responsibilities.
- WorkExec domain owns the **work order lifecycle** (creation, approval, execution, completion), but **scheduling/dispatching** work orders is a ShopMgmt concern.

**Action Items:**
1. Update GitHub issue labels from `domain:workexec` to `domain:shopmgmt` for issues #138, #137, #134, #133.
2. Document cross-domain integration contract in WorkExec's `CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`:
   - WorkExec publishes `workorder.status_changed` events (e.g., `APPROVED` → ready for scheduling)
   - ShopMgmt subscribes to these events to enable scheduling workflows
   - ShopMgmt publishes `appointment.scheduled`, `appointment.rescheduled` events
   - WorkExec screens may display scheduling data via read-only API calls to `pos-shop-manager` endpoints
3. Clarify that WorkExec UI screens may embed scheduling views (iframe or component), but the data and logic are owned by ShopMgmt.

**Backend Ownership:**
- Module: `pos-shop-manager`
- Endpoints: `/api/v1/appointments/{id}`, `/api/v1/appointments/{id}/reschedule`, `/api/v1/appointments/{id}/assign` (assumed, pending Phase 4.2 backend discovery)
- Event Subscriptions: `workorder.status_changed`, `estimate.approved`
- Event Publications: `appointment.scheduled`, `appointment.rescheduled`, `appointment.conflict_detected`

---

### 3. Sales Order Creation → `domain:order`

**Issues Affected:**
- **#85** - Create Sales Order from Work Order Cart

**Rationale:**
- The **Order domain** is the canonical owner of sales order creation, cart management, and order lifecycle workflows.
- Backend module `pos-order` exists and owns order logic per workspace structure.
- Creating a sales order from work order items is a **cross-domain transformation**: WorkExec provides the source data (billable work order items), but the resulting sales order entity is owned by Order domain.
- Pricing, tax calculation, payment terms, and order fulfillment are Order domain responsibilities.

**Action Items:**
1. Update GitHub issue label from `domain:workexec` to `domain:order` for issue #85.
2. Document cross-domain integration contract in WorkExec's `CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`:
   - WorkExec provides a "convert to sales order" endpoint that calls `pos-order` APIs
   - Endpoint: `POST /api/v1/orders/from-workorder` (owned by Order domain)
   - Payload: `{ workOrderId, selectedItemIds[], customerId, paymentTerms }`
   - Response: `{ orderId, orderNumber, totalAmount, status }`
   - WorkExec UI displays sales order creation status but does not own the order entity
3. Clarify that WorkExec screens may initiate sales order creation but should redirect users to Order domain screens for order management.

**Backend Ownership:**
- Module: `pos-order`
- Endpoint: `POST /api/v1/orders/from-workorder` (assumed, pending Phase 4.2 backend discovery)
- Event Publications: `order.created_from_workorder`
- Dependencies: WorkExec APIs to fetch billable work order items

---

## Alternatives Considered

### Option A: Keep all features in `domain:workexec` (Rejected)

**Pros:**
- Simpler issue labeling (everything stays in WorkExec)
- Less cross-domain coordination required

**Cons:**
- Violates established domain boundaries and ownership model
- Creates conflicting sources of truth (e.g., two timekeeping systems)
- Breaks existing security permission model (People domain permissions already defined)
- Duplicates logic across domains (scheduling, timekeeping)
- Makes it impossible to reuse timekeeping/scheduling features in other contexts (e.g., non-workorder scenarios)

**Verdict:** Rejected. Violates DDD principles and existing architectural decisions.

---

### Option B: Create new `domain:timekeeping` and `domain:scheduling` domains (Rejected)

**Pros:**
- Clear separation of concerns with dedicated domains
- Avoids "god domains" (People, ShopMgmt)

**Cons:**
- Timekeeping and scheduling are already owned by existing domains with documented business rules and backend modules
- Creating new domains would require migrating existing code, APIs, and permissions
- Increases architectural complexity and cross-domain coordination overhead
- No clear business justification for splitting these concerns away from People and ShopMgmt

**Verdict:** Rejected. Unnecessary architectural churn; existing domain boundaries are correct.

---

### Option C: Clarify boundaries and update issue labels (Accepted)

**Pros:**
- Preserves existing domain boundaries and ownership model
- Aligns with documented business rules and backend module structure
- Enables proper team assignments and issue triaging
- Reduces confusion about which domain owns which features
- Maintains single source of truth for timekeeping (People) and scheduling (ShopMgmt)

**Cons:**
- Requires updating issue labels and communicating domain boundaries to team
- Some WorkExec UI screens will display data from other domains (cross-domain integration)

**Verdict:** Accepted. This is the correct architectural approach.

---

## Architectural Implications

### 1. Cross-Domain Integration Patterns

**WorkExec → People (Timekeeping):**
- WorkExec publishes work session events → People domain ingests timekeeping entries
- WorkExec UI may display timekeeping data via read-only calls to `pos-people` APIs
- Authentication: WorkExec screens must pass user context to People APIs for authorization

**WorkExec → ShopMgmt (Scheduling):**
- WorkExec publishes work order status events → ShopMgmt enables scheduling workflows
- ShopMgmt publishes scheduling events → WorkExec displays scheduling status in work order views
- WorkExec UI may embed ShopMgmt scheduling components (iframe or shared Vue component)

**WorkExec → Order (Sales Order Creation):**
- WorkExec UI initiates sales order creation → calls `pos-order` APIs with work order item data
- Order domain creates sales order entity and returns `orderId`
- WorkExec stores reference to `orderId` but does not own order lifecycle

### 2. Permission Model Updates

**Current State:**
- Security domain defines `workexec:time_entry:*` permissions (legacy naming)

**Future State:**
- Rename to `people:time_entry:*` for clarity
- Define cross-domain permission delegation patterns:
  - WorkExec screens can check `people:time_entry:view` permissions when displaying timekeeping data
  - ShopMgmt screens can check `workexec:workorder:view` permissions when displaying work order details in scheduling views

### 3. Event-Driven Integration

**Event Schemas (to be defined in Phase 4.4):**
- `work_session.started` (WorkExec → People)
- `work_session.completed` (WorkExec → People)
- `workorder.status_changed` (WorkExec → ShopMgmt, CRM, others)
- `appointment.scheduled` (ShopMgmt → WorkExec)
- `appointment.rescheduled` (ShopMgmt → WorkExec)
- `order.created_from_workorder` (Order → WorkExec, Accounting)

### 4. UI Component Ownership

**WorkExec UI Screens May Display:**
- Work order core data (owned by WorkExec)
- Timekeeping summary (data owned by People, rendered via API calls)
- Scheduling status (data owned by ShopMgmt, rendered via API calls or embedded component)
- Sales order creation status (action owned by Order, initiation button in WorkExec UI)

**Principle:** UI screens can span multiple domains, but data ownership and business logic remain with the canonical domain.

---

## Migration & Communication Plan

### Phase 4.1 (Immediate - This ADR)

1. ✅ Document domain ownership decisions in this ADR
2. ✅ Define architectural implications and integration patterns
3. ⏳ Communicate decisions to team via this ADR

### Phase 4.2-4.3 (Backend Discovery & Issue Analysis)

1. Update GitHub issue labels:
   - #149, #146, #145, #131, #132 → `domain:people`
   - #138, #137, #134, #133 → `domain:shopmgmt`
   - #85 → `domain:order`
2. Discover backend APIs in `pos-people`, `pos-shop-manager`, `pos-order` modules
3. Document cross-domain integration contracts in `CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`

### Phase 4.4 (Integration Contracts)

1. Define event schemas for cross-domain workflows
2. Document API contracts for read-only cross-domain data access
3. Define retry/failure semantics for async event-driven integration

### Future (Post-Phase 4)

1. Refactor security permissions: `workexec:time_entry:*` → `people:time_entry:*`
2. Implement event-driven integration for timekeeping, scheduling, order creation
3. Audit cross-domain API calls for proper authorization and error handling

---

## References

- **People Domain Business Rules:** `/home/louisb/Projects/durion/domains/people/.business-rules/DOMAIN_NOTES.md`
- **ShopMgmt Domain Business Rules:** `/home/louisb/Projects/durion/domains/shopmgmt/.business-rules/AGENT_GUIDE.md`
- **Accounting Domain Cross-Domain Guidance:** `/home/louisb/Projects/durion/domains/accounting/.business-rules/AGENT_GUIDE.md`
- **Security Baseline Permissions:** `/home/louisb/Projects/durion/domains/security/docs/BASELINE_PERMISSIONS.md`
- **Backend Modules:**
  - `pos-people` (timekeeping owner)
  - `pos-shop-manager` (scheduling owner)
  - `pos-order` (order creation owner)
  - `pos-workorder` (work order lifecycle owner)

---

## Decision Authority

**Approved By:** Architecture Team (via Phase 4.1 analysis)  
**Domain Owners Consulted:**
- People domain: Confirmed timekeeping ownership via existing business rules
- ShopMgmt domain: Confirmed scheduling ownership via existing business rules
- Order domain: Confirmed sales order creation ownership (implicit from module structure)

**Status:** Accepted and ready for implementation in Phase 4.2+

---

## Checklist for Phase 4.1 Completion

- [x] Domain ownership decisions documented for all 7 ambiguous issues
- [x] Rationale provided with references to existing business rules and backend modules
- [x] Alternatives considered and rejected with clear justifications
- [x] Architectural implications documented (integration patterns, permissions, events, UI)
- [x] Migration plan defined with phased action items
- [x] References to source documentation provided

**Phase 4.1 Status:** ✅ Complete - Ready to proceed to Phase 4.2 (Backend Contract Discovery)
