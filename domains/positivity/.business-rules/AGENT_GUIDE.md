# AGENT_GUIDE.md

## Summary

This document defines the **normative** rules for the `positivity` domain, which owns POS-facing orchestration (notably Order cancellation) and composed read models (notably Product Detail). It reconciles previously open questions and TODOs into explicit, versionable decisions and contracts. The non-normative rationale and alternatives live in `DOMAIN_NOTES.md`.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `DOMAIN_NOTES.md`
- [x] Reconciled todos and clarifications from prior documents

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-POSITIVITY-001 | Order cancellation uses persisted saga |
| DECISION-POSITIVITY-002 | Work cancellation precedes payment reversal |
| DECISION-POSITIVITY-003 | Cancellation idempotency and deduplication |
| DECISION-POSITIVITY-004 | Product Detail aggregation with graceful degradation |
| DECISION-POSITIVITY-005 | `location_id` is required for Product Detail |
| DECISION-POSITIVITY-006 | Null numeric fields when component status is non-OK |
| DECISION-POSITIVITY-007 | `generatedAt` / `asOf` / staleness semantics |
| DECISION-POSITIVITY-008 | Short TTL caching and cache key requirements |
| DECISION-POSITIVITY-009 | Fail-fast only on Catalog 404 |
| DECISION-POSITIVITY-010 | Dynamic lead time overrides static lead time |
| DECISION-POSITIVITY-011 | Status fields are treated as opaque strings (gate on `OK`) |
| DECISION-POSITIVITY-012 | Currency field rules for pricing |
| DECISION-POSITIVITY-013 | Permission gating model for Product Detail |
| DECISION-POSITIVITY-014 | Trace/correlation propagation and response headers |
| DECISION-POSITIVITY-015 | No per-request events for read degradation (metrics only) |

## Domain Boundaries

### What `positivity` owns (system of record)

- **Order aggregate** state and transitions (including cancellation state machine and saga progress).
- **Cancellation attempt metadata** (`cancellationReason`, `correlationId`, `cancellationIdempotencyKey`, failure categorization).
- **POS-facing API composition** for Product Detail (response shaping, caching policy, and explicit degradation metadata).
- **Degradation semantics** for composed reads (explicit status fields, null-safe payload rules, and timestamps).

### What `positivity` does not own

- **Work order cancellability and cancellation** are owned by `workexec`.
- **Payment reversal execution and void/refund decision** are owned by `billing`.
- **Product master data** is owned by Catalog.
- **Pricing** is owned by Pricing.
- **Availability/ATP and dynamic lead time** are owned by Inventory/Supply Chain.
- **Frontend UX state** for selecting a location is owned by the POS shell/app; `positivity` validates `location_id` and enforces authorization.

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| Order | POS-owned aggregate representing an order and its lifecycle, including cancellation state. |
| CancellationSaga | Persisted saga/state machine that coordinates cancellation steps across `workexec` and `billing`. |
| ProductDetailView | POS-facing read DTO composed from Catalog + Pricing + Inventory with explicit component statuses. |
| PricingComponent | Portion of ProductDetailView containing pricing status and optional numeric fields. |
| AvailabilityComponent | Portion of ProductDetailView containing availability status, optional quantities, and lead time. |

## Invariants / Business Rules

### Cancellation

- Authority invariant: `workexec` is authoritative for work-order cancellation; `billing` is authoritative for payment reversal. (DECISION-POSITIVITY-001)
- Ordering invariant: cancel work first, then reverse payment. (DECISION-POSITIVITY-002)
- No distributed transactions: persisted saga with retries and explicit failure states. (DECISION-POSITIVITY-001)
- Idempotency: duplicate cancel requests must not re-run side effects; return current cancellation state deterministically. (DECISION-POSITIVITY-003)

### Product Detail aggregation

