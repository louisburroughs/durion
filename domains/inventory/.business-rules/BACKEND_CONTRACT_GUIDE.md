# Inventory Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-01-25

---

## Overview

This guide standardizes field naming, data types, payload structures, error formats, and permission patterns for the Inventory domain REST API and backend services. It aligns with CRM and Accounting guides and codifies inventory-specific rules captured in the Inventory domain decisions.

Authoritative references:
- DECISION-INVENTORY-001 (Canonical Location Model)
- DECISION-INVENTORY-002 (Moqui Proxy Integration)
- DECISION-INVENTORY-003 (Plain JSON + Cursor Pagination)
- DECISION-INVENTORY-004 (Availability Contract)
- DECISION-INVENTORY-005 (Immutable Ledger)
- DECISION-INVENTORY-006 (Adjustments Scope)
- DECISION-INVENTORY-007 (StorageLocation CRUD)
- DECISION-INVENTORY-009 (Location Blocking Rules)
- DECISION-INVENTORY-010 (Permission Naming)
- DECISION-INVENTORY-012 (Correlation ID)
- DECISION-INVENTORY-016 (Allocation/Reservation Semantics)

Integration Principle: UI calls Moqui proxy endpoints/services; UI does not call inventory backends directly (DECISION-INVENTORY-002).

---

## Table of Contents

1. JSON Field Naming Conventions
2. Data Types & Formats
3. Identifier Naming
4. Timestamp Conventions
5. Quantities & Precision
6. Collection & Pagination
7. Error Response Format
8. Permissions & Scope
9. Optimistic Locking
10. Entity-Specific Contracts (Inventory)
11. Examples
12. Governance & Domain Boundaries

---

## JSON Field Naming Conventions

- Pattern: camelCase for all JSON field names.
- Consistency with CRM/Accounting guides and Jackson defaults.

```json
{
  "productId": "P-123456",
  "locationId": "LOC-001",
  "storageLocationId": "BIN-A-01",
  "onHandQty": 12.0,
  "allocatedQty": 3.0,
  "createdAt": "2026-01-25T15:00:00Z"
}
```

---

## Data Types & Formats

- String: identifiers, codes, names, free-form descriptions.
- Integer/Long: counts, page sizes, numeric sequence values (backend internal only).
- UUID/ID: Represent IDs as string in REST (UUIDs or encoded IDs) even if backed by numeric PKs.
- Instant: ISO-8601 UTC (e.g., "2026-01-25T15:00:00Z").
- LocalDate: date-only fields for effective dating and scheduled operations.

---

## Identifier Naming

Canonical identifiers used in Inventory contracts:
- `productId`: Product identifier (string, UUID or encoded). Inventory consumes Product; Product/Catalog owns creation.
- `workorderId`: Workorder identifier (string, UUID or encoded). Always spelled as one word: workorder.
- `workorderLineId`: Line-level identifier on a workorder (string).
- `locationId`: Business site/location identifier (string). Canonical per DECISION-INVENTORY-001.
- `storageLocationId`: Bin-level identifier (string). Canonical per DECISION-INVENTORY-001.
- `pickedItemId`: Identifier for picked line/record when applicable.
- `taskId`: Identifier for cycle count tasks.

---

## Timestamp Conventions

- `createdAt`, `modifiedAt`, `postedAt`, `approvedAt` use ISO-8601 UTC.
- Use `Instant` on the backend; always serialize to UTC (`...Z`).

---

## Quantities & Precision

- Quantities are decimals (JSON number) with sufficient precision for unit conversions.
- Follow DECISION-INVENTORY-015: preserve precision; avoid integer-only assumptions.
- Validation examples: `@Positive` for quantities; domain-specific rounding rules in Product/UOM conversions.

---

## Collection & Pagination

- Cursor-based pagination only (DECISION-INVENTORY-003).
- Request: `pageToken` (optional).
- Response: `items[]`, `nextPageToken` (nullable).

```json
{
  "items": [ /* ... */ ],
  "nextPageToken": "abc123"
}
```

---

## Error Response Format

Deterministic error schema with correlation ID (DECISION-INVENTORY-003, -012).

```json
{
  "code": "INV_VALIDATION_ERROR",
  "message": "Return quantity exceeds maximum",
  "correlationId": "c4e037ff-...",
  "details": {
    "reason": "MAX_RETURNABLE_EXCEEDED",
    "maxReturnableQty": 2.0
  },
  "fieldErrors": [
    { "field": "lines[0].returnQty", "message": "Must be <= 2.0" }
  ]
}
```

- Status codes: 200/201 (success), 400 (bad request), 401 (unauthorized), 403 (forbidden), 404 (not found), 409 (conflict), 422 (validation), 500 (server).
- Always include `correlationId` in error responses.

---

## Permissions & Scope

Naming follows DECISION-INVENTORY-010 and RBAC framework:
- Pattern: `inventory:<resource>:<action>`
- Location-scoped checks at Moqui boundary and/or backend service.

Recommended inventory permissions:
- `inventory:availability:query`
- `inventory:cyclecount:plan`
- `inventory:cyclecount:submit`
- `inventory:cyclecount:approve`
- `inventory:adjustment:create`
- `inventory:adjustment:approve`
- `inventory:adjustment:override`
- `inventory:picking:list`
- `inventory:picking:execute`
- `inventory:consume:execute`
- `inventory:return:execute`
- `inventory:receiving:create`
- `inventory:receiving:execute`
- `inventory:location:view`
- `inventory:location:manage`

---

## Optimistic Locking

