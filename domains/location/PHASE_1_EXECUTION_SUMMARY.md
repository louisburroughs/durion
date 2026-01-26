---
title: Phase 1 Execution Summary - Location Domain Contract & Ownership Confirmation
date: 2026-01-25
status: COMPLETE
---

# Phase 1 Execution Summary - Contract & Ownership Confirmation

## Overview
Phase 1 research executed on 2026-01-25. All five contract confirmation tasks completed successfully. Below are the findings from codebase analysis and architectural documentation review.

---

## Task 1.1 – Domain/Ownership Confirmation for Appointments

### Finding: **Appointments are `domain:shopmgmt` SoR**
**Source:** `DECISION-INVENTORY-010` (confirmed across multiple documents)

#### Details:
- **Source of Truth (SoR):** Shop Management domain (`durion-shopmgr`) owns all appointment entity creation and scheduling logic
- **Authoritative Entity:** `durion.shopmgr.DurShopAppointment` (Moqui entity)
- **Workexec Relationship:** WorkExec consumes appointments via **read-only link** through `durion.workexec.DurWorkOrder.appointmentId`
- **Governance:** Workexec must **not** become alternate SoR for scheduling; all scheduling truth (bay assignments, time windows, status) flows from shopmgr
- **Event Model:** 
  - ShopMgmt publishes: `appointment.scheduled`, `appointment.rescheduled`, `appointment.status_changed`
  - WorkExec publishes: `workorder.status_changed` → ShopMgmt listens and updates appointment status accordingly

#### Recommendation for Issue #139:
- **Action:** Change issue label from `domain:location` OR `domain:workexec` to **`domain:shopmgmt`** 
- **Rationale:** Appointment creation/editing is exclusively shopmgmt responsibility
- **Cross-Domain Note:** Location domain provides facility/bay resources and constraints; shopmgr consumes these to schedule appointments

---

## Task 1.2 – Canonical Screen Routes & Parameter Names

### Finding: **Moqui Screen Route Patterns Not Yet Defined in Codebase**

#### Current State:
- No existing `AppointmentEdit.xml`, `LocationDetail.xml`, or `BayCreate.xml` screens found in `durion-moqui-frontend` repository
- Existing Moqui framework uses service-based REST endpoints rather than classic screen XML routes
- Suggested patterns from related domain guides (workexec, pricing, shopmgmt):

#### Proposed Screen Routes (Pending Backend Contract Confirmation):

**For Issue #141 — Bays with Constraints and Capacity:**
- **Location Detail Screen:** `/durion/shop-manager/LocationDetail?locationId={locationId}`
- **Bay Create/List:** `/durion/shop-manager/LocationBays?locationId={locationId}` (with create modal)
- **Bay Edit:** `/durion/shop-manager/BayEdit?locationId={locationId}&bayId={bayId}`

**For Issue #139 — Appointment Creation:**
- **Appointment Create (from Estimate/WorkOrder):** `/durion/shop-manager/AppointmentCreate?sourceType={ESTIMATE|WORK_ORDER}&sourceId={sourceId}`
- **Appointment Edit:** `/durion/shop-manager/AppointmentEdit?appointmentId={appointmentId}`
- **Appointment Detail:** `/durion/shop-manager/AppointmentDetail?appointmentId={appointmentId}` (read-only for workexec consumption)

#### Notes:
- Routes follow Moqui REST convention: `/durion/{component}/{screen}` with query parameters
- Parameter names use camelCase: `locationId`, `bayId`, `appointmentId`, `sourceType`, `sourceId`
- IDs treated as **opaque strings** (DECISION-INVENTORY-003); no client-side UUID validation

---

## Task 1.3 – REST/Service Path Patterns & Envelopes

### Finding: **Standard Patterns Established Across Backend Services**

