# Shop Management Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-01-27  
**OpenAPI Source:** `pos-shop-manager/target/openapi.json` (to be generated)

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Shop Management domain REST API and backend services. The shopmgmt domain manages appointment scheduling, resource assignment (bays, mobile units, mechanics), and conflict detection within automotive service shops.

Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

---

## Table of Contents

1. [JSON Field Naming Conventions](#json-field-naming-conventions)
2. [Data Types & Formats](#data-types--formats)
3. [Enum Value Conventions](#enum-value-conventions)
4. [Identifier Naming](#identifier-naming)
5. [Timestamp Conventions](#timestamp-conventions)
6. [Collection & Pagination](#collection--pagination)
7. [Error Response Format](#error-response-format)
8. [Correlation ID & Request Tracking](#correlation-id--request-tracking)
9. [Idempotency](#idempotency)
10. [API Endpoints](#api-endpoints)
11. [Entity-Specific Contracts](#entity-specific-contracts)
12. [Examples](#examples)

---

## JSON Field Naming Conventions

### Standard Pattern: camelCase

All JSON field names **MUST** use `camelCase` (not `snake_case`, not `PascalCase`).

```json
{
  "appointmentId": "abc-123",
  "scheduledStartDateTime": "2026-01-27T14:30:00-05:00",
  "facilityId": "facility-456",
  "assignmentType": "BAY",
  "createdAt": "2026-01-27T19:30:00Z"
}
```

### Rationale

- Aligns with JSON/JavaScript convention
- Matches Java property naming after Jackson deserialization
- Consistent with REST API best practices (RFC 7231)
- Consistent across all Durion platform domains

---

## Data Types & Formats

### String Fields

Use `string` type for:

- Names and descriptions
- Codes and identifiers
- Free-form text
- Enum values (serialized as strings)

```java
private String appointmentId;
private String facilityId;
private String sourceId;
private String assignmentNotes;
```

### Numeric Fields

Use `Integer` or `Long` for:

- Counts (page numbers, total results)
- Version numbers
- Reschedule counts

```java
private Integer pageNumber;
private Integer pageSize;
private Long totalCount;
private Integer rescheduleCount;
```

### Boolean Fields

Use `boolean` for true/false flags:

```java
private boolean overrideSoftConflicts;
private boolean overridable;
private boolean isPrimary;
```

### UUID/ID Fields

Use `String` for all primary and foreign key IDs (opaque strings):

```java
private String appointmentId;
private String facilityId;
private String sourceId;
private String mechanicId;
private String bayId;
private String mobileUnitId;
```

### Instant/Timestamp Fields

Use `Instant` in Java for UTC timestamps; serialize to ISO 8601 UTC in JSON:

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
private Instant createdAt;
private Instant updatedAt;
private Instant assignedAt;
```

JSON representation:

```json
{
  "createdAt": "2026-01-27T19:30:00Z",
  "updatedAt": "2026-01-27T20:15:00Z",
  "assignedAt": "2026-01-27T19:35:00Z"
}
```

### ZonedDateTime/OffsetDateTime Fields

Use `OffsetDateTime` or `ZonedDateTime` for facility-timezone-aware scheduling:

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
private OffsetDateTime scheduledStartDateTime;
private OffsetDateTime scheduledEndDateTime;
```

JSON representation (with timezone offset):

```json
{
  "scheduledStartDateTime": "2026-01-27T14:30:00-05:00",
  "scheduledEndDateTime": "2026-01-27T16:30:00-05:00",
  "facilityTimeZoneId": "America/New_York"
}
```

### LocalTime Fields

Use `LocalTime` for time-only fields (business hours):

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
private LocalTime businessHoursOpen;
private LocalTime businessHoursClose;
```

JSON representation:

```json
{
  "businessHoursOpen": "08:00:00",
  "businessHoursClose": "18:00:00"
}
```

---

## Enum Value Conventions

### Standard Pattern: UPPER_CASE_SNAKE

All enum values **MUST** use `UPPER_CASE_SNAKE` (uppercase with underscores).

```java
public enum SourceType {
    ESTIMATE,
    WORKORDER  // Note: workorder is one word
}

public enum AssignmentType {
    BAY,
    MOBILE_UNIT,
    UNASSIGNED
}

public enum ConflictSeverity {
    HARD,
    SOFT
}

public enum AppointmentStatus {
    SCHEDULED,
    CONFIRMED,
    IN_PROGRESS,
    CANCELLED,
    COMPLETED
}
```

JSON representation:

```json
{
  "sourceType": "WORKORDER",
  "assignmentType": "BAY",
  "status": "SCHEDULED"
}
```

### Naming Convention: workorder

**CRITICAL:** The term "workorder" **MUST** be written as **ONE WORD** (not "work order", "Work Order", "WorkOrder", or "workOrder").

This convention applies to:
- Enum values: `WORKORDER`
- Field names: `workorderId`, `workorderStatus`
- Code identifiers: `workorder`, `Workorder`, `WORKORDER`
- Comments and documentation

---

## Identifier Naming

### Pattern: {entity}{Id}

Primary and foreign key identifiers follow the pattern `{entity}Id` in camelCase:

```java
private String appointmentId;
private String facilityId;
private String sourceId;
private String estimateId;
private String workorderId;  // Note: workorder is one word
private String mechanicId;
private String bayId;
private String mobileUnitId;
```

### Opaque String IDs

All identifiers are treated as **opaque strings**. Do not expose internal ID structure or format (e.g., UUID format is an implementation detail).

```json
{
  "appointmentId": "appt-abc123",
  "facilityId": "fac-xyz789"
}
```

---

## Timestamp Conventions

### Two Timestamp Formats

1. **UTC Timestamps** (audit fields): `createdAt`, `updatedAt`, `assignedAt`
   - Format: ISO 8601 UTC (`2026-01-27T19:30:00Z`)
   - Use `Instant` in Java

2. **Facility-Timezone Timestamps** (scheduling fields): `scheduledStartDateTime`, `scheduledEndDateTime`
   - Format: ISO 8601 with offset (`2026-01-27T14:30:00-05:00`)
   - Use `OffsetDateTime` or `ZonedDateTime` in Java
   - Include `facilityTimeZoneId` (IANA timezone) for clarity

### Standard Audit Fields

Every entity **SHOULD** include:

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
private Instant createdAt;

@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
private Instant updatedAt;
```

---

## Collection & Pagination

### Standard Pagination Response

All paginated endpoints return:

```json
{
  "results": [...],
  "pageNumber": 0,
  "pageSize": 20,
  "totalCount": 156,
  "totalPages": 8
}
```

### Query Parameters

Standard pagination query parameters:

- `pageNumber` (default: 0, zero-indexed)
- `pageSize` (default: 20, max: 100)
- `sort` (e.g., `scheduledStartDateTime,asc` or `createdAt,desc`)

Example:
```
GET /v1/shop-manager/appointments?pageNumber=0&pageSize=20&sort=scheduledStartDateTime,asc
```

---

## Error Response Format

### Standard Error Envelope

All error responses follow this structure:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-01-27T19:30:00Z",
  "fieldErrors": [
    {
      "field": "scheduledStartDateTime",
      "message": "Start date/time is required",
      "rejectedValue": null
    }
  ]
}
```

### HTTP Status Codes

- `200 OK` — Successful read/query
- `201 Created` — Successful resource creation
- `400 Bad Request` — Validation error, malformed request
- `404 Not Found` — Resource not found
- `409 Conflict` — Scheduling conflict (soft or hard)
- `422 Unprocessable Entity` — Business rule violation
- `500 Internal Server Error` — Unexpected server error

### Domain-Specific Error Codes

#### Appointment Creation

- `VALIDATION_FAILED` — Request validation failed
- `SOURCE_NOT_FOUND` — Source estimate or workorder not found
- `SOURCE_INELIGIBLE` — Source not eligible for appointment (e.g., already scheduled)
- `FACILITY_NOT_FOUND` — Facility not found or inactive
- `SCHEDULING_CONFLICT` — Hard or soft conflict detected (HTTP 409)
- `OUTSIDE_OPERATING_HOURS` — Requested time outside facility operating hours
- `MECHANIC_UNAVAILABLE` — Assigned mechanic unavailable at requested time
- `BAY_OCCUPIED` — Requested bay already occupied
- `IDEMPOTENCY_CONFLICT` — Duplicate request with different parameters

#### Assignment Management

- `APPOINTMENT_NOT_FOUND` — Appointment not found
- `ASSIGNMENT_CONFLICT` — Resource assignment conflict
- `INVALID_ASSIGNMENT_TYPE` — Invalid assignment type
- `RESOURCE_NOT_FOUND` — Bay or mobile unit not found

---

## Correlation ID & Request Tracking

### X-Correlation-Id Header

All API requests **SHOULD** include an `X-Correlation-Id` header for request tracking and distributed tracing:

```
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
```

**Reference:** DECISION-SHOPMGMT-011

### Server Behavior

- If client provides `X-Correlation-Id`, server **MUST** use it
- If client does not provide it, server **MUST** generate one
- Server **MUST** include `correlationId` in all error responses
- Server **SHOULD** include `X-Correlation-Id` in response headers
- Server **MUST** log correlation ID for all operations

### Example Error Response with Correlation ID

```json
{
  "code": "SCHEDULING_CONFLICT",
  "message": "Scheduling conflict detected",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-01-27T19:30:00Z",
  "conflicts": [...]
}
```

---

## Idempotency

### Idempotency-Key Header

Appointment creation endpoint **MUST** support idempotency via `Idempotency-Key` header:

```
POST /v1/shop-manager/appointments
Idempotency-Key: client-request-abc123
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json
```

**Reference:** DECISION-SHOPMGMT-014

### Server Behavior

- If `Idempotency-Key` is provided:
  - Server **MUST** check for duplicate requests with same key
  - If duplicate with **same parameters**: return cached response (HTTP 200 or 201)
  - If duplicate with **different parameters**: return error (HTTP 409, code `IDEMPOTENCY_CONFLICT`)
- If `Idempotency-Key` is not provided:
  - Server **MAY** use `clientRequestId` from request body as idempotency key (if present)
  - Otherwise, request is not idempotent

### Idempotency Key Retention

Server **SHOULD** retain idempotency keys for at least **24 hours**.

---

## API Endpoints

### Base Path

All Shop Management endpoints are under:

```
/v1/shop-manager
```

### Appointment Management

#### Create Appointment

```
POST /v1/shop-manager/appointments
```

**Headers:**
- `Content-Type: application/json`
- `X-Correlation-Id: {uuid}` (recommended)
- `Idempotency-Key: {client-key}` (recommended)

**Request Body:** `AppointmentCreateRequest`

**Success Response:** `201 Created`
- Body: `AppointmentResponse`

**Error Responses:**
- `400 Bad Request` — Validation error
- `404 Not Found` — Source not found
- `409 Conflict` — Scheduling conflict
- `422 Unprocessable Entity` — Source ineligible

#### Get Appointment

```
GET /v1/shop-manager/appointments/{appointmentId}
```

**Success Response:** `200 OK`
- Body: `AppointmentResponse`

**Error Responses:**
- `404 Not Found` — Appointment not found

#### List Appointments

```
GET /v1/shop-manager/appointments
```

**Query Parameters:**
- `facilityId` (optional)
- `sourceType` (optional): `ESTIMATE` or `WORKORDER`
- `sourceId` (optional)
- `status` (optional)
- `scheduledStartFrom` (optional): ISO 8601 date/time
- `scheduledStartTo` (optional): ISO 8601 date/time
- `pageNumber` (default: 0)
- `pageSize` (default: 20)
- `sort` (default: `scheduledStartDateTime,asc`)

**Success Response:** `200 OK`
- Body: Paginated list of `AppointmentResponse`

#### Update Appointment (Reschedule)

```
PUT /v1/shop-manager/appointments/{appointmentId}/schedule
```

**Request Body:** `AppointmentRescheduleRequest`

**Success Response:** `200 OK`
- Body: `AppointmentResponse`

**Error Responses:**
- `404 Not Found` — Appointment not found
- `409 Conflict` — Scheduling conflict
- `422 Unprocessable Entity` — Reschedule limit exceeded

#### Cancel Appointment

```
DELETE /v1/shop-manager/appointments/{appointmentId}
```

**Success Response:** `204 No Content`

**Error Responses:**
- `404 Not Found` — Appointment not found

### Assignment Management

#### Get Assignment

```
GET /v1/shop-manager/appointments/{appointmentId}/assignment
```

**Success Response:** `200 OK`
- Body: `AssignmentView`

**Error Responses:**
- `404 Not Found` — Appointment or assignment not found

#### Update Assignment

```
PUT /v1/shop-manager/appointments/{appointmentId}/assignment
```

**Request Body:** `AssignmentUpdateRequest`

**Success Response:** `200 OK`
- Body: `AssignmentView`

**Error Responses:**
- `404 Not Found` — Appointment not found
- `409 Conflict` — Assignment conflict

### Create Form Initialization

#### Get Create Form Model

```
GET /v1/shop-manager/appointments/create-model
```

**Query Parameters:**
- `sourceType` (required): `ESTIMATE` or `WORKORDER`
- `sourceId` (required)
- `facilityId` (required)

**Success Response:** `200 OK`
- Body: `AppointmentCreateModel`

**Error Responses:**
- `404 Not Found` — Source or facility not found
- `422 Unprocessable Entity` — Source ineligible

---

## Entity-Specific Contracts

### AppointmentCreateRequest

**Description:** Request payload for creating a new appointment.

**Fields:**

```java
public record AppointmentCreateRequest(
    @NotNull SourceType sourceType,           // ESTIMATE | WORKORDER
    @NotBlank String sourceId,                // Opaque string
    @NotBlank String facilityId,              // Opaque string (DECISION-SHOPMGMT-012)
    @NotNull OffsetDateTime scheduledStartDateTime,  // ISO-8601 with offset
    @NotNull OffsetDateTime scheduledEndDateTime,    // ISO-8601 with offset
    String clientRequestId,                   // Optional UUID (recommended)
    boolean overrideSoftConflicts,            // Default: false
    String overrideReason                     // Required if overrideSoftConflicts=true
) {}
```

**Validation Rules:**
- `sourceType` is required
- `sourceId` is required and must not be blank
- `facilityId` is required and must not be blank (DECISION-SHOPMGMT-012)
- `scheduledStartDateTime` is required
- `scheduledEndDateTime` is required and must be after `scheduledStartDateTime`
- `overrideReason` is required if `overrideSoftConflicts` is true (DECISION-SHOPMGMT-007)

**Example:**

```json
{
  "sourceType": "WORKORDER",
  "sourceId": "wo-12345",
  "facilityId": "fac-67890",
  "scheduledStartDateTime": "2026-01-28T09:00:00-05:00",
  "scheduledEndDateTime": "2026-01-28T11:00:00-05:00",
  "clientRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "overrideSoftConflicts": false
}
```

---

### AppointmentResponse

**Description:** Response payload for appointment operations.

**Fields:**

```java
public record AppointmentResponse(
    String appointmentId,                     // Opaque string
    String appointmentStatus,                 // Opaque string (e.g., SCHEDULED, CONFIRMED)
    OffsetDateTime scheduledStartDateTime,    // ISO-8601 with offset
    OffsetDateTime scheduledEndDateTime,      // ISO-8601 with offset
    String facilityId,                        // Opaque string
    String facilityTimeZoneId,                // IANA timezone (e.g., America/New_York)
    SourceType sourceType,                    // ESTIMATE | WORKORDER
    String sourceId,                          // Opaque string
    String notificationOutcomeSummary,        // Optional (DECISION-SHOPMGMT-016)
    Instant createdAt,                        // ISO-8601 UTC
    Instant updatedAt,                        // ISO-8601 UTC
    Integer rescheduleCount                   // Optional
) {}
```

**Example:**

```json
{
  "appointmentId": "appt-abc123",
  "appointmentStatus": "SCHEDULED",
  "scheduledStartDateTime": "2026-01-28T09:00:00-05:00",
  "scheduledEndDateTime": "2026-01-28T11:00:00-05:00",
  "facilityId": "fac-67890",
  "facilityTimeZoneId": "America/New_York",
  "sourceType": "WORKORDER",
  "sourceId": "wo-12345",
  "notificationOutcomeSummary": "Email sent to customer",
  "createdAt": "2026-01-27T19:30:00Z",
  "updatedAt": "2026-01-27T19:30:00Z",
  "rescheduleCount": 0
}
```

---

### ConflictResponse

**Description:** Response payload for scheduling conflicts (HTTP 409).

**Fields:**

```java
public record ConflictResponse(
    String errorCode,                         // Fixed: "SCHEDULING_CONFLICT"
    String message,                           // Human-readable message
    String correlationId,                     // Correlation ID
    Instant timestamp,                        // ISO-8601 UTC
    List<Conflict> conflicts,                 // List of conflicts
    List<SuggestedAlternative> suggestedAlternatives  // Optional
) {}

public record Conflict(
    ConflictSeverity severity,                // HARD | SOFT
    String code,                              // E.g., OUTSIDE_OPERATING_HOURS
    String message,                           // Human-readable message
    boolean overridable,                      // HARD=false, SOFT=true
    String affectedResource                   // E.g., bay ID, mechanic ID
) {}

public record SuggestedAlternative(
    OffsetDateTime startDateTime,            // ISO-8601 with offset
    OffsetDateTime endDateTime,              // ISO-8601 with offset
    String reason                            // Optional explanation
) {}
```

**Example:**

```json
{
  "errorCode": "SCHEDULING_CONFLICT",
  "message": "Scheduling conflict detected",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-01-27T19:30:00Z",
  "conflicts": [
    {
      "severity": "SOFT",
      "code": "MECHANIC_UNAVAILABLE",
      "message": "Preferred mechanic is unavailable at requested time",
      "overridable": true,
      "affectedResource": "mech-456"
    },
    {
      "severity": "HARD",
      "code": "OUTSIDE_OPERATING_HOURS",
      "message": "Requested time is outside facility operating hours",
      "overridable": false,
      "affectedResource": "fac-67890"
    }
  ],
  "suggestedAlternatives": [
    {
      "startDateTime": "2026-01-28T10:00:00-05:00",
      "endDateTime": "2026-01-28T12:00:00-05:00",
      "reason": "Next available slot with preferred mechanic"
    }
  ]
}
```

**Reference:** DECISION-SHOPMGMT-002

---

### AssignmentView

**Description:** Read model for appointment assignment display.

**Fields:**

```java
public record AssignmentView(
    String appointmentId,                     // Opaque string
    String facilityId,                        // Opaque string
    AssignmentType assignmentType,            // BAY | MOBILE_UNIT | UNASSIGNED
    BayAssignment bay,                        // Nullable
    MobileUnitAssignment mobileUnit,          // Nullable
    MechanicAssignment mechanic,              // Nullable
    String assignmentNotes,                   // Max 500 chars
    Instant assignedAt,                       // ISO-8601 UTC
    Instant lastUpdatedAt,                    // ISO-8601 UTC
    Integer version,                          // Optimistic concurrency
    String assignmentStatus                   // Optional (e.g., AWAITING_SKILL_FULFILLMENT)
) {}

public record BayAssignment(
    String bayId,                             // Opaque string
    String bayNameOrNumber,                   // Display name
    String locationName                       // Optional
) {}

public record MobileUnitAssignment(
    String mobileUnitId,                      // Opaque string
    String mobileUnitName,                    // Optional display name
    Double lastKnownLat,                      // Nullable
    Double lastKnownLon,                      // Nullable
    Instant lastUpdatedAt                     // Nullable
) {}

public record MechanicAssignment(
    String mechanicId,                        // Nullable
    String displayName,                       // Nullable
    String photoUrl                           // Nullable
) {}
```

**Example (Bay Assignment):**

```json
{
  "appointmentId": "appt-abc123",
  "facilityId": "fac-67890",
  "assignmentType": "BAY",
  "bay": {
    "bayId": "bay-001",
    "bayNameOrNumber": "Bay 1",
    "locationName": "Main Shop"
  },
  "mobileUnit": null,
  "mechanic": {
    "mechanicId": "mech-456",
    "displayName": "John Smith",
    "photoUrl": "https://example.com/photos/mech-456.jpg"
  },
  "assignmentNotes": "Customer requested John",
  "assignedAt": "2026-01-27T19:35:00Z",
  "lastUpdatedAt": "2026-01-27T19:35:00Z",
  "version": 1,
  "assignmentStatus": null
}
```

**Example (Mobile Unit Assignment):**

```json
{
  "appointmentId": "appt-xyz789",
  "facilityId": "fac-67890",
  "assignmentType": "MOBILE_UNIT",
  "bay": null,
  "mobileUnit": {
    "mobileUnitId": "mobile-002",
    "mobileUnitName": "Mobile Unit 2",
    "lastKnownLat": 40.7128,
    "lastKnownLon": -74.0060,
    "lastUpdatedAt": "2026-01-27T19:30:00Z"
  },
  "mechanic": {
    "mechanicId": "mech-789",
    "displayName": "Jane Doe",
    "photoUrl": null
  },
  "assignmentNotes": "On-site service at customer location",
  "assignedAt": "2026-01-27T19:40:00Z",
  "lastUpdatedAt": "2026-01-27T19:40:00Z",
  "version": 1,
  "assignmentStatus": null
}
```

---

### AppointmentCreateModel

**Description:** Form initialization model for appointment creation.

**Fields:**

```java
public record AppointmentCreateModel(
    String facilityId,                        // Opaque string
    SourceType sourceType,                    // ESTIMATE | WORKORDER
    String sourceId,                          // Opaque string
    String facilityTimeZoneId,                // IANA timezone
    String sourceStatus,                      // Source document status
    OffsetDateTime suggestedStartDateTime,    // Optional
    OffsetDateTime suggestedEndDateTime,      // Optional
    LocalTime businessHoursOpen,              // Optional
    LocalTime businessHoursClose              // Optional
) {}
```

**Example:**

```json
{
  "facilityId": "fac-67890",
  "sourceType": "WORKORDER",
  "sourceId": "wo-12345",
  "facilityTimeZoneId": "America/New_York",
  "sourceStatus": "APPROVED",
  "suggestedStartDateTime": "2026-01-28T09:00:00-05:00",
  "suggestedEndDateTime": "2026-01-28T11:00:00-05:00",
  "businessHoursOpen": "08:00:00",
  "businessHoursClose": "18:00:00"
}
```

---

### AssignmentUpdateRequest

**Description:** Request payload for updating appointment assignment.

**Fields:**

```java
public record AssignmentUpdateRequest(
    AssignmentType assignmentType,            // BAY | MOBILE_UNIT | UNASSIGNED
    String bayId,                             // Required if assignmentType=BAY
    String mobileUnitId,                      // Required if assignmentType=MOBILE_UNIT
    String mechanicId,                        // Optional
    String assignmentNotes,                   // Optional, max 500 chars
    Integer version                           // Required for optimistic concurrency
) {}
```

**Validation Rules:**
- `assignmentType` is required
- `bayId` is required if `assignmentType` is `BAY`
- `mobileUnitId` is required if `assignmentType` is `MOBILE_UNIT`
- `assignmentNotes` max length is 500 characters
- `version` is required for optimistic concurrency

**Example (Bay Assignment):**

```json
{
  "assignmentType": "BAY",
  "bayId": "bay-003",
  "mobileUnitId": null,
  "mechanicId": "mech-456",
  "assignmentNotes": "Moved to Bay 3 due to equipment availability",
  "version": 2
}
```

**Example (Unassigned):**

```json
{
  "assignmentType": "UNASSIGNED",
  "bayId": null,
  "mobileUnitId": null,
  "mechanicId": null,
  "assignmentNotes": "Awaiting mechanic availability",
  "version": 3
}
```

---

### AppointmentRescheduleRequest

**Description:** Request payload for rescheduling an appointment.

**Fields:**

```java
public record AppointmentRescheduleRequest(
    @NotNull OffsetDateTime scheduledStartDateTime,  // ISO-8601 with offset
    @NotNull OffsetDateTime scheduledEndDateTime,    // ISO-8601 with offset
    boolean overrideSoftConflicts,            // Default: false
    String overrideReason,                    // Required if overrideSoftConflicts=true
    Integer version                           // Required for optimistic concurrency
) {}
```

**Validation Rules:**
- `scheduledStartDateTime` is required
- `scheduledEndDateTime` is required and must be after `scheduledStartDateTime`
- `overrideReason` is required if `overrideSoftConflicts` is true
- `version` is required for optimistic concurrency

**Example:**

```json
{
  "scheduledStartDateTime": "2026-01-28T14:00:00-05:00",
  "scheduledEndDateTime": "2026-01-28T16:00:00-05:00",
  "overrideSoftConflicts": false,
  "overrideReason": null,
  "version": 1
}
```

---

## Examples

### Example 1: Create Appointment from Workorder (Success)

**Request:**

```http
POST /v1/shop-manager/appointments HTTP/1.1
Host: api.durion.example.com
Content-Type: application/json
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
Idempotency-Key: client-req-abc123

{
  "sourceType": "WORKORDER",
  "sourceId": "wo-12345",
  "facilityId": "fac-67890",
  "scheduledStartDateTime": "2026-01-28T09:00:00-05:00",
  "scheduledEndDateTime": "2026-01-28T11:00:00-05:00",
  "clientRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "overrideSoftConflicts": false
}
```

**Response:**

```http
HTTP/1.1 201 Created
Content-Type: application/json
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000

{
  "appointmentId": "appt-abc123",
  "appointmentStatus": "SCHEDULED",
  "scheduledStartDateTime": "2026-01-28T09:00:00-05:00",
  "scheduledEndDateTime": "2026-01-28T11:00:00-05:00",
  "facilityId": "fac-67890",
  "facilityTimeZoneId": "America/New_York",
  "sourceType": "WORKORDER",
  "sourceId": "wo-12345",
  "notificationOutcomeSummary": "Email sent successfully",
  "createdAt": "2026-01-27T19:30:00Z",
  "updatedAt": "2026-01-27T19:30:00Z",
  "rescheduleCount": 0
}
```

---

### Example 2: Create Appointment with Soft Conflict (Override)

**Request:**

```http
POST /v1/shop-manager/appointments HTTP/1.1
Host: api.durion.example.com
Content-Type: application/json
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440001

{
  "sourceType": "ESTIMATE",
  "sourceId": "est-56789",
  "facilityId": "fac-67890",
  "scheduledStartDateTime": "2026-01-28T17:30:00-05:00",
  "scheduledEndDateTime": "2026-01-28T19:00:00-05:00",
  "overrideSoftConflicts": true,
  "overrideReason": "Customer special request, approved by manager"
}
```

**Response (if no hard conflicts):**

```http
HTTP/1.1 201 Created
Content-Type: application/json
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440001

{
  "appointmentId": "appt-def456",
  "appointmentStatus": "SCHEDULED",
  "scheduledStartDateTime": "2026-01-28T17:30:00-05:00",
  "scheduledEndDateTime": "2026-01-28T19:00:00-05:00",
  "facilityId": "fac-67890",
  "facilityTimeZoneId": "America/New_York",
  "sourceType": "ESTIMATE",
  "sourceId": "est-56789",
  "createdAt": "2026-01-27T19:35:00Z",
  "updatedAt": "2026-01-27T19:35:00Z",
  "rescheduleCount": 0
}
```

---

### Example 3: Create Appointment with Hard Conflict (Rejected)

**Request:**

```http
POST /v1/shop-manager/appointments HTTP/1.1
Host: api.durion.example.com
Content-Type: application/json
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440002

{
  "sourceType": "WORKORDER",
  "sourceId": "wo-99999",
  "facilityId": "fac-67890",
  "scheduledStartDateTime": "2026-01-28T20:00:00-05:00",
  "scheduledEndDateTime": "2026-01-28T22:00:00-05:00",
  "overrideSoftConflicts": false
}
```

**Response:**

```http
HTTP/1.1 409 Conflict
Content-Type: application/json
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440002

{
  "errorCode": "SCHEDULING_CONFLICT",
  "message": "Scheduling conflict detected",
  "correlationId": "550e8400-e29b-41d4-a716-446655440002",
  "timestamp": "2026-01-27T19:40:00Z",
  "conflicts": [
    {
      "severity": "HARD",
      "code": "OUTSIDE_OPERATING_HOURS",
      "message": "Requested time is outside facility operating hours (8:00 AM - 6:00 PM)",
      "overridable": false,
      "affectedResource": "fac-67890"
    }
  ],
  "suggestedAlternatives": [
    {
      "startDateTime": "2026-01-29T08:00:00-05:00",
      "endDateTime": "2026-01-29T10:00:00-05:00",
      "reason": "Next available slot during operating hours"
    }
  ]
}
```

---

### Example 4: Update Assignment

**Request:**

```http
PUT /v1/shop-manager/appointments/appt-abc123/assignment HTTP/1.1
Host: api.durion.example.com
Content-Type: application/json
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440003

{
  "assignmentType": "BAY",
  "bayId": "bay-005",
  "mechanicId": "mech-789",
  "assignmentNotes": "Moved to larger bay for chassis work",
  "version": 1
}
```

**Response:**

```http
HTTP/1.1 200 OK
Content-Type: application/json
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440003

{
  "appointmentId": "appt-abc123",
  "facilityId": "fac-67890",
  "assignmentType": "BAY",
  "bay": {
    "bayId": "bay-005",
    "bayNameOrNumber": "Bay 5",
    "locationName": "Main Shop"
  },
  "mobileUnit": null,
  "mechanic": {
    "mechanicId": "mech-789",
    "displayName": "Jane Doe",
    "photoUrl": "https://example.com/photos/mech-789.jpg"
  },
  "assignmentNotes": "Moved to larger bay for chassis work",
  "assignedAt": "2026-01-27T19:35:00Z",
  "lastUpdatedAt": "2026-01-27T19:45:00Z",
  "version": 2,
  "assignmentStatus": null
}
```

---

### Example 5: Validation Error

**Request:**

```http
POST /v1/shop-manager/appointments HTTP/1.1
Host: api.durion.example.com
Content-Type: application/json
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440004

{
  "sourceType": "WORKORDER",
  "sourceId": "",
  "facilityId": "fac-67890",
  "scheduledStartDateTime": "2026-01-28T09:00:00-05:00"
}
```

**Response:**

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440004

{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "correlationId": "550e8400-e29b-41d4-a716-446655440004",
  "timestamp": "2026-01-27T19:50:00Z",
  "fieldErrors": [
    {
      "field": "sourceId",
      "message": "Source ID must not be blank",
      "rejectedValue": ""
    },
    {
      "field": "scheduledEndDateTime",
      "message": "Scheduled end date/time is required",
      "rejectedValue": null
    }
  ]
}
```

---

## Related Documentation

- **AGENT_GUIDE.md** — Domain boundaries and design decisions
- **DOMAIN_NOTES.md** — Additional domain context
- **STORY_VALIDATION_CHECKLIST.md** — Story acceptance criteria

### Key Design Decisions

- **DECISION-SHOPMGMT-002** — Hard vs Soft conflict classification
- **DECISION-SHOPMGMT-007** — Override reason required for soft conflicts
- **DECISION-SHOPMGMT-008** — Operating hours source of truth (Location domain)
- **DECISION-SHOPMGMT-009** — Mechanic HR profile source of truth (People domain)
- **DECISION-SHOPMGMT-011** — Correlation ID for request tracking
- **DECISION-SHOPMGMT-012** — Facility ID required for appointment creation
- **DECISION-SHOPMGMT-014** — Idempotency support
- **DECISION-SHOPMGMT-015** — Timezone-aware scheduling
- **DECISION-SHOPMGMT-016** — Notification outcome summary

---

**End of Guide**
