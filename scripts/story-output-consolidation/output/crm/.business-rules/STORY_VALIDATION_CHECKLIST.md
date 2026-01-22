# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates CRM-domain story implementations for correctness, security, operational visibility, and contract fidelity. It incorporates resolved open questions from the prior CRM docs by turning them into testable acceptance criteria. Where upstream contracts remain pending, it encodes safe defaults and requires explicit contract documentation before release.

## Completed items

- [x] Updated acceptance criteria for each resolved open question

## Scope / Ownership

- [ ] Confirm story labeled `domain:crm`
- [ ] Verify primary actor(s) and permissions
- [ ] Confirm CRM is SoR for mutated entities (Party, Vehicle, ContactPoint, roles, preferences)
- [ ] Ensure operational screens are read-only unless explicitly approved
- [ ] Verify each screen/story explicitly declares whether it is **read-only** or **mutating**, and reviewers confirm it matches the story scope
- [ ] Verify all routes and service calls use canonical identifiers (`partyId`, `vehicleId`) and do not introduce `customerId`/`accountId` as primary identifiers
- [ ] Verify cross-domain reference data ownership is respected (e.g., Billing Terms are Billing-owned and consumed read-only by CRM UI)

## Data Model & Validation

- [ ] Validate required inputs and types (`partyId`, `vehicleId`, `eventId` are UUID when specified)
- [ ] Verify date/time semantics and timezone labeling for displayed timestamps
- [ ] Validate vehicle care preferences fields:
  - [ ] `rotationInterval` integer and 1–100000
  - [ ] `rotationIntervalUnit` required when interval present; enum: `MILES`, `KM`
  - [ ] Free-form notes treated as text only
- [ ] Verify Commercial Account create validation:
  - [ ] `legalName` is required and non-empty; UI blocks submit without calling backend
  - [ ] `defaultBillingTermsId` is required and selected from Billing-provided list; UI blocks submit without calling backend
  - [ ] `taxId` is treated as sensitive; UI does not validate format unless backend returns `fieldErrors`
  - [ ] Duplicate override justification is required and non-empty when `duplicateOverride=true`
  - [ ] External identifiers are validated strictly per backend contract; unknown identifier fields/keys are rejected (no free-form assumptions)
- [ ] Verify Party merge validation:
  - [ ] Search requires at least one criterion (`name` OR `email` OR `phone`); no unfiltered browse
  - [ ] UI enforces selecting exactly two distinct parties before proceeding
  - [ ] UI prevents self-merge (sourcePartyId != survivorPartyId) before calling backend
  - [ ] Merge justification is required and enforces min/max length (5–500) before calling backend
- [ ] Verify Contact Roles validation:
  - [ ] Primary toggle cannot be set unless the corresponding role is selected (“primary requires role”)
  - [ ] Role codes submitted are limited to backend-allowed values (either fixed `BILLING|APPROVER|DRIVER` or loaded list); invalid role codes are rejected
  - [ ] When `invoiceDeliveryMethod=EMAIL`, backend-enforced rule is surfaced: at least one Billing contact exists and has a primary email; UI renders the backend validation error deterministically
- [ ] Verify Communication Preferences validation:
  - [ ] `preferredCommunicationChannel` values are restricted to backend-provided enum; invalid values produce 400 with field error mapping
  - [ ] Consent flags are submitted as booleans or null exactly per contract; UI does not invent semantics beyond display
  - [ ] If optimistic locking is used, UI includes required concurrency token (version/ETag/lastUpdatedStamp) on update
- [ ] Verify Vehicle create validation:
  - [ ] VIN is trimmed and uppercased before submit
  - [ ] VIN must be exactly 17 chars and match `^[A-HJ-NPR-Z0-9]{17}$` (no I/O/Q); UI blocks submit
  - [ ] `unitNumber` and `description` are required and non-empty; UI blocks submit
  - [ ] `licensePlate` is trimmed; empty becomes null/omitted per contract
  - [ ] If license plate region is required by backend, UI includes required region fields and validates presence

## API Contract

