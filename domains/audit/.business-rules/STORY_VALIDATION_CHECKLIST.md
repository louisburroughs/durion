# STORY_VALIDATION_CHECKLIST.md (domain: audit) — Updated

Use this checklist to validate any story implementation in the audit domain, including frontend audit UI (search/list/detail/drilldown/export), and backend responsibilities that the UI depends on (query contracts, authorization, immutability guarantees, retention, and cross-domain integration).

Items are actionable and verifiable.

---

## Scope/Ownership

- [ ] Story explicitly states domain ownership: `domain:audit` owns immutable storage, query/search, export, retention; producer domains own authoritative state and event production.
- [ ] Any domain label conflicts are resolved and recorded in the story header/labels.
- [ ] Producer domains do not write directly to audit storage tables; they publish events / outbox messages.
- [ ] The UI scope is explicitly read-only (search/list/detail/drilldown/export only) and excludes create/update/delete of audit records.
- [ ] Navigation placement is defined (POS menu location + contextual entry points).

---

## Data Model & Validation

- [ ] Audit persistence model supports append-only records (no updates/deletes in normal flows).
- [ ] Stored record includes required normalized fields (as applicable):
  - [ ] `eventId` (UUIDv7; globally unique; dedupe key)
  - [ ] `schemaVersion`
  - [ ] `eventType` (controlled vocabulary)
  - [ ] `occurredAt` (UTC) and `emittedAt` (UTC optional)
  - [ ] `tenantId`
  - [ ] `locationId` (required when applicable; enforced server-side)
  - [ ] `actor` (type + id; displayName optional) (or consistent `actorId`)
  - [ ] `aggregateType` and `aggregateId`
  - [ ] `correlationId` / trace correlation when provided
- [ ] Audit detail supports evidentiary inspection:
  - [ ] Raw payload is available only behind explicit permission and safe rendering (escaped text).
- [ ] Reason fields handled safely:
  - [ ] `reasonNotes` length-limited and safely rendered (no stored XSS).
- [ ] Pricing snapshot/trace validated for UI rendering:
  - [ ] `snapshotId` present on estimate line when drilldown is enabled.
  - [ ] Snapshot includes `snapshotId` and UTC timestamp.
  - [ ] Trace linkage unambiguous (embedded vs `ruleTraceId`).
  - [ ] Large traces handled with pagination/truncation indicators.

---

## API Contract

- [ ] Capability contracts exist for:
  - [ ] Search/list (filters + pagination + sort)
  - [ ] Detail by `eventId`
  - [ ] Pricing snapshot by `snapshotId`
  - [ ] Pricing rule trace retrieval by `ruleTraceId`
  - [ ] Export audit logs (async job)
  - [ ] Meta lookups (eventType list, reason code registry, locations)
- [ ] Tenant scoping enforced server-side.
- [ ] Location scoping enforced server-side.
- [ ] Guardrails enforced server-side:
  - [ ] Date range required (`fromUtc`, `toUtc`)
  - [ ] At least one indexed filter beyond date range
  - [ ] Max date range window (default 90 days)
- [ ] Error responses are consistent and verifiable:
  - [ ] 401 login/session restore flow
  - [ ] 403 access denied without leaking existence
  - [ ] 404 not-found for detail screens
  - [ ] 400 field-level validation errors
  - [ ] 429 rate limiting handled
  - [ ] 5xx/timeouts show generic retryable error

---

## Events & Idempotency

- [ ] Producer uses asynchronous delivery and outbox (or equivalent).
- [ ] Audit ingestion is idempotent (dedupe by `eventId`).
- [ ] Unknown/incompatible schema versions routed to DLQ/quarantine with alerting.
- [ ] Snapshots/traces treated as immutable and read-only.

---

## Security

- [ ] Access control enforced for all read/query/export endpoints:
  - [ ] Tenant isolation enforced server-side
  - [ ] Location scoping enforced server-side
- [ ] Raw payload handling:
  - [ ] Not shown by default; requires explicit permission
  - [ ] Rendered as escaped text (no HTML execution)
  - [ ] Field-level redaction honored (backend-owned)
- [ ] Export security:
  - [ ] Export authorized and audited (who/when/filters)
  - [ ] Export artifacts not publicly accessible; access time-bound
  - [ ] Export jobs non-enumerable across tenants/users
  - [ ] Export includes digest manifest (SHA-256)

---

## Observability

- [ ] Frontend logs include non-sensitive correlation fields (no raw payload).
- [ ] Backend logs include tenant/location/identifiers (no raw payload).
- [ ] Metrics exist for query latency/errors, export job success/failure, and DLQ volume.

---

## Performance & Failure Modes

- [ ] Indexes align to required filters.
- [ ] Unbounded queries prevented (date range + additional filter + max window).
- [ ] Large payload handling safe (collapsed view + pagination/truncation for traces).
- [ ] Failure-mode UI states implemented (401/403/404/429/5xx).

---

## Testing

- [ ] Unit tests cover UI validation rules and safe rendering.
- [ ] Integration tests cover search/detail/snapshot/trace/export flows.
- [ ] Security tests cover tenant isolation, location scoping, and payload access gating.

