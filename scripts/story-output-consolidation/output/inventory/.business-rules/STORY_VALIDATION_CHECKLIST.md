# STORY_VALIDATION_CHECKLIST.md

## Summary

Validation checklist for inventory-domain stories. Use this alongside `AGENT_GUIDE.md` and the decision set `DECISION-INVENTORY-###`.

## Completed items

- [x] Converted prior open questions into acceptance criteria

## Scope/Ownership

- [ ] Verify each story is correctly labeled `domain:inventory` and resolve conflicts where issue text suggests a different domain but inventory owns the capability.
- [ ] Verify each screen/flow has a clear primary persona and entry point (Inventory menu vs contextual links).
- [ ] Verify out-of-scope items are explicitly not implemented (e.g., no inventory edits on Availability screen; no ledger mutation; no implicit UOM conversions).
- [ ] Verify integration pattern: UI calls Moqui proxy only (no direct Vue → inventory backend). (DECISION-INVENTORY-002)
- [ ] Verify admin/ops screens (Topology, SyncLog, feed monitoring) are placed under correct navigation namespace and are not exposed to general users by default.
- [ ] Verify cross-domain workflows (Work Orders / Fulfillment / Pricing / Master Data) explicitly document system-of-record per screen and do not “invent” inventory-owned behavior when the owning domain is WorkExec/Pricing/Security/Product.
- [ ] Verify each story explicitly states whether it is read-only vs mutating, and reviewers confirm the UI does not introduce mutations in read-only flows.
- [ ] Verify any “workorder subflow” screen placement and route naming follow repo conventions and are consistent across WorkorderDetail entry points (no duplicate/competing routes).

## Data Model & Validation

- [ ] Verify required identifiers are validated client-side before calling backend (trimmed non-empty `productSku`, required `locationId` where applicable).
- [ ] Verify Availability query validation:
  - [ ] `productSku` is trimmed and non-empty; whitespace-only is rejected.
  - [ ] `locationId` is required.
  - [ ] `storageLocationId` is optional; if provided it must belong to the selected `locationId`.
- [ ] Verify date range filters enforce `from <= to` and block submit when invalid.
- [ ] Verify quantity inputs enforce domain rules where applicable:
  - [ ] Availability display is read-only (no quantity edits). (DECISION-INVENTORY-004)
  - [ ] Adjustments: `quantityChange` required and cannot be zero. (DECISION-INVENTORY-006)
- [ ] Verify “no inventory history” is treated as success for Availability with zeros and a clear empty-state message. (DECISION-INVENTORY-004)
- [ ] Verify storage location create/update validation:
  - [ ] `locationId`/site, `name`, `barcode`, `storageType` required on create. (DECISION-INVENTORY-007)
  - [ ] `barcode` is trimmed and non-empty; duplicates are surfaced as field errors. (DECISION-INVENTORY-003)
  - [ ] Parent selection prevents self-parenting and cycles.
- [ ] Verify deactivation workflow validation:
  - [ ] Deactivate action is disabled/hidden when already inactive.
  - [ ] If backend indicates destination is required for non-empty location, UI requires `destinationLocationId` and blocks submit without it. (DECISION-INVENTORY-007)
- [ ] Verify inactive/pending location selection rules are enforced consistently across any “new movement” flow (block selection; historical display read-only). (DECISION-INVENTORY-009)
- [ ] Verify JSON fields render safely:
  - [ ] `tags`, `capacity`, `temperature`, SyncLog payloads, and feed payloads render via a safe JSON viewer (escape + truncate + explicit expand/copy). (DECISION-INVENTORY-015)
- [ ] Verify “Return to Stock” line validation:
  - [ ] Quantity to return is integer and `> 0` for included lines.
  - [ ] Quantity to return `<= maxReturnableQty` returned by backend (frontend must not compute returnability).
  - [ ] Return reason is mandatory for each line with quantity `> 0` and must be selected from backend-provided codes (no free-text).
  - [ ] Submission is blocked when reason codes list is empty/unavailable.
- [ ] Verify “Consume/Issue picked items” line validation:
  - [ ] `workorderId` is required from route.
  - [ ] Negative quantities are rejected client-side.
  - [ ] Only lines with `qtyToConsume > 0` are included in the submit payload.
  - [ ] `qtyToConsume <= qtyRemaining` when `qtyConsumed` is provided; else `qtyToConsume <= qtyPicked`.
  - [ ] UI accepts decimals unless backend provides per-line `uom`/precision constraints (do not default to integer-only).
