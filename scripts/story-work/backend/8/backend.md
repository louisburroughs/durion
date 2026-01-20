Title: [BACKEND] [STORY] Payment: Void Authorization or Refund Captured Payment
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/8
Labels: payment, type:story, domain:billing, status:ready-for-dev

## Story Intent

Enable **Store Managers** to void authorized payments before settlement or refund captured payments after settlement, ensuring financial corrections are executed safely with proper authorization, reason capture, and accounting synchronization, while supporting both full and partial refund scenarios.

This story establishes the **payment reversal workflow** that handles error correction, customer accommodation, and financial reconciliation while maintaining audit compliance and preventing unauthorized reversals.

---

## Actors & Stakeholders

### Primary Actors
- **Store Manager**: Initiates void or refund requests with authorization and reason
- **Payment Service**: Executes void/refund operations via payment gateway API
- **Payment Gateway**: Processes void (before settlement) or refund (after settlement) transactions
- **Billing Service**: Updates invoice payment status based on void/refund outcomes
- **Accounting Service**: Receives void/refund events and updates GL postings (reversal entries)

### Secondary Stakeholders
- **Customer**: Receives refund credit (card refund, cash refund, account credit)
- **Cashier**: May be notified of refund approval and executes cash refund if applicable
- **Audit Service**: Logs all void/refund attempts with authorization trail
- **Finance Team**: Monitors refund metrics for fraud detection and reconciliation

---

## Preconditions

1. Payment exists in one of the following states:
   - **Authorized** (not yet captured/settled): Eligible for void
   - **Captured/Settled**: Eligible for refund
2. Original payment transaction has valid payment ID and gateway transaction reference
3. User has required permission (`VOID_PAYMENT` or `REFUND_PAYMENT`) based on operation
4. Invoice associated with payment exists and is accessible
5. Payment gateway API is available and responding
6. For partial refunds: Remaining refundable amount is greater than zero

---

## Functional Behavior

(Full void/refund flows, error flows, business rules, data models and acceptance criteria are implemented as described in the original story.)

---

## Resolved: Answers to Open Questions (canonical decisions)

### Q1: Complete list of void and refund reasons — Decision
Use two controlled enums (VOID_REASON, REFUND_REASON) with tight, reportable categories; require “OTHER” notes.

VOID_REASON (authorized-only):
- CUSTOMER_REQUEST
- DUPLICATE_AUTHORIZATION
- ENTRY_ERROR
- FRAUD_PREVENTION
- MANAGER_DISCRETION
- OTHER (requires notes)

REFUND_REASON (captured/settled):
- CUSTOMER_RETURN
- SERVICE_ERROR
- OVERCHARGE
- DAMAGED_GOODS
- GOODWILL
- CHARGEBACK_AVOIDANCE
- FRAUD_PREVENTION
- MANAGER_DISCRETION
- OTHER (requires notes)

Policy: reasons are immutable; additions require a version bump.

---

### Q2: Time windows for voids and refunds — Decision
Method-specific windows (configurable):
- Card: Void ≤ 24h from authorization; Refund ≤ 180 days from capture.
- ACH: Void ≤ 24h; Refund ≤ 60 days from settlement.
- Cash/Check: POS policy enforces ≤ 30 days without finance override.
Outside-window requires `SUPERVISOR_OVERRIDE` and emits an audit event.

---

### Q3: Authorization levels by amount — Decision
Tiered approval (configurable):
- ≤ $100: Store Manager (`REFUND_PAYMENT`)
- $100.01–$500: Store Manager + notes
- $500.01–$1,000: District Manager approval (request → approve → execute)
- > $1,000: Finance approval and dual authorization
Sums of refunds in 24h count toward thresholds.

---

### Q4: Cash vs Card refunds — Decision
Refund to the original tender instrument by default:
- Card → refund to same card via gateway only
- Cash → cash refund with manager/till controls
- Mixed tender → proportional refund by default; manager override only with permissions
Cash refunds record till/drawer info for reconciliation.

---

### Q5: Asynchronous refund failures — Decision
Refund lifecycle: REQUESTED → PENDING → COMPLETED / FAILED.
Gateway webhooks update RefundRecord; POS shows REFUND_PENDING until COMPLETED.
Poll PENDING beyond 7 days; alert customer service if terminal failure.
On failure revert invoice status and alert for manual resolution.

---

### Q6: Limits on partial refunds — Decision
- Max partial refunds per original payment: 10
- Minimum partial refund amount: $0.50 (configurable)
- No dust remainder: last refund must close to zero or meet minimum; overrides require finance approval.

---

### Q7: Multi-payment (split tender) refunds — Decision
Refunds operate against specific payments with LIFO allocation by default. If needed, the user may select target payment(s) where permitted. Multi-payment refund requests may create multiple RefundRecords (one per underlying payment).

---

### Minimal spec adjustments implied
- Add `RefundStatusChanged` event and `REFUND_PENDING` invoice state.
- Add parent `RefundRequest` entity to support multi-payment allocation (recommended).
- Make windows/thresholds configurable per tenant/store.
- Enforce partial refund caps and minimums with override audit events.

---

## Acceptance Criteria (high level)
- Voids on AUTHORIZED payments succeed and emit `PaymentVoided` event.
- Refunds on CAPTURED/SETTLED payments create `RefundRecord`, emit `PaymentRefunded` and follow asynchronous lifecycle.
- Partial refunds obey caps/minimums and update invoice net payment correctly.
- Audit log captures reason, approver, and gateway responses; retention 7+ years.
- Permissions and tiered approvals enforced; overrides recorded and auditable.

---

*Status: ready for development.*
