# STORY_VALIDATION_CHECKLIST.md for domain: accounting

This checklist is intended for engineers and reviewers to validate story implementations in the accounting domain. It covers correctness, security, auditability, and operational robustness for accounting UI flows (Moqui screens + services), including exports, operational ingestion monitoring, and AR workflows.

---

## Scope/Ownership

- [ ] Confirm the story is labeled and implemented under `domain:accounting` (and remove/avoid legacy `domain:payment` labeling where the capability is accounting-owned).
- [ ] Verify the primary actor(s) and permissions match the story (Accounting Clerk vs Accounting Ops vs Auditor) and the UI does not expose actions outside that role.
- [ ] Confirm cross-domain data is treated as read-only unless an explicit contract exists (e.g., People/Timekeeping time entries, CRM customer lookup).
- [ ] Verify the UI does not infer accounting policy (posting rules, GL impacts, tax policy) beyond what backend explicitly returns.
- [ ] Confirm navigation routes/screen paths follow repo conventions and are placed under the correct Accounting menu area (e.g., Accounting → Integrations/Event Ingestion, Accounting → Receivables).
- [ ] Verify new configuration/admin capabilities (CoA, Posting Categories/Keys/Mappings, Posting Rule Sets) are explicitly confirmed as Accounting-owned and not implemented under `domain:inventory`/`domain:general` placeholders.

---

## Data Model & Validation

- [ ] Validate all required request inputs are present and correctly typed before submit (dates, UUIDs, enums, amounts).
- [ ] Verify date range validation is enforced client-side: `endDate >= startDate` (inclusive) for exports and list filters.
- [ ] Verify UUID validation behavior is consistent per screen:
  - [ ] If IDs are guaranteed UUIDs, block invalid UUID input with inline errors.
  - [ ] If IDs are not guaranteed UUIDs, allow free-text and rely on backend validation (do not block legitimate non-UUID identifiers).
- [ ] Verify currency formatting uses `currencyUomId` from backend and does not assume a default currency.
- [ ] Verify money input validation for “Apply Payment”:
  - [ ] `amountToApply > 0` for each selected invoice.
  - [ ] `amountToApply <= invoice.balanceDue` (based on loaded data; still expect backend enforcement).
  - [ ] `sum(amountToApply) <= payment.unappliedAmount` (based on loaded data; still expect backend enforcement).
  - [ ] Amount inputs enforce currency scale/rounding rules consistent with backend (no floating point; decimal only).
- [ ] Verify “Apply Payment” eligibility rules are enforced in UI using backend-provided status fields (do not hardcode status strings unless confirmed):
  - [ ] Submit disabled when payment is not eligible (e.g., status not `AVAILABLE` or `unappliedAmount <= 0`).
  - [ ] Invoices shown/eligible only when backend indicates eligible (or via canonical status enum list).
- [ ] Verify export parameter validation:
  - [ ] `startDate` and `endDate` required.
  - [ ] At least one `locationId` required unless backend explicitly supports “all locations”.
  - [ ] `format` required and constrained to allowed values (CSV/JSON).
- [ ] Verify time zone semantics are not guessed:
  - [ ] UI displays which timezone is used for interpreting `startDate/endDate` once confirmed (location vs business unit vs user).
- [ ] Verify audit fields displayed in UI are read-only and sourced from backend (`createdAt/By`, `updatedAt/By`, `occurredAt`, `receivedAt`).
- [ ] Verify payload display fields are treated as untrusted content:
  - [ ] Render JSON safely (no HTML injection).
  - [ ] Truncate/virtualize very large payloads to avoid UI lockups.
- [ ] Verify UUIDv7 validation is enforced for all identifiers confirmed as UUIDv7 (e.g., `eventId`, `correlationId` if UUID, `refundId`, `paymentId`, `invoiceId`, `ingestionId`, `journalEntryId`, `ledgerTransactionId`, `glAccountId`, `postingCategoryId`, `mappingKeyId`, `glMappingId`, `postingRuleSetId`, `billId`).
- [ ] Verify Event Ingestion Submit (sync tool) form validation:
  - [ ] Required fields are non-empty after trimming: `specVersion`, `eventType`, `sourceModule`, `eventTimestamp`, `schemaVersion`, `payload`.
  - [ ] `eventTimestamp` is ISO-8601 parseable (and UI sends a stable ISO string per contract).
  - [ ] `payload` is syntactically valid JSON before submit.
  - [ ] If backend requires `payload` to be a JSON object, UI blocks non-object JSON values once confirmed.