- [ ] Verify “Picking (scan + confirm)” validation (when contract is confirmed):
  - [ ] Scan input is trimmed and non-empty before any resolve/confirm call.
  - [ ] Confirm quantity is numeric and `> 0`.
  - [ ] Confirm quantity does not exceed remaining required quantity unless backend explicitly supports over-pick/partial completion (do not hardcode policy without contract).
  - [ ] Confirm/complete actions are disabled when backend indicates task/line is not actionable (completed/cancelled/fully picked).
- [ ] Verify “Cycle Count Plan” validation:
  - [ ] `locationId` is required.
  - [ ] At least one `zoneId` is required and zones must belong to selected location.
  - [ ] Scheduled date is not in the past per the defined timezone rule; UI blocks submit when invalid.
  - [ ] Optional plan name/description is not client-truncated unless backend limits are confirmed; backend field errors map to the correct field.
- [ ] Verify “Supplier/Vendor cost tiers” validation is only enforced client-side when backend contract confirms rules; otherwise implement minimal safe validation only:
  - [ ] Supplier selection required via picker (no free-text UUID).
  - [ ] Tier numeric parsing: `minQuantity >= 1`, `maxQuantity >= minQuantity` when present, `unitCost > 0`.
  - [ ] Structural tier rules (start at 1, contiguous, non-overlapping, single open-ended last tier) are not enforced unless confirmed; backend errors are surfaced deterministically.
  - [ ] Uniqueness constraint (one record per supplier+item) is handled via backend 409/422 mapping (no racy client pre-check).
- [ ] Verify “UOM conversions” validation (if story remains in scope for this repo):
  - [ ] `conversionFactor` is numeric and `> 0`.
  - [ ] From/To UOM required.
  - [ ] Self-conversion is blocked unless factor equals 1.
  - [ ] No hard delete is exposed; only deactivate (and optional reactivate only if backend supports it).

## API Contract

- [ ] Verify API contracts use plain JSON responses and deterministic error schema. (DECISION-INVENTORY-003)
- [ ] Verify all list screens use cursor pagination (`pageSize`, `pageToken`, `nextPageToken`) and do not rely on total counts unless explicitly provided. (DECISION-INVENTORY-003)
- [ ] Verify error payload parsing is robust:
  - [ ] Field-level validation errors map to the correct form fields when possible.
  - [ ] Unknown error shapes fall back to a generic message without crashing.
- [ ] Verify each new screen/story explicitly documents the Moqui proxy endpoint(s) it calls, including method, path, required params, and minimum response fields needed for rendering.
- [ ] Verify composite-key vs ID-based routing is unambiguous for detail screens (e.g., `supplierItemCostId` vs `supplierId+itemId`; `pickTaskId` vs `workOrderId`) and the UI uses the canonical identifier.
- [ ] Verify mutation endpoints specify whether they are full-replace vs partial update (PUT vs PATCH) and the UI payload matches (no accidental partial tier deletion or unintended field resets).
- [ ] Verify contracts define stable line identifiers for line-level error mapping (e.g., `pickedItemId`, `workOrderLineId`, `pickLineId`) and UI uses those IDs for inline errors.
- [ ] Verify 409 conflict semantics are defined for mutating flows (what constitutes conflict, expected error code/shape, and whether version/ETag is required on update/confirm/complete).

## Events & Idempotency

- [ ] Verify read-only screens do not emit mutation side effects (Availability, Ledger view, Topology views).
- [ ] Verify mutation actions are protected against double-submit (disable while in-flight).
- [ ] Verify UI does not auto-retry mutations.
- [ ] Verify each mutating flow documents idempotency expectations and retry guidance:
  - [ ] If backend supports idempotency keys, UI sends the required header/field (e.g., `Idempotency-Key`) per request and reuses it only for explicit user retry of the same attempt.
  - [ ] If backend does not support idempotency, UI explicitly warns on timeout that the operation may have completed and provides a safe “Reload status” action rather than blind retry.
- [ ] Verify scan/confirm flows do not re-submit the same scan/confirm automatically on re-render or navigation back (no hidden loops).
- [ ] Verify optimistic locking/version fields (if present) are included on confirm/complete/update requests and are refreshed after successful responses.

## Security

