# INVENTORY_DOMAIN_NOTES.md

## Summary

This document records the rationale, tradeoffs, and audit guidance behind the Inventory domain’s normative rules in `AGENT_GUIDE.md`.
It is intentionally non-normative and may include alternatives that are not implemented.
Use this for architecture review, governance discussions, and auditor support; do not treat it as executable policy.

## Completed items

- [x] Linked each Decision ID to a detailed rationale

## Decision details

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

---

## New institutional memory from consolidated stories (non-normative)

This section captures cross-cutting patterns and tensions that emerged from recent story consolidation. It is intentionally exploratory and may include options not yet implemented.

### 1) Inventory vs “Fulfillment/Work Execution” boundary is repeatedly ambiguous

Multiple stories labeled `domain:inventory` describe workflows that are primarily **Work Execution** state machines:

- Picking execution (scan + confirm) (#244)
- Pick list/task creation (#92)
- Issue/consume picked items to workorder (#243)
- Return unused items to stock from a work order (#242)
- Cross-dock receiving “receive + issue to workorder line” (#99)

**Why this matters:** Inventory’s normative decisions cover topology (`locationId`/`storageLocationId`), availability, ledger immutability, adjustments scope, feed ops, and proxy/error/observability patterns. They do **not** define work order task/line state machines, scan semantics, or work order eligibility rules.

**Pragmatic stance (current):**

- Inventory UI can host screens that *touch* work orders, but the **SoR for task state** must be explicit per endpoint/contract.
- UI must be backend-driven for eligibility and state transitions (avoid inventing state taxonomies client-side).

**Rejected alternative (implicit ownership):**

- Treat “anything that moves inventory” as Inventory-owned. This tends to collapse domain boundaries and creates coupling to WorkExec lifecycle semantics, which then leaks into Inventory contracts and audit narratives.

**Risk:** If we proceed without explicit ownership, we risk:

- inconsistent permission gating (Inventory vs WorkExec roles),
- conflicting status enums,
- audit gaps (who confirmed pick vs who posted ledger),
- and brittle UI logic that guesses business rules.

**Mitigation:** Require a “Domain Ownership & SoR” section in each cross-domain story and confirm:

- which service owns the state machine,
- which service posts ledger entries,
- which identifiers are stable across systems (workOrderId, workOrderLineId, pickTaskId, pickedItemId, etc.).

### 2) “Movement flows” are expanding beyond classic inventory transfers/adjustments

Return-to-stock (#242) and consume/issue (#243) are effectively inventory movements with additional business context (work order linkage, reason codes, eligibility constraints).

**Connection to existing decisions:**

- DECISION-INVENTORY-009 (inactive/pending blocking) becomes more important as new movement flows appear.
- DECISION-INVENTORY-005 (ledger immutability) implies these flows must create new ledger entries, not mutate prior ones.
- DECISION-INVENTORY-011 (sensitive data) applies: quantities should not leak into client telemetry even when shown to the user.

**Tradeoff:** These flows need richer UX (line-level validation, reason codes, destination selection) while still keeping the backend authoritative for:

- max-returnable quantities,
- work order eligibility,
- and concurrency control.

### 3) “Master data” stories keep landing in Inventory but likely belong elsewhere

Stories like:

- UOM conversions (#120)
- Product master / catalog creation (#121, mismatch noted)
- Catalog search (#81)
- Supplier/vendor cost tiers (#260)

…are repeatedly flagged as domain conflicts.

**Institutional memory:** Inventory is often the “closest UI” for operations, so master-data screens get routed there by default. This creates long-term coupling and unclear governance.

**Mitigation options (exploratory):**

- Option A: Keep UI navigation under Inventory but treat backend ownership as Product/Pricing/Security; document this explicitly and require cross-domain review.
- Option B: Re-label and move stories to the owning domain and keep Inventory UI limited to operational read/write flows.

### 4) Concurrency and idempotency are becoming first-class UX requirements

Several stories explicitly call out:

- `409` conflict handling (supplier cost tiers #260, picking #244, consume #243, return #242)
- idempotency keys for POST mutations (return #242, cross-dock receiving #99)

**Why now:** As we add more “atomic” commands (return-to-stock, consume, receive+issue), the cost of duplicate submissions rises (double-posted ledger entries, double-issued parts).

**Mitigation:** Prefer backend-supported idempotency for mutation endpoints and standardize UI retry guidance:

- safe retry only when an idempotency key is used and backend confirms dedupe semantics,
- otherwise require explicit user confirmation and/or reload before retry.

---

## Decision Log (non-normative additions)

This log records newly identified patterns that should be considered for promotion into normative decisions if they stabilize.

> Note: These are *not* yet normative rules unless they already exist above; they are captured here as institutional memory and to guide future governance discussions.

### DECISION-INVENTORY-017 — Cross-domain workflow ownership must be explicit (WorkExec vs Inventory vs Product/Pricing/Security)

- Status: Proposed (emerged from stories; not yet in `AGENT_GUIDE.md`)
- Problem: Stories repeatedly mix Inventory operational concerns with WorkExec task state machines and Product/Pricing master data.
- Decision (proposed): Any story/screen that references work orders, pick tasks, or product master data must explicitly declare:
  - system-of-record (SoR) for the state machine/data,
  - which service posts ledger entries (if any),
  - which domain owns permissions and role taxonomy,
  - stable identifiers and their canonical route/deep-link params.
- Alternatives considered:
  - Option A: Allow implicit ownership based on UI placement: pros (faster), cons (long-term coupling, audit ambiguity).
  - Option B: Force all cross-domain screens into a separate “Operations” domain: pros (clear UI grouping), cons (organizational overhead, unclear backend ownership).
- Tradeoffs:
  - Adds upfront clarification work but reduces rework and audit risk later.
- Risk assessment:
  - Risk: Without this, we will encode business rules in UI that conflict with backend enforcement.
- Mitigation:
  - Add a lightweight “Ownership & SoR” checklist to story templates and require review by both domains when labels conflict.

### DECISION-INVENTORY-018 — Mutation endpoints should support idempotency keys for safe retry UX

- Status: Proposed
- Problem: Return-to-stock, consume/issue, and cross-dock receive+issue are high-impact mutations where timeouts/retries can double-post.
- Decision (proposed): For inventory mutation commands invoked from the UI, prefer:
  - an idempotency key header (name TBD by platform) or request field,
  - a defined dedupe window,
  - deterministic behavior on replay (same result body or a stable reference id).
- Alternatives considered:
  - Option A: No idempotency; rely on “disable submit” only: pros (simple), cons (doesn’t protect against network timeouts and user refresh).
  - Option B: Client-side local persistence of “pending mutation”: pros (some protection), cons (complex, can leak sensitive data if persisted; conflicts with DECISION-INVENTORY-015).
- Tradeoffs:
  - Requires backend work and contract clarity; significantly improves operational safety.
- Risk assessment:
  - Risk: Without idempotency, support burden increases and ledger integrity incidents become more likely.
- Mitigation:
  - Until idempotency exists, UI should avoid auto-retry and should guide users to reload and verify state before re-submitting.

### DECISION-INVENTORY-019 — Workorder-linked movements must carry stable linkage identifiers for audit

- Status: Proposed
- Problem: Consume/issue and return-to-stock flows need strong audit narratives across systems.
- Decision (proposed): When inventory movements are initiated in the context of a work order, the resulting inventory transaction/ledger entries should include stable linkage fields such as:
  - `workOrderId` and preferably `workOrderLineId` (or a single `sourceTransactionId` that is resolvable).
- Alternatives considered:
  - Option A: Only store free-text notes: pros (fast), cons (not reliably queryable/auditable).
  - Option B: Only store workOrderId: pros (some linkage), cons (line-level disputes become hard to resolve).
- Tradeoffs:
  - More schema discipline, but better traceability and dispute resolution.
- Risk assessment:
  - Risk: Without stable linkage, auditors must correlate via timestamps and user narratives.
- Mitigation:
  - Ensure API contracts for these flows include linkage identifiers and that ledger query supports filtering by them (see Open Questions).

### DECISION-INVENTORY-020 — Quantity precision must be backend-driven; UI should not assume integers

- Status: Proposed (reinforced by #243 and counts stories)
- Problem: Several workflows involve quantities where integer-only assumptions may be wrong (UOM conversions, cycle counts, consumption).
- Decision (proposed): UI should accept decimals by default unless backend provides per-line precision/UOM constraints.
- Alternatives considered:
  - Option A: Force integers everywhere: pros (simple), cons (incorrect for fractional UOMs).
  - Option B: Let UI infer from SKU patterns: pros (sometimes works), cons (unsafe business logic in UI).
- Tradeoffs:
  - Slightly more complex validation; avoids incorrect blocking.
- Risk assessment:
  - Risk: Incorrect UI validation can prevent legitimate operations or allow invalid submissions.
- Mitigation:
  - Prefer backend-provided `uom` + `quantityPrecision` (or similar) and validate accordingly.

---

## Known risks & mitigations (cross-cutting)

### Risk: Sensitive quantity leakage via client telemetry/console

- Triggered by: fulfillment flows (pick/consume/return), cost tiers, counts.
- Existing mitigation: DECISION-INVENTORY-011.
- Additional note: Some stories propose showing counts/variances; ensure any telemetry remains metadata-only.

### Risk: XSS/perf issues from rendering upstream JSON payloads

- Triggered by: feed ops payloads, audit logs, potentially supplier cost tier metadata.
- Existing mitigation: DECISION-INVENTORY-015.
- Additional note: Avoid persisting large payloads in local storage; prefer on-demand fetch.

### Risk: Duplicate mutations due to timeouts/retries

- Triggered by: return-to-stock, consume/issue, cross-dock receive+issue, pick confirmations.
- Mitigation: Proposed DECISION-INVENTORY-018; until then, bounded timeout UX + explicit user-driven retry only.

### Risk: Domain conflict causes inconsistent permissions and audit narratives

- Triggered by: master data stories and WorkExec workflows labeled as inventory.
- Mitigation: Proposed DECISION-INVENTORY-017; require explicit SoR and cross-domain review.

---

## Open Questions (updated / consolidated)

This section consolidates open questions from the new stories and highlights those that impact Inventory domain architecture decisions.

### A) Domain ownership / boundaries (blocking)

1. **Supplier/vendor cost tiers ownership:** Should #260 be `domain:pricing` / procurement / costing rather than inventory? If inventory hosts the UI, who owns the backend contract and permission taxonomy?
2. **Picking execution ownership:** Is #244 owned by `domain:workexec` (pick task lifecycle) rather than inventory? If inventory owns it, where is the inventory decision/contract defining pick tasks/lines and statuses?
3. **Master data ownership:** UOM conversions (#120), product master/catalog (#121/#81) appear Product-domain capabilities. Confirm SoR and correct `domain:*` labels.

### B) Backend contract gaps (blocking)

1. **Workorder-linked flows contracts:** Provide Moqui proxy endpoints and schemas for:
   - load picked items for a workorder (#243),
   - consume/issue picked items (#243),
   - load returnable items + reason codes + submit return-to-stock (#242),
   - pick task load/scan/confirm/complete (#244),
   - counts planning endpoints (zones, create plan, list plans, plan detail) (#241).
2. **Deterministic error schema details:** Confirm exact error payload structure for field/line mapping (paths like `tiers[1].minQuantity`, identifiers like `pickedItemId`, etc.) and standard error codes for common cases.
3. **Pagination shapes:** For list endpoints (zones, plans, storage locations), confirm cursor pagination response shape (`items` vs top-level array, token field names).

### C) Permissions (blocking)

1. **Permission strings:** Confirm canonical permission strings (colon-separated per DECISION-INVENTORY-010) for:
   - availability view,
   - counts planning create/view,
   - pick list/task view/confirm/complete,
   - consume/issue,
   - return-to-stock,
   - supplier cost tiers CRUD,
   - UOM conversions CRUD and audit viewing,
   - any security admin screens (role matrix) if hosted under inventory navigation.
2. **Permission discovery mechanism:** How does the frontend discover effective permissions—session claims, a permissions endpoint, or screen-level authz only?

### D) Identifiers & routing (blocking)

1. **Canonical identifiers for items/products:** For supplier cost tiers (#260) and other item-linked screens, confirm whether the canonical identifier is `productSku`, `productId`, or `itemId`, and how it maps to deep-linking conventions (DECISION-INVENTORY-014 is SKU/location/bin focused).
2. **Picking route parameter:** For #244, should the canonical route use `workOrderId` or `pickTaskId`? If both exist, define canonical deep-link params and redirect behavior.

### E) Business rule clarifications that affect UI safety (blocking)

1. **Scan semantics:** What can be scanned (product, bin/location, both), and what is the matching/disambiguation behavior when multiple lines match?
2. **Quantity rules in picking:** Are partial picks allowed? Is completion allowed with remaining quantities? Are over-picks allowed?
3. **Serial/lot control:** Are items serialized/lot-controlled in pick/consume/return flows, and what additional capture is required?
4. **Counts date/timezone rule:** For cycle count planning (#241), define whether “today” is allowed and which timezone defines “past” (user, site, UTC).
5. **Counts empty-zone behavior:** If selected zones contain no items, is plan creation allowed and how is that represented?
6. **Return destination semantics:** For return-to-stock (#242), is destination fixed to the work order’s site or user-selectable? Is `storageLocationId` required/supported?
7. **Idempotency keys:** For return-to-stock (#242) and cross-dock receiving (#99), does the backend support idempotency keys? If yes, what header/field name and dedupe window?

### F) Cost tiers specifics (blocking)

1. **Currency rules:** Is `currencyCode` derived from supplier and read-only, or can supplier-item cost override currency?
2. **Base cost support:** Is `baseCost` supported and how does it interact with tiers (fallback vs ignored)?
3. **Precision/rounding:** Required precision for `unitCost`/`baseCost` and rounding rules for display vs submission.
4. **Optimistic locking:** ETag/version/`updatedAt` concurrency control for cost tiers; expected status code and request requirements on update.

---

## End

End of document.
