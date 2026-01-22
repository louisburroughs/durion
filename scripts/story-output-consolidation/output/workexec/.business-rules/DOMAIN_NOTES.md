# WORKEXEC_DOMAIN_NOTES.md

## Summary

This document is the non-normative rationale and decision log for the `workexec` domain. It expands the decisions in `AGENT_GUIDE.md` with alternatives, tradeoffs, and validation guidance for architects and auditors. It is intended to make changes reviewable and to keep system-of-record boundaries explicit.

## Completed items

- [x] Linked each Decision ID to a detailed rationale

## Decision details

### DECISION-INVENTORY-001 — SubstituteLink ownership boundary

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: SubstituteLink authoring/admin is not owned by workexec; workexec consumes SubstituteLink for runtime substitution and records substitution history.
- Alternatives considered:
- Option A (chosen): inventory/product owns SubstituteLink
- Pros: clear SoR; reusable rules
- Cons: cross-domain dependency
- Option B: workexec owns SubstituteLink
- Pros: fewer moving parts short-term
- Cons: domain drift; harder reuse
- Reasoning and evidence:
- SubstituteLink is master-data adjacency; runtime substitution is execution behavior.
- Architectural implications:
- Components affected: inventory/product APIs, workexec picker consumption
- Diagram (optional): SubstituteLink (master) -> Picker (workexec) -> Apply -> SubstitutionHistory
- Auditor-facing explanation:
- Inspect SubstituteLink changes in the owning domain; inspect substitution history for runtime usage.
- Migration & backward-compatibility notes:
- If early workexec tables exist for SubstituteLink, migrate into inventory/product and keep read-only views temporarily.
- Governance & owner recommendations:
- Owner: inventory/product domain; workexec as consumer reviewer.

### DECISION-INVENTORY-002 — Canonical Moqui screens/routes

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Align stories to existing screens: `WorkOrderBoard.xml`, `WorkOrderEdit.xml`, `EstimateEdit.xml` in `durion-workexec`; appointment screens in `durion-shopmgr`.
- Alternatives considered:
- Option A (chosen): extend existing screens
- Pros: lowest risk; matches current services
- Cons: may require incremental refactors
- Option B: new UI routes first
- Pros: better UX
- Cons: requires stable REST contracts first
- Reasoning and evidence:
- Existing screens already encode navigation and entity/service usage.
- Architectural implications:
- Keep new workexec UIs adjacent to existing screens for discoverability.
- Auditor-facing explanation:
- Screen artifacts provide traceability to invoked services.
- Migration & backward-compatibility notes:
- Preserve service contracts and auditing semantics when refactoring screens.
- Governance & owner recommendations:
- Owners: workexec component maintainers; shopmgr for appointment screens.

### DECISION-INVENTORY-003 — Identifier handling (opaque IDs)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Treat all IDs as opaque strings; do not enforce UUID/numeric client-side constraints.
- Alternatives considered:
- Option A (chosen): opaque IDs
- Option B: enforce UUID everywhere
- Reasoning and evidence:
- Moqui entities use `type="id"` and IDs can be prefixed strings.
- Architectural implications:
- Prefer pickers/search; avoid free-form ID entry.
- Auditor-facing explanation:
- ID format is not a contract; treat as stable identifiers only.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owners: all UI teams; enforce in UI linting/reviews.

### DECISION-INVENTORY-004 — Work order status taxonomy and “work started”

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: In Moqui `durion-workexec`, started means `WO_IN_PROGRESS` or later; pre-start means `WO_CREATED`/`WO_SCHEDULED`. Other taxonomies must expose `isStarted` to clients.
- Alternatives considered:
- Option A (chosen): status-based gating
- Option B: time-based gating (actualStartTime)
- Reasoning and evidence:
- Status is the authoritative lifecycle signal in current entity model.
- Architectural implications:
- Avoid client-side mapping tables by exposing `isStarted`.
- Auditor-facing explanation:
- Validate that assignment edits are rejected after started.
- Migration & backward-compatibility notes:
- If other services use different statuses, add a translation layer returning `isStarted`.
- Governance & owner recommendations:
- Owner: workexec; coordinate with any upstream SoR for status.

