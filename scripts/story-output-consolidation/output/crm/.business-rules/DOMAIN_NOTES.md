# CRM_DOMAIN_NOTES.md

## Summary

This document is non-normative rationale and decision detail for the CRM domain’s agent guide. It explains why decisions were made, what alternatives were considered, and what to audit/inspect operationally. It is intended for architects, senior engineers, and auditors validating correctness and governance alignment.

## Completed items

- [x] Linked each Decision ID to a detailed rationale

## Decision details

### DECISION-CRM-001 — Canonical identifiers (`partyId`)

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

**Additional institutional memory (from consolidated stories):**

- Multiple new UI flows (commercial account create, party merge, contact roles, communication preferences) explicitly depend on stable route parameters. The repeated “returnUrl”/handoff patterns in stories reinforce that `partyId` must be the only durable identifier passed between flows to avoid “ID translation” bugs and accidental cross-tenant data exposure.
- When a flow needs to reference a “contact” that is itself a Party, use `contactPartyId` (not `contactId`) to keep the mental model consistent and avoid introducing a parallel identifier namespace.

---

### DECISION-CRM-002 — Contact roles ownership + primary policy + billing contact rule

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

**Additional institutional memory (from consolidated stories):**

- The “Maintain Contact Roles and Primary Flags” story makes the operational UX expectation explicit: CSRs should not have to manually unset a previous primary before setting a new one. This is why auto-demotion is not just a data constraint but a workflow enabler.
- The EMAIL billing rule introduces a cross-entity dependency (account invoice delivery method + contact point presence). The story strongly suggests a privacy-preserving DTO shape (e.g., `hasPrimaryEmail` boolean) rather than returning full email addresses to the UI. This is consistent with DECISION-CRM-006's default-deny posture.

---

### DECISION-CRM-003 — Contact point validation (phone) + kind immutability

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

**Additional institutional memory (from consolidated stories):**

- Party search for merge includes phone/email criteria. This increases the importance of consistent normalization and query semantics (prefix vs exact vs contains) to avoid “same number, different formatting” misses and to keep indexes usable.

---

### DECISION-CRM-004 — CRM Snapshot proxy + correlation/logging policy

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

**Additional institutional memory (from consolidated stories):**

- Several stories explicitly require “show correlation id in error banners” (commercial account create, party merge). This is a practical compromise: we avoid logging sensitive inputs client-side while still enabling support to correlate failures with server logs.
- The vehicle create story reiterates that VIN and plate must not be emitted in client telemetry. This is not only privacy; it also reduces the blast radius of accidental log retention and third-party telemetry ingestion.

---

### DECISION-CRM-005 — ProcessingLog/Suspense contracts + identifiers

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

---

### DECISION-CRM-006 — Payload/details visibility + redaction/masking

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

**Additional institutional memory (from consolidated stories):**

- Frontend stories repeatedly call out “render backend error strings as plain text; do not render as HTML.” This is both an injection mitigation and a “details are sensitive” posture: we avoid accidentally interpreting backend-provided content as markup.
- For duplicate detection and merge flows, candidate DTOs should prefer masked fields (e.g., `taxIdMasked`) and summary booleans over raw PII.

---

### DECISION-CRM-007 — Permissions + environment restrictions

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

**Additional institutional memory (from consolidated stories):**

- New flows introduce multiple distinct permission needs:
  - Create commercial account
  - Merge parties
  - View vs update contact roles
  - View vs edit communication preferences
  - Create vehicle and view vehicle
- Stories consistently assume “hide entry points where possible, but handle direct URL access deterministically.” This is important for Moqui screens where deep links are common and browser history/back navigation can bypass UI gating.

---

### DECISION-CRM-008 — Correlation semantics + filter semantics

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

**Additional institutional memory (from consolidated stories):**

- Party search for merge is explicitly not a “browse all” screen. This aligns with the “no contains search by default” posture: it reduces accidental data exposure and prevents expensive unbounded queries.

---

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

---

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

---

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

---

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

---

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

**Additional institutional memory (from consolidated stories):**

- The commercial account create story applies a similar “handoff” pattern using `returnUrl`. While DECISION-INVENTORY-013 is vehicle-specific, the underlying rationale generalizes: creation flows should support deterministic return navigation without leaking sensitive context into query strings.
- Risk: `returnUrl` can become an open redirect vector if not constrained. See Decision Log for mitigation patterns.

---

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

**Additional institutional memory (from consolidated stories):**

- The merge UI story clarifies that “search for duplicates” is not the same as “browse parties.” This is a governance choice: requiring criteria reduces accidental exposure of customer lists and reduces load.
- The story also introduces a required merge justification and an “irreversible” acknowledgement. While the acknowledgement is UI-only, the justification is expected to be persisted server-side (MergeAudit). This is important for auditability and for explaining why a merge occurred.

---

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

**Additional institutional memory (from consolidated stories):**

