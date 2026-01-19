# AGENT_GUIDE.md

## Summary

This guide defines the normative business rules and frontend-facing contracts for the Inventory domain in Durion.
It standardizes decision identifiers as `DECISION-INVENTORY-###`, resolves all previously listed open questions, and aligns the Story Validation Checklist with these decisions.
Use this document for implementation and CI validation; use `INVENTORY_DOMAIN_NOTES.md` for rationale and audit-facing explanations.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `INVENTORY_DOMAIN_NOTES.md`
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

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| LocationRef | HR-synced business site/shop/mobile unit. Key: `locationId` (UUIDv7). |
| StorageLocation | Inventory-managed bin within a `LocationRef`. Key: `storageLocationId` (UUIDv7). |
| AvailabilityView | Read-only availability response for `(productSku, locationId, storageLocationId?)`. |
| InventoryLedgerEntry | Append-only movement record; never edited/deleted. |
| Adjustment | A manual correction command that posts a ledger entry immediately in v1. |
| SyncLog | Immutable log of HR sync ingestion outcomes (with payload visibility gated). |
| FeedRun | Ingestion run record for manufacturer availability feeds. |
| UnmappedPart | Unmapped manufacturer part record with triage status. |
| FeedException | Exception queue record for feed processing issues with operator notes. |

## Invariants / Business Rules

- Ledger is append-only; corrections are new ledger entries, never edits. (DECISION-INVENTORY-005)
- Availability is backend-authoritative; the frontend must not compute on-hand/ATP. (DECISION-INVENTORY-004)
- “No inventory history” is a successful availability response with zeros and a clear empty-state message. (DECISION-INVENTORY-004)
- Location aggregation: `locationId` aggregates across bins; `storageLocationId` scopes to a bin. (DECISION-INVENTORY-004)
- INACTIVE/PENDING `LocationRef` must be blocked for new movement flows; historical display is read-only. (DECISION-INVENTORY-009)
- All list screens use cursor pagination: `pageSize`, `pageToken`, `nextPageToken`. (DECISION-INVENTORY-003)
- Inventory quantities and raw payload blobs are sensitive-by-default; do not log them. (DECISION-INVENTORY-011)
- All JSON fields are rendered safely (escaped, truncated in list views, explicit expand/copy). (DECISION-INVENTORY-015)

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-INVENTORY-001 | Stable site/bin model: `locationId` + `storageLocationId`. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-001---canonical-location-model) |
| DECISION-INVENTORY-002 | Vue calls Moqui proxy, not backend directly. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-002---moqui-proxy-integration-pattern) |
| DECISION-INVENTORY-003 | Plain JSON + deterministic errors + cursor paging. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-003---plain-json-and-error-schema) |
| DECISION-INVENTORY-004 | Availability contract + deep-linking + success-with-zeros. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-004---availability-contract-and-deep-linking) |
| DECISION-INVENTORY-005 | Ledger contract: OR-location semantics + immutability. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-005---ledger-contract-and-pagination) |
| DECISION-INVENTORY-006 | Adjustments are create-only and post immediately in v1. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-006---adjustments-v1-scope) |
| DECISION-INVENTORY-007 | StorageLocation CRUD + destination-required deactivation. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-007---storagelocation-crud-and-deactivation) |
| DECISION-INVENTORY-008 | HR sync read models + manual sync trigger. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-008---hr-sync-contracts-and-sync-now) |
| DECISION-INVENTORY-009 | Block inactive/pending locations for new movements. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-009---inactivepending-location-blocking) |
| DECISION-INVENTORY-010 | Canonical permission strings for UI gating. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-010---permission-naming-convention) |
| DECISION-INVENTORY-011 | Treat quantities/payloads as sensitive; no logging. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-011---sensitive-data-and-logging) |
| DECISION-INVENTORY-012 | Use `X-Correlation-Id` + consistent 401/403 behavior. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-012---correlation-id-and-auth-ui-behavior) |
| DECISION-INVENTORY-013 | Feed ops read models and allowed updates. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-013---availability-feed-ops-ownership) |
| DECISION-INVENTORY-014 | Deep-link param names are canonical and stable. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-014---deep-link-parameter-names) |
| DECISION-INVENTORY-015 | JSON fields render safely; no XSS; no localStorage persistence. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-015---json-field-safe-rendering) |
| DECISION-INVENTORY-016 | Allocations are surfaced by inventory services; frontend never calls reservation services for ATP. | [INVENTORY_DOMAIN_NOTES.md](INVENTORY_DOMAIN_NOTES.md#decision-inventory-016---allocationreservation-and-atp) |

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

## Todos Reconciled

- Original todo: “Treat `siteId` vs `locationId` ambiguity” → Resolution: Resolved; use `locationId` and treat `siteId` as legacy synonym (DECISION-INVENTORY-001)
- Original todo: “Allocation/Reservation service treated as external dependency unless confirmed otherwise” → Resolution: Resolved; allocated quantities are surfaced by inventory availability responses; frontend never calls allocation services directly (DECISION-INVENTORY-016)
- Original todo: “Open Questions to Resolve” section in checklist → Resolution: Replaced with acceptance criteria blocks in `STORY_VALIDATION_CHECKLIST.md`

## End

End of document.
