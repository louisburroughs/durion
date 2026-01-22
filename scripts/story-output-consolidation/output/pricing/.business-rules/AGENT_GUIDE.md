# AGENT_GUIDE.md

## Summary

This document is the normative guide for the Pricing domain. It defines system-of-record boundaries, core invariants, and the decision set that the UI and services must follow to keep pricing deterministic, auditable, and safe. All previously marked TBD/CLARIFY items from the source Pricing docs have been resolved into explicit decisions and acceptance criteria.

This guide also incorporates newer frontend stories that expand Pricing’s responsibilities around **estimate totals/tax calculation snapshots**, **promotion admin**, and **line price overrides**. Where backend contracts are not yet finalized, this guide flags items with **TODO/CLARIFY** and adds concrete integration/testing guidance so implementations remain safe-by-default.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `DOMAIN_NOTES.md`
- [x] Reconciled todos from original AGENT_GUIDE
- [ ] TODO: Incorporate finalized backend contracts for estimate totals/tax calculation and price override APIs once published (see Open Questions)

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-PRICING-001 | Money representation and rounding |
| DECISION-PRICING-002 | Pricing API base path and versioning |
| DECISION-PRICING-003 | Effective dating and timezone semantics |
| DECISION-PRICING-004 | Price book scope model (location/tier/currency) |
| DECISION-PRICING-005 | Price book rule condition model (single-dimension) |
| DECISION-PRICING-006 | Rule precedence and deterministic tie-breakers |
| DECISION-PRICING-007 | Promotion code constraints and uniqueness |
| DECISION-PRICING-008 | Single promotion per estimate behavior |
| DECISION-PRICING-009 | Promotion eligibility rule evaluation model |
| DECISION-PRICING-010 | Restriction decisions and override eligibility |
| DECISION-PRICING-011 | Override reason codes catalog source |
| DECISION-PRICING-012 | Restriction override request shape and transactionId requirement |
| DECISION-PRICING-013 | Pricing snapshot contract and UX pattern |
| DECISION-PRICING-014 | Snapshot authorization and sensitive-field redaction |
| DECISION-PRICING-015 | Audit/history contract for pricing admin changes |
| DECISION-PRICING-016 | Deactivation mechanism (status vs effectiveEndAt) |
| DECISION-PRICING-017 | Product lookup contract for pricing admin screens |
| DECISION-PRICING-018 | MSRP historical immutability and permissions |

## Domain Boundaries

### What Pricing owns (system of record)

- MSRP (effective-dated) for products.
- Price books and price book rules (effective-dated), including deterministic evaluation behavior.
- Location price overrides and guardrail outcomes (including approval states).
- Promotions and promotion eligibility rules (including code uniqueness).
- Restriction rules and restriction override records.
- Pricing snapshots (immutable, write-once) referenced by Workexec lines.
- **Calculation snapshots for estimate totals/taxes/fees/discounts** when produced by Pricing/Tax calculation services.  
  - CLARIFY: whether these are the same artifact as `PricingSnapshot` or a separate `CalculationSnapshot` type. UI must treat both as immutable, read-only snapshots.

### What Pricing does not own

- Product identity, SKU/name, UOM catalog (Inventory/Product domain).
- Customer identity, tiers, and account lifecycle (CRM).
- Estimate/work order lifecycle and line persistence (Workexec).
- Order/checkout lifecycle and persistence (Order/Checkout domain).
- Tax jurisdiction configuration authoring UI and lifecycle (likely Tax/Finance/Admin domain). Pricing/Tax calculation may consume it but does not own authoring in the frontend stories.

### Boundary clarifications (new from consolidated stories)

- **Estimate totals/tax calculation**
  - Pricing/Tax services are authoritative for totals/taxes/fees/discounts and must return backend-derived totals and a snapshot reference.
  - Workexec remains SoR for the Estimate and its line items and state transitions; Pricing/Tax is invoked as a calculation service.
  - CLARIFY: whether the “Proceed to Approval” transition is enforced solely by Workexec (preferred) with Workexec calling Pricing/Tax, or whether the UI must call Pricing/Tax directly before attempting the transition. The story expects UI-triggered recalculation plus transition failure if config missing.

