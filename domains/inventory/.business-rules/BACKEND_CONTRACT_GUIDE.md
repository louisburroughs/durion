---
title: Inventory Management Backend Contract Guide
domain: inventory
doc_type: backend_contract
contract:
  status: draft
  owner_repo: louisburroughs/durion
  guide_path: domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md
  openapi_source: pos-inventory/target/openapi.yaml

traceability:
  capability_manifest: docs/capabilities
last_updated: 2026-02-21
---

## CAP-221: Roles, Permissions, and Audit Controls

See domains/BACKEND_CONTRACT_CAPABILITY_TEMPLATE.md for section structure

### Backend Issues

- Inventory audit events and movement/workorder links: https://github.com/louisburroughs/durion-positivity-backend/issues/22
- Inventory roles, permissions, and enforcement: https://github.com/louisburroughs/durion-positivity-backend/issues/23

### Event Contract (Issue #22)

- Topics:
  - `inventory.v1.movements` (movement lifecycle events)
  - `inventory.v1.workorder-links` (workorder link/unlink events)
- Partition key: `aggregateId` (producer MUST partition by `aggregate.id`)
- Required envelope (all events emitted by inventory producers MUST conform):

```json
{
  "schemaVersion": 1,
  "eventId": "uuid-v7",
  "eventType": "MovementCreated|MovementAdjusted|WorkOrderLinked|WorkOrderUnlinked",
  "occurredAt": "ISO8601",
  "emittedAt": "ISO8601",
  "sourceSystem": "inventory",
  "tenantId": "string",
  "actor": { "type": "user|system", "id": "string", "displayName": "string" },
  "correlationId": "string",
  "aggregate": { "type": "movement|workorder", "id": "string" },
  "payload": {}
}
```

- Producer responsibilities:
  - Inventory services are the authoritative producers for the above topics.
  - Emit events for state changes: MovementCreated, MovementAdjusted, WorkOrderLinked, WorkOrderUnlinked.
  - Populate `actor` fields from the security context as defined in ADR-0018.
  - Set `sourceSystem` to `inventory` and include `tenantId` where multi-tenancy applies.
  - Ensure `eventId` uses UUIDv7 for time-ordered idempotency semantics.
  - Partition messages by `aggregate.id` to preserve ordering per aggregate.

- Idempotency note:
  - `eventId` SHOULD be stable per operation (generate once per logical operation) so consumers can detect duplicates.
  - Producers SHOULD design operations to be idempotent on retries; consumer-facing semantics rely on `eventId` + `aggregate.id` for deduplication.

### Permission Matrix (Issue #23)

Permission namespace: `inventory:*`

- Canonical permissions:
  - Catalog: `inventory:item:view`, `inventory:item:create`, `inventory:item:update`, `inventory:item:archive`
  - Stock: `inventory:stock:view`, `inventory:stock:adjust`, `inventory:stock:transfer`
  - Counts: `inventory:count:view`, `inventory:count:initiate`, `inventory:count:submit`, `inventory:count:approve`
  - Receiving: `inventory:receiving:view`, `inventory:receiving:receive`, `inventory:receiving:reverse`
  - Locations: `inventory:location:view`, `inventory:location:create`, `inventory:location:update`, `inventory:location:archive`
  - Reporting: `inventory:report:view`, `inventory:report:export`

- Default roles (seed):

  | Role | Grants (summary) |
  |------|------------------|
  | Inventory Viewer | all `*:view` + `inventory:report:view` |
  | Inventory Clerk | Viewer + `inventory:count:initiate`, `inventory:count:submit`, `inventory:receiving:receive` |
  | Inventory Manager | Clerk + `inventory:item:create`, `inventory:item:update`, `inventory:item:archive`, `inventory:stock:transfer`, `inventory:report:export` |
  | Inventory Controller | Viewer + `inventory:count:approve`, `inventory:stock:adjust`, `inventory:receiving:reverse` |
  | Inventory Admin | all `inventory:*` |

