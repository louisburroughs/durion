# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates implementations in the Pricing domain across UI, API contracts, security, and observability. It incorporates the resolved open questions from the Pricing domain docs into testable acceptance criteria so stories can be reviewed consistently. The UI must remain strictly consumer-only for pricing math and policy.

## Completed items

- [x] Updated acceptance criteria for each resolved open question

## Scope / Ownership

- [ ] Confirm story labeled `domain:pricing`.
- [ ] Verify primary actor(s) and permissions for the story.
- [ ] Confirm Pricing is system-of-record only for pricing entities (price books, promos, restrictions, snapshots), not Workexec lifecycle.
- [ ] Confirm cross-domain boundaries are respected for Estimate/Order screens: UI wiring may live in Workexec/Order modules, but pricing/tax math remains backend-owned and consumed read-only.
- [ ] Verify any “Proceed to Approval” / lifecycle transitions are treated as Workexec-owned state machine; UI uses backend-provided capability/editability flags and does not hardcode statuses.

## Data Model & Validation

- [ ] IDs are treated as opaque identifiers (no parsing).
- [ ] Money is displayed using backend-provided values and currency (`currencyUomId`) (DECISION-PRICING-001).
- [ ] Effective dating uses store-local timezone semantics; UI shows timezone in date editors (DECISION-PRICING-003).
- [ ] Verify estimate totals (subtotal/tax/fee/discount/grand total/rounding adjustment) are treated as backend-derived read-only fields; UI does not compute or recompute totals/taxes/discounts.
- [ ] Verify per-line tax fields (e.g., `taxAmount`, `isTaxable`, `taxCode` requirements) are displayed only when provided by backend; UI does not derive taxability.
- [ ] Verify rounding adjustment is displayed only when backend returns it and it is non-zero; UI does not invent rounding rows.
- [ ] Verify promotion code is trimmed before validation/submit and validated client-side to length 1–32 and charset `[A-Z0-9_-]` (DECISION-PRICING-007).
- [ ] Verify promotion effective window validation enforces `effectiveStartAt < effectiveEndAt` and follows backend field names/types (timestamp vs date-only) without UI-side conversions.
- [ ] Verify promotion numeric validations are enforced client-side and server-side errors are surfaced:
  - [ ] `promotionValue` is required and > 0 (non-zero).
  - [ ] Percent values accept decimals (e.g., `10.5` means 10.5%) consistent with backend representation (DECISION-PRICING-006).
  - [ ] `usageLimit` (if present) is integer >= 0; null renders as “Unlimited”.
- [ ] Verify fixed-amount promotion values do not assume currency; currency is displayed/submitted only per backend contract (DECISION-PRICING-001).
- [ ] Verify price override requested unit price input blocks negative values and non-numeric formats; UI submits decimal-string amount and does not round client-side (DECISION-PRICING-001).
- [ ] Verify price override reason code is required and must come from backend-provided catalog; UI does not hardcode reason codes.
- [ ] Verify currency for price override is read-only and derived from order/line currency; UI submits `currencyUomId` with the Money object (DECISION-PRICING-001).
- [ ] Verify estimate line item quantity supports decimals (not integer-only) and UI does not assume integer semantics.

## API Contract

- [ ] All pricing calls use `/pricing/v1/...` base path (DECISION-PRICING-002).
- [ ] Requests match documented schema; responses are handled defensively for optional fields.
- [ ] Verify standard error envelope is handled consistently where applicable: `{ code, message, correlationId, fieldErrors?, existingResourceId? }` (DECISION-INVENTORY-011).
- [ ] Verify UI maps HTTP status codes to deterministic UX:
  - [ ] 400/422 validation → field-level errors when possible + banner summary with `code` and `correlationId`.
  - [ ] 401 → login flow.
  - [ ] 403 → not-authorized UX; no sensitive details leaked.
  - [ ] 404 (admin/detail screens) → “Not found” UX with safe navigation back.
  - [ ] 409 conflict → reload prompt; no optimistic success state.
  - [ ] 5xx/timeouts → retry UX with `correlationId`.
