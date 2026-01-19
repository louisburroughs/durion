# AGENT_GUIDE.md — Order Domain

---

## Purpose

The Order domain manages lifecycle and state transitions of POS orders, with a focus on orchestrating order cancellation. It enforces cancellation policies, coordinates with Payment and Work Execution domains, and ensures auditability and operational correctness in cancellation workflows.

This guide is updated to reflect new Moqui + Quasar frontend stories that surface cancellation from the Order Detail UI, require reason/comments capture, and require deterministic UI rendering of cancellation outcomes and audit details.

---

## Domain Boundaries

- **Primary Authority:** POS Order domain owns:
  - Order lifecycle state machine (including cancellation-related states)
  - Cancellation policy decisions (cancellable vs blocked)
  - Orchestration of downstream actions (work rollback/cancel, payment void/refund requirement)
  - Audit record creation for every cancellation attempt

- **Downstream Dependencies (authoritative sources):**
  - **Payment/Billing System:** authoritative on payment state and whether a void is possible vs refund required.
  - **Work Execution System:** authoritative on work status and whether work can be rolled back/cancelled.

- **Frontend (Moqui/Quasar) responsibilities (experience layer):**
  - Permission-gated “Cancel Order” action in Order Detail context
  - Collect cancellation inputs (`reason` required, `comments` optional <= 2000 chars)
  - Call the Order domain cancellation operation via Moqui transition/service
  - Render cancellation outcomes and audit metadata returned by backend
  - Handle concurrency (`409`) and in-flight states (`CANCELLING` or equivalent)
  - Avoid leaking sensitive internals in UI and logs

- **Out of Scope (for this domain guide):**
  - Accounting/GL impacts and refund processing workflow UI
  - Operator/admin dashboards for resolving failed cancellations (except displaying current state and audit details)
  - Implementing payment void/refund logic itself (owned by Payment/Billing)

**CLARIFY:** Some frontend issue labels mention `domain:payment` / “domain: Point of Sale”. Backend reference stories place cancellation orchestration under `domain:order` (and possibly `domain:positivity`). Confirm canonical domain label and routing for this repo.

---

## Key Entities / Concepts

| Entity | Description |
|---|---|
| **Order** | Core POS order with status and identifiers needed for downstream coordination. Must expose cancellation-related status to UI. |
| **CancellationRecord** | Immutable audit record for each cancellation attempt. UI stories require displaying at least latest attempt details. |
| **WorkOrder** (external) | Work execution representation linked to the order; used to determine cancellability and rollback outcome. |
| **PaymentTransaction** (external) | Payment representation linked to the order; used to determine void vs refund requirement and void outcome. |
| **CancelOrderRequest** | Input DTO: `orderId`, `reason` (required), `comments` (optional, max 2000). |
| **CancelOrderResponse** | Output DTO: `orderId`, `status`, `message`, `cancellationId`, `paymentVoidStatus`, `workRollbackStatus` (+ optional correlation id). |

### Relationships (practical)
- `Order (1) -> (0..n) CancellationRecord`
  - UI typically needs **latest** cancellation record; optionally a history list if backend provides it.
- `Order (0..1) -> WorkOrder` (via `workOrderId` or equivalent)
- `Order (0..1) -> PaymentTransaction` (via `paymentId` or equivalent)

**TODO:** Confirm actual Moqui entity names/fields and whether cancellation summary is embedded in Order detail response or fetched separately.

---

## Invariants / Business Rules

- **BR-1:** POS Order domain is the orchestrator and authoritative owner of cancellation policy and order state transitions.
- **BR-2:** Cancellation is blocked if work status is in blocking list:  
  `IN_PROGRESS`, `LABOR_STARTED`, `PARTS_ISSUED`, `MATERIALS_CONSUMED`, `COMPLETED`, `CLOSED`.  
  - Backend must enforce; UI may optionally pre-check but must not rely on it.
- **BR-3:** Cancellation allowed regardless of payment settlement state:
  - If payment is authorized/captured but not settled: attempt void.
  - If payment is settled: transition to `CANCELLED_REQUIRES_REFUND` and require manual refund (no refund UI in scope).
- **BR-4:** On downstream failure (payment void or work rollback), transition to `CANCELLATION_FAILED` and require manual intervention.
- **BR-5:** All cancellation attempts must be fully audited with user, timestamp, reason, downstream results, and correlation IDs.
- **BR-6 (UI-driven constraint, must be validated server-side too):**
  - `reason` is required.
  - `comments` length must be `<= 2000` characters.
- **BR-7 (Concurrency / idempotency):**
  - If cancellation is already in progress, return a conflict (`409`) and do not start a second orchestration.
  - If already cancelled, operation must be idempotent (return current state and latest cancellation metadata).

**CLARIFY:** Definitive order status enum(s) exposed to frontend: `CANCELLING` vs `CANCEL_REQUESTED`/`CANCEL_PENDING`, and failure variants (`CANCEL_FAILED_*` vs `CANCELLATION_FAILED`).

---