- [ ] Verify all inventory screens enforce authentication (401 routes to login/session refresh per app convention). (DECISION-INVENTORY-012)
- [ ] Verify authorization is enforced in UI and backend:
  - [ ] UI hides/disables gated actions when permission data is available. (DECISION-INVENTORY-010)
  - [ ] Backend 403 is handled gracefully with a forbidden state and no data leakage. (DECISION-INVENTORY-012)
- [ ] Verify sensitive data handling:
  - [ ] Availability quantities are not logged to console/telemetry. (DECISION-INVENTORY-011)
  - [ ] Raw payload blobs (SyncLog, feed payloads) are not logged and are permission-gated. (DECISION-INVENTORY-011)
- [ ] Verify cost-related screens (supplier cost tiers) are treated as sensitive business data:
  - [ ] UI does not log unit costs/base costs/tier arrays to console/telemetry.
  - [ ] View vs manage permissions are distinct if backend defines them; otherwise UI relies on backend 403 and renders read-only when denied.
- [ ] Verify workorder/fulfillment flows do not expose more workorder data than returned by the backend for the current user (no cross-screen caching that leaks data after a 403).
- [ ] Verify any audit/history panels are permission-gated if backend indicates separate audit permissions (do not assume audit visibility equals edit visibility).

## Observability

- [ ] Verify `X-Correlation-Id` is propagated on requests and shown in error UI technical details. (DECISION-INVENTORY-012)
- [ ] Verify mutating flows include correlation id on create/update/delete/confirm/complete calls and surface it on all error states (banner + technical details).
- [ ] Verify UI telemetry (if present) records only safe metadata (screen name, endpoint name, status code, timing, correlation id) and excludes request/response bodies and numeric quantities/costs.

## Performance & Failure Modes

- [ ] Verify list screens use server-side pagination; do not fetch unbounded datasets.
- [ ] Verify large JSON payloads are not rendered eagerly in tables (preview + on-demand expand).
- [ ] Verify timeout UX is bounded (spinner appears quickly; timeouts show retry).
- [ ] Verify pickers that can be large (locations, storage locations, suppliers, zones) use server-side filtering/pagination and do not load entire datasets by default.
- [ ] Verify scan-driven screens avoid N+1 calls (prefer a single load for task+lines; resolve-scan/confirm are incremental).
- [ ] Verify after successful mutations, refresh behavior is bounded and deterministic (single reload of affected read model; no polling loops).

## Testing

- [ ] Unit tests cover client-side validation rules (required fields, date ranges, non-zero adjustments).
- [ ] Component tests cover `idle → loading → success/error` and correct UI rendering.
- [ ] Contract tests/mocks cover success and failure (401/403/404/422/5xx) with deterministic error schema.
- [ ] Add tests for line-level error mapping for multi-line forms (return-to-stock, consume-picked, pick confirm, cost tiers):
  - [ ] Backend error referencing a line identifier maps to the correct row.
  - [ ] Backend error referencing a field path (e.g., `tiers[1].minQuantity`) maps to the correct row/field.
- [ ] Add tests for conflict handling (409):
  - [ ] UI prompts reload and does not auto-merge.
  - [ ] After reload, staged inputs are reset or preserved only per documented deterministic rule.
- [ ] Add tests ensuring sensitive values are not logged:
  - [ ] Availability quantities not emitted to telemetry.
  - [ ] Costs/quantities not emitted to telemetry for cost tiers and fulfillment mutations.

## Documentation

- [ ] Each new/modified screen documents route, backend services called, and required permissions.
- [ ] Error mapping table documented for each flow (400/401/403/404/409/422/5xx) with user-facing messages.
- [ ] For cross-domain screens, document system-of-record and dependency contracts (WorkExec vs Inventory vs Pricing vs Product vs Security) and link to the owning decision/ADR where applicable.
- [ ] Document idempotency/retry guidance per mutating endpoint (what the user should do after timeout; whether retry is safe).

## Acceptance Criteria (Resolved Open Questions)

### Availability endpoint, auth mechanism, and envelope

- [ ] Availability uses the contract described in the inventory domain docs and is called via Moqui proxy. (DECISION-INVENTORY-002, DECISION-INVENTORY-004)
- [ ] Response is plain JSON (no `{data: ...}` envelope) and errors follow deterministic schema. (DECISION-INVENTORY-003)

### Pickers for `locationId` and `storageLocationId`

- [ ] UI uses pickers backed by inventory read models; no free-text UUID entry in normal user flows. (DECISION-INVENTORY-001, DECISION-INVENTORY-008)
- [ ] UI prevents choosing a storage location outside the selected site when possible; backend errors are surfaced when not preventable. (DECISION-INVENTORY-001, DECISION-INVENTORY-003)

