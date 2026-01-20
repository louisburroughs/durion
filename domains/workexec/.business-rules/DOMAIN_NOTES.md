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

## End

End of document.
