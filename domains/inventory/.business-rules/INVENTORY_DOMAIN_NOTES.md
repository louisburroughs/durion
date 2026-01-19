# INVENTORY_DOMAIN_NOTES.md

## Summary

This document records the rationale, tradeoffs, and audit guidance behind the Inventory domain’s normative rules in `AGENT_GUIDE.md`.
It is intentionally non-normative and may include alternatives that are not implemented.
Use this for architecture review, governance discussions, and auditor support; do not treat it as executable policy.

## Completed items

- [x] Linked each Decision ID to a detailed rationale

## Decision details

<a id="decision-inventory-001---canonical-location-model"></a>
### DECISION-INVENTORY-001 — Canonical location model

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Inventory UI and APIs use `locationId` for the business site (`LocationRef`) and `storageLocationId` for bins (`StorageLocation`). Any legacy `siteId` references are treated as synonyms of `locationId`.
- Alternatives considered:
	- Option A: Continue exposing both `siteId` and `locationId`: pros (no migration), cons (permanent confusion and bugs).
	- Option B: Collapse bins into sites only: pros (simpler UI), cons (loses operational topology).
- Reasoning and evidence:
	- Mixing site vs bin causes incorrect picks/transfers and breaks audit narratives. A two-level model is both operationally correct and easy to enforce with pickers.
- Architectural implications:
	- Components affected: pickers, routing params, storage location endpoints.
	- Data shape: `StorageLocation.locationId` must always match the selected `LocationRef`.
- Auditor-facing explanation:
	- Inspect `InventoryLedgerEntry` for both `locationId` and `storageLocationId` to trace movements at the site and bin level.
- Migration & backward-compatibility notes:
	- Map `siteId → locationId` in adapters; reject new uses of `siteId` in UI contracts.
- Governance & owner recommendations:
	- Owner: Inventory domain. Changes require review by Inventory + Location domain reps.

<a id="decision-inventory-002---moqui-proxy-integration-pattern"></a>
### DECISION-INVENTORY-002 — Moqui proxy integration pattern

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: The UI calls Moqui proxy endpoints/services; it does not call inventory backends directly.
- Alternatives considered:
	- Option A: Direct Vue → backend: pros (lower latency), cons (auth fragmentation, CORS, duplicated error mapping).
	- Option B: Mixed per-screen: pros (tactical), cons (operational inconsistency).
- Reasoning and evidence:
	- Moqui is the stable integration point for session/auth, correlation propagation, and centralized contract evolution.
- Architectural implications:
	- Moqui becomes the single choke point for inventory authn/authz enforcement and mapping backend errors to the UI.
- Auditor-facing explanation:
	- Access control and request logging can be inspected centrally at Moqui boundaries.
- Migration & backward-compatibility notes:
	- If any direct calls exist, they should be migrated behind the proxy before expanding inventory UI scope.
- Governance & owner recommendations:
	- Owner: Platform integration (Moqui) + Inventory.

<a id="decision-inventory-003---plain-json-and-error-schema"></a>
### DECISION-INVENTORY-003 — Plain JSON and error schema

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Inventory endpoints return plain JSON and a deterministic error schema; list endpoints use cursor paging (`pageToken`/`nextPageToken`).
- Alternatives considered:
	- Option A: Global `{data, meta}` envelope: pros (consistency), cons (adapter drift and double parsing).
	- Option B: Offset pagination with total count: pros (simple), cons (expensive totals and race conditions).
- Reasoning and evidence:
	- Cursor paging avoids large totals and performs well under continuous writes (ledger/feed ops).
- Architectural implications:
	- UI cannot rely on `totalCount`; it must render incremental pages.
- Auditor-facing explanation:
	- Deterministic error codes + correlation IDs provide traceability without exposing stack traces.
- Migration & backward-compatibility notes:
	- If an envelope is adopted later, implement it in a single adapter.
- Governance & owner recommendations:
	- Owner: Inventory service + Moqui proxy.