### Availability deep-linking

- [ ] Availability supports canonical deep-link parameters and does not loop on auto-run. (DECISION-INVENTORY-004, DECISION-INVENTORY-014)

### Availability quantity sensitivity

- [ ] Client logs/telemetry never emit numeric quantities from Availability results. (DECISION-INVENTORY-011)

### Ledger endpoints and pagination shape

- [ ] Ledger uses cursor pagination and immutable entries; UI never attempts to mutate ledger records. (DECISION-INVENTORY-003, DECISION-INVENTORY-005)

### Ledger location filter semantics

- [ ] Filtering by location matches `fromLocationId OR toLocationId` semantics and UI copy reflects this. (DECISION-INVENTORY-005)

### Workexec integration identifier

- [ ] Movement history deep-links use identifiers that exist on ledger entries (e.g., `sourceTransactionId`), without assuming Workexec-only IDs. (DECISION-INVENTORY-005)

### Storage location field schemas (`capacity`, `temperature`)

- [ ] `capacity` and `temperature` are treated as opaque JSON; UI validates JSON syntax only and renders safely. (DECISION-INVENTORY-015)

### Inactive storage location editability

- [ ] Inactive locations are blocked for new movement flows; historical display is read-only. (DECISION-INVENTORY-009)
- [ ] If backend allows metadata fixes on inactive bins, UI supports it without enabling operational use. (DECISION-INVENTORY-007, DECISION-INVENTORY-009)

### Storage type mutability

- [ ] `storageType` is create-only unless backend explicitly documents mutation support. (DECISION-INVENTORY-007)

### Parent selection rules (inactive parents)

- [ ] Parent selection defaults to active-only for operational flows; historical organization may include inactive parents if explicitly enabled. (DECISION-INVENTORY-009)

### Topology permissions (LocationRef / SyncLog / payload)

- [ ] Topology screens are permission-gated and payload visibility is separately gated. (DECISION-INVENTORY-010, DECISION-INVENTORY-011)

### Manual “Sync now” trigger

- [ ] If manual sync exists, it is permission-gated and guarded against double-trigger. (DECISION-INVENTORY-008)

### “New movement” flows that block inactive locations

- [ ] Any flow that creates movement/adjustment blocks `INACTIVE` and `PENDING` sites/bins; PENDING is treated like INACTIVE. (DECISION-INVENTORY-009)

### Tags rendering expectations

- [ ] Tags are treated as opaque JSON and are rendered safely (escape + truncate + expand/copy). (DECISION-INVENTORY-015)

### Fitment hints scope (if applicable)

- [ ] Fitment hints remain out-of-scope unless an inventory-owned contract exists; if implemented, they use deterministic errors and inventory permission naming. (DECISION-INVENTORY-003, DECISION-INVENTORY-010)

### Feed normalization / ingestion monitoring

- [ ] Feed ops screens and allowed updates match inventory-owned contracts and permissions. (DECISION-INVENTORY-013, DECISION-INVENTORY-010)

## Open Questions to Resolve

- [ ] Domain ownership: confirm whether Supplier/Vendor cost tiers belong to `domain:inventory` vs `domain:pricing/procurement/costing`, and update labels/routes accordingly.
- [ ] Supplier cost tiers contract: confirm Moqui proxy endpoints, request/response schemas, and identifiers (list by item, load detail, create, update: PUT replace vs PATCH, delete).
- [ ] Supplier cost tiers permissions: confirm canonical permission strings for view vs manage (create/update/delete).
- [ ] Supplier cost tiers identifier model: confirm canonical item identifier (`itemId` vs `productId` vs `productSku`) and canonical route/deep-link params.
- [ ] Supplier cost tiers currency rules: confirm whether `currencyCode` is supplier-derived read-only vs overrideable per supplier-item cost.
- [ ] Supplier cost tiers base cost: confirm whether `baseCost` exists, whether tiers are required, and baseCost fallback semantics.
- [ ] Supplier cost tiers precision/rounding: confirm required decimal precision and rounding rules for display vs submission.
- [ ] Supplier cost tiers optimistic locking: confirm ETag/version/`updatedAt` usage, required request fields/headers, and 409 error shape.

