# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist is used to validate that changes in the `positivity` domain are implementable, testable, and safe across POS backend and frontend integration points. It incorporates previously open questions into explicit, testable acceptance criteria so stories can be implemented without guesswork. It is intended for engineers and reviewers validating PRs.

## Completed items

- [x] Updated acceptance criteria for each resolved open question
- [x] Ensured checklist aligns with `AGENT_GUIDE.md` decisions

## Scope / Ownership

- [ ] The change is correctly scoped to **positivity** responsibilities (POS orchestration, aggregation, Order aggregate ownership, POS-facing read models).
- [ ] Cross-domain authority boundaries are respected:
  - [ ] **workexec** is authoritative for workorder cancellability and cancellation.
  - [ ] **billing** is authoritative for payment reversal (void/refund decision).
  - [ ] **catalog/pricing/inventory** are authoritative for their respective read models (for product detail aggregation).
- [ ] Product detail UI is **read-only** and does not introduce “price finalization” or checkout/quote commit behavior.
- [ ] The product detail screen uses the **single aggregated endpoint** (no direct frontend calls to Catalog/Pricing/Inventory services unless explicitly approved).
- [ ] Location context ownership is clear (where `locationId` comes from) and consistent with repo conventions (route/query vs global state vs selector).
- [ ] Integration points (Moqui screen, Vue route/component, backend endpoint) are explicitly listed in the PR description and match the story contract.

---

## Data Model & Validation

- [ ] Persistence changes (tables/columns/indexes) are present, migrated, and backward compatible where required.
- [ ] Input validation is enforced at the API boundary:
  - [ ] UUID format validation for IDs (`productId`, `locationId`, `orderId`, etc.).
  - [ ] Required fields enforced (e.g., `location_id` required for product detail).
  - [ ] Invalid `location_id` returns `400` (not `200` with degraded payload).
- [ ] Product detail response DTO (`ProductDetailView`) includes required fields and types:
  - [ ] `productId` is present and matches the requested `{productId}`.
  - [ ] `description` is present (may be empty string).
  - [ ] `specifications` is present (may be empty array).
  - [ ] `generatedAt` is present and ISO-8601 parseable.
  - [ ] `substitutions` is present (may be empty array) and each item has `productId` (UUID-shaped) and `reason` (string).
- [ ] Component status modeling is consistent and explicit:
  - [ ] `pricing.status` is present and non-empty string.
  - [ ] `availability.status` is present and non-empty string.
  - [ ] When `pricing.status != OK`, numeric pricing fields (`msrp`, `storePrice`) are `null`/omitted (never `0` as a sentinel).
  - [ ] When `availability.status != OK`, quantity fields (`onHandQuantity`, `availableToPromiseQuantity`) are `null`/omitted (never `0` as a sentinel).
  - [ ] `pricing.asOf` / `availability.asOf` are present when applicable and ISO-8601 parseable.
- [ ] Lead time object handling is safe and null-tolerant:
  - [ ] If `availability.leadTime` is present, `minDays`/`maxDays` are non-negative integers when present.
  - [ ] If `availability.leadTime.asOf` is present, it is ISO-8601 parseable.
- [ ] Domain invariants are enforced in the aggregate/service layer (not only controller-level validation).
- [ ] State transitions are validated (e.g., cannot move from `CANCELLED` back to active; cannot re-run side effects once terminal).

---

## API Contract

- [ ] Endpoints match the story specification (paths, methods, query params):
  - [ ] Product detail endpoint exists: `GET /api/v1/products/{productId}?location_id={locationId}`.
- [ ] Response codes match the story’s rules:
  - [ ] `200` returns `ProductDetailView` (including degraded component statuses when dependencies fail).
  - [ ] `404` when product not found in Catalog (do not synthesize).
  - [ ] `400` for invalid `location_id` (UUID invalid or not allowed per rules).
  - [ ] `401/403` when unauthenticated/unauthorized (no partial data).
- [ ] Response schemas include required metadata:
  - [ ] `generatedAt` is always present on `200`.
  - [ ] Per-component `status` is always present on `200` (pricing + availability).
  - [ ] Degraded responses never silently return `null` without a corresponding `status=UNAVAILABLE|STALE` (or non-OK).
- [ ] Error responses are consistent and actionable (stable error codes/messages; no leaking internals).
- [ ] API changes are versioned or backward compatible (no breaking changes without a version bump and migration plan).

---

## Events & Idempotency

- [ ] Cancellation uses a **persisted saga/state machine** (not purely in-memory orchestration).
- [ ] An **idempotency key** is created/derived and persisted for cancel attempts.
- [ ] Duplicate cancel requests:
  - [ ] Do not re-trigger downstream side effects if already executed.
  - [ ] Return current cancellation state (or equivalent) deterministically.
- [ ] Downstream calls include idempotency identifiers:
  - [ ] Workexec cancel includes `idempotencyKey`.
  - [ ] Billing reverse includes `idempotencyKey`.
