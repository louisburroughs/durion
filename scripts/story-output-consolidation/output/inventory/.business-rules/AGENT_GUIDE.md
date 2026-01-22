# AGENT_GUIDE.md

## Summary

This guide defines the normative business rules and frontend-facing contracts for the Inventory domain in Durion.
It standardizes decision identifiers as `DECISION-INVENTORY-###`, resolves all previously listed open questions, and aligns the Story Validation Checklist with these decisions.
Use this document for implementation and CI validation; use `DOMAIN_NOTES.md` for rationale and audit-facing explanations.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `DOMAIN_NOTES.md`
- [x] Reconciled todos from original `AGENT_GUIDE.md`

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-INVENTORY-001 | Canonical location model (`LocationRef` site; `StorageLocation` bin) |
| DECISION-INVENTORY-002 | Integration pattern: Moqui proxy (no direct Vue → inventory backend) |
| DECISION-INVENTORY-003 | API envelopes & deterministic error schema (plain JSON) |
| DECISION-INVENTORY-004 | Availability contract (inputs/outputs, deep-linking, success-with-zeros) |
| DECISION-INVENTORY-005 | Ledger contract (filters, OR-location semantics, cursor pagination, immutability) |
| DECISION-INVENTORY-006 | Adjustments v1 scope (create-only; immediate posting) |
| DECISION-INVENTORY-007 | StorageLocation CRUD + deactivation (destination-required semantics) |
| DECISION-INVENTORY-008 | HR sync contracts: LocationRef + SyncLog + manual sync trigger |
| DECISION-INVENTORY-009 | Inactive/Pending location selection rules (movement blocking) |
| DECISION-INVENTORY-010 | Permission naming convention (canonical strings) |
| DECISION-INVENTORY-011 | Sensitive data & logging policy (quantities/payloads) |
| DECISION-INVENTORY-012 | Correlation/request ID convention + 401/403 UI behavior |
| DECISION-INVENTORY-013 | Availability feed ops ownership & contracts (runs/normalized/unmapped/exceptions) |
| DECISION-INVENTORY-014 | Deep-link parameter names & routing conventions |
| DECISION-INVENTORY-015 | JSON field handling & safe rendering (tags/capacity/temperature/payload) |
| DECISION-INVENTORY-016 | Allocation/reservation integration for ATP (frontend remains inventory-backend authoritative) |

## Domain Boundaries

### What Inventory owns (system of record)

- Physical inventory movement record (append-only ledger)
- Computed availability views (on-hand, allocated, ATP) as returned by inventory services
- Storage topology within a business location (bins)
- Inventory-side operational read models for:
  - HR location reference ingestion outcomes (`SyncLog`)
  - Manufacturer availability feed ingestion outcomes (runs, normalized outputs, unmapped parts, exceptions)

### What Inventory does *not* own

- Product master data (SKU name/description/base UOM definition) — Product domain
- Work order lifecycle and scheduling — Work Execution domain
- Pricing/quoting/customer master data — Pricing/CRM domains
- HR as the upstream system of record for business location lifecycle (Inventory stores a read model as `LocationRef`)

### CLARIFY: Stories that appear outside Inventory boundaries

The consolidated frontend stories introduce several capabilities that are **not defined** by the current Inventory domain decisions and may belong to other domains. Until ownership is confirmed, treat these as **integration consumers** of Inventory (or as mis-labeled stories):

- Supplier/Vendor cost tiers (likely Pricing/Procurement/Costing) — see Open Questions.
- Picking task lifecycle (pick tasks/lines, scan resolution, completion) — likely Work Execution/Fulfillment.
- UOM conversions and Product Master screens — likely Product/Master Data.
- Inventory Security role matrix/admin UI — likely Security domain.

