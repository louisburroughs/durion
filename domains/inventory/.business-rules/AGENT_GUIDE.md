# AGENT_GUIDE.md — Inventory Control Domain (Normative)

> **Status:** Normative. This document is safe for direct agent input, CI validation, and story execution rules.
>
> **Scope:** Inventory-control UI flows and their backend contracts for Durion Moqui/Vue/Quasar POS.
>
> **Non-normative companion:** `INVENTORY_CONTROL_DOMAIN_NOTES.md` (rationale, tradeoffs, auditor explanations; forbidden for direct agent execution).

---

## 0. Decision Index (Authoritative)

Each Decision ID corresponds 1:1 to a section in `INVENTORY_CONTROL_DOMAIN_NOTES.md`.

| Decision ID | Title |
|---|---|
| INV-DEC-001 | Canonical Location Model: `LocationRef` as Site; `StorageLocation` as Bin |
| INV-DEC-002 | Integration Pattern: Moqui Service Proxy (Frontend does not call inventory backend directly) |
| INV-DEC-003 | API Envelopes & Error Schema (plain JSON + deterministic error codes) |
| INV-DEC-004 | Availability Contract (inputs, outputs, deep-linking, and “success with zeros”) |
| INV-DEC-005 | Ledger Contract (filters, location semantics, pagination, and immutability) |
| INV-DEC-006 | Adjustment Workflow Scope (create-only in v1; no approve/post UI) |
| INV-DEC-007 | StorageLocation CRUD + Deactivation (destination-required semantics; editability rules) |
| INV-DEC-008 | HR Sync Contracts: LocationRef + SyncLog + Manual Sync Trigger |
| INV-DEC-009 | Inactive/Pending Location Selection Rules (movement blocking) |
| INV-DEC-010 | Permission Naming Convention (canonical permission strings) |
| INV-DEC-011 | Sensitive Data & Logging Policy (quantities/payloads) |
| INV-DEC-012 | Correlation/Request ID Convention (headers + UI behavior) |
| INV-DEC-013 | Availability Feed Ops Ownership & Contracts (runs, normalized, unmapped, exceptions) |
| INV-DEC-014 | Deep-Link Parameter Names & Routing Conventions (canonical) |
| INV-DEC-015 | JSON Field Handling (tags/capacity/temperature/payload viewer safety) |

---

## 1. Domain Overview

The Inventory Control domain represents and enforces the state of physical stock and the topology where that stock exists, plus auditability of stock movements.

Inventory Control **does not** own:

- Product master data (SKU display name, base UOM definition, descriptions) — Product domain
- Work order lifecycle and scheduling — Work Execution domain
- Pricing, quoting, customer master — Pricing/CRM domains

Inventory Control **does** provide:

- On-hand, Allocated, ATP availability views
- Append-only inventory ledger of physical movements
- Storage/bin hierarchy management within sites
- HR-synced business location references (site/shop/mobile units)
- Operational controls: block new movements into inactive/pending business locations
- Ops visibility for manufacturer availability feed ingestion artifacts (see INV-DEC-013)

---

## 2. Domain Boundaries & Responsibilities (Normative)

### Inventory Control owns (authoritative for)

- `InventoryLedgerEntry` (append-only)
- `AvailabilityView` (computed; server authoritative)
- `StorageLocation` (inventory-managed bin hierarchy)
- `LocationRef` (HR-synced site reference; inventory stores a read model but HR is upstream truth)
- `SyncLog` (immutable audit log of HR sync ingestion)
- Availability feed ops artifacts (see INV-DEC-013)

### Inventory Control integrates with (not authoritative for)

- Product: SKU ↔ productId mapping, base UOM, display attributes
- Work Execution: generates ledger source transactions (pick/consume/issue, returns)
- Allocation/Reservation service: allocated quantities used in ATP (treated as external dependency unless confirmed otherwise)
- Integration platform: may produce upstream feed events, but inventory ops artifacts are exposed here (INV-DEC-013)

---

## 3. Canonical Location Model (INV-DEC-001)

### 3.1 Canonical entities

**LocationRef** = business site/shop/mobile location (HR-synced)