### DECISION-INVENTORY-005 — Assignment vs operational context (SoR + audit)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Operational context is shopmgr SoR and read-only in workexec; overrides are manager-only, audited, and concurrency-safe.
- Alternatives considered:
- Option A (chosen): strict SoR separation
- Option B: copy operational context into workexec and allow edits
- Reasoning and evidence:
- Scheduling integrity requires a single authoritative owner.
- Architectural implications:
- Add an append-only override audit trail; avoid accepting client-supplied actor IDs.
- Auditor-facing explanation:
- Inspect override audit records and confirm no “start snapshot” mutation.
- Migration & backward-compatibility notes:
- safe_to_defer: true for version token until shopmgr provides one.
- Governance & owner recommendations:
- Owners: shopmgr for operational context; workexec for override UX and audit.

### DECISION-INVENTORY-006 — SubstituteLink update semantics and defaults

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: SubstituteLink key fields are immutable; defaults are `priority=100` and `isAutoSuggest=false`; uniqueness is enforced on `(partId, substitutePartId)`; deactivate instead of delete.
- Alternatives considered:
- Option A (chosen): immutable keys + soft deactivate
- Option B: editable keys
- Reasoning and evidence:
- Simplifies idempotency, audit, and uniqueness conflicts.
- Architectural implications:
- UI edit form disables key fields; 409 duplicate returns `existingResourceId`.
- Auditor-facing explanation:
- Verify deactivation does not delete historical substitution usage.
- Migration & backward-compatibility notes:
- If deletes exist, replace with `isActive=false` semantics.
- Governance & owner recommendations:
- Owner: inventory/product; workexec consumer review.

### DECISION-INVENTORY-007 — Substitution picker scope + eligibility source

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Picker supports both WorkOrder and Estimate lines; backend enforces eligibility and returns pricing + `canEnterManualPrice` flags.
- Alternatives considered:
- Option A (chosen): shared picker DTO with `targetType`
- Option B: separate endpoints/DTOs per target
- Reasoning and evidence:
- Ensures consistent UX and avoids duplicate client logic.
- Architectural implications:
- Contract tests for both targets; DB audit record on apply.
- Auditor-facing explanation:
- Confirm apply creates substitution history and eligibility is enforced server-side.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owners: workexec (runtime apply); inventory/pricing (eligibility inputs).

### DECISION-INVENTORY-008 — Part lookup UX contract

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Part selection uses search/picker with pagination; avoid raw ID entry.
- Alternatives considered:
- Option A (chosen): search/picker
- Option B: free-text IDs
- Reasoning and evidence:
- Prevents invalid IDs and reduces errors.
- Architectural implications:
- Provide a standard DTO for part search results.
- Auditor-facing explanation:
- Confirm part selection logs only IDs, not PII.
- Migration & backward-compatibility notes:
- Replace any ID-only UI fields with a picker.
- Governance & owner recommendations:
- Owner: inventory/product UI.

### DECISION-INVENTORY-009 — Dispatch board contract + aggregation behavior

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Board is read-only in v1; exceptions computed server-side; prefer backend aggregation for SLA; UI merges secondary sources in parallel and degrades gracefully.
- Alternatives considered:
- Option A (chosen): degrade gracefully
- Option B: block until all sources succeed
- Reasoning and evidence:
- Dispatch workflow must remain usable during partial outages.
- Architectural implications:
- Stable exception code enum; include `asOf` timestamp in responses.
- Auditor-facing explanation:
- Inspect logs/metrics for partial failures and stale indicators.
- Migration & backward-compatibility notes:
- If moving from Moqui screen to REST, preserve the same exception code semantics.
- Governance & owner recommendations:
- Owner: workexec; consult people/shopmgr as data providers.