- [ ] Verify CoA (GL Account) validation:
  - [ ] `accountCode` and `accountName` are trimmed and non-empty on create.
  - [ ] `accountType` is constrained to allowed enum values (ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE) from backend/canonical list.
  - [ ] Effective dating validation: if `activeThru` provided, enforce `activeThru > activeFrom` client-side.
  - [ ] Deactivate effective date is required and validated as a datetime per backend contract.
- [ ] Verify Posting Categories / Mapping Keys / GL Mappings validation:
  - [ ] Posting Category code is trimmed and non-empty; uniqueness violations are surfaced from backend.
  - [ ] Mapping Key is trimmed and non-empty; uniqueness violations are surfaced from backend.
  - [ ] Mapping Key → Posting Category association enforces deterministic 1:1 (each key links to exactly one category) per backend contract.
  - [ ] GL Mapping effective dates enforce `effectiveEndDate >= effectiveStartDate` client-side.
  - [ ] UI prevents creating GL mappings for INACTIVE categories when status is provided by backend.
- [ ] Verify Posting Rule Set validation:
  - [ ] `eventType` selection is required for new rule set creation and comes from an authoritative backend list (no hardcoded event types).
  - [ ] `rulesDefinition` is syntactically valid JSON before save/publish when edited as JSON text.
  - [ ] UI does not claim a rule set is “balanced” unless backend returns a validation result; any UI messaging reflects backend validation outcome only.
- [ ] Verify Journal Entry (draft JE review) filter validation:
  - [ ] `eventId` filter blocks invalid UUIDv7 and does not call backend.
  - [ ] Date range filter blocks invalid ranges and does not call backend.
- [ ] Verify Journal Entry balance-by-currency computation:
  - [ ] Totals are grouped by `currencyUomId`.
  - [ ] Comparison uses backend-confirmed rounding/scale rules (no floating point; decimal compare).
- [ ] Verify AP Vendor Bill list/detail validation:
  - [ ] UUIDv7 validation blocks invalid `billId`, `vendorId`, `purchaseOrderId`, `receiptId`, `sourceEventId/eventId`, `ingestionId` inputs/route params.
  - [ ] Bill date range filter blocks invalid ranges and does not call backend.

---

## API Contract

- [ ] Verify each screen has a concrete backend service/endpoint contract documented and implemented (service name/path, request params, response fields).
- [ ] Verify list endpoints support server-side pagination and sorting (pageIndex/pageSize/orderBy) and UI uses them (no client-side full dataset loads).
- [ ] Verify error response handling is consistent and actionable:
  - [ ] 400/422 validation errors map to inline field errors where possible.
  - [ ] 401/403 show access denied without leaking record existence.
  - [ ] 404 shows “not found” with safe messaging.
  - [ ] 409 shows conflict/reload guidance (optimistic locking / state changed).
  - [ ] 5xx/timeout shows retry affordance and preserves user inputs/filters.
- [ ] Verify per-row/per-invoice error shapes are supported for “Apply Payment” (highlight the affected invoice row when backend returns invoice-specific errors).
- [ ] Verify export download contract is implemented exactly as backend specifies:
  - [ ] Binary stream download vs `downloadUrl` handling is correct.
  - [ ] Content-Type and filename handling are correct and safe.
- [ ] Verify filters use the correct timestamp basis per backend contract (occurredAt vs receivedAt) and the UI labels it accurately.
- [ ] Verify Event Ingestion Submit (sync tool) uses the exact backend endpoint path and schema (no placeholder paths in merged code).
- [ ] Verify canonical error shape `{ errorCode, message, details? }` is supported end-to-end:
  - [ ] UI renders `errorCode` and `message` without exposing sensitive internals.
  - [ ] If `details` includes field keys, UI maps them to the correct form fields (and ignores unknown keys safely).
- [ ] Verify CoA endpoints and field names match backend contract (e.g., `glAccountId` vs `accountId`) and UI does not assume naming.
- [ ] Verify Posting Categories/Keys/Mappings endpoints and overlap-conflict error details are rendered as provided (conflicting IDs/date ranges), without UI inference.
- [ ] Verify Posting Rule Set endpoints support version addressing (`postingRuleSetId` + `version`) and UI uses backend-provided version/status (no client-side version math unless contract says so).
- [ ] Verify EventType catalog endpoint is used for Posting Rule Set creation and is cached/loaded per contract (no hardcoded list).
- [ ] Verify Journal Entry list/detail endpoints return all fields required for traceability (eventId/source refs/rule version) and UI handles missing optional fields with explicit “Not available” messaging.
- [ ] Verify AP Vendor Bill list/detail endpoints return posting references and ingestion visibility fields per contract, and UI does not fabricate them when absent.

