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

## Data Model & Validation

- [ ] Validate required inputs and types (`partyId`, `vehicleId`, `eventId` are UUID when specified)
- [ ] Verify date/time semantics and timezone labeling for displayed timestamps
- [ ] Validate vehicle care preferences fields:
  - [ ] `rotationInterval` integer and 1–100000
  - [ ] `rotationIntervalUnit` required when interval present; enum: `MILES`, `KM`
  - [ ] Free-form notes treated as text only

## API Contract

- [ ] Verify endpoints/services and DTO fields are documented near the owning component
- [ ] Verify pagination (`pageIndex`, `pageSize`) and sorting for all list screens
- [ ] Verify error payloads include `errorCode` and do not leak sensitive data

## Events & Idempotency

- [ ] Inbound event processing is idempotent by `eventId`
- [ ] UI and services handle retry semantics without creating duplicates

## Security

- [ ] Permission gating for sensitive payloads and redaction
- [ ] UI never renders backend strings as HTML
- [ ] Snapshot calls are proxied server-side; no browser-to-snapshot direct calls

## Observability

- [ ] Trace identifiers and audit fields surface in UI and logs
- [ ] UI telemetry avoids raw payloads, VIN, and plate values

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

### Q: Are operator actions (reprocess/retry/ack/export/mark resolved) required? If yes, define allowed actions and audit requirements.

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

### Q: Vehicle care preferences: allowed `rotationIntervalUnit` enum values, whether unit is required when interval is provided, and min/max constraints.

- Acceptance: Unit must be `MILES` or `KM`; missing unit with interval returns 400.
- Test Fixtures: interval=6000 with missing unit.
- Example API request/response (code block)

```json
{"errorCode":"FIELD_INVALID","fieldErrors":{"rotationIntervalUnit":"Required when rotationInterval is set"}}
```

### Q: Vehicle lookup: confirm endpoints/methods/response shapes for search and vehicle+owner snapshot.

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

### Q: Vehicle lookup: ranking logic and whether backend returns `rankScore`/`matchType`.

- Acceptance: UI renders match info only if present; it does not compute ranking.
- Test Fixtures: Response includes matchType.
- Example API request/response (code block)

```json
{"matchType":"VIN_PREFIX","rankScore":0.92}
```

### Q: Vehicle lookup: minimum query length and matching rules (contains vs startsWith) for VIN/unit/plate.

- Acceptance: Queries shorter than 3 are rejected; UI disables submit.
- Test Fixtures: query="AB".
- Example API request/response (code block)

```json
{"errorCode":"QUERY_TOO_SHORT","message":"Query must be at least 3 characters"}
```

### Q: Vehicle lookup: result limiting/pagination and whether truncation is indicated in response.

- Acceptance: Response includes `total` and paginated items.
- Test Fixtures: total > pageSize.
- Example API request/response (code block)

```json
{"pageIndex":0,"pageSize":25,"total":120,"items":[...]}
```

### Q: Vehicle lookup: permission model and required behavior when permission is missing (hide entry point vs show blocked screen).

- Acceptance: Without permission, backend returns 403; UI shows blocked state.
- Test Fixtures: User without permission.
- Example API request/response (code block)

```json
{"errorCode":"FORBIDDEN"}
```

### Q: Vehicle lookup: PII policy for displaying/logging full VIN and license plate (masking standard).

- Acceptance: UI does not emit VIN/plate into client logs/telemetry; server logs mask these values.
- Test Fixtures: Inspect client logs.
- Example API request/response (code block)

```text
Client telemetry contains no VIN/licensePlate values.
```

### Q: Vehicle lookup: Moqui screen-flow handoff mechanism to return selected vehicle context to estimate creation (return transition vs redirect params vs shared context).

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

### Q: Contact points/preferences/contact roles/merge/commercial account creation: confirm canonical identifiers (`partyId` vs `personId` vs `customerId`) used in routes and service calls.

- Acceptance: All routes use `partyId`; no endpoint requires `customerId` as primary identifier.
- Test Fixtures: Verify UI routes and request payloads.
- Example API request/response (code block)

```json
{"errorCode":"MISSING_PARTY_ID","message":"partyId is required"}
```

### Q: Contact points: phone validation policy (E.164 vs digits-only vs extensions) and whether `kind` is mutable after creation.

- Acceptance: Invalid phone values are rejected; kind cannot be changed after creation.
- Test Fixtures: Attempt to edit kind.
- Example API request/response (code block)

```json
{"errorCode":"FIELD_INVALID","fieldErrors":{"kind":"Immutable"}}
```

### Q: Party merge: search requirements (allow browse vs require criteria), party types allowed, alias/redirect behavior, and conflict resolution policy (“survivor wins”).

- Acceptance: Merge requires explicit selection and justification; reads of merged party return 409 with `mergedToPartyId`.
- Test Fixtures: Merge two parties then load merged party.
- Example API request/response (code block)

```json
{"errorCode":"PARTY_MERGED","mergedToPartyId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"}
```

### Q: Contact roles: CRM vs Billing ownership, role list source of truth, and primary auto-demotion vs reject behavior.

- Acceptance: CRM services manage roles; primary selection auto-demotes prior primary.
- Test Fixtures: Set primary on another contact.
- Example API request/response (code block)

```json
{"role":"BILLING","primaryContactPartyId":"..."}
```

### Q: Commercial account creation: duplicate detection inputs and backend response shape (separate duplicateCheck vs create returns duplicates requiring confirmation), billing terms source, external identifiers validation, and post-create navigation route.

- Acceptance: Duplicate-check returns candidates; create returns 409 with candidates unless override is provided.
- Test Fixtures: Create with duplicate name/phone.
- Example API request/response (code block)

```json
{"errorCode":"DUPLICATE_CANDIDATES","candidates":[{"partyId":"...","matchReason":"NAME+PHONE"}]}
```

## End

End of document.