**Implementation guidance:** do not add new Inventory decisions or endpoints for these areas without an explicit domain ownership decision. Use `TODO`/`CLARIFY` and rely on backend contracts once provided.

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| LocationRef | HR-synced business site/shop/mobile unit. Key: `locationId` (UUIDv7). |
| StorageLocation | Inventory-managed bin within a `LocationRef`. Key: `storageLocationId` (UUIDv7). |
| AvailabilityView | Read-only availability response for `(productSku, locationId, locationId, storageLocationId?)`. |
| InventoryLedgerEntry | Append-only movement record; never edited/deleted. |
| Adjustment | A manual correction command that posts a ledger entry immediately in v1. |
| SyncLog | Immutable log of HR sync ingestion outcomes (with payload visibility gated). |
| FeedRun | Ingestion run record for manufacturer availability feeds. |
| UnmappedPart | Unmapped manufacturer part record with triage status. |
| FeedException | Exception queue record for feed processing issues with operator notes. |

### CLARIFY: Additional conceptual entities referenced by consolidated stories

These entities are referenced by new stories but are **not** currently owned/defined by Inventory decisions. They are listed here to help integrators reason about relationships and contracts, not as Inventory-owned SoR entities:

- `WorkOrder`, `WorkOrderLineId` (Work Execution SoR)
- `PickTask`, `PickLine`, `PickedItem` (Fulfillment/Work Execution SoR unless explicitly moved)
- `CycleCountPlan`, `Zone` (Counts capability; ownership TBD—could be Inventory subdomain or separate Counts domain)
- `InventoryReturn` / return-to-stock transaction (Inventory movement command; contract TBD)
- `SupplierItemCost`, `CostTier` (Pricing/Procurement SoR likely; ownership TBD)
- `UomConversion`, `UnitOfMeasure` (Product/Master Data SoR likely; ownership TBD)

## Invariants / Business Rules

- Ledger is append-only; corrections are new ledger entries, never edits. (DECISION-INVENTORY-005)
- Availability is backend-authoritative; the frontend must not compute on-hand/ATP. (DECISION-INVENTORY-004)
- “No inventory history” is a successful availability response with zeros and a clear empty-state message. (DECISION-INVENTORY-004)
- Location aggregation: `locationId` aggregates across bins; `storageLocationId` scopes to a bin. (DECISION-INVENTORY-004)
- INACTIVE/PENDING `LocationRef` must be blocked for new movement flows; historical display is read-only. (DECISION-INVENTORY-009)
- All list screens use cursor pagination: `pageSize`, `pageToken`, `nextPageToken`. (DECISION-INVENTORY-003)
- Inventory quantities and raw payload blobs are sensitive-by-default; do not log them. (DECISION-INVENTORY-011)
- All JSON fields are rendered safely (escaped, truncated in list views, explicit expand/copy). (DECISION-INVENTORY-015)

### Movement-flow guardrails (applies to new fulfillment stories)

The consolidated stories add/expand movement-like flows (consume/issue, return-to-stock, receiving, putaway, etc.). The following are **normative** because they are already implied by existing decisions:

- Any screen that creates a new inventory movement MUST:
  - use pickers for `locationId`/`storageLocationId` (no free-text UUID entry) (DECISION-INVENTORY-001)
  - block INACTIVE/PENDING `LocationRef` selection for destination/source in new movement flows (DECISION-INVENTORY-009)
  - treat backend as authoritative for movement validation; surface deterministic errors (DECISION-INVENTORY-003)
  - avoid logging quantities/payloads in client logs/telemetry (DECISION-INVENTORY-011)
  - propagate and display `X-Correlation-Id` on errors (DECISION-INVENTORY-012)

**CLARIFY:** For Work Order–driven flows (consume/return/pick), the “destination/source” location semantics are not yet contractually defined. Do not invent defaults; require backend to provide either:

- an immutable default destination/source, or
- explicit flags indicating whether override is allowed, plus picker requirements.

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-INVENTORY-001 | Stable site/bin model: `locationId` + `storageLocationId`. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-001---canonical-location-model) |
| DECISION-INVENTORY-002 | Vue calls Moqui proxy, not backend directly. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-002---moqui-proxy-integration-pattern) |
| DECISION-INVENTORY-003 | Plain JSON + deterministic errors + cursor paging. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-003---plain-json-and-error-schema) |
| DECISION-INVENTORY-004 | Availability contract + deep-linking + success-with-zeros. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-004---availability-contract-and-deep-linking) |
| DECISION-INVENTORY-005 | Ledger contract: OR-location semantics + immutability. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-005---ledger-contract-and-pagination) |
| DECISION-INVENTORY-006 | Adjustments are create-only and post immediately in v1. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-006---adjustments-v1-scope) |
| DECISION-INVENTORY-007 | StorageLocation CRUD + destination-required deactivation. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-007---storagelocation-crud-and-deactivation) |
| DECISION-INVENTORY-008 | HR sync read models + manual sync trigger. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-008---hr-sync-contracts-and-sync-now) |
| DECISION-INVENTORY-009 | Block inactive/pending locations for new movements. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-009---inactivepending-location-blocking) |
| DECISION-INVENTORY-010 | Canonical permission strings for UI gating. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-010---permission-naming-convention) |
| DECISION-INVENTORY-011 | Treat quantities/payloads as sensitive; no logging. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-011---sensitive-data-and-logging) |
| DECISION-INVENTORY-012 | Use `X-Correlation-Id` + consistent 401/403 behavior. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-012---correlation-id-and-auth-ui-behavior) |
| DECISION-INVENTORY-013 | Feed ops read models and allowed updates. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-013---availability-feed-ops-ownership) |
| DECISION-INVENTORY-014 | Deep-link param names are canonical and stable. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-014---deep-link-parameter-names) |
| DECISION-INVENTORY-015 | JSON fields render safely; no XSS; no localStorage persistence. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-015---json-field-safe-rendering) |
| DECISION-INVENTORY-016 | Allocations are surfaced by inventory services; frontend never calls reservation services for ATP. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-016---allocationreservation-and-atp) |

## Open Questions (from source)

### Q: What is the exact backend endpoint path and auth mechanism the Moqui frontend should call for availability, and what is the expected request/response envelope (plain JSON vs wrapped `{data: ...}`)?

- Answer: Use the Moqui proxy `GET /inventory/availability` with a plain JSON response (no `{data: ...}` envelope). Auth uses the existing Moqui/POS session and is not re-implemented in Vue.
- Assumptions:
  - The inventory proxy endpoints are same-origin to the UI runtime.
- Rationale:
  - Centralizes auth, error mapping, and correlation propagation in Moqui.
- Impact:
  - UI calls Moqui endpoints only; tests should validate “plain JSON” parsing.
- Decision ID: DECISION-INVENTORY-002

### Q: Does the frontend already have existing pickers for `locationId` and `storageLocationId`? If not, should this story use free-text UUID entry or include minimal lookup UI?

- Answer: Inventory screens must use pickers: `LocationRef` picker for `locationId` and `StorageLocation` picker filtered by `locationId`. Free-text UUID entry is not permitted in v1 user-facing flows.
- Assumptions:
  - Admin/debug tooling (if any) may allow free-text UUIDs explicitly.
- Rationale:
  - Prevents user error and enforces the site/bin topology model.
- Impact:
  - Requires list endpoints to power pickers.
- Decision ID: DECISION-INVENTORY-001

### Q: Should the Availability screen support deep-linking via URL query params officially, and if so what are the canonical parameter names and routing conventions?

- Answer: Yes. Canonical query params are `productSku`, `locationId`, and optional `storageLocationId`. Auto-run occurs once per load when required params are present.
- Assumptions:
  - Routing layer supports reading query params into form state.
- Rationale:
  - Advisors need bookmarkable views in day-to-day operations.
- Impact:
  - Add tests for “auto-run once” and “stale results” behavior.
- Decision ID: DECISION-INVENTORY-014

### Q: Are availability quantities considered sensitive such that frontend logs must avoid logging numeric quantities?