- [ ] Ordering guarantees are implemented and tested:
  - [ ] Workexec cancel attempted before Billing reversal when `workOrderId` exists.
  - [ ] Billing reversal is not attempted if Workexec rejects with `409`.
- [ ] Canonical POS/positivity events are emitted for cancellation progress/final state (success and failure).
- [ ] Events include correlation identifiers (`correlationId`, `orderId`, and idempotency key where appropriate).
- [ ] Event emission is resilient (e.g., outbox pattern or documented at-least-once behavior) and does not block the main request path without justification.
- [ ] Product detail read path does not emit high-cardinality events per request unless explicitly required (avoid noisy event streams for read-only UI).

---

## Security

- [ ] Authentication is required for all endpoints in scope.
- [ ] Authorization is enforced for:
  - [ ] Order cancellation (role/permission check for Store Manager or equivalent).
  - [ ] Product/location reads (location-scoped access where applicable).
- [ ] Location-scoped access is enforced server-side (do not rely on frontend-provided `location_id` alone):
  - [ ] Requests for unauthorized locations return `403` (or repo-standard equivalent).
- [ ] Sensitive identifiers are handled safely:
  - [ ] No payment PAN/PCI data is stored or logged.
  - [ ] Logs do not include secrets/tokens/credentials.
- [ ] Input is sanitized/validated to prevent injection (SQL/JPQL, log injection).
- [ ] Cross-service calls use approved service-to-service auth mechanisms (no hardcoded credentials).
- [ ] Rate limiting / abuse controls are considered for high-traffic read endpoints (product detail) and state-changing endpoints (cancel).

---

## Observability

- [ ] Structured logs include key identifiers:
  - [ ] `productId` and `locationId` for product detail requests.
  - [ ] `orderId`, `workOrderId`, `paymentId` (when present) for cancellation flows.
  - [ ] `correlationId` and `idempotencyKey` for cancellation flows.
- [ ] Distributed tracing propagates correlation/trace identifiers to downstream calls (Catalog/Pricing/Inventory; Workexec/Billing).
- [ ] Metrics are added/updated and tagged appropriately:
  - [ ] Dependency latency metrics for Catalog/Pricing/Inventory and Workexec/Billing.
  - [ ] Counters for degraded product detail responses by component (`pricing_status`, `availability_status`).
  - [ ] Counters for product detail errors by HTTP status (`400/401/403/404/5xx`).
- [ ] Alerts/dashboards are feasible based on emitted metrics (at minimum: failure rate, timeout rate, degraded rate).
- [ ] Failures are categorized consistently and visible in logs/metrics (e.g., pricing timeout vs inventory timeout vs catalog 404).

---

## Performance & Failure Modes

- [ ] Timeouts are configured for all downstream calls (Workexec, Billing, Catalog, Pricing, Inventory) and are not infinite.
- [ ] Retry behavior is bounded and safe:
  - [ ] Cancellation reversal retries use backoff and a max attempt limit.
  - [ ] Retries are idempotent and do not duplicate side effects.
- [ ] Product detail aggregation failure behavior matches story intent:
  - [ ] Catalog not found yields `404` (no partial rendering).
  - [ ] Pricing failure yields `200` with `pricing.status != OK` and pricing fields null/omitted.
  - [ ] Inventory failure yields `200` with `availability.status != OK` and quantity fields null/omitted.
  - [ ] Both pricing and inventory failures yield `200` with both statuses non-OK and no misleading numeric values.
- [ ] Caching behavior (for product detail) is implemented as specified:
  - [ ] Aggregated TTL defaults to ~15s (or documented alternative).
  - [ ] Component staleness is represented via `asOf` and `status`.
  - [ ] Cache keys include all required context (at minimum `productId` + `locationId`).
- [ ] Concurrency is handled:
  - [ ] Concurrent cancel attempts do not corrupt state (optimistic locking/versioning or equivalent).
  - [ ] State transitions are atomic at the aggregate level.
- [ ] Frontend “latest request wins” behavior is supported safely:
  - [ ] Backend responses include enough context to prevent UI mismatch (e.g., response `productId` present; optionally echo `locationId` if contract allows).
  - [ ] No server-side caching bug causes cross-location data leakage (verify cache key includes `locationId`).

---

## Testing

- [ ] Unit tests cover:
  - [ ] Product detail aggregation mapping and status metadata behavior.
  - [ ] Degraded mapping: pricing failure => `pricing.status != OK` and numeric fields null/omitted.
  - [ ] Degraded mapping: inventory failure => `availability.status != OK` and quantity fields null/omitted.
  - [ ] Lead time mapping is null-safe and does not throw on missing optional fields.
- [ ] Integration tests cover:
  - [ ] Product not found returns `404` and does not synthesize.
  - [ ] Invalid `location_id` returns `400`.
  - [ ] Unauthorized location access returns `403` (or repo-standard equivalent).
  - [ ] Pricing unavailable produces `200` with correct `pricing.status` and no numeric prices.
  - [ ] Inventory unavailable produces `200` with correct `availability.status` and no numeric quantities.
  - [ ] Both unavailable produces `200` with both statuses non-OK and no misleading numeric values.