- Canonical key: `locationId` (UUIDv7) == `LocationRef.id`
- Fields (minimum): `locationId`, `tenantId`, `hrLocationId`, `name`, `status`, `timezone`, `tags`, timestamps

**StorageLocation** = bin/location inside a business site (inventory-managed)

- Canonical key: `storageLocationId` (UUIDv7)
- Always belongs to a `locationId` (site): `storageLocation.locationId`

### 3.2 Naming rule (resolved)

- `siteId` in older story text is treated as **synonym** for `locationId`.
- UI and APIs use **only**: `locationId` and `storageLocationId`.

### 3.3 UI rules

- Business location selector uses `LocationRef` (`locationId`)
- Bin selector uses `StorageLocation` (`storageLocationId`)
- Enforce in UI when possible: selected `storageLocationId` must belong to selected `locationId`

---

## 4. Business Rules & Invariants (Frontend-relevant, Normative)

### 4.1 Ledger immutability

- Ledger entries are append-only. UI never offers edit/delete.
- Corrections occur via new ledger entries (adjustments).

### 4.2 On-hand vs allocations vs ATP

- On-hand: net sum of physical movement ledger entries
- Allocations: excluded from on-hand
- ATP v1: `OnHand - Allocations`
Frontend must not compute these; backend is authoritative.

### 4.3 Availability “no history” is success

- Successful response with zeros is valid; UI shows “No inventory history for this scope”.

### 4.4 Location aggregation

- Query by `locationId` aggregates across storage locations within that location
- Providing `storageLocationId` scopes to that bin only

### 4.5 HR LocationRef lifecycle constraints (INV-DEC-009)

- `INACTIVE` and `PENDING` LocationRef cannot be selected for **new movement** flows
- Historical records may display inactive/pending locations read-only

---

## 5. Integration Pattern (INV-DEC-002)

Frontend uses **Moqui service proxy**:

- Vue/Quasar screens call Moqui services (or Moqui REST endpoints)
- Moqui services proxy to inventory backend(s), centralizing auth, session, correlation headers, error mapping

Frontend **does not** call inventory backend directly unless an explicit exception is documented per story.

---

## 6. API Contracts & Patterns (Normative)

### 6.1 Envelope and error schema (INV-DEC-003)

Responses are plain JSON (no `{data: ...}` envelope).

Error payload (minimum):

```json
{
  "errorCode": "INV_VALIDATION_FAILED",
  "message": "Validation failed",
  "fieldErrors": { "locationId": "REQUIRED" },
  "reasonCode": "INACTIVE_LOCATION",
  "correlationId": "optional-string"
}
```

### 6.2 Pagination contract (cursor-based)

List endpoints return:

- `items[]`
- `nextPageToken` (null/absent when done)
- `pageSize`
- `sort` (optional echo)

Requests accept:

- `pageSize` (default 25; max 200)
- `pageToken` (optional)
- `sort` (optional; e.g., `-timestamp`)

## 7. Availability Contract (INV-DEC-004)

### 7.1 Endpoint (via Moqui proxy)

`GET /inventory/availability`

Query params:

- `productSku` (required, trimmed)
- `locationId` (required)
- `storageLocationId` (optional)

Response:

```json
{
  "productSku": "string",
  "locationId": "uuidv7",
  "storageLocationId": "uuidv7-or-null",
  "onHandQuantity": "decimal-string",
  "allocatedQuantity": "decimal-string",
  "availableToPromiseQuantity": "decimal-string",
  "unitOfMeasure": "string"
}
```

### 7.2 Deep-linking (official) (INV-DEC-014)

productSku, locationId, storageLocationId (optional)

Auto-run:

Runs once on initial load when required params present

Marks results stale if user edits inputs after success; does not auto-refresh unless user submits again### 7.3 Pickers requirement (resolved)
Frontend must provide selectors for:

locationId (LocationRef picker)

storageLocationId (StorageLocation picker filtered by location)
No free-text UUID entry in v1 UI (except internal admin/debug screen, if explicitly approved).### 7.4 Logging policy for quantities (INV-DEC-011)
Do not log returned quantities. Only log:

productSku (hashed if tenant policy requires)

locationId, presence of storageLocationId (boolean)

status/timing/correlationId

## 8. Ledger Contract (INV-DEC-005)