- Answer: Yes. Treat availability quantities as sensitive-by-default; do not log returned quantities.
- Assumptions:
  - Tenant policy may require hashing `productSku` in logs.
- Rationale:
  - Inventory positions can be commercially sensitive; logs are long-lived.
- Impact:
  - Telemetry scrubbing and linting should enforce “no quantity logging”.
- Decision ID: DECISION-INVENTORY-011

### Q: Backend contract for ledger queries: what are the exact endpoints, including query parameter names and pagination response shape?

- Answer: Use `GET /inventory/ledger` with cursor pagination (`pageSize`, `pageToken` → `nextPageToken`) and optional `sort`. Detail is `GET /inventory/ledger/{ledgerEntryId}`.
- Assumptions:
  - Backend enforces max page size (recommend max 200).
- Rationale:
  - Cursor paging scales and avoids inconsistent totals under writes.
- Impact:
  - List screens must not depend on “totalCount”.
- Decision ID: DECISION-INVENTORY-003

### Q: Adjustment UI scope: should the frontend implement full approval workflows or only create adjustments?

- Answer: v1 supports create-only adjustments via `POST /inventory/adjustments`; backend posts immediately. No approve/post state machine UI in v1.
- Assumptions:
  - Governance-heavy approval workflows are deferred.
- Rationale:
  - Keeps v1 operational while preserving auditability via the ledger.
- Impact:
  - Stories for approval workflows must be separate and explicit.
- Decision ID: DECISION-INVENTORY-006

### Q: Location filtering semantics for the ledger: should `locationId` match `fromLocationId OR toLocationId` or only one side?

- Answer: Match `fromLocationId == locationId OR toLocationId == locationId`.
- Assumptions:
  - Movement type filtering is optional and additive.
- Rationale:
  - Matches investigator mental model (“involving this location”).
- Impact:
  - UI help text should clarify semantics.
- Decision ID: DECISION-INVENTORY-005

### Q: WorkExec integration key: what identifier is available for “movement history for a workorder line”?

- Answer: Ledger entries include `sourceTransactionId` and (when applicable) `workOrderId` and `workOrderLineId`. WorkExec should filter by `workOrderLineId` primarily.
- Assumptions:
  - Not all movements originate from WorkExec.
- Rationale:
  - Line-level filtering is the most precise for investigation.
- Impact:
  - Ledger responses should surface these linkage fields when present.
- Decision ID: DECISION-INVENTORY-005

### Q: Storage Location contracts: what are the endpoints for listing sites, listing storage locations, and create/update/deactivate?

- Answer: Use `GET /inventory/locations` (sites), `GET /inventory/storage-locations?locationId=...`, `GET /inventory/storage-locations/{storageLocationId}`, `POST /inventory/storage-locations`, `PUT /inventory/storage-locations/{storageLocationId}`, `POST /inventory/storage-locations/{storageLocationId}/deactivate`. Storage types come from `GET /inventory/meta/storage-types`.
- Assumptions:
  - Storage types are backend-owned enums.
- Rationale:
  - Stable endpoints simplify UI and tests.
- Impact:
  - Picker flows require these endpoints.
- Decision ID: DECISION-INVENTORY-007

### Q: Are `capacity` and `temperature` structured with a defined schema/units or treated as freeform JSON?

- Answer: Treat them as freeform JSON objects; validate JSON syntax only.
- Assumptions:
  - Unit enforcement may be introduced in later versions.
- Rationale:
  - Prevents false precision and avoids locking in an early schema.
- Impact:
  - JSON editor/viewer needed; no UI-side unit conversions.
- Decision ID: DECISION-INVENTORY-015

### Q: Can an Inactive storage location be edited (name/barcode fixes), or must it be fully read-only?

- Answer: It is editable for non-operational metadata only; it remains non-operational and is not reactivated in v1 unless explicitly supported.
- Assumptions:
  - Backend enforces which fields are mutable.
- Rationale:
  - Supports operational cleanup without enabling movement into inactive bins.
