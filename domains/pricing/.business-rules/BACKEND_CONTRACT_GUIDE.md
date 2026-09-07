---
title: Pricing Backend Contract Guide
domain: pricing
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/pricing/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-price/openapi.yaml
openapi_commit: 0937c73e
last_verified_utc: 2026-09-07T15:30:00Z
last_updated: 2026-09-07
api_reference_generated: domains/pricing/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Pricing Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Pricing domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-price/openapi.yaml`
- Generated API reference: `domains/pricing/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/pricing/.business-rules/AGENT_GUIDE.md`

## How To Use This Guide

Backend coder workflow:

1. Read `Domain Invariants` and the relevant capability section.
2. Validate behavior constraints before implementing endpoint changes.
3. Use `operationId` mappings here, then confirm payload details in generated API reference.
4. Ensure tests cover each changed behavioral assertion.

Frontend developer workflow:

1. Start with `Frontend API Lookup` and identify the `operationId` for the UI action.
2. Open generated API reference for exact payload and response details.
3. Implement error handling and headers described in this guide.

## Domain Invariants

- Pricing behavioral rules are authoritative in backend services, not inferred from frontend state.
- Mutating operations require explicit permission enforcement and auditable outcomes.
- Error responses and correlation headers must be deterministic and traceable across requests.
- Cross-domain interactions must go through API/event contracts, not direct data coupling.

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-TBD | `None` | draft | Pricing Capability Backlog |
| Tier 0 | `durion-positivity-backend#1575`, `#1569` | draft | Shop labor rates and the labor matrix — the price half of a labor line |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Operation | `getSnapshot` | GET | `/v1/price/snapshots/{snapshotId}` | Refer to generated API reference for payload details |
| Normalize pricing | `normalizePricing` | POST | `/v1/price/normalize` | Refer to generated API reference for payload details |
| Operation | `calculatePriceQuote` | POST | `/v1/price/quotes` | Refer to generated API reference for payload details |
| Evaluate price restrictions | `evaluateRestrictions` | POST | `/v1/price/restrictions:evaluate` | Refer to generated API reference for payload details |
| Override price restrictions | `overrideRestrictions` | POST | `/v1/price/restrictions:override` | Refer to generated API reference for payload details |
| List labor rates | `listLaborRates` | GET | `/v1/labor-rates` | The whole table, including closed windows |
| Create a labor rate | `createLaborRate` | POST | `/v1/labor-rates` | Rates are never edited; a change opens a new window |
| List labor matrix steps | `listLaborRateAdjustments` | GET | `/v1/labor-rates/adjustments` | In application order |
| Create a labor matrix step | `createLaborRateAdjustment` | POST | `/v1/labor-rates/adjustments` | Sequence changes the resulting rate |
| Price one job's labor | `resolveLaborRate` | POST | `/v1/labor-rates/quote` | Service-to-service edge; pos-workorder only |

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-TBD: Pricing Capability Backlog

### Capability Metadata

- Capability ID: CAP-TBD
- Parent Issue: None
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-price/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Operation | `getSnapshot` | GET | `/v1/price/snapshots/{snapshotId}` |
| Normalize pricing | `normalizePricing` | POST | `/v1/price/normalize` |
| Operation | `calculatePriceQuote` | POST | `/v1/price/quotes` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Base Price Effective Windows (ADR-0054 §4)

- Base prices in `pos-price` are history-retaining: each row is one effective window per
  (productId, currency); price changes append a new row and close the predecessor's window.
- Window semantics are half-open on `Instant`: a base price row is effective for instants `t`
  where `effectiveFrom <= t < effectiveTo`; a null `effectiveTo` means the window is open-ended
  (inclusive start, exclusive end).
- Quote resolution (`calculatePriceQuote`) selects the base-price window covering the pricing
  instant. When no window covers it, the API returns `404` with `ApiError` code
  `PRICE_BASE_UNAVAILABLE` (replaces the former `PRODUCT_NOT_FOUND` on this path).