- [ ] Verify “calculate totals” contract (estimate) returns totals + `calculationSnapshotId` and optional per-line tax breakdown; UI refreshes from response and does not recompute.
- [ ] Verify “get calculation snapshot” contract supports authorization scoping (e.g., snapshot fetch requires estimate access); UI passes required identifiers per backend contract.
- [ ] Verify promotion admin endpoints (list/get/create/activate/deactivate) are under `/pricing/v1/...` and handle 409 conflicts by reloading entity state.
- [ ] Verify duplicate promotion code errors are mapped to the `promotionCode` field (409 conflict or 400 validation per backend contract) and do not create a record.
- [ ] Verify price override apply endpoint base path is not assumed if owned by non-pricing domain; UI uses the implemented contract and still enforces Money shape and error handling.

## Events & Idempotency

- [ ] Promotion apply is idempotent (no duplicate adjustments on re-apply).
- [ ] Restriction evaluate and override handle retry safely and do not overwrite newer results.
- [ ] Verify all user-triggered mutations that support idempotency send an `Idempotency-Key` header and reuse the same key on retry (DECISION-INVENTORY-012):
  - [ ] Estimate line item create/update/delete.
  - [ ] Estimate “Proceed to Approval” transition/submit.
  - [ ] Price override submit (and optional apply step if present).
- [ ] Verify double-click / rapid retry does not produce duplicate logical outcomes (e.g., duplicate overrides, duplicate recalculation side effects, duplicate promotion application).
- [ ] Verify UI serializes estimate recalculation requests and ignores out-of-order responses so totals shown correspond to the latest successful calculation.
- [ ] Verify any event-driven refresh (if present) is safe under at-least-once delivery (no duplicate UI state transitions without backend confirmation).

## Security

- [ ] Sensitive fields (like cost) are not logged and are redacted unless explicitly authorized (DECISION-PRICING-014).
- [ ] Override operations require explicit permission and backend 403s are handled safely.
- [ ] Verify promotion admin screens enforce view vs manage permissions (list/detail vs create/activate/deactivate) and backend remains authoritative with 403.
- [ ] Verify estimate approval gating respects backend capability/permission signaling; UI disables/hides actions but does not rely solely on client-side gating.
- [ ] Verify snapshot views (pricing snapshot and calculation snapshot) are accessible only when user can access the owning document/line; unauthorized access yields 403 UX and no data is shown.
- [ ] Verify UI does not expose internal rule payloads/metadata unless explicitly required; if shown (e.g., advanced/sourceContext), render as escaped JSON and behind an “Advanced” toggle.

## Observability

- [ ] UI and backend propagate correlation/request IDs and log only safe identifiers.
- [ ] Verify error banners/modals include backend `correlationId` for support without exposing stack traces.
- [ ] Verify client logs (if present) avoid full request/response bodies for pricing/tax calculations, promotions, and overrides; log only safe identifiers (e.g., `estimateId`, `snapshotId`, `promotionOfferId`, `orderId`, `lineItemId`) and `correlationId`.
- [ ] Verify audit/history UI (when backend provides it) renders safely without assuming deep diffs; shows `changedAt`, `changedBy`, `action`, `summary`.

## Performance & Failure Modes

- [ ] Verify estimate totals recalculation does not block line item editing; totals panel shows a loading/recalculating state.
- [ ] Verify recalculation requests are serialized to avoid races; UI does not flicker totals from stale responses.
- [ ] Verify network timeouts/5xx do not leave UI in a false-success state; provide retry and keep last known-good totals visible with “Needs attention” status.
- [ ] Verify 409 conflict handling prompts reload and prevents further actions that depend on stale versions until refreshed.
- [ ] Verify reason code catalog fetch failures (restriction overrides / price overrides) disable submission and show actionable message (“No override reasons available; contact admin.”).
- [ ] Verify promotions list/detail screens show loading and empty states; pagination (if implemented) is server-driven and does not assume total counts.

## Testing

- [ ] Add/verify unit tests for client-side validations:
  - [ ] Promotion code trim + regex + length.
  - [ ] Promotion effective window `start < end`.
  - [ ] Promotion numeric constraints (`promotionValue > 0`, `usageLimit >= 0` integer).
  - [ ] Price override requested unit price non-negative numeric format.