### DECISION-INVENTORY-010 — Appointments vs work orders (SoR + link)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Appointments are shopmgr SoR; work orders may reference via `appointmentId`.
- Alternatives considered:
- Option A (chosen): separate appointment entity
- Option B: embed scheduling into work order
- Reasoning and evidence:
- Enables planning without coupling to execution lifecycle.
- Architectural implications:
- Joining requires a shopmgr API or view.
- Auditor-facing explanation:
- Validate that appointment updates are not authored by workexec except via approved integration.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: shopmgr.

### DECISION-INVENTORY-011 — Standard error envelope + duplicate signaling

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Standardize errors to JSON envelope with `code`, `message`, `correlationId`, optional `fieldErrors`, optional `existingResourceId` for duplicates.
- Alternatives considered:
- Option A (chosen): standard envelope
- Option B: framework defaults
- Reasoning and evidence:
- Enables consistent UI parsing and support workflows.
- Architectural implications:
- Bridge layer normalizes Moqui errors where needed.
- Auditor-facing explanation:
- Correlate incidents via `correlationId` across logs.
- Migration & backward-compatibility notes:
- Provide backward-compatible parsing where legacy errors exist.
- Governance & owner recommendations:
- Owner: platform/API governance.

### DECISION-INVENTORY-012 — Idempotency-Key usage for UI mutations

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: UI sends `Idempotency-Key` for create/submit actions and reuses it on retry for the same attempt.
- Alternatives considered:
- Option A (chosen): header-based idempotency
- Option B: no idempotency
- Reasoning and evidence:
- Prevents duplicates under retries/double-click.
- Architectural implications:
- Persist idempotency outcomes server-side.
- Auditor-facing explanation:
- Verify idempotency logs/outcomes for create operations.
- Migration & backward-compatibility notes:
- Add idempotency support to gateway/bridge if missing.
- Governance & owner recommendations:
- Owner: platform/API governance.

### DECISION-INVENTORY-013 — Capability/permission signaling + manual price gating

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Provide a capability signal (user context endpoint or session claims) to drive UI gating; backend remains authoritative. Manual price gating is explicit in picker DTO (`canEnterManualPrice`) and enforced server-side.
- Alternatives considered:
- Option A (chosen): capability flags
- Option B: UI hardcodes roles
- Reasoning and evidence:
- Reduces coupling and prevents accidental unsafe actions.
- Architectural implications:
- Define stable capability schema; apply artifact auth to screens.
- Auditor-facing explanation:
- Confirm 403 responses exist even when UI hides actions.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: security domain; consumers coordinate.

### DECISION-INVENTORY-014 — Audit visibility strategy (substitutes + overrides)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Default UI shows created/updated metadata; optional append-only audit endpoints may be added for substitutes and overrides when required.
- Alternatives considered:
- Option A (chosen): metadata first, audit optional
- Option B: always fetch full audit logs
- Reasoning and evidence:
- Balances operator needs with performance.
- Architectural implications:
- Audit endpoints (if added) must be append-only and PII-safe.
- Auditor-facing explanation:
- Validate audit log immutability.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: each domain for its records; audit domain consult.

### DECISION-INVENTORY-015 — Event ingestion mechanism + failure handling

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Use inbox persistence + async processing with DB idempotency; store failures in Moqui DB for ops review and emit to external DLQ. Protect ingestion with service auth.
- Alternatives considered:
- Option A (chosen): inbox + ops view + DLQ
- Option B: synchronous handling only
- Reasoning and evidence:
- Durable processing and operational visibility are required.
- Architectural implications:
- Add inbox table, failure table, reprocess capability, and alerts.
- Auditor-facing explanation:
- Verify failures are recorded and processing is idempotent.
- Migration & backward-compatibility notes:
- safe_to_defer: true for transport selection until platform standard chosen.
- Governance & owner recommendations:
- Owner: platform/integration; shopmgr consulted.

