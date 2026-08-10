# ADR-0052: Supplier Outbound Idempotency and Duplicate-Order Prevention

**Status:** ACCEPTED 2026-08-10 — revised for PRCR-001/002/008  
**Date:** 2026-08-10  
**Deciders:** Architecture, Backend Lead, Positivity (Integrations) Domain, Order Domain  
**Affected Issues:** durion#375 (CAP-320), durion-positivity-backend#1226

---

## Context

- **Current State**: CAP-320 transmits purchase orders to vendors over EDIWheel order creation. Vendor order APIs are not idempotent by default; a timeout after send is
  ambiguous (the vendor may or may not have accepted the order).
- **The Problem**: A naive retry on an ambiguous outcome places a duplicate physical order — trucks deliver tires twice. This is the single largest operational risk of
  supplier EDI.
- **Drivers**: At-least-once delivery semantics of the event backbone (ADR-0044 §4); retry behavior of the shared base client; the EDIWheel `DocumentID`/`CustomerReference`
  mechanism vendors use for deduplication; the fact that one purchase order can legitimately produce multiple transmissions (splits, revisions, replacements, vendor
  switches); vendors may be configured with `ORDER_CREATE` but no `ORDER_STATUS` binding.
- **Scope**: All outbound supplier exchanges, with the strictest rules on order creation. Applies to `pos-supplier` orchestration; consuming domains inherit the guarantees via
  events.

---

## Decision

### 1. Transmission intent as the idempotency identity

**Decision:** ✅ **Resolved** — Every exchange that creates vendor-side state is anchored by an immutable **`transmissionIntentId`** (UUIDv7, ADR-0013), minted when the intent
is accepted. The purchase-order UUID is **not** a sufficient identity: one purchase order legitimately produces multiple intents (split shipments, revisions, replacements
after `mark-not-received`, vendor switches), so:

- The wire `DocumentID`/`CustomerReference` derives deterministically from `transmissionIntentId` — never regenerated on retry of the same intent, always new for a new
  intent.
- Uniqueness is enforced across `(vendorProfileId, intentType, purchaseOrderId, revision)`: at most one **active** intent per tuple; a replacement or revision requires a new
  intent (and therefore a new `DocumentID`).
- The intent record carries its originating aggregate (`purchaseOrderId`), intent type (`INITIAL`, `REVISION`, `REPLACEMENT`, `SPLIT`), and revision number, all immutable
  after creation.

### 2. Transactional outbox with persisted attempt state

**Decision:** ✅ **Resolved** — Consuming the `supplier.order.requested` command (on `supplier.commands.v1`, ADR-0049 §3) writes the transmission intent and its outbox row in one transaction (exactly-once-intent). Dispatch
follows a persisted attempt-state machine whose transitions are committed **before** network I/O:

- `PENDING` — intent recorded, no send attempted. Safe to dispatch.
- `DISPATCHING` — persisted immediately **before** the request body is sent. From this state, automatic resend is forbidden.
- `SENT_AWAITING_RESULT` / terminal states — persisted as responses arrive; result events (`confirmed`/`rejected`) apply idempotently.

Crash-recovery rule: on restart, `PENDING` rows re-dispatch normally; **`DISPATCHING` rows never re-dispatch automatically** — they enter status reconciliation (§3), or go
directly to `MANUAL_REVIEW` when no status lookup is available (§4). This closes the window where a crash after body-send but before response-persist could be mistaken for a
never-sent order.

### 3. Ambiguous outcomes reconcile via status, never blind retry

**Decision:** ✅ **Resolved** — When an order-create call times out or fails after the request may have reached the vendor (including any `DISPATCHING` row found at restart),
the orchestrator MUST NOT re-send the create. It queries the vendor's order-status service by document ID and reconciles:

- Vendor knows the order → apply confirmed/rejected result; no resend.
- Vendor does not know the order **and** the failure demonstrably preceded transmission (connection refused before send, breaker open — i.e. the attempt never left `PENDING`)
  → safe re-dispatch from outbox with the same document ID.
- Vendor does not know the order after a confirmed-send window → transmission enters `MANUAL_REVIEW`. **No automatic resend exists for this state**, and no frontend surface
  offers a blind re-send.

### 4. Operating without ORDER_STATUS

**Decision:** ✅ **Resolved** — An `ORDER_CREATE` binding is permitted without an `ORDER_STATUS` binding. In that configuration, **every post-send ambiguous outcome enters
`MANUAL_REVIEW` immediately** — there is no reconciliation path, so no automatic disposition of ambiguity is allowed at all. Manual resolution actions are defined,
permission-gated (`supplier:transmission:resolve`, deny-by-default per ADR-0040), and audited with actor and free-text evidence:

- `confirm-with-vendor-reference` — operator confirms the vendor accepted the order, recording the vendor order number as evidence; transmission becomes `CONFIRMED`.
- `mark-not-received` — operator confirms the vendor has no such order; the intent terminates as `FAILED`. Re-ordering requires a **new transmission intent** (new
  `DocumentID`, §1) — the failed intent is never re-dispatched.
- `cancel` — operator abandons the transmission; the intent terminates as `CANCELLED` and pos-order is informed via the result event.

Every manual resolution emits an audited event consumed by pos-order for the purchase-order timeline.

### 5. Retry classification

**Decision:** ✅ **Resolved** — The base client classifies failures: _pre-send_ (DNS, connect, breaker-open; attempt still `PENDING`) → retryable automatically; _post-send
ambiguous_ (read timeout, 5xx after body sent; attempt reached `DISPATCHING`) → status-first reconciliation per §3 or `MANUAL_REVIEW` per §4; _definitive rejection_ (4xx with
vendor error payload) → no retry, surface the rejection. Batch reads (PRICAT, stock report, invoice fetch) are idempotent by checkpointed window and may retry freely;
consumers deduplicate by natural identity (e.g. one AP voucher per vendor invoice identity).

### 6. Test obligations

**Decision:** ✅ **Resolved** — CAP-320 (and any future capability that creates vendor-side state) MUST ship crash/retry integration tests proving: same document ID across
retries of one intent; distinct document IDs across distinct intents for the same purchase order; no duplicate transmission after crash between intent and dispatch; **crash
after body transmission → restart reconciles (or enters `MANUAL_REVIEW`), never resends**; **crash after response receipt but before persistence → restart reconciles to the
vendor's recorded state without resending**; status-first reconciliation on simulated ambiguous timeout; immediate `MANUAL_REVIEW` when no `ORDER_STATUS` binding exists.
These tests are acceptance criteria, not optional hardening.

---

## Consequences

**Positive:** duplicate physical orders are prevented on our side by intent-scoped document IDs and the `DISPATCHING` no-resend rule, and bounded on the vendor side by
status-first reconciliation; ambiguity resolves from the vendor's authoritative state where a status service exists; humans only see truly unresolvable cases.
**Negative / accepted:** order transmission is eventually consistent (queued during vendor outages); `MANUAL_REVIEW` requires an operational owner — more so for vendors
without `ORDER_STATUS`; status polling adds vendor API traffic.
**Honest limits:** vendor-side deduplication by `DocumentID` is not universally guaranteed by the EDIWheel norms — each vendor's behavior (Michelin first) MUST be confirmed
in sandbox, and the guarantee claimed here is "no duplicate send from Durion for one intent," not "the vendor cannot double-book."

## References

- `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md` §9.1
- ADR-0044 §4 (outbox, idempotent consumers), ADR-0049, ADR-0013 (UUIDv7)