- **Line price overrides**
  - Pricing owns override policy, reason catalogs, and evaluation outcomes.
  - CLARIFY: the SoR for the override record and the API base path for creating a line price override request (Pricing vs Order/Checkout). The UI must not guess; it must follow the authoritative backend contract.

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| ProductMSRP | Effective-dated MSRP for a product. |
| PriceBook | Container for rule sets, scoped by location/tier/currency (see decisions). |
| PriceBookRule | Effective-dated pricing rule evaluated deterministically. |
| LocationPriceOverride | Store-specific override price with guardrail outcome. |
| PromotionOffer | Promotion with code, validity window, and discount definition. |
| PromotionEligibilityRule | Constraint(s) determining promotion applicability. |
| PriceQuote | Authoritative quote result for a line: unit/extended price, breakdown, warnings. |
| PricingSnapshot | Immutable record of pricing outcome at time-of-creation for audit drilldown. |
| RestrictionRule | Rule restricting sell/install by location/service context. |
| OverrideRecord | Immutable record of a restriction override, referenced by downstream systems. |
| **EstimateTotals** | Backend-derived totals for an estimate: subtotal, taxTotal, feeTotal, discountTotal, roundingAdjustment, grandTotal. |
| **CalculationSnapshot** | Immutable snapshot of an estimate totals/tax calculation, referenced by estimate header (e.g., `calculationSnapshotId`). CLARIFY relationship to `PricingSnapshot`. |
| **LinePriceOverrideRequest / PriceOverride** | A request/record representing a requested unit price override for an order/line, including reason and approval metadata. SoR and API ownership CLARIFY. |

### Relationships (actionable mental model)

- `PriceBook (scope: locationId?, customerTierId?, currencyUomId)` → contains many `PriceBookRule (effective-dated)` → evaluated to produce `PriceQuote`.
- `PriceQuote` → persisted as `PricingSnapshot` (immutable) → referenced by Workexec line items (`pricingSnapshotId`).
- `Estimate (Workexec)` → invokes Pricing/Tax calculation → returns `EstimateTotals` + `calculationSnapshotId` → snapshot view is read-only and auditable.
- `PromotionOffer` → may be applied to an estimate (single active promotion per estimate; DECISION-PRICING-008) and must be traceable in breakdown/snapshots.
- `RestrictionRule` → evaluated during quote/calc/commit flows → may produce `ALLOW_WITH_OVERRIDE` → can create `OverrideRecord` (immutable) when overridden.

## Invariants / Business Rules

- UI must not recompute money or infer pricing; it must display backend-authoritative results.
- Effective dating uses half-open intervals for timestamp ranges (see DECISION-PRICING-003).
- Pricing calculations must be deterministic: same inputs must produce the same outputs.
- Snapshots are write-once and must not reference mutable rule content for explainability.
- Restrictions fail closed on commit/finalize flows; non-commit flows may degrade but must block finalize.

### Additional invariants from consolidated stories (normative for UI behavior)

- **Estimate totals/taxes/fees/discounts are backend-derived**:
  - UI must not compute subtotal/tax/fees/discounts/grand total client-side.
  - UI must treat per-line tax amounts/taxability as backend-derived and optional (display only if provided).
- **Workflow gating is driven by backend error codes/capability flags**:
  - “Proceed to Approval” must be disabled when totals are recalculating or when the latest calculation returned a blocking configuration error (e.g., missing jurisdiction or missing tax code).
  - UI must not hardcode estimate/order status enums for gating; prefer backend capability flags (pattern already used in DECISION-PRICING-008).
- **Idempotency for user-triggered mutations**:
  - For create/update/delete/submit actions that can be double-clicked or retried, clients must use idempotency keys/headers per platform convention.  
  - CLARIFY: idempotency header name and semantics are referenced as `DECISION-INVENTORY-012` in stories; Pricing UI must follow the platform standard and not invent new headers.

## API Contract (Normative defaults)

These are the normative API contract expectations until backend contracts diverge (in which case backend contract wins and this guide must be updated).

- Base path: `/pricing/v1/...` (DECISION-PRICING-002).
- Money shape: `{ "amount": "<decimal-string>", "currencyUomId": "USD" }` (DECISION-PRICING-001).

### Additional API patterns (from consolidated stories; apply unless backend specifies otherwise)

- **Error envelope**: UI expects a stable error envelope with `code`, `message`, `correlationId`, and optional `fieldErrors[]`.  
  - CLARIFY: This is referenced as `DECISION-INVENTORY-011` in stories; Pricing endpoints should align to the platform-wide error envelope to avoid per-domain error parsing.