<a id="decision-inventory-004---availability-contract-and-deep-linking"></a>
### DECISION-INVENTORY-004 — Availability contract and deep-linking

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Availability is queried via `GET /inventory/availability` with `productSku`, `locationId`, and optional `storageLocationId`. “No history” is success-with-zeros. Deep-linking via canonical query params is supported and auto-runs once per load.
- Alternatives considered:
	- Option A: Treat “no history” as 404: pros (signals missing), cons (false alarms and bad UX).
	- Option B: Auto-refresh continuously: pros (live data), cons (load loops and cost).
- Reasoning and evidence:
	- Operational users need predictable empty states and bookmarkable links.
- Architectural implications:
	- Routing must be stable: `productSku`, `locationId`, `storageLocationId`.
- Auditor-facing explanation:
	- Availability is a derived view; auditors should trace back to ledger entries for provenance.
- Migration & backward-compatibility notes:
	- Keep param names stable once released.
- Governance & owner recommendations:
	- Owner: Inventory domain.

<a id="decision-inventory-005---ledger-contract-and-pagination"></a>
### DECISION-INVENTORY-005 — Ledger contract and pagination

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Ledger endpoints are read-only and immutable. Filtering by `locationId` matches `fromLocationId OR toLocationId`. Cursor pagination is required.
- Alternatives considered:
	- Option A: One-sided location filters: pros (simple), cons (surprising to investigators).
	- Option B: Allow edits/deletes: pros ("fix mistakes"), cons (breaks audit trails).
- Reasoning and evidence:
	- Investigations require “involving this location” semantics and immutability to preserve provenance.
- Architectural implications:
	- UI must expose filter semantics clearly and avoid client-side filtering of unbounded datasets.
- Auditor-facing explanation:
	- Corrections are separate ledger entries; do not expect a mutation history on a single entry.
- Migration & backward-compatibility notes:
	- If schemas evolve, keep stable identifiers and provide compatibility mapping.
- Governance & owner recommendations:
	- Owner: Inventory domain, reviewed by Audit/Observability domain for traceability.

<a id="decision-inventory-006---adjustments-v1-scope"></a>
### DECISION-INVENTORY-006 — Adjustments v1 scope

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Adjustments are create-only in v1 and post immediately (no approval state machine in UI).
- Alternatives considered:
	- Option A: Full approval workflow: pros (separation of duties), cons (large scope and governance complexity).
	- Option B: Silent edits to ledger: pros (fast), cons (non-auditable).
- Reasoning and evidence:
	- Create-only enables operations while deferring governance-heavy approvals to a dedicated story.
- Architectural implications:
	- Backend must provide deterministic validation errors (e.g., inactive location).
- Auditor-facing explanation:
	- Adjustments should be traceable by reason code/notes and the actor ID in the resulting ledger entry.
- Migration & backward-compatibility notes:
	- If approval is added later, version the workflow explicitly.
- Governance & owner recommendations:
	- Owner: Inventory + Security domain for permission gating.

<a id="decision-inventory-007---storagelocation-crud-and-deactivation"></a>
### DECISION-INVENTORY-007 — StorageLocation CRUD and deactivation

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Storage locations can be created/updated, deactivated with destination-required semantics for non-empty bins, and `storageType` is immutable after creation.
- Alternatives considered:
	- Option A: Allow deactivation without destination: pros (simple), cons (ghost inventory).
	- Option B: Allow changing storageType: pros (flexible), cons (breaks SOP consistency).
- Reasoning and evidence:
	- Destination-required deactivation preserves physical integrity. Immutable types preserve semantics.
- Architectural implications:
	- Deactivation endpoint must provide deterministic errors for destination requirements.
- Auditor-facing explanation:
	- Deactivation + transfer destination provides a clear audit narrative for where inventory moved.
- Migration & backward-compatibility notes:
	- Consider adding a controlled reactivation workflow only with explicit audit events.
- Governance & owner recommendations:
	- Owner: Inventory; review by Safety/Compliance if storage types map to SOP controls.

<a id="decision-inventory-008---hr-sync-contracts-and-sync-now"></a>
### DECISION-INVENTORY-008 — HR sync contracts and “Sync now”

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Inventory stores a `LocationRef` read model and immutable `SyncLog` records; it also exposes an admin-only manual sync trigger.
- Alternatives considered:
	- Option A: No manual trigger: pros (simpler), cons (no ops backstop).
	- Option B: Allow all users to trigger sync: pros (self-serve), cons (abuse risk).