- [ ] Verify endpoints/services and DTO fields are documented near the owning component
- [ ] Verify pagination (`pageIndex`, `pageSize`) and sorting for all list screens
- [ ] Verify error payloads include `errorCode` and do not leak sensitive data
- [ ] Verify all mutating operations use dedicated services/endpoints (no direct entity writes/finds from browser)
- [ ] Verify all read operations that may contain sensitive fields return safe DTOs (no direct entity-find exposure)
- [ ] Verify Commercial Account create contracts:
  - [ ] Billing terms list endpoint/service is documented with ID type and display fields; UI blocks create when list is empty/unavailable
  - [ ] Duplicate detection contract is documented: either separate duplicate-check endpoint or create returns `409 DUPLICATE_CANDIDATES`
  - [ ] Duplicate candidate DTO fields are documented and safe for display (e.g., `taxIdMasked` only; no raw taxId)
  - [ ] Create request/response includes `partyId` on success; UI treats `partyId` as canonical ID
  - [ ] `409 DUPLICATE_CANDIDATES` response shape is documented and UI preserves form state when received
- [ ] Verify Party search + merge contracts:
  - [ ] Party search supports filters (name/email/phone) with pagination and sorting; contract documents matching semantics (exact/prefix/contains) and any minimum lengths
  - [ ] Search excludes merged parties by default; if `includeMerged` exists it is explicitly permission-gated and documented
  - [ ] Merge command contract includes required fields (`sourcePartyId`, `survivorPartyId`, `justification`) and response fields (`mergeAuditId` optional, redirect/alias fields)
  - [ ] Reads of merged party return `409 PARTY_MERGED` with `mergedToPartyId` (documented and tested)
- [ ] Verify Contact Roles contracts:
  - [ ] Load contract includes `invoiceDeliveryMethod` and enough contact email summary to support EMAIL billing rule without exposing full emails (e.g., `hasPrimaryEmail`)
  - [ ] Update contract returns updated assignments sufficient for UI refresh
  - [ ] Error payload supports field-level errors for role/primary constraints
- [ ] Verify Communication Preferences contracts:
  - [ ] Load contract defines missing-record semantics (200 empty vs 404 specific errorCode) and UI behavior matches
  - [ ] Upsert contract defines whether it returns 200 vs 201 and includes audit metadata fields if available (`updatedAt`, `updateSource`)
  - [ ] `updateSource` write behavior is documented (frontend-supplied constant vs backend-inferred)
  - [ ] Consent model is documented (single DTO vs separate ConsentRecord) and UI only implements the documented model
- [ ] Verify Vehicle create/view contracts:
  - [ ] Duplicate VIN behavior is documented (409 errorCode, and whether it returns a safe reference to existing `vehicleId`)
  - [ ] VIN uniqueness scope is documented (global vs scoped) and tests cover the chosen policy
  - [ ] Association-at-create is documented (whether `partyId` is required/optional) and UI behavior matches

## Events & Idempotency

- [ ] Inbound event processing is idempotent by `eventId`
- [ ] UI and services handle retry semantics without creating duplicates
- [ ] Verify any create flows are protected against double-submit:
  - [ ] UI disables submit while request is in-flight
  - [ ] Backend supports idempotency or safe duplicate handling where applicable (e.g., duplicate VIN, duplicate commercial account candidates)
- [ ] Verify merge command is safe under retries:
  - [ ] If the same merge request is submitted twice (network retry), backend response is deterministic (either idempotent success or a clear conflict), and UI handles it without corrupting state

## Security

- [ ] Permission gating for sensitive payloads and redaction
- [ ] UI never renders backend strings as HTML
- [ ] Snapshot calls are proxied server-side; no browser-to-snapshot direct calls
- [ ] Verify fail-closed authorization for each screen and endpoint:
  - [ ] Direct URL access without permission returns 403 and UI shows deterministic “Access denied” without partial data
  - [ ] Entry points are hidden/disabled when permission is known client-side, but backend enforcement is still required
- [ ] Verify PII handling for new CRM flows:
  - [ ] UI does not log or emit to telemetry: taxId, external identifiers, email addresses, phone numbers, VIN, license plate
  - [ ] Server logs mask sensitive values where applicable (VIN/plate/taxId) and do not store raw payloads in client-visible responses
- [ ] Verify duplicate candidate display is safe:
  - [ ] Candidate DTO does not include raw taxId; only masked values are displayed if present
  - [ ] UI does not reveal existence of records to unauthorized users via different error messages (e.g., 403 vs 409 behavior is consistent and non-enumerable)
- [ ] Verify merge justification and duplicate override justification are treated as potentially sensitive free text:
  - [ ] Rendered as plain text (no HTML)
  - [ ] Not logged to client telemetry