- **Conflict handling**: 409 indicates optimistic concurrency conflict; UI should prompt reload and retry after refresh.
- **Out-of-order response safety**: for recalculation/quote-like calls, UI must serialize requests or ignore stale responses (requestId pattern).  
  - TODO: confirm whether Pricing/Tax “calculate totals” supports `requestId` echoing; if not, UI must serialize calls strictly.

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-PRICING-001 | Money uses decimal-string + `currencyUomId`; rounding is backend-owned | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-001--money-representation-and-rounding) |
| DECISION-PRICING-002 | All pricing endpoints live under `/pricing/v1` | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-002--pricing-api-base-path-and-versioning) |
| DECISION-PRICING-003 | Effective-dated timestamps are interpreted in store-local timezone | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-003--effective-dating-and-timezone-semantics) |
| DECISION-PRICING-004 | Price books are explicitly scoped and single-currency | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-004--price-book-scope-model-locationtiercurrency) |
| DECISION-PRICING-005 | Rules support one condition dimension; combine via scoped books | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-005--price-book-rule-condition-model-single-dimension) |
| DECISION-PRICING-006 | Deterministic ordering: specificity → priority → stable tie-breaker | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-006--rule-precedence-and-deterministic-tie-breakers) |
| DECISION-PRICING-007 | Promo codes are unique, case-insensitive lookup, constrained charset | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-007--promotion-code-constraints-and-uniqueness) |
| DECISION-PRICING-008 | One promotion active per estimate; apply is replace-by-default | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-008--single-promotion-per-estimate-behavior) |
| DECISION-PRICING-009 | Eligibility rules default to AND; no rules means eligible | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-009--promotion-eligibility-rule-evaluation-model) |
| DECISION-PRICING-010 | Override is only for `ALLOW_WITH_OVERRIDE` decisions | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-010--restriction-decisions-and-override-eligibility) |
| DECISION-PRICING-011 | Override reason codes are fetched from backend catalog endpoint | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-011--override-reason-codes-catalog-source) |
| DECISION-PRICING-012 | Override requires `transactionId` and `policyVersion`; optional rule selection | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-012--restriction-override-request-shape-and-transactionid-requirement) |
| DECISION-PRICING-013 | Snapshot drilldown opens as read-only modal/drawer | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-013--pricing-snapshot-contract-and-ux-pattern) |
| DECISION-PRICING-014 | Snapshot access is inherited; sensitive fields are redacted | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-014--snapshot-authorization-and-sensitive-field-redaction) |
| DECISION-PRICING-015 | Admin entities expose audit/history via a standard contract | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-015--audithistory-contract-for-pricing-admin-changes) |
| DECISION-PRICING-016 | Prefer `effectiveEndAt` for deactivation; status is derived | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-016--deactivation-mechanism-status-vs-effectiveendat) |
| DECISION-PRICING-017 | Admin uses Inventory product search API for product selection | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-017--product-lookup-contract-for-pricing-admin-screens) |
| DECISION-PRICING-018 | Past MSRP records are immutable unless special permission present | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-pricing-018--msrp-historical-immutability-and-permissions) |

## Open Questions (from source)

### Q: What is the exact response payload for `GET /pricing/v1/snapshots/{snapshotId}` (field names, unit vs extended prices, and currency code field name)?

- Answer: The snapshot response must include `snapshotId`, `createdAt`, and both `unitPrice` and `extendedPrice` as money objects (`amount` + `currencyUomId`). Optional fields may exist but must not be required by the UI.
- Assumptions:

  - Backend already stores unit and extended values at snapshot time.
  - Currency is represented as `currencyUomId` (Moqui convention).
- Rationale:

  - Snapshot drilldowns must be explainable without recomputation.
  - Using a single canonical money shape avoids UI branching.
- Impact:

  - DTOs for snapshot view in UI; contract tests for snapshot endpoint.
- Decision ID: DECISION-PRICING-001

### Q: What permission/role gates viewing a pricing snapshot in the UI (dedicated permission vs inherited from document access)?

- Answer: Snapshot view access is inherited from the owning estimate/work order line access; sensitive fields (like cost) are separately gated and may be redacted.
- Assumptions:

  - Workexec already enforces document access controls.
- Rationale:

  - Prevents “orphan permissions” and reduces authorization drift.
- Impact:

  - UI treats 403 as “access denied”; cost fields require explicit permission.
- Decision ID: DECISION-PRICING-014

### Q: Should snapshot drilldown be added to both Estimate and Work Order line UIs, or only one?

