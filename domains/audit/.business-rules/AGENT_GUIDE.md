# AGENT_GUIDE.md

## Summary

This guide defines the audit domain’s normative business rules for ingestion, immutability, search, and export.
It is authoritative for permission gating, guardrails, and capability contracts; rationale and auditor narrative live in `AUDIT_DOMAIN_NOTES.md`.

## Completed items

- [x] Generated/maintained Decision Index
- [x] Mapped Decision IDs to `AUDIT_DOMAIN_NOTES.md`
- [x] Reconciled open questions into normative answers

## Purpose

Provide a cross-cutting, immutable and searchable audit trail for operational and compliance-relevant changes across the Durion POS system.

The audit service is the **system of record** for audit entries (storage, query, export, retention). Source domains remain authoritative for operational state.

This document is **normative** (direct agent input, CI validation, story execution rules may use this).

**Non-normative companion:** `AUDIT_DOMAIN_NOTES.md` (explanations, rationale, tradeoffs, future design work, auditor-facing narrative).

---

## Decision Index (Authoritative)

| Decision ID | Title |
|---|---|
| **AUD-SEC-001** | Tenant Isolation and Scoping Rules |
| **AUD-SEC-002** | Location Scoping and Cross-Location Permission Model |
| **AUD-SEC-003** | Authorization Model (Roles → Permission Strings) |
| **AUD-SEC-004** | Raw Payload Handling, Redaction, and Safe Rendering |
| **AUD-SEC-005** | Query Guardrails (Mandatory Date Range, Indexed Filter Rule, Max Window) |
| **AUD-SEC-006** | Export Security Model (Async Jobs, Access, Auditing, Digest Manifest) |
| **AUD-SEC-007** | Identifier Semantics for Search (Product ID vs SKU vs Part Number) |
| **AUD-SEC-008** | Pricing Evidence Access (Snapshot/Trace), Size Limits, and Pagination |
| **AUD-SEC-009** | Immutability Proof Fields (Hash Chain / Signature): Display-Only Policy |
| **AUD-SEC-010** | Event Type Vocabulary and Discovery Endpoint |
| **AUD-SEC-011** | Deep-Link Metadata Policy (No Hardcoded URLs; Authorization-Safe) |
| **AUD-SEC-012** | Correlation and Trace Context Standard |

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| AUD-SEC-001 | Enforce tenant isolation for all reads/writes | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-001---tenant-isolation-and-scoping-rules) |
| AUD-SEC-002 | Require and enforce location scoping; gated cross-location | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-002---location-scoping-and-cross-location-permission-model) |
| AUD-SEC-003 | Least-privilege permissions and role guidance | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-003---authorization-model-roles-%E2%86%92-permission-strings) |
| AUD-SEC-004 | Restrict, redact, and safely render raw payloads | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-004---raw-payload-handling-redaction-and-safe-rendering) |
| AUD-SEC-005 | Guardrails: date range required, indexed filters, max window | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-005---query-guardrails) |
| AUD-SEC-006 | Async export jobs with auditing + digest manifest | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-006---export-security-model) |
| AUD-SEC-007 | Support multiple identifier semantics; backend normalization | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-007---identifier-semantics-for-search) |
| AUD-SEC-008 | Pricing evidence access with size limits and pagination | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-008---pricing-evidence-access-size-limits-pagination) |
| AUD-SEC-009 | Proof fields display-only unless verification is explicit | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-009---immutability-proof-fields-hash-chain--signature) |
| AUD-SEC-010 | Controlled eventType vocabulary via discovery endpoint | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-010---event-type-vocabulary) |
| AUD-SEC-011 | Deep-link metadata only; no hardcoded URLs | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-011---deep-link-metadata-policy) |
| AUD-SEC-012 | Use W3C Trace Context for correlation | [AUDIT_DOMAIN_NOTES.md](AUDIT_DOMAIN_NOTES.md#decision-aud-sec-012---correlation-and-trace-context-standard) |

---

## Domain Boundaries (Normative)

### Audit domain owns

- Asynchronous ingestion of audit events
- Persistence in append-only / WORM-like manner
- Immutability guarantees (no update/delete via application APIs)
- Query/search and export capabilities
- Retention enforcement (default minimum 7 years; tenant-configurable)
- Reason code registry (managed codes + metadata)
- Audit evidence artifacts required for compliance investigations:
  - `PricingSnapshot` (immutable evidence)
  - `PricingRuleTrace` (immutable evidence)

### Audit domain does not own

- Operational truth for schedules, assignments, work orders, appointments, inventory movements
- Transactional enforcement that “audit must succeed” for operational writes (eventual consistency required)
- Pricing rule logic/computation behavior (owned by pricing domain)

### Integration posture (required)

- Asynchronous ingestion. Producer domains must not block critical-path transactions on audit availability.
- Outbox pattern in producer domains to ensure durable delivery.

---

## Key Entities / Concepts (Normative)

### `AuditLog` (stored record)

Minimum fields:

- `auditLogId` (UUIDv7)
- `eventId` (UUIDv7; dedupe key; globally unique)
- `schemaVersion`
- `eventType`
- `occurredAt` (UTC), `emittedAt` (UTC, optional)
- `tenantId`
- `locationId` (required; see AUD-SEC-002)
- `actor` (`actorType`, `actorId`, `displayName` optional)
- `aggregateType`, `aggregateId`
- `changeSummaryText` (optional)
- `changePatch` (structured diff; preferred)
- `reasonCode` (optional; from registry)
- `reasonNotes` (optional; length-limited)

Additional (allowed) fields:

- `correlationId` (W3C trace correlation; see AUD-SEC-012)
- `sourceSystem`
- `rawPayload` (restricted; see AUD-SEC-004)
- Optional immutability proof fields: `hash`, `prevHash`, `signature` (display-only; see AUD-SEC-009)

### `PricingSnapshot` (immutable evidence record)

Minimum:

- `snapshotId` (UUIDv7)
- `timestamp` (UTC)
- `quoteContext` (restricted/redacted; see AUD-SEC-004)
- `finalPrice` (money)
- `ruleTraceId` (UUIDv7; optional if embedded trace)

### `PricingRuleTrace` (immutable evidence record)

Minimum:

- `ruleTraceId` (UUIDv7)
- `evaluationSteps[]` (array)
  - `ruleId` (string)
  - `status` (`APPLIED|REJECTED|SKIPPED`)
  - `inputs` (restricted/redacted)
  - `outputs` (restricted/redacted)

---

## Security / Authorization Assumptions (Normative)

### Tenant isolation (AUD-SEC-001)

- All audit reads/writes are tenant-scoped and enforced server-side.
- No cross-tenant search/export is permitted under any role.

### Location scoping (AUD-SEC-002)

- `locationId` is required for `AuditLog` and is enforced server-side.
- Default UI scope is the user’s current location context.
- Cross-location search is allowed only with explicit permission and explicit location filters.

### Authorization model (AUD-SEC-003)

Do not ship screens without explicit permission mapping. Permissions are action-scoped and least-privilege.

**Permission strings (normative):**

- Audit search/list: `audit:log:view`
- Audit detail: `audit:log:view-detail`
- View raw payload: `audit:payload:view` (restricted)
- Export audit logs: `audit:export:execute`
- Download export artifacts: `audit:export:download`
- View pricing snapshot: `audit:pricing-snapshot:view`
- View pricing trace: `audit:pricing-trace:view`
- View immutability proof fields (if present): `audit:proof:view` (display-only)
- Cross-location search: `audit:scope:cross-location`
- Admin reason code registry: `audit:reason-code:manage`

**Role guidance (normative mapping):**

- Shop Manager: view/search + detail (no raw payload by default)
- Compliance Auditor: view/search + detail + export + pricing evidence
- Support/Operations: view/search + detail; raw payload only if explicitly granted
- Pricing Admin (if needed): pricing evidence view; not necessarily export

### Raw payload policy (AUD-SEC-004)

- Raw payload is **not shown by default**.
- Raw payload may only be accessed with `audit:payload:view`.
- Raw payload and quoteContext/inputs/outputs must be safely rendered as **escaped text** (never interpreted as HTML).
- Redaction is backend-owned; UI must not infer redaction.

### Guardrails (AUD-SEC-005)

- Backend enforces: mandatory date range, at least one indexed filter, and maximum date window.
- UI mirrors enforcement for usability but backend remains authoritative.

### Export security (AUD-SEC-006)

- Exports are async jobs only.
- Exports are authorized, auditable, tenant-scoped, and non-enumerable across tenants/users.
- Export includes metadata and SHA-256 digest manifest.

### Correlation and trace (AUD-SEC-012)

- Standard correlation mechanism is W3C Trace Context:
  - `traceparent` (required)
  - `tracestate` (optional)

---

## API Expectations (Normative)

> Concrete Moqui service names may vary by implementation; the **capability contract** is normative.

### Query APIs

- `GET /audit/logs/search`
- `GET /audit/logs/detail?eventId=...`

Search parameters (AND semantics):

- `fromUtc`, `toUtc` (required; see AUD-SEC-005)
- at least one of:
  - `workOrderId`, `appointmentId`, `mechanicId`, `movementId`, `productId`, `sku`, `partNumber`, `actorId`, `eventType`, `aggregateId`, `correlationId`, `reasonCode`
- `locationIds[]` required for cross-location searches (and requires `audit:scope:cross-location`)

### Pricing evidence APIs (AUD-SEC-008)

- `GET /audit/pricing/snapshot?snapshotId=...`
- `GET /audit/pricing/trace?ruleTraceId=...`
- If traces are large: support pagination by `pageToken` OR server-side truncation with explicit `isTruncated=true` and `nextPageToken`.

### Export APIs (AUD-SEC-006)

- `POST /audit/export/request`
- `GET  /audit/export/status?exportId=...`
- `GET  /audit/export/download?exportId=...`

Export formats:

- CSV required
- JSON optional (only if explicitly required; not default)

---

## Immutability Proof Fields Policy (AUD-SEC-009)

- If `hash`, `prevHash`, `signature` exist:
  - UI may display them read-only with `audit:proof:view`.
  - UI must not claim verification unless verification is implemented server-side and exposed via an explicit field (e.g., `proofVerified=true`).
- If verification is not implemented, UI must label fields as “provided” not “verified”.

---

## Deep-Linking / Drilldown (AUD-SEC-011)

- Store structured link metadata: `{targetDomain, targetType, targetId}`.
- UI treats links as optional and hides absent links.
- Deep links must not bypass authorization; destination screens enforce their own access controls.

---

## Event Type Vocabulary (AUD-SEC-010)

- `eventType` is a controlled vocabulary.
- UI obtains valid values via:
  - `GET /audit/meta/eventTypes` (tenant-scoped)
- UI should not hardcode event types.

---

## Open Questions — Resolved (Full Q/A, with section provenance)

> For each question: the full question is reproduced exactly, followed by a clearly marked response.

### Source: AGENT_GUIDE.md → “Open Questions from Frontend Stories” (issues #105, #125, #86)

#### 1) Backend/Moqui service contracts (blocking)

**Question:** What are the exact Moqui service names and request/response shapes for:

- audit search/list (filters, pagination, sort)
- audit detail by `eventId`
- audit export (filters, format, sync vs async)
- optional search context endpoints (eventType list, location list, reason code display metadata)
- pricing snapshot retrieval by `snapshotId`
- pricing rule trace retrieval (embedded in snapshot vs separate by `ruleTraceId`)

**Response:** Use capability endpoints defined in “API Expectations (Normative)” above. Concrete Moqui service names must map to these capability contracts:

- Search: `GET /audit/logs/search`
- Detail: `GET /audit/logs/detail?eventId=...`
- Export: `POST /audit/export/request`, `GET /audit/export/status`, `GET /audit/export/download`
- Meta: `GET /audit/meta/eventTypes`, `GET /audit/meta/reasonCodes`, `GET /audit/meta/locations` (if UI uses dropdowns)
- Pricing evidence: `GET /audit/pricing/snapshot`, `GET /audit/pricing/trace`
Security constraints and guardrails are mandatory (AUD-SEC-001..006, 008, 010).

#### 2) Query guardrails and date range policy (blocking)

**Question:** Must the UI enforce a mandatory date range and “at least one indexed filter”, or does backend enforce defaults (e.g., last 90 days) and guardrails?

**Response:** Backend must enforce guardrails; UI mirrors them. Date range is mandatory and backend rejects missing ranges with 400 + field errors. (AUD-SEC-005)

**Question:** Does backend enforce a maximum date range window per query (e.g., max 90 days), and should UI enforce the same?

**Response:** Yes. Backend enforces max window **90 days** per query by default; tenant policy may lower but not raise without governance approval. UI enforces same for UX. (AUD-SEC-005)

**Question:** Is timezone interpretation for date filters defined (user locale vs UTC input)? **CLARIFY** canonical behavior.

**Response:** Canonical behavior is **UTC inputs/UTC storage** for audit queries (`fromUtc`, `toUtc`). UI may display in user locale but sends UTC. (AUD-SEC-005)

#### 3) Identifier semantics (blocking)

**Question:** Product identifier for search: `productId`, SKU, part number, or multiple? What is the user-facing identifier in POS?

**Response:** Support multiple identifiers. Canonical internal key is `productId` (UUIDv7). UI also supports `sku` and `partNumber` as user-facing search fields; backend normalizes and indexes all three where available. (AUD-SEC-007)

**Question:** Canonical IDs for movement/workorder/product/location/user: what are they and are they always present on `AuditLog` or only via reference entities/indexes?

**Response:** Canonical IDs are UUIDv7 and should be denormalized onto `AuditLog` when applicable (e.g., `workOrderId`, `movementId`, `productId`, `locationId`, `actorId`) to enable indexed search and avoid payload parsing. (AUD-SEC-007, AUD-SEC-002)

#### 4) Location scoping and multi-location behavior (blocking)

**Question:** Is `locationId` implicitly derived from current POS location context, or must it be selectable as a filter?

**Response:** Default is implicit location context, but `locationId` is still a required stored field. Search requires explicit location filter when cross-location permission is used; otherwise backend implicitly scopes to session location. (AUD-SEC-002)

**Question:** Can auditors search across locations? If yes, what permission gates cross-location queries?

**Response:** Yes, with explicit permission `audit:scope:cross-location` and explicit `locationIds[]` filters. (AUD-SEC-002, AUD-SEC-003)

#### 5) Authorization model (blocking)

**Question:** Which roles besides “Auditor” are allowed to access:

- audit search/detail
- export
- pricing snapshot/trace screens

**Response:** Normative role guidance:

- Shop Manager: search/detail (no export by default)
- Compliance Auditor: search/detail/export/pricing evidence
- Support/Operations: search/detail; raw payload only if explicitly granted
Export and pricing evidence are separately permissioned. (AUD-SEC-003)

**Question:** Provide role names used in Moqui security groups and/or permission strings to check in Moqui screens/services.

**Response:** Permission strings are authoritative in this guide (AUD-SEC-003). Moqui groups should map to them (e.g., `SEC_AUDIT_VIEW`, `SEC_AUDIT_EXPORT`, `SEC_AUDIT_PAYLOAD_VIEW`, `SEC_AUDIT_CROSS_LOCATION`, `SEC_AUDIT_PRICING_EVIDENCE`).

**Question:** Is field-level redaction required for `payloadJson` / `rawPayload` / `quoteContext` / trace `inputs/outputs`?

**Response:** Yes. Backend must support redaction or restricted-field markings; UI must render what backend returns and must not infer/redact. Raw payload is behind permission and safe-rendered as escaped text. (AUD-SEC-004)

#### 6) Navigation and integration points (needs clarification)

**Question:** Where should “Audit Logs” live in POS navigation (menu structure)?

**Response:** Place under an “Administration / Compliance” section with role-based visibility. Add contextual entry points from Work Order, Appointment, Mechanic, Movement, and Estimate Line when the user has `audit:log:view`. (AUD-SEC-011, AUD-SEC-003)

**Question:** Should there be contextual links from Work Order / Appointment / Mechanic / Movement screens?

**Response:** Yes, contextual links are permitted but must pass only identifiers; destination enforces authorization. (AUD-SEC-011)

**Question:** Workexec integration: which exact Workexec screen/component should add “View Pricing Trace”, and what parameter name holds the snapshot reference (`snapshotId` exactly)?

**Response:** The integration must pass `snapshotId` exactly. It should appear on estimate line detail and invoice/estimate review screens wherever pricing evidence is referenced. (AUD-SEC-008)

**Question:** If drilldown links navigate to Movement/Workorder/Product/Location/User detail screens, what are the route/screen names?

**Response:** Do not hardcode URLs in audit records. Store structured link metadata (`targetDomain/targetType/targetId`) and resolve to routes in the frontend router per domain. (AUD-SEC-011)

#### 7) Pricing rule trace format and size (blocking)

**Question:** For `evaluationSteps`, what fields are guaranteed and what is the maximum expected size?

**Response:** Guaranteed fields: `ruleId`, `status`, and a stable step index; `inputs/outputs` are optional and may be redacted. Maximum: backend supports up to **10,000 steps** but must provide pagination/truncation indicators beyond **1,000 steps** to protect UI and APIs. (AUD-SEC-008)

**Question:** Should UI provide step filtering (applied-only) or is full list always required?

**Response:** UI should support filtering (applied-only, rejected-only) client-side when steps are loaded; backend may optionally support server-side filtering for large traces. (AUD-SEC-008)

**Question:** If traces can be large, is server-side pagination or truncation supported/required? **TODO** define contract.

**Response:** Required. Use either `pageToken` pagination or truncation with `isTruncated` + `nextPageToken`. (AUD-SEC-008)

#### Additional TODO/CLARIFY items in AGENT_GUIDE.md text

**Question:** confirm whether hash-chain/signature fields exist and whether UI must display them.

**Response:** If present, display-only behind `audit:proof:view`. UI must not claim verification unless backend exposes explicit verification result. (AUD-SEC-009)

**Question:** Define whether eventType is controlled vocabulary and how UI obtains it.

**Response:** Controlled vocabulary; UI obtains via `GET /audit/meta/eventTypes`. (AUD-SEC-010)

**Question:** Whether trace is embedded in snapshot response or retrieved separately; if separate, define caching/ETag behavior.

**Response:** Retrieve separately by default (`snapshot` includes `ruleTraceId`). Allow embedding only for small traces. Use ETag caching on both endpoints where feasible; do not cache restricted fields across users without permission parity. (AUD-SEC-008, AUD-SEC-004)

**Question:** CLARIFY sync vs async job model for export.

**Response:** Async only; security and scalability require job-based exports with auditable access and time-bound downloads. (AUD-SEC-006)

**Question:** CLARIFY whether field-level redaction is required.

**Response:** Required. (AUD-SEC-004)

---

## Todos Reconciled

- None (the current audit guide uses resolved Q/A blocks rather than free-form todos).

## End

End of document.