---

## Events & Idempotency

- [ ] Verify ingestion monitoring UIs display backend-provided idempotency outcomes without reinterpretation:
  - [ ] `DUPLICATE_IGNORED` shows “duplicate ignored; no new postings created” (or equivalent).
  - [ ] `DUPLICATE_CONFLICT` / `QUARANTINED` shows conflict banner and any backend-provided references.
- [ ] Verify ingestion detail screens show traceability identifiers (copyable):
  - [ ] `eventId`, `eventType`, `schemaVersion`, `sourceModule`, `correlationId` (if provided).
  - [ ] Posting references (`ledgerTransactionId`, `journalEntryId`, `transactionId`) when provided.
- [ ] Verify “Apply Payment” submit uses an idempotency key:
  - [ ] Frontend generates `applicationRequestId` once per submit attempt and sends it to backend.
  - [ ] UI prevents double-submit while request is in flight.
  - [ ] On retry after timeout, UI behavior is deterministic (either reuse the same idempotency key if stored, or clearly treat as a new attempt per agreed policy).
- [ ] Verify export requests handle idempotency/conflict if backend supports it:
  - [ ] If backend returns 409 “already in progress”, UI offers “refresh status / download existing export” only when supported by contract.
- [ ] Verify optional “retry/reprocess” actions (if implemented) are:
  - [ ] Visible only for retry-eligible statuses.
  - [ ] Protected by explicit permission checks.
  - [ ] Safe against repeated clicks (disable while in flight).
- [ ] Verify Event Ingestion Submit (sync tool) supports idempotent replay testing:
  - [ ] UI clearly displays backend-provided `idempotencyOutcome` (if returned) and does not infer replay from client state.
  - [ ] UI handles 409 duplicate conflict with deterministic guidance (change `eventId` or revert payload) and preserves the entered envelope for retry.
- [ ] Verify configuration mutation screens (CoA, Posting Categories/Keys/Mappings, Posting Rule Sets) prevent double-submit:
  - [ ] Create/update/deactivate/publish/archive buttons disable while in flight.
  - [ ] On timeout, UI provides a safe retry path and does not show success unless backend confirms.
- [ ] Verify optimistic locking/concurrency behavior is implemented when backend supports it (ETag/version):
  - [ ] UI sends required version/ETag on update/deactivate/publish/archive when specified.
  - [ ] UI handles 409/412 by prompting reload and discarding stale edits safely.

---

## Security

- [ ] Verify screen-level authorization is enforced for each new Accounting screen (Moqui artifact authz), not only via hidden buttons.
- [ ] Verify action-level authorization is enforced for mutations:
  - [ ] “Apply Payment” submit requires the correct permission token.
  - [ ] “Assign Customer” (if supported) requires the correct permission token and is disabled/hidden otherwise.
  - [ ] Export execution requires the correct permission token (distinct from view permission if applicable).
- [ ] Verify sensitive payload visibility is gated:
  - [ ] `sourceEventPayload` and raw event payload JSON are shown only to authorized roles.
  - [ ] If payload is restricted, UI shows a “Restricted” placeholder and does not fetch or render the payload when possible.
- [ ] Verify no secrets/credentials are ever displayed or logged (including in error toasts, console logs, or network error dumps).
- [ ] Verify PII minimization in export UX:
  - [ ] UI does not display PII-heavy “skipped entry details” by default; only counts unless explicitly approved.
- [ ] Verify file download handling is safe:
  - [ ] Download URLs (if used) are same-origin or explicitly allowed and do not expose tokens in UI logs.
  - [ ] UI does not embed untrusted URLs without validation.
- [ ] Verify unauthorized users cannot infer existence of refunds/payments/events by probing IDs (consistent 403/404 behavior per policy).
- [ ] Verify Event Ingestion Submit (sync tool) is deny-by-default:
  - [ ] Screen view permission is explicit and enforced by Moqui artifact authz.
  - [ ] Submit action permission is explicit and enforced (separate from view).
  - [ ] Backend 401/403 responses are handled without revealing whether an `eventId` exists.
