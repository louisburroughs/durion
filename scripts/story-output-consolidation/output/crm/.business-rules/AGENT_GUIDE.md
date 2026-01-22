# AGENT_GUIDE.md

## Summary

This document is the normative agent guide for the CRM domain: system-of-record boundaries, invariants, and integration rules for CRM UI and services. It resolves prior CLARIFY/TODO items by making conservative, explicit decisions with stable IDs and links to detailed rationale in `DOMAIN_NOTES.md`. Where upstream contracts are not yet finalized, it adopts safe defaults and calls out what is safe-to-defer.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `DOMAIN_NOTES.md`
- [x] Reconciled todos from original AGENT_GUIDE

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-INVENTORY-001 | Canonical identifiers (`partyId`) |
| DECISION-INVENTORY-002 | Contact roles ownership + primary policy + billing contact rule |
| DECISION-INVENTORY-003 | Contact point validation (phone) + kind immutability |
| DECISION-INVENTORY-004 | CRM Snapshot proxy + correlation/logging policy |
| DECISION-INVENTORY-005 | ProcessingLog/Suspense contracts + identifiers |
| DECISION-INVENTORY-006 | Payload/details visibility + redaction/masking |
| DECISION-INVENTORY-007 | Permissions + environment restrictions |
| DECISION-INVENTORY-008 | Correlation semantics + filter semantics |
| DECISION-INVENTORY-009 | Operator actions + retry metadata |
| DECISION-INVENTORY-010 | Promotion redemption model, access, and limits visibility |
| DECISION-INVENTORY-011 | VehicleUpdated conflict policy + required fields |
| DECISION-INVENTORY-012 | Vehicle care preferences schema, enums, locking, audit |
| DECISION-INVENTORY-013 | Vehicle lookup/search contract defaults + handoff |
| DECISION-INVENTORY-014 | Party merge policy + alias behavior |
| DECISION-INVENTORY-015 | Commercial account creation + duplicate detection |

## Domain Boundaries

- What CRM owns (system of record)
  - Party master data (Person/Organization/Account) and party lifecycle state
  - Contact points (email/phone) and primary flags
  - Party relationships, including account contact roles and vehicle-party associations
  - Vehicles (VIN/unit/plate/attributes) and ownership/association history
  - Communication preferences (contactability + consent)
  - Promotion redemption records (authoritative recording of redemption events)
  - CRM Snapshot read model and deterministic resolution rules
  - Operational integration read models for inbound event outcomes (ProcessingLog/Suspense)
  - **Party merge commands and merge audit artifacts** (merge is a privileged CRM-owned mutation; downstream consumes alias/redirect behavior). (DECISION-INVENTORY-014)
  - **Commercial account creation including duplicate detection workflow and override auditing** (create is CRM-owned; Billing terms are referenced from Billing-owned data). (DECISION-INVENTORY-015)

- What CRM does *not* own
  - Work order lifecycle (Workexec)
  - Invoice issuance lifecycle (Billing)
  - Payment capture/settlement/refund execution (Billing/Payment)
  - Promotion policy enforcement beyond recording redemption events (unless explicitly contracted)
  - **Billing terms master data** (Billing-owned reference data; CRM stores selected term identifier on the account). (DECISION-INVENTORY-015)
  - **Outbound notification delivery** (Workexec/Notification domain consumes CRM preferences; CRM records preferences only). (Communication preferences are CRM-owned; delivery is not.)

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| Party | Canonical customer identifier; subtype is Person or Organization/Account. |
| ContactPoint | Email/phone contact points for a party; supports primary per kind. |
| PartyRelationship | Relationship between parties with role codes (BILLING/APPROVER/DRIVER). |
| Vehicle | CRM-owned vehicle record. |
| VehiclePartyAssociation | Effective-dated vehicle-to-party association (owner/driver/lessee). |
| CustomerSnapshot | Aggregated CRM Snapshot DTO for downstream consumers. |
| VehicleCarePreference | Fixed-schema vehicle-specific service preferences with audit history. |
| PromotionRedemption | CRM record of a promotion redemption event (idempotent). |
| ProcessingLog | Operational read model for inbound event processing outcomes. |
| SuspenseItem | Operational read model for unprocessable events requiring triage. |
| **CommunicationPreference** | CRM-owned per-person preference/consent record (preferred channel + consent flags + audit metadata). **CLARIFY** exact DTO/endpoint names; entity name is normative for UI semantics. |
| **MergeAudit** | CRM-owned audit record created when a Party merge command succeeds; includes actor, justification, timestamps, and affected party IDs. (DECISION-INVENTORY-014) |
| **PartyAlias / MergeMapping** | CRM-owned mapping from merged (source) partyId to survivor partyId; used to return deterministic `409 PARTY_MERGED` with `mergedToPartyId`. (DECISION-INVENTORY-014) |
| **BillingTerms (reference)** | Billing-owned reference data; CRM stores selected `billingTermsId` (or equivalent) on commercial account profile. (DECISION-INVENTORY-015) |

