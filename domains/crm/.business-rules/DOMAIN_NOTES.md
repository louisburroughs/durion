# CRM_DOMAIN_NOTES.md

## Summary

This document is non-normative rationale and decision detail for the CRM domain’s agent guide. It explains why decisions were made, what alternatives were considered, and what to audit/inspect operationally. It is intended for architects, senior engineers, and auditors validating correctness and governance alignment.

## Completed items

- [x] Linked each Decision ID to a detailed rationale

## Decision details

### DECISION-INVENTORY-001 — Canonical identifiers (`partyId`)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Use `partyId` as the canonical identifier for customer parties in UI routes and service contracts; treat `customerId` as an alias and `personId` only as a subtype identifier.
- Alternatives considered:
- Option A: Allow `customerId` as a first-class ID: ambiguous mapping and drift risk.
- Option B: Use `personId` everywhere: fails for organizations/accounts.
- Reasoning and evidence:
- Party is already the shared abstraction across person/org.
- Frontend stories repeatedly flagged ID ambiguity.
- Architectural implications:
- APIs accept `partyId`; legacy IDs appear as read-only fields only.
- Auditor-facing explanation:
- Verify no endpoints introduce a second primary key named `customerId`.
- Migration & backward-compatibility notes:
- Provide compatibility mapping fields for legacy routes during transition.
- Governance & owner recommendations:
- Owner: CRM domain.

### DECISION-INVENTORY-002 — Contact roles ownership + primary policy + billing contact rule

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: CRM owns account-contact roles. Primary selection auto-demotes previous primary atomically. Default validation: if `invoiceDeliveryMethod=EMAIL`, require a billing contact with a primary email (config scope safe-to-defer).
- safe_to_defer: true
- Alternatives considered:
- Option A: Billing owns billing roles: creates dual-write and unclear SoR.
- Option B: Reject on existing primary: increases operator friction and race windows.
- Reasoning and evidence:
- Roles are relationships; CRM is the natural owner.
- Architectural implications:
- Enforce uniqueness per account+role and transactional auto-demotion.
- Auditor-facing explanation:
- Inspect audit events for primary changes and verify uniqueness.
- Migration & backward-compatibility notes:
- Backfill/clean historical data to remove multiple primaries before enforcing constraints.
- Governance & owner recommendations:
- Owner: CRM domain; Billing consumes read-only.

### DECISION-INVENTORY-003 — Contact point validation (phone) + kind immutability

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Contact kind is immutable after creation. Phone input is accepted flexibly, normalized where possible, and validated as 10–15 digits; extensions are separate.
- Alternatives considered:
- Option A: Strict E.164 only: rejects common formatted inputs.
- Option B: No validation: leads to unusable contact points.
- Reasoning and evidence:
- CRM must balance UX and data quality.
- Architectural implications:
- Add normalization utilities and store normalized values.
- Auditor-facing explanation:
- Review stored phone normalization consistency.
- Migration & backward-compatibility notes:
- Normalize legacy values on update.
- Governance & owner recommendations:
- Owner: CRM domain with Security review for PII.

### DECISION-INVENTORY-004 — CRM Snapshot proxy + correlation/logging policy

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Snapshot calls must be proxied server-side through Moqui; browsers do not call snapshot directly. Use `X-Correlation-Id` and `traceparent`. Do not emit VIN/plate in client telemetry.
- Alternatives considered:
- Option A: Browser direct call: increases credential exposure risk.
- Option B: Public snapshot endpoint: expands attack surface.
- Reasoning and evidence:
- Secure-by-default requires server mediation.
- Architectural implications:
- Moqui proxy enforces RBAC and injects correlation headers.
- Auditor-facing explanation:
- Verify no UI bundle contains service tokens.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: CRM + Security domain.