- [ ] Verify configuration screens (CoA, Posting Categories/Keys/Mappings, Posting Rule Sets) enforce least privilege:
  - [ ] View vs manage permissions are distinct (if defined) and enforced at screen + action level.
  - [ ] Read-only roles (Auditor) cannot access create/edit/deactivate/publish/archive transitions even via crafted requests.
- [ ] Verify JSON editors/viewers (payload, rulesDefinition) do not enable script execution:
  - [ ] Render as text/code with escaping; no HTML rendering of JSON content.
  - [ ] Do not persist sensitive JSON in localStorage/sessionStorage unless explicitly approved; prefer in-memory only.

---

## Observability

- [ ] Verify UI surfaces support-friendly identifiers on success/failure:
  - [ ] Export: `exportId`/`correlationId`, counts, parameters.
  - [ ] Apply Payment: `applicationRequestId`, created record IDs, timestamps.
  - [ ] Ingestion: `ingestionId`/`eventId`, status, error code, posting refs.
- [ ] Verify Moqui server logs (for screen transitions/service calls) include key identifiers (paymentId, invoiceId, eventId, ingestionId, exportId) and do not include full payload bodies.
- [ ] Verify correlation/trace header propagation is implemented if the project has a standard (header name and behavior confirmed).
- [ ] Verify audit/history views (when provided by backend) display immutable audit records (who/when/parameters/outcome) and do not allow edits.
- [ ] Verify W3C Trace Context propagation is implemented on all outbound calls:
  - [ ] `traceparent` is propagated unchanged when present.
  - [ ] `traceparent` is generated only if absent (per project standard).
  - [ ] `tracestate` is propagated when present.
- [ ] Verify Event Ingestion Submit (sync tool) displays copyable support identifiers:
  - [ ] `eventId` (submitted and/or returned), `correlationId` (if present), `receivedAt` (if returned), `traceparent` used, `httpStatus`, `errorCode` (if any).
- [ ] Verify configuration mutation screens surface support details on failure:
  - [ ] CoA: `glAccountId`, attempted action (create/update/deactivate), `errorCode`, request timestamp.
  - [ ] Posting config: `postingCategoryId`/`mappingKeyId`/`glMappingId`, `errorCode`, conflict IDs/date ranges when provided.
  - [ ] Posting Rule Sets: `postingRuleSetId`, `version`, action (publish/archive/createVersion), `errorCode`, request timestamp.
- [ ] Verify client-side telemetry (if present) does not include raw payloads (`payload`, `rulesDefinition`) and includes only identifiers + error codes.

---

## Performance & Failure Modes

- [ ] Verify list screens use server-side pagination and do not fetch full payloads for list rows (payload only on detail).
- [ ] Verify default list load performance targets are met (first page within ~2s under normal conditions) and UI shows loading states.
- [ ] Verify large JSON payload rendering is performant:
  - [ ] Use collapsible sections and lazy rendering.
  - [ ] Apply size limits/truncation with “download/view more” only if backend supports it.
- [ ] Verify export flow handles both synchronous and async modes safely:
  - [ ] If async, polling/refresh does not hammer backend (reasonable interval/backoff) and can be stopped.
  - [ ] If sync, UI handles long-running requests with progress indication and timeout messaging.
- [ ] Verify concurrency/conflict handling for “Apply Payment”:
  - [ ] On 409, UI prompts reload and clears stale allocations.
  - [ ] UI does not show success state unless backend confirms success.
- [ ] Verify empty-result handling is correct and auditable:
  - [ ] Export with 0 records still produces a downloadable artifact and UI indicates 0 exported.
  - [ ] List screens show clear empty states with “clear filters”.
- [ ] Verify Event Ingestion Submit (sync tool) failure modes:
  - [ ] Network timeout/5xx preserves entered envelope and payload for retry.
  - [ ] UI shows in-flight state within ~250ms of submit and prevents double-submit.
  - [ ] UI does not freeze when rendering large response metadata; raw payload echoes are gated and truncated.
- [ ] Verify Posting Rule Set list performance:
  - [ ] UI does not N+1 fetch versions per rule set unless backend contract explicitly requires it and performance is acceptable.
- [ ] Verify Journal Entry detail rendering handles large line counts:
  - [ ] Lines table uses virtualization or pagination when line counts are large (per UI framework capability).
  - [ ] Balance computation is efficient and does not block the main thread for large datasets.
