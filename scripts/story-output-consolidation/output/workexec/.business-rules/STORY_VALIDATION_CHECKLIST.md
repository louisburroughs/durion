```markdown
# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates implementation of `workexec` stories for correctness, security, auditability, and operational robustness. It incorporates resolved open questions from the prior workexec guide and replaces “blocking unknowns” with explicit default decisions and testable acceptance criteria. Acceptance criteria blocks below match the open questions in `AGENT_GUIDE.md`.

## Completed items

- [x] Updated acceptance criteria for each resolved open question

## Scope/Ownership

- [ ] Confirm story labeled `domain:workexec`
- [ ] Verify primary actor(s) and permissions
- [ ] Confirm story is not mis-labeled when SoR is outside workexec (e.g., approvals workflow vs security; appointments in shopmgr; timekeeping in people); document dependency and ownership boundary explicitly
- [ ] Verify Moqui screen ownership: new/modified screens live under the correct component (`durion-workexec` vs `durion-shopmgr` vs `durion-people`) and navigation uses canonical routes
- [ ] Verify “in-scope vs out-of-scope” is enforced in UI (no hidden CRUD for excluded domains such as time-entry CRUD under workexec)

## Data Model & Validation

- [ ] Validate required inputs and types (IDs are opaque strings)
- [ ] Verify date/time and timezone semantics for workexec flows
- [ ] Quantity and rounding rules: quantities must be validated server-side; UI must not assume integer-only unless contract says so
- [ ] Verify money fields are treated as decimals (no floating point math for totals); UI displays backend-calculated totals as authoritative
- [ ] Verify estimate edit gating: estimate line mutations are blocked when estimate status != `DRAFT`
- [ ] Verify work order approval gating: “Record Approval” is blocked unless work order status == `AWAITING_APPROVAL` (or authoritative equivalent)
- [ ] Verify digital signature validation: submission is blocked when signature is empty and PNG generation fails gracefully
- [ ] Verify partial approval validation: every approval-eligible line has an explicit decision before enabling confirm
- [ ] Verify proof requirements are backend-driven: if backend indicates proof required (signature/note/reason), UI blocks submit until present
- [ ] Verify non-catalog part validation (when allowed): description non-empty, unit price present, quantity > 0
- [ ] Verify labor/service validation: labor units > 0 for time-based services; flat-rate editability follows backend contract (no client assumptions)
- [ ] Verify override reason validation: when `requireOverrideReason=true`, override cannot be saved without a reason code
- [ ] Verify revision/versioning rules: prior estimate versions are immutable/read-only; only active version is editable
- [ ] Verify approval expiration handling uses backend as source of truth (status/flag); UI does not compute expiration from client time unless explicitly supported by contract
- [ ] Verify deep-link handling: if user navigates directly to an action screen (approval capture/record approval/summary review) and entity is not eligible, UI shows blocking message and safe navigation back

## API Contract

- [ ] Verify endpoints, pagination, error handling, per-row errors
- [ ] Verify canonical base paths are consistent across stories (`/rest/api/v1/...` vs `/api/v1/...` vs `/api/...`) and are implemented exactly as confirmed for this repo
- [ ] Verify list/search endpoints used by pickers are paginated and require filters when necessary (avoid unbounded global lists)
- [ ] Verify product/service search contracts return stable identifiers and required display fields (e.g., `productId/serviceCode`, description) and pagination metadata (`pageIndex/pageSize/totalCount` or equivalent)
- [ ] Verify mutation endpoints return enough data to refresh UI deterministically (updated entity/totals/status and any concurrency token if used), or UI performs an explicit re-fetch on success
- [ ] Verify standard error envelope for 400/409 includes `correlationId` and optional `fieldErrors[]` and `existingResourceId` on duplicates
- [ ] Verify 409 semantics are consistent and actionable:
  - [ ] invalid state transition (e.g., approval no longer eligible)
  - [ ] optimistic concurrency conflict (stale version/etag)
  - [ ] idempotency replay/duplicate create
- [ ] Verify field-level errors map to UI fields (e.g., missing signature, missing deny reason, missing override reason, invalid quantity)
- [ ] Verify “already exists/already promoted” behaviors return a stable identifier for navigation (e.g., `existingResourceId` or explicit `workOrderId`) and UI uses it without guessing type

## Events & Idempotency

- [ ] UI and services handle retry semantics (reuse idempotency key on retry)
- [ ] Verify all submit-like mutations send `Idempotency-Key` (UUID) and reuse it on retry for the same user attempt:
  - [ ] digital approval submit (estimate/work order)
  - [ ] partial approval record (atomic)
  - [ ] estimate add part/labor (create line)
  - [ ] estimate revise/create new version
  - [ ] estimate summary snapshot generation
  - [ ] price override mutations (if treated as submit-like by backend)
- [ ] Verify UI disables submit controls while request is in-flight to prevent double-submit
- [ ] Verify idempotency replay results in a single user-visible success state (no duplicate confirmations, no duplicate line items/approvals)
- [ ] Verify event ingestion (when applicable) persists inbound events to an inbox first and processes asynchronously with DB-level idempotency
- [ ] Verify duplicate event delivery does not double-apply state transitions (e.g., invoice issuance mapping to `WO_INVOICED` is applied once)

## Security

- [ ] Permission gating for sensitive payloads and raw payload redaction
- [ ] Verify deny-by-default behavior: backend enforces with 401/403 even if UI hides actions
- [ ] Verify capability/permission signal is used only for UX gating; backend remains authoritative
- [ ] Verify approval actions are permission-gated:
  - [ ] capture digital approval
  - [ ] record partial approval
  - [ ] approve/deny in approvals inbox/detail (if implemented)
- [ ] Verify sensitive data handling for signatures:
  - [ ] do not log raw Base64 signature image or stroke data in client logs
  - [ ] do not display raw signature payload after submission; only show “signature on file”/reference metadata if provided
- [ ] Verify customer-facing summary hides internal-only fields (cost/margin/internal notes) even if present in payload (defense-in-depth)
- [ ] Verify override/manager-only actions (e.g., operational context override, conflict override, variance approval) require explicit permission and are audited
- [ ] Verify correlation IDs shown in UI are in a support-only “Details” affordance and do not expose sensitive backend internals

## Observability

- [ ] Ensure trace identifiers and audit fields surface in UI and logs
- [ ] Verify UI captures and displays `correlationId` from error envelope in an expandable details section for support
- [ ] Verify UI logs include action name and relevant opaque IDs (estimateId/workOrderId/approvalId/lineId) without PII
- [ ] Verify audit metadata is displayed when available:
  - [ ] estimate/work order “last updated at/by”
  - [ ] approval recorded timestamp/method/recorded-by (if returned)
  - [ ] estimate version history (createdAt/createdBy/revisionReason if exposed)
  - [ ] snapshot generation metadata (snapshotAt/generatedBy/legalTermsSource)
- [ ] Verify any “history/timeline” UI is read-only and reflects refreshed backend state after mutations

## Performance & Failure Modes

- [ ] Verify search inputs are debounced and paginated (avoid request floods)
- [ ] Verify signature capture remains responsive (avoid heavy re-renders while drawing)
- [ ] Verify large line-item lists (e.g., up to 100 items) render with acceptable performance and have loading states
- [ ] Verify network/5xx failures show retry affordance and do not auto-resubmit without user action
- [ ] Verify 409 conflict flows provide a deterministic recovery path (“Refresh”/“Reload”) and do not lose user input unnecessarily
- [ ] Verify partial failures are handled safely:
  - [ ] if totals refresh fails after a successful mutation, UI indicates stale view and offers reload
  - [ ] if secondary data (e.g., availability) fails, primary screen still renders (non-blocking dependencies)

## Testing

- [ ] Verify unit tests cover client-side validation rules (quantity > 0, labor units > 0, signature required, required decisions, required reason codes)
- [ ] Verify integration tests cover:
  - [ ] idempotent retry (timeout then retry with same `Idempotency-Key` yields single outcome)
  - [ ] 409 invalid state (approval no longer eligible) triggers refresh flow
  - [ ] 409 concurrency conflict (stale version) triggers reload and prevents silent overwrite
  - [ ] 403 forbidden for gated actions (UI hides/disables and backend enforcement handled)
  - [ ] 400 fieldErrors mapping to correct fields
- [ ] Verify snapshot summary tests assert totals are rendered from snapshot payload and not recomputed client-side
- [ ] Verify customer-summary visibility tests assert internal fields are not rendered even if present in payload
- [ ] Verify deep-link tests for ineligible states show blocking message and safe navigation back
- [ ] Verify non-UUID opaque IDs work end-to-end in routes, forms, and API calls

## Documentation

- [ ] Verify story documents canonical screen routes and component paths for any new/modified screens
- [ ] Verify API contract references are updated with definitive endpoints and example payloads once confirmed
- [ ] Verify permission/capability keys used by UI are documented (source of truth and where UI reads them)
- [ ] Verify timezone display rules are documented for any timestamps shown (user timezone vs shop timezone bucketing)
- [ ] Verify sensitive logging/redaction guidance is documented for signature payloads and other sensitive fields

## Acceptance Criteria (per resolved question)

### Q: Substitutes domain ownership: Story #109 is labeled `domain:workexec` but is product/parts admin flavored. Should this be `domain:inventory` or a product domain label?

- Acceptance: Any SubstituteLink authoring/admin story is labeled `domain:inventory` (or product/catalog) and treated as a dependency by workexec stories.
- Test Fixtures: A SubstituteLink create screen is documented under inventory/product docs and not under workexec ownership.
- Example API request/response:

```http
POST /rest/api/v1/inventory/substitutes
Idempotency-Key: 11111111-1111-1111-1111-111111111111

