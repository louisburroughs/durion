```markdown
# Pricing Story Validation Checklist

This checklist is intended for engineers and reviewers to validate story implementations within the **pricing** domain. It covers key aspects to ensure correctness, security, observability, and maintainability—especially for Moqui/Vue/Quasar frontend integrations.

---

## Scope/Ownership
- [ ] Confirm the story belongs to the **pricing** domain (pricing quotes, price books/rules, promotions, restrictions, overrides, snapshots) and does not implement pricing math in the UI.
- [ ] Verify cross-domain ownership boundaries are respected (e.g., Workexec owns estimates/WO lines; Inventory owns products; CRM owns promotions).
- [ ] Confirm the UI only **consumes** authoritative pricing decisions (quote results, restriction decisions, snapshot payloads) and does not infer/compute missing values (e.g., do not compute extended price unless provided).
- [ ] Verify entry points are implemented in the correct Workexec/POS screens (Estimate vs Work Order vs Invoice/Commit flows) per agreed scope.
- [ ] Confirm admin screens (price books/rules, restriction rules, store overrides) are placed under the correct Moqui module/menu and follow repo routing conventions.

---

## Data Model & Validation
- [ ] Verify all IDs are treated as opaque identifiers (UUID/string) and are not parsed for meaning (e.g., `snapshotId`, `overrideId`).
- [ ] Verify quantity validation is enforced client-side where required:
  - [ ] Price quote requests: quantity is an integer > 0.
  - [ ] Restriction evaluation: quantity is decimal > 0 (as required by API contract).
- [ ] Verify monetary inputs are validated without re-implementing pricing logic:
  - [ ] Store price override `overridePrice` is parseable, > 0, and supports up to 4 decimal places (or backend-specified precision).
  - [ ] UI does not round/normalize amounts beyond formatting for display.
- [ ] Verify effective dating validation for rule/config screens:
  - [ ] `effectiveStartAt` is required where specified.
  - [ ] If `effectiveEndAt` is provided, ensure `effectiveEndAt >= effectiveStartAt`.
  - [ ] Historical/ended rules are rendered read-only when backend indicates immutability (or when `effectiveEndAt < now` per contract).
- [ ] Verify enum-backed fields are not free-form:
  - [ ] Restriction rule `conditionType/conditionValue` selected from allowed sets.
  - [ ] Restriction evaluation `serviceTag` is from known enum set.
  - [ ] Override `overrideReasonCode` is selected from authoritative list (not arbitrary text).
- [ ] Verify UI handles optional context fields safely:
  - [ ] Missing `customerTierId` / `customerAccountId` blocks calls only if required by contract; otherwise omit field.
  - [ ] `sourceContext` JSON is treated as optional and rendered only per UX decision (e.g., Advanced toggle).
- [ ] Verify UI prevents invalid state transitions:
  - [ ] Finalize/commit is blocked when any line is `BLOCK`, `RESTRICTION_UNKNOWN`, or `ALLOW_WITH_OVERRIDE` without `overrideId`.
  - [ ] Override submission is blocked unless required fields are present (`overrideReasonCode`, non-empty `notes`, conditional `secondApprover`).

---

## API Contract
- [ ] Verify all pricing-related calls use the agreed base path/versioning (e.g., `/pricing/v1/...`) and do not hardcode environment-specific URLs.
- [ ] Verify request payloads match contract exactly (field names, nesting, types):
  - [ ] `POST /pricing/v1/restrictions:evaluate` includes required context (`tenantId`, `locationId`, `serviceTag`, `items[]`) and does not send null/empty `items`.
  - [ ] `POST /pricing/v1/restrictions:override` includes required identifiers (`transactionId`, `productId`, and `ruleId`/`policyVersion` if required by backend).
  - [ ] Price quote request includes required context (`productId`, `quantity`, `locationId`, `customerTierId`) and optional `effectiveTimestamp` only when supported.
  - [ ] Promotion apply request sends trimmed `promotionCode` and correct estimate identifier.
- [ ] Verify response parsing is resilient to optional/missing fields:
  - [ ] Snapshot view renders required minimum (`snapshotId`, `createdAt`, final price field) and shows “Not provided” for optional fields.
  - [ ] Quote panel renders breakdown entries in the order returned and does not assume non-empty arrays.
  - [ ] Restriction evaluation response maps per-item decisions and captures `ruleIds[]`, `reasonCodes[]`, optional `policyVersion/confidence`.
- [ ] Verify error handling is deterministic and based on HTTP status + stable error codes:
  - [ ] 400: show validation/business error; map field errors when provided.
  - [ ] 401: follow app auth flow (re-auth).
  - [ ] 403: show unauthorized message; do not leak sensitive details.
  - [ ] 404: show not-found/no-price messaging without guessing root cause unless backend provides code.
  - [ ] 409: handle conflicts (optimistic locking, policyVersion mismatch) with reload/retry UX.
  - [ ] 5xx/timeout: show “service unavailable” and provide retry where safe.
- [ ] Verify commit-path behavior fails closed where required:
  - [ ] If restriction evaluation is unavailable during finalize/commit, finalize is blocked with the agreed message.
- [ ] Verify UI does not rely on undocumented fields (denylist: computing extended price, inferring currency, inferring eligibility).

---

## Events & Idempotency
- [ ] Verify UI behavior is idempotent for user actions that may be retried:
  - [ ] Applying the same promotion code twice does not create duplicate adjustment lines in the UI (and handles backend idempotency semantics).
  - [ ] Retrying restriction evaluation updates line state based on latest authoritative response (no stale merge).
  - [ ] Retrying snapshot load does not display stale data after an error (clear state on error).
- [ ] Verify UI handles out-of-order responses for rapid input changes:
  - [ ] Price quote requests are debounced and/or sequenced; only the latest response updates the UI.
  - [ ] Restriction evaluation requests for edits do not overwrite newer decisions with older responses.
- [ ] Verify conflict/policy version handling:
  - [ ] If override fails due to policyVersion mismatch, UI forces re-evaluation before allowing another override attempt.

---

## Security
- [ ] Verify permission gating is enforced in UI **and** handled defensively on API responses:
  - [ ] Restriction override action requires `pricing:restriction:override` (or final agreed permission).
  - [ ] Store price override screens/actions require `pricing:override:manage`.
  - [ ] Price book/rule admin screens require manage permission (exact string TBD).
  - [ ] Snapshot view requires view permission (exact string TBD) or document-derived access per policy.
- [ ] Verify location scoping is enforced for store override flows:
  - [ ] Location selector is constrained to authorized locations.
  - [ ] Direct navigation to unauthorized location shows 403 UX and disables submit.
- [ ] Verify sensitive data handling:
  - [ ] Do not log override `notes` content.
  - [ ] Do not log cost values (if returned) unless explicitly approved and redacted.
  - [ ] Do not expose internal rule metadata beyond allowed `reasonCodes`/`ruleIds`.
- [ ] Verify input validation prevents injection and unsafe rendering:
  - [ ] User-entered strings (promotion code, override notes) are escaped in UI and not rendered as HTML.
  - [ ] `sourceContext` JSON is rendered safely (no HTML injection).
- [ ] Verify “fail closed” behavior for restricted operations:
  - [ ] Finalize/commit is blocked when restriction state is unknown or blocked.
  - [ ] Admin CRUD actions are disabled/hidden without permission and still handle 403 safely.

---

## Observability
- [ ] Verify correlation/request IDs are propagated on pricing API calls per project convention (headers) and captured for troubleshooting.
- [ ] Verify structured client-side logging/telemetry exists for key actions (without PII):
  - [ ] `restriction.evaluate.*` with transactionId, productId(s), decision, status code.
  - [ ] `restriction.override.*` with transactionId, productId, overrideId (if approved), status code.
  - [ ] `priceQuote.requested/succeeded/failed` with productId, quantity, locationId, customerTierId, requestId/hash.
  - [ ] `snapshot.load.*` with snapshotId and calling document context (estimateId/workOrderId/lineId if available).
  - [ ] `promotion.apply.*` with estimateId and errorCode/status.
- [ ] Verify UI error states include enough non-sensitive context for support (e.g., show error code, optionally correlationId if policy allows).
- [ ] Verify audit/history panels render backend-provided audit entries without assuming fields:
  - [ ] Price book/rule audit tab shows timestamp, actor, entity id, change summary if provided.
  - [ ] Store override detail shows status history and approval/rejection metadata if provided.

---

## Performance & Failure Modes
- [ ] Verify restriction evaluation does not block the UI thread:
  - [ ] Per-line loading indicators are shown during evaluation.
  - [ ] Evaluation timeouts are handled (e.g., treat >800ms as unavailable per story guidance).
- [ ] Verify commit-path restriction evaluation fails fast and blocks finalize on timeout/503.
- [ ] Verify quote calls are debounced (e.g., 250–400ms) and do not flood the backend during typing.
- [ ] Verify list screens use pagination/server-side filtering for large datasets (price books/rules, restriction rules, overrides).
- [ ] Verify graceful degradation:
  - [ ] If restriction evaluation fails in non-commit flows, line is marked `RESTRICTION_UNKNOWN` and finalize is blocked until resolved.
  - [ ] If snapshot load fails, no stale snapshot data remains visible.
  - [ ] If pricing context load fails for store overrides, submit is disabled (fail closed).
- [ ] Verify retry behavior is safe and user-controlled:
  - [ ] Provide “Retry” for snapshot load and restriction evaluation failures.
  - [ ] Do not auto-retry on 403/404.

---

## Testing
- [ ] Unit tests cover client-side validation rules:
  - [ ] Quantity validation (integer vs decimal rules per endpoint).
  - [ ] Promotion code trimming and empty handling.
  - [ ] Override modal required fields and conditional second approver.
  - [ ] Effective date validation and immutability read-only behavior.
- [ ] Component tests cover UI state machines:
  - [ ] Snapshot view states: loading/loaded/notFound/forbidden/unavailable.
  - [ ] Quote panel states: idle/loading/success/error and stale/out-of-order response protection.
  - [ ] Restriction line item states: ALLOW/BLOCK/ALLOW_WITH_OVERRIDE/RESTRICTION_UNKNOWN and finalize gating.
- [ ] Integration tests (mocked HTTP) verify API contract adherence:
  - [ ] Correct request payload shapes and headers.
  - [ ] Correct mapping of 400/403/404/409/5xx to UI messages and state.
- [ ] E2E tests validate critical workflows:
  - [ ] Add/edit line triggers restriction evaluation and blocks finalize appropriately.
  - [ ] Override flow stores `overrideId` and unblocks finalize.
  - [ ] Apply promotion updates totals and is idempotent on re-apply.
  - [ ] Store override submit results in ACTIVE vs PENDING_APPROVAL and renders approval panel.
  - [ ] Snapshot drilldown opens from line item and handles 403/404/5xx.
- [ ] Security tests validate permission gating:
  - [ ] Actions hidden/disabled without permission and backend 403 handled safely if forced.
- [ ] Accessibility tests validate:
  - [ ] Modal focus trap, labeled fields, ARIA-live for async errors, keyboard navigation for drilldowns.

---

## Documentation
- [ ] Document the UI-to-backend contracts used (endpoints/services, request/response schemas, error codes).
- [ ] Document required permissions for each screen/action (view vs manage vs override).
- [ ] Document commit-path vs non-commit-path restriction behavior (fail open for add/edit with unknown; fail closed for finalize).
- [ ] Document how currency is displayed and what happens when currency code is missing (do not guess).
- [ ] Document concurrency/conflict handling patterns (409 reload, policyVersion mismatch → re-evaluate).
- [ ] Document where new Moqui screens are registered (menu entries, routes) and any repo conventions followed.
- [ ] Record all open questions and final decisions with links to issues/PRs.

---

## Open Questions to Resolve
- [ ] What is the exact response payload for `GET /pricing/v1/snapshots/{snapshotId}` (field names, unit vs extended prices, and currency code field name)?
- [ ] What permission/role gates viewing a pricing snapshot in the UI (dedicated permission vs inherited from document access)?
- [ ] Should snapshot drilldown be added to both Estimate and Work Order line UIs, or only one (and which is priority)?
- [ ] Should snapshot drilldown open as a modal dialog or a dedicated route/screen (and what is the repo convention)?
- [ ] Should `sourceContext` be shown to end users (collapsed/advanced) or omitted entirely?
- [ ] What are the exact Moqui routing/screen conventions for pricing admin screens in `durion-moqui-frontend` (module path and menu extension point)?
- [ ] What permissions/roles gate *view* vs *manage* for price books/rules and restriction rules (exact permission strings)?
- [ ] What are the exact backend CRUD endpoints/services for `PriceBook`, `PriceBookRule`, and `RestrictionRule` (including error payload formats for 400 vs 409)?
- [ ] Can a price book rule express both location and customer tier simultaneously, or must it be modeled via scoped price books + single-condition rules?
- [ ] Does `PriceBook.scope` support a combined location+tier scope, and what is the exact representation?
- [ ] What is the exact `pricingLogic` JSON schema (types, percent representation, decimal precision/rounding rules)?
- [ ] Are base price books single-currency or multi-currency; for fixed price rules, what currencies are allowed and is currency required?
- [ ] What timezone semantics apply to `effectiveStartAt/effectiveEndAt` (UTC vs store local vs user timezone), and should UI use date-only or date-time pickers?
- [ ] For deactivation, should UI set `status=INACTIVE`, set `effectiveEndAt`, or either (and which is preferred)?
- [ ] What entity/service provides audit entries for rule changes and what fields are available (diffs, actor display name, correlation id)?
- [ ] Promotions: what are the exact endpoints/services for loading an estimate with totals/adjustments and applying a promotion code, and does apply return the full updated estimate payload?
- [ ] Promotions: what are the canonical estimate status enum values allowed for applying promotions?
- [ ] Promotions: is replace/remove promotion supported; if yes, what is the service contract and audit expectation?
- [ ] Promotions: what is the concurrency/versioning error behavior (HTTP status/code) when estimate changes concurrently, and should UI auto-refresh or prompt?
- [ ] Promotions: are there promotion code length/character constraints that should be validated client-side?
- [ ] Restrictions: can `BLOCK` ever be override-eligible, and how does the evaluate response indicate override eligibility?
- [ ] Restrictions: what is the authoritative list of `overrideReasonCode` values and how should the UI fetch it?
- [ ] Restrictions: does override require a single `ruleId` when multiple rules match, or can backend accept multiple ruleIds?
- [ ] Restrictions: is `transactionId` guaranteed to exist at add-item time; if not, should override be disabled until persisted?
- [ ] Product lookup: what is the supported product search/lookup contract for selecting `productId` in admin screens (minimal fields: SKU/name/id)?
```