- [ ] Verify configuration screens handle conflict/reload safely:
  - [ ] On 409/412, UI offers reload and clearly indicates edits may be lost; no silent overwrite.

---

## Testing

- [ ] Add unit tests for client-side validation:
  - [ ] Date range validation (inclusive).
  - [ ] UUID validation behavior (block vs allow) per decided policy.
  - [ ] Apply Payment amount constraints and sum constraints.
- [ ] Add integration tests (or contract tests) for API interactions:
  - [ ] List pagination/sorting parameters are sent and handled.
  - [ ] 401/403/404/409/5xx mapping to UI states is correct.
- [ ] Add tests for idempotency/double-submit prevention:
  - [ ] Apply Payment submit disables button and does not send duplicate requests.
  - [ ] Retry behavior after timeout follows agreed idempotency policy.
- [ ] Add tests for restricted payload behavior:
  - [ ] Unauthorized users cannot see `sourceEventPayload` / raw event payload.
  - [ ] UI renders “Restricted” and does not leak payload via logs.
- [ ] Add tests for export download behavior:
  - [ ] Binary download path works (correct filename/content-type handling).
  - [ ] URL-based download path works (if supported).
  - [ ] Async export status transitions render correctly (QUEUED/PROCESSING/READY/FAILED).
- [ ] Add accessibility checks for tables/forms:
  - [ ] Keyboard navigation for allocation table and filters.
  - [ ] Error messages announced (aria-live) and not color-only.
- [ ] Add tests for Event Ingestion Submit (sync tool):
  - [ ] Required field validation blocks submit.
  - [ ] Invalid UUIDv7 `eventId` blocks submit.
  - [ ] Invalid JSON payload blocks submit.
  - [ ] 409 conflict renders guidance and preserves inputs.
  - [ ] 401/403 renders access denied without existence leakage.
  - [ ] `traceparent` propagation/generation behavior is correct.
- [ ] Add tests for CoA screens:
  - [ ] Create validation (required fields, enum constraint).
  - [ ] Effective dating validation (`activeThru > activeFrom`).
  - [ ] Duplicate code error maps to `accountCode` field.
  - [ ] 409/412 conflict prompts reload and prevents stale overwrite.
- [ ] Add tests for Posting Categories/Keys/Mappings:
  - [ ] Non-overlap conflict (409) renders backend-provided conflict details.
  - [ ] INACTIVE category blocks “New GL Mapping” in UI when status provided.
- [ ] Add tests for Posting Rule Sets:
  - [ ] rulesDefinition JSON validation blocks save when invalid.
  - [ ] Publish failure renders backend validation details and keeps status DRAFT.
  - [ ] Publish/archive buttons are hidden/disabled based on status + permissions.
- [ ] Add tests for Journal Entry balance-by-currency:
  - [ ] Totals computed per currency and compared using configured scale rules.
  - [ ] Missing mapping rule reference shows “Missing mapping reference” warning.
- [ ] Add tests for AP Vendor Bill screens:
  - [ ] UUIDv7 validation blocks invalid deep links/filters.
  - [ ] Quarantine/conflict ingestion banner renders when `processingStatus=QUARANTINED` or `idempotencyOutcome=DUPLICATE_CONFLICT`.
  - [ ] Posting reference link behavior uses `journalEntryId` as primary when present.

---

## Documentation

- [ ] Document each new screen route, menu placement, and required permissions (view vs execute vs retry vs payload-view).
- [ ] Document backend service contracts used by the frontend (service names/paths, request/response schemas, error schema).
- [ ] Document canonical status enums used in UI for:
  - [ ] Payment status (e.g., UNAPPLIED/APPLIED/AVAILABLE/etc.).
  - [ ] Invoice eligibility statuses.
  - [ ] Ingestion `processingStatus` and idempotency outcomes.
  - [ ] Refund statuses.
- [ ] Document payload visibility/redaction policy (what fields are safe to display; who can view raw payload).
- [ ] Document export semantics:
  - [ ] Date interpretation timezone.
  - [ ] Location filtering rules.
  - [ ] Output formats and empty dataset behavior.
  - [ ] Skipped-entry reporting behavior (counts vs downloadable report).
- [ ] Document operational runbook links for common failure codes shown in UI (e.g., schema validation failed, duplicate conflict, currency mismatch).
- [ ] Document Event Ingestion Submit (sync tool):
  - [ ] Endpoint path, request envelope fields, and success/error response schema.
  - [ ] Permission tokens for view vs submit vs payload-view.
  - [ ] Trace header behavior (`traceparent`/`tracestate`) and what identifiers to provide to support.