- The commercial account create story makes the UX expectation explicit: duplicates can be surfaced either via a dedicated duplicate-check endpoint or via `409 DUPLICATE_CANDIDATES` from create. The decision prefers a separate duplicate-check step, but the UI must tolerate the “409 on create” variant to avoid tight coupling during rollout.
- The story also introduces “external identifiers” as optional inputs. This increases the importance of defining uniqueness constraints and validation rules (see Open Questions). Without a clear schema, teams tend to implement ad-hoc key/value blobs that become impossible to govern.

---

## Decision Log (non-normative, living)

> This section captures newly observed patterns and cross-cutting concerns from consolidated stories. These are not necessarily normative yet; they record intent, tradeoffs, and risks for future formalization in `AGENT_GUIDE.md`.

### DECISION-LOG-UI-001 — Two-step “check then commit” for high-risk creates (dedupe/override)

- Status: Observed pattern; candidate for formalization
- Context:
  - Commercial account creation requires duplicate detection and an override path with justification.
- Decision (pattern):
  - Prefer a two-step flow: `duplicate-check` → user confirmation/justification → `create` with explicit override fields.
  - However, UI must also handle the backend alternative: `create` returns `409 DUPLICATE_CANDIDATES` with candidates, then UI re-submits with override.
- Tradeoffs:
  - Pros: reduces accidental duplicates; creates an explicit audit trail for overrides; improves operator confidence.
  - Cons: adds latency and complexity; requires careful state preservation to avoid user frustration.
- Risk & mitigation:
  - Risk: race between duplicate-check and create (a duplicate could be created after check).
  - Mitigation: backend must still enforce uniqueness/duplicate policy at create time and return deterministic conflicts; UI must preserve form state and handle 409 gracefully.

### DECISION-LOG-UI-002 — Justification-required privileged mutations (merge, duplicate override)

- Status: Observed pattern; candidate for formalization
- Context:
  - Party merge requires justification; commercial account duplicate override requires justification.
- Decision (pattern):
  - For privileged, potentially irreversible or data-quality-impacting actions, require a human-entered justification string that is persisted server-side and included in audit events.
- Tradeoffs:
  - Pros: improves auditability and accountability; supports later investigations.
  - Cons: can become “checkbox compliance” if not reviewed; adds friction.
- Risk & mitigation:
  - Risk: low-quality justifications (“test”, “n/a”).
  - Mitigation: enforce minimum length and optionally require selecting a reason code + free-text details (future enhancement; not required yet).

### DECISION-LOG-SEC-001 — `returnUrl` / handoff parameters must be constrained (open redirect prevention)

- Status: Observed risk; mitigation recommended
- Context:
  - Stories propose `returnUrl` for navigation handoff after create/cancel flows.
- Decision (pattern):
  - Treat `returnUrl` as untrusted input. Only allow:
    - relative paths within the application, or
    - a small allowlist of known internal routes.
- Tradeoffs:
  - Pros: prevents open redirect and phishing vectors.
  - Cons: slightly reduces flexibility for deep-linking from external systems.
- Risk & mitigation:
  - Risk: open redirect if arbitrary URLs are accepted.
  - Mitigation: validate server-side (preferred) and also sanitize client-side; fall back to a safe default route when invalid.

### DECISION-LOG-DATA-001 — Prefer “summary booleans” over raw PII in list/search DTOs

- Status: Observed pattern; candidate for formalization
- Context:
  - Contact roles validation wants “hasPrimaryEmail” without exposing email.
  - Duplicate candidates want masked tax ID and minimal fields.
- Decision (pattern):
  - For list/search/selection UIs, return:
    - masked values (e.g., `taxIdMasked`) and/or
    - boolean indicators (e.g., `hasPrimaryEmail`)
  - Avoid returning full contact points unless the screen explicitly requires them and permissions allow.
- Tradeoffs:
  - Pros: reduces PII exposure; aligns with default-deny payload visibility.
  - Cons: can limit troubleshooting and force additional calls for detail views.
- Risk & mitigation:
  - Risk: UI teams may request “just include email/phone for convenience.”
  - Mitigation: require explicit permission gating and a clear “need-to-display” justification for any raw PII fields.

### DECISION-LOG-API-001 — Standardized error payloads and deterministic 409 semantics

- Status: Observed pattern; candidate for formalization
- Context:
  - Multiple stories assume `errorCode`, `message`, optional `fieldErrors`, and deterministic 409 behaviors (e.g., `PARTY_MERGED`, `DUPLICATE_CANDIDATES`, `DUPLICATE_VIN`).
- Decision (pattern):
  - Standardize error response shape across CRM endpoints:
    - `errorCode` (stable, machine-readable)
    - `message` (safe for display as plain text)
    - `fieldErrors` (optional map)
    - additional structured fields for specific 409s (e.g., `mergedToPartyId`, `candidates`)
- Tradeoffs:
  - Pros: consistent UI mapping; better supportability; less coupling to message strings.
  - Cons: requires discipline across services; versioning considerations.
- Risk & mitigation:
  - Risk: inconsistent error shapes lead to UI “string parsing” and brittle behavior.
  - Mitigation: contract tests and shared error schema documentation.