- Impact:
  - UI must keep “movement” actions disabled while allowing safe edits.
- Decision ID: DECISION-INVENTORY-007

### Q: Is changing `storageType` allowed after creation, or is it immutable?

- Answer: Immutable after creation (create-only).
- Assumptions:
  - Backends reject changes with a deterministic error.
- Rationale:
  - Prevents semantic drift and SOP confusion.
- Impact:
  - Edit forms must disable or hide the `storageType` field.
- Decision ID: DECISION-INVENTORY-007

### Q: Should parent selection exclude Inactive locations strictly, or allow choosing an inactive parent for historical organization?

- Answer: Exclude inactive by default; optionally allow “include inactive parents” for historical organization.
- Assumptions:
  - Operational pickers still exclude inactive.
- Rationale:
  - Balances operational safety with information architecture needs.
- Impact:
  - Parent picker may include a toggle.
- Decision ID: DECISION-INVENTORY-007

### Q: What are the endpoints for LocationRef and SyncLog list/detail?

- Answer: `GET /inventory/locations`, `GET /inventory/locations/{locationId}`, `GET /inventory/sync-logs`, `GET /inventory/sync-logs/{syncLogId}`.
- Assumptions:
  - SyncLog detail includes payload only if permitted.
- Rationale:
  - Makes HR ingestion auditable without exposing HR internals broadly.
- Impact:
  - UI must handle 403 payload gating.
- Decision ID: DECISION-INVENTORY-008

### Q: What permission(s) gate access to Topology screens and SyncLog payload visibility?

- Answer: `inventory:topology:view` gates list/detail; `inventory:topology:payload:view` gates payload viewing; `inventory:topology:sync:trigger` gates manual sync.
- Assumptions:
  - Backend remains authoritative on enforcement.
- Rationale:
  - Least privilege and safe ops/auditor separation.
- Impact:
  - UI hides actions and handles 403 gracefully.
- Decision ID: DECISION-INVENTORY-010

### Q: Does the backend support a manual “Sync now” trigger? If yes, what inputs/outputs and who is authorized?

- Answer: Yes. `POST /inventory/locations/sync` returns `{ "syncRunId": "uuidv7" }` and is restricted to `inventory:topology:sync:trigger`.
- Assumptions:
  - Trigger is async; results appear in SyncLog.
- Rationale:
  - Ops needs a backstop when event-driven ingestion lags.
- Impact:
  - UI must be click-safe (no repeated submits) and provide refresh affordance.
- Decision ID: DECISION-INVENTORY-008

### Q: What is the authoritative list of “new stock movement” screens that must block inactive locations?

- Answer: Goods Receipt, Inventory Transfer, Inventory Adjustment, WorkExec Issue/Consume parts, Return-to-stock.
- Assumptions:
  - New movement flows added later must adopt the same guardrail.
- Rationale:
  - Prevents regressions when new flows are introduced.
- Impact:
  - Shared location-picker guard should be reused.
- Decision ID: DECISION-INVENTORY-009

### Q: If `LocationRef.status=PENDING` exists, should PENDING locations be selectable for new movements or treated as inactive until ACTIVE?

- Answer: Treat `PENDING` like `INACTIVE` for new movements; allow read-only display for historical views.
- Assumptions:
  - Backend will reject movement attempts with a deterministic error (e.g., 422).
- Rationale:
  - Avoids misrouting inventory into not-yet-onboarded locations.
- Impact:
  - UI must block selection and surface backend 422 clearly.
- Decision ID: DECISION-INVENTORY-009

### Q: Tags display expectations: should tags be treated as opaque JSON or are there known keys requiring friendly rendering/filtering?

- Answer: Treat tags as opaque JSON. Optionally render tag chips only when tags is clearly a string array; do not hardcode keys.
- Assumptions:
  - Backend may evolve tag shapes.
- Rationale:
  - Avoids UI coupling to incidental metadata.
- Impact:
  - JSON viewer must be safe and performant.
- Decision ID: DECISION-INVENTORY-015