## Invariants / Business Rules

- Identifiers
  - `partyId` is the canonical identifier for customer parties; do not invent `customerId` as a separate primary key. (DECISION-INVENTORY-001)

- Contact points
  - At most one primary per party per kind (`EMAIL`, `PHONE`).
  - Contact point kind is immutable after creation. (DECISION-INVENTORY-003)
  - Phone input is normalized and validated as described in DECISION-INVENTORY-003.

- Contact roles
  - CRM is the system of record for account-contact roles; downstream domains consume read-only. (DECISION-INVENTORY-002)
  - Setting a new primary for a role auto-demotes the prior primary atomically. (DECISION-INVENTORY-002)
  - Default validation: if `invoiceDeliveryMethod=EMAIL`, require a billing contact with a primary email. (DECISION-INVENTORY-002, safe-to-defer exact configuration scope)
  - **UI/Service contract invariant:** role updates must be applied transactionally per account to preserve the single-primary-per-role invariant under concurrency. **TODO** confirm whether backend updates are per-contact or per-account; either way must be atomic for the role+primary constraint.

- Snapshot
  - Snapshot is a CRM-owned read model; consumers must not treat it as a write API.
  - Browser does not call snapshot directly; it is proxied server-side via Moqui. (DECISION-INVENTORY-004)

- Integration monitoring
  - ProcessingLog and Suspense are read-only UI surfaces by default. (DECISION-INVENTORY-009)
  - Raw payload visibility is default-deny; use backend-provided redacted fields. (DECISION-INVENTORY-006)

- VehicleUpdated ingestion
  - Processing is idempotent by `eventId`.
  - Destructive conflicts use `PENDING_REVIEW` and do not auto-apply. (DECISION-INVENTORY-011)

- Vehicle care preferences
  - Fixed schema, optimistic locking, and audit history are required. (DECISION-INVENTORY-012)

- Party merge
  - Merge is a privileged CRM-owned command; only merge same subtype; survivor wins. (DECISION-INVENTORY-014)
  - Reads by merged party return `409 PARTY_MERGED` with `mergedToPartyId` (do not silently redirect). (DECISION-INVENTORY-014)
  - **Justification is required** for merge commands and must be stored in MergeAudit. **CLARIFY** min/max length and visibility in audit views (frontend story suggests 5–500 chars).

- Commercial account creation
  - Create commercial account is CRM-owned; must return stable `partyId` on success. (DECISION-INVENTORY-001, DECISION-INVENTORY-015)
  - Duplicate detection is required: duplicate-check then create; create may also return `409 DUPLICATE_CANDIDATES` as a fallback. (DECISION-INVENTORY-015)
  - Duplicate override requires explicit confirmation and a justification; backend must record justification for audit. (DECISION-INVENTORY-015)
  - Billing terms are Billing-owned reference data; CRM stores selected term identifier. (DECISION-INVENTORY-015)