## Events / Integrations

| Event Name | Emitted When | Payload Highlights |
|---|---|---|
| `OrderCancelled` | Successful cancellation with void/refund decision | `orderId`, `cancellationReason`, `cancelledBy`, `paymentVoidStatus`, `workRollbackStatus`, correlation IDs |
| `OrderCancelledRequiresRefund` | Cancellation when payment settled | `orderId`, payment details (non-sensitive), cancellation reason, correlation IDs |
| `OrderCancellationFailed` | Downstream failure during cancellation | `orderId`, failed subsystems, correlation IDs, retry eligibility |

### Integration Patterns

- **Synchronous orchestration call** from UI → Moqui → Order domain cancel operation.
  - UI expects deterministic HTTP-like outcomes: `200/400/403/409/500`.
- **Downstream calls** (server-side only):
  - Work Execution: query status + attempt rollback/cancel if allowed.
  - Payment/Billing: query payment state + attempt void if possible; otherwise mark refund required.

**TODO:** Replace placeholder endpoints with actual Moqui service names and/or REST endpoints once confirmed.

---

## API Expectations (High-Level)

### Cancel Order Operation (contract required by frontend)

- **Input**
  - `orderId` (required)
  - `reason` (required; enum or string)
  - `comments` (optional; max 2000 chars)

- **Validations**
  - Order exists and is visible to the user/tenant context
  - User permission `ORDER_CANCEL`
  - Order is in cancellable state (not `COMPLETED`, `CLOSED`, already `CANCELLED`, etc.)
  - Work status not in blocking list
  - Input validation: reason present; comments length

- **Behavior**
  - Transition order to in-flight cancellation state (if modeled)
  - Orchestrate downstream work rollback and payment void/refund requirement
  - Persist `CancellationRecord` with downstream outcomes and correlation IDs
  - Return a response suitable for UI rendering (status + message + metadata)

- **Responses (UI mapping)**
  - `200 OK`: cancellation accepted/completed; status is one of:
    - `CANCELLED`
    - `CANCELLED_REQUIRES_REFUND`
    - (optionally) `CANCELLING` if async completion is used
  - `400 Bad Request`: validation failures, terminal state, or work-blocking status
  - `403 Forbidden`: missing permission
  - `409 Conflict`: cancellation already in progress
  - `500 Internal Server Error`: downstream failure requiring manual intervention (order should end in `CANCELLATION_FAILED`)

- **Idempotency**
  - Repeated cancel requests for an already-cancelled order should return current state and latest cancellation metadata (no duplicate side effects).
  - Repeated requests during in-flight cancellation should return `409` (or a stable “in progress” response) consistently.

**CLARIFY:** Error response schema. Frontend stories ask whether there is a stable `errorCode` vs only `message`. Define and document a stable error contract if possible.

---

## Security / Authorization Assumptions

- Users must have `ORDER_CANCEL` permission to initiate cancellation.
- Authorization checks occur **before** any state changes or downstream calls.
- UI must gate the Cancel action, but server-side enforcement is mandatory.
- Audit trails record user identity and actions for accountability.

### Frontend-specific security requirements (from stories)
- Do not display or log sensitive payment data (PAN, tokens, full processor responses).
- If correlation IDs are exposed to UI, ensure they are safe to share with support and do not embed secrets.

**CLARIFY:** How the frontend determines `ORDER_CANCEL` capability:
- via permissions API,
- session context,
- Moqui screen conditions,
- or another standard pattern in this repo.

---

## Observability (Logs / Metrics / Tracing)

### Logs (server-side)
Log at **INFO** for each cancellation attempt:
- `orderId`, `cancellationId` (once created), `userId`/actor, `reason`
- state transitions (from → to)
- downstream call outcomes (work rollback status, payment void status)
- correlation IDs for downstream calls

Log at **WARN/ERROR**:
- downstream failures leading to `CANCELLATION_FAILED`
- repeated conflicts (`409`) above a threshold (could indicate stuck state)

### Metrics
- `order_cancellation_requests_total{reason}`
- `order_cancellation_success_total`
- `order_cancellation_requires_refund_total`
- `order_cancellation_failed_total{failedSubsystem}`
- `order_cancellation_conflict_total` (409s)
- `order_cancellation_latency_ms` (end-to-end orchestration time)
- Gauge: `orders_in_cancellation_failed` (alert if above threshold or aging)

### Tracing
- Propagate a correlation/trace ID through:
  - UI request → Moqui → Order domain → downstream calls
- Include `orderId` and `cancellationId` as trace attributes (avoid PII).

### Frontend observability (Moqui/Quasar)
- UI should not log sensitive details.
- If the platform has client telemetry, include:
  - `orderId`, `cancellationId` (if returned), and correlation id (if safe)
  - outcome status (`CANCELLED`, `CANCELLED_REQUIRES_REFUND`, `CANCELLATION_FAILED`, `CONFLICT`, `FORBIDDEN`)

**CLARIFY:** Whether correlation IDs should be displayed in UI (copyable) or restricted to logs/admin views.