### DECISION-INVENTORY-016 — Timezone semantics for shop UX

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Display timestamps in user timezone; interpret date-bucket filters in shop timezone when available and label timezone explicitly. If shop timezone is unavailable, use user timezone with explicit labeling.
- Alternatives considered:
- Option A (chosen): user display + shop bucketing
- Option B: always user timezone
- Reasoning and evidence:
- Reduces confusion while preserving operational day boundaries.
- Architectural implications:
- Provide shop timezone source in location/facility config.
- Auditor-facing explanation:
- Validate that board day boundaries are consistent across users.
- Migration & backward-compatibility notes:
- Add timezone field to location/facility entities when available.
- Governance & owner recommendations:
- Owner: shopmgr/location domain.

## Exploratory notes (institutional memory)

This section captures cross-story patterns and “why” behind emerging architecture, without being normative.

### Approvals are becoming a first-class cross-cutting workflow concept

Recent consolidated stories introduce multiple “approval-like” flows:

- Estimate approval capture (digital signature) for Estimate and Work Order.
- Work Order partial approval (per-line approve/decline).
- Approval expiration handling (inbox/detail + approve/deny actions).

These are related but not identical. The key architectural tension is whether “Approvals” are:

- **embedded** as a sub-resource of each domain object (Estimate approvals, WorkOrder approvals), or
- **centralized** as a workflow service with a generic Approval entity that references a subject (`subjectType`, `subjectId`).

The stories currently assume both patterns exist (sub-resource POSTs for estimate/work order approvals, plus a generic approvals inbox/detail). This is a common evolution path: start embedded for speed, then centralize for reuse. The risk is ending up with two incompatible approval models.

Non-normative guidance:

- If we keep embedded endpoints, we should still strive for a shared DTO and shared error codes so the UI can reuse components.
- If we centralize, we should preserve the embedded endpoints as façade routes for backward compatibility (or at least provide stable redirects/links).

### Snapshot-based artifacts are the preferred audit primitive

Multiple stories converge on “immutable snapshot” semantics:

- Estimate customer-facing summary snapshot (generate + render).
- Estimate revision/version history (immutable prior versions).
- Work order finalization/billable scope snapshot (mentioned in open questions list).
- Digital approval artifacts (signature/proof) must be immutable and non-repudiable.

This suggests a consistent institutional preference:

- **Mutable working state** (draft estimate, pending approval work order lines)
- → **immutable snapshot** at key workflow boundaries (customer review, approval, billing finalization)

Tradeoff:

- Snapshots increase storage and require careful “what is the canonical truth” messaging in UI.
- But they dramatically simplify audit, dispute resolution, and legal defensibility.

### Capability-driven UI gating is necessary but insufficient

Stories repeatedly rely on capability flags:

- price override, non-catalog entry, custom labor, approval actions, visibility of sensitive fields.

We should treat capability flags as:

- **UX optimization** (hide/disable actions), not as authorization.
- Backend must still enforce with 403 and deterministic error codes.

Risk:

- If backend omits sensitive fields without explicit capability flags, UI cannot distinguish “not authorized” vs “not applicable”.
- This can lead to accidental disclosure if UI assumes omission means “safe to show”.

Mitigation:

- Prefer explicit capability flags for sensitive-field visibility and actionability.
- Prefer backend returning customer-facing DTOs with restricted fields omitted *and* a `visibility`/capabilities section for clarity.

### Concurrency and idempotency are now table-stakes for POS flows

The consolidated stories introduce multiple “submit-like” actions:

- approval submit (signature)
- partial approval record
- add estimate items (parts/labor)
- revise estimate (create new version)
- generate summary snapshot

These are all susceptible to:

- double-clicks
- flaky networks
- stale state (someone else updated totals/status)

We already have idempotency and error envelope decisions (DECISION-INVENTORY-011/012). The missing piece is a consistent concurrency token strategy across estimate/work order/approval resources.