- Enforcement pattern:
  - All inventory APIs MUST enforce RBAC using the platform security framework (issue #42 dependency).
  - Server-side enforcement is DENY-BY-DEFAULT: absence of required permission MUST result in `403 Forbidden` with standard error response format.
  - Permission checks MUST occur before performing state-changing operations and before emitting events.

- Audit events (emit on relevant actions):
  - `inventory.stock.adjusted`
  - `inventory.count.approved`
  - `inventory.receiving.reversed`
  - `inventory.access.denied` (when a permission check fails; include actor, path, attemptedAction, reason)

### Endpoint Permission Table

Mapping of OpenAPI paths (service-local) to canonical `inventory:*` permissions. Gateway-normalized path shown using `http://localhost:8080/v1/inventory/...`.

| HTTP | Gateway Path | Required Permission |
|------|--------------|---------------------|
| GET | `http://localhost:8080/v1/inventory/availability/{productId}` | `inventory:stock:view` |
| POST | `http://localhost:8080/v1/inventory/availability/{productId}` | `inventory:stock:adjust` |
| GET | `http://localhost:8080/v1/inventory/sites/{siteId}/defaultLocations` | `inventory:location:view` |
| PUT | `http://localhost:8080/v1/inventory/sites/{siteId}/defaultLocations` | `inventory:location:update` |
| POST | `http://localhost:8080/v1/inventory/pickingLists/{id}/confirm` | `inventory:stock:adjust` |
| GET | `http://localhost:8080/v1/inventory/cycleCountAdjustments` | `inventory:count:view` |
| POST | `http://localhost:8080/v1/inventory/cycleCountAdjustments` | `inventory:count:initiate` |
| POST | `http://localhost:8080/v1/inventory/cycleCountAdjustments/{adjustmentId}/approve` | `inventory:count:approve` |
| POST | `http://localhost:8080/v1/inventory/cycleCountAdjustments/{adjustmentId}/reject` | `inventory:count:approve` |
| POST | `http://localhost:8080/v1/inventory/locations/{locationId}/deactivate` | `inventory:location:update` |
| POST | `http://localhost:8080/v1/inventory/cycleCount/submit` | `inventory:count:submit` |
| POST | `http://localhost:8080/v1/inventory/cycleCount/recount` | `inventory:count:submit` |

Notes:
- Paths using `/api/v1/` prefix in the OpenAPI are exposed via the gateway as `http://localhost:8080/v1/inventory/...` (see Gateway path note below). Implementers MUST validate permissions using the internal route's security context.

### Behavioral Assertions

- `403 Forbidden` MUST be returned when the authenticated principal lacks the required `inventory:*` permission for the operation.
- When permission is denied, an `inventory.access.denied` audit event SHOULD be emitted (include `actor`, `path`, `attemptedAction`, `correlationId`).
- Event emission guarantees:
  - Events listed in the Event Contract MUST be emitted for successful state changes.
  - `eventId` MUST use UUIDv7 and be stable for the operation to support idempotent processing.

### Provider Test Hints (ContractBehaviorIT)

- Permission enforcement tests:
  - Assert that requests without the required permission return `403` and the `inventory.access.denied` audit event is emitted to the audit topic or stored in the audit log.
  - Seed test principals with and without each relevant permission and verify success vs `403` behavior.

- Audit/event tests:
  - For state-changing operations (stock adjustments, count approvals, receiving reversals), assert that the appropriate Kafka topic receives an event conforming to the envelope and `payload` shape.
  - Verify partitioning by `aggregate.id` and that `eventId` is present and follows UUIDv7 semantics (time-ordered). Tests may validate idempotency by replaying identical operations and asserting dedup behaviour on consumers where applicable.

### Gateway Path Note

- The API gateway normalizes service paths to `http://localhost:8080/v{version}/{domain}/...`.
- Therefore OpenAPI paths using `/api/v1/inventory/...` or `/v1/inventory/...` are exposed to clients as `http://localhost:8080/v1/inventory/...`.

### General Notes and References

- Security implementation depends on the platform RBAC service (issue #42). Coordinate permission constant names with the shared security repository.
- See `domains/BACKEND_CONTRACT_CAPABILITY_TEMPLATE.md` for the required capability documentation structure and examples.
- Related ADRs and docs:
  - ADR-0018 (Audit actor fields from security context)
  - `docs/architecture/observability/OBSERVABILITY.md` for event logging and telemetry guidelines
  - OpenAPI source: `pos-inventory/target/openapi.yaml`

# Inventory Management Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-01-27  
**OpenAPI Source:** `pos-inventory/target/openapi.yaml`

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Inventory Management domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

This guide is generated from the OpenAPI specification and follows the standards established across all Durion platform domains.

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
9. [API Endpoints](#api-endpoints)
10. [Entity-Specific Contracts](#entity-specific-contracts)
11. [Examples](#examples)

---

## JSON Field Naming Conventions

### Standard Pattern: camelCase

All JSON field names **MUST** use `camelCase` (not `snake_case`, not `PascalCase`).

```json
{
  "id": "abc-123",
  "createdAt": "2026-01-27T14:30:00Z",
  "updatedAt": "2026-01-27T15:45:30Z",
  "status": "ACTIVE"
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
private String id;
private String name;
private String description;
private String status;
```

### Numeric Fields

Use `Integer` or `Long` for:

- Counts (page numbers, total results)
- Version numbers
- Sequence numbers

```java
private Integer pageNumber;
private Integer pageSize;
private Long totalCount;
```

### Boolean Fields

Use `boolean` for true/false flags:

```java
private boolean isActive;
private boolean isPrimary;
private boolean hasPermission;
```

### UUID/ID Fields

Use `String` for all primary and foreign key IDs:

```java
private String id;
private String parentId;
private String referenceId;
```

### Instant/Timestamp Fields

Use `Instant` in Java; serialize to ISO 8601 UTC in JSON:

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
private Instant createdAt;
private Instant updatedAt;
```

JSON representation:

```json
{
  "createdAt": "2026-01-27T14:30:00Z",
  "updatedAt": "2026-01-27T15:45:30Z"
}
```

### LocalDate Fields

Use `LocalDate` for date-only fields (no time component):

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
private LocalDate effectiveFrom;
private LocalDate effectiveTo;
```

JSON representation:

```json
{
  "effectiveFrom": "2026-01-01",
  "effectiveTo": "2026-12-31"
}
```

---

## Enum Value Conventions

### Standard Pattern: UPPER_SNAKE_CASE

All enum values **MUST** use `UPPER_SNAKE_CASE`:

```java
public enum Status {
    ACTIVE,
    INACTIVE,
    PENDING_APPROVAL,
    ARCHIVED
}
```

### Enums in this Domain

#### AdjustmentResponse.requiredApprovalTier

- `TIER_1_MANAGER`
- `TIER_2_DIRECTOR`

#### AdjustmentResponse.status

- `PENDING_APPROVAL`
- `AUTO_APPROVED`
- `APPROVED`
- `POSTED`
- `REJECTED`
- `FAILED`

#### CountResponse.taskStatus

- `ASSIGNED`
- `COUNTED_PENDING_REVIEW`
- `REQUIRES_INVESTIGATION`
- `APPROVED`
- `REJECTED`

#### CycleCountTask.status

- `ASSIGNED`
- `COUNTED_PENDING_REVIEW`
- `REQUIRES_INVESTIGATION`
- `APPROVED`
- `REJECTED`

---

## Identifier Naming

### Standard Pattern

- Primary keys: `id` or `{entity}Id` (e.g., `customerId`, `orderId`)
- Foreign keys: `{entity}Id` (e.g., `parentId`, `accountId`)
- Composite identifiers: use structured object, not concatenated string

### Examples

```json
{
  "id": "abc-123",
  "customerId": "cust-456",
  "orderId": "ord-789"
}
```

---

## Timestamp Conventions

### Standard Pattern: ISO 8601 UTC

All timestamps **MUST** be:

- Serialized in ISO 8601 format with UTC timezone (`Z` suffix)
- Stored as `Instant` in Java
- Include millisecond precision when available

```json
{
  "createdAt": "2026-01-27T14:30:00.123Z",
  "updatedAt": "2026-01-27T15:45:30.456Z"
}
```

### Common Timestamp Fields

- `createdAt`: When the entity was created
- `updatedAt`: When the entity was last updated
- `deletedAt`: When the entity was soft-deleted (if applicable)
- `effectiveFrom`: Start date for effective dating
- `effectiveTo`: End date for effective dating

---

## Collection & Pagination

### Standard Pagination Request

```json
{
  "pageNumber": 0,
  "pageSize": 20,
  "sortField": "createdAt",
  "sortOrder": "DESC"
}
```

### Standard Pagination Response

```json
{
  "results": [...],
  "totalCount": 150,
  "pageNumber": 0,
  "pageSize": 20,
  "totalPages": 8
}
```

### Guidelines

- Use zero-based page numbering
- Default page size: 20 items
- Maximum page size: 100 items
- Include total count for client-side pagination controls

---

## Error Response Format

### Standard Error Response

All error responses **MUST** follow this format:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "correlationId": "abc-123-def-456",
  "timestamp": "2026-01-27T14:30:00Z",
  "fieldErrors": [
    {
      "field": "email",
      "message": "Invalid email format",
      "rejectedValue": "invalid-email"
    }
  ]
}
```

### Standard HTTP Status Codes

- `200 OK`: Successful GET, PUT, PATCH
- `201 Created`: Successful POST
- `204 No Content`: Successful DELETE
- `400 Bad Request`: Validation error
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `409 Conflict`: Business rule violation
- `422 Unprocessable Entity`: Semantic validation error
- `500 Internal Server Error`: Unexpected server error
- `501 Not Implemented`: Endpoint not yet implemented

---

## Correlation ID & Request Tracking

### X-Correlation-Id Header

All API requests **SHOULD** include an `X-Correlation-Id` header for distributed tracing:

```http
GET /v1/inventory/entities/123
X-Correlation-Id: abc-123-def-456
```

### Response Headers

All API responses **MUST** echo the correlation ID:

```http
HTTP/1.1 200 OK
X-Correlation-Id: abc-123-def-456
```

### Error Responses

All error responses **MUST** include the correlation ID in the body:

```json
{
  "code": "NOT_FOUND",
  "message": "Entity not found",
  "correlationId": "abc-123-def-456"
}
```

**Reference:** See `DECISION-INVENTORY-012` in domain AGENT_GUIDE.md for correlation ID standards.

---

## API Endpoints

### Endpoint Summary

This domain exposes **18** REST API endpoints:

| Method | Path | Summary |
|--------|------|---------|
| GET | `/api/inventory/cycleCount/auditor/{auditorId}/tasks` | Get tasks assigned to an auditor |
| POST | `/api/inventory/cycleCount/recount` | Submit a recount for a cycle count task |
| POST | `/api/inventory/cycleCount/submit` | Submit a count for a cycle count task |
| GET | `/api/inventory/cycleCount/task/{taskId}` | Get cycle count task details |
| GET | `/api/inventory/cycleCount/task/{taskId}/history` | Get count history for a task |
| POST | `/api/inventory/locations/{locationId}/deactivate` |  |
| GET | `/api/v1/inventory/cycleCountAdjustments` | List adjustments by status |
| POST | `/api/v1/inventory/cycleCountAdjustments` | Create cycle count adjustment |
| GET | `/api/v1/inventory/cycleCountAdjustments/pending` | List pending approvals |
| GET | `/api/v1/inventory/cycleCountAdjustments/pending/count` | Count pending approvals |
| GET | `/api/v1/inventory/cycleCountAdjustments/{adjustmentId}` | Get adjustment details |
| POST | `/api/v1/inventory/cycleCountAdjustments/{adjustmentId}/approve` | Approve adjustment |
| POST | `/api/v1/inventory/cycleCountAdjustments/{adjustmentId}/reject` | Reject adjustment |
| GET | `/v1/inventory/availability/{productId}` | Query inventory availability |
| POST | `/v1/inventory/availability/{productId}` | Update inventory availability |
| POST | `/v1/inventory/pickingLists/{id}/confirm` | Confirm picking list |
| GET | `/v1/inventory/sites/{siteId}/defaultLocations` | Get site default locations |
| PUT | `/v1/inventory/sites/{siteId}/defaultLocations` | Replace site default locations |

### Endpoint Details

#### GET /api/inventory/cycleCount/auditor/{auditorId}/tasks

**Summary:** Get tasks assigned to an auditor

**Description:** Retrieves all cycle count tasks assigned to a specific auditor.

**Operation ID:** `getAuditorTasks`

**Parameters:**

- `auditorId` (path, Required, string): Auditor ID

**Responses:**

- `200`: Tasks retrieved successfully


---

#### POST /api/inventory/cycleCount/recount

**Summary:** Submit a recount for a cycle count task

**Description:** Records a recount with permission validation and limit enforcement. Maximum 2 recounts allowed (3 total counts).

**Operation ID:** `submitRecount`

**Responses:**

- `200`: Recount submitted successfully
- `400`: Invalid request or recount limit exceeded
- `403`: Insufficient permission
- `404`: Task not found


---

#### POST /api/inventory/cycleCount/submit

**Summary:** Submit a count for a cycle count task

**Description:** Records the actual quantity counted by an auditor. Calculates variance and updates task status.

**Operation ID:** `submitCount`

**Responses:**

- `200`: Count submitted successfully
- `400`: Invalid request or quantity
- `404`: Task not found


---

#### GET /api/inventory/cycleCount/task/{taskId}

**Summary:** Get cycle count task details

**Description:** Retrieves details of a specific cycle count task.

**Operation ID:** `getTask`

**Parameters:**

- `taskId` (path, Required, string): Task ID

**Responses:**

- `200`: Task retrieved successfully
- `404`: Task not found


---

#### GET /api/inventory/cycleCount/task/{taskId}/history

**Summary:** Get count history for a task

**Description:** Retrieves all count entries (original + recounts) for a task, ordered by sequence.

**Operation ID:** `getCountHistory`

**Parameters:**

- `taskId` (path, Required, string): Task ID

**Responses:**

- `200`: History retrieved successfully


---

#### POST /api/inventory/locations/{locationId}/deactivate

**Operation ID:** `deactivate`

**Parameters:**

- `locationId` (path, Required, string): 

**Responses:**

- `200`: OK


---

#### GET /api/v1/inventory/cycleCountAdjustments

**Summary:** List adjustments by status

**Description:** Lists all adjustments matching the specified status

**Operation ID:** `listAdjustments`

**Parameters:**

- `status` (query, Optional, string): Filter by adjustment status

**Responses:**

- `200`: Adjustments retrieved


---

#### POST /api/v1/inventory/cycleCountAdjustments

**Summary:** Create cycle count adjustment

**Description:** Creates a new adjustment from a cycle count. Automatically evaluates against approval thresholds.

**Operation ID:** `createAdjustment`

**Responses:**

- `201`: Adjustment created successfully
- `400`: Invalid request or no variance detected


---

#### GET /api/v1/inventory/cycleCountAdjustments/pending

**Summary:** List pending approvals

**Description:** Lists all adjustments awaiting approval

**Operation ID:** `listPendingApprovals`

**Responses:**

- `200`: Pending adjustments retrieved


---

#### GET /api/v1/inventory/cycleCountAdjustments/pending/count

**Summary:** Count pending approvals

**Description:** Returns the count of adjustments awaiting approval

**Operation ID:** `countPendingApprovals`

**Responses:**

- `200`: Count retrieved


---

#### GET /api/v1/inventory/cycleCountAdjustments/{adjustmentId}

**Summary:** Get adjustment details

**Description:** Retrieves details of a specific cycle count adjustment

**Operation ID:** `getAdjustment`

**Parameters:**

- `adjustmentId` (path, Required, integer): Adjustment ID

**Responses:**

- `200`: Adjustment found
- `404`: Adjustment not found


---

#### POST /api/v1/inventory/cycleCountAdjustments/{adjustmentId}/approve

**Summary:** Approve adjustment

**Description:** Approves a pending adjustment and posts it to the inventory ledger

**Operation ID:** `approveAdjustment`

**Parameters:**

- `adjustmentId` (path, Required, integer): Adjustment ID

**Responses:**

- `200`: Adjustment approved and posted
- `400`: Adjustment not found or not in approvable state
- `403`: User lacks required approval permission


---

#### POST /api/v1/inventory/cycleCountAdjustments/{adjustmentId}/reject

**Summary:** Reject adjustment

**Description:** Rejects a pending adjustment with a reason. No inventory changes are made.

**Operation ID:** `rejectAdjustment`

**Parameters:**

- `adjustmentId` (path, Required, integer): Adjustment ID

**Responses:**

- `200`: Adjustment rejected
- `400`: Adjustment not found or not in rejectable state
- `403`: User lacks required approval permission


---

#### GET /v1/inventory/availability/{productId}

**Summary:** Query inventory availability

**Description:** Returns availability for a product. Stub implementation.

**Operation ID:** `queryInventoryAvailability`

**Parameters:**

- `productId` (path, Required, string): Product identifier

**Responses:**

- `200`: Availability returned
- `501`: Not implemented


---

#### POST /v1/inventory/availability/{productId}

**Summary:** Update inventory availability

**Description:** Updates availability for a product. Stub implementation.

**Operation ID:** `updateInventoryAvailability`

**Parameters:**

- `productId` (path, Required, string): Product identifier

**Responses:**

- `200`: Availability updated
- `501`: Not implemented


---

#### POST /v1/inventory/pickingLists/{id}/confirm

**Summary:** Confirm picking list

**Description:** Confirms a picking list and commits consumption. Stub implementation.

**Operation ID:** `confirmPickingList`

**Parameters:**

- `id` (path, Required, string): Picking list identifier

**Responses:**

- `200`: Picking list confirmed
- `501`: Not implemented


---

#### GET /v1/inventory/sites/{siteId}/defaultLocations

**Summary:** Get site default locations

**Description:** Returns configured default locations for a site. Stub implementation.

**Operation ID:** `getSiteDefaultLocations`

**Parameters:**

- `siteId` (path, Required, string): Site identifier

**Responses:**

- `200`: Default locations returned
- `501`: Not implemented


---

#### PUT /v1/inventory/sites/{siteId}/defaultLocations

**Summary:** Replace site default locations

**Description:** Replaces the configured default locations for a site. Stub implementation.

**Operation ID:** `putSiteDefaultLocations`

**Parameters:**

- `siteId` (path, Required, string): Site identifier

**Responses:**

- `200`: Default locations replaced
- `501`: Not implemented



---

### CAP-170 — Availability & Inventory Visibility

This section documents the contract additions for CAP-170 (Availability & Inventory Visibility).

- **Gateway paths (v1 inventory):**
  - `GET http://localhost:8080/v1/inventory/availability/{productId}`
  - `POST http://localhost:8080/v1/inventory/availability/{productId}`

- **Notes:**
  - These endpoints are defined in the `Inventory Availability` tag in the service OpenAPI. The implementation currently contains stubs that may return `501 Not Implemented` for unimplemented behavior. The contract below specifies the expected production behavior for CAP-170.
  - For CAP-170 the canonical response for the `GET` endpoint is a per-location array (multi-location) of availability objects (see schema section). This is an important contract requirement even though the current OpenAPI artifact includes a single-object schema reference; the contract requires a multi-location array response.

- **Implementation links (backend stories):**
  - Feed ingestion / manufacturer normalization: https://github.com/louisburroughs/durion-positivity-backend/issues/46
  - Distributor feed normalization & lead-time policy: https://github.com/louisburroughs/durion-positivity-backend/issues/47
  - Availability endpoint implementation & behavioral tests: https://github.com/louisburroughs/durion-positivity-backend/issues/48

#### Response Shape (per-location availability)

The `GET` response is a JSON array where each element represents availability for a single location for the requested product. Example element fields (field names normalized to contract):

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | string (uuid) | Yes | Product identifier |
| `locationId` | string (uuid) | Yes | Location identifier |
| `locationName` | string | No | Human readable location name (optional) |
| `onHandQuantity` | number | Yes | Physical on-hand quantity at this location |
| `allocatedQuantity` | number | Yes | Quantity currently allocated / reserved (hard commitments) |
| `availableToPromiseQuantity` | number | Yes | ATP for this location (see ATP formula below) |
| `uom` | string | Yes | Unit of measure (e.g., `EACH`) |
| `asOfTimestamp` | string (date-time) | Yes | Timestamp when calculation was performed |
| `expectedReceiptsQuantity` | number | No | Known expected receipts (not included in v1 ATP calculation) |

The above fields map to the OpenAPI schema `InventoryAvailabilityResponse` properties (`onHandQty`, `allocatedQty`, `atpQty`, etc.). The contract uses the more explicit names above for clarity; implementers MUST accept either the OpenAPI canonical names or the contract names, but responses emitted by the service MUST use the contract field names in the array for multi-location results.

#### ATP Calculation (Available-To-Promise)

ATP is calculated per-location as:

```
ATP = On-Hand − Active Reservations
```

Active reservation statuses (counted in `allocatedQuantity` / active reservations): `RESERVED`, `ALLOCATED`, `PICK_ASSIGNED`, `ISSUE_PENDING`.

Statuses that MUST NOT be counted as active reservations: `CANCELLED`, `RELEASED`, `EXPIRED`, `FULFILLED`.

`expectedReceiptsQuantity` is recorded for visibility but is NOT included in the v1 ATP calculation.

#### Behavioral Assertions (Issue #48 acceptance criteria)

Implementations for `GET /v1/inventory/availability/{productId}` MUST satisfy the following:

- When a valid `productId` exists but there is no stock across any locations, return `200 OK` with an empty array `[]` (empty locations list).
- When `productId` is not found, return `404 Not Found` with the standard error response format defined in this guide.
- When `productId` is malformed (invalid UUID) or missing, return `400 Bad Request` using the standard error response format.
- ATP for each location MUST equal `onHandQuantity - sum(active reservations)` where active reservations are those with statuses `RESERVED`, `ALLOCATED`, `PICK_ASSIGNED`, `ISSUE_PENDING`.
- The endpoint MUST return a per-location array even for single-location products; clients rely on the array shape.
- The initial API stub may return `501 Not Implemented` (OpenAPI contains `501` responses). That is acceptable during early development, but the contract requires implementation and tests (Issue #48) before the capability is marked `stable-for-ui`.

#### Related non-REST work (Issues #46 and #47)

- Issue #46 implements manufacturer feed ingestion and normalization into `NormalizedAvailability` and `UnmappedManufacturerParts` artifacts; this work feeds the availability calculation pipeline and MUST be referenced by the availability implementation for source-of-truth reconciliation.
- Issue #47 implements distributor feed normalization (DistributorSkuMap), lead-time normalization policy (versioned), and `shipFrom` region normalization (ISO 3166-2). These are pipeline/adapter stories and do not add REST endpoints, but are required for correct ATP/visibility.

#### Test requirements

- Provider contract tests (backend) MUST include tests that assert the behavioral assertions above (empty array, 404, 400, ATP formula). These tests are tracked in Issue #48.
- Tests should seed normalized availability data (from the ingestion pipelines) and assert per-location ATP values match the formula.


## Entity-Specific Contracts

### AdjustmentResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `adjustmentId` | integer (int64) | No |  |
| `approvedAt` | string (date-time) | No |  |
| `approvedByUserId` | string | No |  |
| `costAtTimeOfAdjustment` | number | No |  |
| `countedQuantity` | integer (int32) | No |  |
| `createdAt` | string (date-time) | No |  |
| `createdByUserId` | string | No |  |
| `errorMessage` | string | No |  |
| `ledgerEntryId` | integer (int64) | No |  |
| `postedAt` | string (date-time) | No |  |
| `quantityChange` | integer (int32) | No |  |
| `quantityOnHandBefore` | integer (int32) | No |  |
| `reasonCode` | string | No |  |
| `rejectedAt` | string (date-time) | No |  |
| `rejectedByUserId` | string | No |  |
| `rejectionReason` | string | No |  |
| `requiredApprovalTier` | string | No |  |
| `status` | string | No |  |
| `stockItemId` | string | No |  |
| `updatedAt` | string (date-time) | No |  |
| `variancePercentage` | number | No |  |
| `varianceValue` | number | No |  |


### ApproveAdjustmentRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `approverUserId` | string | Yes |  |
| `notes` | string | No |  |


### CountEntry

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `actualQuantity` | integer (int32) | No |  |
| `auditorId` | string | No |  |
| `countEntryId` | string (uuid) | No |  |
| `countedAt` | string (date-time) | No |  |
| `cycleCountTaskId` | string (uuid) | No |  |
| `expectedQuantity` | integer (int32) | No |  |
| `recount` | boolean | No |  |
| `recountOfCountEntryId` | string (uuid) | No |  |
| `recountSequenceNumber` | integer (int32) | No |  |
| `variance` | integer (int32) | No |  |


### CountResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `actualQuantity` | integer (int32) | No |  |
| `countEntryId` | string (uuid) | No |  |
| `countedAt` | string (date-time) | No |  |
| `expectedQuantity` | integer (int32) | No |  |
| `limitExceeded` | boolean | No |  |
| `message` | string | No |  |
| `recountSequenceNumber` | integer (int32) | No |  |
| `taskId` | string (uuid) | No |  |
| `taskStatus` | string | No |  |
| `variance` | integer (int32) | No |  |


### CreateAdjustmentRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `costAtTimeOfAdjustment` | number | Yes |  |
| `countedQuantity` | integer (int32) | Yes |  |
| `createdByUserId` | string | Yes |  |
| `quantityOnHandBefore` | integer (int32) | Yes |  |
| `reasonCode` | string | Yes |  |
| `stockItemId` | string | Yes |  |


### CycleCountTask

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `auditorId` | string | No |  |
| `binLocation` | string | No |  |
| `countEntriesCount` | integer (int32) | No |  |
| `createdAt` | string (date-time) | No |  |
| `expectedQuantity` | integer (int32) | No |  |
| `itemDescription` | string | No |  |
| `itemSku` | string | No |  |
| `latestCountEntryId` | string (uuid) | No |  |
| `status` | string | No |  |
| `taskId` | string (uuid) | No |  |
| `updatedAt` | string (date-time) | No |  |


### DeactivateLocationRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `destinationLocationId` | string (uuid) | No |  |


### DeactivateLocationResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `destinationLocationId` | string (uuid) | No |  |
| `sourceLocationId` | string (uuid) | No |  |
| `status` | string | No |  |
| `transfer` | string | No |  |


### InventoryAvailabilityResponse

Inventory availability response including on-hand, allocations, and ATP

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `allocatedQty` | number | Yes | Total quantity allocated (hard commitments) |
| `asOfTimestamp` | string (date-time) | Yes | Timestamp when this calculation was performed |
| `atpQty` | number | Yes | Available-to-promise quantity (On-Hand - Allocations) |
| `expectedReceiptsQty` | number | No | Expected receipts quantity (optional, not included in ATP for v1) |
| `locationId` | string (uuid) | Yes | Location identifier |
| `onHandQty` | number | Yes | Physical on-hand quantity (net sum of ledger events) |
| `productId` | string (uuid) | Yes | Product identifier |
| `uom` | string | Yes | Base unit of measure for all quantities |


### MovedItem

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `itemId` | string | No |  |
| `quantity` | number (double) | No |  |


*Additional schemas omitted for brevity. See OpenAPI spec for complete list.*

---

## Examples

### Example Request/Response Pairs

#### Example: Create Request

```http
POST /v1/inventory/pickingLists/{id}/confirm
Content-Type: application/json
X-Correlation-Id: abc-123-def-456

{
  "name": "Example",
  "description": "Example description",
  "status": "ACTIVE"
}
```

**Response:**

```http
HTTP/1.1 201 Created
X-Correlation-Id: abc-123-def-456

{
  "id": "new-id-123",
  "name": "Example",
  "description": "Example description",
  "status": "ACTIVE",
  "createdAt": "2026-01-27T14:30:00Z"
}
```

#### Example: Retrieve Request

```http
GET /v1/inventory/sites/{siteId}/defaultLocations
X-Correlation-Id: abc-123-def-456
```

**Response:**

```http
HTTP/1.1 200 OK
X-Correlation-Id: abc-123-def-456

{
  "id": "existing-id-456",
  "name": "Example",
  "status": "ACTIVE",
  "createdAt": "2026-01-27T14:00:00Z",
  "updatedAt": "2026-01-27T14:30:00Z"
}
```

---

## Summary

This guide establishes standardized contracts for the Inventory Management domain:

- **Field Naming**: camelCase for all JSON fields
- **Enum Values**: UPPER_SNAKE_CASE for all enums
- **Timestamps**: ISO 8601 UTC format
- **Identifiers**: String-based UUIDs
- **Pagination**: Zero-based with standard response format
- **Error Handling**: Consistent error response structure with correlation IDs

---

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-27 | Initial version generated from OpenAPI spec |

---

## References

- OpenAPI Specification: `pos-inventory/target/openapi.yaml`
- Domain Agent Guide: `domains/inventory/.business-rules/AGENT_GUIDE.md`
- Cross-Domain Integration: `domains/inventory/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- Error Codes: `domains/inventory/.business-rules/ERROR_CODES.md`
- Correlation ID Standards: `X-Correlation-Id-Implementation-Plan.md`

---

**Generated:** 2026-01-27 14:27:53 UTC  
**Tool:** `scripts/generate_backend_contract_guides.py`

---

## Capability Contract Template

Use the shared template for capability sections:

- `domains/BACKEND_CONTRACT_CAPABILITY_TEMPLATE.md`
