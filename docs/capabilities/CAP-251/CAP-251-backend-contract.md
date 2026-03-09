---
title: CAP-251 Backend Contract — Invoice Payment Status Sync
capability_id: CAP-251
domain: accounting
openapi_source: durion-positivity-backend/pos-accounting/openapi.yaml
openapi_version: v1
owner_repo: louisburroughs/durion-positivity-backend
last_updated: 2026-03-08
---

# CAP-251 Backend Contract (Implementation Handoff)

Summary

- Implement coordination between POS payment outcomes and Accounting invoice status.
- Source of truth for endpoint shapes: `durion-positivity-backend/pos-accounting/openapi.yaml` (authoritative).

Backend child issues

- <https://github.com/louisburroughs/durion-positivity-backend/issues/5>
- <https://github.com/louisburroughs/durion-positivity-backend/issues/6>

Per-story handoff payloads

- Story #6 (Invoice Payment Status from Payment Outcomes): Backend issue: #6
  - Primary endpoints (gateway URLs):
    - `applyPayment` (POST) — `http://localhost:8080/v1/accounting/payments/{paymentId}/applications`
    - `getInvoiceStatus` (GET) — `http://localhost:8080/v1/accounting/invoice/{invoiceId}/status`
  - Purpose: transition invoice state based on payment application outcome (Paid / PartiallyPaid / no-op for duplicate transactionId).
  - Behavioral assertions (derive from story):
    - Idempotency: dedupe on `transactionId` first; fallback to `applicationRequestId`/idempotency key when provided. Duplicate `transactionId` must be strict no-op.
    - Full payment: result -> `invoiceStatus=Paid`, `outstandingAmountMinor=0`, emit `InvoicePaymentRecorded` and create posting intent in outbox.
    - Partial payment: result -> `invoiceStatus=PartiallyPaid`; `paidAmountMinor` incremented;
    - Posting failure after retries: set `postingError=true`, emit `InvoicePostingFailed`, create reconciliation record.
    - Chargeback: triggers compensating reversal posting flow.
    - Overpayment: create customer credit while invoice transitions to Paid.
  - Provider test hints:
    - Test idempotency by replaying same `transactionId` with same and different payloads; assert no double-apply and 409 or no-op as specified by implementation.
    - Test full/partial/outstanding amount transitions and emitted events listed above.
    - Test posting retry exhaustion updates `postingError` and event emission.

- Story #5 (Reconcile POS Status with Accounting Authoritative Status): Backend issue: #5
  - Primary endpoints (gateway URLs):
    - `getInvoiceStatus` (GET) — `http://localhost:8080/v1/accounting/invoice/{invoiceId}/status`
    - `listEvents` (GET) — `http://localhost:8080/v1/accounting/events` (for processing/ingestion status)
  - Purpose: Provide authoritative accounting status for POS reconciliation and status refresh flows.
  - Behavioral assertions (derive from story):
    - Authoritative status enum (v1): `PENDING_POSTING`, `POSTED`, `RECONCILED`, `REJECTED`, `REVERSED`, `VOIDED`, `ON_HOLD`, `DISPUTED`.
    - Backward transitions (e.g., from POSTED -> PENDING_POSTING) must be blocked and cause an alert/incident (implementation: return 409 + audit log); mark as TODO where exact alerting channel unspecified and reference backend issues.
    - Permissions: three tiered authorities required for different UI/ops actions: `VIEW_ACCOUNTING_STATUS`, `REFRESH_ACCOUNTING_STATUS`, `VIEW_ACCOUNTING_DETAIL`.
    - Archived invoices continue to be eligible for synchronization.
    - Delivery semantics: at-least-once with DLQ on exhaustion (operational behavior; provider tests should validate DLQ handling where available).
    - SLA expectations (observability hints): critical statuses p95 < 5s, p99 < 30s; non-critical p95 < 30s, p99 < 2min.
    - Staleness indicator: when `accountingStatusUpdatedAt` > 1 hour surface stale flag in response.
  - Provider test hints:
    - Validate enum values are present and stable in OpenAPI responses.
    - Validate blocked backward transition behavior (trigger attempted backward transition and assert 409 or documented failure mode).
    - Validate permission gating returns 403 for missing authorities and 200 for authorized callers.

OpenAPI delta notes (authoritative source)

- This contract derives all operationIds and path shapes from `durion-positivity-backend/pos-accounting/openapi.yaml`.
- Gateway path format used in this guide: `http://localhost:8080/v1/accounting/{resource}` — do not call service ports directly.

TODO / Open questions

- Exact alerting channel and payload for blocked backward transitions (refer to backend issues #5/#6). TODO: implementers should update this contract after issue resolution.
- Confirmation of exact idempotency header/key naming conventions if they differ from `applicationRequestId` in OpenAPI; refer to backend issue #6.

Traceability

- OpenAPI reference (authoritative): durion-positivity-backend/pos-accounting/openapi.yaml
- Backend implementation backlog: louisburroughs/durion-positivity-backend issues #5, #6

Provider test checklist (minimum)

- Idempotency suite for `applyPayment` with duplicate transactionId and idempotencyKey variations.
- Full/partial/overpayment/chargeback flows validate invoice status transitions and emitted events.
- Reconcile flow validates status enum, staleness indicator, permission gating, and DLQ behavior on exhausted retries.