- `location_id` is required for location-scoped fields; missing/invalid is `400`. (DECISION-POSITIVITY-005)
- Catalog 404 is the only fail-fast condition (`404`); other component failures degrade to `200` with statuses. (DECISION-POSITIVITY-009)
- When `pricing.status != OK`, all pricing numeric fields are `null` (never `0`). (DECISION-POSITIVITY-006)
- When `availability.status != OK`, all quantity numeric fields are `null` (never `0`). (DECISION-POSITIVITY-006)
- Lead time precedence: Inventory dynamic lead time overrides Catalog static hints when both exist. (DECISION-POSITIVITY-010)
- Cache key must include `productId` and `locationId` to prevent cross-location leakage. (DECISION-POSITIVITY-008)

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-POSITIVITY-001 | Persisted saga orchestration for cancellation | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-001---order-cancellation-uses-persisted-saga) |
| DECISION-POSITIVITY-002 | Work first, then payment | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-002---work-cancellation-precedes-payment-reversal) |
| DECISION-POSITIVITY-003 | Client idempotency key with server dedupe | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-003---cancellation-idempotency-and-deduplication) |
| DECISION-POSITIVITY-004 | Aggregate endpoint with graceful degradation | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-004---product-detail-aggregation-with-graceful-degradation) |
| DECISION-POSITIVITY-005 | `location_id` required | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-005---location_id-is-required-for-product-detail) |
| DECISION-POSITIVITY-006 | Null numeric fields when non-OK | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-006---null-numeric-fields-when-component-status-is-non-ok) |
| DECISION-POSITIVITY-007 | Timestamp + staleness semantics | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-007---generatedat-asof-and-staleness-semantics) |
| DECISION-POSITIVITY-008 | Short TTL caching | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-008---short-ttl-caching-and-cache-key-requirements) |
| DECISION-POSITIVITY-009 | Only Catalog miss returns 404 | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-009---fail-fast-only-on-catalog-404) |
| DECISION-POSITIVITY-010 | Dynamic lead time overrides static | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-010---dynamic-lead-time-overrides-static-lead-time) |
| DECISION-POSITIVITY-011 | Status fields treated as opaque strings | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-011---status-fields-are-treated-as-opaque-strings) |
| DECISION-POSITIVITY-012 | Currency rules | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-012---currency-field-rules-for-pricing) |
| DECISION-POSITIVITY-013 | Permission gating model | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-013---permission-gating-model-for-product-detail) |
| DECISION-POSITIVITY-014 | Trace/correlation propagation | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-014---trace-and-correlation-propagation) |
| DECISION-POSITIVITY-015 | No read-side events for degradation | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-positivity-015---no-per-request-events-for-read-degradation) |

## Open Questions (from source)

### Q: What is the frontend source of truth for selected `locationId` (global state, route query `location_id`, user default, or selector)?

- Answer: The frontend stores the selected location in app-level state (global “location context”), and the Product Detail call always includes it as `location_id` query param.
- Assumptions:
  - The POS shell/app already has a notion of “current location/store context”.
  - Deep links may include `location_id`, but app state remains the primary source of truth.
- Rationale:
  - Keeps URL/query contract explicit while avoiding per-screen duplication of location selection logic.
- Impact:
  - UI must block Product Detail requests until a location is selected.
  - Backend cache keys and authorization must use `location_id`.
- Decision ID: DECISION-POSITIVITY-005

### Q: What is the standard Moqui ↔ Vue integration pattern for Product Detail?

- Answer: Implement Product Detail as a Moqui-hosted screen that mounts a Vue component, relying on Moqui for auth/session and using the aggregated `positivity` endpoint from the Vue component.
- Assumptions:
  - This repo’s UI is primarily served via Moqui screens with embedded Vue/Quasar components.
- Rationale:
  - Centralizes authentication and navigation under Moqui while keeping UI logic in Vue.
- Impact:
  - Screen routing and guards live in Moqui; component logic lives in Vue.
- Decision ID: DECISION-POSITIVITY-004

### Q: What exact permission/role gate should be enforced for product/location reads, and what is the naming convention?

- Answer: Use the platform permission naming convention `domain:resource:action`. Product Detail requires `positivity:product_detail:read`, and location scoping is enforced by checking location authorization for the caller.
- Assumptions:
  - Permission strings are registered code-first via the Security domain.
  - Location authorization is evaluated server-side (deny by default).
- Rationale:
  - Keeps permissions stable and auditable; avoids embedding UI-only role names in contracts.
- Impact:
  - Backend must return `401/403` without partial data.
  - Frontend route guard should check the same permission if a capability endpoint exists.
- Decision ID: DECISION-POSITIVITY-013

### Q: Are `pricing.status` / `availability.status` enums strict (`OK|UNAVAILABLE|STALE`) or should clients treat them as opaque strings and only special-case `OK` vs non-OK?

- Answer: Treat status fields as opaque strings; client logic must only special-case `status == "OK"` and treat everything else as “non-OK”.
- Assumptions:
  - Additional statuses may be introduced later (e.g., `DEGRADED`, `PARTIAL`).
- Rationale:
  - Prevents brittle client parsing and enables safe evolution.
- Impact:
  - Frontend must not hardcode a finite enum list beyond `OK` gating.
- Decision ID: DECISION-POSITIVITY-011

### Q: What is the project-standard currency formatting utility/component and null-safe display pattern?

- Answer: UI must format money using the project’s i18n/locale number formatting (e.g., Vue i18n `$n` currency formatting). If `storePrice` is `null` or status is non-OK, display a non-numeric fallback (“Price unavailable”) and do not attempt to format.
- Assumptions:
  - Vue i18n number formats are configured for currency display.
- Rationale:
  - Prevents `null -> 0` coercion and keeps display locale-correct.
- Impact:
  - Frontend rendering must never display `0.00` unless backend returned `0.00` with `status == OK`.
- Decision ID: DECISION-POSITIVITY-012

### Q: Should `generatedAt` and component `asOf` be visible in the UI by default?