- [ ] Verify contact roles editing does not expose full contact points unless explicitly permitted:
  - [ ] Prefer `hasPrimaryEmail` boolean over returning full email addresses in role-management DTOs

## Observability

- [ ] Trace identifiers and audit fields surface in UI and logs
- [ ] UI telemetry avoids raw payloads, VIN, and plate values
- [ ] Verify correlation ID handling for new flows:
  - [ ] If backend provides `X-Correlation-Id` header (or `correlationId` field), UI displays it in error banners/technical details
  - [ ] Correlation IDs are not treated as secrets and are safe to copy for support
- [ ] Verify audit metadata display where applicable:
  - [ ] Commercial account create success shows `partyId` and displays `createdAt/createdBy` only if provided
  - [ ] Communication preferences view shows `updatedAt` and `updateSource` only if provided
  - [ ] Party merge result shows `mergeAuditId` if provided

## Performance & Failure Modes

- [ ] Verify list screens use pagination and do not attempt to load unbounded datasets
- [ ] Verify search screens enforce minimum criteria to prevent expensive queries:
  - [ ] Party merge search blocks “browse all” and requires at least one criterion
  - [ ] If backend defines minimum lengths for name/email/phone, UI enforces them before calling backend
- [ ] Verify reference data dependency failure handling:
  - [ ] If Billing terms fail to load or return empty, Commercial Account create is disabled and shows actionable message
- [ ] Verify network/timeout handling:
  - [ ] UI shows retryable banner on transient failures and preserves form state (commercial account create, vehicle create, preferences save, roles save)
- [ ] Verify conflict handling:
  - [ ] `409 DUPLICATE_CANDIDATES` shows duplicates panel and preserves inputs
  - [ ] `409 PARTY_MERGED` is handled deterministically with redirect option to `mergedToPartyId`
  - [ ] `409 CONFLICT` (optimistic locking) prompts reload and does not silently overwrite

## Testing

- [ ] Verify unit/integration tests cover validation and error mapping for each story
- [ ] Verify contract tests (or mocked fixtures) exist for:
  - [ ] 400 with `fieldErrors` mapping to UI fields
  - [ ] 403 access denied behavior (no partial data rendered)
  - [ ] 404 not found behavior (vehicle view, party not found, contact not found)
  - [ ] 409 duplicate/conflict behaviors (`DUPLICATE_CANDIDATES`, `DUPLICATE_VIN`, `PARTY_MERGED`, optimistic locking conflicts)
- [ ] Verify Commercial Account create tests:
  - [ ] Legal name required blocks submit (no service call)
  - [ ] Billing terms required blocks submit (no service call)
  - [ ] Billing terms empty/unavailable disables create
  - [ ] Duplicate-check returns candidates → duplicates panel shown; cancel preserves inputs
  - [ ] Override requires confirmation + non-empty justification; request includes override fields
- [ ] Verify Party merge tests:
  - [ ] Search requires criteria; no browse
  - [ ] Exactly two selection enforced; >2 and <2 disabled
  - [ ] Self-merge prevented (UI) and handled (backend error mapping)
  - [ ] `409 PARTY_MERGED` on party detail read offers redirect to survivor
- [ ] Verify Contact roles tests:
  - [ ] Primary toggle disabled unless role selected
  - [ ] Auto-demotion behavior reflected after save (single primary per role)
  - [ ] EMAIL billing rule violation returns 400 and UI remains in edit mode
- [ ] Verify Communication preferences tests:
  - [ ] Missing record semantics handled as empty state (per contract)
  - [ ] Invalid enum returns 400 and maps to field error
  - [ ] 409 optimistic locking conflict prompts reload (if implemented)
- [ ] Verify Vehicle create tests:
  - [ ] VIN normalization (trim + uppercase) occurs before submit
  - [ ] VIN invalid pattern blocks submit
  - [ ] Duplicate VIN 409 shows non-sensitive message and does not leak existing vehicle details unless explicitly allowed

## Documentation

- [ ] Verify each implemented screen documents:
  - [ ] owning domain (`domain:crm`)
  - [ ] called services/endpoints and DTOs (request/response)
  - [ ] permissions required (exact keys)
  - [ ] error handling mapping (400/403/404/409/5xx)
- [ ] Verify any “TBD” contracts are resolved before release and linked in the story/PR description
- [ ] Verify any safe defaults applied by UI are documented (e.g., disable submit during in-flight, preserve form state on error)
- [ ] Verify any cross-domain dependencies are documented (e.g., Billing terms list contract and ownership)

