---
title: Pricing Backend Contract Guide
domain: pricing
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/pricing/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-price/openapi.yaml
openapi_commit: ca7fadc3
last_verified_utc: 2026-02-24T14:23:11Z
last_updated: 2026-02-24
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

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Operation | `getSnapshot` | GET | `/v1/price/snapshots/{snapshotId}` | Refer to generated API reference for payload details |
| Normalize pricing | `normalizePricing` | POST | `/v1/price/normalize` | Refer to generated API reference for payload details |
| Operation | `calculatePriceQuote` | POST | `/v1/price/quotes` | Refer to generated API reference for payload details |
| Evaluate price restrictions | `evaluateRestrictions` | POST | `/v1/price/restrictions:evaluate` | Refer to generated API reference for payload details |
| Override price restrictions | `overrideRestrictions` | POST | `/v1/price/restrictions:override` | Refer to generated API reference for payload details |

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

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-price/openapi.yaml`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:23:11Z`
- Generated API reference: `domains/pricing/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/pricing/.business-rules/AGENT_GUIDE.md`
- `domains/pricing/.business-rules/DOMAIN_NOTES.md`
- `domains/pricing/.business-rules/BACKEND_API_REFERENCE.generated.md`