201 Created
```

### Q: Operational context story ownership: Frontend issue labels “Shop Management/user” but backend reference is `domain:workexec` with Shopmgr as SoR. Confirm ownership/label.

- Acceptance: Operational context data is sourced from shopmgr; workexec screens display it read-only unless an override permission is present.
- Test Fixtures: A work order linked to a shopmgr appointment.
- Example API request/response:

```http
GET /rest/api/v1/shopmgr/appointments/APPT-1

200 OK
```

### Q: Timekeeping / people-adjacent features: Several stories label domain conflicts (user/shop management vs workexec). Confirm what belongs in workexec UI vs people/shopmgr UI.

- Acceptance: No time-entry CRUD is implemented under workexec; dispatch views may show People availability as a read-only signal.
- Test Fixtures: People availability API returns schedule/availability.
- Example API request/response:

```http
GET /rest/api/v1/people/availability?locationId=LOC-1&date=2026-01-19

200 OK
```

### Q: What are the canonical Moqui screen paths/routes for work order detail, estimate detail, appointment detail, reporting/dispatch screens?

- Acceptance: Navigation uses the existing Moqui workexec and shopmgr screens (work orders/estimates in `durion-workexec`, appointments in `durion-shopmgr`).
- Test Fixtures: Work order board loads and links to work order edit.
- Example API request/response:

```http
GET /webroot/durion-workexec/WorkOrderBoard