- Reasoning and evidence:
	- Operations need visibility and a reconciliation lever when upstream sync is delayed.
- Architectural implications:
	- SyncLog payload must be permission-gated.
- Auditor-facing explanation:
	- SyncLogs show when HR updates were ingested and what decisions were derived.
- Migration & backward-compatibility notes:
	- Keep sync trigger async and idempotent by run ID.
- Governance & owner recommendations:
	- Owner: Location domain (truth) + Inventory (read model) + Security (permissions).

<a id="decision-inventory-009---inactivepending-location-blocking"></a>
### DECISION-INVENTORY-009 — Inactive/Pending location blocking

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: INACTIVE/PENDING `LocationRef` cannot be selected for new movement flows; treat PENDING as INACTIVE for movement eligibility.
- Alternatives considered:
	- Option A: Allow PENDING: pros (faster onboarding), cons (misrouting risk).
	- Option B: Block only in some screens: pros (less work), cons (regression risk).
- Reasoning and evidence:
	- Incomplete onboarding implies missing staffing/configuration; movement should be blocked consistently.
- Architectural implications:
	- Shared location-picker guard should be used across movement flows.
- Auditor-facing explanation:
	- Movement eligibility rules can be audited by looking at LocationRef status at the time of the attempted action.
- Migration & backward-compatibility notes:
	- Add explicit story if the status taxonomy changes.
- Governance & owner recommendations:
	- Owner: Location domain defines statuses; Inventory enforces movement eligibility.

<a id="decision-inventory-010---permission-naming-convention"></a>
### DECISION-INVENTORY-010 — Permission naming convention

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Permissions are stable colon-separated strings (e.g., `inventory:ledger:view`) and the UI gates actions accordingly.
- Alternatives considered:
	- Option A: Screen-only permissions: pros (simple), cons (insufficient for action gating).
	- Option B: Ad-hoc permission names: pros (quick), cons (role design drift).
- Reasoning and evidence:
	- Stable permission taxonomy enables least privilege and consistent UI patterns.
- Architectural implications:
	- UI needs a consistent permission discovery mechanism (existing app convention).
- Auditor-facing explanation:
	- Auditors can map actions to permission strings and validate role assignments.
- Migration & backward-compatibility notes:
	- Add new permissions; do not rename existing permissions without a migration plan.
- Governance & owner recommendations:
	- Owner: Security domain.

<a id="decision-inventory-011---sensitive-data-and-logging"></a>
### DECISION-INVENTORY-011 — Sensitive data and logging

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Availability quantities and raw payload blobs are sensitive-by-default and must not be logged or emitted in client telemetry.
- Alternatives considered:
	- Option A: Log quantities for debugging: pros (easy), cons (data leakage risk).
	- Option B: Disable all logging: pros (safe), cons (hard to support).
- Reasoning and evidence:
	- Inventory levels are commercially sensitive; logs persist and are difficult to redact.
- Architectural implications:
	- Telemetry must record timing/status and correlation IDs only.
- Auditor-facing explanation:
	- Debugging artifacts should rely on correlation IDs and server-side logs, not client quantity logs.
- Migration & backward-compatibility notes:
	- If policies loosen, require explicit governance decision.
- Governance & owner recommendations:
	- Owner: Security + Audit/Observability domains.

<a id="decision-inventory-012---correlation-id-and-auth-ui-behavior"></a>
### DECISION-INVENTORY-012 — Correlation ID and auth UI behavior

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Use `X-Correlation-Id` on requests and responses. UI behavior: 401 triggers login/session refresh; 403 shows forbidden state without data leakage.
- Alternatives considered:
	- Option A: No correlation IDs: pros (less work), cons (hard to support).
	- Option B: Treat 401/403 the same: pros (simpler UI), cons (security and UX issues).
- Reasoning and evidence:
	- Consistent correlation IDs enable cross-service debugging.