- [ ] Document CoA (GL Account) capability:
  - [ ] Endpoint family, field names, and concurrency control mechanism (ETag/version).
  - [ ] Deactivation policy constraints and stable `errorCode` taxonomy.
- [ ] Document Posting Categories/Keys/Mappings:
  - [ ] Endpoint family and effective-dating non-overlap rule.
  - [ ] Dimensions schema (types, required/optional, lookup sources).
  - [ ] Immutability/versioning policy (in-place edit vs versioned).
- [ ] Document Posting Rule Sets:
  - [ ] Endpoint family, version semantics, and status lifecycle (DRAFT/PUBLISHED/ARCHIVED).
  - [ ] EventType catalog source endpoint and caching strategy.
  - [ ] Publish validation error `details` shape (sample payload) for UI rendering.
- [ ] Document Journal Entry review screens:
  - [ ] Field naming conventions (`eventId` vs `sourceEventId`, `mappingRuleVersionId`).
  - [ ] Balance check rounding/scale rules per currency.
- [ ] Document AP Vendor Bills:
  - [ ] Endpoint family, status enums, and origin event type taxonomy.
  - [ ] Posting reference navigation route for `journalEntryId` (if available) and required permission.

---

## Open Questions to Resolve

## 1. What are the exact Moqui endpoints/services and schemas for

- Timekeeping export request, status polling (if any), and download (binary vs URL)?
- Payments list/detail, and (optional) customer assignment?
- Apply Payment: load payment, list eligible invoices, submit applications (including per-invoice error shape)?
- Refunds list/detail and linkage fields (paymentId/invoiceId/originalTxnRef)?
- Ingestion monitoring: list/detail for `InvoiceIssued`, `InvoiceAdjusted`, `CreditMemoIssued` (and any retry endpoint)?

**Response:**
All accounting UI flows rely on **Accounting-owned Moqui services**, exposed under a consistent `/accounting/*` namespace. The frontend must not infer schemas.

Authoritative service families:

- **Timekeeping export**

  - `/accounting/export/request`
  - `/accounting/export/status`
  - `/accounting/export/download`
- **Payments (accounting read model)**

  - `/accounting/payments/list`
  - `/accounting/payments/detail`
  - `/accounting/payments/assignCustomer`
- **Apply payment**

  - `/accounting/ar/payment`
  - `/accounting/ar/eligibleInvoices`
  - `/accounting/ar/apply`
- **Refunds (read-only)**

  - `/accounting/refunds/list`
  - `/accounting/refunds/detail`
- **Ingestion monitoring**

  - `/accounting/ingestion/list`
  - `/accounting/ingestion/detail`
  - `/accounting/ingestion/retry` (async)

Exact request/response schemas are backend-authoritative and must be referenced from the backend contract documentation; frontend must not invent fields or enums.

---

## 2. Is timekeeping export synchronous, async, or both? If both, what determines the mode and what statuses are returned?

**Response:**
Timekeeping export is **asynchronous only**.

Status lifecycle:

- `QUEUED`
- `PROCESSING`
- `READY`
- `FAILED`

Rationale:

- Potentially large datasets
- Auditability requirements
- Retry and operational visibility

Download is permitted only in `READY` state via backend endpoint.

---

## 3. What permission strings/roles gate

- Viewing vs executing timekeeping exports?
- Viewing refunds?
- Viewing payments list/detail?
- Viewing raw payload (`sourceEventPayload` / event payload JSON)?
- Assigning/changing `customerId` on a payment?
- Applying payments to invoices?
- Retrying/reprocessing ingestion records (if supported)?

**Response:**

| Capability                 | Permission                           |
| -------------------------- | ------------------------------------ |
| View time export screen    | `accounting:time-export:view`        |
| Execute time export        | `accounting:time-export:execute`     |
| View payments              | `accounting:payment:view`            |
| Assign customer to payment | `accounting:payment:assign-customer` |
| Apply payment              | `accounting:ar:apply-payment`        |
| View refunds               | `accounting:refund:view`             |
| View ingestion monitoring  | `accounting:events:view`             |
| Retry ingestion            | `accounting:events:retry`            |
| View raw payload JSON      | `accounting:events:view-payload`     |