### 8.1 Endpoints (via Moqui proxy)

GET /inventory/ledger
Filters:

productSku (optional)

productId (optional; prefer when available)

locationId (optional)

movementType (optional)

fromUtc, toUtc (optional but recommended for large tenants)

sourceTransactionId (optional)

workOrderId (optional)

workOrderLineId (optional)
Pagination: pageSize, pageToken, sort

GET /inventory/ledger/{ledgerEntryId}### 8.2 Location filter semantics (resolved)
If locationId filter is provided, it matches:

fromLocationId == locationId OR toLocationId == locationId

### 8.3 WorkExec integration key (resolved)

Ledger entries support these linkage fields (when applicable):

sourceTransactionId (always present for workexec-originated movements)

workOrderId (present when source is a work order)

workOrderLineId (present when movement is tied to a specific line)

WorkExec “View Movement History” filter uses:

workOrderLineId primarily

fallback: sourceTransactionId when line id absent

## 9. Adjustments Scope (INV-DEC-006)

Adjustment UI scope in v1:

Create Adjustment Request only (single-step post)

No approve/post state machine UI

Backend posts the ledger entry immediately upon successful create

Endpoint:

POST /inventory/adjustments
Request (minimum):

```json
{
  "locationId": "uuidv7",
  "storageLocationId": "uuidv7-or-null",
  "productSku": "string",
  "quantityChange": "decimal-string",
  "reasonCode": "string",
  "reasonNotes": "string-or-null"
}
```

Rules:

- `quantityChange` must be non-zero
- `reasonCode` required
- `locationId` must be ACTIVE (not INACTIVE/PENDING)

## 10. StorageLocation Contracts (INV-DEC-007)

### 10.1 Endpoints

GET /inventory/storage-locations?locationId=... (list bins within a location)

GET /inventory/storage-locations/{storageLocationId}

POST /inventory/storage-locations

PUT /inventory/storage-locations/{storageLocationId}

POST /inventory/storage-locations/{storageLocationId}/deactivate

### 10.2 Field schema decisions (resolved)

capacity and temperature are treated as freeform JSON objects (stored and displayed as JSON).

UI provides a JSON editor/viewer with validation for valid JSON only; no unit enforcement in v1.

### 10.3 Editability decisions (resolved)

Inactive StorageLocations:

editable for non-operational metadata only (name, barcode, tags, notes)

cannot be reactivated in v1 unless explicitly implemented by backend story

storageType is immutable after creation (create-only).

### 10.4 Parent selection rules (resolved)

Parent picker defaults to Active-only.

UI may provide “Include inactive parents” toggle for historical organization, but operational flows must still exclude inactive bins.

### 10.5 Deactivation rules

If backend indicates destination required (non-empty bin):

UI requires destinationStorageLocationId

destination must be Active, same locationId, and not the source bin

Backend errors:

422 DESTINATION_REQUIRED

422 INVALID_DESTINATION

409 CONFLICT (optimistic locking if implemented)

## 11. HR-synced LocationRef & SyncLog (INV-DEC-008, INV-DEC-009)

### 11.1 Endpoints

GET /inventory/locations (LocationRef list)

GET /inventory/locations/{locationId} (detail)

GET /inventory/sync-logs (SyncLog list)

GET /inventory/sync-logs/{syncLogId} (detail)

Optional trigger:

POST /inventory/locations/sync → { "syncRunId": "uuidv7" }

### 11.2 Permissions gating (INV-DEC-010)

LocationRef list/detail: inventory:topology:view

SyncLog list/detail (metadata): inventory:topology:view

SyncLog payload “View payload”: inventory:topology:payload:view

Manual “Sync now”: inventory:topology:sync:trigger

### 11.3 Manual sync trigger (resolved)

Supported; admin-only by permission above

Async acknowledgement only; UI polls SyncLog list by syncRunId if supported, otherwise refresh

### 11.4 PENDING behavior (resolved)

Treat PENDING the same as INACTIVE for movement selection:

blocked for new inbound/outbound/adjustment/transfer movements

visible read-only for historical records

### 11.5 “New stock movement” screens (authoritative list for this repo) (INV-DEC-009)

The following UI flows must block selecting INACTIVE/PENDING locationId and must handle backend 422:

Goods Receipt (receiving into inventory)

Inventory Transfer (between locations/bins)

Inventory Adjustment (manual correction)

WorkExec Issue/Consume parts (posting physical issue to a location/bin)

Return-to-stock (posting physical return into a location/bin)
If the repo has additional movement screens later, they must adopt the same guardrail.

## 12. Availability Feed Ops (Manufacturer Feeds) (INV-DEC-013)

### 12.2 Endpoints

Ingestion runs:

GET /inventory/availability-feeds/runs

GET /inventory/availability-feeds/runs/{runId}

Normalized availability:

GET /inventory/availability-feeds/normalized

Unmapped parts:

GET /inventory/availability-feeds/unmapped

PATCH /inventory/availability-feeds/unmapped/{unmappedId} (status update allowed; see below)

Exception queue:

GET /inventory/availability-feeds/exceptions

GET /inventory/availability-feeds/exceptions/{exceptionId}

PATCH /inventory/availability-feeds/exceptions/{exceptionId} (status update + operator notes)

### 12.3 Permissions (INV-DEC-010)

View feed ops: inventory:feedops:view

Update unmapped status: inventory:feedops:unmapped:update

Update exception status/notes: inventory:feedops:exception:update

### 12.4 ExceptionQueue scope (resolved)

Inventory feed exception queue is inventory-scoped.

Required fields: severity, status, message, details (redacted-safe), correlationId, timestamps.

Status enum: OPEN, ACKNOWLEDGED, RESOLVED.

### 12.5 Unmapped status update (resolved)

Allowed in v1:

PENDING_REVIEW, IGNORED, RESOLVED
Status updates require optimistic locking if etag/version is provided.

### 12.6 Retention/TTL UX (resolved)

Runs and exceptions are retained for 90 days minimum.

UI requires date range filters for runs and exceptions; tolerate missing older records gracefully.

## 13. Permission Naming Convention (INV-DEC-010)

Canonical permission strings are colon-separated, stable, and action-scoped.

Minimum set:

Availability:

view: inventory:availability:view

Ledger:

view: inventory:ledger:view

Adjustments:

create: inventory:adjustment:create

Storage locations:

view: inventory:storage:view

manage: inventory:storage:manage

deactivate: inventory:storage:deactivate

Topology:

view: inventory:topology:view

payload view: inventory:topology:payload:view

sync trigger: inventory:topology:sync:trigger

Feed ops:

view: inventory:feedops:view

update unmapped: inventory:feedops:unmapped:update

update exception: inventory:feedops:exception:update

UI hides/disables actions when permissions absent; backend remains authoritative.

## 14. Correlation / 401 vs 403 Handling (INV-DEC-012)

### 14.1 Headers

Request header: X-Correlation-Id (generated by frontend per request if not present)

Response header: X-Correlation-Id (echoed by backend/Moqui proxy)

### 14.2 UI behavior

401: redirect to login/session refresh per app convention; do not show cached sensitive content

403: show forbidden state; do not leak data; keep filters but clear results

Error UI shows correlation id under collapsed “Technical details”.

## 15. Sensitive Data & Logging (INV-DEC-011, INV-DEC-015)

### 15.1 Do not log

Returned availability quantities

SyncLog full payloads

ExceptionQueue full details blobs (only safe summary fields)

Tags/capacity/temperature JSON beyond minimal size (avoid in logs)

### 15.2 Safe JSON rendering

All JSON/payload fields are rendered with:

escaped text (no HTML)

truncation in list views

explicit expand/copy affordances

no persistence to localStorage

Fields covered:

tags, capacity, temperature

SyncLog payload

Ingestion payloads / exception details (redacted-safe)

## 16. Open Questions — Resolved (Full Q/A, with provenance)

Questions are restated from the prior Inventory AGENT_GUIDE “Open Questions from Frontend Stories” section and the prior Inventory STORY_VALIDATION_CHECKLIST “Open Questions to Resolve” section. Each has a clear Response and is now reconciled into this guide.

Source: AGENT_GUIDE.md — 12.1 Availability (On-hand / ATP)
AGENT_GUIDE

