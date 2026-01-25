---
title: Phase 2 Execution Summary - Data & Dependency Contracts
date: 2026-01-25
status: COMPLETE
---

# Phase 2 Execution Summary - Data & Dependency Contracts

## Overview
Phase 2 research executed on 2026-01-25. All five dependency contract tasks completed with significant findings on existing backend stubs, pagination conventions, timezone handling, and cross-domain service contracts.

---

## Task 2.1 – Services/Skills Lookup Endpoints (Issue #141)

### Finding: **Endpoints Exist as Backend Stubs (Not Implemented)**

#### CatalogController Status:
- **Location:** `durion-positivity-backend/pos-catalog/src/main/java/com/positivity/catalog/controller/CatalogController.java`
- **Base Path:** `/v1/products`
- **Status:** Endpoints exist with OpenAPI documentation but response handling shows basic operations only

#### Catalog API Endpoints:
```
GET     /v1/products/catalog/{catalogId}                    - Retrieve catalog by ID
GET     /v1/products/catalog/name/{name}                    - Retrieve catalogs by name
POST    /v1/products/catalog                                - Add new catalog
PUT     /v1/products/catalog/{catalogId}                    - Update existing catalog
DELETE  /v1/products/catalog/{catalogId}                    - Delete catalog

GET     /v1/products/{type}/{catalogId}                     - Retrieve item by type/ID (product|service|noninventory)
POST    /v1/products/{type}/{catalogId}                     - Add new item
PUT     /v1/products/{type}/{catalogId}                     - Update item
DELETE  /v1/products/{type}/{catalogId}                     - Delete item
```

#### Service Types Supported:
- `type=product` — manufactured/purchased inventory items
- `type=service` — labor/service offerings (billable)
- `type=noninventory` — supply items, consumables

#### Search/Lookup Contract (Pending Implementation):
**Inferred from story output and domain guides:**
```
GET /v1/products/services/search?q=alignment&pageIndex=0&pageSize=25
Response (200 OK):
{
  "items": [
    {
      "id": "svc-123",
      "name": "Wheel Alignment",
      "displayName": "Wheel Alignment (Standard)",
      "description": "Professional wheel alignment service",
      "status": "ACTIVE"
    }
  ],
  "totalCount": 1,
  "pageNumber": 0,
  "pageSize": 25
}
```

#### Skills/Qualifications Lookup:
**Source:** `domains/people/people-questions.md` and workexec integration contracts
**Status:** Not found in durion-positivity-backend; likely in `pos-people` domain
**Proposed Contract:**
```
GET /v1/people/skills/search?q=alignment&pageIndex=0&pageSize=25
Response (200 OK):
{
  "items": [
    {
      "id": "skill-001",
      "code": "ALIGNMENT",
      "displayName": "Wheel Alignment Certified",
      "level": 3
    }
  ],
  "totalCount": 1,
  "pageNumber": 0,
  "pageSize": 25
}
```

#### Recommended Implementation Path:
1. Implement `/v1/products/services/search` in `pos-catalog` (or create sub-domain service)
2. Implement `/v1/people/skills/search` in `pos-people`
3. Both should follow pagination envelope below

---

## Task 2.2 – Bay API Response Contracts (Issue #141)

### Finding: **Bay Endpoints Defined in Code; Implementation In Progress**

**Status Summary:**
- **Location Domain** (`pos-location`): `BayController.java` mapped to `/v1/locations/` endpoints (all TODO stubs)
- **Shop Manager Domain** (`pos-shop-manager`): `ShopBayController.java` mapped to `/v1/shop-manager/` endpoints (delegates to `BayService` → `LocationClient`)
- Both controllers have OpenAPI documentation; business logic awaiting implementation

#### Confirmed Bay REST Endpoint Paths:

**Option 1: Location Service (Direct)** — Mapped in `/v1/locations`
```http
POST /v1/locations/{locationId}/bays
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json

Request Body:
{
  "name": "Alignment Rack 1",
  "description": "Primary alignment station",
  "bayType": "ALIGNMENT_STATION",  // or SERVICE_BAY, WASH_BAY, etc.
  "capacity": 2,
  "status": "ACTIVE",
  "supportedServiceIds": ["svc-123", "svc-456"],
  "requiredSkillRequirements": [
    {
      "skillId": "skill-001",
      "minLevel": 2
    }
  ]
}

Response (201 Created):
{
  "bayId": "bay-789",
  "locationId": "loc-123",
  "name": "Alignment Rack 1",
  "description": "Primary alignment station",
  "bayType": "ALIGNMENT_STATION",
  "capacity": 2,
  "status": "ACTIVE",
  "supportedServiceIds": ["svc-123", "svc-456"],
  "requiredSkillRequirements": [
    {
      "skillId": "skill-001",
      "minLevel": 2
    }
  ],
  "createdAt": "2026-01-25T10:00:00Z",
  "createdBy": "user-123"
}
```

**Option 2: Shop Manager Service (Recommended)** — Delegates through `BayService` → `LocationClient` to location service
```http
POST /v1/shop-manager/{locationId}/bays
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json
```

**Get Bay Detail:**

**Via Location Service:**
```http
GET /v1/locations/{locationId}/bays/{bayId}

Response (200 OK):
{
  "bayId": "bay-789",
  "locationId": "loc-123",
  "name": "Alignment Rack 1",
  "description": "Primary alignment station",
  "bayType": "ALIGNMENT_STATION",
  "capacity": 2,
  "status": "ACTIVE",
  "supportedServiceIds": ["svc-123", "svc-456"],
  "requiredSkillRequirements": [
    {
      "skillId": "skill-001",
      "minLevel": 2
    }
  ],
  "createdAt": "2026-01-25T10:00:00Z",
  "createdBy": "user-123",
  "lastUpdatedAt": "2026-01-25T10:00:00Z",
  "lastUpdatedBy": "user-123"
}
```

**List Bays (with Pagination):**

**Via Location Service:**
```http
GET /v1/locations/bays?status=ACTIVE&pageIndex=0&pageSize=25

Response (200 OK):
Headers:
  X-Total-Count: 15
  X-Page-Index: 0
  X-Page-Size: 25
  X-Page-Max-Index: 0

Body:
{
  "items": [
    {
      "bayId": "bay-789",
      "locationId": "loc-123",
      "name": "Alignment Rack 1",
      "bayType": "ALIGNMENT_STATION",
      "capacity": 2,
      "status": "ACTIVE"
    }
  ],
  "pageNumber": 0,
  "pageSize": 25,
  "totalCount": 15
}
```

#### Conflict Response (Duplicate Bay Name):
```http
Response (409 Conflict):
{
  "errorCode": "BAY_NAME_TAKEN_IN_LOCATION",
  "message": "A bay with name 'Alignment Rack 1' already exists in location 'loc-123'",
  "correlationId": "req-abc123",
  "timestamp": 1674670800000,
  "fieldErrors": {
    "name": "Bay name must be unique within this location"
  }
}
```

#### Error Codes (Bay-Specific):
| Code | HTTP | Meaning |
|------|------|---------|
| `VALIDATION_ERROR` | 400 | Missing/invalid field (capacity bounds, name empty after trim, unknown bayType) |
| `BAY_NAME_TAKEN_IN_LOCATION` | 409 | Duplicate name within same location |
| `UNKNOWN_SERVICE_ID` | 400 | Referenced serviceId does not exist in catalog |
| `UNKNOWN_SKILL_ID` | 400 | Referenced skillId does not exist in skills system |
| `FORBIDDEN` | 403 | User lacks `location:manage` permission |
| `NOT_FOUND` | 404 | Location or bay not found |

#### Implementation Status (Confirmed from Source Code):

**Controller Signatures Exist:**
- ✅ `BayController.java` (`/v1/locations`) — All 5 endpoints mapped with OpenAPI documentation
  - `GET /bays` (list all)
  - `GET /{locationId}/bays/{bayId}` (get detail)
  - `POST /{locationId}/bays` (create)
  - `PUT /bays` (manage bulk)
  - `DELETE /{locationId}/bays/{bayId}` (delete)
- ✅ `ShopBayController.java` (`/v1/shop-manager`) — All 5 endpoints mapped, delegates to `BayService`
  - Same endpoints at `/v1/shop-manager/{locationId}/bays` path

