
# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates implementations in the Pricing domain across UI, API contracts, security, and observability. It incorporates the resolved open questions from the Pricing domain docs into testable acceptance criteria so stories can be reviewed consistently. The UI must remain strictly consumer-only for pricing math and policy.

## Completed items

- [x] Updated acceptance criteria for each resolved open question

## Scope / Ownership

- [ ] Confirm story labeled `domain:pricing`.
- [ ] Verify primary actor(s) and permissions for the story.
- [ ] Confirm Pricing is system-of-record only for pricing entities (price books, promos, restrictions, snapshots), not Workexec lifecycle.

## Data Model & Validation

- [ ] IDs are treated as opaque identifiers (no parsing).
- [ ] Money is displayed using backend-provided values and currency (`currencyUomId`) (DECISION-PRICING-001).
- [ ] Effective dating uses store-local timezone semantics; UI shows timezone in date editors (DECISION-PRICING-003).

## API Contract

- [ ] All pricing calls use `/pricing/v1/...` base path (DECISION-PRICING-002).
- [ ] Requests match documented schema; responses are handled defensively for optional fields.

## Events & Idempotency

- [ ] Promotion apply is idempotent (no duplicate adjustments on re-apply).
- [ ] Restriction evaluate and override handle retry safely and do not overwrite newer results.

## Security

- [ ] Sensitive fields (like cost) are not logged and are redacted unless explicitly authorized (DECISION-PRICING-014).
- [ ] Override operations require explicit permission and backend 403s are handled safely.

## Observability

- [ ] UI and backend propagate correlation/request IDs and log only safe identifiers.

## Acceptance Criteria (per resolved question)

### Q: What is the exact response payload for `GET /pricing/v1/snapshots/{snapshotId}` (field names, unit vs extended prices, and currency code field name)?

- Acceptance: Snapshot endpoint returns `snapshotId`, `createdAt`, `unitPrice`, and `extendedPrice` as `{ amount, currencyUomId }`; UI renders read-only without recomputation.
- Test Fixtures: `snapshotId=11111111-1111-1111-1111-111111111111`
- Example API request/response (code block)

```http
GET /pricing/v1/snapshots/11111111-1111-1111-1111-111111111111
```

```json
{
  "snapshotId": "11111111-1111-1111-1111-111111111111",
  "createdAt": "2026-01-19T12:00:00Z",
  "unitPrice": { "amount": "100.00", "currencyUomId": "USD" },
  "extendedPrice": { "amount": "200.00", "currencyUomId": "USD" }
}
```

### Q: What permission/role gates viewing a pricing snapshot in the UI (dedicated permission vs inherited from document access)?

- Acceptance: If user can view the owning estimate/work order line, snapshot view is allowed; unauthorized users receive 403 UX and no data is shown.
- Test Fixtures: User without estimate access attempts snapshot view.
- Example API request/response (code block)

```http
GET /pricing/v1/snapshots/11111111-1111-1111-1111-111111111111
```

```json
{ "errorCode": "FORBIDDEN" }
```

### Q: Should snapshot drilldown be added to both Estimate and Work Order line UIs, or only one?

- Acceptance: Any UI line showing `pricingSnapshotId` renders a “View pricing snapshot” action; both estimate and work order contexts behave identically.
- Test Fixtures: One estimate line and one work order line with `pricingSnapshotId`.
- Example API request/response (code block)

```json
{ "pricingSnapshotId": "11111111-1111-1111-1111-111111111111" }
```

### Q: Should snapshot drilldown open as a modal dialog or a dedicated route/screen (and what is the repo convention)?

- Acceptance: Snapshot drilldown opens in a modal/drawer; closing returns to the originating screen without losing form state.
- Test Fixtures: Open snapshot from an editable estimate line, then close.
- Example API request/response (code block)

```json
{ "ui": { "presentation": "modal" } }
```

### Q: Should `sourceContext` be shown to end users (collapsed/advanced) or omitted entirely?

- Acceptance: `sourceContext` is not shown by default; if present it is behind an “Advanced” toggle and rendered as escaped JSON.
- Test Fixtures: Snapshot payload includes `sourceContext`.
- Example API request/response (code block)

```json
{ "sourceContext": { "internal": "value" } }
```

### Q: What are the exact Moqui routing/screen conventions for pricing admin screens in `durion-moqui-frontend` (module path and menu extension point)?