## Acceptance Criteria (per resolved question)

### Q: What are the exact Moqui services/entity views to query `ProcessingLog` and `SuspenseQueue` (list + detail), including required params and returned fields?

- Acceptance: UI uses dedicated read endpoints that return safe DTOs (not direct entity-find); list supports pagination + filters (`eventType`, `eventId`, `correlationIdPrefix`, status, date range).
- Test Fixtures: Two log rows exist with different event types.
- Example API request/response (code block)

```http
GET /rest/api/v1/crm/integrations/processing-logs?eventType=VehicleUpdated&pageIndex=0&pageSize=25
```

```json
{"items":[{"processingLogId":"...","eventType":"VehicleUpdated","status":"SUCCESS"}],"pageIndex":0,"pageSize":25,"total":1}
```

### Q: Is a suspense/DLQ record addressed by `eventId` or a separate `suspenseId`/messageId?

- Acceptance: List returns `suspenseId`; detail loads by `suspenseId`. `eventId` is filterable but not the primary key.
- Test Fixtures: One suspense record exists.
- Example API request/response (code block)

```http
GET /rest/api/v1/crm/integrations/suspense?suspenseId=0f0f0f0f-0000-0000-0000-000000000001
```

```json
{"suspenseId":"0f0f0f0f-0000-0000-0000-000000000001","eventId":"11111111-1111-1111-1111-111111111111","status":"ERROR_VALIDATION"}
```

### Q: Can the UI display raw `payload` JSON for suspense items? If yes, what redaction rules apply and is there a safe `payloadSummary` field?

- Acceptance: Users without payload permission only see `payloadSummary` and `redactedPayload`. No screen renders raw payload.
- Test Fixtures: A suspense record contains a payload.
- Example API request/response (code block)

```json
{"payloadSummary":"Schema validation failed","redactedPayload":{"eventType":"VehicleUpdated"}}
```

### Q: What permission keys/roles gate access to integration monitoring screens, and are there environment-based restrictions (prod vs non-prod)?

- Acceptance: Access is denied (403) without the explicit permission; UI handles 403 as “Access denied” without partial data.
- Test Fixtures: A user without permission loads the screen.
- Example API request/response (code block)

```json
{"errorCode":"FORBIDDEN","message":"Missing permission"}
```

### Q: What are the filter semantics for `correlationId` (exact/prefix/contains), and what performance/indexing constraints apply?

- Acceptance: Prefix match is supported; contains is rejected with 400.
- Test Fixtures: Call contains filter.
- Example API request/response (code block)

```json
{"errorCode":"UNSUPPORTED_FILTER","message":"Contains search is not supported for correlationId"}
```

### Q: Should the UI expose retry/attempt metadata (retry count, last error, next retry timestamp), and what fields provide it?

- Acceptance: UI renders retry fields only if present; absence does not break rendering.
- Test Fixtures: One record includes `attemptCount`, another does not.
- Example API request/response (code block)

```json
{"attemptCount":3,"lastAttemptAt":"2026-01-19T00:00:00Z","nextAttemptAt":"2026-01-19T00:05:00Z"}
```

### Q: Are operator actions (reprocess/retry/ack/export/mark resolved) required? If yes, define allowed actions and audit requirements

- Acceptance: No operator action endpoints are called; the UI has no action buttons.
- Test Fixtures: N/A.
- Example API request/response (code block)

```text
No mutation requests occur from this screen.
```

### Q: Promotions redemptions: what is the exact API/service contract for listing/getting `PromotionRedemption` (pagination/sort/filters)?

- Acceptance: List supports pagination and date range filtering; detail loads by `promotionRedemptionId`.
- Test Fixtures: A redemption exists in the date range.
- Example API request/response (code block)

```http
GET /rest/api/v1/crm/promotions/redemptions?pageIndex=0&pageSize=25&from=2026-01-01&to=2026-01-31
```

```json
{"items":[{"promotionRedemptionId":"...","promotionId":"...","partyId":"..."}],"pageIndex":0,"pageSize":25,"total":1}
```

### Q: Promotions redemptions: what roles/permissions gate CSR vs Marketing access?

- Acceptance: Different permissions can be applied; without permission, API returns 403 and UI shows access denied.
- Test Fixtures: User without access loads list.
- Example API request/response (code block)

```json
{"errorCode":"FORBIDDEN"}
```