200 OK
```

### Q: Part lookup UX: What endpoint/screen should admin UI use to search/select parts by SKU/name? Provide route(s) + response shape.

- Acceptance: Part selection uses a search/picker with pagination; manual ID entry is not required for normal workflows.
- Test Fixtures: Search query `brake` returns at least one part.
- Example API request/response:

```http
GET /rest/api/v1/product/parts?query=brake&pageIndex=0&pageSize=20

200 OK
```

### Q: SubstituteLink list/search: Do we have `GET /api/v1/substitutes` with filters/pagination, or must list be “query by partId” only?

- Acceptance: UI defaults to “select part → list substitutes for part”; any global list is paginated and requires filters.
- Test Fixtures: Substitute list requires `partId` or equivalent filter.
- Example API request/response:

```http
GET /rest/api/v1/inventory/parts/P-100/substitutes

200 OK
```

### Q: Substitution picker endpoints: Exact endpoints and payload schemas for fetching candidates (WO/Estimate) and applying a selected substitute.

- Acceptance: Picker works for both WorkOrder and Estimate targets and returns eligibility + pricing + `canEnterManualPrice`.
- Test Fixtures: One WorkOrder line and one Estimate line.
- Example API request/response:

```http
GET /rest/api/v1/workexec/substitution-candidates?targetType=WORK_ORDER&targetId=WO-1&lineId=LINE-1