- Communication preferences
  - Communication preferences are CRM-owned per-person records; downstream consumes read-only.
  - **CLARIFY** consent semantics (e.g., whether `null` is allowed and how it is interpreted) and whether a separate `ConsentRecord` exists; UI must not invent semantics beyond backend contract.

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-INVENTORY-001 | Canonical ID strategy for CRM routes/contracts | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-001--canonical-identifiers-partyid) |
| DECISION-INVENTORY-002 | Roles owned by CRM; primary auto-demotion; billing contact rule | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-002--contact-roles-ownership--primary-policy--billing-contact-rule) |
| DECISION-INVENTORY-003 | Phone validation/normalization; contact kind immutability | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-003--contact-point-validation-phone--kind-immutability) |
| DECISION-INVENTORY-004 | Snapshot proxy; correlation and logging/masking | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-004--crm-snapshot-proxy--correlationlogging-policy) |
| DECISION-INVENTORY-005 | ProcessingLog/Suspense contracts and identifiers | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-005--processinglogsuspense-contracts--identifiers) |
| DECISION-INVENTORY-006 | Payload/details visibility and redaction | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-006--payloaddetails-visibility--redactionmasking) |
| DECISION-INVENTORY-007 | Permission keys and environment restrictions | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-007--permissions--environment-restrictions) |
| DECISION-INVENTORY-008 | Correlation semantics and filter semantics | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-008--correlation-semantics--filter-semantics) |
| DECISION-INVENTORY-009 | Operator actions and retry metadata | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-009--operator-actions--retry-metadata) |
| DECISION-INVENTORY-010 | Promotion redemption model/access/limits | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-010--promotion-redemption-model-access--limits-visibility) |
| DECISION-INVENTORY-011 | VehicleUpdated conflict policy and required fields | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-011--vehicleupdated-conflict-policy--required-fields) |
| DECISION-INVENTORY-012 | Vehicle care preferences schema/enums/locking/audit | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-012--vehicle-care-preferences-schema-enums-locking-audit) |
| DECISION-INVENTORY-013 | Vehicle lookup/search defaults and handoff | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-013--vehicle-lookupsearch-defaults--handoff) |
| DECISION-INVENTORY-014 | Party merge policy and alias behavior | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-014--party-merge-policy--alias-behavior) |
| DECISION-INVENTORY-015 | Commercial account creation and duplicate detection | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-015--commercial-account-creation--duplicate-detection) |

## Open Questions (from source)

### Q: What are the exact Moqui services/entity views to query `ProcessingLog` and `SuspenseQueue` (list + detail), including required params and returned fields?

- Answer: CRM must expose dedicated read services returning safe DTOs (not direct entity-find), supporting pagination, sorting, and filters by `eventType`, `eventId`, `correlationIdPrefix`, date range, and status.
- Assumptions:
- Moqui screens can call services; services can enforce RBAC and redaction.
- Rationale:
- Prevents RBAC/redaction bypass.
- Impact:
- Requires a written contract and DTO schema before UI ships.
- Decision ID: DECISION-INVENTORY-005

### Q: Is a suspense/DLQ record addressed by `eventId` or a separate `suspenseId`/messageId?

- Answer: A separate `suspenseId` is the identity key for suspense records; `eventId` is a field and filter.
- Assumptions:
- Multiple suspense attempts/records may exist over time.
- Rationale:
- Stable UI navigation and audit history.
- Impact:
- List returns `suspenseId`; detail loads by `suspenseId`.
- Decision ID: DECISION-INVENTORY-005

### Q: Can the UI display raw `payload` JSON for suspense items? If yes, what redaction rules apply and is there a safe `payloadSummary` field?

- Answer: Default-deny raw payload. Provide `payloadSummary` and `redactedPayload` only. Raw payload is auditor-only and still redacted for secrets.
- Assumptions:
- Redaction policy is centrally defined and testable.
- Rationale:
- PII/secrets leakage risk.
- Impact:
- Add redaction tests and payload size limits.
- Decision ID: DECISION-INVENTORY-006

### Q: What permission keys/roles gate access to integration monitoring screens, and are there environment-based restrictions (prod vs non-prod)?

- Answer: Use explicit permissions and fail closed. Default is allowed in all envs for internal support roles; environment restriction is safe-to-defer.
- Assumptions:
- Security domain will map permissions to roles.
- Rationale:
- Least privilege and auditability.
- Impact:
- UI may hide menu entries, but backend must enforce.
- Decision ID: DECISION-INVENTORY-007

### Q: What are the filter semantics for `correlationId` (exact/prefix/contains), and what performance/indexing constraints apply?

- Answer: Exact and prefix match only; contains is unsupported unless explicitly indexed and approved.
- Assumptions:
- Correlation identifiers are structured.
- Rationale:
- Avoid full scans.
- Impact:
- UI labels “starts with”.
- Decision ID: DECISION-INVENTORY-008