Question 1: What is the exact backend endpoint path and auth mechanism the Moqui frontend should call for availability (e.g., /v1/inventory/availability vs something else), and what is the expected request/response envelope (plain JSON vs wrapped {data: ...})?
Response: Use Moqui proxy endpoint GET /inventory/availability with plain JSON response (no {data:...} envelope). Auth uses existing POS session/cookie/headers via Moqui; Vue does not call backend directly. (INV-DEC-002, INV-DEC-003, INV-DEC-004)

Question 2: Does the frontend already have existing pickers for locationId and storageLocationId (and a known service to load them)? If not, should this story: (a) use free-text UUID entry, or (b) include building minimal location/storage lookup UI (expanded scope)?
Response: Inventory screens must use pickers (LocationRef picker and StorageLocation picker filtered by location). Free-text UUID entry is not allowed in v1 user-facing flows. (INV-DEC-004, INV-DEC-001)

Question 3: Should the Availability screen support deep-linking via URL query params officially (bookmarked by advisors), and if so what are the canonical parameter names and routing conventions in this repo?
Response: Yes, supported. Canonical params: productSku, locationId, storageLocationId (optional). Auto-run once per load. (INV-DEC-014, INV-DEC-004)

Question 4: Are availability quantities considered sensitive such that frontend logs must avoid logging numeric quantities (only status/timing), or is it acceptable to log them for debugging?
Response: Treat as sensitive-by-default; do not log quantities. Log identifiers/timing only. (INV-DEC-011)

Source: AGENT_GUIDE.md — 12.2 Ledger (Stock movements)
AGENT_GUIDE

Question 5: Backend contract for ledger queries: What are the exact Moqui service names or REST endpoints, including query parameter names and pagination response shape (items/total, page tokens, etc.)?
Response: Use GET /inventory/ledger (cursor pagination with nextPageToken, request pageToken + pageSize, optional sort). Detail: GET /inventory/ledger/{ledgerEntryId}. (INV-DEC-005, INV-DEC-003)

Question 6: Adjustment UI scope: Should the frontend implement full Adjustment Request screens in this repo, or only provide links to an existing adjustments area? If full, what is the adjustment request entity name/fields and state machine (PENDING/APPROVED/POSTED)?
Response: Implement create-only adjustment in v1 (POST /inventory/adjustments), backend posts immediately; no approve/post UI and no multi-state workflow in v1. (INV-DEC-006)

Question 7: Location filtering semantics: For the ledger list filter by location, should it match fromLocationId OR toLocationId (recommended), or only a specific side depending on movement type?
Response: Match fromLocationId OR toLocationId. (INV-DEC-005)

Question 8: Workexec integration key: For “Workexec can query movement history for a workorder line”, what identifier is available in ledger entries—sourceTransactionId only, or a dedicated workorderId/workorderLineId field?
Response: Ledger entries include sourceTransactionId and, when applicable, workOrderId and workOrderLineId. WorkExec uses workOrderLineId primarily. (INV-DEC-005)

Source: AGENT_GUIDE.md — 12.3 Storage Locations (Topology)
AGENT_GUIDE

Question 9: What are the exact Moqui service names / REST endpoints and response schemas for: list sites, list storage types, list locations by site, get location detail, create, update, deactivate?
Response: Use:

GET /inventory/locations (sites / LocationRefs)

GET /inventory/storage-locations?locationId=...

GET /inventory/storage-locations/{storageLocationId}

POST /inventory/storage-locations

PUT /inventory/storage-locations/{storageLocationId}

POST /inventory/storage-locations/{storageLocationId}/deactivate
Storage types come from GET /inventory/meta/storage-types. (INV-DEC-007, INV-DEC-001)

Question 10: Are capacity and temperature structured JSON inputs expected to follow a defined schema (keys/units), or should the frontend treat them as freeform JSON text?
Response: Treat as freeform JSON objects; validate JSON syntax only. (INV-DEC-015, INV-DEC-007)

Question 11: Can an Inactive storage location be edited (e.g., name/barcode fixes), or must it be fully read-only?
Response: Editable for non-operational metadata only; not reactivatable in v1 unless explicitly supported by backend. (INV-DEC-007)

Question 12: Is changing storageType allowed after creation, or is it immutable?
Response: Immutable after creation. (INV-DEC-007)