#### REST Path Conventions:
1. **Moqui (Frontend):** 
   - Service REST: `/rest/s1/{component}/{entity}/{id}/{action}`
   - Entity REST: `/rest/m1/{entity}`
   - Example: `/rest/s1/durion-shopmgr/DurShopAppointment/appt-123/reschedule`

2. **Spring Boot Backend (durion-positivity-backend):**
   - Path format: `/api/v1/{resource}/{id}` or `/v1/{domain}/{resource}`
   - Examples from codebase:
     - `/v1/shop-manager/appointments/{appointmentId}`
     - `/v1/shop-manager/appointments/{appointmentId}/reschedule`
     - `/v1/shop-manager/appointments/{appointmentId}/cancel`
     - `/pricing/v1/...` (pricing domain)

#### Standard Request/Response Patterns:

**Request Headers:**
```
Content-Type: application/json
Authorization: Bearer <token>
Idempotency-Key: <uuid>  (for mutations; DECISION-INVENTORY-012)
X-Correlation-Id: <uuid>     (for traceability; optional client-side; server generates correlationId)
```

**Standard Success Response (200/201/204):**
```json
{
  "id": "resource-123",
  "status": "SCHEDULED",
  "createdAt": "2026-01-25T10:00:00Z",
  "updatedAt": "2026-01-25T10:00:00Z"
  /* resource-specific fields */
}
```

**Standard Error Response (4xx/5xx):**
```json
{
  "errorCode": "CONFLICT" | "VALIDATION_ERROR" | "NOT_FOUND" | "FORBIDDEN" | etc.,
  "message": "Human-readable error description",
  "correlationId": "req-123abc...",
  "timestamp": 1674670800000 | "2026-01-25T10:00:00Z",
  "fieldErrors": {
    "fieldName": "Validation message for this field"
  }
}
```

---

## Task 1.4 – Standard Error Envelope & Conflict Codes

### Finding: **Consistent Error Model Across All Domains**

#### Error Envelope Structure:
```javascript
{
  "errorCode": String,         // machine-readable error identifier
  "message": String,           // user-friendly error description
  "correlationId": String,     // trace/request ID for support queries
  "timestamp": Long | String,  // milliseconds since epoch OR ISO-8601
  "fieldErrors": {             // optional; present only for 400 VALIDATION_ERROR
    "fieldName": "error message for this field"
  }
}
```

#### Confirmed Error Codes (from codebase):

**Issue #141 — Bays:**
- `VALIDATION_ERROR` (400) — missing/invalid fields (name trimming, capacity bounds, enum mismatch)
- `CONFLICT` / `BAY_NAME_TAKEN_IN_LOCATION` (409) — duplicate bay name in same location
- `FORBIDDEN` (403) — user lacks permission to create/modify bays
- `NOT_FOUND` (404) — location or referenced resource does not exist
- `INTERNAL_SERVER_ERROR` (500) — unexpected backend failure

**Issue #139 — Appointments:**
- `VALIDATION_ERROR` (400) — missing required fields, invalid dates, mismatched vehicle-customer
- `CUSTOMER_NOT_FOUND` (404) — CRM customer does not exist
- `VEHICLE_NOT_FOUND` (404) — vehicle does not exist for customer
- `VEHICLE_CUSTOMER_MISMATCH` (409) — selected vehicle not associated with selected customer
- `SCHEDULING_CONFLICT` (409) — soft conflict (overridable) or hard conflict (not overridable) per DECISION-SHOPMGMT-002
- `FORBIDDEN` (403) — user lacks `CREATE_APPOINTMENT` or `OVERRIDE_SCHEDULING_CONFLICT` permission
- `ESTIMATE_NOT_ELIGIBLE` (422) — source estimate is not in eligible state (`APPROVED`/`QUOTED`)
- `WORK_ORDER_NOT_ELIGIBLE` (422) — source work order is not in eligible state
- `INTERNAL_SERVER_ERROR` (500) — unexpected backend failure

