Title: [BACKEND] [STORY] Payment: Initiate Card Authorization and Capture
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/9
Labels: payment, type:story, domain:billing, status:ready-for-dev

## Story Intent

Enable **Cashiers** to accept card payments at checkout by authorizing and capturing customer credit/debit card payments, ensuring PCI-DSS compliant tokenization, producing receipts, and notifying accounting of successful captures. Support both auth-then-capture (two-step) and sale/capture (one-step) flows with safe retry/idempotency semantics.

---

## Actors & Stakeholders

- **Cashier** (initiates card payment)
- **Customer** (presents card)
- **Payment Gateway** (authorization/capture)
- **Payment Service** (orchestrates tokenization, idempotency, status inquiries)
- **Billing Service** (updates invoice status)
- **Accounting Service** (receives `PaymentCaptured` events)
- **Receipt Service**, **Audit Service**, **Finance Team**

---

## Preconditions

- Invoice exists with outstanding balance > $0
- POS terminal is online and authenticated with a configured gateway
- Cashier has `PROCESS_PAYMENT` permission
- Gateway provider configured for tenant/location (MVP: single gateway)

---

## Resolved: Answers to Open Questions (canonical decisions)

### Q1: Supported gateways — Decision
MVP supports exactly one configured gateway per environment/tenant via a `PaymentGatewayPort` adapter. Multi-gateway/failover is out-of-scope for v1; include `gatewayProvider` on `PaymentIntent` and preserve raw `gatewayResponse` for future adapters.

---

### Q2: Authorization hold duration — Decision
Configurable defaults with warnings and automated cleanup:
- Credit (Visa/MC/Amex): usable up to **7 days**; warn at >5 days.
- Debit: usable up to **3 days**; warn at >2 days.
- Background job marks authorizations **EXPIRED** after window and attempts gateway void if supported.

---

### Q3: When to use AUTH_ONLY vs SALE_CAPTURE — Decision
Default: **SALE_CAPTURE** for POS checkout. Use **AUTH_ONLY** when invoice flags indicate delayed capture (e.g., `requiresManagerApproval`, `amountMayChange`, partial fulfillment). Cashier selection allowed only with `SELECT_PAYMENT_FLOW` permission.

---

### Q4: Partial capture rules — Decision
Supported if gateway supports partial capture; v1 enforces **single partial capture per authorization** and voids remainder. Add `authorizedAmount`, `capturedAmount`, `voidedRemainderAmount` to model.

---

### Q5: Receipt requirements — Decision
Reuse Receipt Content v1: print by default, email optional with consent, retain for 7 years, reprint supported with watermark. Mandatory fields: merchant info, invoice, amount, card brand + last4, auth code, txn id, timestamp, cashier/terminal.

---

### Q6: Retry policy for timeouts — Decision
Tight, idempotent retries with inquiry on unknown outcome:
- Authorization: timeout 30s, up to 2 automatic retries (total 3) backoff 5s/10s.
- Capture: timeout 30s, up to 1 automatic retry (total 2) backoff 10s.
- If outcome unknown, perform gateway status inquiry by idempotency key/txn ref before retrying to avoid duplicate charges.

---

### Q7: Authorization for manual capture/void — Decision
Permission model:
- `PROCESS_PAYMENT`: cashier can perform immediate sale/capture and same-session auth→capture.
- `MANUAL_CAPTURE`: required for later/back-office captures.
- `VOID_PAYMENT`: required to void authorizations (requires reason).
- `OVERRIDE_PAYMENT_LIMIT`: for exceeding configured thresholds.
Default thresholds (configurable): cashier up to $500; >$500 requires manager approval.

---

## Minimal spec deltas implied
- Add `paymentStatus: EXPIRED` (or CAPTURE_FAILED+reason) and fields `authorizedAmount`, `capturedAmount`, `voidedRemainderAmount`.
- Add `gateway status inquiry` operation for unknown outcomes.
- Add explicit permissions: `SELECT_PAYMENT_FLOW`, `MANUAL_CAPTURE`, `VOID_PAYMENT`, `OVERRIDE_PAYMENT_LIMIT`.
- Implement single-gateway MVP via adapter interface.

---

## Acceptance Criteria (high level)
- Authorizations and captures follow the flows and update `PaymentIntent` statuses accordingly.
- Sale/capture completes in one step; auth→capture supported with single partial capture and remainder void.
- Tokenization used for PCI safety; no PAN/CVV stored.
- Idempotency keys used for retry safety; status inquiries performed for unknown outcomes.
- Receipt generated and retained per policy; reprint supported.
- Audit events and metrics tracked as specified.

---

*Status: ready for development.*