- Answer: Not by default. These timestamps must be present in the API response for support/debugging and can be shown in an optional “Details” drawer/tool-tip for privileged users.
- Assumptions:
  - Product/UX can decide whether to expose “as-of” messaging later.
- Rationale:
  - Avoids cluttering the primary UI while retaining transparency and auditability.
- Impact:
  - Frontend should preserve these fields for diagnostics and logging (avoid PII).
- Decision ID: DECISION-POSITIVITY-007

### Q: Is `pricing.currency` required when `pricing.status == OK`?

- Answer: Yes. When `pricing.status == OK`, `pricing.currency` must be a non-empty ISO-4217 code. When status is non-OK, `pricing.currency` is `null`.
- Assumptions:
  - Pricing service is authoritative for currency and returns it with prices.
- Rationale:
  - Prevents ambiguous formatting and mismatched locale assumptions.
- Impact:
  - Backend schema and tests must enforce currency presence when OK.
- Decision ID: DECISION-POSITIVITY-012

### Q: Can `location_id` be omitted for a “catalog-only” Product Detail view?

- Answer: No for the Product Detail endpoint. If catalog-only behavior is needed, it should be a separate endpoint/variant with an explicit contract.
- Assumptions:
  - Product Detail UI depends on location-scoped availability and pricing.
- Rationale:
  - Avoids ambiguous semantics and accidental cross-location leakage.
- Impact:
  - Missing/invalid `location_id` is always `400`.
- Decision ID: DECISION-POSITIVITY-005

### Q: What are the staleness thresholds and when should the service emit `STALE` vs `UNAVAILABLE`?

- Answer: `STALE` is only used when serving cached component data that is older than a configurable threshold; otherwise use `UNAVAILABLE` when data cannot be obtained. Defaults: `staleAfterSeconds=300` and `maxStaleSeconds=3600`.
- Assumptions:
  - Thresholds are configuration-backed and can be tuned without contract breakage.
- Rationale:
  - Separates “we have older data” from “we have no data”, enabling better UX.
- Impact:
  - Requires tests for threshold boundary behavior.
  - SRE dashboards should track stale vs unavailable rates separately.
- Decision ID: DECISION-POSITIVITY-007

### Q: Should `generatedAt` reflect cache time or composition time?

- Answer: `generatedAt` reflects the timestamp of the composed response payload (including when it was cached). Component freshness is indicated via per-component `asOf` and `status`.
- Assumptions:
  - Cached responses preserve the original `generatedAt` at the time of composition.
- Rationale:
  - Keeps `generatedAt` truthful for cached payloads without conflating it with component times.
- Impact:
  - Clients can compare `generatedAt` and `asOf` for troubleshooting.
- Decision ID: DECISION-POSITIVITY-007

### Q: Should the system emit read-side events for Product Detail degradation?

- Answer: No. Read degradation is tracked via metrics (and optionally structured logs), not per-request events.
- Assumptions:
  - Events are reserved for business-significant state changes (cancellation saga, etc.).
- Rationale:
  - Avoids high-cardinality event streams and excess costs.
- Impact:
  - Add counters/histograms instead of events.
- Decision ID: DECISION-POSITIVITY-015

### Q: What is the standard trace header propagation model from frontend to backend?

- Answer: Use W3C Trace Context (`traceparent`, `tracestate`) when available. If absent, the backend generates a `correlationId` and returns it as `X-Correlation-Id` in responses.
- Assumptions:
  - The platform supports standard HTTP header forwarding.
- Rationale:
  - Keeps tracing interoperable across services and tools.
- Impact:
  - Client/network layer should forward `traceparent` when present.
  - Backend logs should always include `correlationId`.
- Decision ID: DECISION-POSITIVITY-014

## Todos Reconciled

- Original todo: "confirm whether `location_id` can be omitted for “catalog-only” view" → Resolution: Resolved (no; separate endpoint if needed) | Decision: DECISION-POSITIVITY-005
- Original todo: "define staleness thresholds and when to emit `STALE` vs `UNAVAILABLE`" → Resolution: Resolved (configurable thresholds with defaults) | Decision: DECISION-POSITIVITY-007
- Original todo: "confirm whether `generatedAt` should reflect cache time or composition time" → Resolution: Resolved (composition time for payload, preserved across caching) | Decision: DECISION-POSITIVITY-007
- Original todo: "decide whether to emit read-side events for ProductDetailView degradation" → Resolution: Resolved (metrics only) | Decision: DECISION-POSITIVITY-015
- Original todo: "document required permission string(s)" → Resolution: Resolved (use `domain:resource:action` convention; `positivity:product_detail:read`) | Decision: DECISION-POSITIVITY-013
- Original todo: "frontend trace header propagation standard" → Resolution: Resolved (W3C trace context + `X-Correlation-Id`) | Decision: DECISION-POSITIVITY-014

## End

End of document.