- Answer: Add snapshot drilldown anywhere a line exposes `pricingSnapshotId` (both estimate and work order when present).
- Assumptions:

  - Both documents can reference snapshots.
- Rationale:

  - Consistent behavior across the platform reduces support friction.
- Impact:

  - Frontend workexec screens; test coverage in both contexts.
- Decision ID: DECISION-PRICING-013

### Q: Should snapshot drilldown open as a modal dialog or a dedicated route/screen?

- Answer: Use a modal/drawer drilldown to preserve the editing context; only add a route if deep-linking becomes required.
- Assumptions:

  - The UI framework supports modal/drawer patterns consistently.
- Rationale:

  - Prevents navigation loss and accidental state resets during editing.
- Impact:

  - UI component design; accessibility focus-trap requirements.
- Decision ID: DECISION-PRICING-013

### Q: Should `sourceContext` be shown to end users (collapsed/advanced) or omitted entirely?

- Answer: `sourceContext` is not rendered by default; if exposed, it must be behind an “Advanced” toggle and only for authorized roles.
- Assumptions:

  - `sourceContext` may contain internal rule metadata.
- Rationale:

  - Avoids leaking sensitive or confusing internals.
- Impact:

  - UI toggle; authorization checks; safe JSON rendering.
- Decision ID: DECISION-PRICING-014

### Q: What are the exact Moqui routing/screen conventions for pricing admin screens in `durion-moqui-frontend`?

- Answer: Pricing admin screens must live under the Pricing domain’s own UI module area and be discoverable from the POS admin menu; specific screen paths are frontend-repo-convention-owned and must follow the nearest existing admin screen pattern.
- Assumptions:

  - The frontend repo has established admin menu extension points.
- Rationale:

  - Avoids introducing non-standard navigation.
- Impact:

  - UI story implementation should mirror an existing admin section.
- Decision ID: DECISION-PRICING-015

### Q: What permissions/roles gate view vs manage for price books/rules and restriction rules (exact permission strings)?

- Answer: Use a view/manage split where possible: `pricing:pricebook:view` and `pricing:pricebook:manage`, `pricing:restriction:view` and `pricing:restriction:manage`; if the backend only provides a single permission, the UI must degrade to manage-only gating.
- Assumptions:

  - Backend permission vocabulary can be extended without breaking callers.
- Rationale:

  - Least privilege and reduced blast radius.
- Impact:

  - UI capability gating and defensive 403 handling.
- Decision ID: DECISION-PRICING-015

### Q: Can a price book rule express both location and customer tier simultaneously?

- Answer: A single rule supports at most one condition dimension (location OR tier); expressing both is done by using a scoped price book (e.g., location+ tier scope) with unconditioned rules.
- Assumptions:

  - Scope can be represented on PriceBook.
- Rationale:

  - Keeps evaluation deterministic and UI simpler.
- Impact:

  - Admin UI should not show multi-condition editors.
- Decision ID: DECISION-PRICING-005

### Q: What is the exact `pricingLogic` JSON schema (types, percent representation, decimal precision/rounding rules)?

- Answer: The schema is constrained to an explicit set of rule types (e.g., `FIXED_UNIT_PRICE`, `DISCOUNT_PERCENT`) and must treat percentages as decimal percentages (e.g., 10.5 means 10.5%). Rounding remains backend-owned; UI only validates schema shape.
- Assumptions:

  - Backend persists and evaluates logic; UI is an editor.
- Rationale:

  - Avoids UI re-implementations of pricing math.
- Impact:

  - JSON editor widgets, backend validation messages in UI.
- Decision ID: DECISION-PRICING-006

### Q: What timezone semantics apply to `effectiveStartAt/effectiveEndAt` and should UI use date-only or date-time pickers?

- Answer: UI uses date-time pickers for timestamp-effective entities and interprets “effective” in store-local timezone; backend stores timestamps in UTC but compares in store-local.
- Assumptions:

  - Each estimate/location has a resolvable store timezone.
- Rationale:

  - Prevents off-by-one-day errors across distributed users.
- Impact:

  - Timezone display in UI, backend conversion rules.
- Decision ID: DECISION-PRICING-003

### Q: For deactivation, should UI set `status=INACTIVE`, set `effectiveEndAt`, or either?

- Answer: Prefer setting `effectiveEndAt` (soft end-date) and treat `status` as derived; if a `status` field exists it can be used as a convenience view but must not be the primary source of truth.
- Assumptions:

  - Entities are effective-dated.
- Rationale:

  - End-dating is auditable and time-travel friendly.