- [ ] Add/verify integration tests (or contract tests/mocks) for API behaviors:
  - [ ] Estimate recalculation success updates totals and snapshot link; no client recomputation.
  - [ ] Estimate recalculation returns `ERR_CONFIG_JURISDICTION_MISSING` → approval disabled + banner includes `correlationId`.
  - [ ] Estimate recalculation returns `ERR_MISSING_TAX_CODE` with `fieldErrors` → banner + line highlighting.
  - [ ] Estimate recalculation 409 → reload prompt; totals not marked updated.
  - [ ] Promotion create duplicate code → field error on `promotionCode`; no navigation to detail.
  - [ ] Promotion activate/deactivate 409 → reload detail and show conflict message.
  - [ ] Price override submit 403/409/validation → correct UX mapping; no optimistic price change.
  - [ ] Idempotency: double-submit uses same `Idempotency-Key` and results in single logical outcome.
- [ ] Verify accessibility checks for banners/modals:
  - [ ] Error banners use `aria-live` and are keyboard reachable.
  - [ ] Override modal traps focus and announces validation errors.

## Documentation

- [ ] Update/verify UI documentation for where pricing/tax totals are sourced (backend) and explicitly state “no client-side pricing math.”
- [ ] Document the endpoints used (paths, required headers like `Idempotency-Key`, and error envelope expectations) for:
  - [ ] Estimate totals calculation + snapshot retrieval.
  - [ ] Promotion admin CRUD + activate/deactivate.
  - [ ] Price override reason catalog + submit endpoint.
- [ ] Document permission strings used for promotions admin, snapshot viewing, and overrides (once confirmed), including expected 403 behaviors.
- [ ] Document timezone semantics for effective dating and timestamp display (store-local interpretation vs user display timezone).

## Open Questions to Resolve

- [ ] Confirm domain ownership/labeling for estimate totals/tax recalculation UI wiring: should it remain `domain:pricing` or be split with Workexec ownership for screen changes?
- [ ] Confirm exact Moqui screen artifacts/paths for estimate editing and where totals panel + snapshot link should live (e.g., `durion-workexec/EstimateEdit.xml`).
- [ ] Provide definitive backend contracts (service names/REST endpoints + DTO schemas) for:
  - [ ] calculate totals for estimate (response fields incl. totals, per-line tax breakdown, `calculationSnapshotId`, `totalsCalculatedAt`)
  - [ ] load calculation snapshot details (authorization scoping requirements)
  - [ ] proceed-to-approval transition (blocking error codes when tax config missing/totals invalid)
  - [ ] standard error envelope field names and `fieldErrors` shape
- [ ] Confirm missing tax code policy: fail calculation with `ERR_MISSING_TAX_CODE` vs apply a default tax code; if defaulting, define how it appears in snapshot/audit and UI (warning vs silent).
- [ ] Confirm tax-inclusive vs tax-exclusive modes: which must be supported now and how backend indicates the mode for an estimate/location.
- [ ] Confirm authoritative currency source for estimates: does estimate payload include `currencyUomId`/`currencyCode`; if not, what source must UI use for formatting?
- [ ] Confirm promotions admin permission strings for view vs manage and whether UI should hide actions or show disabled with “no permission.”
- [ ] Confirm promotion scope model contract: `storeCode`, `locationId`, both, or structured `scope`; representation for “all stores/locations.”
- [ ] Confirm promotion effective dating fields and formats: timestamp (`effectiveStartAt/effectiveEndAt`) vs date-only (`startDate/endDate`) and store-local timezone interpretation.
- [ ] Confirm fixed-amount promotion currency modeling: Money object vs separate `promotionValue` + `currencyUomId`, and which currency applies (store vs tenant default).
- [ ] Confirm promotion activation/deactivation mechanism: status transition vs end-dating (`effectiveEndAt`) vs both; include response payload expectations.
- [ ] Confirm promotion code normalization: should UI uppercase on input/submit or rely on backend case-insensitive uniqueness only.
- [ ] Confirm system-of-record and endpoint ownership for line price override APIs (Pricing `/pricing/v1/...` vs Order/Checkout base path), including request/response payloads and where `canOverridePrice` capability flag is sourced.
- [ ] Confirm exact permission identifiers for price override actions (request/apply if separate) and manager approval validation (if token-based).
- [ ] Confirm authoritative reason code catalog endpoint for *price overrides* (reuse restrictions override reasons vs separate endpoint) and response shape (`code`, `description`, `activeFlag`).
- [ ] Confirm how POS obtains `managerApprovalId` (re-auth modal, scanned token, selection list) and the data shape passed to override submit.
- [ ] Confirm whether multiple overrides per line are allowed (supersede vs multiple records) and what capability flag or error code indicates “cannot create new override.”

## End

End of document.