- Acceptance: New admin screens match the nearest existing admin convention in the repo (menu placement, route naming, permissions) and are discoverable from the admin menu.
- Test Fixtures: A PriceBooks list screen appears under the admin navigation.
- Example API request/response (code block)

```json
{ "ui": { "menu": "admin", "section": "pricing" } }
```

### Q: What permissions/roles gate view vs manage for price books/rules and restriction rules (exact permission strings)?

- Acceptance: View screens are accessible with view permission; create/update/deactivate require manage permission; forced navigation without permission returns 403 UX.
- Test Fixtures: User with view but not manage permissions.
- Example API request/response (code block)

```json
{ "requiredPermissions": ["pricing:pricebook:view"], "managePermissions": ["pricing:pricebook:manage"] }
```

### Q: What are the exact backend CRUD endpoints/services for `PriceBook`, `PriceBookRule`, and `RestrictionRule` (including error payload formats for 400 vs 409)?

- Acceptance: CRUD uses `/pricing/v1/...` endpoints; 400 returns field errors; 409 returns conflict code and a recoverable message.
- Test Fixtures: Submit invalid rule and conflicting update.
- Example API request/response (code block)

```json
{ "errorCode": "VALIDATION_ERROR", "fieldErrors": { "effectiveStartAt": "required" } }
```

```json
{ "errorCode": "CONFLICT", "message": "policyVersion mismatch" }
```

### Q: Can a price book rule express both location and customer tier simultaneously, or must it be modeled via scoped price books + single-condition rules?

- Acceptance: UI does not allow multi-condition rule editing; combined scenarios are represented by scoped price books.
- Test Fixtures: Attempt to set both location and tier conditions.
- Example API request/response (code block)

```json
{ "conditionType": "LOCATION", "conditionValue": "loc-1" }
```

### Q: Does `PriceBook.scope` support a combined location+tier scope, and what is the exact representation?

- Acceptance: PriceBook includes optional `locationId` and `customerTierId` and selection logic chooses most-specific match.
- Test Fixtures: A book scoped to both `locationId` and `customerTierId`.
- Example API request/response (code block)

```json
{ "priceBookId": "pb-1", "locationId": "loc-1", "customerTierId": "tier-1", "currencyUomId": "USD" }
```

### Q: What is the exact `pricingLogic` JSON schema (types, percent representation, decimal precision/rounding rules)?

- Acceptance: `pricingLogic` supports an explicit `type` enum and validated parameters; UI only edits the JSON shape and displays backend validation errors.
- Test Fixtures: Create a discount-percent rule.
- Example API request/response (code block)

```json
{ "pricingLogic": { "type": "DISCOUNT_PERCENT", "percent": "10.5" } }
```

### Q: Are base price books single-currency or multi-currency; for fixed price rules, what currencies are allowed and is currency required?

- Acceptance: PriceBook is single-currency (`currencyUomId` required); rules do not override currency.
- Test Fixtures: Create a book without `currencyUomId` (must fail).
- Example API request/response (code block)

```json
{ "errorCode": "VALIDATION_ERROR", "fieldErrors": { "currencyUomId": "required" } }
```

### Q: What timezone semantics apply to `effectiveStartAt/effectiveEndAt` (UTC vs store local vs user timezone), and should UI use date-only or date-time pickers?

- Acceptance: UI uses date-time pickers and displays store timezone; backend stores UTC and evaluates as store-local.
- Test Fixtures: Rule starts at store midnight.
- Example API request/response (code block)

```json
{ "effectiveStartAt": "2026-01-20T00:00:00", "timezone": "America/Chicago" }
```

### Q: For deactivation, should UI set `status=INACTIVE`, set `effectiveEndAt`, or either (and which is preferred)?

- Acceptance: UI deactivates by setting `effectiveEndAt` and shows derived status in lists.
- Test Fixtures: Deactivate a rule and verify it no longer applies after end time.
- Example API request/response (code block)

```json
{ "effectiveEndAt": "2026-02-01T00:00:00" }
```

### Q: What entity/service provides audit entries for rule changes and what fields are available (diffs, actor display name, correlation id)?

- Acceptance: Audit history includes `changedAt`, `changedBy`, `action`, `summary`; UI renders safely without assuming deep diffs.
- Test Fixtures: Update rule priority.
- Example API request/response (code block)