### Q: Ingestion monitoring (manufacturer feed normalization): what are the exact endpoints for runs/normalized/unmapped/exceptions?

- Answer: Use `GET /inventory/availability-feeds/runs`, `/runs/{runId}`, `/normalized`, `/unmapped`, and `/exceptions` plus detail endpoints; updates are via `PATCH /inventory/availability-feeds/unmapped/{unmappedId}` and `PATCH /inventory/availability-feeds/exceptions/{exceptionId}`.
- Assumptions:
  - Lists use cursor pagination and require date range filters for large tenants.
- Rationale:
  - Ops needs a single, auditable place to triage feed health.
- Impact:
  - UI needs optimistic-locking support when version/etag is available.
- Decision ID: DECISION-INVENTORY-013

### Q: Authorization for feed ops: what permission(s) gate access and updates?

- Answer: `inventory:feedops:view` gates read; `inventory:feedops:unmapped:update` gates unmapped status updates; `inventory:feedops:exception:update` gates exception status/notes.
- Assumptions:
  - Backend returns 403 for unauthorized update attempts.
- Rationale:
  - Separates read-only monitoring from corrective actions.
- Impact:
  - UI hides write actions for read-only users.
- Decision ID: DECISION-INVENTORY-010

### Q: ExceptionQueue scope: inventory-specific or shared? What fields are guaranteed?

- Answer: Inventory-scoped. Required fields: `severity`, `status`, `message`, `details` (redacted-safe), `correlationId`, timestamps.
- Assumptions:
  - Details remain safe-to-display by contract; raw payloads remain gated.
- Rationale:
  - Prevents cross-domain coupling and keeps triage actionable.
- Impact:
  - UI can rely on stable fields for list/detail displays.
- Decision ID: DECISION-INVENTORY-013

### Q: Unmapped status update: allow changing UnmappedManufacturerParts.status?

- Answer: Yes. Allowed statuses in v1: `PENDING_REVIEW`, `IGNORED`, `RESOLVED`.
- Assumptions:
  - Backend enforces allowed transitions.
- Rationale:
  - Enables triage workflow without deleting records.
- Impact:
  - UI must handle 409 conflicts if optimistic locking is enabled.
- Decision ID: DECISION-INVENTORY-013

### Q: Retention/TTL UX: Will runs/availability be retained, and should UI support selecting archived ranges?

- Answer: Retain runs and exceptions for at least 90 days; UI must use date range filters and tolerate missing older records.
- Assumptions:
  - Backend may archive or purge older data without client-visible errors.
- Rationale:
  - Prevents “infinite scrolling into history” patterns that harm performance.
- Impact:
  - Add default date ranges and clear empty-state copy.
- Decision ID: DECISION-INVENTORY-013

### Q: Moqui integration pattern: call backend directly from Vue client, or via Moqui proxy services?

- Answer: Use Moqui proxy services; Vue does not call inventory backends directly.
- Assumptions:
  - Moqui proxy handles host/version routing and auth.
- Rationale:
  - Consistent security and operational observability.
- Impact:
  - Network client should be configured for Moqui routes.
- Decision ID: DECISION-INVENTORY-002

### Q: Auth/correlation: what is the convention for correlation IDs and for handling 401 vs 403 in UI?

- Answer: Use request/response header `X-Correlation-Id`. 401 redirects to login/session refresh; 403 renders a forbidden state without leaking data.
- Assumptions:
  - Backend echoes `X-Correlation-Id` on errors.
- Rationale:
  - Support can trace failures without exposing stack traces.
- Impact:
  - Error UI should show correlation ID in “Technical details”.
- Decision ID: DECISION-INVENTORY-012

### Q: SLA requirement detail: is there a frontend SLA expectation to monitor/enforce?

- Answer: UX targets: show loading indicator within 100ms; surface a timeout at 8 seconds with a retry affordance; do not auto-refresh loops.
- Assumptions:
  - Backend performance targets are owned by service SLOs.