Institutional memory:

- Moqui often exposes `lastUpdatedStamp` and/or entity `version` patterns, but not consistently across services.
- We should avoid inventing client-side concurrency rules; instead, require backend to provide a token and a deterministic 409 code when stale.

## Decision Log (additions from consolidated stories)

> These entries capture newly identified patterns. They are non-normative rationale records and should be reconciled into `AGENT_GUIDE.md` if/when they become normative.

### DECISION-INVENTORY-017 — Approval artifact handling (immutability + sensitive payload hygiene)

- Status: proposed (emerged from stories #271, #269, #268)
- Decision: Treat approval proof artifacts (e.g., signature images/strokes) as sensitive and immutable:
  - UI must not log raw signature payloads (Base64 images, stroke arrays).
  - UI must only display a proof *reference* or summary (“Signature on file”) unless explicitly required and permitted.
  - Backend should store proof in a controlled store (DB/blob) and return references/metadata, not raw proof, for normal reads.
- Alternatives considered:
  - Option A (chosen): store proof server-side; return metadata/reference
    - Pros: reduces leakage risk; supports legal retention; consistent with audit strategy
    - Cons: requires storage integration and access controls
  - Option B: store proof client-side or echo proof back in responses
    - Pros: simpler demo flows
    - Cons: high leakage risk; larger payloads; harder compliance
- Reasoning and evidence:
  - Stories explicitly call out “non-repudiable, auditable” approvals and “do not log raw Base64 signature image”.
- Architectural implications:
  - Standardize redaction in client logging and error reporting.
  - Prefer append-only approval records with immutable timestamps and actor attribution server-side.
- Auditor-facing explanation:
  - Verify approval records are append-only and that proof is not retrievable without explicit authorization.
- Migration & backward-compatibility notes:
  - If any legacy endpoints return raw proof, introduce a new DTO that omits proof by default and gate proof retrieval behind a dedicated endpoint and capability.
- Governance & owner recommendations:
  - Owner: approval/workflow owning domain (likely workexec initially); security consulted for sensitive-data handling.

### DECISION-INVENTORY-018 — Snapshot generation endpoints are idempotent “submit-like” mutations

- Status: proposed (emerged from story #234 and related snapshot patterns)
- Decision: Treat snapshot generation (e.g., estimate customer summary snapshot) as a mutation requiring:
  - `Idempotency-Key` (DECISION-INVENTORY-012)
  - standard error envelope (DECISION-INVENTORY-011)
  - append-only semantics (no updates; generate new snapshot or replay existing for same idempotency key)
- Alternatives considered:
  - Option A (chosen): POST generate with idempotency and append-only snapshot
    - Pros: audit-friendly; retry-safe; deterministic customer review artifact
    - Cons: more backend work; snapshot storage growth
  - Option B: render summary directly from live estimate without snapshot
    - Pros: simpler; no storage
    - Cons: not legally defensible; totals can change after “review”
- Reasoning and evidence:
  - Story explicitly requires “immutable snapshot created at generation time”.
- Architectural implications:
  - Snapshot DTO should include `snapshotAt`, `generatedByUserId`, and policy outcomes (e.g., legal terms source).
- Auditor-facing explanation:
  - Confirm snapshot immutability and that the rendered summary matches stored snapshot data.
- Migration & backward-compatibility notes:
  - If a legacy “render live estimate” exists, keep it but label as non-auditable and do not use for customer approval flows.
- Governance & owner recommendations:
  - Owner: workexec estimate workflow; legal/compliance consulted for terms retention.

### DECISION-INVENTORY-019 — Prefer server-provided “actionability” flags over client status mapping for approvals

- Status: proposed (emerged from stories #268, #271, #269)
- Decision: For approval-related UI gating, prefer backend-provided booleans such as:
  - `isApprovalEligible`, `isActionable`, `isExpired`, `expiresAt`, `approvalWindowEnd`
  rather than requiring the frontend to hardcode status enums and time computations.
- Alternatives considered:
  - Option A (chosen): server provides actionability flags + timestamps
    - Pros: avoids duplicated policy logic; reduces bugs from enum drift; supports gradual workflow evolution
    - Cons: requires backend to add fields; may feel redundant with status
  - Option B: frontend maps status enums and computes expiration from timestamps
    - Pros: fewer backend changes
    - Cons: brittle; timezone/client clock issues; policy drift
- Reasoning and evidence:
  - Approval expiration story explicitly warns against client-time computation unless backend provides explicit support.
  - Digital approval story has blocking questions about status mapping and payload schema.
- Architectural implications:
  - Add contract tests ensuring flags align with status transitions.
- Auditor-facing explanation:
  - Validate that backend rejects invalid actions even if UI mistakenly enables them.
- Migration & backward-compatibility notes:
  - If flags are absent, UI can fall back to status mapping but must treat backend 409/400 as authoritative and refresh.
- Governance & owner recommendations:
  - Owner: approval/workflow owning domain; platform consulted for DTO conventions.

### DECISION-INVENTORY-020 — Estimate revision/versioning is preferred over “edit-in-place after approval”

- Status: proposed (emerged from story #235)
- Decision: Once an estimate has been customer-reviewed/approved, changes should occur via:
  - creating a new version/revision (new active draft)
  - preserving prior versions as immutable
  - invalidating prior approvals for the new version
- Alternatives considered:
  - Option A (chosen): versioned revisions
    - Pros: strong audit trail; prevents stale approvals; supports dispute resolution
    - Cons: more complex UI (history/compare); more backend entities
  - Option B: edit-in-place with audit log
    - Pros: simpler UI; fewer entities
    - Cons: harder to prove what was approved; approvals become ambiguous
- Reasoning and evidence:
  - Story explicitly requires “immutable history” and “approval invalidation”.
- Architectural implications:
  - UI must clearly show active version number and read-only prior versions.
  - Backend should expose `activeVersionId` and `activeVersionNumber`.
- Auditor-facing explanation:
  - Confirm approvals are tied to a specific version/snapshot and cannot be reused after revision.
- Migration & backward-compatibility notes:
  - If legacy estimates are unversioned, introduce version 1 as a synthetic baseline and migrate forward.
- Governance & owner recommendations:
  - Owner: workexec estimate workflow; audit/compliance consulted.

## Risk assessment & mitigations (from consolidated stories)

### Risk: Approval model fragmentation (embedded vs centralized)

- Symptom: UI must implement both `/work-orders/{id}/approvals` and `/approvals/{approvalId}` patterns with different DTOs.
- Impact: duplicated UI logic, inconsistent audit, inconsistent expiration handling.
- Mitigation:
  - Define a shared Approval DTO and error codes even if endpoints differ.
  - Prefer a single “approval read model” for inbox/detail screens that can reference estimate/work order subjects.

### Risk: Signature payload leakage (client logs, error reporting, analytics)

- Symptom: Base64 PNG or stroke arrays appear in logs, crash reports, or network traces.
- Impact: sensitive data exposure; compliance breach.
- Mitigation:
  - Redaction rules in frontend logging.
  - Avoid storing signature payload in local storage.
  - Prefer proof references after submit; do not echo proof back into UI state beyond what is needed for immediate submit.

### Risk: Client-side time computation for expiration

- Symptom: UI marks approvals expired based on local clock skew or timezone confusion.
- Impact: blocked legitimate approvals or allowed stale approvals.
- Mitigation:
  - Backend provides `isExpired` or `status=EXPIRED` and enforces on submit.
  - UI treats backend as source of truth; uses timestamps for display only.

### Risk: Concurrency conflicts during approval/partial approval/revise

- Symptom: totals/status changed while user is signing or selecting line decisions.
- Impact: approving stale totals; inconsistent authorized scope.
- Mitigation:
  - Backend requires a version/digest in approval payload (open question).
  - UI handles 409 by reloading and requiring re-confirmation.

### Risk: Policy-driven features without a stable capability source

- Symptom: UI cannot reliably determine `canOverridePrice`, `allowNonCatalogParts`, custom labor allowance, approval permissions.
- Impact: either over-permissive UI (security risk) or under-permissive UI (operational friction).
- Mitigation:
  - Standardize a `/me/capabilities` (or equivalent) contract and/or embed capabilities in primary payloads.
  - Keep backend enforcement authoritative.

## Open Questions / Known Issues (consolidated)

> This section is intentionally redundant with story-level open questions; it centralizes cross-cutting unknowns that block coherent architecture.

### API contract and routing consistency (blocking)

1. What are the canonical API base paths and routing conventions used by the Moqui frontend integration layer?
   - Conflicts observed: `/api/...` vs `/api/v1/...` vs `/rest/api/v1/...` and resource naming (`workorders` vs `work-orders`).
2. For each major resource, confirm canonical endpoints or Moqui services:
   - Estimate: load, mutate items (parts/labor), revise/version, generate summary snapshot, approvals
   - Work Order: load, approvals (full + partial), parts issue/consume, finalize snapshot
   - Approvals (generic): list/inbox, detail, approve, deny, expiration metadata

### Approval domain ownership and model (blocking)

1. Confirm the owning domain/service for “Approval” as a workflow concept:
   - Are approvals owned by workexec, or by a separate workflow service?
   - If separate, what is the SoR boundary and what façade endpoints (if any) does workexec expose?
2. Provide the authoritative approval state model:
   - full enum list
   - which states are actionable
   - how expiration is represented (`status=EXPIRED` vs `isExpired` vs `expiresAt` only)

### Approval payload schema and concurrency (blocking)

1. For digital signature approvals (estimate + work order):
   - required schema for `approvalPayload`
   - whether a document digest/version is required to prevent approving stale totals
   - whether `signatureStrokes` are required; if yes, coordinate system and timestamp semantics
   - signer identity requirements (customer id vs implicit)
2. For partial approval (work order):
   - exact line item identifiers and collections (services vs parts)
   - supported approval methods and proof schema
   - terminal outcome mapping when all items declined (what `statusId` is set)

### Estimate workflow gaps (blocking)

1. Estimate status model mismatch:
   - Some stories require “pending approval” while `CUSTOMER_APPROVAL_WORKFLOW.md` lists only `DRAFT/APPROVED/DECLINED/EXPIRED`.
   - Confirm whether there is a `PENDING_APPROVAL` (or equivalent) status and how it transitions.
2. Estimate versioning contract:
   - endpoints/services for create revision, list versions, load version, save active version
   - concurrency token field/header (`version`, `lastUpdatedStamp`, ETag)

### Capability/policy signal source (blocking)

1. Where does the frontend obtain capability/policy flags in this deployment?
   - session claims vs `/me/capabilities` endpoint vs embedded in estimate/work order payload
2. Confirm canonical capability keys for:

- price override, manual price entry, non-catalog parts, custom labor
- record approvals, approve/deny approvals, override conflicts
- sensitive field visibility (pricing/cost/margin)

### Timezone and display standards (blocking for approvals expiration UX)

1. Confirm timezone standard for displaying `expiresAt` and other approval timestamps:

- user timezone vs shop timezone vs always UTC
- formatting standard (and whether to label timezone explicitly)

### Snapshot and audit read models (non-blocking but important)

1. For snapshot-based artifacts (estimate summary snapshot, billable scope snapshot):

- read endpoints and stable DTO schema
- whether snapshots are linkable from work order/estimate screens

1. For audit visibility:

- which audit endpoints exist (if any) for approvals, revisions, overrides
- whether UI should link to them or display IDs only until screens exist

## End

End of document.
