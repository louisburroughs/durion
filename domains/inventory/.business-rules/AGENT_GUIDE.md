```markdown
# AGENT_GUIDE.md — Inventory Domain (POS / Moqui Frontend)

> **Status:** Living document. Updated to reflect new frontend stories in `durion-moqui-frontend` for the **inventory** domain.
>
> **Scope:** This guide is for engineers implementing or integrating Inventory-domain UI flows in the Moqui/Vue/Quasar POS frontend, and for aligning with the Inventory backend service(s).

---

## 1. Domain Overview

The **Inventory** domain is responsible for representing and enforcing the state of **physical stock** and the **operational topology** (sites/locations/bins) where stock exists, plus the **auditability** of stock movements.

Inventory is **not** the owner of:
- Product master data (SKU, base UOM, descriptions) — typically **Product** domain.
- Work order lifecycle and scheduling — typically **Work Execution** domain.
- Pricing, quoting, and customer data — typically **Pricing/CRM** domains.

Inventory **does** provide:
- Availability (On-hand, Allocated, ATP) at a location/bin scope.
- Append-only ledger of stock movements.
- Location topology management:
  - HR-synced “real-world” locations (shops/mobile sites) used for scoping and validation.
  - Inventory-managed storage/bin hierarchy used for operational workflows.
- Operational controls:
  - Blocking new stock movements to inactive locations.
  - Adjustment workflows (if implemented in this frontend).
- **Integration operations visibility** for availability feeds (ingestion runs, normalized availability, unmapped parts, exception queue) when the inventory service is the system of record for those artifacts. **CLARIFY** exact ownership vs “integration” domain.

---

## 2. Domain Boundaries & Responsibilities

### Inventory owns (or is authoritative for)
- **InventoryLedgerEntry**: immutable record of physical stock movements.
- **AvailabilityView**: computed view (on-hand, allocated, ATP) for a SKU at a location/bin scope.
- **StorageLocation**: user-managed storage/bin hierarchy within a site (barcode, type, parent/child).
- **LocationRef**: HR-synced “site/shop/mobile location” reference used for scoping and validation.
- **SyncLog**: immutable audit log of HR sync ingestion and outcomes.
- **Availability Feed Ops artifacts** (if implemented in inventory backend):
  - `IngestionRun` (or equivalent)
  - `ExternalAvailability` / “NormalizedAvailability”
  - `UnmappedManufacturerParts`
  - `ExceptionQueue` entries related to availability ingestion

### Inventory integrates with (but does not own)
- **Product**: SKU ↔ productId mapping, base UOM, display name, manufacturer part mapping tools.
- **Allocations/Reservations**: allocated quantities used in ATP (may be inventory-owned or separate service; treat as external dependency unless confirmed).
- **Work Execution**: source transactions for ledger entries (workorderId/workorderLineId, pick/consume/issue flows).
- **HR Topology**: upstream source for LocationRef roster and lifecycle.
- **Integration platform** (if separate): may own exception queue, run orchestration, retention/TTL policies.

### Practical boundary guidance for frontend
- If a screen is primarily about **stock quantities, movements, or locations**, it belongs under Inventory navigation.
- If a screen is primarily about **product attributes** (fitment hints, UOM conversions, manufacturer part mapping), it likely belongs to Product domain even if inventory consumes it.
  - **CLARIFY:** Some frontend issues are labeled `domain:inventory` but describe Product Admin workflows (e.g., fitment hints). Confirm ownership before adding to Inventory nav.

---

## 3. Key Entities & Relationships

### 3.1 Location model (two layers)

Inventory stories imply two related but distinct concepts:

1) **LocationRef** (HR-synced “shop/mobile site”)
- Fields: `id`, `tenantId`, `hrLocationId`, `name`, `status` (`ACTIVE|INACTIVE|PENDING`), `isActive`, `timezone`, `tags`, `version`, timestamps.
- Used to **scope** inventory operations and to block new movements to inactive locations.
- Backend reference indicates deactivation causes existing stock to be treated as **PendingTransfer** (derived state) requiring manual reconciliation; no auto-transfer in v1.

2) **StorageLocation** (inventory-managed internal hierarchy)
- Fields: `storageLocationId`, `siteId` (or mapping to LocationRef), `parentLocationId`, `name`, `barcode`, `storageType`, `status`, optional `capacity`, `temperature`.
- Used for **bin-level** operations: putaway, pick, cycle count, availability by bin, etc.

**CLARIFY:** Are `siteId` and `locationId` the same as `LocationRef.id`, or is there a separate Site entity? Frontend stories use `siteId`, `locationId`, and `storageLocationId`.

**Actionable UI guidance**
- Treat `LocationRef` as the “business location” selector (shop/site).
- Treat `StorageLocation` as the “bin within a site” selector.
- When both are present in a flow, enforce: `storageLocation.siteId == locationRef.id` (or equivalent mapping) in the UI when possible; backend remains authoritative.

### 3.2 InventoryLedgerEntry (append-only)
- Identifies a movement of stock with:
  - `productId` (or `productSku` in some views)
  - `timestamp` (UTC)
  - `movementType` (see 4.2)
  - `quantityChange` (signed decimal)
  - `unitOfMeasure` (product base UOM)
  - `fromLocationId` / `toLocationId` (nullable)
  - `actorId` (user/system)
  - `reasonCode` (required for adjustments)
  - `sourceTransactionId` (links to upstream transaction/work order)

Relationship highlights:
- Availability is computed by **replaying/summing ledger entries** (physical movements only) and subtracting allocations.
- StorageLocation deactivation may trigger **transfer** of stock to a destination location (atomic backend operation) for storage locations; HR LocationRef deactivation does **not** auto-transfer in v1 (PendingTransfer instead). **CLARIFY** if both behaviors exist simultaneously and how they interact.

### 3.3 AvailabilityView (computed)
- Request: `productSku`, `locationId`, optional `storageLocationId`
- Response: `onHandQuantity`, `allocatedQuantity`, `availableToPromiseQuantity`, `unitOfMeasure`

### 3.4 Availability feed ingestion (ops visibility)
If the inventory backend exposes manufacturer feed normalization artifacts, the frontend will treat them as read-only operational entities:
- `IngestionRun`: run id, manufacturer, startedAt/endedAt, asOfTime, outcome, counts (processed/failed/unmapped), error summary.
- `NormalizedAvailability` / `ExternalAvailability`: manufacturer part number, mapped productSku/productId (if mapped), quantity, asOfTime, location scope (if any), source metadata.
- `UnmappedManufacturerParts`: manufacturer, partNumber, firstSeenAt, lastSeenAt, status (`PENDING_REVIEW|IGNORED|RESOLVED`), notes (maybe).
- `ExceptionQueue`: severity, status (`OPEN|ACKNOWLEDGED|RESOLVED`), details, createdAt, updatedAt, operator notes, correlation ids.

**TODO:** Confirm exact entity names and fields; build UI adapters to tolerate additive fields.

---

## 4. Business Rules & Invariants (Frontend-Relevant)

### 4.1 Ledger immutability
- Ledger entries are **append-only**. UI must never offer edit/delete.
- Any “correction” is a new movement (e.g., adjustment) that creates new ledger entries.

### 4.2 On-hand vs allocations vs ATP (resolved by backend story #36)
From backend reference:
- **On-hand** = net sum of **physical stock movements** (receipts/issues/transfers/returns/adjustments/count variances).
- **Allocations** are excluded from on-hand.
- **ATP v1** = `OnHand - Allocations` (expected receipts excluded).

Physical movement types included in on-hand:
- Inbound: `GOODS_RECEIPT`, `TRANSFER_IN`, `RETURN_TO_STOCK`, `ADJUSTMENT_IN`, `COUNT_VARIANCE_IN`
- Outbound: `GOODS_ISSUE`, `TRANSFER_OUT`, `SCRAP_OUT`, `ADJUSTMENT_OUT`, `COUNT_VARIANCE_OUT`

Explicitly excluded from on-hand:
- reservation/allocation events and pick task lifecycle events unless they post a physical movement.

Frontend invariants:
- Do not compute ATP client-side.
- Display values in **product base UOM** only (no conversions in v1).
- Quantities are **decimal**; do not assume integer-only.

### 4.3 Availability empty state is success
- “No inventory history” must be treated as a **successful** response with all quantities = 0.
- UI should show an explicit note (“No inventory history for this scope”) rather than an error.

### 4.4 Location aggregation
- Querying a parent `locationId` aggregates across child storage locations.
- Providing `storageLocationId` scopes to that bin only.
- If `storageLocationId` is provided, it must belong to the selected `locationId`.

### 4.5 StorageLocation hierarchy constraints
- Barcode must be **unique per site**.
- Parent/child hierarchy must be **cycle-free**.
- Deactivation:
  - If location is non-empty, backend may require `destinationLocationId` and perform atomic transfer+deactivate.
  - Inactive locations should be excluded from parent pickers by default (configurable; see Open Questions).

### 4.6 HR-synced LocationRef constraints (resolved by backend story #40)
- HR is authoritative for `LocationRef` lifecycle and attributes.
- **Inactive LocationRef cannot receive new inbound stock movements**; backend rejects with **422**.
- Existing stock at an HR-deactivated location becomes **PendingTransfer** (derived state) requiring manual reconciliation within SLA (5 business days per backend reference).
- Frontend must proactively prevent selection of inactive locations in “new movement” flows (defense in depth).

**CLARIFY:** What constitutes “new inbound stock movements” in this frontend repo (receipts/transfers/adjustments/consumption/etc.) and which routes/screens must be updated.

### 4.7 Deep-linking behavior (availability + ledger)
- Availability story expects query params (`productSku`, `locationId`, optional `storageLocationId`) and auto-run on load.
- Ledger story expects deep links to list with filters and to entry detail by id.

**CLARIFY:** Whether deep-linking is officially supported and canonical param names/routing conventions.

---

## 5. Integration Patterns & Events

### 5.1 HR → Inventory topology sync
- Inventory consumes HR events or roster sync and upserts `LocationRef`.
- Every applied change produces a `SyncLog` entry.
- Frontend provides read-only visibility into LocationRef and SyncLog, and optionally a “Sync now” trigger if backend supports it.

**Backend reference nuance:** backend supports both event-driven and bulk reconcile; UI “Sync now” should likely trigger bulk reconcile (FULL/INCREMENTAL) if exposed.

### 5.2 Work Execution ↔ Inventory ledger linkage
- Ledger entries should be linkable from work order lines for “movement history”.
- Frontend needs a stable identifier to filter ledger entries:
  - `sourceTransactionId` and/or `workorderId/workorderLineId` fields.

**CLARIFY:** Which identifier is guaranteed in ledger entries for workexec integration.

### 5.3 Inventory availability consumers
- Service Advisor screens (estimate/quote) may call availability.
- Prefer a consistent integration approach:
  - either Vue client calls backend directly, or
  - Moqui services act as a proxy/facade (common in Moqui setups).

**TODO:** Confirm repo convention and standardize.

### 5.4 Availability feed normalization (manufacturer feeds)
If inventory owns the normalization pipeline (or exposes its artifacts), frontend patterns should follow:
- Read-only list/detail screens for runs and normalized availability.
- ExceptionQueue triage actions (status updates, operator notes) must be permission-gated and use optimistic locking if supported.
- Retention/TTL: UI must tolerate missing older runs and provide date-range filters rather than assuming infinite history.

**CLARIFY:** Whether ExceptionQueue is inventory-specific or shared across integrations.

---

## 6. API Contracts & Patterns (Moqui Frontend)

> Many frontend stories are blocked on exact service names/paths. Until confirmed, implement UI with a thin adapter layer so endpoint changes are localized.

### 6.1 Contract conventions (recommended)
- Prefer **plain JSON** responses with stable shapes.
- If backend uses an envelope (`{ data, meta, errors }`), centralize unwrapping in one client module.
- Pagination should be consistent across list endpoints:
  - Either `items + totalCount + pageIndex/pageSize`
  - Or cursor-based `items + nextPageToken`

**CLARIFY:** Ledger list pagination shape and parameter names.

### 6.2 Availability API (read-only)
- **Purpose:** show on-hand/allocated/ATP for a SKU at location/bin scope.
- **Inputs:** `productSku`, `locationId`, optional `storageLocationId`
- **Outputs:** `AvailabilityView`
- **Performance expectation (backend):** p95 < 200ms at service boundary (warm cache). Frontend should:
  - show loading state immediately
  - avoid repeated auto-refresh loops
  - debounce input changes; only call on explicit submit or deep-link auto-run once

**TODO:** Confirm exact endpoint path (e.g., `/v1/inventory/availability`) and auth mechanism.

### 6.3 Ledger APIs (read-only list + detail)
- List supports filters: product, location, movementType, date range, sourceTransactionId.
- Location filter semantics should be:
  - match `fromLocationId OR toLocationId` (recommended for usability) unless backend dictates otherwise.
- Must be paginated; default page size 25.

**CLARIFY:** Confirm location filter semantics and paging contract.

### 6.4 Adjustments APIs (create + approve/post) — if in scope
- Adjustment create requires:
  - permission
  - non-zero quantityChange
  - reasonCode
- Approve/post may be a separate transition and may require optimistic locking.

**CLARIFY:** Whether adjustments are implemented in this repo or link-out only; confirm entity/state machine.

### 6.5 StorageLocation APIs (CRUD + deactivate)
- List by site, get detail, create, update, deactivate.
- Deactivate may require destination selection if non-empty.

**TODO:** Confirm service names/paths and error codes for `DESTINATION_REQUIRED` / `INVALID_DESTINATION`.

### 6.6 LocationRef / SyncLog APIs (read-only + optional trigger)
- List/detail for LocationRef and SyncLog.
- SyncLog payload visibility may be permission-gated.
- Optional “Sync now” trigger:
  - should return a run id or acknowledgement; UI should not assume synchronous completion.

**TODO:** Confirm permissions and whether payload is always returned or requires separate call.

### 6.7 Availability feed ops APIs (runs, normalized availability, unmapped, exceptions)
Frontend stories require contracts for:
- ingestion run list + detail
- normalized availability list
- unmapped parts list (+ optional status update)
- exception queue list + detail (+ optional status update, operator notes)

**TODO:** Confirm endpoints/services, error schema, paging, and whether updates are PUT vs PATCH.

---

## 7. Security / Authorization Requirements

### 7.1 Backend enforcement is mandatory
Frontend gating improves UX but must not be relied upon for security. Always handle:
- `401 Unauthorized` (session expired)
- `403 Forbidden` (insufficient permissions)
- `422 Unprocessable Entity` (business rule violations like inactive location)

### 7.2 Permission-driven UI affordances (when available)
Stories reference permissions like:
- `INVENTORY_ADJUST_CREATE`, `INVENTORY_ADJUST_APPROVE` (ledger/adjustments)
- Topology view/admin permissions (LocationRef/SyncLog)
- Integration ops permissions (availability ingestion monitoring, exception triage)
- Potential inventory.* style permissions (other stories mention conflicts)

**CLARIFY:** Permission naming convention for this repo (enum-like vs `inventory.*` strings). Do not hardcode until confirmed.

### 7.3 Sensitive data handling
- Availability quantities may be considered sensitive in some environments.
- SyncLog payloads may contain upstream metadata; treat as potentially sensitive.
- ExceptionQueue details may include vendor identifiers or operational notes; treat as sensitive-by-default.

**TODO:** Decide logging policy for quantities and payloads (see Open Questions).

### 7.4 Secure-by-default UI patterns
- Do not render SyncLog payload by default; require explicit “View payload” action and permission check.
- Avoid storing sensitive payloads in client-side persistent storage.
- When showing correlation IDs, do not show internal stack traces or raw exception dumps.
- For exception triage updates:
  - require explicit confirmation for status transitions that imply resolution
  - include optimistic locking token if provided (ETag/version) to prevent overwriting another operator’s work

---

## 8. Observability Guidance (Frontend)

### 8.1 Logs (structured, minimal)
Log events for:
- Availability query initiated/succeeded/failed
- Ledger list query initiated/succeeded/failed
- StorageLocation create/update/deactivate attempts and outcomes
- Topology LocationRef/SyncLog list/detail fetch outcomes
- Availability feed ops:
  - ingestion run list/detail fetch
  - exception queue list/detail fetch
  - exception status update attempts/outcomes
  - unmapped parts list fetch and status update (if supported)

Recommended log fields:
- `feature`: `inventory.availability | inventory.ledger | inventory.topology | inventory.availabilityOps`
- `action`: `query | list | detail | create | update | deactivate | triage`
- `httpStatus`
- `durationMs`
- Identifiers: `locationId`, `storageLocationId`, `ledgerEntryId`, `syncId`, `runId`, `exceptionId` (avoid PII)
- Correlation ID if present

**Avoid** logging:
- Full SyncLog payloads
- ExceptionQueue full details blobs
- Potentially sensitive quantities unless explicitly allowed (see Open Questions)

### 8.2 Metrics (frontend-side)
If the repo has a metrics hook, emit:
- `inventory_availability_request_count{status}`
- `inventory_availability_latency_ms`
- `inventory_ledger_list_request_count{status}`
- `inventory_topology_syncLog_payload_view_count{status}`
- `inventory_availabilityOps_exception_update_count{status}`

### 8.3 Traces / correlation
- Propagate correlation IDs if the repo supports it (e.g., `X-Correlation-Id`).
- Surface correlation ID in error UI under “Technical details” (collapsed by default).

**CLARIFY:** Existing convention in this repo for correlation IDs and 401 vs 403 handling.

---

## 9. Testing Strategies

### 9.1 Unit tests (Vue components)
- Availability form validation:
  - required SKU/location
  - storage selector disabled until location selected
  - “stale result” behavior when inputs change after success
- Ledger filters:
  - dateFrom <= dateTo validation
  - location filter semantics (UI expectation: from OR to)
- StorageLocation forms:
  - required fields
  - self-as-parent prevention
  - inactive state disables actions per rules
- Topology screens:
  - status badges (ACTIVE/INACTIVE/PENDING)
  - payload viewer gated and truncated rendering
- Availability ops screens:
  - list paging controls
  - status chips and filters
  - exception status update confirmation flows

### 9.2 Integration tests (service adapters)
- Contract tests for:
  - availability response mapping (including success-with-zeros)
  - ledger pagination mapping
  - deactivate location error mapping (`DESTINATION_REQUIRED`, `INVALID_DESTINATION`)
  - inactive location rejection (422) mapping to user message
  - SyncLog payload access denied (403) mapping to “not authorized to view payload”
  - exception queue update conflict (409) mapping if optimistic locking exists

Use mock server fixtures with:
- 200 success
- 400 validation errors (field-level)
- 401/403 auth errors
- 404 not found
- 409 conflict (optimistic locking)
- 422 business rule violations (inactive location)

### 9.3 End-to-end tests (happy paths)
- Availability screen:
  - location-level query
  - storage-level query
  - deep-link load with query params (if supported)
  - 403 path does not display quantities
- Ledger:
  - list + filter + open detail
  - verify no edit/delete actions exist
- Storage locations:
  - create, update parent, deactivate empty, deactivate non-empty requiring destination
- Topology:
  - view LocationRef list/detail
  - view SyncLog list and handle forbidden payload
- Availability ops:
  - view ingestion runs list
  - view exception queue list/detail
  - update exception status (if supported) with retry on conflict

---

## 10. Common Pitfalls & Gotchas

1. **Confusing `locationId` vs `storageLocationId` vs `siteId`:**
   - Stories use all three. Ensure UI labels and service calls are consistent.
   - **CLARIFY** the canonical model and naming.

2. **Assuming pickers exist:**
   - Availability and ledger filters need product/location pickers.
   - If pickers/services don’t exist, scope expands significantly. Prefer minimal UUID/text input only if explicitly approved.

3. **Leaking sensitive data in logs:**
   - Avoid logging quantities and payloads until policy is confirmed.
   - Treat SyncLog payload and ExceptionQueue details as sensitive-by-default.

4. **Not handling “success with zeros”:**
   - Availability “no history” is a valid success state; do not treat as error.

5. **Not enforcing inactive location blocking consistently:**
   - Must be applied to all “new movement” flows, not just topology screens.
   - Backend will reject with 422; frontend should prevent selection and handle 422 gracefully.

6. **Pagination mismatches:**
   - Ledger lists and ops lists must not fetch unbounded results.
   - Centralize pagination mapping to avoid off-by-one and “totalCount missing” bugs.

7. **Timezone confusion:**
   - Ledger timestamps are UTC; display in user locale.
   - For date filters, ensure ISO formatting and clarify whether backend expects UTC boundaries.
   - LocationRef has its own `timezone` field; do not “convert” it—display as IANA string.

8. **Mixing HR LocationRef deactivation vs StorageLocation deactivation semantics:**
   - HR deactivation: blocks new inbound movements; existing stock becomes PendingTransfer (no auto-transfer).
   - StorageLocation deactivation: may require destination transfer to deactivate.
   - UI must not assume the same workflow applies to both.

---

## 11. Implementation Notes (Moqui-Oriented)

### 11.1 Prefer Moqui service façade (recommended default)
Unless the repo explicitly calls backend APIs directly from Vue:
- Implement Moqui services that proxy to backend endpoints.
- Keep auth/session handling centralized in Moqui.
- Standardize error mapping and correlation ID propagation.

**TODO:** Confirm whether Vue calls backend directly or via Moqui transitions/services.

### 11.2 Screen deep-linking
Availability story proposes query params:
- `productSku`, `locationId`, `storageLocationId`

Ledger story proposes:
- list with query params (filters)
- detail route by `ledgerEntryId`

**CLARIFY:** Whether deep-linking is officially supported and the canonical param names/routing conventions.

### 11.3 Adapter layer pattern (actionable)
Implement a single `inventoryApi.ts` (or Moqui service wrapper) with functions:
- `getAvailability({ productSku, locationId, storageLocationId? })`
- `listLedger({ filters..., page })`
- `getLedgerEntry(id)`
- `listStorageLocations(siteId, filters, page)`
- `deactivateStorageLocation(id, destinationId?)`
- `listLocationRefs(filters, page)`
- `listSyncLogs(filters, page)`
- `getSyncLog(syncId)` (payload gated)
- `listIngestionRuns(filters, page)` **TODO**
- `listNormalizedAvailability(filters, page)` **TODO**
- `listUnmappedParts(filters, page)` **TODO**
- `updateUnmappedPartStatus(id, status)` **TODO**
- `listExceptionQueue(filters, page)` **TODO**
- `updateExceptionStatus(id, status, version?)` **TODO**

This keeps endpoint churn localized while stories are still clarifying contracts.

---

## 12. Open Questions from Frontend Stories

Consolidated questions requiring clarification before finalizing contracts and UI behavior:

### 12.1 Availability (On-hand / ATP)
1. What is the **exact backend endpoint path and auth mechanism** the Moqui frontend should call for availability (e.g., `/v1/inventory/availability` vs something else), and what is the expected request/response envelope (plain JSON vs wrapped `{data: ...}`)?
2. Does the frontend already have **existing pickers** for `locationId` and `storageLocationId` (and a known service to load them)? If not, should this story:
   - (a) use free-text UUID entry, or
   - (b) include building minimal location/storage lookup UI (expanded scope)?
3. Should the Availability screen support **deep-linking via URL query params** officially (bookmarked by advisors), and if so what are the canonical parameter names and routing conventions in this repo?
4. Are availability quantities considered sensitive such that frontend logs must **avoid logging numeric quantities** (only status/timing), or is it acceptable to log them for debugging?

### 12.2 Ledger (Stock movements)
5. **Backend contract for ledger queries:** What are the exact Moqui service names or REST endpoints, including query parameter names and pagination response shape (items/total, page tokens, etc.)?
6. **Adjustment UI scope:** Should the frontend implement full Adjustment Request screens in this repo, or only provide links to an existing adjustments area? If full, what is the adjustment request entity name/fields and state machine (`PENDING/APPROVED/POSTED`)?
7. **Location filtering semantics:** For the ledger list filter by location, should it match `fromLocationId OR toLocationId` (recommended), or only a specific side depending on movement type?
8. **Workexec integration key:** For “Workexec can query movement history for a workorder line”, what identifier is available in ledger entries—`sourceTransactionId` only, or a dedicated `workorderId/workorderLineId` field?

### 12.3 Storage Locations (Topology: StorageLocation CRUD)
9. What are the exact Moqui service names / REST endpoints and response schemas for:
   - list sites, list storage types, list locations by site, get location detail, create, update, deactivate?
10. Are `capacity` and `temperature` structured JSON inputs expected to follow a defined schema (keys/units), or should the frontend treat them as freeform JSON text?
11. Can an **Inactive** storage location be edited (e.g., name/barcode fixes), or must it be fully read-only?
12. Is changing `storageType` allowed after creation, or is it immutable?
13. Should parent selection exclude **Inactive** locations strictly, or allow choosing an inactive parent for historical organization (while still preventing operational use)?

### 12.4 HR-synced Locations (Topology: LocationRef + SyncLog)
14. **Backend service contracts:** What are the exact Moqui service names (or REST endpoints) and request/response schemas for `LocationRef` and `SyncLog` list/detail?
15. **Permissions:** What permission(s) gate access to Topology screens and SyncLog payload visibility?
16. **Manual sync trigger:** Does the backend support an on-demand “Sync now” / reconcile action? If yes, what are the inputs/outputs, and should it be restricted to admins only?
17. **Definition of “stock movements” in the current frontend:** Which specific screens/components in `durion-moqui-frontend` represent “new inbound stock movements” (receipts/transfers/adjustments/consumption) that must block inactive locations? Provide routes/screen names to update.
18. **PENDING status behavior:** If `LocationRef.status=PENDING` exists in v1, should PENDING locations be selectable for new movements, or treated as inactive until ACTIVE?
19. **Tags display expectations:** Should tags be treated as opaque JSON, or are there known keys that need friendly rendering/filtering?

### 12.5 Availability feed normalization / integration ops (Manufacturer feeds)
20. **Backend API contracts (blocking):** What are the exact endpoints (paths), request params, and response schemas for:
   - ingestion run list + detail
   - normalized availability list
   - unmapped parts list (+ optional status update)
   - exception queue list + detail
21. **Authorization (blocking):** What permission(s)/role(s) gate access to these screens? Are they the same as existing “inventory ops” permissions or new integration-specific ones?
22. **ExceptionQueue scope (blocking):** Is there an existing exception queue API in this frontend/backends, or must this screen be limited to inventory-domain exceptions only? What fields are guaranteed (severity, status, details)?
23. **Unmapped status update (scope decision):** Should the frontend allow changing `UnmappedManufacturerParts.status` (PENDING_REVIEW/IGNORED/RESOLVED), or is it strictly read-only in v1?
24. **Retention/TTL UX:** Will runs/availability be retained, and should UI support selecting archived ranges?

### 12.6 Cross-cutting (Moqui integration + routing)
25. **Moqui integration pattern (blocking):** Should the frontend call the backend Inventory API directly from the Vue client, or via Moqui screen transitions/services acting as a proxy (preferred in many Moqui setups)?
26. **Auth/correlation (blocking):** Is there an existing convention in this repo for propagating correlation IDs (e.g., `X-Correlation-Id`) and for handling 401 vs 403 in the UI?
27. **SLA requirement detail:** Availability backend targets p95 < 200ms at service boundary; is there a frontend SLA expectation (e.g., p95 end-to-end) that should be monitored/enforced?

---

## 13. TODO / CLARIFY Summary (Actionable)

- TODO: Confirm canonical identifiers and naming: `siteId` vs `locationId` vs `LocationRef.id`.
- TODO: Confirm integration pattern: Vue direct-to-backend vs Moqui proxy services.
- TODO: Confirm endpoint paths, envelopes, and pagination shapes for availability/ledger/topology/availability-ops.
- TODO: Confirm permission strings and how frontend discovers them (claims vs endpoint).
- TODO: Confirm logging policy for quantities and SyncLog/ExceptionQueue payload visibility.
- TODO: Confirm whether availability feed normalization artifacts are inventory-owned and what the canonical entity names are.
- CLARIFY: Which inventory “movement” screens exist today and must enforce inactive-location blocking.
- CLARIFY: Whether StorageLocation deactivation transfer semantics and LocationRef deactivation PendingTransfer semantics both exist, and which applies in which UI flows.

---
```