- Rationale:
  - Prevents “stuck spinner” UX and supports poor connectivity.
- Impact:
  - Add a client timeout handler and tests for timeout state.
- Decision ID: DECISION-INVENTORY-004

## Open Questions from Frontend Stories

This section tracks **new** open questions introduced by consolidated frontend stories. These are not resolved by existing Inventory decisions and must be clarified before implementation. Use `TODO`/`CLARIFY` tags to block unsafe assumptions.

### Domain boundaries & ownership (blocking)

1. **CLARIFY (Supplier cost tiers ownership):** Supplier/vendor cost tiers appear to be Pricing/Procurement/Costing rather than Inventory. Should story #260 remain `domain:inventory` or be reassigned (e.g., `domain:pricing`)? If it remains in Inventory, provide the Inventory-owned backend contract and SoR statement.
2. **CLARIFY (Picking workflow ownership):** Stories #244/#92 describe pick tasks/lines and a fulfillment state machine. Is this owned by `domain:workexec` (recommended) or Inventory? If Inventory owns it, provide the decision/contract defining pick task/line states and transitions.
3. **CLARIFY (Consume/issue ownership split):** For story #243, which system is SoR for:
   - the “picked items for workorder” list (WorkExec vs Inventory read model)?
   - the “consume/issue” command (Inventory movement command vs WorkExec transition)?
4. **CLARIFY (Return-to-stock ownership split):** For story #242, which system is SoR for:
   - returnable items/max-returnable quantities (WorkExec vs Inventory)?
   - return reason codes (Inventory vs shared lookup vs WorkExec)?
5. **CLARIFY (Counts ownership):** For story #241 (cycle count planning), is this an Inventory subdomain (“Counts”) or a separate domain/service? Confirm owning backend and endpoint namespace.
6. **CLARIFY (UOM conversions ownership):** For story #120, confirm SoR domain for `UnitOfMeasure` and `UomConversion` (Product/Master Data vs Inventory). Inventory boundaries currently exclude product master data.
7. **CLARIFY (Security admin UI ownership):** For story #87, confirm whether this is `domain:security` or an Inventory-routed UI backed by Security services.

### API contracts & identifiers (blocking)

1. **TODO (Supplier cost tiers endpoints):** Provide Moqui proxy endpoints, request/response schemas, and identifiers for list/detail/create/update/delete. Confirm whether update is `PUT` full replace vs `PATCH`.
2. **TODO (Supplier cost tiers identifiers):** Confirm canonical item identifier for cost tiers: `itemId` vs `productId` vs `productSku`. Inventory deep-link conventions use `productSku` (DECISION-INVENTORY-014) but that does not automatically apply to other domains.
3. **TODO (Supplier cost tiers currency rules):** Is `currencyCode` derived from supplier and read-only, or overrideable per supplier-item cost?
4. **TODO (Supplier cost tiers baseCost semantics):** Is `baseCost` supported? If yes, can it exist without tiers, and is it a fallback when tiers exist?
5. **TODO (Supplier cost tiers precision/rounding):** Required precision for `unitCost`/`baseCost` and rounding rules for display vs submission.
6. **TODO (Supplier cost tiers optimistic locking):** Is there ETag/version/`updatedAt` concurrency control? Confirm 409 error shape and required request headers/fields.

7. **TODO (Picking endpoints):** Provide exact endpoints and payloads for:
    - loading pick task/lines
    - resolving a scan
    - confirming a pick line
    - completing the pick task
    Include deterministic error schema and idempotency/locking mechanism.
8. **TODO (Picking route identifier):** Should the UI route by `workOrderId` or `pickTaskId`? If both, define canonical route and deep-link params.
9. **TODO (Picking scan semantics):** What can be scanned (product barcode, bin barcode, both)? If both, define sequence and matching behavior.
10. **TODO (Picking multi-match disambiguation):** If scan matches multiple lines, what disambiguation key is required (location, lot/serial, work step)?
11. **TODO (Picking quantity rules):** Are partial picks allowed? Is completion allowed with remaining qty? Are over-picks ever allowed?
12. **TODO (Picking serial/lot control):** Are items serialized/lot-controlled? If yes, what additional capture is required at pick time?
13. **TODO (Picking permissions):** Permission strings for view/confirm/complete, and how frontend discovers permissions (claims endpoint vs backend flags vs rely on 403).