- [ ] Domain ownership: confirm whether “Mechanic executes picking (scan + confirm)” is `domain:workexec` vs `domain:inventory`, and provide the owning contract/decision for pick tasks/lines.
- [ ] Picking contract: confirm endpoints/payloads for load task/lines, resolve scan, confirm pick line, complete task; include deterministic error schema and locking/idempotency mechanism.
- [ ] Picking routing: confirm canonical route parameter (`pickTaskId` vs `workOrderId`) and deep-link conventions.
- [ ] Picking scan semantics: confirm what can be scanned (product barcode, location/bin barcode, both), expected sequence, and matching behavior.
- [ ] Picking multi-match: confirm disambiguation rules when a scan matches multiple lines (location, lot, work step, etc.).
- [ ] Picking quantity policy: confirm partial picks, completion with remaining qty, and over-pick rules (UI must not hardcode without contract).
- [ ] Picking serial/lot control: confirm whether serial/lot capture is required at pick time and required fields/UI.
- [ ] Picking permissions: confirm permission strings for view/confirm/complete and how frontend discovers permissions (claims endpoint vs backend 403 only).
- [ ] Picking state model: confirm canonical task/line statuses and allowed transitions (or provide backend `can*` flags).

- [ ] Consume picked items: confirm authoritative route/screen naming and placement for workorder subflows in `durion-moqui-frontend`.
- [ ] Consume picked items contract: confirm Moqui proxy endpoint to load picked items and system-of-record (WorkExec vs Inventory).
- [ ] Consume picked items contract: confirm Moqui proxy endpoint to consume/issue picked items and whether it posts ledger entries immediately (and what identifiers are returned).
- [ ] Consume picked items permissions: confirm permission strings for viewing the consume screen/action and submitting consumption.
- [ ] Consume picked items precision: confirm whether quantities can be fractional and, if so, per-line precision/step metadata to enforce.
- [ ] Consume picked items success identifiers: confirm which identifiers are safe and expected to display (consumptionId, transaction reference, ledgerEntryIds).
- [ ] Consume picked items conflict behavior: confirm whether UI should preserve user-entered quantities after a 409 reload and the deterministic rule to do so safely.

- [ ] Return-to-stock contract: confirm Moqui proxy endpoints and payload shapes for loading returnable items (including stable line IDs), listing reason codes, and submitting return-to-stock (including `inventoryReturnId`).
- [ ] Return-to-stock destination semantics: confirm whether destination is immutable default vs user-selectable; whether `storageLocationId` is supported/required.
- [ ] Return-to-stock permissions: confirm canonical permission strings for viewing/using the return action and submitting a return.
- [ ] Return-to-stock post-success navigation: confirm whether to return to Work Order detail with banner vs dedicated confirmation/details screen.
- [ ] Return-to-stock idempotency: confirm whether submit supports idempotency keys, required header/field name, and retry guidance after timeout.

- [ ] Cycle count planning contracts: confirm Moqui proxy endpoints and schemas for list zones by location, create plan, list plans (pagination shape), and get plan by id (if PlanDetail is required).
- [ ] Cycle count planning permissions: confirm canonical permission strings for viewing planning screens and creating a plan (must follow DECISION-INVENTORY-010 convention).
- [ ] Cycle count planning field naming/limits: confirm whether optional field is `planName`, `description`, or both; confirm max lengths and allowed characters.
- [ ] Cycle count planning scheduled date rule: confirm whether “today” is allowed vs strictly future.
- [ ] Cycle count planning timezone: confirm whether “past” is evaluated in user timezone, site/location timezone, or UTC.
- [ ] Cycle count planning empty zones/items behavior: confirm whether creation is allowed when zones contain no items and whether backend returns a warning/flag/status.
- [ ] Cycle count planning post-create navigation: confirm whether PlanDetail is required and, if so, minimum fields and endpoint.

- [ ] UOM conversions domain ownership: confirm whether UOM/UomConversion is owned by Product vs Inventory and update `domain:*` label accordingly.
- [ ] UOM conversions contract: confirm Moqui paths/service names for listing UOMs, querying conversions (including pagination shape), create/update/deactivate, and audit log retrieval (if supported).
- [ ] UOM conversions permissions: confirm permission strings for view, create/update, deactivate, and audit viewing (if separately gated).
- [ ] UOM conversions scope: confirm whether conversions are global or product-specific (and required product identifier/picker if product-specific).
- [ ] UOM conversions edit constraints: confirm whether `fromUomId`/`toUomId` are immutable after creation and required behavior if changes are needed.
- [ ] UOM conversions re-activation: confirm whether re-activating inactive conversions is supported and the endpoint/transition.

## End

End of document.