### Q: Should the UI expose retry/attempt metadata (retry count, last error, next retry timestamp), and what fields provide it?

- Answer: UI may display retry metadata if backend provides it; UI must not assume these fields exist.
- Assumptions:
- Retry is asynchronous and backend-owned.
- Rationale:
- Useful observability without implying actionability.
- Impact:
- DTO may include `attemptCount`, `lastAttemptAt`, `nextAttemptAt`.
- Decision ID: DECISION-INVENTORY-009

### Q: Are operator actions (reprocess/retry/ack/export/mark resolved) required? If yes, define allowed actions and audit requirements

- Answer: Not required by default; any future operator actions require audited commands with idempotency keys and strict RBAC.
- Assumptions:
- Ops runbooks cover remediation.
- Rationale:
- Reduces mutation surface.
- Impact:
- Separate story needed before adding any actions.
- Decision ID: DECISION-INVENTORY-009

### Q: Promotions redemptions: what is the exact API/service contract for listing/getting `PromotionRedemption` (pagination/sort/filters)?

- Answer: Provide list/detail read services with server-side pagination and filters by promotionId, partyId, workOrderId, invoiceId, and date range.
- Assumptions:
- Read-only UI.
- Rationale:
- Audit/operational use cases require filtering.
- Impact:
- Backend contract required.
- Decision ID: DECISION-INVENTORY-010

### Q: Promotions redemptions: what roles/permissions gate CSR vs Marketing access?

- Answer: Gate via separate read permissions; CSR may have narrower access than Marketing. Exact role mapping is safe-to-defer.
- Assumptions:
- Security domain owns role mapping.
- Rationale:
- Least privilege.
- Impact:
- Two permissions recommended: `crm.promotions.redemptions.read` and `crm.promotions.redemptions.audit`.
- Decision ID: DECISION-INVENTORY-007

### Q: Promotions redemptions: which entity is the canonical read model exposed to UI (`PromotionRedemption` only vs also `ProcessingLog`/event metadata)?

- Answer: UI consumes a dedicated `PromotionRedemption` read model; `eventId` may be included for optional cross-linking.
- Assumptions:
- ProcessingLog schema can evolve independently.
- Rationale:
- Keeps UI stable.
- Impact:
- Detail DTO may include `eventId`.
- Decision ID: DECISION-INVENTORY-010

### Q: Promotions redemptions: are `workOrderId` and `invoiceId` UUIDs or external human-readable identifiers?

- Answer: Treat as UUIDs; optionally also return display-only numbers.
- Assumptions:
- UUIDs are canonical join keys.
- Rationale:
- Deterministic integration.
- Impact:
- DTO may include both ID and number.
- Decision ID: DECISION-INVENTORY-010

### Q: VehicleUpdated ingestion logs: what is the conflict resolution policy and when is `PENDING_REVIEW` used vs last-write-wins?

- Answer: Non-critical conflicts use last-write-wins; destructive conflicts (VIN change, significant mileage decrease) are `PENDING_REVIEW` and not applied.
- Assumptions:
- Thresholds are configurable (safe-to-defer exact values).
- Rationale:
- Prevent silent corruption.
- Impact:
- Include `reasonCode` and conflict metadata.
- Decision ID: DECISION-INVENTORY-011

### Q: VehicleUpdated ingestion logs: does ProcessingLog include `estimateId` in addition to `workorderId`, and what is the field name?

- Answer: Include optional UUID fields `estimateId` and `workorderId`; also keep `correlationId` as a generic string.
- Assumptions:
- Some flows create estimates before workorders.
- Rationale:
- Better traceability.
- Impact:
- UI shows whichever reference is present.
- Decision ID: DECISION-INVENTORY-011

### Q: VehicleUpdated ingestion logs: can `details` contain sensitive customer data and what masking rules apply?

- Answer: Treat `details` as sensitive; backend must redact PII/secrets and UI must not render as HTML or log it.
- Assumptions:
- Security policy defines masking.
- Rationale:
- Prevent leakage.
- Impact:
- Add redaction tests.
- Decision ID: DECISION-INVENTORY-006

### Q: Vehicle care preferences: what are the exact endpoints/services for load, upsert, and audit history (including error payload shape and field-level errors)?