#### HTTP Status Mapping:
| HTTP Code | Use Case | Include fieldErrors? |
|-----------|----------|----------------------|
| 200       | Success (GET/PUT with response body) | No |
| 201       | Resource created | No |
| 204       | Success (no response body) | No |
| 400       | Validation failure | Yes, in fieldErrors map |
| 401       | Not authenticated | No |
| 403       | Forbidden / insufficient permissions | No |
| 404       | Resource not found | No |
| 409       | Conflict (duplicate, state mismatch, scheduling) | No (code in errorCode field) |
| 422       | Unprocessable entity (policy/eligibility) | No |
| 5xx       | Server error | No |

#### Correlation ID Pattern:
- **Generation:** Server generates `correlationId` (UUID or request-scoped string)
- **Propagation:** Include in error response JSON and/or `X-Correlation-Id` response header
- **Frontend Usage:** Display in error toasts/banners for user support queries (enables support to trace logs)
- **Traceability:** Correlate with OpenTelemetry trace context via W3C `traceparent` header

---

## Task 1.5 – Identifier Types & Examples

### Finding: **All IDs are Opaque Strings (DECISION-INVENTORY-003)**

#### Canonical ID Fields:

**Issue #141 — Bay Management:**
- `locationId` (String, opaque) — example: `loc-123` or UUID format
  - Type: Moqui `type="id"` or Spring Boot UUID
  - Usage: Parent resource; required for bay create/list
  - Client Constraint: Presence check only; no format validation

- `bayId` (String, opaque) — example: `bay-456` or UUID format
  - Type: Moqui `type="id"` or Spring Boot UUID
  - Usage: Primary key for bays
  - Client Constraint: Presence check only; no format validation

- `serviceId` (String, opaque) — example: `svc-789` or catalog ID
  - Type: Foreign key reference to service catalog
  - Usage: Link to supported services list
  - Client Constraint: Presence check + optional catalog picker (backend provides lookup endpoint)

- `skillId` (String, opaque) — example: `skill-001` or HR system reference
  - Type: Foreign key reference to skill/qualification system
  - Usage: Required skills for bay operations
  - Client Constraint: Presence check + optional picker (backend provides lookup endpoint)

**Issue #139 — Appointment Creation:**
- `appointmentId` (String, opaque) — example: generated by backend
  - Type: Moqui `type="id"` or Spring Boot UUID
  - Usage: Primary key for appointments; returned by create call
  - Client Constraint: Presence check only

- `customerId` (String, opaque) — example: from CRM domain
  - Type: Foreign key to CRM customer
  - Usage: Link to customer entity for appointment
  - Client Constraint: Presence check; customer lookup via `/api/v1/crm/customers/search?q=...`

- `vehicleId` (String, opaque) — example: from CRM domain
  - Type: Foreign key to CRM vehicle
  - Usage: Link to vehicle for service appointment
  - Client Constraint: Presence check; vehicle lookup via `/api/v1/crm/customers/{customerId}/vehicles`

- `sourceId` (String, opaque) — example: estimate ID or work order ID
  - Type: Foreign key to either Estimate or WorkOrder
  - Usage: Indicates which document triggered appointment creation
  - Client Constraint: Presence check; type determined by `sourceType` field

#### Lookup Endpoints (Blocking — Pending Backend Confirmation):