- [ ] Contract tests (or schema validation) exist for:
  - [ ] `GET /api/v1/products/{productId}?location_id={locationId}` response schema (including `generatedAt`, component `status`, and nullability rules).
  - [ ] Workexec and Billing request/response shapes used by cancellation flows (if touched).
- [ ] Frontend tests (where applicable in repo) validate UI-safe behavior:
  - [ ] UI does not render `null` prices/quantities as `0`.
  - [ ] UI does not claim “in stock/out of stock” when `availability.status != OK`.
  - [ ] UI shows “Product not found” on `404` and does not show partial cached data.
  - [ ] Rapid location changes do not show mismatched location data (latest request wins).
- [ ] Negative/security tests exist for unauthorized/forbidden access and invalid inputs.

---

## Documentation

- [ ] API documentation updated (OpenAPI/Swagger) with:
  - [ ] Request/response schemas including `generatedAt`, component `status`, and `asOf`.
  - [ ] Explicit nullability/omission rules for pricing and availability numeric fields when status is non-OK.
  - [ ] Error codes and examples for `400/401/403/404/5xx`.
- [ ] UI integration notes documented (repo-appropriate place):
  - [ ] How `locationId` is sourced and propagated (query param vs global state).
  - [ ] How the product detail screen is routed (Moqui screen vs Vue route) and how auth is enforced.
- [ ] Runbook notes added for operations:
  - [ ] How to identify elevated degraded rates for pricing/inventory dependencies.
  - [ ] How to correlate a frontend failure to backend logs/traces using correlation/trace IDs.
- [ ] Any new configuration (timeouts, TTLs, retry limits) is documented with defaults and rationale.

---

## Acceptance Criteria (per resolved question)

### Q: What is the frontend source of truth for selected `locationId`?

- Acceptance: The frontend obtains `locationId` from a single app-level “location context” and always calls Product Detail with `location_id={locationId}`.
- Test Fixtures:
  - `locationId = 11111111-1111-1111-1111-111111111111`
  - `productId = 22222222-2222-2222-2222-222222222222`
- Example API request/response:

```http
GET /api/v1/products/22222222-2222-2222-2222-222222222222?location_id=11111111-1111-1111-1111-111111111111
```

### Q: What is the standard Moqui ↔ Vue integration pattern for Product Detail?

- Acceptance: The Product Detail UI is served under a Moqui-hosted screen that mounts a Vue component; the Vue component calls the single aggregated backend endpoint.
- Test Fixtures:
  - Navigate to the Product Detail screen and confirm authenticated session is required.
- Example behavior:
  - If unauthenticated, navigation results in `401/redirect` per Moqui convention.

### Q: What exact permission/role gate should be enforced for product/location reads?

- Acceptance: Backend requires permission `positivity:product_detail:read` and enforces location-scoped authorization; unauthorized requests return `403` with no partial response payload.
- Test Fixtures:
  - User A has `positivity:product_detail:read` and access to Location X.
  - User B has `positivity:product_detail:read` but lacks access to Location X.
- Example API request/response:

```http
GET /api/v1/products/22222222-2222-2222-2222-222222222222?location_id=11111111-1111-1111-1111-111111111111
```

### Q: Are `pricing.status` / `availability.status` strict enums or opaque strings?

- Acceptance: Frontend gates display logic only on `status === "OK"`; any other value is treated as “non-OK” (no numeric display and no “in stock/out of stock” claim).
- Test Fixtures:
  - pricing.status = `OK`
  - pricing.status = `UNAVAILABLE`
  - pricing.status = `SOME_NEW_STATUS`
- Example API response snippet:

```json
{
  "pricing": { "status": "SOME_NEW_STATUS", "storePrice": null, "currency": null },
  "availability": { "status": "UNAVAILABLE", "onHandQuantity": null }
}
```

### Q: What is the project-standard currency formatting utility/component?

- Acceptance: When `pricing.status == OK`, UI formats `storePrice` using locale-aware currency formatting with the provided ISO-4217 `currency`. When `storePrice` is null or status is non-OK, UI renders a non-numeric fallback.
- Test Fixtures:
  - `storePrice = 19.99`, `currency = "USD"`
  - `storePrice = null`, `currency = null`
- Example API response snippet:

```json
{
  "pricing": { "status": "OK", "storePrice": 19.99, "currency": "USD" }
}
```

### Q: Should `generatedAt` and component `asOf` be visible in the UI?

- Acceptance: API always returns `generatedAt` and may return component `asOf`. UI does not display these timestamps by default, but they are accessible via an optional details view and are preserved for diagnostics.
- Test Fixtures:
  - `generatedAt` is present and ISO-8601 parseable.
- Example API response snippet:

```json
{
  "generatedAt": "2026-01-19T17:10:30Z",
  "pricing": { "status": "OK", "asOf": "2026-01-19T17:10:15Z" }
}
```

## End

End of document.