- Answer: Provide read (`GET by vehicleId`), upsert (`PUT by vehicleId`), and audit history (`GET /audit`) services. Error payloads include `errorCode`, `message`, `fieldErrors`.
- Assumptions:
- UI expects field-level errors.
- Rationale:
- Avoid UI guessing.
- Impact:
- Backend contract required.
- Decision ID: DECISION-INVENTORY-012

### Q: Vehicle care preferences: fixed schema vs dynamic key/value preferences (EAV/JSON)?

- Answer: Fixed schema.
- Assumptions:
- Fields are known.
- Rationale:
- Auditability and validation.
- Impact:
- DB migrations for additions.
- Decision ID: DECISION-INVENTORY-012

### Q: Vehicle care preferences: is optimistic locking required (version/ETag/lastUpdatedStamp) and what is the expected 409 behavior?

- Answer: Yes; stale updates return `409` with `currentVersion` and guidance to reload.
- Assumptions:
- Concurrent edits possible.
- Rationale:
- Prevent silent overwrite.
- Impact:
- Include `version` in read DTO.
- Decision ID: DECISION-INVENTORY-012

### Q: Vehicle care preferences: allowed `rotationIntervalUnit` enum values, whether unit is required when interval is provided, and min/max constraints

- Answer: Units: `MILES|KM`. Unit required when interval present. Interval integer 1–100000.
- Assumptions:
- Time-based units are out of scope.
- Rationale:
- Determinism.
- Impact:
- Shared validation in UI and backend.
- Decision ID: DECISION-INVENTORY-012

### Q: Vehicle lookup: confirm endpoints/methods/response shapes for search and vehicle+owner snapshot

- Answer: Default contract: `POST /rest/api/v1/crm/vehicles/search` and `GET /rest/api/v1/crm/vehicles/{vehicleId}/snapshot` via Moqui proxy; safe-to-defer final naming.
- Assumptions:
- Search is paginated.
- Rationale:
- Enables UI implementation.
- Impact:
- Requires DTO contract.
- Decision ID: DECISION-INVENTORY-013

### Q: Vehicle lookup: ranking logic and whether backend returns `rankScore`/`matchType`

- Answer: Backend may return `matchType` and optional `rankScore`; UI treats them as display-only.
- Assumptions:
- Ranking evolves.
- Rationale:
- Avoid UI encoding policy.
- Impact:
- DTO may include these fields.
- Decision ID: DECISION-INVENTORY-013

### Q: Vehicle lookup: minimum query length and matching rules (contains vs startsWith) for VIN/unit/plate

- Answer: Minimum query length is 3. Default matching is prefix for unit/plate; VIN contains only if indexed and approved (safe-to-defer).
- Assumptions:
- Performance constraints apply.
- Rationale:
- Avoid expensive queries.
- Impact:
- UI disables submit until length met.
- Decision ID: DECISION-INVENTORY-013

### Q: Vehicle lookup: result limiting/pagination and whether truncation is indicated in response

- Answer: Require server-side pagination with `total`; UI never assumes full set.
- Assumptions:
- Backend enforces max page size.
- Rationale:
- Performance safety.
- Impact:
- Response includes `pageIndex`, `pageSize`, `total`.
- Decision ID: DECISION-INVENTORY-013

### Q: Vehicle lookup: permission model and required behavior when permission is missing (hide entry point vs show blocked screen)

- Answer: UI may hide entry points, but backend must enforce and UI must handle 403 deterministically.
- Assumptions:
- Moqui supports menu gating.
- Rationale:
- Fail closed.
- Impact:
- Provide an access-denied state.
- Decision ID: DECISION-INVENTORY-007

### Q: Vehicle lookup: PII policy for displaying/logging full VIN and license plate (masking standard)

- Answer: Display full values only to authorized roles; mask in logs and client telemetry.
- Assumptions:
- Security confirms masking rules.
- Rationale:
- PII minimization.
- Impact:
- Shared masking helpers.
- Decision ID: DECISION-INVENTORY-006

### Q: Vehicle lookup: Moqui screen-flow handoff mechanism to return selected vehicle context to estimate creation (return transition vs redirect params vs shared context)