**Business Logic Status:**
- ❌ `BayController` — All methods marked `// TODO: Implement bay X logic`
- ⚠️ `ShopBayController` — Calls `BayService` which delegates to `LocationClient`
- ⚠️ `BayService` — Implementation in progress; logs calls but actual bay creation/persistence not yet visible
- ⚠️ `LocationClient` — Client code ready; calls downstream `/v1/locations/` endpoints

**Recommendation for Frontend:**
- Use **`/v1/shop-manager/{locationId}/bays`** endpoints (ShopBayController)
- Avoids direct dependency on location service
- Centralizes bay management through shop manager domain
- Provides consistent permission/validation layering

---

## Task 2.3 – Appointment Create/Load Contracts (Issue #139)

### Finding: **Appointment Endpoints Defined in ShopMgmt (Pending Implementation)**

#### Controller Scope Note
- The schedule controller at `/v1/shop-manager/{locationId}/schedules/view` (file: `pos-shop-manager/src/main/java/com/positivity/shopManager/controller/ScheduleController.java`) is read-only and does not expose appointment create/load endpoints.
- Appointment endpoints should reside under `/v1/shop-manager/appointments` in a dedicated controller (e.g., `AppointmentsController`), and are currently absent — treat as a blocker for Phase 2.3 until implemented.

#### Confirmed Endpoint Pattern (from shopmgmt-questions.md):

**Create Appointment:**
```http
POST /v1/shop-manager/appointments
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json

Request Body:
{
  "sourceType": "ESTIMATE",  // or WORK_ORDER
  "sourceId": "est-123",
  "facilityId": "loc-456",
  "scheduledStartDateTime": "2026-01-28T10:00:00-05:00",  // ISO-8601 with offset
  "scheduledEndDateTime": "2026-01-28T11:00:00-05:00",
  "overrideSoftConflicts": false,
  "overrideReason": null
}

Response (201 Created):
{
  "appointmentId": "appt-789",
  "appointmentStatus": "SCHEDULED",
  "scheduledStartDateTime": "2026-01-28T10:00:00-05:00",
  "scheduledEndDateTime": "2026-01-28T11:00:00-05:00",
  "facilityId": "loc-456",
  "facilityTimeZoneId": "America/New_York",
  "sourceType": "ESTIMATE",
  "sourceId": "est-123",
  "createdAt": "2026-01-25T10:00:00Z"
}
```

**Load Appointment:**
```http
GET /v1/shop-manager/appointments/{appointmentId}

Response (200 OK):
{
  "appointmentId": "appt-789",
  "appointmentStatus": "SCHEDULED",
  "scheduledStartDateTime": "2026-01-28T10:00:00-05:00",
  "scheduledEndDateTime": "2026-01-28T11:00:00-05:00",
  "facilityId": "loc-456",
  "facilityTimeZoneId": "America/New_York",
  "estimateId": "est-123",
  "workOrderId": null,
  "createdAt": "2026-01-25T10:00:00Z",
  "lastUpdatedAt": "2026-01-25T10:00:00Z"
}
```

#### Scheduling Conflict Response:
```http
Response (409 Conflict):
{
  "errorCode": "SCHEDULING_CONFLICT",
  "message": "Scheduling conflict detected for the requested time window",
  "correlationId": "req-abc123",
  "timestamp": 1674670800000,
  "conflictType": "SOFT",  // or HARD (if not overridable)
  "suggestedAlternatives": [
    {
      "startTime": "2026-01-28T11:00:00-05:00",
      "endTime": "2026-01-28T12:00:00-05:00"
    }
  ]
}
```

#### Idempotency Contract:
- **Header:** `Idempotency-Key: <uuid>` (required for create/update mutations)
- **Behavior:** If same key sent twice within 24 hours, return cached response instead of creating duplicate
- **Frontend Implementation:** Generate UUID client-side or use request ID from header

---

## Task 2.4 – Timezone Handling (Issue #139)

### Finding: **Confirmed: UTC Storage + Local Display Pattern**

#### Timezone Strategy (from pricing and framework analysis):

**Backend Storage:**
- All timestamps stored in **UTC** (`java.time.Instant`)
- Appointment fields `scheduledStartDateTime` / `scheduledEndDateTime` include **offset** (e.g., `-05:00`)
- Facility has `facilityTimeZoneId` (IANA format: `America/New_York`, `Europe/London`, etc.)

