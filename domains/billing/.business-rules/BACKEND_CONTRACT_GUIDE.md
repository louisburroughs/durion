---
type: Backend Contract
title: Billing Backend Contract Guide
domain: billing
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/billing/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-invoice/openapi.yaml
openapi_commit: ca7fadc3
last_verified_utc: 2026-02-24T14:23:11Z
last_updated: 2026-02-24
api_reference_generated: domains/billing/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
description: This is the curated contract guide for Billing domain behavior.
tags: [domain, billing, backend-contract]
---

# Billing Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Billing domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-invoice/openapi.yaml`
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
- OpenAPI Source: `durion-positivity-backend/pos-invoice/openapi.yaml`

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
- Invoice lifecycle `DRAFT → FINALIZED → POSTED` (backend#1843): finalization emits
  `invoice.invoice.updated` (status `FINALIZED`) on `invoice.events.v1`; pos-accounting posts the revenue
  journal entry and publishes `accounting.invoice.gl-posted` (`InvoiceGlPostedV1`) on
  `accounting.events.v1`; pos-invoice consumes that fact to transition the invoice to `POSTED`, recording
  the fact's `journalEntryId` (the canonical posting reference, `AD-011`) as the invoice's `glEntryId`.
  pos-invoice no longer simulates GL posting in-process. A `FINALIZED → DRAFT` revert (within the
  finalization revert window enforced by `InvoiceFinalizationService`, before the posted fact arrives) is
  reversed by accounting on the `DRAFT` update.
- `InvoiceUpdatedV1.taxBreakdown[]` (`TaxBreakdownLine`) gains **`taxType`** (CAP:550 S32a, backend#2636,
  backend PR #2640): a nullable String, the last record component, carrying the tax-type code of the tax
  row exactly as pos-tax priced it (e.g. `GST`). Tax types are a configuration-only vocabulary declared per
  country in pos-tax (`pos.tax.countries.<country>.tax-types`); there is no enum, and a code is 1–32
  upper-case letters, digits or underscores. The code is copied, never inferred: `null` for an untyped row
  (every US row, and a malformed value). Additive within schema version 1 on `invoice.events.v1`, no version
  bump, no dual-publish (ADR-0044 §3); a consumer built before the field ignores it. Persisted in
  `invoice_line_tax.tax_type` and `invoice_tax_summary.tax_type` (`varchar(32) NULL`, pos-invoice V4); the
  summary rollup key is `jurisdictionType|jurisdictionCode|taxType`, so two tax types sharing a jurisdiction
  are never merged. Written only by the DRAFT re-price path and frozen at finalization (BILL-DEC-004); no
  backfill, existing rows stay null. The `tax == Σ taxBreakdown.taxAmount` invariant and the
  empty-versus-null contract (#982) are unchanged. No invoice or receipt endpoint, DTO or SDK changes.

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

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Initiate card payment (sale/capture) | `initiatePayment` | POST | `/v1/invoices/{invoiceId}/payments` | Requires `invoice:payment:process`. Body field `paymentFlow` is required: `SALE_CAPTURE`, or `AUTH_ONLY`, which also requires `invoice:payment:flow_select` |
| Manually capture an authorization hold | `capturePayment` | POST | `/v1/invoices/{invoiceId}/payments/{paymentId}/capture` | Requires `invoice:payment:capture` |
| Inquire on unknown payment outcome | *(planned — not yet in pos-invoice OpenAPI)* | GET | `/v1/invoices/{invoiceId}/payments/{paymentId}/status` | Use before retry to avoid duplicate charges |
| Generate receipt after capture | `generateReceipt` | POST | `/v1/invoices/{invoiceId}/receipts` | Requires `invoice:receipt:generate`. Stores the receipt; it does not send an email |
| Get stored receipt | `getReceipt` | GET | `/v1/invoices/{invoiceId}/receipts/{receiptId}` | Requires `invoice:invoice:view`. Returns the stored receipt with its `templateVersion` |
| Reprint receipt | `reprintReceipt` | POST | `/v1/invoices/{invoiceId}/receipts/{receiptId}/reprint` | No permission beyond authentication while the receipt's reprint count is below 5; from then on requires `invoice:receipt:reprint_override` |
| Void an authorization hold | `voidPayment` | POST | `/v1/invoices/{invoiceId}/payments/{paymentId}/void` | Requires `invoice:payment:void` + VOID_REASON; outside the 24-hour window also `invoice:payment:override` |
| Refund a captured payment | `refundPayment` | POST | `/v1/invoices/{invoiceId}/payments/{paymentId}/refunds` | Requires `invoice:payment:refund` + REFUND_REASON; outside the 180-day window also `invoice:payment:override`; async lifecycle |

Paths are the pos-invoice paths in `openapi.yaml`. The gateway has no `/billing` route: it exposes pos-invoice under
the `/invoice` prefix (`Path=/invoice/**`, `StripPrefix=1`). A client calls `POST /invoice/invoices/{invoiceId}/payments`
with `X-API-Version: 1`; the gateway rewrites that to `/invoice/v1/invoices/{invoiceId}/payments` and forwards
`/v1/invoices/{invoiceId}/payments` to pos-invoice. A path that already carries the version segment
(`/invoice/v1/...`) is forwarded without the rewrite.

### Permission Matrix

| Operation | Required Permission | Notes |
| --- | --- | --- |
| Initiate payment (default) | `invoice:payment:process` | Amount up to 500.00. Enforced on the endpoint |
| Initiate payment (over threshold) | `invoice:payment:limit_override` | Amount above 500.00, a fixed threshold in `PaymentServiceImpl`. In addition to `invoice:payment:process`. Checked in the service, so the operation's `x-required-permissions` does not list it |
| Select AUTH_ONLY flow | `invoice:payment:flow_select` | In addition to `invoice:payment:process`. Checked in the service, so the operation's `x-required-permissions` does not list it |
| Manual capture | `invoice:payment:capture` | Back-office captures after AUTH_ONLY. Enforced on the endpoint |
| Void authorization | `invoice:payment:void` | Requires reason. Enforced on the endpoint |
| Void authorization (out of window) | `invoice:payment:override` | More than 24 hours after the payment intent was created. In addition to `invoice:payment:void`. Checked in the service, so the operation's `x-required-permissions` does not list it. Without it the void is refused `422 PAYMENT_WINDOW_EXPIRED` |
| Refund captured payment | `invoice:payment:refund` | Requires reason. Enforced on the endpoint |
| Refund captured payment (out of window) | `invoice:payment:override` | More than 180 days after the payment intent was created. In addition to `invoice:payment:refund`. Checked in the service, so the operation's `x-required-permissions` does not list it. Without it the refund is refused `422 PAYMENT_WINDOW_EXPIRED` |
| Manual refund not tied to a captured payment | `invoice:refund:issue_manual` | `createStandaloneInvoiceRefund` and `createStandalonePartyRefund`. Enforced on the endpoint |
| Generate receipt; record its print or email delivery | `invoice:receipt:generate` | `generateReceipt`, `recordReceiptPrintDelivery`, `recordReceiptEmailDelivery`. Enforced on the endpoint |
| Get stored receipt | `invoice:invoice:view` | `getReceipt`. Enforced on the endpoint |
| Reprint receipt | *(authentication only)* | While the receipt's reprint count is below 5 |
| Reprint receipt (over the cap) | `invoice:receipt:reprint_override` | Once the reprint count has reached 5. Checked in the service, so the operation's `x-required-permissions` does not list it. Without it the reprint is refused `409 REPRINT_LIMIT_EXCEEDED` |

The `invoice:payment:*`, `invoice:receipt:*` and `invoice:refund:*` codes above are catalog permissions: backend #2226
registered `invoice:payment:void`, `invoice:payment:refund`, `invoice:payment:override`, `invoice:receipt:generate`,
`invoice:receipt:reprint_override` and `invoice:refund:issue_manual`; #2393 registers `invoice:payment:process`,
`invoice:payment:limit_override`, `invoice:payment:flow_select` and `invoice:payment:capture`. The
earlier raw authority strings (`PROCESS_PAYMENT`, `OVERRIDE_PAYMENT_LIMIT`, `SELECT_PAYMENT_FLOW`,
`MANUAL_CAPTURE`, `VOID_PAYMENT`, `REFUND_PAYMENT`, `GENERATE_RECEIPT`, `ISSUE_MANUAL_REFUND`, `SUPERVISOR_OVERRIDE`)
are retired: no role holds them and no check accepts them.
Each payment mutation is also scoped to the invoice's location
([ADR-0061](../../../docs/adr/0061-location-scope-authorization-ownership.adr.md)): a caller whose grant of the code does
not reach that location is refused `403 LOCATION_SCOPE_DENIED`. The conditional codes (`invoice:payment:limit_override`,
`invoice:payment:flow_select`, `invoice:payment:override`, `invoice:receipt:reprint_override`) are scoped the same way.
`createStandalonePartyRefund` has no invoice and is not location-scoped.

### Behavioral Assertions (Story #9 — Authorization and Capture)

- `paymentFlow` (`SALE_CAPTURE` or `AUTH_ONLY`) is a required field of the `initiatePayment` request body. A request with
  `paymentFlow` `AUTH_ONLY` is accepted only when the caller holds `invoice:payment:flow_select` in addition to
  `invoice:payment:process`; without it the request is refused `403`. No policy flag substitutes for the permission: the
  request body has no `requiresManagerApproval` or `amountMayChange` field (its fields are `paymentFlow`, `amount`,
  `idempotencyKey` and `paymentToken`), and `initiatePayment` consults no such flag.
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

## CAP-007: Invoice Finder Search

### Capability Metadata

- Capability: `cap:007` — Convert Workorder to Invoice
- Parent story: [durion#337](https://github.com/louisburroughs/durion/issues/337)
- Backend child: [durion-positivity-backend#765](https://github.com/louisburroughs/durion-positivity-backend/issues/765)
- Module: `pos-invoice` (with a server-side dependency on `pos-workorder`)

### Frontend API Lookup

- `GET /v1/invoices/search?q={q}&page={p}&size={s}` → `Page<InvoiceSearchResult>` — finder backing the billing landing invoice-detail card. SDK: `InvoiceSearchService.searchInvoices(pageable, q)`.

### Permission Matrix

- `GET /v1/invoices/search` requires authority `invoice:manage`.
- `POST /v1/workorders/numbers:resolve` (pos-workorder, internal) requires authority `workorder:workorder:view`.

### Behavioral Assertions

- The query matches the **invoice number** (case-insensitive substring; LIKE metacharacters `%`/`_` are escaped and matched literally), the **customer name** (resolved to party ids via pos-customer), or the **workorder number** (resolved to workorder ids via pos-workorder).
- Each result row is enriched with the resolved customer display name and the human workorder number; the invoice row itself stores only `partyId` and `workorderId`.
- A blank/whitespace `q` returns an empty page (the finder requires a term; it does not list all invoices).
- Page size is capped at 50; default sort is `createdAt` descending for deterministic pagination.
- Reference resolution/enrichment runs outside any open DB transaction, with bounded connect/read timeouts on the outbound calls; a sibling-service failure degrades the affected enrichment field to null rather than failing the search.
- `InvoiceSearchResult`: `invoiceId`, `invoiceNumber`, `customerName`, `workorderId`, `workorderNumber`, `status` (`DRAFT|FINALIZED|POSTED|ERROR`), `total`, `createdAt`.

### Status Code Semantics (ADR-0017)

- `200 OK` — page of results (possibly empty).
- `400 Bad Request` — invalid pagination parameters.
- `403 Forbidden` — caller lacks `invoice:manage`.

### Events

- `INVOICE_SEARCH` (fastRead) — emitted on invoice search.
- `WORKORDER_NUMBER_RESOLVE` (fastRead) — emitted on the pos-workorder batch id→number resolution.

### Implementation Links

- [Parent story #337](https://github.com/louisburroughs/durion/issues/337) · [Backend #765](https://github.com/louisburroughs/durion-positivity-backend/issues/765)
- Implementation doc: `docs/capabilities/CAP-007/CAP-007-backend-implementation.md`

---

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-invoice/openapi.yaml`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:23:11Z`
- Generated API reference: `domains/billing/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/billing/.business-rules/AGENT_GUIDE.md`
- `domains/billing/.business-rules/DOMAIN_NOTES.md`
- `domains/billing/.business-rules/BACKEND_API_REFERENCE.generated.md`