- Answer: Use caller-provided return URL/params; lookup redirects back with `vehicleId` (and optionally `partyId`). Safe-to-defer exact Moqui wiring.
- Assumptions:
- Calling flow provides return destination.
- Rationale:
- Reusable component.
- Impact:
- Preserve filters on return.
- Decision ID: DECISION-INVENTORY-013

### Q: CRM snapshot viewer: should the browser call `GET /v1/crm-snapshot` directly or must it be proxied via Moqui server-side service (allowlist + scope)?

- Answer: Must be proxied server-side via Moqui.
- Assumptions:
- Snapshot requires allowlist/scope.
- Rationale:
- Do not expose service credentials.
- Impact:
- Moqui injects correlation headers.
- Decision ID: DECISION-INVENTORY-004

### Q: CRM snapshot viewer: where is correlation ID provided (header name vs JSON field), and is logging partyId/vehicleId allowed or must be masked?

- Answer: Use `X-Correlation-Id` and `traceparent`. Server logs may include partyId/vehicleId; client telemetry must not include VIN/plate and should avoid logging full IDs.
- Assumptions:
- Platform correlation standards apply.
- Rationale:
- Supportability without leaking PII.
- Impact:
- Logging/telemetry guidelines.
- Decision ID: DECISION-INVENTORY-004

### Q: Contact points/preferences/contact roles/merge/commercial account creation: confirm canonical identifiers (`partyId` vs `personId` vs `customerId`) used in routes and service calls

- Answer: Canonical is `partyId`. `customerId` must map to `partyId`. `personId` is only for subtype-specific operations.
- Assumptions:
- Legacy IDs may exist.
- Rationale:
- Eliminate ambiguity.
- Impact:
- Standardize route params.
- Decision ID: DECISION-INVENTORY-001

### Q: Contact points: phone validation policy (E.164 vs digits-only vs extensions) and whether `kind` is mutable after creation

- Answer: Phone policy is defined in DECISION-INVENTORY-003; contact kind is immutable.
- Assumptions:
- Changing kind is delete + recreate.
- Rationale:
- Cleaner audit trail.
- Impact:
- UI blocks kind changes.
- Decision ID: DECISION-INVENTORY-003

### Q: Party merge: search requirements (allow browse vs require criteria), party types allowed, alias/redirect behavior, and conflict resolution policy (“survivor wins”)

- Answer: Merge requires explicit selection (no unfiltered browse). Only merge same subtype. Survivor wins. Reads by merged party return `409 PARTY_MERGED` with `mergedToPartyId`.
- Assumptions:
- Merge is privileged.
- Rationale:
- Prevent accidental merges.
- Impact:
- Audit event required.
- Decision ID: DECISION-INVENTORY-014

### Q: Contact roles: CRM vs Billing ownership, role list source of truth, and primary auto-demotion vs reject behavior

- Answer: CRM owns roles; role codes are backend-owned; primary uses auto-demotion.
- Assumptions:
- Billing consumes snapshot.
- Rationale:
- Avoid split-brain.
- Impact:
- Domain labels and docs align to CRM.
- Decision ID: DECISION-INVENTORY-002

### Q: Commercial account creation: duplicate detection inputs and backend response shape (separate duplicateCheck vs create returns duplicates requiring confirmation), billing terms source, external identifiers validation, and post-create navigation route

- Answer: Duplicate-check then create. Create returns 409 with candidates unless override + justification. Billing terms are selected from Billing-owned reference data (safe-to-defer exact wiring). Post-create navigates to the new party profile route.
- Assumptions:
- Billing provides terms list.
- Rationale:
- Prevent duplicate accounts while allowing controlled overrides.
- Impact:
- Requires cross-domain integration for billing terms.
- Decision ID: DECISION-INVENTORY-015

## Open Questions from Frontend Stories