- Architectural implications:
	- UI error components should surface correlation IDs in “Technical details”.
- Auditor-facing explanation:
	- Correlation IDs provide traceability without exposing internal stack traces.
- Migration & backward-compatibility notes:
	- If header names change, adapter must support both during migration.
- Governance & owner recommendations:
	- Owner: Platform integration + Security.

<a id="decision-inventory-013---availability-feed-ops-ownership"></a>
### DECISION-INVENTORY-013 — Availability feed ops ownership

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Inventory exposes operational read models for feed runs, normalized availability, unmapped parts, and exception queues; some triage status updates are allowed with permissions.
- Alternatives considered:
	- Option A: Hide feed ops in an integration platform only: pros (separation), cons (ops context missing for inventory users).
	- Option B: Allow delete/overwrite of feed artifacts: pros (cleanup), cons (auditability loss).
- Reasoning and evidence:
	- Inventory is the consumer of availability; it is the natural place for triage screens and run evidence.
- Architectural implications:
	- Endpoints must support pagination and safe payload rendering.
- Auditor-facing explanation:
	- Retain evidence of runs and operator actions (status/notes) to support audits.
- Migration & backward-compatibility notes:
	- Keep minimum retention (90 days) and version status enums explicitly.
- Governance & owner recommendations:
	- Owner: Inventory + Audit domain.

<a id="decision-inventory-014---deep-link-parameter-names"></a>
### DECISION-INVENTORY-014 — Deep-link parameter names

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Deep-link query param names are canonical and stable: `productSku`, `locationId`, `storageLocationId`.
- Alternatives considered:
	- Option A: Per-screen param names: pros (local freedom), cons (operational churn).
- Reasoning and evidence:
	- Links get embedded in SOPs and tickets; stability prevents support cost.
- Architectural implications:
	- Router and forms must map these params consistently.
- Auditor-facing explanation:
	- N/A (routing-only), but stable links improve traceability in incident records.
- Migration & backward-compatibility notes:
	- If renamed, support both names for a transition period.
- Governance & owner recommendations:
	- Owner: Frontend platform team.

<a id="decision-inventory-015---json-field-safe-rendering"></a>
### DECISION-INVENTORY-015 — JSON field safe rendering

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: JSON fields (tags/capacity/temperature/payload) are rendered safely (escaped), truncated in list views, expanded on-demand, and never persisted to `localStorage`.
- Alternatives considered:
	- Option A: Render raw HTML-like strings: pros (rich display), cons (XSS risk).
	- Option B: Always render full JSON: pros (complete), cons (UI lockups).
- Reasoning and evidence:
	- Payloads may contain upstream snippets and operator notes; safe rendering prevents XSS and performance issues.
- Architectural implications:
	- Requires a safe JSON viewer component and size limits.
- Auditor-facing explanation:
	- Payload visibility must be permission-gated; auditors can inspect full payloads only when authorized.
- Migration & backward-compatibility notes:
	- If payload sizes increase, add download links instead of inline rendering.
- Governance & owner recommendations:
	- Owner: Security + Frontend platform.

<a id="decision-inventory-016---allocationreservation-and-atp"></a>
### DECISION-INVENTORY-016 — Allocation/reservation and ATP

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: The frontend treats availability responses as authoritative, including allocated quantities and ATP, and does not call allocation/reservation services directly.
- Alternatives considered:
	- Option A: UI calls allocation service: pros (fresh allocations), cons (cross-domain coupling and auth complexity).
	- Option B: UI computes ATP: pros (simple), cons (incorrect under business rules and concurrency).
- Reasoning and evidence:
	- Inventory services are responsible for composing on-hand and allocation state into a single availability view.
- Architectural implications:
	- Inventory backend must integrate allocation/reservation upstream sources and surface results.
- Auditor-facing explanation:
	- For disputes, auditors should verify ATP by comparing ledger-derived on-hand with allocation records (server-side) rather than client logs.
- Migration & backward-compatibility notes:
	- If allocation ownership changes, update server composition; keep UI stable.
- Governance & owner recommendations:
	- Owner: Inventory + Allocation/Reservation owning team (if separate).

## End

End of document.