---

## Testing Guidance

### Backend/domain tests
- Unit tests for cancellation policy:
  - blocking work statuses
  - terminal order states
  - payment settlement scenarios (void vs refund required)
  - comments length and reason required
- Integration tests with mocked downstream systems:
  - Work Execution: blocking vs non-blocking; rollback success/failure
  - Payment: authorized/captured/settled; void success/failure
- Concurrency tests:
  - two cancel requests in parallel → one succeeds, one returns `409`
- Idempotency tests:
  - cancel twice after success → no duplicate downstream calls; stable response
- Failure-mode tests:
  - downstream timeout/error → `CANCELLATION_FAILED` + audit record + event emission

### Frontend tests (Moqui/Quasar)
- Component tests for Cancel dialog:
  - reason required
  - comments max length 2000
  - submit disabled while in-flight
- Contract tests (mock Moqui service responses):
  - `200` → render status + cancellation metadata
  - `400` → show safe error message; keep dialog open
  - `403` → hide action + show permission error on forced attempt
  - `409` → show conflict message; refresh order state
  - `500` → show “manual intervention required”; render `CANCELLATION_FAILED` after refresh
- Snapshot/visual tests for cancellation summary panel across states.

**TODO:** Add explicit fixtures for status enum variants once the definitive enum set is confirmed.

---

## Common Pitfalls

- **Ignoring work status blocking rules:** Cancelling orders with irreversible work started leads to inconsistent states.
- **Treating cancellation as financial reversal:** Cancellation is a logical state; settled payments require manual refund.
- **Leaking internal error details to UI:** Backend `400/500` messages must be user-safe; otherwise return stable `errorCode` and map to safe UI text.
- **Not handling concurrency:** Double-clicks and parallel requests must be handled via server-side locking/idempotency and UI disabling.
- **Not handling idempotency:** Duplicate cancellation requests must not duplicate downstream side effects.
- **Status enum drift:** UI stories explicitly call out mismatch risk (`CANCELLING` vs `CANCEL_PENDING`, etc.). Publish a single canonical enum contract.
- **Missing audit metadata in Order detail:** UI requires cancellation summary (id, who/when, reason/comments, downstream statuses). Ensure Order detail includes it or provide a dedicated endpoint/service.
- **Correlation ID confusion:** If correlation IDs are exposed, ensure they are consistent (header vs body) and safe to share.

---

## Open Questions from Frontend Stories

### Domain ownership / labeling
1. **Domain label mismatch:** Frontend issue text says **Domain: payment** and “domain: Point of Sale”, but backend reference stories assert cancellation orchestration under **domain:order** (and/or `domain:positivity`). Confirm the frontend repo’s canonical domain label for this story: `domain:order` vs `domain:positivity`. (Blocking: label + routing.)

### Moqui UI routing and integration contracts
2. **Order Detail route/screen:** What is the exact Moqui screen route/screen name for Order Detail in this repo (e.g., `/apps/pos/order/detail?orderId=...`)?
3. **Moqui integration contract:** What are the actual Moqui service names/endpoints to call for:
   - loading order detail (including cancellation summary),
   - submitting cancellation,
   - retrieving cancellation record (if separate)?
   Provide names, parameters, and response mappings.
4. **Frontend-to-backend cancellation contract:** Confirm:
   - endpoint/path or Moqui service name
   - request/response JSON fields (as per story §8)
   - error response schema (stable `errorCode` vs only `message`)

### Status enums and UI rendering
5. **Order status enum:** Which exact status values will the frontend receive (`CANCELLING` vs `CANCEL_REQUESTED`/`CANCEL_PENDING`, `CANCEL_FAILED_*` vs `CANCELLATION_FAILED`)? Provide the definitive enum set to render.

### Authorization / roles
6. **Permission exposure:** How does the frontend determine `ORDER_CANCEL` capability—via a permissions API, session context, or screen conditions in Moqui? Provide the standard pattern used in this repo.
7. **Roles allowed:** Which roles besides Store Manager can cancel orders? Backend reference includes Service Advisor—confirm if UI should allow this when permission `ORDER_CANCEL` is present.

### UX behavior decisions
8. **Work/payment advisory pre-check:** Should the frontend call any pre-check endpoints (e.g., show “work not cancellable” before submit), or rely solely on submit response for blocking?
9. **Retry/admin re-trigger UI:** For `CANCELLATION_FAILED`, should the frontend present a “Retry cancellation” button (admin only) or only display status? Is retry handled elsewhere (operator/admin dashboard)?
10. **Cancellation reason source:** Is `reason` a fixed enum list (e.g., `CUSTOMER_REQUEST|INVENTORY_UNAVAILABLE|PRICING_ERROR|DUPLICATE_ORDER|OTHER`) that UI can hardcode, or should it be loaded from backend/config?
11. **Correlation IDs visibility:** Should the UI display correlation IDs and downstream subsystem details, or are those restricted to logs/admin views?

---

*End of AGENT_GUIDE.md*
