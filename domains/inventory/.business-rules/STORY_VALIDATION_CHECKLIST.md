```markdown
# STORY_VALIDATION_CHECKLIST (inventory)

## Scope/Ownership
- [ ] Verify each story is correctly labeled `domain:inventory` and resolve conflicts where frontend issue text says `domain:user` or `Product / Parts Management` but inventory backend owns the capability.
- [ ] Verify each screen/flow has a clear primary persona and entry point (Inventory menu vs contextual links from Product/Work Order/Estimate).
- [ ] Verify out-of-scope items are explicitly not implemented (e.g., no inventory edits on Availability screen; no ledger mutation; no UOM conversions when stated out-of-scope; no product master editing in fitment hints story).
- [ ] Verify Moqui integration pattern is consistent with repo conventions (direct Vue→backend vs Moqui service proxy), and the chosen pattern is documented per story.
- [ ] Verify all “optional” sub-scopes are either implemented or explicitly deferred (e.g., deep-linking, adjustment screens vs link-out, manual sync trigger, unmapped status updates).
- [ ] Verify cross-module entry points are explicitly enumerated and tested (e.g., “View Movement History” from Work Order Line; Availability link from estimate/work order if implemented).
- [ ] Verify admin/ops screens (Topology, SyncLog, ingestion monitoring) are placed under the correct navigation namespace and are not exposed to general users by default.

## Data Model & Validation
- [ ] Verify required identifiers are validated client-side before calling backend (trimmed non-empty SKU, required locationId, required productId where applicable).
- [ ] Verify Availability query validation:
  - [ ] `productSku` is trimmed and non-empty; whitespace-only is rejected.
  - [ ] `locationId` is required.
  - [ ] `storageLocationId` is optional and cannot be provided unless `locationId` is present.
  - [ ] If `storageLocationId` is provided, UI prevents selecting a storage location outside the selected `locationId` when picker supports it; otherwise backend error is surfaced clearly.
- [ ] Verify date range filters enforce `from <= to` and block submit when invalid (Ledger list, SyncLog list, ingestion runs, exception queues, any list screens with date filters).
- [ ] Verify quantity inputs enforce domain rules where applicable:
  - [ ] Availability display never allows editing quantities (read-only).
  - [ ] Adjustment request `quantityChange` is required and cannot be zero (block submit).
  - [ ] Quantity fields accept decimals only if backend/UOM rules allow; otherwise enforce integer-only (confirm per workflow).
- [ ] Verify inventory quantities displayed are never shown as `null/undefined`; render numeric values only when backend returns success.
- [ ] Verify “no inventory history” is treated as success for Availability:
  - [ ] UI renders `onHandQuantity`, `allocatedQuantity`, `availableToPromiseQuantity` as `0` only when backend returns success with zeros (not on error).
  - [ ] UI shows an explicit “No inventory history” note in the success state.
- [ ] Verify storage location create/update validation:
  - [ ] `siteId`, `name`, `barcode`, `storageType` required on create.
  - [ ] `barcode` is trimmed and non-empty; duplicates are surfaced as field errors.
  - [ ] Parent selection prevents self-parenting; cycle prevention errors from backend are displayed clearly.
  - [ ] Parent picker is constrained to same `siteId`.
- [ ] Verify deactivation workflow validation:
  - [ ] Deactivate action is disabled/hidden when already inactive.
  - [ ] If backend indicates destination required for non-empty location, UI requires `destinationLocationId` and prevents submit without it.
  - [ ] Destination must be Active, same site, and not equal to source (enforced in UI when possible; backend authoritative).
- [ ] Verify inactive/pending location selection rules are enforced consistently across “new stock movement” screens (block selection; allow read-only display for historical records).
- [ ] Verify tags/JSON fields are handled safely:
  - [ ] `tags`, `capacity`, `temperature`, SyncLog payloads, and ingestion payloads render via safe JSON viewer (no HTML injection).
  - [ ] Large JSON payloads are truncated in list views with explicit “expand” behavior.
- [ ] Verify fitment hints/tag editor validation (if implemented under inventory domain):
  - [ ] Prevent submitting a hint with blank `tagType`/key or blank `tagValue`.
  - [ ] Prevent duplicate tag rows within a hint when backend enforces uniqueness (or surface backend field errors precisely).
  - [ ] Enforce year range format rules exactly as specified by backend (no “best guess” parsing).
  - [ ] Prevent submitting a hint with zero tags unless backend explicitly allows it.

## API Contract
- [ ] Verify exact endpoint/service names, HTTP methods, and parameter names are implemented as agreed (no “TBD” left in code).
- [ ] Verify request/response envelope handling matches backend (plain JSON vs `{data: ...}`) and is consistent across inventory screens.
- [ ] Verify Availability contract implementation:
  - [ ] Request includes `productSku`, `locationId`, and optional `storageLocationId` only when provided.
  - [ ] Response fields are mapped exactly: `onHandQuantity`, `allocatedQuantity`, `availableToPromiseQuantity`, `unitOfMeasure`.
- [ ] Verify pagination contract is implemented correctly for list screens (Ledger, LocationRef, SyncLog, ingestion runs, normalized availability, unmapped parts, exception queues):
  - [ ] Page size/index (or token) is sent correctly.
  - [ ] Total count (or next token) is handled correctly.
  - [ ] Sorting parameters match backend expectations (e.g., `-timestamp`, `-appliedAt`).
- [ ] Verify error payload parsing is robust:
  - [ ] Field-level validation errors map to the correct form fields when possible (including row-indexed errors for tag editors).
  - [ ] Unknown error shapes fall back to a generic message without crashing.
- [ ] Verify 404 semantics are handled per flow:
  - [ ] Availability: 404 SKU vs 404 location show distinct user messages.
  - [ ] Detail screens: 404 routes back to list with “Not found”.
- [ ] Verify 422 semantics are handled where specified:
  - [ ] Inactive location rejection shows a clear, actionable message and preserves inputs.
- [ ] Verify list endpoints support server-side filtering and the UI does not implement client-side filtering over unbounded datasets.

## Events & Idempotency
- [ ] Verify read-only screens do not emit mutation events or side effects (Availability query, Ledger view, LocationRef/SyncLog views, ingestion monitoring views).
- [ ] Verify mutation actions (create/update/deactivate storage location; adjustment create/post if implemented; fitment hint CRUD if implemented; exception/unmapped status updates if supported) are protected against double-submit:
  - [ ] Submit buttons disabled while request is in-flight.
  - [ ] UI prevents duplicate transitions on rapid clicks/navigation.
- [ ] Verify idempotency expectations are documented and implemented where backend supports it (e.g., idempotency key header for posting actions); if not supported, UI retry behavior is conservative (no automatic retries for mutations).
- [ ] Verify “manual trigger” actions (e.g., “Sync now”, ingestion trigger) are guarded against repeated clicks and show clear in-flight/progress state.
- [ ] Verify eventual consistency expectations are handled:
  - [ ] If a mutation returns success but list/detail may lag, UI shows “refresh” affordance and does not assume immediate list visibility.

## Security
- [ ] Verify all inventory screens enforce authentication (401 routes to login/session refresh per app convention).
- [ ] Verify authorization is enforced in UI and backend:
  - [ ] Navigation items and action buttons are hidden/disabled when user lacks permission (if permissions are discoverable).
  - [ ] Backend 403 responses are handled gracefully with a forbidden state and no data leakage.
- [ ] Verify sensitive data handling:
  - [ ] Availability quantities are not logged to console/telemetry unless explicitly approved.
  - [ ] SyncLog payload visibility is permission-gated; if 403, payload is not shown and UI does not attempt to render cached payload.
  - [ ] Ingestion/exception payloads (if present) are treated as potentially sensitive; do not log raw payloads by default.
- [ ] Verify no secrets are stored in frontend code/config (API keys, tokens); only use existing auth/session mechanisms.
- [ ] Verify input fields that accept IDs (SKU, UUIDs, hrLocationId, sourceTransactionId) are treated as untrusted:
  - [ ] Proper encoding in URLs/query params.
  - [ ] No direct HTML rendering of user-provided strings.
- [ ] Verify deep-linking does not bypass authorization (screen must still enforce 401/403 and must not show cached results from a prior authorized session/user).

## Observability
- [ ] Verify each screen logs structured client events per repo convention:
  - [ ] Request initiated (without sensitive payloads).
  - [ ] Request success with timing.
  - [ ] Request failure with HTTP status and safe error code/message.
- [ ] Verify correlation/request ID propagation and display:
  - [ ] If backend returns `X-Correlation-Id` (or equivalent), include it in error UI “Technical details” and logs.
  - [ ] If frontend generates correlation IDs, ensure they are passed consistently.
- [ ] Verify Availability logging is safe:
  - [ ] Log SKU and location identifiers only as allowed by policy; do not log returned quantities unless approved.
  - [ ] Log whether `storageLocationId` was provided (boolean) rather than always logging the raw value if considered sensitive.
- [ ] Verify error UI does not expose stack traces or raw backend internals; only safe, actionable messages.
- [ ] Verify audit metadata is displayed when provided (createdAt/By, updatedAt/By, deactivatedAt/By, actorId on ledger entries) and omitted when not provided (no fabricated values).

## Performance & Failure Modes
- [ ] Verify list screens use server-side pagination and do not fetch unbounded datasets (Ledger, LocationRef, SyncLog, ingestion runs, normalized availability, unmapped parts, exception queues).
- [ ] Verify large JSON payloads (SyncLog payloads, ingestion payloads, tags) are not rendered eagerly in tables; use preview + on-demand expand.
- [ ] Verify loading states prevent UI thrash:
  - [ ] Show spinner/progress during calls.
  - [ ] Disable inputs/actions during in-flight requests where appropriate.
- [ ] Verify retry UX:
  - [ ] For transient network/5xx errors, show “Try again” and keep user inputs/filters.
  - [ ] For mutations, do not auto-retry; require explicit user retry.
- [ ] Verify Availability deep-link auto-run behavior:
  - [ ] Auto-run executes at most once per load unless inputs change.
  - [ ] No infinite loops when query params update the form state.
  - [ ] Results are marked “stale” when inputs change after a success.
- [ ] Verify SLA-related UX for Availability:
  - [ ] UI shows a loading indicator quickly (no “frozen” feel).
  - [ ] If request exceeds a reasonable client timeout, show a timeout message and allow retry (do not keep spinner indefinitely).

## Testing
- [ ] Verify unit tests cover client-side validation rules:
  - [ ] Required fields (SKU/locationId).
  - [ ] Date range validation.
  - [ ] Adjustment quantity non-zero.
  - [ ] Parent selection cannot be self.
  - [ ] Fitment hint tag row validation (no blank type/value; year range format).
- [ ] Verify component tests cover state transitions: `idle → loading → success/error` and correct UI rendering for each.
- [ ] Verify API contract tests/mocks cover:
  - [ ] Success responses (including “no history” zeros for Availability).
  - [ ] 400 field validation errors (including row-indexed errors for tag editors).
  - [ ] 401/403 forbidden flows.
  - [ ] 404 not found flows.
  - [ ] 409 conflict (where applicable: adjustments, hint updates, optimistic locking).
  - [ ] 422 inactive location rejection.
- [ ] Verify E2E tests for critical flows:
  - [ ] Availability query via form submit and via deep-link query params (if supported).
  - [ ] Ledger list filtering and detail rendering with nullable from/to locations.
  - [ ] Storage location create/update/deactivate (including destination-required path).
  - [ ] Topology LocationRef/SyncLog list filtering and payload viewer permission handling.
  - [ ] Ingestion monitoring list screens (runs/availability/unmapped/exceptions) enforce pagination and safe payload rendering (if implemented).
- [ ] Verify accessibility checks:
  - [ ] Form labels and ARIA association for validation errors.
  - [ ] Keyboard navigation for tables/forms/dialogs.
  - [ ] Status not conveyed by color alone.
  - [ ] JSON/payload viewer is keyboard accessible and supports copy/select.

## Documentation
- [ ] Verify each new/modified screen has a short README or inline doc comment describing:
  - [ ] Route/path and entry point.
  - [ ] Backend service(s) called and parameters.
  - [ ] Required permissions.
- [ ] Verify error mapping table is documented for each flow (400/401/403/404/409/422/5xx) with user-facing messages.
- [ ] Verify any repo-wide conventions used are documented/linked (routing conventions, correlation ID handling, permission discovery mechanism).
- [ ] Verify “safe defaults” applied in stories are reflected in implementation notes (pagination defaults, empty states, loading states).
- [ ] Verify deep-linking behavior (if supported) is documented: canonical query param names, auto-run rules, and how stale results are indicated.

## Open Questions to Resolve
- [ ] What is the exact backend endpoint path and auth mechanism for Availability, and what is the request/response envelope (plain JSON vs `{data: ...}`)?
- [ ] Do existing pickers/services exist for `locationId` and `storageLocationId`? If not, is free-text UUID entry acceptable or must minimal lookup UI be added?
- [ ] Should Availability support official deep-linking via URL query params? If yes, what are the canonical parameter names and routing conventions?
- [ ] Are Availability quantities considered sensitive such that frontend logs/telemetry must avoid logging numeric quantities?
- [ ] Ledger queries: what are the exact endpoints/service names, query params, and pagination response shape (items/total vs tokens)?
- [ ] Ledger location filter semantics: should location filter match `fromLocationId OR toLocationId` (recommended) or only one side depending on movement type?
- [ ] Ledger Workexec integration: what identifier is available for “movement history for a workorder line” (`sourceTransactionId` only vs `workorderId/workorderLineId`)?
- [ ] Storage location fields: are `capacity` and `temperature` structured with a defined schema/units or treated as freeform JSON?
- [ ] Can an Inactive storage location be edited (name/barcode fixes), or must it be fully read-only?
- [ ] Is `storageType` mutable after creation, or create-only?
- [ ] Should parent selection exclude Inactive locations strictly, or allow inactive parents for historical organization?
- [ ] Topology sync screens: what permissions gate LocationRef/SyncLog access and SyncLog payload visibility?
- [ ] Does backend support a manual “Sync now” trigger? If yes, what inputs/outputs and who is authorized?
- [ ] What is the authoritative definition/list of “new stock movement” screens in this repo that must block inactive locations (routes/screen IDs)?
- [ ] If `LocationRef.status=PENDING` exists, should PENDING locations be selectable for new movements or treated as inactive until ACTIVE?
- [ ] Tags rendering: are tags opaque JSON or are there known keys requiring friendly rendering/filtering?
- [ ] Fitment hints (if kept under inventory domain): what are the exact endpoints/services, permissions, tag model (free-form vs enum), year range format, hint cardinality/uniqueness constraints, and audit payload shape?
- [ ] Ingestion monitoring (manufacturer feed normalization): what are the exact endpoints/services, permissions, and which lists are read-only vs allow status updates (unmapped parts, exception queue)?
```
