```markdown
# AGENT_GUIDE.md — Domain: `positivity`

## Purpose
`positivity` is the POS-facing orchestration domain. It owns the POS **Order** aggregate and provides:
- **Order lifecycle orchestration** across downstream domains (notably `workexec` and `billing`) using a **persisted saga** (no distributed transactions).
- **Read aggregation** for browse/quote experiences by composing authoritative data from Catalog/Pricing/Inventory into a single response with explicit degradation metadata.

This guide is updated to reflect new frontend story requirements for **Product Detail** rendering with **location-scoped pricing and availability signals** and explicit component status handling.

---

## Domain Boundaries

### What `positivity` owns (authoritative)
- **Order aggregate state** and transitions, including cancellation state machine and persisted saga progress.
- **Cancellation attempt metadata**: `cancellationReason`, `correlationId`, `cancellationIdempotencyKey`, and failure categorization.
- **API composition** for product detail views (response shaping, caching policy, and status metadata).
- **Degradation semantics** for composed reads:
  - Explicit `pricing.status` / `availability.status`
  - Null-safe payload rules (e.g., do not emit numeric values when status is not OK)
  - `generatedAt` and component `asOf` timestamps

### What `positivity` does *not* own (delegated authority)
- **Payment state and reversals**: owned by `domain:billing` (void/refund decision and execution).
- **Work order state and cancellability**: owned by `domain:workexec` (authoritative gating + cancellation).
- **Product master data**: owned by Product Catalog service.
- **Pricing**: owned by Pricing service.
- **Availability/ATP and dynamic lead time**: owned by Inventory/Supply Chain service.
- **Frontend location selection UX/state**: owned by POS frontend shell/app (but `positivity` must validate `location_id` and enforce authorization).

### Boundary clarifications from frontend story
- `positivity` is the **single backend endpoint** for the Product Detail screen: the frontend must not stitch multiple downstream calls.
- `positivity` must provide **safe defaults** and **explicit status** so the UI can avoid misrepresenting unknown price/stock.
- `positivity` must treat `location_id` as a required input for location-scoped fields; missing/invalid location must be rejected with `400`.

---

## Key Entities / Concepts

### Order (POS-owned aggregate)
Minimum fields implied by stories:
- `orderId`
- `status` (includes in-flight + terminal cancellation states)
- `workOrderId` (nullable)
- `paymentId` (nullable)
- `cancellationReason` (string)
- `correlationId` (trace/correlation)
- `cancellationIdempotencyKey`

Cancellation-related statuses (examples from story; exact enum TBD):
- In-flight: `CANCEL_REQUESTED` / `CANCEL_PENDING`, `WORKORDER_CANCELLED`, `PAYMENT_REVERSED`
- Terminal success: `CANCELLED`
- Terminal failure: `CANCEL_FAILED_WORKEXEC`, `CANCEL_FAILED_BILLING`, `CANCEL_REQUIRES_MANUAL_REVIEW`

### Cancellation Saga (orchestrated, persisted)
A deterministic workflow driven by `positivity`:
1. Persist “cancel requested” state + idempotency key
2. Cancel work order (if present) via `workexec` (authoritative)
3. Reverse payment (if present) via `billing` (authoritative)
4. Finalize order state and emit events/logs/metrics

### ProductDetailView (read model / API response DTO)
An aggregated view composed from:
- Catalog (master details + static lead-time hints + substitutions)
- Pricing (MSRP + location-scoped store price + currency)
- Inventory (on-hand, ATP, best-effort dynamic lead time)

Frontend story adds concrete expectations for fields and nullability:

**Top-level**
- `productId: string` (required)
- `description: string` (required; may be empty)
- `specifications: Array<{name: string, value: string}>` (required; may be empty)
- `substitutions: Array<{productId: string, reason: string}>` (required; may be empty)
- `generatedAt: string (ISO-8601)` (required)

**pricing**
- `pricing.status: string` (required; expected `OK|UNAVAILABLE|STALE`, exact enum TBD)
- `pricing.asOf: string (ISO-8601)` (optional; “when applicable”)
- `pricing.msrp: number|null` (null when not OK)
- `pricing.storePrice: number|null` (null when not OK)
- `pricing.currency: string|null` (CLARIFY: required when OK? nullable when not OK)

**availability**
- `availability.status: string` (required; expected `OK|UNAVAILABLE|STALE`, exact enum TBD)
- `availability.asOf: string (ISO-8601)` (optional)
- `availability.onHandQuantity: number|null` (null/omitted when not OK)
- `availability.availableToPromiseQuantity: number|null` (null/omitted when not OK)
- `availability.leadTime?: { source?: string, minDays?: number, maxDays?: number, asOf?: string, confidence?: string }`

**Relationship notes**
- `ProductDetailView` is a **DTO/read model**, not a persisted aggregate in `positivity`.
- `locationId` is an **input parameter** that scopes pricing/availability; it is not embedded as authoritative state unless explicitly included in response (optional; consider adding for debuggability).

---

## Invariants / Business Rules

### Cancellation (Order)
- **Authority invariant**: `workexec` is the sole authority for work-order cancellability; `billing` is the sole authority for payment reversal.
- **Ordering invariant**: cancel **work order first**, then reverse payment (minimizes revenue-loss risk).
- **No distributed transactions**: use a persisted saga with retries and explicit failure states.
- **Idempotency**: duplicate cancel requests must not re-run side effects; return current cancellation state.
- **Auditability**: every attempt (success/failure) must be traceable via correlation + idempotency identifiers.

### Product detail aggregation (browse/quote read)
Rules updated with frontend story specifics:

- **Input validation**
  - `productId` must be UUID-shaped; otherwise return `400` (frontend expects to block call, but backend must still validate).
  - `location_id` is required and must be UUID-shaped; otherwise return `400 Bad Request`.
  - TODO: confirm whether `location_id` can be omitted for “catalog-only” view. Frontend story treats it as required.

- **Fail-fast only for missing product**
  - If Catalog says product not found → return `404` and do not synthesize partial response.

- **Graceful degradation with explicit status**
  - Pricing/Inventory failures still return `200` with explicit status metadata and safe defaults:
    - Pricing unavailable/stale → `pricing.status != OK` and **all numeric price fields must be null** (do not emit `0`).
    - Inventory unavailable/stale → `availability.status != OK` and **quantity fields must be null/omitted** (do not emit `0`).
  - UI must never infer “in stock/out of stock” when `availability.status != OK`.

- **Lead time precedence**
  - Dynamic lead time from Inventory overrides Catalog static hint when available.
  - UI renders what it receives; precedence is a backend responsibility.

- **Staleness semantics**
  - `STALE` must be treated as “not OK” for UI gating (frontend story disables “known price/stock” claims when status != OK).
  - TODO: define staleness thresholds and when to emit `STALE` vs `UNAVAILABLE`.

- **Caching**
  - Short TTLs with staleness metadata (aggregated default 15s; component TTLs per story).
  - CLARIFY: confirm whether `generatedAt` should reflect cache time or composition time.

---

## Events / Integrations

### Synchronous integrations (explicitly referenced)
- `workexec`
  - Advisory pre-check: `GET /api/v1/work-orders/{workOrderId}` → includes `status`, `cancellable`, optional `nonCancellableReason`, `updatedAt/version`
  - Authoritative command: `POST /api/v1/work-orders/{workOrderId}/cancel` with `orderId`, `requestedBy`, `reasonCode`, `idempotencyKey`
- `billing`
  - `POST /api/v1/payments/{paymentId}/reverse` with `orderId`, `reasonCode`, `currency`, `idempotencyKey`, and intent `type: VOID|REFUND` (billing decides final)
- Catalog / Pricing / Inventory services
  - Endpoints are not specified here beyond the composed POS endpoint; treat downstream contracts as **TBD** except for the required fields described in Issue #80.

### Integration patterns for ProductDetailView
- **Fan-out + compose** (recommended):
  - Fetch Catalog first (or in parallel) to determine existence.
  - Fetch Pricing and Inventory in parallel with timeouts and circuit breakers.
  - Compose response with per-component status and timestamps.
- **Timeout and partial failure behavior**
  - If Pricing/Inventory times out or errors: set component `status=UNAVAILABLE` (or `STALE` if serving cached component data) and return `200`.
  - Do not block the entire response unless Catalog is missing or request is invalid/unauthorized.

### Domain events (asynchronous)
`positivity` emits “canonical events for cancellation progress/final state” for audit/reporting/ops visibility. Event names, schemas, and transport are **TBD**, but must include:
- `orderId`, `workOrderId` (if any), `paymentId` (if any)
- `correlationId`, `idempotencyKey`
- state transition + failure category (if any)
- timestamps

TODO: decide whether to emit read-side events for ProductDetailView degradation (generally avoid high-cardinality events; prefer metrics).

---

## API Expectations (high-level)

### Product details (read aggregation)
- `GET /api/v1/products/{productId}?location_id={locationId}`
  - `200 OK` with aggregated `ProductDetailView` and per-component status metadata
  - `404 Not Found` if product missing in Catalog
  - `400 Bad Request` for invalid/missing `location_id` (frontend expects “Invalid location selected”)
  - `401/403` when user lacks permission (frontend expects “You don’t have access” and no partial data)
  - Must include `generatedAt` and component `asOf` timestamps when applicable
  - Must not silently null-out pricing/availability without a corresponding status

**Contract details to enforce (backend)**
- When `pricing.status != OK`:
  - `msrp=null`, `storePrice=null`
  - `currency` should be `null` unless there is a strong reason to keep it (CLARIFY).
- When `availability.status != OK`:
  - `onHandQuantity=null` and `availableToPromiseQuantity=null` (or omit consistently; pick one and document it).
- `substitutions` must always be present (empty array if none) to simplify UI.

**Concurrency / “latest wins”**
- Frontend will issue rapid requests on location changes; backend should be safe under concurrency and avoid server-side caching keyed incorrectly (must include `location_id` in cache key).

### Order cancellation (command)
- Cancel endpoint path/shape is **TBD** (not provided in export).
- Behavior must match the cancellation saga described in Issue #19:
  - Requires `orderId` and `cancellationReason` (and maps to downstream `reasonCode`)
  - Persists in-flight state before calling downstream services
  - Uses idempotency key and returns deterministic state on duplicates
  - Stops if `workexec` rejects (e.g., `409 Conflict`) and does not attempt billing reversal

---

## Security / Authorization assumptions

### Authentication
- Requests are authenticated (session/JWT/etc per platform convention).
- For ProductDetailView, do not return partial data on `401/403`. Return the appropriate status code.

### Authorization
- Product detail read requires permission to read:
  - product master data
  - location-scoped pricing
  - location-scoped availability
- CLARIFY (blocking): exact permission/role gate and naming convention in this repo.
  - TODO: document required permission string(s) once confirmed (e.g., `POS_CATALOG_READ`, `POS_LOCATION_READ`, etc.).

### Location scoping
- Backend must enforce that the caller is authorized for the requested `location_id`.
  - Do not rely on frontend “selected location” correctness.
  - Return `403` if user is authenticated but not allowed for that location.

### Data handling
- Do not log sensitive payment details; only log identifiers (`paymentId`) and correlation metadata.
- For ProductDetailView, avoid logging full descriptions/specifications if they can be large; log identifiers and statuses.

---

## Observability (logs / metrics / tracing)

### Tracing
- Generate/propagate `correlationId` across:
  - inbound request → `positivity` → `workexec` / `billing` / Catalog / Pricing / Inventory
- Ensure correlation is present in logs and outbound headers (exact header keys TBD; use standard trace propagation where available).
- CLARIFY: frontend trace header propagation standard (if any). If none, backend should still generate a correlation id and return it in response headers for support.

### Logs (structured)
For cancellation:
- Include: `orderId`, `workOrderId`, `paymentId`, `correlationId`, `idempotencyKey`, current `orderStatus`, failure category, downstream HTTP status (if applicable).

For product detail:
- Include: `productId`, `locationId`, `generatedAt`
- Component statuses: `pricing.status`, `availability.status`
- Dependency latency summaries and error categories (timeout vs 5xx vs 4xx)
- Do **not** log numeric prices/quantities unless needed for debugging and approved (prefer status-only logging).

### Metrics (minimum implied)
Cancellation:
- `cancellations.success.count`
- `cancellations.failure.count` tagged by `reason` (`workexec_denial`, `billing_error`, `timeout`, `manual_review`)

Product detail:
- `product_detail.requests.count` tagged by `http_status`
- `product_detail.degraded.count` tagged by `component=pricing|availability` and `status=UNAVAILABLE|STALE`
- Per-dependency latency histograms:
  - `catalog.latency.ms`, `pricing.latency.ms`, `inventory.latency.ms`
- Error counters per dependency:
  - `pricing.errors.count` tagged by `type=timeout|5xx|4xx`
  - `inventory.errors.count` tagged by `type=timeout|5xx|4xx`

---

## Testing guidance

### Unit tests
Cancellation state machine:
- Workexec rejection (`409`) → no billing call; terminal failure state set.
- Workexec success + billing failure → `CANCEL_FAILED_BILLING` (or manual review) and retry eligibility.
- Duplicate cancel request → no repeated side effects; returns persisted state.

Product detail aggregation:
- Catalog not found → `404`
- Missing/invalid `location_id` → `400`
- Unauthorized location access → `403`
- Pricing unavailable → `200` with `pricing.status=UNAVAILABLE` and `msrp/storePrice=null`
- Inventory unavailable → `200` with `availability.status=UNAVAILABLE` and quantities null/omitted
- Lead time precedence: inventory dynamic overrides catalog static
- `substitutions` always present (empty array when none)

### Integration tests (contract + resilience)
- Stub downstream services to validate:
  - Idempotency key propagation to `workexec` and `billing`
  - Correct ordering: `workexec` cancel before `billing` reverse
  - Timeout handling results in persisted failure state and safe retry behavior

For product detail:
- Partial outage scenarios return `200` with explicit status metadata (not silent nulls)
- Cache key includes `location_id` (no cross-location leakage)
- “STALE” behavior if serving cached component data (TODO once staleness policy is defined)

### Frontend-facing contract tests (recommended)
- Snapshot/JSON-schema tests for `ProductDetailView`:
  - Ensure nullability rules match UI expectations (null != 0)
  - Ensure required arrays are present (specifications/substitutions)
- Backward/forward compatibility:
  - UI should treat `pricing.status`/`availability.status` as opaque strings and only special-case `OK` vs not OK (CLARIFY; see Open Questions).

### Data / persistence tests
- Verify cancellation saga progress is persisted before side effects (crash/restart safety).
- Verify state transitions are monotonic and deterministic (no “backwards” transitions).

---

## Common pitfalls
- **Reversing payment before work cancellation**: violates ordering invariant and can create revenue-loss/manual-reconciliation scenarios.
- **Missing idempotency**: retries or duplicate user actions can double-cancel work orders or double-reverse payments.
- **Silent degradation** in product detail: returning `null` pricing/availability without `status` leads to unsafe downstream actions.
- **Rendering null as 0**: UI explicitly must not show numeric values when status != OK; backend must not send `0` as a placeholder.
- **Treating inventory-unavailable as “0”**: must be represented as UNKNOWN, not out-of-stock or in-stock.
- **Cache key bugs**: caching ProductDetailView without `location_id` in the key can leak pricing/availability across locations.
- **Not persisting in-flight state** before calling downstream services: makes recovery and retries non-deterministic.
- **Insufficient correlation**: lack of `correlationId`/`idempotencyKey` in logs/events breaks auditability and support workflows.
- **Overly strict enum parsing**: frontend story indicates enum values may be TBD; treat status as string and gate on `== "OK"` unless contract is finalized.

---

## Open Questions from Frontend Stories
Consolidated from Issue #80 (blocking unless noted):

1. **Location source of truth (blocking):** How does the frontend obtain the selected `locationId` (global app state, route query, user profile default, or a screen-level selector)? What is the existing convention in this repo?
   - Impacts: API call construction, cache keying, and authorization checks.

2. **Moqui ↔ Vue integration pattern (blocking):** Should this be implemented as a Moqui screen that hosts a Vue route/component, or as a Vue SPA route calling backend directly (with Moqui providing shell/auth)? Repo convention needed.
   - Impacts: routing, auth/session propagation, and where to implement retry/latest-wins behavior.

3. **Auth & permission enforcement (blocking):** What permission/role gate should the screen enforce for product/location reads (and what is the existing permission naming pattern)?
   - Impacts: backend authorization checks and frontend route guards.

4. **Status enum exact values:** Backend suggests `OK|UNAVAILABLE|STALE` but says exact enum TBD—should frontend treat status as opaque string and only special-case `OK` vs “not OK”?
   - Recommendation (until clarified): treat as opaque string; only `status === "OK"` enables numeric display/“known” claims.

5. **Currency formatting rules:** Are there existing utilities/components for money formatting and null-safe display in this project?
   - Impacts: UI correctness and consistency; backend should ensure `currency` presence rules are documented.

6. **Timestamp display:** Should the UI display `generatedAt`/`asOf` visibly, or only for debug/details? If visible, where is the standard placement?
   - Backend must provide timestamps consistently; UI placement is a product decision.

---

**TBDs to resolve in implementation**
- Exact Order cancel REST endpoint(s) and response schema
- Event names/schemas/transport for cancellation progress/finalization
- Standard headers for correlation/trace propagation (if not already standardized across the platform)
- Exact enum values for component `status` (`OK|UNAVAILABLE|STALE`) and cancellation statuses (beyond examples)
- CLARIFY: `pricing.currency` nullability rules (required when `pricing.status=OK`?)
- TODO: staleness thresholds and when to emit `STALE` vs `UNAVAILABLE`
- CLARIFY: whether `location_id` is strictly required for ProductDetailView or if a catalog-only mode exists
```
