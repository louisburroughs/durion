# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates implementations within the Billing domain (billing rules, invoice draft creation, invoice issuance, traceability/artifacts, AP vendor payments, checkout PO enforcement, receipts).

## Completed items

- [x] Aligned checklist items with `BILL-DEC-###` decisions in `AGENT_GUIDE.md`
- [x] Captured contract and security invariants needed for implementation

---

## Scope/Ownership

- [ ] Story stays within Billing responsibilities and does not implement Accounting-owned GL logic beyond displaying acknowledgements/status.
- [ ] UI does not couple to upstream internal storage; all upstream data consumed via stable contracts.
- [ ] Billing stores traceability references; upstream systems own the records.
- [ ] Draft invoice creation is Billing-owned and idempotent by workOrderId.
- [ ] Checkout PO enforcement consumes backend policy evaluation; UI does not hardcode policy thresholds.
- [ ] AP vendor payment execution is Billing/AP capability; GL posting is downstream/async.

---

## Data Model & Validation

- [ ] InvoiceStatus matches canonical enum set (BILL-DEC-001): `DRAFT`, `ISSUED`, `PAID`, `VOID` (optional `ERROR` only if implemented).
- [ ] Draft creation validates Work Order completion + `invoiceReady=true`; otherwise deterministic 409 reason codes.
- [ ] Draft creation returns structured 422 missing fields for billing data completeness.
- [ ] Traceability snapshot fields are immutable and read-only:
  - [ ] `sourceWorkOrderId`, `sourceBillableScopeSnapshotId`, `sourceSchemaVersion`
  - [ ] `sourceEstimateId`, `sourceEstimateVersionId`
  - [ ] `sourceApprovalIds[]`
- [ ] Issuance validates state is `DRAFT`; issuance from non-draft returns 409.
- [ ] Issuance blockers are structured and actionable (422) with stable blocker codes.

BillingRules:

- [ ] GET returns ETag; PUT uses If-Match for updates; 409 on stale etag (BILL-DEC-006).
- [ ] Options/enums sourced from backend discovery endpoints (BILL-DEC-007).

Checkout PO:

- [ ] PO capture validated server-side; write-once enforced.
- [ ] Overrides require reason code + elevation token where required (BILL-DEC-009, BILL-DEC-010).

Receipts:

- [ ] Delivery statuses are canonical (BILL-DEC-011).
- [ ] Reprint policy evaluation returned; no UI hardcoding.

AP vendor payments:

- [ ] Idempotent by `paymentRef`.
- [ ] Allocation validation enforced and deterministic.
- [ ] Status enums match canonical set (BILL-DEC-013).

---

## API Contract

- [ ] Contracts exist for:
  - [ ] Create draft invoice (`POST /billing/invoices/draft`)
  - [ ] Get invoice detail (`GET /billing/invoices/{invoiceId}`)
  - [ ] Get invoice by work order (`GET /billing/invoices/by-workorder/{workOrderId}`)
  - [ ] Issue invoice (`POST /billing/invoices/{invoiceId}/issue`)
  - [ ] BillingRules GET/PUT with ETag/If-Match
  - [ ] Discovery endpoints for options/enums
  - [ ] Artifact list + secure download
  - [ ] Checkout evaluation + PO capture + PO override
  - [ ] Receipts view + deliver + reprint
  - [ ] AP bills list + execute payment + payment detail
- [ ] HTTP semantics consistent:
  - [ ] 422 validation failures with structured errors
  - [ ] 409 state conflicts / stale etag / idempotency conflict
  - [ ] 403 permission failures
  - [ ] 404 not found/inaccessible resources
  - [ ] 503 downstream unavailable
- [ ] Error response shape is stable enough for UI:
  - [ ] `errorCode`, `message`, `fieldErrors|missingFields|blockers`, `correlationId`

---

## Events & Idempotency

- [ ] Draft creation idempotent per workOrderId.
- [ ] Issuance idempotent per invoiceId+version; emits `InvoiceIssued` exactly once.
- [ ] AP payment idempotent by paymentRef; conflicting payload returns 409.
- [ ] Checkout finalize is safe against double-submit.
- [ ] Override actions are protected against duplicate submissions.
- [ ] Emitted events include: actorUserId, timestamps, tenant scope, correlation.

---

## Security

- [ ] All endpoints require auth; tenant boundaries enforced server-side.
- [ ] Authorization enforced server-side for:
  - [ ] BillingRules view/manage
  - [ ] create draft invoice
  - [ ] issue invoice
  - [ ] traceability panel (if restricted)
  - [ ] artifact list/download
  - [ ] AP execute/view
  - [ ] checkout PO override + second approver
  - [ ] receipts delivery/reprint
- [ ] Permissions come from a single source of truth (canonical strings) (BILL-DEC-008).
- [ ] Artifact downloads protected:
  - [ ] short-lived tokens or signed URLs
  - [ ] never logged
  - [ ] scoped to user and invoice context