---

## Documentation

- [ ] UI docs include navigation placement, filter rules, deep-link parameters.
- [ ] API docs include request/response examples and guardrail rules.
- [ ] Data dictionary documents canonical identifiers used in filters.

---

## Open Questions to Resolve — With Responses (Security Domain)

> The following questions are restated verbatim from the prior checklist’s “Open Questions to Resolve” section, followed by responses.

### Q1

**Question:** What are the exact Moqui service names and request/response shapes for:

- audit search/list (filters, pagination, sort)
- audit detail by `eventId`
- pricing snapshot by `snapshotId`
- pricing rule trace retrieval (embedded vs separate)?

**Response:** Define and implement capability endpoints:

- `GET /audit/logs/search`
- `GET /audit/logs/detail?eventId=...`
- `GET /audit/pricing/snapshot?snapshotId=...`
- `GET /audit/pricing/trace?ruleTraceId=...`
Moqui service naming must map to these capability contracts; UI must not hardcode internal service names beyond the public contract.

### Q2

**Question:** What is the canonical date range rule for audit queries: mandatory date range, safe default window (e.g., last 90 days), and/or maximum allowed window?

**Response:** Mandatory date range + max window 90 days enforced server-side. UI mirrors. Dates are UTC (`fromUtc`, `toUtc`).

### Q3

**Question:** Must the UI enforce “at least one indexed filter” beyond date range (and which filters qualify)?

**Response:** Yes. Backend enforces; UI mirrors. Qualifying filters include: `workOrderId`, `appointmentId`, `mechanicId`, `movementId`, `productId`, `sku`, `partNumber`, `actorId`, `eventType`, `aggregateId`, `correlationId`, `reasonCode`.

### Q4

**Question:** What is the canonical product identifier for search: `productId`, SKU, part number, or multiple fields?

**Response:** Multiple. Canonical is `productId` (UUIDv7), with supported search by `sku` and `partNumber` for user workflows.

### Q5

**Question:** Is `locationId` implicitly derived from POS session context, required as a filter, or can auditors search across locations?

**Response:** Default implicit scoping to session location. Cross-location search is allowed only with `audit:scope:cross-location` and explicit `locationIds[]` filters.

### Q6

**Question:** Which roles/permissions can access:

- audit search/list/detail
- pricing snapshot/trace screens
- export (if present)?

**Response:** Use permission strings:

- View/search: `audit:log:view`
- Detail: `audit:log:view-detail`
- Pricing snapshot: `audit:pricing-snapshot:view`
- Pricing trace: `audit:pricing-trace:view`
- Export: `audit:export:execute` and download: `audit:export:download`
- Raw payload: `audit:payload:view` (restricted)
- Cross-location: `audit:scope:cross-location`

### Q7

**Question:** Where exactly should “Audit Logs” live in POS navigation, and which contextual links are required (Work Order / Appointment / Mechanic / Movement / Estimate Line)?

**Response:** Place under “Administration / Compliance.” Provide contextual links from the listed screens when the user has `audit:log:view`. Links pass identifiers only; destination enforces authorization.

### Q8

**Question:** What is the Workexec integration point for “View Pricing Trace”, and what parameter name carries the snapshot reference (`snapshotId` exactly)?

**Response:** Parameter name must be `snapshotId`. Integration points: estimate line detail and any estimate/invoice review UI where snapshot references are shown.

### Q9

**Question:** For `evaluationSteps[]` in rule trace:

- Which fields are guaranteed?
- What is the maximum expected size?
- Is step filtering (e.g., applied-only) required or optional?

**Response:** Guaranteed: `ruleId`, `status`, stable step index. Inputs/outputs may be redacted/optional. Support pagination/truncation beyond 1,000 steps; tolerate up to 10,000 steps in total. Filtering is optional but recommended.

### Q10

**Question:** Does backend expose immutability proof fields (`hash`/`prevHash`/`signature`), and is the UI required to display and/or verify them?

**Response:** If exposed, UI may display read-only behind `audit:proof:view`. UI must not claim verification unless backend returns explicit verification status.

### Q11

**Question:** Export requirements (if in scope): formats (CSV only vs CSV+JSON/PDF), sync vs async job, filename convention, and whether digest/manifest is required.

**Response:** CSV required; JSON optional only by explicit requirement. Export is async job only. Filenames must avoid sensitive data. Digest/manifest (SHA-256) required.

### Q12

**Question:** Will backend return `reasonCode` only, or also `displayName/description` for UI display?

**Response:** Backend should return `reasonCode` and may optionally include `displayName/description` from the registry via a meta endpoint; UI must not invent labels.

### Q13

**Question:** What are the canonical route/screen names for drilldown targets (Movement/Workorder/Product/Location/User) if deep links are shown?

**Response:** Do not store routes in audit data. Store structured link metadata (`targetDomain/targetType/targetId`). Frontend resolves to routes per domain router; authorization enforced by destination.

---

# End of STORY_VALIDATION_CHECKLIST.md