```json
{ "changedAt": "2026-01-19T12:01:00Z", "changedBy": "user-1", "action": "UPDATE", "summary": "priority: 10 -> 20" }
```

### Q: Promotions: what are the exact endpoints/services for loading an estimate with totals/adjustments and applying a promotion code, and does apply return the full updated estimate payload?

- Acceptance: Apply returns updated totals/adjustments (either full estimate or sufficient pricing subtree); UI does not recompute totals.
- Test Fixtures: Apply `SUMMER10` to estimate.
- Example API request/response (code block)

```http
POST /pricing/v1/estimates/est-1/promotion:apply
```

```json
{ "promotionCode": "SUMMER10" }
```

### Q: Promotions: what are the canonical estimate status enum values allowed for applying promotions?

- Acceptance: UI relies on backend capability flag (e.g., `canApplyPromotions`) and blocks apply when false.
- Test Fixtures: Estimate with `canApplyPromotions=false`.
- Example API request/response (code block)

```json
{ "estimateId": "est-1", "canApplyPromotions": false }
```

### Q: Promotions: is replace/remove promotion supported; if yes, what is the service contract and audit expectation?

- Acceptance: Applying a new promotion replaces the previous one; removing is supported by a dedicated endpoint if needed, and changes are auditable.
- Test Fixtures: Replace `SUMMER10` with `WINTER5`.
- Example API request/response (code block)

```http
POST /pricing/v1/estimates/est-1/promotion:apply
```

### Q: Promotions: what is the concurrency/versioning error behavior (HTTP status/code) when estimate changes concurrently, and should UI auto-refresh or prompt?

- Acceptance: Backend returns 409 conflict with stable error code; UI prompts reload and does not show success.
- Test Fixtures: Concurrent edit.
- Example API request/response (code block)

```json
{ "errorCode": "CONFLICT", "message": "estimate version changed" }
```

### Q: Promotions: are there promotion code length/character constraints that should be validated client-side?

- Acceptance: UI validates code length 1–32 and charset `[A-Z0-9_-]` and trims whitespace before submit.
- Test Fixtures: `"  SUMMER10  "`.
- Example API request/response (code block)

```json
{ "promotionCode": "SUMMER10" }
```

### Q: Restrictions: can `BLOCK` ever be override-eligible, and how does the evaluate response indicate override eligibility?

- Acceptance: Only `ALLOW_WITH_OVERRIDE` can be overridden; UI never shows override for `BLOCK`.
- Test Fixtures: Evaluate returns `BLOCK`.
- Example API request/response (code block)

```json
{ "decision": "BLOCK" }
```

### Q: Restrictions: what is the authoritative list of `overrideReasonCode` values and how should the UI fetch it?

- Acceptance: UI fetches reason codes from catalog endpoint; if unavailable, override action is disabled.
- Test Fixtures: Catalog endpoint returns list.
- Example API request/response (code block)

```http
GET /pricing/v1/restrictions/override-reasons
```

```json
{ "reasonCodes": ["SAFETY_EXCEPTION", "CUSTOMER_OVERRIDE", "OTHER"] }
```

### Q: Restrictions: does override require a single `ruleId` when multiple rules match, or can backend accept multiple ruleIds?

- Acceptance: Override request includes a selected `ruleId` if multiple candidates exist; backend rejects ambiguous override without selection.
- Test Fixtures: Evaluate returns multiple `ruleIds`.
- Example API request/response (code block)

```json
{ "ruleIds": ["rule-1", "rule-2"], "overrideRuleSelectionRequired": true }
```

### Q: Restrictions: is `transactionId` guaranteed to exist at add-item time; if not, should override be disabled until persisted?

- Acceptance: If `transactionId` is missing, override is disabled and user is prompted to persist/save first.
- Test Fixtures: New line without transactionId.
- Example API request/response (code block)

```json
{ "transactionId": null, "overrideEnabled": false }
```

### Q: Product lookup: what is the supported product search/lookup contract for selecting `productId` in admin screens (minimal fields: SKU/name/id)?

- Acceptance: Admin uses Inventory search and displays SKU/name; UI does not accept free-form productId without validation.
- Test Fixtures: Search query `"oil"`.
- Example API request/response (code block)

```http
GET /inventory/v1/products?query=oil&page=1&pageSize=10
```

```json
{ "items": [{ "productId": "p-1", "sku": "OIL-5W30", "name": "5W-30 Oil" }] }
```

## End

End of document.