- Impact:

  - Admin UI “Deactivate” action maps to end-dating.
- Decision ID: DECISION-PRICING-016

### Q: Promotions: what are the canonical estimate status enum values allowed for applying promotions?

- Answer: The UI must not hardcode statuses; it should rely on a backend-provided capability flag (e.g., `canApplyPromotions`) on the estimate payload. If unavailable, default to allowing only draft/editable estimates.
- Assumptions:

  - Backend can expose capability flags as a stable contract.
- Rationale:

  - Avoids coupling UI to internal state machine strings.
- Impact:

  - Estimate DTO enhancement; UI gating logic.
- Decision ID: DECISION-PRICING-008

### Q: Restrictions: can `BLOCK` ever be override-eligible, and how does the evaluate response indicate override eligibility?

- Answer: Override eligibility is represented only by `ALLOW_WITH_OVERRIDE`; `BLOCK` is not override-eligible.
- Assumptions:

  - Restriction rules encode “override allowed” as a different decision category.
- Rationale:

  - Prevents bypassing true safety/compliance restrictions.
- Impact:

  - UI override buttons appear only for `ALLOW_WITH_OVERRIDE`.
- Decision ID: DECISION-PRICING-010

### Q: Restrictions: what is the authoritative list of `overrideReasonCode` values and how should the UI fetch it?

- Answer: The UI must fetch reason codes from a dedicated backend catalog endpoint and never hardcode them.
- Assumptions:

  - Backend exposes `GET /pricing/v1/restrictions/override-reasons`.
- Rationale:

  - Reason codes are governance-controlled and may change.
- Impact:

  - UI dropdown loads asynchronously and caches results.
- Decision ID: DECISION-PRICING-011

### Q: Restrictions: is `transactionId` guaranteed to exist at add-item time; if not, should override be disabled until persisted?

- Answer: Overrides require a persisted `transactionId`; if it is not available, the UI must disable override and instruct the user to save/persist first.
- Assumptions:

  - Override records must attach to an auditable transaction.
- Rationale:

  - Prevents orphan overrides without traceability.
- Impact:

  - UI state gating; backend validations.
- Decision ID: DECISION-PRICING-012

### Q: MSRP Historical Immutability: Are MSRP records with `effectiveEndDate` in the past editable, and what permission is required?

- Answer: Past-effective MSRP records are immutable by default; editing requires a dedicated elevated permission and must create an audit event.
- Assumptions:

  - MSRP changes impact customer pricing disputes.
- Rationale:

  - Prevents rewriting history and preserves auditability.
- Impact:

  - UI renders historical rows read-only unless permission present.
- Decision ID: DECISION-PRICING-018

### Q: Product lookup: what is the supported product search/lookup contract for selecting `productId` in admin screens?

- Answer: Pricing admin screens must use Inventory’s product search API to select products, returning at minimum `productId`, `sku`, and `name`.
- Assumptions:

  - Inventory is the system of record for product identity.
- Rationale:

  - Prevents duplicated product search logic in Pricing.
- Impact:

  - Shared product-picker component or adapter.
- Decision ID: DECISION-PRICING-017

## Open Questions from Frontend Stories

This section consolidates open questions from the latest frontend stories that impact Pricing domain contracts, boundaries, and UI behavior. These are **blocking** where noted; implementers must not guess.

### Estimate totals + taxes + calculation snapshot (Issue #236)

1. **Domain ownership/label correction (blocking):** Confirm whether this work is owned by `domain:pricing` (Pricing/Tax calculation + snapshot) vs `domain:workexec` (Estimate screen wiring). If split is required, define the seam: Workexec screen triggers + Pricing/Tax contract.
2. **Exact Moqui artifacts (blocking):** Confirm actual screen names/paths for estimate editing and totals panel placement (expected: `durion-workexec/EstimateEdit.xml`, but must be verified).
3. **Backend contract (blocking):** Provide exact service names/REST endpoints and DTO schemas for:
   - calculate totals for estimate (request/response, including per-line tax breakdown fields)
   - load calculation snapshot details
   - proceed-to-approval transition  
   Include definitive error `code` list and `fieldErrors` shape.
4. **Missing tax code policy (blocking):** Is missing tax code a hard failure (`ERR_MISSING_TAX_CODE`) or does backend apply a default? If defaulting is allowed:
   - how is it represented in snapshot/audit (explicit field vs applied rule)?
   - is it a warning vs silent behavior?
