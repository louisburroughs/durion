---
title: Billing Backend Contract Guide
domain: billing
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/billing/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-invoice/openapi.json
openapi_commit: ca7fadc3
last_verified_utc: 2026-02-24T14:23:11Z
last_updated: 2026-02-24
api_reference_generated: domains/billing/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Billing Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Billing domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-invoice/openapi.json`
- Generated API reference: `domains/billing/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/billing/.business-rules/AGENT_GUIDE.md`

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

- Billing behavioral rules are authoritative in backend services, not inferred from frontend state.
- Mutating operations require explicit permission enforcement and auditable outcomes.
- Error responses and correlation headers must be deterministic and traceable across requests.
- Cross-domain interactions must go through API/event contracts, not direct data coupling.

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-TBD | `None` | draft | Billing Capability Backlog |
| CAP-250 | [durion#250](https://github.com/louisburroughs/durion/issues/250) | draft | Card Acceptance via Payment Service |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Get billing rules for a party/customer | `getBillingRules` | GET | `/v1/billing/rules/{partyId}` | Refer to generated API reference for payload details |
| Create or update billing rules | `upsertBillingRules` | PUT | `/v1/billing/rules/{partyId}` | Refer to generated API reference for payload details |

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-TBD: Billing Capability Backlog

### Capability Metadata

- Capability ID: CAP-TBD
- Parent Issue: None
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-invoice/openapi.json`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get billing rules for a party/customer | `getBillingRules` | GET | `/v1/billing/rules/{partyId}` |
| Create or update billing rules | `upsertBillingRules` | PUT | `/v1/billing/rules/{partyId}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

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

- Provider tests: `durion-positivity-backend/pos-invoice/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-250: Payments (Card Acceptance via Payment Service)

### Capability Metadata