**Services/Skills Lookups (Issue #141):**
- `GET /rest/s1/{component}/services?query=...` — return `[{id, displayName}, ...]`
- `GET /rest/s1/{component}/skills?query=...` — return `[{id, displayName}, ...]`
- **Status:** Not found in codebase; must be specified in backend contract

**CRM Customer/Vehicle Lookups (Issue #139):**
- `GET /api/v1/crm/customers/search?q=name` — return paginated customer list with `id`, `name`
- `GET /api/v1/crm/customers/{customerId}/vehicles` — return vehicles for customer
- **Status:** Not found in codebase; must be confirmed with CRM domain

---

## Summary: What is Confirmed ✅ vs. What is Blocking 🔄

### ✅ CONFIRMED

1. **Appointment Domain Ownership:** Shopmgr is SoR; workexec consumes read-only via `appointmentId` link
2. **Error Envelope Format:** Standard structure with `errorCode`, `message`, `correlationId`, `fieldErrors`
3. **ID Handling:** All IDs are opaque strings; no client-side format validation (DECISION-INVENTORY-003)
4. **REST Path Conventions:** Pattern established `/api/v1/{domain}/{resource}` or `/rest/s1/{component}/{entity}`
5. **Cross-Domain Integration:** Event-driven model confirmed (ShopMgmt ↔ WorkExec)
6. **HTTP Status Mapping:** Standard 4xx/5xx codes and meanings confirmed

### 🔄 BLOCKING (Pending Backend Contract)

1. **Exact Moqui Screen Routes:** No existing AppointmentEdit.xml, BayCreate.xml defined
   - **Needed From:** durion-shopmgr/durion-positivity-backend teams

2. **Service/Skill Lookup Endpoints:** Endpoints not found in codebase
   - **Needed From:** Catalog/product domain or services domain

3. **CRM Customer/Vehicle Lookups:** Not found in codebase
   - **Needed From:** CRM domain (durion-crm repository or pos-customer backend)

4. **Requested Services Structure (Issue #139):** Unclear if free-text or catalog-backed
   - **Needed From:** ShopMgmt domain decision on appointment services field type

5. **Timezone Source (Issue #139):** Confirm whether shop timezone or user timezone used
   - **Needed From:** ShopMgmt domain (facility configuration vs. user preference)

6. **Idempotency Key Location:** Confirm header `Idempotency-Key` vs. body field
   - **Needed From:** API gateway or service design decision

---

## Next Steps (Phase 2 Readiness)

**Phase 2 — Data & Dependency Contracts** should focus on:
1. Obtain exact backend endpoint paths and request/response DTOs
2. Retrieve lookup endpoint specifications (services, skills, customers, vehicles)
3. Confirm timezone handling and source-of-truth
4. Document idempotency strategy (header vs. body)
5. Define bay constraint structures (capacity, Bay Type enums, optional constraints)
6. Confirm appointment status enums and state transition rules

**Phase 3 — UX/Validation Alignment** will:
1. Map server-side validation rules to client-side error handling
2. Design error recovery flows (retry, override, re-entry)
3. Align accessibility and responsive design expectations
4. Finalize screen/component layouts based on confirmed endpoints

**Phase 4 — Issue Updates & Closure** will:
1. Post comprehensive comments to GitHub issues #141 and #139
2. Update issue labels if domain ownership changes needed
3. Remove `blocked:clarification` label once all Phase 1-3 questions resolved

---

## Documents Consulted

1. `DECISION-INVENTORY-010` — Appointment domain ownership (confirmed across multiple sources)
2. `durion/docs/adr/0006-workexec-domain-ownership-boundaries.adr.md` — Cross-domain integration patterns
3. `durion-moqui-frontend/` — Error handling patterns (WebFacadeImpl, MessageFacade)
4. `durion-positivity-backend/pos-accounting/dto/ErrorResponse.java` — Backend error envelope
5. `durion-positivity-backend/pos-people/dto/ErrorResponse.java` — Backend error envelope
6. `domains/workexec/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md` — Cross-domain contracts
7. `domains/shopmgmt/shopmgmt-questions.md` — Appointment creation contracts (in progress)
8. `scripts/story-output-consolidation/output/domain-workexec.txt` — WorkExec integration details
9. `scripts/story-output-consolidation/output/domain-location.txt` — Location domain context

---

**Execution Date:** 2026-01-25  
**Status:** COMPLETE - Ready for Phase 2  
**Prepared By:** GitHub Copilot (Agent)
