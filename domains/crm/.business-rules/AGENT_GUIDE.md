```markdown
# CRM Domain Agent Guide

## Purpose
The CRM domain is the authoritative system of record (SoR) for customer relationship data within the modular POS system. It manages party (individuals and organizations), contact points, vehicles, party relationships, communication preferences, promotions/redemptions, and CRM-owned “snapshot” read models used by downstream domains (Workorder Execution, Billing).

This guide is written for engineers implementing or integrating CRM features (Moqui services/screens, REST endpoints, and event handlers) with secure-by-default patterns, explicit invariants, and operational guidance.

---

## Domain Boundaries

### Authoritative Ownership (Write)
CRM owns and is the only domain allowed to **mutate**:
- **Party** master data (Persons, Organizations/Accounts)
- **Contact points** (email/phone) and primary flags
- **Party relationships** (e.g., account contacts + roles like BILLING/APPROVER/DRIVER)
- **Vehicles** and vehicle-party associations (owner/driver/lessee)
- **Communication preferences / consent** (contactability + billing-relevant preferences)
- **Vehicle care preferences/notes** (vehicle-specific service preferences + audit trail) *(new from frontend stories; backend contract TBD)*
- **Promotion redemption records** (idempotent tracking of redemption events)

### CRM-Owned Read Models (Read APIs)
CRM provides stable read models for consumers:
- **CRM Snapshot** (`GET /v1/crm-snapshot`) — account/person + contacts + vehicles + preferences (contactability + billing-relevant only). This is explicitly CRM-owned even if consumers are Billing/Workexec.

### Read-Only Consumers (No Writes)
- Workorder Execution, Billing, and other domains **consume** CRM data but do not modify CRM entities directly.
- POS frontend screens may be read-only for some operational surfaces (e.g., ingestion logs, redemption viewer) unless explicitly authorized and designed for mutation.

### Event-Driven Integration Boundary
- CRM **consumes** inbound events from Workorder Execution (e.g., `VehicleUpdated`, `PromotionRedeemed`, `ContactPreferenceUpdated`, `PartyNoteAdded`).
- CRM **emits** outbound domain events for downstream consumers (e.g., `PERSON_CREATED`, vehicle association events).
- CRM maintains **operational processing logs** and a **suspense/DLQ** mechanism for failed inbound events; frontend stories add operator-facing UI for these.

**CLARIFY:** Whether CRM exposes operator actions (retry/reprocess/acknowledge) on suspense items is explicitly out-of-scope unless confirmed.

---

## Key Entities / Concepts

### Party / Person / Organization(Account)
- **Party** is the canonical identifier for customer entities.
- **Person** and **Organization/Account** are party subtypes.
- Identifiers:
  - Existing guide uses `personId` and `partyId`. Frontend stories frequently ask whether routes use `partyId`, `personId`, or `customerId`.
  - **TODO:** Standardize on `partyId` as the canonical identifier in frontend routes and service contracts; document mapping if legacy IDs exist.

### ContactPoint
- Multiple emails/phones per party.
- Primary per kind (`EMAIL`, `PHONE`) invariant (see below).
- Used by snapshot and by account contact role assignment screens.

### PartyRelationship (Account Contacts / Roles)
- Links parties with roles such as `BILLING`, `APPROVER`, `DRIVER`.
- Supports “primary” designation per role (policy must be explicit; see invariants).
- Also ties into billing preferences such as invoice delivery method.

**CLARIFY:** Domain ownership for “contact roles” is questioned in frontend stories (CRM vs Payment/Billing labels). CRM should remain SoR; update labels accordingly.

### Vehicle
- CRM-owned vehicle master record.
- Identified by `vehicleId` (UUID).
- Attributes: VIN, unit number, license plate, description, mileage, etc.

### VehiclePartyAssociation
- Links vehicle to parties with relationship types (e.g., `PRIMARY_OWNER`, `OWNER`, `LESSEE/DRIVER`).
- Effective dating and deterministic resolution rules are required for snapshot (see below).

### Vehicle Care Preferences / Notes *(new)*
- Vehicle-specific service preferences: tire brand/line, rotation interval + unit, alignment preference, torque notes, service notes.
- Must be editable from vehicle profile and visible read-only in estimate/workorder contexts.
- Requires audit history (who/when; ideally field-level diffs).

**TODO:** Confirm entity name (`VehiclePreference` vs `VehicleCarePreference`) and whether schema is fixed vs dynamic key/value.

### CommunicationPreference
- Contactability + consent flags (email/sms/phone opt-in, do-not-contact, preferred method/language).
- Account-level preferences include invoice delivery method and marketing opt-out.

### PromotionRedemption
- CRM record of promotion usage linked to work order and invoice.
- Used for auditing and abuse detection; frontend story adds read-only viewer.

### CustomerSnapshot (CRM Snapshot DTO)
- Aggregated read model returned by `GET /v1/crm-snapshot`.
- Includes snapshot metadata (version/timestamp), account summary, contacts, vehicles, and preferences.

### ProcessingLog / SuspenseQueue (Operational Integration Surfaces) *(expanded)*
- **ProcessingLog**: operational record of inbound event processing outcomes (success/failure/duplicate/etc.).
- **SuspenseQueue/DLQ**: records for failed/unprocessable events requiring triage.
- Frontend stories require list/detail screens with filtering by eventId/correlationId/status/type/date.

**CLARIFY:** Whether `ProcessingLog` is a single shared entity across event types or per-integration (vehicle ingestion vs generic inbound events) is unclear; align naming and fields.

---

## Invariants / Business Rules

### Party & Identity
- Party creation requires required name fields (e.g., `firstName`, `lastName` for Person).
- Party identifiers are immutable once created.
- Merge behavior:
  - Source party becomes `MERGED`.
  - Child entities reassociate to survivor.
  - Alias/redirect behavior must be consistent (PartyAlias).

### Contact Points
- A party can have zero or more contact points.
- **Exactly one primary per kind** (`EMAIL`, `PHONE`) at a time.
- Format validation:
  - Email must be valid format.
  - Phone validation policy must be explicit (E.164 vs digits-only vs extensions). **CLARIFY** (frontend story asks).

### Party Relationships / Contact Roles
- No overlapping active relationships for same party pair + role.
- Primary behavior:
  - When setting a new primary for a role, system must either:
    - auto-demote previous primary, or
    - reject until cleared.
  - **CLARIFY** which policy applies (frontend blocker).
- Billing constraints:
  - If invoice delivery method is `EMAIL`, a billing contact may be required. **CLARIFY** config scope (global vs per account vs derived only).

### Vehicle Ownership & Snapshot Resolution
- Exactly one active `OWNER` (or `PRIMARY_OWNER`) per vehicle at a time (depending on model).
- Creating a new owner association must atomically end-date the previous owner association.
- Snapshot resolution (backend story #99, authoritative):
  - When only `vehicleId` is provided, CRM resolves a single primary party by precedence:
    1. `PRIMARY_OWNER` (ACTIVE)
    2. else `OWNER` (ACTIVE; most recent)
    3. else `LESSEE/DRIVER` (ACTIVE; most recent)
    4. else any ACTIVE relationship (most recent)
  - Tie-breaker: latest `effectiveFrom`, then latest `updatedAt`.
  - If no active relationships: `404` with `errorCode=VEHICLE_HAS_NO_ACTIVE_PARTY`.

### VehicleUpdated Ingestion (Operational)
- Event processing must be idempotent.
- Conflicts must follow a defined policy (last-write-wins vs pending review).
- Frontend story expects statuses like:
  - `SUCCESS`, `DUPLICATE`, `ERROR_VALIDATION`, `ERROR_NOT_FOUND`, `PENDING_REVIEW`.

**CLARIFY:** Conflict resolution policy for mileage decreases, VIN changes, etc., and when `PENDING_REVIEW` is used.

### Vehicle Care Preferences
- Rotation interval must be integer if provided.
- Unit enum must be explicit (e.g., `MILES`, `KM`). **TODO/CLARIFY** min/max constraints and whether unit is required when interval is present.
- Updates must be auditable and concurrency-safe (optimistic locking preferred). **CLARIFY** version/ETag behavior.

### Promotion Redemption
- Idempotent processing keyed by a stable dedupe key (existing guide says `promotionId + workOrderId`).
- Redemption records should link to workOrderId and invoiceId (nullable if not available).
- Usage limits enforcement exists “when configured” but ownership/visibility is unclear.
  - **CLARIFY:** whether CRM enforces limits or only records redemptions; whether UI should show “limit reached” indicators.

### Integration Monitoring (ProcessingLog + Suspense)
- Logs must be queryable by operational identifiers:
  - `eventId`, `correlationId` (workorder reference), event type/version/source, timestamps, status, failure reason.
- Payload visibility must be safe:
  - Prefer returning a sanitized `payloadSummary` rather than raw payload.
  - **CLARIFY:** redaction rules and whether raw payload can ever be displayed.

---

## Events / Integrations

### Inbound Events (from Workorder Execution)
- `VehicleUpdated`
- `PromotionRedeemed`
- `ContactPreferenceUpdated`
- `PartyNoteAdded`

Operational requirements from frontend stories:
- Each inbound event should produce:
  - a ProcessingLog record (success/failure/duplicate)
  - a SuspenseQueue record when unprocessable or retries exhausted
  - correlation identifiers for traceability (`eventId`, `correlationId`/workorder reference)

**CLARIFY:** Whether `correlationId` is always `workorderId`, or a separate correlation identifier that may include estimateId.

### Outbound Events (from CRM)
- `PERSON_CREATED`
- `VehicleOwnerAssociated`, `VehicleOwnerReassigned`, `VehiclePrimaryDriverAssigned`
- Audit events for:
  - contact point changes
  - communication preference updates
  - party merges
  - relationship changes
  - vehicle care preference changes *(new; ensure audit event exists or audit log is queryable)*

### Integration Patterns
- **Idempotency:** inbound events must be deduped by `eventId` (preferred) and/or domain-specific dedupe keys.
- **Suspense/DLQ:** failures route to suspense with enough metadata to triage without DB access.
- **Read models:** snapshot endpoint is cache-friendly and versioned; consumers should treat it as authoritative for association rules.

---

## APIs

### CRM Snapshot (Confirmed Contract)
- `GET /v1/crm-snapshot?partyId={uuid}&vehicleId={uuid}`
- At least one identifier required; else `400`.
- `403` if caller not allowlisted and/or missing scope `crm.snapshot.read`.
- `404` with `errorCode=VEHICLE_HAS_NO_ACTIVE_PARTY` when vehicle exists but has no active party relationship.

**Implementation guidance (secure-by-default):**
- Prefer server-side calls from Moqui to avoid exposing service tokens to browsers.
- Propagate correlation IDs across calls (see Observability).

### Vehicle Search + Vehicle+Owner Snapshot (Proposed, not confirmed)
Frontend story references:
- `POST /api/v1/vehicles/search` with `{ query }`
- `GET /api/v1/vehicles/{vehicleId}?include=owner`

**TODO/CLARIFY:** Confirm endpoints, response schema, pagination/limits, ranking fields (`rankScore`/`matchType`), and minimum query length.

### Promotion Redemption Read APIs (Not confirmed)
Frontend requires:
- list/search with pagination/sorting and filters: promotionId, workOrderId, invoiceId, customerId, date range
- detail by `promotionRedemptionId`

**TODO/CLARIFY:** Provide Moqui service names or REST endpoints and canonical read model (`PromotionRedemption` vs additional entities).

### Vehicle Care Preferences APIs (Not confirmed)
Frontend requires:
- load by `vehicleId`
- create/update/upsert by `vehicleId`
- audit history by `vehicleId`

**TODO/CLARIFY:** Provide service names, parameters, response schema, and error payload shape (field-level errors, 409 conflicts).

### Integration Monitoring APIs (Not confirmed)
Frontend requires:
- ProcessingLog list/detail
- SuspenseQueue list/detail

**TODO/CLARIFY:** Provide service names/entity views, required parameters, and returned fields; define identity key for suspense items.

---

## API Expectations (Patterns)

### Versioning & Compatibility
- Public/consumer-facing REST endpoints must be versioned (`/v1/...`).
- Backward compatible changes only within a version.

### Pagination & Filtering
- All list endpoints/services must support:
  - `pageIndex`, `pageSize`
  - stable sorting (`orderBy`)
- Filtering semantics must be explicit and index-friendly:
  - **CLARIFY:** correlationId filtering (exact vs prefix vs contains). Prefer exact or prefix for performance unless strong need for contains.

### Error Contracts
- Standardize error payload shape for frontend mapping:
  - `errorCode` (string)
  - `message` (string)
  - `fieldErrors` (map) when applicable
  - `correlationId` (string) when available
- Use:
  - `400` for validation
  - `403` for authorization
  - `404` for not found
  - `409` for optimistic locking conflicts
  - `5xx` for unexpected errors

### Idempotency
- Event ingestion endpoints/handlers must be idempotent by `eventId`.
- Promotion redemption must be idempotent by a stable dedupe key (document exact key in backend contract).

---

## Security / Authorization Requirements

### General
- All APIs require authentication.
- Enforce authorization at the backend (Moqui artifact auth + service-level checks).
- Do not expose secrets or internal stack traces to the frontend.

### Service-to-Service (Snapshot)
- Snapshot endpoint requires:
  - allowlisted service identity
  - scope `crm.snapshot.read`

### User-Level RBAC (Frontend Screens)
Frontend stories introduce multiple user-facing screens requiring explicit permissions:

1. **Integration Monitoring (ProcessingLog + Suspense/DLQ)**
   - Proposed permission: `crm.integration.monitor.read` (placeholder)
   - **CLARIFY:** exact permission keys/roles and whether access is restricted by environment (prod vs non-prod).

2. **Vehicle Ingestion Logs (VehicleUpdated processing)**
   - Proposed permissions: `crm.audit.read` and/or `crm.vehicle.read` (placeholder)
   - **CLARIFY:** exact roles/scopes and any org/location restrictions.

3. **Promotion Redemption Viewer**
   - Proposed permissions: `crm.promotions.read` / `crm.redemptions.read` (placeholder)
   - **CLARIFY:** whether CSR vs Marketing have different access.

4. **Vehicle Search + Vehicle Snapshot**
   - Proposed permissions: `crm.vehicle.read`, `crm.snapshot.read` (placeholder)
   - **CLARIFY:** UI behavior on missing permission (hide entry point vs show forbidden).

5. **Vehicle Care Preferences**
   - Separate permissions for:
     - view preferences
     - edit preferences
     - view audit history
   - **CLARIFY:** enforcement mechanism (artifact auth vs permission service vs route guards).

### Sensitive Data Handling
- VIN/license plate and payload/details fields may be sensitive.
- **CLARIFY:** masking policy for VIN/plate in UI and logs.
- For integration payloads:
  - Prefer backend-provided sanitized fields (`payloadSummary`, `redactedPayload`) over raw JSON.
  - Never log raw payloads client-side.

---

## Observability (Logs / Metrics / Tracing)

### Correlation & Traceability
- Every inbound event should carry:
  - `eventId` (UUID)
  - `correlationId` (workorder reference; may be workorderId)
  - `sourceSystem`, `eventType`, `eventVersion`
- Persist these into ProcessingLog and SuspenseQueue for operator UI and debugging.

### Logging (Backend)
- Structured logs for:
  - inbound event receipt
  - processing outcome (status)
  - suspense routing
  - snapshot requests (caller identity + identifiers)
- Include `correlationId` and `eventId` in log fields.
- Avoid logging PII and free-form notes; if necessary, mask.

### Metrics (Backend)
Minimum recommended metrics (tagged by eventType/caller/status):
- `crm_inbound_events_total{eventType,status}`
- `crm_inbound_event_processing_latency_ms{eventType}`
- `crm_suspense_queue_depth{eventType}` (gauge)
- `crm_snapshot_requests_total{status,caller}`
- `crm_snapshot_latency_ms{status,caller}`
- `crm_vehicle_search_requests_total{status}` *(if/when implemented)*
- `crm_promotion_redemption_queries_total{status}` *(if/when implemented)*

### Tracing
- Propagate trace context from:
  - inbound event envelope (if present) into processing spans
  - REST calls (snapshot/search/redemptions) into distributed traces
- Ensure UI surfaces show `eventId` and `correlationId` for human correlation with traces.

### Operator UI Observability
For the integration monitoring screens:
- Ensure list/detail DTOs include:
  - timestamps (received/processed/routed)
  - status/failure reason
  - identifiers (eventId, correlationId)
  - retry/attempt metadata if available (**CLARIFY**)

---

## Testing Guidance

### Unit Tests
- Validate invariants:
  - primary contact per kind
  - primary role per account (if applicable)
  - vehicle association uniqueness and end-dating
  - promotion redemption idempotency key behavior
  - rotation interval validation and unit enum validation (once defined)

### Integration Tests (Service/API)
- Snapshot endpoint:
  - partyId path
  - vehicleId-only resolution precedence and tie-breakers
  - 400 missing identifiers
  - 404 vehicle has no active party (`errorCode=VEHICLE_HAS_NO_ACTIVE_PARTY`)
  - 403 allowlist/scope enforcement
- Vehicle search (once contract confirmed):
  - minimum query length
  - ranking/matchType fields
  - pagination/limit behavior
- Promotion redemption read APIs:
  - pagination/sorting
  - filter combinations
  - authorization
- Vehicle care preferences:
  - create/update/upsert behavior
  - 404 empty state semantics
  - 409 optimistic locking behavior (if used)
  - audit history retrieval

### Event Processing Tests
- Idempotency:
  - duplicate `eventId` produces `SKIPPED_DUPLICATE`/`DUPLICATE` status without double-apply
- Failure routing:
  - schema validation failure routes to suspense with reason
  - missing entity routes to suspense with reason
- ProcessingLog correctness:
  - required fields persisted and queryable for UI

### Contract Tests
- Snapshot DTO schema stability (consumer-facing).
- ProcessingLog/Suspense DTOs for operator UI (once defined).
- Error payload shape consistency (fieldErrors, errorCode, correlationId).

### Security Tests
- RBAC enforcement:
  - unauthorized users cannot access integration monitoring, ingestion logs, redemptions, preferences edit
- Data leakage:
  - ensure payload/details redaction rules are enforced server-side
  - ensure logs do not include raw payloads or free-form notes

### End-to-End Tests
- Vehicle lookup → select → snapshot load → handoff to estimate flow (mechanism TBD).
- Vehicle preferences visible in vehicle profile and read-only in estimate/workorder screens.
- Integration monitoring screens load lists and details with filters and handle 403/404/empty states.

---

## Common Pitfalls / Gotchas

1. **Unclear identifier conventions (`partyId` vs `personId` vs `customerId`)**
   - Leads to broken routes and incorrect service calls.
   - **TODO:** Standardize and document canonical identifiers per entity.

2. **CorrelationId filter semantics**
   - “contains” searches can cause full table scans.
   - Prefer exact/prefix with indexes; only allow contains if indexed/trigram supported.
   - **CLARIFY** required semantics.

3. **Payload/details exposure**
   - Rendering raw JSON payloads or `details` can leak PII/secrets.
   - Require backend-provided sanitized fields; treat all payload as untrusted text (no HTML rendering).

4. **Primary flag race conditions**
   - Concurrent updates to primary contact/role can violate invariants.
   - Use transactional updates and unique constraints where possible; return 409 on conflict.

5. **VehicleUpdated conflict policy not defined**
   - Without explicit policy, ingestion may silently corrupt data (e.g., mileage decreases).
   - **CLARIFY** and encode policy in handler + surfaced status (`PENDING_REVIEW`).

6. **Snapshot misuse**
   - Snapshot is a read model; do not treat it as a write API.
   - Ensure consumers do not cache beyond intended TTL without version checks.

7. **Operational UI without backend RBAC**
   - Direct entity-find from screens can bypass redaction/RBAC.
   - Prefer services returning safe DTOs for ProcessingLog/Suspense/Redemptions.

8. **Audit history expectations mismatch**
   - UI needs to know whether audit provides field-level diffs or only “who/when”.
   - **CLARIFY** audit payload shape early to avoid rework.

---

## Open Questions from Frontend Stories

### A) Integration Monitoring (ProcessingLog + Suspense/DLQ)
1. **Backend contract:** Exact Moqui services/entity views to query `ProcessingLog` and `SuspenseQueue` (service names, params, returned fields).
2. **Suspense identity:** Is suspense addressed by `eventId` or a separate `suspenseId`/messageId?
3. **Payload visibility/redaction:** Can UI display raw payload JSON? If yes, what redaction rules apply? Is there a safe `payloadSummary`?
4. **Permissions/RBAC:** Exact permission keys/roles (e.g., `crm.integration.monitor.read`) and environment restrictions (prod vs non-prod).
5. **Filter semantics:** `correlationId` filtering exact vs prefix vs contains.
6. **Retry/attempt details:** Should UI expose retry count/last error/next retry timestamp or only final status?
7. **Operator actions:** Should UI support retry/reprocess/ack/export/mark resolved? If yes, define allowed actions + audit requirements.

### B) Promotion Redemption Viewer
1. **Frontend scope:** Confirm expected UX: read-only redemption viewer vs promotion detail enhancement vs no UI.
2. **API/service contract:** Actual Moqui services/REST endpoints for listing/getting `PromotionRedemption` (pagination/sorting/filters).
3. **Authorization model:** Required permissions/roles; CSR vs Marketing access differences.
4. **Canonical read model:** Is `PromotionRedemption` the only entity exposed, or also `PromotionUsage`, `RedemptionEvent`, `ProcessingLog`?
5. **Usage limits visibility:** Should UI show “limit reached” indicators? What backend field/source provides this?
6. **Identifier formats:** Are `workOrderId` and `invoiceId` UUIDs or external human-readable references?

### C) VehicleUpdated Ingestion Log UI
1. **Conflict resolution policy:** How to handle mileage decreases, VIN changes; when to use `PENDING_REVIEW` vs last-write-wins.
2. **Data fields:** Does ProcessingLog include `estimateId` in addition to `workorderId`? If yes, exact field name.
3. **Security:** Roles/scopes required to view ingestion logs; restrictions by location/org.
4. **Backend contract:** Actual Moqui services/screen paths/endpoints to query ProcessingLog and Vehicle; entity-find vs REST.
5. **Sensitive details:** Can `details` contain customer data/notes? Masking/redaction rules for UI.

### D) Vehicle Care Preferences
1. **Backend contract (blocking):** Endpoints/services for load by `vehicleId`, upsert, and audit history; include error schema and field-level errors.
2. **Schema model:** Fixed schema vs dynamic key/value preferences (EAV/JSON).
3. **Authorization:** Permissions for view/edit/audit; enforcement mechanism (artifact auth vs permission service vs route guards).
4. **Audit detail:** Field-level diffs/before-after vs summary only.
5. **Concurrency/versioning:** Version/ETag/lastUpdatedStamp requirements; expected 409 behavior.
6. **Rotation interval unit enum:** Allowed units; whether unit required; min/max constraints.

### E) Vehicle Lookup (Search + Select Snapshot)
1. **Backend API contract (blocking):** Confirm endpoints/methods/response shapes for vehicle search and vehicle+owner snapshot.
2. **Ranking logic:** Expected ranking rules; whether backend returns `rankScore`/`matchType`.
3. **Partial search rules:** Minimum query length; contains vs startsWith for VIN/unit/plate.
4. **Result limiting/pagination:** Max results; truncation indicator; pagination support.
5. **Permission model:** Roles/scopes for vehicle search and snapshot view; UI behavior when missing permission.
6. **PII display policy:** Whether full VIN/license plate can be displayed and logged; masking standard.
7. **Caller handoff mechanism:** How VehicleLookup returns selected context to estimate creation flow in Moqui (transition params vs shared context vs redirect).

### F) Additional CRM Frontend Areas (from consolidated questions)
1. **Contact points:** Canonical identifier (`partyId` vs `personId` vs `customerId`), kind mutability, route conventions, phone validation policy, permissions split (view vs manage).
2. **Communication preferences:** Missing record behavior (404 vs 200 empty), `updateSource` handling, whether separate `ConsentRecord` exists, optimistic locking.
3. **Account contact roles:** Domain ownership (CRM vs Payment/Billing), backend contract for loading/updating roles + invoice delivery method, primary auto-demotion policy, role list source (fixed vs backend-driven), which roles support “Primary”, permissions.
4. **Party merge:** Search contract, merge contract, criteria requirements, alias behavior, party types allowed, authorization, post-merge visibility, conflict resolution policy.
5. **Create commercial account:** Domain label confirmation (CRM SoR), duplicate detection inputs and contract, accountStatus defaulting, billing terms source, external identifiers structure, post-create navigation route.

---

*This guide summarizes the CRM domain’s responsibilities, invariants, integration surfaces, and operational requirements as implied by current frontend stories. Items marked TODO/CLARIFY must be resolved to finalize service contracts and UI behavior safely.*
```