200 OK
```

### Q: Dispatch Board endpoint contract: Confirm exact endpoint path and request/response schema for `DispatchBoardView` and `ExceptionIndicator`.

- Acceptance: Dispatch view is read-only; exception codes are server-provided and stable.
- Test Fixtures: Board response contains an `asOf` timestamp or UI shows “Last updated”.
- Example API request/response:

```http
GET /webroot/durion-workexec/WorkOrderBoard

200 OK
```

### Q: Aggregation responsibility: Does dispatch board already include mechanic availability and bay occupancy, or must frontend call People availability separately?

- Acceptance: If People availability is separate, UI fetches it in parallel and does not block board rendering on failure.
- Test Fixtures: People API returns 500 while board returns 200.
- Example API request/response:

```http
GET /rest/api/v1/people/availability?locationId=LOC-1&date=2026-01-19

500 Internal Server Error
```

### Q: Appointments vs Work Orders: Are “appointments” separate entities or represented as work orders with scheduled times? If separate, what endpoint supplies them?

- Acceptance: Appointments remain shopmgr entities; work orders link via `appointmentId`.
- Test Fixtures: `DurWorkOrder.appointmentId` is populated.
- Example API request/response:

```json
{"workOrderId":"WO-1","appointmentId":"APPT-1"}
```

### Q: Assignment context endpoints: Exact endpoints/services for loading work order detail, updating assignment context, and fetching audit/history.

- Acceptance: Assignment edits are performed through workexec services and are state-gated; audit/history is available via metadata or a dedicated append-only audit log when required.
- Test Fixtures: Work order status transitions to started while edit is attempted.
- Example API request/response:

```http
PUT /rest/api/v1/workexec/workorders/WO-1

409 Conflict
```

### Q: Operational context override contract: Does override require `version`? What field name? What response shape and success status (200 vs 201)?

- Acceptance: Overrides require manager permission, are audited, and are concurrency-safe (409 on stale version when token exists). Success returns 200 with updated context when possible.
- Test Fixtures: Two users edit same operational context.
- Example API request/response:

```http
POST /rest/api/v1/workexec/workorders/WO-1/operational-context:override

200 OK
```

### Q: Event ingestion mechanism: How are Workexec events delivered/handled in this Moqui repo (webhook, broker consumer, polling/inbox)?

- Acceptance: Events are persisted to an inbox first and processed asynchronously with DB-level idempotency.
- Test Fixtures: Duplicate delivery of the same event.
- Example API request/response:

```http
POST /rest/api/v1/workexec/events

202 Accepted
```

### Q: Invoice event semantics: Is `InvoiceIssued` a separate event type or a status within `WorkorderStatusChanged`? What fields are present?

- Acceptance: In Moqui, invoice issuance transitions the work order to `WO_INVOICED`; if an `InvoiceIssued` event exists later, it maps to the same transition without double-applying.
- Test Fixtures: Creating invoice sets status to invoiced.
- Example API request/response:

```json
{"workOrderId":"WO-1","statusId":"WO_INVOICED"}
```

### Q: Standard error envelope: For 400/409, what is the standard error response format (field errors, message, correlationId, existingResourceId on duplicates)?

- Acceptance: 400/409 errors conform to the standard envelope and include `correlationId`.
- Test Fixtures: Duplicate create returns 409 with `existingResourceId`.
- Example API request/response:

```json
{"code":"DUPLICATE","correlationId":"c-1","existingResourceId":"SUB-1"}
```

### Q: Idempotency-Key usage: Should frontend generate/send `Idempotency-Key` for create calls by default?

- Acceptance: UI sends `Idempotency-Key` for create/submit actions and reuses it on retry; server returns the same outcome.
- Test Fixtures: Double-click submit.
- Example API request/response:

```http
POST /rest/api/v1/inventory/substitutes
Idempotency-Key: 22222222-2222-2222-2222-222222222222