- Base-price writes that would overlap or backdate the latest existing window are rejected with
  `409` and `ApiError` code `PRICE_BASE_WINDOW_CONFLICT`; re-submitting the current open window's
  price is an idempotent no-op.
- Quote resolution is currency-aware (durion-positivity-backend#1239): `calculatePriceQuote`
  accepts an optional ISO 4217 `currency` code; when omitted, the company default currency applies
  (`pos.price.default-currency`, default `USD`). Base-price and location-override selection are
  filtered to the resolved currency; customer-tier discounts are percentage-based and
  currency-agnostic. A currency with no applicable base-price window returns `404`
  `PRICE_BASE_UNAVAILABLE`.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-price/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## Tier 0: Shop Labor Rates & The Labor Matrix

### Capability Metadata

- Capability ID: Tier 0 (issue-driven; no capability manifest)
- Parent Issue: `durion-positivity-backend#1575` (sourcing tiers), `durion-positivity-backend#1569`
  (estimated service time — this closes its hand-typed price operand)
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-price/openapi.yaml`
- ADRs: ADR-0054 (sell-price system-of-record split), ADR-0044 amendment 2026-09-07 (the quote
  edge), ADR-0026 (grant surface)

#1569's gap analysis ended with *"book time x rate would still have one input hand-typed on the
line"*, because pos-price modelled no hourly rate at all. This is that rate. The split follows
ADR-0054: **pos-catalog owns how long an operation takes, pos-price owns what an hour of it
costs**, and pos-workorder multiplies them. Neither module needs the other's table.

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| List labor rates | `listLaborRates` | GET | `/v1/labor-rates` |
| Create a labor rate | `createLaborRate` | POST | `/v1/labor-rates` |
| List labor matrix steps | `listLaborRateAdjustments` | GET | `/v1/labor-rates/adjustments` |
| Create a labor matrix step | `createLaborRateAdjustment` | POST | `/v1/labor-rates/adjustments` |
| Resolve one job's labor rate | `resolveLaborRate` | POST | `/v1/labor-rates/quote` |
| Bulk import labor rates | `bulkIngestLaborRates` | POST | `/v1/labor-rates/bulk-ingest` |
| Bulk import labor matrix steps | `bulkIngestLaborRateAdjustments` | POST | `/v1/labor-rate-adjustments/bulk-ingest` |

### Behavioral Assertions

**Rate scope narrows before category.** Resolution picks the narrowest row in force at the
requested instant: `(location, category)`, then `(location, null)`, then `(null, category)`, then
the platform default `(null, null)`. A location's own general rate therefore beats the platform's
category rate — which is the ordering a shop expects once it has priced its own work. The
answering row's `scope` is returned so a caller can see which applied.

**Rates are never edited in place.** There is no update or delete. A rate that has priced an
invoice cannot be edited away without making that invoice unexplainable, so a change closes the
current window and opens a new row — the same append-and-supersede reasoning as
`service_labor_standard` in pos-catalog. Windows are half-open `[effectiveFrom, effectiveTo)`;
a null `effectiveTo` is open-ended.

**The matrix is ordered, and the order is part of the answer.** Steps are opt-in: a quote names
the `adjustmentCodes` the writer agreed apply. They are applied in `sequence` order, `PERCENT`
compounding on the running rate and `FIXED` adding to it. Order is stored rather than assumed
because percentages compound — +15% then -10% is not -10% then +15%, and a fleet discount
sequenced last is a discount off the *adjusted* rate, which is what the contract says.

**Every step is itemised in the response.** `steps[]` returns each applied code, its type, its
configured value and the running rate it produced, alongside `baseHourlyRate` and the final
`hourlyRate`. A charge whose derivation a customer cannot see is a charge they will dispute.

**Degradation is typed, and forgiving on the way in.** `resolveLaborRate` answers
`RESOLVED | NO_RATE_AVAILABLE` and never throws for a miss. An unrecognised `operationCategory`
widens to the category-agnostic rate and an `adjustmentCode` the shop has not priced is simply not
applied — both are hints from a caller in another module, and vocabulary drift should cost
precision, not availability. A discount deeper than the rate clamps at zero rather than inverting
the charge.

**Re-quoting is reproducible.** `at` prices the job at a stated instant; omitted, it means now.
Passing the original instant reproduces an old estimate's rate.

**Authoring validates in the service layer, not at the constraint.** An unknown category, a
non-positive rate, a bad currency or an inverted window answer `422` naming the field. The V4 CHECK
constraints are the backstop for direct SQL and concurrent writers, not the user-facing rule.

**Bulk import stores, it does not overwrite.** The two bulk operations exist so a whole rate card
or a whole matrix loads in one call, with the steps' sequences relative to each other visible. A row
whose scope, code and `effectiveFrom` are already held is answered with the *stored* row unchanged
— which is the append-only rule above, not an exception to it, so a changed rate is still a new
window. They answer `200` even when rows fail: read `successCount`, `failureCount` and the per-row
`results`, not the HTTP status.

**The Tier 0 rates are invented.** They are placeholder pricing, not any shop's real rates, and
they arrive through the bulk operations above rather than through a database seed; the fixture packs
are `durion-positivity-backend/scripts/fixtures/seed/alpha/price/labor-rate*.csv`.

### Frontend Usage Notes

- Use the operation IDs above as the stable integration keys; read payload shapes from the
  generated API reference.
- Render `steps[]` as the derivation of the charged rate — base rate, each named adjustment, the
  result. That is the artifact a service advisor defends at the counter.
- `NO_RATE_AVAILABLE` is not an error state: leave the price for the writer to type.
- `listLaborRates` returns closed windows too; filter by `effectiveTo` when showing "current".

### ADR Constraints

- ADR-0054: the hourly rate is a sell price and belongs to pos-price. pos-catalog must not carry it.
- ADR-0044 amendment 2026-09-07: `resolveLaborRate` is granted file-scoped to pos-workorder's
  `PriceLaborRateClientImpl`. No other caller, no write path, and no replica fallback behind it —
  a stale rate is a wrong number on an invoice.
- ADR-0026: `ShopLaborRateService` is the only granted type in `price.service`.

### Events & Dependencies

- Event ids: `PRICE_LABOR_RATE_CREATE`, `PRICE_LABOR_RATE_LIST`,
  `PRICE_LABOR_RATE_ADJUSTMENT_CREATE`, `PRICE_LABOR_RATE_ADJUSTMENT_LIST`,
  `PRICE_LABOR_RATE_QUOTE`.
- Permissions: `pricing:labor_rate:manage`, `pricing:labor_rate:view`, `pricing:labor_rate:quote`.
  The quote permission is separate on purpose — listing what a shop charges and pricing a specific
  job are different powers, and the edge's holder is a service account.
- The operation category on a quote comes from pos-workorder's local `ext_catalog_service` replica,
  not from a second call into pos-catalog. pos-price publishes no new events for this surface.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-price/src/test/java/com/positivity/price/internal/service/`
  — `LaborRateResolutionServiceImplTest` (scope ladder, matrix arithmetic, typed misses),
  `LaborRateAdminServiceImplTest` (authoring validation).
- Add or update tests covering each behavioral assertion above when behavior changes.

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-price/openapi.yaml`
- OpenAPI source revision: `0937c73e` (branch `claude/tier-0-spec-implementation-o5j539`; adds the #1575 Tier 0 labor-rate surface and its two bulk-ingest operations)
- Last verified UTC: `2026-09-07T15:30:00Z`
- Generated API reference: `domains/pricing/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/pricing/.business-rules/AGENT_GUIDE.md`
- `domains/pricing/.business-rules/DOMAIN_NOTES.md`
- `domains/pricing/.business-rules/BACKEND_API_REFERENCE.generated.md`