Auditor roles are read-only and never granted mutation permissions.

---

## 4. What is the authoritative source and filtering rules for the export location selector (by business unit and user access)?

**Response:**
The location selector is sourced from the **Location domain** (`domain:location`).

Filtering rules:

- Locations must belong to the user’s authorized business unit(s).
- Locations must be active.
- UI must not allow arbitrary location IDs.

---

## 5. What timezone is used to interpret `startDate/endDate` for exports (location vs business unit vs user)?

**Response:**
Dates are interpreted in the **location’s local timezone**.

The UI must explicitly label the timezone used and must not assume user or system timezone.

---

## 6. For exports, should the UI show only skipped counts or also provide a downloadable skipped-report artifact?

**Response:**
Default behavior:

- UI shows **counts only** (exported vs skipped).

Optional behavior:

- A downloadable skipped-entry report may be provided **only to auditor-authorized users**, and must exclude unnecessary PII.

---

## 7. Are `refundId`, `eventId`, `paymentId`, `invoiceId`, and ingestion identifiers guaranteed UUIDs (and which version), or should UI accept arbitrary strings?

**Response:**
All identifiers are **UUIDv7**.

The UI **must validate UUID format client-side** and block invalid input.

---

## 8. What are the canonical enum values for

- Refund status?
- Invoice status eligibility for payment application?
- Payment status values relevant to apply/assign flows?
- Ingestion `processingStatus` and idempotency outcomes?

**Response:**

**Refund status**

- `PENDING`
- `COMPLETED`
- `FAILED`
- `QUARANTINED`

**Payment status (accounting view)**

- `AVAILABLE`
- `APPLIED`
- `CLOSED`

**Invoice eligibility**

- Eligible: issued, open, balanceDue > 0
- Ineligible: draft, voided, cancelled, paid-in-full

**Ingestion statuses**

- `processingStatus`: `PROCESSED`, `REJECTED`, `QUARANTINED`
- `idempotencyOutcome`: `NEW`, `DUPLICATE_IGNORED`, `DUPLICATE_CONFLICT`

UI must treat enums as backend-authoritative.

---

## 9. For ingestion lists, should filtering default to `occurredAt` (event time) or `receivedAt` (ingestion time), and which fields are always available?

**Response:**
Default filtering is based on **`receivedAt` (ingestion time)**.

Fields guaranteed to exist:

- `eventId`
- `eventType`
- `receivedAt`
- `processingStatus`
- `idempotencyOutcome`

`occurredAt` may be present but is not guaranteed for all events.

---

## 10. What posting reference identifiers should be displayed and linked (ledgerTransactionId vs journalEntryId vs transactionId), and what are the canonical routes to view them?

**Response:**
Primary reference:

- `journalEntryId` (canonical navigation target)

Secondary reference:

- `ledgerTransactionId` (display only)

UI navigation must link to journal entry views when available.

---

## 11. Is it acceptable to display raw event payload JSON in the UI, or must the backend provide a redacted `payloadSummary` only?

**Response:**
Default behavior:

- Display **redacted `payloadSummary` only**.

Raw JSON:

- Allowed **only** with `accounting:events:view-payload` permission.
- Must never be logged or cached client-side.

---

## 12. Is manual association of `customerId` on ingested payments allowed, and can it be changed after set? Is a reason/justification required and what are its constraints?

**Response:**
Manual association **is allowed once only**.

Rules:

- Requires permission `accounting:payment:assign-customer`
- Requires mandatory justification text (minimum 10 characters)
- Once set, the customer **cannot be changed**
- Action must be fully audited

---

## 13. What is the correlation/trace header standard for this frontend (header name, generation rules, logging expectations)?

**Response:**
The system uses **W3C Trace Context**:

- `traceparent` (required)
- `tracestate` (optional)

Frontend must propagate these headers unchanged on all backend calls.

---

## 14. What is the exact backend endpoint path and response schema for Event Ingestion Submit (sync tool)?

- [ ] Confirm the exact `POST` path under `/accounting/*` for sync event submission.
- [ ] Confirm the success response fields (e.g., `eventId`, `receivedAt`, `processingStatus/status`, `idempotencyOutcome`, `payloadSummary`).
- [ ] Confirm the error response uses `{ errorCode, message, details? }` and whether `eventId` may be present on errors.

---

## 15. What permissions gate the Event Ingestion Submit (sync tool)?