- Where applicable (e.g., plan/adjustment approvals), include version or ETag semantics:
  - Header: `If-Match: <etag>` or body field `version`.
  - On conflict: `409 Conflict` or `412 Precondition Failed` with error details.
- Use only when entities are mutable; immutable ledger entries do not use updates (DECISION-INVENTORY-005).

---

## Entity-Specific Contracts (Inventory)

### 1. Inventory Availability

- Path (Moqui proxy): `/rest/api/v1/inventory/availability/{productId}`
- Method(s): `GET` (query), `POST` (batch query)
- Query params: `locationId` (required), `storageLocationId` (optional), `includeAllocations` (optional)
- Response (per DTO):
  ```json
  {
    "productId": "P-123",
    "locationId": "LOC-001",
    "onHandQty": 12.0,
    "allocatedQty": 3.0,
    "availableToPromise": 9.0
  }
  ```
- Notes: ATP = On-Hand - Allocations (DECISION-INVENTORY-004, -005).

### 2. Cycle Counts

- Paths:
  - Create Plan: `/rest/api/v1/inventory/cycleCount/plans` (POST)
  - List Plans: `/rest/api/v1/inventory/cycleCount/plans` (GET)
  - Get Plan: `/rest/api/v1/inventory/cycleCount/plans/{planId}` (GET)
  - Tasks: `/rest/api/v1/inventory/cycleCount/tasks` (GET)
  - Submit Counts: `/rest/api/v1/inventory/cycleCount/submit` (POST)
- DTOs: `CycleCountTask`, `CountEntry`, `TaskStatus` (e.g., PENDING, IN_PROGRESS, COMPLETED, APPROVED, REJECTED)
- Validation: Blind count flows hide `expectedQuantity` until submission.

### 3. Picking Lists (WorkExecution Coordination)

- Path: `/rest/api/v1/inventory/pickingLists/{id}/confirm` (POST)
- Ownership: WorkExecution (workorder) owns pick task lifecycle; Inventory validates availability & ledger updates.
- Request: `{ lines: [{ workorderLineId, productId, qty }] }`
- Response: `{ status: "COMPLETED", completedAt: "..." }`

### 4. Issue/Consume Picked Items

- Path: `/rest/api/v1/inventory/consume` (POST)
- Request: `{ workorderId, lines: [{ pickedItemId, workorderLineId, productId, qty }] }`
- Response: `{ transactionId, postedAt }`
- Notes: Updates immutable ledger; deep-link to workorder refs (DECISION-INVENTORY-014).

### 5. Return Unused Items to Stock

- Path: `/rest/api/v1/inventory/returns` (POST)
- Request: `{ workorderId, destination: { locationId, storageLocationId? }, lines: [{ pickedItemId, productId, returnQty, reasonCode }] }`
- Validation:
  - `maxReturnableQty` enforced by backend.
  - Block INACTIVE/PENDING destinations (DECISION-INVENTORY-009).
- Response: `{ returnId, processedAt }`

### 6. Adjustments (Append-Only)

- Path: `/rest/api/v1/inventory/adjustments` (POST)
- Request: `{ locationId, storageLocationId?, lines: [{ productId, qty, reasonCode }] }`
- Approvals: `/rest/api/v1/inventory/adjustments/{id}/approve` (POST)
- Notes: No balance transfers; add/remove only (DECISION-INVENTORY-006).

### 7. Storage Locations (CRUD with Restrictions)

- Paths:
  - List: `/rest/api/v1/inventory/locations/{locationId}/storageLocations` (GET)
  - Create: `/rest/api/v1/inventory/locations/{locationId}/storageLocations` (POST)
  - Update: `/rest/api/v1/inventory/storageLocations/{storageLocationId}` (PUT)
- Rules: No reparenting without validation; updates constrained per DECISION-INVENTORY-007.

### 8. Allocation/Reservation

- Paths:
  - Create reservation: `/rest/api/v1/inventory/reservations` (POST)
  - List reservations: `/rest/api/v1/inventory/reservations` (GET)
- Semantics: Reservation distinct from picks; reserve before pick (DECISION-INVENTORY-016).

---

## Examples

### Availability (GET)
```json
{
  "productId": "P-123",
  "locationId": "LOC-001",
  "onHandQty": 12.0,
  "allocatedQty": 3.0,
  "availableToPromise": 9.0
}
```

### Validation Error (422)
```json
{
  "code": "INV_VALIDATION_ERROR",
  "message": "Quantity must be positive",
  "correlationId": "c4e037ff-...",
  "fieldErrors": [
    { "field": "lines[0].qty", "message": "> 0 required" }
  ]
}
```

---

## Governance & Domain Boundaries

- Product/Catalog owns product master data (create, identifiers, UOM conversions, lifecycle). Inventory consumes product data.
- WorkExecution (workorder) owns picking task lifecycle; Inventory validates and records ledger movements.
- Pricing owns supplier/vendor cost tiers. Inventory consumes pricing/tiers where relevant.
- Enforce naming conventions: always use `workorder` as one word in all contexts.
- All frontend requests go via Moqui proxy; backend errors and permission checks are surfaced consistently.

---

## Integration & Security

- Moqui Proxy: `/rest/api/v1/...` endpoints; forward to backend services with session/auth.
- Permission Checks: Per-operation checks at proxy and/or backend using RBAC (location-scoped).
- Correlation ID: Propagate `X-Correlation-Id` from UI → Moqui → backend; always include in error responses.

---

## Change Management

- Versioning: Use URL version segment (`/v1/`); add new fields compatibly; avoid breaking changes.
- Deprecation: Mark deprecated fields; provide migration notes.
- Backward compatibility: Preserve existing contracts; introduce new endpoints for incompatible changes.