### DECISION-INVENTORY-005 — ProcessingLog/Suspense contracts + identifiers

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Use one shared ProcessingLog and one shared SuspenseItem read model across event types; identify records by `processingLogId`/`suspenseId` and include `eventId` as a query field.
- Alternatives considered:
- Option A: Per-integration tables: duplicates UI and filters.
- Option B: Use `eventId` as primary key: fails if multiple attempts exist.
- Reasoning and evidence:
- Operational UIs need consistent list/detail experience.
- Architectural implications:
- Add indexes: (eventType, receivedAt DESC), (eventId), (correlationId prefix).
- Auditor-facing explanation:
- Confirm suspense records retain original `eventId` and failure reason.
- Migration & backward-compatibility notes:
- Introduce IDs without breaking ingestion.
- Governance & owner recommendations:
- Owner: CRM domain; consult Audit/Observability for schema alignment.

### DECISION-INVENTORY-006 — Payload/details visibility + redaction/masking

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Default-deny raw payload. UI uses `payloadSummary` and server-generated `redactedPayload`. Treat `details` as sensitive and redact. Raw payload is auditor-only and still redacted for secrets.
- Alternatives considered:
- Option A: Always show raw payload: highest risk.
- Option B: Never store payload: blocks troubleshooting.
- Reasoning and evidence:
- Compliance and PII/secrets exposure risks outweigh convenience.
- Architectural implications:
- Central redaction utilities with golden tests.
- Auditor-facing explanation:
- Validate redaction policy through tests and spot checks.
- Migration & backward-compatibility notes:
- Retrofit redaction at read time if raw payload exists.
- Governance & owner recommendations:
- Owner: Security domain; CRM implements.

### DECISION-INVENTORY-007 — Permissions + environment restrictions

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Introduce explicit permission keys per feature (ops monitoring, payload view, promotions, vehicle lookup, preferences edit, merge). Backend enforces; UI may hide entry points. Environment restrictions are safe-to-defer.
- safe_to_defer: true
- Alternatives considered:
- Option A: UI-only gating: insecure.
- Option B: One broad CRM permission: overly permissive.
- Reasoning and evidence:
- Least privilege and auditability.
- Architectural implications:
- Define permission taxonomy and document it next to routes/services.
- Auditor-facing explanation:
- Verify 403 behavior returns no partial data.
- Migration & backward-compatibility notes:
- Add permissions with defaults mapped to existing internal roles.
- Governance & owner recommendations:
- Owner: Security domain for role mapping; CRM domain for taxonomy.

### DECISION-INVENTORY-008 — Correlation semantics + filter semantics

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: `correlationId` is a generic string; also support explicit optional UUID fields like `estimateId`/`workorderId` where applicable. Filtering supports exact and prefix only.
- Alternatives considered:
- Option A: Contains search: unpredictable performance.
- Option B: Exact only: less usable.
- Reasoning and evidence:
- Prefix is a good compromise.
- Architectural implications:
- UI communicates “starts with” semantics.
- Auditor-facing explanation:
- Ensure filter predicates are indexed.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: CRM domain.

### DECISION-INVENTORY-009 — Operator actions + retry metadata

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Ops UI is read-only by default. Retry metadata may be displayed if present, but no retry actions are exposed without audited command endpoints.
- Alternatives considered:
- Option A: Add “retry” buttons: requires strict auditing and increases risk.
- Option B: Hide retry metadata: reduces operational insight.
- Reasoning and evidence:
- Visibility-first is safer.
- Architectural implications:
- If future actions are added, require idempotency keys and audit events.
- Auditor-facing explanation:
- Confirm there are no mutation endpoints reachable by ops screens.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: CRM + Audit domain.

### DECISION-INVENTORY-010 — Promotion redemption model, access, and limits visibility

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Promotion redemption is a CRM-owned read model keyed by `promotionRedemptionId` (UUID). Access is gated by explicit permissions. Limit enforcement is not inferred by UI unless backend provides explicit fields.
- safe_to_defer: true
- Alternatives considered:
- Option A: Billing owns redemptions: complicates CRM servicing workflows.
- Option B: UI infers limits: unsafe.
- Reasoning and evidence:
- Redemptions are customer-facing audit artifacts.
- Architectural implications:
- List/detail endpoints must paginate and filter.
- Auditor-facing explanation:
- Verify idempotent ingestion does not create duplicates.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: CRM domain with consult from Pricing domain.