- [ ] Confirm the screen view permission token (new token vs reuse `accounting:events:view`).
- [ ] Confirm the submit/ingest permission token for human users (do not reuse undefined `SCOPE_accounting:events:ingest` unless it is formally defined).
- [ ] Confirm whether payload viewing uses existing `accounting:events:view-payload` only, and whether payload summary is visible to `accounting:events:view` users.

---

## 16. Does the ingestion sync endpoint accept `payload` as any JSON value or object-only?

- [ ] Confirm whether `payload` must be a JSON object.
- [ ] If object-only, confirm the backend `errorCode` and `details` shape returned for non-object payloads so UI can map errors correctly.

---

## 17. What are the backend contracts and permissions for CoA (GLAccount) maintenance?

- [ ] Confirm endpoints/services for list/detail/create/update/deactivate and exact field names (`glAccountId` vs `accountId`, etc.).
- [ ] Confirm explicit permission tokens for view vs manage (create/update/deactivate).
- [ ] Confirm deactivation policy constraints and stable `errorCode` values for UI display.
- [ ] Confirm whether inactive accounts are editable (name/description) or immutable.
- [ ] Confirm whether list supports server-side filtering by derived status (Active/Inactive/NotYetActive).
- [ ] Confirm optimistic locking mechanism (ETag/version), required request fields/headers, and conflict status code (409 vs 412).

---

## 18. What are the backend contracts, permissions, and dimension schema for Posting Categories / Mapping Keys / GL Mappings?

- [ ] Confirm endpoints/services for Posting Category CRUD/deactivate, Mapping Key CRUD/linking, GL Mapping create/history, and GL Account lookup.
- [ ] Confirm explicit permission tokens for view vs manage posting configuration.
- [ ] Confirm dimensions schema (names, types, required/optional, lookup sources) and whether dimensions are validated server-side with field-level `details`.
- [ ] Confirm immutability/versioning policy (in-place edit vs versioned/append-only) for categories, keys, and mappings.
- [ ] Confirm overlap handling policy (always reject with 409 vs auto-end-date prior mapping) and the conflict `details` payload shape.
- [ ] Confirm whether a mapping resolution test endpoint exists (`mappingKey + transactionDate → postingCategory + glAccount + dimensions`) and its permissions.

---

## 19. What are the backend contracts, permissions, and validation detail shape for Posting Rule Sets?

- [ ] Confirm endpoints/services for list/detail/versionDetail/create/createVersion/publish/archive and their schemas.
- [ ] Confirm explicit permission tokens for view/create/upversion/publish/archive.
- [ ] Confirm authoritative EventType catalog endpoint/service and whether it is shared with ingestion event types.
- [ ] Confirm `rulesDefinition` format (raw JSON vs structured editor) and provide JSON schema/link for validation.
- [ ] Confirm version creation semantics (`baseVersion` required vs auto-increment) and 409 conflict behavior for concurrent version creation.
- [ ] Confirm archiving policy (eligible base versions, un-archive allowed, restrictions).
- [ ] Confirm list optimization contract (latest version summary available vs requiring per-rule-set version fetch).
- [ ] Confirm publish validation failure `details` structure (sample payload) for UI rendering.

---

## 20. What are the backend contracts and rounding/scale rules for Journal Entry review?

- [ ] Confirm endpoints/services for JE list/search and JE detail (including lines) and exact field names (`eventId` vs `sourceEventId`, `mappingRuleVersionId` naming).
- [ ] Confirm permission token(s) for viewing journal entries.
- [ ] Confirm rounding/scale rules for balance-by-currency comparison (exact vs currency-scale rounding, and scale per currency).
- [ ] Confirm whether mapping failures/quarantine/DLQ visibility is handled via ingestion monitoring screens or a separate failure view.

---

## 21. What are the backend contracts, permissions, and enums for AP Vendor Bills?

- [ ] Confirm endpoints/services for listing Vendor Bills and loading detail (including line items).
- [ ] Confirm whether ingestion visibility fields are embedded on the bill or linked via `ingestionId` and the exact schema.
- [ ] Confirm posting reference fields and whether `journalEntryId` is always the primary reference when present.
- [ ] Confirm explicit permission tokens for viewing Vendor Bills and for linking to Journal Entry detail (if link exists).
- [ ] Confirm canonical upstream origin event type strings that can create Vendor Bills (no inference).
- [ ] Confirm Vendor Bill status enum values for filtering/default tabs.

---

## End

End of document.