### Q: Promotions redemptions: which entity is the canonical read model exposed to UI (`PromotionRedemption` only vs also `ProcessingLog`/event metadata)?

- Acceptance: UI renders from `PromotionRedemption` DTO; `eventId` link is optional.
- Test Fixtures: A DTO without `eventId` still renders.
- Example API request/response (code block)

```json
{"promotionRedemptionId":"...","promotionId":"...","eventId":null}
```

### Q: Promotions redemptions: are `workOrderId` and `invoiceId` UUIDs or external human-readable identifiers?

- Acceptance: UUID IDs are accepted in filters; display numbers are optional read-only fields.
- Test Fixtures: Filter by UUID workOrderId.
- Example API request/response (code block)

```json
{"workOrderId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","workOrderNumber":"WO-12345"}
```

### Q: VehicleUpdated ingestion logs: what is the conflict resolution policy and when is `PENDING_REVIEW` used vs last-write-wins?

- Acceptance: VIN changes or significant mileage decreases produce `PENDING_REVIEW` and do not mutate Vehicle.
- Test Fixtures: Existing vehicle VIN A; inbound VIN B.
- Example API request/response (code block)

```json
{"status":"PENDING_REVIEW","reasonCode":"VIN_CHANGED"}
```

### Q: VehicleUpdated ingestion logs: does ProcessingLog include `estimateId` in addition to `workorderId`, and what is the field name?

- Acceptance: DTO includes optional `estimateId` and `workorderId` fields.
- Test Fixtures: One log row includes estimateId.
- Example API request/response (code block)

```json
{"estimateId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","workorderId":null,"correlationId":"ESTIMATE:bbbb..."}
```

### Q: VehicleUpdated ingestion logs: can `details` contain sensitive customer data and what masking rules apply?

- Acceptance: `details` is redacted server-side; UI does not log or render as HTML.
- Test Fixtures: A details string containing PII is returned redacted.
- Example API request/response (code block)

```json
{"details":"[REDACTED]"}
```

### Q: Vehicle care preferences: what are the exact endpoints/services for load, upsert, and audit history (including error payload shape and field-level errors)?

- Acceptance: Load returns a fixed DTO; upsert returns 200/201; validation errors return 400 with `fieldErrors`.
- Test Fixtures: Submit invalid interval.
- Example API request/response (code block)

```json
{"errorCode":"FIELD_INVALID","fieldErrors":{"rotationInterval":"Must be an integer between 1 and 100000"}}
```

### Q: Vehicle care preferences: fixed schema vs dynamic key/value preferences (EAV/JSON)?

- Acceptance: Unknown fields are rejected.
- Test Fixtures: Request includes `unknownField`.
- Example API request/response (code block)

```json
{"errorCode":"FIELD_NOT_ALLOWED","fieldErrors":{"unknownField":"Not allowed"}}
```

### Q: Vehicle care preferences: is optimistic locking required (version/ETag/lastUpdatedStamp) and what is the expected 409 behavior?

- Acceptance: Stale version returns 409 with `currentVersion`.
- Test Fixtures: Two clients update same record.
- Example API request/response (code block)

```json
{"errorCode":"CONFLICT","currentVersion":4}
```

### Q: Vehicle care preferences: allowed `rotationIntervalUnit` enum values, whether unit is required when interval is provided, and min/max constraints

- Acceptance: Unit must be `MILES` or `KM`; missing unit with interval returns 400.
- Test Fixtures: interval=6000 with missing unit.
- Example API request/response (code block)

```json
{"errorCode":"FIELD_INVALID","fieldErrors":{"rotationIntervalUnit":"Required when rotationInterval is set"}}
```

### Q: Vehicle lookup: confirm endpoints/methods/response shapes for search and vehicle+owner snapshot

- Acceptance: Search uses POST and supports pagination; snapshot returns owner section.
- Test Fixtures: Query length >= 3.
- Example API request/response (code block)

```http
POST /rest/api/v1/crm/vehicles/search
Content-Type: application/json

{"query":"ABC","pageIndex":0,"pageSize":25}
```

```json
{"items":[{"vehicleId":"...","vin":"...","unitNumber":"...","licensePlate":"..."}],"pageIndex":0,"pageSize":25,"total":1}
```

### Q: Vehicle lookup: ranking logic and whether backend returns `rankScore`/`matchType`