### DECISION-INVENTORY-011 — VehicleUpdated conflict policy + required fields

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Non-critical conflicts use last-write-wins; destructive conflicts route to `PENDING_REVIEW` and are not auto-applied. Include `eventId`, `eventType`, `correlationId`, and optional `estimateId`/`workorderId` in logs.
- safe_to_defer: true
- Alternatives considered:
- Option A: Always last-write-wins: risk of corrupting identity/history.
- Option B: Always quarantine: too noisy.
- Reasoning and evidence:
- VIN is an identity field; mileage decreases can indicate data quality issues.
- Architectural implications:
- ProcessingLog captures `reasonCode` and conflict metadata.
- Auditor-facing explanation:
- Validate PENDING_REVIEW records do not mutate vehicle fields.
- Migration & backward-compatibility notes:
- Introduce policy gradually.
- Governance & owner recommendations:
- Owner: CRM domain; consult Workexec for event semantics.

### DECISION-INVENTORY-012 — Vehicle care preferences schema, enums, locking, audit

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Vehicle care preferences are fixed-schema `VehicleCarePreference` with optimistic locking. Units are `MILES|KM`. Interval range is 1–100000. Audit history exists per vehicle.
- Alternatives considered:
- Option A: JSON/EAV: hard to validate and audit.
- Option B: No locking: silent overwrites.
- Reasoning and evidence:
- Stories require auditability and predictable rendering.
- Architectural implications:
- Add version field to DTO and enforce on update.
- Auditor-facing explanation:
- Confirm audit entries exist for each update.
- Migration & backward-compatibility notes:
- None (new feature).
- Governance & owner recommendations:
- Owner: CRM domain.

### DECISION-INVENTORY-013 — Vehicle lookup/search defaults + handoff

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Default endpoints are `/rest/api/v1/crm/vehicles/search` (POST) and `/rest/api/v1/crm/vehicles/{vehicleId}/snapshot` (GET) via Moqui proxy. Minimum query length 3. Pagination required. Handoff returns to caller with `vehicleId`.
- safe_to_defer: true
- Alternatives considered:
- Option A: Unbounded search: performance risk.
- Option B: Exact-only: poor usability.
- Reasoning and evidence:
- Safe defaults enable UI build-out.
- Architectural implications:
- DTO fields are stable and backend-owned.
- Auditor-facing explanation:
- Confirm search requests do not log full query values.
- Migration & backward-compatibility notes:
- Provide compatibility alias if routes change.
- Governance & owner recommendations:
- Owner: CRM domain with Security review for PII.

### DECISION-INVENTORY-014 — Party merge policy + alias behavior

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Party merges are explicit privileged commands. Only merge same subtype. Source becomes MERGED. Reads return `409 PARTY_MERGED` with `mergedToPartyId`.
- Alternatives considered:
- Option A: Silent redirect: hides identity change.
- Option B: Hard 404: breaks consumers.
- Reasoning and evidence:
- 409 with explicit field is deterministic.
- Architectural implications:
- Standard error payload must include `errorCode` and `mergedToPartyId`.
- Auditor-facing explanation:
- Verify merge audit event includes actor, reason, and affected party IDs.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: CRM domain with Security gating.

### DECISION-INVENTORY-015 — Commercial account creation + duplicate detection

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Commercial account creation includes explicit duplicate-check. Create rejects with `409 DUPLICATE_CANDIDATES` unless override + justification. Billing terms are referenced from Billing-owned data (safe-to-defer exact wiring).
- safe_to_defer: true
- Alternatives considered:
- Option A: Always create then merge: higher operational burden.
- Option B: Hard-block all duplicates: prevents legitimate new accounts.
- Reasoning and evidence:
- Safety-first while allowing controlled overrides.
- Architectural implications:
- Duplicate-check service returns match reasons.
- Auditor-facing explanation:
- Inspect override events and verify justification recorded.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: CRM domain; consult Billing/Pricing for billing terms integration.

## End

End of document.