201 Created
```

### Q: Duplicate signaling: For duplicates (e.g., SubstituteLink), does backend return existing resource id? If yes, where?

- Acceptance: Duplicate create returns 409 with `existingResourceId` in the standard error envelope.
- Test Fixtures: Create same SubstituteLink twice.
- Example API request/response:

```http
409 Conflict
Content-Type: application/json

{"existingResourceId":"SUB-1"}
```

### Q: Permission signal source: How does frontend determine permission scopes/capabilities (session claims, user context endpoint, embedded in payload)?

- Acceptance: UI relies on a capability signal (user context endpoint or session claims) and backend enforces with 403.
- Test Fixtures: User without permission attempts override.
- Example API request/response:

```http
403 Forbidden
```

### Q: Manual price override permission: How does backend indicate user has `ENTER_MANUAL_PRICE` and what is expected frontend behavior when pricing is unavailable?

- Acceptance: Picker response includes `canEnterManualPrice`; if false and price is missing, UI blocks apply. Backend also enforces.
- Test Fixtures: Candidate with `price=null`.
- Example API request/response:

```json
{"price":null,"canEnterManualPrice":false}
```

### Q: Dispatch Board RBAC: Which roles/permissions may view Dispatch Board? Location membership only or explicit permission (e.g., `DISPATCH_VIEW`)?

- Acceptance: Board requires an explicit permission (e.g., `WORKEXEC_DISPATCH_VIEW`) and location membership.
- Test Fixtures: Unauthorized user loads board.
- Example API request/response:

```http
GET /webroot/durion-workexec/WorkOrderBoard

403 Forbidden
```

### Q: Editability of SubstituteLink key fields: On update, are `partId` and `substitutePartId` immutable or editable? If editable, how handle uniqueness conflicts?

- Acceptance: `partId` and `substitutePartId` are immutable; updates cannot change them; changing either requires new + deactivate old.
- Test Fixtures: Edit screen disables key fields.
- Example API request/response:

```http
PUT /rest/api/v1/inventory/substitutes/SUB-1

200 OK
```

### Q: Defaults alignment: Does backend default `priority=100` and `isAutoSuggest=false` when omitted?

- Acceptance: Omitting these fields results in `priority=100` and `isAutoSuggest=false` in the created resource.
- Test Fixtures: Create request omits both fields.
- Example API request/response:

```json
{"priority":100,"isAutoSuggest":false}
```

### Q: Candidate inclusion rules: Should candidate list include only available candidates or both available/unavailable with statuses?

- Acceptance: Default candidate list includes only eligible/available; optional `includeUnavailable=true` includes unavailable with reason codes.
- Test Fixtures: Candidate unavailable due to no on-hand.
- Example API request/response:

```json
{"availabilityStatus":"UNAVAILABLE","reason":"NO_ON_HAND"}
```

### Q: Eligibility source for substitution: How does frontend determine “original is unavailable”? Is there a line-level field or should backend enforce?

- Acceptance: Backend enforces eligibility; UI may display flags but must not enforce solely client-side.
- Test Fixtures: Backend rejects apply when not eligible.
- Example API request/response:

```http
400 Bad Request
```

### Q: Override-after-start rule: After work starts, are overrides disallowed, manager-only, or versioned without mutating locked snapshot?

- Acceptance: Manager-only overrides are allowed after start with explicit audit records and without mutating a start snapshot.
- Test Fixtures: Manager override performed after status is started.
- Example API request/response:

```http
POST /rest/api/v1/workexec/workorders/WO-1/operational-context:override