- Acceptance: UI renders match info only if present; it does not compute ranking.
- Test Fixtures: Response includes matchType.
- Example API request/response (code block)

```json
{"matchType":"VIN_PREFIX","rankScore":0.92}
```

### Q: Vehicle lookup: minimum query length and matching rules (contains vs startsWith) for VIN/unit/plate

- Acceptance: Queries shorter than 3 are rejected; UI disables submit.
- Test Fixtures: query="AB".
- Example API request/response (code block)

```json
{"errorCode":"QUERY_TOO_SHORT","message":"Query must be at least 3 characters"}
```

### Q: Vehicle lookup: result limiting/pagination and whether truncation is indicated in response

- Acceptance: Response includes `total` and paginated items.
- Test Fixtures: total > pageSize.
- Example API request/response (code block)

```json
{"pageIndex":0,"pageSize":25,"total":120,"items":[...]}
```

### Q: Vehicle lookup: permission model and required behavior when permission is missing (hide entry point vs show blocked screen)

- Acceptance: Without permission, backend returns 403; UI shows blocked state.
- Test Fixtures: User without permission.
- Example API request/response (code block)

```json
{"errorCode":"FORBIDDEN"}
```

### Q: Vehicle lookup: PII policy for displaying/logging full VIN and license plate (masking standard)

- Acceptance: UI does not emit VIN/plate into client logs/telemetry; server logs mask these values.
- Test Fixtures: Inspect client logs.
- Example API request/response (code block)

```text
Client telemetry contains no VIN/licensePlate values.
```

### Q: Vehicle lookup: Moqui screen-flow handoff mechanism to return selected vehicle context to estimate creation (return transition vs redirect params vs shared context)

- Acceptance: Selecting a vehicle returns to caller with `vehicleId` (and optionally `partyId`) in a deterministic way.
- Test Fixtures: Caller provides returnUrl.
- Example API request/response (code block)

```text
After selection, browser navigates to returnUrl?vehicleId=<uuid>
```

### Q: CRM snapshot viewer: should the browser call `GET /v1/crm-snapshot` directly or must it be proxied via Moqui server-side service (allowlist + scope)?

- Acceptance: Browser calls Moqui endpoint; Moqui calls snapshot server-to-server.
- Test Fixtures: Inspect network requests.
- Example API request/response (code block)

```http
GET /rest/api/v1/crm/snapshot?partyId=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
```

```json
{"snapshotMetadata":{"generatedAt":"2026-01-19T00:00:00Z"},"party":{"partyId":"aaaaaaaa-..."}}
```

### Q: CRM snapshot viewer: where is correlation ID provided (header name vs JSON field), and is logging partyId/vehicleId allowed or must be masked?

- Acceptance: Responses include `X-Correlation-Id`; server logs can include partyId/vehicleId; client telemetry does not.
- Test Fixtures: Inspect response headers and client telemetry.
- Example API request/response (code block)

```text
Response header includes X-Correlation-Id: <value>
```

### Q: Contact points/preferences/contact roles/merge/commercial account creation: confirm canonical identifiers (`partyId` vs `personId` vs `customerId`) used in routes and service calls

- Acceptance: All routes use `partyId`; no endpoint requires `customerId` as primary identifier.
- Test Fixtures: Verify UI routes and request payloads.
- Example API request/response (code block)

```json
{"errorCode":"MISSING_PARTY_ID","message":"partyId is required"}
```

### Q: Contact points: phone validation policy (E.164 vs digits-only vs extensions) and whether `kind` is mutable after creation

- Acceptance: Invalid phone values are rejected; kind cannot be changed after creation.
- Test Fixtures: Attempt to edit kind.
- Example API request/response (code block)

```json
{"errorCode":"FIELD_INVALID","fieldErrors":{"kind":"Immutable"}}
```

### Q: Party merge: search requirements (allow browse vs require criteria), party types allowed, alias/redirect behavior, and conflict resolution policy (“survivor wins”)

- Acceptance: Merge requires explicit selection and justification; reads of merged party return 409 with `mergedToPartyId`.
- Test Fixtures: Merge two parties then load merged party.
- Example API request/response (code block)

```json
{"errorCode":"PARTY_MERGED","mergedToPartyId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"}
```

### Q: Contact roles: CRM vs Billing ownership, role list source of truth, and primary auto-demotion vs reject behavior

- Acceptance: CRM services manage roles; primary selection auto-demotes prior primary.
- Test Fixtures: Set primary on another contact.
- Example API request/response (code block)