- [ ] Sensitive data not logged:
  - [ ] emails, addresses, artifacts content, payment instrument secrets, manager credentials
- [ ] Step-up auth uses elevation token; do not log secrets (BILL-DEC-010).
- [ ] Posting errors sanitized for frontline roles (BILL-DEC-014).

---

## Observability

- [ ] W3C Trace Context propagated (traceparent/tracestate) (BILL-DEC-015).
- [ ] Structured logs include identifiers only; no PII.
- [ ] Audit events recorded for irreversible actions: issue, override, execute payment, reprint.

---

## Performance & Failure Modes

- [ ] Invoice detail supports many lines efficiently.
- [ ] Artifact list loads asynchronously; does not block invoice detail rendering.
- [ ] Vendor bills list supports large sets with paging/filtering.
- [ ] Timeouts and bounded retries; UI preserves form state on retry.
- [ ] Deterministic handling of 409 conflicts.

---

## Testing

- [ ] Contract tests pin canonical enums and field names (traceability).
- [ ] Idempotency tests for draft, issuance, AP payment, overrides.
- [ ] Security tests for permissions and artifact download scoping.
- [ ] E2E tests cover draft → review → issue; PO required + override; AP payment + GL status refresh; receipt delivery + reprint.

---

## Open Questions — With Responses (Billing Domain)

> Full questions are restated from the prior checklist “Open Questions to Resolve” section, followed by responses. These responses are now the intended contract for future stories.

### Q1

**Question:** What are the canonical invoice traceability field names: `sourceEstimateId` vs `sourceEstimateVersionId` vs both, and what is the exact invoice detail response schema?  
**Response:** Use both canonical fields under `traceability`:

- `sourceEstimateId` (optional)
- `sourceEstimateVersionId` (optional, preferred when present)  
Invoice detail must include `traceability` and `issuancePolicy/issuanceBlockers` when status is DRAFT.

### Q2

**Question:** Does invoice detail include an explicit `issuanceBlockers[]` array, or must the UI only learn blockers from the issuance attempt (`422`)?  
**Response:** Yes, invoice detail includes `issuancePolicy` and `issuanceBlockers[]`. Issuance attempt returns 422 with the same blocker codes.

### Q3

**Question:** What are the exact permission IDs/roles for: traceability panel, issue invoice, artifact download, BillingRules view/manage, draft creation, AP payments, PO override, supervisor/second-approver actions?  
**Response:** Use canonical permission strings from BILL-DEC-008 (AGENT_GUIDE permission table). Supervisor/second-approver uses `billing:auth:elevate` plus the specific action permission (e.g., `billing:checkout:po:override`).

### Q4

**Question:** What is the artifact retrieval contract: endpoint/service names, input is invoiceId vs approvalIds[], output is signed URL vs secure endpoint?  
**Response:** Input is `invoiceId`. Provide:

- `GET /billing/invoices/{invoiceId}/artifacts` (list)
- Secure download via tokenized download endpoint (preferred) or short-lived signed URLs when explicitly requested and authorized.

### Q5

**Question:** Which traceability identifiers are policy-required to issue (approval always? estimate version always? configurable per customer/account), and how is policy communicated to the UI?  
**Response:** Configurable per tenant/account and communicated via `issuancePolicy` + `issuanceBlockers[]`. Always required: work order id, snapshot id, schema version. Conditional: approvals and estimate version based on policy.

### Q6

**Question:** Do routes/screens exist for Work Order, Estimate, Approval record navigation in this frontend? If not, copy-only?  
**Response:** Billing returns link metadata; UI renders links only if routes exist. Otherwise provide copy-only identifiers.

### Q7

**Question:** What are the canonical Moqui screen paths and service names for invoice detail, create draft, issue invoice, receipts, AP flows?  
**Response:** Treat HTTP capability endpoints as canonical. Moqui screens must map to them; screen paths are implementation details and must not be embedded into domain contracts.

### Q8

**Question:** Are partial payments supported in POS receipt/payment flows, and what statuses must the UI render (email delivery; GL posting)?  
**Response:** Partial payments supported unless policy disables. Receipt shows partial vs paid-in-full and remaining balance. Email delivery statuses: `PENDING/SENT/FAILED/BOUNCED`. AP GL posting statuses per BILL-DEC-013.

### Q9

**Question:** What is the exact “manager approval code” semantics and what content is safe to show in `postingErrorSummary` to non-admin users?  
**Response:** Replace code with step-up auth elevation token (`/billing/auth/elevate`). `postingErrorSummary` for non-admin is sanitized code + generic guidance + correlationId only; detailed diagnostics restricted.

### Q10

**Question:** What are the exact enum values returned for invoice states so UI mapping is consistent and testable?  
**Response:** `DRAFT`, `ISSUED`, `PAID`, `VOID` (optional `ERROR` only if implemented). UI must derive “editable/finalized” from these enums.

---

## End

End of document.