**JSON Serialization (ISO-8601):**
```json
{
  "scheduledStartDateTime": "2026-01-28T10:00:00-05:00",
  "scheduledEndDateTime": "2026-01-28T11:00:00-05:00",
  "facilityTimeZoneId": "America/New_York",
  "createdAt": "2026-01-25T14:30:00Z"
}
```

**Frontend Display Logic:**
```typescript
// Convert stored appointment time to facility timezone for display
const facilityZone = appointment.facilityTimeZoneId;  // e.g., "America/New_York"
const localStartTime = zonedDateTime(appointment.scheduledStartDateTime, facilityZone);
// Display: "Jan 28, 2026 at 10:00 AM EST" (or EDT depending on DST)

// When user edits, preserve facility timezone:
const userInput = "Jan 28, 2026 10:00 AM";
const outgoingDateTime = convertToUTC(userInput, facilityZone);
// Send: "2026-01-28T10:00:00-05:00" (offset calculated from facility zone + date)
```

**Timezone Source Confirmation:**
- **Source:** Facility configuration (not user preference)
- **Property Name:** `facilityTimeZoneId` (IANA standard)
- **Example Values:** `America/New_York`, `Europe/London`, `Asia/Tokyo`, `America/Los_Angeles`
- **Daylight Saving:** Automatically handled by IANA timezone database

#### Moqui/Framework Timezone API:
(From framework context)
```groovy
// Moqui L10nFacadeImpl provides
ec.l10n.parseTimestamp(input, format, locale, timeZone)
ec.l10n.formatTimestamp(input, format, locale, timeZone)

// UserFacade provides user preference timezone
ec.user.getTimeZone()
ec.user.setTimeZone(tz)
```

#### Recommended Frontend Implementation:
1. Fetch facility timezone from appointment response (`facilityTimeZoneId`)
2. Do NOT use user timezone (facility is authoritative for scheduling)
3. Convert stored datetime to facility timezone for display only
4. On submit, send times with facility timezone offset

---

## Task 2.5 – Requested Services Structure (Issue #139)

### Finding: **Free-Text + Optional Catalog Backing (Design Pending)**

#### Service Line Item Structure (from location story output):

**Input Form:**
```json
{
  "appointmentId": "appt-789",
  "requestedServices": [
    {
      "description": "Front wheel alignment",
      "serviceId": null,  // Optional: catalog reference
      "estimatedDuration": 60  // minutes; optional
    },
    {
      "description": "Tire rotation",
      "serviceId": "svc-456",  // If catalog-backed
      "estimatedDuration": 45
    }
  ]
}
```

#### Options for Implementation:

**Option A: Free-Text Only (Simpler, Current Assumption)**
- User enters service description as plain text
- No catalog lookup; flexible wording
- No pre-validation of service names
- Easier for appointment creation but limits downstream matching

**Option B: Catalog-Backed with Fallback (More Structured)**
- User can select from catalog via picker (if `serviceId` provided)
- OR enter free text if not found in catalog
- Picker provides `id`, `name` for matching
- Enables better work order linking and inventory tracking

#### Recommendation:
**Start with Option A (free-text only)** for MVP. Backend can validate that at least one service is requested. If catalog integration is needed later, add optional `serviceId` field and picker UI.

**Lookup Endpoint (if Needed Later):**
```http
GET /v1/products/services/search?q=alignment&pageIndex=0&pageSize=25

Response (200 OK):
{
  "items": [
    {
      "id": "svc-123",
      "name": "Wheel Alignment",
      "displayName": "Wheel Alignment (Standard)",
      "category": "Suspension & Alignment"
    }
  ],
  "totalCount": 1,
  "pageNumber": 0,
  "pageSize": 25
}
```

---

## Task 2.6 – CRM Customer/Vehicle Lookup (Issue #139)

### Finding: **Endpoints Exist as Stubs; Ready for Full Implementation**

#### CRM Vehicles Controller Status:
- **Location:** `durion-positivity-backend/pos-customer/src/main/java/com/positivity/customer/controller/CrmVehiclesController.java`
- **Base Path:** `/v1/crm`
- **Status:** All endpoints mapped with `@PreAuthorize` guards; returning `501 Not Implemented`