- Capability ID: CAP-250
- Parent Issue: [durion#250](https://github.com/louisburroughs/durion/issues/250)
- Capability Status: draft
- Backend Stories: [#9](https://github.com/louisburroughs/durion-positivity-backend/issues/9), [#7](https://github.com/louisburroughs/durion-positivity-backend/issues/7), [#8](https://github.com/louisburroughs/durion-positivity-backend/issues/8)
- Module: `pos-invoice`
- OpenAPI Source: `durion-positivity-backend/pos-invoice/openapi.yaml`

### Frontend API Lookup

| UI Task | operationId | Method | Gateway Path | Notes |
| --- | --- | --- | --- | --- |
| Initiate card payment (sale/capture) | `initiatePayment` | POST | `/v1/billing/invoices/{invoiceId}/payments` | Default SALE_CAPTURE; use `flow=AUTH_ONLY` with SELECT_PAYMENT_FLOW permission |
| Manually capture an authorization hold | `capturePayment` | POST | `/v1/billing/invoices/{invoiceId}/payments/{paymentId}/capture` | Requires MANUAL_CAPTURE permission |
| Inquire on unknown payment outcome | `inquirePaymentStatus` | GET | `/v1/billing/invoices/{invoiceId}/payments/{paymentId}/status` | Use before retry to avoid duplicate charges |
| Generate receipt after capture | `generateReceipt` | POST | `/v1/billing/invoices/{invoiceId}/receipts` | Triggers async email if consent given |
| Get stored receipt | `getReceipt` | GET | `/v1/billing/invoices/{invoiceId}/receipts/{receiptId}` | Returns immutable receipt with original templateVersion |
| Reprint receipt | `reprintReceipt` | POST | `/v1/billing/invoices/{invoiceId}/receipts/{receiptId}/reprint` | Requires reprint authorization; watermark applied |
| Void an authorization hold | `voidPayment` | POST | `/v1/billing/invoices/{invoiceId}/payments/{paymentId}/void` | Requires VOID_PAYMENT permission + VOID_REASON |
| Refund a captured payment | `refundPayment` | POST | `/v1/billing/invoices/{invoiceId}/payments/{paymentId}/refund` | Requires REFUND_PAYMENT permission + REFUND_REASON; async lifecycle |

### Permission Matrix

| Operation | Required Permission | Notes |
| --- | --- | --- |
| Initiate payment (default) | `PROCESS_PAYMENT` | Up to configured threshold (default $500) |
| Initiate payment (over threshold) | `OVERRIDE_PAYMENT_LIMIT` | Manager approval required |
| Select AUTH_ONLY flow | `SELECT_PAYMENT_FLOW` | In addition to PROCESS_PAYMENT |
| Manual capture | `MANUAL_CAPTURE` | Back-office captures after AUTH_ONLY |
| Void authorization | `VOID_PAYMENT` | Requires reason |
| Refund captured payment | `REFUND_PAYMENT` | Requires reason |

### Behavioral Assertions (Story #9 — Authorization and Capture)

- Default payment flow is `SALE_CAPTURE`; `AUTH_ONLY` requires the `requiresManagerApproval`, `amountMayChange`, or equivalent policy flag, or cashier with `SELECT_PAYMENT_FLOW`.
- Idempotency key (`Idempotency-Key` header) is required for all payment mutations; duplicate submissions with the same key return the previous outcome without re-executing.
- Retry policy: authorization uses 30s timeout with up to 2 automatic retries (backoff 5s/10s); capture uses 30s timeout with up to 1 automatic retry (backoff 10s).
- On unknown outcome (timeout/network failure), gateway status inquiry is performed by idempotency key before any retry to prevent duplicate charges.
- `PaymentIntent` lifecycle: `PENDING` → `AUTHORIZED` → `CAPTURED` | `CAPTURE_FAILED` | `EXPIRED`.
- `EXPIRED` state: background job marks authorizations expired after hold window (credit ≤ 7 days, debit ≤ 3 days) and attempts gateway void if supported.
- Partial capture (v1): single partial capture per authorization; remainder is voided automatically.
- No PAN or CVV stored; gateway tokenization is mandatory.
- `authorizedAmount`, `capturedAmount`, and `voidedRemainderAmount` are tracked on the payment record.

### Behavioral Assertions (Story #7 — Receipt and Reference)

- Receipt is generated after confirmed `CAPTURED` state; receipt stores `templateId` + `templateVersion` immutably at creation time.
- Reprints MUST use the original `templateVersion`; template upgrades do not retroactively affect stored receipts.
- Mandatory receipt fields: merchant info, invoice number, payment amount, card brand + last4, authorization code, transaction ID, timestamp, cashier/terminal ID.
- Email delivery is asynchronous; bounded retries (configurable, default 3 attempts); explicit final `DELIVERY_FAILED` state surfaced when retries exhausted.
- Reprint watermarking is mandatory; reprint authorization limits are enforced.
- No PAN stored in receipt; only card brand + last4 permitted.

### Behavioral Assertions (Story #8 — Void and Refund)

- Void is only available while payment is in `AUTHORIZED` (pre-settlement) state; requires `VOID_REASON` (controlled enum).
- Refund is only available while payment is in `CAPTURED` or `SETTLED` state; requires `REFUND_REASON` (controlled enum).
- `OTHER` reason requires non-empty `notes` field in both void and refund requests.
- Time windows (configurable): card void ≤ 24h from authorization; card refund ≤ 180 days from capture.
- Async refund lifecycle: `REQUESTED` → `PENDING` → `COMPLETED` | `FAILED`.
- Partial refund rules: minimum refund amount enforced; total refunds cannot exceed captured amount.
- Split-tender allocation uses LIFO order by default.
- All out-of-window or over-limit overrides require manager authorization and produce an auditable authorization trail.

### Audit and Security Rules

- Actor fields (cashier, terminal) derive from authenticated security context only (ADR-0018); callers MUST NOT pass actor identity in request body.
- All state-changing payment operations emit `@EmitEvent`-annotated events for audit trail.
- Permission enforcement is at the service layer, not controller layer only.

### Status Code Semantics (ADR-0017)

| Scenario | HTTP Status |
| --- | --- |
| Payment captured successfully | 201 |
| Authorization created (AUTH_ONLY) | 201 |
| Receipt generated | 201 |
| Void/refund accepted (async) | 202 |
| Invalid request / validation failure | 400 |
| Insufficient permissions | 403 |
| Invoice or payment not found | 404 |
| Duplicate idempotent request | 200 (returns original outcome) |
| Payment method declined | 422 |
| Gateway unavailable / unknown outcome | 503 |

### Provider Test Hints

- Service-layer tests must cover: `SALE_CAPTURE` happy path, `AUTH_ONLY` → explicit capture path, partial capture with remainder void, idempotent retry returning original outcome, gateway inquiry before retry on unknown outcome.
- Receipt tests: immutable template version on reprint, async email delivery states, mandatory field validation.
- Void/refund tests: VOID_REASON enum coverage, REFUND_REASON + OTHER/notes validation, time window enforcement, async refund state transitions, partial refund limit enforcement.
- Tests reside in `durion-positivity-backend/pos-invoice/src/test/`.

### Implementation Links

- [Story #9 — Initiate Card Authorization and Capture](https://github.com/louisburroughs/durion-positivity-backend/issues/9)
- [Story #7 — Print/Email Receipt and Store Reference](https://github.com/louisburroughs/durion-positivity-backend/issues/7)
- [Story #8 — Void Authorization or Refund Captured Payment](https://github.com/louisburroughs/durion-positivity-backend/issues/8)

---

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-invoice/openapi.json`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:23:11Z`
- Generated API reference: `domains/billing/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/billing/.business-rules/AGENT_GUIDE.md`
- `domains/billing/.business-rules/DOMAIN_NOTES.md`
- `domains/billing/.business-rules/BACKEND_API_REFERENCE.generated.md`