14. **TODO (Consume picked items endpoints):** Provide Moqui proxy endpoint to load picked items for a workorder and endpoint to submit consume/issue. Confirm request field casing and response identifiers safe to display.
15. **TODO (Consume quantity precision):** Are quantities integer-only or decimal? If decimal, what per-line precision/step should UI enforce (if any)?
16. **TODO (Consume success identifiers):** On success, what identifiers should be displayed (consumptionId, ledgerEntryIds, workorder transaction ref) and which are safe to display?
17. **TODO (Consume 409 reload behavior):** Should UI preserve user-entered quantities after reload? If yes, define deterministic rule to avoid accidental over-issue.

18. **TODO (Return-to-stock endpoints):** Provide Moqui proxy endpoints and payload shapes for:
    - loading returnable items (with stable line identifiers)
    - listing return reason codes
    - submitting return-to-stock and receiving `inventoryReturnId`
    - deterministic error schema details for line-level mapping
19. **TODO (Return destination semantics):** Is destination always the work order site `locationId` (immutable) or user-selectable? Is `storageLocationId` supported/required?
20. **TODO (Return permissions):** Canonical permission strings for viewing/using return screen and submitting return.
21. **TODO (Return post-success navigation):** Return to Work Order detail with banner vs dedicated confirmation screen.
22. **TODO (Return idempotency):** Does submit support idempotency keys? If yes, header/field name and retry guidance after timeout.

23. **TODO (Counts planning endpoints):** Provide Moqui proxy endpoints and schemas for:
    - list zones by location
    - create cycle count plan
    - list plans (cursor pagination shape)
    - get plan by id (if PlanDetail required)
24. **TODO (Counts permissions):** Canonical permission strings for viewing planning screens and creating plans (must follow DECISION-INVENTORY-010).
25. **TODO (Counts field naming/limits):** Is optional field `planName`, `description`, or both? Max lengths and allowed characters.
26. **TODO (Counts scheduled date rule):** Is “today” allowed or must be strictly future?
27. **TODO (Counts timezone rule):** Is “past” evaluated in user timezone, site timezone, or UTC?
28. **TODO (Counts empty zones/items behavior):** If selected zones contain no items, is creation allowed? Any warning/status flag returned?
29. **TODO (Counts post-create navigation):** Navigate to PlanDetail vs back to list only.

### Integration patterns, events, and idempotency (blocking/clarify)

1. **CLARIFY (Idempotency for movement commands):** For movement-like POSTs (receive+issue, consume/issue, return-to-stock, putaway complete), confirm whether the backend supports idempotency keys and the canonical header/field name. If not supported, UI must not auto-retry mutations after timeout (already required by DECISION-INVENTORY-004 UX target).
2. **CLARIFY (Event emission visibility):** For cross-domain flows (WorkExec ↔ Inventory), confirm whether responses include any “event queued/published” status fields. If absent, UI should treat eventing as async and not display speculative statuses.

## Todos Reconciled

- Original todo: “Treat `siteId` vs `locationId` ambiguity” → Resolution: Resolved; use `locationId` and treat `siteId` as legacy synonym (DECISION-INVENTORY-001)
- Original todo: “Allocation/Reservation service treated as external dependency unless confirmed otherwise” → Resolution: Resolved; allocated quantities are surfaced by inventory availability responses; frontend never calls allocation services directly (DECISION-INVENTORY-016)
- Original todo: “Open Questions to Resolve” section in checklist → Resolution: Replaced with acceptance criteria blocks in `STORY_VALIDATION_CHECKLIST.md`

## End

End of document.