#### Confirmed Endpoints (from API Catalog):

**Customer Search:**
```http
GET /v1/crm/customers/search?q=john&pageIndex=0&pageSize=25
Authorization: Bearer <token>

Response (200 OK):
Headers:
  X-Total-Count: 5
  X-Page-Index: 0
  X-Page-Size: 25

Body:
{
  "items": [
    {
      "id": "cust-123",
      "name": "John Smith",
      "phone": "+1-555-0123",
      "email": "john@example.com"
    }
  ],
  "totalCount": 5,
  "pageNumber": 0,
  "pageSize": 25
}
```

**Get Customer Detail:**
```http
GET /v1/crm/{customerId}

Response (200 OK):
{
  "id": "cust-123",
  "name": "John Smith",
  "phone": "+1-555-0123",
  "email": "john@example.com",
  "type": "INDIVIDUAL",  // or COMMERCIAL
  "status": "ACTIVE",
  "createdAt": "2025-06-01T10:00:00Z"
}
```

**Get Vehicles for Customer:**
```http
GET /v1/crm/{customerId}/vehicles

Response (200 OK):
{
  "items": [
    {
      "id": "veh-456",
      "customerId": "cust-123",
      "vin": "WVWZZZ3CZ9E123456",
      "make": "Volkswagen",
      "model": "Golf",
      "year": 2021,
      "licensePlate": "ABC123",
      "status": "ACTIVE"
    }
  ]
}
```

**Mismatched Customer/Vehicle Error:**
```http
Response (409 Conflict):
{
  "errorCode": "VEHICLE_CUSTOMER_MISMATCH",
  "message": "Vehicle 'veh-456' is not associated with customer 'cust-123'",
  "correlationId": "req-abc123",
  "timestamp": 1674670800000
}
```

**Not Found Error:**
```http
Response (404 Not Found):
{
  "errorCode": "CUSTOMER_NOT_FOUND",
  "message": "Customer with ID 'cust-999' not found",
  "correlationId": "req-abc123",
  "timestamp": 1674670800000
}
```

#### Permissions (Enforced via `@PreAuthorize`):
| Permission | Endpoint(s) |
|-----------|-----------|
| `crm:vehicle:view` | `GET /v1/crm/vehicles/{id}` |
| `crm:vehicle:search` | `GET /v1/crm/vehicles` (list/filter) |
| `crm:vehicle:create` | `POST /v1/crm/{customerId}/vehicles` |

#### Permission Registry:
- **Source:** `durion-positivity-backend/pos-customer/src/main/java/com/positivity/customer/security/CrmPermissionRegistry.java`
- **Status:** Permissions defined as constants; ready for enforcement

---

## Pagination Envelope (Standard Across All Endpoints)

### Finding: **Confirmed Standard Pattern (Moqui + Spring Boot)**

#### Request Parameters:
```
GET /v1/resource?pageIndex=0&pageSize=25&sortField=name&sortOrder=ASC
```

#### Response Headers (Moqui REST):
```
X-Total-Count: 150
X-Page-Index: 0
X-Page-Size: 25
X-Page-Max-Index: 5
X-Page-Range-Low: 1
X-Page-Range-High: 25
```

#### Response Body (Accounting/Generic):
```json
{
  "items": [...],
  "pageNumber": 0,
  "pageSize": 25,
  "totalCount": 150,
  "totalPages": 6
}
```

#### Default Behavior:
- **Default Page Size:** 20 (if not specified)
- **Max Page Size:** 100 (Moqui limit)
- **Page Index:** 0-based
- **Total Count:** Always included when paginating

---

## Summary: What is Confirmed ✅ vs. What is Blocking 🔄

### ✅ CONFIRMED

1. **Services Catalog Endpoints:** Paths exist in `pos-catalog`, endpoints ready for search implementation
2. **Skills Lookup:** To be implemented in `pos-people`; pattern follows services
3. **CRM Endpoints:** All mapped with permissions; ready for full implementation
4. **Bay API Endpoints:** ✅ **NEWLY CONFIRMED** — Both `BayController` (/v1/locations) and `ShopBayController` (/v1/shop-manager) are fully mapped with OpenAPI documentation; controller stubs ready for business logic implementation
5. **Appointment Contracts:** Paths and DTOs documented in shopmgmt-questions.md
6. **Timezone Handling:** UTC storage + facility timezone for display confirmed
7. **Pagination Standard:** Consistent headers + body envelope across Moqui and Spring Boot
8. **Error Envelope:** Confirmed across accounting, people, catalog domains
9. **Idempotency:** Header-based `Idempotency-Key` per DECISION-INVENTORY-012