```json
{"role":"BILLING","primaryContactPartyId":"..."}
```

### Q: Commercial account creation: duplicate detection inputs and backend response shape (separate duplicateCheck vs create returns duplicates requiring confirmation), billing terms source, external identifiers validation, and post-create navigation route

- Acceptance: Duplicate-check returns candidates; create returns 409 with candidates unless override is provided.
- Test Fixtures: Create with duplicate name/phone.
- Example API request/response (code block)

```json
{"errorCode":"DUPLICATE_CANDIDATES","candidates":[{"partyId":"...","matchReason":"NAME+PHONE"}]}
```

## Open Questions to Resolve

- [ ] Commercial Account create: What is the exact permission key (or keys) required to access and submit “Create Commercial Account” (and is there a separate permission for duplicate override, if any)?
- [ ] Commercial Account create: What is the exact Billing terms list service/endpoint, including ID type (`billingTermsId`), active filtering, and display fields?
- [ ] Commercial Account create: What is the duplicate detection contract—separate duplicate-check endpoint vs create returning `409 DUPLICATE_CANDIDATES`—and what fields are required for matching (legalName only vs also phone/email/taxId/external identifiers)?
- [ ] Commercial Account create: What is the allowed schema for `externalIdentifiers` (fixed set vs key/value list), and what validations/uniqueness constraints apply?
- [ ] Commercial Account create: What is the canonical Party/Account view route/screen name for post-create navigation (or is confirmation-only acceptable for MVP)?
- [ ] Party merge: What are the exact Party search and Party merge service names/endpoints and DTO schemas (filters, pagination, returned summary fields; merge response fields including `mergeAuditId` and redirect/alias fields)?
- [ ] Party merge: What is the exact permission key for party merge, and should the UI hide the entry point or show an access-denied screen when missing (backend must enforce either way)?
- [ ] Party merge: Is merge `justification` required by backend, what are min/max lengths, and is it stored in MergeAudit and visible in audit views?
- [ ] Party merge: What are the search matching rules for name/email/phone (exact/prefix/contains) and any minimum lengths to prevent expensive queries?
- [ ] Party merge: Does search always exclude merged parties, or is there an Admin-only `includeMerged` toggle (and what permission gates it)?
- [ ] Contact roles: What are the exact load/update service names/endpoints and DTO shapes for loading contacts+roles+`invoiceDeliveryMethod` and updating roles/primary flags (including error payload schema)?
- [ ] Contact roles: What explicit permission keys gate viewing role assignments vs updating role assignments (and is view allowed when edit is denied)?
- [ ] Contact roles: Are role codes strictly limited to `BILLING|APPROVER|DRIVER` for MVP, or must the UI load an allowed role list from backend (with display labels and ordering)?
- [ ] Contact roles: For the EMAIL billing rule, will the load DTO include `hasPrimaryEmail` per contact (preferred), or must UI infer from contact points (and if so, what PII-safe fields are allowed)?
- [ ] Communication preferences: What are the exact load and upsert service names/endpoints and DTO field names (including error payload schema)?
- [ ] Communication preferences: When a party exists but has no preferences, does backend return `404` with a specific `errorCode` or `200` with empty/null payload?
- [ ] Communication preferences: Is `updateSource` required on write, and if yes is it frontend-supplied (what constant) or backend-inferred from authenticated context?
- [ ] Communication preferences: Is consent stored only on the communication preferences DTO, or is there a separate `ConsentRecord` entity/DTO the UI must manage (and which services write it)?
- [ ] Communication preferences: What explicit permission keys gate viewing vs editing communication preferences?
- [ ] Communication preferences: Is optimistic locking required; if yes, what token is used and what is the expected 409 payload and resolution flow?
- [ ] Vehicle create: Is VIN uniqueness global or scoped (per party/account/location), what `errorCode` is returned on duplicate, and does it ever include a safe reference to an existing `vehicleId`?
- [ ] Vehicle create: Does create-vehicle require `partyId` association at create time; if optional, what is the behavior when omitted?
- [ ] Vehicle create: What are the exact create and load-by-`vehicleId` service names/endpoints and DTO schemas (including standard error payload shape)?
- [ ] Vehicle create: What permission keys gate vehicle creation and vehicle view?
- [ ] Vehicle create: Is license plate stored as a single string or as plate+region; if region is required, what are field names and validation rules?

## End

End of document.