5. **Tax-inclusive vs tax-exclusive (blocking):** Which modes must frontend support now, and how does backend indicate the mode for an estimate/location (flag on estimate, location config, or calculation response)?
6. **Currency source (blocking if multi-currency):** Does estimate payload include `currencyUomId` (preferred) vs `currencyCode`? If absent, what is the authoritative source for currency formatting?

### Promotions admin: create/activate/deactivate PromotionOffer (Issue #161)

1. **Permissions (blocking):** Confirm exact permission strings for:
   - view promotions list/detail (e.g., `pricing:promotion:view`)
   - manage promotions create/activate/deactivate (e.g., `pricing:promotion:manage`)  
   Also confirm whether UI should hide actions vs show disabled with “no permission”.
2. **Scope model (blocking):** Backend contract for promotion scope:
   - `storeCode`, `locationId`, both, or structured `scope` object?
   - Is “all stores/locations” supported, and how represented (null vs explicit flag)?
3. **Effective dating fields (blocking):** Are promotions timestamp-effective (`effectiveStartAt/effectiveEndAt`, store-local interpretation per DECISION-PRICING-003) or date-only (`startDate/endDate`)? Provide exact field names and formats.
4. **Fixed amount currency (blocking):** For `FIXED_INVOICE`, does backend require:
   - Money object `{ amount, currencyUomId }` (DECISION-PRICING-001), or
   - separate `promotionValue` + `currencyUomId` fields?  
   Which currency applies (store currency vs tenant default)?
5. **Activation/deactivation mechanism (blocking):** For promotions, is deactivation:
   - status transition (`ACTIVE` → `INACTIVE`), or
   - setting `effectiveEndAt` (DECISION-PRICING-016 preference), or
   - both?  
   Provide authoritative behavior and response payload.
6. **Code normalization (blocking):** Should UI uppercase codes on input/submit, or only validate charset and rely on backend case-insensitive uniqueness (DECISION-PRICING-007)? Provide expected backend behavior for mixed-case input.

### Order line price override (Issue #84)

1. **System-of-record + endpoint ownership (blocking):** Is the line price override API owned by Pricing (`/pricing/v1/...`) or by Order/Checkout (non-pricing base path)? Provide exact REST paths or Moqui service names and request/response payloads for:
   - create override request
   - (optional) apply approved override
   - load per-line `canOverridePrice` capability flag and override summary
2. **Permission strings (blocking):** Exact permission identifiers for:
   - requesting a price override (advisor)
   - applying an override (if separate)
   - manager approval validation (if token-based)
3. **Reason code catalog for price overrides (blocking):** What endpoint provides authoritative reason codes for **price overrides**?
   - If reusing `GET /pricing/v1/restrictions/override-reasons`, confirm semantics and field names.
   - If separate, provide correct endpoint and response shape.
4. **Manager approval token acquisition (blocking):** How does POS obtain `managerApprovalId` (re-auth modal, scanned token, selection from pending approvals)? Provide UX entry point and data shape.
5. **Multiple overrides per line (blocking):** If a line already has an override in `PENDING_APPROVAL` or `APPLIED`, can a new override request be created?
   - If yes: does it supersede prior override or keep multiple with an “active” pointer?
   - What capability flag or error code indicates “cannot create new override”?

## Todos Reconciled

- Original todo: "PriceBook scoped (company default, location, customer tier, combined scope TBD)" → Resolution: Resolved via DECISION-PRICING-004.
- Original todo: "Promotion eligibility rules combination logic AND/OR TBD" → Resolution: Resolved via DECISION-PRICING-009.
- Original todo: "MSRP historical immutability permission string TBD" → Resolution: Resolved via DECISION-PRICING-018.
- Original todo: "Rule ordering exact tie-breakers TODO" → Resolution: Resolved via DECISION-PRICING-006.
- Original todo: "Effective dating boundary semantics CLARIFY" → Resolution: Resolved via DECISION-PRICING-003.
- Original todo: "Price quote base path CLARIFY" → Resolution: Resolved via DECISION-PRICING-002.
- Original todo: "Snapshot currency representation CLARIFY" → Resolution: Resolved via DECISION-PRICING-001.
- Original todo: "Restriction override request schema CLARIFY" → Resolution: Resolved via DECISION-PRICING-012.
- TODO: Clarify whether estimate totals “CalculationSnapshot” is the same as `PricingSnapshot` or a separate snapshot type; document the canonical endpoint(s) and DTO(s) once backend confirms.

## End

End of document.