200 OK
```

### Q: Which statuses count as “work started”: Is `READY_FOR_PICKUP` considered started for lock rules?

- Acceptance: `READY_FOR_PICKUP` is treated as started (post-start); assignment edits are rejected.
- Test Fixtures: Work order in READY_FOR_PICKUP.
- Example API request/response:

```http
PUT /rest/api/v1/workexec/workorders/WO-1

409 Conflict
```

### Q: “Team” definition: Is team represented by `assignedMechanics[]` only or separate team entity?

- Acceptance: Team is represented by assigned mechanic(s) on the work order; no team entity is required.
- Test Fixtures: Work order shows one primary mechanic.
- Example API request/response:

```json
{"mechanicId":"M-1"}
```

### Q: Substitute audit trail API: Is there an API to fetch `SubstituteAudit` entries for display? If not, should UI show only created/updated metadata?

- Acceptance: UI shows created/updated metadata; audit endpoint is optional and append-only if implemented.
- Test Fixtures: Substitute detail view shows `createdDate`/`updatedDate`.
- Example API request/response:

```http
GET /rest/api/v1/inventory/substitutes/SUB-1

200 OK
```

### Q: Assignment/override audit source: Should UI display generic work order transition history, a specific assignment sync log, or both?

- Acceptance: UI can display both transition history and override audit log; if override log is not implemented yet, transitions + metadata are shown.
- Test Fixtures: Override performed and appears in history.
- Example API request/response:

```json
{"events":[{"eventType":"OVERRIDE"}]}
```

### Q: Event failure handling: Should orphaned/invalid events be stored in DLQ outside Moqui, in Moqui DB for review, or both?

- Acceptance: Failures are persisted for ops review and also emitted to a DLQ/alerting mechanism.
- Test Fixtures: Orphan event without a mapping.
- Example API request/response:

```json
{"status":"FAILED","failureReason":"ORPHAN"}
```

### Q: Timezone source: Should timestamps display in shop/location timezone or user preference timezone? How does frontend obtain location timezone?

- Acceptance: UI labels the timezone used; timestamps display in user timezone; day-bucket filters use shop timezone when available; safe-to-defer until shop timezone source exists.
- Test Fixtures: Two users with different timezones view same board day.
- Example API request/response:

```json
{"viewDate":"2026-01-19","timezone":"America/Chicago"}
```

### Q: ID types: Confirm identifier types (uuid vs numeric vs prefixed strings) for `locationId`, `resourceId`, `mechanicId`, `partId`, etc., and whether UI should use searchable pickers.

- Acceptance: UI treats IDs as opaque strings and uses pickers/search; no UUID-only validation.
- Test Fixtures: Non-UUID IDs work end-to-end.
- Example API request/response:

```json
{"workOrderId":"WO-123","appointmentId":"APPT-9","locationId":"BAY-2"}
```

## Open Questions to Resolve

- [ ] Confirm canonical API base path and exact endpoints used by this Moqui frontend for:
  - [ ] Estimate detail GET
  - [ ] Work order detail GET
  - [ ] Estimate approval POST
  - [ ] Work order approval POST
- [ ] Confirm authoritative `statusId` values for “awaiting customer approval” and “approved/ineligible” for both Estimate and Work Order
- [ ] Confirm approval submission payload schema for digital approvals, including any required `approvalPayload` fields and any required document digest/version to prevent approving stale totals
- [ ] Confirm whether signature strokes are required; if required, confirm coordinate normalization, timestamp semantics, and stroke grouping schema
- [ ] Confirm signer identity requirements: whether customer identifier is required, field name, and source in POS
- [ ] Confirm approval POST success response shape (does it return `approvalId`, `approvedAt`, and/or approval summary; if not, where approval metadata is read after refresh)
- [ ] Confirm backend contract for Work Order partial approval:
  - [ ] load work order detail includes line approval fields and editability flags
  - [ ] approval requirements/config endpoint (or embedded fields) and precedence rules
  - [ ] submit partial approval endpoint path, request/response schema, and idempotency behavior
- [ ] Confirm Work Order line item identifier model for approvals (separate `WorkOrderService` vs `WorkOrderPart` collections and IDs to echo back in `lineDecisions`)
- [ ] Confirm supported approval methods and proof schema for Work Orders (signature token vs file ref vs note-only), including how UI detects unsupported methods
- [ ] Confirm terminal outcome mapping when all work order items are declined (exact returned `statusId`)
- [ ] Confirm approval window semantics for Work Orders (whether backend provides `approvalWindowEnd` and how expiration is represented)
- [ ] Confirm approvals inbox/detail domain ownership and endpoints:
  - [ ] list approvals endpoint + pagination schema
  - [ ] approval detail endpoint schema (status/isExpired/expiresAt/version)
  - [ ] approve/deny endpoints and required inputs (version, deny reason)
  - [ ] error envelope and deterministic error codes for expired/not-actionable/version conflict
- [ ] Confirm timezone/display standard for approval expiration timestamps (`expiresAt`) and whether UI must rely solely on backend status/flag vs computing from time
- [ ] Confirm deny reason requirements and validation rules (required/optional, min/max length, allowed characters) and whether enforced server-side only or also client-side
- [ ] Confirm estimate parts/labor contracts used by this repo:
  - [ ] estimate load includes items + totals + concurrency token (if any)
  - [ ] product parts search endpoint schema and pagination
  - [ ] service catalog search endpoint schema and pagination
  - [ ] create catalog part item endpoint schema
  - [ ] create non-catalog part item endpoint schema and policy gating
  - [ ] add labor/service line endpoint schema (catalog)
  - [ ] add custom labor endpoint schema (if supported)
  - [ ] update quantity endpoint schema and concurrency behavior
  - [ ] override price endpoint schema and whether it requires idempotency key
- [ ] Confirm capability/policy signal source and exact field names for:
  - [ ] `canOverridePrice`
  - [ ] `allowNonCatalogParts`
  - [ ] `requireOverrideReason`
  - [ ] custom labor allowed
  - [ ] revise estimate allowed
  - [ ] view customer summary allowed
  - [ ] proceed-to-approval allowed
- [ ] Confirm override reason codes source endpoint/schema and whether codes are location-scoped
- [ ] Confirm estimate concurrency token mechanism (field/header name: `version`, `lastUpdatedStamp`, ETag/If-Match) and 409 behavior for stale updates
- [ ] Confirm estimate versioning/revision contracts:
  - [ ] create revision endpoint/service name and response (newVersionId/newVersionNumber/status)
  - [ ] list versions endpoint schema and pagination
  - [ ] load specific version endpoint schema
  - [ ] save active version endpoint schema and recalculated totals response
  - [ ] revise eligibility for `APPROVED` and `DECLINED` (and whether `reopenEstimate()` is separate/preferred for declined)
- [ ] Confirm estimate line item type schema and required fields per type (PART/LABOR/SERVICE/FEE/etc.) for editing and compare views
- [ ] Confirm currency source for formatting when totals do not include `currencyUomId/currencyCode`
- [ ] Confirm estimate customer-summary snapshot contracts:
  - [ ] generate snapshot endpoint/service name, request schema, and idempotency behavior
  - [ ] fetch snapshot endpoint schema (customer-facing DTO vs visibility flags)
  - [ ] legal terms handling policy outcomes (FAIL vs USE_DEFAULTS) and error codes
  - [ ] whether backend returns `downloadUrl`/rendered HTML and whether URLs are time-limited
- [ ] Confirm whether any approval token/link is returned by backend and whether POS users are allowed to view/copy it; if not allowed, verify UI never renders it

## End

End of document.
```