> This section consolidates *new* open questions from the latest frontend stories (#176, #173, #172, #171, #169) and maps them to existing decisions where possible. Items are phrased as contract questions that must be answered before UI implementation can be finalized. Use **TODO** for work items and **CLARIFY** for policy/ownership ambiguity.

### Commercial Account Create (Issue #176)

1. **Permissions (blocking):** What is the exact permission key (or keys) required to access and submit “Create Commercial Account”? (DECISION-INVENTORY-007)
   - TODO: Security to confirm permission taxonomy and role mapping (e.g., `crm.party.commercial.create` vs `crm.party.create`).

2. **Billing terms contract (blocking):** What is the exact Moqui service/REST endpoint to list billing terms, and what are the fields/ID type (`billingTermsId` type, display label fields)? (DECISION-INVENTORY-015)
   - CLARIFY: Is the identifier a UUID, code, or composite key? What is the stable display label?

3. **Duplicate-check contract (blocking):** Confirm the backend contract for duplicate detection. (DECISION-INVENTORY-015)
   - Is there a dedicated duplicate-check endpoint (preferred), or does create return `409 DUPLICATE_CANDIDATES` with candidates?
   - What fields are required for matching (legalName only vs also phone/email/taxId/external identifiers)?
   - What is the candidate DTO shape (fields available for display and `matchReason`/`matchScore`)?

4. **External identifiers schema (blocking):** What is the allowed structure for `externalIdentifiers` (fixed set vs key/value list), and what validations apply? (DECISION-INVENTORY-015)
   - CLARIFY: Are any external identifiers unique across all parties? If yes, what is the conflict errorCode and safe error messaging?

5. **Post-create navigation route (blocking):** What is the canonical Party/Account view route in this frontend (pattern and screen name)? If none exists, should this story include creating a minimal `PartyView` screen, or is confirmation-only acceptable for MVP? (DECISION-INVENTORY-001)
   - TODO: Document canonical route pattern once confirmed (e.g., `/crm/party/{partyId}`).

### Party Search + Merge (Issue #173)

1. **Backend contract (blocking):** What are the exact Moqui service names/endpoints and DTO schemas for:
   - Party search (filters, pagination, returned summary fields)
   - Party merge command (required fields; response payload including `mergeAuditId` and redirect/alias fields) (DECISION-INVENTORY-014)

2. **Permissions (blocking):** What is the exact permission key for party merge (e.g., `crm.party.merge`) and should the UI hide the entry point or show an access-denied screen when missing? (DECISION-INVENTORY-007)
   - Note: backend must enforce regardless of UI behavior.

3. **Justification storage (blocking):** Does the merge command require `justification` and is it stored in MergeAudit? If required, confirm min/max length and whether it is visible in audit views. (DECISION-INVENTORY-014)
   - CLARIFY: Is justification considered sensitive? If yes, apply redaction rules for logs/UI exports. (DECISION-INVENTORY-006)

4. **Search matching rules (non-blocking if backend already exists):** Are name/email/phone searches exact, prefix, or contains? Any minimum lengths (especially for phone/email) to prevent expensive queries? (DECISION-INVENTORY-014, DECISION-INVENTORY-008)
   - Default-safe guidance: prefer exact/prefix; avoid contains unless indexed/approved.

5. **Merged exclusion (non-blocking):** Does search always exclude merged parties, or is there a supported `includeMerged` toggle for Admin-only troubleshooting? (DECISION-INVENTORY-014)

### Contact Roles + Primary Flags (Issue #172)

1. **Backend contract finalization (blocking):** What are the exact Moqui service names (or REST endpoints) and DTO shapes for:

- Loading account contacts + role assignments + `invoiceDeliveryMethod`
- Updating a contact’s roles and primary flags  
   Include required params, returned fields, and error payload schema (`errorCode`, `message`, `fieldErrors`). (DECISION-INVENTORY-002)

1. **Permission keys (blocking):** What permission(s) gate:

- Viewing role assignments
- Updating role assignments (DECISION-INVENTORY-007)

1. **Role code source (blocking):** Are role codes strictly limited to `BILLING|APPROVER|DRIVER` for MVP, or must the UI load an allowed role list from backend (with display labels and ordering)? (DECISION-INVENTORY-002)

- CLARIFY: If backend-owned list exists, does it include deprecation/visibility flags?

1. **Billing EMAIL rule data dependency (clarification):** For enforcing “billing contact with primary email required”, will the load DTO include `hasPrimaryEmail` per contact (preferred), or must UI infer from contact points (which may expose PII)? (DECISION-INVENTORY-002, DECISION-INVENTORY-006)

### Communication Preferences + Consent (Issue #171)

1. **Backend contract (blocking):** What are the exact Moqui service names and/or REST endpoints for:

- load communication preferences by `partyId`
- upsert (create/update) communication preferences by `partyId`  
   Include request/response DTO field names, required fields, and error payload shape (`errorCode`, `message`, `fieldErrors`). (DECISION-INVENTORY-001, DECISION-INVENTORY-007)

1. **Missing record semantics (blocking):** When a party exists but has no preferences yet, does the backend return:

- `404` with a specific `errorCode` (preferred), or
- `200` with an empty/null payload? (CLARIFY)

1. **updateSource handling (blocking):** Is `updateSource` required on write? If yes, should:

- the frontend send a constant value (what exact value?), or
- Moqui/backend infer it from the authenticated client/app context? (CLARIFY)

1. **Consent model (blocking):** Are consent flags stored only on the communication preferences DTO, or is there a separate `ConsentRecord` entity/DTO the UI must manage? If separate, define how they relate and which service(s) write them. (CLARIFY)

2. **Permissions (blocking):** What are the explicit permission keys for:

- viewing communication preferences
- editing communication preferences  
   Confirm whether view is allowed when edit is denied. (DECISION-INVENTORY-007)

1. **Optimistic locking (blocking):** Is optimistic locking required for this record? If yes, what field is used (`version`, `lastUpdatedStamp`, ETag), and what is the expected 409 response payload (e.g., `currentVersion`) and UI resolution flow? (Pattern aligns with DECISION-INVENTORY-012; **CLARIFY** whether same pattern applies.)

### Vehicle Create (Issue #169)

1. **VIN uniqueness scope (blocking):** Is VIN uniqueness global across all vehicles or scoped (e.g., per party/account/location)? What `errorCode` is returned on duplicate (e.g., `DUPLICATE_VIN`) and does it ever include a safe reference to an existing `vehicleId`? (DECISION-INVENTORY-011, DECISION-INVENTORY-006)

- Default-safe: do not return identifying details about the existing vehicle unless authorized and explicitly safe.

1. **Association-at-create (blocking):** Does create-vehicle require a `partyId` or any account/customer context? If yes, is it required or optional, and what is the expected behavior when omitted? (DECISION-INVENTORY-001)

2. **Moqui service/API contract (blocking):** What are the exact Moqui service names or REST endpoints for:

- create vehicle
- load vehicle by `vehicleId`  
   Include request/response DTO fields and the standard error payload shape (`errorCode`, `message`, `fieldErrors`). (DECISION-INVENTORY-007)

1. **Authorization model (blocking):** What permission key(s) gate vehicle creation and vehicle view? (DECISION-INVENTORY-007)

2. **License plate structure (clarification):** Is license plate stored as a single string only, or does backend require plate + region (state/province/country)? If region is required, provide field names and validation rules. (DECISION-INVENTORY-006 for masking/logging)

## Todos Reconciled

- Original todo: "Standardize on `partyId` as the canonical identifier in frontend routes and service contracts" → Resolution: Resolved | Replace with task: `TASK-CRM-IDS-001`
- Original todo: "Confirm entity name (`VehiclePreference` vs `VehicleCarePreference`) and whether schema is fixed vs dynamic" → Resolution: Resolved (fixed schema `VehicleCarePreference`)
- Original todo: "Provide Moqui service names/endpoints for ProcessingLog/Suspense/Redemptions" → Resolution: Defer (requires backend contract doc) | Replace with task: `TASK-CRM-CONTRACT-001`
- Original todo: "Confirm vehicle search endpoints/response schema/pagination/limits" → Resolution: Defer (safe defaults set; finalize with backend) | Replace with task: `TASK-CRM-CONTRACT-002`
- **New todo:** "Define Moqui service contracts for Party merge/search, commercial account create/dedupe, contact roles update, communication preferences upsert, and vehicle create/view" → Resolution: Defer | Replace with task: `TASK-CRM-CONTRACT-003`
- **New todo:** "Define permission taxonomy for new CRM UI features (create commercial, merge, roles edit, preferences edit, vehicle create/view)" → Resolution: Defer | Replace with task: `TASK-CRM-AUTHZ-001`
- **New todo:** "Define observability/telemetry redaction rules for new flows (taxId, VIN, plate, email/phone)" → Resolution: Defer | Replace with task: `TASK-CRM-OBS-REDACTION-001`

## End

End of document.