---

## Known Risks / Mitigations (cross-cutting)

### Risk: PII leakage via UI telemetry, logs, or error rendering

- Drivers:
  - VIN/plate, tax ID, email/phone appear in multiple flows.
- Mitigations:
  - Continue enforcing DECISION-INVENTORY-004 and DECISION-INVENTORY-006.
  - UI displays correlation IDs for support instead of logging inputs.
  - Render backend messages as plain text; do not embed raw payloads.

### Risk: Unbounded search / “browse all” data exposure

- Drivers:
  - Party merge search and other selection screens.
- Mitigations:
  - Require at least one criterion; enforce minimum lengths where appropriate.
  - Prefer prefix/exact semantics; avoid contains by default (aligns with DECISION-INVENTORY-008).

### Risk: Open redirect via `returnUrl`

- Drivers:
  - Create flows and handoff patterns.
- Mitigations:
  - Constrain `returnUrl` to relative/allowlisted routes (Decision Log SEC-001).

### Risk: Race conditions around dedupe and primary selection

- Drivers:
  - Duplicate-check then create; primary auto-demotion.
- Mitigations:
  - Backend remains authoritative; enforce constraints transactionally.
  - UI must handle 409 conflicts deterministically and preserve state.

---

## Open Questions (consolidated, living)

> These are consolidated from the new stories. Many are blocking for implementation but are recorded here as institutional memory and to guide contract work.

### Permissions / RBAC (blocking)

1. What are the exact permission keys for:
   - Create commercial account
   - Party search (merge flow)
   - Party merge command
   - View contact roles vs update contact roles
   - View communication preferences vs edit communication preferences
   - Create vehicle vs view vehicle  
   (DECISION-INVENTORY-007 requires explicit permissions and fail-closed behavior.)

### Commercial account creation (blocking)

1. Billing terms contract:
   - Exact endpoint/service to list billing terms
   - ID type (`billingTermsId`) and display fields (`code`, `description`, etc.)
   - Empty list semantics (is empty valid or should it be treated as misconfiguration?)
2. Duplicate detection contract:
   - Dedicated duplicate-check endpoint vs `409 DUPLICATE_CANDIDATES` on create
   - Required matching inputs (legalName only vs also taxId/external identifiers/phone/email)
   - Candidate DTO shape (what fields are safe to display; matchReason/matchScore semantics)
3. External identifiers schema:
   - Fixed set vs key/value list
   - Uniqueness constraints (global? per party type? per external system?)
   - Validation rules and error mapping
4. Post-create navigation:
   - Canonical Party/Account view route/screen name in Moqui frontend
   - If absent, is “confirmation-only” acceptable for MVP?

### Party merge (blocking / clarifications)

1. Backend contract:
   - Party search endpoint/service name and DTO fields
   - Merge command endpoint/service name and response payload (mergeAuditId, alias/redirect fields)
2. Justification storage:
   - Required? min/max length? stored in MergeAudit?
   - Visibility rules (who can view justifications)?
3. Search semantics (non-blocking if backend exists):
   - name/email/phone matching: exact/prefix/contains
   - minimum lengths to protect performance
4. Merged exclusion:
   - Does search always exclude merged parties?
   - Is there an Admin-only `includeMerged` toggle?

### Contact roles / primary flags (blocking / clarifications)

1. Backend contract:

- Load account contacts + role assignments + invoiceDeliveryMethod
- Update contact roles and primary flags
- Error payload schema (`errorCode`, `message`, `fieldErrors`)

1. Role code source:

- Are role codes fixed to `BILLING|APPROVER|DRIVER` for MVP?
- Or must UI load allowed roles list with display labels?

1. Billing EMAIL rule data dependency:

- Will load DTO include `hasPrimaryEmail` per contact (preferred)?
- Or must UI infer from contact points (risk of exposing PII)?

### Communication preferences / consent (blocking)

1. Backend contract:

- Load preferences by `partyId`
- Upsert preferences by `partyId`
- DTO field names and requiredness

1. Missing record semantics:

- `404 PREFERENCES_NOT_FOUND` vs `200` empty payload

1. `updateSource` handling:

- Required on write?
- If yes, is it inferred server-side or provided by client (and what constant value)?

1. Consent model:

- Are consent flags stored only on the preferences record?
- Or is there a separate ConsentRecord entity/DTO the UI must manage?

1. Optimistic locking:

- Required? which field (`version`, `lastUpdatedStamp`, ETag)?
- Expected 409 payload and UI resolution flow?

### Vehicle create (blocking / clarifications)

1. VIN uniqueness scope:

- Global vs scoped (per party/account/location)
- Duplicate errorCode (`DUPLICATE_VIN`?) and whether a safe reference to existing `vehicleId` is ever returned

1. Association-at-create:

- Is `partyId` required/optional when creating a vehicle?
- Behavior when omitted

1. Moqui service/API contract:

- Exact create and load endpoints/services and DTO fields

1. License plate structure:

- Single string vs plate + region fields and validation rules

## End

End of document.