Question 13: Should parent selection exclude Inactive locations strictly, or allow choosing an inactive parent for historical organization (while still preventing operational use)?
Response: Exclude inactive by default; allow optional “include inactive” toggle for historical organization only. (INV-DEC-007)

Source: AGENT_GUIDE.md — 12.4 HR-synced Locations (Topology)
AGENT_GUIDE

Question 14: Backend service contracts: What are the exact Moqui service names (or REST endpoints) and request/response schemas for LocationRef and SyncLog list/detail?
Response: Use:

GET /inventory/locations, GET /inventory/locations/{locationId}

GET /inventory/sync-logs, GET /inventory/sync-logs/{syncLogId} (INV-DEC-008)

Question 15: Permissions: What permission(s) gate access to Topology screens and SyncLog payload visibility?
Response: inventory:topology:view and inventory:topology:payload:view for payload. (INV-DEC-010)

Question 16: Manual sync trigger: Does the backend support an on-demand “Sync now” / reconcile action? If yes, what are the inputs/outputs, and should it be restricted to admins only?
Response: Yes. POST /inventory/locations/sync returns {syncRunId}; restricted by inventory:topology:sync:trigger. (INV-DEC-008, INV-DEC-010)

Question 17: Definition of “stock movements” in the current frontend: Which specific screens/components represent “new inbound stock movements” that must block inactive locations? Provide routes/screen names to update.
Response: Authoritative movement flows listed in §11.5; all must block INACTIVE/PENDING locations and handle backend 422. (INV-DEC-009)

Question 18: PENDING status behavior: If LocationRef.status=PENDING exists in v1, should PENDING locations be selectable for new movements, or treated as inactive until ACTIVE?
Response: Treat as inactive until ACTIVE. (INV-DEC-009)

Question 19: Tags display expectations: Should tags be treated as opaque JSON, or are there known keys that need friendly rendering/filtering?
Response: Treat as opaque JSON. Provide generic JSON viewer + optional tag chips if tags is an array of strings; do not assume keys. (INV-DEC-015)

Source: AGENT_GUIDE.md — 12.5 Availability feed normalization / ops
AGENT_GUIDE

Question 20: Backend API contracts (blocking): What are the exact endpoints (paths), request params, and response schemas for runs/normalized/unmapped/exceptions?
Response: Endpoints are defined in §12.2. Lists use cursor paging; filters include manufacturer, status, date range (runs/exceptions). (INV-DEC-013, INV-DEC-003)

Question 21: Authorization (blocking): What permission(s)/role(s) gate access to these screens?
Response: inventory:feedops:view, inventory:feedops:unmapped:update, inventory:feedops:exception:update. (INV-DEC-010)

Question 22: ExceptionQueue scope (blocking): inventory-specific or shared? What fields are guaranteed?
Response: Inventory-scoped feed exception queue; required fields are defined in §12.4. (INV-DEC-013)

Question 23: Unmapped status update (scope decision): allow changing UnmappedManufacturerParts.status?
Response: Yes, allowed in v1 with enum values in §12.5. (INV-DEC-013)

Question 24: Retention/TTL UX: Will runs/availability be retained, and should UI support selecting archived ranges?
Response: Retain minimum 90 days; UI supports date range filters and tolerates older missing records. (INV-DEC-013)

Source: AGENT_GUIDE.md — 12.6 Cross-cutting (Moqui integration + routing)
AGENT_GUIDE

Question 25: Moqui integration pattern (blocking): call backend directly from Vue client, or via Moqui proxy services?
Response: Use Moqui proxy services. (INV-DEC-002)

Question 26: Auth/correlation (blocking): existing convention for correlation IDs (e.g., X-Correlation-Id) and for handling 401 vs 403 in UI?
Response: Use X-Correlation-Id and behaviors in §14.2. (INV-DEC-012)

Question 27: SLA requirement detail: is there a frontend SLA expectation to monitor/enforce?
Response: Frontend UX SLA: show loading indicator within 100ms; surface timeout at 8 seconds with retry; no auto-refresh loops. Backend SLA remains p95 < 200ms service boundary when warm. (INV-DEC-004)

# End of AGENT_GUIDE.md