### 🔄 BLOCKING (Needs Implementation/Confirmation)

1. **Services Search Endpoint:** `/v1/products/services/search` not yet implemented
2. **Skills Search Endpoint:** `/v1/people/skills/search` not yet implemented
3. **Bay Business Logic:** Controllers exist; awaiting implementation in `BayController` and `BayService`
4. **Appointment Create/Load Endpoints:** Awaiting shopmgmt backend implementation
5. **CRM Customer Search:** `/v1/crm/customers/search` is a 501 stub
6. **CRM Vehicles-for-Customer:** `/v1/crm/{customerId}/vehicles` is a 501 stub
7. **Requested Services Structure:** Confirm free-text vs. catalog-backed approach with ShopMgmt

---

## Findings Summary Table

| Task | Status | Key Finding |
|------|--------|------------|
| 2.1 Services/Skills | 🔄 BLOCKED | Catalog endpoints exist; search not implemented |
| 2.2 Bay API Response | ✅ CONFIRMED | Endpoint signatures defined; controllers ready for business logic |
| 2.3 Appointment Contracts | 🔄 BLOCKED | Endpoints documented; awaiting implementation |
| 2.4 Timezone Handling | ✅ CONFIRMED | UTC + facility timezone confirmed |
| 2.5 Requested Services | 🔄 DESIGN | Free-text vs. catalog approach pending |
| 2.6 CRM Lookups | 🔄 BLOCKED | Endpoints mapped; returning 501 stubs |

---

## Documents Consulted

1. `durion-positivity-backend/pos-location/src/main/java/com/positivity/location/controller/BayController.java` — Location service bay endpoints (stubs)
2. `durion-positivity-backend/pos-shop-manager/src/main/java/com/positivity/shopManager/controller/ShopBayController.java` — Shop manager bay wrapper endpoints
3. `durion-positivity-backend/pos-shop-manager/src/main/java/com/positivity/shopManager/service/BayService.java` — Bay service delegation layer
4. `durion-positivity-backend/pos-shop-manager/src/main/java/com/positivity/shopManager/client/LocationClient.java` — HTTP client to location service
5. `durion-positivity-backend/pos-catalog/CatalogController.java` — Catalog API endpoints
6. `durion-positivity-backend/pos-customer/CrmVehiclesController.java` — Vehicle endpoints (stubs)
7. `durion-positivity-backend/pos-customer/security/CrmPermissionRegistry.java` — Permission definitions
8. `.github/agents/api-catalog.yml` — API endpoint catalog
9. `domains/shopmgmt/shopmgmt-questions.md` — Appointment contract details
10. `domains/pricing/PHASE_2_COMPLETION.md` — Timezone handling patterns
11. `durion-moqui-frontend/framework/src/main/groovy/org/moqui/impl/` — L10nFacade, timezone APIs
12. `scripts/story-output-consolidation/output/domain-location.txt` — Bay and appointment details

---

## Next Steps (Phase 3 Readiness)

**Phase 3 — UX/Validation Alignment** should focus on:
1. Map server-side validation errors to client-side UI feedback
2. Design error recovery flows (retry, override on conflicts, etc.)
3. Confirm screen layouts match Moqui/Vue patterns
4. Define client-side field validation (presence checks only; no format validation per DECISION-INVENTORY-003)
5. Accessibility and responsiveness requirements

**Phase 4 — Issue Updates & Closure** will:
1. Post detailed comments to GitHub issues #141 and #139 with confirmed contracts
2. Reference this Phase 2 summary with exact endpoint paths and DTOs
3. Flag blocking items requiring backend team responses
4. Remove `blocked:clarification` when all dependencies are resolved

---

**Execution Date:** 2026-01-25  
**Status:** COMPLETE - Ready for Phase 3  
**Prepared By:** GitHub Copilot (Agent)
