# ADR-0052: Supplier Outbound Idempotency and Duplicate-Order Prevention

**Status:** PROPOSED **Date:** 2026-08-10 **Deciders:** Architecture, Backend Lead, Positivity (Integrations) Domain, Order Domain **Affected Issues:** durion#375 (CAP-320),
durion-positivity-backend#1226

---

## Context

- **Current State**: CAP-320 transmits purchase orders to vendors over EDIWheel order creation. Vendor order APIs are not idempotent by default; a timeout after send is
  ambiguous (the vendor may or may not have accepted the order).
- **The Problem**: A naive retry on an ambiguous outcome places a duplicate physical order — trucks deliver tires twice. This is the single largest operational risk of
  supplier EDI.
- **Drivers**: At-least-once delivery semantics of the event backbone (ADR-0044 §4); retry behavior of the shared base client; the EDIWheel `DocumentID`/`CustomerReference`
  mechanism vendors use for deduplication.
- **Scope**: All outbound supplier exchanges, with the strictest rules on order creation. Applies to `pos-supplier` orchestration; consuming domains inherit the guarantees via
  events.

---

## Decision

### 1. Deterministic client references

**Decision:** ✅ **Resolved** — Every outbound exchange that creates vendor-side state carries a client reference derived **deterministically from the canonical entity UUID**
(e.g. purchase-order UUID → `DocumentID`/`CustomerReference`). Retries of the same intent always present the same reference, making vendor-side deduplication possible.
References are never regenerated on retry.

### 2. Transactional outbox for order transmission

**Decision:** ✅ **Resolved** — Consuming `supplier.order.requested.v1` writes the transmission intent and its outbox row in one transaction (exactly-once-intent). A
background dispatcher performs the network send; crash/restart re-dispatches from the outbox with the same document ID. Result events (`confirmed`/`rejected`) are applied
idempotently against the transmission state.

### 3. Ambiguous outcomes reconcile via status, never blind retry

**Decision:** ✅ **Resolved** — When an order-create call times out or fails after the request may have reached the vendor, the orchestrator MUST NOT re-send the create. It
queries the vendor's order-status service by document ID and reconciles:

- Vendor knows the order → apply confirmed/rejected result; no resend.
- Vendor does not know the order **and** the failure clearly preceded transmission (connection refused before send, breaker open) → safe re-dispatch from outbox with the same
  document ID.
- Vendor does not know the order after a confirmed-send window → transmission enters `MANUAL_REVIEW`; resolution actions are human-triggered. **No automatic resend exists for
  this state**, and no frontend surface offers a blind re-send.

### 4. Retry classification

**Decision:** ✅ **Resolved** — The base client classifies failures: _pre-send_ (DNS, connect, breaker-open) → retryable automatically; _post-send ambiguous_ (read timeout,
5xx after body sent) → status-first reconciliation per §3; _definitive rejection_ (4xx with vendor error payload) → no retry, surface the rejection. Batch reads (PRICAT, stock
report, invoice fetch) are idempotent by checkpointed window and may retry freely; consumers deduplicate by natural identity (e.g. one AP voucher per vendor invoice identity).

### 5. Test obligations

**Decision:** ✅ **Resolved** — CAP-320 (and any future capability that creates vendor-side state) MUST ship crash/retry integration tests proving: same document ID across
retries; no duplicate transmission after crash between intent and dispatch; status-first reconciliation on simulated ambiguous timeout; `MANUAL_REVIEW` on
vendor-unknown-after-send. These tests are acceptance criteria, not optional hardening.

---

## Consequences

**Positive:** duplicate physical orders are structurally prevented rather than operationally hoped against; ambiguity resolves from the vendor's authoritative state; humans
only see truly unresolvable cases. **Negative / accepted:** order transmission is eventually consistent (queued during vendor outages); `MANUAL_REVIEW` requires an operational
owner; status polling adds vendor API traffic.

## References

- `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md` §9.1
- ADR-0044 §4 (outbox, idempotent consumers), ADR-0049
